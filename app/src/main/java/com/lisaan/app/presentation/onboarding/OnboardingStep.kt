package com.lisaan.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.domain.usecase.placement.CalculatePlacementLevelUseCase
import com.lisaan.app.domain.usecase.placement.PlacementEvaluationResult
import com.lisaan.app.domain.usecase.placement.PlacementQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    INTRO_PAGER,
    INTERFACE_LANGUAGE,
    TARGET_LANGUAGE,
    LEARNING_GOAL,
    LEVEL_OR_PLACEMENT_CHOICE,
    PLACEMENT_TEST,
    DAILY_GOAL,
    NOTIFICATIONS_OPT_IN
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.INTRO_PAGER,
    val interfaceLang: String = "ar",
    val targetLang: String = "en",
    val selectedGoal: String = "TRAVEL", // TRAVEL, WORK, STUDY, HOBBY
    val selectedLevel: String = "A1",
    val placementQuestions: List<PlacementQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val placementAnswers: Map<Int, Int> = emptyMap(),
    val placementResult: PlacementEvaluationResult? = null,
    val dailyGoalMinutes: Int = 10, // 5, 10, 15, 20
    val notificationsEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val placementUseCase: CalculatePlacementLevelUseCase,
    private val analyticsTracker: LisaanAnalyticsTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(placementQuestions = placementUseCase.getPlacementQuestions())
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun finishIntroPager() {
        analyticsTracker.logOnboardingStep("intro_pager_completed")
        _uiState.update { it.copy(currentStep = OnboardingStep.INTERFACE_LANGUAGE) }
    }

    fun selectInterfaceLanguage(langCode: String) {
        viewModelScope.launch {
            preferencesDataStore.setInterfaceLanguage(langCode)
            analyticsTracker.logOnboardingStep("interface_language", langCode)
            _uiState.update {
                it.copy(
                    interfaceLang = langCode,
                    currentStep = OnboardingStep.TARGET_LANGUAGE
                )
            }
        }
    }

    fun selectTargetLanguage(targetLangCode: String) {
        analyticsTracker.logOnboardingStep("target_language", targetLangCode)
        _uiState.update {
            it.copy(
                targetLang = targetLangCode,
                currentStep = OnboardingStep.LEARNING_GOAL
            )
        }
    }

    fun selectLearningGoal(goalCode: String) {
        analyticsTracker.logOnboardingStep("learning_goal", goalCode)
        _uiState.update {
            it.copy(
                selectedGoal = goalCode,
                currentStep = OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE
            )
        }
    }

    fun chooseManualLevel(levelCode: String) {
        analyticsTracker.logOnboardingStep("manual_level_selected", levelCode)
        _uiState.update {
            it.copy(
                selectedLevel = levelCode,
                currentStep = OnboardingStep.DAILY_GOAL
            )
        }
    }

    fun startPlacementTest() {
        analyticsTracker.logOnboardingStep("placement_test_started")
        _uiState.update {
            it.copy(
                currentQuestionIndex = 0,
                placementAnswers = emptyMap(),
                placementResult = null,
                currentStep = OnboardingStep.PLACEMENT_TEST
            )
        }
    }

    fun answerPlacementQuestion(questionId: Int, optionIndex: Int) {
        val currentState = _uiState.value
        val updatedAnswers = currentState.placementAnswers.toMutableMap().apply {
            put(questionId, optionIndex)
        }

        if (currentState.currentQuestionIndex < currentState.placementQuestions.lastIndex) {
            _uiState.update {
                it.copy(
                    placementAnswers = updatedAnswers,
                    currentQuestionIndex = it.currentQuestionIndex + 1
                )
            }
        } else {
            val evalResult = placementUseCase.evaluatePlacement(updatedAnswers)
            analyticsTracker.logPlacementTestCompleted(
                score = evalResult.correctCount,
                total = evalResult.totalQuestions,
                assignedLevel = evalResult.assignedLevel
            )
            _uiState.update {
                it.copy(
                    placementAnswers = updatedAnswers,
                    placementResult = evalResult,
                    selectedLevel = evalResult.assignedLevel
                )
            }
        }
    }

    fun confirmPlacementResultAndProceed() {
        _uiState.update { it.copy(currentStep = OnboardingStep.DAILY_GOAL) }
    }

    fun selectDailyGoalMinutes(minutes: Int) {
        analyticsTracker.logOnboardingStep("daily_goal_minutes", minutes.toString())
        _uiState.update {
            it.copy(
                dailyGoalMinutes = minutes,
                currentStep = OnboardingStep.NOTIFICATIONS_OPT_IN
            )
        }
    }

    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute
            )
        }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            preferencesDataStore.updateOnboardingSelection(
                interfaceLang = state.interfaceLang,
                targetLang = state.targetLang,
                goal = state.selectedGoal,
                level = state.selectedLevel,
                dailyMinutes = state.dailyGoalMinutes,
                notificationsEnabled = state.notificationsEnabled,
                reminderHour = state.reminderHour,
                reminderMinute = state.reminderMinute
            )
            analyticsTracker.logOnboardingStep("onboarding_completed", state.selectedLevel)
            _uiState.update { it.copy(isSaving = false) }
            onFinished()
        }
    }

    fun navigateBackStep(): Boolean {
        val prevStep = when (_uiState.value.currentStep) {
            OnboardingStep.INTRO_PAGER -> return false
            OnboardingStep.INTERFACE_LANGUAGE -> OnboardingStep.INTRO_PAGER
            OnboardingStep.TARGET_LANGUAGE -> OnboardingStep.INTERFACE_LANGUAGE
            OnboardingStep.LEARNING_GOAL -> OnboardingStep.TARGET_LANGUAGE
            OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE -> OnboardingStep.LEARNING_GOAL
            OnboardingStep.PLACEMENT_TEST -> OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE
            OnboardingStep.DAILY_GOAL -> OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE
            OnboardingStep.NOTIFICATIONS_OPT_IN -> OnboardingStep.DAILY_GOAL
        }
        _uiState.update { it.copy(currentStep = prevStep) }
        return true
    }
}
