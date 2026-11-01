package com.lisaan.app.presentation.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LeagueBronze
import com.lisaan.app.core.designsystem.theme.LeagueGold
import com.lisaan.app.core.designsystem.theme.LeagueSilver
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight

data class LeaderboardCompetitorUi(
    val id: String,
    val name: String,
    val weeklyXp: Int,
    val streakDays: Int,
    val isCurrentUser: Boolean = false,
    val isFriend: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onBack: () -> Unit
) {
    val isAr = LocalAppLanguage.current == "ar"
    var selectedLeague by remember { mutableStateOf("GOLD") } // BRONZE, SILVER, GOLD
    var onlyFriendsFilter by remember { mutableStateOf(false) }

    val allCompetitors = remember(selectedLeague) {
        listOf(
            LeaderboardCompetitorUi("1", "مريم الشافعي", 1420, 21, isFriend = true),
            LeaderboardCompetitorUi("2", "أنت (متعلم لِسان)", 1280, 14, isCurrentUser = true, isFriend = true),
            LeaderboardCompetitorUi("3", "خالد الدوسري", 1150, 9, isFriend = true),
            LeaderboardCompetitorUi("4", "نور الهدى", 980, 12, isFriend = false),
            LeaderboardCompetitorUi("5", "عمر الفاروق", 840, 6, isFriend = false)
        )
    }

    val displayedList = if (onlyFriendsFilter) allCompetitors.filter { it.isFriend } else allCompetitors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "لوحة المتصدرين الأسبوعية" else "Weekly Leaderboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    FilterChip(
                        selected = onlyFriendsFilter,
                        onClick = { onlyFriendsFilter = !onlyFriendsFilter },
                        label = { Text(if (isAr) "الأصدقاء فقط" else "Friends Only") },
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // اختيار الدوري (برونز 🥉 / فضة 🥈 / ذهب 🥇)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("BRONZE", if (isAr) "برونزي 🥉" else "Bronze 🥉", LeagueBronze),
                    Triple("SILVER", if (isAr) "فضي 🥈" else "Silver 🥈", LeagueSilver),
                    Triple("GOLD", if (isAr) "ذهبي 🥇" else "Gold 🥇", LeagueGold)
                ).forEach { (code, label, _) ->
                    FilterChip(
                        selected = selectedLeague == code,
                        onClick = { selectedLeague = code },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(displayedList, key = { _, item -> item.id }) { index, competitor ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (competitor.isCurrentUser) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (index < 3) DesertAmberLight else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = competitor.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "🔥 ${competitor.streakDays} " + if (isAr) "يوم متواصل" else "day streak",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            Text(
                                text = "${competitor.weeklyXp} XP",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OasisEmeraldLight
                            )
                        }
                    }
                }
            }
        }
    }
}
