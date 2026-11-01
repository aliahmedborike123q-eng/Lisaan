package com.lisaan.app.presentation.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.theme.LisaanTheme
import com.lisaan.app.domain.usecase.placement.CalculatePlacementLevelUseCase
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class OnboardingFlowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingFlow_skipIntro_selectLanguagesAndGoal_reachesLevelSelection() {
        val fakeDataStore = mockk<UserPreferencesDataStore>(relaxed = true)
        val fakeAnalytics = mockk<LisaanAnalyticsTracker>(relaxed = true)
        val viewModel = OnboardingViewModel(
            preferencesDataStore = fakeDataStore,
            placementUseCase = CalculatePlacementLevelUseCase(),
            analyticsTracker = fakeAnalytics
        )

        composeTestRule.setContent {
            LisaanTheme(interfaceLangCode = "ar") {
                OnboardingFlowScreen(
                    onOnboardingCompleteNavigateToAuth = {},
                    viewModel = viewModel
                )
            }
        }

        // 1. التحقق من ظهور الشاشة الترحيبية الأولى وتخطيها
        composeTestRule.onNodeWithText("تخطي").assertIsDisplayed().performClick()

        // 2. اختيار لغة الواجهة العربية
        composeTestRule.onNodeWithText("العربية (الوضع الأصلي RTL)").assertIsDisplayed().performClick()

        // 3. اختيار اللغة الإنجليزية للتعلم
        composeTestRule.onNodeWithText("اللغة الإنجليزية (English)").assertIsDisplayed().performClick()

        // 4. اختيار هدف السفر
        composeTestRule.onNodeWithText("السفر واستكشاف العالم").assertIsDisplayed().performClick()

        // 5. التأكد من الوصول إلى شاشة تحديد المستوى أو الاختبار الذكي (10 أسئلة)
        composeTestRule.onNodeWithText("حدد مستواي عبر اختبار ذكي (10 أسئلة)").assertIsDisplayed()
    }
}
