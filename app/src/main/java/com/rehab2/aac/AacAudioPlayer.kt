package com.rehab2.aac

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.io.File
import java.util.Locale

class AacAudioPlayer(private val context: Context) : TextToSpeech.OnInitListener {
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false
    private var isTtsFailed = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isTtsFailed = true
            isTtsReady = false
            return
        }

        val result = textToSpeech?.setLanguage(Locale("sl")) ?: TextToSpeech.ERROR
        isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        isTtsFailed = !isTtsReady
    }

    fun playOrSpeak(item: AacItem) {
        stopPlayback()

        val audioFile = item.audioSl.takeIf { it.isNotBlank() }?.let { File(it) }
        if (audioFile != null && audioFile.exists() && audioFile.isFile) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(audioFile.absolutePath)
                    setOnCompletionListener {
                        releaseMediaPlayer()
                    }
                    prepare()
                    start()
                }
                return
            } catch (_: Exception) {
                releaseMediaPlayer()
            }
        }

        speak(item.labelSl)
    }

    private fun speak(text: String) {
        if (!isTtsReady || isTtsFailed) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            return
        }

        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "aac_$text")
    }

    fun release() {
        stopPlayback()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
    }

    private fun stopPlayback() {
        mediaPlayer?.stopSafely()
        releaseMediaPlayer()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun MediaPlayer.stopSafely() {
        try {
            if (isPlaying) {
                stop()
            }
        } catch (_: IllegalStateException) {
        }
    }
}
