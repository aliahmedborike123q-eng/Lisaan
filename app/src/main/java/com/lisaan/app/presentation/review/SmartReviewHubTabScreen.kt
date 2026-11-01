package com.lisaan.app.presentation.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.core.designsystem.theme.SemanticErrorRed
import com.lisaan.app.domain.usecase.sm2.Sm2UserGrade

@Composable
fun SmartReviewHubTabScreen(
    onStartSm2Session: () -> Unit,
    onOpenDictionary: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenMiniGames: () -> Unit,
    viewModel: SmartReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isAr) "المراجعة الذكية والقاموس" else "Smart Review & Dictionary",
            style = MaterialTheme.typography.headlineLarge
        )

        // 1. بطاقة التكرار المتباعد SM-2
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NabeehMascotView(mood = NabeehMoodState.THINKING, size = 72.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAr) "مراجعة الذاكرة طويلة المدى (SM-2)" else "SM-2 Spaced Repetition",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAr) "لديك ${state.dueCards.size} مفردات مستحقة للمراجعة الآن"
                            else "${state.dueCards.size} words due for review right now",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onStartSm2Session,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "ابدأ جلسة المراجعة الذكية" else "Start Smart Review Session")
                }
            }
        }

        // 2. بطاقة قاموسي
        OutlinedCard(
            onClick = onOpenDictionary,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "قاموسي الشخصي" else "My Personal Dictionary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "ابحث في جميع الكلمات، استمع للنطق، وفلتر بالتصنيفات" else "Search words, listen to audio & filter by category",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        // 3. بطاقة الفلاشكاردز
        OutlinedCard(
            onClick = onOpenFlashcards,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Style, contentDescription = null, tint = OasisEmeraldLight, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "بطاقات الاستذكار (Flashcards)" else "Interactive Flashcards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "اقلب البطاقات واختبر سرعة تذكرك للمعاني والأمثلة" else "Flip cards to test your recall speed",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        // 4. بطاقة الألعاب المصغرة
        OutlinedCard(
            onClick = onOpenMiniGames,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = DesertAmberLight, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAr) "الألعاب اللغوية المصغّرة" else "Mini Language Games",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAr) "المطابقة السريعة • سباق الكلمات • تخمين الصورة" else "Speed Match • Word Sprint • Guess the Picture",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sm2ReviewSessionScreen(
    onBack: () -> Unit,
    viewModel: SmartReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "مراجعة SM-2 الذكية" else "SM-2 Smart Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.isSessionCompleted) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NabeehMascotView(mood = NabeehMoodState.CELEBRATE, size = 140.dp)
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = if (isAr) "أنهيت جميع المراجعات المستحقة!" else "All Due Reviews Completed!",
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isAr) "راجعت ${state.reviewedCountInSession} مفردات واكتسبت +${state.sessionEarnedXp} XP"
                    else "Reviewed ${state.reviewedCountInSession} words and earned +${state.sessionEarnedXp} XP",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(if (isAr) "العودة" else "Done")
                }
            }
            return@Scaffold
        }

        val currentItem = state.dueCards.getOrNull(state.currentCardIndex) ?: return@Scaffold
        val vocab = currentItem.vocabulary

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${state.currentCardIndex + 1} / ${state.dueCards.size} • EF: ${"%.2f".format(currentItem.reviewItem.easinessFactor)}",
                style = MaterialTheme.typography.labelLarge
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = vocab.wordTarget,
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = vocab.phoneticIpa,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    IconButton(
                        onClick = {
                            viewModel.audioPlayer.playAudioOrFallbackTts(
                                vocab.localAudioPath, vocab.audioUrl, vocab.wordTarget
                            )
                        }
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen", modifier = Modifier.size(34.dp))
                    }

                    if (state.isAnswerRevealed) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
                        Text(
                            text = vocab.translationAr,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = OasisEmeraldLight
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = vocab.exampleTarget,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = vocab.exampleAr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (!state.isAnswerRevealed) {
                Button(
                    onClick = viewModel::revealCardAnswer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (isAr) "إظهار المعنى والمثال" else "Show Answer")
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isAr) "كيف كان تذكرك لهذه المفردة؟" else "How well did you recall this word?",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Sm2UserGrade.entries.forEach { grade ->
                            val btnColor = when (grade) {
                                Sm2UserGrade.AGAIN -> SemanticErrorRed
                                Sm2UserGrade.HARD -> DesertAmberLight
                                Sm2UserGrade.GOOD -> MaterialTheme.colorScheme.primary
                                Sm2UserGrade.EASY -> OasisEmeraldLight
                            }
                            Button(
                                onClick = { viewModel.submitCardGrade(grade) },
                                colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                contentPadding = PaddingValues(4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isAr) grade.labelAr else grade.labelEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
