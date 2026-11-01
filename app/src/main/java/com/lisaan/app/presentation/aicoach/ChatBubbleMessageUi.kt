package com.lisaan.app.presentation.aicoach

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.common.Resource
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.core.media.LisaanSpeechRecognizer
import com.lisaan.app.core.media.LisaanTtsManager
import com.lisaan.app.data.remote.dto.AiConversationTurnDto
import com.lisaan.app.data.remote.dto.AiGrammarCorrectionDto
import com.lisaan.app.data.repository.AiCoachRepository
import com.lisaan.app.data.repository.AiRoleplayScenario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatBubbleMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val isFromUser: Boolean,
    val textEn: String,
    val translationAr: String = "",
    val correction: AiGrammarCorrectionDto? = null
)

data class AiCoachUiState(
    val scenarios: List<AiRoleplayScenario> = emptyList(),
    val activeScenario: AiRoleplayScenario? = null,
    val messages: List<ChatBubbleMessageUi> = emptyList(),
    val currentInputText: String = "",
    val suggestedHintEn: String = "",
    val isAiThinking: Boolean = false
)

@HiltViewModel
class AiCoachViewModel @Inject constructor(
    private val aiCoachRepository: AiCoachRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    val ttsManager: LisaanTtsManager,
    val speechRecognizer: LisaanSpeechRecognizer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiCoachUiState(scenarios = aiCoachRepository.getAvailableScenarios())
    )
    val uiState: StateFlow<AiCoachUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            speechRecognizer.lastResult.collect { speechRes ->
                if (speechRes != null && speechRes.recognizedTranscript.isNotBlank()) {
                    _uiState.update { it.copy(currentInputText = speechRes.recognizedTranscript) }
                }
            }
        }
    }

    fun startScenario(scenarioId: String) {
        val scenario = _uiState.value.scenarios.find { it.id == scenarioId }
            ?: _uiState.value.scenarios.first()
        val openingMsg = ChatBubbleMessageUi(
            isFromUser = false,
            textEn = scenario.openingLineEn,
            translationAr = scenario.openingLineAr
        )
        _uiState.update {
            it.copy(
                activeScenario = scenario,
                messages = listOf(openingMsg),
                currentInputText = "",
                suggestedHintEn = "Hello! I would like to practice."
            )
        }
        ttsManager.speak(scenario.openingLineEn)
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(currentInputText = text) }
    }

    fun useSuggestedHint() {
        val hint = _uiState.value.suggestedHintEn
        if (hint.isNotBlank()) {
            _uiState.update { it.copy(currentInputText = hint) }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val text = state.currentInputText.trim()
        val scenario = state.activeScenario ?: return
        if (text.isBlank() || state.isAiThinking) return

        val userBubble = ChatBubbleMessageUi(isFromUser = true, textEn = text)
        val historyDto = state.messages.map {
            AiConversationTurnDto(
                role = if (it.isFromUser) "user" else "assistant",
                content = it.textEn
            )
        }

        _uiState.update {
            it.copy(
                messages = it.messages + userBubble,
                currentInputText = "",
                isAiThinking = true
            )
        }

        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            when (val res = aiCoachRepository.sendLearnerMessage(
                scenarioId = scenario.id,
                learnerLevel = prefs.proficiencyLevel,
                userMessage = text,
                history = historyDto
            )) {
                is Resource.Success -> {
                    val data = res.data
                    // تحديث فقاعة المستخدم ببطاقة التصحيح الفوري، وإضافة رد المدرب
                    val updatedList = _uiState.value.messages.map { msg ->
                        if (msg.id == userBubble.id) msg.copy(correction = data.correction) else msg
                    } + ChatBubbleMessageUi(
                        isFromUser = false,
                        textEn = data.replyEn,
                        translationAr = data.replyTranslationAr
                    )

                    _uiState.update {
                        it.copy(
                            messages = updatedList,
                            suggestedHintEn = data.suggestedFollowUpEn,
                            isAiThinking = false
                        )
                    }
                    ttsManager.speak(data.replyEn)
                }
                else -> {
                    _uiState.update { it.copy(isAiThinking = false) }
                }
            }
        }
    }
}

@Composable
fun AiCoachScenariosTabScreen(
    onSelectScenario: (String) -> Unit,
    onOpenPaywall: (String) -> Unit,
    viewModel: AiCoachViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NabeehMascotView(mood = NabeehMoodState.IDLE, size = 76.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (isAr) "مدرب المحادثة الذكي (AI Coach)" else "AI Conversation Coach",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "حاور «نبيه» بالصوت أو النص واحصل على تصحيح فوري لقواعدك"
                        else "Practice by voice or text with instant grammar feedback",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(state.scenarios, key = { it.id }) { scenario ->
            Card(
                onClick = { onSelectScenario(scenario.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = scenario.emoji, style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAr) scenario.titleAr else scenario.titleEn,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(scenario.levelBadge, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAr) scenario.descriptionAr else scenario.descriptionEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatSessionScreen(
    scenarioId: String,
    onBack: () -> Unit,
    viewModel: AiCoachViewModel = hiltViewModel()
) {
    LaunchedEffect(scenarioId) {
        viewModel.startScenario(scenarioId)
    }

    val state by viewModel.uiState.collectAsState()
    val isListening by viewModel.speechRecognizer.isListening.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.speechRecognizer.startListening("")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val sc = state.activeScenario
                    Text(if (isAr) sc?.titleAr.orEmpty() else sc?.titleEn.orEmpty())
                },
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
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isFromUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (msg.isFromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.widthIn(max = 310.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = msg.textEn,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (msg.isFromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!msg.isFromUser) {
                                        IconButton(
                                            onClick = { viewModel.ttsManager.speak(msg.textEn) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                                        }
                                    }
                                }
                                if (msg.translationAr.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = msg.translationAr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // بطاقة التصحيح والشرح المبسط تحت رسالة المتعلم
                        msg.correction?.let { corr ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.widthIn(max = 320.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (corr.hasMistake) DesertAmberLight.copy(alpha = 0.16f)
                                    else OasisEmeraldLight.copy(alpha = 0.15f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (corr.hasMistake) {
                                            if (isAr) "✨ صياغة أصح: ${corr.correctedText}" else "✨ Better: ${corr.correctedText}"
                                        } else {
                                            if (isAr) "✓ جملة ممتازة وسليمة!" else "✓ Perfect sentence!"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isAr) corr.explanationAr else corr.explanationEn,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                if (state.isAiThinking) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NabeehMascotView(mood = NabeehMoodState.THINKING, size = 48.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAr) "نبيه يكتب ويحلل الجملة..." else "Nabeeh is thinking...")
                        }
                    }
                }
            }

            // اقتراح جملة مساعدة (Suggested Hint)
            if (state.suggestedHintEn.isNotBlank()) {
                AssistChip(
                    onClick = viewModel::useSuggestedHint,
                    label = { Text("💡 ${state.suggestedHintEn}") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // شريط الإدخال النصي والصوتي
            Surface(tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = if (isListening) DesertAmberLight else MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = state.currentInputText,
                        onValueChange = viewModel::onInputTextChanged,
                        placeholder = { Text(if (isAr) "تحدث أو اكتب بالإنجليزية..." else "Speak or type in English...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = viewModel::sendMessage,
                        enabled = state.currentInputText.isNotBlank() && !state.isAiThinking
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
