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

    fun play(url: String) {
        onStatusChanged("Nalagam...")
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
        player.stop()
    }

    fun release() {
        player.release()
    }

    fun isPlaying(): Boolean {
        return player.isPlaying
    }
}