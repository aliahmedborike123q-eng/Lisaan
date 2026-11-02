package com.lisaan.app.presentation.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lisaan.app.core.database.dao.AchievementDao
import com.lisaan.app.core.database.dao.CourseContentDao
import com.lisaan.app.core.database.dao.UserDao
import com.lisaan.app.core.database.entities.AchievementEntity
import com.lisaan.app.core.database.relations.UnitWithLessonsRelation
import com.lisaan.app.core.database.relations.UserCompleteProfileRelation
import com.lisaan.app.core.datastore.AppThemeMode
import com.lisaan.app.core.datastore.AppUserSettings
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.core.media.LisaanTtsManager
import com.lisaan.app.core.sync.OfflineProgressSyncWorker
import com.lisaan.app.core.sync.UnitDownloadWorker
import com.lisaan.app.domain.repository.AuthRepository
import com.lisaan.app.presentation.auth.DeleteAccountConfirmationDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileAndSettingsViewModel @Inject constructor(
    private val userDao: UserDao,
    private val achievementDao: AchievementDao,
    private val courseContentDao: CourseContentDao,
    private val authRepository: AuthRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val ttsManager: LisaanTtsManager,
    private val workManager: WorkManager
) : ViewModel() {

    val settingsFlow: StateFlow<AppUserSettings> = preferencesDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUserSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    val userProfileFlow: StateFlow<UserCompleteProfileRelation?> = settingsFlow
        .flatMapLatest { prefs ->
            val uid = prefs.currentUserId ?: "guest_default"
            userDao.observeCompleteProfile(uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val achievementsFlow: StateFlow<List<AchievementEntity>> = achievementDao.observeAchievements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unitsFlow: StateFlow<List<UnitWithLessonsRelation>> =
        courseContentDao.observeUnitsWithLessons("en_from_ar")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun changeInterfaceLanguage(langCode: String) {
        viewModelScope.launch {
            preferencesDataStore.setInterfaceLanguage(langCode)
        }
    }

    fun changeThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesDataStore.setThemeMode(mode)
        }
    }

    fun changeAudioSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesDataStore.setAudioPlaybackSpeed(speed)
            ttsManager.setPreferredSpeechRate(speed)
        }
    }

    fun toggleSmartReminders(enabled: Boolean) {
        val current = settingsFlow.value
        viewModelScope.launch {
            preferencesDataStore.updateNotificationPreferences(
                smartReminder = enabled,
                hour = current.reminderHourOfDay,
                minute = current.reminderMinute,
                streakAlerts = current.streakProtectionAlertsEnabled,
                communityAlerts = current.communityNotificationsEnabled
            )
        }
    }

    fun downloadUnitForOffline(unitId: String) {
        val request = OneTimeWorkRequestBuilder<UnitDownloadWorker>()
            .setInputData(workDataOf(UnitDownloadWorker.KEY_UNIT_ID to unitId))
            .build()
        workManager.enqueue(request)
    }

    fun triggerManualCloudSync() {
        OfflineProgressSyncWorker.enqueueAutomaticNetworkSync(workManager)
    }

    fun signOutUser(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }

    fun deleteUserAccountPermanently(password: String?, onDeleted: () -> Unit) {
        viewModelScope.launch {
            authRepository.deleteAccountPermanently(password)
            onDeleted()
        }
    }
}

@Composable
fun ProfileOverviewTabScreen(
    onOpenSettings: () -> Unit,
    onOpenPaywall: (String) -> Unit,
    onOpenAchievements: () -> Unit,
    viewModel: ProfileAndSettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.userProfileFlow.collectAsState()
    val achievements by viewModel.achievementsFlow.collectAsState()
    val units by viewModel.unitsFlow.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    val user = profile?.user
    val streak = profile?.streak
    val sub = profile?.subscription

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. بطاقة الملف الشخصي
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (user?.displayName?.firstOrNull() ?: 'ل').toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = user?.displayName ?: (if (isAr) "متعلم لِسان" else "Lisaan Learner"),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user?.email ?: (if (isAr) "حساب ضيف" else "Guest Account"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    text = if (sub?.tier == "PREMIUM") "Lisaan Plus 👑" else "Free Plan",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        // 2. بطاقة الترقية إلى Lisaan Plus إن كان مجانياً
        if (sub?.tier != "PREMIUM") {
            item {
                Card(
                    onClick = { onOpenPaywall("profile_banner") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = DesertAmberLight, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAr) "احصل على «لِسان بلس»" else "Upgrade to Lisaan Plus",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAr) "محادثات ذكية لا محدودة وتحميل كامل للأوفلاين" else "Unlimited AI Coach & Offline Units",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        // 3. إحصائيات المتعلم
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileStatBox(
                    modifier = Modifier.weight(1f),
                    title = if (isAr) "الشعلة الحالية" else "Current Streak",
                    value = "🔥 ${streak?.currentStreakDays ?: 0}"
                )
                ProfileStatBox(
                    modifier = Modifier.weight(1f),
                    title = if (isAr) "مجموع XP" else "Total XP",
                    value = "⚡ ${user?.totalXp ?: 0}"
                )
                ProfileStatBox(
                    modifier = Modifier.weight(1f),
                    title = if (isAr) "المستوى" else "CEFR Level",
                    value = "🧭 ${user?.proficiencyLevel ?: "A1"}"
                )
            }
        }

        // 4. إدارة تحميل الوحدات للأوفلاين
        item {
            Text(
                text = if (isAr) "تحميل الوحدات للتعلم أوفلاين" else "Offline Unit Downloads",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(units, key = { it.unit.id }) { uwl ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAr) uwl.unit.titleAr else uwl.unit.titleEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uwl.unit.isDownloadedOffline) {
                                if (isAr) "✓ متاحة بدون إنترنت" else "✓ Downloaded Offline"
                            } else {
                                if (isAr) "${uwl.lessons.size} دروس + الصوتيات" else "${uwl.lessons.size} lessons + audio"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uwl.unit.isDownloadedOffline) OasisEmeraldLight else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.downloadUnitForOffline(uwl.unit.id) }) {
                        Icon(
                            imageVector = if (uwl.unit.isDownloadedOffline) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                            contentDescription = "Download Offline",
                            tint = if (uwl.unit.isDownloadedOffline) OasisEmeraldLight else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 5. الأوسمة والإنجازات
        item {
            Text(
                text = if (isAr) "الأوسمة والإنجازات (${achievements.count { it.isUnlocked }}/${achievements.size})"
                else "Achievements (${achievements.count { it.isUnlocked }}/${achievements.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(achievements, key = { it.id }) { ach ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (ach.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (ach.isUnlocked) DesertAmberLight else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAr) ach.titleAr else ach.titleEn,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAr) ach.descriptionAr else ach.descriptionEn,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "+${ach.rewardXp} XP",
                        style = MaterialTheme.typography.labelLarge,
                        color = OasisEmeraldLight
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOutNavigateToAuth: () -> Unit,
    viewModel: ProfileAndSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settingsFlow.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "الإعدادات والخصوصية" else "Settings & Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. لغة الواجهة (العربية / الإنجليزية)
            item {
                Text(if (isAr) "لغة الواجهة (Interface Language)" else "Interface Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = settings.interfaceLanguage == "ar",
                        onClick = { viewModel.changeInterfaceLanguage("ar") },
                        label = { Text("🇸🇦 العربية (RTL)") }
                    )
                    FilterChip(
                        selected = settings.interfaceLanguage == "en",
                        onClick = { viewModel.changeInterfaceLanguage("en") },
                        label = { Text("🇬🇧 English (LTR)") }
                    )
                }
            }

            // 2. المظهر (فاتح / داكن / تلقائي)
            item {
                Text(if (isAr) "المظهر (Theme)" else "Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            AppThemeMode.SYSTEM -> if (isAr) "تلقائي" else "System"
                            AppThemeMode.LIGHT -> if (isAr) "فاتح" else "Light"
                            AppThemeMode.DARK -> if (isAr) "داكن" else "Dark"
                        }
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.changeThemeMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 3. سرعة الصوت في الدروس
            item {
                Text(if (isAr) "سرعة نطق الصوتيات" else "Audio Playback Speed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(0.75f to "0.75x 🐢", 1.0f to "1.0x", 1.25f to "1.25x ⚡").forEach { (speed, label) ->
                        FilterChip(
                            selected = settings.audioPlaybackSpeed == speed,
                            onClick = { viewModel.changeAudioSpeed(speed) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // 4. الإشعارات الذكية والمزامنة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isAr) "التذكير اليومي الذكي وحماية الـ Streak" else "Smart Daily & Streak Reminders")
                    Switch(
                        checked = settings.smartReminderEnabled,
                        onCheckedChange = viewModel::toggleSmartReminders
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::triggerManualCloudSync,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "مزامنة التقدم مع السحابة الآن" else "Sync Progress to Cloud Now")
                }
            }

            // 5. تواصل معنا والخصوصية
            item {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@lisaan.app"))
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "تواصل معنا (support@lisaan.app)" else "Contact Us (support@lisaan.app)")
                }
            }

            // 6. تسجيل الخروج وحذف الحساب
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(
                    onClick = { viewModel.signOutUser(onSignedOutNavigateToAuth) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "تسجيل الخروج" else "Sign Out")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAr) "حذف الحساب والبيانات نهائياً" else "Delete Account Permanently")
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteAccountConfirmationDialog(
            isAr = isAr,
            onConfirmDelete = { password ->
                showDeleteDialog = false
                viewModel.deleteUserAccountPermanently(password, onSignedOutNavigateToAuth)
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun ProfileStatBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.labelMedium)
        }
    }
}
