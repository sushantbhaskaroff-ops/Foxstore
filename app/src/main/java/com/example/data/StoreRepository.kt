package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    companion object {
        const val ADMIN_EMAIL = "sushantbhaskaroff@gmail.com"
        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
        private val VALID_CATEGORIES = AppCategory.entries.map { it.label }.toSet()
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeApprovedApps(): Flow<List<MarketplaceApp>> = flow {
        requireUserId()
        val path = "apps"
        emitAll(
            db.collection(path)
                .whereEqualTo("status", AppStatus.APPROVED.name)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MarketplaceApp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.downloadsCount }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeMySubmissions(userId: String): Flow<List<MarketplaceApp>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user query." }
        val path = "apps"
        emitAll(
            db.collection(path)
                .whereEqualTo("developerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MarketplaceApp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeAllAppsForAdmin(): Flow<List<MarketplaceApp>> = flow {
        requireUserId()
        val path = "apps"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MarketplaceApp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeInstalledApps(userId: String): Flow<List<InstalledApp>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user query." }
        val path = "users/$userId/installedApps"
        emitAll(
            db.collection("users").document(userId).collection("installedApps")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InstalledApp::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.installedAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeReviews(appId: String): Flow<List<AppReview>> = flow {
        requireUserId()
        require(appId.isNotBlank()) { "App ID required" }
        val path = "apps/$appId/reviews"
        emitAll(
            db.collection("apps").document(appId).collection("reviews")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AppReview::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun fetchMySubmissionsSnapshot(userId: String): List<MarketplaceApp> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        if (uid != userId) return emptyList()
        return try {
            db.collection("apps")
                .whereEqualTo("developerId", userId)
                .get()
                .await()
                .toObjects(
                    MarketplaceApp::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "apps")
            emptyList()
        }
    }

    suspend fun submitApp(input: AppSubmissionInput): Result<String> {
        var docPath = "apps"
        return try {
            val uid = requireUserId()
            val cleanName = input.name.trim()
            val cleanPkg = input.packageName.trim()
            val cleanDev = input.developerName.trim().ifBlank { "Independent Studio" }.take(60)
            val cleanTagline = input.tagline.trim()
            val cleanDesc = input.description.trim()
            val cleanNotes = input.releaseNotes.trim().take(400)
            val cleanCategory = if (input.category in VALID_CATEGORIES) input.category else AppCategory.Productivity.label
            val cleanVer = input.versionName.trim().ifBlank { "1.0.0" }.take(20)
            val cleanSize = input.sizeLabel.trim().ifBlank { "12.0 MB" }.take(20)
            val cleanIcon = input.iconKey.trim().ifBlank { "rocket" }.take(40)
            val cleanHex = if (HEX_REGEX.matches(input.accentHex.trim())) input.accentHex.trim() else "#10B981"
            val cleanSource = if (input.source in setOf("WEBSITE", "STORE_SEED", "STUDIO")) input.source else "WEBSITE"

            require(cleanName.length in 2..30) { "App name must be 2 to 30 characters." }
            require(cleanPkg.length in 3..100 && PACKAGE_REGEX.matches(cleanPkg)) {
                "Package ID must look like com.studio.myapp"
            }
            require(cleanTagline.length in 2..80) { "Tagline must be 2 to 80 characters." }
            require(cleanDesc.length in 10..1000) { "Description must be 10 to 1000 characters." }

            val docRef = db.collection("apps").document()
            docPath = docRef.path
            val payload = mapOf(
                "developerId" to uid,
                "developerName" to cleanDev,
                "name" to cleanName,
                "packageName" to cleanPkg,
                "tagline" to cleanTagline,
                "description" to cleanDesc,
                "releaseNotes" to cleanNotes,
                "category" to cleanCategory,
                "versionName" to cleanVer,
                "sizeLabel" to cleanSize,
                "iconKey" to cleanIcon,
                "accentHex" to cleanHex,
                "status" to AppStatus.PENDING.name,
                "reviewNote" to "",
                "reviewedBy" to "",
                "downloadsCount" to 0L,
                "ratingAvg" to 0.0,
                "ratingCount" to 0L,
                "source" to cleanSource,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun reviewApp(
        appId: String,
        approved: Boolean,
        reviewNote: String,
    ): Result<Unit> {
        val docPath = "apps/$appId"
        return try {
            val uid = requireUserId()
            require(appId.isNotBlank()) { "App ID is required." }
            val nextStatus = if (approved) AppStatus.APPROVED.name else AppStatus.REJECTED.name
            val cleanNote = reviewNote.trim().ifBlank {
                if (approved) "Approved for AppVault Store." else "Needs updates before publishing."
            }.take(300)

            db.collection("apps").document(appId).update(
                mapOf(
                    "status" to nextStatus,
                    "reviewNote" to cleanNote,
                    "reviewedBy" to uid,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteApp(appId: String): Result<Unit> {
        val docPath = "apps/$appId"
        return try {
            requireUserId()
            require(appId.isNotBlank()) { "App ID is required." }
            db.collection("apps").document(appId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun installApp(app: MarketplaceApp): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val installPath = "users/$uid/installedApps/${app.id}"
        return try {
            val authedUid = requireUserId()
            require(app.id.isNotBlank()) { "App ID is required." }
            val batch = db.batch()
            val installRef = db.collection("users").document(authedUid)
                .collection("installedApps").document(app.id)
            batch.set(
                installRef,
                mapOf(
                    "appId" to app.id,
                    "appName" to app.name.take(30),
                    "packageName" to app.packageName.take(100),
                    "versionName" to app.versionName.take(20),
                    "category" to app.category.take(30),
                    "iconKey" to app.iconKey.take(40),
                    "accentHex" to app.accentHex,
                    "installedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            val appRef = db.collection("apps").document(app.id)
            batch.update(
                appRef,
                mapOf(
                    "downloadsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, installPath)
            Result.failure(e)
        }
    }

    suspend fun uninstallApp(appId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val installPath = "users/$uid/installedApps/$appId"
        return try {
            val authedUid = requireUserId()
            require(appId.isNotBlank()) { "App ID is required." }
            db.collection("users").document(authedUid)
                .collection("installedApps").document(appId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, installPath)
            Result.failure(e)
        }
    }

    suspend fun submitReview(
        app: MarketplaceApp,
        rating: Int,
        comment: String,
        authorName: String,
        existingReviews: List<AppReview>,
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: "unauthenticated"
        val reviewPath = "apps/${app.id}/reviews/$uid"
        return try {
            val authedUid = requireUserId()
            val cleanComment = comment.trim()
            require(rating in 1..5) { "Rating must be between 1 and 5." }
            require(cleanComment.length in 1..500) { "Review must be 1 to 500 characters." }

            val existingMine = existingReviews.firstOrNull { it.userId == authedUid }
            val otherReviews = existingReviews.filter { it.userId != authedUid }
            val newCount = (otherReviews.size + 1).toLong()
            val sumRatings = otherReviews.sumOf { it.rating } + rating.toLong()
            val newAvg = (sumRatings.toDouble() / newCount.toDouble())
                .let { Math.round(it * 10.0) / 10.0 }

            val batch = db.batch()
            val reviewRef = db.collection("apps").document(app.id)
                .collection("reviews").document(authedUid)

            if (existingMine == null) {
                batch.set(
                    reviewRef,
                    mapOf(
                        "userId" to authedUid,
                        "authorName" to authorName.trim().ifBlank { "AppVault User" }.take(60),
                        "rating" to rating.toLong(),
                        "comment" to cleanComment,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                batch.update(
                    reviewRef,
                    mapOf(
                        "authorName" to authorName.trim().ifBlank { "AppVault User" }.take(60),
                        "rating" to rating.toLong(),
                        "comment" to cleanComment,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            batch.update(
                db.collection("apps").document(app.id),
                mapOf(
                    "ratingAvg" to newAvg,
                    "ratingCount" to newCount,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, reviewPath)
            Result.failure(e)
        }
    }

    suspend fun seedShowcaseAppsIfEmpty(): Result<Int> {
        return try {
            val uid = requireUserId()
            val existing = db.collection("apps")
                .whereEqualTo("status", AppStatus.APPROVED.name)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) return Result.success(0)

            val seeds = listOf(
                Triple(
                    "seed_pulsefit",
                    "PulseFit Tracker",
                    mapOf(
                        "developerId" to uid,
                        "developerName" to "Kinetic Labs",
                        "name" to "PulseFit Tracker",
                        "packageName" to "com.kinetic.pulsefit",
                        "tagline" to "Smart interval training & heart-rate zones",
                        "description" to "Track runs, HIIT intervals, and recovery streaks with adaptive daily goals, clean visual trends, and offline workout logging.",
                        "releaseNotes" to "Added marathon split charts and dark mode telemetry.",
                        "category" to "Fitness",
                        "versionName" to "3.2.0",
                        "sizeLabel" to "19.4 MB",
                        "iconKey" to "pulsefit",
                        "accentHex" to "#F97316",
                        "status" to AppStatus.APPROVED.name,
                        "reviewNote" to "Editor's Choice verified release.",
                        "reviewedBy" to uid,
                        "downloadsCount" to 1420L,
                        "ratingAvg" to 4.8,
                        "ratingCount" to 12L,
                        "source" to "STORE_SEED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "reviewedAt" to FieldValue.serverTimestamp(),
                    )
                ),
                Triple(
                    "seed_novanotes",
                    "NovaNotes Studio",
                    mapOf(
                        "developerId" to uid,
                        "developerName" to "Origami Software",
                        "name" to "NovaNotes Studio",
                        "packageName" to "com.origami.novanotes",
                        "tagline" to "Markdown notebooks & instant task outlines",
                        "description" to "Capture ideas, technical docs, and daily checklists in distraction-free Markdown folders with lightning-fast search.",
                        "releaseNotes" to "v2.4 adds collapsible code blocks and split tablet pane.",
                        "category" to "Productivity",
                        "versionName" to "2.4.1",
                        "sizeLabel" to "14.1 MB",
                        "iconKey" to "novanotes",
                        "accentHex" to "#8B5CF6",
                        "status" to AppStatus.APPROVED.name,
                        "reviewNote" to "Approved for AppVault Productivity.",
                        "reviewedBy" to uid,
                        "downloadsCount" to 2890L,
                        "ratingAvg" to 4.9,
                        "ratingCount" to 24L,
                        "source" to "STORE_SEED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "reviewedAt" to FieldValue.serverTimestamp(),
                    )
                ),
                Triple(
                    "seed_skylens",
                    "SkyLens Weather",
                    mapOf(
                        "developerId" to uid,
                        "developerName" to "Stratos Systems",
                        "name" to "SkyLens Weather",
                        "packageName" to "com.stratos.skylens",
                        "tagline" to "Hyperlocal radar, UV index & hourly alerts",
                        "description" to "Crisp meteorological radar layers, golden hour timers for photographers, and air-quality forecasts without ad clutter.",
                        "releaseNotes" to "New storm-front animation and widget refresh.",
                        "category" to "Utilities",
                        "versionName" to "4.0.2",
                        "sizeLabel" to "11.8 MB",
                        "iconKey" to "skylens",
                        "accentHex" to "#0284C7",
                        "status" to AppStatus.APPROVED.name,
                        "reviewNote" to "Verified clean weather data feeds.",
                        "reviewedBy" to uid,
                        "downloadsCount" to 3510L,
                        "ratingAvg" to 4.7,
                        "ratingCount" to 19L,
                        "source" to "STORE_SEED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "reviewedAt" to FieldValue.serverTimestamp(),
                    )
                ),
                Triple(
                    "seed_vaultcalc",
                    "VaultCalc Pro",
                    mapOf(
                        "developerId" to uid,
                        "developerName" to "FinCraft Studio",
                        "name" to "VaultCalc Pro",
                        "packageName" to "com.fincraft.vaultcalc",
                        "tagline" to "Compound interest, SIP & tax estimator",
                        "description" to "Plan savings targets, EMI schedules, and currency conversions with precision financial formulas and clean exportable breakdowns.",
                        "releaseNotes" to "Added multi-currency tax bracket presets.",
                        "category" to "Finance",
                        "versionName" to "1.9.0",
                        "sizeLabel" to "8.6 MB",
                        "iconKey" to "vaultcalc",
                        "accentHex" to "#10B981",
                        "status" to AppStatus.APPROVED.name,
                        "reviewNote" to "Approved for Finance.",
                        "reviewedBy" to uid,
                        "downloadsCount" to 960L,
                        "ratingAvg" to 4.6,
                        "ratingCount" to 8L,
                        "source" to "STORE_SEED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "reviewedAt" to FieldValue.serverTimestamp(),
                    )
                )
            )

            val batch = db.batch()
            seeds.forEach { (docId, _, data) ->
                batch.set(db.collection("apps").document(docId), data)
            }
            batch.commit().await()
            Result.success(seeds.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "apps")
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }
}
