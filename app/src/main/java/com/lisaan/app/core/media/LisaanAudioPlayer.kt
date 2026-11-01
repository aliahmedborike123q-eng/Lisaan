package com.lisaan.app.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LisaanAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsFallback: LisaanTtsManager
) {
    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
            exoPlayer = player
        }
    }

    /**
     * يشغل الملف المحلي أولاً (إن تم تحميل الوحدة للأوفلاين)، أو الرابط السحابي،
     * وفي حال عدم توفر ملف صوتي مسجل يستخدم محرك TTS الفوري كبديل سلس.
     */
    fun playAudioOrFallbackTts(
        localFilePath: String?,
        remoteAudioUrl: String?,
        fallbackTextForTts: String,
        playbackSpeed: Float = 1.0f,
        slowMode: Boolean = false
    ) {
        val localFile = localFilePath?.let { File(it) }
        val resolvedUri: Uri? = when {
            localFile != null && localFile.exists() -> Uri.fromFile(localFile)
            !remoteAudioUrl.isNullOrBlank() && remoteAudioUrl.startsWith("http") -> Uri.parse(remoteAudioUrl)
            else -> null
        }

        if (resolvedUri != null) {
            try {
                val player = getOrCreatePlayer()
                player.stop()
                player.setMediaItem(MediaItem.fromUri(resolvedUri))
                val effectiveSpeed = if (slowMode) 0.65f else playbackSpeed
                player.playbackParameters = PlaybackParameters(effectiveSpeed)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                ttsFallback.speak(fallbackTextForTts, slowMode = slowMode)
            }
        } else {
            ttsFallback.speak(fallbackTextForTts, slowMode = slowMode)
        }
    }

    fun stop() {
        exoPlayer?.stop()
        ttsFallback.stop()
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
    }
}
