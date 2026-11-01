package com.lisaan.app.presentation.onboarding

import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.domain.usecase.placement.CalculatePlacementLevelUseCase
import com.lisaan.app.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var preferencesDataStore: UserPreferencesDataStore
    private lateinit var analyticsTracker: LisaanAnalyticsTracker
    private lateinit var placementUseCase: CalculatePlacementLevelUseCase
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        preferencesDataStore = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        placementUseCase = CalculatePlacementLevelUseCase()
        viewModel = OnboardingViewModel(preferencesDataStore, placementUseCase, analyticsTracker)
    }

    @Test
    fun `stepping through onboarding transitions states in strict order`() = runTest {
        assertEquals(OnboardingStep.INTRO_PAGER, viewModel.uiState.value.currentStep)

        viewModel.finishIntroPager()
        assertEquals(OnboardingStep.INTERFACE_LANGUAGE, viewModel.uiState.value.currentStep)

        viewModel.selectInterfaceLanguage("ar")
        coVerify { preferencesDataStore.setInterfaceLanguage("ar") }
        assertEquals(OnboardingStep.TARGET_LANGUAGE, viewModel.uiState.value.currentStep)

        viewModel.selectTargetLanguage("en")
        assertEquals(OnboardingStep.LEARNING_GOAL, viewModel.uiState.value.currentStep)

        viewModel.selectLearningGoal("WORK")
        assertEquals(OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `answering all 10 placement test questions computes CEFR level B2 when all correct`() = runTest {
        viewModel.startPlacementTest()
        assertEquals(OnboardingStep.PLACEMENT_TEST, viewModel.uiState.value.currentStep)

        val questions = viewModel.uiState.value.placementQuestions
        assertEquals(10, questions.size)

        questions.forEach { q ->
            viewModel.answerPlacementQuestion(q.id, q.correctOptionIndex)
        }

        val result = viewModel.uiState.value.placementResult
        assertNotNull(result)
        assertEquals(10, result?.correctCount)
        assertEquals("B2", result?.assignedLevel)
        assertEquals("B2", viewModel.uiState.value.selectedLevel)
    }
}
