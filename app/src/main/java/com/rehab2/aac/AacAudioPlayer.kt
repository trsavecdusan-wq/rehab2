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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10

class AacAudioPlayer(
    private val context: Context,
    private val audioDebug: (String) -> Unit = {}
) : TextToSpeech.OnInitListener {
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
    @Volatile
    private var hasMusicAudioFocus = false
    private var speechListener: SpeechListener? = null

    override fun onInit(status: Int) {
        traceAudio("TTS onInit status=$status")
        if (status != TextToSpeech.SUCCESS) {
            isTtsFailed = true
            isTtsReady = false
            traceAudio("TTS unavailable: init failed")
            return
        }

        isTtsReady = configureTtsLanguage(AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
        isTtsFailed = !isTtsReady
        traceAudio("TTS available=$isTtsReady")
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
        traceAudio("playOrSpeak itemId=${item.id} label=${item.labelSl}")
        stopPlayback(notifyCancellation = false)
        val requestSerial = nextSpeechRequestSerial()
        val fallbackText = resolveTileSpeechText(item, languageCode)
        traceAudio("playOrSpeak resolvedText='${fallbackText.take(80)}'")

        playGeneratedSpeechOrFallback(
            text = fallbackText,
            languageCode = languageCode,
            requestSerial = requestSerial,
            generatedLog = "AUDIO GENERATED: ${item.id}"
        ) {
            val directAudioFile = item.audioSl.takeIf { it.isNotBlank() }?.let { File(it) }
            if (playAudioFileIfAvailable(directAudioFile)) {
                traceAudio("selected path=direct audio file=${directAudioFile?.absolutePath}")
                Toast.makeText(context, "AUDIO DIRECT: ${item.id}", Toast.LENGTH_SHORT).show()
                return@playGeneratedSpeechOrFallback
            }

            val cachedAudioFile = speechCache.getExistingCacheFile(item.id)
            if (playAudioFileIfAvailable(cachedAudioFile)) {
                traceAudio("selected path=cached audio file=${cachedAudioFile?.absolutePath}")
                Toast.makeText(context, "AUDIO CACHE: ${item.id}", Toast.LENGTH_SHORT).show()
                return@playGeneratedSpeechOrFallback
            }

            traceAudio("selected path=Android TTS fallback itemId=${item.id}")
            speakAndroidFallback(fallbackText, languageCode)
        }
    }

    fun speakText(text: String) {
        speakText(text, AacLanguageResolver.DEFAULT_LANGUAGE_CODE)
    }

    fun speakText(text: String, languageCode: String) {
        val trimmed = text.trim()
        traceAudio("speakText called text='${trimmed.take(80)}' language=$languageCode")
        if (trimmed.isEmpty()) {
            traceAudio("speakText ignored: blank text")
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
            traceAudio("selected path=Android TTS fallback")
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
        traceAudio("speech generation requested serial=$requestSerial")
        val handled = AtomicBoolean(false)
        mainHandler.postDelayed({
            if (requestSerial == speechRequestSerial && handled.compareAndSet(false, true)) {
                traceAudio("speech generation timeout -> Android TTS fallback")
                fallback()
            }
        }, SPEECH_GENERATION_TIMEOUT_MS)
        Thread {
            val generatedAudioFile = speechCoordinator.getOrGenerateSpeechFile(
                text = text,
                languageCode = languageCode
            )

            mainHandler.post {
                if (requestSerial != speechRequestSerial) {
                    traceAudio("speech generation ignored: stale serial=$requestSerial current=$speechRequestSerial")
                    return@post
                }
                if (!handled.compareAndSet(false, true)) {
                    traceAudio("speech generation ignored: fallback already used")
                    return@post
                }

                if (playAudioFileIfAvailable(generatedAudioFile)) {
                    Log.d(TAG, generatedLog)
                    traceAudio("selected path=generated audio file=${generatedAudioFile?.absolutePath}")
                    return@post
                }

                traceAudio("generated audio unavailable -> fallback")
                fallback()
            }
        }.start()
    }

    private fun showSpeechApiConfigIssueIfNeeded() {
        val configFile = AacSpeechApiConfig.getConfigFile(context)
        if (!configFile.exists() || !configFile.isFile || configFile.length() <= 0L) {
            AacSpeechApiConfig.ensureExampleConfigFile(context)
            Log.d(TAG, AacSpeechApiConfig.readDiagnostics(context))
            traceAudio("Speech API config missing")
            Toast.makeText(context, "Speech API config manjka", Toast.LENGTH_SHORT).show()
            return
        }

        val config = AacSpeechApiConfig.read(context)
        Log.d(TAG, AacSpeechApiConfig.readDiagnostics(context))
        if (config.enabled && config.apiKey.isBlank()) {
            traceAudio("Speech API enabled but API key missing")
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
            traceAudio("audio file unavailable=${audioFile?.absolutePath}")
            return false
        }

        return try {
            traceAudio("play audio file=${audioFile.absolutePath}")
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
        } catch (error: Exception) {
            traceAudio("audio file play error=${error.javaClass.simpleName}: ${error.message}")
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
        traceAudio("TTS language selected=$normalizedLanguage result=$selectedResult")
        traceAudio("TTS voice selected=${tts.voice?.name ?: "unknown"} locale=${tts.voice?.locale?.toLanguageTag() ?: "unknown"}")
        if (isLanguageUsable(selectedResult)) {
            return true
        }

        val slovenianResult = tts.setLanguage(Locale("sl"))
        Log.d(TAG, "TTS language sl result=$slovenianResult")
        traceAudio("TTS language sl result=$slovenianResult")
        if (isLanguageUsable(slovenianResult)) {
            return true
        }

        val defaultResult = tts.setLanguage(Locale.getDefault())
        Log.d(TAG, "TTS language default=${Locale.getDefault()} result=$defaultResult")
        traceAudio("TTS language default=${Locale.getDefault()} result=$defaultResult")
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
            traceAudio("TTS not ready; queued text")
            Toast.makeText(context, "TTS PRIPRAVLJAM: ${text}", Toast.LENGTH_SHORT).show()
            return
        }

        if (isTtsFailed) {
            traceAudio("TTS unavailable before speak")
            Toast.makeText(context, "GOVOR NI NA VOLJO", Toast.LENGTH_SHORT).show()
            notifySpeechError()
            return
        }

        traceAudio("TTS speak request text='${text.take(80)}' language=${AacLanguageResolver.normalize(languageCode)} voice=${textToSpeech?.voice?.name ?: "unknown"}")
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
                traceAudio("TTS speak OK")
            }
            TextToSpeech.ERROR -> {
                Log.e(TAG, "TTS speak ERROR: $text")
                traceAudio("TTS speak ERROR")
                notifySpeechError()
                Toast.makeText(context, "TTS speak ERROR", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.e(TAG, "TTS speak unavailable: $text")
                traceAudio("TTS speak unavailable result=$result")
                notifySpeechError()
                Toast.makeText(context, "TTS speak ERROR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestMusicAudioFocus() {
        val result = audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        hasMusicAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        traceAudio("audio focus request result=$result granted=$hasMusicAudioFocus")
    }

    @Suppress("DEPRECATION")
    private fun abandonMusicAudioFocus() {
        if (!hasMusicAudioFocus) return
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        hasMusicAudioFocus = false
        traceAudio("audio focus abandoned")
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
        traceAudio("onSpeechStarted")
        postSpeechCallback { it.onSpeechStarted() }
    }

    private fun notifySpeechCompleted() {
        if (!isSpeechActive) return
        isSpeechActive = false
        abandonMusicAudioFocus()
        runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        Log.d(TAG, "SPEECH_COMPLETED")
        traceAudio("onSpeechCompleted")
        postSpeechCallback { it.onSpeechCompleted() }
    }

    private fun notifySpeechCancelled() {
        if (!isSpeechActive) return
        isSpeechActive = false
        abandonMusicAudioFocus()
        runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        Log.d(TAG, "SPEECH_CANCELLED")
        traceAudio("onSpeechCancelled")
        postSpeechCallback { it.onSpeechCancelled() }
    }

    private fun notifySpeechError() {
        isSpeechActive = false
        abandonMusicAudioFocus()
        runCatching { RadioDuckingCoordinator.restoreAfterSpeech() }
        Log.d(TAG, "SPEECH_ERROR")
        traceAudio("notifySpeechError called")
        postSpeechCallback { it.onSpeechError() }
    }

    private fun traceAudio(message: String) {
        Log.d(TAG, message)
        val event = "AAC $message"
        appendAudioDiagnosticEvent(event)
        audioDebug(event)
    }

    private fun appendAudioDiagnosticEvent(event: String) {
        val prefs = context.getSharedPreferences(PREFS_AUDIO_DIAGNOSTICS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_AUDIO_EVENTS, "[]").orEmpty()
        val sourceArray = runCatching { JSONArray(existing) }.getOrElse { JSONArray() }
        val events = mutableListOf<JSONObject>()
        for (index in 0 until sourceArray.length()) {
            sourceArray.optJSONObject(index)?.let(events::add)
        }
        events += JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("event", event.take(180))
        val targetArray = JSONArray()
        events.takeLast(AUDIO_EVENT_LIMIT).forEach { targetArray.put(it) }
        prefs.edit().putString(KEY_AUDIO_EVENTS, targetArray.toString()).apply()
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
        const val SPEECH_GENERATION_TIMEOUT_MS = 1800L
        const val PREFS_AUDIO_DIAGNOSTICS = "audio_diagnostics"
        const val KEY_AUDIO_EVENTS = "audio_events"
        const val AUDIO_EVENT_LIMIT = 20
    }
}
