package com.lisaan.app.presentation.minigames

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
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.presentation.dictionary.MyDictionaryViewModel
import kotlinx.coroutines.delay

enum class MiniGameMode { HUB, SPEED_MATCH, WORD_SPRINT, GUESS_PICTURE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniGamesHubAndPlayerScreen(
    onBack: () -> Unit,
    viewModel: MyDictionaryViewModel = hiltViewModel()
) {
    val vocabList by viewModel.vocabulariesFlow.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var currentMode by remember { mutableStateOf(MiniGameMode.HUB) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentMode) {
                            MiniGameMode.HUB -> if (isAr) "الألعاب اللغوية المصغّرة" else "Mini Language Games"
                            MiniGameMode.SPEED_MATCH -> if (isAr) "المطابقة السريعة ⏱️" else "Speed Match ⏱️"
                            MiniGameMode.WORD_SPRINT -> if (isAr) "سباق الكلمات 🚀" else "Word Sprint 🚀"
                            MiniGameMode.GUESS_PICTURE -> if (isAr) "تخمين الصورة 🖼️" else "Guess the Picture 🖼️"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentMode == MiniGameMode.HUB) onBack() else currentMode = MiniGameMode.HUB
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {
            when (currentMode) {
                MiniGameMode.HUB -> {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GameEntryCard(
                            emoji = "⚡",
                            title = if (isAr) "1. المطابقة السريعة (Speed Match)" else "1. Speed Match",
                            desc = if (isAr) "وصّل أكبر عدد من الكلمات بمعانيها خلال 30 ثانية!" else "Match as many word pairs as possible in 30 seconds!",
                            onClick = { currentMode = MiniGameMode.SPEED_MATCH }
                        )
                        GameEntryCard(
                            emoji = "🏃",
                            title = if (isAr) "2. سباق الكلمات (Word Sprint)" else "2. Word Sprint",
                            desc = if (isAr) "حدّد هل الترجمة المعروضة صحيحة أم خاطئة قبل نفاد العداد!" else "Decide if the translation is right or wrong before time runs out!",
                            onClick = { currentMode = MiniGameMode.WORD_SPRINT }
                        )
                        GameEntryCard(
                            emoji = "🖼️",
                            title = if (isAr) "3. تخمين الصورة والرمز (Picture Guess)" else "3. Guess the Picture",
                            desc = if (isAr) "اختر المفردة الإنجليزية المعبرة عن المشهد المصوّر والمنطوق" else "Pick the English phrase that matches the visual scene",
                            onClick = { currentMode = MiniGameMode.GUESS_PICTURE }
                        )
                    }
                }

                // اللعبة 1: المطابقة السريعة ضد الوقت
                MiniGameMode.SPEED_MATCH -> {
                    var timeLeft by remember { mutableIntStateOf(30) }
                    var score by remember { mutableIntStateOf(0) }
                    var roundSeed by remember { mutableIntStateOf(0) }
                    val sampleFour = remember(vocabList, roundSeed) { vocabList.shuffled().take(4) }
                    var selectedEn by remember { mutableStateOf<String?>(null) }
                    var matchedIds by remember { mutableStateOf(setOf<String>()) }

                    LaunchedEffect(timeLeft) {
                        if (timeLeft > 0) {
                            delay(1000L)
                            timeLeft--
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⏱️ ${timeLeft}s", style = MaterialTheme.typography.headlineMedium, color = DesertAmberLight)
                            Text("🏆 $score", style = MaterialTheme.typography.headlineMedium, color = OasisEmeraldLight)
                        }

                        if (timeLeft == 0) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isAr) "انتهى الوقت! نتيجتك: $score" else "Time's Up! Score: $score",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { timeLeft = 30; score = 0; matchedIds = emptySet(); roundSeed++ }) {
                                    Text(if (isAr) "العب مجدداً" else "Play Again")
                                }
                            }
                        } else {
                            val rightShuffled = remember(sampleFour) { sampleFour.shuffled() }
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sampleFour.forEachIndexed { idx, leftItem ->
                                    val rightItem = rightShuffled[idx]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { selectedEn = leftItem.id },
                                            enabled = !matchedIds.contains(leftItem.id),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(leftItem.wordTarget)
                                        }
                                        Button(
                                            onClick = {
                                                if (selectedEn == rightItem.id) {
                                                    score += 10
                                                    matchedIds = matchedIds + rightItem.id
                                                    selectedEn = null
                                                    if (matchedIds.size == sampleFour.size) {
                                                        matchedIds = emptySet()
                                                        roundSeed++
                                                    }
                                                } else {
                                                    selectedEn = null
                                                }
                                            },
                                            enabled = !matchedIds.contains(rightItem.id),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(rightItem.translationAr)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // اللعبة 2: سباق الكلمات السريع
                MiniGameMode.WORD_SPRINT -> {
                    var score by remember { mutableIntStateOf(0) }
                    var streakCombo by remember { mutableIntStateOf(0) }
                    var step by remember { mutableIntStateOf(0) }

                    val currentPair = remember(vocabList, step) {
                        if (vocabList.size < 2) null else {
                            val target = vocabList.random()
                            val showCorrect = (step % 2 == 0)
                            val displayedAr = if (showCorrect) target.translationAr else vocabList.first { it.id != target.id }.translationAr
                            Triple(target.wordTarget, displayedAr, showCorrect)
                        }
                    }

                    currentPair?.let { (wordEn, displayedAr, isActuallyCorrect) ->
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔥 Combo x$streakCombo • 🏆 $score",
                                style = MaterialTheme.typography.headlineMedium
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = wordEn, style = MaterialTheme.typography.displayLarge)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(text = "⬇️", style = MaterialTheme.typography.headlineMedium)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = displayedAr,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (!isActuallyCorrect) { score += 15; streakCombo++ } else streakCombo = 0
                                        step++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                ) {
                                    Text(if (isAr) "✗ خاطئة" else "✗ False")
                                }
                                Button(
                                    onClick = {
                                        if (isActuallyCorrect) { score += 15; streakCombo++ } else streakCombo = 0
                                        step++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                ) {
                                    Text(if (isAr) "✓ صحيحة" else "✓ True")
                                }
                            }
                        }
                    }
                }

                // اللعبة 3: تخمين الصورة والمشهد
                MiniGameMode.GUESS_PICTURE -> {
                    var score by remember { mutableIntStateOf(0) }
                    var round by remember { mutableIntStateOf(0) }
                    val options = remember(vocabList, round) { vocabList.shuffled().take(4) }
                    val target = remember(options) { options.firstOrNull() }

                    target?.let { correctVocab ->
                        val sceneEmoji = when (correctVocab.categoryTag) {
                            "GREETINGS" -> "👋🌅"
                            "TRAVEL" -> "✈️🏨🗺️"
                            "FOOD" -> "☕🍽️🥗"
                            "WORK" -> "💼📊🤝"
                            else -> "🏡⏰👨‍👩‍👧‍👦"
                        }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🏆 Score: $score", style = MaterialTheme.typography.headlineMedium)

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = sceneEmoji, style = MaterialTheme.typography.displayLarge)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = correctVocab.translationAr,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = correctVocab.exampleAr,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                options.shuffled().forEach { option ->
                                    OutlinedButton(
                                        onClick = {
                                            if (option.id == correctVocab.id) {
                                                score += 20
                                                viewModel.audioPlayer.playAudioOrFallbackTts(
                                                    option.localAudioPath, option.audioUrl, option.wordTarget
                                                )
                                            }
                                            round++
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp)
                                    ) {
                                        Text(option.wordTarget, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameEntryCard(
    emoji: String,
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = desc, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
