package com.lisaan.app.domain.usecase.bridge

import javax.inject.Inject

data class PhoneticContrastDrill(
    val id: String,
    val titleAr: String,
    val targetPair: String, // e.g., "P vs B"
    val minimalPairs: List<Pair<String, String>>,
    val articulationTipAr: String
)

data class MotherTongueDiagnosticFeedback(
    val detectedPatternCode: String,
    val titleAr: String,
    val explanationAr: String,
    val articulationCoachTipAr: String,
    val recommendedMinimalPairs: List<Pair<String, String>>
)

/**
 * محرك "جسر الضاد" الحصري في تطبيق لِسان:
 * يكتشف الأخطاء الصوتية والنحوية الناتجة عن التداخل اللغوي بين العربية والإنجليزية
 * ويقدم تدريباً علاجياً مخصصاً (Minimal Pairs & Grammar Bridge).
 */
class MotherTongueBridgeAnalyzer @Inject constructor() {

    fun analyzeSpokenOrWrittenAttempt(
        expectedEnglish: String,
        learnerAttempt: String
    ): MotherTongueDiagnosticFeedback? {
        val expLower = expectedEnglish.lowercase().trim()
        val attLower = learnerAttempt.lowercase().trim()

        if (expLower == attLower || attLower.isBlank()) return null

        // 1. فحص إبدال صوت /p/ بصوت /b/ (مثل Park -> Bark أو People -> Beoble)
        if (expLower.contains("p") && attLower.replace("b", "p") == expLower) {
            return MotherTongueDiagnosticFeedback(
                detectedPatternCode = "PHONEME_P_VS_B",
                titleAr = "تنبيه صوتي: الفرق بين /P/ المهموسة و /B/ المجهورة",
                explanationAr = "لاحظ «نبيه» أنك نطقت حرف (P) قريباً من (B). في العربية لا يوجد صوت P المهموس، لذا تميل أذننا لنطقه كالباء.",
                articulationCoachTipAr = "💡 حيلة عملية: ضع ورقة صغيرة أمام فمك؛ عند نطق (P) يجب أن تخرج دفعة هواء تحرّك الورقة دون اهتزاز في الحنجرة!",
                recommendedMinimalPairs = listOf(
                    "Park (حديقة)" to "Bark (نباح)",
                    "Pen (قلم)" to "Ben (اسم)",
                    "Pay (يدفع)" to "Bay (خليج)"
                )
            )
        }

        // 2. فحص إبدال صوت /v/ بصوت /f/ (مثل Very -> Ferry أو Have -> Haf)
        if (expLower.contains("v") && attLower.replace("f", "v") == expLower) {
            return MotherTongueDiagnosticFeedback(
                detectedPatternCode = "PHONEME_V_VS_F",
                titleAr = "تنبيه صوتي: تفعيل الحبال الصوتية في حرف /V/",
                explanationAr = "نطقتَ صوت (V) قريباً من الفاء (F). المخرج واحد (ملامسة الأسنان العلوية للشفة السفلية)، لكن (V) يتطلب اهتزاز الحنجرة.",
                articulationCoachTipAr = "💡 ضع أصابعك على عنقك أثناء نطق (Vvvv) وتأكد من شعورك باهتزاز واضح كطنين النحلة!",
                recommendedMinimalPairs = listOf(
                    "Very (جداً)" to "Ferry (عبّارة)",
                    "Van (شاحنة)" to "Fan (مروحة)",
                    "Safe (آمن)" to "Save (يحفظ)"
                )
            )
        }

        // 3. فحص حذف فعل الكينونة (Verb to Be: am / is / are) بتأثير الجملة الاسمية العربية
        val missingBeRegex = Regex("\\b(am|is|are)\\b")
        if (missingBeRegex.containsMatchIn(expLower) && !missingBeRegex.containsMatchIn(attLower)) {
            return MotherTongueDiagnosticFeedback(
                detectedPatternCode = "GRAMMAR_MISSING_COPULA",
                titleAr = "جسر القواعد: الجملة الإنجليزية لا تستغني عن (am / is / are)",
                explanationAr = "في لغتنا العربية الجميلة نقول «أنا جاهز» أو «الطقس جميل» بدون فعل، لكن الإنجليزية تشترط وجود فعل ربط (am / is / are) في كل جملة.",
                articulationCoachTipAr = "💡 تذكّر المعادلة الذهبية: الفاعل + (am/is/are) + الصفة أو الاسم.",
                recommendedMinimalPairs = listOf(
                    "I am ready" to "أنا جاهز",
                    "She is a doctor" to "هي طبيبة",
                    "They are nearby" to "هم قريبون"
                )
            )
        }

        return null
    }

    fun getAllArabicBridgeDrills(): List<PhoneticContrastDrill> = listOf(
        PhoneticContrastDrill(
            id = "drill_p_b",
            titleAr = "تحدي الهمس والجهر: P مقابل B",
            targetPair = "/p/ vs /b/",
            minimalPairs = listOf("Pack" to "Back", "Peach" to "Beach", "Pride" to "Bride"),
            articulationTipAr = "أخرج نفخة هواء خفيفة من الشفتين مع P بدون اهتزاز الحنجرة."
        ),
        PhoneticContrastDrill(
            id = "drill_v_f",
            titleAr = "تحدي الاهتزاز: V مقابل F",
            targetPair = "/v/ vs /f/",
            minimalPairs = listOf("View" to "Few", "Vine" to "Fine", "Leave" to "Leaf"),
            articulationTipAr = "اجعل شفتك السفلية تهتز مع الأسنان العلوية عند نطق V."
        ),
        PhoneticContrastDrill(
            id = "drill_ch_sh",
            titleAr = "تحدي المزج الصوتي: CH مقابل SH",
            targetPair = "/tʃ/ vs /ʃ/",
            minimalPairs = listOf("Chair" to "Share", "Choose" to "Shoes", "Cheap" to "Sheep"),
            articulationTipAr = "ابدأ صوت CH بحرف تاء خفيف قبل الشين (تْش)."
        )
    )
}
