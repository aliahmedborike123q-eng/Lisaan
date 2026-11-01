package com.lisaan.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lisaan.app.R
import com.lisaan.app.core.database.dao.ProgressAndStreakDao
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DAILY_REMINDER,
                    "التذكير اليومي بالتعلم",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    CHANNEL_STREAK_SAVER,
                    "تنبيهات حماية الـ Streak",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    CHANNEL_COMMUNITY,
                    "إشعارات وتصحيحات المجتمع",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    fun showNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_DAILY_REMINDER = "lisaan_daily_reminder"
        const val CHANNEL_STREAK_SAVER = "lisaan_streak_saver"
        const val CHANNEL_COMMUNITY = "lisaan_community_updates"
    }
}

/**
 * 1. عامل التذكير اليومي الذكي (Smart Daily Reminder Worker)
 */
@HiltWorker
class SmartReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val progressAndStreakDao: ProgressAndStreakDao,
    private val notificationHelper: LisaanNotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.settingsFlow.first()
        if (!prefs.smartReminderEnabled) return Result.success()

        val uid = prefs.currentUserId ?: "guest_default"
        val streak = progressAndStreakDao.getStreakSnapshot(uid)
        val todayIso = LocalDate.now().toString()

        // ذكي: لا يزعج المستخدم إذا كان قد أتم هدفه اليومي بالفعل
        if (streak?.lastCompletedDateIso == todayIso && streak.todayMinutesLearned >= prefs.dailyGoalMinutes) {
            return Result.success()
        }

        val isAr = prefs.interfaceLanguage == "ar"
        notificationHelper.showNotification(
            channelId = LisaanNotificationHelper.CHANNEL_DAILY_REMINDER,
            notificationId = 1001,
            title = if (isAr) "«نبيه» بانتظارك في لِسان! 🧭" else "Nabeeh is waiting for you! 🧭",
            body = if (isAr) "خصّص ${prefs.dailyGoalMinutes} دقائق الآن لإكمال درسك اليومي وتثبيت المفردات الجديدة."
            else "Take ${prefs.dailyGoalMinutes} minutes now to complete your daily lesson."
        )
        return Result.success()
    }
}

/**
 * 2. عامل حماية الـ Streak المسائي (Streak Saver Worker)
 */
@HiltWorker
class StreakSaverWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val progressAndStreakDao: ProgressAndStreakDao,
    private val notificationHelper: LisaanNotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = preferencesDataStore.settingsFlow.first()
        if (!prefs.streakProtectionAlertsEnabled) return Result.success()

        val uid = prefs.currentUserId ?: "guest_default"
        val streak = progressAndStreakDao.getStreakSnapshot(uid) ?: return Result.success()
        val todayIso = LocalDate.now().toString()

        if (streak.currentStreakDays > 0 && streak.lastCompletedDateIso != todayIso) {
            val isAr = prefs.interfaceLanguage == "ar"
            notificationHelper.showNotification(
                channelId = LisaanNotificationHelper.CHANNEL_STREAK_SAVER,
                notificationId = 1002,
                title = if (isAr) "🔥 شعلتك (${streak.currentStreakDays} أيام) على وشك الانطفاء!"
                else "🔥 Your ${streak.currentStreakDays}-day streak is at risk!",
                body = if (isAr) "أكمل تمريناً سريعاً قبل منتصف الليل للحفاظ على تقدمك."
                else "Complete a quick lesson before midnight to save your streak."
            )
        }
        return Result.success()
    }
}

/**
 * 3. خدمة استقبال إشعارات المجتمع السحابية (Firebase Cloud Messaging)
 */
@AndroidEntryPoint
class LisaanFcmService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationHelper: LisaanNotificationHelper

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "تحديث من مجتمع لِسان"
        val body = message.notification?.body ?: message.data["body"] ?: "قام متحدث بتصحيح مشاركتك اللغوية!"
        notificationHelper.showNotification(
            channelId = LisaanNotificationHelper.CHANNEL_COMMUNITY,
            notificationId = System.currentTimeMillis().toInt(),
            title = title,
            body = body
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
    }
}
