# تطبيق "لِسان (Lisaan)" — المرحلة 8: الاختبارات الشاملة (Unit Tests & Compose UI Tests)، معالجة الحالات وإمكانية الوصول (Accessibility)، قواعد ProGuard/R8، وقائمة النشر على Google Play Store مع سياسة الخصوصية الكاملة

---

## 1. اختبارات الوحدات الشاملة (`Unit Tests`: SM-2، تقييم التمارين والنطق، تحديد المستوى، والـ ViewModels)

### 1.1 قاعدة Coroutines للاختبارات (`MainDispatcherRule.kt`)

```kotlin
package com.lisaan.app.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

### 1.2 اختبارات خوارزمية التكرار المتباعد SM-2 (`CalculateSm2ScheduleUseCaseTest.kt`)

```kotlin
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
```

### 1.3 اختبارات محرك تقييم التمارين ودقة النطق (`EvaluateExerciseAnswerUseCaseTest.kt`)

```kotlin
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
```

### 1.4 اختبارات الـ ViewModels (`OnboardingViewModelTest.kt` & `AuthViewModelTest.kt`)

```kotlin
package com.lisaan.app.presentation.onboarding

import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.domain.usecase.placement.CalculatePlacementLevelUseCase
import com.lisaan.app.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var preferencesDataStore: UserPreferencesDataStore
    private lateinit var analyticsTracker: LisaanAnalyticsTracker
    private lateinit var placementUseCase: CalculatePlacementLevelUseCase
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        preferencesDataStore = mockk(relaxed = true)
        analyticsTracker = mockk(relaxed = true)
        placementUseCase = CalculatePlacementLevelUseCase()
        viewModel = OnboardingViewModel(preferencesDataStore, placementUseCase, analyticsTracker)
    }

    @Test
    fun `stepping through onboarding transitions states in strict order`() = runTest {
        assertEquals(OnboardingStep.INTRO_PAGER, viewModel.uiState.value.currentStep)

        viewModel.finishIntroPager()
        assertEquals(OnboardingStep.INTERFACE_LANGUAGE, viewModel.uiState.value.currentStep)

        viewModel.selectInterfaceLanguage("ar")
        coVerify { preferencesDataStore.setInterfaceLanguage("ar") }
        assertEquals(OnboardingStep.TARGET_LANGUAGE, viewModel.uiState.value.currentStep)

        viewModel.selectTargetLanguage("en")
        assertEquals(OnboardingStep.LEARNING_GOAL, viewModel.uiState.value.currentStep)

        viewModel.selectLearningGoal("WORK")
        assertEquals(OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `answering all 10 placement test questions computes CEFR level B2 when all correct`() = runTest {
        viewModel.startPlacementTest()
        assertEquals(OnboardingStep.PLACEMENT_TEST, viewModel.uiState.value.currentStep)

        val questions = viewModel.uiState.value.placementQuestions
        assertEquals(10, questions.size)

        questions.forEach { q ->
            viewModel.answerPlacementQuestion(q.id, q.correctOptionIndex)
        }

        val result = viewModel.uiState.value.placementResult
        assertNotNull(result)
        assertEquals(10, result?.correctCount)
        assertEquals("B2", result?.assignedLevel)
        assertEquals("B2", viewModel.uiState.value.selectedLevel)
    }
}
```

---

## 2. اختبارات واجهة المستخدم لـ Onboarding (`OnboardingFlowUiTest.kt`)

```kotlin
package com.lisaan.app.presentation.onboarding

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.theme.LisaanTheme
import com.lisaan.app.domain.usecase.placement.CalculatePlacementLevelUseCase
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class OnboardingFlowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingFlow_skipIntro_selectLanguagesAndGoal_reachesLevelSelection() {
        val fakeDataStore = mockk<UserPreferencesDataStore>(relaxed = true)
        val fakeAnalytics = mockk<LisaanAnalyticsTracker>(relaxed = true)
        val viewModel = OnboardingViewModel(
            preferencesDataStore = fakeDataStore,
            placementUseCase = CalculatePlacementLevelUseCase(),
            analyticsTracker = fakeAnalytics
        )

        composeTestRule.setContent {
            LisaanTheme(interfaceLangCode = "ar") {
                OnboardingFlowScreen(
                    onOnboardingCompleteNavigateToAuth = {},
                    viewModel = viewModel
                )
            }
        }

        // 1. التحقق من ظهور الشاشة الترحيبية الأولى وتخطيها
        composeTestRule.onNodeWithText("تخطي").assertIsDisplayed().performClick()

        // 2. اختيار لغة الواجهة العربية
        composeTestRule.onNodeWithText("العربية (الوضع الأصلي RTL)").assertIsDisplayed().performClick()

        // 3. اختيار اللغة الإنجليزية للتعلم
        composeTestRule.onNodeWithText("اللغة الإنجليزية (English)").assertIsDisplayed().performClick()

        // 4. اختيار هدف السفر
        composeTestRule.onNodeWithText("السفر واستكشاف العالم").assertIsDisplayed().performClick()

        // 5. التأكد من الوصول إلى شاشة تحديد المستوى أو الاختبار الذكي (10 أسئلة)
        composeTestRule.onNodeWithText("حدد مستواي عبر اختبار ذكي (10 أسئلة)").assertIsDisplayed()
    }
}
```

---

## 3. المكونات الموحدة لحالات (Loading / Empty / Error) ودعم إمكانية الوصول (`StateViews.kt`)

```kotlin
package com.lisaan.app.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage

@Composable
fun LisaanLoadingView(
    messageAr: String = "جاري التحميل...",
    messageEn: String = "Loading...",
    modifier: Modifier = Modifier
) {
    val isAr = LocalAppLanguage.current == "ar"
    val msg = if (isAr) messageAr else messageEn

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = msg
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NabeehMascotView(mood = NabeehMoodState.THINKING, size = 100.dp)
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = msg,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LisaanEmptyStateView(
    titleAr: String,
    titleEn: String,
    subtitleAr: String,
    subtitleEn: String,
    actionButtonLabelAr: String? = null,
    actionButtonLabelEn: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isAr = LocalAppLanguage.current == "ar"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) titleAr else titleEn,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isAr) subtitleAr else subtitleEn,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onActionClick != null && actionButtonLabelAr != null && actionButtonLabelEn != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.heightIn(min = 48.dp) // WCAG 48dp minimum touch target
            ) {
                Text(if (isAr) actionButtonLabelAr else actionButtonLabelEn)
            }
        }
    }
}

@Composable
fun LisaanErrorStateView(
    errorAr: String,
    errorEn: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAr = LocalAppLanguage.current == "ar"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) errorAr else errorEn,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isAr) "إعادة المحاولة" else "Retry")
        }
    }
}
```

---

## 4. قواعد التقليص والتعتيم للإنتاج (`app/proguard-rules.pro`)

```proguard
# ==============================================================================
# ProGuard / R8 Rules for "Lisaan (لِسان)" Release Builds
# ==============================================================================

# 1. Keep Kotlinx Serialization generated serializers & DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lisaan.app.**$$serializer { *; }
-keepclassmembers class com.lisaan.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.lisaan.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 2. Keep Room Database Entities, DAOs, and TypeConverters
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep interface com.lisaan.app.core.database.dao.** { *; }

# 3. Keep Firestore DTOs (reflection-based serialization)
-keep class com.lisaan.app.data.remote.firestore.** { *; }
-keep class com.lisaan.app.core.database.entities.** { *; }

# 4. Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# 5. Google Play Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.api.** { *; }

# 6. Firebase Crashlytics Stacktrace Line Numbers
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
```

---

## 5. قائمة فحص النشر على Google Play Store (`Play Store Launch Checklist`)

1. **توقيع التطبيق وحزمة النشر (`App Signing & AAB`):**
   * إنشاء مفتاح توقيع الإنتاج (`upload-keystore.jks`) وتفعيل **Play App Signing** في Google Play Console.
   * بناء حزمة الإطلاق عبر الأمر: `./gradlew bundleRelease` والتأكد من نجاح عمل R8/ProGuard وعدم حذف أي كلاسات `Firestore` أو `Kotlinx Serialization`.
   * إضافة بصمات `SHA-1` و`SHA-256` لمفتاح الـ Upload ومفتاح Google Play داخل مشروع Firebase لتفعيل `Google Sign-In` و`Dynamic/App Links`.
2. **نموذج أمان البيانات وحذف الحساب (`Play Console Data Safety & Account Deletion Requirement`):**
   * التصريح عن جمع البريد الإلكتروني والاسم ومعرف المستخدم (للتوثيق ومزامنة التقدم السحابي) والتسجيلات الصوتية الاختيارية (لتصحيحات المجتمع وتقييم النطق).
   * توفير مسار حذف الحساب من داخل التطبيق مباشرة (`الإعدادات -> حذف الحساب والبيانات نهائياً`) + رابط ويب خارجي لطلب حذف الحساب (`https://app.lisaan.com/delete-account`) امتثالاً لسياسة جوجل الإلزامية.
3. **إعداد المنتجات والاشتراكات (`Google Play Billing Products`):**
   * إنشاء الاشتراكات في Play Console بنفس المعرفات البرمجية:
     * `lisaan_plus_monthly` (خطة شهرية متجددة).
     * `lisaan_plus_yearly` (خطة سنوية متجددة).
   * إنشاء منتج الشراء لمرة واحدة (`In-App Product`):
     * `lisaan_plus_lifetime` (ملكية دائمة مدى الحياة).
4. **سياسة المحتوى المولد من المستخدمين (`UGC Policy Compliance` لقسم المجتمع):**
   * تفعيل زر الإبلاغ عن المحتوى المخالف (`🚩 Report & Hide`) وحظر المستخدمين المسيئين، وتضمين شروط الاستخدام قبل أول نشر في المجتمع.
5. **الفحص المسبق للإطلاق (`Pre-Launch Report & Accessibility`):**
   * رفع النسخة إلى مسار **Closed Testing / Internal Testing** ومراجعة تقرير `Firebase Test Lab` في Play Console للتأكد من خلو التطبيق من أي أعطال (`Crash-free rate > 99.5%`) وتوافق التباين وأحجام الأزرار (`>= 48dp`) مع معايير Accessibility في الوضعين RTL وLTR.

---

## 6. وثيقة سياسة الخصوصية الرسمية لتطبيق "لِسان (Lisaan)" (عربي / English)

### أولاً: النسخة العربية

> **سياسة الخصوصية لتطبيق "لِسان (Lisaan)"**  
> **تاريخ السريان:** 1 نوفمبر 2026
>
> يلتزم تطبيق **"لِسان (Lisaan)"** بحماية خصوصية مستخدميه وبياناتهم الشخصية وفقاً لأفضل المعايير الدولية ومتطلبات متجر Google Play.
>
> **1. البيانات التي نجمعها وكيفية استخدامها:**
> * **بيانات الحساب والتوثيق:** عند التسجيل عبر البريد الإلكتروني أو حساب Google، نحفظ الاسم، البريد الإلكتروني، والصورة الرمزية عبر خدمة `Firebase Authentication` لمزامنة تقدمك الدراسي عبر أجهزتك. كما يمكنك استخدام التطبيق في "وضع الضيف" دون تقديم بريد إلكتروني.
> * **بيانات التقدم الدراسي:** يتم حفظ دروسك المكتملة، نقاط الـ XP، سلسلة الأيام المتتالية (Streak)، وجدول المراجعة الذكية (SM-2) محلياً على جهازك في قاعدة بيانات `Room` ومزامنتها بشكل مشفر مع `Cloud Firestore`.
> * **البيانات الصوتية والميكروفون (`RECORD_AUDIO`):** نستخدم صلاحية الميكروفون حصرياً عندما تضغط بنفسك على زر التحدث في تمارين النطق، أو في محادثة المدرب الذكي (AI Coach)، أو عند تسجيل مقطع صوتي لنشره في قسم المجتمع. لا يقوم التطبيق أبداً بالتسجيل في الخلفية.
> * **مدرب المحادثة بالذكاء الاصطناعي (AI Coach):** تُرسل الرسائل النصية ضمن جلسة التدريب عبر اتصال مشفر (`HTTPS/TLS`) إلى خوادمنا لمعالجتها بواسطة نموذج `Claude API` بغرض تقديم التصحيح اللغوي الفوري، ولا تُستخدم محادثاتك لأي أغراض إعلانية.
> * **الفوترة والاشتراكات:** تتم جميع عمليات الدفع والاشتراك عبر نظام `Google Play Billing` الرسمي، ولا يقوم تطبيق "لِسان" بتخزين أو الاطلاع على بيانات بطاقتك البنكية.
> * **التحليلات وتقارير الأعطال:** نستخدم `Firebase Analytics` و`Firebase Crashlytics` لجمع إحصائيات استخدام غير محددة للهوية وتقارير الأعطال التقنية لتحسين استقرار التطبيق.
>
> **2. مشاركة البيانات وحمايتها:**
> لا نبيع أو نؤجر بياناتك الشخصية لأي طرف ثالث. جميع البيانات المنقولة بين التطبيق والخوادم مشفرة بالكامل عبر بروتوكول `TLS`.
>
> **3. حقك في حذف الحساب والبيانات نهائياً:**
> يمكنك في أي وقت حذف حسابك وجميع بياناتك وسجلات تقدمك نهائياً وبشكل فوري من داخل التطبيق عبر الانتقال إلى: **(حسابي ← الإعدادات والخصوصية ← حذف الحساب والبيانات نهائياً)**، أو عبر مراسلتنا على البريد الرسمي: `privacy@lisaan.app`.

---

### ثانياً: النسخة الإنجليزية (English Version)

> **Privacy Policy for "Lisaan (لِسان)"**  
> **Effective Date:** November 1, 2026
>
> **1. Information We Collect & Purpose:**
> * **Account & Authentication Data:** When you sign up via Email or Google Sign-In, we collect your name, email address, and profile photo via `Firebase Authentication` solely to synchronize your learning progress across devices. You may also use Lisaan in Guest Mode without providing an email.
> * **Learning Progress & Spaced Repetition Data:** Completed lessons, XP, daily streaks, and SM-2 vocabulary schedules are stored locally on your device (`Room Database`) and synced securely with `Google Cloud Firestore`.
> * **Audio & Microphone Permission (`RECORD_AUDIO`):** Microphone access is used strictly when you actively tap the microphone button during pronunciation exercises, AI Coach voice chat, or voluntary community voice posts. Lisaan never records audio in the background.
> * **AI Conversation Coach:** Practice messages are transmitted securely over `HTTPS` to our backend proxy powered by the `Claude API` to generate grammar explanations and conversational responses.
> * **Payments & Subscriptions:** All transactions are processed directly by `Google Play Billing`. We never collect or store your credit card details.
>
> **2. Account & Data Deletion:**
> You have full control over your data. You can permanently delete your account and all associated cloud and local records at any time directly inside the app via **(Profile -> Settings & Privacy -> Delete Account Permanently)**, or by contacting us at `privacy@lisaan.app`.
