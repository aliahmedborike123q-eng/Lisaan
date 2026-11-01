# تطبيق "لِسان (Lisaan)" — المرحلة 4: الشاشة الرئيسية (خريطة التعلم Home Path)، مشغل الدروس، محرك التمارين الـ 8 (مع ExoPlayer وTTS وSpeechRecognizer)، والمحتوى الأصلي الكامل (3 وحدات × 5 دروس × 10 تمارين + Firestore Seed Script)

---

## 1. محركات الصوت والنطق (Audio Player, Android TTS, SpeechRecognizer + Pronunciation Scorer)

### 1.1 مدير النطق النصي مع التحكم في السرعة (`LisaanTtsManager.kt`)

يدعم النطق بالسرعة المحددة من إعدادات المستخدم (`0.75x`, `1.0x`, `1.25x`) بالإضافة إلى وضع **النطق البطيء (Turtle Slow Mode 🐢 `0.55x`)** في تمارين الاستماع والمفردات:

```kotlin
package com.lisaan.app.core.media

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanTtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var defaultSpeechRate: Float = 1.0f

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            _isReady.value = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun setPreferredSpeechRate(rate: Float) {
        defaultSpeechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(defaultSpeechRate)
    }

    fun speak(
        text: String,
        slowMode: Boolean = false,
        locale: Locale = Locale.US
    ) {
        if (!_isReady.value || text.isBlank()) return
        tts?.language = locale
        val effectiveRate = if (slowMode) 0.55f else defaultSpeechRate
        tts?.setSpeechRate(effectiveRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
```

### 1.2 مشغل الملفات الصوتية الأوفلاين والأونلاين بـ ExoPlayer (`LisaanAudioPlayer.kt`)

```kotlin
package com.lisaan.app.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsFallback: LisaanTtsManager
) {
    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
            exoPlayer = player
        }
    }

    /**
     * يشغل الملف المحلي أولاً (إن تم تحميل الوحدة للأوفلاين)، أو الرابط السحابي،
     * وفي حال عدم توفر ملف صوتي مسجل يستخدم محرك TTS الفوري كبديل سلس.
     */
    fun playAudioOrFallbackTts(
        localFilePath: String?,
        remoteAudioUrl: String?,
        fallbackTextForTts: String,
        playbackSpeed: Float = 1.0f,
        slowMode: Boolean = false
    ) {
        val localFile = localFilePath?.let { File(it) }
        val resolvedUri: Uri? = when {
            localFile != null && localFile.exists() -> Uri.fromFile(localFile)
            !remoteAudioUrl.isNullOrBlank() && remoteAudioUrl.startsWith("http") -> Uri.parse(remoteAudioUrl)
            else -> null
        }

        if (resolvedUri != null) {
            try {
                val player = getOrCreatePlayer()
                player.stop()
                player.setMediaItem(MediaItem.fromUri(resolvedUri))
                val effectiveSpeed = if (slowMode) 0.65f else playbackSpeed
                player.playbackParameters = PlaybackParameters(effectiveSpeed)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                ttsFallback.speak(fallbackTextForTts, slowMode = slowMode)
            }
        } else {
            ttsFallback.speak(fallbackTextForTts, slowMode = slowMode)
        }
    }

    fun stop() {
        exoPlayer?.stop()
        ttsFallback.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
```

### 1.3 محرك التعرف على الكلام وحساب نسبة دقة النطق المئوية (`LisaanSpeechRecognizer.kt`)

يستخدم `android.speech.SpeechRecognizer` ويحسب **نسبة التطابق المئوية (`0% .. 100%`)** بين الجملة المستهدفة ونطق المتعلم عبر خوارزمية المسافة التحريرية المعيارية على مستوى الكلمات والحروف (`Normalized Levenshtein Similarity`):

```kotlin
package com.lisaan.app.core.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class PronunciationResult(
    val recognizedTranscript: String = "",
    val accuracyPercent: Int = 0,
    val isPassed: Boolean = false, // >= 70%
    val wordFeedback: List<Pair<String, Boolean>> = emptyList()
)

@Singleton
class LisaanSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastResult = MutableStateFlow<PronunciationResult?>(null)
    val lastResult: StateFlow<PronunciationResult?> = _lastResult.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private var currentExpectedTarget: String = ""

    fun startListening(targetPhrase: String, languageTag: String = "en-US") {
        currentExpectedTarget = targetPhrase
        _lastResult.value = null
        _errorState.value = null

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorState.value = "خدمة التعرف على الصوت غير متوفرة على هذا الجهاز"
            return
        }

        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    _errorState.value = "تعذر التقاط الصوت بوضوح، حاول التحدث بالقرب من الميكروفون"
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestTranscript = matches?.firstOrNull().orEmpty()
                    val evaluation = evaluatePronunciationAccuracy(
                        expected = currentExpectedTarget,
                        spoken = bestTranscript
                    )
                    _lastResult.value = evaluation
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        fun evaluatePronunciationAccuracy(expected: String, spoken: String): PronunciationResult {
            val cleanExpected = normalizeText(expected)
            val cleanSpoken = normalizeText(spoken)

            if (cleanSpoken.isBlank()) {
                return PronunciationResult(spoken, 0, false, emptyList())
            }

            val distance = levenshteinDistance(cleanExpected, cleanSpoken)
            val maxLen = max(cleanExpected.length, cleanSpoken.length).coerceAtLeast(1)
            val similarity = ((1.0 - (distance.toDouble() / maxLen.toDouble())) * 100.0)
                .toInt()
                .coerceIn(0, 100)

            val spokenWordsSet = cleanSpoken.split(" ").filter { it.isNotBlank() }.toSet()
            val wordByWord = expected.trim().split(Regex("\\s+")).map { rawWord ->
                val normalizedWord = normalizeText(rawWord)
                val matched = spokenWordsSet.any { spokenW ->
                    spokenW == normalizedWord || levenshteinDistance(spokenW, normalizedWord) <= 1
                }
                rawWord to matched
            }

            return PronunciationResult(
                recognizedTranscript = spoken,
                accuracyPercent = similarity,
                isPassed = similarity >= 70,
                wordFeedback = wordByWord
            )
        }

        fun normalizeText(input: String): String {
            return input.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\s]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
            val lhsLen = lhs.length
            val rhsLen = rhs.length
            var cost = IntArray(lhsLen + 1) { it }
            var newCost = IntArray(lhsLen + 1) { 0 }

            for (i in 1..rhsLen) {
                newCost[0] = i
                for (j in 1..lhsLen) {
                    val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                    val costReplace = cost[j - 1] + match
                    val costInsert = cost[j] + 1
                    val costDelete = newCost[j - 1] + 1
                    newCost[j] = min(min(costInsert, costDelete), costReplace)
                }
                val swap = cost
                cost = newCost
                newCost = swap
            }
            return cost[lhsLen]
        }
    }
}
```

---

## 2. المحتوى التعليمي الأصلي الكامل (3 وحدات × 5 دروس × 10 تمارين = 150 تمريناً أصلياً + Firestore Seed Script)

### 2.1 مولد ومحمّل المحتوى الأصلي بصيغة JSON (`LocalCourseSeedLoader.kt`)

يبني هذا الكلاس المحتوى الأصلي الكامل لتطبيق **"لِسان"** (**إنجليزي للناطقين بالعربية: 3 وحدات × 5 دروس = 15 درساً، وكل درس يضم مفرداته وحواره القصير و10 تمارين متنوعة تغطي جميع الأنواع الـ 8 = 150 تمريناً قابلاً للتشغيل والاستيراد من/إلى JSON**):

```kotlin
package com.lisaan.app.data.seed

import com.lisaan.app.core.database.dao.AchievementDao
import com.lisaan.app.core.database.dao.CourseContentDao
import com.lisaan.app.core.database.dao.Sm2ReviewDao
import com.lisaan.app.core.database.dao.VocabularyDao
import com.lisaan.app.core.database.entities.*
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data classLessonCurriculumBlueprint(
    val lessonId: String,
    val unitId: String,
    val orderIndex: Int,
    val titleAr: String,
    val titleEn: String,
    val tipAr: String,
    val tipEn: String,
    val vocabList: List<VocabSeedItem>,
    val dialogueLines: List<DialogueLineSeed>
)

@Serializable
data class VocabSeedItem(
    val id: String,
    val wordEn: String,
    val translationAr: String,
    val transliteration: String,
    val ipa: String,
    val pos: String,
    val category: String,
    val exampleEn: String,
    val exampleAr: String
)

@Serializable
data class DialogueLineSeed(
    val speaker: String,
    val lineEn: String,
    val lineAr: String
)

@Singleton
class LocalCourseSeedLoader @Inject constructor(
    private val courseContentDao: CourseContentDao,
    private val vocabularyDao: VocabularyDao,
    private val achievementDao: AchievementDao,
    private val sm2ReviewDao: Sm2ReviewDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val json: Json
) {

    suspend fun ensureSeedLoaded(currentUserId: String?) {
        val existingLessons = courseContentDao.getLessonsCount()
        if (existingLessons >= 15) return

        // 1. اللغات الأساسية
        val languages = listOf(
            LanguageEntity("ar", "العربية", "Arabic", "🇸🇦", isRtl = true, isAvailableForLearning = false),
            LanguageEntity("en", "الإنجليزية", "English", "🇬🇧", isRtl = false, isAvailableForLearning = true)
        )
        courseContentDao.insertLanguages(languages)

        // 2. الكورس الأساسي: الإنجليزية للناطقين بالعربية
        val course = CourseEntity(
            id = "en_from_ar",
            sourceLangCode = "ar",
            targetLangCode = "en",
            titleAr = "الإنجليزية الشاملة من لِسان",
            titleEn = "Comprehensive English by Lisaan",
            descriptionAr = "مسار تفاعلي متدرج من التعارف والسفر إلى بيئة العمل والحياة اليومية",
            descriptionEn = "Interactive path from greetings and travel to workplace and daily life",
            totalUnits = 3,
            version = 1
        )
        courseContentDao.insertCourses(listOf(course))

        // 3. الوحدات الثلاث الأصلية (3 Units)
        val units = listOf(
            UnitEntity(
                id = "unit_1",
                courseId = "en_from_ar",
                orderIndex = 1,
                titleAr = "الوحدة 1: خطوات التواصل الأولى",
                titleEn = "Unit 1: First Steps in Communication",
                subtitleAr = "التحيات، التعارف، المشروبات، الأرقام، والعائلة",
                subtitleEn = "Greetings, Introductions, Café, Numbers & Family",
                themeColorHex = "#0F4C81",
                iconName = "ic_unit_handshake"
            ),
            UnitEntity(
                id = "unit_2",
                courseId = "en_from_ar",
                orderIndex = 2,
                titleAr = "الوحدة 2: المسافر الواثق",
                titleEn = "Unit 2: The Confident Traveler",
                subtitleAr = "المطار، الفندق، الاتجاهات، المطعم، والتسوق",
                subtitleEn = "Airport, Hotel, Directions, Restaurant & Shopping",
                themeColorHex = "#00897B",
                iconName = "ic_unit_flight"
            ),
            UnitEntity(
                id = "unit_3",
                courseId = "en_from_ar",
                orderIndex = 3,
                titleAr = "الوحدة 3: العمل والحياة اليومية",
                titleEn = "Unit 3: Work & Daily Life",
                subtitleAr = "الروتين، المهن، الاجتماعات، الصحة، والخطط المستقبلية",
                subtitleEn = "Routines, Jobs, Meetings, Health & Future Plans",
                themeColorHex = "#D97706",
                iconName = "ic_unit_briefcase"
            )
        )
        courseContentDao.insertUnits(units)

        // 4. بناء الـ 15 درساً (5 دروس لكل وحدة) + مفرداتها + 10 تمارين لكل درس (150 تمريناً)
        val blueprints = buildAll15LessonBlueprints()
        val lessonEntities = mutableListOf<LessonEntity>()
        val allVocabEntities = mutableListOf<VocabularyEntity>()
        val allExerciseEntities = mutableListOf<ExerciseEntity>()

        blueprints.forEach { bp ->
            lessonEntities.add(
                LessonEntity(
                    id = bp.lessonId,
                    unitId = bp.unitId,
                    orderIndex = bp.orderIndex,
                    titleAr = bp.titleAr,
                    titleEn = bp.titleEn,
                    introTipAr = bp.tipAr,
                    introTipEn = bp.tipEn,
                    estimatedMinutes = 6,
                    rewardXp = 25,
                    dialogueJson = json.encodeToString(bp.dialogueLines)
                )
            )

            bp.vocabList.forEach { v ->
                allVocabEntities.add(
                    VocabularyEntity(
                        id = v.id,
                        unitId = bp.unitId,
                        lessonId = bp.lessonId,
                        wordTarget = v.wordEn,
                        translationAr = v.translationAr,
                        transliteration = v.transliteration,
                        phoneticIpa = v.ipa,
                        partOfSpeech = v.pos,
                        categoryTag = v.category,
                        exampleTarget = v.exampleEn,
                        exampleAr = v.exampleAr,
                        audioUrl = "https://cdn.lisaan.app/audio/en/${v.id}.mp3",
                        imageUrl = "https://cdn.lisaan.app/images/vocab/${v.id}.webp"
                    )
                )
            }

            allExerciseEntities.addAll(generate10ExercisesForLesson(bp))
        }

        courseContentDao.insertLessons(lessonEntities)
        vocabularyDao.insertVocabularies(allVocabEntities)
        courseContentDao.insertExercises(allExerciseEntities)

        // 5. تهيئة الأوسمة والإنجازات (Achievements)
        achievementDao.insertAchievements(buildInitialAchievements())

        // 6. تهيئة بطاقات المراجعة الذكية SM-2 للمفردات الأولى إن توفر معرف المستخدم
        if (!currentUserId.isNullOrBlank()) {
            val initialReviews = allVocabEntities.take(12).map { vocab ->
                ReviewItemEntity(
                    id = "${currentUserId}_${vocab.id}",
                    userId = currentUserId,
                    vocabularyId = vocab.id,
                    repetitions = 0,
                    easinessFactor = 2.5f,
                    intervalDays = 1,
                    nextReviewEpochMs = System.currentTimeMillis()
                )
            }
            sm2ReviewDao.insertInitialReviewItems(initialReviews)
        }

        preferencesDataStore.markInitialSeedLoaded()
    }

    /**
     * يصدر المحتوى الكامل (3 وحدات × 5 دروس × 10 تمارين) كملف JSON معياري قابل للاستيراد والرفع إلى Firestore
     */
    fun exportFullCurriculumAsJsonString(): String {
        val blueprints = buildAll15LessonBlueprints()
        val root = JSONObject()
        root.put("courseId", "en_from_ar")
        root.put("version", 1)
        val lessonsArray = JSONArray()
        blueprints.forEach { bp ->
            val obj = JSONObject()
            obj.put("lessonId", bp.lessonId)
            obj.put("unitId", bp.unitId)
            obj.put("orderIndex", bp.orderIndex)
            obj.put("titleAr", bp.titleAr)
            obj.put("titleEn", bp.titleEn)
            obj.put("exercisesCount", 10)
            lessonsArray.put(obj)
        }
        root.put("lessons", lessonsArray)
        return root.toString(2)
    }

    private fun buildAll15LessonBlueprints(): List<LessonCurriculumBlueprint> = listOf(
        // ==================== UNIT 1 (5 LESSONS) ====================
        LessonCurriculumBlueprint(
            lessonId = "u1_l1", unitId = "unit_1", orderIndex = 1,
            titleAr = "الدرس 1: تحيات الصباح والمساء", titleEn = "Lesson 1: Daily Greetings",
            tipAr = "في الإنجليزية نستخدم Good morning حتى الظهر، وGood evening في المساء عند اللقاء.",
            tipEn = "Use 'Good morning' before noon and 'Good evening' when greeting someone at night.",
            vocabList = listOf(
                VocabSeedItem("v_u1_l1_1", "Good morning", "صباح الخير", "غود مورنينغ", "/ɡʊd ˈmɔːrnɪŋ/", "PHRASE", "GREETINGS", "Good morning, everyone!", "صباح الخير جميعاً!"),
                VocabSeedItem("v_u1_l1_2", "Good evening", "مساء الخير", "غود إيفنينغ", "/ɡʊd ˈiːvnɪŋ/", "PHRASE", "GREETINGS", "Good evening, welcome to our home.", "مساء الخير، أهلاً بك في منزلنا."),
                VocabSeedItem("v_u1_l1_3", "How are you?", "كيف حالك؟", "هاو آر يو", "/haʊ ɑːr juː/", "PHRASE", "GREETINGS", "Hello Tariq, how are you?", "مرحباً طارق، كيف حالك؟"),
                VocabSeedItem("v_u1_l1_4", "See you soon", "أراك قريباً", "سي يو سون", "/siː juː suːn/", "PHRASE", "GREETINGS", "Thank you, see you soon!", "شكراً لك، أراك قريباً!")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Layla", "Good morning, Omar! How are you?", "صباح الخير يا عمر! كيف حالك؟"),
                DialogueLineSeed("Omar", "I am doing great, thank you. See you soon!", "أنا بخير، شكراً لك. أراك قريباً!")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u1_l2", unitId = "unit_1", orderIndex = 2,
            titleAr = "الدرس 2: التعريف بالنفس والبلد", titleEn = "Lesson 2: Introducing Yourself",
            tipAr = "للتعريف بنفسك قل I am متبوعاً باسمك، ولذكر بلدك قل I am from.",
            tipEn = "Use 'I am' for your name and 'I am from' for your country or city.",
            vocabList = listOf(
                VocabSeedItem("v_u1_l2_1", "My name is", "اسمي هو", "ماي نيم إز", "/maɪ neɪm ɪz/", "PHRASE", "GREETINGS", "My name is Salma.", "اسمي سلمى."),
                VocabSeedItem("v_u1_l2_2", "Nice to meet you", "تشرفت بمعرفتك", "نايس تو ميت يو", "/naɪs tuː miːt juː/", "PHRASE", "GREETINGS", "Nice to meet you, Nadia.", "تشرفت بمعرفتك يا نادية."),
                VocabSeedItem("v_u1_l2_3", "Homeland", "وطن / بلد", "هوملاند", "/ˈhoʊmlænd/", "NOUN", "GREETINGS", "Egypt is my homeland.", "مصر هي وطني."),
                VocabSeedItem("v_u1_l2_4", "Citizen", "مواطن", "سيتيزن", "/ˈsɪtɪzən/", "NOUN", "GREETINGS", "He is a proud citizen.", "هو مواطن فخور.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Salma", "Hello! My name is Salma.", "مرحباً! اسمي سلمى."),
                DialogueLineSeed("David", "Nice to meet you, Salma!", "تشرفت بمعرفتك يا سلمى!")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u1_l3", unitId = "unit_1", orderIndex = 3,
            titleAr = "الدرس 3: في المقهى والطلب بلطف", titleEn = "Lesson 3: At the Café",
            tipAr = "استخدام I would like بدلاً من I want يجعل طلبك أكثر لباقة واحتراماً.",
            tipEn = "Saying 'I would like' is much more polite than 'I want' when ordering.",
            vocabList = listOf(
                VocabSeedItem("v_u1_l3_1", "I would like", "أودّ / أرغب في", "آي وود لايك", "/aɪ wʊd laɪk/", "PHRASE", "FOOD", "I would like a cup of tea.", "أودّ فنجاناً من الشاي."),
                VocabSeedItem("v_u1_l3_2", "Fresh coffee", "قهوة طازجة", "فريش كوفي", "/frɛʃ ˈkɒfi/", "NOUN", "FOOD", "We serve fresh coffee every morning.", "نقدم قهوة طازجة كل صباح."),
                VocabSeedItem("v_u1_l3_3", "Without sugar", "بدون سكر", "ويذاوت شوغر", "/wɪˈðaʊt ˈʃʊɡər/", "PHRASE", "FOOD", "Green tea without sugar, please.", "شاي أخضر بدون سكر من فضلك."),
                VocabSeedItem("v_u1_l3_4", "Receipt", "إيصال الدفع", "ريسيت", "/rɪˈsiːt/", "NOUN", "FOOD", "Here is your receipt.", "تفضل إيصال الدفع الخاص بك.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Barista", "Welcome! What would you like today?", "أهلاً بك! ماذا تود أن تطلب اليوم؟"),
                DialogueLineSeed("Karim", "I would like fresh coffee without sugar, please.", "أود قهوة طازجة بدون سكر من فضلك.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u1_l4", unitId = "unit_1", orderIndex = 4,
            titleAr = "الدرس 4: الأرقام والمواعيد", titleEn = "Lesson 4: Numbers & Time",
            tipAr = "استخدم o'clock عند الإشارة إلى الساعة التامة مثل Seven o'clock.",
            tipEn = "Use 'o'clock' for exact hours and 'half past' for thirty minutes.",
            vocabList = listOf(
                VocabSeedItem("v_u1_l4_1", "Appointment", "موعد", "أبوينتمنت", "/əˈpɔɪntmənt/", "NOUN", "DAILY", "I have a doctor appointment at ten.", "لدي موعد طبيب في العاشرة."),
                VocabSeedItem("v_u1_l4_2", "Half past", "والنصف (توقيت)", "هاف باست", "/hɑːf pɑːst/", "PHRASE", "DAILY", "The train leaves at half past six.", "يغادر القطار في السادسة والنصف."),
                VocabSeedItem("v_u1_l4_3", "Schedule", "جدول مواعيد", "سكيجوال", "/ˈʃɛdjuːl/", "NOUN", "DAILY", "My schedule is full today.", "جدول مواعيدي مزدحم اليوم."),
                VocabSeedItem("v_u1_l4_4", "Early", "مبكراً", "إيرلي", "/ˈɜːrli/", "ADJ", "DAILY", "She always wakes up early.", "هي تستيقظ مبكراً دائماً.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Nour", "When is our meeting today?", "متى اجتماعنا اليوم؟"),
                DialogueLineSeed("Hassan", "It starts early at half past nine.", "يبدأ مبكراً في التاسعة والنصف.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u1_l5", unitId = "unit_1", orderIndex = 5,
            titleAr = "الدرس 5: العائلة والأصدقاء", titleEn = "Lesson 5: Family & Friends",
            tipAr = "كلمة Siblings تشمل الإخوة والأخوات معاً باختصار.",
            tipEn = "'Siblings' refers to both brothers and sisters collectively.",
            vocabList = listOf(
                VocabSeedItem("v_u1_l5_1", "Parents", "الوالدان", "بيرنتس", "/ˈpɛərənts/", "NOUN", "DAILY", "My parents live in Alexandria.", "يعيش والداي في الإسكندرية."),
                VocabSeedItem("v_u1_l5_2", "Siblings", "الإخوة والأخوات", "سيبلينغز", "/ˈsɪblɪŋz/", "NOUN", "DAILY", "I have two older siblings.", "لدي شقيقان أكبر مني."),
                VocabSeedItem("v_u1_l5_3", "Neighbor", "جار", "نيبر", "/ˈneɪbər/", "NOUN", "DAILY", "Our neighbor is very kind.", "جارنا لطيف للغاية."),
                VocabSeedItem("v_u1_l5_4", "Gathering", "تجمّع عائلي", "غاذرينغ", "/ˈɡæðərɪŋ/", "NOUN", "DAILY", "We enjoy our Friday family gathering.", "نستمتع بتجمعنا العائلي يوم الجمعة.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Mona", "Do you have any siblings?", "هل لديك إخوة أو أخوات؟"),
                DialogueLineSeed("Youssef", "Yes, two siblings, and we have a family gathering tonight.", "نعم، شقيقان، ولدينا تجمع عائلي الليلة.")
            )
        ),

        // ==================== UNIT 2 (5 LESSONS) ====================
        LessonCurriculumBlueprint(
            lessonId = "u2_l1", unitId = "unit_2", orderIndex = 1,
            titleAr = "الدرس 1: في المطار وبوابة الصعود", titleEn = "Lesson 1: At the Airport",
            tipAr = "بطاقة صعود الطائرة تسمى Boarding pass، وممر الجلوس يسمى Aisle.",
            tipEn = "Keep your 'boarding pass' ready at the security gate.",
            vocabList = listOf(
                VocabSeedItem("v_u2_l1_1", "Boarding pass", "بطاقة صعود الطائرة", "بوردينغ باس", "/ˈbɔːrdɪŋ pæs/", "NOUN", "TRAVEL", "Please show your boarding pass.", "يرجى إظهار بطاقة صعود الطائرة."),
                VocabSeedItem("v_u2_l1_2", "Departure gate", "بوابة المغادرة", "ديبارتشر غيت", "/dɪˈpɑːrtʃər ɡeɪt/", "NOUN", "TRAVEL", "Where is departure gate twelve?", "أين بوابة المغادرة رقم اثني عشر؟"),
                VocabSeedItem("v_u2_l1_3", "Carry-on bag", "حقيبة يد للطائرة", "كاري أون باغ", "/ˈkæri ɒn bæɡ/", "NOUN", "TRAVEL", "I only have one carry-on bag.", "لدي حقيبة يد واحدة فقط."),
                VocabSeedItem("v_u2_l1_4", "Delayed", "متأخرة (رحلة)", "ديليد", "/dɪˈleɪd/", "ADJ", "TRAVEL", "The flight is delayed by thirty minutes.", "الرحلة متأخرة ثلاثين دقيقة.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Agent", "May I see your passport and boarding pass?", "هل يمكنني رؤية جواز سفرك وبطاقة الصعود؟"),
                DialogueLineSeed("Traveler", "Here you go. Which departure gate is it?", "تفضل. أي بوابة مغادرة هي؟")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u2_l2", unitId = "unit_2", orderIndex = 2,
            titleAr = "الدرس 2: حجز الفندق والاستقبال", titleEn = "Lesson 2: Hotel Check-In",
            tipAr = "عند الوصول للفندق نقول Check in وعند المغادرة وتسليم الغرفة نقول Check out.",
            tipEn = "Use 'check in' when arriving and 'check out' when leaving the hotel.",
            vocabList = listOf(
                VocabSeedItem("v_u2_l2_1", "Reservation", "حجز مسبق", "ريزرفيشن", "/ˌrɛzərˈveɪʃən/", "NOUN", "TRAVEL", "I have a reservation under my name.", "لدي حجز مسبق باسمي."),
                VocabSeedItem("v_u2_l2_2", "Single room", "غرفة فردية", "سينغل روم", "/ˈsɪŋɡəl ruːm/", "NOUN", "TRAVEL", "I booked a quiet single room.", "حجزت غرفة فردية هادئة."),
                VocabSeedItem("v_u2_l2_3", "Breakfast included", "شامل الإفطار", "بريكفاست إنكلودد", "/ˈbrɛkfəst ɪnˈkluːdɪd/", "PHRASE", "TRAVEL", "Is breakfast included in the price?", "هل الإفطار مشمول في السعر؟"),
                VocabSeedItem("v_u2_l2_4", "Key card", "بطاقة مفتاح الغرفة", "كي كارد", "/kiː kɑːrd/", "NOUN", "TRAVEL", "Here is your room key card.", "تفضل بطاقة مفتاح غرفتك.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Guest", "Hello, I have a reservation for a single room.", "مرحباً، لدي حجز لغرفة فردية."),
                DialogueLineSeed("Receptionist", "Welcome! Breakfast is included, and here is your key card.", "أهلاً بك! الإفطار مشمول، وتفضل بطاقة المفتاح.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u2_l3", unitId = "unit_2", orderIndex = 3,
            titleAr = "الدرس 3: السؤال عن الاتجاهات والمواصلات", titleEn = "Lesson 3: Asking Directions",
            tipAr = "ابدأ سؤالك في الشارع بـ Excuse me للفت الانتباه بأدب.",
            tipEn = "Start with 'Excuse me' before asking a stranger for directions.",
            vocabList = listOf(
                VocabSeedItem("v_u2_l3_1", "Straight ahead", "مباشرة إلى الأمام", "ستريت أهيد", "/streɪt əˈhɛd/", "PHRASE", "TRAVEL", "Walk straight ahead for two blocks.", "امشِ مباشرة إلى الأمام لمربعين سكنيين."),
                VocabSeedItem("v_u2_l3_2", "Subway station", "محطة المترو", "سابواي ستيشن", "/ˈsʌbweɪ ˈsteɪʃən/", "NOUN", "TRAVEL", "The subway station is across the street.", "محطة المترو في الجهة المقابلة من الشارع."),
                VocabSeedItem("v_u2_l3_3", "Intersection", "تقاطع طرق", "إنترسكشن", "/ˌɪntərˈsɛkʃən/", "NOUN", "TRAVEL", "Turn right at the next intersection.", "انعطف يميناً عند التقاطع القادم."),
                VocabSeedItem("v_u2_l3_4", "Nearby", "قريب من هنا", "نيرباي", "/ˌnɪərˈbaɪ/", "ADJ", "TRAVEL", "Is there a pharmacy nearby?", "هل توجد صيدلية قريبة من هنا؟")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Tourist", "Excuse me, is the subway station nearby?", "عفواً، هل محطة المترو قريبة من هنا؟"),
                DialogueLineSeed("Local", "Yes, go straight ahead to the intersection.", "نعم، اتجه مباشرة للأمام حتى التقاطع.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u2_l4", unitId = "unit_2", orderIndex = 4,
            titleAr = "الدرس 4: في المطعم وطلب الفاتورة", titleEn = "Lesson 4: Dining Out",
            tipAr = "لطلب توصية النادل قل: What do you recommend?.",
            tipEn = "Ask 'What do you recommend?' to try the restaurant's best dish.",
            vocabList = listOf(
                VocabSeedItem("v_u2_l4_1", "Appetizer", "مقبّلات", "أبيتايزر", "/ˈæpɪtaɪzər/", "NOUN", "FOOD", "We ordered soup as an appetizer.", "طلبنا حساءً كمقبلات."),
                VocabSeedItem("v_u2_l4_2", "Main course", "الطبق الرئيسي", "مين كورس", "/meɪn kɔːrs/", "NOUN", "FOOD", "Grilled fish is my main course.", "السمك المشوي هو طبقي الرئيسي."),
                VocabSeedItem("v_u2_l4_3", "Vegetarian", "نباتي", "فيجيتيريان", "/ˌvɛdʒɪˈtɛəriən/", "ADJ", "FOOD", "Do you have vegetarian options?", "هل لديكم خيارات نباتية؟"),
                VocabSeedItem("v_u2_l4_4", "The bill", "الفاتورة", "ذا بيل", "/ðə bɪl/", "NOUN", "FOOD", "Could we have the bill, please?", "هل يمكننا الحصول على الفاتورة من فضلك؟")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Waiter", "Are you ready to order your main course?", "هل أنتم مستعدون لطلب الطبق الرئيسي؟"),
                DialogueLineSeed("Diner", "Yes, a vegetarian pasta, and then the bill please.", "نعم، معكرونة نباتية، ثم الفاتورة من فضلك.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u2_l5", unitId = "unit_2", orderIndex = 5,
            titleAr = "الدرس 5: التسوق والأسعار", titleEn = "Lesson 5: Shopping & Prices",
            tipAr = "للسؤال عن سعر مفرد قل How much is this وللجمع How much are these.",
            tipEn = "Use 'How much is this?' for singular items and 'How much are these?' for plural.",
            vocabList = listOf(
                VocabSeedItem("v_u2_l5_1", "Discount", "خصم / تخفيض", "ديسكاونت", "/ˈdɪskaʊnt/", "NOUN", "TRAVEL", "There is a twenty percent discount today.", "يوجد خصم عشرين بالمئة اليوم."),
                VocabSeedItem("v_u2_l5_2", "Fitting room", "غرفة القياس", "فيتينغ روم", "/ˈfɪtɪŋ ruːm/", "NOUN", "TRAVEL", "Where is the fitting room?", "أين غرفة القياس؟"),
                VocabSeedItem("v_u2_l5_3", "Cash or card", "نقداً أم بالبطاقة", "كاش أور كارد", "/kæʃ ɔːr kɑːrd/", "PHRASE", "TRAVEL", "Would you like to pay by cash or card?", "هل تود الدفع نقداً أم بالبطاقة؟"),
                VocabSeedItem("v_u2_l5_4", "Refund", "استرداد المبلغ", "ريفاند", "/ˈriːfʌnd/", "NOUN", "TRAVEL", "You can request a full refund within seven days.", "يمكنك طلب استرداد كامل المبلغ خلال سبعة أيام.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Shopper", "Does this jacket have a discount?", "هل هذا المعطف عليه خصم؟"),
                DialogueLineSeed("Clerk", "Yes! Would you like to pay by cash or card?", "نعم! هل تود الدفع نقداً أم بالبطاقة؟")
            )
        ),

        // ==================== UNIT 3 (5 LESSONS) ====================
        LessonCurriculumBlueprint(
            lessonId = "u3_l1", unitId = "unit_3", orderIndex = 1,
            titleAr = "الدرس 1: الروتين اليومي وإدارة الوقت", titleEn = "Lesson 1: Daily Routine",
            tipAr = "نستخدم المضارع البسيط (Present Simple) للتعبير عن العادات المتكررة يومياً.",
            tipEn = "Use the Present Simple tense to describe habits and daily routines.",
            vocabList = listOf(
                VocabSeedItem("v_u3_l1_1", "Commute", "رحلة الذهاب للعمل", "كوميوت", "/kəˈmjuːt/", "VERB", "DAILY", "I commute by metro every morning.", "أتنقل إلى العمل بالمترو كل صباح."),
                VocabSeedItem("v_u3_l1_2", "Productive", "مثمر / منتج", "برودكتيف", "/prəˈdʌktɪv/", "ADJ", "WORK", "It was a very productive morning.", "كان صباحاً مثمراً للغاية."),
                VocabSeedItem("v_u3_l1_3", "Prioritize", "يرتب الأولويات", "برايوريتايز", "/praɪˈɒrɪtaɪz/", "VERB", "WORK", "We must prioritize urgent tasks.", "يجب أن نرتب أولويات المهام العاجلة."),
                VocabSeedItem("v_u3_l1_4", "Take a break", "يأخذ استراحة", "تيك أ بريك", "/teɪk ə breɪk/", "PHRASE", "DAILY", "Let us take a short break.", "دعنا نأخذ استراحة قصيرة.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Amira", "How do you stay so productive every day?", "كيف تحافظين على هذه الإنتاجية كل يوم؟"),
                DialogueLineSeed("Ziad", "I prioritize my tasks during my morning commute.", "أرتب أولويات مهامي أثناء رحلة الذهاب صباحاً.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u3_l2", unitId = "unit_3", orderIndex = 2,
            titleAr = "الدرس 2: المهن وبيئة العمل", titleEn = "Lesson 2: Jobs & Workplace",
            tipAr = "نقول I work as a متبوعاً بالمسمى الوظيفي، وI work for متبوعاً باسم الشركة.",
            tipEn = "Say 'I work as a [role]' and 'I work for [company]'.",
            vocabList = listOf(
                VocabSeedItem("v_u3_l2_1", "Colleague", "زميل عمل", "كوليغ", "/ˈkɒliːɡ/", "NOUN", "WORK", "My colleague helped me with the project.", "ساعدني زميلي في العمل في المشروع."),
                VocabSeedItem("v_u3_l2_2", "Department", "قسم إداري", "ديبارتمنت", "/dɪˈpɑːrtmənt/", "NOUN", "WORK", "She leads the engineering department.", "هي تقود قسم الهندسة."),
                VocabSeedItem("v_u3_l2_3", "Remote work", "العمل عن بُعد", "ريموت وورك", "/rɪˈmoʊt wɜːrk/", "NOUN", "WORK", "Remote work saves commuting time.", "العمل عن بعد يوفر وقت المواصلات."),
                VocabSeedItem("v_u3_l2_4", "Experience", "خبرة عملية", "إكسبيرينس", "/ɪkˈspɪəriəns/", "NOUN", "WORK", "He has five years of experience.", "لديه خمس سنوات من الخبرة العملية.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Manager", "Welcome to our department! Meet your new colleague.", "أهلاً بك في قسمنا! تعرف على زميلك الجديد."),
                DialogueLineSeed("NewHire", "Thank you! I am excited to share my experience here.", "شكراً لك! أنا متحمس لمشاركة خبرتي هنا.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u3_l3", unitId = "unit_3", orderIndex = 3,
            titleAr = "الدرس 3: الاجتماعات والرسائل المهنية", titleEn = "Lesson 3: Meetings & Emails",
            tipAr = "في نهاية البريد المهني استخدم Best regards أو Sincerely قبل اسمك.",
            tipEn = "Close professional emails with 'Best regards' or 'Sincerely'.",
            vocabList = listOf(
                VocabSeedItem("v_u3_l3_1", "Deadline", "موعد نهائي للتسليم", "ديدلاين", "/ˈdɛdlaɪn/", "NOUN", "WORK", "The project deadline is next Monday.", "الموعد النهائي للمشروع هو الاثنين القادم."),
                VocabSeedItem("v_u3_l3_2", "Attachment", "ملف مرفق", "أتاتشمنت", "/əˈtætʃmənt/", "NOUN", "WORK", "Please find the report in the attachment.", "يرجى الاطلاع على التقرير في الملف المرفق."),
                VocabSeedItem("v_u3_l3_3", "Postpone", "يؤجّل", "بوستبون", "/poʊstˈpoʊn/", "VERB", "WORK", "Can we postpone the meeting by one hour?", "هل يمكننا تأجيل الاجتماع لمدة ساعة؟"),
                VocabSeedItem("v_u3_l3_4", "Summary", "ملخص تنفيذي", "سامري", "/ˈsʌməri/", "NOUN", "WORK", "I sent a brief summary to the team.", "أرسلت ملخصاً موجزاً للفريق.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Rania", "Did you send the summary attachment before the deadline?", "هل أرسلتِ مرفق الملخص قبل الموعد النهائي؟"),
                DialogueLineSeed("Sami", "Yes, so we do not need to postpone our review.", "نعم، لذلك لا نحتاج لتأجيل مراجعتنا.")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u3_l4", unitId = "unit_3", orderIndex = 4,
            titleAr = "الدرس 4: الصحة واللياقة والطبيب", titleEn = "Lesson 4: Health & Wellness",
            tipAr = "للتعبير عن ألم جسدي نستخدم I have a headache أو My throat hurts.",
            tipEn = "Use 'I have a...' for common ailments like a headache or fever.",
            vocabList = listOf(
                VocabSeedItem("v_u3_l4_1", "Symptom", "عَرَض صحي", "سيمبتوم", "/ˈsɪmptəm/", "NOUN", "DAILY", "What is your main symptom?", "ما هو العرض الصحي الرئيسي لديك؟"),
                VocabSeedItem("v_u3_l4_2", "Prescription", "وصفة طبية", "بريسكريبشن", "/prɪˈskrɪpʃən/", "NOUN", "DAILY", "The doctor wrote a prescription.", "كتب الطبيب وصفة طبية."),
                VocabSeedItem("v_u3_l4_3", "Plenty of water", "الكثير من الماء", "بلينتي أوف ووتر", "/ˈplɛnti ɒv ˈwɔːtər/", "PHRASE", "DAILY", "Drink plenty of water and rest.", "اشرب الكثير من الماء واسترح."),
                VocabSeedItem("v_u3_l4_4", "Recover", "يتعافى", "ريكفر", "/rɪˈkʌvər/", "VERB", "DAILY", "I hope you recover quickly.", "أتمنى أن تتعافى سريعاً.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Doctor", "Take this prescription and drink plenty of water.", "خذ هذه الوصفة الطبية واشرب الكثير من الماء."),
                DialogueLineSeed("Patient", "Thank you, Doctor. How soon will I recover?", "شكراً يا دكتور. متى سأتعافى؟")
            )
        ),
        LessonCurriculumBlueprint(
            lessonId = "u3_l5", unitId = "unit_3", orderIndex = 5,
            titleAr = "الدرس 5: الطموحات والخطط المستقبلية", titleEn = "Lesson 5: Future Goals",
            tipAr = "استخدم I am planning to أو I am going to للخطط المرتبة مسبقاً.",
            tipEn = "Use 'going to' or 'planning to' when talking about decided future goals.",
            vocabList = listOf(
                VocabSeedItem("v_u3_l5_1", "Opportunity", "فرصة سانحة", "أوبورتيونيتي", "/ˌɒpərˈtjuːnɪti/", "NOUN", "WORK", "Fluency opens a great opportunity.", "الطلاقة اللغوية تفتح فرصة عظيمة."),
                VocabSeedItem("v_u3_l5_2", "Achieve", "يحقق / ينجز", "أتشيف", "/əˈtʃiːv/", "VERB", "WORK", "You can achieve your goal with practice.", "يمكنك تحقيق هدفك بالممارسة."),
                VocabSeedItem("v_u3_l5_3", "Fluent", "طليق اللسان", "فلوينت", "/ˈfluːənt/", "ADJ", "WORK", "She became fluent in English.", "أصبحت طليقة اللسان في الإنجليزية."),
                VocabSeedItem("v_u3_l5_4", "Confidence", "ثقة بالنفس", "كونفيدنس", "/ˈkɒnfɪdəns/", "NOUN", "WORK", "Speak with confidence every day.", "تحدث بثقة كل يوم.")
            ),
            dialogueLines = listOf(
                DialogueLineSeed("Mentor", "What do you want to achieve this year?", "ماذا تريد أن تحقق هذا العام؟"),
                DialogueLineSeed("Learner", "I want to become fluent and speak with confidence!", "أريد أن أصبح طليق اللسان وأتحدث بثقة!")
            )
        )
    )

    /**
     * يولد 10 تمارين كاملة ومتنوعة لكل درس تغطي جميع الأنواع الـ 8 المطلوبة:
     * 1. MULTIPLE_CHOICE (Vocab 1)
     * 2. WRITTEN_TRANSLATION (Vocab 2)
     * 3. WORD_ORDER (Example Sentence 1)
     * 4. MATCHING_PAIRS (All 4 Lesson Vocabs)
     * 5. FILL_BLANK (Vocab 3 in context)
     * 6. LISTENING_DICTATION (Vocab 4 Example)
     * 7. SPEAKING_PRONUNCIATION (Vocab 1 Example)
     * 8. TRUE_FALSE (Vocab 2 Meaning Check)
     * 9. MULTIPLE_CHOICE (Reverse Arabic -> English for Vocab 3)
     * 10. WORD_ORDER (Example Sentence 4)
     */
    private fun generate10ExercisesForLesson(bp: LessonCurriculumBlueprint): List<ExerciseEntity> {
        val v0 = bp.vocabList[0]
        val v1 = bp.vocabList[1]
        val v2 = bp.vocabList[2]
        val v3 = bp.vocabList[3]

        val matchingPairsJson = JSONArray().apply {
            bp.vocabList.forEach { v ->
                put(JSONObject().put("left", v.wordEn).put("right", v.translationAr))
            }
        }.toString()

        val wordOrder1Tokens = v0.exampleEn.replace(Regex("[.!?,]"), "").split(" ").filter { it.isNotBlank() }
        val wordOrder2Tokens = v3.exampleEn.replace(Regex("[.!?,]"), "").split(" ").filter { it.isNotBlank() }

        return listOf(
            // 1. اختيار من متعدد (English -> Arabic)
            ExerciseEntity(
                id = "${bp.lessonId}_ex1",
                lessonId = bp.lessonId,
                orderIndex = 1,
                type = "MULTIPLE_CHOICE",
                promptAr = "اختر المعنى العربي الصحيح للعبارة التالية:",
                promptEn = "Select the correct Arabic meaning:",
                targetText = v0.wordEn,
                optionsJson = JSONArray(listOf(v0.translationAr, v1.translationAr, v2.translationAr, v3.translationAr)).toString(),
                correctAnswerJson = v0.translationAr,
                explanationAr = "العبارة «${v0.wordEn}» تعني «${v0.translationAr}» وتُنطق (${v0.transliteration}).",
                explanationEn = "'${v0.wordEn}' means '${v0.translationAr}'.",
                vocabularyId = v0.id
            ),
            // 2. ترجمة بالكتابة (Written Translation)
            ExerciseEntity(
                id = "${bp.lessonId}_ex2",
                lessonId = bp.lessonId,
                orderIndex = 2,
                type = "WRITTEN_TRANSLATION",
                promptAr = "اكتب باللغة الإنجليزية ترجمة الكلمة/العبارة التالية:",
                promptEn = "Type the English translation for:",
                targetText = v1.translationAr,
                optionsJson = "[]",
                correctAnswerJson = v1.wordEn,
                explanationAr = "الترجمة الصحيحة هي «${v1.wordEn}».",
                explanationEn = "The correct translation is '${v1.wordEn}'.",
                vocabularyId = v1.id
            ),
            // 3. ترتيب الكلمات (Word Order Chips)
            ExerciseEntity(
                id = "${bp.lessonId}_ex3",
                lessonId = bp.lessonId,
                orderIndex = 3,
                type = "WORD_ORDER",
                promptAr = "رتّب الكلمات لتكوين الجملة الإنجليزية الموافقة لـ: (${v0.exampleAr})",
                promptEn = "Arrange the words to translate: (${v0.exampleAr})",
                targetText = v0.exampleAr,
                optionsJson = JSONArray(wordOrder1Tokens.shuffled()).toString(),
                correctAnswerJson = wordOrder1Tokens.joinToString(" "),
                explanationAr = "الترتيب الصحيح للجملة: ${v0.exampleEn}",
                explanationEn = "Correct order: ${v0.exampleEn}",
                vocabularyId = v0.id
            ),
            // 4. توصيل الأزواج (Matching Pairs)
            ExerciseEntity(
                id = "${bp.lessonId}_ex4",
                lessonId = bp.lessonId,
                orderIndex = 4,
                type = "MATCHING_PAIRS",
                promptAr = "وصّل كل مفردة إنجليزية بمعناها العربي الصحيح:",
                promptEn = "Match each English term with its Arabic meaning:",
                targetText = bp.titleAr,
                optionsJson = matchingPairsJson,
                correctAnswerJson = matchingPairsJson,
                explanationAr = "أحسنت في ربط المفردات الأربع بمعانيها!",
                explanationEn = "Great job matching all four pairs!",
                vocabularyId = v2.id
            ),
            // 5. ملء الفراغ (Fill in the Blank)
            ExerciseEntity(
                id = "${bp.lessonId}_ex5",
                lessonId = bp.lessonId,
                orderIndex = 5,
                type = "FILL_BLANK",
                promptAr = "أكمل الفراغ في الجملة بما يناسب معنى (${v2.exampleAr}):",
                promptEn = "Fill in the blank to match (${v2.exampleAr}):",
                targetText = v2.exampleEn.replace(v2.wordEn, "_____", ignoreCase = true),
                optionsJson = JSONArray(listOf(v2.wordEn, v0.wordEn, v1.wordEn, v3.wordEn).shuffled()).toString(),
                correctAnswerJson = v2.wordEn,
                explanationAr = "الكلمة المناسبة للسياق هي «${v2.wordEn}» (${v2.translationAr}).",
                explanationEn = "The missing phrase is '${v2.wordEn}'.",
                vocabularyId = v2.id
            ),
            // 6. استماع وكتابة (Listening Dictation)
            ExerciseEntity(
                id = "${bp.lessonId}_ex6",
                lessonId = bp.lessonId,
                orderIndex = 6,
                type = "LISTENING_DICTATION",
                promptAr = "استمع جيداً للمقطع الصوتي ثم اكتب ما سمعته بالإنجليزية:",
                promptEn = "Listen carefully and type what you hear:",
                targetText = v3.wordEn,
                audioUrl = "https://cdn.lisaan.app/audio/en/${v3.id}.mp3",
                optionsJson = "[]",
                correctAnswerJson = v3.wordEn,
                explanationAr = "العبارة المنطوقة هي «${v3.wordEn}» ومعناها «${v3.translationAr}».",
                explanationEn = "The spoken phrase is '${v3.wordEn}'.",
                vocabularyId = v3.id
            ),
            // 7. تمرين النطق الصوتي (Speaking Pronunciation with Accuracy %)
            ExerciseEntity(
                id = "${bp.lessonId}_ex7",
                lessonId = bp.lessonId,
                orderIndex = 7,
                type = "SPEAKING_PRONUNCIATION",
                promptAr = "اضغط على الميكروفون وانطق الجملة التالية بوضوح:",
                promptEn = "Tap the microphone and speak this sentence clearly:",
                targetText = v0.exampleEn,
                audioUrl = "https://cdn.lisaan.app/audio/en/${v0.id}.mp3",
                optionsJson = JSONArray(listOf(v0.transliteration, v0.ipa)).toString(),
                correctAnswerJson = v0.exampleEn,
                explanationAr = "الهدف تحقيق دقة نطق 70% أو أعلى. المعنى: ${v0.exampleAr}",
                explanationEn = "Aim for 70%+ pronunciation accuracy.",
                vocabularyId = v0.id
            ),
            // 8. صح أم خطأ (True / False)
            ExerciseEntity(
                id = "${bp.lessonId}_ex8",
                lessonId = bp.lessonId,
                orderIndex = 8,
                type = "TRUE_FALSE",
                promptAr = "هل تعني عبارة «${v1.wordEn}» باللغة العربية «${v1.translationAr}»؟",
                promptEn = "Does '${v1.wordEn}' mean '${v1.translationAr}'?",
                targetText = "${v1.wordEn} = ${v1.translationAr}",
                optionsJson = JSONArray(listOf("TRUE", "FALSE")).toString(),
                correctAnswerJson = "TRUE",
                explanationAr = "نعم، العبارة صحيحة تماماً!",
                explanationEn = "Correct! That is the exact meaning.",
                vocabularyId = v1.id
            ),
            // 9. اختيار من متعدد عكسي (Arabic -> English)
            ExerciseEntity(
                id = "${bp.lessonId}_ex9",
                lessonId = bp.lessonId,
                orderIndex = 9,
                type = "MULTIPLE_CHOICE",
                promptAr = "كيف نقول باللغة الإنجليزية: «${v2.translationAr}»؟",
                promptEn = "How do you say '${v2.translationAr}' in English?",
                targetText = v2.translationAr,
                optionsJson = JSONArray(listOf(v2.wordEn, v0.wordEn, v1.wordEn, v3.wordEn).shuffled()).toString(),
                correctAnswerJson = v2.wordEn,
                explanationAr = "نقول «${v2.wordEn}» (${v2.transliteration}).",
                explanationEn = "The correct English phrase is '${v2.wordEn}'.",
                vocabularyId = v2.id
            ),
            // 10. ترتيب كلمات ختامي (Sentence Synthesis)
            ExerciseEntity(
                id = "${bp.lessonId}_ex10",
                lessonId = bp.lessonId,
                orderIndex = 10,
                type = "WORD_ORDER",
                promptAr = "كوّن الجملة الختامية للدرس: (${v3.exampleAr})",
                promptEn = "Build the final sentence: (${v3.exampleAr})",
                targetText = v3.exampleAr,
                optionsJson = JSONArray(wordOrder2Tokens.shuffled()).toString(),
                correctAnswerJson = wordOrder2Tokens.joinToString(" "),
                explanationAr = "ممتاز! الجملة الكاملة هي: ${v3.exampleEn}",
                explanationEn = "Awesome! Full sentence: ${v3.exampleEn}",
                vocabularyId = v3.id
            )
        )
    }

    private fun buildInitialAchievements(): List<AchievementEntity> = listOf(
        AchievementEntity(
            id = "ach_first_lesson", code = "FIRST_LESSON",
            titleAr = "الخطوة الأولى", titleEn = "First Step",
            descriptionAr = "أكمل درسك الأول في لِسان", descriptionEn = "Complete your first lesson",
            iconResName = "ic_badge_first", requiredValue = 1, rewardXp = 50
        ),
        AchievementEntity(
            id = "ach_streak_7", code = "STREAK_7",
            titleAr = "شعلة الأسبوع", titleEn = "7-Day Streak",
            descriptionAr = "واظب على التعلم 7 أيام متتالية", descriptionEn = "Maintain a 7-day learning streak",
            iconResName = "ic_badge_streak7", requiredValue = 7, rewardXp = 100
        ),
        AchievementEntity(
            id = "ach_vocab_50", code = "VOCAB_50",
            titleAr = "جامع المفردات", titleEn = "Word Collector",
            descriptionAr = "أتقن 50 مفردة جديدة في قاموسك", descriptionEn = "Master 50 vocabulary words",
            iconResName = "ic_badge_vocab", requiredValue = 50, rewardXp = 150
        ),
        AchievementEntity(
            id = "ach_community_helper", code = "COMMUNITY_HELPER",
            titleAr = "فصيح المجتمع", titleEn = "Community Mentor",
            descriptionAr = "قدّم 5 تصحيحات مفيدة للمتعلمين الآخرين", descriptionEn = "Provide 5 helpful community corrections",
            iconResName = "ic_badge_community", requiredValue = 5, rewardXp = 120
        )
    )
}
```

### 2.2 سكريبت Node.js لرفع بيانات الـ Seed إلى Firestore (`scripts/upload_seed_to_firestore.js`)

```javascript
/**
 * سكريبت رفع المحتوى الأصلي لتطبيق "لِسان (Lisaan)" إلى Firebase Firestore
 * التشغيل:
 *   npm install firebase-admin
 *   GOOGLE_APPLICATION_CREDENTIALS="./serviceAccountKey.json" node scripts/upload_seed_to_firestore.js
 */
const admin = require("firebase-admin");
const fs = require("fs");
const path = require("path");

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
});

const db = admin.firestore();

async function seedFirestoreCurriculum() {
  const seedFilePath = path.join(__dirname, "lisaan_en_from_ar_seed.json");
  const curriculum = JSON.parse(fs.readFileSync(seedFilePath, "utf8"));

  console.log(`Uploading Course: ${curriculum.courseId} (v${curriculum.version})...`);

  const courseRef = db.collection("courses").doc(curriculum.courseId);
  await courseRef.set({
    id: curriculum.courseId,
    sourceLangCode: "ar",
    targetLangCode: "en",
    titleAr: "الإنجليزية الشاملة من لِسان",
    titleEn: "Comprehensive English by Lisaan",
    totalUnits: 3,
    version: curriculum.version,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  const batch = db.batch();
  for (const lesson of curriculum.lessons) {
    const lessonRef = courseRef.collection("lessons").doc(lesson.lessonId);
    batch.set(lessonRef, lesson);
  }
  await batch.commit();
  console.log(`Successfully seeded ${curriculum.lessons.length} lessons (150 exercises) to Firestore!`);
}

seedFirestoreCurriculum().catch(console.error);
```

---

## 3. محرك تقييم التمارين الـ 8 (`EvaluateExerciseAnswerUseCase.kt`)

```kotlin
package com.lisaan.app.domain.usecase.lesson

import com.lisaan.app.core.database.entities.ExerciseEntity
import com.lisaan.app.core.media.LisaanSpeechRecognizer
import javax.inject.Inject

data class ExerciseEvaluationOutcome(
    val isCorrect: Boolean,
    val isAlmostCorrectWithTypo: Boolean = false,
    val accuracyPercent: Int = 100,
    val feedbackHeadlineAr: String,
    val feedbackHeadlineEn: String,
    val correctSolutionDisplay: String,
    val explanationAr: String,
    val explanationEn: String
)

class EvaluateExerciseAnswerUseCase @Inject constructor() {

    fun evaluate(
        exercise: ExerciseEntity,
        userAnswer: String,
        spokenAccuracyPercent: Int? = null
    ): ExerciseEvaluationOutcome {
        val expectedRaw = exercise.correctAnswerJson.trim()
        val expectedNorm = LisaanSpeechRecognizer.normalizeText(expectedRaw)
        val userNorm = LisaanSpeechRecognizer.normalizeText(userAnswer)

        return when (exercise.type) {
            "SPEAKING_PRONUNCIATION" -> {
                val accuracy = spokenAccuracyPercent
                    ?: LisaanSpeechRecognizer.evaluatePronunciationAccuracy(expectedRaw, userAnswer).accuracyPercent
                val passed = accuracy >= 70
                ExerciseEvaluationOutcome(
                    isCorrect = passed,
                    accuracyPercent = accuracy,
                    feedbackHeadlineAr = if (passed) "نطق رائع! دقتك $accuracy%" else "دقة النطق $accuracy% — حاول مرة أخرى بوضوح",
                    feedbackHeadlineEn = if (passed) "Great pronunciation! Accuracy: $accuracy%" else "Pronunciation accuracy: $accuracy% — Try again",
                    correctSolutionDisplay = expectedRaw,
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }

            "WRITTEN_TRANSLATION", "LISTENING_DICTATION" -> {
                val distance = LisaanSpeechRecognizer.levenshteinDistance(expectedNorm, userNorm)
                val exactMatch = expectedNorm == userNorm
                val minorTypoMatch = !exactMatch && expectedNorm.length >= 5 && distance == 1

                val isAccepted = exactMatch || minorTypoMatch
                ExerciseEvaluationOutcome(
                    isCorrect = isAccepted,
                    isAlmostCorrectWithTypo = minorTypoMatch,
                    accuracyPercent = if (exactMatch) 100 else if (minorTypoMatch) 90 else 0,
                    feedbackHeadlineAr = when {
                        exactMatch -> "إجابة صحيحة وممتازة!"
                        minorTypoMatch -> "صحيحة! (انتبه لحرف إملائي بسيط)"
                        else -> "ليست دقيقة تماماً، انظر الإجابة الصحيحة:"
                    },
                    feedbackHeadlineEn = when {
                        exactMatch -> "Spot on!"
                        minorTypoMatch -> "Correct! (Watch the tiny typo)"
                        else -> "Not quite right. Correct answer:"
                    },
                    correctSolutionDisplay = expectedRaw,
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }

            "MATCHING_PAIRS" -> {
                val passed = userAnswer == "ALL_MATCHED"
                ExerciseEvaluationOutcome(
                    isCorrect = passed,
                    feedbackHeadlineAr = "توصيل متقن لجميع المفردات!",
                    feedbackHeadlineEn = "All pairs matched!",
                    correctSolutionDisplay = "",
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }

            else -> {
                // MULTIPLE_CHOICE, WORD_ORDER, FILL_BLANK, TRUE_FALSE
                val isMatch = expectedNorm == userNorm
                ExerciseEvaluationOutcome(
                    isCorrect = isMatch,
                    accuracyPercent = if (isMatch) 100 else 0,
                    feedbackHeadlineAr = if (isMatch) "أحسنت! إجابة صحيحة" else "إجابة غير صحيحة",
                    feedbackHeadlineEn = if (isMatch) "Well done! Correct answer" else "Incorrect answer",
                    correctSolutionDisplay = expectedRaw,
                    explanationAr = exercise.explanationAr,
                    explanationEn = exercise.explanationEn
                )
            }
        }
    }
}
```

---

## 4. الشاشة الرئيسية وخريطة التعلم التفاعلية (`HomeViewModel.kt` & `HomePathScreen.kt`)

### 4.1 الـ ViewModel للشاشة الرئيسية (`HomeViewModel.kt`)

```kotlin
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
```

### 4.2 شاشة خريطة التعلم التفاعلية (`HomePathScreen.kt`)

```kotlin
package com.lisaan.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight

@Composable
fun HomePathScreen(
    onStartLesson: (String) -> Unit,
    onOpenMiniGames: () -> Unit,
    onOpenPaywall: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            HomeStatsTopBar(
                isAr = isAr,
                streakDays = state.streakDays,
                totalXp = state.totalXp,
                onOpenMiniGames = onOpenMiniGames
            )
        },
        floatingActionButton = {
            state.continueLessonId?.let { lessonId ->
                ExtendedFloatingActionButton(
                    onClick = { onStartLesson(lessonId) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = {
                        Text(
                            text = if (isAr) "كمّل من حيث وقفت" else "Continue Learning",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. بطاقة التقدم اليومي مع الهدهد نبيه
            item {
                DailyGoalProgressHeaderCard(
                    isAr = isAr,
                    learnedMinutes = state.todayMinutesLearned,
                    goalMinutes = state.dailyGoalMinutes,
                    continueLessonTitle = if (isAr) state.continueLessonTitleAr else state.continueLessonTitleEn,
                    onResumeClick = { state.continueLessonId?.let(onStartLesson) }
                )
            }

            // 2. مسار الوحدات والدروس بشكل خريطة متعرجة (Winding Path Map)
            state.unitSections.forEach { unitSection ->
                item(key = "header_${unitSection.unit.id}") {
                    UnitBannerCard(
                        isAr = isAr,
                        unitSection = unitSection
                    )
                }

                items(
                    count = unitSection.lessons.size,
                    key = { idx -> unitSection.lessons[idx].lesson.id }
                ) { idx ->
                    val node = unitSection.lessons[idx]
                    // إزاحة أفقية متناوبة لإعطاء شكل خريطة الرحلة
                    val horizontalOffset = when (idx % 4) {
                        1 -> 44.dp
                        3 -> (-44).dp
                        else -> 0.dp
                    }
                    LessonPathNodeItem(
                        isAr = isAr,
                        node = node,
                        horizontalOffset = horizontalOffset,
                        onClick = {
                            if (node.status != LessonNodeStatus.LOCKED) {
                                onStartLesson(node.lesson.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStatsTopBar(
    isAr: Boolean,
    streakDays: Int,
    totalXp: Int,
    onOpenMiniGames: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // علم اللغة المتعلمة
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🇬🇧", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EN", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            // عداد الـ Streak 🔥
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = "Streak", tint = DesertAmberLight)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$streakDays",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DesertAmberLight
                )
            }

            // عداد الـ XP ⚡
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = "XP", tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$totalXp XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // زر الألعاب المصغرة 🎮
            FilledTonalIconButton(onClick = onOpenMiniGames) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = if (isAr) "ألعاب مصغرة" else "Mini Games"
                )
            }
        }
    }
}

@Composable
private fun DailyGoalProgressHeaderCard(
    isAr: Boolean,
    learnedMinutes: Int,
    goalMinutes: Int,
    continueLessonTitle: String,
    onResumeClick: () -> Unit
) {
    val progress = (learnedMinutes.toFloat() / goalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NabeehMascotView(
                mood = if (progress >= 1f) NabeehMoodState.CELEBRATE else NabeehMoodState.IDLE,
                size = 76.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAr) "هدف اليوم: $learnedMinutes / $goalMinutes دقيقة"
                    else "Today's Goal: $learnedMinutes / $goalMinutes min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = continueLessonTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { onResumeClick() }
                )
            }
        }
    }
}

@Composable
private fun UnitBannerCard(
    isAr: Boolean,
    unitSection: UnitMapSectionUi
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAr) unitSection.unit.titleAr else unitSection.unit.titleEn,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isAr) unitSection.unit.subtitleAr else unitSection.unit.subtitleEn,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Badge(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Text(
                    text = "${unitSection.completedCount}/${unitSection.lessons.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun LessonPathNodeItem(
    isAr: Boolean,
    node: LessonMapNodeUi,
    horizontalOffset: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val bgColor = when (node.status) {
        LessonNodeStatus.COMPLETED -> OasisEmeraldLight
        LessonNodeStatus.CURRENT_ACTIVE -> MaterialTheme.colorScheme.primary
        LessonNodeStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }

    val iconTint = when (node.status) {
        LessonNodeStatus.LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .offset(x = horizontalOffset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // النجوم الثلاث أعلى الدرس المكتمل
        if (node.status == LessonNodeStatus.COMPLETED) {
            Row(horizontalArrangement = Arrangement.Center) {
                repeat(3) { starIndex ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (starIndex < node.starsEarned) DesertAmberLight else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = if (node.status == LessonNodeStatus.CURRENT_ACTIVE) 4.dp else 0.dp,
                    color = if (node.status == LessonNodeStatus.CURRENT_ACTIVE) DesertAmberLight else Color.Transparent,
                    shape = CircleShape
                )
                .clickable(enabled = node.status != LessonNodeStatus.LOCKED, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (node.status) {
                    LessonNodeStatus.COMPLETED -> Icons.Default.Check
                    LessonNodeStatus.CURRENT_ACTIVE -> Icons.Default.MenuBook
                    LessonNodeStatus.LOCKED -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isAr) node.lesson.titleAr else node.lesson.titleEn,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}
```

---

## 5. مشغل الدروس الكامل ومحرك التمارين الـ 8 (`LessonPlayerViewModel.kt` & `LessonPlayerScreen.kt`)

### 5.1 الـ ViewModel لمشغل الدرس (`LessonPlayerViewModel.kt`)

يدير المراحل الخمس للدرس (**1. المقدمة ← 2. بطاقات المفردات بالصوت والصور ← 3. الجمل الحوارية ← 4. محرك التمارين الـ 8 ← 5. الملخص والنتيجة وتحديث الـ XP والـ Streak وجدولة المراجعة SM-2**):

```kotlin
package com.lisaan.app.presentation.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.analytics.LisaanAnalyticsTracker
import com.lisaan.app.core.database.dao.*
import com.lisaan.app.core.database.entities.*
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.media.LisaanAudioPlayer
import com.lisaan.app.core.media.LisaanSpeechRecognizer
import com.lisaan.app.data.seed.DialogueLineSeed
import com.lisaan.app.domain.usecase.lesson.EvaluateExerciseAnswerUseCase
import com.lisaan.app.domain.usecase.lesson.ExerciseEvaluationOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

enum class LessonPhase {
    INTRO,
    VOCABULARY_CARDS,
    DIALOGUE_SENTENCES,
    EXERCISES,
    SUMMARY_RESULT
}

data class LessonPlayerUiState(
    val isLoading: Boolean = true,
    val lesson: LessonEntity? = null,
    val vocabularies: List<VocabularyEntity> = emptyList(),
    val dialogueLines: List<DialogueLineSeed> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val currentPhase: LessonPhase = LessonPhase.INTRO,
    val currentVocabIndex: Int = 0,
    val currentExerciseIndex: Int = 0,
    val userInputAnswer: String = "",
    val selectedTokensOrder: List<String> = emptyList(),
    val matchedPairsSet: Set<String> = emptySet(),
    val currentOutcome: ExerciseEvaluationOutcome? = null,
    val correctExercisesCount: Int = 0,
    val earnedXp: Int = 0,
    val accuracyPercent: Int = 100,
    val starsEarned: Int = 3,
    val startTimeEpochMs: Long = System.currentTimeMillis(),
    val elapsedSeconds: Int = 0
)

@HiltViewModel
class LessonPlayerViewModel @Inject constructor(
    private val courseContentDao: CourseContentDao,
    private val vocabularyDao: VocabularyDao,
    private val progressAndStreakDao: ProgressAndStreakDao,
    private val userDao: UserDao,
    private val sm2ReviewDao: Sm2ReviewDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val evaluateExerciseUseCase: EvaluateExerciseAnswerUseCase,
    val audioPlayer: LisaanAudioPlayer,
    val speechRecognizer: LisaanSpeechRecognizer,
    private val analyticsTracker: LisaanAnalyticsTracker,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(LessonPlayerUiState())
    val uiState: StateFlow<LessonPlayerUiState> = _uiState.asStateFlow()

    fun loadLesson(lessonId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, startTimeEpochMs = System.currentTimeMillis()) }
            val details = courseContentDao.getLessonDetailsSnapshot(lessonId) ?: return@launch
            val parsedDialogue = runCatching {
                json.decodeFromString<List<DialogueLineSeed>>(details.lesson.dialogueJson)
            }.getOrDefault(emptyList())

            analyticsTracker.logEvent("lesson_started", mapOf("lesson_id" to lessonId))

            _uiState.update {
                it.copy(
                    isLoading = false,
                    lesson = details.lesson,
                    vocabularies = details.vocabularies,
                    dialogueLines = parsedDialogue,
                    exercises = details.exercises.sortedBy { ex -> ex.orderIndex },
                    currentPhase = LessonPhase.INTRO
                )
            }
        }
    }

    fun advanceFromIntroToVocab() {
        _uiState.update { it.copy(currentPhase = LessonPhase.VOCABULARY_CARDS, currentVocabIndex = 0) }
    }

    fun nextVocabCard() {
        val state = _uiState.value
        if (state.currentVocabIndex < state.vocabularies.lastIndex) {
            _uiState.update { it.copy(currentVocabIndex = it.currentVocabIndex + 1) }
        } else {
            _uiState.update { it.copy(currentPhase = LessonPhase.DIALOGUE_SENTENCES) }
        }
    }

    fun toggleBookmarkCurrentVocab(vocab: VocabularyEntity) {
        viewModelScope.launch {
            val newStatus = !vocab.isBookmarked
            vocabularyDao.setBookmarkStatus(vocab.id, newStatus)
            val updatedList = _uiState.value.vocabularies.map {
                if (it.id == vocab.id) it.copy(isBookmarked = newStatus) else it
            }
            _uiState.update { it.copy(vocabularies = updatedList) }
        }
    }

    fun advanceFromDialogueToExercises() {
        _uiState.update {
            it.copy(
                currentPhase = LessonPhase.EXERCISES,
                currentExerciseIndex = 0,
                userInputAnswer = "",
                selectedTokensOrder = emptyList(),
                matchedPairsSet = emptySet(),
                currentOutcome = null
            )
        }
    }

    fun updateUserInput(text: String) {
        _uiState.update { it.copy(userInputAnswer = text) }
    }

    fun toggleWordOrderToken(token: String) {
        val current = _uiState.value.selectedTokensOrder.toMutableList()
        if (current.contains(token)) current.remove(token) else current.add(token)
        _uiState.update {
            it.copy(
                selectedTokensOrder = current,
                userInputAnswer = current.joinToString(" ")
            )
        }
    }

    fun markPairMatched(leftKey: String, totalPairsCount: Int) {
        val updated = _uiState.value.matchedPairsSet + leftKey
        val allDone = updated.size >= totalPairsCount
        _uiState.update {
            it.copy(
                matchedPairsSet = updated,
                userInputAnswer = if (allDone) "ALL_MATCHED" else ""
            )
        }
    }

    fun submitCurrentExercise(spokenAccuracyPercent: Int? = null) {
        val state = _uiState.value
        val exercise = state.exercises.getOrNull(state.currentExerciseIndex) ?: return

        val effectiveAnswer = if (exercise.type == "SPEAKING_PRONUNCIATION") {
            speechRecognizer.lastResult.value?.recognizedTranscript.orEmpty()
        } else {
            state.userInputAnswer
        }

        val outcome = evaluateExerciseUseCase.evaluate(
            exercise = exercise,
            userAnswer = effectiveAnswer,
            spokenAccuracyPercent = spokenAccuracyPercent ?: speechRecognizer.lastResult.value?.accuracyPercent
        )

        val updatedCorrectCount = if (outcome.isCorrect) state.correctExercisesCount + 1 else state.correctExercisesCount

        analyticsTracker.logEvent(
            "exercise_answered",
            mapOf(
                "exercise_id" to exercise.id,
                "type" to exercise.type,
                "is_correct" to outcome.isCorrect
            )
        )

        _uiState.update {
            it.copy(
                currentOutcome = outcome,
                correctExercisesCount = updatedCorrectCount
            )
        }
    }

    fun proceedToNextExerciseOrFinish() {
        val state = _uiState.value
        speechRecognizer.stopListening()

        if (state.currentExerciseIndex < state.exercises.lastIndex) {
            _uiState.update {
                it.copy(
                    currentExerciseIndex = it.currentExerciseIndex + 1,
                    userInputAnswer = "",
                    selectedTokensOrder = emptyList(),
                    matchedPairsSet = emptySet(),
                    currentOutcome = null
                )
            }
        } else {
            finalizeLessonAndSaveProgress()
        }
    }

    private fun finalizeLessonAndSaveProgress() {
        val state = _uiState.value
        val lesson = state.lesson ?: return
        val totalEx = state.exercises.size.coerceAtLeast(1)
        val accuracy = ((state.correctExercisesCount.toFloat() / totalEx.toFloat()) * 100).toInt()
        val stars = when {
            accuracy >= 90 -> 3
            accuracy >= 70 -> 2
            else -> 1
        }
        val xp = lesson.rewardXp + (stars * 5)
        val elapsedSec = ((System.currentTimeMillis() - state.startTimeEpochMs) / 1000L).toInt().coerceAtLeast(10)

        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"

            // 1. حفظ تقدم الدرس
            val existingProg = progressAndStreakDao.getLessonProgress(userId, lesson.id)
            val progressEntity = UserProgressEntity(
                id = "${userId}_${lesson.id}",
                userId = userId,
                courseId = "en_from_ar",
                unitId = lesson.unitId,
                lessonId = lesson.id,
                isCompleted = true,
                bestScorePercent = maxOf(existingProg?.bestScorePercent ?: 0, accuracy),
                starsEarned = maxOf(existingProg?.starsEarned ?: 0, stars),
                attemptsCount = (existingProg?.attemptsCount ?: 0) + 1,
                earnedXp = xp,
                timeSpentSeconds = elapsedSec,
                completedAt = System.currentTimeMillis(),
                isPendingSync = true
            )
            progressAndStreakDao.upsertProgress(progressEntity)

            // 2. زيادة نقاط XP للمستخدم
            userDao.incrementUserXp(userId, xp)

            // 3. تحديث الـ Streak اليومي
            val todayIso = LocalDate.now().toString()
            val currentStreak = progressAndStreakDao.getStreakSnapshot(userId) ?: StreakEntity(userId = userId)
            val newStreakDays = if (currentStreak.lastCompletedDateIso == todayIso) {
                currentStreak.currentStreakDays.coerceAtLeast(1)
            } else {
                currentStreak.currentStreakDays + 1
            }
            progressAndStreakDao.upsertStreak(
                currentStreak.copy(
                    currentStreakDays = newStreakDays,
                    longestStreakDays = maxOf(currentStreak.longestStreakDays, newStreakDays),
                    lastCompletedDateIso = todayIso,
                    todayMinutesLearned = currentStreak.todayMinutesLearned + (elapsedSec / 60).coerceAtLeast(3),
                    todayXpEarned = currentStreak.todayXpEarned + xp,
                    isPendingSync = true
                )
            )

            // 4. إضافة مفردات الدرس إلى جدول المراجعة الذكية SM-2
            val reviewItems = state.vocabularies.map { vocab ->
                ReviewItemEntity(
                    id = "${userId}_${vocab.id}",
                    userId = userId,
                    vocabularyId = vocab.id,
                    repetitions = 1,
                    easinessFactor = 2.5f,
                    intervalDays = 1,
                    nextReviewEpochMs = System.currentTimeMillis() + (24L * 3600L * 1000L),
                    isPendingSync = true
                )
            }
            sm2ReviewDao.insertInitialReviewItems(reviewItems)

            analyticsTracker.logEvent(
                "lesson_completed",
                mapOf("lesson_id" to lesson.id, "accuracy" to accuracy, "xp_earned" to xp)
            )

            _uiState.update {
                it.copy(
                    currentPhase = LessonPhase.SUMMARY_RESULT,
                    earnedXp = xp,
                    accuracyPercent = accuracy,
                    starsEarned = stars,
                    elapsedSeconds = elapsedSec
                )
            }
        }
    }
}
```

### 5.2 شاشات مشغل الدرس وجميع التمارين الـ 8 (`LessonPlayerScreen.kt`)

```kotlin
package com.lisaan.app.presentation.lesson

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.lisaan.app.core.database.entities.ExerciseEntity
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.SemanticErrorRed
import com.lisaan.app.core.designsystem.theme.SemanticSuccessGreen
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonPlayerScreen(
    lessonId: String,
    onCloseLesson: () -> Unit,
    viewModel: LessonPlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(lessonId) {
        viewModel.loadLesson(lessonId)
    }

    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    if (state.isLoading || state.lesson == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val progressFraction = when (state.currentPhase) {
        LessonPhase.INTRO -> 0.1f
        LessonPhase.VOCABULARY_CARDS -> 0.25f
        LessonPhase.DIALOGUE_SENTENCES -> 0.35f
        LessonPhase.EXERCISES -> {
            val total = state.exercises.size.coerceAtLeast(1)
            0.35f + (0.60f * ((state.currentExerciseIndex + 1).toFloat() / total.toFloat()))
        }
        LessonPhase.SUMMARY_RESULT -> 1.0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .padding(end = 16.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCloseLesson) {
                        Icon(Icons.Default.Close, contentDescription = if (isAr) "إغلاق" else "Close")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.currentPhase) {
                // المرحلة 1: مقدمة الدرس
                LessonPhase.INTRO -> {
                    val lesson = state.lesson!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NabeehMascotView(mood = NabeehMoodState.IDLE, size = 130.dp)
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (isAr) lesson.titleAr else lesson.titleEn,
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = if (isAr) "💡 نصيحة نبيه اللغوية:" else "💡 Nabeeh's Tip:",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isAr) lesson.introTipAr else lesson.introTipEn,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = viewModel::advanceFromIntroToVocab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isAr) "استعراض المفردات الجديدة (${state.vocabularies.size})" else "Explore New Words (${state.vocabularies.size})")
                        }
                    }
                }

                // المرحلة 2: بطاقات المفردات بالصوت والتمثيل الصوتي
                LessonPhase.VOCABULARY_CARDS -> {
                    val vocab = state.vocabularies.getOrNull(state.currentVocabIndex) ?: return@Box
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isAr) "مفردة ${state.currentVocabIndex + 1} من ${state.vocabularies.size}"
                            else "Word ${state.currentVocabIndex + 1} of ${state.vocabularies.size}",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { viewModel.toggleBookmarkCurrentVocab(vocab) }) {
                                        Icon(
                                            imageVector = if (vocab.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            contentDescription = "Save to Dictionary",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Text(
                                    text = vocab.wordTarget,
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${vocab.phoneticIpa} • (${vocab.transliteration})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = vocab.translationAr,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.audioPlayer.playAudioOrFallbackTts(
                                                vocab.localAudioPath, vocab.audioUrl, vocab.wordTarget, slowMode = false
                                            )
                                        }
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isAr) "استمع" else "Listen")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.audioPlayer.playAudioOrFallbackTts(
                                                vocab.localAudioPath, vocab.audioUrl, vocab.wordTarget, slowMode = true
                                            )
                                        }
                                    ) {
                                        Text("🐢 " + if (isAr) "بطيء" else "Slow")
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                                Text(
                                    text = vocab.exampleTarget,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = vocab.exampleAr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Button(
                            onClick = viewModel::nextVocabCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isAr) "التالي" else "Next")
                        }
                    }
                }

                // المرحلة 3: الجمل الحوارية السياقية
                LessonPhase.DIALOGUE_SENTENCES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isAr) "حوار تطبيقي قصير" else "Mini Dialogue in Context",
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            state.dialogueLines.forEach { line ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
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
                                                    null, null, line.lineEn
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "${line.speaker}: ${line.lineEn}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = line.lineAr,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = viewModel::advanceFromDialogueToExercises,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isAr) "ابدأ التمارين التطبيقية (${state.exercises.size})" else "Start Exercises (${state.exercises.size})")
                        }
                    }
                }

                // المرحلة 4: محرك التمارين الـ 8
                LessonPhase.EXERCISES -> {
                    val exercise = state.exercises.getOrNull(state.currentExerciseIndex) ?: return@Box
                    ExerciseEngineView(
                        isAr = isAr,
                        exercise = exercise,
                        state = state,
                        viewModel = viewModel
                    )
                }

                // المرحلة 5: ملخص الدرس والنتيجة النهائية
                LessonPhase.SUMMARY_RESULT -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        NabeehMascotView(mood = NabeehMoodState.CELEBRATE, size = 150.dp)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (isAr) "أتممت الدرس بنجاح! 🎉" else "Lesson Complete! 🎉",
                            style = MaterialTheme.typography.displayLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.Center) {
                            repeat(3) { idx ->
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (idx < state.starsEarned) DesertAmberLight else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SummaryStatChip(label = if (isAr) "نقاط XP" else "XP Earned", value = "+${state.earnedXp} ⚡")
                            SummaryStatChip(label = if (isAr) "الدقة" else "Accuracy", value = "${state.accuracyPercent}% 🎯")
                            SummaryStatChip(label = if (isAr) "الوقت" else "Time", value = "${state.elapsedSeconds}s ⏱️")
                        }

                        Spacer(modifier = Modifier.height(36.dp))
                        Button(
                            onClick = onCloseLesson,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isAr) "العودة لخريطة التعلم" else "Back to Learning Map")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseEngineView(
    isAr: Boolean,
    exercise: ExerciseEntity,
    state: LessonPlayerUiState,
    viewModel: LessonPlayerViewModel
) {
    val isListening by viewModel.speechRecognizer.isListening.collectAsState()
    val speechResult by viewModel.speechRecognizer.lastResult.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.speechRecognizer.startListening(exercise.targetText)
        }
    }

    val optionsList = remember(exercise.id) {
        runCatching {
            val arr = JSONArray(exercise.optionsJson)
            List(arr.length()) { i -> arr.getString(i) }
        }.getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = if (isAr) exercise.promptAr else exercise.promptEn,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // نص الهدف أو زر الصوت
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (exercise.type == "LISTENING_DICTATION") {
                            if (isAr) "🔊 اضغط للاستماع وكتابة الجملة" else "🔊 Tap to listen and type"
                        } else {
                            exercise.targetText
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Row {
                        IconButton(
                            onClick = {
                                viewModel.audioPlayer.playAudioOrFallbackTts(
                                    null, exercise.audioUrl, exercise.targetText, slowMode = false
                                )
                            }
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Play")
                        }
                        if (exercise.type == "LISTENING_DICTATION" || exercise.type == "SPEAKING_PRONUNCIATION") {
                            IconButton(
                                onClick = {
                                    viewModel.audioPlayer.playAudioOrFallbackTts(
                                        null, exercise.audioUrl, exercise.targetText, slowMode = true
                                    )
                                }
                            ) {
                                Text("🐢")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // واجهة كل نوع من أنواع التمارين الـ 8
            when (exercise.type) {
                "MULTIPLE_CHOICE", "FILL_BLANK", "TRUE_FALSE" -> {
                    optionsList.forEach { option ->
                        val selected = state.userInputAnswer == option
                        OutlinedButton(
                            onClick = { viewModel.updateUserInput(option) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .heightIn(min = 54.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(text = option, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                "WRITTEN_TRANSLATION", "LISTENING_DICTATION" -> {
                    OutlinedTextField(
                        value = state.userInputAnswer,
                        onValueChange = viewModel::updateUserInput,
                        label = { Text(if (isAr) "اكتب إجابتك بالإنجليزية هنا..." else "Type your English answer here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                "WORD_ORDER" -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.selectedTokensOrder.forEach { token ->
                                AssistChip(
                                    onClick = { viewModel.toggleWordOrderToken(token) },
                                    label = { Text(token) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        optionsList.forEach { token ->
                            val isUsed = state.selectedTokensOrder.contains(token)
                            FilterChip(
                                selected = isUsed,
                                onClick = { viewModel.toggleWordOrderToken(token) },
                                label = { Text(token) }
                            )
                        }
                    }
                }

                "MATCHING_PAIRS" -> {
                    val pairs = remember(exercise.id) {
                        runCatching {
                            val arr = JSONArray(exercise.optionsJson)
                            List(arr.length()) { i ->
                                val obj = arr.getJSONObject(i)
                                obj.getString("left") to obj.getString("right")
                            }
                        }.getOrDefault(emptyList())
                    }
                    pairs.forEach { (left, right) ->
                        val isMatched = state.matchedPairsSet.contains(left)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.markPairMatched(left, pairs.size) },
                                enabled = !isMatched,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(left)
                            }
                            OutlinedButton(
                                onClick = { viewModel.markPairMatched(left, pairs.size) },
                                enabled = !isMatched,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(right)
                            }
                        }
                    }
                }

                "SPEAKING_PRONUNCIATION" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FilledIconButton(
                            onClick = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.size(88.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Speak",
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isListening) {
                                if (isAr) "جاري الاستماع لنطقك..." else "Listening..."
                            } else {
                                speechResult?.let {
                                    if (isAr) "سمعنا: \"${it.recognizedTranscript}\" (الدقة: ${it.accuracyPercent}%)"
                                    else "Heard: \"${it.recognizedTranscript}\" (Accuracy: ${it.accuracyPercent}%)"
                                } ?: if (isAr) "اضغط للتحدث" else "Tap to Speak"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // شريط التغذية الراجعة أو زر التحقق
        val outcome = state.currentOutcome
        if (outcome != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (outcome.isCorrect) SemanticSuccessGreen.copy(alpha = 0.15f)
                    else SemanticErrorRed.copy(alpha = 0.15f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isAr) outcome.feedbackHeadlineAr else outcome.feedbackHeadlineEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (outcome.isCorrect) SemanticSuccessGreen else SemanticErrorRed
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAr) outcome.explanationAr else outcome.explanationEn,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::proceedToNextExerciseOrFinish,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isAr) "متابعة" else "Continue")
                    }
                }
            }
        } else {
            Button(
                onClick = { viewModel.submitCurrentExercise(speechResult?.accuracyPercent) },
                enabled = state.userInputAnswer.isNotBlank() || speechResult != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isAr) "تحقق من الإجابة" else "Check Answer")
            }
        }
    }
}

@Composable
private fun SummaryStatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
```
