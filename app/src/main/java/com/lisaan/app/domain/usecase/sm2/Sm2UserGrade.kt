package com.lisaan.app.domain.usecase.sm2

import com.lisaan.app.core.database.entities.ReviewItemEntity
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * درجات التقييم في واجهة المستخدم (مربوطة بدرجات SM-2 من 0 إلى 5):
 * - AGAIN (نسيت تماماً) -> Grade 1
 * - HARD (تذكرت بصعوبة) -> Grade 3
 * - GOOD (تذكرت بشكل جيد) -> Grade 4
 * - EASY (تذكرت فوراً بسهولة) -> Grade 5
 */
enum class Sm2UserGrade(val qualityScore: Int, val labelAr: String, val labelEn: String) {
    AGAIN(1, "نسيتها (أعد اليوم)", "Again"),
    HARD(3, "صعبة", "Hard"),
    GOOD(4, "جيدة", "Good"),
    EASY(5, "سهلة جداً", "Easy")
}

class CalculateSm2ScheduleUseCase @Inject constructor() {

    fun calculateNextReview(
        currentItem: ReviewItemEntity,
        qualityGrade: Int, // 0..5
        nowEpochMs: Long = System.currentTimeMillis()
    ): ReviewItemEntity {
        val q = qualityGrade.coerceIn(0, 5)

        val newEasinessFactor = max(
            MIN_EASINESS_FACTOR,
            currentItem.easinessFactor + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
        )

        val newRepetitions: Int
        val newIntervalDays: Int
        val newLapseCount: Int

        if (q < 3) {
            // إذا كانت الدرجة أقل من 3 يعاد ضبط التكرارات إلى الصفر وتُراجع البطاقة بعد 10 دقائق أو غداً
            newRepetitions = 0
            newIntervalDays = 1
            newLapseCount = currentItem.lapseCount + 1
        } else {
            newRepetitions = currentItem.repetitions + 1
            newLapseCount = currentItem.lapseCount
            newIntervalDays = when (newRepetitions) {
                1 -> 1
                2 -> 6
                else -> (currentItem.intervalDays * newEasinessFactor).roundToInt().coerceAtLeast(1)
            }
        }

        // إذا نسيها المتعلم (q < 3) نجدولها بعد 15 دقيقة في نفس اليوم أو بعد عدد الأيام المحسوب
        val nextReviewDelayMs = if (q < 3) {
            15L * 60L * 1000L // 15 minutes for immediate reinforcement
        } else {
            newIntervalDays.toLong() * DAY_IN_MILLIS
        }

        return currentItem.copy(
            repetitions = newRepetitions,
            easinessFactor = newEasinessFactor,
            intervalDays = newIntervalDays,
            nextReviewEpochMs = nowEpochMs + nextReviewDelayMs,
            lastReviewedEpochMs = nowEpochMs,
            lastQualityGrade = q,
            lapseCount = newLapseCount,
            isPendingSync = true
        )
    }

    companion object {
        const val MIN_EASINESS_FACTOR = 1.3f
        const val DAY_IN_MILLIS = 24L * 60L * 60L * 1000L
    }
}
