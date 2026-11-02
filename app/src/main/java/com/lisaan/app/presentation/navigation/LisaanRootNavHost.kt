package com.lisaan.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import androidx.compose.ui.unit.dp
@Composable
fun LisaanRootNavHost(
    rootNavController: NavHostController,
    startDestination: String,
    onOnboardingCompleted: () -> Unit,
    onAuthCompleted: () -> Unit,
    // Callbacks or Screen Slots injected cleanly across modules/phases
    onboardingFlowComposable: @Composable (NavHostController) -> Unit,
    authFlowComposable: @Composable (NavHostController) -> Unit,
    homeTabComposable: @Composable (onStartLesson: (String) -> Unit, onOpenMiniGames: () -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    reviewTabComposable: @Composable (onStartSm2: () -> Unit, onOpenDictionary: () -> Unit, onOpenFlashcards: () -> Unit, onOpenMiniGames: () -> Unit) -> Unit,
    aiCoachTabComposable: @Composable (onSelectScenario: (String) -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    communityTabComposable: @Composable (onCreatePost: () -> Unit, onOpenPost: (String) -> Unit, onOpenLeaderboard: () -> Unit, onOpenAchievements: () -> Unit) -> Unit,
    profileTabComposable: @Composable (onOpenSettings: () -> Unit, onOpenPaywall: (String) -> Unit, onOpenAchievements: () -> Unit) -> Unit,
    lessonPlayerComposable: @Composable (lessonId: String, onCloseLesson: () -> Unit) -> Unit,
    secondaryRouteComposable: @Composable (route: String, args: android.os.Bundle?, onBack: () -> Unit) -> Unit
) {
    NavHost(
        navController = rootNavController,
        startDestination = startDestination
    ) {
        // 1. Onboarding Flow
        composable(route = LisaanRoute.OnboardingGraph.route) {
            onboardingFlowComposable(rootNavController)
        }

        // 2. Auth Flow
        composable(route = LisaanRoute.AuthGraph.route) {
            authFlowComposable(rootNavController)
        }

        // 3. Main 5-Tab Scaffold
        composable(route = LisaanRoute.MainScaffold.route) {
            LisaanMainScaffold(
                onNavigateToLesson = { lessonId ->
                    rootNavController.navigate(LisaanRoute.LessonPlayer.createRoute(lessonId))
                },
                onNavigateToSecondary = { targetRoute ->
                    rootNavController.navigate(targetRoute)
                },
                homeTabContent = homeTabComposable,
                reviewTabContent = reviewTabComposable,
                aiCoachTabContent = aiCoachTabComposable,
                communityTabContent = communityTabComposable,
                profileTabContent = profileTabComposable
            )
        }

        // 4. Full-Screen Lesson Player
        composable(
            route = LisaanRoute.LessonPlayer.route,
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId").orEmpty()
            lessonPlayerComposable(lessonId) {
                rootNavController.popBackStack()
            }
        }

        // 5. Secondary Screens (SM-2 Session, Dictionary, Flashcards, Mini-games, AI Chat, Community Detail, Leaderboard, Settings, Paywall)
        val secondaryRoutes = listOf(
            LisaanRoute.Sm2Session.route,
            LisaanRoute.Dictionary.route,
            LisaanRoute.FlashcardsDeck.route,
            LisaanRoute.MiniGamesHub.route,
            LisaanRoute.SpeedMatchGame.route,
            LisaanRoute.WordSprintGame.route,
            LisaanRoute.GuessPictureGame.route,
            LisaanRoute.AiChatSession.route,
            LisaanRoute.CreateCommunityPost.route,
            LisaanRoute.CommunityPostDetail.route,
            LisaanRoute.Leaderboard.route,
            LisaanRoute.Achievements.route,
            LisaanRoute.Settings.route,
            LisaanRoute.Paywall.route
        )

        secondaryRoutes.forEach { routePattern ->
            composable(route = routePattern) { entry ->
                secondaryRouteComposable(routePattern, entry.arguments) {
                    rootNavController.popBackStack()
                }
            }
        }
    }
}

@Composable
fun LisaanMainScaffold(
    onNavigateToLesson: (String) -> Unit,
    onNavigateToSecondary: (String) -> Unit,
    homeTabContent: @Composable (onStartLesson: (String) -> Unit, onOpenMiniGames: () -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    reviewTabContent: @Composable (onStartSm2: () -> Unit, onOpenDictionary: () -> Unit, onOpenFlashcards: () -> Unit, onOpenMiniGames: () -> Unit) -> Unit,
    aiCoachTabContent: @Composable (onSelectScenario: (String) -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    communityTabContent: @Composable (onCreatePost: () -> Unit, onOpenPost: (String) -> Unit, onOpenLeaderboard: () -> Unit, onOpenAchievements: () -> Unit) -> Unit,
    profileTabContent: @Composable (onOpenSettings: () -> Unit, onOpenPaywall: (String) -> Unit, onOpenAchievements: () -> Unit) -> Unit
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isArabic = LocalAppLanguage.current == "ar"

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                MainBottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    val label = if (isArabic) item.titleAr else item.titleEn
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                tabNavController.navigate(item.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = tabNavController,
                startDestination = LisaanRoute.HomePathTab.route
            ) {
                composable(LisaanRoute.HomePathTab.route) {
                    homeTabContent(
                        onNavigateToLesson,
                        { onNavigateToSecondary(LisaanRoute.MiniGamesHub.route) },
                        { source -> onNavigateToSecondary(LisaanRoute.Paywall.createRoute(source)) }
                    )
                }
                composable(LisaanRoute.SmartReviewTab.route) {
                    reviewTabContent(
                        { onNavigateToSecondary(LisaanRoute.Sm2Session.route) },
                        { onNavigateToSecondary(LisaanRoute.Dictionary.route) },
                        { onNavigateToSecondary(LisaanRoute.FlashcardsDeck.route) },
                        { onNavigateToSecondary(LisaanRoute.MiniGamesHub.route) }
                    )
                }
                composable(LisaanRoute.AiCoachTab.route) {
                    aiCoachTabContent(
                        { scenarioId -> onNavigateToSecondary(LisaanRoute.AiChatSession.createRoute(scenarioId)) },
                        { source -> onNavigateToSecondary(LisaanRoute.Paywall.createRoute(source)) }
                    )
                }
                composable(LisaanRoute.CommunityTab.route) {
                    communityTabContent(
                        { onNavigateToSecondary(LisaanRoute.CreateCommunityPost.route) },
                        { postId -> onNavigateToSecondary(LisaanRoute.CommunityPostDetail.createRoute(postId)) },
                        { onNavigateToSecondary(LisaanRoute.Leaderboard.route) },
                        { onNavigateToSecondary(LisaanRoute.Achievements.route) }
                    )
                }
                composable(LisaanRoute.ProfileTab.route) {
                    profileTabContent(
                        { onNavigateToSecondary(LisaanRoute.Settings.route) },
                        { source -> onNavigateToSecondary(LisaanRoute.Paywall.createRoute(source)) },
                        { onNavigateToSecondary(LisaanRoute.Achievements.route) }
                    )
                }
            }
        }
    }
}
