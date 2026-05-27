package com.rehab2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.rehab2.aac.AacPackExporter
import com.rehab2.aac.AacPackImporter
import com.rehab2.aac.AacPackImportPreflight
import java.io.File
import java.util.Locale

/**
 * Faza 2A — zaslon za IZVOZ lokalnega AAC paketa.
 *
 * Ta aktivnost zna izkljucno:
 *   - sprozit izvoz lokalnega AAC paketa v ZIP (AacPackExporter),
 *   - deliti nastali ZIP prek standardnega Android deljenja (FileProvider).
 *
 * Uvoz je dovoljen samo po uspesnem preflight pregledu in izrecni potrditvi.
 * Brez prepisa obstojecih datotek, brez brisanja in brez samodejnega ponovnega zagona AAC.
 */
class AacPackSettingsActivity : AppCompatActivity() {

    companion object {
        private const val EXPORT_BUTTON_COLOR = 0xFF3E7C4A.toInt()
        private const val SHARE_READY_COLOR = 0xFF214A78.toInt()
        private const val BUSY_BUTTON_COLOR = 0xFF5B6672.toInt()
        private const val IMPORT_PREFS_NAME = "aac_pack_import_diagnostics"
        private const val KEY_LAST_IMPORT_STATUS = "last_import_status"
    }

    private lateinit var btnExport: Button
    private lateinit var btnShare: Button
    private lateinit var btnImportPreflight: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtLastImportStatus: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastExportedZipPath: String? = null
    private val pickZipForPreflight = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            txtStatus.text = "Izbira ZIP datoteke preklicana."
        } else {
            startImportPreflight(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_pack_settings)

        btnExport = findViewById(R.id.btnExportAacPack)
        btnShare = findViewById(R.id.btnShareAacPack)
        btnImportPreflight = findViewById(R.id.btnImportAacPackPreflight)
        txtStatus = findViewById(R.id.txtExportStatus)
        txtLastImportStatus = findViewById(R.id.txtLastImportStatus)

        findViewById<Button>(R.id.btnBackAacPackSettings).setOnClickListener {
            finish()
        }

        btnExport.setOnClickListener {
            startExport()
        }

        btnShare.setOnClickListener {
            shareLastExportedZip()
        }

        btnImportPreflight.setOnClickListener {
            pickZipForPreflight.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        }

        setShareEnabled(false)
        txtStatus.text = "Pripravljeno za izvoz ali predpreverjanje ZIP paketa."
        refreshLastImportDiagnostic()
    }

    private fun startExport() {
        btnExport.isEnabled = false
        btnExport.text = "IZVAZAM..."
        btnExport.backgroundTintList = ColorStateList.valueOf(BUSY_BUTTON_COLOR)
        setShareEnabled(false)
        txtStatus.text = "Izvazam AAC paket ..."

        // Izvoz tece na ozadju; ZIP + slike lahko zmrznejo UI nit.
        Thread {
            val result = AacPackExporter.export(this)
            mainHandler.post {
                handleExportResult(result)
            }
        }.start()
    }

    private fun handleExportResult(result: AacPackExporter.Result) {
        btnExport.isEnabled = true
        btnExport.text = "IZVOZI AAC PAKET"
        btnExport.backgroundTintList = ColorStateList.valueOf(EXPORT_BUTTON_COLOR)

        when (result) {
            is AacPackExporter.Result.Success -> {
                lastExportedZipPath = result.zipFile.absolutePath
                setShareEnabled(true)
                txtStatus.text = buildString {
                    append("Izvoz uspel.\n\n")
                    append("Datotek v paketu: ${result.fileCount}\n")
                    append("Velikost ZIP: ${formatSize(result.zipSizeBytes)}\n")
                    append("Lokacija:\n${result.zipFile.absolutePath}")
                }
            }
            is AacPackExporter.Result.NoLocalPackage -> {
                lastExportedZipPath = null
                setShareEnabled(false)
                txtStatus.text = "Ni lokalnega AAC paketa za izvoz.\n" +
                    "Uporablja se SYSTEM vsebina. To ni napaka."
            }
            is AacPackExporter.Result.Failure -> {
                lastExportedZipPath = null
                setShareEnabled(false)
                txtStatus.text = "Izvoz ni uspel.\n${result.reason}"
            }
        }
    }

    private fun shareLastExportedZip() {
        val path = lastExportedZipPath
        if (path == null) {
            txtStatus.text = "Najprej izvozi AAC paket."
            return
        }
        val zipFile = File(path)
        if (!zipFile.exists() || zipFile.length() <= 0L) {
            txtStatus.text = "ZIP datoteke ni mogoce najti. Ponovi izvoz."
            setShareEnabled(false)
            return
        }

        try {
            val zipUri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                zipFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, zipUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Deli AAC paket"))
        } catch (error: ActivityNotFoundException) {
            Log.e("NovaRehabAacExport", "Share target not found", error)
            txtStatus.text = "Ni aplikacije za deljenje datoteke."
        } catch (error: Exception) {
            Log.e("NovaRehabAacExport", "Share failed", error)
            txtStatus.text = "Deljenje ni uspelo."
        }
    }

    private fun startImportPreflight(uri: Uri) {
        btnImportPreflight.isEnabled = false
        btnImportPreflight.backgroundTintList = ColorStateList.valueOf(BUSY_BUTTON_COLOR)
        txtStatus.text = "Preverjam ZIP paket ..."

        Thread {
            val result = AacPackImportPreflight.inspect(contentResolver, uri)
            mainHandler.post {
                handleImportPreflightResult(uri, result)
            }
        }.start()
    }

    private fun handleImportPreflightResult(uri: Uri, result: AacPackImportPreflight.Result) {
        btnImportPreflight.isEnabled = true
        btnImportPreflight.backgroundTintList = ColorStateList.valueOf(SHARE_READY_COLOR)

        when (result) {
            is AacPackImportPreflight.Result.Success -> {
                txtStatus.text = buildPreflightSuccessText(result)
                showImportConfirmDialog(uri, result)
            }
            is AacPackImportPreflight.Result.Rejected ->
                txtStatus.text = "ZIP zavrnjen.\n${result.reason}\n\nNoben podatek ni bil uvozen ali prepisan."
            is AacPackImportPreflight.Result.Failure ->
                txtStatus.text = "Preflight ni uspel.\n${result.reason}\n\nNoben podatek ni bil uvozen ali prepisan."
        }
    }

    private fun buildPreflightSuccessText(result: AacPackImportPreflight.Result.Success): String {
        val conflictPreview = buildConflictPreview(result)
        return buildString {
            append("Preflight uspel. ZIP je bil samo prebran.\n\n")
            append("Za uvoz je potrebna potrditev.\n")
            append("Obstojece datoteke bodo preskocene.\n\n")
            append("Predogled paketa:\n")
            append("AAC elementi: ${if (result.hasItems) 1 else 0}\n")
            append("Profili: ${result.profileCount}\n")
            append(formatProfilePreview(result.profileNames))
            append("Ikone custom: ${result.customIconCount}\n")
            append("Ikone SOCA: ${result.socaIconCount}\n")
            append("Ikone ARASAAC: ${result.arasaacIconCount}\n")
            append("Manifest: ${if (result.hasManifest) 1 else 0}\n\n")
            append(formatConflictPreview(conflictPreview))
            append("\n")
            append("Obstojece datoteke bodo preskocene.\n")
            append("Nobena datoteka ne bo izbrisana ali prepisana.")
        }
    }

    private fun showImportConfirmDialog(
        uri: Uri,
        result: AacPackImportPreflight.Result.Success
    ) {
        val conflictPreview = buildConflictPreview(result)
        AlertDialog.Builder(this)
            .setTitle("Uvozi AAC ZIP paket?")
            .setMessage(
                buildString {
                    append("Uvoz bo dodal samo manjkajoce datoteke.\n")
                    append("Obstojece datoteke bodo preskocene.\n")
                    append("Nobena datoteka ne bo izbrisana ali prepisana.\n\n")
                    append("AAC elementi: ${if (result.hasItems) 1 else 0}\n")
                    append("Profili: ${result.profileCount}\n")
                    append(formatProfilePreview(result.profileNames))
                    append("Ikone custom: ${result.customIconCount}\n")
                    append("Ikone SOCA: ${result.socaIconCount}\n")
                    append("Ikone ARASAAC: ${result.arasaacIconCount}\n")
                    append("Manifest: ${if (result.hasManifest) 1 else 0}\n\n")
                    append(formatConflictPreview(conflictPreview))
                }
            )
            .setNegativeButton("Preklici", null)
            .setPositiveButton("Uvozi") { _, _ ->
                startConfirmedImport(uri)
            }
            .show()
    }

    private fun startConfirmedImport(uri: Uri) {
        btnImportPreflight.isEnabled = false
        btnImportPreflight.backgroundTintList = ColorStateList.valueOf(BUSY_BUTTON_COLOR)
        txtStatus.text = "Uvazam AAC ZIP paket ..."

        Thread {
            val result = AacPackImporter.importNoOverwrite(this, uri)
            mainHandler.post {
                handleImportResult(result)
            }
        }.start()
    }

    private fun handleImportResult(result: AacPackImporter.Result) {
        btnImportPreflight.isEnabled = true
        btnImportPreflight.backgroundTintList = ColorStateList.valueOf(SHARE_READY_COLOR)

        val statusText = when (result) {
            is AacPackImporter.Result.Success -> buildString {
                append("Uvoz zakljucen.\n\n")
                append("Uvozenih datotek: ${result.importedCount}\n")
                append("Preskocenih obstojecih datotek: ${result.skippedExistingCount}\n")
                append("\nUvozeno po kategoriji:\n")
                append(formatCategoryCounts(result.importedByCategory))
                append("\nPreskoceno po kategoriji:\n")
                append(formatCategoryCounts(result.skippedExistingByCategory))
                append("\n")
                append("AAC komunikator se ni samodejno ponovno zagnal.")
            }
            is AacPackImporter.Result.Rejected ->
                "Uvoz zavrnjen.\n${result.reason}\n\nNoben obstojec podatek ni bil prepisan ali izbrisan."
            is AacPackImporter.Result.Failure ->
                "Uvoz ni uspel.\n${result.reason}\n\nNoben obstojec podatek ni bil prepisan ali izbrisan."
        }
        txtStatus.text = statusText
        saveLastImportDiagnostic(statusText)
        refreshLastImportDiagnostic()
    }

    private fun setShareEnabled(enabled: Boolean) {
        btnShare.isEnabled = enabled
        btnShare.backgroundTintList = ColorStateList.valueOf(
            if (enabled) SHARE_READY_COLOR else BUSY_BUTTON_COLOR
        )
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L ->
                String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L ->
                String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun formatCategoryCounts(counts: AacPackImporter.CategoryCounts): String {
        return buildString {
            append("AAC elementi: ${counts.aacItems}\n")
            append("Profili: ${counts.profiles}\n")
            append("Ikone custom: ${counts.customIcons}\n")
            append("Ikone SOCA: ${counts.socaIcons}\n")
            append("Ikone ARASAAC: ${counts.arasaacIcons}\n")
            append("Manifest: ${counts.manifest}\n")
        }
    }

    private fun formatProfilePreview(profileNames: List<String>): String {
        if (profileNames.isEmpty()) {
            return "Profili v ZIP: ni profilov\n"
        }
        return buildString {
            append("Profili v ZIP:\n")
            profileNames.take(12).forEach { profileName ->
                append("- $profileName\n")
            }
            val remainingCount = profileNames.size - 12
            if (remainingCount > 0) {
                append("- se $remainingCount dodatnih profilov\n")
            }
        }
    }

    private data class ConflictPreview(
        val newCounts: AacPackImporter.CategoryCounts,
        val existingCounts: AacPackImporter.CategoryCounts,
        val duplicateNames: List<String>
    )

    private fun buildConflictPreview(
        result: AacPackImportPreflight.Result.Success
    ): ConflictPreview {
        val externalFilesDir = getExternalFilesDir(null)
        var newCounts = AacPackImporter.CategoryCounts.empty()
        var existingCounts = AacPackImporter.CategoryCounts.empty()
        val duplicateNames = mutableListOf<String>()

        result.importEntryNames.forEach { entryName ->
            val relativePath = AacPackImportPreflight.relativeDestinationPath(entryName) ?: return@forEach
            val exists = externalFilesDir?.let { File(it, relativePath).exists() } ?: false
            if (exists) {
                existingCounts = incrementCategory(existingCounts, entryName)
                if (duplicateNames.size < 10) {
                    duplicateNames += entryName
                }
            } else {
                newCounts = incrementCategory(newCounts, entryName)
            }
        }

        return ConflictPreview(
            newCounts = newCounts,
            existingCounts = existingCounts,
            duplicateNames = duplicateNames
        )
    }

    private fun formatConflictPreview(preview: ConflictPreview): String {
        return buildString {
            append("Novo za uvoz:\n")
            append(formatCategoryCounts(preview.newCounts))
            append("\nZe obstaja - bo preskoceno:\n")
            append(formatCategoryCounts(preview.existingCounts))
            if (preview.duplicateNames.isNotEmpty()) {
                append("\nPrve podvojene datoteke:\n")
                preview.duplicateNames.forEach { name ->
                    append("- $name\n")
                }
            }
        }
    }

    private fun incrementCategory(
        counts: AacPackImporter.CategoryCounts,
        name: String
    ): AacPackImporter.CategoryCounts {
        return when {
            name == "data/aac_items.json" -> counts.copy(aacItems = counts.aacItems + 1)
            name.startsWith("data/profiles/") -> counts.copy(profiles = counts.profiles + 1)
            name.startsWith("icons/custom/") -> counts.copy(customIcons = counts.customIcons + 1)
            name.startsWith("icons/soca/") -> counts.copy(socaIcons = counts.socaIcons + 1)
            name.startsWith("icons/arasaac/") -> counts.copy(arasaacIcons = counts.arasaacIcons + 1)
            name == "aac_export_manifest.json" -> counts.copy(manifest = counts.manifest + 1)
            else -> counts
        }
    }

    private fun saveLastImportDiagnostic(statusText: String) {
        getSharedPreferences(IMPORT_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_IMPORT_STATUS, statusText)
            .apply()
    }

    private fun refreshLastImportDiagnostic() {
        val status = getSharedPreferences(IMPORT_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_IMPORT_STATUS, null)
        txtLastImportStatus.text = if (status.isNullOrBlank()) {
            "Zadnji uvoz: ni podatka."
        } else {
            "Zadnji uvoz:\n$status"
        }
    }
}
