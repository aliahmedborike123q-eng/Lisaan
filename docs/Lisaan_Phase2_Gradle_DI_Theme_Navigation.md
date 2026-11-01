# تطبيق "لِسان (Lisaan)" — المرحلة 2: إعدادات Gradle، حقن الاعتماديات (Hilt DI)، نظام التصميم والهوية (Material 3 Theme + RTL/LTR + Mascot)، ونظام التنقل الكامل (Compose Navigation)

---

## 1. إعدادات البناء والاعتماديات (Gradle & Version Catalog)

### 1.1 ملف كتالوج الإصدارات (`gradle/libs.versions.toml`)

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
coreKtx = "1.15.0"
appcompat = "1.7.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.11.00"
navigationCompose = "2.8.4"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
hiltWork = "1.2.0"
room = "2.6.1"
datastore = "1.1.1"
coroutines = "1.9.0"
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
workManager = "2.10.0"
media3 = "1.5.0"
billing = "7.1.1"
firebaseBom = "33.6.0"
playServicesAuth = "21.2.0"
lottieCompose = "6.6.0"
coilCompose = "2.7.0"
googleServices = "4.4.2"
crashlyticsPlugin = "3.0.2"
junit = "4.13.2"
coroutinesTest = "1.9.0"
turbine = "1.2.0"
mockk = "1.13.13"
androidxTestExt = "1.2.1"
espressoCore = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Compose
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }

# Room
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }

# DataStore & WorkManager
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workManager" }

# Coroutines & Serialization
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version.ref = "coroutines" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }

# Network (Retrofit + OkHttp)
retrofit-core = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
okhttp-core = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# Media3 (ExoPlayer) & Animations & Images
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
lottie-compose = { group = "com.airbnb.android", name = "lottie-compose", version.ref = "lottieCompose" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coilCompose" }

# Google Play Billing & Auth
play-billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }
play-services-auth = { group = "com.google.android.gms", name = "play-services-auth", version.ref = "playServicesAuth" }

# Firebase
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-auth-ktx = { group = "com.google.firebase", name = "firebase-auth-ktx" }
firebase-firestore-ktx = { group = "com.google.firebase", name = "firebase-firestore-ktx" }
firebase-storage-ktx = { group = "com.google.firebase", name = "firebase-storage-ktx" }
firebase-messaging-ktx = { group = "com.google.firebase", name = "firebase-messaging-ktx" }
firebase-analytics-ktx = { group = "com.google.firebase", name = "firebase-analytics-ktx" }
firebase-crashlytics-ktx = { group = "com.google.firebase", name = "firebase-crashlytics-ktx" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidxTestExt" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "crashlyticsPlugin" }
```

### 1.2 ملف البناء على مستوى المشروع (`build.gradle.kts` - Project Level)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
```

### 1.3 ملف البناء على مستوى التطبيق (`app/build.gradle.kts` - Module Level)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.lisaan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lisaan.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Claude Backend Endpoint Config
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://api.lisaan.app/v1/\"")
        buildConfigField("String", "CLAUDE_MODEL_VERSION", "\"claude-3-5-sonnet-latest\"")
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.generateKotlin", "true")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt + WorkManager DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore & WorkManager
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Media3 ExoPlayer, Lottie, Coil
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation(libs.lottie.compose)
    implementation(libs.coil.compose)

    // Google Play Billing & Auth
    implementation(libs.play.billing.ktx)
    implementation(libs.play.services.auth)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)

    // Unit & UI Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
```

### 1.4 ملف البيان (`app/src/main/AndroidManifest.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="com.android.vending.BILLING" />

    <!-- السماح لخدمة التعرف على الكلام وTTS بالعمل في أندرويد 11+ -->
    <queries>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
        <intent>
            <action android:name="android.intent.action.TTS_SERVICE" />
        </intent>
    </queries>

    <application
        android:name=".LisaanApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Lisaan"
        android:localeConfig="@xml/locales_config"
        tools:targetApi="35">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="locale|layoutDirection|uiMode|screenSize|orientation"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Deep Link للدروس والتحديات -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:scheme="https"
                    android:host="app.lisaan.com" />
            </intent-filter>
        </activity>

        <!-- تعطيل مُهيئ WorkManager الافتراضي لاستخدام HiltWorkerFactory -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <!-- خدمة إشعارات Firebase Cloud Messaging -->
        <service
            android:name=".core.notifications.LisaanFcmService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

---

## 2. التخزين المحلي للإعدادات وحقن الاعتماديات (DataStore & Hilt DI Modules)

### 2.1 إعدادات التطبيق وتفضيلات المستخدم (`UserPreferencesDataStore.kt`)

```kotlin
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
```

### 2.2 موفر الـ Coroutine Dispatchers (`DispatcherProvider.kt`)

```kotlin
package com.lisaan.app.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
```

### 2.3 جميع وحدات حقن الاعتماديات بـ Hilt (`AllHiltModules.kt`)

```kotlin
package com.lisaan.app.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.lisaan.app.BuildConfig
import com.lisaan.app.core.common.DefaultDispatcherProvider
import com.lisaan.app.core.common.DispatcherProvider
import com.lisaan.app.core.database.DatabaseMigrations
import com.lisaan.app.core.database.LisaanDatabase
import com.lisaan.app.core.database.dao.*
import com.lisaan.app.core.network.AuthInterceptor
import com.lisaan.app.core.network.ClaudeAiApiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLisaanDatabase(@ApplicationContext context: Context): LisaanDatabase {
        return Room.databaseBuilder(
            context,
            LisaanDatabase::class.java,
            LisaanDatabase.DATABASE_NAME
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideUserDao(db: LisaanDatabase): UserDao = db.userDao()

    @Provides
    fun provideCourseContentDao(db: LisaanDatabase): CourseContentDao = db.courseContentDao()

    @Provides
    fun provideVocabularyDao(db: LisaanDatabase): VocabularyDao = db.vocabularyDao()

    @Provides
    fun provideProgressAndStreakDao(db: LisaanDatabase): ProgressAndStreakDao = db.progressAndStreakDao()

    @Provides
    fun provideSm2ReviewDao(db: LisaanDatabase): Sm2ReviewDao = db.sm2ReviewDao()

    @Provides
    fun provideAchievementDao(db: LisaanDatabase): AchievementDao = db.achievementDao()

    @Provides
    fun provideCommunityDao(db: LisaanDatabase): CommunityDao = db.communityDao()

    @Provides
    fun provideSubscriptionDao(db: LisaanDatabase): SubscriptionDao = db.subscriptionDao()
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(100L * 1024L * 1024L) // 100 MB offline Firestore cache
                    .build()
            )
            .build()
        firestore.firestoreSettings = settings
        return firestore
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideClaudeAiApiService(retrofit: Retrofit): ClaudeAiApiService =
        retrofit.create(ClaudeAiApiService::class.java)
}
```

### 2.4 معترض التوثيق للشبكة (`AuthInterceptor.kt`)

```kotlin
package com.lisaan.app.core.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val currentUser = firebaseAuth.currentUser

        val token = try {
            if (currentUser != null) {
                Tasks.await(currentUser.getIdToken(false)).token
            } else null
        } catch (e: Exception) {
            null
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("X-Client-Platform", "Android-Lisaan")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        return chain.proceed(requestBuilder.build())
    }
}
```

---

## 3. الهوية البصرية ونظام التصميم الكامل (Material 3 Theme + RTL/LTR + Mascot)

### 3.1 لوحة ألوان "لِسان" الأصلية (`Color.kt`)

```kotlin
package com.lisaan.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Primary: Lapis Lazuli (أزرق لازوردي معرفي)
val LapisBlueLight = Color(0xFF0F4C81)
val OnLapisBlueLight = Color(0xFFFFFFFF)
val LapisContainerLight = Color(0xFFD6E6F8)
val OnLapisContainerLight = Color(0xFF072541)

val LapisBlueDark = Color(0xFF7CC0FF)
val OnLapisBlueDark = Color(0xFF003357)
val LapisContainerDark = Color(0xFF0B3B66)
val OnLapisContainerDark = Color(0xFFD6E6F8)

// Secondary: Oasis Emerald (أخضر واحة زمردي للتقدم والنمو)
val OasisEmeraldLight = Color(0xFF00897B)
val OnOasisEmeraldLight = Color(0xFFFFFFFF)
val OasisContainerLight = Color(0xFFC8F5EE)
val OnOasisContainerLight = Color(0xFF003831)

val OasisEmeraldDark = Color(0xFF4DB6AC)
val OnOasisEmeraldDark = Color(0xFF003731)
val OasisContainerDark = Color(0xFF005047)
val OnOasisContainerDark = Color(0xFFC8F5EE)

// Tertiary / Accent: Desert Amber (كهرماني مشرق للـ Streak والـ XP)
val DesertAmberLight = Color(0xFFD97706)
val OnDesertAmberLight = Color(0xFFFFFFFF)
val AmberContainerLight = Color(0xFFFEF3C7)
val OnAmberContainerLight = Color(0xFF451A03)

val DesertAmberDark = Color(0xFFFBBF24)
val OnDesertAmberDark = Color(0xFF451A03)
val AmberContainerDark = Color(0xFF78350F)
val OnAmberContainerDark = Color(0xFFFEF3C7)

// Semantic Colors (Success, Warning, Error, League Colors)
val SemanticSuccessGreen = Color(0xFF10B981)
val SemanticSuccessContainer = Color(0xFFD1FAE5)
val SemanticWarningAmber = Color(0xFFF59E0B)
val SemanticErrorRed = Color(0xFFEF4444)
val SemanticErrorContainer = Color(0xFFFEE2E2)

val LeagueBronze = Color(0xFFCD7F32)
val LeagueSilver = Color(0xFF94A3B8)
val LeagueGold = Color(0xFFF59E0B)

// Backgrounds & Surfaces
val WarmSandBackgroundLight = Color(0xFFFAF8F5)
val PureSurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1EDE6)
val OnSurfacePrimaryLight = Color(0xFF18222F)
val OnSurfaceSecondaryLight = Color(0xFF526071)
val OutlineLight = Color(0xFFD5CFC4)

val NightSkyBackgroundDark = Color(0xFF0B131E)
val DeepSlateSurfaceDark = Color(0xFF152232)
val SurfaceVariantDark = Color(0xFF1E2F44)
val OnSurfacePrimaryDark = Color(0xFFF1F5F9)
val OnSurfaceSecondaryDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF334155)
```

### 3.2 الخطوط العربية واللاتينية المتكيفة (`Type.kt`)

```kotlin
package com.lisaan.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * في بيئة الإنتاج يتم وضع ملفات خطوط:
 * - res/font/ibm_plex_sans_arabic_regular.ttf / medium / semibold / bold
 * - res/font/plus_jakarta_sans_regular.ttf / medium / semibold / bold
 * ويتم التبديل بينهما تلقائياً أو دمجهما في FontFamily موحدة لضمان دعم التشكيل والـ IPA.
 */
val LisaanArabicLatinFontFamily = FontFamily.SansSerif

fun buildLisaanTypography(isArabic: Boolean): Typography {
    // زيادة طفيفة في ارتفاع السطر (lineHeight) للغة العربية لضمان عدم تداخل الحركات والتشكيل
    val lineHeightMultiplier = if (isArabic) 1.15f else 1.0f

    return Typography(
        displayLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = (44 * lineHeightMultiplier).sp
        ),
        headlineLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = (36 * lineHeightMultiplier).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = (32 * lineHeightMultiplier).sp
        ),
        titleLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = (28 * lineHeightMultiplier).sp
        ),
        titleMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = (24 * lineHeightMultiplier).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = (24 * lineHeightMultiplier).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = (20 * lineHeightMultiplier).sp
        ),
        labelLarge = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = (20 * lineHeightMultiplier).sp
        ),
        labelMedium = TextStyle(
            fontFamily = LisaanArabicLatinFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = (16 * lineHeightMultiplier).sp
        )
    )
}
```

### 3.3 الحواف والأشكال الهندسية (`Shape.kt`)

```kotlin
package com.lisaan.app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val LisaanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
```

### 3.4 الثيم الكامل مع دعم التبديل الفوري لـ RTL/LTR وDark Mode (`Theme.kt`)

```kotlin
package com.lisaan.app.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import com.lisaan.app.core.datastore.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LapisBlueLight,
    onPrimary = OnLapisBlueLight,
    primaryContainer = LapisContainerLight,
    onPrimaryContainer = OnLapisContainerLight,
    secondary = OasisEmeraldLight,
    onSecondary = OnOasisEmeraldLight,
    secondaryContainer = OasisContainerLight,
    onSecondaryContainer = OnOasisContainerLight,
    tertiary = DesertAmberLight,
    onTertiary = OnDesertAmberLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = WarmSandBackgroundLight,
    onBackground = OnSurfacePrimaryLight,
    surface = PureSurfaceLight,
    onSurface = OnSurfacePrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceSecondaryLight,
    outline = OutlineLight,
    error = SemanticErrorRed,
    errorContainer = SemanticErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = LapisBlueDark,
    onPrimary = OnLapisBlueDark,
    primaryContainer = LapisContainerDark,
    onPrimaryContainer = OnLapisContainerDark,
    secondary = OasisEmeraldDark,
    onSecondary = OnOasisEmeraldDark,
    secondaryContainer = OasisContainerDark,
    onSecondaryContainer = OnOasisContainerDark,
    tertiary = DesertAmberDark,
    onTertiary = OnDesertAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = NightSkyBackgroundDark,
    onBackground = OnSurfacePrimaryDark,
    surface = DeepSlateSurfaceDark,
    onSurface = OnSurfacePrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceSecondaryDark,
    outline = OutlineDark,
    error = SemanticErrorRed,
    errorContainer = SemanticErrorContainer
)

val LocalAppLanguage = staticCompositionLocalOf { "ar" }

@Composable
fun LisaanTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    interfaceLangCode: String = "ar",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val isArabic = interfaceLangCode.lowercase() == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildLisaanTypography(isArabic = isArabic)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppLanguage provides interfaceLangCode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = LisaanShapes,
            content = content
        )
    }
}
```

### 3.5 المكون الرسومي الأصلي للتميمة: الهدهد "نَبيه (Nabeeh)" (`NabeehMascotView.kt`)

يرسم هذا المكون شخصية الهدهد "نبيه" برمجياً عبر `Compose Canvas` مع حركات تنفس ورفرفة وتفاعل حسب الحالة (`IDLE`, `CELEBRATE`, `THINKING`, `ENCOURAGE`, `STREAK_FIRE`) بحيث يعمل فوراً دون الاعتماد على ملفات خارجية مفقودة، مع دعم تشغيل ملفات Lottie عند توفرها في `res/raw/`:

```kotlin
package com.lisaan.app.core.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LapisBlueLight
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight

enum class NabeehMoodState {
    IDLE,
    CELEBRATE,
    THINKING,
    ENCOURAGE,
    STREAK_FIRE
}

@Composable
fun NabeehMascotView(
    mood: NabeehMoodState = NabeehMoodState.IDLE,
    size: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nabeeh_anim")

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (mood == NabeehMoodState.CELEBRATE) -16f else -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (mood == NabeehMoodState.CELEBRATE) 450 else 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val crestRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = if (mood == NabeehMoodState.THINKING) 12f else 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crest"
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "نبيه تميمة تطبيق لسان" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cy = h * 0.55f + bounceOffset

            // 1. هالة خلفية دافئة حسب الحالة
            val auraColor = when (mood) {
                NabeehMoodState.CELEBRATE, NabeehMoodState.STREAK_FIRE -> DesertAmberLight.copy(alpha = 0.22f)
                NabeehMoodState.THINKING -> LapisBlueLight.copy(alpha = 0.18f)
                NabeehMoodState.ENCOURAGE -> OasisEmeraldLight.copy(alpha = 0.20f)
                NabeehMoodState.IDLE -> LapisBlueLight.copy(alpha = 0.12f)
            }
            drawCircle(
                color = auraColor,
                radius = w * 0.46f,
                center = Offset(w * 0.5f, cy)
            )

            // 2. تاج الهدهد المميز (Crest Feathers)
            rotate(degrees = crestRotation, pivot = Offset(w * 0.5f, cy - h * 0.22f)) {
                val featherColors = listOf(DesertAmberLight, Color(0xFFEA580C), DesertAmberLight)
                val angles = listOf(-25f, 0f, 25f)
                angles.forEachIndexed { idx, angle ->
                    rotate(degrees = angle, pivot = Offset(w * 0.5f, cy - h * 0.20f)) {
                        drawRoundRect(
                            color = featherColors[idx],
                            topLeft = Offset(w * 0.46f, cy - h * 0.42f),
                            size = Size(w * 0.08f, h * 0.22f),
                            cornerRadius = CornerRadius(16f, 16f)
                        )
                        // طرف الريشة الداكن المميز للهدهد
                        drawCircle(
                            color = Color(0xFF18222F),
                            radius = w * 0.04f,
                            center = Offset(w * 0.50f, cy - h * 0.40f)
                        )
                    }
                }
            }

            // 3. جسم الهدهد (Warm Terracotta Amber)
            drawRoundRect(
                color = Color(0xFFF59E0B),
                topLeft = Offset(w * 0.26f, cy - h * 0.24f),
                size = Size(w * 0.48f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.24f, w * 0.24f)
            )

            // 4. الصدر الأبيض الكريمي
            drawRoundRect(
                color = Color(0xFFFFFBEB),
                topLeft = Offset(w * 0.33f, cy - h * 0.06f),
                size = Size(w * 0.34f, h * 0.30f),
                cornerRadius = CornerRadius(w * 0.17f, w * 0.17f)
            )

            // 5. الوشاح اللازوردي الذكي (Lapis Scarf)
            drawRoundRect(
                color = LapisBlueLight,
                topLeft = Offset(w * 0.28f, cy - h * 0.04f),
                size = Size(w * 0.44f, h * 0.09f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            // بوصلة لسان الذهبية على الوشاح
            drawCircle(
                color = DesertAmberLight,
                radius = w * 0.045f,
                center = Offset(w * 0.5f, cy + h * 0.005f)
            )

            // 6. العينان الذكيتان
            val eyeY = cy - h * 0.13f
            drawCircle(color = Color.White, radius = w * 0.075f, center = Offset(w * 0.40f, eyeY))
            drawCircle(color = Color.White, radius = w * 0.075f, center = Offset(w * 0.60f, eyeY))
            drawCircle(color = Color(0xFF0F172A), radius = w * 0.04f, center = Offset(w * 0.40f, eyeY))
            drawCircle(color = Color(0xFF0F172A), radius = w * 0.04f, center = Offset(w * 0.60f, eyeY))

            // 7. منقار الهدهد الطويل الأنيق
            val beakPath = Path().apply {
                moveTo(w * 0.46f, cy - h * 0.10f)
                lineTo(w * 0.54f, cy - h * 0.10f)
                lineTo(w * 0.50f, cy - h * 0.01f)
                close()
            }
            drawPath(path = beakPath, color = Color(0xFF1E293B))
        }
    }
}
```

---

## 4. نظام التنقل الكامل (Type-Safe Navigation Routes & Host & Main Scaffold)

### 4.1 تعريف مسارات الشاشات (`ScreenRoutes.kt`)

```kotlin
package com.lisaan.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class LisaanRoute(val route: String) {
    // Root Graphs
    data object Splash : LisaanRoute("splash_route")
    data object OnboardingGraph : LisaanRoute("onboarding_graph")
    data object AuthGraph : LisaanRoute("auth_graph")
    data object MainScaffold : LisaanRoute("main_scaffold_route")

    // 1. Onboarding Screens
    data object OnboardingIntro : LisaanRoute("onboarding_intro")
    data object OnboardingInterfaceLang : LisaanRoute("onboarding_interface_lang")
    data object OnboardingTargetLang : LisaanRoute("onboarding_target_lang")
    data object OnboardingGoal : LisaanRoute("onboarding_goal")
    data object OnboardingLevel : LisaanRoute("onboarding_level")
    data object OnboardingPlacementTest : LisaanRoute("onboarding_placement_test")
    data object OnboardingDailyGoal : LisaanRoute("onboarding_daily_goal")
    data object OnboardingNotifications : LisaanRoute("onboarding_notifications")

    // 2. Auth Screens
    data object AuthWelcome : LisaanRoute("auth_welcome")
    data object AuthEmailSignIn : LisaanRoute("auth_email_sign_in")
    data object AuthEmailSignUp : LisaanRoute("auth_email_sign_up")
    data object AuthForgotPassword : LisaanRoute("auth_forgot_password")

    // 3. Bottom Navigation Tabs
    data object HomePathTab : LisaanRoute("tab_home_path")
    data object SmartReviewTab : LisaanRoute("tab_smart_review")
    data object AiCoachTab : LisaanRoute("tab_ai_coach")
    data object CommunityTab : LisaanRoute("tab_community")
    data object ProfileTab : LisaanRoute("tab_profile")

    // 4. Secondary Feature Routes
    data object LessonPlayer : LisaanRoute("lesson_player/{lessonId}") {
        fun createRoute(lessonId: String) = "lesson_player/$lessonId"
    }
    data object Sm2Session : LisaanRoute("sm2_review_session")
    data object Dictionary : LisaanRoute("my_dictionary")
    data object FlashcardsDeck : LisaanRoute("flashcards_deck")
    data object MiniGamesHub : LisaanRoute("mini_games_hub")
    data object SpeedMatchGame : LisaanRoute("game_speed_match")
    data object WordSprintGame : LisaanRoute("game_word_sprint")
    data object GuessPictureGame : LisaanRoute("game_guess_picture")

    data object AiChatSession : LisaanRoute("ai_chat_session/{scenarioId}") {
        fun createRoute(scenarioId: String) = "ai_chat_session/$scenarioId"
    }

    data object CreateCommunityPost : LisaanRoute("community_create_post")
    data object CommunityPostDetail : LisaanRoute("community_post_detail/{postId}") {
        fun createRoute(postId: String) = "community_post_detail/$postId"
    }
    data object Leaderboard : LisaanRoute("challenges_leaderboard")
    data object Achievements : LisaanRoute("challenges_achievements")

    data object Settings : LisaanRoute("profile_settings")
    data object Paywall : LisaanRoute("paywall_screen?source={source}") {
        fun createRoute(source: String = "general") = "paywall_screen?source=$source"
    }
}

data class BottomNavItem(
    val route: String,
    val titleAr: String,
    val titleEn: String,
    val icon: ImageVector
)

val MainBottomNavItems = listOf(
    BottomNavItem(
        route = LisaanRoute.HomePathTab.route,
        titleAr = "المسار",
        titleEn = "Path",
        icon = Icons.Default.Map
    ),
    BottomNavItem(
        route = LisaanRoute.SmartReviewTab.route,
        titleAr = "المراجعة",
        titleEn = "Review",
        icon = Icons.Default.Psychology
    ),
    BottomNavItem(
        route = LisaanRoute.AiCoachTab.route,
        titleAr = "المحادثة",
        titleEn = "AI Coach",
        icon = Icons.Default.AutoAwesome
    ),
    BottomNavItem(
        route = LisaanRoute.CommunityTab.route,
        titleAr = "المجتمع",
        titleEn = "Community",
        icon = Icons.Default.Groups
    ),
    BottomNavItem(
        route = LisaanRoute.ProfileTab.route,
        titleAr = "حسابي",
        titleEn = "Profile",
        icon = Icons.Default.Person
    )
)
```

### 4.2 الموجه الرئيسي للتنقل والهيكل السفلي (`LisaanNavHost.kt` & `MainActivity.kt` & `LisaanApplication.kt`)

```kotlin
package com.lisaan.app.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage

@Composable
fun LisaanRootNavHost(
    rootNavController: NavHostController,
    startDestination: String,
    onOnboardingCompleted: () -> Unit,
    onAuthCompleted: () -> Unit,
    // Callbacks or Screen Slots injected cleanly across modules/phases
    onboardingFlowComposable: @Composable (NavHostController) -> Unit,
    authFlowComposable: @Composable (NavHostController) -> Unit,
    homeTabComposable: @Composable (onStartLesson: (String) -> Unit, onOpenMiniGames: () -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    reviewTabComposable: @Composable (onStartSm2: () -> Unit, onOpenDictionary: () -> Unit, onOpenFlashcards: () -> Unit, onOpenMiniGames: () -> Unit) -> Unit,
    aiCoachTabComposable: @Composable (onSelectScenario: (String) -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    communityTabComposable: @Composable (onCreatePost: () -> Unit, onOpenPost: (String) -> Unit, onOpenLeaderboard: () -> Unit, onOpenAchievements: () -> Unit) -> Unit,
    profileTabComposable: @Composable (onOpenSettings: () -> Unit, onOpenPaywall: (String) -> Unit, onOpenAchievements: () -> Unit) -> Unit,
    lessonPlayerComposable: @Composable (lessonId: String, onCloseLesson: () -> Unit) -> Unit,
    secondaryRouteComposable: @Composable (route: String, args: android.os.Bundle?, onBack: () -> Unit) -> Unit
) {
    NavHost(
        navController = rootNavController,
        startDestination = startDestination
    ) {
        // 1. Onboarding Flow
        composable(route = LisaanRoute.OnboardingGraph.route) {
            onboardingFlowComposable(rootNavController)
        }

        // 2. Auth Flow
        composable(route = LisaanRoute.AuthGraph.route) {
            authFlowComposable(rootNavController)
        }

        // 3. Main 5-Tab Scaffold
        composable(route = LisaanRoute.MainScaffold.route) {
            LisaanMainScaffold(
                onNavigateToLesson = { lessonId ->
                    rootNavController.navigate(LisaanRoute.LessonPlayer.createRoute(lessonId))
                },
                onNavigateToSecondary = { targetRoute ->
                    rootNavController.navigate(targetRoute)
                },
                homeTabContent = homeTabComposable,
                reviewTabContent = reviewTabComposable,
                aiCoachTabContent = aiCoachTabComposable,
                communityTabContent = communityTabComposable,
                profileTabContent = profileTabComposable
            )
        }

        // 4. Full-Screen Lesson Player
        composable(
            route = LisaanRoute.LessonPlayer.route,
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType })
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId").orEmpty()
            lessonPlayerComposable(lessonId) {
                rootNavController.popBackStack()
            }
        }

        // 5. Secondary Screens (SM-2 Session, Dictionary, Flashcards, Mini-games, AI Chat, Community Detail, Leaderboard, Settings, Paywall)
        val secondaryRoutes = listOf(
            LisaanRoute.Sm2Session.route,
            LisaanRoute.Dictionary.route,
            LisaanRoute.FlashcardsDeck.route,
            LisaanRoute.MiniGamesHub.route,
            LisaanRoute.SpeedMatchGame.route,
            LisaanRoute.WordSprintGame.route,
            LisaanRoute.GuessPictureGame.route,
            LisaanRoute.AiChatSession.route,
            LisaanRoute.CreateCommunityPost.route,
            LisaanRoute.CommunityPostDetail.route,
            LisaanRoute.Leaderboard.route,
            LisaanRoute.Achievements.route,
            LisaanRoute.Settings.route,
            LisaanRoute.Paywall.route
        )

        secondaryRoutes.forEach { routePattern ->
            composable(route = routePattern) { entry ->
                secondaryRouteComposable(routePattern, entry.arguments) {
                    rootNavController.popBackStack()
                }
            }
        }
    }
}

@Composable
fun LisaanMainScaffold(
    onNavigateToLesson: (String) -> Unit,
    onNavigateToSecondary: (String) -> Unit,
    homeTabContent: @Composable (onStartLesson: (String) -> Unit, onOpenMiniGames: () -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    reviewTabContent: @Composable (onStartSm2: () -> Unit, onOpenDictionary: () -> Unit, onOpenFlashcards: () -> Unit, onOpenMiniGames: () -> Unit) -> Unit,
    aiCoachTabContent: @Composable (onSelectScenario: (String) -> Unit, onOpenPaywall: (String) -> Unit) -> Unit,
    communityTabContent: @Composable (onCreatePost: () -> Unit, onOpenPost: (String) -> Unit, onOpenLeaderboard: () -> Unit, onOpenAchievements: () -> Unit) -> Unit,
    profileTabContent: @Composable (onOpenSettings: () -> Unit, onOpenPaywall: (String) -> Unit, onOpenAchievements: () -> Unit) -> Unit
) {
    val tabNavController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isArabic = LocalAppLanguage.current == "ar"

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                MainBottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    val label = if (isArabic) item.titleAr else item.titleEn
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                tabNavController.navigate(item.route) {
                                    popUpTo(tabNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = tabNavController,
                startDestination = LisaanRoute.HomePathTab.route
            ) {
                composable(LisaanRoute.HomePathTab.route) {
                    homeTabContent(
                        onNavigateToLesson,
                        { onNavigateToSecondary(LisaanRoute.MiniGamesHub.route) },
                        { source -> onNavigateToSecondary(LisaanRoute.Paywall.createRoute(source)) }
                    )
                }
                composable(LisaanRoute.SmartReviewTab.route) {
                    reviewTabContent(
                        { onNavigateToSecondary(LisaanRoute.Sm2Session.route) },
                        { onNavigateToSecondary(LisaanRoute.Dictionary.route) },
                        { onNavigateToSecondary(LisaanRoute.FlashcardsDeck.route) },
                        { onNavigateToSecondary(LisaanRoute.MiniGamesHub.route) }
                    )
                }
                composable(LisaanRoute.AiCoachTab.route) {
                    aiCoachTabContent(
                        { scenarioId -> onNavigateToSecondary(LisaanRoute.AiChatSession.createRoute(scenarioId)) },
                        { source -> onNavigateToSecondary(LisaanRoute.Paywall.createRoute(source)) }
                    )
                }
                composable(LisaanRoute.CommunityTab.route) {
                    communityTabContent(
                        { onNavigateToSecondary(LisaanRoute.CreateCommunityPost.route) },
                        { postId -> onNavigateToSecondary(LisaanRoute.CommunityPostDetail.createRoute(postId)) },
                        { onNavigateToSecondary(LisaanRoute.Leaderboard.route) },
                        { onNavigateToSecondary(LisaanRoute.Achievements.route) }
                    )
                }
                composable(LisaanRoute.ProfileTab.route) {
                    profileTabContent(
                        { onNavigateToSecondary(LisaanRoute.Settings.route) },
                        { source -> onNavigateToSecondary(LisaanRoute.Paywall.createRoute(source)) },
                        { onNavigateToSecondary(LisaanRoute.Achievements.route) }
                    )
                }
            }
        }
    }
}
```

### 4.3 كلاس التطبيق ونقطة الدخول الرئيسية (`LisaanApplication.kt` & `MainActivity.kt`)

```kotlin
package com.lisaan.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LisaanApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
```

```kotlin
package com.lisaan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.lisaan.app.core.datastore.AppUserSettings
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.theme.LisaanTheme
import com.lisaan.app.presentation.navigation.LisaanRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userSettings by preferencesDataStore.settingsFlow.collectAsState(
                initial = AppUserSettings()
            )

            LisaanTheme(
                themeMode = userSettings.themeMode,
                interfaceLangCode = userSettings.interfaceLanguage
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val rootNavController = rememberNavController()
                    val startDestination = when {
                        !userSettings.isOnboardingCompleted -> LisaanRoute.OnboardingGraph.route
                        userSettings.currentUserId == null -> LisaanRoute.AuthGraph.route
                        else -> LisaanRoute.MainScaffold.route
                    }

                    // يتم ربط الشاشات التفصيلية تباعاً في المراحل 3 إلى 7 داخل LisaanRootNavHost
                }
            }
        }
    }
}
```
