package com.lisaan.app.core.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class PronunciationResult(
    val recognizedTranscript: String = "",
    val accuracyPercent: Int = 0,
    val isPassed: Boolean = false, // >= 70%
    val wordFeedback: List<Pair<String, Boolean>> = emptyList()
)

@Singleton
class LisaanSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastResult = MutableStateFlow<PronunciationResult?>(null)
    val lastResult: StateFlow<PronunciationResult?> = _lastResult.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private var currentExpectedTarget: String = ""

    fun startListening(targetPhrase: String, languageTag: String = "en-US") {
        currentExpectedTarget = targetPhrase
        _lastResult.value = null
        _errorState.value = null

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorState.value = "خدمة التعرف على الصوت غير متوفرة على هذا الجهاز"
            return
        }

        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }
                override fun onError(error: Int) {
                    _isListening.value = false
                    _errorState.value = "تعذر التقاط الصوت بوضوح، حاول التحدث بالقرب من الميكروفون"
                }
                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestTranscript = matches?.firstOrNull().orEmpty()
                    val evaluation = evaluatePronunciationAccuracy(
                        expected = currentExpectedTarget,
                        spoken = bestTranscript
                    )
                    _lastResult.value = evaluation
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        _isListening.value = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        fun evaluatePronunciationAccuracy(expected: String, spoken: String): PronunciationResult {
            val cleanExpected = normalizeText(expected)
            val cleanSpoken = normalizeText(spoken)

            if (cleanSpoken.isBlank()) {
                return PronunciationResult(spoken, 0, false, emptyList())
            }

            val distance = levenshteinDistance(cleanExpected, cleanSpoken)
            val maxLen = max(cleanExpected.length, cleanSpoken.length).coerceAtLeast(1)
            val similarity = ((1.0 - (distance.toDouble() / maxLen.toDouble())) * 100.0)
                .toInt()
                .coerceIn(0, 100)

            val spokenWordsSet = cleanSpoken.split(" ").filter { it.isNotBlank() }.toSet()
            val wordByWord = expected.trim().split(Regex("\\s+")).map { rawWord ->
                val normalizedWord = normalizeText(rawWord)
                val matched = spokenWordsSet.any { spokenW ->
                    spokenW == normalizedWord || levenshteinDistance(spokenW, normalizedWord) <= 1
                }
                rawWord to matched
            }

            return PronunciationResult(
                recognizedTranscript = spoken,
                accuracyPercent = similarity,
                isPassed = similarity >= 70,
                wordFeedback = wordByWord
            )
        }

        fun normalizeText(input: String): String {
            return input.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9\\u0600-\\u06FF\\s]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
            val lhsLen = lhs.length
            val rhsLen = rhs.length
            var cost = IntArray(lhsLen + 1) { it }
            var newCost = IntArray(lhsLen + 1) { 0 }

            for (i in 1..rhsLen) {
                newCost[0] = i
                for (j in 1..lhsLen) {
                    val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                    val costReplace = cost[j - 1] + match
                    val costInsert = cost[j] + 1
                    val costDelete = newCost[j - 1] + 1
                    newCost[j] = min(min(costInsert, costDelete), costReplace)
                }
                val swap = cost
                cost = newCost
                newCost = swap
            }
            return cost[lhsLen]
        }
    }
}
