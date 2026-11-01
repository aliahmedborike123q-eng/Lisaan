package com.lisaan.app.core.media

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanTtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var defaultSpeechRate: Float = 1.0f

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            _isReady.value = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun setPreferredSpeechRate(rate: Float) {
        defaultSpeechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(defaultSpeechRate)
    }

    fun speak(
        text: String,
        slowMode: Boolean = false,
        locale: Locale = Locale.US
    ) {
        if (!_isReady.value || text.isBlank()) return
        tts?.language = locale
        val effectiveRate = if (slowMode) 0.55f else defaultSpeechRate
        tts?.setSpeechRate(effectiveRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
