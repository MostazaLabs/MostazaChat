package com.example.ui.navigation

import android.app.Application
import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.ExternalSessionConfig
import com.example.auth.ExternalSessionStore
import com.example.auth.authStateFlow
import com.example.auth.firebaseAuthOrNull
import com.example.data.ChatDataRepository
import com.example.data.firestore.ChatMockRepository
import com.example.data.local.LocalChatMockRepository
import com.example.ui.screens.CallsTabScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.ChatsListScreen
import com.example.ui.screens.CloudSettingsScreen
import com.example.ui.screens.FakeCallOverlayScreen
import com.example.ui.viewmodel.CallsViewModel
import com.example.ui.viewmodel.ChatRoomViewModel
import com.example.ui.viewmodel.ChatsHomeViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val ROUTE_CHATS = "chats"
private const val ROUTE_CALLS = "calls"
private const val ROUTE_CLOUD = "cloud"
private const val ROUTE_CHAT_ROOM = "chat/{chatId}"

private const val SESSION_PREFS_NAME = "mostazachat_session_prefs"
private const val KEY_IS_GUEST_MODE = "is_guest_mode"

private data class TopTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private fun buildTopTabs(isGuestMode: Boolean, localLabel: String = "Local"): List<TopTab> = listOf(
    TopTab(ROUTE_CHATS, "Chats", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "tab_chats"),
    TopTab(ROUTE_CALLS, "Llamadas", Icons.Rounded.Call, Icons.Outlined.Call, "tab_calls"),
    if (isGuestMode) {
        TopTab(ROUTE_CLOUD, localLabel, Icons.Rounded.PhoneAndroid, Icons.Outlined.PhoneAndroid, "tab_cloud")
    } else {
        TopTab(ROUTE_CLOUD, "Nube", Icons.Rounded.CloudDone, Icons.Outlined.CloudQueue, "tab_cloud")
    }
)

@Composable
fun ChatMockApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(SESSION_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var isGuestMode by remember { mutableStateOf(prefs.getBoolean(KEY_IS_GUEST_MODE, false)) }
    var externalSession by remember { mutableStateOf(ExternalSessionStore.load(context)) }
    var pendingAutoImportConfig by remember { mutableStateOf<ExternalSessionConfig?>(null) }

    val auth = remember(context) { firebaseAuthOrNull(context) }
    val currentUser by (auth?.authStateFlow() ?: remember { flowOf(null) })
        .collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    val user = currentUser

    LaunchedEffect(user?.uid) {
        if (user != null && isGuestMode) {
            isGuestMode = false
            prefs.edit().putBoolean(KEY_IS_GUEST_MODE, false).apply()
        }
    }

    val isExternalActive = externalSession?.active == true

    when {
        user != null -> {
            key("cloud_${user.uid}") {
                ChatMockWorkspaceRoot(
                    isGuestMode = false,
                    currentUserId = user.uid,
                    userDisplayName = user.displayName ?: "Usuario",
                    userEmail = user.email,
                    externalSession = externalSession,
                    pendingAutoImportConfig = null,
                    onConsumePendingAutoImport = {},
                    onUpdateExternalSession = { updated, _ ->
                        ExternalSessionStore.save(context, updated)
                        externalSession = updated
                    },
                    onExitGuestMode = {}
                )
            }
        }
        isGuestMode || isExternalActive -> {
            val activeExt = externalSession?.takeIf { it.active }
            val displayName = when {
                activeExt != null && activeExt.accountLabel.isNotBlank() -> activeExt.accountLabel
                activeExt != null -> activeExt.provider.displayName
                else -> "Invitado local"
            }
            key("local_guest") {
                ChatMockWorkspaceRoot(
                    isGuestMode = true,
                    currentUserId = LocalChatMockRepository.DEFAULT_GUEST_USER_ID,
                    userDisplayName = displayName,
                    userEmail = null,
                    externalSession = activeExt,
                    pendingAutoImportConfig = pendingAutoImportConfig,
                    onConsumePendingAutoImport = { pendingAutoImportConfig = null },
                    onUpdateExternalSession = { updated, _ ->
                        ExternalSessionStore.save(context, updated)
                        externalSession = updated
                    },
                    onExitGuestMode = {
                        isGuestMode = false
                        pendingAutoImportConfig = null
                        prefs.edit().putBoolean(KEY_IS_GUEST_MODE, false).apply()
                        ExternalSessionStore.deactivateSession(context)
                        externalSession = externalSession?.copy(active = false)
                    }
                )
            }
        }
        else -> {
            AuthScreen(
                onAuthSuccess = {
                    isGuestMode = false
                    prefs.edit().putBoolean(KEY_IS_GUEST_MODE, false).apply()
                },
                onContinueAsGuest = {
                    ExternalSessionStore.deactivateSession(context)
                    externalSession = externalSession?.copy(active = false)
                    isGuestMode = true
                    prefs.edit().putBoolean(KEY_IS_GUEST_MODE, true).apply()
                },
                onExternalProviderSelected = { config, importImmediately ->
                    ExternalSessionStore.save(context, config)
                    externalSession = config
                    isGuestMode = true
                    prefs.edit().putBoolean(KEY_IS_GUEST_MODE, true).apply()
                    pendingAutoImportConfig = if (importImmediately) config else null
                }
            )
        }
    }
}

private fun createRepository(app: Application, isGuestMode: Boolean): ChatDataRepository {
    return if (isGuestMode) {
        LocalChatMockRepository(app)
    } else {
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        ChatMockRepository(db)
    }
}

@Composable
private fun ChatMockWorkspaceRoot(
    isGuestMode: Boolean,
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    externalSession: ExternalSessionConfig?,
    pendingAutoImportConfig: ExternalSessionConfig?,
    onConsumePendingAutoImport: () -> Unit,
    onUpdateExternalSession: (config: ExternalSessionConfig, importImmediately: Boolean) -> Unit,
    onExitGuestMode: () -> Unit
) {
    val context = LocalContext.current
    val scopeKey = if (isGuestMode) "local_$currentUserId" else "cloud_$currentUserId"

    val homeViewModel: ChatsHomeViewModel = viewModel(
        key = "home_$scopeKey",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                ChatsHomeViewModel(
                    repository = createRepository(app, isGuestMode),
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName,
                    initialEmail = userEmail
                )
            }
        }
    )

    val callsViewModel: CallsViewModel = viewModel(
        key = "calls_$scopeKey",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                CallsViewModel(
                    repository = createRepository(app, isGuestMode),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val localBadgeLabel = externalSession?.takeIf { it.active }?.shortTagLabel ?: "Local"
    val topTabs = remember(isGuestMode, localBadgeLabel) {
        buildTopTabs(isGuestMode = isGuestMode, localLabel = localBadgeLabel)
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomOrRail = currentDestination?.route in topTabs.map { it.route }

    val userProfile by homeViewModel.userProfile.collectAsStateWithLifecycle()
    val statusMessage by homeViewModel.statusMessage.collectAsStateWithLifecycle()
    val callStatusMessage by callsViewModel.statusMessage.collectAsStateWithLifecycle()
    val activeCall by callsViewModel.activeCall.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pendingAutoImportConfig) {
        val pending = pendingAutoImportConfig
        if (pending != null) {
            onConsumePendingAutoImport()
            homeViewModel.importFromExternalSession(
                context = context,
                config = pending,
                onImportedChat = { chatId ->
                    navController.navigate("chat/$chatId")
                }
            )
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            homeViewModel.clearStatusMessage()
        }
    }
    LaunchedEffect(callStatusMessage) {
        callStatusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            callsViewModel.clearStatusMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomOrRail && !useNavRail) {
                        NavigationBar {
                            topTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showBottomOrRail && useNavRail) {
                        NavigationRail {
                            topTabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_CHATS,
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(ROUTE_CHATS) {
                            ChatsListScreen(
                                viewModel = homeViewModel,
                                isGuestMode = isGuestMode,
                                localModeLabel = localBadgeLabel,
                                onOpenChat = { chatId ->
                                    navController.navigate("chat/$chatId")
                                },
                                onStartFakeCall = { chat, callType ->
                                    callsViewModel.launchFakeCall(
                                        contactName = chat.contactName,
                                        avatarEmoji = chat.avatarEmoji,
                                        avatarColorHex = chat.avatarColorHex,
                                        avatarImageBase64 = chat.avatarImageBase64,
                                        callType = callType,
                                        delaySeconds = 0
                                    )
                                }
                            )
                        }
                        composable(ROUTE_CALLS) {
                            CallsTabScreen(viewModel = callsViewModel)
                        }
                        composable(ROUTE_CLOUD) {
                            CloudSettingsScreen(
                                viewModel = homeViewModel,
                                userEmail = userEmail,
                                userDisplayName = userDisplayName,
                                isGuestMode = isGuestMode,
                                externalSession = externalSession,
                                onUpdateExternalSession = onUpdateExternalSession,
                                onExitGuestMode = onExitGuestMode,
                                onOpenChat = { chatId ->
                                    navController.navigate("chat/$chatId")
                                },
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        composable(
                            route = ROUTE_CHAT_ROOM,
                            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                        ) { entry ->
                            val chatId = entry.arguments?.getString("chatId").orEmpty()
                            val roomViewModel: ChatRoomViewModel = viewModel(
                                key = "room_${scopeKey}_$chatId",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY]) as Application
                                        ChatRoomViewModel(
                                            repository = createRepository(app, isGuestMode),
                                            currentUserId = currentUserId,
                                            chatId = chatId
                                        )
                                    }
                                }
                            )
                            ChatRoomScreen(
                                viewModel = roomViewModel,
                                myDefaultName = userProfile?.defaultMyName ?: "Yo",
                                onBack = { navController.popBackStack() },
                                onStartFakeCall = { chat, callType ->
                                    callsViewModel.launchFakeCall(
                                        contactName = chat.contactName,
                                        avatarEmoji = chat.avatarEmoji,
                                        avatarColorHex = chat.avatarColorHex,
                                        avatarImageBase64 = chat.avatarImageBase64,
                                        callType = callType,
                                        delaySeconds = 0
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        activeCall?.let { currentCall ->
            FakeCallOverlayScreen(
                call = currentCall,
                onAnswer = { callsViewModel.answerActiveCall() },
                onEndCall = { durationSec -> callsViewModel.endActiveCall(durationSec) }
            )
        }
    }
}
