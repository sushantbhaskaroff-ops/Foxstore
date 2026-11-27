package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.attemptAutoSignIn
import com.example.auth.signOut
import com.example.data.AppStatus
import com.example.data.MarketplaceApp
import com.example.data.StoreRepository
import com.example.ui.components.InteractiveAppLaunchDialog
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ReviewerConsoleScreen
import com.example.ui.screens.StorefrontScreen
import com.example.ui.screens.WebPortalScreen
import com.example.ui.theme.AppVaultTheme
import com.example.viewmodel.MarketplaceViewModel
import com.example.viewmodel.UiState
import com.example.web.PortalServerManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object AppRoutes {
    const val STORE = "store"
    const val WEBSITE = "website"
    const val REVIEWER = "reviewer"
    const val LIBRARY = "library"
    const val DETAIL = "app_detail/{appId}"
    fun detail(appId: String) = "app_detail/$appId"
}

private data class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

class MainActivity : ComponentActivity() {
    private var initialStartTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initialStartTab = intent?.getStringExtra("start_tab")
        setContent {
            AppVaultTheme {
                AppContent(initialStartTab = initialStartTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initialStartTab = intent.getStringExtra("start_tab")
    }
}

@Composable
fun AppContent(initialStartTab: String?) {
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
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedAppSuite(
                user = user,
                initialStartTab = initialStartTab,
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
private fun AuthenticatedAppSuite(
    user: FirebaseUser,
    initialStartTab: String?,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val uid = user.uid
    val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Developer"
    val email = user.email ?: ""
    val isAdmin = remember(email, user.isEmailVerified) {
        email.equals(StoreRepository.ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    val viewModel: MarketplaceViewModel = viewModel(
        key = uid,
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

    // Start shared embedded Developer Upload Website server bound to 127.0.0.1:8765
    DisposableEffect(uid) {
        PortalServerManager.acquire(
            context = context,
            userId = uid,
            developerName = displayName,
            onAppUploaded = { appName ->
                viewModel.notifyMessage("\"$appName\" uploaded from website! Open App Reviewer to approve.")
            },
        )
        onDispose { PortalServerManager.release() }
    }

    val approvedAppsState by viewModel.approvedAppsState.collectAsStateWithLifecycle()
    val mySubmissionsState by viewModel.mySubmissionsState.collectAsStateWithLifecycle()
    val installedAppsState by viewModel.installedAppsState.collectAsStateWithLifecycle()
    val adminAppsState by viewModel.adminAppsState.collectAsStateWithLifecycle()
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

    var launchedApp by remember { mutableStateOf<MarketplaceApp?>(null) }

    val navController = rememberNavController()
    LaunchedEffect(initialStartTab) {
        when (initialStartTab) {
            "website" -> navController.navigate(AppRoutes.WEBSITE) { launchSingleTop = true }
            "reviewer" -> navController.navigate(AppRoutes.REVIEWER) { launchSingleTop = true }
            "store" -> navController.navigate(AppRoutes.STORE) { launchSingleTop = true }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.STORE
    val isDetailRoute = currentRoute.startsWith("app_detail/")

    val tabs = remember {
        listOf(
            TopTab(AppRoutes.STORE, R.string.nav_store, Icons.Filled.Storefront, Icons.Outlined.Storefront, "tab_store"),
            TopTab(AppRoutes.WEBSITE, R.string.nav_website, Icons.Filled.Language, Icons.Outlined.Language, "tab_website"),
            TopTab(AppRoutes.REVIEWER, R.string.nav_reviewer, Icons.Filled.VerifiedUser, Icons.Outlined.VerifiedUser, "tab_reviewer"),
            TopTab(AppRoutes.LIBRARY, R.string.nav_library, Icons.Filled.Apps, Icons.Outlined.Apps, "tab_library"),
        )
    }

    val pendingCount = remember(adminAppsState) {
        (adminAppsState as? UiState.Success)?.data?.count { it.statusEnum() == AppStatus.PENDING } ?: 0
    }

    val approvedList = (approvedAppsState as? UiState.Success)?.data ?: emptyList()
    val adminList = (adminAppsState as? UiState.Success)?.data ?: emptyList()
    val mySubList = (mySubmissionsState as? UiState.Success)?.data ?: emptyList()
    val installedList = (installedAppsState as? UiState.Success)?.data ?: emptyList()
    val installedIds = remember(installedList) { installedList.map { it.appId }.toSet() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (!isDetailRoute) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = when (currentRoute) {
                                    AppRoutes.STORE -> "AppVault Play Store"
                                    AppRoutes.WEBSITE -> "Developer Upload Website"
                                    AppRoutes.REVIEWER -> "AppVault Reviewer"
                                    AppRoutes.LIBRARY -> "My Library"
                                    else -> stringResource(R.string.app_name)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        actions = {
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    val intent = Intent(context, ReviewerActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.testTag("launch_reviewer_app_button"),
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (pendingCount > 0) {
                                            Badge { Text(pendingCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = "Open App Reviewer",
                                    )
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(AppRoutes.STORE) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    if (tab.route == AppRoutes.REVIEWER && pendingCount > 0) {
                                        BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes),
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes),
                                        )
                                    }
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(AppRoutes.STORE) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    if (tab.route == AppRoutes.REVIEWER && pendingCount > 0) {
                                        BadgedBox(badge = { Badge { Text(pendingCount.toString()) } }) {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes),
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes),
                                        )
                                    }
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.STORE,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 960.dp)
                        .align(Alignment.Top),
                ) {
                    composable(AppRoutes.STORE) {
                        StorefrontScreen(
                            appsState = approvedAppsState,
                            installedApps = installedList,
                            installingAppIds = installingAppIds,
                            isAdmin = isAdmin,
                            onAppClick = { app ->
                                viewModel.selectAppForDetail(app.id)
                                navController.navigate(AppRoutes.detail(app.id))
                            },
                            onInstallClick = { app -> viewModel.installApp(app) },
                            onOpenInstalledApp = { app -> launchedApp = app },
                            onGoToWebsite = {
                                navController.navigate(AppRoutes.WEBSITE) { launchSingleTop = true }
                            },
                            onSeedShowcaseApps = { viewModel.seedShowcaseApps() },
                        )
                    }

                    composable(AppRoutes.WEBSITE) {
                        WebPortalScreen()
                    }

                    composable(AppRoutes.REVIEWER) {
                        ReviewerConsoleScreen(
                            appsState = adminAppsState,
                            isAdmin = isAdmin,
                            currentUserEmail = email,
                            isBusy = isBusy,
                            onApprove = { app, note -> viewModel.approveApp(app, note) },
                            onReject = { app, reason -> viewModel.rejectApp(app, reason) },
                            onOpenDetail = { app ->
                                viewModel.selectAppForDetail(app.id)
                                navController.navigate(AppRoutes.detail(app.id))
                            },
                        )
                    }

                    composable(AppRoutes.LIBRARY) {
                        LibraryScreen(
                            userName = displayName,
                            userEmail = email,
                            isAdmin = isAdmin,
                            installedAppsState = installedAppsState,
                            mySubmissionsState = mySubmissionsState,
                            approvedApps = approvedList,
                            onOpenInstalled = { app -> launchedApp = app },
                            onUninstall = { id, name -> viewModel.uninstallApp(id, name) },
                            onDeleteSubmission = { app -> viewModel.deleteSubmission(app) },
                            onGoToWebsite = {
                                navController.navigate(AppRoutes.WEBSITE) { launchSingleTop = true }
                            },
                            onSignOut = onSignOut,
                        )
                    }

                    composable(
                        route = AppRoutes.DETAIL,
                        arguments = listOf(navArgument("appId") { type = NavType.StringType }),
                    ) { entry ->
                        val appId = entry.arguments?.getString("appId") ?: ""
                        val app = remember(appId, approvedList, adminList, mySubList) {
                            (approvedList + adminList + mySubList).firstOrNull { it.id == appId }
                        }
                        AppDetailScreen(
                            app = app,
                            isInstalled = appId in installedIds,
                            isInstalling = appId in installingAppIds,
                            reviewsState = reviewsState,
                            currentUserId = uid,
                            onBack = { navController.popBackStack() },
                            onInstall = { target -> viewModel.installApp(target) },
                            onUninstall = { target -> viewModel.uninstallApp(target.id, target.name) },
                            onOpenApp = { target -> launchedApp = target },
                            onSubmitReview = { target, rating, comment, existing, onDone ->
                                viewModel.submitReview(target, rating, comment, existing, onDone)
                            },
                        )
                    }
                }
            }
        }
    }

    launchedApp?.let { app ->
        InteractiveAppLaunchDialog(
            app = app,
            onDismiss = { launchedApp = null },
        )
    }
}
