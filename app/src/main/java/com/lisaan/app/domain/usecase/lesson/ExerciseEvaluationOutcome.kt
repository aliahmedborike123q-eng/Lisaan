package com.lisaan.app.domain.usecase.lesson

import com.lisaan.app.core.database.entities.ExerciseEntity
import com.lisaan.app.core.media.LisaanSpeechRecognizer
import javax.inject.Inject

data class ExerciseEvaluationOutcome(
    val isCorrect: Boolean,
    val isAlmostCorrectWithTypo: Boolean = false,
    val accuracyPercent: Int = 100,
    val feedbackHeadlineAr: String,
    val feedbackHeadlineEn: String,
    val correctSolutionDisplay: String,
    val explanationAr: String,
    val explanationEn: String
)

class EvaluateExerciseAnswerUseCase @Inject constructor() {

    fun evaluate(
        exercise: ExerciseEntity,
        userAnswer: String,
        spokenAccuracyPercent: Int? = null
    ): ExerciseEvaluationOutcome {
        val expectedRaw = exercise.correctAnswerJson.trim()
        val expectedNorm = LisaanSpeechRecognizer.normalizeText(expectedRaw)
        val userNorm = LisaanSpeechRecognizer.normalizeText(userAnswer)

        return when (exercise.type) {
            "SPEAKING_PRONUNCIATION" -> {
                val accuracy = spokenAccuracyPercent
                    ?: LisaanSpeechRecognizer.evaluatePronunciationAccuracy(expectedRaw, userAnswer).accuracyPercent
                val passed = accuracy >= 70
                ExerciseEvaluationOutcome(
                    isCorrect = passed,
                    accuracyPercent = accuracy,
                    feedbackHeadlineAr = if (passed) "نطق رائع! دقتك $accuracy%" else "دقة النطق $accuracy% — حاول مرة أخرى بوضوح",
                    feedbackHeadlineEn = if (passed) "Great pronunciation! Accuracy: $accuracy%" else "Pronunciation accuracy: $accuracy% — Try again",
                    correctSolutionDisplay = expectedRaw,
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }

            "WRITTEN_TRANSLATION", "LISTENING_DICTATION" -> {
                val distance = LisaanSpeechRecognizer.levenshteinDistance(expectedNorm, userNorm)
                val exactMatch = expectedNorm == userNorm
                val minorTypoMatch = !exactMatch && expectedNorm.length >= 5 && distance == 1

                val isAccepted = exactMatch || minorTypoMatch
                ExerciseEvaluationOutcome(
                    isCorrect = isAccepted,
                    isAlmostCorrectWithTypo = minorTypoMatch,
                    accuracyPercent = if (exactMatch) 100 else if (minorTypoMatch) 90 else 0,
                    feedbackHeadlineAr = when {
                        exactMatch -> "إجابة صحيحة وممتازة!"
                        minorTypoMatch -> "صحيحة! (انتبه لحرف إملائي بسيط)"
                        else -> "ليست دقيقة تماماً، انظر الإجابة الصحيحة:"
                    },
                    feedbackHeadlineEn = when {
                        exactMatch -> "Spot on!"
                        minorTypoMatch -> "Correct! (Watch the tiny typo)"
                        else -> "Not quite right. Correct answer:"
                    },
                    correctSolutionDisplay = expectedRaw,
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }

            "MATCHING_PAIRS" -> {
                val passed = userAnswer == "ALL_MATCHED"
                ExerciseEvaluationOutcome(
                    isCorrect = passed,
                    feedbackHeadlineAr = "توصيل متقن لجميع المفردات!",
                    feedbackHeadlineEn = "All pairs matched!",
                    correctSolutionDisplay = "",
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }

            else -> {
                // MULTIPLE_CHOICE, WORD_ORDER, FILL_BLANK, TRUE_FALSE
                val isMatch = expectedNorm == userNorm
                ExerciseEvaluationOutcome(
                    isCorrect = isMatch,
                    accuracyPercent = if (isMatch) 100 else 0,
                    feedbackHeadlineAr = if (isMatch) "أحسنت! إجابة صحيحة" else "إجابة غير صحيحة",
                    feedbackHeadlineEn = if (isMatch) "Well done! Correct answer" else "Incorrect answer",
                    correctSolutionDisplay = expectedRaw,
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }
        }
    }
}
