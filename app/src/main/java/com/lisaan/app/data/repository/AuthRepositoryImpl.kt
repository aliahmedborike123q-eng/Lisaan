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
