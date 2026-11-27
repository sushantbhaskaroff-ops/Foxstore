package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppReview
import com.example.data.AppSubmissionInput
import com.example.data.InstalledApp
import com.example.data.MarketplaceApp
import com.example.data.StoreRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MarketplaceViewModel(
    private val repository: StoreRepository,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String,
    val isAdmin: Boolean,
) : ViewModel() {

    val approvedAppsState: StateFlow<UiState<List<MarketplaceApp>>> =
        repository.observeApprovedApps().toUiStateFlow("approved apps")

    val mySubmissionsState: StateFlow<UiState<List<MarketplaceApp>>> =
        repository.observeMySubmissions(currentUserId).toUiStateFlow("submissions")

    val installedAppsState: StateFlow<UiState<List<InstalledApp>>> =
        repository.observeInstalledApps(currentUserId).toUiStateFlow("installed apps")

    val adminAppsState: StateFlow<UiState<List<MarketplaceApp>>> =
        if (isAdmin) {
            repository.observeAllAppsForAdmin().toUiStateFlow("admin queue")
        } else {
            mySubmissionsState
        }

    private val _selectedAppId = MutableStateFlow<String?>(null)
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedAppReviewsState: StateFlow<UiState<List<AppReview>>> =
        _selectedAppId.flatMapLatest { appId ->
            if (appId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeReviews(appId)
                    .map<List<AppReview>, UiState<List<AppReview>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing reviews", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load reviews"))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _installingAppIds = MutableStateFlow<Set<String>>(emptySet())
    val installingAppIds: StateFlow<Set<String>> = _installingAppIds.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        if (isAdmin) {
            viewModelScope.launch {
                repository.seedShowcaseAppsIfEmpty()
            }
        }
    }

    fun selectAppForDetail(appId: String?) {
        _selectedAppId.value = appId
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun notifyMessage(message: String) {
        _statusMessage.value = message
    }

    fun installApp(app: MarketplaceApp) {
        if (app.id in _installingAppIds.value) return
        viewModelScope.launch {
            _installingAppIds.value = _installingAppIds.value + app.id
            delay(750L)
            val res = repository.installApp(app)
            _installingAppIds.value = _installingAppIds.value - app.id
            res.fold(
                onSuccess = { _statusMessage.value = "${app.name} installed." },
                onFailure = { err -> _statusMessage.value = err.message ?: "Install failed" }
            )
        }
    }

    fun uninstallApp(appId: String, appName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.uninstallApp(appId)
            _isBusy.value = false
            res.fold(
                onSuccess = { _statusMessage.value = "$appName uninstalled." },
                onFailure = { err -> _statusMessage.value = err.message ?: "Uninstall failed" }
            )
        }
    }

    fun submitAppDirectly(input: AppSubmissionInput, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitApp(input)
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    _statusMessage.value = "${input.name} uploaded to Review Queue."
                    onSuccess()
                },
                onFailure = { err -> _statusMessage.value = err.message ?: "Upload failed" }
            )
        }
    }

    fun approveApp(app: MarketplaceApp, note: String = "Approved for AppVault Store.") {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.reviewApp(app.id, approved = true, reviewNote = note)
            _isBusy.value = false
            res.fold(
                onSuccess = { _statusMessage.value = "${app.name} is now live in the Play Store!" },
                onFailure = { err -> _statusMessage.value = err.message ?: "Approval failed" }
            )
        }
    }

    fun rejectApp(app: MarketplaceApp, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.reviewApp(app.id, approved = false, reviewNote = reason)
            _isBusy.value = false
            res.fold(
                onSuccess = { _statusMessage.value = "${app.name} marked as rejected." },
                onFailure = { err -> _statusMessage.value = err.message ?: "Rejection failed" }
            )
        }
    }

    fun deleteSubmission(app: MarketplaceApp) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.deleteApp(app.id)
            _isBusy.value = false
            res.fold(
                onSuccess = { _statusMessage.value = "${app.name} removed." },
                onFailure = { err -> _statusMessage.value = err.message ?: "Delete failed" }
            )
        }
    }

    fun submitReview(
        app: MarketplaceApp,
        rating: Int,
        comment: String,
        existingReviews: List<AppReview>,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.submitReview(
                app = app,
                rating = rating,
                comment = comment,
                authorName = currentUserName,
                existingReviews = existingReviews
            )
            _isBusy.value = false
            res.fold(
                onSuccess = {
                    _statusMessage.value = "Review published!"
                    onSuccess()
                },
                onFailure = { err -> _statusMessage.value = err.message ?: "Review failed" }
            )
        }
    }

    fun seedShowcaseApps() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.seedShowcaseAppsIfEmpty()
            _isBusy.value = false
            res.fold(
                onSuccess = { count ->
                    _statusMessage.value = if (count > 0) "Loaded $count showcase apps!" else "Store already has apps."
                },
                onFailure = { err -> _statusMessage.value = err.message ?: "Seeding failed" }
            )
        }
    }

    private fun <T> Flow<T>.toUiStateFlow(label: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing $label", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load $label"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private companion object {
        const val TAG = "MarketplaceVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
