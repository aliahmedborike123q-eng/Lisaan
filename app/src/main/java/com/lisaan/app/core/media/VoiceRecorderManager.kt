package com.lisaan.app.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartMs: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    fun startRecording(): File? {
        stopRecording()
        val outputFile = File(context.cacheDir, "community_voice_${System.currentTimeMillis()}.m4a")
        currentOutputFile = outputFile

        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recordingStartMs = System.currentTimeMillis()
            _isRecording.value = true
            outputFile
        } catch (e: Exception) {
            _isRecording.value = false
            null
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        val durationSec = if (recordingStartMs > 0L) {
            ((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt().coerceAtLeast(1)
        } else 0

        try {
            recorder?.stop()
        } catch (_: Exception) {
        } finally {
            recorder?.release()
            recorder = null
            _isRecording.value = false
            recordingStartMs = 0L
        }
        return currentOutputFile to durationSec
    }
}
