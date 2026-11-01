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
