package com.rehab2.aac

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import com.rehab2.radio.RadioDuckingCoordinator
import java.io.File
import java.util.Locale
import kotlin.math.log10

class AacAudioPlayer(private val context: Context) : TextToSpeech.OnInitListener {
    interface SpeechListener {
        fun onSpeechStarted()
        fun onSpeechCompleted()
        fun onSpeechCancelled()
        fun onSpeechError()
    }

    private val speechCache = AacSpeechCache(context)
    private val speechCoordinator = AacSpeechCoordinator(
        speechCache,
        OpenAiAacSpeechApiClient(context),
        voiceIdProvider = { AacSpeechApiConfig.read(context).normalizedVoiceId() },
        speedProvider = { AacSpeechApiConfig.read(context).normalizedSpeed() }
    )
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var mediaPlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var textToSpeech: TextToSpeech? = TextToSpeech(context, this)
    private var isTtsReady = false
    private var isTtsFailed = false
    private var pendingTextToSpeak: String? = null
    private var pendingLanguageCode: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var speechRequestSerial = 0
    @Volatile
    private var isSpeechActive = false
    private var speechListener: SpeechListener? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isTtsFailed = true
            isTtsReady = false
            return
        }

        isTtsReady = configureTtsLanguage(AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
        isTtsFailed = !isTtsReady
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                notifySpeechStarted()
            }

            override fun onDone(utteranceId: String?) {
                notifySpeechCompleted()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                notifySpeechError()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                notifySpeechError()
            }
        })
        if (isTtsReady) {
            pendingTextToSpeak?.let { text ->
                configureTtsLanguage(pendingLanguageCode)
                pendingTextToSpeak = null
                speak(text, pendingLanguageCode)
            }
        }
    }

    fun setSpeechListener(listener: SpeechListener?) {
        speechListener = listener
    }

    fun playOrSpeak(item: AacItem) {
        playOrSpeak(item, AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
    }

    fun playOrSpeak(item: AacItem, languageCode: String) {
        stopPlayback(notifyCancellation = false)
        val requestSerial = nextSpeechRequestSerial()
        val fallbackText = resolveTileSpeechText(item, languageCode)

        playGeneratedSpeechOrFallback(
            text = fallbackText,
            languageCode = languageCode,
            requestSerial = requestSerial,
            generatedLog = "AUDIO GENERATED: ${item.id}"
        ) {
            val directAudioFile = item.audioSl.takeIf { it.isNotBlank() }?.let { File(it) }
            if (playAudioFileIfAvailable(directAudioFile)) {
                Toast.makeText(context, "AUDIO DIRECT: ${item.id}", Toast.LENGTH_SHORT).show()
                return@playGeneratedSpeechOrFallback
            }

            val cachedAudioFile = speechCache.getExistingCacheFile(item.id)
            if (playAudioFileIfAvailable(cachedAudioFile)) {
                Toast.makeText(context, "AUDIO CACHE: ${item.id}", Toast.LENGTH_SHORT).show()
                return@playGeneratedSpeechOrFallback
            }

            speakAndroidFallback(fallbackText, languageCode)
        }
    }

    fun speakText(text: String) {
        speakText(text, AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
    }

    fun speakText(text: String, languageCode: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return
        }

        stopPlayback(notifyCancellation = false)
        val requestSerial = nextSpeechRequestSerial()

        playGeneratedSpeechOrFallback(
            text = trimmed,
            languageCode = languageCode,
            requestSerial = requestSerial,
            generatedLog = "AUDIO GENERATED"
        ) {
            speakAndroidFallback(trimmed, languageCode)
        }
    }

    fun stopCurrentSpeech() {
        pendingTextToSpeak = null
        stopPlayback()
        textToSpeech?.stop()
        nextSpeechRequestSerial()
    }

    fun interruptCurrentSpeechForNewInput() {
        pendingTextToSpeak = null
        stopPlayback(notifyCancellation = false, restoreDuckingWhenSilent = true)
        textToSpeech?.stop()
        nextSpeechRequestSerial()
    }

    private fun playGeneratedSpeechOrFallback(
        text: String,
        languageCode: String,
        requestSerial: Int,
        generatedLog: String,
        fallback: () -> Unit
    ) {
        showSpeechApiConfigIssueIfNeeded()
        Thread {
            val generatedAudioFile = speechCoordinator.getOrGenerateSpeechFile(
                text = text,
                languageCode = languageCode
            )

            mainHandler.post {
                if (requestSerial != speechRequestSerial) {
                    return@post
                }

                if (playAudioFileIfAvailable(generatedAudioFile)) {
                    Log.d(TAG, generatedLog)
                    return@post
                }

                fallback()
            }
        }.start()
    }

    private fun showSpeechApiConfigIssueIfNeeded() {
        val configFile = AacSpeechApiConfig.getConfigFile(context)
        if (!configFile.exists() || !configFile.isFile || configFile.length() <= 0L) {
            AacSpeechApiConfig.ensureExampleConfigFile(context)
            Log.d(TAG, AacSpeechApiConfig.readDiagnostics(context))
            Toast.makeText(context, "Speech API config manjka", Toast.LENGTH_SHORT).show()
            return
        }

        val config = AacSpeechApiConfig.read(context)
        Log.d(TAG, AacSpeechApiConfig.readDiagnostics(context))
        if (config.enabled && config.apiKey.isBlank()) {
            Toast.makeText(context, "Speech API key manjka", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakAndroidFallback(text: String, languageCode: String) {
        if (isTtsReady) {
            configureTtsLanguage(languageCode)
        }
        speak(text, languageCode)
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
                    notifySpeechCompleted()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "SPEECH_ERROR media what=$what extra=$extra")
                    releaseMediaPlayer()
                    notifySpeechError()
                    true
                }
                prepare()
                if (duration in 1 until MIN_AUDIO_DURATION_MS) {
                    release()
                    mediaPlayer = null
                    return false
                }
                applySpeechLoudness(this)
                start()
                notifySpeechStarted()
            }
            true
        } catch (_: Exception) {
            releaseMediaPlayer()
            notifySpeechError()
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
        val utteranceId = "aac_${speechRequestSerial}"
        val result = textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        when (result) {
            TextToSpeech.SUCCESS -> {
                Log.d(TAG, "TTS speak OK: $text")
            }
            TextToSpeech.ERROR -> {
                Log.e(TAG, "TTS speak ERROR: $text")
                notifySpeechError()
                Toast.makeText(context, "TTS speak ERROR", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.e(TAG, "TTS speak unavailable: $text")
                notifySpeechError()
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
        textToSpeech?.setOnUtteranceProgressListener(null)
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
        speechListener = null
    }

    private fun stopPlayback(
        notifyCancellation: Boolean = true,
        restoreDuckingWhenSilent: Boolean = false
    ) {
        val hadActiveSpeech = mediaPlayer != null || isSpeechActive
        val shouldNotifyCancelled = notifyCancellation && hadActiveSpeech
        mediaPlayer?.stopSafely()
        releaseMediaPlayer()
        if (shouldNotifyCancelled) {
            notifySpeechCancelled()
        } else if (restoreDuckingWhenSilent && hadActiveSpeech) {
            isSpeechActive = false
            runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        }
    }

    private fun nextSpeechRequestSerial(): Int {
        speechRequestSerial += 1
        return speechRequestSerial
    }

    private fun releaseMediaPlayer() {
        releaseLoudnessEnhancer()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun applySpeechLoudness(player: MediaPlayer) {
        releaseLoudnessEnhancer()
        val gain = AacSpeechLoudnessSettings.load(context).gain
        if (gain <= 1.0) {
            Log.d(TAG, "SPEECH_LOUDNESS disabled gain=$gain")
            return
        }

        runCatching {
            val gainMb = (20.0 * log10(gain) * 100.0).toInt().coerceIn(0, MAX_LOUDNESS_GAIN_MB)
            if (gainMb <= 0) return
            loudnessEnhancer = LoudnessEnhancer(player.audioSessionId).apply {
                setTargetGain(gainMb)
                enabled = true
            }
            Log.d(TAG, "SPEECH_LOUDNESS applied gain=$gain targetGainMb=$gainMb")
        }.onFailure { error ->
            Log.w(TAG, "SPEECH_LOUDNESS enhancer unavailable: ${error.javaClass.simpleName}")
            releaseLoudnessEnhancer()
        }
    }

    private fun releaseLoudnessEnhancer() {
        runCatching {
            loudnessEnhancer?.enabled = false
            loudnessEnhancer?.release()
        }
        loudnessEnhancer = null
    }

    private fun notifySpeechStarted() {
        isSpeechActive = true
        runCatching { RadioDuckingCoordinator.duckForSpeech(context) }
        Log.d(TAG, "SPEECH_STARTED")
        postSpeechCallback { it.onSpeechStarted() }
    }

    private fun notifySpeechCompleted() {
        if (!isSpeechActive) return
        isSpeechActive = false
        runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        Log.d(TAG, "SPEECH_COMPLETED")
        postSpeechCallback { it.onSpeechCompleted() }
    }

    private fun notifySpeechCancelled() {
        if (!isSpeechActive) return
        isSpeechActive = false
        runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        Log.d(TAG, "SPEECH_CANCELLED")
        postSpeechCallback { it.onSpeechCancelled() }
    }

    private fun notifySpeechError() {
        isSpeechActive = false
        runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        Log.d(TAG, "SPEECH_ERROR")
        postSpeechCallback { it.onSpeechError() }
    }

    private fun postSpeechCallback(callback: (SpeechListener) -> Unit) {
        mainHandler.post {
            speechListener?.let(callback)
        }
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
        const val MAX_LOUDNESS_GAIN_MB = 400
    }
}
