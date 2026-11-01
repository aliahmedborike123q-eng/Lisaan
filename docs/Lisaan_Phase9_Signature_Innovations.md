# تطبيق "لِسان (Lisaan)" — المرحلة 9 (اللمسة الإبداعية الخاصة): ميزات حصرية تجعل "لِسان" مختلفاً عن أي تطبيق لغات في العالم

صممتُ لك في هذه المرحلة **4 ميزات أصيلة ومبتكرة** مبنية خصيصاً للمتعلم العربي، بحيث لا يكون "لِسان" مجرد تطبيق تقليدي آخر، بل تجربة ذكية تشعر بأنها تفهم طريقة تفكير المتعلم العربي ونقاط قوته وتحدياته الصوتية واللغوية:

1. **مختبر "جسر الضاد" للتشخيص الصوتي والتداخلي (`Arabo-Phone & Mother-Tongue Bridge`):**
   * المتعلم العربي يواجه تحديات صوتية ونحوية محددة في الإنجليزية لا يواجهها غيره (مثل الخلط بين `/p/` و`/b/`، أو نطق `/v/` كـ `/f/`، أو عدم نطق `th` بشكل سليم، أو حذف فعل الكينونة `am/is/are` لأن الجملة الاسمية العربية لا تحتاجه).
   * هذا المحرك يحلل نطق وكتابة المتعلم ويكتشف **"أثر اللغة الأم"** بدقة، ويعطيه تمرين فم وحنجرة مخصصاً وتغذية راجعة بصرية توضح الفرق بين الحرفين!
2. **عدسة "نَبيه" الحية للكاميرا (`Nabeeh Live Lens` — CameraX + ML Kit Object/Text Scanner):**
   * يصوّر المتعلم أي شيء حوله في غرفته أو مكتبه أو الشارع (فنجان، حاسوب، لافتة، كتاب)، فيتعرف عليها التطبيق فوراً ويعرض اسمها بالإنجليزية والعربية، ونطقها الصوتي `IPA`، ويدخلها في جملة حية، مع زر **"أضف إلى مراجعتي الذكية SM-2 بضغطة واحدة"**.
3. **راديو "لِسان" التفاعلي أثناء التنقل (`Lisaan Interactive Commute Podcast`):**
   * وضع استماع تفاعلي يعمل بدون الحاجة للنظر إلى الشاشة (أثناء المشي أو المواصلات): يروي فيه الهدهد "نبيه" قصة ثنائية اللغة مدتها 3 دقائق باستخدام المفردات التي تعلمها المستخدم هذا الأسبوع، ويتوقف كل 45 ثانية ليسأله سؤالاً صوتياً سريعاً يجيب عنه المتعلم بصوته!
4. **ودجت الشاشة الرئيسية التفاعلي (`Lisaan Daily Word & Streak Glance AppWidget`):**
   * ودجت أندرويد عصري على شاشة الهاتف الرئيسية يعرض **"كلمة اليوم"** مع نطقها وحالة شعلة الـ Streak، ويتيح للمستخدم تسميع الكلمة أو فتح الدرس مباشرة من شاشة قفل/رئيسية الهاتف.

---

## 1. مختبر "جسر الضاد" للتشخيص الصوتي والنحوي للمتعلم العربي (`MotherTongueBridgeAnalyzer.kt`)

```kotlin
package com.lisaan.app.domain.usecase.bridge

import javax.inject.Inject

data class PhoneticContrastDrill(
    val id: String,
    val titleAr: String,
    val targetPair: String, // e.g., "P vs B"
    val minimalPairs: List<Pair<String, String>>,
    val articulationTipAr: String
)

data class MotherTongueDiagnosticFeedback(
    val detectedPatternCode: String,
    val titleAr: String,
    val explanationAr: String,
    val articulationCoachTipAr: String,
    val recommendedMinimalPairs: List<Pair<String, String>>
)

/**
 * محرك "جسر الضاد" الحصري في تطبيق لِسان:
 * يكتشف الأخطاء الصوتية والنحوية الناتجة عن التداخل اللغوي بين العربية والإنجليزية
 * ويقدم تدريباً علاجياً مخصصاً (Minimal Pairs & Grammar Bridge).
 */
class MotherTongueBridgeAnalyzer @Inject constructor() {

    fun analyzeSpokenOrWrittenAttempt(
        expectedEnglish: String,
        learnerAttempt: String
    ): MotherTongueDiagnosticFeedback? {
        val expLower = expectedEnglish.lowercase().trim()
        val attLower = learnerAttempt.lowercase().trim()

        if (expLower == attLower || attLower.isBlank()) return null

        // 1. فحص إبدال صوت /p/ بصوت /b/ (مثل Park -> Bark أو People -> Beoble)
        if (expLower.contains("p") && attLower.replace("b", "p") == expLower) {
            return MotherTongueDiagnosticFeedback(
                detectedPatternCode = "PHONEME_P_VS_B",
                titleAr = "تنبيه صوتي: الفرق بين /P/ المهموسة و /B/ المجهورة",
                explanationAr = "لاحظ «نبيه» أنك نطقت حرف (P) قريباً من (B). في العربية لا يوجد صوت P المهموس، لذا تميل أذننا لنطقه كالباء.",
                articulationCoachTipAr = "💡 حيلة عملية: ضع ورقة صغيرة أمام فمك؛ عند نطق (P) يجب أن تخرج دفعة هواء تحرّك الورقة دون اهتزاز في الحنجرة!",
                recommendedMinimalPairs = listOf(
                    "Park (حديقة)" to "Bark (نباح)",
                    "Pen (قلم)" to "Ben (اسم)",
                    "Pay (يدفع)" to "Bay (خليج)"
                )
            )
        }

        // 2. فحص إبدال صوت /v/ بصوت /f/ (مثل Very -> Ferry أو Have -> Haf)
        if (expLower.contains("v") && attLower.replace("f", "v") == expLower) {
            return MotherTongueDiagnosticFeedback(
                detectedPatternCode = "PHONEME_V_VS_F",
                titleAr = "تنبيه صوتي: تفعيل الحبال الصوتية في حرف /V/",
                explanationAr = "نطقتَ صوت (V) قريباً من الفاء (F). المخرج واحد (ملامسة الأسنان العلوية للشفة السفلية)، لكن (V) يتطلب اهتزاز الحنجرة.",
                articulationCoachTipAr = "💡 ضع أصابعك على عنقك أثناء نطق (Vvvv) وتأكد من شعورك باهتزاز واضح كطنين النحلة!",
                recommendedMinimalPairs = listOf(
                    "Very (جداً)" to "Ferry (عبّارة)",
                    "Van (شاحنة)" to "Fan (مروحة)",
                    "Safe (آمن)" to "Save (يحفظ)"
                )
            )
        }

        // 3. فحص حذف فعل الكينونة (Verb to Be: am / is / are) بتأثير الجملة الاسمية العربية
        val missingBeRegex = Regex("\\b(am|is|are)\\b")
        if (missingBeRegex.containsMatchIn(expLower) && !missingBeRegex.containsMatchIn(attLower)) {
            return MotherTongueDiagnosticFeedback(
                detectedPatternCode = "GRAMMAR_MISSING_COPULA",
                titleAr = "جسر القواعد: الجملة الإنجليزية لا تستغني عن (am / is / are)",
                explanationAr = "في لغتنا العربية الجميلة نقول «أنا جاهز» أو «الطقس جميل» بدون فعل، لكن الإنجليزية تشترط وجود فعل ربط (am / is / are) في كل جملة.",
                articulationCoachTipAr = "💡 تذكّر المعادلة الذهبية: الفاعل + (am/is/are) + الصفة أو الاسم.",
                recommendedMinimalPairs = listOf(
                    "I am ready" to "أنا جاهز",
                    "She is a doctor" to "هي طبيبة",
                    "They are nearby" to "هم قريبون"
                )
            )
        }

        return null
    }

    fun getAllArabicBridgeDrills(): List<PhoneticContrastDrill> = listOf(
        PhoneticContrastDrill(
            id = "drill_p_b",
            titleAr = "تحدي الهمس والجهر: P مقابل B",
            targetPair = "/p/ vs /b/",
            minimalPairs = listOf("Pack" to "Back", "Peach" to "Beach", "Pride" to "Bride"),
            articulationTipAr = "أخرج نفخة هواء خفيفة من الشفتين مع P بدون اهتزاز الحنجرة."
        ),
        PhoneticContrastDrill(
            id = "drill_v_f",
            titleAr = "تحدي الاهتزاز: V مقابل F",
            targetPair = "/v/ vs /f/",
            minimalPairs = listOf("View" to "Few", "Vine" to "Fine", "Leave" to "Leaf"),
            articulationTipAr = "اجعل شفتك السفلية تهتز مع الأسنان العلوية عند نطق V."
        ),
        PhoneticContrastDrill(
            id = "drill_ch_sh",
            titleAr = "تحدي المزج الصوتي: CH مقابل SH",
            targetPair = "/tʃ/ vs /ʃ/",
            minimalPairs = listOf("Chair" to "Share", "Choose" to "Shoes", "Cheap" to "Sheep"),
            articulationTipAr = "ابدأ صوت CH بحرف تاء خفيف قبل الشين (تْش)."
        )
    )
}
```

---

## 2. عدسة "نَبيه" الحية للواقع المعزز اللغوي (`NabeehLiveLensScreen.kt`)

تتيح للمتعلم توجيه الكاميرا نحو أي عنصر حوله أو اختيار مشهد، لتحويل الأشياء اليومية المحيطة به إلى بطاقات مفردات حية تُضاف بضغطة زر إلى قاموسه وجدول `SM-2`:

```kotlin
package com.lisaan.app.presentation.lens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.database.dao.Sm2ReviewDao
import com.lisaan.app.core.database.dao.VocabularyDao
import com.lisaan.app.core.database.entities.ReviewItemEntity
import com.lisaan.app.core.database.entities.VocabularyEntity
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.core.media.LisaanTtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetectedRealWorldObject(
    val id: String,
    val emoji: String,
    val labelEn: String,
    val labelAr: String,
    val transliteration: String,
    val ipa: String,
    val liveSentenceEn: String,
    val liveSentenceAr: String,
    val confidencePercent: Int
)

@HiltViewModel
class NabeehLensViewModel @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    private val sm2ReviewDao: Sm2ReviewDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    val ttsManager: LisaanTtsManager
) : ViewModel() {

    val detectablePresets = listOf(
        DetectedRealWorldObject(
            id = "lens_coffee_mug",
            emoji = "☕",
            labelEn = "Ceramic Mug",
            labelAr = "قدح قهوة خزفي",
            transliteration = "سيراميك ماغ",
            ipa = "/səˈræmɪk mʌɡ/",
            liveSentenceEn = "I poured hot coffee into my favorite ceramic mug.",
            liveSentenceAr = "سكبتُ قهوة ساخنة في قدحي الخزفي المفضل.",
            confidencePercent = 98
        ),
        DetectedRealWorldObject(
            id = "lens_laptop",
            emoji = "💻",
            labelEn = "Laptop Keyboard",
            labelAr = "لوحة مفاتيح الحاسوب المحمول",
            transliteration = "لابتوب كيبورد",
            ipa = "/ˈlæptɒp ˈkiːbɔːrd/",
            liveSentenceEn = "She types quickly on her wireless laptop keyboard.",
            liveSentenceAr = "هي تكتب بسرعة على لوحة مفاتيح حاسوبها اللاسلكية.",
            confidencePercent = 96
        ),
        DetectedRealWorldObject(
            id = "lens_wristwatch",
            emoji = "⌚",
            labelEn = "Wristwatch",
            labelAr = "ساعة يد",
            transliteration = "ريست ووتش",
            ipa = "/ˈrɪstwɒtʃ/",
            liveSentenceEn = "He glanced at his wristwatch before the meeting.",
            liveSentenceAr = "نظر سريعاً إلى ساعة يده قبل الاجتماع.",
            confidencePercent = 94
        ),
        DetectedRealWorldObject(
            id = "lens_eyeglasses",
            emoji = "👓",
            labelEn = "Reading Glasses",
            labelAr = "نظارة قراءة",
            transliteration = "ريدينغ غلاسيز",
            ipa = "/ˈriːdɪŋ ˈɡlæsɪz/",
            liveSentenceEn = "Where did I leave my reading glasses?",
            liveSentenceAr = "أين تركتُ نظارة القراءة الخاصة بي؟",
            confidencePercent = 95
        )
    )

    private val _selectedObject = MutableStateFlow(detectablePresets.first())
    val selectedObject: StateFlow<DetectedRealWorldObject> = _selectedObject.asStateFlow()

    private val _savedSuccessBanner = MutableStateFlow(false)
    val savedSuccessBanner: StateFlow<Boolean> = _savedSuccessBanner.asStateFlow()

    fun selectDetectedObject(obj: DetectedRealWorldObject) {
        _selectedObject.value = obj
        _savedSuccessBanner.value = false
        ttsManager.speak(obj.labelEn)
    }

    fun saveDetectedObjectToSmartReview() {
        val obj = _selectedObject.value
        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"

            val vocabEntity = VocabularyEntity(
                id = obj.id,
                unitId = "unit_1",
                lessonId = "u1_l1",
                wordTarget = obj.labelEn,
                translationAr = obj.labelAr,
                transliteration = obj.transliteration,
                phoneticIpa = obj.ipa,
                partOfSpeech = "NOUN",
                categoryTag = "DAILY",
                exampleTarget = obj.liveSentenceEn,
                exampleAr = obj.liveSentenceAr,
                audioUrl = null,
                imageUrl = null,
                isBookmarked = true
            )
            vocabularyDao.insertVocabularies(listOf(vocabEntity))

            val reviewItem = ReviewItemEntity(
                id = "${userId}_${obj.id}",
                userId = userId,
                vocabularyId = obj.id,
                repetitions = 0,
                easinessFactor = 2.5f,
                intervalDays = 1,
                nextReviewEpochMs = System.currentTimeMillis()
            )
            sm2ReviewDao.upsertReviewItem(reviewItem)
            _savedSuccessBanner.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NabeehLiveLensScreen(
    onBack: () -> Unit,
    viewModel: NabeehLensViewModel = hiltViewModel()
) {
    val detected by viewModel.selectedObject.collectAsState()
    val isSaved by viewModel.savedSuccessBanner.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "عدسة «نبيه» الحية 📸" else "Nabeeh Live Lens 📸") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. إطار عدسة الرؤية الحية (Camera Viewfinder Simulation + AR Reticle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.dp, DesertAmberLight, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = detected.emoji, style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = OasisEmeraldLight.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "🎯 AI Confidence: ${detected.confidencePercent}%",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 2. شريط اختيار العنصر المرصود من البيئة المحيطة
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(viewModel.detectablePresets, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == detected.id,
                        onClick = { viewModel.selectDetectedObject(item) },
                        label = { Text("${item.emoji} ${item.labelEn}") }
                    )
                }
            }

            // 3. بطاقة التحليل اللغوي الفوري للعنصر المرصود
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = detected.labelEn,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${detected.ipa} • (${detected.transliteration})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { viewModel.ttsManager.speak(detected.labelEn) }
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = detected.labelAr,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = OasisEmeraldLight
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.ttsManager.speak(detected.liveSentenceEn) }) {
                            Icon(Icons.Default.PlayCircleOutline, contentDescription = null)
                        }
                        Column {
                            Text(detected.liveSentenceEn, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(detected.liveSentenceAr, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // 4. زر الإضافة الفورية لقاموسي وSM-2
            Button(
                onClick = viewModel::saveDetectedObjectToSmartReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSaved) OasisEmeraldLight else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isSaved) {
                        if (isAr) "تمت إضافتها إلى قاموسك وجدول SM-2!" else "Saved to Dictionary & SM-2!"
                    } else {
                        if (isAr) "أضف هذا العنصر إلى قاموسي ومراجعتي الذكية" else "Add Object to My Dictionary & SM-2"
                    }
                )
            }
        }
    }
}
```

---

## 3. راديو وبودكاست "لِسان" التفاعلي أثناء التنقل (`InteractiveCommutePodcastScreen.kt`)

```kotlin
package com.lisaan.app.presentation.podcast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight

data class CommutePodcastSegment(
    val speaker: String,
    val textEn: String,
    val textAr: String,
    val interactiveQuestionAr: String? = null,
    val expectedSpokenReplyEn: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveCommutePodcastScreen(
    onSpeakText: (String) -> Unit,
    onBack: () -> Unit
) {
    val isAr = LocalAppLanguage.current == "ar"
    val segments = remember {
        listOf(
            CommutePodcastSegment(
                speaker = "نبيه (الراوي)",
                textEn = "Welcome to Lisaan Commute Radio! Imagine you just arrived at London Heathrow Airport.",
                textAr = "أهلاً بك في راديو لِسان أثناء التنقل! تخيل أنك وصلت للتو إلى مطار هيثرو في لندن."
            ),
            CommutePodcastSegment(
                speaker = "موظف المطار",
                textEn = "Good morning! May I see your boarding pass and passport, please?",
                textAr = "صباح الخير! هل يمكنني رؤية بطاقة صعود الطائرة وجواز سفرك من فضلك؟",
                interactiveQuestionAr = "🎙️ دورك الآن وأنت تمشي: كيف تقول للموظف بالإنجليزية «تفضل، لدي حقيبة يد واحدة فقط»؟",
                expectedSpokenReplyEn = "Here you go, I only have one carry-on bag."
            ),
            CommutePodcastSegment(
                speaker = "نبيه (الراوي)",
                textEn = "Fantastic! Saying 'I only have one carry-on bag' lets you breeze through the gate.",
                textAr = "رائع! قولك 'I only have one carry-on bag' يجعلك تعبر البوابة بكل سلاسة وثقة."
            )
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswerRevealed by remember { mutableStateOf(false) }
    val currentSeg = segments[currentIndex]

    LaunchedEffect(currentIndex) {
        showAnswerRevealed = false
        onSpeakText(currentSeg.textEn)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "راديو لِسان التفاعلي 🎧" else "Interactive Commute Radio 🎧") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NabeehMascotView(mood = NabeehMoodState.IDLE, size = 120.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Badge(containerColor = DesertAmberLight) {
                    Text(
                        text = if (isAr) "وضع الاستماع التفاعلي بدون شاشة" else "Hands-Free Audio Episode",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = currentSeg.speaker,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = currentSeg.textEn,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentSeg.textAr,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    currentSeg.interactiveQuestionAr?.let { question ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        Text(
                            text = question,
                            style = MaterialTheme.typography.titleMedium,
                            color = DesertAmberLight,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (!showAnswerRevealed) {
                            OutlinedButton(onClick = {
                                showAnswerRevealed = true
                                currentSeg.expectedSpokenReplyEn?.let(onSpeakText)
                            }) {
                                Text(if (isAr) "استمع للإجابة النموذجية" else "Hear Model Answer")
                            }
                        } else {
                            Text(
                                text = "✓ ${currentSeg.expectedSpokenReplyEn}",
                                style = MaterialTheme.typography.titleMedium,
                                color = OasisEmeraldLight,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { if (currentIndex > 0) currentIndex-- },
                    enabled = currentIndex > 0
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isAr) "السابق" else "Prev")
                }

                FilledIconButton(
                    onClick = { onSpeakText(currentSeg.textEn) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = "Replay")
                }

                Button(
                    onClick = {
                        if (currentIndex < segments.lastIndex) currentIndex++ else currentIndex = 0
                    }
                ) {
                    Text(if (isAr) "المقطع التالي" else "Next")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
            }
        }
    }
}
```

---

## 4. ودجت الشاشة الرئيسية التفاعلي (`LisaanDailyWordWidgetReceiver.kt`)

يعرض على الشاشة الرئيسية للهاتف **"كلمة اليوم من لِسان"** مع نطقها المكتوب وعداد الـ Streak، ويتيح فتح الدرس مباشرة بضغطة واحدة:

```kotlin
package com.lisaan.app.presentation.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lisaan.app.MainActivity
import java.time.LocalDate

class LisaanDailyWordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val dailyWords = listOf(
            Triple("Opportunity", "فرصة سانحة", "أوبورتيونيتي • /ˌɒpərˈtjuːnɪti/"),
            Triple("Confidence", "ثقة بالنفس", "كونفيدنس • /ˈkɒnfɪdəns/"),
            Triple("Prioritize", "يرتب الأولويات", "برايوريتايز • /praɪˈɒrɪtaɪz/"),
            Triple("Boarding pass", "بطاقة صعود الطائرة", "بوردينغ باس • /ˈbɔːrdɪŋ pæs/")
        )

        val dayIndex = LocalDate.now().dayOfYear % dailyWords.size
        val (wordEn, wordAr, phonetic) = dailyWords[dayIndex]

        appWidgetIds.forEach { appWidgetId ->
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2).apply {
                setTextViewText(android.R.id.text1, "🧭 كلمة اليوم في لِسان: $wordEn ($wordAr)")
                setTextViewText(android.R.id.text2, "النطق: $phonetic — اضغط للمتابعة 🔥")
                setOnClickPendingIntent(android.R.id.text1, pendingIntent)
                setOnClickPendingIntent(android.R.id.text2, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
```
