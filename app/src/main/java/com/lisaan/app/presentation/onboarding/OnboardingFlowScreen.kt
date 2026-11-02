package com.lisaan.app.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lisaan.app.core.designsystem.components.NabeehMascotView
import com.lisaan.app.core.designsystem.components.NabeehMoodState
import com.lisaan.app.core.designsystem.theme.LocalAppLanguage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFlowScreen(
    onOnboardingCompleteNavigateToAuth: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isAr = LocalAppLanguage.current == "ar"

    BackHandler(enabled = state.currentStep != OnboardingStep.INTRO_PAGER) {
        viewModel.navigateBackStep()
    }

    val stepProgress = when (state.currentStep) {
        OnboardingStep.INTRO_PAGER -> 0.14f
        OnboardingStep.INTERFACE_LANGUAGE -> 0.28f
        OnboardingStep.TARGET_LANGUAGE -> 0.42f
        OnboardingStep.LEARNING_GOAL -> 0.56f
        OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE, OnboardingStep.PLACEMENT_TEST -> 0.72f
        OnboardingStep.DAILY_GOAL -> 0.86f
        OnboardingStep.NOTIFICATIONS_OPT_IN -> 1.0f
    }

    Scaffold(
        topBar = {
            if (state.currentStep != OnboardingStep.INTRO_PAGER) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {
                            LinearProgressIndicator(
                                progress = { stepProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .padding(end = 16.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.navigateBackStep() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isAr) "رجوع" else "Back"
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.currentStep,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "onboarding_step_transition"
        ) { step ->
            when (step) {
                OnboardingStep.INTRO_PAGER -> IntroPagerContent(
                    isAr = isAr,
                    onFinishIntro = viewModel::finishIntroPager
                )
                OnboardingStep.INTERFACE_LANGUAGE -> InterfaceLanguageContent(
                    currentLang = state.interfaceLang,
                    onSelectLang = viewModel::selectInterfaceLanguage
                )
                OnboardingStep.TARGET_LANGUAGE -> TargetLanguageContent(
                    isAr = isAr,
                    onSelectTarget = viewModel::selectTargetLanguage
                )
                OnboardingStep.LEARNING_GOAL -> LearningGoalContent(
                    isAr = isAr,
                    selectedGoal = state.selectedGoal,
                    onSelectGoal = viewModel::selectLearningGoal
                )
                OnboardingStep.LEVEL_OR_PLACEMENT_CHOICE -> LevelOrPlacementChoiceContent(
                    isAr = isAr,
                    onSelectManualLevel = viewModel::chooseManualLevel,
                    onStartPlacementTest = viewModel::startPlacementTest
                )
                OnboardingStep.PLACEMENT_TEST -> PlacementTestContent(
                    isAr = isAr,
                    state = state,
                    onSelectOption = viewModel::answerPlacementQuestion,
                    onProceedAfterResult = viewModel::confirmPlacementResultAndProceed
                )
                OnboardingStep.DAILY_GOAL -> DailyGoalContent(
                    isAr = isAr,
                    selectedMinutes = state.dailyGoalMinutes,
                    onSelectMinutes = viewModel::selectDailyGoalMinutes
                )
                OnboardingStep.NOTIFICATIONS_OPT_IN -> NotificationOptInContent(
                    isAr = isAr,
                    state = state,
                    onUpdateSettings = viewModel::updateReminderSettings,
                    onFinish = { viewModel.completeOnboarding(onOnboardingCompleteNavigateToAuth) }
                )
            }
        }
    }
}

// 1. شاشات التعريف الثلاث (3 Intro Slides)
@Composable
private fun IntroPagerContent(
    isAr: Boolean,
    onFinishIntro: () -> Unit
) {
    val pages = listOf(
        Triple(
            if (isAr) "أهلاً بك في «لِسان»" else "Welcome to Lisaan",
            if (isAr) "رحلة لغوية أصيلة مصممة للعقل العربي، يقودك فيها الهدهد «نبيه» خطوة بخطوة نحو الطلاقة."
            else "An authentic language journey crafted for clarity, guided step-by-step by Nabeeh the Hoopoe.",
            NabeehMoodState.IDLE
        ),
        Triple(
            if (isAr) "تعلّم ذكي وتكرار متباعد" else "Smart Review & AI Coaching",
            if (isAr) "خوارزمية SM-2 تحفظ لك الكلمات الصعبة في ذاكرتك طويلة المدى، ومدرب ذكاء اصطناعي يحاورك بالصوت والنص."
            else "SM-2 spaced repetition locks vocabulary into long-term memory, paired with a voice & text AI Coach.",
            NabeehMoodState.THINKING
        ),
        Triple(
            if (isAr) "مجتمع حي وتحديات ممتعة" else "Live Community & Leagues",
            if (isAr) "شارك كتاباتك وصوتك ليصححها متحدثون متقنون، وتنافس في الدوريات الأسبوعية بدون ملل."
            else "Share your writing and voice recordings for peer feedback, and climb weekly leagues.",
            NabeehMoodState.CELEBRATE
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinishIntro) {
                Text(if (isAr) "تخطي" else "Skip")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { pageIndex ->
            val (title, subtitle, mood) = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NabeehMascotView(mood = mood, size = 160.dp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < pages.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinishIntro()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (pagerState.currentPage < pages.lastIndex) {
                    if (isAr) "التالي" else "Next"
                } else {
                    if (isAr) "ابدأ رحلتك" else "Get Started"
                },
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// 2. اختيار لغة الواجهة (العربية / الإنجليزية)
@Composable
private fun InterfaceLanguageContent(
    currentLang: String,
    onSelectLang: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabeehMascotView(mood = NabeehMoodState.IDLE, size = 100.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "اختر لغة واجهة التطبيق\nChoose App Interface Language",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        SelectableOptionCard(
            title = "العربية (الوضع الأصلي RTL)",
            subtitle = "واجهة عربية كاملة مع شرح القواعد والمفردات بالعربية",
            leadingEmoji = "🇸🇦",
            isSelected = currentLang == "ar",
            onClick = { onSelectLang("ar") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SelectableOptionCard(
            title = "English (LTR Interface)",
            subtitle = "Full English interface with bilingual Arabic hints",
            leadingEmoji = "🇬🇧",
            isSelected = currentLang == "en",
            onClick = { onSelectLang("en") }
        )
    }
}

// 3. اختيار اللغة المستهدفة للتعلم
@Composable
private fun TargetLanguageContent(
    isAr: Boolean,
    onSelectTarget: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isAr) "أي لغة تود إتقانها في «لِسان»؟" else "Which language do you want to master?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        SelectableOptionCard(
            title = if (isAr) "اللغة الإنجليزية (English)" else "English (for Arabic Speakers)",
            subtitle = if (isAr) "3 وحدات تأسيسية كاملة × 15 درساً تفاعلياً + محادثة ذكية" else "3 Core Units × 15 Lessons + AI Conversation",
            leadingEmoji = "🇬🇧",
            isSelected = true,
            onClick = { onSelectTarget("en") }
        )
        Spacer(modifier = Modifier.height(16.dp))
      OutlinedCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🇪🇸", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isAr) "الإسبانية والفرنسية والألمانية" else "Spanish, French & German",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (isAr) "تُضاف تباعاً في التحديثات القادمة" else "Coming soon in upcoming updates",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// 4. اختيار الهدف (سفر / شغل / دراسة / هواية)
@Composable
private fun LearningGoalContent(
    isAr: Boolean,
    selectedGoal: String,
    onSelectGoal: (String) -> Unit
) {
    val goals = listOf(
        Triple("TRAVEL", if (isAr) "السفر واستكشاف العالم" else "Travel & Exploration", "✈️"),
        Triple("WORK", if (isAr) "التطور المهني والعمل" else "Career & Business", "💼"),
        Triple("STUDY", if (isAr) "الدراسة الأكاديمية والامتحانات" else "Academic Study & Exams", "🎓"),
        Triple("HOBBY", if (isAr) "الثقافة والتواصل الشخصي" else "Culture & Hobby", "🎨")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isAr) "ما هدفك الأساسي من تعلّم الإنجليزية؟" else "What is your main learning goal?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        goals.forEach { (code, label, emoji) ->
            SelectableOptionCard(
                title = label,
                subtitle = "",
                leadingEmoji = emoji,
                isSelected = selectedGoal == code,
                onClick = { onSelectGoal(code) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// 5. اختيار المستوى أو دخول اختبار تحديد المستوى (10 أسئلة)
@Composable
private fun LevelOrPlacementChoiceContent(
    isAr: Boolean,
    onSelectManualLevel: (String) -> Unit,
    onStartPlacementTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabeehMascotView(mood = NabeehMoodState.THINKING, size = 100.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) "ما مستواك الحالي في الإنجليزية؟" else "What is your current English level?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        SelectableOptionCard(
            title = if (isAr) "أبدأ من الصفر (مبتدئ A1)" else "Starting from scratch (A1 Beginner)",
            subtitle = if (isAr) "أعرف فقط بعض الحروف أو الكلمات البسيطة جداً" else "I only know a few basic words",
            leadingEmoji = "🌱",
            isSelected = false,
            onClick = { onSelectManualLevel("A1") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        SelectableOptionCard(
            title = if (isAr) "لديّ أساسيات بسيطة (A2)" else "Elementary (A2)",
            subtitle = if (isAr) "أستطيع تقديم نفسي وطلب أشياء بسيطة" else "I can introduce myself and order food",
            leadingEmoji = "🌿",
            isSelected = false,
            onClick = { onSelectManualLevel("A2") }
        )
        Spacer(modifier = Modifier.height(12.dp))

        SelectableOptionCard(
            title = if (isAr) "حدد مستواي عبر اختبار ذكي (10 أسئلة)" else "Take the 10-Question Placement Test",
            subtitle = if (isAr) "يستغرق دقيقتين ليضعك «نبيه» في الوحدة الأنسب لك" else "Takes 2 minutes to unlock the right units for you",
            leadingEmoji = "🧭",
            isSelected = true,
            onClick = onStartPlacementTest
        )
    }
}

// 5.ب شاشة اختبار تحديد المستوى (10 أسئلة)
@Composable
private fun PlacementTestContent(
    isAr: Boolean,
    state: OnboardingUiState,
    onSelectOption: (Int, Int) -> Unit,
    onProceedAfterResult: () -> Unit
) {
    val result = state.placementResult
    if (result != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            NabeehMascotView(mood = NabeehMoodState.CELEBRATE, size = 130.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (isAr) "مستواك الموصى به: ${result.assignedLevel}" else "Recommended Level: ${result.assignedLevel}",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isAr) "أجبت بشكل صحيح على ${result.correctCount} من ${result.totalQuestions} أسئلة"
                else "You answered ${result.correctCount} out of ${result.totalQuestions} correctly",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isAr) result.feedbackAr else result.feedbackEn,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onProceedAfterResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isAr) "متابعة لاختيار الهدف اليومي" else "Continue to Daily Goal")
            }
        }
        return
    }

    val currentQuestion = state.placementQuestions.getOrNull(state.currentQuestionIndex) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (isAr) "سؤال ${state.currentQuestionIndex + 1} من ${state.placementQuestions.size} • مستوى ${currentQuestion.cefrBand}"
            else "Question ${state.currentQuestionIndex + 1} of ${state.placementQuestions.size} • ${currentQuestion.cefrBand}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isAr) currentQuestion.promptAr else currentQuestion.promptEn,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = currentQuestion.sentenceContext,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        currentQuestion.options.forEachIndexed { idx, optionText ->
            OutlinedButton(
                onClick = { onSelectOption(currentQuestion.id, idx) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = optionText,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

// 6. اختيار الهدف اليومي (5 / 10 / 15 / 20 دقيقة)
@Composable
private fun DailyGoalContent(
    isAr: Boolean,
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit
) {
    val options = listOf(
        Triple(5, if (isAr) "5 دقائق يومياً (هادئ)" else "5 min / day (Casual)", "☕"),
        Triple(10, if (isAr) "10 دقائق يومياً (منتظم - موصى به)" else "10 min / day (Regular)", "⚡"),
        Triple(15, if (isAr) "15 دقيقة يومياً (جادّ)" else "15 min / day (Serious)", "🔥"),
        Triple(20, if (isAr) "20 دقيقة يومياً (مكثف)" else "20 min / day (Intense)", "🚀")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NabeehMascotView(mood = NabeehMoodState.STREAK_FIRE, size = 100.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAr) "كم دقيقة تود تخصيصها يومياً؟" else "What is your daily learning goal?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        options.forEach { (minutes, title, emoji) ->
            SelectableOptionCard(
                title = title,
                subtitle = "",
                leadingEmoji = emoji,
                isSelected = selectedMinutes == minutes,
                onClick = { onSelectMinutes(minutes) }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// 7. تفعيل الإشعارات الذكية
@Composable
private fun NotificationOptInContent(
    isAr: Boolean,
    state: OnboardingUiState,
    onUpdateSettings: (Boolean, Int, Int) -> Unit,
    onFinish: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onUpdateSettings(granted, state.reminderHour, state.reminderMinute)
        onFinish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NabeehMascotView(mood = NabeehMoodState.ENCOURAGE, size = 120.dp)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (isAr) "لا تدع شعلتك اليومية تنطفئ!" else "Protect Your Daily Streak!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isAr) "سيذكّرك «نبيه» بلطف في الوقت الذي يناسبك لتكمل هدفك اليومي (${state.dailyGoalMinutes} دقائق)."
                else "Nabeeh will gently remind you at your preferred time to complete your ${state.dailyGoalMinutes}-minute goal.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isAr) "وقت التذكير اليومي" else "Daily Reminder Time",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = String.format("%02d:%02d", state.reminderHour, state.reminderMinute),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onUpdateSettings(true, state.reminderHour, state.reminderMinute)
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isAr) "تفعيل التذكير والبدء" else "Enable Reminders & Start")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    onUpdateSettings(false, state.reminderHour, state.reminderMinute)
                    onFinish()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isAr) "ربما لاحقاً" else "Maybe Later")
            }
        }
    }
}

@Composable
private fun SelectableOptionCard(
    title: String,
    subtitle: String,
    leadingEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = leadingEmoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
