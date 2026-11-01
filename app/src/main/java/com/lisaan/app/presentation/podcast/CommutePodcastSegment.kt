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
