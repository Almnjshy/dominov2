package com.agon.app.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.agon.app.domain.model.GameMode
import com.agon.app.presentation.screens.*
import com.agon.app.presentation.viewmodel.*

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Menu : Screen("menu")
    data object Game : Screen("game/{mode}") {
        fun createRoute(mode: GameMode) = "game/${mode.name}"
    }
    data object Network : Screen("network")
    data object Settings : Screen("settings")
    data object Stats : Screen("stats")
    data object Verify : Screen("verify")
    data object Demo : Screen("demo")
}

@Composable
fun DominoNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {

    NavHost(
        navController = navController,
        startDestination = startDestination,

        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        tween(300)
                    )
        },

        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        tween(300)
                    )
        },

        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        tween(300)
                    )
        },

        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        tween(300)
                    )
        }
    ) {

        // ───── SPLASH ─────
        composable(Screen.Splash.route) {
            val vm: SplashViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            SplashScreen(
                progress = state.progress,
                isLoading = state.isLoading,
                error = state.error,
                onReady = {
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onRetry = { vm.retry() }
            )
        }

        // ───── MENU ─────
        composable(Screen.Menu.route) {
            val vm: MenuViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            MainMenuScreen(
                selectedMode = state.selectedMode,
                isLoading = state.isLoading,
                error = state.error,
                onModeSelected = vm::selectMode,
                onNewGame = {
                    navController.navigate(Screen.Game.createRoute(state.selectedMode))
                },
                onNetwork = { navController.navigate(Screen.Network.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onStats = { navController.navigate(Screen.Stats.route) },
                onVerify = { navController.navigate(Screen.Verify.route) },
                onDemo = { navController.navigate(Screen.Demo.route) },
                onClearError = vm::clearError
            )
        }

        // ───── GAME ─────
        composable(
            route = Screen.Game.route,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = GameMode.HUMAN_VS_AI.name
                }
            )
        ) { entry ->

            val mode = try {
                GameMode.valueOf(entry.arguments?.getString("mode") ?: "")
            } catch (e: Exception) {
                GameMode.HUMAN_VS_AI
            }

            val vm: GameViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(mode) {
                vm.newGame(mode)
            }

            val gameState = state.gameState

            GameScreen(
                gameState = gameState,
                isAiThinking = state.isAiThinking,

                // ✅ FIX: بدل showResult الغير موجود
                showResult = state.roundResult != null,

                error = state.error,

                onTileClick = vm::playTile,
                onDrawOrPass = vm::drawOrPass,
                legalSides = vm::getLegalSides,
                onNewGame = { vm.newGame(mode) },

                onBackToMenu = {
                    navController.navigate(Screen.Menu.route) {
                        popUpTo(Screen.Menu.route) { inclusive = false }
                    }
                },

                onDismissResult = {
                    vm.dismissRoundResult()

                    // حماية من crash
                    val gs = vm.uiState.value.gameState
                    if (gs.isGameOver && !gs.isMatchOver) {
                        vm.newRound()
                    }
                },

                onClearError = vm::clearError
            )
        }

        // ───── NETWORK ─────
        composable(Screen.Network.route) {
            val vm: NetworkViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            NetworkScreen(
                networkState = state.networkState,
                discoveredRooms = state.discoveredRooms,
                isLoading = state.isLoading,
                error = state.error,
                showCreateDialog = state.showCreateDialog,
                onCreateRoom = vm::createRoom,
                onDiscover = vm::discoverRooms,
                onJoinRoom = vm::joinRoom,
                onLeaveRoom = vm::leaveRoom,
                onShowCreateDialog = vm::showCreateDialog,
                onDismissCreateDialog = vm::dismissCreateDialog,
                onBack = { navController.popBackStack() },
                onClearError = vm::clearError
            )
        }

        // ───── SETTINGS ─────
        composable(Screen.Settings.route) {
            val vm: SettingsViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            SettingsScreen(
                settings = state.settings,
                hasChanges = state.hasChanges,
                isLoading = state.isLoading,
                error = state.error,
                showResetConfirmation = state.showResetConfirmation,
                saveSuccess = state.saveSuccess,

                onVolumeChange = vm::updateVolume,
                onEffectsToggle = vm::toggleEffects,
                onVibrationToggle = vm::toggleVibration,
                onLanguageChange = vm::setLanguage,
                onModeChange = vm::setPreferredMode,
                onSave = vm::saveSettings,
                onReset = vm::resetSettings,
                onShowReset = vm::showResetConfirmation,
                onDismissReset = vm::dismissResetConfirmation,
                onDismissSaveSuccess = vm::dismissSaveSuccess,

                onBack = { navController.popBackStack() },
                onClearError = vm::clearError
            )
        }

        // ───── STATS ─────
        composable(Screen.Stats.route) {
            val vm: StatsViewModel = hiltViewModel()
            val state by vm.uiState.collectAsStateWithLifecycle()

            StatsScreen(
                stats = state.stats,
                achievements = state.achievements,
                isLoading = state.isLoading,
                error = state.error,

                showClearConfirmation = state.showClearConfirmation,
                exportedJson = state.exportedJson,
                showExportDialog = state.showExportDialog,

                onClearStats = vm::clearStats,
                onExport = vm::exportStats,
                onRefresh = vm::loadStats,

                onBack = { navController.popBackStack() },
                onClearError = vm::clearError
            )
        }

        // ───── DEMO ─────
        composable(Screen.Demo.route) {
            DemoScreen(
                onStartAi = {
                    navController.navigate(Screen.Game.createRoute(GameMode.HUMAN_VS_AI))
                },
                onCreateNetworkRoom = {
                    navController.navigate(Screen.Network.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ───── VERIFY ─────
        composable(Screen.Verify.route) {
            VerificationScreen(
                onStartAi = {
                    navController.navigate(Screen.Game.createRoute(GameMode.HUMAN_VS_AI))
                },
                onNetwork = { navController.navigate(Screen.Network.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onStats = { navController.navigate(Screen.Stats.route) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}