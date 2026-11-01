# تطبيق "لِسان (Lisaan)" — المرحلة 5: المراجعة الذكية بالتكرار المتباعد (SM-2)، قاموسي والفلاشكاردز، نظام الـ Streak/XP وحماية الشعلة، والألعاب المصغّرة الثلاث

---

## 1. خوارزمية التكرار المتباعد القياسية SM-2 (`CalculateSm2ScheduleUseCase.kt`)

تطبّق هذه الخوارزمية معادلة **SuperMemo SM-2** الأصلية بدقة رياضية لحساب:
* **معامل السهولة الجديد (`Easiness Factor - EF`)** مع حد أدنى `1.3f`:
  $$EF' = \max\left(1.3,\; EF + \left(0.1 - (5 - q) \times (0.08 + (5 - q) \times 0.02)\right)\right)$$
* **عدد التكرارات المتتالية الناجحة (`Repetitions`)**.
* **الفاصل الزمني بالأيام (`Interval Days`)** وموعد المراجعة القادم بالمللي ثانية (`nextReviewEpochMs`).

```kotlin
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
```

---

## 2. محرك الـ Streak، تجميد الشعلة (Streak Freeze)، والـ XP (`StreakAndXpManagerUseCase.kt`)

```kotlin
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
```

---

## 3. شاشات المراجعة الذكية بالتكرار المتباعد (`SmartReviewViewModel.kt` & `SmartReviewScreens.kt`)

### 3.1 الـ ViewModel للمراجعة الذكية (`SmartReviewViewModel.kt`)

```kotlin
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
```

### 3.2 شاشة مركز المراجعة الذكية وجلسة SM-2 (`SmartReviewScreens.kt`)

```kotlin
package com.lisaan.app.presentation.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.core.designsystem.theme.SemanticErrorRed
import com.lisaan.app.domain.usecase.sm2.Sm2UserGrade

@Composable
fun SmartReviewHubTabScreen(
    onStartSm2Session: () -> Unit,
    onOpenDictionary: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenMiniGames: () -> Unit,
    viewModel: SmartReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isAr) "المراجعة الذكية والقاموس" else "Smart Review & Dictionary",
            style = MaterialTheme.typography.headlineLarge
        )

        // 1. بطاقة التكرار المتباعد SM-2
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NabeehMascotView(mood = NabeehMoodState.THINKING, size = 72.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAr) "مراجعة الذاكرة طويلة المدى (SM-2)" else "SM-2 Spaced Repetition",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAr) "لديك ${state.dueCards.size} مفردات مستحقة للمراجعة الآن"
                            else "${state.dueCards.size} words due for review right now",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onStartSm2Session,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "ابدأ جلسة المراجعة الذكية" else "Start Smart Review Session")
                }
            }
        }

        // 2. بطاقة قاموسي
        OutlinedCard(
            onClick = onOpenDictionary,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "قاموسي الشخصي" else "My Personal Dictionary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "ابحث في جميع الكلمات، استمع للنطق، وفلتر بالتصنيفات" else "Search words, listen to audio & filter by category",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        // 3. بطاقة الفلاشكاردز
        OutlinedCard(
            onClick = onOpenFlashcards,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Style, contentDescription = null, tint = OasisEmeraldLight, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "بطاقات الاستذكار (Flashcards)" else "Interactive Flashcards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "اقلب البطاقات واختبر سرعة تذكرك للمعاني والأمثلة" else "Flip cards to test your recall speed",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        // 4. بطاقة الألعاب المصغرة
        OutlinedCard(
            onClick = onOpenMiniGames,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = DesertAmberLight, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "الألعاب اللغوية المصغّرة" else "Mini Language Games",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "المطابقة السريعة • سباق الكلمات • تخمين الصورة" else "Speed Match • Word Sprint • Guess the Picture",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sm2ReviewSessionScreen(
    onBack: () -> Unit,
    viewModel: SmartReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "مراجعة SM-2 الذكية" else "SM-2 Smart Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.isSessionCompleted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NabeehMascotView(mood = NabeehMoodState.CELEBRATE, size = 140.dp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isAr) "أنهيت جميع المراجعات المستحقة!" else "All Due Reviews Completed!",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isAr) "راجعت ${state.reviewedCountInSession} مفردات واكتسبت +${state.sessionEarnedXp} XP"
                    else "Reviewed ${state.reviewedCountInSession} words and earned +${state.sessionEarnedXp} XP",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(if (isAr) "العودة" else "Done")
                }
            }
            return@Scaffold
        }

        val currentItem = state.dueCards.getOrNull(state.currentCardIndex) ?: return@Scaffold
        val vocab = currentItem.vocabulary

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${state.currentCardIndex + 1} / ${state.dueCards.size} • EF: ${"%.2f".format(currentItem.reviewItem.easinessFactor)}",
                style = MaterialTheme.typography.labelLarge
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = vocab.wordTarget,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = vocab.phoneticIpa,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = {
                            viewModel.audioPlayer.playAudioOrFallbackTts(
                                vocab.localAudioPath, vocab.audioUrl, vocab.wordTarget
                            )
                        }
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen", modifier = Modifier.size(34.dp))
                    }

                    if (state.isAnswerRevealed) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                        Text(
                            text = vocab.translationAr,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = OasisEmeraldLight
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = vocab.exampleTarget,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = vocab.exampleAr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!state.isAnswerRevealed) {
                Button(
                    onClick = viewModel::revealCardAnswer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isAr) "إظهار المعنى والمثال" else "Show Answer")
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isAr) "كيف كان تذكرك لهذه المفردة؟" else "How well did you recall this word?",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Sm2UserGrade.entries.forEach { grade ->
                            val btnColor = when (grade) {
                                Sm2UserGrade.AGAIN -> SemanticErrorRed
                                Sm2UserGrade.HARD -> DesertAmberLight
                                Sm2UserGrade.GOOD -> MaterialTheme.colorScheme.primary
                                Sm2UserGrade.EASY -> OasisEmeraldLight
                            }
                            Button(
                                onClick = { viewModel.submitCardGrade(grade) },
                                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                contentPadding = PaddingValues(4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isAr) grade.labelAr else grade.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

---

## 4. قاموسي الشخصي وبطاقات الاستذكار (`MyDictionaryViewModel.kt` & `MyDictionaryAndFlashcardsScreens.kt`)

```kotlin
package com.lisaan.app.presentation.dictionary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.database.dao.VocabularyDao
import com.lisaan.app.core.database.entities.VocabularyEntity
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.media.LisaanAudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyDictionaryViewModel @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    val audioPlayer: LisaanAudioPlayer
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val onlyBookmarked = MutableStateFlow(false)

    val queryState: StateFlow<String> = searchQuery.asStateFlow()
    val categoryState: StateFlow<String?> = selectedCategory.asStateFlow()
    val bookmarkedOnlyState: StateFlow<Boolean> = onlyBookmarked.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val vocabulariesFlow: StateFlow<List<VocabularyEntity>> = combine(
        searchQuery,
        selectedCategory,
        onlyBookmarked
    ) { q, cat, bookmarked ->
        Triple(q, cat, bookmarked)
    }.flatMapLatest { (q, cat, bookmarked) ->
        vocabularyDao.searchVocabularies(
            query = q.trim(),
            category = cat,
            onlyBookmarked = bookmarked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun onSelectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun onToggleBookmarkedFilter() {
        onlyBookmarked.value = !onlyBookmarked.value
    }

    fun toggleWordBookmark(vocab: VocabularyEntity) {
        viewModelScope.launch {
            vocabularyDao.setBookmarkStatus(vocab.id, !vocab.isBookmarked)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDictionaryScreen(
    onBack: () -> Unit,
    viewModel: MyDictionaryViewModel = hiltViewModel()
) {
    val vocabList by viewModel.vocabulariesFlow.collectAsState()
    val query by viewModel.queryState.collectAsState()
    val selectedCat by viewModel.categoryState.collectAsState()
    val onlySaved by viewModel.bookmarkedOnlyState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    val categories = listOf(
        null to if (isAr) "الكل" else "All",
        "GREETINGS" to if (isAr) "تحيات وتعارف" else "Greetings",
        "TRAVEL" to if (isAr) "سفر وفنادق" else "Travel",
        "FOOD" to if (isAr) "مقهى ومطاعم" else "Food & Café",
        "WORK" to if (isAr) "عمل واجتماعات" else "Work",
        "DAILY" to if (isAr) "حياة يومية" else "Daily Life"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "قاموسي (${vocabList.size})" else "My Dictionary (${vocabList.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    FilterChip(
                        selected = onlySaved,
                        onClick = viewModel::onToggleBookmarkedFilter,
                        label = { Text(if (isAr) "المحفوظة" else "Saved") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (onlySaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text(if (isAr) "ابحث بالعربية أو الإنجليزية أو النطق..." else "Search English or Arabic...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (catCode, label) ->
                    FilterChip(
                        selected = selectedCat == catCode,
                        onClick = { viewModel.onSelectCategory(catCode) },
                        label = { Text(label) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vocabList, key = { it.id }) { vocab ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    viewModel.audioPlayer.playAudioOrFallbackTts(
                                        vocab.localAudioPath, vocab.audioUrl, vocab.wordTarget
                                    )
                                }
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${vocab.wordTarget} — ${vocab.translationAr}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${vocab.phoneticIpa} • ${vocab.transliteration}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = vocab.exampleTarget,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { viewModel.toggleWordBookmark(vocab) }) {
                                Icon(
                                    imageVector = if (vocab.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsDeckScreen(
    onBack: () -> Unit,
    viewModel: MyDictionaryViewModel = hiltViewModel()
) {
    val vocabList by viewModel.vocabulariesFlow.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val rotationY by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400),
        label = "flip_card"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "بطاقات الفلاشكاردز" else "Flashcards Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (vocabList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val currentVocab = vocabList[currentIndex.coerceIn(0, vocabList.lastIndex)]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${currentIndex + 1} / ${vocabList.size}",
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .graphicsLayer {
                        this.rotationY = rotationY
                        cameraDistance = 12f * density
                    }
                    .clickable { isFlipped = !isFlipped },
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotationY <= 90f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentVocab.wordTarget,
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = currentVocab.phoneticIpa, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (isAr) "اضغط لقلب البطاقة ↻" else "Tap to flip card ↻",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.graphicsLayer { this.rotationY = 180f },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentVocab.translationAr,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentVocab.exampleTarget,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentVocab.exampleAr,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        isFlipped = false
                        if (currentIndex > 0) currentIndex--
                    },
                    enabled = currentIndex > 0
                ) {
                    Text(if (isAr) "السابقة" else "Previous")
                }

                FilledTonalIconButton(
                    onClick = {
                        viewModel.audioPlayer.playAudioOrFallbackTts(
                            currentVocab.localAudioPath, currentVocab.audioUrl, currentVocab.wordTarget
                        )
                    }
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                }

                Button(
                    onClick = {
                        isFlipped = false
                        if (currentIndex < vocabList.lastIndex) currentIndex++ else currentIndex = 0
                    }
                ) {
                    Text(if (isAr) "التالية" else "Next")
                }
            }
        }
    }
}
```

---

## 5. الألعاب المصغّرة الثلاث (`MiniGamesScreens.kt`: مطابقة سريعة، سباق كلمات، تخمين الصورة)

```kotlin
package com.lisaan.app.presentation.minigames

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.presentation.dictionary.MyDictionaryViewModel
import kotlinx.coroutines.delay

enum class MiniGameMode { HUB, SPEED_MATCH, WORD_SPRINT, GUESS_PICTURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniGamesHubAndPlayerScreen(
    onBack: () -> Unit,
    viewModel: MyDictionaryViewModel = hiltViewModel()
) {
    val vocabList by viewModel.vocabulariesFlow.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var currentMode by remember { mutableStateOf(MiniGameMode.HUB) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentMode) {
                            MiniGameMode.HUB -> if (isAr) "الألعاب اللغوية المصغّرة" else "Mini Language Games"
                            MiniGameMode.SPEED_MATCH -> if (isAr) "المطابقة السريعة ⏱️" else "Speed Match ⏱️"
                            MiniGameMode.WORD_SPRINT -> if (isAr) "سباق الكلمات 🚀" else "Word Sprint 🚀"
                            MiniGameMode.GUESS_PICTURE -> if (isAr) "تخمين الصورة 🖼️" else "Guess the Picture 🖼️"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentMode == MiniGameMode.HUB) onBack() else currentMode = MiniGameMode.HUB
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            when (currentMode) {
                MiniGameMode.HUB -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GameEntryCard(
                            emoji = "⚡",
                            title = if (isAr) "1. المطابقة السريعة (Speed Match)" else "1. Speed Match",
                            desc = if (isAr) "وصّل أكبر عدد من الكلمات بمعانيها خلال 30 ثانية!" else "Match as many word pairs as possible in 30 seconds!",
                            onClick = { currentMode = MiniGameMode.SPEED_MATCH }
                        )
                        GameEntryCard(
                            emoji = "🏃",
                            title = if (isAr) "2. سباق الكلمات (Word Sprint)" else "2. Word Sprint",
                            desc = if (isAr) "حدّد هل الترجمة المعروضة صحيحة أم خاطئة قبل نفاد العداد!" else "Decide if the translation is right or wrong before time runs out!",
                            onClick = { currentMode = MiniGameMode.WORD_SPRINT }
                        )
                        GameEntryCard(
                            emoji = "🖼️",
                            title = if (isAr) "3. تخمين الصورة والرمز (Picture Guess)" else "3. Guess the Picture",
                            desc = if (isAr) "اختر المفردة الإنجليزية المعبرة عن المشهد المصوّر والمنطوق" else "Pick the English phrase that matches the visual scene",
                            onClick = { currentMode = MiniGameMode.GUESS_PICTURE }
                        )
                    }
                }

                // اللعبة 1: المطابقة السريعة ضد الوقت
                MiniGameMode.SPEED_MATCH -> {
                    var timeLeft by remember { mutableIntStateOf(30) }
                    var score by remember { mutableIntStateOf(0) }
                    var roundSeed by remember { mutableIntStateOf(0) }
                    val sampleFour = remember(vocabList, roundSeed) { vocabList.shuffled().take(4) }
                    var selectedEn by remember { mutableStateOf<String?>(null) }
                    var matchedIds by remember { mutableStateOf(setOf<String>()) }

                    LaunchedEffect(timeLeft) {
                        if (timeLeft > 0) {
                            delay(1000L)
                            timeLeft--
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⏱️ ${timeLeft}s", style = MaterialTheme.typography.headlineMedium, color = DesertAmberLight)
                            Text("🏆 $score", style = MaterialTheme.typography.headlineMedium, color = OasisEmeraldLight)
                        }

                        if (timeLeft == 0) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isAr) "انتهى الوقت! نتيجتك: $score" else "Time's Up! Score: $score",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { timeLeft = 30; score = 0; matchedIds = emptySet(); roundSeed++ }) {
                                    Text(if (isAr) "العب مجدداً" else "Play Again")
                                }
                            }
                        } else {
                            val rightShuffled = remember(sampleFour) { sampleFour.shuffled() }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sampleFour.forEachIndexed { idx, leftItem ->
                                    val rightItem = rightShuffled[idx]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { selectedEn = leftItem.id },
                                            enabled = !matchedIds.contains(leftItem.id),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(leftItem.wordTarget)
                                        }
                                        Button(
                                            onClick = {
                                                if (selectedEn == rightItem.id) {
                                                    score += 10
                                                    matchedIds = matchedIds + rightItem.id
                                                    selectedEn = null
                                                    if (matchedIds.size == sampleFour.size) {
                                                        matchedIds = emptySet()
                                                        roundSeed++
                                                    }
                                                } else {
                                                    selectedEn = null
                                                }
                                            },
                                            enabled = !matchedIds.contains(rightItem.id),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(rightItem.translationAr)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // اللعبة 2: سباق الكلمات السريع
                MiniGameMode.WORD_SPRINT -> {
                    var score by remember { mutableIntStateOf(0) }
                    var streakCombo by remember { mutableIntStateOf(0) }
                    var step by remember { mutableIntStateOf(0) }

                    val currentPair = remember(vocabList, step) {
                        if (vocabList.size < 2) null else {
                            val target = vocabList.random()
                            val showCorrect = (step % 2 == 0)
                            val displayedAr = if (showCorrect) target.translationAr else vocabList.first { it.id != target.id }.translationAr
                            Triple(target.wordTarget, displayedAr, showCorrect)
                        }
                    }

                    currentPair?.let { (wordEn, displayedAr, isActuallyCorrect) ->
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔥 Combo x$streakCombo • 🏆 $score",
                                style = MaterialTheme.typography.headlineMedium
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = wordEn, style = MaterialTheme.typography.displayLarge)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(text = "⬇️", style = MaterialTheme.typography.headlineMedium)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = displayedAr,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isActuallyCorrect) { score += 15; streakCombo++ } else streakCombo = 0
                                        step++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                ) {
                                    Text(if (isAr) "✗ خاطئة" else "✗ False")
                                }
                                Button(
                                    onClick = {
                                        if (isActuallyCorrect) { score += 15; streakCombo++ } else streakCombo = 0
                                        step++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                ) {
                                    Text(if (isAr) "✓ صحيحة" else "✓ True")
                                }
                            }
                        }
                    }
                }

                // اللعبة 3: تخمين الصورة والمشهد
                MiniGameMode.GUESS_PICTURE -> {
                    var score by remember { mutableIntStateOf(0) }
                    var round by remember { mutableIntStateOf(0) }
                    val options = remember(vocabList, round) { vocabList.shuffled().take(4) }
                    val target = remember(options) { options.firstOrNull() }

                    target?.let { correctVocab ->
                        val sceneEmoji = when (correctVocab.categoryTag) {
                            "GREETINGS" -> "👋🌅"
                            "TRAVEL" -> "✈️🏨🗺️"
                            "FOOD" -> "☕🍽️🥗"
                            "WORK" -> "💼📊🤝"
                            else -> "🏡⏰👨‍👩‍👧‍👦"
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🏆 Score: $score", style = MaterialTheme.typography.headlineMedium)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = sceneEmoji, style = MaterialTheme.typography.displayLarge)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = correctVocab.translationAr,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = correctVocab.exampleAr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                options.shuffled().forEach { option ->
                                    OutlinedButton(
                                        onClick = {
                                            if (option.id == correctVocab.id) {
                                                score += 20
                                                viewModel.audioPlayer.playAudioOrFallbackTts(
                                                    option.localAudioPath, option.audioUrl, option.wordTarget
                                                )
                                            }
                                            round++
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                    ) {
                                        Text(option.wordTarget, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameEntryCard(
    emoji: String,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```
