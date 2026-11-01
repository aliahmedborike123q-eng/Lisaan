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
data class LessonCurriculumBlueprint(
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
