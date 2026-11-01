package com.lisaan.app.presentation.community

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.lisaan.app.core.database.dao.AchievementDao
import com.lisaan.app.core.database.dao.CommunityDao
import com.lisaan.app.core.database.dao.UserDao
import com.lisaan.app.core.database.entities.CommunityPostEntity
import com.lisaan.app.core.database.entities.CorrectionEntity
import com.lisaan.app.core.database.relations.CommunityPostWithCorrectionsRelation
import com.lisaan.app.core.datastore.UserPreferencesDataStore
import com.lisaan.app.core.designsystem.theme.DesertAmberLight
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.designsystem.theme.OasisEmeraldLight
import com.lisaan.app.core.designsystem.theme.SemanticErrorRed
import com.lisaan.app.core.media.LisaanAudioPlayer
import com.lisaan.app.core.media.VoiceRecorderManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val communityDao: CommunityDao,
    private val userDao: UserDao,
    private val achievementDao: AchievementDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    val voiceRecorder: VoiceRecorderManager,
    val audioPlayer: LisaanAudioPlayer
) : ViewModel() {

    val communityFeed: StateFlow<List<CommunityPostWithCorrectionsRelation>> =
        communityDao.observeCommunityFeed()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        seedInitialCommunityPostsIfNeeded()
    }

    private fun seedInitialCommunityPostsIfNeeded() {
        viewModelScope.launch {
            if (communityFeed.first().isEmpty()) {
                val samplePost1 = CommunityPostEntity(
                    id = "post_sample_1",
                    authorId = "learner_ahmed",
                    authorName = "أحمد منصور",
                    authorAvatarUrl = null,
                    targetLangCode = "en",
                    nativeLangCode = "ar",
                    textContent = "Yesterday I go to the airport early because my flight was at 8 AM.",
                    promptTopic = "وصف رحلة سفر",
                    correctionsCount = 1
                )
                val sampleCorrection1 = CorrectionEntity(
                    id = "corr_sample_1",
                    postId = "post_sample_1",
                    correctorId = "mentor_sarah",
                    correctorName = "Sarah Jenkins (Native)",
                    correctorAvatarUrl = null,
                    originalTextSnapshot = samplePost1.textContent,
                    correctedText = "Yesterday I went to the airport early because my flight was at 8 AM.",
                    explanationNote = "بما أن الحدث وقع بالأمس (Yesterday)، نستخدم الماضي من go وهو went.",
                    thanksCount = 3,
                    isThankedByAuthor = true
                )
                val samplePost2 = CommunityPostEntity(
                    id = "post_sample_2",
                    authorId = "learner_reem",
                    authorName = "ريم العتيبي",
                    authorAvatarUrl = null,
                    targetLangCode = "en",
                    nativeLangCode = "ar",
                    textContent = "I am looking forward to meet my new colleagues next week.",
                    promptTopic = "بيئة العمل",
                    correctionsCount = 0
                )
                communityDao.upsertPosts(listOf(samplePost1, samplePost2))
                communityDao.upsertCorrections(listOf(sampleCorrection1))
            }
        }
    }

    fun publishNewPost(
        topic: String,
        textContent: String,
        recordedAudioFile: File?,
        audioDurationSec: Int,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"
            val user = userDao.getUserById(userId)
            val postId = "post_${UUID.randomUUID()}"

            // رفع التسجيل الصوتي إلى Firebase Storage إن وجد، أو الاحتفاظ بالمسار المحلي
            val uploadedAudioUrl = if (recordedAudioFile != null && recordedAudioFile.exists()) {
                runCatching {
                    val ref = storage.reference.child("community_audio/$postId.m4a")
                    ref.putFile(Uri.fromFile(recordedAudioFile)).await()
                    ref.downloadUrl.await().toString()
                }.getOrDefault(recordedAudioFile.absolutePath)
            } else null

            val postEntity = CommunityPostEntity(
                id = postId,
                authorId = userId,
                authorName = user?.displayName ?: "متعلم لِسان",
                authorAvatarUrl = user?.avatarUrl,
                textContent = textContent.trim(),
                audioUrl = uploadedAudioUrl,
                audioDurationSec = audioDurationSec,
                promptTopic = topic.ifBlank { "كتابة حرة" }
            )

            communityDao.upsertPost(postEntity)
            runCatching {
                firestore.collection("community_posts").document(postId).set(postEntity).await()
            }
            onComplete()
        }
    }

    fun submitCorrection(
        post: CommunityPostEntity,
        correctedText: String,
        explanationNote: String
    ) {
        viewModelScope.launch {
            val prefs = preferencesDataStore.settingsFlow.first()
            val userId = prefs.currentUserId ?: "guest_default"
            val user = userDao.getUserById(userId)

            val correction = CorrectionEntity(
                id = "corr_${UUID.randomUUID()}",
                postId = post.id,
                correctorId = userId,
                correctorName = user?.displayName ?: "متعلم لِسان",
                correctorAvatarUrl = user?.avatarUrl,
                originalTextSnapshot = post.textContent,
                correctedText = correctedText.trim(),
                explanationNote = explanationNote.trim()
            )

            communityDao.upsertCorrection(correction)
            communityDao.upsertPost(post.copy(correctionsCount = post.correctionsCount + 1))
            userDao.incrementUserXp(userId, 15) // مكافأة +15 XP لتصحيح زملائك

            // تحديث تقدم وسام فصيح المجتمع
            achievementDao.getAchievementByCode("COMMUNITY_HELPER")?.let { ach ->
                val newProg = (ach.currentProgress + 1).coerceAtMost(ach.requiredValue)
                achievementDao.updateAchievementProgress(
                    id = ach.id,
                    progress = newProg,
                    unlocked = newProg >= ach.requiredValue,
                    unlockedAt = if (newProg >= ach.requiredValue) System.currentTimeMillis() else null
                )
            }
        }
    }

    fun thankCorrection(correctionId: String) {
        viewModelScope.launch {
            communityDao.thankCorrection(correctionId)
        }
    }

    fun reportPostContent(postId: String, reason: String) {
        viewModelScope.launch {
            communityDao.markPostReported(postId)
            runCatching {
                firestore.collection("content_reports").add(
                    mapOf(
                        "postId" to postId,
                        "reason" to reason,
                        "reportedAt" to System.currentTimeMillis()
                    )
                ).await()
            }
        }
    }
}

@Composable
fun CommunityHubTabScreen(
    onOpenCreatePost: () -> Unit,
    onOpenPostDetail: (String) -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenAchievements: () -> Unit,
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val feed by viewModel.communityFeed.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var postToCorrect by remember { mutableStateOf<CommunityPostEntity?>(null) }
    var postToReport by remember { mutableStateOf<CommunityPostEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCreatePost,
                icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                text = { Text(if (isAr) "انشر نصاً أو صوتاً للتصحيح" else "Post Text or Voice") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // شريط الوصول السريع للدوري الأسبوعي والأوسمة
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenLeaderboard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = DesertAmberLight)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAr) "الدوري الأسبوعي" else "Leaderboard")
                    }
                    FilledTonalButton(
                        onClick = onOpenAchievements,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = OasisEmeraldLight)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAr) "الأوسمة" else "Badges")
                    }
                }
            }

            items(feed, key = { it.post.id }) { item ->
                val post = item.post
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(post.authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(post.promptTopic, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { postToReport = post }) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = if (isAr) "إبلاغ عن محتوى" else "Report Content",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(post.textContent, style = MaterialTheme.typography.bodyLarge)

                        if (!post.audioUrl.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.audioPlayer.playAudioOrFallbackTts(
                                        post.audioUrl, post.audioUrl, post.textContent
                                    )
                                }
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isAr) "استمع لتسجيل المتعلم (${post.audioDurationSec} ث)" else "Play Recording (${post.audioDurationSec}s)")
                            }
                        }

                        // عرض التصحيحات المقدمة
                        if (item.corrections.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            item.corrections.forEach { corr ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = corr.correctorName,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = corr.originalTextSnapshot,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = TextDecoration.LineThrough,
                                            color = SemanticErrorRed
                                        )
                                        Text(
                                            text = "✓ ${corr.correctedText}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OasisEmeraldLight
                                        )
                                        if (corr.explanationNote.isNotBlank()) {
                                            Text(corr.explanationNote, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { viewModel.thankCorrection(corr.id) }) {
                                                Icon(Icons.Default.Favorite, contentDescription = "Thank", tint = SemanticErrorRed)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isAr) "شكراً (${corr.thanksCount})" else "Thank (${corr.thanksCount})")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { postToCorrect = post },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RateReview, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAr) "قدّم تصحيحاً واكسب +15 XP" else "Write a Correction (+15 XP)")
                        }
                    }
                }
            }
        }
    }

    // نافذة إضافة تصحيح
    postToCorrect?.let { targetPost ->
        var correctedText by remember { mutableStateOf(targetPost.textContent) }
        var explanation by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { postToCorrect = null },
            title = { Text(if (isAr) "تصحيح مشاركة ${targetPost.authorName}" else "Correct ${targetPost.authorName}'s Post") },
            text = {
                Column {
                    OutlinedTextField(
                        value = correctedText,
                        onValueChange = { correctedText = it },
                        label = { Text(if (isAr) "النص بعد التصحيح" else "Corrected Sentence") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = explanation,
                        onValueChange = { explanation = it },
                        label = { Text(if (isAr) "شرح مبسط للسبب" else "Helpful Explanation") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.submitCorrection(targetPost, correctedText, explanation)
                        postToCorrect = null
                    }
                ) {
                    Text(if (isAr) "إرسال التصحيح" else "Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { postToCorrect = null }) {
                    Text(if (isAr) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // نافذة الإبلاغ عن محتوى مخالف
    postToReport?.let { targetPost ->
        AlertDialog(
            onDismissRequest = { postToReport = null },
            title = { Text(if (isAr) "الإبلاغ عن محتوى غير ملائم" else "Report Inappropriate Content") },
            text = {
                Text(
                    if (isAr) "هل تود الإبلاغ عن هذا المنشور وإخفاءه فوراً لمراجعته من فريق الإشراف؟"
                    else "Would you like to report and hide this post for moderation review?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reportPostContent(targetPost.id, "Inappropriate or spam")
                        postToReport = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isAr) "إبلاغ وإخفاء" else "Report & Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { postToReport = null }) {
                    Text(if (isAr) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityPostScreen(
    onBack: () -> Unit,
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val isAr = LocalAppLanguage.current == "ar"
    val isRecording by viewModel.voiceRecorder.isRecording.collectAsState()
    var topic by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordedSec by remember { mutableIntStateOf(0) }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.voiceRecorder.startRecording()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "مشاركة جديدة للتصحيح" else "New Practice Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(if (isAr) "موضوع المشاركة (مثال: يومي في العمل)" else "Topic (e.g., My Workday)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (isAr) "اكتب جملتك بالإنجليزية ليصححها المجتمع..." else "Write your English paragraph...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = {
                            if (isRecording) {
                                val (file, sec) = viewModel.voiceRecorder.stopRecording()
                                recordedFile = file
                                recordedSec = sec
                            } else {
                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isRecording) {
                                if (isAr) "إيقاف التسجيل" else "Stop Recording"
                            } else {
                                if (isAr) "إرفاق تسجيل صوتي لنطقك" else "Attach Voice Recording"
                            }
                        )
                    }
                    if (recordedFile != null) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("✓ ${recordedSec}s", fontWeight = FontWeight.Bold, color = OasisEmeraldLight)
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.publishNewPost(topic, content, recordedFile, recordedSec, onBack)
                },
                enabled = content.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(if (isAr) "نشر في المجتمع" else "Publish to Community")
            }
        }
    }
}
