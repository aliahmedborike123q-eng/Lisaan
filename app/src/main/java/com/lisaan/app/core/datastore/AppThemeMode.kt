package com.lisaan.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lisaan_preferences")

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class AppUserSettings(
    val isOnboardingCompleted: Boolean = false,
    val currentUserId: String? = null,
    val isGuestUser: Boolean = false,
    val interfaceLanguage: String = "ar", // "ar" or "en"
    val targetLanguage: String = "en",
    val learningGoal: String = "TRAVEL",
    val proficiencyLevel: String = "A1",
    val dailyGoalMinutes: Int = 10,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val audioPlaybackSpeed: Float = 1.0f, // 0.75f, 1.0f, 1.25f
    val smartReminderEnabled: Boolean = true,
    val reminderHourOfDay: Int = 20, // 8:00 PM default
    val reminderMinute: Int = 0,
    val streakProtectionAlertsEnabled: Boolean = true,
    val communityNotificationsEnabled: Boolean = true,
    val isInitialSeedLoaded: Boolean = false
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val CURRENT_USER_ID = stringPreferencesKey("current_user_id")
        val IS_GUEST_USER = booleanPreferencesKey("is_guest_user")
        val INTERFACE_LANG = stringPreferencesKey("interface_lang")
        val TARGET_LANG = stringPreferencesKey("target_lang")
        val LEARNING_GOAL = stringPreferencesKey("learning_goal")
        val PROFICIENCY_LEVEL = stringPreferencesKey("proficiency_level")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AUDIO_SPEED = floatPreferencesKey("audio_speed")
        val SMART_REMINDER_ENABLED = booleanPreferencesKey("smart_reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val STREAK_ALERTS = booleanPreferencesKey("streak_alerts")
        val COMMUNITY_ALERTS = booleanPreferencesKey("community_alerts")
        val INITIAL_SEED_LOADED = booleanPreferencesKey("initial_seed_loaded")
    }

    val settingsFlow: Flow<AppUserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            AppUserSettings(
                isOnboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
                currentUserId = prefs[Keys.CURRENT_USER_ID],
                isGuestUser = prefs[Keys.IS_GUEST_USER] ?: false,
                interfaceLanguage = prefs[Keys.INTERFACE_LANG] ?: "ar",
                targetLanguage = prefs[Keys.TARGET_LANG] ?: "en",
                learningGoal = prefs[Keys.LEARNING_GOAL] ?: "TRAVEL",
                proficiencyLevel = prefs[Keys.PROFICIENCY_LEVEL] ?: "A1",
                dailyGoalMinutes = prefs[Keys.DAILY_GOAL_MINUTES] ?: 10,
                themeMode = runCatching {
                    AppThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name)
                }.getOrDefault(AppThemeMode.SYSTEM),
                audioPlaybackSpeed = prefs[Keys.AUDIO_SPEED] ?: 1.0f,
                smartReminderEnabled = prefs[Keys.SMART_REMINDER_ENABLED] ?: true,
                reminderHourOfDay = prefs[Keys.REMINDER_HOUR] ?: 20,
                reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 0,
                streakProtectionAlertsEnabled = prefs[Keys.STREAK_ALERTS] ?: true,
                communityNotificationsEnabled = prefs[Keys.COMMUNITY_ALERTS] ?: true,
                isInitialSeedLoaded = prefs[Keys.INITIAL_SEED_LOADED] ?: false
            )
        }

    suspend fun updateOnboardingSelection(
        interfaceLang: String,
        targetLang: String,
        goal: String,
        level: String,
        dailyMinutes: Int,
        notificationsEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INTERFACE_LANG] = interfaceLang
            prefs[Keys.TARGET_LANG] = targetLang
            prefs[Keys.LEARNING_GOAL] = goal
            prefs[Keys.PROFICIENCY_LEVEL] = level
            prefs[Keys.DAILY_GOAL_MINUTES] = dailyMinutes
            prefs[Keys.SMART_REMINDER_ENABLED] = notificationsEnabled
            prefs[Keys.REMINDER_HOUR] = reminderHour
            prefs[Keys.REMINDER_MINUTE] = reminderMinute
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun setAuthenticatedUser(userId: String?, isGuest: Boolean) {
        context.dataStore.edit { prefs ->
            if (userId != null) {
                prefs[Keys.CURRENT_USER_ID] = userId
            } else {
                prefs.remove(Keys.CURRENT_USER_ID)
            }
            prefs[Keys.IS_GUEST_USER] = isGuest
        }
    }

    suspend fun setInterfaceLanguage(langCode: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INTERFACE_LANG] = langCode
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAudioPlaybackSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUDIO_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun updateNotificationPreferences(
        smartReminder: Boolean,
        hour: Int,
        minute: Int,
        streakAlerts: Boolean,
        communityAlerts: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SMART_REMINDER_ENABLED] = smartReminder
            prefs[Keys.REMINDER_HOUR] = hour
            prefs[Keys.REMINDER_MINUTE] = minute
            prefs[Keys.STREAK_ALERTS] = streakAlerts
            prefs[Keys.COMMUNITY_ALERTS] = communityAlerts
        }
    }

    suspend fun markInitialSeedLoaded() {
        context.dataStore.edit { prefs ->
            prefs[Keys.INITIAL_SEED_LOADED] = true
        }
    }

    suspend fun clearUserSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.CURRENT_USER_ID)
            prefs[Keys.IS_GUEST_USER] = false
        }
    }
}
