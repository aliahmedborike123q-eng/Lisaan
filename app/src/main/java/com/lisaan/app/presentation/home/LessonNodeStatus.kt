package com.lisaan.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.database.dao.CourseContentDao
import com.lisaan.app.core.database.dao.ProgressAndStreakDao
import com.lisaan.app.core.database.dao.UserDao
import com.lisaan.app.core.database.entities.LessonEntity
import com.lisaan.app.core.database.entities.UnitEntity
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.data.seed.LocalCourseSeedLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LessonNodeStatus { LOCKED, CURRENT_ACTIVE, COMPLETED }

data class LessonMapNodeUi(
    val lesson: LessonEntity,
    val status: LessonNodeStatus,
    val starsEarned: Int,
    val bestScorePercent: Int
)

data class UnitMapSectionUi(
    val unit: UnitEntity,
    val lessons: List<LessonMapNodeUi>,
    val completedCount: Int
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val streakDays: Int = 0,
    val totalXp: Int = 0,
    val todayMinutesLearned: Int = 0,
    val dailyGoalMinutes: Int = 10,
    val continueLessonId: String? = null,
    val continueLessonTitleAr: String = "",
    val continueLessonTitleEn: String = "",
    val unitSections: List<UnitMapSectionUi> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val seedLoader: LocalCourseSeedLoader,
    private val userDao: UserDao,
    private val courseContentDao: CourseContentDao,
    private val progressAndStreakDao: ProgressAndStreakDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        initializeAndObserveHome()
    }

    private fun initializeAndObserveHome() {
        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"
            seedLoader.ensureSeedLoaded(userId)

            combine(
                userDao.observeCompleteProfile(userId),
                courseContentDao.observeUnitsWithLessons("en_from_ar"),
                progressAndStreakDao.observeUserProgress(userId),
                preferencesDataStore.settingsFlow
            ) { profile, unitsWithLessons, progressList, settings ->
                val progressByLessonId = progressList.associateBy { it.lessonId }
                var foundFirstIncomplete = false
                var nextLessonToContinue: LessonEntity? = null

                val sections = unitsWithLessons.map { uwl ->
                    val sortedLessons = uwl.lessons.sortedBy { it.orderIndex }
                    var completedInUnit = 0

                    val nodes = sortedLessons.map { lesson ->
                        val prog = progressByLessonId[lesson.id]
                        val isCompleted = prog?.isCompleted == true
                        if (isCompleted) completedInUnit++

                        val status = when {
                            isCompleted -> LessonNodeStatus.COMPLETED
                            !foundFirstIncomplete -> {
                                foundFirstIncomplete = true
                                nextLessonToContinue = lesson
                                LessonNodeStatus.CURRENT_ACTIVE
                            }
                            else -> LessonNodeStatus.LOCKED
                        }

                        LessonMapNodeUi(
                            lesson = lesson,
                            status = status,
                            starsEarned = prog?.starsEarned ?: 0,
                            bestScorePercent = prog?.bestScorePercent ?: 0
                        )
                    }

                    UnitMapSectionUi(
                        unit = uwl.unit,
                        lessons = nodes,
                        completedCount = completedInUnit
                    )
                }

                val fallbackFirstLesson = sections.firstOrNull()?.lessons?.firstOrNull()?.lesson
                val targetContinue = nextLessonToContinue ?: fallbackFirstLesson

                HomeUiState(
                    isLoading = false,
                    userName = profile?.user?.displayName ?: "متعلم لِسان",
                    streakDays = profile?.streak?.currentStreakDays ?: 0,
                    totalXp = profile?.user?.totalXp ?: 0,
                    todayMinutesLearned = profile?.streak?.todayMinutesLearned ?: 0,
                    dailyGoalMinutes = settings.dailyGoalMinutes,
                    continueLessonId = targetContinue?.id,
                    continueLessonTitleAr = targetContinue?.titleAr.orEmpty(),
                    continueLessonTitleEn = targetContinue?.titleEn.orEmpty(),
                    unitSections = sections
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }
}
