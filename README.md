# تطبيق "لِسان (Lisaan)" — مشروع Android Studio الكامل

## نظرة عامة
تطبيق تعليم لغات أصيل مبني بـ **Kotlin + Jetpack Compose (Material 3) + MVVM + Clean Architecture + Hilt + Room + Retrofit + Coroutines/Flow + DataStore + WorkManager + Firebase + ExoPlayer + Android TTS & SpeechRecognizer + Google Play Billing v7**.

## طريقة التشغيل في Android Studio
1. فك الضغط عن ملف `Lisaan_Android_Studio_Project.zip`.
2. افتح **Android Studio (Ladybug أو أحدث)** واختر **Open** ثم حدد مجلد المشروع.
3. استبدل ملف `app/google-services.json` بملف مشروعك الفعلي من **Firebase Console** (أو استخدم الوضع المحلي/الضيف مباشرة).
4. لتشغيل سكريبت رفع بيانات الـ Seed إلى Firestore:
   ```bash
   cd scripts
   npm install firebase-admin
   node upload_seed_to_firestore.js
   ```
5. لتشغيل الاختبارات:
   * اختبارات الوحدات (Unit Tests): `./gradlew testDebugUnitTest`
   * اختبارات الواجهة (UI Tests): `./gradlew connectedDebugAndroidTest`
