package com.lisaan.app.domain.usecase.lesson

import com.lisaan.app.core.database.entities.ExerciseEntity
import com.lisaan.app.core.media.LisaanSpeechRecognizer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvaluateExerciseAnswerUseCaseTest {

    private lateinit var evaluateUseCase: EvaluateExerciseAnswerUseCase

    @Before
    fun setUp() {
        evaluateUseCase = EvaluateExerciseAnswerUseCase()
    }

    @Test
    fun `written translation accepts exact match and single-character typo`() {
        val exercise = ExerciseEntity(
            id = "ex_1",
            lessonId = "u1_l1",
            orderIndex = 1,
            type = "WRITTEN_TRANSLATION",
            promptAr = "ترجم:",
            promptEn = "Translate:",
            targetText = "صباح الخير",
            correctAnswerJson = "Good morning",
            explanationAr = "شرح",
            explanationEn = "Explanation"
        )

        // 1. Exact Match (case-insensitive)
        val exactOutcome = evaluateUseCase.evaluate(exercise, "good morning!")
        assertTrue(exactOutcome.isCorrect)
        assertFalse(exactOutcome.isAlmostCorrectWithTypo)
        assertEquals(100, exactOutcome.accuracyPercent)

        // 2. Single-character Typo ("Good mornin")
        val typoOutcome = evaluateUseCase.evaluate(exercise, "Good mornin")
        assertTrue(typoOutcome.isCorrect)
        assertTrue(typoOutcome.isAlmostCorrectWithTypo)
        assertEquals(90, typoOutcome.accuracyPercent)

        // 3. Completely wrong answer
        val wrongOutcome = evaluateUseCase.evaluate(exercise, "Good night")
        assertFalse(wrongOutcome.isCorrect)
        assertEquals(0, wrongOutcome.accuracyPercent)
    }

    @Test
    fun `speaking pronunciation scorer computes Levenshtein accuracy percentage accurately`() {
        val target = "I would like fresh coffee without sugar"
        val spokenGood = "I would like fresh coffee without sugar"
        val spokenPartial = "I would like coffee without sugar"

        val perfectResult = LisaanSpeechRecognizer.evaluatePronunciationAccuracy(target, spokenGood)
        assertEquals(100, perfectResult.accuracyPercent)
        assertTrue(perfectResult.isPassed)

        val partialResult = LisaanSpeechRecognizer.evaluatePronunciationAccuracy(target, spokenPartial)
        assertTrue(partialResult.accuracyPercent >= 75)
        assertTrue(partialResult.isPassed)
    }
}
