package com.rehab2

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rehab2.aac.AacAudioPlayer
import com.rehab2.aac.AacCommunicationContext
import com.rehab2.aac.AacCommunicationContextPrefs
import com.rehab2.aac.AacGuidedFollowUpSettings
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLabelMode
import com.rehab2.aac.AacLanguageResolver
import com.rehab2.aac.AacLocalStorage
import com.rehab2.aac.AacLocalizedTextResolver
import com.rehab2.aac.AacPage
import com.rehab2.aac.AacProfileStore
import com.rehab2.aac.AacRepository
import com.rehab2.aac.AacSentenceItem
import com.rehab2.aac.AacSentenceStateManager
import com.rehab2.aac.AacSpeechTimingSettings
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.AacUsageStats
import com.rehab2.aac.AacV2JsonParser
import com.rehab2.aac.AacV2PageAdapter
import com.rehab2.aac.AacVendingScenario
import java.io.File

class AacCommunicatorActivity : AppCompatActivity() {
    // Speech modes stay separate so tile feedback, sentence composition, and future
    // learning/message flows can share AAC items without double-speaking one tap.
    private enum class SpeechMode {
        SINGLE_ICON,
        SENTENCE
    }

    private lateinit var repository: AacRepository
    private val pageHistory = ArrayDeque<String>()
    private val sentenceManager = AacSentenceStateManager()
    private var currentV2ItemsById: Map<String, AacItem> = emptyMap()
    private val currentV2VisibleHistory: ArrayDeque<List<AacItem>> = ArrayDeque()
    private var currentVisibleItems: List<AacItem> = emptyList()
    private var currentV2RootItems: List<AacItem> = emptyList()
    private lateinit var audioPlayer: AacAudioPlayer
    private val autoSpeakHandler = Handler(Looper.getMainLooper())
    private var pendingSingleIconSpeak: Runnable? = null
    private var pendingAutoSpeakSentence: Runnable? = null
    private var isSpeakingSentence = false
    private var isSpeakingSingleIcon = false
    private var lastSpeechRequestId = 0
    private var pendingSpeechMode: SpeechMode? = null
    private var activeSpeechMode: SpeechMode? = null
    private var aacGridSize = DEFAULT_AAC_GRID_SIZE
    private var persistentTopRowEnabled = true
    private var persistentTopRowCount = DEFAULT_PERSISTENT_TOP_ROW_COUNT
    private var persistentTopRowItemIds: List<String> = DEFAULT_PERSISTENT_TOP_ROW_ITEM_IDS
    private lateinit var txtTitle: TextView
    private lateinit var txtPath: TextView
    private lateinit var btnBackNav: Button
    private lateinit var sentenceBar: View
    private lateinit var txtPrompt: TextView
    private lateinit var txtSentence: TextView
    private lateinit var btnOpenDrinksV2Test: Button
    private lateinit var btnSpeakSentence: Button
    private lateinit var btnClearSentence: Button
    private lateinit var recycler: RecyclerView
    private lateinit var txtWaterTraceDebug: TextView
    private var labelMode: AacLabelMode = AacLabelMode.DEFAULT
    private var languageCode: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE
    private var speechTimingSettings: AacSpeechTimingSettings = AacSpeechTimingSettings()
    private var guidedFollowUpSettings: AacGuidedFollowUpSettings = AacGuidedFollowUpSettings()
    private var activeAacProfileId: String = AacProfileStore.DEFAULT_PROFILE_ID
    private var aacCommunicationContext: AacCommunicationContext = AacCommunicationContext.NORMAL_COMMUNICATION
    private var realWorldHelpersEnabled = true
    private var singleIconSpeechOccurredInCurrentSentence = false
    private var sentenceCompositionStartedAt = 0L
    private var lastIconClickAt = 0L
    private var pendingVendingDigitsSpeech: String? = null
    private var currentPageId: String = "home"
    private var waterPageModelChildrenCount: Int = -1
    private var waterBeforeAdapterChildrenCount: Int = -1
    private var waterAdapterBindChildrenCount: Int = -1
    private var waterClickItemChildrenCount: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_communicator)
        addWaterTraceDebugView()
        repository = AacRepository(this)
        audioPlayer = AacAudioPlayer(this)
        audioPlayer.setSpeechListener(object : AacAudioPlayer.SpeechListener {
            override fun onSpeechStarted() {
                activeSpeechMode = pendingSpeechMode
                when (activeSpeechMode) {
                    SpeechMode.SENTENCE -> {
                        isSpeakingSentence = true
                        isSpeakingSingleIcon = false
                    }
                    SpeechMode.SINGLE_ICON -> {
                        isSpeakingSingleIcon = true
                        isSpeakingSentence = false
                    }
                    null -> Unit
                }
                Log.d(TAG, "AAC_SPEECH SPEECH_STARTED mode=$activeSpeechMode")
            }

            override fun onSpeechCompleted() {
                val completedMode = activeSpeechMode
                Log.d(TAG, "AAC_SPEECH SPEECH_COMPLETED mode=$completedMode")
                resetSpeechState("completed")
                if (completedMode == SpeechMode.SENTENCE) {
                    resetSentenceCompositionTracking()
                    if (speechTimingSettings.clearSentenceAfterSentenceEnabled) {
                        Log.d(TAG, "AAC_SENTENCE CLEAR_AFTER_SENTENCE_COMPLETED")
                        sentenceManager.clear()
                        updateSentenceBar()
                    }
                    if (speechTimingSettings.returnToRootAfterSentenceEnabled) {
                        returnToRootMenuAfterSentence()
                    }
                    speakPendingVendingDigitsIfNeeded()
                }
            }

            override fun onSpeechCancelled() {
                Log.d(TAG, "AAC_SPEECH SPEECH_CANCELLED mode=$activeSpeechMode")
                pendingVendingDigitsSpeech = null
                resetSpeechState("cancelled")
            }

            override fun onSpeechError() {
                Log.d(TAG, "AAC_SPEECH SPEECH_ERROR mode=$activeSpeechMode")
                pendingVendingDigitsSpeech = null
                resetSpeechState("error")
            }
        })

        Log.d(TAG, "AAC_STORAGE ensureStructure=${AacLocalStorage.ensureStructure(this)}")
        Log.d(TAG, "AAC_STORAGE seedBundledDefaultPages=${AacLocalStorage.seedBundledDefaultPages(this)}")
        Log.d(TAG, "AAC_STORAGE seedBundledAudio=${AacLocalStorage.seedBundledTestAudio(this)}")

        txtTitle = findViewById(R.id.txtAacTitle)
        txtPath = findViewById(R.id.txtAacPath)
        btnBackNav = findViewById(R.id.btnAacBackNav)
        sentenceBar = findViewById(R.id.aacSentenceBar)
        txtPrompt = findViewById(R.id.txtAacPrompt)
        txtSentence = findViewById(R.id.txtAacSentence)
        btnOpenDrinksV2Test = findViewById(R.id.btnOpenDrinksV2Test)
        btnOpenDrinksV2Test.text = "PIJAČE"
        btnSpeakSentence = findViewById(R.id.btnAacSpeakSentence)
        btnClearSentence = findViewById(R.id.btnAacClearSentence)
        recycler = findViewById(R.id.recyclerAacTiles)
        readAacGridSize()
        recycler.layoutManager = GridLayoutManager(this, aacGridSize)
        labelMode = readAacLabelMode()
        languageCode = AacLanguageResolver.readSelectedLanguageCode(this)
        speechTimingSettings = AacSpeechTimingSettings.read(this)
        guidedFollowUpSettings = AacGuidedFollowUpSettings.read(this)
        AacProfileStore.applyProfileDefaultsIfNeeded(this)
        activeAacProfileId = AacProfileStore.getActiveAacProfile(this).id
        aacCommunicationContext = AacProfileStore.getActiveAacContext(this)
        realWorldHelpersEnabled = AacCommunicationContextPrefs.areRealWorldHelpersEnabled(this)
        readPersistentTopRowSettings()

        btnSpeakSentence.setOnClickListener {
            cancelPendingSpeech()
            speakCurrentSentence()
        }
        btnClearSentence.setOnClickListener {
            cancelPendingSpeech()
            sentenceManager.clear()
            resetSentenceCompositionTracking()
            currentV2VisibleHistory.clear()
            clearPromptText()
            returnToRootMenuAfterClear()
            updateSentenceBar()
        }
        btnBackNav.setOnClickListener {
            cancelPendingSpeech()
            goBack()
        }
        setupQuickAccessRow()
        btnOpenDrinksV2Test.setOnClickListener {
            resetWaterTraceDebug()
            updateWaterTraceDebug("PIJAČE")
            openDrinksCategory()
        }

        val homePage = repository.loadHomePage()
        showPage(homePage)
        showRepositoryDebugStatus()
    }

    override fun onResume() {
        super.onResume()
        val updatedLabelMode = readAacLabelMode()
        val updatedLanguageCode = AacLanguageResolver.readSelectedLanguageCode(this)
        val updatedSpeechTimingSettings = AacSpeechTimingSettings.read(this)
        val updatedGuidedFollowUpSettings = AacGuidedFollowUpSettings.read(this)
        AacProfileStore.applyProfileDefaultsIfNeeded(this)
        val updatedProfileId = AacProfileStore.getActiveAacProfile(this).id
        val updatedCommunicationContext = AacProfileStore.getActiveAacContext(this)
        val updatedRealWorldHelpersEnabled = AacCommunicationContextPrefs.areRealWorldHelpersEnabled(this)
        val oldGridSize = aacGridSize
        val oldTopRowEnabled = persistentTopRowEnabled
        val oldTopRowCount = persistentTopRowCount
        val oldTopRowItemIds = persistentTopRowItemIds
        readAacGridSize()
        readPersistentTopRowSettings()
        if (
            updatedLabelMode != labelMode ||
            updatedLanguageCode != languageCode ||
            updatedSpeechTimingSettings != speechTimingSettings ||
            updatedGuidedFollowUpSettings != guidedFollowUpSettings ||
            updatedProfileId != activeAacProfileId ||
            updatedCommunicationContext != aacCommunicationContext ||
            updatedRealWorldHelpersEnabled != realWorldHelpersEnabled ||
            oldGridSize != aacGridSize ||
            oldTopRowEnabled != persistentTopRowEnabled ||
            oldTopRowCount != persistentTopRowCount ||
            oldTopRowItemIds != persistentTopRowItemIds
        ) {
            labelMode = updatedLabelMode
            languageCode = updatedLanguageCode
            speechTimingSettings = updatedSpeechTimingSettings
            guidedFollowUpSettings = updatedGuidedFollowUpSettings
            activeAacProfileId = updatedProfileId
            aacCommunicationContext = updatedCommunicationContext
            realWorldHelpersEnabled = updatedRealWorldHelpersEnabled
            applyAacGridSize()
            if (currentVisibleItems.isNotEmpty()) {
                showItems(currentVisibleItems)
            }
            updateSentenceBar()
        }
    }

    override fun onDestroy() {
        cancelPendingSpeech()
        audioPlayer.setSpeechListener(null)
        audioPlayer.release()
        super.onDestroy()
    }

    private fun showPage(page: AacPage) {
        currentPageId = page.pageId
        txtTitle.text = buildTitleText(page.title)
        updateNavigationChrome(page.title)
        if (isV2Page(page)) {
            currentV2ItemsById = page.items.associateBy { it.id }
            currentV2RootItems = getV2RootItems(page.items)
            currentV2VisibleHistory.clear()
            sentenceBar.visibility = View.VISIBLE
            clearPromptText()
            updateSentenceBar()
            showItems(currentV2RootItems)
            if (page.pageId == DRINKS_CATEGORY_PAGE_ID) {
                showDrinksCategoryWaterLog(page)
            }
        } else {
            clearV2State()
            showItems(page.items)
        }
    }

    private fun showItems(items: List<AacItem>) {
        currentVisibleItems = items
        applyAacGridSize()
        val waterItem = items.firstOrNull { it.id == WATER_NODE_ID }
        waterBeforeAdapterChildrenCount = waterItem?.children?.size ?: -1
        logWaterTrace("before adapter", waterItem)
        updateWaterTraceDebug("before adapter")
        recycler.adapter = AacAdapter(
            items = mergePersistentTopRowWithCurrentMenuItems(items),
            labelMode = labelMode,
            languageCode = languageCode,
            onItemClick = { item ->
                handleItemClick(item)
            },
            onWaterBindTrace = { item ->
                waterAdapterBindChildrenCount = item.children.size
                updateWaterTraceDebug("adapter bind")
            }
        )
        updateNavigationChrome(txtTitle.text.toString().lineSequence().firstOrNull().orEmpty())
    }

    private fun readAacLabelMode(): AacLabelMode {
        val prefs = getSharedPreferences(AacLabelMode.PREFS_FILE, MODE_PRIVATE)
        return AacLabelMode.fromPreference(
            prefs.getString(AacLabelMode.PREF_AAC_LABEL_MODE, AacLabelMode.DEFAULT.name)
        )
    }

    private fun handleItemClick(item: AacItem) {
        confirmGuidedTopSuggestionIfYesItem(item)?.let { suggestedItem ->
            handleItemClick(suggestedItem)
            return
        }

        if (isGuidedBackNoItem(item)) {
            cancelPendingSpeech()
            goBack()
            return
        }

        when (item.actionType) {
            "open_page" -> {
                cancelPendingSpeech()
                openTargetPage(item.targetPageId)
                return
            }
            "go_home" -> {
                cancelPendingSpeech()
                goHome()
                return
            }
            "go_back" -> {
                cancelPendingSpeech()
                goBack()
                return
            }
        }

        if (isV2Item(item)) {
            val speechRequestId = nextSpeechRequestId("ITEM_SELECTED:${item.id}")
            if (item.id == WATER_NODE_ID) {
                waterClickItemChildrenCount = item.children.size
                logWaterTrace("click item", item)
                updateWaterTraceDebug("click water")
            }
            val clickedAt = System.currentTimeMillis()
            startSentenceCompositionTrackingIfNeeded(clickedAt)
            val childItems = resolveGuidedChildItems(item, resolveV2ChildItems(item))
            if (item.id == WATER_NODE_ID) {
                Log.d(TAG, "WATER clicked children=${childItems.size}")
                if (childItems.isEmpty()) {
                    updateWaterTraceDebug("WATER CLICK CHILDREN=0")
                } else {
                    updateWaterTraceDebug("WATER CLICK CHILDREN=${childItems.size}")
                }
            }
            AacUsageStats.recordUse(this, item.id)
            if (handleVendingScenarioSelection(item)) {
                return
            }
            sentenceManager.addItem(
                AacSentenceItem(
                    conceptId = item.conceptId ?: item.id,
                    text = AacLocalizedTextResolver.resolveSpeakText(item, languageCode),
                    role = item.sentenceRole
                )
            )
            Log.d(TAG, "AAC_SENTENCE ITEM_ADDED count=${sentenceManager.getItems().size} item=${item.id}")
            updateSentenceBar()
            val singleIconText = AacLocalizedTextResolver.resolveSpeakText(item, languageCode)
            maybeShowVendingNumber(item)

            if (childItems.isNotEmpty()) {
                speakSingleIconIfEnabled(singleIconText, speechRequestId)
                scheduleAutoSpeakSentenceIfEnabled(speechRequestId)
                setPromptText(resolveFollowUpQuestion(item))
                currentV2VisibleHistory.addLast(currentVisibleItems)
                showItems(childItems)
                return
            } else {
                clearPromptText()
            }
            speakSingleIconIfEnabled(singleIconText, speechRequestId)
            scheduleAutoSpeakSentenceIfEnabled(speechRequestId)
            return
        }

        audioPlayer.playOrSpeak(item, languageCode)
    }

    private fun confirmGuidedTopSuggestionIfYesItem(item: AacItem): AacItem? {
        if (!isGuidedYesItem(item) || !isGuidedFollowUpAllowed() || currentV2VisibleHistory.isEmpty()) {
            return null
        }

        val topSuggestionId = AacUsageStats.topSuggestion(
            this,
            currentVisibleItems.map { visibleItem -> visibleItem.id }
        ) ?: return null

        return currentVisibleItems.firstOrNull { visibleItem -> visibleItem.id == topSuggestionId }
    }

    private fun isGuidedYesItem(item: AacItem): Boolean {
        val conceptId = item.conceptId?.trim().orEmpty()
        return item.id == "yes" ||
            item.id == "quick_yes" ||
            conceptId == "yes" ||
            conceptId == "core.yes"
    }

    private fun handleVendingScenarioSelection(item: AacItem): Boolean {
        if (!isVendingScenarioActive() || currentV2VisibleHistory.isEmpty() || !AacVendingScenario.canHandle(item)) {
            return false
        }

        cancelPendingSpeech()
        setPromptText(AacVendingScenario.codePromptFor(this, item))
        val requestId = nextSpeechRequestId("VENDING_SCENARIO:${item.id}")
        startSingleIconSpeech(AacVendingScenario.speechFor(this, item), requestId)
        return true
    }

    private fun isVendingScenarioActive(): Boolean {
        return isRealWorldHelperAllowed() &&
            getActiveAacProfileId() == AacVendingScenario.PROFILE_ID &&
            getAacCommunicationContext() == AacCommunicationContext.REAL_WORLD_ASSISTANT
    }

    private fun isGuidedBackNoItem(item: AacItem): Boolean {
        if (!isGuidedFollowUpAllowed() || currentV2VisibleHistory.isEmpty()) {
            return false
        }

        val conceptId = item.conceptId?.trim().orEmpty()
        return item.id == "no" ||
            item.id == "quick_no" ||
            conceptId == "no" ||
            conceptId == "core.no"
    }

    private fun setupQuickAccessRow() {
        bindQuickAacButton(R.id.btnQuickWater, quickSpeakItem("quick_water", "VODA", "voda", "water"))
        bindQuickAacButton(R.id.btnQuickWc, quickSpeakItem("quick_wc", "WC", "WC", "wc"))
        bindQuickAacButton(R.id.btnQuickHelp, quickSpeakItem("quick_help", "POMOČ", "pomoč", "help"))
        bindQuickAacButton(R.id.btnQuickYes, quickSpeakItem("quick_yes", "DA", "da", "yes"))
        bindQuickAacButton(R.id.btnQuickNo, quickSpeakItem("quick_no", "NE", "ne", "no"))
        bindQuickAacButton(
            R.id.btnQuickHome,
            AacItem(
                id = "quick_home",
                labelSl = "DOMOV",
                imagePath = "",
                audioSl = "",
                actionType = "go_home",
                targetPageId = "home"
            )
        )
        bindQuickAacButton(R.id.btnQuickPain, quickSpeakItem("quick_pain", "BOLI", "boli", "pain"))
        bindQuickAacButton(R.id.btnQuickTired, quickSpeakItem("quick_tired", "UTRUJENA", "utrujena", "tired"))
    }

    private fun bindQuickAacButton(buttonId: Int, item: AacItem) {
        findViewById<Button>(buttonId).setOnClickListener {
            handleItemClick(item)
        }
    }

    private fun quickSpeakItem(id: String, label: String, speakText: String, conceptId: String): AacItem {
        return AacItem(
            id = id,
            labelSl = label,
            imagePath = "",
            audioSl = "",
            actionType = "speak",
            targetPageId = "",
            speakTextSl = speakText,
            conceptId = conceptId,
            sentenceRole = "quick",
            isRootItem = false
        )
    }

    private fun openTargetPage(targetPageId: String) {
        val normalizedTargetPageId = targetPageId.trim()
        if (normalizedTargetPageId.isBlank()) {
            Toast.makeText(this, "Stran ni določena", Toast.LENGTH_SHORT).show()
            return
        }

        val effectiveTargetPageId = if (normalizedTargetPageId == "drinks") {
            val refreshResult = refreshBundledDrinksCategoryPage()
            showDrinksCategoryRefreshLog(refreshResult)
            if (refreshResult.isReady) DRINKS_CATEGORY_PAGE_ID else normalizedTargetPageId
        } else {
            normalizedTargetPageId
        }
        val page = repository.loadPage(effectiveTargetPageId)
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.addLast(currentPageId)
        showPage(page)
    }

    private fun updateNavigationChrome(pageTitle: String) {
        val normalizedTitle = pageTitle.trim().ifBlank { "AAC" }
        val inSubcategory = currentV2VisibleHistory.isNotEmpty()
        val canGoBack = pageHistory.isNotEmpty() || inSubcategory

        txtPath.text = buildString {
            append("Kategorija: ")
            append(normalizedTitle)
            if (inSubcategory) {
                append(" > izbira")
            }
        }
        btnBackNav.isEnabled = canGoBack
        btnBackNav.text = if (canGoBack) "NAZAJ" else "ZAČETEK"
        btnBackNav.visibility = if (canGoBack) View.VISIBLE else View.INVISIBLE
        btnBackNav.alpha = 1.0f
    }

    private fun openDrinksCategory() {
        val refreshResult = refreshBundledDrinksCategoryPage()
        showDrinksCategoryRefreshLog(refreshResult)
        if (refreshResult.isReady) {
            updateWaterTraceDebug("PIJAČE")
            openTargetPage(DRINKS_CATEGORY_PAGE_ID)
        } else {
            Log.d(TAG, "DRINKS CATEGORY REFRESH FAILED")
        }
    }

    private fun refreshBundledDrinksCategoryPage(): DrinksCategoryRefreshResult {
        val pagesDir = AacLocalStorage.getPagesDir(this)
        val runtimeFile = pagesDir?.let { File(it, "$DRINKS_CATEGORY_PAGE_ID.json") }
        val rebuilt = AacLocalStorage.rebuildBundledDrinksV2Page(this)
        val exists = runtimeFile?.exists() == true
        val size = runtimeFile?.takeIf { it.exists() }?.length() ?: 0L
        return DrinksCategoryRefreshResult(
            rebuilt = rebuilt,
            exists = exists,
            size = size,
            path = runtimeFile?.absolutePath.orEmpty()
        )
    }

    private fun showDrinksCategoryRefreshLog(result: DrinksCategoryRefreshResult) {
        val existsText = if (result.exists) "yes" else "no"
        val message = "page=$DRINKS_CATEGORY_PAGE_ID runtime exists=$existsText size=${result.size}"
        Log.d(TAG, "$message path=${result.path} rebuilt=${result.rebuilt}")
    }

    private fun showDrinksCategoryWaterLog(page: AacPage) {
        val waterItem = page.items.firstOrNull { it.id == WATER_NODE_ID }
        waterPageModelChildrenCount = waterItem?.children?.size ?: -1
        logWaterTrace("page model", waterItem)
        updateWaterTraceDebug("page model")
        val waterChildrenCount = waterItem?.children?.size ?: 0
        val message = if (waterChildrenCount == 4) {
            "WATER children=4"
        } else {
            "WATER children=$waterChildrenCount - category data not ready"
        }
        Log.d(TAG, "page=${page.pageId} $message")
    }

    private fun logWaterTrace(stage: String, item: AacItem?) {
        val children = item?.children.orEmpty()
        Log.d(TAG, "TRACE category $stage children=${children.size} ids=$children")
    }

    private fun addWaterTraceDebugView() {
        val root = findViewById<FrameLayout>(android.R.id.content)
        txtWaterTraceDebug = TextView(this).apply {
            text = "AAC CATEGORY TRACE: waiting"
            setTextColor(Color.WHITE)
            setBackgroundColor(0xDD111820.toInt())
            setPadding(10, 8, 10, 8)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 10
            visibility = View.GONE
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        root.addView(txtWaterTraceDebug, params)
    }

    private fun resetWaterTraceDebug() {
        waterPageModelChildrenCount = -1
        waterBeforeAdapterChildrenCount = -1
        waterAdapterBindChildrenCount = -1
        waterClickItemChildrenCount = -1
    }

    private fun updateWaterTraceDebug(stage: String) {
        txtWaterTraceDebug.visibility = View.GONE
        txtWaterTraceDebug.text = buildString {
            appendLine("AAC CATEGORY TRACE: $stage")
            appendLine("JSON children=${AacV2JsonParser.lastWaterJsonChildrenCount}")
            appendLine("parsed model children=${AacV2PageAdapter.lastWaterParsedModelChildrenCount}")
            appendLine("mapped AacItem children=${AacV2PageAdapter.lastWaterMappedItemChildrenCount}")
            appendLine("page model children=$waterPageModelChildrenCount")
            appendLine("before adapter children=$waterBeforeAdapterChildrenCount")
            appendLine("adapter bind children=$waterAdapterBindChildrenCount")
            append("click item children=$waterClickItemChildrenCount")
        }
    }

    private fun goHome() {
        cancelPendingSpeech()
        clearV2State()
        val page = repository.loadPage("home")
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.clear()
        showPage(page)
    }

    private fun goBack() {
        if (currentV2VisibleHistory.isNotEmpty()) {
            val previousItems = currentV2VisibleHistory.removeLast()
            clearPromptText()
            showItems(previousItems)
            return
        }

        if (pageHistory.isEmpty()) {
            return
        }

        val previousPageId = pageHistory.last()
        val page = repository.loadPage(previousPageId)
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.removeLast()
        showPage(page)
    }

    private fun clearV2State() {
        cancelPendingSpeech()
        sentenceManager.clear()
        resetSentenceCompositionTracking()
        pendingVendingDigitsSpeech = null
        currentV2ItemsById = emptyMap()
        currentV2VisibleHistory.clear()
        currentVisibleItems = emptyList()
        currentV2RootItems = emptyList()
        clearPromptText()
        sentenceBar.visibility = View.GONE
        txtSentence.text = ""
        updateSentenceBar()
    }

    private fun returnToRootMenuAfterSentence() {
        Log.d(TAG, "AAC_NAV RETURN_TO_ROOT_AFTER_SENTENCE")
        returnToRootMenu()
    }

    private fun returnToRootMenuAfterClear() {
        Log.d(TAG, "AAC_NAV RETURN_TO_ROOT_AFTER_CLEAR")
        returnToRootMenu()
    }

    private fun returnToRootMenu() {
        currentV2VisibleHistory.clear()
        clearPromptText()
        if (currentV2RootItems.isNotEmpty()) {
            showItems(currentV2RootItems)
        }
    }

    private fun isV2Page(page: AacPage): Boolean {
        return page.items.any { item -> isV2Item(item) }
    }

    private fun isV2Item(item: AacItem): Boolean {
        return item.conceptId != null || item.children.isNotEmpty() || item.sentenceRole != null
    }

    private fun getV2RootItems(items: List<AacItem>): List<AacItem> {
        val explicitRootItems = items
            .filter { it.isRootItem && !it.isHiddenUntilParent }
            .sortedBy { it.priority }
        if (explicitRootItems.isNotEmpty()) {
            return explicitRootItems
        }

        val childIds = items.flatMap { it.children }.toSet()
        val rootItems = items.filter { it.id !in childIds }
        return rootItems.ifEmpty { items }
    }

    private fun readAacGridSize() {
        val prefs = getSharedPreferences(AAC_PREFS_FILE, MODE_PRIVATE)
        aacGridSize = normalizeAacGridSize(
            prefs.getInt(PREF_AAC_GRID_SIZE, DEFAULT_AAC_GRID_SIZE)
        )
    }

    private fun applyAacGridSize() {
        val layoutManager = recycler.layoutManager as? GridLayoutManager
        if (layoutManager == null) {
            recycler.layoutManager = GridLayoutManager(this, aacGridSize)
        } else if (layoutManager.spanCount != aacGridSize) {
            layoutManager.spanCount = aacGridSize
        }
    }

    private fun getAacGridSize(): Int {
        return normalizeAacGridSize(aacGridSize)
    }

    private fun getAacItemsPerPage(): Int {
        val gridSize = getAacGridSize()
        return gridSize * gridSize
    }

    private fun readPersistentTopRowSettings() {
        val prefs = getSharedPreferences(AAC_PREFS_FILE, MODE_PRIVATE)
        persistentTopRowEnabled = prefs.getBoolean(PREF_AAC_PERSISTENT_TOP_ROW_ENABLED, true)
        val rawTopRowCount = prefs.getInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, DEFAULT_PERSISTENT_TOP_ROW_COUNT)
        persistentTopRowCount = normalizePersistentTopRowConfiguredCount(rawTopRowCount)
        if (persistentTopRowCount != rawTopRowCount) {
            prefs.edit().putInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, persistentTopRowCount).apply()
        }
        persistentTopRowItemIds = prefs.getString(
            PREF_AAC_PERSISTENT_TOP_ROW_ITEM_IDS,
            DEFAULT_PERSISTENT_TOP_ROW_ITEM_IDS.joinToString(",")
        )
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { DEFAULT_PERSISTENT_TOP_ROW_ITEM_IDS }
    }

    private fun getPersistentTopRowCount(): Int {
        return normalizePersistentTopRowCount(persistentTopRowCount, getAacGridSize())
    }

    private fun getPersistentTopRowItems(items: List<AacItem>): List<AacItem> {
        if (!persistentTopRowEnabled) return emptyList()

        val homeItems = repository.loadPage("home")?.items.orEmpty()
        val metadataTopRowItems = (homeItems + items)
            .distinctBy { it.id }
            .filter { it.fixedTopRowPosition != null }
            .sortedWith(
                compareBy<AacItem> { it.fixedTopRowPosition ?: MAX_PERSISTENT_TOP_ROW_COUNT + 1 }
                    .thenBy { it.priority }
            )
        if (metadataTopRowItems.isNotEmpty()) {
            return metadataTopRowItems
                .take(MAX_PERSISTENT_TOP_ROW_COUNT)
                .map(::asPersistentTopRowItem)
        }

        val homeItemsById = homeItems.associateBy { it.id }
        return persistentTopRowItemIds
            .take(MAX_PERSISTENT_TOP_ROW_COUNT)
            .mapNotNull { id -> homeItemsById[id] }
            .map(::asPersistentTopRowItem)
    }

    private fun mergePersistentTopRowWithCurrentMenuItems(items: List<AacItem>): List<AacItem> {
        val configuredTopRowItems = getPersistentTopRowItems(items)
        val maxItems = getAacItemsPerPage()
        if (configuredTopRowItems.isEmpty()) return items.take(maxItems)

        val fixedTopRowItems = configuredTopRowItems.take(getPersistentTopRowCount())
        val overflowItems = configuredTopRowItems.drop(fixedTopRowItems.size)
        val fixedTopRowIds = fixedTopRowItems.map { it.id }.toSet()
        val overflowIds = overflowItems.map { it.id }.toSet()
        val menuItems = items.filter { it.id !in fixedTopRowIds }
        val menuWithOverflow = overflowItems + menuItems.filter { it.id !in overflowIds }
        Log.d(
            TAG,
            "AAC_TOP_ROW grid=${getAacGridSize()} fixed=${fixedTopRowItems.map { it.id }} overflow=${overflowItems.map { it.id }} max=$maxItems"
        )
        return (fixedTopRowItems + menuWithOverflow).take(maxItems)
    }

    private fun asPersistentTopRowItem(item: AacItem): AacItem {
        return item.copy(
            actionType = "speak",
            targetPageId = "",
            conceptId = item.conceptId ?: item.id,
            sentenceRole = item.sentenceRole ?: "quick"
        )
    }

    private fun normalizeAacGridSize(value: Int): Int {
        return when (value) {
            3, 4, 5 -> value
            else -> DEFAULT_AAC_GRID_SIZE
        }
    }

    private fun normalizePersistentTopRowCount(value: Int, gridSize: Int): Int {
        return value.coerceIn(MIN_PERSISTENT_TOP_ROW_COUNT, gridSize.coerceIn(3, 5))
    }

    private fun normalizePersistentTopRowConfiguredCount(value: Int): Int {
        return value.coerceIn(MIN_PERSISTENT_TOP_ROW_COUNT, MAX_PERSISTENT_TOP_ROW_COUNT)
    }

    private fun updateSentenceBar() {
        val displayText = sentenceManager.getDisplayText()
        txtSentence.text = displayText
        val hasSentence = displayText.isNotBlank()
        btnSpeakSentence.isEnabled = hasSentence
        btnClearSentence.isEnabled = hasSentence
    }

    private fun speakSingleIconIfEnabled(text: String, requestId: Int) {
        cancelPendingSingleIconSpeak()
        if (!speechTimingSettings.speakSingleIconEnabled) {
            return
        }

        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return
        }

        if (!speechTimingSettings.delayedSingleIconSpeakEnabled ||
            speechTimingSettings.singleIconSpeakDelayMs <= 0L
        ) {
            startSingleIconSpeech(trimmed, requestId)
            return
        }

        val pending = Runnable {
            pendingSingleIconSpeak = null
            startSingleIconSpeech(trimmed, requestId)
        }
        pendingSingleIconSpeak = pending
        autoSpeakHandler.postDelayed(pending, speechTimingSettings.singleIconSpeakDelayMs)
    }

    private fun scheduleAutoSpeakSentenceIfEnabled(requestId: Int) {
        cancelPendingAutoSpeakSentence()
        if (!speechTimingSettings.autoSpeakSentenceEnabled) {
            return
        }

        val itemCount = sentenceManager.getItems().size
        val sentence = sentenceManager.getSpeakText(languageCode).trim()
        if (sentence.isEmpty()) {
            return
        }
        // A single tile already has its own speech path; auto-sentence is reserved for composed phrases.
        if (itemCount <= 1 && speechTimingSettings.speakSingleIconEnabled) {
            Log.d(TAG, "AAC_SENTENCE AUTO_SKIP_SINGLE_ITEM_DUPLICATE requestId=$requestId")
            return
        }

        Log.d(TAG, "AAC_SENTENCE AUTO_SCHEDULED count=$itemCount requestId=$requestId")

        val pending = Runnable {
            pendingAutoSpeakSentence = null
            Log.d(TAG, "AAC_SENTENCE AUTO_SPEAK_START count=$itemCount requestId=$requestId")
            startSentenceSpeech(sentence, requestId)
        }
        pendingAutoSpeakSentence = pending
        autoSpeakHandler.postDelayed(pending, speechTimingSettings.autoSpeakSentenceDelayMs)
    }

    private fun startSingleIconSpeech(text: String, requestId: Int) {
        if (requestId != lastSpeechRequestId) {
            Log.d(TAG, "AAC_SPEECH REQUEST_IGNORED_OLD_ID single_icon requestId=$requestId active=$lastSpeechRequestId")
            return
        }
        if (isSpeakingSentence) {
            Log.d(TAG, "AAC_SPEECH SINGLE_ICON_CANCEL sentence_active requestId=$requestId")
            isSpeakingSingleIcon = false
            return
        }
        if (shouldSkipFastCompositionLastIcon()) {
            Log.d(TAG, "AAC_SPEECH FAST_COMPOSITION_SKIP_LAST_ICON requestId=$requestId text=$text")
            isSpeakingSingleIcon = false
            return
        }

        isSpeakingSingleIcon = true
        pendingSpeechMode = SpeechMode.SINGLE_ICON
        activeSpeechMode = SpeechMode.SINGLE_ICON
        singleIconSpeechOccurredInCurrentSentence = true
        Log.d(TAG, "AAC_SPEECH SINGLE_ICON_OCCURRED_IN_SENTENCE requestId=$requestId")
        Log.d(TAG, "AAC_SPEECH SINGLE_ICON_START requestId=$requestId text=$text")
        audioPlayer.speakText(text, languageCode)
    }

    private fun startSentenceSpeech(text: String, requestId: Int) {
        if (requestId != lastSpeechRequestId) {
            Log.d(TAG, "AAC_SPEECH REQUEST_IGNORED_OLD_ID sentence requestId=$requestId active=$lastSpeechRequestId")
            return
        }
        if (isSpeakingSentence) {
            Log.d(TAG, "AAC_SPEECH SENTENCE_CANCEL previous requestId=$requestId")
        }

        cancelPendingSingleIconSpeak()
        isSpeakingSingleIcon = false
        isSpeakingSentence = true
        pendingSpeechMode = SpeechMode.SENTENCE
        activeSpeechMode = SpeechMode.SENTENCE
        Log.d(TAG, "AAC_SPEECH SENTENCE_START requestId=$requestId text=$text")
        audioPlayer.speakText(text, languageCode)
    }

    private fun cancelPendingAutoSpeakSentence() {
        pendingAutoSpeakSentence?.let { pending ->
            autoSpeakHandler.removeCallbacks(pending)
            Log.d(TAG, "AAC_SPEECH SENTENCE_CANCEL pending requestId=$lastSpeechRequestId")
        }
        pendingAutoSpeakSentence = null
    }

    private fun cancelPendingSingleIconSpeak() {
        pendingSingleIconSpeak?.let { pending ->
            autoSpeakHandler.removeCallbacks(pending)
            Log.d(TAG, "AAC_SPEECH SINGLE_ICON_CANCEL pending requestId=$lastSpeechRequestId")
        }
        pendingSingleIconSpeak = null
        isSpeakingSingleIcon = false
    }

    private fun cancelPendingSpeech() {
        cancelPendingSingleIconSpeak()
        cancelPendingAutoSpeakSentence()
        isSpeakingSentence = false
        isSpeakingSingleIcon = false
        pendingSpeechMode = null
        activeSpeechMode = null
        pendingVendingDigitsSpeech = null
        audioPlayer.stopCurrentSpeech()
        nextSpeechRequestId("CANCEL_ALL")
    }

    private fun resetSpeechState(reason: String) {
        isSpeakingSentence = false
        isSpeakingSingleIcon = false
        pendingSpeechMode = null
        activeSpeechMode = null
        Log.d(TAG, "AAC_SPEECH STATE_RESET reason=$reason")
    }

    private fun resolveV2ChildItems(item: AacItem): List<AacItem> {
        val explicitChildren = item.children.mapNotNull { childId ->
            currentV2ItemsById[childId]
        }
        if (explicitChildren.isNotEmpty()) {
            return explicitChildren.sortedBy { it.priority }
        }

        return currentV2ItemsById.values
            .filter { it.parentId == item.id }
            .sortedBy { it.priority }
    }

    private fun resolveGuidedChildItems(item: AacItem, existingChildren: List<AacItem>): List<AacItem> {
        if (!isGuidedFollowUpAllowed()) {
            return existingChildren
        }
        if (existingChildren.isNotEmpty()) {
            return existingChildren
        }
        if (!isDrinkFollowUpTrigger(item)) {
            return existingChildren
        }
        return createFallbackDrinkChildItems(parentId = item.id)
    }

    private fun isDrinkFollowUpTrigger(item: AacItem): Boolean {
        val id = item.id.lowercase()
        val concept = item.conceptId?.lowercase().orEmpty()
        val label = item.labelSl.uppercase()
        return id == "thirsty" ||
            id == "drinks" ||
            id == "drink" ||
            concept == "thirsty" ||
            concept == "drinks" ||
            label.contains("ŽEJNA") ||
            label.contains("PIJAČA")
    }

    private fun createFallbackDrinkChildItems(parentId: String): List<AacItem> {
        return listOf(
            AacItem(
                id = "guided_water",
                labelSl = "VODA",
                imagePath = "",
                audioSl = "",
                actionType = "speak",
                targetPageId = "",
                speakTextSl = "voda",
                conceptId = "water",
                sentenceRole = "object",
                parentId = parentId,
                isRootItem = false,
                isHiddenUntilParent = true,
                priority = 0,
                vendingNumber = "12"
            ),
            AacItem(
                id = "guided_juice",
                labelSl = "SOK",
                imagePath = "",
                audioSl = "",
                actionType = "speak",
                targetPageId = "",
                speakTextSl = "sok",
                conceptId = "juice",
                sentenceRole = "object",
                parentId = parentId,
                isRootItem = false,
                isHiddenUntilParent = true,
                priority = 1,
                vendingNumber = "14"
            ),
            AacItem(
                id = "guided_coffee",
                labelSl = "KAVA",
                imagePath = "",
                audioSl = "",
                actionType = "speak",
                targetPageId = "",
                speakTextSl = "kava",
                conceptId = "coffee",
                sentenceRole = "object",
                parentId = parentId,
                isRootItem = false,
                isHiddenUntilParent = true,
                priority = 2,
                vendingNumber = "21",
                hasLargeCupOption = true
            ),
            AacItem(
                id = "guided_tea",
                labelSl = "ČAJ",
                imagePath = "",
                audioSl = "",
                actionType = "speak",
                targetPageId = "",
                speakTextSl = "čaj",
                conceptId = "tea",
                sentenceRole = "object",
                parentId = parentId,
                isRootItem = false,
                isHiddenUntilParent = true,
                priority = 3,
                vendingNumber = "22",
                hasLargeCupOption = true
            )
        )
    }

    private fun resolveFollowUpQuestion(item: AacItem): String? {
        if (isVendingScenarioActive() && isDrinkFollowUpTrigger(item)) {
            return "Kaj želiš izbrati?"
        }
        if (isGuidedFollowUpAllowed()) {
            item.followUpQuestion?.takeIf { it.isNotBlank() }?.let { return it }
            if (isDrinkFollowUpTrigger(item)) {
                return "Kaj želiš piti?"
            }
        } else if (isDrinkFollowUpTrigger(item)) {
            return null
        }
        return AacLocalizedTextResolver.resolveQuestion(item, languageCode)
    }

    private fun maybeShowVendingNumber(item: AacItem) {
        if (!isRealWorldHelperAllowed() ||
            !guidedFollowUpSettings.guidedFollowUpEnabled ||
            !guidedFollowUpSettings.vendingNumberDisplayEnabled ||
            !isDrinkMachineHelperItem(item)) {
            pendingVendingDigitsSpeech = null
            return
        }
        val vendingNumber = item.vendingNumber?.trim().orEmpty()
        if (vendingNumber.isEmpty()) {
            pendingVendingDigitsSpeech = null
            return
        }
        showVendingNumberDialog(item, vendingNumber)
        pendingVendingDigitsSpeech = if (guidedFollowUpSettings.speakDigitsSeparatelyEnabled) {
            buildDigitSpeechText(vendingNumber)
        } else {
            null
        }
    }

    private fun showVendingNumberDialog(item: AacItem, vendingNumber: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val titleView = TextView(this).apply {
            text = item.labelSl
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        val numberView = TextView(this).apply {
            text = vendingNumber
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
        }
        val subtitleView = TextView(this).apply {
            text = "Številka na avtomatu"
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }
        container.addView(titleView)
        container.addView(numberView)
        container.addView(subtitleView)

        AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("V redu", null)
            .show()
    }

    private fun buildDigitSpeechText(vendingNumber: String): String {
        val digits = vendingNumber.filter { it.isDigit() }
        if (digits.isBlank()) {
            return vendingNumber
        }
        if (AacLanguageResolver.normalize(languageCode) != AacLanguageResolver.DEFAULT_LANGUAGE_CODE) {
            return digits.toCharArray().joinToString(" ")
        }
        return digits.map { digit ->
            when (digit) {
                '0' -> "nič"
                '1' -> "ena"
                '2' -> "dva"
                '3' -> "tri"
                '4' -> "štiri"
                '5' -> "pet"
                '6' -> "šest"
                '7' -> "sedem"
                '8' -> "osem"
                '9' -> "devet"
                else -> digit.toString()
            }
        }.joinToString(", ")
    }

    private fun speakPendingVendingDigitsIfNeeded() {
        val pendingText = pendingVendingDigitsSpeech?.trim().orEmpty()
        pendingVendingDigitsSpeech = null
        if (pendingText.isNotEmpty() &&
            isRealWorldHelperAllowed() &&
            guidedFollowUpSettings.speakDigitsSeparatelyEnabled) {
            audioPlayer.speakText(pendingText, languageCode)
        }
    }

    private fun getAacCommunicationContext(): AacCommunicationContext {
        return aacCommunicationContext
    }

    private fun getActiveAacProfileId(): String {
        return activeAacProfileId.ifBlank { AacProfileStore.DEFAULT_PROFILE_ID }
    }

    private fun isGuidedFollowUpAllowed(): Boolean {
        return guidedFollowUpSettings.guidedFollowUpEnabled &&
            getActiveAacProfileId() != VIDEO_CALL_PROFILE_ID &&
            getAacCommunicationContext() != AacCommunicationContext.VIDEO_CALL_COMMUNICATION
    }

    private fun isRealWorldHelperAllowed(): Boolean {
        return realWorldHelpersEnabled &&
            getActiveAacProfileId() != VIDEO_CALL_PROFILE_ID &&
            getAacCommunicationContext() != AacCommunicationContext.VIDEO_CALL_COMMUNICATION
    }

    private fun isDrinkMachineHelperItem(item: AacItem): Boolean {
        val concept = item.conceptId?.lowercase().orEmpty()
        return !item.vendingNumber.isNullOrBlank() ||
            !item.vendingInstructionImagePath.isNullOrBlank() ||
            item.hasLargeCupOption ||
            !item.largeCupImagePath.isNullOrBlank() ||
            concept == "coffee" ||
            concept == "tea"
    }

    private fun startSentenceCompositionTrackingIfNeeded(clickedAt: Long) {
        if (sentenceCompositionStartedAt == 0L) {
            sentenceCompositionStartedAt = clickedAt
            singleIconSpeechOccurredInCurrentSentence = false
        }
        lastIconClickAt = clickedAt
    }

    private fun resetSentenceCompositionTracking() {
        singleIconSpeechOccurredInCurrentSentence = false
        sentenceCompositionStartedAt = 0L
        lastIconClickAt = 0L
    }

    private fun shouldSkipFastCompositionLastIcon(): Boolean {
        if (!speechTimingSettings.speakSingleIconEnabled ||
            !speechTimingSettings.delayedSingleIconSpeakEnabled ||
            !speechTimingSettings.fastCompositionSkipLastIconEnabled ||
            !speechTimingSettings.autoSpeakSentenceEnabled ||
            singleIconSpeechOccurredInCurrentSentence
        ) {
            return false
        }

        val itemCount = sentenceManager.getItems().size
        if (itemCount <= 1) {
            return false
        }

        if (sentenceCompositionStartedAt <= 0L || lastIconClickAt <= sentenceCompositionStartedAt) {
            return false
        }

        val compositionDuration = lastIconClickAt - sentenceCompositionStartedAt
        val fastCompositionThreshold = speechTimingSettings.singleIconSpeakDelayMs * itemCount
        return compositionDuration < fastCompositionThreshold
    }

    private fun speakCurrentSentence() {
        val text = sentenceManager.getSpeakText(languageCode)
        if (text.isNotBlank()) {
            val requestId = nextSpeechRequestId("MANUAL_SENTENCE")
            startSentenceSpeech(text, requestId)
        }
    }

    private fun nextSpeechRequestId(reason: String): Int {
        lastSpeechRequestId += 1
        Log.d(TAG, "AAC_SPEECH $reason requestId=$lastSpeechRequestId")
        return lastSpeechRequestId
    }

    private fun setPromptText(text: String?) {
        val value = text?.trim().orEmpty()
        txtPrompt.text = value
        txtPrompt.visibility = if (value.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun clearPromptText() {
        setPromptText(null)
    }

    private fun buildTitleText(baseTitle: String): String {
        return baseTitle
    }

    private fun showRepositoryDebugStatus() {
        if (repository.lastDebugCode == "OK") {
            return
        }

        txtTitle.text = buildTitleText(txtTitle.text.toString().lineSequence().firstOrNull().orEmpty())
    }

    private class AacAdapter(
        private val items: List<AacItem>,
        private val labelMode: AacLabelMode,
        private val languageCode: String,
        private val onItemClick: (AacItem) -> Unit,
        private val onWaterBindTrace: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacAdapter.AacViewHolder>() {
        private companion object {
            const val IMAGE_LOG_TAG = "AacCommunicatorActivity"

            fun resolveAacImageFile(
                context: android.content.Context,
                item: AacItem
            ): File? {
                val rawPath = item.imagePath.trim()
                if (rawPath.isEmpty()) {
                    Log.d(IMAGE_LOG_TAG, "AAC_IMAGE IMAGE_MISSING item=${item.id}")
                    return null
                }

                if (item.iconSource == com.rehab2.aac.IconSource.SYSTEM) {
                    Log.d(IMAGE_LOG_TAG, "AAC_IMAGE FALLBACK_TEXT_ICON item=${item.id}")
                    return null
                }

                val resolved = AacStoragePaths.resolveIconFile(context, rawPath, item.iconSource)
                val resolvedFile = resolved?.takeIf { it.exists() && it.isFile }
                if (item.iconSource == com.rehab2.aac.IconSource.SOCA) {
                    val state = if (resolvedFile != null) "resolved" else "missing"
                    Log.d(IMAGE_LOG_TAG, "AAC_IMAGE SOCA_$state item=${item.id} path=$rawPath")
                }
                return resolvedFile ?: run {
                    Log.d(IMAGE_LOG_TAG, "AAC_IMAGE IMAGE_MISSING item=${item.id}")
                    null
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AacViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_aac_tile, parent, false)
            return AacViewHolder(view, labelMode, languageCode, onItemClick, onWaterBindTrace)
        }

        override fun onBindViewHolder(holder: AacViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class AacViewHolder(
            itemView: View,
            private val labelMode: AacLabelMode,
            private val languageCode: String,
            private val onItemClick: (AacItem) -> Unit,
            private val onWaterBindTrace: (AacItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private companion object {
                val TILE_DEFAULT_COLOR = 0xFF263746.toInt()
                val TILE_CATEGORY_COLOR = 0xFF265B7E.toInt()
                val TILE_SUBCATEGORY_COLOR = 0xFF316A8D.toInt()
                val TILE_NAVIGATION_COLOR = 0xFF5B4A78.toInt()
                val TILE_PRESSED_COLOR = 0xFF3A8790.toInt()
                const val TILE_PRESS_FEEDBACK_MS = 180L
                const val MAX_TILE_IMAGE_DECODE_SIZE = 1024
            }

            private val image: ImageView = itemView.findViewById(R.id.imgAacTile)
            private val label: TextView = itemView.findViewById(R.id.txtAacTileLabel)
            private val context: android.content.Context = itemView.context
            private var tileColor: Int = TILE_DEFAULT_COLOR

            fun bind(item: AacItem) {
                tileColor = tileColorFor(item)
                itemView.setBackgroundColor(tileColor)
                if (item.id == "water") {
                    Log.d("AacCommunicatorActivity", "TRACE water adapter bind children=${item.children.size} ids=${item.children}")
                    onWaterBindTrace(item)
                }
                label.text = AacLocalizedTextResolver.resolveLabel(item, languageCode)
                label.gravity = Gravity.CENTER
                label.setTypeface(label.typeface, Typeface.BOLD)
                applyLabelMode()
                image.setImageBitmap(null)
                bindImage(item)

                itemView.setOnClickListener {
                    showPressedFeedback()
                    onItemClick(item)
                }
            }

            private fun showPressedFeedback() {
                itemView.setBackgroundColor(TILE_PRESSED_COLOR)
                itemView.postDelayed({
                    itemView.setBackgroundColor(tileColor)
                }, TILE_PRESS_FEEDBACK_MS)
            }

            private fun tileColorFor(item: AacItem): Int {
                return when {
                    item.actionType == "go_back" || item.actionType == "go_home" -> TILE_NAVIGATION_COLOR
                    item.actionType == "open_page" -> TILE_CATEGORY_COLOR
                    item.children.isNotEmpty() -> TILE_SUBCATEGORY_COLOR
                    else -> TILE_DEFAULT_COLOR
                }
            }

            private fun bindImage(item: AacItem) {
                val imageFile = resolveAacImageFile(context, item)
                if (imageFile == null) {
                    Log.d("AacCommunicatorActivity", "AAC_IMAGE FALLBACK_TEXT_ICON item=${item.id}")
                    showMissingImageFallback()
                    return
                }

                try {
                    val bitmap = decodeTileBitmap(imageFile)
                    if (bitmap != null) {
                        Log.d("AacCommunicatorActivity", "AAC_IMAGE IMAGE_LOADED item=${item.id}")
                        image.alpha = 1.0f
                        image.setImageBitmap(bitmap)
                    } else {
                        Log.d("AacCommunicatorActivity", "AAC_IMAGE IMAGE_LOAD_ERROR item=${item.id}")
                        if (item.iconSource == com.rehab2.aac.IconSource.SOCA) {
                            Log.d("AacCommunicatorActivity", "AAC_IMAGE SOCA_decode_failed item=${item.id} path=${item.imagePath}")
                        }
                        Log.d("AacCommunicatorActivity", "AAC_IMAGE FALLBACK_TEXT_ICON item=${item.id}")
                        showMissingImageFallback()
                    }
                } catch (_: Exception) {
                    Log.d("AacCommunicatorActivity", "AAC_IMAGE IMAGE_LOAD_ERROR item=${item.id}")
                    if (item.iconSource == com.rehab2.aac.IconSource.SOCA) {
                        Log.d("AacCommunicatorActivity", "AAC_IMAGE SOCA_decode_failed item=${item.id} path=${item.imagePath}")
                    }
                    Log.d("AacCommunicatorActivity", "AAC_IMAGE FALLBACK_TEXT_ICON item=${item.id}")
                    showMissingImageFallback()
                } catch (_: OutOfMemoryError) {
                    Log.d("AacCommunicatorActivity", "AAC_IMAGE IMAGE_LOAD_ERROR item=${item.id}")
                    if (item.iconSource == com.rehab2.aac.IconSource.SOCA) {
                        Log.d("AacCommunicatorActivity", "AAC_IMAGE SOCA_decode_failed item=${item.id} path=${item.imagePath}")
                    }
                    Log.d("AacCommunicatorActivity", "AAC_IMAGE FALLBACK_TEXT_ICON item=${item.id}")
                    showMissingImageFallback()
                }
            }

            private fun decodeTileBitmap(imageFile: File): android.graphics.Bitmap? {
                val boundsOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(imageFile.absolutePath, boundsOptions)
                if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                    return null
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateImageSampleSize(boundsOptions.outWidth, boundsOptions.outHeight)
                }
                return BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            }

            private fun calculateImageSampleSize(width: Int, height: Int): Int {
                var sampleSize = 1
                while (width / sampleSize > MAX_TILE_IMAGE_DECODE_SIZE || height / sampleSize > MAX_TILE_IMAGE_DECODE_SIZE) {
                    sampleSize *= 2
                }
                return sampleSize
            }

            private fun showMissingImageFallback() {
                image.alpha = 0.0f
                image.setImageDrawable(null)
                if (labelMode == AacLabelMode.HIDDEN) {
                    label.visibility = View.VISIBLE
                    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    label.maxLines = 2
                }
            }

            private fun applyLabelMode() {
                when (labelMode) {
                    AacLabelMode.HIDDEN -> {
                        label.visibility = View.GONE
                    }
                    AacLabelMode.SMALL -> {
                        label.visibility = View.VISIBLE
                        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        label.maxLines = 1
                    }
                    AacLabelMode.NORMAL -> {
                        label.visibility = View.VISIBLE
                        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        label.maxLines = 2
                    }
                    AacLabelMode.LARGE -> {
                        label.visibility = View.VISIBLE
                        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        label.maxLines = 2
                    }
                }
            }
        }
    }

    private data class DrinksCategoryRefreshResult(
        val rebuilt: Boolean,
        val exists: Boolean,
        val size: Long,
        val path: String
    ) {
        val isReady: Boolean
            get() = rebuilt && exists && size > 0L
    }

    private companion object {
        const val TAG = "AacCommunicatorActivity"
        const val DRINKS_CATEGORY_PAGE_ID = "drinks_v2"
        const val WATER_NODE_ID = "water"
        const val VIDEO_CALL_PROFILE_ID = "video_call"
        const val AAC_PREFS_FILE = "rehab2_prefs"
        const val PREF_AAC_GRID_SIZE = "aac_grid_size"
        const val DEFAULT_AAC_GRID_SIZE = 3
        const val PREF_AAC_PERSISTENT_TOP_ROW_ENABLED = "aac_persistent_top_row_enabled"
        const val PREF_AAC_PERSISTENT_TOP_ROW_COUNT = "aac_persistent_top_row_count"
        const val PREF_AAC_PERSISTENT_TOP_ROW_ITEM_IDS = "aac_persistent_top_row_item_ids"
        const val MIN_PERSISTENT_TOP_ROW_COUNT = 3
        const val MAX_PERSISTENT_TOP_ROW_COUNT = 5
        // Future therapist settings/content metadata may provide positions 1..5.
        // Runtime fixes only the first grid-width items; remaining configured items flow normally.
        const val DEFAULT_PERSISTENT_TOP_ROW_COUNT = 5
        val DEFAULT_PERSISTENT_TOP_ROW_ITEM_IDS = listOf("no", "yes", "dont_understand", "thank_you", "sorry")
    }
}
