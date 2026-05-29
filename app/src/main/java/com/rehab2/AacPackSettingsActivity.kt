package com.rehab2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.rehab2.aac.AacIconZipImporter
import com.rehab2.aac.AacPackExporter
import com.rehab2.aac.AacPackImporter
import com.rehab2.aac.AacPackImportPreflight
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.AacStoredTranslationCache
import com.rehab2.aac.IconSource
import java.io.File
import java.text.Normalizer
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
        private const val PROFILE_SELECTION_PREFS_NAME = "aac_therapist_profile_selection"
        private const val KEY_SELECTED_PROFILE_ID = "selected_profile_id"
        private const val KEY_SELECTED_PROFILE_NAME = "selected_profile_name"
        private const val KEY_SELECTED_PROFILE_TIMESTAMP = "selected_profile_timestamp"
        private const val SOURCE_ACTIVATION_PREFS_NAME = "aac_source_activation"
        private const val KEY_ACTIVE_LIBRARY_SOURCES = "active_library_sources"
        private const val PATIENT_PAGE_PREFS_NAME = "aac_patient_pages"
        private const val KEY_PATIENT_PAGES = "patient_pages"
        private const val KEY_DEFAULT_PATIENT_PAGE_ID = "default_patient_page_id"
        private const val PATIENT_PAGE_SEPARATOR = "\u001E"
        private const val PATIENT_PAGE_FIELD_SEPARATOR = "\u001F"
        private const val AAC_LANGUAGE_PREFS_NAME = "aac_language_settings"
        private const val KEY_AAC_ACTIVE_LANGUAGES = "active_languages"
        private const val KEY_AAC_BASE_LANGUAGE = "base_language"
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_PATIENT_LANGUAGE_1 = "patient_language_1"
        private const val PREF_PATIENT_LANGUAGE_2 = "patient_language_2"
        private const val PREF_PATIENT_LANGUAGE_3 = "patient_language_3"
        private const val PREF_ACTIVE_SPEECH_LANGUAGE = "active_speech_language"
        private const val LIBRARY_PLACEMENT_SOURCE = "library_activation"
        private const val LIBRARY_PAGE_SIZE = 25
        private const val MAX_EDITOR_LIST_ITEMS = 80
        private const val MAX_PROFILE_PREVIEW_BYTES = 64 * 1024
        private const val MAX_ITEMS_PREVIEW_BYTES = 512 * 1024
        private const val SUSPICIOUS_ITEMS_PER_PROFILE = 250
    }

    private lateinit var btnExport: Button
    private lateinit var btnShare: Button
    private lateinit var btnCreateTestZip: Button
    private lateinit var btnImportPreflight: Button
    private lateinit var btnImportIconZip: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtLastImportStatus: TextView
    private lateinit var txtAacHealthSummary: TextView
    private lateinit var txtFixedTopRowStatus: TextView
    private lateinit var editFixedTopRowItemId: EditText
    private lateinit var editFixedTopRowPosition: EditText
    private lateinit var btnChooseFixedTopRowItem: Button
    private lateinit var btnSaveFixedTopRowPosition: Button
    private lateinit var btnClearFixedTopRowPosition: Button
    private lateinit var txtFixedTopRowAvailableItems: TextView
    private lateinit var iconSourceFilterButtons: Map<TherapistIconSourceFilter, Button>
    private lateinit var txtSourceActivationStatus: TextView
    private lateinit var btnActivateSocaLibrary: Button
    private lateinit var btnDeactivateSocaLibrary: Button
    private lateinit var btnActivateCustomLibrary: Button
    private lateinit var btnDeactivateCustomLibrary: Button
    private lateinit var btnActivateArasaacLibrary: Button
    private lateinit var btnDeactivateArasaacLibrary: Button
    private lateinit var aacItemListActions: LinearLayout
    private lateinit var editAacItemId: EditText
    private lateinit var editAacLabelSl: EditText
    private lateinit var editAacLabelUk: EditText
    private lateinit var editAacLabelEn: EditText
    private lateinit var editAacBaseLanguage: EditText
    private lateinit var editAacActiveLanguages: EditText
    private lateinit var txtAacLanguageManagerStatus: TextView
    private lateinit var editAacLanguageCode: EditText
    private lateinit var btnAddAacActiveLanguage: Button
    private lateinit var btnSetAacBaseLanguage: Button
    private lateinit var editAacSpeechText: EditText
    private lateinit var editAacIconSource: EditText
    private lateinit var editAacImagePath: EditText
    private lateinit var btnChooseAacImage: Button
    private lateinit var imgAacImagePreview: ImageView
    private lateinit var editAacCategoryId: EditText
    private lateinit var checkAacAddsToSentence: CheckBox
    private lateinit var checkAacSpeaksImmediately: CheckBox
    private lateinit var checkAacOpensSubicons: CheckBox
    private lateinit var checkLearningImageText: CheckBox
    private lateinit var checkLearningImageOnly: CheckBox
    private lateinit var checkLearningTextOnly: CheckBox
    private lateinit var btnSaveAacItem: Button
    private lateinit var editPlacementItemId: EditText
    private lateinit var editPlacementPageId: EditText
    private lateinit var editPlacementPosition5x5: EditText
    private lateinit var btnAddPlacement: Button
    private lateinit var btnRemovePlacement: Button
    private lateinit var txtPlacementStatus: TextView
    private lateinit var txtPatientPagesStatus: TextView
    private lateinit var editPatientPageId: EditText
    private lateinit var editPatientPageTitle: EditText
    private lateinit var btnSavePatientPage: Button
    private lateinit var btnSetDefaultPatientPage: Button
    private lateinit var editSubiconParentId: EditText
    private lateinit var editSubiconChildId: EditText
    private lateinit var btnChooseSubiconParent: Button
    private lateinit var btnChooseSubiconChild: Button
    private lateinit var btnAddSubicon: Button
    private lateinit var btnRemoveSubicon: Button
    private lateinit var txtSubiconStatus: TextView
    private lateinit var txtActiveProfileStatus: TextView
    private lateinit var txtIconFolderStatus: TextView
    private lateinit var txtLocalProfilesStatus: TextView
    private lateinit var localProfileActions: LinearLayout

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastExportedZipPath: String? = null
    private var therapistIconSourceFilter = TherapistIconSourceFilter.ALL
    private val pickZipForPreflight = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            txtStatus.text = "Izbira ZIP datoteke preklicana."
        } else {
            startImportPreflight(uri)
        }
    }
    private val pickIconZip = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            txtStatus.text = "Izbira ZIP datoteke z ikonami preklicana."
        } else {
            startIconZipImport(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_pack_settings)

        btnExport = findViewById(R.id.btnExportAacPack)
        btnShare = findViewById(R.id.btnShareAacPack)
        btnCreateTestZip = findViewById(R.id.btnCreateTestAacZip)
        btnImportPreflight = findViewById(R.id.btnImportAacPackPreflight)
        btnImportIconZip = findViewById(R.id.btnImportAacIconZip)
        txtStatus = findViewById(R.id.txtExportStatus)
        txtLastImportStatus = findViewById(R.id.txtLastImportStatus)
        txtAacHealthSummary = findViewById(R.id.txtAacHealthSummary)
        txtFixedTopRowStatus = findViewById(R.id.txtFixedTopRowStatus)
        editFixedTopRowItemId = findViewById(R.id.editFixedTopRowItemId)
        editFixedTopRowPosition = findViewById(R.id.editFixedTopRowPosition)
        btnChooseFixedTopRowItem = findViewById(R.id.btnChooseFixedTopRowItem)
        btnSaveFixedTopRowPosition = findViewById(R.id.btnSaveFixedTopRowPosition)
        btnClearFixedTopRowPosition = findViewById(R.id.btnClearFixedTopRowPosition)
        txtFixedTopRowAvailableItems = findViewById(R.id.txtFixedTopRowAvailableItems)
        iconSourceFilterButtons = mapOf(
            TherapistIconSourceFilter.ALL to findViewById(R.id.btnIconSourceAll),
            TherapistIconSourceFilter.SOCA to findViewById(R.id.btnIconSourceSoca),
            TherapistIconSourceFilter.CUSTOM to findViewById(R.id.btnIconSourceCustom),
            TherapistIconSourceFilter.ARASAAC to findViewById(R.id.btnIconSourceArasaac),
            TherapistIconSourceFilter.SYSTEM to findViewById(R.id.btnIconSourceSystem)
        )
        txtSourceActivationStatus = findViewById(R.id.txtSourceActivationStatus)
        btnActivateSocaLibrary = findViewById(R.id.btnActivateSocaLibrary)
        btnDeactivateSocaLibrary = findViewById(R.id.btnDeactivateSocaLibrary)
        btnActivateCustomLibrary = findViewById(R.id.btnActivateCustomLibrary)
        btnDeactivateCustomLibrary = findViewById(R.id.btnDeactivateCustomLibrary)
        btnActivateArasaacLibrary = findViewById(R.id.btnActivateArasaacLibrary)
        btnDeactivateArasaacLibrary = findViewById(R.id.btnDeactivateArasaacLibrary)
        aacItemListActions = findViewById(R.id.aacItemListActions)
        editAacItemId = findViewById(R.id.editAacItemId)
        editAacLabelSl = findViewById(R.id.editAacLabelSl)
        editAacLabelUk = findViewById(R.id.editAacLabelUk)
        editAacLabelEn = findViewById(R.id.editAacLabelEn)
        editAacBaseLanguage = findViewById(R.id.editAacBaseLanguage)
        editAacActiveLanguages = findViewById(R.id.editAacActiveLanguages)
        txtAacLanguageManagerStatus = findViewById(R.id.txtAacLanguageManagerStatus)
        editAacLanguageCode = findViewById(R.id.editAacLanguageCode)
        btnAddAacActiveLanguage = findViewById(R.id.btnAddAacActiveLanguage)
        btnSetAacBaseLanguage = findViewById(R.id.btnSetAacBaseLanguage)
        editAacSpeechText = findViewById(R.id.editAacSpeechText)
        editAacIconSource = findViewById(R.id.editAacIconSource)
        editAacImagePath = findViewById(R.id.editAacImagePath)
        btnChooseAacImage = findViewById(R.id.btnChooseAacImage)
        imgAacImagePreview = findViewById(R.id.imgAacImagePreview)
        editAacCategoryId = findViewById(R.id.editAacCategoryId)
        checkAacAddsToSentence = findViewById(R.id.checkAacAddsToSentence)
        checkAacSpeaksImmediately = findViewById(R.id.checkAacSpeaksImmediately)
        checkAacOpensSubicons = findViewById(R.id.checkAacOpensSubicons)
        checkLearningImageText = findViewById(R.id.checkLearningImageText)
        checkLearningImageOnly = findViewById(R.id.checkLearningImageOnly)
        checkLearningTextOnly = findViewById(R.id.checkLearningTextOnly)
        btnSaveAacItem = findViewById(R.id.btnSaveAacItem)
        editPlacementItemId = findViewById(R.id.editPlacementItemId)
        editPlacementPageId = findViewById(R.id.editPlacementPageId)
        editPlacementPosition5x5 = findViewById(R.id.editPlacementPosition5x5)
        btnAddPlacement = findViewById(R.id.btnAddPlacement)
        btnRemovePlacement = findViewById(R.id.btnRemovePlacement)
        txtPlacementStatus = findViewById(R.id.txtPlacementStatus)
        txtPatientPagesStatus = findViewById(R.id.txtPatientPagesStatus)
        editPatientPageId = findViewById(R.id.editPatientPageId)
        editPatientPageTitle = findViewById(R.id.editPatientPageTitle)
        btnSavePatientPage = findViewById(R.id.btnSavePatientPage)
        btnSetDefaultPatientPage = findViewById(R.id.btnSetDefaultPatientPage)
        editSubiconParentId = findViewById(R.id.editSubiconParentId)
        editSubiconChildId = findViewById(R.id.editSubiconChildId)
        btnChooseSubiconParent = findViewById(R.id.btnChooseSubiconParent)
        btnChooseSubiconChild = findViewById(R.id.btnChooseSubiconChild)
        btnAddSubicon = findViewById(R.id.btnAddSubicon)
        btnRemoveSubicon = findViewById(R.id.btnRemoveSubicon)
        txtSubiconStatus = findViewById(R.id.txtSubiconStatus)
        txtActiveProfileStatus = findViewById(R.id.txtActiveProfileStatus)
        txtIconFolderStatus = findViewById(R.id.txtIconFolderStatus)
        txtLocalProfilesStatus = findViewById(R.id.txtLocalProfilesStatus)
        localProfileActions = findViewById(R.id.localProfileActions)

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

        btnImportIconZip.setOnClickListener {
            pickIconZip.launch(
                arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
            )
        }

        btnSaveFixedTopRowPosition.setOnClickListener {
            saveFixedTopRowPosition()
        }

        btnChooseFixedTopRowItem.setOnClickListener {
            showFixedTopRowItemChooser()
        }

        btnClearFixedTopRowPosition.setOnClickListener {
            clearFixedTopRowPosition()
        }

        iconSourceFilterButtons.forEach { (filter, button) ->
            button.setOnClickListener {
                therapistIconSourceFilter = filter
                updateIconSourceFilterButtons()
                refreshLocalAacOverview()
            }
        }

        btnSaveAacItem.setOnClickListener {
            saveTherapistAacItem()
        }
        btnChooseAacImage.setOnClickListener {
            showAacImageChooser()
        }
        btnAddAacActiveLanguage.setOnClickListener {
            addAacActiveLanguage()
        }
        btnSetAacBaseLanguage.setOnClickListener {
            setAacBaseLanguage()
        }
        btnActivateSocaLibrary.setOnClickListener {
            setLibrarySourceActive(LibraryIconSource.SOCA, active = true)
        }
        btnDeactivateSocaLibrary.setOnClickListener {
            setLibrarySourceActive(LibraryIconSource.SOCA, active = false)
        }
        btnActivateCustomLibrary.setOnClickListener {
            setLibrarySourceActive(LibraryIconSource.CUSTOM, active = true)
        }
        btnDeactivateCustomLibrary.setOnClickListener {
            setLibrarySourceActive(LibraryIconSource.CUSTOM, active = false)
        }
        btnActivateArasaacLibrary.setOnClickListener {
            setLibrarySourceActive(LibraryIconSource.ARASAAC, active = true)
        }
        btnDeactivateArasaacLibrary.setOnClickListener {
            setLibrarySourceActive(LibraryIconSource.ARASAAC, active = false)
        }
        btnAddPlacement.setOnClickListener {
            updatePlacement(add = true)
        }
        btnRemovePlacement.setOnClickListener {
            updatePlacement(add = false)
        }
        btnSavePatientPage.setOnClickListener {
            savePatientPage()
        }
        btnSetDefaultPatientPage.setOnClickListener {
            setDefaultPatientPage()
        }
        btnChooseSubiconParent.setOnClickListener {
            showSubiconItemChooser(targetParent = true)
        }
        btnChooseSubiconChild.setOnClickListener {
            showSubiconItemChooser(targetParent = false)
        }
        btnAddSubicon.setOnClickListener {
            updateSubicon(add = true)
        }
        btnRemoveSubicon.setOnClickListener {
            updateSubicon(add = false)
        }

        AacStoragePaths.ensureAacContentDirs(this)
        setShareEnabled(false)
        txtStatus.text = "Pripravljeno za izvoz ali predpreverjanje ZIP paketa."
        updateIconSourceFilterButtons()
        refreshAacLanguageManagerStatus()
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

    private fun startIconZipImport(uri: Uri) {
        btnImportIconZip.isEnabled = false
        btnImportIconZip.backgroundTintList = ColorStateList.valueOf(BUSY_BUTTON_COLOR)
        txtStatus.text = "Uvažam lokalne AAC ikone iz ZIP ..."

        Thread {
            val result = AacIconZipImporter.importNoOverwrite(this, uri)
            mainHandler.post {
                handleIconZipImportResult(result)
            }
        }.start()
    }

    private fun handleIconZipImportResult(result: AacIconZipImporter.Result) {
        btnImportIconZip.isEnabled = true
        btnImportIconZip.backgroundTintList = ColorStateList.valueOf(SHARE_READY_COLOR)

        txtStatus.text = when (result) {
            is AacIconZipImporter.Result.Success -> buildString {
                append("Uvoz ikon ZIP končan.\n")
                append("Uvoženo: ${result.importedCount}\n")
                append("Preskočeno obstoječih: ${result.skippedExistingCount}\n")
                append("Zavrnjenih nevarnih: ${result.rejectedUnsafeCount}\n")
                append("Ignorirano nepodprtih: ${result.ignoredUnsupportedCount}\n")
                append("SOCA: ${result.importedSocaCount}, Custom: ${result.importedCustomCount}, ARASAAC: ${result.importedArasaacCount}\n")
                append("Podprta struktura: soca/*.png, custom/*.png, arasaac/*.png\n")
                append("Obstoječe datoteke niso bile prepisane.")
            }
            is AacIconZipImporter.Result.Failure ->
                "Uvoz ikon ZIP ni uspel: ${result.reason}"
        }
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
        txtSourceActivationStatus.text = buildSourceActivationStatus()
        renderAacItemEditorList(overview.relationAnalysis.availableItems)
        txtFixedTopRowStatus.text = buildFixedTopRowStatus(overview.relationAnalysis.fixedTopRowItems)
        txtFixedTopRowAvailableItems.text = buildFixedTopRowAvailableItems(overview.relationAnalysis.availableItems)
        txtPatientPagesStatus.text = buildPatientPagesStatus()
        txtPlacementStatus.text = buildPlacementStatus(overview.relationAnalysis.availableItems)
        txtSubiconStatus.text = buildSubiconStatus()
        txtActiveProfileStatus.text = buildActiveProfileStatus()
        txtIconFolderStatus.text = buildIconFolderStatus()
        txtLocalProfilesStatus.text = buildLocalAacProfilesReport(overview)
        renderLocalProfileActions(overview)
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
        val relationAnalysis = analyzeLocalAacRelations(aacItemsFile)
        val iconCount = countLocalIconsSafely()
        val profileSummaries = profileFiles.map { profileFile ->
            readLocalProfileSummary(profileFile)
        }
        val duplicateProfileIds = profileSummaries
            .map { profile -> profile.profileId }
            .filter { profileId -> profileId.isNotBlank() }
            .groupingBy { profileId -> profileId }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        val profiles = profileSummaries.map { summary ->
            val relation = relationAnalysis.profileRelations[summary.profileId]
            val linkedItemCount = relation?.itemCount ?: 0
            val extraWarnings = mutableListOf<String>()
            if (linkedItemCount == 0) {
                extraWarnings += "profil nima povezanih AAC elementov"
            }
            if (summary.profileId in duplicateProfileIds) {
                extraWarnings += "ID profila je podvojen"
            }
            if (linkedItemCount > SUSPICIOUS_ITEMS_PER_PROFILE) {
                extraWarnings += "profil ima sumljivo veliko AAC elementov"
            }
            summary.copy(
                linkedItemCount = linkedItemCount,
                missingIconCount = relation?.missingIconCount ?: 0,
                warnings = summary.warnings + extraWarnings
            )
        }

        return LocalAacOverview(
            profiles = profiles,
            hasAacItems = hasAacItems,
            iconCount = iconCount,
            duplicateProfileIdCount = duplicateProfileIds.size,
            relationAnalysis = relationAnalysis,
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
            append("AAC elementi brez lokalnega profila: ${overview.orphanItemCount}\n")
            append("Profili brez elementov: ${overview.zeroItemProfileCount}\n")
            append("Manjkajoce ikone v elementih: ${overview.relationAnalysis.missingIconReferenceCount}\n")
            append("Podvojeni AAC item ID-ji: ${overview.relationAnalysis.duplicateItemIdCount}\n")
            append("Podvojeni profil ID-ji: ${overview.duplicateProfileIdCount}\n")
            append("Neveljavni AAC elementi: ${overview.relationAnalysis.invalidItemCount}\n")
            append("Neveljavne ikonske reference: ${overview.relationAnalysis.invalidIconReferenceCount}\n")
            append("Zadnji uvoz: ${overview.lastImportSummary}")
        }
    }

    private fun buildFixedTopRowStatus(fixedItems: List<FixedTopRowItem>): String {
        return buildString {
            append("FIKSNA PRVA VRSTICA\n")
            append("3x3: prvi 3 fiksni, 4x4: prvi 4 fiksni, 5x5: vseh 5 fiksnih.\n\n")

            if (fixedItems.isEmpty()) {
                append("Ni nastavljenih fiksnih ikon v prvi vrstici.")
                return@buildString
            }

            val itemsByPosition = fixedItems.associateBy { it.position }
            for (position in 1..5) {
                val item = itemsByPosition[position]
                if (item == null) {
                    append("$position. ni nastavljeno - ne obstaja\n")
                } else {
                    append("$position. ${item.label.ifBlank { "brez oznake" }} (${item.itemId}) - obstaja\n")
                }
            }
        }.trimEnd()
    }

    private fun buildFixedTopRowAvailableItems(items: List<AacListItem>): String {
        val filteredItems = items.filter { item -> therapistIconSourceFilter.matches(item.iconSource) }
        return buildString {
            append("DOSTOPNI AAC ELEMENTI ZA FIKSNO VRSTICO\n")
            append("Filter vira ikon: ${therapistIconSourceFilter.label}\n")
            append("Filter vpliva samo na terapevtski seznam, ne na pacientov zaslon.\n")
            if (filteredItems.isEmpty()) {
                append("Ni najdenih AAC elementov za izbran vir.")
                return@buildString
            }
            filteredItems.take(20).forEach { item ->
                append("- ${item.itemId}: ${item.label.ifBlank { "brez oznake" }} (${item.iconSource.name})\n")
            }
            val remaining = filteredItems.size - 20
            if (remaining > 0) {
                append("... se $remaining")
            }
        }.trimEnd()
    }

    private fun showFixedTopRowItemChooser() {
        val items = buildLocalAacOverview()
            .relationAnalysis
            .availableItems
            .filter { item -> therapistIconSourceFilter.matches(item.iconSource) }
        if (items.isEmpty()) {
            txtStatus.text = "Ni AAC elementov za trenutni filter vira."
            return
        }
        val labels = items.map { item ->
            val title = item.label.ifBlank { item.itemId }
            "$title (${item.itemId}, ${item.iconSource.name})"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Izberi AAC element")
            .setItems(labels) { _, index ->
                val selectedItem = items[index]
                editFixedTopRowItemId.setText(selectedItem.itemId)
                txtStatus.text = "Izbran AAC element.\n${selectedItem.itemId}"
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun renderAacItemEditorList(items: List<AacListItem>) {
        aacItemListActions.removeAllViews()
        val filteredItems = items.filter { item -> therapistIconSourceFilter.matches(item.iconSource) }
        if (filteredItems.isEmpty()) {
            aacItemListActions.addView(buildAacItemListMessage("Ni AAC elementov za trenutni filter."))
            return
        }

        filteredItems.take(MAX_EDITOR_LIST_ITEMS).forEach { item ->
            val button = Button(this).apply {
                text = "${item.label.ifBlank { "brez oznake" }}\n${item.itemId} · ${item.iconSource.name}"
                setAllCaps(false)
                textSize = 15f
                setTextColor(0xFFF4F7FA.toInt())
                setBackgroundColor(0xFF34414D.toInt())
                setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
                setOnClickListener {
                    loadAacItemIntoEditor(item.itemId)
                }
            }
            aacItemListActions.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    64.dp()
                ).apply {
                    bottomMargin = 8.dp()
                }
            )
        }

        val remaining = filteredItems.size - MAX_EDITOR_LIST_ITEMS
        if (remaining > 0) {
            aacItemListActions.addView(
                buildAacItemListMessage("Prikazanih je prvih $MAX_EDITOR_LIST_ITEMS elementov. Preostalih: $remaining.")
            )
        }
    }

    private fun buildAacItemListMessage(message: String): TextView {
        return TextView(this).apply {
            text = message
            setTextColor(0xFFB8C0C8.toInt())
            textSize = 15f
            setPadding(0, 4.dp(), 0, 8.dp())
        }
    }

    private fun loadAacItemIntoEditor(itemId: String) {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val item = currentItemsArray(itemsText)?.let { findItemById(it, itemId) }
        if (item == null) {
            txtStatus.text = "AAC element ni bil najden.\n$itemId"
            return
        }

        editAacItemId.setText(item.optString("id").trim())
        editAacLabelSl.setText(itemLabel(item))
        editAacLabelUk.setText(item.optString("labelUk").ifBlank { itemLanguageText(item, "labelByLanguage", "uk") })
        editAacLabelEn.setText(item.optString("labelEn").ifBlank { itemLanguageText(item, "labelByLanguage", "en") })
        val baseLanguage = item.optString("baseLanguage").ifBlank { "sl" }
        editAacBaseLanguage.setText(baseLanguage)
        editAacActiveLanguages.setText(
            stringArrayValues(item.optJSONArray("activeLanguages"))
                .ifEmpty { listOf("sl") }
                .take(3)
                .joinToString(",")
        )
        editAacSpeechText.setText(
            item.optString("speechText")
                .ifBlank { itemLanguageText(item, "speechTextByLanguage", baseLanguage) }
                .ifBlank { item.optString("speakTextSl") }
        )
        val iconSource = itemIconSource(item)
        val imagePath = item.optString("imagePath")
            .ifBlank { item.optString("image_path") }
            .ifBlank { item.optString("icon") }
        editAacIconSource.setText(iconSource.name)
        editAacImagePath.setText(imagePath)
        editAacCategoryId.setText(item.optString("categoryId"))
        checkAacAddsToSentence.isChecked = item.optBoolean("addsToSentence", true)
        checkAacSpeaksImmediately.isChecked = item.optBoolean("speaksImmediately", true)
        checkAacOpensSubicons.isChecked = item.optBoolean("opensSubicons", false)
        loadLearningRepresentationChecks(item.optJSONArray("learningRepresentations"))
        val fixedTopRowPosition = itemFixedTopRowPosition(item)
        if (fixedTopRowPosition != null) {
            editFixedTopRowItemId.setText(itemId)
            editFixedTopRowPosition.setText(fixedTopRowPosition.toString())
        } else {
            editFixedTopRowItemId.setText(itemId)
            editFixedTopRowPosition.setText("")
        }
        updateAacImagePreview(imagePath, iconSource)
        txtStatus.text = "AAC element naložen v urejevalnik.\n$itemId"
    }

    private fun loadLearningRepresentationChecks(array: org.json.JSONArray?) {
        val modes = mutableSetOf<String>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val mode = array.optJSONObject(index)?.optString("mode")?.trim().orEmpty()
                if (mode.isNotBlank()) modes += mode
            }
        }
        checkLearningImageText.isChecked = modes.isEmpty() || "image_text" in modes
        checkLearningImageOnly.isChecked = "image_only" in modes
        checkLearningTextOnly.isChecked = "text_only" in modes
    }

    private fun itemLanguageText(item: org.json.JSONObject, objectKey: String, languageCode: String): String {
        return item.optJSONObject(objectKey)
            ?.optString(languageCode.trim().lowercase(Locale.ROOT))
            ?.trim()
            .orEmpty()
    }

    private fun refreshAacLanguageManagerStatus() {
        val activeLanguages = loadAacActiveLanguages()
        val baseLanguage = loadAacBaseLanguage()
        txtAacLanguageManagerStatus.text = buildString {
            append("Aktivni AAC jeziki: ${activeLanguages.joinToString(", ")}\n")
            append("Osnovni jezik: $baseLanguage\n")
            AacStoredTranslationCache.readLastPretranslationResult(this@AacPackSettingsActivity)?.let { result ->
                append("Zadnja priprava prevodov (${result.languageCode}): manjka ${result.missingCount}, prevedeno ${result.translatedCount}, napak ${result.failedCount}.\n")
            }
            append("Najvec 3 aktivni jeziki. Prevodi ostanejo shranjeni tudi, ce jezik ni aktiven.")
        }
        editAacActiveLanguages.setText(activeLanguages.joinToString(","))
        if (editAacBaseLanguage.text.isNullOrBlank()) {
            editAacBaseLanguage.setText(baseLanguage)
        }
    }

    private fun addAacActiveLanguage() {
        val newLanguage = normalizeAacLanguageCode(editAacLanguageCode.text.toString())
        if (newLanguage == null) {
            txtStatus.text = "Vnesi varen jezikovni kod, npr. sl, uk, en, de, hr, sr ali it."
            return
        }
        val activeLanguages = loadAacActiveLanguages()
        if (newLanguage in activeLanguages) {
            txtStatus.text = "Jezik je ze aktiven: $newLanguage"
            return
        }
        if (activeLanguages.size < 3) {
            saveAacActiveLanguages(activeLanguages + newLanguage)
            txtStatus.text = "Aktivni jezik dodan.\n$newLanguage"
            refreshAacLanguageManagerStatus()
            return
        }
        showReplaceActiveLanguageDialog(activeLanguages, newLanguage)
    }

    private fun showReplaceActiveLanguageDialog(activeLanguages: List<String>, newLanguage: String) {
        val baseLanguage = loadAacBaseLanguage()
        AlertDialog.Builder(this)
            .setTitle("Zamenjaj aktivni jezik")
            .setMessage("Aktivni so ze 3 jeziki. Prevodi ostanejo shranjeni tudi, ce jezik ni aktiven.")
            .setItems(activeLanguages.toTypedArray()) { _, index ->
                val replacedLanguage = activeLanguages[index]
                if (replacedLanguage == baseLanguage) {
                    txtStatus.text = "Osnovnega jezika $baseLanguage ni mogoce odstraniti. Najprej nastavi drug osnovni jezik."
                    return@setItems
                }
                val updatedLanguages = activeLanguages.toMutableList()
                updatedLanguages[index] = newLanguage
                saveAacActiveLanguages(updatedLanguages.distinct().take(3))
                txtStatus.text = "Aktivni jezik zamenjan.\n$replacedLanguage -> $newLanguage"
                refreshAacLanguageManagerStatus()
            }
            .setNegativeButton("Preklici", null)
            .show()
    }

    private fun setAacBaseLanguage() {
        val language = normalizeAacLanguageCode(editAacLanguageCode.text.toString())
            ?: normalizeAacLanguageCode(editAacBaseLanguage.text.toString())
        if (language == null) {
            txtStatus.text = "Vnesi varen osnovni jezik, npr. sl, uk ali en."
            return
        }
        val activeLanguages = loadAacActiveLanguages()
        if (language !in activeLanguages) {
            txtStatus.text = "Osnovni jezik mora biti med aktivnimi jeziki. Najprej dodaj $language."
            return
        }
        getSharedPreferences(AAC_LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AAC_BASE_LANGUAGE, language)
            .apply()
        editAacBaseLanguage.setText(language)
        txtStatus.text = "Osnovni AAC jezik nastavljen.\n$language"
        refreshAacLanguageManagerStatus()
    }

    private fun loadAacActiveLanguages(): List<String> {
        val raw = getSharedPreferences(AAC_LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AAC_ACTIVE_LANGUAGES, null)
        return raw
            ?.split(',', ';', ' ')
            ?.mapNotNull(::normalizeAacLanguageCode)
            ?.distinct()
            ?.take(3)
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("sl", "uk", "en")
    }

    private fun loadAacBaseLanguage(): String {
        val saved = getSharedPreferences(AAC_LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AAC_BASE_LANGUAGE, null)
        val activeLanguages = loadAacActiveLanguages()
        return normalizeAacLanguageCode(saved)
            ?.takeIf { it in activeLanguages }
            ?: activeLanguages.firstOrNull()
            ?: "sl"
    }

    private fun saveAacActiveLanguages(languages: List<String>) {
        val cleanLanguages = languages.mapNotNull(::normalizeAacLanguageCode).distinct().take(3).ifEmpty { listOf("sl") }
        val baseLanguage = loadAacBaseLanguage()
        val safeLanguages = if (baseLanguage in cleanLanguages) cleanLanguages else (listOf(baseLanguage) + cleanLanguages).distinct().take(3)
        getSharedPreferences(AAC_LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AAC_ACTIVE_LANGUAGES, safeLanguages.joinToString(","))
            .apply()
        val runtimePrefs = getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        val activeSpeechLanguage = runtimePrefs.getString(PREF_ACTIVE_SPEECH_LANGUAGE, "sl").orEmpty()
        runtimePrefs.edit()
            .putString(PREF_PATIENT_LANGUAGE_1, safeLanguages.getOrElse(0) { "sl" })
            .putString(PREF_PATIENT_LANGUAGE_2, safeLanguages.getOrElse(1) { safeLanguages.first() })
            .putString(PREF_PATIENT_LANGUAGE_3, safeLanguages.getOrElse(2) { safeLanguages.last() })
            .putString(
                PREF_ACTIVE_SPEECH_LANGUAGE,
                normalizeAacLanguageCode(activeSpeechLanguage)?.takeIf { it in safeLanguages } ?: safeLanguages.first()
            )
            .apply()
    }

    private fun normalizeAacLanguageCode(rawValue: String?): String? {
        val value = rawValue?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return value.takeIf { it.matches(Regex("[a-z]{2,5}")) }
    }

    private fun buildSourceActivationStatus(): String {
        val activeSources = loadActiveLibrarySources()
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText)
        return buildString {
            append("AKTIVACIJA VIROV ZA KNJIŽNICO\n")
            append("Filter vira je samo terapevtski pogled. Aktivacija ustvari samo generirane strani knjižnice.\n")
            LibraryIconSource.values().forEach { source ->
                val generatedCount = countGeneratedLibraryPlacements(itemsArray, source)
                val pageCount = generatedLibraryPageIds(itemsArray, source).size
                val activeText = if (source in activeSources) "aktivna" else "neaktivna"
                append("- ${source.label}: $activeText, strani: $pageCount, postavitve: $generatedCount\n")
            }
            append("Ročne pacientove postavitve se ne spreminjajo.")
        }.trimEnd()
    }

    private fun setLibrarySourceActive(source: LibraryIconSource, active: Boolean) {
        val result = if (active) {
            regenerateLibraryPlacements(source)
        } else {
            removeGeneratedLibraryPlacements(source)
        }
        when (result) {
            AacMetadataWriteResult.Success -> {
                updateActiveLibrarySource(source, active)
                txtStatus.text = if (active) {
                    "${source.label} knjižnica aktivirana."
                } else {
                    "${source.label} knjižnica deaktivirana."
                }
                refreshLocalAacOverview()
            }
            AacMetadataWriteResult.ItemNotFound -> {
                txtStatus.text = "Ni AAC elementov za vir ${source.label}."
            }
            AacMetadataWriteResult.WriteFailed -> {
                txtStatus.text = "Aktivacije vira ${source.label} ni bilo mogoče shraniti."
            }
        }
    }

    private fun regenerateLibraryPlacements(source: LibraryIconSource): AacMetadataWriteResult {
        return updateItemsJsonMetadata { itemsArray ->
            val candidates = libraryCandidates(itemsArray, source)
            if (candidates.isEmpty()) {
                return@updateItemsJsonMetadata AacMetadataWriteResult.ItemNotFound
            }
            removeGeneratedLibraryPlacementsFromArray(itemsArray, source)
            candidates.forEachIndexed { index, item ->
                val pageIndex = index / LIBRARY_PAGE_SIZE + 1
                val position = index % LIBRARY_PAGE_SIZE + 1
                val pageId = "library_${source.key}_$pageIndex"
                val placements = item.optJSONArray("placements") ?: org.json.JSONArray()
                placements.put(
                    org.json.JSONObject()
                        .put("pageId", pageId)
                        .put("position5x5", position)
                        .put("generated", true)
                        .put("placementSource", LIBRARY_PLACEMENT_SOURCE)
                )
                item.put("placements", placements)
            }
            AacMetadataWriteResult.Success
        }
    }

    private fun removeGeneratedLibraryPlacements(source: LibraryIconSource): AacMetadataWriteResult {
        return updateItemsJsonMetadata { itemsArray ->
            removeGeneratedLibraryPlacementsFromArray(itemsArray, source)
            AacMetadataWriteResult.Success
        }
    }

    private fun removeGeneratedLibraryPlacementsFromArray(itemsArray: org.json.JSONArray, source: LibraryIconSource) {
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val placements = item.optJSONArray("placements") ?: continue
            val preservedPlacements = org.json.JSONArray()
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                if (!isGeneratedLibraryPlacementForSource(placement, source)) {
                    preservedPlacements.put(placement)
                }
            }
            if (preservedPlacements.length() > 0) {
                item.put("placements", preservedPlacements)
            } else {
                item.remove("placements")
            }
        }
    }

    private fun libraryCandidates(itemsArray: org.json.JSONArray, source: LibraryIconSource): List<org.json.JSONObject> {
        return buildList {
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                if (source.matches(itemIconSource(item))) {
                    add(item)
                }
            }
        }.sortedWith(
            compareBy<org.json.JSONObject> { itemLabel(it).ifBlank { it.optString("id") }.lowercase(Locale.ROOT) }
                .thenBy { it.optString("id") }
        )
    }

    private fun countGeneratedLibraryPlacements(itemsArray: org.json.JSONArray?, source: LibraryIconSource): Int {
        if (itemsArray == null) return 0
        var count = 0
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val placements = item.optJSONArray("placements") ?: continue
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                if (isGeneratedLibraryPlacementForSource(placement, source)) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun generatedLibraryPageIds(itemsArray: org.json.JSONArray?, source: LibraryIconSource): Set<String> {
        if (itemsArray == null) return emptySet()
        return buildSet {
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                val placements = item.optJSONArray("placements") ?: continue
                for (placementIndex in 0 until placements.length()) {
                    val placement = placements.optJSONObject(placementIndex) ?: continue
                    if (isGeneratedLibraryPlacementForSource(placement, source)) {
                        val pageId = placement.optString("pageId").trim()
                        if (pageId.isNotBlank()) add(pageId)
                    }
                }
            }
        }
    }

    private fun isGeneratedLibraryPlacementForSource(
        placement: org.json.JSONObject,
        source: LibraryIconSource
    ): Boolean {
        val pageId = placement.optString("pageId").trim()
        return placement.optBoolean("generated", false) &&
            placement.optString("placementSource").trim() == LIBRARY_PLACEMENT_SOURCE &&
            pageId.startsWith("library_${source.key}_")
    }

    private fun loadActiveLibrarySources(): Set<LibraryIconSource> {
        val rawSources = getSharedPreferences(SOURCE_ACTIVATION_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_LIBRARY_SOURCES, "")
            .orEmpty()
        return rawSources.split(',')
            .mapNotNull { raw -> LibraryIconSource.fromKey(raw.trim()) }
            .toSet()
    }

    private fun updateActiveLibrarySource(source: LibraryIconSource, active: Boolean) {
        val updatedSources = loadActiveLibrarySources().toMutableSet()
        if (active) {
            updatedSources += source
        } else {
            updatedSources -= source
        }
        getSharedPreferences(SOURCE_ACTIVATION_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_LIBRARY_SOURCES, updatedSources.joinToString(",") { it.key })
            .apply()
    }

    private fun buildPlacementStatus(items: List<AacListItem>): String {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        return buildString {
            append("TRENUTNE POSTAVITVE\n")
            append("categoryId ni postavitev. Ena ikona ima lahko vec postavitev.\n")
            val placementLines = currentItemsArray(itemsText)
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val itemId = item.optString("id").trim()
                            val placements = item.optJSONArray("placements") ?: continue
                            for (placementIndex in 0 until placements.length()) {
                                val placement = placements.optJSONObject(placementIndex) ?: continue
                                val pageId = placement.optString("pageId").trim()
                                val position = placement.optInt("position5x5", 0)
                                if (itemId.isNotBlank() && pageId.isNotBlank() && position in 1..25) {
                                    add("- $itemId -> $pageId / $position")
                                }
                            }
                        }
                    }
                }
                .orEmpty()
            if (placementLines.isEmpty()) {
                append("Ni nastavljenih postavitev.")
            } else {
                placementLines.take(20).forEach { append("$it\n") }
                val remaining = placementLines.size - 20
                if (remaining > 0) append("... se $remaining")
            }
            if (items.isEmpty()) {
                append("\nAAC elementi niso najdeni.")
            }
        }.trimEnd()
    }

    private fun buildPatientPagesStatus(): String {
        val pages = loadPatientPages()
        val defaultPageId = defaultPatientPageId()
        return buildString {
            append("PACIENTOVE STRANI\n")
            append("Strani so terapevtsko določene. pageTitle je samo prikazno ime.\n")
            append("Knjižnične strani iz aktivacije virov so ločene in ne spreminjajo teh strani.\n")
            if (defaultPageId.isBlank()) {
                append("Začetna stran: varna privzeta stran aplikacije.\n")
            } else {
                append("Začetna stran: $defaultPageId\n")
            }
            if (pages.isEmpty()) {
                append("Ni shranjenih pacientovih strani.")
            } else {
                pages.forEach { page ->
                    val marker = if (page.pageId == defaultPageId) " (začetna)" else ""
                    append("- ${page.pageId}: ${page.pageTitle}$marker\n")
                }
            }
        }.trimEnd()
    }

    private fun savePatientPage() {
        val pageId = editPatientPageId.text.toString().trim()
        val pageTitle = editPatientPageTitle.text.toString().trim().ifBlank { pageId }
        if (!isSafePatientPageId(pageId)) {
            txtStatus.text = "ID strani lahko vsebuje samo črke, številke, _ ali -."
            return
        }
        val pages = loadPatientPages().toMutableList()
        val existingIndex = pages.indexOfFirst { it.pageId == pageId }
        val updatedPage = PatientPage(pageId = pageId, pageTitle = pageTitle)
        if (existingIndex >= 0) {
            pages[existingIndex] = updatedPage
            txtStatus.text = "Pacientova stran posodobljena.\n$pageId: $pageTitle"
        } else {
            pages += updatedPage
            txtStatus.text = "Pacientova stran dodana.\n$pageId: $pageTitle"
        }
        savePatientPages(pages)
        refreshLocalAacOverview()
    }

    private fun setDefaultPatientPage() {
        val pageId = editPatientPageId.text.toString().trim()
        if (!isSafePatientPageId(pageId)) {
            txtStatus.text = "Vnesi veljaven ID strani."
            return
        }
        val pages = loadPatientPages()
        if (pages.none { it.pageId == pageId }) {
            txtStatus.text = "Stran ne obstaja. Najprej jo shrani."
            return
        }
        getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_PATIENT_PAGE_ID, pageId)
            .apply()
        txtStatus.text = "Začetna pacientova stran nastavljena.\n$pageId"
        refreshLocalAacOverview()
    }

    private fun loadPatientPages(): List<PatientPage> {
        val rawPages = getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PATIENT_PAGES, "")
            .orEmpty()
        return rawPages.split(PATIENT_PAGE_SEPARATOR)
            .mapNotNull { raw ->
                val parts = raw.split(PATIENT_PAGE_FIELD_SEPARATOR, limit = 2)
                val pageId = parts.getOrNull(0).orEmpty().trim()
                val pageTitle = parts.getOrNull(1).orEmpty().trim()
                if (isSafePatientPageId(pageId)) {
                    PatientPage(pageId = pageId, pageTitle = pageTitle.ifBlank { pageId })
                } else {
                    null
                }
            }
            .distinctBy { it.pageId }
            .sortedBy { it.pageId.lowercase(Locale.ROOT) }
    }

    private fun savePatientPages(pages: List<PatientPage>) {
        val encodedPages = pages
            .filter { isSafePatientPageId(it.pageId) }
            .distinctBy { it.pageId }
            .sortedBy { it.pageId.lowercase(Locale.ROOT) }
            .joinToString(PATIENT_PAGE_SEPARATOR) { page ->
                page.pageId + PATIENT_PAGE_FIELD_SEPARATOR + page.pageTitle
                    .replace(PATIENT_PAGE_SEPARATOR, " ")
                    .replace(PATIENT_PAGE_FIELD_SEPARATOR, " ")
            }
        getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATIENT_PAGES, encodedPages)
            .apply()
    }

    private fun defaultPatientPageId(): String {
        return getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_PATIENT_PAGE_ID, "")
            .orEmpty()
            .trim()
            .takeIf { isSafePatientPageId(it) }
            .orEmpty()
    }

    private fun isSafePatientPageId(pageId: String): Boolean {
        return pageId.isNotBlank() && pageId.matches(Regex("[A-Za-z0-9_-]+"))
    }

    private fun buildSubiconStatus(): String {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        return buildString {
            append("TRENUTNE PODIKONE\n")
            append("Podikone so shranjene kot children pri starsu in visibleUnderIds pri otroku.\n")
            val childLines = currentItemsArray(itemsText)
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val parentId = item.optString("id").trim()
                            val children = item.optJSONArray("children") ?: continue
                            for (childIndex in 0 until children.length()) {
                                val childId = children.optString(childIndex).trim()
                                if (parentId.isNotBlank() && childId.isNotBlank()) {
                                    add("- $parentId -> $childId")
                                }
                            }
                        }
                    }
                }
                .orEmpty()
            if (childLines.isEmpty()) {
                append("Ni nastavljenih podikon.")
            } else {
                childLines.take(20).forEach { append("$it\n") }
                val remaining = childLines.size - 20
                if (remaining > 0) append("... se $remaining")
            }
        }.trimEnd()
    }

    private fun showSubiconItemChooser(targetParent: Boolean) {
        val items = buildLocalAacOverview()
            .relationAnalysis
            .availableItems
            .filter { item -> therapistIconSourceFilter.matches(item.iconSource) }
        if (items.isEmpty()) {
            txtStatus.text = "Ni AAC elementov za trenutni filter vira."
            return
        }
        val labels = items.map { item ->
            val title = item.label.ifBlank { item.itemId }
            "$title (${item.itemId}, ${item.iconSource.name})"
        }.toTypedArray()
        val title = if (targetParent) "Izberi starša" else "Izberi podikono"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, index ->
                val selectedItem = items[index]
                if (targetParent) {
                    editSubiconParentId.setText(selectedItem.itemId)
                    txtStatus.text = "Starš izbran.\n${selectedItem.itemId}"
                } else {
                    editSubiconChildId.setText(selectedItem.itemId)
                    txtStatus.text = "Podikona izbrana.\n${selectedItem.itemId}"
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun updateIconSourceFilterButtons() {
        iconSourceFilterButtons.forEach { (filter, button) ->
            val color = if (filter == therapistIconSourceFilter) 0xFF2F5F9E.toInt() else 0xFF34414D.toInt()
            button.backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun saveFixedTopRowPosition() {
        val itemId = editFixedTopRowItemId.text.toString().trim()
        val position = editFixedTopRowPosition.text.toString().trim().toIntOrNull()
        if (itemId.isBlank()) {
            txtStatus.text = "Vnesi ID AAC elementa."
            return
        }
        if (position == null || position !in 1..5) {
            txtStatus.text = "Pozicija mora biti 1-5."
            return
        }

        when (val result = writeFixedTopRowPosition(itemId, position)) {
            FixedTopRowWriteResult.Success -> {
                txtStatus.text = "Shranjeno.\n$itemId -> $position"
                refreshLocalAacOverview()
            }
            FixedTopRowWriteResult.ItemsFileMissing -> {
                txtStatus.text = "AAC elementi niso najdeni. Najprej pripravi lokalno AAC vsebino."
            }
            FixedTopRowWriteResult.ItemNotFound -> {
                txtStatus.text = "ID AAC elementa ne obstaja."
            }
            FixedTopRowWriteResult.WriteFailed -> {
                txtStatus.text = "Fiksne pozicije ni bilo mogoce shraniti."
            }
        }
    }

    private fun clearFixedTopRowPosition() {
        val position = editFixedTopRowPosition.text.toString().trim().toIntOrNull()
        if (position == null || position !in 1..5) {
            txtStatus.text = "Pozicija mora biti 1-5."
            return
        }

        when (clearFixedTopRowPositionInJson(position)) {
            FixedTopRowWriteResult.Success -> {
                txtStatus.text = "Pozicija počiščena."
                refreshLocalAacOverview()
            }
            FixedTopRowWriteResult.ItemsFileMissing -> {
                txtStatus.text = "AAC elementi niso najdeni. Najprej pripravi lokalno AAC vsebino."
            }
            FixedTopRowWriteResult.ItemNotFound -> {
                txtStatus.text = "Na poziciji $position ni fiksnega AAC elementa."
            }
            FixedTopRowWriteResult.WriteFailed -> {
                txtStatus.text = "Fiksne pozicije ni bilo mogoce počistiti."
            }
        }
    }

    private fun saveTherapistAacItem() {
        val labelSl = editAacLabelSl.text.toString().trim()
        if (labelSl.isBlank()) {
            txtStatus.text = "Vnesi slovenski tekst."
            return
        }

        val existingItemIds = currentAacItemIds()
        val rawItemId = editAacItemId.text.toString().trim()
        val itemId = if (rawItemId.isBlank()) {
            generateUniqueAacItemId(labelSl, existingItemIds)
        } else {
            rawItemId
        }
        if (itemId.isBlank()) {
            txtStatus.text = "ID AAC elementa ni varen."
            return
        }
        if (rawItemId.isNotBlank() && !rawItemId.matches(Regex("[A-Za-z0-9_-]+"))) {
            txtStatus.text = "ID AAC elementa sme vsebovati samo crke, stevilke, _ ali -."
            return
        }
        if (rawItemId.isBlank()) {
            editAacItemId.setText(itemId)
        }

        val iconSource = editAacIconSource.text.toString().trim().uppercase(Locale.ROOT)
        if (iconSource.isNotBlank() && parseLocalIconSource(iconSource) == null) {
            txtStatus.text = "Vir ikone mora biti SOCA, CUSTOM, ARASAAC ali SYSTEM."
            return
        }
        val imagePath = editAacImagePath.text.toString().trim()
        if (imagePath.isNotBlank() && isInvalidIconPath(imagePath)) {
            txtStatus.text = "Pot slike ni varna."
            return
        }

        val activeLanguages = parseTherapistLanguages(editAacActiveLanguages.text.toString())
        if (activeLanguages.size > 3) {
            txtStatus.text = "Aktivni jeziki: najvec 3."
            return
        }

        when (saveTherapistAacItemToJson(itemId, labelSl, activeLanguages)) {
            AacItemEditorWriteResult.SuccessCreated -> {
                txtStatus.text = "AAC element ustvarjen.\n$itemId"
                refreshLocalAacOverview()
            }
            AacItemEditorWriteResult.SuccessUpdated -> {
                txtStatus.text = "AAC element shranjen.\n$itemId"
                refreshLocalAacOverview()
            }
            AacItemEditorWriteResult.WriteFailed -> {
                txtStatus.text = "AAC elementa ni bilo mogoce shraniti."
            }
        }
    }

    private fun showAacImageChooser() {
        val iconSource = parseLocalIconSource(iconSourceForEditor())
        if (iconSource == null || iconSource == IconSource.SYSTEM) {
            txtStatus.text = "Najprej nastavi vir ikone: SOCA, CUSTOM ali ARASAAC."
            return
        }
        val sourceDir = iconSourceDir(iconSource)
        if (sourceDir?.isDirectory != true) {
            txtStatus.text = "Lokalna mapa za vir ${iconSource.name} ne obstaja."
            return
        }
        val imageFiles = sourceDir.walkTopDown()
            .filter { file -> file.isFile && isSupportedAacImageFile(file) }
            .sortedBy { file -> file.relativeTo(sourceDir).invariantSeparatorsPath.lowercase(Locale.ROOT) }
            .take(200)
            .toList()
        if (imageFiles.isEmpty()) {
            txtStatus.text = "V mapi ${iconSource.name} ni lokalnih slik."
            return
        }
        val labels = imageFiles.map { file ->
            file.relativeTo(sourceDir).invariantSeparatorsPath
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Izberi lokalno sliko")
            .setItems(labels) { _, index ->
                val selectedFile = imageFiles[index]
                val imagePath = relativeImagePathForIconSource(iconSource, selectedFile, sourceDir)
                editAacIconSource.setText(iconSource.name)
                editAacImagePath.setText(imagePath)
                updateAacImagePreview(imagePath, iconSource)
                txtStatus.text = "Slika izbrana.\n$imagePath"
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun updateAacImagePreview(imagePath: String, iconSource: IconSource?) {
        val source = iconSource ?: parseLocalIconSource(iconSourceForEditor())
        val imageFile = source?.let { AacStoragePaths.resolveIconFile(this, imagePath, it) }
        if (imageFile?.isFile != true) {
            imgAacImagePreview.setImageDrawable(null)
            return
        }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            imgAacImagePreview.setImageDrawable(null)
        } else {
            imgAacImagePreview.setImageBitmap(bitmap)
        }
    }

    private fun updatePlacement(add: Boolean) {
        val itemId = editPlacementItemId.text.toString().trim()
        val pageId = editPlacementPageId.text.toString().trim()
        val position = editPlacementPosition5x5.text.toString().trim().toIntOrNull()
        if (itemId.isBlank() || pageId.isBlank()) {
            txtStatus.text = "Vnesi ID elementa in stran."
            return
        }
        if (position == null || position !in 1..25) {
            txtStatus.text = "Pozicija mora biti 1-25."
            return
        }

        when (updatePlacementInJson(itemId, pageId, position, add)) {
            AacMetadataWriteResult.Success -> {
                txtStatus.text = if (add) "Postavitev dodana.\n$itemId -> $pageId / $position" else "Postavitev odstranjena."
                refreshLocalAacOverview()
            }
            AacMetadataWriteResult.ItemNotFound -> txtStatus.text = "ID AAC elementa ne obstaja."
            AacMetadataWriteResult.WriteFailed -> txtStatus.text = "Postavitve ni bilo mogoce shraniti."
        }
    }

    private fun updateSubicon(add: Boolean) {
        val parentId = editSubiconParentId.text.toString().trim()
        val childId = editSubiconChildId.text.toString().trim()
        if (parentId.isBlank() || childId.isBlank()) {
            txtStatus.text = "Vnesi starsa in podikono."
            return
        }
        if (parentId == childId) {
            txtStatus.text = "Ikona ne more biti svoja podikona."
            return
        }

        when (updateSubiconInJson(parentId, childId, add)) {
            AacMetadataWriteResult.Success -> {
                txtStatus.text = if (add) "Podikona dodana.\n$parentId -> $childId" else "Podikona odstranjena."
                refreshLocalAacOverview()
            }
            AacMetadataWriteResult.ItemNotFound -> txtStatus.text = "Stars ali podikona ne obstaja."
            AacMetadataWriteResult.WriteFailed -> txtStatus.text = "Podikone ni bilo mogoce shraniti."
        }
    }

    private fun updatePlacementInJson(
        itemId: String,
        pageId: String,
        position: Int,
        add: Boolean
    ): AacMetadataWriteResult {
        return updateItemsJsonMetadata { itemsArray ->
            val item = findItemById(itemsArray, itemId) ?: return@updateItemsJsonMetadata AacMetadataWriteResult.ItemNotFound
            val placements = item.optJSONArray("placements") ?: org.json.JSONArray()
            val nextPlacements = org.json.JSONArray()
            for (index in 0 until placements.length()) {
                val placement = placements.optJSONObject(index) ?: continue
                val samePlacement = placement.optString("pageId").trim() == pageId &&
                    placement.optInt("position5x5", 0) == position
                if (!samePlacement) {
                    nextPlacements.put(placement)
                }
            }
            if (add) {
                nextPlacements.put(org.json.JSONObject().put("pageId", pageId).put("position5x5", position))
            }
            if (nextPlacements.length() > 0) {
                item.put("placements", nextPlacements)
            } else {
                item.remove("placements")
            }
            AacMetadataWriteResult.Success
        }
    }

    private fun updateSubiconInJson(parentId: String, childId: String, add: Boolean): AacMetadataWriteResult {
        return updateItemsJsonMetadata { itemsArray ->
            val parentItem = findItemById(itemsArray, parentId)
            val childItem = findItemById(itemsArray, childId)
            if (parentItem == null || childItem == null) {
                return@updateItemsJsonMetadata AacMetadataWriteResult.ItemNotFound
            }
            parentItem.put("children", updateStringArray(parentItem.optJSONArray("children"), childId, add))
            val visibleUnderIds = updateStringArray(childItem.optJSONArray("visibleUnderIds"), parentId, add)
            childItem.put("visibleUnderIds", visibleUnderIds)
            childItem.put("isRootItem", visibleUnderIds.length() == 0)
            childItem.put("isHiddenUntilParent", visibleUnderIds.length() > 0)
            AacMetadataWriteResult.Success
        }
    }

    private fun updateStringArray(array: org.json.JSONArray?, value: String, add: Boolean): org.json.JSONArray {
        val values = linkedSetOf<String>()
        if (array != null) {
            for (index in 0 until array.length()) {
                val existing = array.optString(index).trim()
                if (existing.isNotBlank()) values += existing
            }
        }
        if (add) {
            values += value
        } else {
            values -= value
        }
        return org.json.JSONArray().apply {
            values.forEach { put(it) }
        }
    }

    private fun updateItemsJsonMetadata(update: (org.json.JSONArray) -> AacMetadataWriteResult): AacMetadataWriteResult {
        val itemsFile = AacStoragePaths.getAacItemsFile(this) ?: return AacMetadataWriteResult.WriteFailed
        val itemsText = readTextSafely(itemsFile, MAX_ITEMS_PREVIEW_BYTES) ?: return AacMetadataWriteResult.WriteFailed
        return try {
            val trimmed = itemsText.trimStart()
            val rootObject = if (trimmed.startsWith("[")) null else org.json.JSONObject(itemsText)
            val itemsArray = rootObject?.optJSONArray("items")
                ?: if (rootObject == null) org.json.JSONArray(itemsText) else return AacMetadataWriteResult.WriteFailed
            val result = update(itemsArray)
            if (result != AacMetadataWriteResult.Success) return result
            val output = rootObject?.toString(2) ?: itemsArray.toString(2)
            itemsFile.writeText(output, Charsets.UTF_8)
            AacMetadataWriteResult.Success
        } catch (error: Exception) {
            Log.w("NovaRehabAacEditor", "AAC placement/subicon metadata write failed", error)
            AacMetadataWriteResult.WriteFailed
        }
    }

    private fun currentItemsArray(itemsText: String?): org.json.JSONArray? {
        if (itemsText.isNullOrBlank()) return null
        return try {
            val trimmed = itemsText.trimStart()
            if (trimmed.startsWith("[")) {
                org.json.JSONArray(itemsText)
            } else {
                org.json.JSONObject(itemsText).optJSONArray("items")
            }
        } catch (error: Exception) {
            null
        }
    }

    private fun findItemById(itemsArray: org.json.JSONArray, itemId: String): org.json.JSONObject? {
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            if (item.optString("id").trim() == itemId) {
                return item
            }
        }
        return null
    }

    private fun saveTherapistAacItemToJson(
        itemId: String,
        labelSl: String,
        activeLanguages: List<String>
    ): AacItemEditorWriteResult {
        val itemsFile = AacStoragePaths.getAacItemsFile(this) ?: return AacItemEditorWriteResult.WriteFailed
        return try {
            val existingText = readTextSafely(itemsFile, MAX_ITEMS_PREVIEW_BYTES)
            val trimmed = existingText?.trimStart().orEmpty()
            val rootObject = when {
                trimmed.isBlank() -> org.json.JSONObject().put("items", org.json.JSONArray())
                trimmed.startsWith("[") -> null
                else -> org.json.JSONObject(existingText.orEmpty())
            }
            val itemsArray = rootObject?.optJSONArray("items")
                ?: if (rootObject == null && trimmed.isNotBlank()) org.json.JSONArray(existingText.orEmpty()) else org.json.JSONArray()

            var target: org.json.JSONObject? = null
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                if (item.optString("id").trim() == itemId) {
                    target = item
                    break
                }
            }

            val created = target == null
            val item = target ?: org.json.JSONObject().also { newItem ->
                newItem.put("id", itemId)
                newItem.put("actionType", "speak")
                newItem.put("targetPageId", "")
                newItem.put("isRootItem", true)
                newItem.put("priority", itemsArray.length())
                itemsArray.put(newItem)
            }

            item.put("labelSl", labelSl)
            putOptionalString(item, "labelUk", editAacLabelUk.text.toString())
            putOptionalString(item, "labelEn", editAacLabelEn.text.toString())
            putOptionalString(item, "baseLanguage", editAacBaseLanguage.text.toString().ifBlank { "sl" })
            item.put("activeLanguages", org.json.JSONArray().apply {
                activeLanguages.ifEmpty { listOf("sl") }.take(3).forEach { put(it) }
            })
            putOptionalString(item, "speechText", editAacSpeechText.text.toString())
            putOptionalString(item, "iconSource", iconSourceForEditor())
            putOptionalString(item, "imagePath", editAacImagePath.text.toString())
            item.remove("image_path")
            item.remove("icon")
            putOptionalString(item, "categoryId", editAacCategoryId.text.toString())
            item.put("addsToSentence", checkAacAddsToSentence.isChecked)
            item.put("speaksImmediately", checkAacSpeaksImmediately.isChecked)
            item.put("opensSubicons", checkAacOpensSubicons.isChecked)
            item.put("labelByLanguage", buildLabelByLanguageJson(item.optJSONObject("labelByLanguage"), labelSl))
            item.put("speechTextByLanguage", buildSpeechByLanguageJson(item.optJSONObject("speechTextByLanguage")))
            val learningRepresentations = buildLearningRepresentationsJson()
            if (learningRepresentations.length() > 0) {
                item.put("learningRepresentations", learningRepresentations)
            }

            val parentDir = itemsFile.parentFile ?: return AacItemEditorWriteResult.WriteFailed
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                return AacItemEditorWriteResult.WriteFailed
            }
            val output = rootObject?.toString(2) ?: itemsArray.toString(2)
            itemsFile.writeText(output, Charsets.UTF_8)
            if (created) AacItemEditorWriteResult.SuccessCreated else AacItemEditorWriteResult.SuccessUpdated
        } catch (error: Exception) {
            Log.w("NovaRehabAacEditor", "AAC item editor write failed", error)
            AacItemEditorWriteResult.WriteFailed
        }
    }

    private fun iconSourceForEditor(): String {
        return editAacIconSource.text.toString().trim().uppercase(Locale.ROOT).ifBlank { "SYSTEM" }
    }

    private fun currentAacItemIds(): Set<String> {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText) ?: return emptySet()
        return buildSet {
            for (index in 0 until itemsArray.length()) {
                val itemId = itemsArray.optJSONObject(index)?.optString("id")?.trim().orEmpty()
                if (itemId.isNotBlank()) add(itemId)
            }
        }
    }

    private fun generateUniqueAacItemId(label: String, existingIds: Set<String>): String {
        val baseId = sanitizeAacItemId(label).ifBlank { "aac_item" }
        if (baseId !in existingIds) return baseId
        for (suffix in 2..999) {
            val candidate = "${baseId}_$suffix"
            if (candidate !in existingIds) return candidate
        }
        return ""
    }

    private fun sanitizeAacItemId(rawValue: String): String {
        val normalized = Normalizer.normalize(rawValue.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .trim('_', '-')
        return normalized.take(80)
    }

    private fun iconSourceDir(iconSource: IconSource): File? {
        return when (iconSource) {
            IconSource.SOCA -> AacStoragePaths.getIconsSocaDir(this)
            IconSource.CUSTOM,
            IconSource.PATIENT -> AacStoragePaths.getIconsCustomDir(this)
            IconSource.ARASAAC -> AacStoragePaths.getIconsArasaacDir(this)
            IconSource.SYSTEM -> null
        }
    }

    private fun relativeImagePathForIconSource(iconSource: IconSource, imageFile: File, sourceDir: File): String {
        val relativePath = imageFile.relativeTo(sourceDir).invariantSeparatorsPath
        val prefix = when (iconSource) {
            IconSource.SOCA -> "soca"
            IconSource.CUSTOM,
            IconSource.PATIENT -> "custom"
            IconSource.ARASAAC -> "arasaac"
            IconSource.SYSTEM -> ""
        }
        return if (prefix.isBlank()) relativePath else "$prefix/$relativePath"
    }

    private fun isSupportedAacImageFile(file: File): Boolean {
        return when (file.extension.lowercase(Locale.ROOT)) {
            "png", "jpg", "jpeg", "webp" -> true
            else -> false
        }
    }

    private fun parseTherapistLanguages(rawValue: String): List<String> {
        return rawValue.split(',', ';', ' ')
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun putOptionalString(item: org.json.JSONObject, key: String, value: String) {
        val cleanValue = value.trim()
        if (cleanValue.isBlank()) {
            item.remove(key)
        } else {
            item.put(key, cleanValue)
        }
    }

    private fun buildLabelByLanguageJson(existing: org.json.JSONObject?, labelSl: String): org.json.JSONObject {
        return org.json.JSONObject(existing?.toString() ?: "{}").apply {
            put("sl", labelSl)
            val labelUk = editAacLabelUk.text.toString().trim()
            val labelEn = editAacLabelEn.text.toString().trim()
            if (labelUk.isNotBlank()) put("uk", labelUk)
            if (labelEn.isNotBlank()) put("en", labelEn)
        }
    }

    private fun buildSpeechByLanguageJson(existing: org.json.JSONObject?): org.json.JSONObject {
        return org.json.JSONObject(existing?.toString() ?: "{}").apply {
            val speechText = editAacSpeechText.text.toString().trim()
            if (speechText.isNotBlank()) {
                put(editAacBaseLanguage.text.toString().trim().lowercase(Locale.ROOT).ifBlank { "sl" }, speechText)
            }
        }
    }

    private fun buildLearningRepresentationsJson(): org.json.JSONArray {
        return org.json.JSONArray().apply {
            if (checkLearningImageText.isChecked) put(org.json.JSONObject().put("mode", "image_text"))
            if (checkLearningImageOnly.isChecked) put(org.json.JSONObject().put("mode", "image_only"))
            if (checkLearningTextOnly.isChecked) put(org.json.JSONObject().put("mode", "text_only"))
        }
    }

    private fun writeFixedTopRowPosition(itemId: String, position: Int): FixedTopRowWriteResult {
        return updateFixedTopRowItemsJson { itemsArray ->
            var targetFound = false
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                if (itemFixedTopRowPosition(item) == position) {
                    item.remove("fixedTopRowPosition")
                    item.remove("fixed_top_row_position")
                }
                if (item.optString("id").trim() == itemId) {
                    item.remove("fixed_top_row_position")
                    item.put("fixedTopRowPosition", position)
                    targetFound = true
                }
            }
            if (targetFound) FixedTopRowWriteResult.Success else FixedTopRowWriteResult.ItemNotFound
        }
    }

    private fun clearFixedTopRowPositionInJson(position: Int): FixedTopRowWriteResult {
        return updateFixedTopRowItemsJson { itemsArray ->
            var cleared = false
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                if (itemFixedTopRowPosition(item) == position) {
                    item.remove("fixedTopRowPosition")
                    item.remove("fixed_top_row_position")
                    cleared = true
                }
            }
            if (cleared) FixedTopRowWriteResult.Success else FixedTopRowWriteResult.ItemNotFound
        }
    }

    private fun updateFixedTopRowItemsJson(
        update: (org.json.JSONArray) -> FixedTopRowWriteResult
    ): FixedTopRowWriteResult {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
            ?: return FixedTopRowWriteResult.ItemsFileMissing
        val itemsText = readTextSafely(itemsFile, MAX_ITEMS_PREVIEW_BYTES)
            ?: return FixedTopRowWriteResult.ItemsFileMissing

        return try {
            val trimmed = itemsText.trimStart()
            val rootObject = if (trimmed.startsWith("[")) null else org.json.JSONObject(itemsText)
            val itemsArray = rootObject?.optJSONArray("items")
                ?: if (rootObject == null) org.json.JSONArray(itemsText) else return FixedTopRowWriteResult.WriteFailed
            val updateResult = update(itemsArray)
            if (updateResult != FixedTopRowWriteResult.Success) {
                return updateResult
            }

            val output = rootObject?.toString(2) ?: itemsArray.toString(2)
            itemsFile.writeText(output, Charsets.UTF_8)
            FixedTopRowWriteResult.Success
        } catch (error: Exception) {
            Log.w("NovaRehabAacTopRow", "Fixed top-row metadata write failed", error)
            FixedTopRowWriteResult.WriteFailed
        }
    }

    private fun buildLocalAacProfilesReport(overview: LocalAacOverview): String {
        return buildString {
            append("LOKALNI AAC PROFILI\n")
            append("AAC elementi: ${if (overview.hasAacItems) "prisotni" else "niso prisotni"}\n")
            append("Lokalne ikone skupaj: ${overview.iconCount}\n\n")

            if (overview.profiles.isEmpty()) {
                append("Ni lokalnih AAC profilov.")
                return@buildString
            }

            overview.profiles.forEach { summary ->
                append("- ${summary.displayName}\n")
                append("  Datoteka: ${summary.fileName}\n")
                append("  ID profila: ${summary.profileId.ifBlank { "ni podatka" }}\n")
                append("  Povezani AAC elementi: ${summary.linkedItemCount}\n")
                append("  Manjkajoce ikone v povezanih elementih: ${summary.missingIconCount}\n")
                append("  Velikost JSON: ${formatSize(summary.fileSizeBytes)}\n")
                if (summary.warnings.isNotEmpty()) {
                    append("  Opozorila: ${summary.warnings.joinToString("; ")}\n")
                }
            }
        }.trimEnd()
    }

    private fun renderLocalProfileActions(overview: LocalAacOverview) {
        localProfileActions.removeAllViews()
        val selectedProfile = loadSelectedProfile()

        overview.profiles.forEach { profile ->
            val isSelected = selectedProfile?.profileId == profile.profileId
            val button = Button(this).apply {
                text = if (isSelected) {
                    "AKTIVEN PROFIL - ${profile.displayName}"
                } else {
                    "AKTIVIRAJ PROFIL - ${profile.displayName}"
                }
                isEnabled = !isSelected
                setAllCaps(false)
                setTextColor(0xFFF4F7FA.toInt())
                textSize = 18f
                backgroundTintList = ColorStateList.valueOf(
                    if (isSelected) BUSY_BUTTON_COLOR else SHARE_READY_COLOR
                )
                setOnClickListener {
                    activateLocalProfile(profile)
                }
            }
            localProfileActions.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    64.dp()
                ).apply {
                    bottomMargin = 12.dp()
                }
            )
        }
    }

    private fun activateLocalProfile(profile: LocalProfileSummary) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())
        getSharedPreferences(PROFILE_SELECTION_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_PROFILE_ID, profile.profileId)
            .putString(KEY_SELECTED_PROFILE_NAME, profile.displayName)
            .putString(KEY_SELECTED_PROFILE_TIMESTAMP, timestamp)
            .apply()

        txtStatus.text = buildString {
            append("AAC profil izbran za terapevtsko nastavitev.\n")
            append("Profil: ${profile.displayName}\n")
            append("ID: ${profile.profileId}\n\n")
            append("AAC komunikator se ni samodejno ponovno zagnal.")
        }
        refreshLocalAacOverview()
    }

    private fun buildActiveProfileStatus(): String {
        val selectedProfile = loadSelectedProfile()
            ?: return "AKTIVNI AAC PROFIL\nNi izbranega lokalnega AAC profila."

        return buildString {
            append("AKTIVNI AAC PROFIL\n")
            append("Profil: ${selectedProfile.displayName}\n")
            append("ID: ${selectedProfile.profileId}\n")
            append("Izbran: ${selectedProfile.selectedAt}")
        }
    }

    private fun buildIconFolderStatus(): String {
        return buildString {
            append("LOKALNE AAC IKONE\n")
            append(formatIconFolderLine("Soča", AacStoragePaths.getIconsSocaDir(this@AacPackSettingsActivity)))
            append('\n')
            append(formatIconFolderLine("Custom", AacStoragePaths.getIconsCustomDir(this@AacPackSettingsActivity)))
            append('\n')
            append(formatIconFolderLine("ARASAAC", AacStoragePaths.getIconsArasaacDir(this@AacPackSettingsActivity)))
            append("\nKopiraj na primer: soca/voda.png, soca/wc.png, soca/pomoc.png, soca/boli.png")
            append("\nSoča test PNG:\n")
            append(formatSocaStarterFileLine(AacStoragePaths.SOCA_STARTER_WATER_ICON))
            append('\n')
            append(formatSocaStarterFileLine(AacStoragePaths.SOCA_STARTER_WC_ICON))
            append('\n')
            append(formatSocaStarterFileLine(AacStoragePaths.SOCA_STARTER_HELP_ICON))
            append('\n')
            append(formatSocaStarterFileLine(AacStoragePaths.SOCA_STARTER_PAIN_ICON))
        }
    }

    private fun formatIconFolderLine(label: String, dir: File?): String {
        val existsText = if (dir?.isDirectory == true) "obstaja" else "manjka"
        val pngCount = dir
            ?.takeIf { it.isDirectory }
            ?.listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            ?.size
            ?: 0
        return "$label: $existsText, PNG: $pngCount"
    }

    private fun formatSocaStarterFileLine(fileName: String): String {
        val socaDir = AacStoragePaths.getIconsSocaDir(this)
        val file = socaDir?.let { File(it, fileName) }
        val status = if (file?.isFile == true && file.extension.equals("png", ignoreCase = true)) {
            "OK"
        } else {
            "MISSING"
        }
        return "$fileName: $status"
    }

    private fun loadSelectedProfile(): SelectedProfile? {
        val prefs = getSharedPreferences(PROFILE_SELECTION_PREFS_NAME, Context.MODE_PRIVATE)
        val profileId = prefs.getString(KEY_SELECTED_PROFILE_ID, null)?.trim().orEmpty()
        val displayName = prefs.getString(KEY_SELECTED_PROFILE_NAME, null)?.trim().orEmpty()
        val selectedAt = prefs.getString(KEY_SELECTED_PROFILE_TIMESTAMP, null)?.trim().orEmpty()
        if (profileId.isEmpty()) {
            return null
        }
        return SelectedProfile(
            profileId = profileId,
            displayName = displayName.ifBlank { profileId },
            selectedAt = selectedAt.ifBlank { "ni podatka" }
        )
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
                linkedItemCount = 0,
                missingIconCount = 0
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
                linkedItemCount = 0,
                missingIconCount = 0
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
                linkedItemCount = 0,
                missingIconCount = 0
            )
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

    private fun analyzeLocalAacRelations(itemsFile: File?): AacRelationAnalysis {
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
            ?: return AacRelationAnalysis.empty()

        return try {
            val itemsArray = when {
                itemsText.trimStart().startsWith("[") -> org.json.JSONArray(itemsText)
                else -> org.json.JSONObject(itemsText).optJSONArray("items") ?: org.json.JSONArray()
            }
            val mutableRelations = mutableMapOf<String, MutableProfileRelation>()
            val itemIds = mutableListOf<String>()
            var orphanItemCount = 0
            var missingIconReferenceCount = 0
            var invalidItemCount = 0
            var invalidIconReferenceCount = 0
            val fixedTopRowItems = mutableListOf<FixedTopRowItem>()
            val availableItems = mutableListOf<AacListItem>()

            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                val itemWarnings = mutableListOf<String>()
                val itemId = item.optString("id").trim()
                if (itemId.isBlank()) {
                    itemWarnings += "AAC element nima ID"
                } else {
                    itemIds += itemId
                }
                if (!itemHasLabelOrText(item)) {
                    itemWarnings += "AAC element nima oznake/besedila"
                }
                val profileIds = itemProfileIds(item)
                if (profileIds.isEmpty()) {
                    orphanItemCount += 1
                    itemWarnings += "AAC element nima reference na profil"
                }

                val iconStatus = itemIconStatus(item)
                if (iconStatus.isMissing) {
                    missingIconReferenceCount += 1
                }
                if (iconStatus.isInvalid) {
                    invalidIconReferenceCount += 1
                    itemWarnings += "AAC element ima neveljavno ikonsko referenco"
                }
                if (itemWarnings.isNotEmpty()) {
                    invalidItemCount += 1
                }
                if (itemId.isNotBlank()) {
                    availableItems += AacListItem(
                        itemId = itemId,
                        label = itemLabel(item),
                        iconSource = itemIconSource(item)
                    )
                }
                val fixedTopRowPosition = itemFixedTopRowPosition(item)
                if (fixedTopRowPosition != null && itemId.isNotBlank()) {
                    fixedTopRowItems += FixedTopRowItem(
                        position = fixedTopRowPosition,
                        label = itemLabel(item),
                        itemId = itemId
                    )
                }

                profileIds.forEach { profileId ->
                    val relation = mutableRelations.getOrPut(profileId) { MutableProfileRelation() }
                    relation.itemCount += 1
                    if (iconStatus.isMissing) {
                        relation.missingIconCount += 1
                    }
                }
            }
            val duplicateItemIdCount = itemIds
                .groupingBy { itemId -> itemId }
                .eachCount()
                .count { (_, count) -> count > 1 }

            AacRelationAnalysis(
                profileRelations = mutableRelations.mapValues { (_, relation) ->
                    ProfileRelation(
                        itemCount = relation.itemCount,
                        missingIconCount = relation.missingIconCount
                    )
                },
                orphanItemCount = orphanItemCount,
                missingIconReferenceCount = missingIconReferenceCount,
                duplicateItemIdCount = duplicateItemIdCount,
                invalidItemCount = invalidItemCount,
                invalidIconReferenceCount = invalidIconReferenceCount,
                fixedTopRowItems = fixedTopRowItems
                    .sortedWith(compareBy<FixedTopRowItem> { it.position }.thenBy { it.itemId })
                    .distinctBy { it.position },
                availableItems = availableItems
                    .sortedWith(compareBy<AacListItem> { it.label.ifBlank { it.itemId }.lowercase(Locale.ROOT) }.thenBy { it.itemId })
                    .distinctBy { it.itemId }
            )
        } catch (error: Exception) {
            AacRelationAnalysis.empty()
        }
    }

    private fun itemHasLabelOrText(item: org.json.JSONObject): Boolean {
        return listOf("label", "labelSl", "text", "speakTextSl", "name")
            .any { key -> item.optString(key).trim().isNotEmpty() }
    }

    private fun itemLabel(item: org.json.JSONObject): String {
        listOf("labelSl", "label", "text", "speakTextSl", "name").forEach { key ->
            val value = item.optString(key).trim()
            if (value.isNotEmpty()) {
                return value
            }
        }
        return ""
    }

    private fun itemFixedTopRowPosition(item: org.json.JSONObject): Int? {
        val value = when {
            item.has("fixedTopRowPosition") -> item.optInt("fixedTopRowPosition", 0)
            item.has("fixed_top_row_position") -> item.optInt("fixed_top_row_position", 0)
            else -> 0
        }
        return value.takeIf { it in 1..5 }
    }

    private fun itemProfileIds(item: org.json.JSONObject): List<String> {
        val directIds = listOf("profileId", "profile_id", "profile")
            .mapNotNull { key ->
                val profileId = item.optString(key).trim()
                if (profileId.isEmpty()) null else profileId
            }
        val arrayIds = listOf("profileIds", "profile_ids", "profiles")
            .flatMap { key -> stringArrayValues(item.optJSONArray(key)) }
        return (directIds + arrayIds).distinct()
    }

    private fun stringArrayValues(array: org.json.JSONArray?): List<String> {
        if (array == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }

    private fun itemIconStatus(item: org.json.JSONObject): IconReferenceStatus {
        val imagePath = item.optString("imagePath")
            .ifBlank { item.optString("image_path") }
            .ifBlank { item.optString("icon") }
            .trim()
        if (imagePath.isBlank()) {
            return IconReferenceStatus(isMissing = false, isInvalid = false)
        }
        val rawIconSource = item.optString("iconSource").ifBlank { item.optString("icon_source") }
        val iconSource = parseLocalIconSource(rawIconSource)
        val invalidSource = rawIconSource.isNotBlank() && iconSource == null
        val invalidPath = isInvalidIconPath(imagePath)
        if (invalidSource || invalidPath) {
            return IconReferenceStatus(isMissing = false, isInvalid = true)
        }
        if (iconSource == null || iconSource == IconSource.SYSTEM) {
            return IconReferenceStatus(isMissing = false, isInvalid = false)
        }
        val resolvedIcon = AacStoragePaths.resolveIconFile(this, imagePath, iconSource)
        return IconReferenceStatus(
            isMissing = resolvedIcon != null && !resolvedIcon.exists(),
            isInvalid = resolvedIcon == null
        )
    }

    private fun parseLocalIconSource(rawSource: String): IconSource? {
        return when (rawSource.trim().uppercase(Locale.ROOT)) {
            "SOCA" -> IconSource.SOCA
            "ARASAAC" -> IconSource.ARASAAC
            "CUSTOM" -> IconSource.CUSTOM
            "PATIENT" -> IconSource.PATIENT
            "SYSTEM", "" -> IconSource.SYSTEM
            else -> null
        }
    }

    private fun itemIconSource(item: org.json.JSONObject): IconSource {
        val rawIconSource = item.optString("iconSource").ifBlank { item.optString("icon_source") }
        return parseLocalIconSource(rawIconSource) ?: IconSource.SYSTEM
    }

    private fun isInvalidIconPath(imagePath: String): Boolean {
        val normalizedPath = imagePath.replace('\\', '/')
        return normalizedPath.contains("../") ||
            normalizedPath.startsWith("/") ||
            Regex("^[A-Za-z]:").containsMatchIn(normalizedPath)
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
        val duplicateProfileIdCount: Int,
        val relationAnalysis: AacRelationAnalysis,
        val lastImportSummary: String
    ) {
        val suspiciousProfileCount: Int
            get() = profiles.count { it.warnings.isNotEmpty() }

        val emptyOrInvalidProfileCount: Int
            get() = profiles.count { it.isEmptyOrInvalid }

        val zeroItemProfileCount: Int
            get() = profiles.count { it.linkedItemCount == 0 }

        val orphanItemCount: Int
            get() {
                val localProfileIds = profiles.map { it.profileId }.toSet()
                val missingProfileItemCount = relationAnalysis.profileRelations
                    .filterKeys { profileId -> profileId !in localProfileIds }
                    .values
                    .sumOf { relation -> relation.itemCount }
                return relationAnalysis.orphanItemCount + missingProfileItemCount
            }
    }

    private data class LocalProfileSummary(
        val fileName: String,
        val displayName: String,
        val profileId: String,
        val fileSizeBytes: Long,
        val warnings: List<String>,
        val isEmptyOrInvalid: Boolean,
        val linkedItemCount: Int,
        val missingIconCount: Int
    )

    private data class AacRelationAnalysis(
        val profileRelations: Map<String, ProfileRelation>,
        val orphanItemCount: Int,
        val missingIconReferenceCount: Int,
        val duplicateItemIdCount: Int,
        val invalidItemCount: Int,
        val invalidIconReferenceCount: Int,
        val fixedTopRowItems: List<FixedTopRowItem>,
        val availableItems: List<AacListItem>
    ) {
        companion object {
            fun empty(): AacRelationAnalysis {
                return AacRelationAnalysis(
                    profileRelations = emptyMap(),
                    orphanItemCount = 0,
                    missingIconReferenceCount = 0,
                    duplicateItemIdCount = 0,
                    invalidItemCount = 0,
                    invalidIconReferenceCount = 0,
                    fixedTopRowItems = emptyList(),
                    availableItems = emptyList()
                )
            }
        }
    }

    private data class FixedTopRowItem(
        val position: Int,
        val label: String,
        val itemId: String
    )

    private data class AacListItem(
        val itemId: String,
        val label: String,
        val iconSource: IconSource
    )

    private data class PatientPage(
        val pageId: String,
        val pageTitle: String
    )

    private enum class TherapistIconSourceFilter(val label: String) {
        ALL("ALL"),
        SOCA("SOCA"),
        CUSTOM("CUSTOM"),
        ARASAAC("ARASAAC"),
        SYSTEM("SYSTEM");

        fun matches(iconSource: IconSource): Boolean {
            return when (this) {
                ALL -> true
                SOCA -> iconSource == IconSource.SOCA
                CUSTOM -> iconSource == IconSource.CUSTOM || iconSource == IconSource.PATIENT
                ARASAAC -> iconSource == IconSource.ARASAAC
                SYSTEM -> iconSource == IconSource.SYSTEM
            }
        }
    }

    private enum class LibraryIconSource(val key: String, val label: String) {
        SOCA("soca", "SOCA"),
        CUSTOM("custom", "CUSTOM"),
        ARASAAC("arasaac", "ARASAAC");

        fun matches(iconSource: IconSource): Boolean {
            return when (this) {
                SOCA -> iconSource == IconSource.SOCA
                CUSTOM -> iconSource == IconSource.CUSTOM || iconSource == IconSource.PATIENT
                ARASAAC -> iconSource == IconSource.ARASAAC
            }
        }

        companion object {
            fun fromKey(value: String): LibraryIconSource? {
                return values().firstOrNull { it.key == value.trim().lowercase(Locale.ROOT) }
            }
        }
    }

    private enum class FixedTopRowWriteResult {
        Success,
        ItemsFileMissing,
        ItemNotFound,
        WriteFailed
    }

    private enum class AacItemEditorWriteResult {
        SuccessCreated,
        SuccessUpdated,
        WriteFailed
    }

    private enum class AacMetadataWriteResult {
        Success,
        ItemNotFound,
        WriteFailed
    }

    private data class ProfileRelation(
        val itemCount: Int,
        val missingIconCount: Int
    )

    private data class MutableProfileRelation(
        var itemCount: Int = 0,
        var missingIconCount: Int = 0
    )

    private data class IconReferenceStatus(
        val isMissing: Boolean,
        val isInvalid: Boolean
    )

    private data class SelectedProfile(
        val profileId: String,
        val displayName: String,
        val selectedAt: String
    )

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private sealed class TestZipResult {
        data class Success(
            val zipFile: File,
            val fileCount: Int
        ) : TestZipResult()

        data class Failure(val reason: String) : TestZipResult()
    }
}
