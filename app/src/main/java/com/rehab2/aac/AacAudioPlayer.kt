package com.rehab2.aac

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.io.File
import java.util.Locale

class AacAudioPlayer(private val context: Context) : TextToSpeech.OnInitListener {
    private val speechCache = AacSpeechCache(context)
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false
    private var isTtsFailed = false
    private var pendingTextToSpeak: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isTtsFailed = true
            isTtsReady = false
            return
        }

        val result = textToSpeech?.setLanguage(Locale("sl")) ?: TextToSpeech.ERROR
        isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        isTtsFailed = !isTtsReady
        if (isTtsReady) {
            pendingTextToSpeak?.let { text ->
                pendingTextToSpeak = null
                speak(text)
            }
        }
    }

    fun playOrSpeak(item: AacItem) {
        stopPlayback()

        val directAudioFile = item.audioSl.takeIf { it.isNotBlank() }?.let { File(it) }
        if (playAudioFileIfAvailable(directAudioFile)) {
            Toast.makeText(context, "AUDIO DIRECT: ${item.id}", Toast.LENGTH_SHORT).show()
            return
        }

        val cachedAudioFile = speechCache.getExistingCacheFile(item.id)
        if (playAudioFileIfAvailable(cachedAudioFile)) {
            Toast.makeText(context, "AUDIO CACHE: ${item.id}", Toast.LENGTH_SHORT).show()
            return
        }

        val fallbackText = item.speakTextSl?.trim()?.takeIf { it.isNotEmpty() } ?: item.labelSl
        speak(fallbackText)
    }

    fun speakText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return
        }

        stopPlayback()
        speak(trimmed)
    }

    private fun playAudioFileIfAvailable(audioFile: File?): Boolean {
        if (audioFile == null || !audioFile.exists() || !audioFile.isFile) {
            return false
        }

        return try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    releaseMediaPlayer()
                }
                prepare()
                start()
            }
            true
        } catch (_: Exception) {
            releaseMediaPlayer()
            false
        }
    }

    private fun speak(text: String) {
        if (!isTtsReady && !isTtsFailed) {
            pendingTextToSpeak = text
            Toast.makeText(context, "TTS PRIPRAVLJAM: ${text}", Toast.LENGTH_SHORT).show()
            return
        }

        if (isTtsFailed) {
            Toast.makeText(context, "GOVOR NI NA VOLJO", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "TTS: ${text}", Toast.LENGTH_SHORT).show()
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
        pendingTextToSpeak = null
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
