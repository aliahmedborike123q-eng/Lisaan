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
