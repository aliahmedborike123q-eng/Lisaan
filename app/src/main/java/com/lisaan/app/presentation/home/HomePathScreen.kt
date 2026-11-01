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
