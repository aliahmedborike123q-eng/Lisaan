package com.lisaan.app.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.database.dao.Sm2ReviewDao
import com.lisaan.app.core.database.relations.ReviewItemWithVocabularyRelation
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.media.LisaanAudioPlayer
import com.lisaan.app.domain.usecase.lesson.StreakAndXpManagerUseCase
import com.lisaan.app.domain.usecase.sm2.CalculateSm2ScheduleUseCase
import com.lisaan.app.domain.usecase.sm2.Sm2UserGrade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartReviewUiState(
    val isLoading: Boolean = true,
    val dueCards: List<ReviewItemWithVocabularyRelation> = emptyList(),
    val currentCardIndex: Int = 0,
    val isAnswerRevealed: Boolean = false,
    val reviewedCountInSession: Int = 0,
    val isSessionCompleted: Boolean = false,
    val sessionEarnedXp: Int = 0
)

@HiltViewModel
class SmartReviewViewModel @Inject constructor(
    private val sm2ReviewDao: Sm2ReviewDao,
    private val calculateSm2UseCase: CalculateSm2ScheduleUseCase,
    private val streakAndXpManager: StreakAndXpManagerUseCase,
    private val preferencesDataStore: UserPreferencesDataStore,
    val audioPlayer: LisaanAudioPlayer,
    private val analyticsTracker: LisaanAnalyticsTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartReviewUiState())
    val uiState: StateFlow<SmartReviewUiState> = _uiState.asStateFlow()

    init {
        loadDueCards()
    }

    fun loadDueCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isSessionCompleted = false, currentCardIndex = 0) }
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"
            val dueList = sm2ReviewDao.getDueReviewSessionItems(
                userId = userId,
                currentTimeMs = System.currentTimeMillis(),
                limit = 25
            )
            analyticsTracker.logEvent("sm2_session_started", mapOf("due_count" to dueList.size))
            _uiState.update {
                it.copy(
                    isLoading = false,
                    dueCards = dueList,
                    currentCardIndex = 0,
                    isAnswerRevealed = false,
                    reviewedCountInSession = 0,
                    isSessionCompleted = dueList.isEmpty()
                )
            }
        }
    }

    fun revealCardAnswer() {
        _uiState.update { it.copy(isAnswerRevealed = true) }
    }

    fun submitCardGrade(grade: Sm2UserGrade) {
        val state = _uiState.value
        val currentRelation = state.dueCards.getOrNull(state.currentCardIndex) ?: return

        viewModelScope.launch {
            val updatedReviewEntity = calculateSm2UseCase.calculateNextReview(
                currentItem = currentRelation.reviewItem,
                qualityGrade = grade.qualityScore
            )
            sm2ReviewDao.upsertReviewItem(updatedReviewEntity)

            analyticsTracker.logEvent(
                "sm2_card_graded",
                mapOf(
                    "vocab_id" to currentRelation.vocabulary.id,
                    "grade" to grade.qualityScore,
                    "new_interval_days" to updatedReviewEntity.intervalDays
                )
            )

            val nextIdx = state.currentCardIndex + 1
            val reviewedSoFar = state.reviewedCountInSession + 1

            if (nextIdx <= state.dueCards.lastIndex) {
                _uiState.update {
                    it.copy(
                        currentCardIndex = nextIdx,
                        isAnswerRevealed = false,
                        reviewedCountInSession = reviewedSoFar
                    )
                }
            } else {
                val earnedXp = reviewedSoFar * 4
                val prefs = preferencesDataStore.settingsFlow.first()
                val userId = prefs.currentUserId ?: "guest_default"
                streakAndXpManager.recordLearningActivityAndEvaluateStreak(
                    userId = userId,
                    earnedXp = earnedXp,
                    minutesLearned = 3
                )
                analyticsTracker.logEvent("sm2_session_completed", mapOf("reviewed_count" to reviewedSoFar))
                _uiState.update {
                    it.copy(
                        reviewedCountInSession = reviewedSoFar,
                        isSessionCompleted = true,
                        sessionEarnedXp = earnedXp
                    )
                }
            }
        }
    }
}
