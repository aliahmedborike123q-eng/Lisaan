package com.lisaan.app.presentation.dictionary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lisaan.app.core.database.dao.VocabularyDao
import com.lisaan.app.core.database.entities.VocabularyEntity
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import com.lisaan.app.core.media.LisaanAudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyDictionaryViewModel @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    val audioPlayer: LisaanAudioPlayer
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<String?>(null)
    private val onlyBookmarked = MutableStateFlow(false)

    val queryState: StateFlow<String> = searchQuery.asStateFlow()
    val categoryState: StateFlow<String?> = selectedCategory.asStateFlow()
    val bookmarkedOnlyState: StateFlow<Boolean> = onlyBookmarked.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val vocabulariesFlow: StateFlow<List<VocabularyEntity>> = combine(
        searchQuery,
        selectedCategory,
        onlyBookmarked
    ) { q, cat, bookmarked ->
        Triple(q, cat, bookmarked)
    }.flatMapLatest { (q, cat, bookmarked) ->
        vocabularyDao.searchVocabularies(
            query = q.trim(),
            category = cat,
            onlyBookmarked = bookmarked
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun onSelectCategory(category: String?) {
        selectedCategory.value = category
    }

    fun onToggleBookmarkedFilter() {
        onlyBookmarked.value = !onlyBookmarked.value
    }

    fun toggleWordBookmark(vocab: VocabularyEntity) {
        viewModelScope.launch {
            vocabularyDao.setBookmarkStatus(vocab.id, !vocab.isBookmarked)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDictionaryScreen(
    onBack: () -> Unit,
    viewModel: MyDictionaryViewModel = hiltViewModel()
) {
    val vocabList by viewModel.vocabulariesFlow.collectAsState()
    val query by viewModel.queryState.collectAsState()
    val selectedCat by viewModel.categoryState.collectAsState()
    val onlySaved by viewModel.bookmarkedOnlyState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    val categories = listOf(
        null to if (isAr) "الكل" else "All",
        "GREETINGS" to if (isAr) "تحيات وتعارف" else "Greetings",
        "TRAVEL" to if (isAr) "سفر وفنادق" else "Travel",
        "FOOD" to if (isAr) "مقهى ومطاعم" else "Food & Café",
        "WORK" to if (isAr) "عمل واجتماعات" else "Work",
        "DAILY" to if (isAr) "حياة يومية" else "Daily Life"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "قاموسي (${vocabList.size})" else "My Dictionary (${vocabList.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    FilterChip(
                        selected = onlySaved,
                        onClick = viewModel::onToggleBookmarkedFilter,
                        label = { Text(if (isAr) "المحفوظة" else "Saved") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (onlySaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null
                            )
                        },
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
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text(if (isAr) "ابحث بالعربية أو الإنجليزية أو النطق..." else "Search English or Arabic...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (catCode, label) ->
                    FilterChip(
                        selected = selectedCat == catCode,
                        onClick = { viewModel.onSelectCategory(catCode) },
                        label = { Text(label) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vocabList, key = { it.id }) { vocab ->
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
                            IconButton(
                                onClick = {
                                    viewModel.audioPlayer.playAudioOrFallbackTts(
                                        vocab.localAudioPath, vocab.audioUrl, vocab.wordTarget
                                    )
                                }
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Listen", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${vocab.wordTarget} — ${vocab.translationAr}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${vocab.phoneticIpa} • ${vocab.transliteration}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = vocab.exampleTarget,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { viewModel.toggleWordBookmark(vocab) }) {
                                Icon(
                                    imageVector = if (vocab.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardsDeckScreen(
    onBack: () -> Unit,
    viewModel: MyDictionaryViewModel = hiltViewModel()
) {
    val vocabList by viewModel.vocabulariesFlow.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val rotationY by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(400),
        label = "flip_card"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isAr) "بطاقات الفلاشكاردز" else "Flashcards Deck") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (vocabList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val currentVocab = vocabList[currentIndex.coerceIn(0, vocabList.lastIndex)]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${currentIndex + 1} / ${vocabList.size}",
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .graphicsLayer {
                        this.rotationY = rotationY
                        cameraDistance = 12f * density
                    }
                    .clickable { isFlipped = !isFlipped },
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotationY <= 90f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentVocab.wordTarget,
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = currentVocab.phoneticIpa, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (isAr) "اضغط لقلب البطاقة ↻" else "Tap to flip card ↻",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.graphicsLayer { this.rotationY = 180f },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentVocab.translationAr,
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentVocab.exampleTarget,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentVocab.exampleAr,
                                style = MaterialTheme.typography.bodyMedium,
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
                    onClick = {
                        isFlipped = false
                        if (currentIndex > 0) currentIndex--
                    },
                    enabled = currentIndex > 0
                ) {
                    Text(if (isAr) "السابقة" else "Previous")
                }

                FilledTonalIconButton(
                    onClick = {
                        viewModel.audioPlayer.playAudioOrFallbackTts(
                            currentVocab.localAudioPath, currentVocab.audioUrl, currentVocab.wordTarget
                        )
                    }
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Listen")
                }

                Button(
                    onClick = {
                        isFlipped = false
                        if (currentIndex < vocabList.lastIndex) currentIndex++ else currentIndex = 0
                    }
                ) {
                    Text(if (isAr) "التالية" else "Next")
                }
            }
        }
    }
}
