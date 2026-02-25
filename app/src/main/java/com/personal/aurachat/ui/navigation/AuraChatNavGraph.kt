package com.personal.aurachat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.aurachat.AppContainer
import com.personal.aurachat.presentation.chat.ChatViewModel
import com.personal.aurachat.presentation.home.HomeViewModel
import com.personal.aurachat.presentation.settings.SettingsViewModel
import com.personal.aurachat.ui.chat.ChatScreen
import com.personal.aurachat.ui.home.HomeScreen
import com.personal.aurachat.ui.settings.SettingsScreen

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val CHAT_ROUTE = "chat/{conversationId}"

@Composable
fun AuraChatNavGraph(
    appContainer: AppContainer
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = HOME_ROUTE
    ) {
        composable(HOME_ROUTE) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(appContainer.conversationRepository)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                onNewChat = { navController.navigate("chat/-1") },
                onOpenConversation = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onRenameConversation = viewModel::renameConversation,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) }
            )
        }

        composable(
            route = CHAT_ROUTE,
            arguments = listOf(
                navArgument("conversationId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: -1L

            val viewModel: ChatViewModel = viewModel(
                key = "chat_$conversationId",
                factory = ChatViewModel.factory(
                    conversationRepository = appContainer.conversationRepository,
                    networkMonitor = appContainer.networkMonitor,
                    initialConversationId = conversationId.takeIf { it > 0 }
                )
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            ChatScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onSendMessage = { message, onResult ->
                    viewModel.requestSendMessage(message, onResult)
                },
                onRetryFailed = viewModel::requestRetryLastFailed,
                onDismissOfflineNotice = viewModel::clearOfflineNotice
            )
        }

        composable(SETTINGS_ROUTE) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(appContainer.settingsRepository)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onApiKeyChanged = viewModel::onApiKeyChanged,
                onTimeoutSelected = viewModel::onTimeoutSelected,
                onSave = viewModel::saveSettings,
                onClearMessage = viewModel::clearSaveMessage
            )
        }
    }
}
