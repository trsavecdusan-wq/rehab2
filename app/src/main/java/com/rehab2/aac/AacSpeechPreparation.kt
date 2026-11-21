package com.rehab2.aac

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** User-initiated bounded warmup, never playback. A second start cannot duplicate a batch. */
object AacSpeechPreparation {
    private val running = AtomicBoolean(false)
    @Volatile private var cancelled = false
    @Volatile var status = "Govor še ni pripravljen."
        private set
    fun cancel() { cancelled = true; status = "Ustavljanje po trenutni zahtevi …" }
    fun start(context: Context, language: String): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelled = false
        val app = context.applicationContext
        val items = AacHomeLayout.ordered(AacHomeLayout.catalog(app, AacEditorStorage.loadItems(app)))
        val patientId = AacPatientImages.store(app).currentPatientId()
        val patientVoice = AacPatientVoiceSettings.voice(app, false)
        val guideVoice = AacPatientVoiceSettings.voice(app, true)
        val speed = AacSpeechApiConfig.read(app).normalizedSpeed()
        val phrases = items.flatMap { item ->
            val patient = AacLocalizedTextResolver.resolveSpeakText(item, language)
            val guide = if (language == "sl") AacGuidedPromptEngine.questionFor(item).ifBlank { AacLocalizedTextResolver.resolveQuestion(item, language).orEmpty() } else AacLocalizedTextResolver.resolveQuestion(item, language)
            listOfNotNull(patient.takeIf { it.isNotBlank() }?.let { it to patientVoice },
                guide?.takeIf { it.isNotBlank() }?.let { it to guideVoice })
        }.distinct().take(50)
        Thread {
            try {
                val cache = AacSpeechCache(app)
                val coordinator = AacSpeechCoordinator(cache, OpenAiAacSpeechApiClient(app))
                var ready = 0
                for ((index, phrase) in phrases.withIndex()) {
                    if (cancelled || AacPatientImages.store(app).currentPatientId() != patientId) break
                    val text = AacPatientVoiceSettings.text(app, phrase.first, language)
                    status = "Priprava ${index + 1}/${phrases.size}; pripravljeno $ready"
                    coordinator.getOrGenerateSpeechFile(text, language, phrase.second, speed)
                    if (cache.getGeneratedSpeechFile(text, language, phrase.second, speed) != null) ready++
                }
                status = "${if (cancelled) "Prekinjeno" else "Končano"}: $ready/${phrases.size} novih ali že shranjenih posnetkov."
            } catch (_: Exception) { status = "Priprava ni uspela. Preverite nastavitve govora in povezavo." }
            finally { running.set(false) }
        }.start()
        return true
    }
}
