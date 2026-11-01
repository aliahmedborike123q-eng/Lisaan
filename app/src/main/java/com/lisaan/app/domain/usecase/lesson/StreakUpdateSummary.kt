package com.lisaan.app.domain.usecase.lesson

import com.lisaan.app.core.database.dao.AchievementDao
import com.lisaan.app.core.database.dao.ProgressAndStreakDao
import com.lisaan.app.core.database.dao.UserDao
import com.lisaan.app.core.database.entities.StreakEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class StreakUpdateSummary(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val wasFreezeConsumed: Boolean,
    val wasStreakReset: Boolean,
    val totalUserXp: Int
)

class StreakAndXpManagerUseCase @Inject constructor(
    private val progressAndStreakDao: ProgressAndStreakDao,
    private val userDao: UserDao,
    private val achievementDao: AchievementDao
) {
    /**
     * يفحص حالة الـ Streak عند فتح التطبيق أو إتمام نشاط، ويطبق حماية الـ Streak (Freeze) تلقائياً
     * إذا غاب المتعلم يوماً واحداً وكان يملك رصيد تجميد، ويحدّث قناع النشاط الأسبوعي (7 أيام).
     */
    suspend fun recordLearningActivityAndEvaluateStreak(
        userId: String,
        earnedXp: Int,
        minutesLearned: Int,
        todayDate: LocalDate = LocalDate.now()
    ): StreakUpdateSummary {
        val todayIso = todayDate.toString()
        val existing = progressAndStreakDao.getStreakSnapshot(userId)
            ?: StreakEntity(userId = userId)

        var currentDays = existing.currentStreakDays
        var freezesLeft = existing.freezeCountAvailable
        var freezeUsedToday = false
        var streakReset = false

        val lastDate = existing.lastCompletedDateIso?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

        when {
            lastDate == null -> {
                currentDays = 1
            }
            lastDate == todayDate -> {
                // سبق أن تعلّم اليوم؛ نحافظ على عدد الأيام ونزيد الدقائق والـ XP
                currentDays = currentDays.coerceAtLeast(1)
            }
            else -> {
                val daysBetween = ChronoUnit.DAYS.between(lastDate, todayDate)
                when {
                    daysBetween == 1L -> {
                        // تعلّم بالأمس واليوم -> زيادة متواصلة
                        currentDays += 1
                    }
                    daysBetween == 2L && freezesLeft > 0 -> {
                        // غاب يوم أمس فقط ولديه درع تجميد الشعلة (Streak Freeze)
                        freezesLeft -= 1
                        freezeUsedToday = true
                        currentDays += 1
                    }
                    else -> {
                        // انقطعت السلسلة لأكثر من يوم بدون تجميد
                        currentDays = 1
                        streakReset = true
                    }
                }
            }
        }

        val longestDays = maxOf(existing.longestStreakDays, currentDays)
        val dayOfWeekIndex = todayDate.dayOfWeek.value % 7 // 0..6
        val updatedMask = updateWeeklyMask(existing.weeklyActivityMaskJson, dayOfWeekIndex)

        val updatedStreak = existing.copy(
            currentStreakDays = currentDays,
            longestStreakDays = longestDays,
            lastCompletedDateIso = todayIso,
            todayMinutesLearned = if (lastDate == todayDate) existing.todayMinutesLearned + minutesLearned else minutesLearned,
            todayXpEarned = if (lastDate == todayDate) existing.todayXpEarned + earnedXp else earnedXp,
            freezeCountAvailable = freezesLeft,
            isFreezeUsedToday = freezeUsedToday,
            weeklyActivityMaskJson = updatedMask,
            isPendingSync = true
        )

        progressAndStreakDao.upsertStreak(updatedStreak)
        userDao.incrementUserXp(userId, earnedXp)

        // تحديث وسام شعلة الأسبوع (7 أيام) تلقائياً
        val streak7Ach = achievementDao.getAchievementByCode("STREAK_7")
        if (streak7Ach != null && !streak7Ach.isUnlocked) {
            val unlocked = currentDays >= streak7Ach.requiredValue
            achievementDao.updateAchievementProgress(
                id = streak7Ach.id,
                progress = currentDays.coerceAtMost(streak7Ach.requiredValue),
                unlocked = unlocked,
                unlockedAt = if (unlocked) System.currentTimeMillis() else null
            )
        }

        val updatedUser = userDao.getUserById(userId)

        return StreakUpdateSummary(
            currentStreakDays = currentDays,
            longestStreakDays = longestDays,
            wasFreezeConsumed = freezeUsedToday,
            wasStreakReset = streakReset,
            totalUserXp = updatedUser?.totalXp ?: earnedXp
        )
    }

    private fun updateWeeklyMask(currentMaskJson: String, dayIndex: Int): String {
        val clean = currentMaskJson.removePrefix("[").removeSuffix("]").split(",")
        val bools = MutableList(7) { idx ->
            clean.getOrNull(idx)?.trim()?.toBooleanStrictOrNull() ?: false
        }
        if (dayIndex in 0..6) {
            bools[dayIndex] = true
        }
        return bools.joinToString(prefix = "[", postfix = "]", separator = ",")
    }
}
