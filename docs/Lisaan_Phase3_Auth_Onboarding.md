# تطبيق "لِسان (Lisaan)" — المرحلة 3: التوثيق الكامل (Firebase Auth) وتجربة الإعداد الأولي (Onboarding + اختبار تحديد المستوى 10 أسئلة)

---

## 1. تتبع التحليلات ومسارات التحويل (`LisaanAnalyticsTracker.kt`)

```kotlin
package com.lisaan.app.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanAnalyticsTracker @Inject constructor(
    private val firebaseAnalytics: FirebaseAnalytics
) {
    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap()) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putString(key, value.toString())
                }
            }
        }
        firebaseAnalytics.logEvent(eventName, bundle)
    }

    // Onboarding Funnel Events
    fun logOnboardingStep(stepName: String, selectionValue: String = "") {
        logEvent(
            "onboarding_step_completed",
            mapOf("step_name" to stepName, "selection" to selectionValue)
        )
    }

    fun logPlacementTestCompleted(score: Int, total: Int, assignedLevel: String) {
        logEvent(
            "placement_test_completed",
            mapOf("score" to score, "total" to total, "assigned_level" to assignedLevel)
        )
    }

    // Auth Funnel Events
    fun logAuthSuccess(method: String, isNewUser: Boolean) {
        logEvent(
            "auth_success",
            mapOf("method" to method, "is_new_user" to isNewUser)
        )
    }

    fun logAccountDeleted(userId: String) {
        logEvent("account_deleted", mapOf("user_id" to userId))
    }
}
```

---

## 2. طبقة التوثيق وإدارة الحسابات (Domain & Data Layer - `AuthRepository`)

### 2.1 غلاف النتائج الموحد (`Resource.kt`) وواجهة المستودع (`AuthRepository.kt`)

```kotlin
package com.lisaan.app.core.common

sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val messageAr: String, val messageEn: String, val throwable: Throwable? = null) : Resource<Nothing>()
}
```

```kotlin
package com.lisaan.app.domain.repository

import com.lisaan.app.core.common.Resource
import com.lisaan.app.core.database.entities.UserEntity
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUserFlow: Flow<UserEntity?>
    suspend fun signInWithEmail(email: String, password: String): Resource<UserEntity>
    suspend fun signUpWithEmail(displayName: String, email: String, password: String): Resource<UserEntity>
    suspend fun signInWithGoogleIdToken(idToken: String): Resource<UserEntity>
    suspend fun continueAsGuest(): Resource<UserEntity>
    suspend fun sendPasswordResetEmail(email: String): Resource<Unit>
    suspend fun signOut(): Resource<Unit>
    suspend fun deleteAccountPermanently(passwordForReauth: String? = null): Resource<Unit>
}
```

### 2.2 التنفيذ الكامل لمستودع التوثيق (`AuthRepositoryImpl.kt`)

يدعم تسجيل البريد، Google Sign-In، الدخول كضيف (Anonymous Auth مع إمكانية الترقية لاحقاً)، استعادة كلمة المرور، وحذف الحساب نهائياً من Firebase Auth وFirestore وRoom:

```kotlin
package com.lisaan.app.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.common.DispatcherProvider
import com.lisaan.app.core.common.Resource
import com.lisaan.app.core.database.LisaanDatabase
import com.lisaan.app.core.database.dao.ProgressAndStreakDao
import com.lisaan.app.core.database.dao.SubscriptionDao
import com.lisaan.app.core.database.dao.UserDao
import com.lisaan.app.core.database.entities.StreakEntity
import com.lisaan.app.core.database.entities.SubscriptionEntity
import com.lisaan.app.core.database.entities.UserEntity
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.data.remote.firestore.FirestoreUserDto
import com.lisaan.app.domain.repository.AuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val database: LisaanDatabase,
    private val userDao: UserDao,
    private val streakDao: ProgressAndStreakDao,
    private val subscriptionDao: SubscriptionDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val analyticsTracker: LisaanAnalyticsTracker,
    private val dispatchers: DispatcherProvider
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentUserFlow: Flow<UserEntity?> =
        preferencesDataStore.settingsFlow.flatMapLatest { settings ->
            val uid = settings.currentUserId
            if (uid.isNullOrBlank()) flowOf(null) else userDao.observeUser(uid)
        }

    override suspend fun signInWithEmail(email: String, password: String): Resource<UserEntity> =
        withContext(dispatchers.io) {
            try {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
                val firebaseUser = authResult.user
                    ?: return@withContext Resource.Error("تعذر العثور على بيانات المستخدم", "User not found")

                val userEntity = syncOrCreateUserRecord(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName ?: email.substringBefore("@"),
                    avatarUrl = firebaseUser.photoUrl?.toString(),
                    isGuest = false
                )
                analyticsTracker.logAuthSuccess(method = "email_sign_in", isNewUser = false)
                Resource.Success(userEntity)
            } catch (e: Exception) {
                Resource.Error(
                    messageAr = "بيانات الدخول غير صحيحة أو حدث خطأ في الاتصال: ${e.localizedMessage ?: ""}",
                    messageEn = "Invalid credentials or connection error: ${e.localizedMessage ?: ""}",
                    throwable = e
                )
            }
        }

    override suspend fun signUpWithEmail(
        displayName: String,
        email: String,
        password: String
    ): Resource<UserEntity> = withContext(dispatchers.io) {
        try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = authResult.user
                ?: return@withContext Resource.Error("تعذر إنشاء الحساب", "Failed to create account")

            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val userEntity = syncOrCreateUserRecord(
                uid = firebaseUser.uid,
                email = firebaseUser.email,
                displayName = displayName.trim(),
                avatarUrl = null,
                isGuest = false
            )
            analyticsTracker.logAuthSuccess(method = "email_sign_up", isNewUser = true)
            Resource.Success(userEntity)
        } catch (e: Exception) {
            Resource.Error(
                messageAr = "تعذر إنشاء الحساب، تأكد من صلاحية البريد وأن كلمة المرور 6 أحرف على الأقل",
                messageEn = "Could not create account. Verify email and password length (min 6 chars).",
                throwable = e
            )
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Resource<UserEntity> =
        withContext(dispatchers.io) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val firebaseUser = authResult.user
                    ?: return@withContext Resource.Error("فشل تسجيل الدخول بحساب جوجل", "Google Sign-In failed")

                val isNew = authResult.additionalUserInfo?.isNewUser ?: false
                val userEntity = syncOrCreateUserRecord(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email,
                    displayName = firebaseUser.displayName ?: "متعلم لِسان",
                    avatarUrl = firebaseUser.photoUrl?.toString(),
                    isGuest = false
                )
                analyticsTracker.logAuthSuccess(method = "google", isNewUser = isNew)
                Resource.Success(userEntity)
            } catch (e: Exception) {
                Resource.Error(
                    messageAr = "تعذر إتمام الدخول عبر جوجل: ${e.localizedMessage ?: ""}",
                    messageEn = "Google Sign-In failed: ${e.localizedMessage ?: ""}",
                    throwable = e
                )
            }
        }

    override suspend fun continueAsGuest(): Resource<UserEntity> = withContext(dispatchers.io) {
        try {
            val uid = try {
                val anonResult = firebaseAuth.signInAnonymously().await()
                anonResult.user?.uid ?: "guest_${UUID.randomUUID()}"
            } catch (_: Exception) {
                // يدعم الدخول كضيف حتى لو كان المستخدم أوفلاين بالكامل عند أول تشغيل
                "guest_${UUID.randomUUID()}"
            }

            val userEntity = syncOrCreateUserRecord(
                uid = uid,
                email = null,
                displayName = "ضيف لِسان",
                avatarUrl = null,
                isGuest = true
            )
            analyticsTracker.logAuthSuccess(method = "guest", isNewUser = true)
            Resource.Success(userEntity)
        } catch (e: Exception) {
            Resource.Error(
                messageAr = "حدث خطأ أثناء إعداد حساب الضيف",
                messageEn = "Failed to initialize guest session",
                throwable = e
            )
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Resource<Unit> =
        withContext(dispatchers.io) {
            try {
                firebaseAuth.sendPasswordResetEmail(email.trim()).await()
                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error(
                    messageAr = "تعذر إرسال رابط استعادة كلمة المرور، تحقق من البريد الإلكتروني المدخل",
                    messageEn = "Failed to send password reset link. Please check your email address.",
                    throwable = e
                )
            }
        }

    override suspend fun signOut(): Resource<Unit> = withContext(dispatchers.io) {
        try {
            firebaseAuth.signOut()
            preferencesDataStore.clearUserSession()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(
                messageAr = "حدث خطأ أثناء تسجيل الخروج",
                messageEn = "Error signing out",
                throwable = e
            )
        }
    }

    override suspend fun deleteAccountPermanently(passwordForReauth: String?): Resource<Unit> =
        withContext(dispatchers.io) {
            try {
                val settings = preferencesDataStore.settingsFlow.first()
                val uid = settings.currentUserId
                val firebaseUser = firebaseAuth.currentUser

                // 1. Re-authenticate if email/password provided
                if (firebaseUser != null && !passwordForReauth.isNullOrBlank() && !firebaseUser.email.isNullOrBlank()) {
                    val credential = EmailAuthProvider.getCredential(firebaseUser.email!!, passwordForReauth)
                    firebaseUser.reauthenticate(credential).await()
                }

                // 2. Delete remote Firestore user document & subcollections
                if (!uid.isNullOrBlank() && !settings.isGuestUser) {
                    runCatching {
                        firestore.collection("users").document(uid).delete().await()
                    }
                }

                // 3. Delete Firebase Auth user
                if (firebaseUser != null) {
                    runCatching { firebaseUser.delete().await() }
                }

                // 4. Clear local Room user records & DataStore session
                if (!uid.isNullOrBlank()) {
                    userDao.deleteUserById(uid)
                    analyticsTracker.logAccountDeleted(uid)
                }
                preferencesDataStore.clearUserSession()

                Resource.Success(Unit)
            } catch (e: Exception) {
                Resource.Error(
                    messageAr = "يتطلب حذف الحساب إعادة تسجيل الدخول حديثاً لتأكيد هويتك ثم المحاولة مرة أخرى",
                    messageEn = "Deleting your account requires recent sign-in for security verification.",
                    throwable = e
                )
            }
        }

    private suspend fun syncOrCreateUserRecord(
        uid: String,
        email: String?,
        displayName: String,
        avatarUrl: String?,
        isGuest: Boolean
    ): UserEntity {
        val prefs = preferencesDataStore.settingsFlow.first()

        // فحص ما إذا كان للمستخدم وثيقة محفوظة على Firestore
        val remoteUserDto = if (!isGuest) {
            runCatching {
                firestore.collection("users").document(uid).get().await()
                    .toObject(FirestoreUserDto::class.java)
            }.getOrNull()
        } else null

        val userEntity = remoteUserDto?.toEntity(isGuest = isGuest) ?: UserEntity(
            id = uid,
            email = email,
            displayName = displayName,
            avatarUrl = avatarUrl,
            isGuest = isGuest,
            interfaceLang = prefs.interfaceLanguage,
            currentCourseId = "${prefs.targetLanguage}_from_ar",
            learningGoal = prefs.learningGoal,
            proficiencyLevel = prefs.proficiencyLevel,
            dailyGoalMinutes = prefs.dailyGoalMinutes,
            totalXp = 0,
            currentLeague = "BRONZE"
        )

        userDao.upsertUser(userEntity)

        // تهيئة الـ Streak والاشتراك المجاني الافتراضي إن لم يكونا موجودين
        if (streakDao.getStreakSnapshot(uid) == null) {
            streakDao.upsertStreak(StreakEntity(userId = uid))
        }
        if (subscriptionDao.getSubscription(uid) == null) {
            subscriptionDao.upsertSubscription(
                SubscriptionEntity(userId = uid, planId = "free", tier = "FREE", status = "ACTIVE")
            )
        }

        // رفع بيانات المستخدم الأولية إلى Firestore إن كان حساباً مسجلاً
        if (!isGuest) {
            runCatching {
                firestore.collection("users").document(uid)
                    .set(FirestoreUserDto.fromEntity(userEntity)).await()
            }
        }

        preferencesDataStore.setAuthenticatedUser(userId = uid, isGuest = isGuest)
        return userEntity
    }
}
```

---

## 3. خوارزمية وأسئلة اختبار تحديد المستوى الأصلي (10-Question Placement Test)

### 3.1 نموذج ومحرك تقييم اختبار تحديد المستوى (`CalculatePlacementLevelUseCase.kt`)

يحتوي على 10 أسئلة أصلية متدرجة الصعوبة (من A1 حتى B2) مع أوزان نقطية، ويحسب مستوى المتعلم بدقة ليفتح له الوحدات المناسبة:

```kotlin
package com.lisaan.app.domain.usecase.placement

import javax.inject.Inject

data class PlacementQuestion(
    val id: Int,
    val cefrBand: String, // "A1", "A2", "B1", "B2"
    val weightPoints: Int,
    val promptAr: String,
    val promptEn: String,
    val sentenceContext: String,
    val options: List<String>,
    val correctOptionIndex: Int
)

data class PlacementEvaluationResult(
    val correctCount: Int,
    val totalQuestions: Int,
    val weightedScorePercent: Int,
    val assignedLevel: String, // "A1", "A2", "B1", "B2"
    val feedbackAr: String,
    val feedbackEn: String
)

class CalculatePlacementLevelUseCase @Inject constructor() {

    fun getPlacementQuestions(): List<PlacementQuestion> = listOf(
        // A1 Questions (Weight = 1)
        PlacementQuestion(
            id = 1,
            cefrBand = "A1",
            weightPoints = 1,
            promptAr = "اختر الكلمة الصحيحة لإكمال التحية الصباحية:",
            promptEn = "Choose the correct word to complete the morning greeting:",
            sentenceContext = "Good ____! How are you today?",
            options = listOf("morning", "night", "goodbye", "tomorrow"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 2,
            cefrBand = "A1",
            weightPoints = 1,
            promptAr = "اختر الفعل المساعد المناسب للفاعل:",
            promptEn = "Select the correct verb form:",
            sentenceContext = "She ____ a software engineer from Cairo.",
            options = listOf("am", "is", "are", "be"),
            correctOptionIndex = 1
        ),
        PlacementQuestion(
            id = 3,
            cefrBand = "A1",
            weightPoints = 1,
            promptAr = "ما الترجمة الإنجليزية الصحيحة لجملة: (أريد فنجان قهوة من فضلك)؟",
            promptEn = "Which sentence means 'أريد فنجان قهوة من فضلك'?",
            sentenceContext = "Ordering at a café:",
            options = listOf(
                "I would like a cup of coffee, please.",
                "I am drinking coffee yesterday.",
                "Where is the coffee shop?",
                "Coffee is very hot."
            ),
            correctOptionIndex = 0
        ),
        // A2 Questions (Weight = 2)
        PlacementQuestion(
            id = 4,
            cefrBand = "A2",
            weightPoints = 2,
            promptAr = "اختر صيغة الماضي الصحيحة للفعل:",
            promptEn = "Choose the correct past tense form:",
            sentenceContext = "We ____ to Alexandria by train last weekend.",
            options = listOf("travel", "traveled", "traveling", "will travel"),
            correctOptionIndex = 1
        ),
        PlacementQuestion(
            id = 5,
            cefrBand = "A2",
            weightPoints = 2,
            promptAr = "اختر صيغة المقارنة الصحيحة:",
            promptEn = "Select the correct comparative adjective:",
            sentenceContext = "Learning with daily practice is ____ than cramming once a month.",
            options = listOf("more effective", "most effective", "effective", "as effective"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 6,
            cefrBand = "A2",
            weightPoints = 2,
            promptAr = "اختر حرف الجر المناسب مع الوقت:",
            promptEn = "Choose the right preposition:",
            sentenceContext = "Our flight departs ____ 7:30 PM from Terminal 2.",
            options = listOf("in", "on", "at", "to"),
            correctOptionIndex = 2
        ),
        // B1 Questions (Weight = 3)
        PlacementQuestion(
            id = 7,
            cefrBand = "B1",
            weightPoints = 3,
            promptAr = "اختر صيغة المضارع التام المناسبة:",
            promptEn = "Choose the correct present perfect form:",
            sentenceContext = "I ____ in this company for three years so far.",
            options = listOf("have worked", "worked", "am working", "had work"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 8,
            cefrBand = "B1",
            weightPoints = 3,
            promptAr = "أكمل الجملة الشرطية من النوع الأول:",
            promptEn = "Complete the first conditional sentence:",
            sentenceContext = "If we finish the report early, we ____ the client this afternoon.",
            options = listOf("will call", "called", "would have called", "calling"),
            correctOptionIndex = 0
        ),
        // B2 Questions (Weight = 4)
        PlacementQuestion(
            id = 9,
            cefrBand = "B2",
            weightPoints = 4,
            promptAr = "اختر التركيب الصحيح للجملة الشرطية التخيلية:",
            promptEn = "Choose the correct second conditional structure:",
            sentenceContext = "If I ____ more free time, I would learn a third language.",
            options = listOf("had", "have", "will have", "would have"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 10,
            cefrBand = "B2",
            weightPoints = 4,
            promptAr = "اختر الرابط المنطقي الدال على التناقض:",
            promptEn = "Select the appropriate connector of contrast:",
            sentenceContext = "____ the heavy rain, the outdoor cultural festival continued on schedule.",
            options = listOf("Despite", "Although", "Because", "Unless"),
            correctOptionIndex = 0
        )
    )

    fun evaluatePlacement(selectedAnswers: Map<Int, Int>): PlacementEvaluationResult {
        val questions = getPlacementQuestions()
        var correctCount = 0
        var earnedWeight = 0
        val maxWeight = questions.sumOf { it.weightPoints } // 23 points

        questions.forEach { q ->
            if (selectedAnswers[q.id] == q.correctOptionIndex) {
                correctCount++
                earnedWeight += q.weightPoints
            }
        }

        val scorePercent = ((earnedWeight.toFloat() / maxWeight.toFloat()) * 100).toInt()

        val (level, feedbackAr, feedbackEn) = when {
            scorePercent >= 80 -> Triple(
                "B2",
                "ممتاز! لديك حصيلة لغوية متقدمة، سنبدأ معك من التراكيب المتقدمة والمحادثات الطليقة.",
                "Awesome! You have an upper-intermediate command. We'll unlock advanced units for you."
            )
            scorePercent >= 55 -> Triple(
                "B1",
                "رائع! أساسياتك قوية، سنضعك في المستوى المتوسط لتطوير طلاقتك في المواقف اليومية والعملية.",
                "Great job! Your foundation is strong. You'll start at the intermediate level."
            )
            scorePercent >= 30 -> Triple(
                "A2",
                "بداية جيدة! تعرف المفردات الأساسية، وسنبني معاً الجمل والحوارات خطوة بخطوة.",
                "Good start! You know key basics, and we'll build full conversations together."
            )
            else -> Triple(
                "A1",
                "أهلاً بك في بداية الرحلة! سنبدأ من الصفر بأسلوب ممتع ومبسط حتى تتحدث بثقة.",
                "Welcome aboard! We'll start from scratch at a comfortable, clear pace."
            )
        }

        return PlacementEvaluationResult(
            correctCount = correctCount,
            totalQuestions = questions.size,
            weightedScorePercent = scorePercent,
            assignedLevel = level,
            feedbackAr = feedbackAr,
            feedbackEn = feedbackEn
        )
    }
}
```

---

## 4. إدارة الحالة وشاشات الـ Onboarding الكاملة (`OnboardingViewModel.kt` & `OnboardingScreens.kt`)

### 4.1 الـ ViewModel الخاص بالـ Onboarding (`OnboardingViewModel.kt`)

```kotlin
package com.lisaan.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.domain.usecase.placement.CalculatePlacementLevelUseCase
import com.lisaan.app.domain.usecase.placement.PlacementEvaluationResult
import com.lisaan.app.domain.usecase.placement.PlacementQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    INTRO_PAGER,
    INTERFACE_LANGUAGE,
    TARGET_LANGUAGE,
    LEARNING_GOAL,
    LEVEL_OR_PLACEMENT_CHOICE,
    PLACEMENT_TEST,
    DAILY_GOAL,
    NOTIFICATIONS_OPT_IN
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.INTRO_PAGER,
    val interfaceLang: String = "ar",
    val targetLang: String = "en",
    val selectedGoal: String = "TRAVEL", // TRAVEL, WORK, STUDY, HOBBY
    val selectedLevel: String = "A1",
    val placementQuestions: List<PlacementQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val placementAnswers: Map<Int, Int> = emptyMap(),
    val placementResult: PlacementEvaluationResult? = null,
    val dailyGoalMinutes: Int = 10, // 5, 10, 15, 20
    val notificationsEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val placementUseCase: CalculatePlacementLevelUseCase,
    private val analyticsTracker: LisaanAnalyticsTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingUiState(placementQuestions = placementUseCase.getPlacementQuestions())
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun finishIntroPager() {
        analyticsTracker.logOnboardingStep("intro_pager_completed")
        _uiState.update { it.copy(currentStep = OnboardingStep.INTERFACE_LANGUAGE) }
    }

    fun selectInterfaceLanguage(langCode: String) {
        viewModelScope.launch {
            preferencesDataStore.setInterfaceLanguage(langCode)
            analyticsTracker.logOnboardingStep("interface_language", langCode)
            _uiState.update {
                it.copy(
                    interfaceLang = langCode,
                    currentStep = OnboardingStep.TARGET_LANGUAGE
                )
            }
        }
    }

    fun selectTargetLanguage(targetLangCode: String) {
        analyticsTracker.logOnboardingStep("target_language", targetLangCode)
        _uiState.update {
            it.copy(
                targetLang = targetLangCode,
                currentStep = OnboardingStep.LEARNING_GOAL
            )
        }
    }

    fun selectLearningGoal(goalCode: String) {
        analyticsTracker.logOnboardingStep("learning_goal", goalCode)
        _uiState.update {
            it.copy(
                selectedGoal = goalCode,
                currentStep = OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE
            )
        }
    }

    fun chooseManualLevel(levelCode: String) {
        analyticsTracker.logOnboardingStep("manual_level_selected", levelCode)
        _uiState.update {
            it.copy(
                selectedLevel = levelCode,
                currentStep = OnboardingStep.DAILY_GOAL
            )
        }
    }

    fun startPlacementTest() {
        analyticsTracker.logOnboardingStep("placement_test_started")
        _uiState.update {
            it.copy(
                currentQuestionIndex = 0,
                placementAnswers = emptyMap(),
                placementResult = null,
                currentStep = OnboardingStep.PLACEMENT_TEST
            )
        }
    }

    fun answerPlacementQuestion(questionId: Int, optionIndex: Int) {
        val currentState = _uiState.value
        val updatedAnswers = currentState.placementAnswers.toMutableMap().apply {
            put(questionId, optionIndex)
        }

        if (currentState.currentQuestionIndex < currentState.placementQuestions.lastIndex) {
            _uiState.update {
                it.copy(
                    placementAnswers = updatedAnswers,
                    currentQuestionIndex = it.currentQuestionIndex + 1
                )
            }
        } else {
            val evalResult = placementUseCase.evaluatePlacement(updatedAnswers)
            analyticsTracker.logPlacementTestCompleted(
                score = evalResult.correctCount,
                total = evalResult.totalQuestions,
                assignedLevel = evalResult.assignedLevel
            )
            _uiState.update {
                it.copy(
                    placementAnswers = updatedAnswers,
                    placementResult = evalResult,
                    selectedLevel = evalResult.assignedLevel
                )
            }
        }
    }

    fun confirmPlacementResultAndProceed() {
        _uiState.update { it.copy(currentStep = OnboardingStep.DAILY_GOAL) }
    }

    fun selectDailyGoalMinutes(minutes: Int) {
        analyticsTracker.logOnboardingStep("daily_goal_minutes", minutes.toString())
        _uiState.update {
            it.copy(
                dailyGoalMinutes = minutes,
                currentStep = OnboardingStep.NOTIFICATIONS_OPT_IN
            )
        }
    }

    fun updateReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute
            )
        }
    }

    fun completeOnboarding(onFinished: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            preferencesDataStore.updateOnboardingSelection(
                interfaceLang = state.interfaceLang,
                targetLang = state.targetLang,
                goal = state.selectedGoal,
                level = state.selectedLevel,
                dailyMinutes = state.dailyGoalMinutes,
                notificationsEnabled = state.notificationsEnabled,
                reminderHour = state.reminderHour,
                reminderMinute = state.reminderMinute
            )
            analyticsTracker.logOnboardingStep("onboarding_completed", state.selectedLevel)
            _uiState.update { it.copy(isSaving = false) }
            onFinished()
        }
    }

    fun navigateBackStep(): Boolean {
        val prevStep = when (_uiState.value.currentStep) {
            OnboardingStep.INTRO_PAGER -> return false
            OnboardingStep.INTERFACE_LANGUAGE -> OnboardingStep.INTRO_PAGER
            OnboardingStep.TARGET_LANGUAGE -> OnboardingStep.INTERFACE_LANGUAGE
            OnboardingStep.LEARNING_GOAL -> OnboardingStep.TARGET_LANGUAGE
            OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE -> OnboardingStep.LEARNING_GOAL
            OnboardingStep.PLACEMENT_TEST -> OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE
            OnboardingStep.DAILY_GOAL -> OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE
            OnboardingStep.NOTIFICATIONS_OPT_IN -> OnboardingStep.DAILY_GOAL
        }
        _uiState.update { it.copy(currentStep = prevStep) }
        return true
    }
}
```

### 4.2 جميع شاشات الـ Onboarding الـ 7 (`OnboardingScreens.kt`)

```kotlin
package com.lisaan.app.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFlowScreen(
    onOnboardingCompleteNavigateToAuth: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    BackHandler(enabled = state.currentStep != OnboardingStep.INTRO_PAGER) {
        viewModel.navigateBackStep()
    }

    val stepProgress = when (state.currentStep) {
        OnboardingStep.INTRO_PAGER -> 0.14f
        OnboardingStep.INTERFACE_LANGUAGE -> 0.28f
        OnboardingStep.TARGET_LANGUAGE -> 0.42f
        OnboardingStep.LEARNING_GOAL -> 0.56f
        OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE, OnboardingStep.PLACEMENT_TEST -> 0.72f
        OnboardingStep.DAILY_GOAL -> 0.86f
        OnboardingStep.NOTIFICATIONS_OPT_IN -> 1.0f
    }

    Scaffold(
        topBar = {
            if (state.currentStep != OnboardingStep.INTRO_PAGER) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {
                            LinearProgressIndicator(
                                progress = { stepProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .padding(end = 16.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.navigateBackStep() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isAr) "رجوع" else "Back"
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.currentStep,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "onboarding_step_transition"
        ) { step ->
            when (step) {
                OnboardingStep.INTRO_PAGER -> IntroPagerContent(
                    isAr = isAr,
                    onFinishIntro = viewModel::finishIntroPager
                )
                OnboardingStep.INTERFACE_LANGUAGE -> InterfaceLanguageContent(
                    currentLang = state.interfaceLang,
                    onSelectLang = viewModel::selectInterfaceLanguage
                )
                OnboardingStep.TARGET_LANGUAGE -> TargetLanguageContent(
                    isAr = isAr,
                    onSelectTarget = viewModel::selectTargetLanguage
                )
                OnboardingStep.LEARNING_GOAL -> LearningGoalContent(
                    isAr = isAr,
                    selectedGoal = state.selectedGoal,
                    onSelectGoal = viewModel::selectLearningGoal
                )
                OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE -> LevelOrPlacementChoiceContent(
                    isAr = isAr,
                    onSelectManualLevel = viewModel::chooseManualLevel,
                    onStartPlacementTest = viewModel::startPlacementTest
                )
                OnboardingStep.PLACEMENT_TEST -> PlacementTestContent(
                    isAr = isAr,
                    state = state,
                    onSelectOption = viewModel::answerPlacementQuestion,
                    onProceedAfterResult = viewModel::confirmPlacementResultAndProceed
                )
                OnboardingStep.DAILY_GOAL -> DailyGoalContent(
                    isAr = isAr,
                    selectedMinutes = state.dailyGoalMinutes,
                    onSelectMinutes = viewModel::selectDailyGoalMinutes
                )
                OnboardingStep.NOTIFICATIONS_OPT_IN -> NotificationOptInContent(
                    isAr = isAr,
                    state = state,
                    onUpdateSettings = viewModel::updateReminderSettings,
                    onFinish = { viewModel.completeOnboarding(onOnboardingCompleteNavigateToAuth) }
                )
            }
        }
    }
}

// 1. شاشات التعريف الثلاث (3 Intro Slides)
@Composable
private fun IntroPagerContent(
    isAr: Boolean,
    onFinishIntro: () -> Unit
) {
    val pages = listOf(
        Triple(
            if (isAr) "أهلاً بك في «لِسان»" else "Welcome to Lisaan",
            if (isAr) "رحلة لغوية أصيلة مصممة للعقل العربي، يقودك فيها الهدهد «نبيه» خطوة بخطوة نحو الطلاقة."
            else "An authentic language journey crafted for clarity, guided step-by-step by Nabeeh the Hoopoe.",
            NabeehMoodState.IDLE
        ),
        Triple(
            if (isAr) "تعلّم ذكي وتكرار متباعد" else "Smart Review & AI Coaching",
            if (isAr) "خوارزمية SM-2 تحفظ لك الكلمات الصعبة في ذاكرتك طويلة المدى، ومدرب ذكاء اصطناعي يحاورك بالصوت والنص."
            else "SM-2 spaced repetition locks vocabulary into long-term memory, paired with a voice & text AI Coach.",
            NabeehMoodState.THINKING
        ),
        Triple(
            if (isAr) "مجتمع حي وتحديات ممتعة" else "Live Community & Leagues",
            if (isAr) "شارك كتاباتك وصوتك ليصححها متحدثون متقنون، وتنافس في الدوريات الأسبوعية بدون ملل."
            else "Share your writing and voice recordings for peer feedback, and climb weekly leagues.",
            NabeehMoodState.CELEBRATE
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinishIntro) {
                Text(if (isAr) "تخطي" else "Skip")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val (title, subtitle, mood) = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NabeehMascotView(mood = mood, size = 160.dp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < pages.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinishIntro()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (pagerState.currentPage < pages.lastIndex) {
                    if (isAr) "التالي" else "Next"
                } else {
                    if (isAr) "ابدأ رحلتك" else "Get Started"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// 2. اختيار لغة الواجهة (العربية / الإنجليزية)
@Composable
private fun InterfaceLanguageContent(
    currentLang: String,
    onSelectLang: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabeehMascotView(mood = NabeehMoodState.IDLE, size = 100.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "اختر لغة واجهة التطبيق\nChoose App Interface Language",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        SelectableOptionCard(
            title = "العربية (الوضع الأصلي RTL)",
            subtitle = "واجهة عربية كاملة مع شرح القواعد والمفردات بالعربية",
            leadingEmoji = "🇸🇦",
            isSelected = currentLang == "ar",
            onClick = { onSelectLang("ar") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SelectableOptionCard(
            title = "English (LTR Interface)",
            subtitle = "Full English interface with bilingual Arabic hints",
            leadingEmoji = "🇬🇧",
            isSelected = currentLang == "en",
            onClick = { onSelectLang("en") }
        )
    }
}

// 3. اختيار اللغة المستهدفة للتعلم
@Composable
private fun TargetLanguageContent(
    isAr: Boolean,
    onSelectTarget: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isAr) "أي لغة تود إتقانها في «لِسان»؟" else "Which language do you want to master?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        SelectableOptionCard(
            title = if (isAr) "اللغة الإنجليزية (English)" else "English (for Arabic Speakers)",
            subtitle = if (isAr) "3 وحدات تأسيسية كاملة × 15 درساً تفاعلياً + محادثة ذكية" else "3 Core Units × 15 Lessons + AI Conversation",
            leadingEmoji = "🇬🇧",
            isSelected = true,
            onClick = { onSelectTarget("en") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🇪🇸", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isAr) "الإسبانية والفرنسية والألمانية" else "Spanish, French & German",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isAr) "تُضاف تباعاً في التحديثات القادمة" else "Coming soon in upcoming updates",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// 4. اختيار الهدف (سفر / شغل / دراسة / هواية)
@Composable
private fun LearningGoalContent(
    isAr: Boolean,
    selectedGoal: String,
    onSelectGoal: (String) -> Unit
) {
    val goals = listOf(
        Triple("TRAVEL", if (isAr) "السفر واستكشاف العالم" else "Travel & Exploration", "✈️"),
        Triple("WORK", if (isAr) "التطور المهني والعمل" else "Career & Business", "💼"),
        Triple("STUDY", if (isAr) "الدراسة الأكاديمية والامتحانات" else "Academic Study & Exams", "🎓"),
        Triple("HOBBY", if (isAr) "الثقافة والتواصل الشخصي" else "Culture & Hobby", "🎨")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isAr) "ما هدفك الأساسي من تعلّم الإنجليزية؟" else "What is your main learning goal?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        goals.forEach { (code, label, emoji) ->
            SelectableOptionCard(
                title = label,
                subtitle = "",
                leadingEmoji = emoji,
                isSelected = selectedGoal == code,
                onClick = { onSelectGoal(code) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// 5. اختيار المستوى أو دخول اختبار تحديد المستوى (10 أسئلة)
@Composable
private fun LevelOrPlacementChoiceContent(
    isAr: Boolean,
    onSelectManualLevel: (String) -> Unit,
    onStartPlacementTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabeehMascotView(mood = NabeehMoodState.THINKING, size = 100.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) "ما مستواك الحالي في الإنجليزية؟" else "What is your current English level?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        SelectableOptionCard(
            title = if (isAr) "أبدأ من الصفر (مبتدئ A1)" else "Starting from scratch (A1 Beginner)",
            subtitle = if (isAr) "أعرف فقط بعض الحروف أو الكلمات البسيطة جداً" else "I only know a few basic words",
            leadingEmoji = "🌱",
            isSelected = false,
            onClick = { onSelectManualLevel("A1") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        SelectableOptionCard(
            title = if (isAr) "لديّ أساسيات بسيطة (A2)" else "Elementary (A2)",
            subtitle = if (isAr) "أستطيع تقديم نفسي وطلب أشياء بسيطة" else "I can introduce myself and order food",
            leadingEmoji = "🌿",
            isSelected = false,
            onClick = { onSelectManualLevel("A2") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        SelectableOptionCard(
            title = if (isAr) "حدد مستواي عبر اختبار ذكي (10 أسئلة)" else "Take the 10-Question Placement Test",
            subtitle = if (isAr) "يستغرق دقيقتين ليضعك «نبيه» في الوحدة الأنسب لك" else "Takes 2 minutes to unlock the right units for you",
            leadingEmoji = "🧭",
            isSelected = true,
            onClick = onStartPlacementTest
        )
    }
}

// 5.ب شاشة اختبار تحديد المستوى (10 أسئلة)
@Composable
private fun PlacementTestContent(
    isAr: Boolean,
    state: OnboardingUiState,
    onSelectOption: (Int, Int) -> Unit,
    onProceedAfterResult: () -> Unit
) {
    val result = state.placementResult
    if (result != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NabeehMascotView(mood = NabeehMoodState.CELEBRATE, size = 130.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isAr) "مستواك الموصى به: ${result.assignedLevel}" else "Recommended Level: ${result.assignedLevel}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAr) "أجبت بشكل صحيح على ${result.correctCount} من ${result.totalQuestions} أسئلة"
                else "You answered ${result.correctCount} out of ${result.totalQuestions} correctly",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isAr) result.feedbackAr else result.feedbackEn,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onProceedAfterResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isAr) "متابعة لاختيار الهدف اليومي" else "Continue to Daily Goal")
            }
        }
        return
    }

    val currentQuestion = state.placementQuestions.getOrNull(state.currentQuestionIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isAr) "سؤال ${state.currentQuestionIndex + 1} من ${state.placementQuestions.size} • مستوى ${currentQuestion.cefrBand}"
            else "Question ${state.currentQuestionIndex + 1} of ${state.placementQuestions.size} • ${currentQuestion.cefrBand}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isAr) currentQuestion.promptAr else currentQuestion.promptEn,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = currentQuestion.sentenceContext,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        currentQuestion.options.forEachIndexed { idx, optionText ->
            OutlinedButton(
                onClick = { onSelectOption(currentQuestion.id, idx) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = optionText,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

// 6. اختيار الهدف اليومي (5 / 10 / 15 / 20 دقيقة)
@Composable
private fun DailyGoalContent(
    isAr: Boolean,
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit
) {
    val options = listOf(
        Triple(5, if (isAr) "5 دقائق يومياً (هادئ)" else "5 min / day (Casual)", "☕"),
        Triple(10, if (isAr) "10 دقائق يومياً (منتظم - موصى به)" else "10 min / day (Regular)", "⚡"),
        Triple(15, if (isAr) "15 دقيقة يومياً (جادّ)" else "15 min / day (Serious)", "🔥"),
        Triple(20, if (isAr) "20 دقيقة يومياً (مكثف)" else "20 min / day (Intense)", "🚀")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabeehMascotView(mood = NabeehMoodState.STREAK_FIRE, size = 100.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) "كم دقيقة تود تخصيصها يومياً؟" else "What is your daily learning goal?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        options.forEach { (minutes, title, emoji) ->
            SelectableOptionCard(
                title = title,
                subtitle = "",
                leadingEmoji = emoji,
                isSelected = selectedMinutes == minutes,
                onClick = { onSelectMinutes(minutes) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// 7. تفعيل الإشعارات الذكية
@Composable
private fun NotificationOptInContent(
    isAr: Boolean,
    state: OnboardingUiState,
    onUpdateSettings: (Boolean, Int, Int) -> Unit,
    onFinish: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateSettings(granted, state.reminderHour, state.reminderMinute)
        onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NabeehMascotView(mood = NabeehMoodState.ENCOURAGE, size = 120.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (isAr) "لا تدع شعلتك اليومية تنطفئ!" else "Protect Your Daily Streak!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isAr) "سيذكّرك «نبيه» بلطف في الوقت الذي يناسبك لتكمل هدفك اليومي (${state.dailyGoalMinutes} دقائق)."
                else "Nabeeh will gently remind you at your preferred time to complete your ${state.dailyGoalMinutes}-minute goal.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isAr) "وقت التذكير اليومي" else "Daily Reminder Time",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = String.format("%02d:%02d", state.reminderHour, state.reminderMinute),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onUpdateSettings(true, state.reminderHour, state.reminderMinute)
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isAr) "تفعيل التذكير والبدء" else "Enable Reminders & Start")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    onUpdateSettings(false, state.reminderHour, state.reminderMinute)
                    onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAr) "ربما لاحقاً" else "Maybe Later")
            }
        }
    }
}

@Composable
private fun SelectableOptionCard(
    title: String,
    subtitle: String,
    leadingEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = leadingEmoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
```

---

## 5. إدارة الحالة وشاشات التوثيق الكاملة (`AuthViewModel.kt` & `AuthScreens.kt`)

### 5.1 الـ ViewModel الخاص بالتوثيق (`AuthViewModel.kt`)

```kotlin
package com.lisaan.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.common.Resource
import com.lisaan.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthScreenMode {
    WELCOME,
    SIGN_IN_EMAIL,
    SIGN_UP_EMAIL,
    FORGOT_PASSWORD
}

data class AuthUiState(
    val mode: AuthScreenMode = AuthScreenMode.WELCOME,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessageAr: String? = null,
    val errorMessageEn: String? = null,
    val passwordResetSentSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun switchMode(newMode: AuthScreenMode) {
        _uiState.update {
            it.copy(
                mode = newMode,
                errorMessageAr = null,
                errorMessageEn = null,
                passwordResetSentSuccess = false
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(displayName = value, errorMessageAr = null, errorMessageEn = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessageAr = null, errorMessageEn = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessageAr = null, errorMessageEn = null) }
    }

    fun submitEmailSignIn(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.update {
                it.copy(
                    errorMessageAr = "يرجى إدخال بريد إلكتروني صحيح وكلمة مرور من 6 أحرف على الأقل",
                    errorMessageEn = "Please enter a valid email and a password of at least 6 characters"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.signInWithEmail(state.email, state.password)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun submitEmailSignUp(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.displayName.isBlank() || state.email.isBlank() || state.password.length < 6) {
            _uiState.update {
                it.copy(
                    errorMessageAr = "يرجى ملء الاسم والبريد وكلمة مرور لا تقل عن 6 أحرف",
                    errorMessageEn = "Please provide your name, email, and a password (min 6 characters)"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.signUpWithEmail(state.displayName, state.email, state.password)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun handleGoogleIdToken(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.signInWithGoogleIdToken(idToken)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun continueAsGuest(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.continueAsGuest()) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email
        if (email.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessageAr = "يرجى كتابة بريدك الإلكتروني أولاً",
                    errorMessageEn = "Please enter your email address first"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.sendPasswordResetEmail(email)) {
                is Resource.Success -> {
                    _uiState.update {
                        it.copy(isLoading = false, passwordResetSentSuccess = true)
                    }
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }

    fun deleteAccount(password: String?, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessageAr = null) }
            when (val res = authRepository.deleteAccountPermanently(password)) {
                is Resource.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    onDeleted()
                }
                is Resource.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessageAr = res.messageAr,
                            errorMessageEn = res.messageEn
                        )
                    }
                }
                Resource.Loading -> Unit
            }
        }
    }
}
```

### 5.2 شاشات التوثيق الكاملة (`AuthScreens.kt`)

```kotlin
package com.lisaan.app.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage

@Composable
fun AuthFlowScreen(
    onAuthSuccessNavigateToHome: () -> Unit,
    onRequestGoogleSignInLauncher: ((String) -> Unit) -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        NabeehMascotView(
            mood = if (state.isLoading) NabeehMoodState.THINKING else NabeehMoodState.IDLE,
            size = 110.dp
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (state.mode) {
                AuthScreenMode.WELCOME -> if (isAr) "احفظ تقدمك في «لِسان»" else "Save Your Progress on Lisaan"
                AuthScreenMode.SIGN_IN_EMAIL -> if (isAr) "تسجيل الدخول بالبريد" else "Sign In with Email"
                AuthScreenMode.SIGN_UP_EMAIL -> if (isAr) "إنشاء حساب جديد" else "Create a New Account"
                AuthScreenMode.FORGOT_PASSWORD -> if (isAr) "استعادة كلمة المرور" else "Reset Password"
            },
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // عرض رسائل الخطأ إن وجدت
        val errText = if (isAr) state.errorMessageAr else state.errorMessageEn
        if (!errText.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = errText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        when (state.mode) {
            AuthScreenMode.WELCOME -> {
                Button(
                    onClick = { viewModel.switchMode(AuthScreenMode.SIGN_UP_EMAIL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (isAr) "التسجيل بالبريد الإلكتروني" else "Sign Up with Email")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        onRequestGoogleSignInLauncher { idToken ->
                            viewModel.handleGoogleIdToken(idToken, onAuthSuccessNavigateToHome)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(if (isAr) "المتابعة باستخدام حساب Google" else "Continue with Google")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = { viewModel.switchMode(AuthScreenMode.SIGN_IN_EMAIL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isAr) "لديك حساب بالفعل؟ سجّل الدخول" else "Already have an account? Sign In")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                OutlinedButton(
                    onClick = { viewModel.continueAsGuest(onAuthSuccessNavigateToHome) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PersonOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "الدخول كضيف (تعلّم فوراً بدون حساب)" else "Continue as Guest")
                }
            }

            AuthScreenMode.SIGN_IN_EMAIL, AuthScreenMode.SIGN_UP_EMAIL -> {
                if (state.mode == AuthScreenMode.SIGN_UP_EMAIL) {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onNameChange,
                        label = { Text(if (isAr) "الاسم الكامل" else "Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(if (isAr) "البريد الإلكتروني" else "Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(if (isAr) "كلمة المرور" else "Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                if (state.mode == AuthScreenMode.SIGN_IN_EMAIL) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.switchMode(AuthScreenMode.FORGOT_PASSWORD) }) {
                            Text(if (isAr) "نسيت كلمة المرور؟" else "Forgot Password?")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Button(
                    onClick = {
                        if (state.mode == AuthScreenMode.SIGN_IN_EMAIL) {
                            viewModel.submitEmailSignIn(onAuthSuccessNavigateToHome)
                        } else {
                            viewModel.submitEmailSignUp(onAuthSuccessNavigateToHome)
                        }
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (state.mode == AuthScreenMode.SIGN_IN_EMAIL) {
                                if (isAr) "تسجيل الدخول" else "Sign In"
                            } else {
                                if (isAr) "إنشاء الحساب والبدء" else "Create Account"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { viewModel.switchMode(AuthScreenMode.WELCOME) }) {
                    Text(if (isAr) "العودة لخيارات الدخول الأخرى" else "Back to all Sign-In options")
                }
            }

            AuthScreenMode.FORGOT_PASSWORD -> {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text(if (isAr) "أدخل بريدك الإلكتروني المسجل" else "Enter your registered email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (state.passwordResetSentSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = if (isAr) "تم إرسال رابط استعادة كلمة المرور إلى بريدك الإلكتروني بنجاح."
                            else "Password reset link has been sent to your email.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                Button(
                    onClick = viewModel::sendPasswordReset,
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isAr) "إرسال رابط الاستعادة" else "Send Reset Link")
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = { viewModel.switchMode(AuthScreenMode.SIGN_IN_EMAIL) }) {
                    Text(if (isAr) "العودة لتسجيل الدخول" else "Back to Sign In")
                }
            }
        }
    }
}

/**
 * نافذة تأكيد حذف الحساب نهائياً (تُستدعى من الإعدادات أو الحساب)
 */
@Composable
fun DeleteAccountConfirmationDialog(
    isAr: Boolean,
    onConfirmDelete: (password: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = {
            Text(if (isAr) "حذف الحساب نهائياً" else "Delete Account Permanently")
        },
        text = {
            Column {
                Text(
                    text = if (isAr) "سيؤدي هذا الإجراء إلى حذف حسابك وجميع نقاطك وسجل تقدمك ومفرداتك المحفوظة نهائياً ولا يمكن التراجع عنه."
                    else "This will permanently delete your account, XP, streak, and saved vocabulary. This action cannot be undone."
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isAr) "كلمة المرور للتأكيد (إن وجدت)" else "Password to confirm (if applicable)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(password.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(if (isAr) "نعم، احذف حسابي" else "Yes, Delete Account")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isAr) "إلغاء" else "Cancel")
            }
        }
    )
}
```
