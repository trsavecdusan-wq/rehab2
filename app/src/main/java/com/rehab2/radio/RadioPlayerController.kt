package com.rehab2.radio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class RadioPlayerController(
    context: Context,
    private val onStatusChanged: (String) -> Unit
) {
    private var volumeBeforeDucking: Float? = null

    private val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
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
                if (isPlaying) {
                    onStatusChanged("Postaja deluje")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onStatusChanged("Napaka pri predvajanju")
            }
        })
    }

    init {
        RadioDuckingCoordinator.register(this)
    }

    fun play(url: String) {
        onStatusChanged("Nalagam...")
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
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
    }

    fun restoreDucking() {
        val previousVolume = volumeBeforeDucking ?: return
        player.volume = previousVolume.coerceIn(0f, 1f)
        volumeBeforeDucking = null
    }
}
