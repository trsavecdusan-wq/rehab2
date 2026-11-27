package com.rehab2.radio

import android.content.Context
import com.rehab2.aac.AudioDuckingSettings
import java.util.Collections
import java.util.WeakHashMap

object RadioDuckingCoordinator {
    private val controllers = Collections.newSetFromMap(WeakHashMap<RadioPlayerController, Boolean>())
    private val speechOwners = mutableSetOf<Any>()
    private var activePercent = AudioDuckingSettings.DEFAULT_DUCKING_PERCENT

    fun register(controller: RadioPlayerController) {
        synchronized(controllers) {
            controllers.add(controller)
            if (speechOwners.isNotEmpty()) controller.applyDucking(activePercent)
        }
    }

    fun unregister(controller: RadioPlayerController) {
        synchronized(controllers) {
            controllers.remove(controller)
        }
    }

    fun duckForSpeech(context: Context, owner: Any) {
        val settings = AudioDuckingSettings.load(context)
        if (!settings.enabled || settings.duckingPercent <= 0) return
        activePercent = settings.duckingPercent
        speechOwners.add(owner)
        applyToControllers { controller ->
            controller.applyDucking(activePercent)
        }
    }

    fun restoreAfterSpeech(owner: Any) {
        speechOwners.remove(owner)
        if (speechOwners.isNotEmpty()) return
        applyToControllers { controller ->
            controller.restoreDucking()
        }
    }

    private fun applyToControllers(action: (RadioPlayerController) -> Unit) {
        val snapshot = synchronized(controllers) { controllers.toList() }
        snapshot.forEach { controller ->
            runCatching { action(controller) }
        }
    }
}
