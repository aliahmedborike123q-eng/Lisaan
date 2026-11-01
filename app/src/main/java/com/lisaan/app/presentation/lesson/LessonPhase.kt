package com.lisaan.app.presentation.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.database.dao.*
import com.lisaan.app.core.database.entities.*
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.media.LisaanAudioPlayer
import com.lisaan.app.core.media.LisaanSpeechRecognizer
import com.lisaan.app.data.seed.DialogueLineSeed
import com.lisaan.app.domain.usecase.lesson.EvaluateExerciseAnswerUseCase
import com.lisaan.app.domain.usecase.lesson.ExerciseEvaluationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

enum class LessonPhase {
    INTRO,
    VOCABULARY_CARDS,
    DIALOGUE_SENTENCES,
    EXERCISES,
    SUMMARY_RESULT
}

data class LessonPlayerUiState(
    val isLoading: Boolean = true,
    val lesson: LessonEntity? = null,
    val vocabularies: List<VocabularyEntity> = emptyList(),
    val dialogueLines: List<DialogueLineSeed> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val currentPhase: LessonPhase = LessonPhase.INTRO,
    val currentVocabIndex: Int = 0,
    val currentExerciseIndex: Int = 0,
    val userInputAnswer: String = "",
    val selectedTokensOrder: List<String> = emptyList(),
    val matchedPairsSet: Set<String> = emptySet(),
    val currentOutcome: ExerciseEvaluationOutcome? = null,
    val correctExercisesCount: Int = 0,
    val earnedXp: Int = 0,
    val accuracyPercent: Int = 100,
    val starsEarned: Int = 3,
    val startTimeEpochMs: Long = System.currentTimeMillis(),
    val elapsedSeconds: Int = 0
)

@HiltViewModel
class LessonPlayerViewModel @Inject constructor(
    private val courseContentDao: CourseContentDao,
    private val vocabularyDao: VocabularyDao,
    private val progressAndStreakDao: ProgressAndStreakDao,
    private val userDao: UserDao,
    private val sm2ReviewDao: Sm2ReviewDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val evaluateExerciseUseCase: EvaluateExerciseAnswerUseCase,
    val audioPlayer: LisaanAudioPlayer,
    val speechRecognizer: LisaanSpeechRecognizer,
    private val analyticsTracker: LisaanAnalyticsTracker,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonPlayerUiState())
    val uiState: StateFlow<LessonPlayerUiState> = _uiState.asStateFlow()

    fun loadLesson(lessonId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, startTimeEpochMs = System.currentTimeMillis()) }
            val details = courseContentDao.getLessonDetailsSnapshot(lessonId) ?: return@launch
            val parsedDialogue = runCatching {
                json.decodeFromString<List<DialogueLineSeed>>(details.lesson.dialogueJson)
            }.getOrDefault(emptyList())

            analyticsTracker.logEvent("lesson_started", mapOf("lesson_id" to lessonId))

            _uiState.update {
                it.copy(
                    isLoading = false,
                    lesson = details.lesson,
                    vocabularies = details.vocabularies,
                    dialogueLines = parsedDialogue,
                    exercises = details.exercises.sortedBy { ex -> ex.orderIndex },
                    currentPhase = LessonPhase.INTRO
                )
            }
        }
    }

    fun advanceFromIntroToVocab() {
        _uiState.update { it.copy(currentPhase = LessonPhase.VOCABULARY_CARDS, currentVocabIndex = 0) }
    }

    fun nextVocabCard() {
        val state = _uiState.value
        if (state.currentVocabIndex < state.vocabularies.lastIndex) {
            _uiState.update { it.copy(currentVocabIndex = it.currentVocabIndex + 1) }
        } else {
            _uiState.update { it.copy(currentPhase = LessonPhase.DIALOGUE_SENTENCES) }
        }
    }

    fun toggleBookmarkCurrentVocab(vocab: VocabularyEntity) {
        viewModelScope.launch {
            val newStatus = !vocab.isBookmarked
            vocabularyDao.setBookmarkStatus(vocab.id, newStatus)
            val updatedList = _uiState.value.vocabularies.map {
                if (it.id == vocab.id) it.copy(isBookmarked = newStatus) else it
            }
            _uiState.update { it.copy(vocabularies = updatedList) }
        }
    }

    fun advanceFromDialogueToExercises() {
        _uiState.update {
            it.copy(
                currentPhase = LessonPhase.EXERCISES,
                currentExerciseIndex = 0,
                userInputAnswer = "",
                selectedTokensOrder = emptyList(),
                matchedPairsSet = emptySet(),
                currentOutcome = null
            )
        }
    }

    fun updateUserInput(text: String) {
        _uiState.update { it.copy(userInputAnswer = text) }
    }

    fun toggleWordOrderToken(token: String) {
        val current = _uiState.value.selectedTokensOrder.toMutableList()
        if (current.contains(token)) current.remove(token) else current.add(token)
        _uiState.update {
            it.copy(
                selectedTokensOrder = current,
                userInputAnswer = current.joinToString(" ")
            )
        }
    }

    fun markPairMatched(leftKey: String, totalPairsCount: Int) {
        val updated = _uiState.value.matchedPairsSet + leftKey
        val allDone = updated.size >= totalPairsCount
        _uiState.update {
            it.copy(
                matchedPairsSet = updated,
                userInputAnswer = if (allDone) "ALL_MATCHED" else ""
            )
        }
    }

    fun submitCurrentExercise(spokenAccuracyPercent: Int? = null) {
        val state = _uiState.value
        val exercise = state.exercises.getOrNull(state.currentExerciseIndex) ?: return

        val effectiveAnswer = if (exercise.type == "SPEAKING_PRONUNCIATION") {
            speechRecognizer.lastResult.value?.recognizedTranscript.orEmpty()
        } else {
            state.userInputAnswer
        }

        val outcome = evaluateExerciseUseCase.evaluate(
            exercise = exercise,
            userAnswer = effectiveAnswer,
            spokenAccuracyPercent = spokenAccuracyPercent ?: speechRecognizer.lastResult.value?.accuracyPercent
        )

        val updatedCorrectCount = if (outcome.isCorrect) state.correctExercisesCount + 1 else state.correctExercisesCount

        analyticsTracker.logEvent(
            "exercise_answered",
            mapOf(
                "exercise_id" to exercise.id,
                "type" to exercise.type,
                "is_correct" to outcome.isCorrect
            )
        )

        _uiState.update {
            it.copy(
                currentOutcome = outcome,
                correctExercisesCount = updatedCorrectCount
            )
        }
    }

    fun proceedToNextExerciseOrFinish() {
        val state = _uiState.value
        speechRecognizer.stopListening()

        if (state.currentExerciseIndex < state.exercises.lastIndex) {
            _uiState.update {
                it.copy(
                    currentExerciseIndex = it.currentExerciseIndex + 1,
                    userInputAnswer = "",
                    selectedTokensOrder = emptyList(),
                    matchedPairsSet = emptySet(),
                    currentOutcome = null
                )
            }
        } else {
            finalizeLessonAndSaveProgress()
        }
    }

    private fun finalizeLessonAndSaveProgress() {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        val totalEx = state.exercises.size.coerceAtLeast(1)
        val accuracy = ((state.correctExercisesCount.toFloat() / totalEx.toFloat()) * 100).toInt()
        val stars = when {
            accuracy >= 90 -> 3
            accuracy >= 70 -> 2
            else -> 1
        }
        val xp = lesson.rewardXp + (stars * 5)
        val elapsedSec = ((System.currentTimeMillis() - state.startTimeEpochMs) / 1000L).toInt().coerceAtLeast(10)

        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"

            // 1. حفظ تقدم الدرس
            val existingProg = progressAndStreakDao.getLessonProgress(userId, lesson.id)
            val progressEntity = UserProgressEntity(
                id = "${userId}_${lesson.id}",
                userId = userId,
                courseId = "en_from_ar",
                unitId = lesson.unitId,
                lessonId = lesson.id,
                isCompleted = true,
                bestScorePercent = maxOf(existingProg?.bestScorePercent ?: 0, accuracy),
                starsEarned = maxOf(existingProg?.starsEarned ?: 0, stars),
                attemptsCount = (existingProg?.attemptsCount ?: 0) + 1,
                earnedXp = xp,
                timeSpentSeconds = elapsedSec,
                completedAt = System.currentTimeMillis(),
                isPendingSync = true
            )
            progressAndStreakDao.upsertProgress(progressEntity)

            // 2. زيادة نقاط XP للمستخدم
            userDao.incrementUserXp(userId, xp)

            // 3. تحديث الـ Streak اليومي
            val todayIso = LocalDate.now().toString()
            val currentStreak = progressAndStreakDao.getStreakSnapshot(userId) ?: StreakEntity(userId = userId)
            val newStreakDays = if (currentStreak.lastCompletedDateIso == todayIso) {
                currentStreak.currentStreakDays.coerceAtLeast(1)
            } else {
                currentStreak.currentStreakDays + 1
            }
            progressAndStreakDao.upsertStreak(
                currentStreak.copy(
                    currentStreakDays = newStreakDays,
                    longestStreakDays = maxOf(currentStreak.longestStreakDays, newStreakDays),
                    lastCompletedDateIso = todayIso,
                    todayMinutesLearned = currentStreak.todayMinutesLearned + (elapsedSec / 60).coerceAtLeast(3),
                    todayXpEarned = currentStreak.todayXpEarned + xp,
                    isPendingSync = true
                )
            )

            // 4. إضافة مفردات الدرس إلى جدول المراجعة الذكية SM-2
            val reviewItems = state.vocabularies.map { vocab ->
                ReviewItemEntity(
                    id = "${userId}_${vocab.id}",
                    userId = userId,
                    vocabularyId = vocab.id,
                    repetitions = 1,
                    easinessFactor = 2.5f,
                    intervalDays = 1,
                    nextReviewEpochMs = System.currentTimeMillis() + (24L * 3600L * 1000L),
                    isPendingSync = true
                )
            }
            sm2ReviewDao.insertInitialReviewItems(reviewItems)

            analyticsTracker.logEvent(
                "lesson_completed",
                mapOf("lesson_id" to lesson.id, "accuracy" to accuracy, "xp_earned" to xp)
            )

            _uiState.update {
                it.copy(
                    currentPhase = LessonPhase.SUMMARY_RESULT,
                    earnedXp = xp,
                    accuracyPercent = accuracy,
                    starsEarned = stars,
                    elapsedSeconds = elapsedSec
                )
            }
        }
    }
}
