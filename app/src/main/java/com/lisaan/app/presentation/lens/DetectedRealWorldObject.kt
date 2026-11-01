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
