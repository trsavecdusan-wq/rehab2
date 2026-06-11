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
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.rehab2.aac.AacContentBootstrap
import com.rehab2.aac.AacCoreV2HomeRepair
import com.rehab2.aac.AacIconZipImporter
import com.rehab2.aac.AacLocalJsonLoader
import com.rehab2.aac.AacPackExporter
import com.rehab2.aac.AacPackImporter
import com.rehab2.aac.AacPackImportPreflight
import com.rehab2.aac.AacStarterContentV1
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
        private const val AAC_GRID_PREFS_NAME = "aac_grid_settings"
        private const val KEY_AAC_GRID_SIZE = "aac_grid_size"
        private const val KEY_SHOW_SUBICONS_ON_MAIN_PAGES = "show_subicons_on_main_pages"
        private const val DEFAULT_AAC_GRID_SIZE = 4
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
        private val STARTER_DIAGNOSTIC_PRIORITY = listOf(
            "no",
            "yes",
            "dont_understand",
            "thank_you",
            "sorry",
            "help",
            "wait",
            "water",
            "coffee",
            "diaper",
            "body_position",
            "uncomfortable",
            "pain",
            "bad",
            "tired",
            "afraid",
            "not_safe",
            "stop_movement",
            "fear_falling"
        )
    }

    private lateinit var btnExport: Button
    private lateinit var btnShare: Button
    private lateinit var btnCreateTestZip: Button
    private lateinit var btnImportPreflight: Button
    private lateinit var btnImportIconZip: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtLastImportStatus: TextView
    private lateinit var txtAacHealthSummary: TextView
    private lateinit var communicatorDashboardActions: LinearLayout
    private lateinit var txtAacBootstrapStatus: TextView
    private lateinit var btnRepairAacBootstrap: Button
    private lateinit var btnCoreV2HomeRepair: Button
    private lateinit var txtAacGridSizeStatus: TextView
    private lateinit var btnAacGrid3x3: Button
    private lateinit var btnAacGrid4x4: Button
    private lateinit var btnAacGrid5x5: Button
    private lateinit var btnAacGrid6x6: Button
    private lateinit var txtShowSubiconsOnMainPagesStatus: TextView
    private lateinit var btnToggleShowSubiconsOnMainPages: Button
    private lateinit var txtFixedTopRowStatus: TextView
    private lateinit var editFixedTopRowItemId: EditText
    private lateinit var editFixedTopRowPosition: EditText
    private lateinit var btnChooseFixedTopRowItem: Button
    private lateinit var btnSaveFixedTopRowPosition: Button
    private lateinit var btnClearFixedTopRowPosition: Button
    private lateinit var fixedTopRowVisualActions: LinearLayout
    private lateinit var previewFixedTopRowActions: LinearLayout
    private lateinit var previewGridActions: LinearLayout
    private lateinit var txtFixedTopRowAvailableItems: TextView
    private lateinit var iconSourceFilterButtons: Map<TherapistIconSourceFilter, Button>
    private lateinit var txtSourceActivationStatus: TextView
    private lateinit var btnActivateSocaLibrary: Button
    private lateinit var btnDeactivateSocaLibrary: Button
    private lateinit var btnActivateCustomLibrary: Button
    private lateinit var btnDeactivateCustomLibrary: Button
    private lateinit var btnActivateArasaacLibrary: Button
    private lateinit var btnDeactivateArasaacLibrary: Button
    private lateinit var editAacLibrarySearch: EditText
    private lateinit var translationFilterButtons: Map<TherapistTranslationFilter, Button>
    private lateinit var pageUsageFilterButtons: Map<TherapistPageUsageFilter, Button>
    private lateinit var txtBulkAacSelectionStatus: TextView
    private lateinit var editBulkAacCategory: EditText
    private lateinit var editBulkAacSource: EditText
    private lateinit var editBulkAacLanguages: EditText
    private lateinit var editBulkPlacementPageId: EditText
    private lateinit var editBulkPlacementStartPosition: EditText
    private lateinit var btnBulkSelectVisible: Button
    private lateinit var btnBulkClearSelection: Button
    private lateinit var btnBulkAssignCategory: Button
    private lateinit var btnBulkAssignSource: Button
    private lateinit var btnBulkActivate: Button
    private lateinit var btnBulkDeactivate: Button
    private lateinit var btnBulkAssignLanguages: Button
    private lateinit var btnBulkPlaceSequentially: Button
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
    private lateinit var editAacQuestionByLanguage: EditText
    private lateinit var editAacIconSource: EditText
    private lateinit var editAacImagePath: EditText
    private lateinit var btnChooseAacImage: Button
    private lateinit var imgAacImagePreview: ImageView
    private lateinit var aacItemUsageInspectorActions: LinearLayout
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
    private lateinit var patientPageActions: LinearLayout
    private lateinit var editPatientPageId: EditText
    private lateinit var editPatientPageTitle: EditText
    private lateinit var btnSavePatientPage: Button
    private lateinit var btnSetDefaultPatientPage: Button
    private lateinit var pageWorkspaceActions: LinearLayout
    private lateinit var placementGridActions: LinearLayout
    private lateinit var communicatorStructureActions: LinearLayout
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
    private var therapistTranslationFilter = TherapistTranslationFilter.ALL
    private var therapistPageUsageFilter = TherapistPageUsageFilter.ALL
    private var currentFilteredAacItems: List<AacListItem> = emptyList()
    private val bulkSelectedAacItemIds = linkedSetOf<String>()
    private var previewPageStack: MutableList<String> = mutableListOf()
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
        communicatorDashboardActions = findViewById(R.id.communicatorDashboardActions)
        txtAacBootstrapStatus = findViewById(R.id.txtAacBootstrapStatus)
        btnRepairAacBootstrap = findViewById(R.id.btnRepairAacBootstrap)
        btnCoreV2HomeRepair = findViewById(R.id.btnCoreV2HomeRepair)
        txtAacGridSizeStatus = findViewById(R.id.txtAacGridSizeStatus)
        btnAacGrid3x3 = findViewById(R.id.btnAacGrid3x3)
        btnAacGrid4x4 = findViewById(R.id.btnAacGrid4x4)
        btnAacGrid5x5 = findViewById(R.id.btnAacGrid5x5)
        btnAacGrid6x6 = findViewById(R.id.btnAacGrid6x6)
        txtShowSubiconsOnMainPagesStatus = findViewById(R.id.txtShowSubiconsOnMainPagesStatus)
        btnToggleShowSubiconsOnMainPages = findViewById(R.id.btnToggleShowSubiconsOnMainPages)
        txtFixedTopRowStatus = findViewById(R.id.txtFixedTopRowStatus)
        editFixedTopRowItemId = findViewById(R.id.editFixedTopRowItemId)
        editFixedTopRowPosition = findViewById(R.id.editFixedTopRowPosition)
        btnChooseFixedTopRowItem = findViewById(R.id.btnChooseFixedTopRowItem)
        btnSaveFixedTopRowPosition = findViewById(R.id.btnSaveFixedTopRowPosition)
        btnClearFixedTopRowPosition = findViewById(R.id.btnClearFixedTopRowPosition)
        fixedTopRowVisualActions = findViewById(R.id.fixedTopRowVisualActions)
        previewFixedTopRowActions = findViewById(R.id.previewFixedTopRowActions)
        previewGridActions = findViewById(R.id.previewGridActions)
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
        editAacLibrarySearch = findViewById(R.id.editAacLibrarySearch)
        translationFilterButtons = mapOf(
            TherapistTranslationFilter.ALL to findViewById(R.id.btnTranslationAll),
            TherapistTranslationFilter.TRANSLATED to findViewById(R.id.btnTranslationTranslated),
            TherapistTranslationFilter.MISSING to findViewById(R.id.btnTranslationMissing),
            TherapistTranslationFilter.PENDING to findViewById(R.id.btnTranslationPending)
        )
        pageUsageFilterButtons = mapOf(
            TherapistPageUsageFilter.ALL to findViewById(R.id.btnPageUsageAll),
            TherapistPageUsageFilter.NOT_USED to findViewById(R.id.btnPageUsageNotUsed),
            TherapistPageUsageFilter.USED_ON_PAGE to findViewById(R.id.btnPageUsageUsed)
        )
        txtBulkAacSelectionStatus = findViewById(R.id.txtBulkAacSelectionStatus)
        editBulkAacCategory = findViewById(R.id.editBulkAacCategory)
        editBulkAacSource = findViewById(R.id.editBulkAacSource)
        editBulkAacLanguages = findViewById(R.id.editBulkAacLanguages)
        editBulkPlacementPageId = findViewById(R.id.editBulkPlacementPageId)
        editBulkPlacementStartPosition = findViewById(R.id.editBulkPlacementStartPosition)
        btnBulkSelectVisible = findViewById(R.id.btnBulkSelectVisible)
        btnBulkClearSelection = findViewById(R.id.btnBulkClearSelection)
        btnBulkAssignCategory = findViewById(R.id.btnBulkAssignCategory)
        btnBulkAssignSource = findViewById(R.id.btnBulkAssignSource)
        btnBulkActivate = findViewById(R.id.btnBulkActivate)
        btnBulkDeactivate = findViewById(R.id.btnBulkDeactivate)
        btnBulkAssignLanguages = findViewById(R.id.btnBulkAssignLanguages)
        btnBulkPlaceSequentially = findViewById(R.id.btnBulkPlaceSequentially)
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
        editAacQuestionByLanguage = findViewById(R.id.editAacQuestionByLanguage)
        editAacIconSource = findViewById(R.id.editAacIconSource)
        editAacImagePath = findViewById(R.id.editAacImagePath)
        btnChooseAacImage = findViewById(R.id.btnChooseAacImage)
        imgAacImagePreview = findViewById(R.id.imgAacImagePreview)
        aacItemUsageInspectorActions = findViewById(R.id.aacItemUsageInspectorActions)
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
        patientPageActions = findViewById(R.id.patientPageActions)
        editPatientPageId = findViewById(R.id.editPatientPageId)
        editPatientPageTitle = findViewById(R.id.editPatientPageTitle)
        btnSavePatientPage = findViewById(R.id.btnSavePatientPage)
        btnSetDefaultPatientPage = findViewById(R.id.btnSetDefaultPatientPage)
        pageWorkspaceActions = findViewById(R.id.pageWorkspaceActions)
        placementGridActions = findViewById(R.id.placementGridActions)
        communicatorStructureActions = findViewById(R.id.communicatorStructureActions)
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

        configureSettingsModuleNavigation()

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
                updateLibraryFilterButtons()
                refreshLocalAacOverview()
            }
        }
        translationFilterButtons.forEach { (filter, button) ->
            button.setOnClickListener {
                therapistTranslationFilter = filter
                updateLibraryFilterButtons()
                refreshLocalAacOverview()
            }
        }
        pageUsageFilterButtons.forEach { (filter, button) ->
            button.setOnClickListener {
                therapistPageUsageFilter = filter
                updateLibraryFilterButtons()
                refreshLocalAacOverview()
            }
        }
        btnBulkSelectVisible.setOnClickListener {
            bulkSelectedAacItemIds += currentFilteredAacItems.take(MAX_EDITOR_LIST_ITEMS).map { item -> item.itemId }
            refreshBulkAacSelectionStatus()
            renderAacItemEditorList(currentFilteredAacItems)
        }
        btnBulkClearSelection.setOnClickListener {
            bulkSelectedAacItemIds.clear()
            refreshBulkAacSelectionStatus()
            renderAacItemEditorList(currentFilteredAacItems)
        }
        btnBulkAssignCategory.setOnClickListener {
            showBulkAacDryRun(
                title = "Kategorija",
                changeLines = listOf("categoryId -> ${editBulkAacCategory.text.toString().trim().ifBlank { "(pocisti)" }}")
            ) {
                bulkUpdateSelectedItems { item ->
                    putOptionalString(item, "categoryId", editBulkAacCategory.text.toString())
                }
            }
        }
        btnBulkAssignSource.setOnClickListener {
            showBulkAacDryRun(
                title = "Vir ikon",
                changeLines = listOf("iconSource -> ${editBulkAacSource.text.toString().trim().uppercase(Locale.ROOT)}")
            ) {
                bulkAssignIconSource()
            }
        }
        btnBulkActivate.setOnClickListener {
            showBulkAacDryRun(
                title = "Aktivacija",
                changeLines = listOf("active -> true")
            ) {
                bulkUpdateSelectedItems { item -> item.put("active", true) }
            }
        }
        btnBulkDeactivate.setOnClickListener {
            showBulkAacDryRun(
                title = "Deaktivacija",
                changeLines = listOf("active -> false", "Postavitve in slike ostanejo shranjene.")
            ) {
                bulkUpdateSelectedItems { item -> item.put("active", false) }
            }
        }
        btnBulkAssignLanguages.setOnClickListener {
            showBulkAacDryRun(
                title = "Jeziki",
                changeLines = listOf("activeLanguages -> ${editBulkAacLanguages.text.toString().trim()}", "Prevodi ostanejo shranjeni.")
            ) {
                bulkAssignActiveLanguages()
            }
        }
        btnBulkPlaceSequentially.setOnClickListener {
            showBulkPlacementDryRun()
        }
        btnRepairAacBootstrap.setOnClickListener {
            repairPatientAacBootstrap()
        }
        btnCoreV2HomeRepair.setOnClickListener {
            confirmCoreV2HomeRepair()
        }
        btnAacGrid3x3.setOnClickListener { saveAacGridSize(3) }
        btnAacGrid4x4.setOnClickListener { saveAacGridSize(4) }
        btnAacGrid5x5.setOnClickListener { saveAacGridSize(5) }
        btnAacGrid6x6.setOnClickListener { saveAacGridSize(6) }
        btnToggleShowSubiconsOnMainPages.setOnClickListener { toggleShowSubiconsOnMainPages() }
        editAacLibrarySearch.setOnEditorActionListener { _, _, _ ->
            refreshLocalAacOverview()
            false
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
        AacContentBootstrap.ensurePatientStartupContent(this, AacLocalJsonLoader.loadItems(this, emptyList()))
        setShareEnabled(false)
        txtStatus.text = "Pripravljeno za izvoz ali predpreverjanje ZIP paketa."
        updateLibraryFilterButtons()
        refreshAacLanguageManagerStatus()
        refreshLastImportDiagnostic()
        refreshLocalAacOverview()
    }

    private fun configureSettingsModuleNavigation() {
        fun bind(buttonId: Int, targetId: Int) {
            findViewById<Button>(buttonId).setOnClickListener {
                scrollToSettingsSection(targetId)
            }
        }
        bind(R.id.btnModuleCommunicator, R.id.communicatorDashboardActions)
        bind(R.id.btnModuleVideoCalls, R.id.sectionFutureModules)
        bind(R.id.btnModuleMessages, R.id.sectionFutureModules)
        bind(R.id.btnModuleGallery, R.id.sectionFutureModules)
        bind(R.id.btnModuleMirror, R.id.sectionFutureModules)
        bind(R.id.btnModuleSpeechApi, R.id.sectionAacLanguages)
        bind(R.id.btnModuleSystemUpdates, R.id.sectionAacImportExport)
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
        renderCommunicatorDashboard(overview)
        txtAacBootstrapStatus.text = buildAacBootstrapStatus(overview)
        txtSourceActivationStatus.text = buildSourceActivationStatus()
        renderAacItemEditorList(overview.relationAnalysis.availableItems)
        refreshAacGridSizeStatus()
        refreshShowSubiconsOnMainPagesStatus()
        txtFixedTopRowStatus.text = buildFixedTopRowStatus(overview.relationAnalysis.fixedTopRowItems)
        renderFixedTopRowVisualEditor()
        txtFixedTopRowAvailableItems.text = buildFixedTopRowAvailableItems(overview.relationAnalysis.availableItems)
        txtPatientPagesStatus.text = buildPatientPagesStatus()
        renderPatientPageBrowser()
        txtPlacementStatus.text = buildPlacementStatus(overview.relationAnalysis.availableItems)
        renderPageWorkspace()
        renderPlacementGrid()
        renderPatientFlowPreview()
        renderCommunicatorStructureOverview(overview)
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
                extraWarnings += if (summary.profileId == "real_world" || summary.profileId == "video_call") {
                    "pripravljeno, ni se nastavljeno"
                } else {
                    "profil nima povezanih AAC elementov"
                }
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

    private fun buildAacBootstrapStatus(overview: LocalAacOverview): String {
        val pages = loadPatientPages()
        val defaultPageId = defaultPatientPageId()
        val visibleItemCount = defaultPageId
            .takeIf { it.isNotBlank() }
            ?.let { patientPageItemCounts()[it] ?: 0 }
            ?: 0
        val fixedRowCount = fixedTopRowCellItems().size
        val domLinkedCount = overview.relationAnalysis.profileRelations["dom"]?.itemCount ?: 0
        val domDebug = AacContentBootstrap.inspectDomProfileDebug(this)
        val domCountDebug = buildDomProfileCountDebug(domLinkedCount)
        val health = when {
            overview.relationAnalysis.availableItems.isEmpty() -> "NI AAC ELEMENTOV"
            pages.isEmpty() -> "MANJKA STRAN"
            defaultPageId.isBlank() -> "MANJKA DEFAULT STRAN"
            visibleItemCount == 0 -> "PRAZNA STRAN"
            domLinkedCount == 0 -> "DOM PROFIL PRAZEN"
            else -> "OK"
        }
        return buildString {
            append("ZAGON KOMUNIKATORJA\n")
            append("Stanje: $health\n")
            append("AAC ikon: ${overview.relationAnalysis.availableItems.size}\n")
            append("Pacientovih strani: ${pages.size}\n")
            append("Začetna stran: ${defaultPageId.ifBlank { "ni nastavljena" }}\n")
            append("Vidnih ikon na začetni strani: $visibleItemCount\n")
            append("Fiksna vrstica: $fixedRowCount/5\n")
            append("DOM profil povezanih ikon: $domLinkedCount\n")
            append("Lokalne PNG ikone: ${overview.iconCount}\n\n")
            append("DOM PROFILE DEBUG\n")
            append("Profile file:\n${domDebug.profileFilePath.ifBlank { "ni poti" }}\n")
            append("Exists: ${if (domDebug.profileFileExists) "YES" else "NO"}\n")
            append("Profile type: ${domDebug.profileType}\n")
            append("DOM profile found: ${if (domDebug.domProfileFound) "YES" else "NO"}\n")
            append("DOM profile id: ${domDebug.domProfileId.ifBlank { "ni zaznan" }}\n")
            append("ItemIds before: ${domDebug.itemIdsBefore}\n")
            append("ItemIds after: ${domDebug.itemIdsAfter}\n\n")
            append("DOM PROFILE COUNT DEBUG\n")
            append("Count source:\n${domCountDebug.countSource}\n")
            append("File:\n${domCountDebug.filePath.ifBlank { "ni poti" }}\n")
            append("Profile id: ${domCountDebug.profileId.ifBlank { "ni zaznan" }}\n")
            append("Loaded itemIds:\n${domCountDebug.loadedItemIds.ifEmpty { "ni povezav" }}\n")
            append("Loaded itemIds count: ${domCountDebug.loadedItemIdsCount}\n")
            append("Displayed count: ${domCountDebug.displayedCount}\n\n")
            append(buildStarterPackDebug(defaultPageId, pages.size))
        }
    }

    private fun buildStarterPackDebug(defaultPageId: String, patientPageCount: Int): String {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText)
        val localItemIds = itemIdsInArray(itemsArray)
        val starterIds = AacStarterContentV1.items()
            .map { item -> item.id.trim() }
            .filter { itemId -> itemId.isNotBlank() }
            .distinct()
        val starterIdSet = starterIds.toSet()
        val localStarterIds = starterIds.filter { itemId -> itemId in localItemIds }
        val missingStarterIds = starterIds.filterNot { itemId -> itemId in localItemIds }
        val starterIdsOnAnyPage = starterIds.filter { itemId -> itemId in itemIdsPlacedOnAnyPage(itemsArray) }
        val starterIdsOnDefaultPage = if (defaultPageId.isNotBlank()) {
            starterIds.filter { itemId -> itemId in itemIdsPlacedOnPage(itemsArray, defaultPageId) }
        } else {
            emptyList()
        }
        val occupiedDefaultSlots = if (defaultPageId.isNotBlank()) {
            occupiedPagePositions(itemsArray, defaultPageId)
        } else {
            emptySet()
        }
        val freeDefaultSlots = (6..25).count { position -> position !in occupiedDefaultSlots }
        val patientPagesPrefsPresent = getSharedPreferences(PATIENT_PAGE_PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_PATIENT_PAGES)
        val selectedProfileId = loadSelectedProfile()?.profileId.orEmpty()
        val priorityNotVisible = STARTER_DIAGNOSTIC_PRIORITY
            .filter { itemId -> itemId in starterIdSet }
            .filter { itemId -> itemId in localItemIds }
            .filterNot { itemId -> itemId in starterIdsOnDefaultPage }

        return buildString {
            append("STARTER PACK DEBUG\n")
            append("aac_items.json path:\n${itemsFile?.absolutePath.orEmpty().ifBlank { "ni poti" }}\n")
            append("aac_items.json exists: ${if (itemsFile?.isFile == true) "YES" else "NO"}\n")
            append("Total AAC items: ${itemsArray?.length() ?: 0}\n")
            append("Starter definition count: ${starterIds.size}\n")
            append("Starter items merged locally: ${localStarterIds.size}\n")
            append("Starter items missing locally: ${missingStarterIds.size}\n")
            append("Starter items placed on any page: ${starterIdsOnAnyPage.size}\n")
            append("Starter items placed on default page: ${starterIdsOnDefaultPage.size}\n")
            append("Current/default page id: ${defaultPageId.ifBlank { "ni nastavljena" }}\n")
            append("Patient page count: $patientPageCount\n")
            append("Patient page storage:\nSharedPreferences $PATIENT_PAGE_PREFS_NAME / $KEY_PATIENT_PAGES\n")
            append("Patient pages prefs present: ${if (patientPagesPrefsPresent) "YES" else "NO"}\n")
            append("Selected profile id: ${selectedProfileId.ifBlank { "ni izbran" }}\n")
            append("Free default page slots 6..25: $freeDefaultSlots\n")
            append("High-priority starter ids not yet visible:\n${priorityNotVisible.joinToString(", ").ifBlank { "vsi vidni ali niso lokalno prisotni" }}")
        }
    }

    private fun itemIdsInArray(itemsArray: org.json.JSONArray?): Set<String> {
        if (itemsArray == null) return emptySet()
        return buildSet {
            for (index in 0 until itemsArray.length()) {
                val itemId = itemsArray.optJSONObject(index)?.optString("id")?.trim().orEmpty()
                if (itemId.isNotBlank()) add(itemId)
            }
        }
    }

    private fun itemIdsPlacedOnAnyPage(itemsArray: org.json.JSONArray?): Set<String> {
        if (itemsArray == null) return emptySet()
        return buildSet {
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                val itemId = item.optString("id").trim()
                if (itemId.isBlank()) continue
                val placements = item.optJSONArray("placements") ?: continue
                for (placementIndex in 0 until placements.length()) {
                    val placement = placements.optJSONObject(placementIndex) ?: continue
                    val pageId = placement.optString("pageId").trim()
                    val position = placement.optInt("position5x5", 0)
                    if (isSafePatientPageId(pageId) && position in 1..25) {
                        add(itemId)
                        break
                    }
                }
            }
        }
    }

    private fun itemIdsPlacedOnPage(itemsArray: org.json.JSONArray?, pageId: String): Set<String> {
        if (itemsArray == null || pageId.isBlank()) return emptySet()
        return buildSet {
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                val itemId = item.optString("id").trim()
                if (itemId.isBlank()) continue
                val placements = item.optJSONArray("placements") ?: continue
                for (placementIndex in 0 until placements.length()) {
                    val placement = placements.optJSONObject(placementIndex) ?: continue
                    val position = placement.optInt("position5x5", 0)
                    if (placement.optString("pageId").trim() == pageId && position in 1..25) {
                        add(itemId)
                        break
                    }
                }
            }
        }
    }

    private fun occupiedPagePositions(itemsArray: org.json.JSONArray?, pageId: String): Set<Int> {
        if (itemsArray == null || pageId.isBlank()) return emptySet()
        return buildSet {
            for (index in 0 until itemsArray.length()) {
                val placements = itemsArray.optJSONObject(index)?.optJSONArray("placements") ?: continue
                for (placementIndex in 0 until placements.length()) {
                    val placement = placements.optJSONObject(placementIndex) ?: continue
                    val position = placement.optInt("position5x5", 0)
                    if (placement.optString("pageId").trim() == pageId && position in 1..25) {
                        add(position)
                    }
                }
            }
        }
    }

    private fun buildDomProfileCountDebug(displayedCount: Int): DomProfileCountDebug {
        val profileFile = AacStoragePaths.getProfilesDataDir(this)?.let { profilesDir ->
            java.io.File(profilesDir, "dom.json")
        }
        val profileJson = profileFile
            ?.takeIf { it.isFile }
            ?.let { file ->
                try {
                    org.json.JSONObject(file.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
            }
        val domProfile = when {
            profileJson == null -> null
            profileJson.optString("id").trim() == "dom" -> profileJson
            else -> {
                val profiles = profileJson.optJSONArray("profiles")
                var found: org.json.JSONObject? = null
                if (profiles != null) {
                    for (index in 0 until profiles.length()) {
                        val candidate = profiles.optJSONObject(index) ?: continue
                        if (candidate.optString("id").trim() == "dom") {
                            found = candidate
                            break
                        }
                    }
                }
                found
            }
        }
        val loadedItemIds = stringArrayValues(domProfile?.optJSONArray("itemIds"))
        return DomProfileCountDebug(
            countSource = "aac_items.json profileId/profileIds/profiles -> relationAnalysis.profileRelations[\"dom\"].itemCount",
            filePath = profileFile?.absolutePath.orEmpty(),
            profileId = domProfile?.optString("id")?.trim().orEmpty(),
            loadedItemIds = loadedItemIds.joinToString(", "),
            loadedItemIdsCount = loadedItemIds.size,
            displayedCount = displayedCount
        )
    }

    private fun repairPatientAacBootstrap() {
        val result = AacContentBootstrap.ensurePatientStartupContent(this, AacLocalJsonLoader.loadItems(this, emptyList()))
        txtStatus.text = buildString {
            append("Popravilo pacientove strani končano.\n")
            append("Stanje: ${result.reason}\n")
            append("Začetna stran: ${result.defaultPageId.ifBlank { "ni nastavljena" }}\n")
            append("Dodanih postavitev: ${result.addedPlacements}\n")
            append("DOM povezanih ikon: ${result.domProfileLinkedItemCount}")
        }
        refreshLocalAacOverview()
    }

    private fun confirmCoreV2HomeRepair() {
        AlertDialog.Builder(this)
            .setTitle("ZAKLENI DOMOV STRAN")
            .setMessage(
                "To bo uredilo DOM AAC na eno stran: 5 fiksnih ikon in 20 glavnih ikon.\n\n" +
                    "Obstoječe slike, osebe, podikone in govor se ne bodo izbrisali.\n" +
                    "Pred spremembo bo narejen backup."
            )
            .setPositiveButton("ZAKLENI") { _, _ -> runCoreV2HomeRepair() }
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun runCoreV2HomeRepair() {
        btnCoreV2HomeRepair.isEnabled = false
        btnCoreV2HomeRepair.backgroundTintList = ColorStateList.valueOf(BUSY_BUTTON_COLOR)
        txtStatus.text = "Zaklepam DOM stran AAC Core V2 ..."
        Thread {
            val result = try {
                AacCoreV2HomeRepair.execute(this)
            } catch (error: Throwable) {
                val reportPath = AacCoreV2HomeRepair.writeExceptionReport(
                    context = this,
                    error = error,
                    stage = "activity_execute"
                )
                AacCoreV2HomeRepair.Result.Failure(
                    buildString {
                        append("Class: ${error.javaClass.name}\n")
                        append("Message: ${error.message.orEmpty().ifBlank { "(empty)" }}")
                        if (reportPath.isNotBlank()) {
                            append("\nReport path: $reportPath")
                        }
                    }
                )
            }
            val displayedResult = when (result) {
                is AacCoreV2HomeRepair.Result.Success -> result
                is AacCoreV2HomeRepair.Result.Failure -> {
                    if ("Report path:" in result.reason) {
                        result
                    } else {
                        val reportPath = AacCoreV2HomeRepair.writeFailureReport(
                            context = this,
                            reason = result.reason,
                            stage = "activity_failure_result"
                        )
                        if (reportPath.isBlank()) {
                            result
                        } else {
                            AacCoreV2HomeRepair.Result.Failure("${result.reason}\nReport path: $reportPath")
                        }
                    }
                }
            }
            mainHandler.post {
                btnCoreV2HomeRepair.isEnabled = true
                btnCoreV2HomeRepair.backgroundTintList = ColorStateList.valueOf(0xFF8A6D2F.toInt())
                val title = coreV2HomeRepairResultTitle(displayedResult)
                val message = buildCoreV2HomeRepairResultMessage(displayedResult)
                txtStatus.text = message
                showCoreV2HomeRepairResultDialog(title, message)
                if (displayedResult is AacCoreV2HomeRepair.Result.Success) {
                    refreshLocalAacOverview()
                }
            }
        }.start()
    }

    private fun coreV2HomeRepairResultTitle(result: AacCoreV2HomeRepair.Result): String {
        return when (result) {
            is AacCoreV2HomeRepair.Result.Failure -> "ERROR"
            is AacCoreV2HomeRepair.Result.Success -> {
                val changed = result.fixedRowUpdatedCount + result.placementsUpdatedCount + result.domRootChangedCount
                when {
                    changed == 0 -> "NO CHANGES"
                    result.jsonWriteVerified -> "SUCCESS"
                    else -> "ERROR"
                }
            }
        }
    }

    private fun buildCoreV2HomeRepairResultMessage(result: AacCoreV2HomeRepair.Result): String {
        return when (result) {
            is AacCoreV2HomeRepair.Result.Success -> {
                val changed = result.fixedRowUpdatedCount + result.placementsUpdatedCount + result.domRootChangedCount
                buildString {
                    append("executed: DA\n")
                    append("fixed row changed: ${result.fixedRowUpdatedCount}\n")
                    append("placements changed: ${result.placementsUpdatedCount}\n")
                    append("dom root changed: ${result.domRootChangedCount}\n")
                    append("JSON write verified: ${if (result.jsonWriteVerified) "DA" else "NE"}\n\n")
                    append("Backup:\n${result.backupDir.absolutePath}\n\n")
                    append("aac_items.json:\n${result.itemsFilePath}\n\n")
                    append("dom.json:\n${result.domFilePath}\n\n")
                    append("first 25 after repair:\n")
                    append(result.afterPage1Positions.take(25).joinToString("\n"))
                    if (changed == 0) {
                        append("\n\nNO CHANGES\n")
                        append("Reason: ${result.noChangeReason.ifBlank { "No repair counters changed." }}\n")
                        append("\nDOM before:\n${result.beforeDomRootItemIds.joinToString(", ")}\n")
                        append("\nDOM after:\n${result.afterDomRootItemIds.joinToString(", ")}\n")
                        append("\npage_1 before:\n${result.beforePage1Positions.take(25).joinToString("\n")}\n")
                        append("\npage_1 after:\n${result.afterPage1Positions.take(25).joinToString("\n")}")
                    }
                }
            }
            is AacCoreV2HomeRepair.Result.Failure -> {
                "executed: NE\n\n${result.reason}"
            }
        }
    }

    private fun showCoreV2HomeRepairResultDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun saveAacGridSize(gridSize: Int) {
        if (gridSize !in 3..6) {
            txtStatus.text = "Velikost mreže mora biti 3x3, 4x4, 5x5 ali 6x6."
            return
        }
        getSharedPreferences(AAC_GRID_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AAC_GRID_SIZE, gridSize)
            .apply()
        refreshAacGridSizeStatus()
        refreshShowSubiconsOnMainPagesStatus()
        txtStatus.text = if (gridSize == 6) {
            "Shranjeno: 6x6 je napredna nastavitev za velik 14\" zaslon."
        } else {
            "Shranjeno: mreža komunikatorja ${gridSize}x$gridSize."
        }
    }

    private fun refreshAacGridSizeStatus() {
        val gridSize = selectedAacGridSize()
        txtAacGridSizeStatus.text = buildString {
            appendLine("Trenutna mreža: ${gridSize}x$gridSize")
            appendLine("Privzeto: 4x4. 6x6 je napredno, samo za velik 14\" zaslon.")
            append("Fiksna vrstica: 3x3 F1-F3, 4x4 F1-F4, 5x5 F1-F5, 6x6 F1-F5; 6. polje je normalna ikona.")
        }
        val selectedColor = 0xFF2E8B57.toInt()
        val normalColor = 0xFF34414D.toInt()
        listOf(
            3 to btnAacGrid3x3,
            4 to btnAacGrid4x4,
            5 to btnAacGrid5x5,
            6 to btnAacGrid6x6
        ).forEach { (size, button) ->
            button.backgroundTintList = ColorStateList.valueOf(if (size == gridSize) selectedColor else normalColor)
        }
    }

    private fun selectedAacGridSize(): Int {
        return getSharedPreferences(AAC_GRID_PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AAC_GRID_SIZE, DEFAULT_AAC_GRID_SIZE)
            .takeIf { it in 3..6 }
            ?: DEFAULT_AAC_GRID_SIZE
    }

    private fun toggleShowSubiconsOnMainPages() {
        val currentValue = showSubiconsOnMainPages()
        val newValue = !currentValue
        getSharedPreferences(AAC_GRID_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_SUBICONS_ON_MAIN_PAGES, newValue)
            .apply()
        refreshShowSubiconsOnMainPagesStatus()
        txtStatus.text = if (newValue) {
            "Shranjeno: podikone se lahko prikažejo na glavnih straneh, če ostane prostor."
        } else {
            "Shranjeno: podikone so skrite z glavnih strani in se odprejo prek starševske ikone."
        }
    }

    private fun refreshShowSubiconsOnMainPagesStatus() {
        val enabled = showSubiconsOnMainPages()
        txtShowSubiconsOnMainPagesStatus.text = buildString {
            appendLine("Prikaži podikone na glavnih straneh: ${if (enabled) "VKLOPLJENO" else "IZKLOPLJENO"}")
            append("Privzeto je izklopljeno. Če je izklopljeno, se JUHA/KRUH/SADJE pokažejo šele po pritisku na HRANA.")
        }
        btnToggleShowSubiconsOnMainPages.text = if (enabled) {
            "IZKLOPI PODIKONE NA GLAVNIH STRANEH"
        } else {
            "VKLOPI PODIKONE NA GLAVNIH STRANEH"
        }
        btnToggleShowSubiconsOnMainPages.backgroundTintList = ColorStateList.valueOf(
            if (enabled) 0xFF2E8B57.toInt() else 0xFF34414D.toInt()
        )
    }

    private fun showSubiconsOnMainPages(): Boolean {
        return getSharedPreferences(AAC_GRID_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_SUBICONS_ON_MAIN_PAGES, false)
    }

    private fun renderCommunicatorDashboard(overview: LocalAacOverview) {
        communicatorDashboardActions.removeAllViews()
        val pages = loadPatientPages()
        val defaultPageId = defaultPatientPageId().ifBlank { "varna privzeta" }
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText)
        val translationStatus = itemsArray?.let { buildTranslationStatus(it, loadAacActiveLanguages()) }
            ?: TranslationStatus.empty(loadAacActiveLanguages())
        val subiconStatus = itemsArray?.let { buildSubiconDashboardStatus(it) } ?: SubiconDashboardStatus.empty()
        val warnings = buildCommunicatorWarnings(overview, translationStatus)

        communicatorDashboardActions.addView(TextView(this).apply {
            text = "KOMUNIKATOR — NADZORNA PLOŠČA"
            textSize = 22f
            setTextColor(0xFFF4F7FA.toInt())
            setPadding(0, 0, 0, 10.dp())
        })
        addDashboardCard(
            title = "STRANI",
            accentColor = 0xFF2F5F9E.toInt(),
            lines = listOf("Število strani: ${pages.size}", "Začetna stran: $defaultPageId"),
            actionText = "UREDI STRANI",
            targetId = R.id.sectionAacPlacement
        )
        addDashboardCard(
            title = "IKONE",
            accentColor = 0xFF3E7C4A.toInt(),
            lines = listOf("AAC ikone: ${overview.relationAnalysis.availableItems.size}"),
            actionText = "UREDI IKONE",
            targetId = R.id.sectionAacLibrary
        )
        addDashboardCard(
            title = "PREVODI",
            accentColor = if (translationStatus.missingCount > 0) 0xFF8F6B2D.toInt() else 0xFF3E7C4A.toInt(),
            lines = listOf(
                "Prevedeno: ${translationStatus.translatedCount}",
                "Manjka: ${translationStatus.missingCount}",
                "V pripravi: ${translationStatus.pendingCount}"
            ),
            actionText = "JEZIKI",
            targetId = R.id.sectionAacLanguages
        )
        addDashboardCard(
            title = "PODIKONE",
            accentColor = 0xFF5B6672.toInt(),
            lines = listOf(
                "Starši: ${subiconStatus.parentCount}",
                "Podikone: ${subiconStatus.childCount}",
                "Brez povezave: ${subiconStatus.orphanCount}"
            ),
            actionText = "UREDI PODIKONE",
            targetId = R.id.sectionAacSubicons
        )
        addDashboardCard(
            title = "FIKSNA VRSTICA",
            accentColor = if (overview.relationAnalysis.fixedTopRowItems.size < 5) 0xFF8F6B2D.toInt() else 0xFF3E7C4A.toInt(),
            lines = listOf(
                "Nastavljeno: ${overview.relationAnalysis.fixedTopRowItems.size}/5",
                "Manjka: ${5 - overview.relationAnalysis.fixedTopRowItems.size}"
            ),
            actionText = "FIKSNA VRSTICA",
            targetId = R.id.sectionAacFixedRow
        )
        addDashboardCard(
            title = "OPOZORILA",
            accentColor = if (warnings.isEmpty()) 0xFF3E7C4A.toInt() else 0xFF8F3A3A.toInt(),
            lines = warnings.ifEmpty { listOf("Ni kritičnih opozoril.") },
            actionText = "PREGLED",
            targetId = R.id.sectionAacPlacement
        )
    }

    private fun addDashboardCard(
        title: String,
        accentColor: Int,
        lines: List<String>,
        actionText: String,
        targetId: Int
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF26323D.toInt())
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
        }
        card.addView(
            TextView(this).apply {
                text = title
                textSize = 20f
                setTextColor(0xFFF4F7FA.toInt())
            }
        )
        card.addView(
            android.view.View(this).apply {
                setBackgroundColor(accentColor)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                3.dp()
            ).apply {
                topMargin = 6.dp()
                bottomMargin = 6.dp()
            }
        )
        lines.forEach { line ->
            card.addView(
                TextView(this).apply {
                    text = line
                    textSize = 17f
                    setTextColor(0xFFB8C0C8.toInt())
                    setPadding(0, 4.dp(), 0, 0)
                }
            )
        }
        card.addView(
            Button(this).apply {
                text = actionText
                setAllCaps(false)
                textSize = 17f
                setTextColor(0xFFF4F7FA.toInt())
                backgroundTintList = ColorStateList.valueOf(accentColor)
                setOnClickListener { scrollToSettingsSection(targetId) }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                58.dp()
            ).apply {
                topMargin = 12.dp()
            }
        )
        communicatorDashboardActions.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12.dp()
            }
        )
    }

    private fun scrollToSettingsSection(targetId: Int) {
        val scrollView = findViewById<ScrollView>(R.id.aacSettingsScrollView)
        val target = findViewById<android.view.View>(targetId)
        scrollView.post {
            scrollView.smoothScrollTo(0, target.top)
        }
    }

    private fun buildSubiconDashboardStatus(itemsArray: org.json.JSONArray): SubiconDashboardStatus {
        val allItemIds = linkedSetOf<String>()
        val parentIds = linkedSetOf<String>()
        val childIds = linkedSetOf<String>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) continue
            allItemIds += itemId
            val children = stringArrayValues(item.optJSONArray("children"))
            if (children.isNotEmpty()) {
                parentIds += itemId
                childIds += children
            }
        }
        val orphanCount = allItemIds.count { itemId -> itemId !in parentIds && itemId !in childIds }
        return SubiconDashboardStatus(
            parentCount = parentIds.size,
            childCount = childIds.size,
            orphanCount = orphanCount
        )
    }

    private fun buildCommunicatorWarnings(
        overview: LocalAacOverview,
        translationStatus: TranslationStatus
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (translationStatus.missingCount > 0) {
            warnings += "Manjkajo prevodi: ${translationStatus.missingCount}"
        }
        if (overview.orphanItemCount > 0) {
            warnings += "Elementi brez profila: ${overview.orphanItemCount}"
        }
        if (overview.relationAnalysis.invalidIconReferenceCount > 0) {
            warnings += "Neveljavne poti slik: ${overview.relationAnalysis.invalidIconReferenceCount}"
        }
        if (overview.relationAnalysis.availableItems.any { it.pageCount == 0 }) {
            warnings += "Elementi brez strani: ${overview.relationAnalysis.availableItems.count { it.pageCount == 0 }}"
        }
        return warnings
    }

    private fun buildFixedTopRowStatus(fixedItems: List<FixedTopRowItem>): String {
        return buildString {
            append("FIKSNA PRVA VRSTICA\n")
            append("5x5: vidne so pozicije 1-5.\n")
            append("4x4: vidne so pozicije 1-4; pozicija 5 gre v normalno vsebino.\n")
            append("3x3: vidne so pozicije 1-3; poziciji 4-5 gresta v normalno vsebino.\n\n")

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

    private fun renderFixedTopRowVisualEditor() {
        fixedTopRowVisualActions.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val fixedCells = fixedTopRowCellItems()
        for (position in 1..5) {
            val cellItem = fixedCells[position]
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
                setBackgroundColor(if (cellItem == null) 0xFF172029.toInt() else 0xFF214A78.toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    editFixedTopRowPosition.setText(position.toString())
                    if (cellItem == null) {
                        showFixedPlacementCellItemChooser(position, null)
                    } else {
                        showFixedPlacementCellActions(position, cellItem)
                    }
                }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setBackgroundColor(0xFF172029.toInt())
                setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            }
            val label = TextView(this).apply {
                text = cellItem?.let { item ->
                    "F$position\n${item.label.ifBlank { item.itemId }}"
                } ?: "F$position\nprosto"
                gravity = Gravity.CENTER
                maxLines = 3
                textSize = 13f
                setTextColor(if (cellItem == null) 0xFF9CA8B5.toInt() else 0xFFF4F7FA.toInt())
            }
            if (cellItem != null) {
                bindPlacementCellIconPreview(image, cellItem)
                cell.addView(
                    image,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        54.dp()
                    )
                )
            }
            cell.addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            row.addView(
                cell,
                LinearLayout.LayoutParams(
                    0,
                    116.dp(),
                    1f
                ).apply {
                    marginEnd = if (position < 5) 4.dp() else 0
                }
            )
        }
        fixedTopRowVisualActions.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun fixedTopRowCellItems(): Map<Int, PlacementCellItem> {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText) ?: return emptyMap()
        val cells = linkedMapOf<Int, PlacementCellItem>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val fixedPosition = itemFixedTopRowPosition(item)
            if (fixedPosition == null || fixedPosition !in 1..5) continue
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) continue
            cells[fixedPosition] = placementCellItemFromJson(item, fixedPosition, isFixedTopRowCell = true)
        }
        return cells
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
        val searchText = editAacLibrarySearch.text.toString().trim().lowercase(Locale.ROOT)
        val filteredItems = items
            .filter { item -> therapistIconSourceFilter.matches(item.iconSource) }
            .filter { item -> therapistTranslationFilter.matches(item.translationStatus) }
            .filter { item -> therapistPageUsageFilter.matches(item.pageCount) }
            .filter { item -> item.matchesLibrarySearch(searchText) }
        currentFilteredAacItems = filteredItems
        bulkSelectedAacItemIds.retainAll(items.map { item -> item.itemId }.toSet())
        refreshBulkAacSelectionStatus()
        if (filteredItems.isEmpty()) {
            aacItemListActions.addView(buildAacItemListMessage("Ni AAC elementov za trenutne filtre."))
            return
        }

        aacItemListActions.addView(
            buildAacItemListMessage(
                "KNJIŽNICA IKON\n" +
                    "Prikazano: ${filteredItems.size} ikon · filter vira: ${therapistIconSourceFilter.label} · tapni kartico za urejanje."
            )
        )

        filteredItems.take(MAX_EDITOR_LIST_ITEMS).chunked(3).forEach { rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowItems.forEachIndexed { index, item ->
                row.addView(
                    buildAacGalleryCard(item),
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginEnd = if (index < rowItems.lastIndex) 10.dp() else 0
                    }
                )
            }
            repeat(3 - rowItems.size) {
                row.addView(
                    LinearLayout(this),
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                    )
                )
            }
            aacItemListActions.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12.dp()
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

    private fun buildAacGalleryCard(item: AacListItem): LinearLayout {
        val isMissingImage = itemHasMissingImage(item)
        val isSelected = item.itemId in bulkSelectedAacItemIds
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(
                when {
                    isSelected -> 0xFF214A78.toInt()
                    isMissingImage -> 0xFF3B3327.toInt()
                    item.translationStatus == ItemTranslationStatus.MISSING -> 0xFF3A313E.toInt()
                    item.pageCount == 0 -> 0xFF24313C.toInt()
                    else -> 0xFF26323D.toInt()
                }
            )
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener { loadAacItemIntoEditor(item.itemId) }
            setOnLongClickListener {
                toggleBulkAacSelection(item.itemId)
                true
            }
        }
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(0xFF172029.toInt())
            setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
        }
        bindAacListIconPreview(preview, item)
        card.addView(
            preview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                132.dp()
            )
        )
        card.addView(TextView(this).apply {
            text = if (isSelected) "✓ ${item.label.ifBlank { "brez oznake" }}" else item.label.ifBlank { "brez oznake" }
            gravity = Gravity.CENTER
            maxLines = 2
            textSize = 18f
            setTextColor(0xFFF4F7FA.toInt())
            setPadding(0, 10.dp(), 0, 2.dp())
        })
        card.addView(TextView(this).apply {
            text = item.itemId
            gravity = Gravity.CENTER
            maxLines = 1
            textSize = 12f
            setTextColor(0xFF9CA8B5.toInt())
        })
        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 0)
        }
        badges.addView(buildGalleryBadge(item.iconSource.name, 0xFF2F5F9E.toInt()))
        badges.addView(
            buildGalleryBadge(
                item.translationStatus.label,
                when (item.translationStatus) {
                    ItemTranslationStatus.TRANSLATED -> 0xFF2F6F50.toInt()
                    ItemTranslationStatus.MISSING -> 0xFF8A5A24.toInt()
                    ItemTranslationStatus.PENDING -> 0xFF5A4F8A.toInt()
                }
            )
        )
        card.addView(badges)
        val indicators = galleryIndicators(item, isMissingImage)
        if (indicators.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = indicators.joinToString(" · ")
                gravity = Gravity.CENTER
                maxLines = 3
                textSize = 14f
                setTextColor(if (isMissingImage || item.translationStatus == ItemTranslationStatus.MISSING) 0xFFFFD27A.toInt() else 0xFFB8C0C8.toInt())
                setPadding(0, 8.dp(), 0, 0)
            })
        }
        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8.dp(), 0, 0)
        }
        quickActions.addView(buildLibraryQuickButton("STRAN") {
            editPlacementItemId.setText(item.itemId)
            editPlacementPageId.setText(selectedPlacementPageId())
            txtStatus.text = "Ikona izbrana za postavitev.\n${item.itemId}"
        })
        quickActions.addView(buildLibraryQuickButton("FIKSNO") {
            editFixedTopRowItemId.setText(item.itemId)
            txtStatus.text = "Ikona izbrana za fiksno vrstico.\n${item.itemId}"
        })
        quickActions.addView(buildLibraryQuickButton("POD") {
            editSubiconChildId.setText(item.itemId)
            txtStatus.text = "Ikona izbrana kot podikona.\n${item.itemId}"
        })
        card.addView(quickActions)
        return card
    }

    private fun buildGalleryBadge(label: String, color: Int): TextView {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(0xFFF4F7FA.toInt())
            setBackgroundColor(color)
            setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 6.dp()
            }
        }
    }

    private fun galleryIndicators(item: AacListItem, isMissingImage: Boolean): List<String> {
        return buildList {
            if (item.pageCount > 0) add("na ${item.pageCount} str.")
            if (item.fixedTopRowPosition != null) add("F${item.fixedTopRowPosition}")
            if (item.subiconCount > 0) add("${item.subiconCount} podikon")
            if (item.translationStatus == ItemTranslationStatus.MISSING) add("manjka prevod")
            if (isMissingImage) add("manjka slika")
        }
    }

    private fun itemHasMissingImage(item: AacListItem): Boolean {
        return item.imagePath.isNotBlank() &&
            item.iconSource != IconSource.SYSTEM &&
            AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)?.isFile != true
    }

    private fun buildLibraryQuickButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setAllCaps(false)
            textSize = 13f
            setTextColor(0xFFF4F7FA.toInt())
            backgroundTintList = ColorStateList.valueOf(0xFF26323D.toInt())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                56.dp(),
                1f
            ).apply {
                marginEnd = 6.dp()
            }
        }
    }

    private fun toggleBulkAacSelection(itemId: String) {
        if (!bulkSelectedAacItemIds.add(itemId)) {
            bulkSelectedAacItemIds.remove(itemId)
        }
        refreshBulkAacSelectionStatus()
        renderAacItemEditorList(currentFilteredAacItems)
    }

    private fun refreshBulkAacSelectionStatus() {
        if (!::txtBulkAacSelectionStatus.isInitialized) return
        val selectedItems = buildLocalAacOverview()
            .relationAnalysis
            .availableItems
            .filter { item -> item.itemId in bulkSelectedAacItemIds }
        val translated = selectedItems.count { it.translationStatus == ItemTranslationStatus.TRANSLATED }
        val missing = selectedItems.count { it.translationStatus == ItemTranslationStatus.MISSING }
        val pending = selectedItems.count { it.translationStatus == ItemTranslationStatus.PENDING }
        txtBulkAacSelectionStatus.text = "Izbranih ikon: ${bulkSelectedAacItemIds.size}\n" +
            "Prevodi: prevedeno $translated, manjka $missing, v pripravi $pending\n" +
            "Skupinske postavitve dodajajo samo proste celice in ne spreminjajo fiksne vrstice."
    }

    private fun showBulkAacDryRun(
        title: String,
        changeLines: List<String>,
        action: () -> Unit
    ) {
        if (bulkSelectedAacItemIds.isEmpty()) {
            txtStatus.text = "Najprej izberi AAC ikone. Kartico drzi za izbor ali uporabi IZBERI VIDNE."
            return
        }
        val selectedItems = buildLocalAacOverview()
            .relationAnalysis
            .availableItems
            .filter { item -> item.itemId in bulkSelectedAacItemIds }
        val skippedCount = bulkSelectedAacItemIds.size - selectedItems.size
        val warningLines = buildList {
            if (skippedCount > 0) add("Preskoceno: $skippedCount ikon ni vec najdenih v lokalnem JSON.")
            add("Ne brise prevodov, slik, podikon, visibleUnderIds, postavitev ali fiksne vrstice.")
            add("categoryId je samo terapevtska organizacija, ne pacientova postavitev.")
        }
        val previewText = buildString {
            append("Izbranih ikon: ${bulkSelectedAacItemIds.size}\n")
            append("Spremenilo se bo:\n")
            changeLines.forEach { line -> append("- $line\n") }
            append("\nPrimer izbranih ikon:\n")
            selectedItems.take(12).forEach { item ->
                append("- ${item.label.ifBlank { item.itemId }} (${item.itemId})\n")
            }
            val remaining = selectedItems.size - 12
            if (remaining > 0) append("... se $remaining\n")
            append("\nOpozorila:\n")
            warningLines.forEach { warning -> append("- $warning\n") }
        }.trimEnd()
        AlertDialog.Builder(this)
            .setTitle("Predogled: $title")
            .setMessage(previewText)
            .setPositiveButton("Potrdi spremembe za izbrane ikone") { _, _ -> action() }
            .setNegativeButton("Preklici", null)
            .show()
    }

    private fun bulkUpdateSelectedItems(updateItem: (org.json.JSONObject) -> Unit) {
        val selectedIds = bulkSelectedAacItemIds.toSet()
        var updatedCount = 0
        val result = updateItemsJsonMetadata { itemsArray ->
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                if (item.optString("id").trim() in selectedIds) {
                    updateItem(item)
                    updatedCount += 1
                }
            }
            AacMetadataWriteResult.Success
        }
        if (result == AacMetadataWriteResult.Success) {
            val skippedCount = (selectedIds.size - updatedCount).coerceAtLeast(0)
            txtStatus.text = "Skupinska sprememba shranjena.\nSpremenjeno: $updatedCount\nPreskoceno: $skippedCount\nOpozorila: 0"
            refreshLocalAacOverview()
        } else {
            txtStatus.text = "Skupinske spremembe ni bilo mogoce shraniti."
        }
    }

    private fun bulkAssignIconSource() {
        val sourceText = editBulkAacSource.text.toString().trim().uppercase(Locale.ROOT)
        if (sourceText.isBlank() || parseLocalIconSource(sourceText) == null) {
            txtStatus.text = "Vir mora biti SOCA, CUSTOM, PATIENT, ARASAAC ali SYSTEM."
            return
        }
        bulkUpdateSelectedItems { item ->
            item.put("iconSource", sourceText)
        }
    }

    private fun bulkAssignActiveLanguages() {
        val languages = editBulkAacLanguages.text.toString()
            .split(',', ';', ' ')
            .mapNotNull(::normalizeAacLanguageCode)
            .distinct()
            .take(3)
        if (languages.isEmpty()) {
            txtStatus.text = "Vnesi 1 do 3 jezike, npr. sl,uk,en."
            return
        }
        bulkUpdateSelectedItems { item ->
            item.put("activeLanguages", org.json.JSONArray().apply {
                languages.forEach { language -> put(language) }
            })
        }
    }

    private fun showBulkPlacementDryRun() {
        val preview = buildBulkPlacementPreview()
        if (preview == null) return
        val previewText = buildString {
            append("Ciljna stran: ${preview.pageId}\n")
            append("Zacetna pozicija: ${preview.startPosition}\n")
            append("Izbranih ikon: ${bulkSelectedAacItemIds.size}\n")
            append("Za postavitev: ${preview.toPlace.size}\n")
            append("Preskoceno: ${preview.skippedLines.size}\n")
            append("Prostora dovolj: ${if (preview.hasEnoughSpace) "DA" else "NE"}\n\n")
            append("Postavile se bodo:\n")
            preview.toPlace.take(15).forEach { line -> append("- $line\n") }
            val remainingToPlace = preview.toPlace.size - 15
            if (remainingToPlace > 0) append("... se $remainingToPlace\n")
            append("\nPreskoceno / opozorila:\n")
            if (preview.skippedLines.isEmpty()) {
                append("- Ni preskocenih ikon.\n")
            } else {
                preview.skippedLines.take(15).forEach { line -> append("- $line\n") }
                val remainingSkipped = preview.skippedLines.size - 15
                if (remainingSkipped > 0) append("... se $remainingSkipped\n")
            }
            append("\nVarnost:\n")
            append("- Ne prepisuje zasedenih celic.\n")
            append("- Ne spreminja fiksne vrstice.\n")
            append("- Ne brise obstojecih postavitev, prevodov, slik ali podikon.")
        }.trimEnd()
        AlertDialog.Builder(this)
            .setTitle("Predogled: zaporedna postavitev")
            .setMessage(previewText)
            .setPositiveButton("Potrdi spremembe za izbrane ikone") { _, _ ->
                bulkPlaceSelectedItemsSequentially(preview)
            }
            .setNegativeButton("Preklici", null)
            .show()
    }

    private fun buildBulkPlacementPreview(): BulkPlacementPreview? {
        if (bulkSelectedAacItemIds.isEmpty()) {
            txtStatus.text = "Najprej izberi AAC ikone."
            return null
        }
        val pageId = editBulkPlacementPageId.text.toString().trim()
            .ifBlank { selectedPlacementPageId() }
        val startPosition = editBulkPlacementStartPosition.text.toString().trim().toIntOrNull() ?: 1
        if (!isSafePatientPageId(pageId)) {
            txtStatus.text = "Vnesi veljaven ID strani za postavitev."
            return null
        }
        if (startPosition !in 1..25) {
            txtStatus.text = "Zacetna pozicija mora biti 1-25."
            return null
        }
        val orderedSelectedIds = orderedBulkSelectedItemIds()
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText)
        if (itemsArray == null) {
            txtStatus.text = "AAC elementi niso najdeni."
            return null
        }
        val itemById = mutableMapOf<String, org.json.JSONObject>()
        val occupiedPositions = mutableSetOf<Int>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim()
            if (itemId.isNotBlank()) itemById[itemId] = item
            val placements = item.optJSONArray("placements") ?: continue
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                val position = placement.optInt("position5x5", 0)
                if (placement.optString("pageId").trim() == pageId && position in 1..25) {
                    occupiedPositions += position
                }
            }
        }
        val toPlace = mutableListOf<String>()
        val skippedLines = mutableListOf<String>()
        var nextPosition = startPosition
        orderedSelectedIds.forEach { itemId ->
            val item = itemById[itemId]
            if (item == null) {
                skippedLines += "$itemId: element ni najden"
                return@forEach
            }
            if (itemAlreadyPlacedOnPage(item, pageId)) {
                skippedLines += "$itemId: ze je na strani $pageId"
                return@forEach
            }
            while (nextPosition <= 25 && nextPosition in occupiedPositions) {
                skippedLines += "pozicija $nextPosition: zasedena"
                nextPosition += 1
            }
            if (nextPosition > 25) {
                skippedLines += "$itemId: ni dovolj prostih polj"
                return@forEach
            }
            val label = itemLabel(item).ifBlank { itemId }
            toPlace += "$label ($itemId) -> $pageId / $nextPosition"
            occupiedPositions += nextPosition
            nextPosition += 1
        }
        return BulkPlacementPreview(
            pageId = pageId,
            startPosition = startPosition,
            toPlace = toPlace,
            skippedLines = skippedLines,
            hasEnoughSpace = toPlace.isNotEmpty() && skippedLines.none { it.contains("ni dovolj prostih polj") }
        )
    }

    private fun orderedBulkSelectedItemIds(): List<String> {
        return currentFilteredAacItems
            .map { item -> item.itemId }
            .filter { itemId -> itemId in bulkSelectedAacItemIds } +
            bulkSelectedAacItemIds.filterNot { itemId -> currentFilteredAacItems.any { it.itemId == itemId } }
    }

    private fun bulkPlaceSelectedItemsSequentially(preview: BulkPlacementPreview) {
        val pageId = preview.pageId
        val startPosition = preview.startPosition
        val orderedSelectedIds = orderedBulkSelectedItemIds()
        var placedCount = 0
        var skippedCount = 0
        var warningCount = preview.skippedLines.size
        val result = updateItemsJsonMetadata { itemsArray ->
            val itemById = mutableMapOf<String, org.json.JSONObject>()
            val occupiedPositions = mutableSetOf<Int>()
            for (index in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(index) ?: continue
                val itemId = item.optString("id").trim()
                if (itemId.isNotBlank()) itemById[itemId] = item
                val placements = item.optJSONArray("placements") ?: continue
                for (placementIndex in 0 until placements.length()) {
                    val placement = placements.optJSONObject(placementIndex) ?: continue
                    if (placement.optString("pageId").trim() == pageId &&
                        placement.optInt("position5x5", 0) in 1..25
                    ) {
                        occupiedPositions += placement.optInt("position5x5", 0)
                    }
                }
            }
            var nextPosition = startPosition
            orderedSelectedIds.forEach { itemId ->
                val item = itemById[itemId]
                if (item == null || itemAlreadyPlacedOnPage(item, pageId)) {
                    skippedCount += 1
                    return@forEach
                }
                while (nextPosition <= 25 && nextPosition in occupiedPositions) {
                    warningCount += 1
                    nextPosition += 1
                }
                if (nextPosition > 25) {
                    skippedCount += 1
                    return@forEach
                }
                val placements = item.optJSONArray("placements") ?: org.json.JSONArray()
                placements.put(org.json.JSONObject().put("pageId", pageId).put("position5x5", nextPosition))
                item.put("placements", placements)
                occupiedPositions += nextPosition
                nextPosition += 1
                placedCount += 1
            }
            AacMetadataWriteResult.Success
        }
        if (result == AacMetadataWriteResult.Success) {
            editPlacementPageId.setText(pageId)
            txtStatus.text = "Skupinska postavitev shranjena.\nSpremenjeno: $placedCount\nPreskoceno: $skippedCount\nOpozorila: $warningCount"
            refreshLocalAacOverview()
        } else {
            txtStatus.text = "Skupinske postavitve ni bilo mogoce shraniti."
        }
    }

    private fun itemAlreadyPlacedOnPage(item: org.json.JSONObject, pageId: String): Boolean {
        val placements = item.optJSONArray("placements") ?: return false
        for (index in 0 until placements.length()) {
            val placement = placements.optJSONObject(index) ?: continue
            if (placement.optString("pageId").trim() == pageId && !placement.optBoolean("generated", false)) {
                return true
            }
        }
        return false
    }

    private fun bindAacListIconPreview(preview: ImageView, item: AacListItem) {
        val imageFile = AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
        if (imageFile?.isFile != true) {
            preview.setImageDrawable(null)
            return
        }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            preview.setImageDrawable(null)
        } else {
            preview.setImageBitmap(bitmap)
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
        editAacQuestionByLanguage.setText(formatQuestionByLanguageForEditor(item))
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
        renderAacItemUsageInspector(item)
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

    private fun renderAacItemUsageInspector(item: org.json.JSONObject) {
        aacItemUsageInspectorActions.removeAllViews()
        val itemId = item.optString("id").trim()
        if (itemId.isBlank()) {
            aacItemUsageInspectorActions.addView(buildAacItemListMessage("Izberi AAC element za pregled uporabe."))
            return
        }
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val usage = buildAacItemUsage(item, currentItemsArray(itemsText))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF26323D.toInt())
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
        }
        card.addView(TextView(this).apply {
            text = "OSNOVNO / UPORABA AAC ELEMENTA"
            textSize = 19f
            setTextColor(0xFFF4F7FA.toInt())
        })
        card.addView(TextView(this).apply {
            text = buildString {
                append("Osnovno: ${usage.label.ifBlank { usage.itemId }} · ${usage.iconSource.name}\n")
                append("Prevodi: ${usage.translatedLanguages.joinToString(", ").ifBlank { "ni shranjenih" }}\n")
                append("Manjkajo: ${usage.missingLanguages.joinToString(", ").ifBlank { "nič" }}\n")
                append("Vprašanje: ${if (usage.hasQuestion) "prisotno" else "ni nastavljeno"}\n")
                append("Fiksna vrstica: ${usage.fixedTopRowPosition?.let { "F$it" } ?: "ni nastavljena"}")
            }
            textSize = 16f
            setTextColor(0xFFB8C0C8.toInt())
            setPadding(0, 8.dp(), 0, 12.dp())
        })
        addUsageSection(
            card = card,
            title = "UPORABA NA STRANEH (${usage.pageUsages.size})",
            emptyText = "Element ni postavljen na pacientovi strani.",
            rows = usage.pageUsages.map { page ->
                UsageNavigationRow(
                    label = "${page.pageTitle} (${page.pageId}) · pozicija ${page.position5x5}",
                    action = { openPageInEditor(page.pageId, page.pageTitle) }
                )
            }
        )
        addUsageSection(
            card = card,
            title = "STARŠI (${usage.parentItems.size})",
            emptyText = "Element ni podikona druge ikone.",
            rows = usage.parentItems.map { parent ->
                UsageNavigationRow(
                    label = "${parent.label.ifBlank { parent.itemId }} (${parent.itemId})",
                    action = { loadAacItemIntoEditor(parent.itemId) }
                )
            }
        )
        addUsageSection(
            card = card,
            title = "PODIKONE (${usage.childItems.size})",
            emptyText = "Element nima podikon.",
            rows = usage.childItems.map { child ->
                UsageNavigationRow(
                    label = "${child.label.ifBlank { child.itemId }} (${child.itemId})",
                    action = { loadAacItemIntoEditor(child.itemId) }
                )
            }
        )
        aacItemUsageInspectorActions.addView(card)
    }

    private fun addUsageSection(
        card: LinearLayout,
        title: String,
        emptyText: String,
        rows: List<UsageNavigationRow>
    ) {
        card.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(0xFFF4F7FA.toInt())
            setPadding(0, 10.dp(), 0, 6.dp())
        })
        if (rows.isEmpty()) {
            card.addView(TextView(this).apply {
                text = emptyText
                textSize = 14f
                setTextColor(0xFF9CA8B5.toInt())
            })
            return
        }
        rows.forEach { row ->
            card.addView(Button(this).apply {
                text = row.label
                setAllCaps(false)
                textSize = 17f
                setTextColor(0xFFF4F7FA.toInt())
                backgroundTintList = ColorStateList.valueOf(0xFF1E252C.toInt())
                setOnClickListener { row.action() }
            })
        }
    }

    private fun buildAacItemUsage(
        item: org.json.JSONObject,
        itemsArray: org.json.JSONArray?
    ): AacItemUsage {
        val itemId = item.optString("id").trim()
        val pageTitles = loadPatientPages().associate { page -> page.pageId to page.pageTitle }
        val translatedLanguages = storedTextLanguages(item)
        val activeLanguages = loadAacActiveLanguages()
        val missingLanguages = activeLanguages.filterNot { language -> language in translatedLanguages }
        val pageUsages = item.optJSONArray("placements")?.let { placements ->
            buildList {
                for (index in 0 until placements.length()) {
                    val placement = placements.optJSONObject(index) ?: continue
                    if (placement.optBoolean("generated", false)) continue
                    val pageId = placement.optString("pageId").trim()
                    val position = placement.optInt("position5x5", 0)
                    if (isSafePatientPageId(pageId) && position in 1..25) {
                        add(
                            AacItemPageUsage(
                                pageId = pageId,
                                pageTitle = pageTitles[pageId] ?: pageId,
                                position5x5 = position
                            )
                        )
                    }
                }
            }
        }.orEmpty()
        val children = stringArrayValues(item.optJSONArray("children"))
        val parentIdsFromItem = buildList {
            addAll(stringArrayValues(item.optJSONArray("visibleUnderIds")))
            addAll(stringArrayValues(item.optJSONArray("parentIds")))
            item.optString("parentId").trim().takeIf { it.isNotBlank() }?.let { add(it) }
        }
        val parents = mutableMapOf<String, UsageItemRef>()
        val childItems = mutableMapOf<String, UsageItemRef>()
        if (itemsArray != null) {
            for (index in 0 until itemsArray.length()) {
                val other = itemsArray.optJSONObject(index) ?: continue
                val otherId = other.optString("id").trim()
                if (otherId.isBlank()) continue
                if (otherId in children) {
                    childItems[otherId] = UsageItemRef(otherId, itemLabel(other))
                }
                if (itemId in stringArrayValues(other.optJSONArray("children")) || otherId in parentIdsFromItem) {
                    parents[otherId] = UsageItemRef(otherId, itemLabel(other))
                }
            }
        }
        return AacItemUsage(
            itemId = itemId,
            label = itemLabel(item),
            iconSource = itemIconSource(item),
            pageUsages = pageUsages.sortedWith(compareBy<AacItemPageUsage> { it.pageTitle }.thenBy { it.position5x5 }),
            parentItems = parents.values.sortedBy { it.label.ifBlank { it.itemId }.lowercase(Locale.ROOT) },
            childItems = childItems.values.sortedBy { it.label.ifBlank { it.itemId }.lowercase(Locale.ROOT) },
            fixedTopRowPosition = itemFixedTopRowPosition(item),
            translatedLanguages = translatedLanguages.sorted(),
            missingLanguages = missingLanguages,
            hasQuestion = itemHasQuestion(item)
        )
    }

    private fun storedTextLanguages(item: org.json.JSONObject): Set<String> {
        val languages = mutableSetOf<String>()
        listOf("labelByLanguage", "speechTextByLanguage").forEach { key ->
            val values = item.optJSONObject(key) ?: return@forEach
            val keys = values.keys()
            while (keys.hasNext()) {
                val language = keys.next().trim().lowercase(Locale.ROOT)
                if (language.isNotBlank() && values.optString(language).isNotBlank()) {
                    languages += language
                }
            }
        }
        if (item.optString("labelSl").isNotBlank() || item.optString("speechText").isNotBlank() || item.optString("speakTextSl").isNotBlank()) languages += "sl"
        if (item.optString("labelUk").isNotBlank() || item.optString("speakTextUk").isNotBlank() || item.optString("speechTextUk").isNotBlank()) languages += "uk"
        if (item.optString("labelEn").isNotBlank() || item.optString("speakTextEn").isNotBlank() || item.optString("speechTextEn").isNotBlank()) languages += "en"
        return languages
    }

    private fun itemHasQuestion(item: org.json.JSONObject): Boolean {
        val questionByLanguage = item.optJSONObject("questionByLanguage")
        if (questionByLanguage != null) {
            val keys = questionByLanguage.keys()
            while (keys.hasNext()) {
                if (questionByLanguage.optString(keys.next()).trim().isNotBlank()) {
                    return true
                }
            }
        }
        return item.optString("questionSl").isNotBlank() || item.optString("questionUk").isNotBlank()
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
        val itemCounts = patientPageItemCounts()
        return buildString {
            append("PACIENTOVE STRANI\n")
            append("Ustvari pacientovo stran, izberi ikono in jo postavi na stran.\n")
            append("Kategorije so za iskanje ikon; pacientove strani so zaporedne strani.\n")
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
                    val count = itemCounts[page.pageId] ?: 0
                    append("- ${page.pageTitle} (${page.pageId}) - $count ikon$marker\n")
                }
            }
        }.trimEnd()
    }

    private fun renderPatientPageBrowser() {
        patientPageActions.removeAllViews()
        val pages = loadPatientPages()
        val itemCounts = patientPageItemCounts()
        val fixedRowCount = fixedTopRowCellItems().size
        val defaultPageId = defaultPatientPageId()
        val selectedPageId = selectedPlacementPageId()
        if (pages.isEmpty()) {
            patientPageActions.addView(buildAacItemListMessage("Ni pacientovih strani. Ustvari stran, nato izberi postavitve."))
            return
        }
        pages.forEach { page ->
            val button = Button(this).apply {
                val marker = if (page.pageId == defaultPageId) " · začetna" else ""
                text = "${page.pageTitle}\n${page.pageId} · ${itemCounts[page.pageId] ?: 0} ikon · fiksnih: $fixedRowCount$marker"
                setAllCaps(false)
                textSize = 17f
                setTextColor(0xFFF4F7FA.toInt())
                setBackgroundColor(
                    when (page.pageId) {
                        selectedPageId -> 0xFF214A78.toInt()
                        defaultPageId -> 0xFF2F5F9E.toInt()
                        else -> 0xFF34414D.toInt()
                    }
                )
                setPadding(18.dp(), 10.dp(), 18.dp(), 10.dp())
                setOnClickListener {
                    editPatientPageId.setText(page.pageId)
                    editPatientPageTitle.setText(page.pageTitle)
                    editPlacementPageId.setText(page.pageId)
                    previewPageStack.clear()
                    previewPageStack += page.pageId
                    txtStatus.text = "Stran izbrana.\n${page.pageTitle} (${page.pageId})"
                    renderPageWorkspace()
                    renderPlacementGrid()
                    renderPatientFlowPreview()
                }
            }
            patientPageActions.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    96.dp()
                ).apply {
                    bottomMargin = 10.dp()
                }
            )
        }
    }

    private fun patientPageItemCounts(): Map<String, Int> {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText) ?: return emptyMap()
        val counts = mutableMapOf<String, Int>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val placements = item.optJSONArray("placements") ?: continue
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                val pageId = placement.optString("pageId").trim()
                val position = placement.optInt("position5x5", 0)
                if (isSafePatientPageId(pageId) && position in 1..25) {
                    counts[pageId] = (counts[pageId] ?: 0) + 1
                }
            }
        }
        return counts
    }

    private fun renderPlacementGrid() {
        placementGridActions.removeAllViews()
        val pageId = selectedPlacementPageId()
        if (!isSafePatientPageId(pageId)) {
            placementGridActions.addView(buildAacItemListMessage("Izberi ali vnesi stran za vizualni predogled 5x5."))
            return
        }
        val pageTitle = loadPatientPages()
            .firstOrNull { it.pageId == pageId }
            ?.pageTitle
            ?: pageId
        val placedItems = pageCellItems(pageId)
        placementGridActions.addView(
            buildAacItemListMessage("OBLIKOVALEC PACIENTOVE STRANI\n$pageTitle ($pageId)\nFiksna vrstica je vedno zgoraj. Vsebina strani je spodaj. Tapni ikono za urejanje, drzi za zamenjavo ali brisanje.")
        )
        placementGridActions.addView(buildPlacementGridHeading("FIKSNA VRSTICA"))
        placementGridActions.addView(buildPatientDesignerFixedRow())
        placementGridActions.addView(buildPlacementGridHeading("VSEBINA STRANI"))
        for (rowIndex in 0 until 5) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (columnIndex in 0 until 5) {
                val position = rowIndex * 5 + columnIndex + 1
                val cellItem = placedItems[position]
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
                    setBackgroundColor(placementCellBackground(cellItem))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        editPlacementPageId.setText(pageId)
                        editPlacementPosition5x5.setText(position.toString())
                        if (cellItem == null) {
                            showPlacementCellItemChooser(pageId, position, null)
                        } else if (cellItem.isFixedTopRowCell) {
                            loadAacItemIntoEditor(cellItem.itemId)
                        } else {
                            loadAacItemIntoEditor(cellItem.itemId)
                        }
                    }
                    setOnLongClickListener {
                        editPlacementPageId.setText(pageId)
                        editPlacementPosition5x5.setText(position.toString())
                        if (cellItem == null) {
                            showPlacementCellItemChooser(pageId, position, null)
                        } else if (cellItem.isFixedTopRowCell) {
                            showFixedPlacementCellActions(position, cellItem)
                        } else {
                            showPlacementCellActions(pageId, position, cellItem)
                        }
                        true
                    }
                }
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setBackgroundColor(0xFF172029.toInt())
                    setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                }
                val label = TextView(this).apply {
                    text = placementCellLabel(position, cellItem)
                    gravity = Gravity.CENTER
                    maxLines = 4
                    textSize = 13f
                    setTextColor(if (cellItem == null) 0xFF7F8A96.toInt() else 0xFFF4F7FA.toInt())
                }
                if (cellItem != null) {
                    bindPlacementCellIconPreview(image, cellItem)
                    cell.addView(
                        image,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            52.dp()
                        )
                    )
                }
                cell.addView(
                    label,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                )
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(
                        0,
                        104.dp(),
                        1f
                    ).apply {
                        marginEnd = if (columnIndex < 4) 6.dp() else 0
                    }
                )
            }
            placementGridActions.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 6.dp()
                }
            )
        }
    }

    private fun buildPlacementGridHeading(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 17f
            setTextColor(0xFFF4F7FA.toInt())
            setPadding(0, 12.dp(), 0, 8.dp())
        }
    }

    private fun buildPatientDesignerFixedRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val fixedCells = fixedTopRowCellItems()
        for (position in 1..5) {
            val cellItem = fixedCells[position]
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
                setBackgroundColor(if (cellItem == null) 0xFF172029.toInt() else 0xFF214A78.toInt())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    editFixedTopRowPosition.setText(position.toString())
                    if (cellItem == null) {
                        showFixedPlacementCellItemChooser(position, null)
                    } else {
                        loadAacItemIntoEditor(cellItem.itemId)
                    }
                }
                setOnLongClickListener {
                    editFixedTopRowPosition.setText(position.toString())
                    if (cellItem == null) {
                        showFixedPlacementCellItemChooser(position, null)
                    } else {
                        showFixedPlacementCellActions(position, cellItem)
                    }
                    true
                }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setBackgroundColor(0xFF172029.toInt())
                setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            }
            val label = TextView(this).apply {
                text = cellItem?.let { item ->
                    "F$position\n${item.label.ifBlank { item.itemId }}"
                } ?: "F$position\nprosto"
                gravity = Gravity.CENTER
                maxLines = 3
                textSize = 13f
                setTextColor(if (cellItem == null) 0xFF9CA8B5.toInt() else 0xFFF4F7FA.toInt())
            }
            if (cellItem != null) {
                bindPlacementCellIconPreview(image, cellItem)
                cell.addView(
                    image,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        54.dp()
                    )
                )
            }
            cell.addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            row.addView(
                cell,
                LinearLayout.LayoutParams(
                    0,
                    116.dp(),
                    1f
                ).apply {
                    marginEnd = if (position < 5) 4.dp() else 0
                }
            )
        }
        return row
    }

    private fun placementCellBackground(cellItem: PlacementCellItem?): Int {
        return when {
            cellItem == null -> 0xFF172029.toInt()
            placementCellHasProblem(cellItem) -> 0xFF604B24.toInt()
            cellItem.isFixedTopRowCell -> 0xFF214A78.toInt()
            else -> 0xFF34414D.toInt()
        }
    }

    private fun placementCellLabel(position: Int, cellItem: PlacementCellItem?): String {
        if (cellItem == null) {
            return "PROSTO\n$position"
        }
        return buildString {
            append(cellItem.label.ifBlank { cellItem.itemId })
            cellItem.fixedTopRowPosition?.let { fixedPosition ->
                append("\nF$fixedPosition")
            }
            if (placementCellHasProblem(cellItem)) {
                append("\nPOZOR")
            }
        }
    }

    private fun placementCellHasProblem(cellItem: PlacementCellItem): Boolean {
        return cellItem.translationStatus == ItemTranslationStatus.MISSING || placementCellMissingImage(cellItem)
    }

    private fun placementCellMissingImage(cellItem: PlacementCellItem): Boolean {
        return cellItem.imagePath.isNotBlank() &&
            cellItem.iconSource != IconSource.SYSTEM &&
            AacStoragePaths.resolveIconFile(this, cellItem.imagePath, cellItem.iconSource)?.isFile != true
    }

    private fun renderPageWorkspace() {
        pageWorkspaceActions.removeAllViews()
        val pageId = selectedPlacementPageId()
        if (!isSafePatientPageId(pageId)) {
            pageWorkspaceActions.addView(buildAacItemListMessage("Izberi stran za delovni prostor."))
            return
        }
        val pageTitle = loadPatientPages().firstOrNull { it.pageId == pageId }?.pageTitle ?: pageId
        val cells = pageCellItems(pageId)
        val diagnostics = pageWorkspaceDiagnostics(pageId, cells)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF26323D.toInt())
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
        }
        card.addView(TextView(this).apply {
            text = "PACIENTOVA STRAN: $pageTitle"
            textSize = 20f
            setTextColor(0xFFF4F7FA.toInt())
        })
        card.addView(TextView(this).apply {
            text = buildString {
                append("$pageId · ${cells.size} ikon na strani · fiksnih: ${fixedTopRowCellItems().size}/5\n")
                append("Podikone na strani: ${diagnostics.subiconCount}\n")
                append("Manjkajoči prevodi: ${diagnostics.missingTranslations}\n")
                append("Manjkajoče slike: ${diagnostics.missingImages}\n")
                append("Podvojene pozicije: ${diagnostics.duplicatePlacements}")
            }
            textSize = 16f
            setTextColor(0xFFB8C0C8.toInt())
            setPadding(0, 8.dp(), 0, 8.dp())
        })
        card.addView(TextView(this).apply {
            text = "Hitro delo: tapni prazno polje za dodajanje. Tapni ikono za urejanje. Drzi ikono za zamenjavo ali brisanje."
            textSize = 15f
            setTextColor(0xFFD7DEE7.toInt())
            setPadding(0, 0, 0, 4.dp())
        })
        card.addView(TextView(this).apply {
            text = "Prihodnje prilagajanje: uporaba, zadnja uporaba in AI predlogi lahko pomagajo pri vrstnem redu, vendar ne smejo premakniti fiksne vrstice ali roÄŤno zaklenjenih mest."
            textSize = 14f
            setTextColor(0xFF9CA8B5.toInt())
            setPadding(0, 2.dp(), 0, 4.dp())
        })
        if (diagnostics.warningLines.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = diagnostics.warningLines.joinToString("\n")
                textSize = 15f
                setTextColor(0xFFFFD27A.toInt())
                setPadding(0, 8.dp(), 0, 0)
            })
        }
        pageWorkspaceActions.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
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
            val selectedParent = editSubiconParentId.text.toString().trim().ifBlank { "ni izbran" }
            val selectedChild = editSubiconChildId.text.toString().trim().ifBlank { "ni izbrana" }
            append("Izbran stars: $selectedParent\n")
            append("Izbrana podikona: $selectedChild\n\n")
            val childLines = currentItemsArray(itemsText)
                ?.let { array ->
                    val labelsById = buildMap {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val itemId = item.optString("id").trim()
                            if (itemId.isNotBlank()) {
                                put(itemId, itemLabel(item).ifBlank { itemId })
                            }
                        }
                    }
                    buildList {
                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val parentId = item.optString("id").trim()
                            val children = item.optJSONArray("children") ?: continue
                            if (parentId.isBlank()) continue
                            add("${labelsById[parentId] ?: parentId}")
                            for (childIndex in 0 until children.length()) {
                                val childId = children.optString(childIndex).trim()
                                if (childId.isNotBlank()) {
                                    val branch = if (childIndex == children.length() - 1) "└─" else "├─"
                                    add("$branch ${labelsById[childId] ?: childId} ($childId)")
                                }
                            }
                            add("")
                        }
                    }
                }
                .orEmpty()
            if (childLines.isEmpty()) {
                append("Ni nastavljenih podikon.")
            } else {
                childLines.take(40).forEach { append("$it\n") }
                val remaining = childLines.size - 40
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
                txtSubiconStatus.text = buildSubiconStatus()
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

    private fun updateLibraryFilterButtons() {
        updateIconSourceFilterButtons()
        translationFilterButtons.forEach { (filter, button) ->
            val color = if (filter == therapistTranslationFilter) 0xFF2F5F9E.toInt() else 0xFF34414D.toInt()
            button.backgroundTintList = ColorStateList.valueOf(color)
        }
        pageUsageFilterButtons.forEach { (filter, button) ->
            val color = if (filter == therapistPageUsageFilter) 0xFF2F5F9E.toInt() else 0xFF34414D.toInt()
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

    private fun selectedPlacementPageId(): String {
        return editPlacementPageId.text.toString().trim()
            .ifBlank { editPatientPageId.text.toString().trim() }
            .ifBlank { defaultPatientPageId() }
    }

    private fun pageCellItems(pageId: String): Map<Int, PlacementCellItem> {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText) ?: return emptyMap()
        val cells = linkedMapOf<Int, PlacementCellItem>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) continue
            val placements = item.optJSONArray("placements") ?: continue
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                if (placement.optBoolean("generated", false)) continue
                val placementPageId = placement.optString("pageId").trim()
                val position = placement.optInt("position5x5", 0)
                if (placementPageId == pageId && position in 1..25 && !cells.containsKey(position)) {
                    cells[position] = placementCellItemFromJson(item, itemFixedTopRowPosition(item), isFixedTopRowCell = false)
                }
            }
        }
        return cells
    }

    private fun placementCellItemFromJson(
        item: org.json.JSONObject,
        fixedTopRowPosition: Int?,
        isFixedTopRowCell: Boolean
    ): PlacementCellItem {
        val targetPageId = item.optString("targetPageId")
            .ifBlank { item.optString("opensPageId") }
            .ifBlank { item.optString("pageIdToOpen") }
            .trim()
        return PlacementCellItem(
            itemId = item.optString("id").trim(),
            label = itemLabel(item),
            iconSource = itemIconSource(item),
            imagePath = item.optString("imagePath")
                .ifBlank { item.optString("image_path") }
                .ifBlank { item.optString("icon") },
            fixedTopRowPosition = fixedTopRowPosition,
            isFixedTopRowCell = isFixedTopRowCell,
            targetPageId = targetPageId.ifBlank { null },
            translationStatus = itemTranslationStatus(item, loadAacActiveLanguages())
        )
    }

    private fun bindPlacementCellIconPreview(preview: ImageView, item: PlacementCellItem) {
        val imageFile = AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
        if (imageFile?.isFile != true) {
            preview.setImageDrawable(null)
            return
        }
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            preview.setImageDrawable(null)
        } else {
            preview.setImageBitmap(bitmap)
        }
    }

    private fun showPlacementCellActions(pageId: String, position: Int, currentItem: PlacementCellItem) {
        val title = "Pozicija $position"
        val message = "${currentItem.label.ifBlank { currentItem.itemId }}\n${currentItem.itemId}"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Uredi ikono") { _, _ ->
                loadAacItemIntoEditor(currentItem.itemId)
            }
            .setNegativeButton("Zamenjaj") { _, _ ->
                showPlacementCellItemChooser(pageId, position, currentItem)
            }
            .setNeutralButton("Počisti") { _, _ ->
                confirmClearPlacementCell(pageId, position, currentItem)
            }
            .show()
    }

    private fun showFixedPlacementCellActions(position: Int, currentItem: PlacementCellItem) {
        AlertDialog.Builder(this)
            .setTitle("Fiksna pozicija $position")
            .setMessage("${currentItem.label.ifBlank { currentItem.itemId }}\n${currentItem.itemId}\nTa celica je nadzorovana prek fiksne prve vrstice.")
            .setPositiveButton("Zamenjaj") { _, _ ->
                showFixedPlacementCellItemChooser(position, currentItem)
            }
            .setNegativeButton("Počisti") { _, _ ->
                confirmClearFixedPlacementCell(position, currentItem)
            }
            .setNeutralButton("Prekliči", null)
            .show()
    }

    private fun showFixedPlacementCellItemChooser(position: Int, currentItem: PlacementCellItem?) {
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
            .setTitle("Izberi fiksno ikono $position")
            .setItems(labels) { _, index ->
                val selectedItem = items[index]
                when (writeFixedTopRowPosition(selectedItem.itemId, position)) {
                    FixedTopRowWriteResult.Success -> {
                        editFixedTopRowItemId.setText(selectedItem.itemId)
                        editFixedTopRowPosition.setText(position.toString())
                        txtStatus.text = if (currentItem == null) {
                            "Fiksna ikona nastavljena.\nF$position -> ${selectedItem.itemId}"
                        } else {
                            "Fiksna ikona zamenjana.\n${currentItem.itemId} -> ${selectedItem.itemId}"
                        }
                        refreshLocalAacOverview()
                    }
                    FixedTopRowWriteResult.ItemsFileMissing -> txtStatus.text = "AAC elementi niso najdeni."
                    FixedTopRowWriteResult.ItemNotFound -> txtStatus.text = "ID AAC elementa ne obstaja."
                    FixedTopRowWriteResult.WriteFailed -> txtStatus.text = "Fiksne pozicije ni bilo mogoce shraniti."
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun confirmClearFixedPlacementCell(position: Int, currentItem: PlacementCellItem) {
        AlertDialog.Builder(this)
            .setTitle("Počisti fiksno pozicijo?")
            .setMessage("Odstrani ${currentItem.label.ifBlank { currentItem.itemId }} iz fiksne prve vrstice, pozicija $position?\nIkona, prevodi, postavitve in podikone ostanejo shranjeni.")
            .setPositiveButton("Počisti") { _, _ ->
                when (clearFixedTopRowPositionInJson(position)) {
                    FixedTopRowWriteResult.Success -> {
                        editFixedTopRowPosition.setText(position.toString())
                        txtStatus.text = "Fiksna pozicija počiščena.\n$position"
                        refreshLocalAacOverview()
                    }
                    FixedTopRowWriteResult.ItemsFileMissing -> txtStatus.text = "AAC elementi niso najdeni."
                    FixedTopRowWriteResult.ItemNotFound -> txtStatus.text = "Na poziciji $position ni fiksnega AAC elementa."
                    FixedTopRowWriteResult.WriteFailed -> txtStatus.text = "Fiksne pozicije ni bilo mogoce počistiti."
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun showPlacementCellItemChooser(pageId: String, position: Int, currentItem: PlacementCellItem?) {
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
            .setTitle("Izberi ikono za pozicijo $position")
            .setItems(labels) { _, index ->
                val selectedItem = items[index]
                when (replacePlacementCell(pageId, position, selectedItem.itemId)) {
                    AacMetadataWriteResult.Success -> {
                        editPlacementItemId.setText(selectedItem.itemId)
                        editPlacementPageId.setText(pageId)
                        editPlacementPosition5x5.setText(position.toString())
                        txtStatus.text = if (currentItem == null) {
                            "Ikona dodana na stran.\n${selectedItem.itemId} -> $pageId / $position"
                        } else {
                            "Ikona zamenjana.\n${currentItem.itemId} -> ${selectedItem.itemId}"
                        }
                        refreshLocalAacOverview()
                    }
                    AacMetadataWriteResult.ItemNotFound -> txtStatus.text = "ID AAC elementa ne obstaja."
                    AacMetadataWriteResult.WriteFailed -> txtStatus.text = "Postavitve ni bilo mogoce shraniti."
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun confirmClearPlacementCell(pageId: String, position: Int, currentItem: PlacementCellItem) {
        AlertDialog.Builder(this)
            .setTitle("Počisti pozicijo?")
            .setMessage("Odstrani ${currentItem.label.ifBlank { currentItem.itemId }} iz strani $pageId, pozicija $position?\nIkona, prevodi in podikone ostanejo shranjeni.")
            .setPositiveButton("Počisti") { _, _ ->
                when (clearPlacementCell(pageId, position)) {
                    AacMetadataWriteResult.Success -> {
                        txtStatus.text = "Polje počiščeno.\n$pageId / $position"
                        refreshLocalAacOverview()
                    }
                    AacMetadataWriteResult.ItemNotFound -> txtStatus.text = "Na tej poziciji ni ikone."
                    AacMetadataWriteResult.WriteFailed -> txtStatus.text = "Postavitve ni bilo mogoce počistiti."
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun renderPatientFlowPreview() {
        previewFixedTopRowActions.removeAllViews()
        previewGridActions.removeAllViews()
        renderPreviewFixedTopRow()
        val pageId = currentPreviewPageId()
        if (!isSafePatientPageId(pageId)) {
            previewGridActions.addView(buildAacItemListMessage("Ni izbrane pacientove strani za predogled."))
            return
        }
        val pageTitle = loadPatientPages().firstOrNull { it.pageId == pageId }?.pageTitle ?: pageId
        previewGridActions.addView(
            buildAacItemListMessage("Predogled pacienta: $pageTitle ($pageId)\nTapni ikono za simulacijo odpiranja strani ali podikon. Podatki se ne spreminjajo.")
        )
        val pageItems = previewPageCellItems(pageId)
        for (rowIndex in 0 until 5) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            for (columnIndex in 0 until 5) {
                val position = rowIndex * 5 + columnIndex + 1
                val item = pageItems[position]
                val cell = if (position == 25) {
                    buildPreviewBackCell()
                } else {
                    buildPreviewCell(item, position)
                }
                row.addView(
                    cell,
                    LinearLayout.LayoutParams(
                        0,
                        104.dp(),
                        1f
                    ).apply {
                        marginEnd = if (columnIndex < 4) 6.dp() else 0
                    }
                )
            }
            previewGridActions.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 6.dp()
                }
            )
        }
    }

    private fun renderPreviewFixedTopRow() {
        val fixedCells = fixedTopRowCellItems()
        previewFixedTopRowActions.addView(
            buildAacItemListMessage("Fiksna vrstica: 5x5 pokaže F1-F5, 4x4 F1-F4, 3x3 F1-F3. Preostale ikone ostanejo v normalnem toku.")
        )
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (position in 1..5) {
            row.addView(
                buildPreviewCell(fixedCells[position], position, fixedLabel = "F$position"),
                LinearLayout.LayoutParams(
                    0,
                    104.dp(),
                    1f
                ).apply {
                    marginEnd = if (position < 5) 6.dp() else 0
                }
            )
        }
        previewFixedTopRowActions.addView(row)
    }

    private fun buildPreviewCell(
        item: PlacementCellItem?,
        position: Int,
        fixedLabel: String? = null
    ): LinearLayout {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            setBackgroundColor(
                when {
                    item == null -> 0xFF172029.toInt()
                    fixedLabel != null -> 0xFF214A78.toInt()
                    placementCellHasProblem(item) -> 0xFF604B24.toInt()
                    else -> 0xFF34414D.toInt()
                }
            )
            isClickable = item != null
            isFocusable = item != null
            if (item != null) {
                setOnClickListener { openPreviewItem(item) }
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setBackgroundColor(0xFF172029.toInt())
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        }
        val label = TextView(this).apply {
            text = item?.let { previewCellLabel(it, fixedLabel) } ?: fixedLabel ?: position.toString()
            gravity = Gravity.CENTER
            maxLines = 3
            textSize = 13f
            setTextColor(if (item == null) 0xFF7F8A96.toInt() else 0xFFF4F7FA.toInt())
        }
        if (item != null) {
            bindPlacementCellIconPreview(image, item)
            cell.addView(
                image,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    52.dp()
                )
            )
        }
        cell.addView(
            label,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        return cell
    }

    private fun buildPreviewBackCell(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
            setBackgroundColor(0xFF214A78.toInt())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (previewPageStack.size > 1) {
                    previewPageStack.removeAt(previewPageStack.lastIndex)
                } else {
                    previewPageStack.clear()
                }
                renderPatientFlowPreview()
            }
            addView(
                TextView(this@AacPackSettingsActivity).apply {
                    text = if (previewPageStack.size > 1) "NAZAJ" else "DOMOV"
                    gravity = Gravity.CENTER
                    textSize = 15f
                    setTextColor(0xFFF4F7FA.toInt())
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun previewCellLabel(item: PlacementCellItem, fixedLabel: String?): String {
        return buildString {
            if (fixedLabel != null) {
                append(fixedLabel)
                append("\n")
            }
            append(item.label.ifBlank { item.itemId })
            append("\n")
            append(item.iconSource.name)
            append(" · ")
            append(item.translationStatus.label)
        }
    }

    private fun openPreviewItem(item: PlacementCellItem) {
        val targetPage = item.targetPageId?.takeIf { isSafePatientPageId(it) }
        val childPageId = "children_${item.itemId}"
        when {
            targetPage != null -> {
                previewPageStack += targetPage
                renderPatientFlowPreview()
            }
            previewChildItems(item.itemId).isNotEmpty() -> {
                previewPageStack += childPageId
                renderPatientFlowPreview()
            }
            else -> {
                txtStatus.text = "Predogled: ${item.label.ifBlank { item.itemId }}\nKončna ikona bi spregovorila po pacientovem toku."
            }
        }
    }

    private fun currentPreviewPageId(): String {
        if (previewPageStack.isEmpty()) {
            val startPage = editPlacementPageId.text.toString().trim()
                .ifBlank { editPatientPageId.text.toString().trim() }
                .ifBlank { defaultPatientPageId() }
            if (isSafePatientPageId(startPage)) {
                previewPageStack += startPage
            }
        }
        return previewPageStack.lastOrNull().orEmpty()
    }

    private fun previewChildItems(parentId: String): List<PlacementCellItem> {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText) ?: return emptyList()
        val parent = findItemById(itemsArray, parentId) ?: return emptyList()
        val childIds = stringArrayValues(parent.optJSONArray("children"))
        return childIds.mapNotNull { childId ->
            findItemById(itemsArray, childId)?.let { item ->
                placementCellItemFromJson(
                    item = item,
                    fixedTopRowPosition = itemFixedTopRowPosition(item),
                    isFixedTopRowCell = false
                )
            }
        }
    }

    private fun previewPageCellItems(pageId: String): Map<Int, PlacementCellItem> {
        if (!pageId.startsWith("children_")) {
            return pageCellItems(pageId)
        }
        val parentId = pageId.removePrefix("children_")
        val children = previewChildItems(parentId)
        return children
            .take(24)
            .mapIndexed { index, item -> index + 1 to item }
            .toMap()
    }

    private fun pageWorkspaceDiagnostics(
        pageId: String,
        cells: Map<Int, PlacementCellItem>
    ): PageWorkspaceDiagnostics {
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText)
        val duplicatePlacements = itemsArray?.let { countDuplicatePlacementsForPage(it, pageId) } ?: 0
        val pageItems = cells.values.filter { !it.isFixedTopRowCell }
        val missingTranslations = pageItems.count { it.translationStatus == ItemTranslationStatus.MISSING }
        val missingImages = pageItems.count { item ->
            item.imagePath.isNotBlank() &&
                item.iconSource != IconSource.SYSTEM &&
                AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)?.isFile != true
        }
        val subiconCount = pageItems.count { previewChildItems(it.itemId).isNotEmpty() }
        val warningLines = mutableListOf<String>()
        if (missingTranslations > 0) warningLines += "PREVODI: na strani manjkajo prevodi."
        if (missingImages > 0) warningLines += "SLIKE: nekatere slike niso najdene."
        if (duplicatePlacements > 0) warningLines += "POSTAVITEV: podvojene pozicije na strani."
        if (pageItems.isEmpty()) warningLines += "PRAZNA STRAN: klikni prazno polje in izberi ikono."
        return PageWorkspaceDiagnostics(
            subiconCount = subiconCount,
            missingTranslations = missingTranslations,
            missingImages = missingImages,
            duplicatePlacements = duplicatePlacements,
            warningLines = warningLines
        )
    }

    private fun countDuplicatePlacementsForPage(itemsArray: org.json.JSONArray, pageId: String): Int {
        val counts = mutableMapOf<Int, Int>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val placements = item.optJSONArray("placements") ?: continue
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                if (placement.optBoolean("generated", false)) continue
                val placementPageId = placement.optString("pageId").trim()
                val position = placement.optInt("position5x5", 0)
                if (placementPageId == pageId && position in 1..25) {
                    counts[position] = (counts[position] ?: 0) + 1
                }
            }
        }
        return counts.values.count { count -> count > 1 }
    }

    private fun renderCommunicatorStructureOverview(overview: LocalAacOverview) {
        communicatorStructureActions.removeAllViews()
        val itemsFile = AacStoragePaths.getAacItemsFile(this)
        val itemsText = itemsFile?.let { readTextSafely(it, MAX_ITEMS_PREVIEW_BYTES) }
        val itemsArray = currentItemsArray(itemsText)
        val activeLanguages = loadAacActiveLanguages()
        val translationStatus = itemsArray?.let { buildTranslationStatus(it, activeLanguages) }
            ?: TranslationStatus.empty(activeLanguages)
        communicatorStructureActions.addView(
            buildAacItemListMessage(
                buildString {
                    append("Strani: ${loadPatientPages().size}\n")
                    append("AAC elementi: ${overview.relationAnalysis.availableItems.size}\n")
                    append("Fiksna vrstica: ${overview.relationAnalysis.fixedTopRowItems.size}/5\n")
                    append("Elementi brez profila: ${overview.orphanItemCount}\n")
                    append("Prevodi manjkajo: ${translationStatus.missingCount}\n")
                    append("Prevodi pripravljeni: ${translationStatus.translatedCount}\n")
                    append("Prevodi v pripravi: ${translationStatus.pendingCount}")
                }
            )
        )
        if (itemsArray == null) {
            communicatorStructureActions.addView(buildAacItemListMessage("AAC elementi niso najdeni."))
            return
        }
        val structure = buildCommunicatorStructure(itemsArray)
        if (structure.pages.isEmpty()) {
            communicatorStructureActions.addView(buildAacItemListMessage("Ni pacientovih strani za prikaz."))
            return
        }
        structure.pages.forEach { page ->
            addStructurePageCard(page)
        }
    }

    private fun addStructurePageCard(page: StructurePage) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF26323D.toInt())
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                openPageInEditor(page.pageId, page.pageTitle)
            }
        }
        val title = TextView(this).apply {
            text = "${page.pageTitle} (${page.pageId})"
            textSize = 16f
            setTextColor(0xFFF4F7FA.toInt())
        }
        val detail = TextView(this).apply {
            text = "${page.items.size} ikon · tapni za urejanje strani"
            textSize = 13f
            setTextColor(0xFFB8C0C8.toInt())
        }
        card.addView(title)
        card.addView(detail)
        page.items.take(12).forEach { item ->
            card.addView(buildStructureItemRow(item, indentLevel = 1))
            item.children.take(8).forEach { child ->
                card.addView(buildStructureItemRow(child, indentLevel = 2))
            }
        }
        if (page.items.size > 12) {
            card.addView(buildAacItemListMessage("... se ${page.items.size - 12} ikon"))
        }
        communicatorStructureActions.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp()
            }
        )
    }

    private fun buildStructureItemRow(item: StructureItem, indentLevel: Int): TextView {
        return TextView(this).apply {
            val prefix = if (indentLevel == 1) "  ├ " else "      └ "
            val translationText = when (item.translationStatus) {
                ItemTranslationStatus.TRANSLATED -> "prevedeno"
                ItemTranslationStatus.MISSING -> "manjka prevod"
                ItemTranslationStatus.PENDING -> "v pripravi"
            }
            text = "$prefix${item.label.ifBlank { item.itemId }} (${item.itemId}) · $translationText"
            textSize = if (indentLevel == 1) 14f else 13f
            setTextColor(if (item.translationStatus == ItemTranslationStatus.MISSING) 0xFFFFD27A.toInt() else 0xFFDEE5EC.toInt())
            setBackgroundColor(if (indentLevel == 1) 0xFF1E252C.toInt() else 0xFF202A33.toInt())
            setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
            isClickable = true
            isFocusable = true
            setOnClickListener {
                loadAacItemIntoEditor(item.itemId)
            }
        }
    }

    private fun openPageInEditor(pageId: String, pageTitle: String) {
        editPatientPageId.setText(pageId)
        editPatientPageTitle.setText(pageTitle)
        editPlacementPageId.setText(pageId)
        txtStatus.text = "Stran odprta v urejevalniku.\n$pageTitle ($pageId)"
        previewPageStack.clear()
        previewPageStack.add(pageId)
        renderPageWorkspace()
        renderPlacementGrid()
        renderPatientFlowPreview()
    }

    private fun buildCommunicatorStructure(itemsArray: org.json.JSONArray): CommunicatorStructure {
        val pagesById = loadPatientPages().associateBy { it.pageId }
        val pageItems = linkedMapOf<String, MutableList<StructureItem>>()
        val labelsById = mutableMapOf<String, String>()
        val itemsById = mutableMapOf<String, org.json.JSONObject>()
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) continue
            labelsById[itemId] = itemLabel(item).ifBlank { itemId }
            itemsById[itemId] = item
        }
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            val itemId = item.optString("id").trim()
            if (itemId.isBlank()) continue
            val placements = item.optJSONArray("placements") ?: continue
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                if (placement.optBoolean("generated", false)) continue
                val pageId = placement.optString("pageId").trim()
                val position = placement.optInt("position5x5", 0)
                if (!isSafePatientPageId(pageId) || position !in 1..25) continue
                pageItems.getOrPut(pageId) { mutableListOf() } += structureItemFromJson(
                    item = item,
                    position = position,
                    labelsById = labelsById,
                    itemsById = itemsById
                )
            }
        }
        val pages = pageItems.map { (pageId, items) ->
            val page = pagesById[pageId]
            StructurePage(
                pageId = pageId,
                pageTitle = page?.pageTitle ?: pageId,
                items = items.sortedWith(compareBy<StructureItem> { it.position }.thenBy { it.itemId })
            )
        }.sortedBy { page -> page.pageTitle.lowercase(Locale.ROOT) }
        return CommunicatorStructure(pages)
    }

    private fun structureItemFromJson(
        item: org.json.JSONObject,
        position: Int,
        labelsById: Map<String, String>,
        itemsById: Map<String, org.json.JSONObject>
    ): StructureItem {
        val itemId = item.optString("id").trim()
        val children = stringArrayValues(item.optJSONArray("children"))
            .mapNotNull { childId ->
                val childItem = itemsById[childId] ?: return@mapNotNull null
                StructureItem(
                    itemId = childId,
                    label = labelsById[childId].orEmpty(),
                    position = 0,
                    children = emptyList(),
                    translationStatus = itemTranslationStatus(childItem, loadAacActiveLanguages())
                )
            }
        return StructureItem(
            itemId = itemId,
            label = labelsById[itemId].orEmpty(),
            position = position,
            children = children,
            translationStatus = itemTranslationStatus(item, loadAacActiveLanguages())
        )
    }

    private fun buildTranslationStatus(
        itemsArray: org.json.JSONArray,
        activeLanguages: List<String>
    ): TranslationStatus {
        var translated = 0
        var missing = 0
        var pending = 0
        for (index in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(index) ?: continue
            when (itemTranslationStatus(item, activeLanguages)) {
                ItemTranslationStatus.TRANSLATED -> translated += 1
                ItemTranslationStatus.MISSING -> missing += 1
                ItemTranslationStatus.PENDING -> pending += 1
            }
        }
        return TranslationStatus(activeLanguages, translated, missing, pending)
    }

    private fun itemTranslationStatus(
        item: org.json.JSONObject,
        activeLanguages: List<String>
    ): ItemTranslationStatus {
        val languages = activeLanguages.ifEmpty { listOf("sl") }
        val labelMap = item.optJSONObject("labelByLanguage")
        val speechMap = item.optJSONObject("speechTextByLanguage")
        val missingLanguage = languages.any { language ->
            !hasStoredLabelForLanguage(item, labelMap, language) ||
                !hasStoredSpeechForLanguage(item, speechMap, language)
        }
        if (!missingLanguage) return ItemTranslationStatus.TRANSLATED
        val generated = item.optBoolean("translationGenerated", false)
        val source = item.optString("translationSource").trim()
        return if (generated && source.isNotBlank()) ItemTranslationStatus.PENDING else ItemTranslationStatus.MISSING
    }

    private fun manualPlacementPageIds(item: org.json.JSONObject): Set<String> {
        val placements = item.optJSONArray("placements") ?: return emptySet()
        val pageIds = linkedSetOf<String>()
        for (index in 0 until placements.length()) {
            val placement = placements.optJSONObject(index) ?: continue
            if (placement.optBoolean("generated", false)) continue
            val pageId = placement.optString("pageId").trim()
            val position = placement.optInt("position5x5", 0)
            if (isSafePatientPageId(pageId) && position in 1..25) {
                pageIds += pageId
            }
        }
        return pageIds
    }

    private fun hasStoredLabelForLanguage(
        item: org.json.JSONObject,
        labelMap: org.json.JSONObject?,
        language: String
    ): Boolean {
        val normalizedLanguage = language.trim().lowercase(Locale.ROOT)
        return labelMap?.optString(normalizedLanguage)?.trim()?.isNotBlank() == true ||
            (normalizedLanguage == "sl" && itemLabel(item).isNotBlank()) ||
            (normalizedLanguage == "uk" && item.optString("labelUk").trim().isNotBlank()) ||
            (normalizedLanguage == "en" && item.optString("labelEn").trim().isNotBlank())
    }

    private fun hasStoredSpeechForLanguage(
        item: org.json.JSONObject,
        speechMap: org.json.JSONObject?,
        language: String
    ): Boolean {
        val normalizedLanguage = language.trim().lowercase(Locale.ROOT)
        return speechMap?.optString(normalizedLanguage)?.trim()?.isNotBlank() == true ||
            (normalizedLanguage == "sl" && (
                item.optString("speechText").trim().isNotBlank() ||
                    item.optString("speakTextSl").trim().isNotBlank() ||
                    itemLabel(item).isNotBlank()
                )) ||
            (normalizedLanguage == "uk" && (
                item.optString("speechTextUk").trim().isNotBlank() ||
                    item.optString("speakTextUk").trim().isNotBlank()
                )) ||
            (normalizedLanguage == "en" && (
                item.optString("speechTextEn").trim().isNotBlank() ||
                    item.optString("speakTextEn").trim().isNotBlank()
                ))
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
            txtStatus.text = "Vir ikone mora biti SOCA, CUSTOM, PATIENT, ARASAAC ali SYSTEM."
            return
        }
        val imagePath = editAacImagePath.text.toString().trim()
        if (imagePath.isNotBlank() && isInvalidIconPath(imagePath)) {
            txtStatus.text = "Pot slike ni varna."
            return
        }
        val editorWarnings = buildList {
            if (imagePath.isBlank() && iconSource != "SYSTEM") add("manjka slika")
            if (iconSource.isBlank()) add("manjka vir ikone")
        }

        val activeLanguages = parseTherapistLanguages(editAacActiveLanguages.text.toString())
        if (activeLanguages.size > 3) {
            txtStatus.text = "Aktivni jeziki: najvec 3."
            return
        }

        when (saveTherapistAacItemToJson(itemId, labelSl, activeLanguages)) {
            AacItemEditorWriteResult.SuccessCreated -> {
                txtStatus.text = buildEditorSaveStatus("Ikona ustvarjena.", itemId, editorWarnings)
                refreshLocalAacOverview()
            }
            AacItemEditorWriteResult.SuccessUpdated -> {
                txtStatus.text = buildEditorSaveStatus("Ikona shranjena.", itemId, editorWarnings)
                refreshLocalAacOverview()
            }
            AacItemEditorWriteResult.WriteFailed -> {
                txtStatus.text = "AAC elementa ni bilo mogoce shraniti."
            }
        }
    }

    private fun buildEditorSaveStatus(
        headline: String,
        itemId: String,
        warnings: List<String>
    ): String {
        return buildString {
            append(headline)
            append("\n")
            append(itemId)
            if (warnings.isNotEmpty()) {
                append("\nOpozorilo: ")
                append(warnings.joinToString(", "))
            }
        }
    }

    private fun showAacImageChooser() {
        val iconSource = parseLocalIconSource(iconSourceForEditor())
        if (iconSource == null || iconSource == IconSource.SYSTEM) {
            txtStatus.text =
                "Najprej nastavi vir ikone: SOCA, CUSTOM, PATIENT ali ARASAAC. PATIENT je za osebe, druzino, prijatelje in pacientove fotografije; CUSTOM je za predmete, pijace in domace slike."
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
                txtStatus.text = if (add) "Ikona dodana na stran.\n$itemId -> $pageId / $position" else "Polje počiščeno."
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

    private fun replacePlacementCell(pageId: String, position: Int, itemId: String): AacMetadataWriteResult {
        return updateItemsJsonMetadata { itemsArray ->
            val targetItem = findItemById(itemsArray, itemId)
                ?: return@updateItemsJsonMetadata AacMetadataWriteResult.ItemNotFound
            removePlacementFromAllItems(itemsArray, pageId, position)
            val placements = targetItem.optJSONArray("placements") ?: org.json.JSONArray()
            placements.put(org.json.JSONObject().put("pageId", pageId).put("position5x5", position))
            targetItem.put("placements", placements)
            AacMetadataWriteResult.Success
        }
    }

    private fun clearPlacementCell(pageId: String, position: Int): AacMetadataWriteResult {
        return updateItemsJsonMetadata { itemsArray ->
            val removed = removePlacementFromAllItems(itemsArray, pageId, position)
            if (removed) AacMetadataWriteResult.Success else AacMetadataWriteResult.ItemNotFound
        }
    }

    private fun removePlacementFromAllItems(
        itemsArray: org.json.JSONArray,
        pageId: String,
        position: Int
    ): Boolean {
        var removed = false
        for (itemIndex in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(itemIndex) ?: continue
            val placements = item.optJSONArray("placements") ?: continue
            val nextPlacements = org.json.JSONArray()
            for (placementIndex in 0 until placements.length()) {
                val placement = placements.optJSONObject(placementIndex) ?: continue
                val samePlacement = placement.optString("pageId").trim() == pageId &&
                    placement.optInt("position5x5", 0) == position
                if (samePlacement && !placement.optBoolean("generated", false)) {
                    removed = true
                } else {
                    nextPlacements.put(placement)
                }
            }
            if (nextPlacements.length() > 0) {
                item.put("placements", nextPlacements)
            } else {
                item.remove("placements")
            }
        }
        return removed
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
            val questionByLanguage = parseLanguageAssignments(editAacQuestionByLanguage.text.toString())
            if (questionByLanguage.isNotEmpty()) {
                item.put("questionByLanguage", org.json.JSONObject(questionByLanguage))
                item.remove("questionSl")
                item.remove("questionUk")
            } else {
                item.remove("questionByLanguage")
            }
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
            IconSource.CUSTOM, IconSource.CUSTOM_PHOTO -> AacStoragePaths.getIconsCustomDir(this)
            IconSource.PATIENT, IconSource.PATIENT_PHOTO -> AacStoragePaths.getIconsPatientDir(this)
            IconSource.PLACE_PHOTO -> AacStoragePaths.getIconsPlacesDir(this)
            IconSource.ARASAAC -> AacStoragePaths.getIconsArasaacDir(this)
            IconSource.SYSTEM -> null
        }
    }

    private fun relativeImagePathForIconSource(iconSource: IconSource, imageFile: File, sourceDir: File): String {
        val relativePath = imageFile.relativeTo(sourceDir).invariantSeparatorsPath
        val prefix = when (iconSource) {
            IconSource.SOCA -> "soca"
            IconSource.CUSTOM, IconSource.CUSTOM_PHOTO -> "custom"
            IconSource.PATIENT, IconSource.PATIENT_PHOTO -> "patient"
            IconSource.PLACE_PHOTO -> "places"
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

    private fun formatQuestionByLanguageForEditor(item: org.json.JSONObject): String {
        val values = linkedMapOf<String, String>()
        item.optJSONObject("questionByLanguage")?.let { questions ->
            questions.keys().forEach { key ->
                val language = key.trim().lowercase(Locale.ROOT)
                val value = questions.optString(key).trim()
                if (language.isNotBlank() && value.isNotBlank()) {
                    values[language] = value
                }
            }
        }
        item.optString("questionSl").trim().takeIf { it.isNotBlank() }?.let { values.putIfAbsent("sl", it) }
        item.optString("questionUk").trim().takeIf { it.isNotBlank() }?.let { values.putIfAbsent("uk", it) }
        return values.entries.joinToString("; ") { (language, value) -> "$language=$value" }
    }

    private fun parseLanguageAssignments(rawValue: String): Map<String, String> {
        return rawValue
            .split(';')
            .mapNotNull { assignment ->
                val separatorIndex = assignment.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                val language = assignment.substring(0, separatorIndex).trim().lowercase(Locale.ROOT)
                val value = assignment.substring(separatorIndex + 1).trim()
                if (language.matches(Regex("[a-z]{2,3}")) && value.isNotBlank()) {
                    language to value
                } else {
                    null
                }
            }
            .toMap()
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
                    val manualPages = manualPlacementPageIds(item)
                    val children = stringArrayValues(item.optJSONArray("children"))
                    availableItems += AacListItem(
                        itemId = itemId,
                        label = itemLabel(item),
                        speechText = item.optString("speechText")
                            .ifBlank { item.optString("speakTextSl") }
                            .ifBlank { item.optString("text") },
                        iconSource = itemIconSource(item),
                        imagePath = item.optString("imagePath")
                            .ifBlank { item.optString("image_path") }
                            .ifBlank { item.optString("icon") },
                        fixedTopRowPosition = itemFixedTopRowPosition(item),
                        pageCount = manualPages.size,
                        subiconCount = children.size,
                        translationStatus = itemTranslationStatus(item, loadAacActiveLanguages())
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
            "CUSTOM", "CUSTOM_PHOTO" -> IconSource.CUSTOM_PHOTO
            "PATIENT", "PATIENT_PHOTO" -> IconSource.PATIENT_PHOTO
            "PLACE", "PLACE_PHOTO" -> IconSource.PLACE_PHOTO
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

    private data class DomProfileCountDebug(
        val countSource: String,
        val filePath: String,
        val profileId: String,
        val loadedItemIds: String,
        val loadedItemIdsCount: Int,
        val displayedCount: Int
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
        val speechText: String,
        val iconSource: IconSource,
        val imagePath: String,
        val fixedTopRowPosition: Int?,
        val pageCount: Int,
        val subiconCount: Int,
        val translationStatus: ItemTranslationStatus
    ) {
        fun matchesLibrarySearch(searchText: String): Boolean {
            if (searchText.isBlank()) return true
            return itemId.lowercase(Locale.ROOT).contains(searchText) ||
                label.lowercase(Locale.ROOT).contains(searchText) ||
                speechText.lowercase(Locale.ROOT).contains(searchText)
        }
    }

    private data class AacItemUsage(
        val itemId: String,
        val label: String,
        val iconSource: IconSource,
        val pageUsages: List<AacItemPageUsage>,
        val parentItems: List<UsageItemRef>,
        val childItems: List<UsageItemRef>,
        val fixedTopRowPosition: Int?,
        val translatedLanguages: List<String>,
        val missingLanguages: List<String>,
        val hasQuestion: Boolean
    )

    private data class AacItemPageUsage(
        val pageId: String,
        val pageTitle: String,
        val position5x5: Int
    )

    private data class BulkPlacementPreview(
        val pageId: String,
        val startPosition: Int,
        val toPlace: List<String>,
        val skippedLines: List<String>,
        val hasEnoughSpace: Boolean
    )

    private data class UsageItemRef(
        val itemId: String,
        val label: String
    )

    private data class UsageNavigationRow(
        val label: String,
        val action: () -> Unit
    )

    private data class PatientPage(
        val pageId: String,
        val pageTitle: String
    )

    private data class PlacementCellItem(
        val itemId: String,
        val label: String,
        val iconSource: IconSource,
        val imagePath: String,
        val fixedTopRowPosition: Int?,
        val isFixedTopRowCell: Boolean,
        val targetPageId: String?,
        val translationStatus: ItemTranslationStatus
    )

    private data class CommunicatorStructure(
        val pages: List<StructurePage>
    )

    private data class StructurePage(
        val pageId: String,
        val pageTitle: String,
        val items: List<StructureItem>
    )

    private data class StructureItem(
        val itemId: String,
        val label: String,
        val position: Int,
        val children: List<StructureItem>,
        val translationStatus: ItemTranslationStatus
    )

    private data class TranslationStatus(
        val languages: List<String>,
        val translatedCount: Int,
        val missingCount: Int,
        val pendingCount: Int
    ) {
        companion object {
            fun empty(languages: List<String>): TranslationStatus {
                return TranslationStatus(
                    languages = languages,
                    translatedCount = 0,
                    missingCount = 0,
                    pendingCount = 0
                )
            }
        }
    }

    private data class SubiconDashboardStatus(
        val parentCount: Int,
        val childCount: Int,
        val orphanCount: Int
    ) {
        companion object {
            fun empty(): SubiconDashboardStatus {
                return SubiconDashboardStatus(
                    parentCount = 0,
                    childCount = 0,
                    orphanCount = 0
                )
            }
        }
    }

    private data class PageWorkspaceDiagnostics(
        val subiconCount: Int,
        val missingTranslations: Int,
        val missingImages: Int,
        val duplicatePlacements: Int,
        val warningLines: List<String>
    )

    private enum class ItemTranslationStatus(val label: String) {
        TRANSLATED("prevedeno"),
        MISSING("manjka prevod"),
        PENDING("v pripravi")
    }

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
                CUSTOM -> iconSource == IconSource.CUSTOM ||
                    iconSource == IconSource.CUSTOM_PHOTO ||
                    iconSource == IconSource.PATIENT ||
                    iconSource == IconSource.PATIENT_PHOTO ||
                    iconSource == IconSource.PLACE_PHOTO
                ARASAAC -> iconSource == IconSource.ARASAAC
                SYSTEM -> iconSource == IconSource.SYSTEM
            }
        }
    }

    private enum class TherapistTranslationFilter(val label: String) {
        ALL("ALL"),
        TRANSLATED("TRANSLATED"),
        MISSING("MISSING"),
        PENDING("PENDING");

        fun matches(status: ItemTranslationStatus): Boolean {
            return when (this) {
                ALL -> true
                TRANSLATED -> status == ItemTranslationStatus.TRANSLATED
                MISSING -> status == ItemTranslationStatus.MISSING
                PENDING -> status == ItemTranslationStatus.PENDING
            }
        }
    }

    private enum class TherapistPageUsageFilter(val label: String) {
        ALL("ALL"),
        NOT_USED("NOT_USED"),
        USED_ON_PAGE("USED_ON_PAGE");

        fun matches(pageCount: Int): Boolean {
            return when (this) {
                ALL -> true
                NOT_USED -> pageCount == 0
                USED_ON_PAGE -> pageCount > 0
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
                CUSTOM -> iconSource == IconSource.CUSTOM ||
                    iconSource == IconSource.CUSTOM_PHOTO ||
                    iconSource == IconSource.PATIENT ||
                    iconSource == IconSource.PATIENT_PHOTO ||
                    iconSource == IconSource.PLACE_PHOTO
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
