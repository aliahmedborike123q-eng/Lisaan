package com.lisaan.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class LisaanRoute(val route: String) {
    // Root Graphs
    data object Splash : LisaanRoute("splash_route")
    data object OnboardingGraph : LisaanRoute("onboarding_graph")
    data object AuthGraph : LisaanRoute("auth_graph")
    data object MainScaffold : LisaanRoute("main_scaffold_route")

    // 1. Onboarding Screens
    data object OnboardingIntro : LisaanRoute("onboarding_intro")
    data object OnboardingInterfaceLang : LisaanRoute("onboarding_interface_lang")
    data object OnboardingTargetLang : LisaanRoute("onboarding_target_lang")
    data object OnboardingGoal : LisaanRoute("onboarding_goal")
    data object OnboardingLevel : LisaanRoute("onboarding_level")
    data object OnboardingPlacementTest : LisaanRoute("onboarding_placement_test")
    data object OnboardingDailyGoal : LisaanRoute("onboarding_daily_goal")
    data object OnboardingNotifications : LisaanRoute("onboarding_notifications")

    // 2. Auth Screens
    data object AuthWelcome : LisaanRoute("auth_welcome")
    data object AuthEmailSignIn : LisaanRoute("auth_email_sign_in")
    data object AuthEmailSignUp : LisaanRoute("auth_email_sign_up")
    data object AuthForgotPassword : LisaanRoute("auth_forgot_password")

    // 3. Bottom Navigation Tabs
    data object HomePathTab : LisaanRoute("tab_home_path")
    data object SmartReviewTab : LisaanRoute("tab_smart_review")
    data object AiCoachTab : LisaanRoute("tab_ai_coach")
    data object CommunityTab : LisaanRoute("tab_community")
    data object ProfileTab : LisaanRoute("tab_profile")

    // 4. Secondary Feature Routes
    data object LessonPlayer : LisaanRoute("lesson_player/{lessonId}") {
        fun createRoute(lessonId: String) = "lesson_player/$lessonId"
    }
    data object Sm2Session : LisaanRoute("sm2_review_session")
    data object Dictionary : LisaanRoute("my_dictionary")
    data object FlashcardsDeck : LisaanRoute("flashcards_deck")
    data object MiniGamesHub : LisaanRoute("mini_games_hub")
    data object SpeedMatchGame : LisaanRoute("game_speed_match")
    data object WordSprintGame : LisaanRoute("game_word_sprint")
    data object GuessPictureGame : LisaanRoute("game_guess_picture")

    data object AiChatSession : LisaanRoute("ai_chat_session/{scenarioId}") {
        fun createRoute(scenarioId: String) = "ai_chat_session/$scenarioId"
    }

    data object CreateCommunityPost : LisaanRoute("community_create_post")
    data object CommunityPostDetail : LisaanRoute("community_post_detail/{postId}") {
        fun createRoute(postId: String) = "community_post_detail/$postId"
    }
    data object Leaderboard : LisaanRoute("challenges_leaderboard")
    data object Achievements : LisaanRoute("challenges_achievements")

    data object Settings : LisaanRoute("profile_settings")
    data object Paywall : LisaanRoute("paywall_screen?source={source}") {
        fun createRoute(source: String = "general") = "paywall_screen?source=$source"
    }
}

data class BottomNavItem(
    val route: String,
    val titleAr: String,
    val titleEn: String,
    val icon: ImageVector
)

val MainBottomNavItems = listOf(
    BottomNavItem(
        route = LisaanRoute.HomePathTab.route,
        titleAr = "المسار",
        titleEn = "Path",
        icon = Icons.Default.Map
    ),
    BottomNavItem(
        route = LisaanRoute.SmartReviewTab.route,
        titleAr = "المراجعة",
        titleEn = "Review",
        icon = Icons.Default.Psychology
    ),
    BottomNavItem(
        route = LisaanRoute.AiCoachTab.route,
        titleAr = "المحادثة",
        titleEn = "AI Coach",
        icon = Icons.Default.AutoAwesome
    ),
    BottomNavItem(
        route = LisaanRoute.CommunityTab.route,
        titleAr = "المجتمع",
        titleEn = "Community",
        icon = Icons.Default.Groups
    ),
    BottomNavItem(
        route = LisaanRoute.ProfileTab.route,
        titleAr = "حسابي",
        titleEn = "Profile",
        icon = Icons.Default.Person
    )
)
