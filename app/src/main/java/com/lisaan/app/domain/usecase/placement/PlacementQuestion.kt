package com.lisaan.app.domain.usecase.placement

import javax.inject.Inject

data class PlacementQuestion(
    val id: Int,
    val cefrBand: String, // "A1", "A2", "B1", "B2"
    val weightPoints: Int,
    val promptAr: String,
    val promptEn: String,
    val sentenceContext: String,
    val options: List<String>,
    val correctOptionIndex: Int
)

data class PlacementEvaluationResult(
    val correctCount: Int,
    val totalQuestions: Int,
    val weightedScorePercent: Int,
    val assignedLevel: String, // "A1", "A2", "B1", "B2"
    val feedbackAr: String,
    val feedbackEn: String
)

class CalculatePlacementLevelUseCase @Inject constructor() {

    fun getPlacementQuestions(): List<PlacementQuestion> = listOf(
        // A1 Questions (Weight = 1)
        PlacementQuestion(
            id = 1,
            cefrBand = "A1",
            weightPoints = 1,
            promptAr = "اختر الكلمة الصحيحة لإكمال التحية الصباحية:",
            promptEn = "Choose the correct word to complete the morning greeting:",
            sentenceContext = "Good ____! How are you today?",
            options = listOf("morning", "night", "goodbye", "tomorrow"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 2,
            cefrBand = "A1",
            weightPoints = 1,
            promptAr = "اختر الفعل المساعد المناسب للفاعل:",
            promptEn = "Select the correct verb form:",
            sentenceContext = "She ____ a software engineer from Cairo.",
            options = listOf("am", "is", "are", "be"),
            correctOptionIndex = 1
        ),
        PlacementQuestion(
            id = 3,
            cefrBand = "A1",
            weightPoints = 1,
            promptAr = "ما الترجمة الإنجليزية الصحيحة لجملة: (أريد فنجان قهوة من فضلك)؟",
            promptEn = "Which sentence means 'أريد فنجان قهوة من فضلك'?",
            sentenceContext = "Ordering at a café:",
            options = listOf(
                "I would like a cup of coffee, please.",
                "I am drinking coffee yesterday.",
                "Where is the coffee shop?",
                "Coffee is very hot."
            ),
            correctOptionIndex = 0
        ),
        // A2 Questions (Weight = 2)
        PlacementQuestion(
            id = 4,
            cefrBand = "A2",
            weightPoints = 2,
            promptAr = "اختر صيغة الماضي الصحيحة للفعل:",
            promptEn = "Choose the correct past tense form:",
            sentenceContext = "We ____ to Alexandria by train last weekend.",
            options = listOf("travel", "traveled", "traveling", "will travel"),
            correctOptionIndex = 1
        ),
        PlacementQuestion(
            id = 5,
            cefrBand = "A2",
            weightPoints = 2,
            promptAr = "اختر صيغة المقارنة الصحيحة:",
            promptEn = "Select the correct comparative adjective:",
            sentenceContext = "Learning with daily practice is ____ than cramming once a month.",
            options = listOf("more effective", "most effective", "effective", "as effective"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 6,
            cefrBand = "A2",
            weightPoints = 2,
            promptAr = "اختر حرف الجر المناسب مع الوقت:",
            promptEn = "Choose the right preposition:",
            sentenceContext = "Our flight departs ____ 7:30 PM from Terminal 2.",
            options = listOf("in", "on", "at", "to"),
            correctOptionIndex = 2
        ),
        // B1 Questions (Weight = 3)
        PlacementQuestion(
            id = 7,
            cefrBand = "B1",
            weightPoints = 3,
            promptAr = "اختر صيغة المضارع التام المناسبة:",
            promptEn = "Choose the correct present perfect form:",
            sentenceContext = "I ____ in this company for three years so far.",
            options = listOf("have worked", "worked", "am working", "had work"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 8,
            cefrBand = "B1",
            weightPoints = 3,
            promptAr = "أكمل الجملة الشرطية من النوع الأول:",
            promptEn = "Complete the first conditional sentence:",
            sentenceContext = "If we finish the report early, we ____ the client this afternoon.",
            options = listOf("will call", "called", "would have called", "calling"),
            correctOptionIndex = 0
        ),
        // B2 Questions (Weight = 4)
        PlacementQuestion(
            id = 9,
            cefrBand = "B2",
            weightPoints = 4,
            promptAr = "اختر التركيب الصحيح للجملة الشرطية التخيلية:",
            promptEn = "Choose the correct second conditional structure:",
            sentenceContext = "If I ____ more free time, I would learn a third language.",
            options = listOf("had", "have", "will have", "would have"),
            correctOptionIndex = 0
        ),
        PlacementQuestion(
            id = 10,
            cefrBand = "B2",
            weightPoints = 4,
            promptAr = "اختر الرابط المنطقي الدال على التناقض:",
            promptEn = "Select the appropriate connector of contrast:",
            sentenceContext = "____ the heavy rain, the outdoor cultural festival continued on schedule.",
            options = listOf("Despite", "Although", "Because", "Unless"),
            correctOptionIndex = 0
        )
    )

    fun evaluatePlacement(selectedAnswers: Map<Int, Int>): PlacementEvaluationResult {
        val questions = getPlacementQuestions()
        var correctCount = 0
        var earnedWeight = 0
        val maxWeight = questions.sumOf { it.weightPoints } // 23 points

        questions.forEach { q ->
            if (selectedAnswers[q.id] == q.correctOptionIndex) {
                correctCount++
                earnedWeight += q.weightPoints
            }
        }

        val scorePercent = ((earnedWeight.toFloat() / maxWeight.toFloat()) * 100).toInt()

        val (level, feedbackAr, feedbackEn) = when {
            scorePercent >= 80 -> Triple(
                "B2",
                "ممتاز! لديك حصيلة لغوية متقدمة، سنبدأ معك من التراكيب المتقدمة والمحادثات الطليقة.",
                "Awesome! You have an upper-intermediate command. We'll unlock advanced units for you."
            )
            scorePercent >= 55 -> Triple(
                "B1",
                "رائع! أساسياتك قوية، سنضعك في المستوى المتوسط لتطوير طلاقتك في المواقف اليومية والعملية.",
                "Great job! Your foundation is strong. You'll start at the intermediate level."
            )
            scorePercent >= 30 -> Triple(
                "A2",
                "بداية جيدة! تعرف المفردات الأساسية، وسنبني معاً الجمل والحوارات خطوة بخطوة.",
                "Good start! You know key basics, and we'll build full conversations together."
            )
            else -> Triple(
                "A1",
                "أهلاً بك في بداية الرحلة! سنبدأ من الصفر بأسلوب ممتع ومبسط حتى تتحدث بثقة.",
                "Welcome aboard! We'll start from scratch at a comfortable, clear pace."
            )
        }

        return PlacementEvaluationResult(
            correctCount = correctCount,
            totalQuestions = questions.size,
            weightedScorePercent = scorePercent,
            assignedLevel = level,
            feedbackAr = feedbackAr,
            feedbackEn = feedbackEn
        )
    }
}
