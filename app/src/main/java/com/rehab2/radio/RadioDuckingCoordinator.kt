package com.rehab2.radio

import android.content.Context
import com.rehab2.aac.AudioDuckingSettings
import java.util.Collections
import java.util.WeakHashMap

object RadioDuckingCoordinator {
    private val controllers = Collections.newSetFromMap(WeakHashMap<RadioPlayerController, Boolean>())

    fun register(controller: RadioPlayerController) {
        synchronized(controllers) {
            controllers.add(controller)
        }
    }

    fun unregister(controller: RadioPlayerController) {
        synchronized(controllers) {
            controllers.remove(controller)
        }
    }

    fun duckForSpeech(context: Context) {
        val settings = AudioDuckingSettings.load(context)
        if (!settings.enabled || settings.duckingPercent <= 0) return
        applyToControllers { controller ->
            controller.applyDucking(settings.duckingPercent)
        }
    }

    fun restoreAfterSpeech() {
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
