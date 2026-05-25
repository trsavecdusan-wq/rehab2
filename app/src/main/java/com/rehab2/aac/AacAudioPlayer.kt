package com.rehab2.aac

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.Locale

class AacAudioPlayer(private val context: Context) : TextToSpeech.OnInitListener {
    private val speechCache = AacSpeechCache(context)
    private val speechCoordinator = AacSpeechCoordinator(speechCache)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var mediaPlayer: MediaPlayer? = null
    private var textToSpeech: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false
    private var isTtsFailed = false
    private var pendingTextToSpeak: String? = null
    private var pendingLanguageCode: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isTtsFailed = true
            isTtsReady = false
            return
        }

        isTtsReady = configureTtsLanguage(AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
        isTtsFailed = !isTtsReady
        if (isTtsReady) {
            pendingTextToSpeak?.let { text ->
                configureTtsLanguage(pendingLanguageCode)
                pendingTextToSpeak = null
                speak(text, pendingLanguageCode)
            }
        }
    }

    fun playOrSpeak(item: AacItem) {
        playOrSpeak(item, AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
    }

    fun playOrSpeak(item: AacItem, languageCode: String) {
        stopPlayback()
        val fallbackText = resolveTileSpeechText(item, languageCode)

        val generatedAudioFile = speechCoordinator.getOrGenerateSpeechFile(
            text = fallbackText,
            languageCode = languageCode
        )
        if (playAudioFileIfAvailable(generatedAudioFile)) {
            Log.d(TAG, "AUDIO GENERATED: ${item.id}")
            return
        }

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

        speakText(fallbackText, languageCode)
    }

    fun speakText(text: String) {
        speakText(text, AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
    }

    fun speakText(text: String, languageCode: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return
        }

        stopPlayback()
        val generatedAudioFile = speechCoordinator.getOrGenerateSpeechFile(
            text = trimmed,
            languageCode = languageCode
        )
        if (playAudioFileIfAvailable(generatedAudioFile)) {
            Log.d(TAG, "AUDIO GENERATED")
            return
        }

        if (isTtsReady) {
            configureTtsLanguage(languageCode)
        }
        speak(trimmed, languageCode)
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
                if (duration in 1 until MIN_AUDIO_DURATION_MS) {
                    release()
                    mediaPlayer = null
                    return false
                }
                start()
            }
            true
        } catch (_: Exception) {
            releaseMediaPlayer()
            false
        }
    }

    private fun configureTtsLanguage(languageCode: String): Boolean {
        val tts = textToSpeech ?: return false
        val normalizedLanguage = AacLanguageResolver.normalize(languageCode)
        val selectedResult = tts.setLanguage(Locale.forLanguageTag(normalizedLanguage))
        Log.d(TAG, "TTS language selected=$normalizedLanguage result=$selectedResult")
        if (isLanguageUsable(selectedResult)) {
            return true
        }

        val slovenianResult = tts.setLanguage(Locale("sl"))
        Log.d(TAG, "TTS language sl result=$slovenianResult")
        if (isLanguageUsable(slovenianResult)) {
            return true
        }

        val defaultResult = tts.setLanguage(Locale.getDefault())
        Log.d(TAG, "TTS language default=${Locale.getDefault()} result=$defaultResult")
        if (isLanguageUsable(defaultResult)) {
            return true
        }

        return true
    }

    private fun isLanguageUsable(result: Int): Boolean {
        return result != TextToSpeech.ERROR &&
            result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun speak(text: String, languageCode: String) {
        if (!isTtsReady && !isTtsFailed) {
            pendingTextToSpeak = text
            pendingLanguageCode = AacLanguageResolver.normalize(languageCode)
            Toast.makeText(context, "TTS PRIPRAVLJAM: ${text}", Toast.LENGTH_SHORT).show()
            return
        }

        if (isTtsFailed) {
            Toast.makeText(context, "GOVOR NI NA VOLJO", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(context, "TTS: ${text}", Toast.LENGTH_SHORT).show()
        requestMusicAudioFocus()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC.toString())
        }
        val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "aac_$text")
        when (result) {
            TextToSpeech.SUCCESS -> {
                Log.d(TAG, "TTS speak OK: $text")
            }
            TextToSpeech.ERROR -> {
                Log.e(TAG, "TTS speak ERROR: $text")
                Toast.makeText(context, "TTS speak ERROR", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.e(TAG, "TTS speak unavailable: $text")
                Toast.makeText(context, "TTS speak ERROR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestMusicAudioFocus() {
        audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
    }

    private fun resolveTileSpeechText(item: AacItem, languageCode: String): String {
        val resolvedText = AacLocalizedTextResolver.resolveSpeakText(item, languageCode)
        if (AacLanguageResolver.normalize(languageCode) != AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return resolvedText
        }

        return when (resolvedText.trim().uppercase(Locale.ROOT)) {
            "VODA" -> "Želim piti vodo."
            "MRZLA VODA" -> "Želim piti mrzlo vodo."
            "TOPLA VODA" -> "Želim piti toplo vodo."
            "VODA Z MEHURČKI" -> "Želim piti vodo z mehurčki."
            "VODA BREZ MEHURČKOV" -> "Želim piti vodo brez mehurčkov."
            "ČAJ" -> "Želim piti čaj."
            "SOK" -> "Želim piti sok."
            "KAVA" -> "Želim piti kavo."
            "MLEKO" -> "Želim piti mleko."
            "LIMONADA" -> "Želim piti limonado."
            else -> resolvedText
        }
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

    private companion object {
        const val TAG = "AacAudioPlayer"
        const val MIN_AUDIO_DURATION_MS = 700
    }
}
