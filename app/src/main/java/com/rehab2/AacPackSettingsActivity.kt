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
import com.rehab2.aac.AacStoragePaths
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        private const val KEY_IMPORT_REPORTS = "import_reports"
        private const val IMPORT_REPORT_SEPARATOR = "\u001E"
        private const val MAX_IMPORT_REPORTS = 5
        private const val MAX_PROFILE_PREVIEW_BYTES = 64 * 1024
        private const val MAX_ITEMS_PREVIEW_BYTES = 512 * 1024
    }

    private lateinit var btnExport: Button
    private lateinit var btnShare: Button
    private lateinit var btnCreateTestZip: Button
    private lateinit var btnImportPreflight: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtLastImportStatus: TextView
    private lateinit var txtAacHealthSummary: TextView
    private lateinit var txtLocalProfilesStatus: TextView

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
        btnCreateTestZip = findViewById(R.id.btnCreateTestAacZip)
        btnImportPreflight = findViewById(R.id.btnImportAacPackPreflight)
        txtStatus = findViewById(R.id.txtExportStatus)
        txtLastImportStatus = findViewById(R.id.txtLastImportStatus)
        txtAacHealthSummary = findViewById(R.id.txtAacHealthSummary)
        txtLocalProfilesStatus = findViewById(R.id.txtLocalProfilesStatus)

        findViewById<Button>(R.id.btnBackAacPackSettings).setOnClickListener {
            finish()
        }

        btnExport.setOnClickListener {
            startExport()
        }

        btnShare.setOnClickListener {
            shareLastExportedZip()
        }

        btnCreateTestZip.setOnClickListener {
            startCreateTestZip()
        }

        btnImportPreflight.setOnClickListener {
            pickZipForPreflight.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        }

        setShareEnabled(false)
        txtStatus.text = "Pripravljeno za izvoz ali predpreverjanje ZIP paketa."
        refreshLastImportDiagnostic()
        refreshLocalAacOverview()
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

    private fun startCreateTestZip() {
        btnCreateTestZip.isEnabled = false
        btnCreateTestZip.backgroundTintList = ColorStateList.valueOf(BUSY_BUTTON_COLOR)
        txtStatus.text = "Ustvarjam testni AAC ZIP ..."

        Thread {
            val result = createTestZip()
            mainHandler.post {
                handleCreateTestZipResult(result)
            }
        }.start()
    }

    private fun handleCreateTestZipResult(result: TestZipResult) {
        btnCreateTestZip.isEnabled = true
        btnCreateTestZip.backgroundTintList = ColorStateList.valueOf(SHARE_READY_COLOR)

        when (result) {
            is TestZipResult.Success -> {
                lastExportedZipPath = result.zipFile.absolutePath
                setShareEnabled(true)
                txtStatus.text = buildString {
                    append("Testni AAC ZIP ustvarjen.\n\n")
                    append("Datotek v paketu: ${result.fileCount}\n")
                    append("Velikost ZIP: ${formatSize(result.zipFile.length())}\n")
                    append("Lokacija:\n${result.zipFile.absolutePath}\n\n")
                    append("ZIP ni bil uvozen samodejno. Za prenos uporabi DELI ZIP.")
                }
            }
            is TestZipResult.Failure -> {
                txtStatus.text = "Testnega ZIP paketa ni bilo mogoce ustvariti.\n${result.reason}"
            }
        }
    }

    private fun createTestZip(): TestZipResult {
        return try {
            val exportsDir = AacPackExporter.getExportsDir(this)
            if (!exportsDir.exists() && !exportsDir.mkdirs()) {
                return TestZipResult.Failure("Mape za izvoz ni bilo mogoce ustvariti.")
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
            val zipFile = File(exportsDir, "NovaRehab_AAC_TEST_$timestamp.zip")
            val exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).format(Date())

            val entries = linkedMapOf(
                "aac_export_manifest.json" to buildTestManifest(exportedAt),
                "data/aac_items.json" to buildTestItemsJson(),
                "data/profiles/test_profile.json" to buildTestProfileJson()
            )

            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                entries.forEach { (entryName, content) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(content.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            if (!zipFile.exists() || zipFile.length() <= 0L) {
                TestZipResult.Failure("ZIP datoteka ni bila ustvarjena.")
            } else {
                TestZipResult.Success(zipFile = zipFile, fileCount = entries.size)
            }
        } catch (error: Exception) {
            TestZipResult.Failure(error.message ?: "Neznana napaka.")
        }
    }

    private fun buildTestManifest(exportedAt: String): String {
        return org.json.JSONObject().apply {
            put("packageName", "NovaRehab testni AAC paket")
            put("packageVersion", "1")
            put("exportTimestamp", exportedAt)
            put("author", "NovaRehab test generator")
            put("therapist", "Testni terapevt")
            put("description", "Majhen testni paket za preverjanje AAC ZIP uvoza brez sprememb pacientovih podatkov.")
        }.toString(2)
    }

    private fun buildTestItemsJson(): String {
        return """
            {
              "items": [
                {
                  "id": "test_hello",
                  "label": "Pozdrav",
                  "text": "Pozdrav",
                  "profileId": "test_profile"
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildTestProfileJson(): String {
        return """
            {
              "id": "test_profile",
              "name": "Testni profil",
              "description": "Profil za varen test AAC ZIP uvoza."
            }
        """.trimIndent()
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
            append(formatManifestMetadata(result))
            append("\n")
            append(formatIntegrityWarnings(result.integrityWarnings))
            append("\n")
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
                    append(formatManifestMetadata(result))
                    append("\n")
                    append(formatIntegrityWarnings(result.integrityWarnings))
                    append("\n")
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

        val statusText = buildImportStatusText(result)
        txtStatus.text = statusText
        saveImportReport(buildImportReport(result))
        refreshLastImportDiagnostic()
        refreshLocalAacOverview()
    }

    private fun buildImportStatusText(result: AacPackImporter.Result): String {
        return when (result) {
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

    private fun formatManifestMetadata(result: AacPackImportPreflight.Result.Success): String {
        if (!result.hasManifest) {
            return "Metapodatki paketa:\nManifest ni prisoten.\n"
        }

        val metadata = result.manifestMetadata
            ?: return "Metapodatki paketa:\nManifest je prisoten, metapodatki niso na voljo.\n"

        return buildString {
            append("Metapodatki paketa:\n")
            if (metadata.warning != null) {
                append("Opozorilo: ${metadata.warning}\n")
            }
            append("Ime paketa: ${metadata.packageName ?: "ni podatka"}\n")
            append("Verzija paketa: ${metadata.packageVersion ?: "ni podatka"}\n")
            append("Cas izvoza: ${metadata.exportTimestamp ?: "ni podatka"}\n")
            if (metadata.therapistAuthor != null) {
                append("Terapevt/avtor: ${metadata.therapistAuthor}\n")
            }
            if (metadata.description != null) {
                append("Opis: ${metadata.description}\n")
            }
        }
    }

    private fun formatIntegrityWarnings(warnings: List<String>): String {
        if (warnings.isEmpty()) {
            return "Diagnostika ZIP:\nNi opozoril.\n"
        }
        return buildString {
            append("Diagnostika ZIP - opozorila:\n")
            warnings.forEach { warning ->
                append("- $warning\n")
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

    private fun buildImportReport(result: AacPackImporter.Result): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
        return when (result) {
            is AacPackImporter.Result.Success -> buildString {
                append("$timestamp - uspeh\n")
                append("Uvozenih: ${result.importedCount}, preskocenih: ${result.skippedExistingCount}\n")
                append("Uvozeno po kategoriji:\n")
                append(formatCategoryCounts(result.importedByCategory).trimEnd())
                append("\nPreskoceno po kategoriji:\n")
                append(formatCategoryCounts(result.skippedExistingByCategory).trimEnd())
            }
            is AacPackImporter.Result.Rejected -> buildString {
                append("$timestamp - zavrnjeno\n")
                append(result.reason)
            }
            is AacPackImporter.Result.Failure -> buildString {
                append("$timestamp - napaka\n")
                append(result.reason)
            }
        }
    }

    private fun saveImportReport(report: String) {
        val prefs = getSharedPreferences(IMPORT_PREFS_NAME, Context.MODE_PRIVATE)
        val reports = loadImportReports().toMutableList()
        reports.add(0, report)
        val encodedReports = reports
            .take(MAX_IMPORT_REPORTS)
            .joinToString(IMPORT_REPORT_SEPARATOR)
        prefs.edit()
            .putString(KEY_IMPORT_REPORTS, encodedReports)
            .apply()
    }

    private fun refreshLastImportDiagnostic() {
        val reports = loadImportReports()
        txtLastImportStatus.text = if (reports.isEmpty()) {
            "Zadnji uvozi: ni podatka."
        } else {
            buildString {
                append("Zadnji uvozi:\n")
                reports.forEachIndexed { index, report ->
                    if (index > 0) {
                        append("\n")
                    }
                    append(report)
                    append("\n")
                }
            }.trimEnd()
        }
    }

    private fun loadImportReports(): List<String> {
        val rawReports = getSharedPreferences(IMPORT_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IMPORT_REPORTS, null)
            ?: return emptyList()
        return rawReports
            .split(IMPORT_REPORT_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_IMPORT_REPORTS)
    }

    private fun refreshLocalAacOverview() {
        val overview = buildLocalAacOverview()
        txtAacHealthSummary.text = buildAacHealthSummary(overview)
        txtLocalProfilesStatus.text = buildLocalAacProfilesReport(overview)
    }

    private fun buildLocalAacOverview(): LocalAacOverview {
        val profilesDir = AacStoragePaths.getProfilesDataDir(this)
        val profileFiles = profilesDir
            ?.takeIf { it.isDirectory }
            ?.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            .orEmpty()

        val aacItemsFile = AacStoragePaths.getAacItemsFile(this)
        val hasAacItems = aacItemsFile?.isFile == true && aacItemsFile.length() > 0L
        val aacItemsPreview = if (hasAacItems) {
            readTextSafely(aacItemsFile, MAX_ITEMS_PREVIEW_BYTES)
        } else {
            null
        }
        val iconCount = countLocalIconsSafely()
        val profiles = profileFiles.map { profileFile ->
            val summary = readLocalProfileSummary(profileFile)
            summary.copy(hasLinkedItems = profileHasLinkedItems(summary, aacItemsPreview))
        }

        return LocalAacOverview(
            profiles = profiles,
            hasAacItems = hasAacItems,
            iconCount = iconCount,
            lastImportSummary = lastImportSummary()
        )
    }

    private fun buildAacHealthSummary(overview: LocalAacOverview): String {
        return buildString {
            append("AAC ZDRAVJE\n")
            append("Lokalni profili: ${overview.profiles.size}\n")
            append("Lokalne ikone: ${overview.iconCount}\n")
            append("Sumljivi profili: ${overview.suspiciousProfileCount}\n")
            append("Prazni/neveljavni profili: ${overview.emptyOrInvalidProfileCount}\n")
            append("AAC elementi: ${if (overview.hasAacItems) "prisotni" else "niso prisotni"}\n")
            append("Zadnji uvoz: ${overview.lastImportSummary}")
        }
    }

    private fun buildLocalAacProfilesReport(overview: LocalAacOverview): String {
        return buildString {
            append("LOKALNI AAC PROFILI\n")
            append("AAC elementi: ${if (overview.hasAacItems) "prisotni" else "niso prisotni"}\n")
            append("Ikone v lokalnih mapah (pribl.): ${overview.iconCount}\n\n")

            if (overview.profiles.isEmpty()) {
                append("Ni lokalnih AAC profilov.")
                return@buildString
            }

            overview.profiles.forEach { summary ->
                append("- ${summary.displayName}\n")
                append("  Datoteka: ${summary.fileName}\n")
                append("  ID profila: ${summary.profileId.ifBlank { "ni podatka" }}\n")
                append("  Povezani AAC elementi: ${if (summary.hasLinkedItems) "da" else "ni potrjeno"}\n")
                append("  Priblizno lokalnih ikon: ${overview.iconCount}\n")
                append("  Velikost JSON: ${formatSize(summary.fileSizeBytes)}\n")
                if (summary.warnings.isNotEmpty()) {
                    append("  Opozorila: ${summary.warnings.joinToString("; ")}\n")
                }
            }
        }.trimEnd()
    }

    private fun readLocalProfileSummary(profileFile: File): LocalProfileSummary {
        val fallbackName = profileFile.nameWithoutExtension.ifBlank { profileFile.name }
        val fileSizeBytes = profileFile.length()
        val warnings = mutableListOf<String>()
        if (fileSizeBytes <= 0L) {
            warnings += "profil JSON je prazen"
        }
        val profileText = readTextSafely(profileFile, MAX_PROFILE_PREVIEW_BYTES)
        if (profileText == null) {
            if (fileSizeBytes > MAX_PROFILE_PREVIEW_BYTES.toLong()) {
                warnings += "profil JSON je vecji od varnega predogleda"
            } else if (fileSizeBytes > 0L) {
                warnings += "profil JSON ni berljiv"
            }
            return LocalProfileSummary(
                fileName = profileFile.name,
                displayName = fallbackName,
                profileId = profileFile.nameWithoutExtension,
                fileSizeBytes = fileSizeBytes,
                warnings = warnings,
                isEmptyOrInvalid = warnings.any { it.contains("prazen") || it.contains("ni veljaven") },
                hasLinkedItems = false
            )
        }

        if (profileText.isBlank()) {
            warnings += "profil JSON nima vsebine"
        }

        return try {
            val json = org.json.JSONObject(profileText)
            val profileName = json.optString("name").trim()
            val profileId = json.optString("id").trim()
            if (profileName.isBlank()) {
                warnings += "manjka ime profila"
            }
            if (profileId.isBlank()) {
                warnings += "manjka ID profila"
            }
            if (json.length() == 0) {
                warnings += "profil JSON nima polj"
            }
            LocalProfileSummary(
                fileName = profileFile.name,
                displayName = profileName.ifBlank { fallbackName },
                profileId = profileId.ifBlank { profileFile.nameWithoutExtension },
                fileSizeBytes = fileSizeBytes,
                warnings = warnings,
                isEmptyOrInvalid = warnings.any { it.contains("prazen") || it.contains("nima vsebine") || it.contains("ni veljaven") },
                hasLinkedItems = false
            )
        } catch (error: Exception) {
            warnings += "profil JSON ni veljaven"
            LocalProfileSummary(
                fileName = profileFile.name,
                displayName = fallbackName,
                profileId = profileFile.nameWithoutExtension,
                fileSizeBytes = fileSizeBytes,
                warnings = warnings,
                isEmptyOrInvalid = true,
                hasLinkedItems = false
            )
        }
    }

    private fun profileHasLinkedItems(
        profile: LocalProfileSummary,
        aacItemsPreview: String?
    ): Boolean {
        if (aacItemsPreview.isNullOrBlank()) {
            return false
        }
        val markers = listOf(profile.profileId, profile.displayName, profile.fileName)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return markers.any { marker ->
            aacItemsPreview.contains(marker, ignoreCase = true)
        }
    }

    private fun countLocalIconsSafely(): Int {
        return listOf(
            AacStoragePaths.getIconsCustomDir(this),
            AacStoragePaths.getIconsSocaDir(this),
            AacStoragePaths.getIconsArasaacDir(this)
        ).sumOf { dir ->
            dir
                ?.takeIf { it.isDirectory }
                ?.listFiles { file -> file.isFile }
                ?.size
                ?: 0
        }
    }

    private fun readTextSafely(file: File, maxBytes: Int): String? {
        return try {
            if (!file.isFile || file.length() > maxBytes.toLong()) {
                return null
            }
            file.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            null
        }
    }

    private fun lastImportSummary(): String {
        val report = loadImportReports().firstOrNull() ?: return "ni podatka"
        return report.lineSequence().firstOrNull()?.ifBlank { null } ?: "ni podatka"
    }

    private data class LocalAacOverview(
        val profiles: List<LocalProfileSummary>,
        val hasAacItems: Boolean,
        val iconCount: Int,
        val lastImportSummary: String
    ) {
        val suspiciousProfileCount: Int
            get() = profiles.count { it.warnings.isNotEmpty() }

        val emptyOrInvalidProfileCount: Int
            get() = profiles.count { it.isEmptyOrInvalid }
    }

    private data class LocalProfileSummary(
        val fileName: String,
        val displayName: String,
        val profileId: String,
        val fileSizeBytes: Long,
        val warnings: List<String>,
        val isEmptyOrInvalid: Boolean,
        val hasLinkedItems: Boolean
    )

    private sealed class TestZipResult {
        data class Success(
            val zipFile: File,
            val fileCount: Int
        ) : TestZipResult()

        data class Failure(val reason: String) : TestZipResult()
    }
}
