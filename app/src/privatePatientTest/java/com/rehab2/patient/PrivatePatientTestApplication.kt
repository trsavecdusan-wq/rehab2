package com.rehab2.patient

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.rehab2.MainActivity
import com.rehab2.aac.AacEditorStorage
import com.rehab2.aac.AacPatientImages
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.AacUpgradeFieldPolicy
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.util.Properties

/** Compiled ONLY by the local privatePatientTest build; ordinary debug/release cannot load it. */
class PrivatePatientTestApplication : Application(), Application.ActivityLifecycleCallbacks {
    private var attempted = false
    private var activity = WeakReference<MainActivity>(null)
    private val ids = listOf("please", "thank_you")

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(resumed: Activity) {
        if (resumed !is MainActivity) return
        activity = WeakReference(resumed)
        if (attempted) return
        attempted = true
        try {
            val pack = Properties().apply {
                assets.open("private-patient-seed/manifest.properties").use { load(it) }
            }
            require(pack.getProperty("version") == "1")
            val packId = pack.getProperty("packId") ?: error("Missing package ID")
            val patientName = pack.getProperty("patientName")?.takeIf { it.isNotBlank() }
                ?: error("Missing patient name")
            ids.forEach { id ->
                require(pack.getProperty("$id.file") == "$id.png")
                require(pack.getProperty("$id.sha256")?.matches(Regex("[0-9a-f]{64}")) == true)
            }
            val store = AacPatientImages.store(this)
            val patientId = store.currentPatientId() // Retains an existing UUID; no patient switching.
            val confirmed = store.confirmedSeedPatient(packId)
            when {
                confirmed != null && confirmed != patientId -> report("Uvoz osebnih slik je blokiran: paket je potrjen za drug pacientov profil.")
                confirmed == patientId -> runImport(pack, packId, patientId)
                else -> AlertDialog.Builder(resumed)
                    .setTitle("Potrditev skrbnika – zasebni test")
                    .setMessage("Ali ta pacientov profil pripada osebi $patientName?\n\nS potrditvijo se osebni sliki PROSIM in HVALA povežeta s tem profilom. Obstoječe veljavne osebne slike ostanejo ohranjene.")
                    .setPositiveButton("DA, POTRJUJEM") { _, _ ->
                        try {
                            store.confirmSeedPatient(packId, patientId)
                            runImport(pack, packId, patientId)
                        } catch (_: Exception) { report("Uvoz je blokiran: potrditve identitete ni bilo mogoče shraniti.") }
                    }
                    .setNegativeButton("NE / PREKLIČI") { _, _ -> report("Osebne slike niso uvožene: identiteta ni potrjena.") }
                    .setOnCancelListener { report("Osebne slike niso uvožene: identiteta ni potrjena.") }
                    .show()
            }
        } catch (_: Exception) { report("Zasebnega paketa ali identitete ni mogoče prebrati. Aplikacija uporablja obstoječe slike.") }
    }

    private fun runImport(pack: Properties, packId: String, patientId: String) {
        Thread({
            try {
                val store = AacPatientImages.store(this)
                if (store.seedComplete(packId, patientId)) {
                    Log.i("PrivatePatientSeed", "Uvoz je bil že zaključen; brez novih kopij.")
                    return@Thread
                }
                val items = AacEditorStorage.loadItems(this).associateBy { it.id }
                val results = ids.map { id ->
                    val item = items[id] ?: error("AAC ID missing")
                    val existing = store.resolve(patientId, id)
                    val legacy = AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
                    val personalLegacy = AacUpgradeFieldPolicy.hasCustomImage(JSONObject()
                        .put("imagePath", item.imagePath).put("iconSource", item.iconSource.name))
                    val result = when {
                        existing != null && readable(existing) -> "SKIPPED_EXISTING"
                        store.ownsLegacyImages(patientId) && personalLegacy && legacy != null && readable(legacy) -> "SKIPPED_LEGACY"
                        item.locked -> error("AAC icon locked")
                        else -> store.importSeed(packId, patientId, id, pack.getProperty("$id.sha256"),
                            { assets.open("private-patient-seed/${pack.getProperty("$id.file")}") },
                            ::readable, AacStoragePaths.getPatientProfileFile(this), AacStoragePaths.getAacItemsFile(this)).name
                    }
                    "$id=$result"
                }
                store.completeSeed(packId, patientId, results.joinToString(";"))
                report("Osebne slike: " + results.joinToString(", ") { value ->
                    val name = if (value.startsWith("please=")) "PROSIM" else "HVALA"
                    "$name – " + if (value.contains("SKIPPED")) "obstoječa slika ohranjena" else "uvoženo"
                })
                activity.get()?.runOnUiThread { activity.get()?.refreshPersonalAacImages(ids.toSet()) }
            } catch (_: Exception) {
                report("Uvoz osebnih slik ni dokončan. Obstoječe slike so ohranjene; ob naslednjem zagonu bo poskus ponovljen.")
            } catch (_: OutOfMemoryError) {
                report("Za uvoz osebnih slik trenutno ni dovolj pomnilnika. Obstoječe slike so ohranjene.")
            }
        }, "private-patient-seed").start()
    }

    private fun readable(file: File): Boolean {
        if (!file.isFile) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        bitmap.recycle()
        return true
    }

    private fun report(message: String) {
        Log.i("PrivatePatientSeed", message)
        activity.get()?.runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
