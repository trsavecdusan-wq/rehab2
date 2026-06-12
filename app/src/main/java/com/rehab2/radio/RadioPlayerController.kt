package com.rehab2.radio

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class RadioPlayerController(
    context: Context,
    private val onStatusChanged: (String) -> Unit,
    private val onDebug: (String) -> Unit = {}
) {
    private var volumeBeforeDucking: Float? = null

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                trace("player state=$playbackState playWhenReady=$playWhenReady isPlaying=$isPlaying")
                when (playbackState) {
                    Player.STATE_BUFFERING -> onStatusChanged("Nalagam...")
                    Player.STATE_READY -> {
                        if (isPlaying) {
                            onStatusChanged("Postaja deluje")
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trace("isPlaying=$isPlaying")
                if (isPlaying) {
                    onStatusChanged("Postaja deluje")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                trace("player error=${error.errorCodeName}: ${error.message}")
                onStatusChanged("Napaka pri predvajanju")
            }
        })
    }

    init {
        RadioDuckingCoordinator.register(this)
    }

    fun play(url: String) {
        trace("play request url=${url.take(120)}")
        onStatusChanged("Nalagam...")
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        trace("playWhenReady=true state=${player.playbackState}")
    }

    fun stop() {
        trace("stop request isPlaying=${player.isPlaying}")
        restoreDucking()
        player.stop()
    }

    fun release() {
        RadioDuckingCoordinator.unregister(this)
        restoreDucking()
        player.release()
    }

    fun isPlaying(): Boolean {
        return player.isPlaying
    }

    fun applyDucking(duckingPercent: Int) {
        if (!player.isPlaying) return
        val normalizedPercent = duckingPercent.coerceIn(0, 100)
        if (normalizedPercent <= 0) return
        if (volumeBeforeDucking == null) {
            volumeBeforeDucking = player.volume
        }
        val duckedVolume = (volumeBeforeDucking ?: player.volume) * (100 - normalizedPercent) / 100f
        player.volume = duckedVolume.coerceIn(0f, 1f)
        trace("ducking applied percent=$normalizedPercent volume=${player.volume}")
    }

    fun restoreDucking() {
        val previousVolume = volumeBeforeDucking ?: return
        player.volume = previousVolume.coerceIn(0f, 1f)
        volumeBeforeDucking = null
        trace("ducking restored volume=${player.volume}")
    }

    private fun trace(message: String) {
        Log.d(TAG, message)
        onDebug("RADIO $message")
    }

    private companion object {
        const val TAG = "RadioPlayerController"
    }
}
