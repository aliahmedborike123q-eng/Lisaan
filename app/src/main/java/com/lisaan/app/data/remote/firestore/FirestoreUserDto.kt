package com.lisaan.app.data.remote.firestore

import com.google.firebase.firestore.PropertyName
import com.lisaan.app.core.database.entities.*

data class FirestoreUserDto(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("email") @set:PropertyName("email") var email: String? = null,
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String = "",
    @get:PropertyName("avatarUrl") @set:PropertyName("avatarUrl") var avatarUrl: String? = null,
    @get:PropertyName("interfaceLang") @set:PropertyName("interfaceLang") var interfaceLang: String = "ar",
    @get:PropertyName("currentCourseId") @set:PropertyName("currentCourseId") var currentCourseId: String = "en_from_ar",
    @get:PropertyName("learningGoal") @set:PropertyName("learningGoal") var learningGoal: String = "TRAVEL",
    @get:PropertyName("proficiencyLevel") @set:PropertyName("proficiencyLevel") var proficiencyLevel: String = "A1",
    @get:PropertyName("dailyGoalMinutes") @set:PropertyName("dailyGoalMinutes") var dailyGoalMinutes: Int = 10,
    @get:PropertyName("totalXp") @set:PropertyName("totalXp") var totalXp: Int = 0,
    @get:PropertyName("currentLeague") @set:PropertyName("currentLeague") var currentLeague: String = "BRONZE",
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis()
) {
    fun toEntity(isGuest: Boolean = false) = UserEntity(
        id = id,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        isGuest = isGuest,
        interfaceLang = interfaceLang,
        currentCourseId = currentCourseId,
        learningGoal = learningGoal,
        proficiencyLevel = proficiencyLevel,
        dailyGoalMinutes = dailyGoalMinutes,
        totalXp = totalXp,
        currentLeague = currentLeague,
        lastActiveAt = updatedAt
    )

    companion object {
        fun fromEntity(entity: UserEntity) = FirestoreUserDto(
            id = entity.id,
            email = entity.email,
            displayName = entity.displayName,
            avatarUrl = entity.avatarUrl,
            interfaceLang = entity.interfaceLang,
            currentCourseId = entity.currentCourseId,
            learningGoal = entity.learningGoal,
            proficiencyLevel = entity.proficiencyLevel,
            dailyGoalMinutes = entity.dailyGoalMinutes,
            totalXp = entity.totalXp,
            currentLeague = entity.currentLeague,
            updatedAt = System.currentTimeMillis()
        )
    }
}

data class FirestoreProgressDto(
    var id: String = "",
    var userId: String = "",
    var courseId: String = "",
    var unitId: String = "",
    var lessonId: String = "",
    var isCompleted: Boolean = false,
    var bestScorePercent: Int = 0,
    var starsEarned: Int = 0,
    var attemptsCount: Int = 0,
    var earnedXp: Int = 0,
    var timeSpentSeconds: Int = 0,
    var completedAt: Long = 0L
) {
    fun toEntity() = UserProgressEntity(
        id = id,
        userId = userId,
        courseId = courseId,
        unitId = unitId,
        lessonId = lessonId,
        isCompleted = isCompleted,
        bestScorePercent = bestScorePercent,
        starsEarned = starsEarned,
        attemptsCount = attemptsCount,
        earnedXp = earnedXp,
        timeSpentSeconds = timeSpentSeconds,
        completedAt = completedAt,
        isPendingSync = false
    )

    companion object {
        fun fromEntity(e: UserProgressEntity) = FirestoreProgressDto(
            id = e.id,
            userId = e.userId,
            courseId = e.courseId,
            unitId = e.unitId,
            lessonId = e.lessonId,
            isCompleted = e.isCompleted,
            bestScorePercent = e.bestScorePercent,
            starsEarned = e.starsEarned,
            attemptsCount = e.attemptsCount,
            earnedXp = e.earnedXp,
            timeSpentSeconds = e.timeSpentSeconds,
            completedAt = e.completedAt
        )
    }
}

data class FirestoreReviewItemDto(
    var id: String = "",
    var userId: String = "",
    var vocabularyId: String = "",
    var repetitions: Int = 0,
    var easinessFactor: Float = 2.5f,
    var intervalDays: Int = 1,
    var nextReviewEpochMs: Long = 0L,
    var lastReviewedEpochMs: Long = 0L,
    var lastQualityGrade: Int = 0,
    var lapseCount: Int = 0
) {
    fun toEntity() = ReviewItemEntity(
        id = id,
        userId = userId,
        vocabularyId = vocabularyId,
        repetitions = repetitions,
        easinessFactor = easinessFactor,
        intervalDays = intervalDays,
        nextReviewEpochMs = nextReviewEpochMs,
        lastReviewedEpochMs = lastReviewedEpochMs,
        lastQualityGrade = lastQualityGrade,
        lapseCount = lapseCount,
        isPendingSync = false
    )

    companion object {
        fun fromEntity(e: ReviewItemEntity) = FirestoreReviewItemDto(
            id = e.id,
            userId = e.userId,
            vocabularyId = e.vocabularyId,
            repetitions = e.repetitions,
            easinessFactor = e.easinessFactor,
            intervalDays = e.intervalDays,
            nextReviewEpochMs = e.nextReviewEpochMs,
            lastReviewedEpochMs = e.lastReviewedEpochMs,
            lastQualityGrade = e.lastQualityGrade,
            lapseCount = e.lapseCount
        )
    }
}
