package com.lisaan.app.domain.usecase.sm2

import com.lisaan.app.core.database.entities.ReviewItemEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalculateSm2ScheduleUseCaseTest {

    private lateinit var useCase: CalculateSm2ScheduleUseCase
    private val fixedNowMs = 1_700_000_000_000L

    @Before
    fun setUp() {
        useCase = CalculateSm2ScheduleUseCase()
    }

    @Test
    fun `grade 5 (EASY) on first review sets repetitions to 1 and interval to 1 day`() {
        val initial = ReviewItemEntity(
            id = "u1_v1",
            userId = "u1",
            vocabularyId = "v1",
            repetitions = 0,
            easinessFactor = 2.5f,
            intervalDays = 1
        )

        val updated = useCase.calculateNextReview(
            currentItem = initial,
            qualityGrade = Sm2UserGrade.EASY.qualityScore,
            nowEpochMs = fixedNowMs
        )

        assertEquals(1, updated.repetitions)
        assertEquals(1, updated.intervalDays)
        assertEquals(2.6f, updated.easinessFactor, 0.01f)
        assertEquals(fixedNowMs + CalculateSm2ScheduleUseCase.DAY_IN_MILLIS, updated.nextReviewEpochMs)
        assertTrue(updated.isPendingSync)
    }

    @Test
    fun `second consecutive successful review sets interval to 6 days`() {
        val afterFirst = ReviewItemEntity(
            id = "u1_v1",
            userId = "u1",
            vocabularyId = "v1",
            repetitions = 1,
            easinessFactor = 2.5f,
            intervalDays = 1
        )

        val updated = useCase.calculateNextReview(
            currentItem = afterFirst,
            qualityGrade = Sm2UserGrade.GOOD.qualityScore, // Grade 4 keeps EF = 2.5
            nowEpochMs = fixedNowMs
        )

        assertEquals(2, updated.repetitions)
        assertEquals(6, updated.intervalDays)
        assertEquals(2.5f, updated.easinessFactor, 0.01f)
        assertEquals(fixedNowMs + (6L * CalculateSm2ScheduleUseCase.DAY_IN_MILLIS), updated.nextReviewEpochMs)
    }

    @Test
    fun `third consecutive successful review multiplies interval by easiness factor`() {
        val afterSecond = ReviewItemEntity(
            id = "u1_v1",
            userId = "u1",
            vocabularyId = "v1",
            repetitions = 2,
            easinessFactor = 2.5f,
            intervalDays = 6
        )

        val updated = useCase.calculateNextReview(
            currentItem = afterSecond,
            qualityGrade = Sm2UserGrade.GOOD.qualityScore,
            nowEpochMs = fixedNowMs
        )

        assertEquals(3, updated.repetitions)
        assertEquals(15, updated.intervalDays) // 6 * 2.5 = 15
    }

    @Test
    fun `grade below 3 (AGAIN) resets repetitions to 0, increments lapseCount, and schedules in 15 minutes`() {
        val masteredItem = ReviewItemEntity(
            id = "u1_v1",
            userId = "u1",
            vocabularyId = "v1",
            repetitions = 4,
            easinessFactor = 2.3f,
            intervalDays = 28,
            lapseCount = 1
        )

        val updated = useCase.calculateNextReview(
            currentItem = masteredItem,
            qualityGrade = Sm2UserGrade.AGAIN.qualityScore, // Grade 1
            nowEpochMs = fixedNowMs
        )

        assertEquals(0, updated.repetitions)
        assertEquals(1, updated.intervalDays)
        assertEquals(2, updated.lapseCount)
        assertEquals(fixedNowMs + (15L * 60L * 1000L), updated.nextReviewEpochMs)
    }

    @Test
    fun `easiness factor never drops below minimum 1_3f floor`() {
        val strugglingItem = ReviewItemEntity(
            id = "u1_v1",
            userId = "u1",
            vocabularyId = "v1",
            repetitions = 1,
            easinessFactor = 1.32f,
            intervalDays = 1
        )

        val updated = useCase.calculateNextReview(
            currentItem = strugglingItem,
            qualityGrade = 0,
            nowEpochMs = fixedNowMs
        )

        assertEquals(CalculateSm2ScheduleUseCase.MIN_EASINESS_FACTOR, updated.easinessFactor, 0.001f)
    }
}
