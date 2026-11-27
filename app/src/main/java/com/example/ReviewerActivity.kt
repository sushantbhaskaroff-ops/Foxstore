package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.MarketplaceApp
import com.example.data.StoreRepository
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ReviewerConsoleScreen
import com.example.ui.theme.AppVaultTheme
import com.example.viewmodel.MarketplaceViewModel
import com.example.viewmodel.UiState
import com.example.web.LocalWebPortalServer
import com.example.web.PortalServerManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class ReviewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppVaultTheme {
                ReviewerAppRoot()
            }
        }
    }
}

@Composable
private fun ReviewerAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        key(user.uid) {
            ReviewerAppContent(
                user = user,
                onSignOut = {
                    signOut(credentialManager, scope) {
                        currentUser = null
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewerAppContent(
    user: FirebaseUser,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val uid = user.uid
    val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Reviewer"
    val email = user.email ?: ""
    val isAdmin = remember(email, user.isEmailVerified) {
        email.equals(StoreRepository.ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    val viewModel: MarketplaceViewModel = viewModel(
        key = "reviewer_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MarketplaceViewModel(
                    repository = StoreRepository(db),
                    currentUserId = uid,
                    currentUserName = displayName,
                    currentUserEmail = email,
                    isAdmin = isAdmin,
                )
            }
        }
    )

    DisposableEffect(uid) {
        PortalServerManager.acquire(
            context = context,
            userId = uid,
            developerName = displayName,
            onAppUploaded = { name ->
                viewModel.notifyMessage("New upload from website: \"$name\" ready for review!")
            },
        )
        onDispose { PortalServerManager.release() }
    }

    val adminAppsState by viewModel.adminAppsState.collectAsStateWithLifecycle()
    val installedAppsState by viewModel.installedAppsState.collectAsStateWithLifecycle()
    val reviewsState by viewModel.selectedAppReviewsState.collectAsStateWithLifecycle()
    val installingAppIds by viewModel.installingAppIds.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusMessage) {
        val msg = statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    var inspectedApp by remember { mutableStateOf<MarketplaceApp?>(null) }
    val installedIds = remember(installedAppsState) {
        ((installedAppsState as? UiState.Success)?.data ?: emptyList()).map { it.appId }.toSet()
    }

    if (inspectedApp != null) {
        androidx.activity.compose.BackHandler {
            inspectedApp = null
            viewModel.selectAppForDetail(null)
        }
        AppDetailScreen(
            app = inspectedApp,
            isInstalled = inspectedApp!!.id in installedIds,
            isInstalling = inspectedApp!!.id in installingAppIds,
            reviewsState = reviewsState,
            currentUserId = uid,
            onBack = {
                inspectedApp = null
                viewModel.selectAppForDetail(null)
            },
            onInstall = { target -> viewModel.installApp(target) },
            onUninstall = { target -> viewModel.uninstallApp(target.id, target.name) },
            onOpenApp = { },
            onSubmitReview = { target, rating, comment, existing, onDone ->
                viewModel.submitReview(target, rating, comment, existing, onDone)
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "App Reviewer",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LocalWebPortalServer.URL))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.testTag("reviewer_open_website_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Open Upload Website",
                        )
                    }
                    IconButton(
                        onClick = {
                            val intent = Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.testTag("reviewer_open_store_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "Open AppVault Store",
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("reviewer_sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ReviewerConsoleScreen(
                appsState = adminAppsState,
                isAdmin = isAdmin,
                currentUserEmail = email,
                isBusy = isBusy,
                onApprove = { app, note -> viewModel.approveApp(app, note) },
                onReject = { app, reason -> viewModel.rejectApp(app, reason) },
                onOpenDetail = { app ->
                    viewModel.selectAppForDetail(app.id)
                    inspectedApp = app
                },
            )
        }
    }
}
