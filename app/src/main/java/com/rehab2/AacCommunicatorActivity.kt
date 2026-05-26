package com.rehab2

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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rehab2.aac.AacAudioPlayer
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLabelMode
import com.rehab2.aac.AacLanguageResolver
import com.rehab2.aac.AacLocalStorage
import com.rehab2.aac.AacLocalizedTextResolver
import com.rehab2.aac.AacPage
import com.rehab2.aac.AacRepository
import com.rehab2.aac.AacSentenceItem
import com.rehab2.aac.AacSentenceStateManager
import com.rehab2.aac.AacSpeechTimingSettings
import com.rehab2.aac.AacV2JsonParser
import com.rehab2.aac.AacV2PageAdapter
import java.io.File

class AacCommunicatorActivity : AppCompatActivity() {
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
    private var singleIconSpeechOccurredInCurrentSentence = false
    private var sentenceCompositionStartedAt = 0L
    private var lastIconClickAt = 0L
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
                }
            }

            override fun onSpeechCancelled() {
                Log.d(TAG, "AAC_SPEECH SPEECH_CANCELLED mode=$activeSpeechMode")
                resetSpeechState("cancelled")
            }

            override fun onSpeechError() {
                Log.d(TAG, "AAC_SPEECH SPEECH_ERROR mode=$activeSpeechMode")
                resetSpeechState("error")
            }
        })

        if (AacLocalStorage.ensureStructure(this)) {
            Toast.makeText(this, "AAC MAPE PRIPRAVLJENE", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "AAC MAP NI MOGOČE USTVARITI", Toast.LENGTH_SHORT).show()
        }

        if (AacLocalStorage.seedBundledDefaultPages(this)) {
            Toast.makeText(this, "AAC STRANI PRIPRAVLJENE", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "AAC STRANI NISO PRIPRAVLJENE", Toast.LENGTH_SHORT).show()
        }

        if (AacLocalStorage.seedBundledTestAudio(this)) {
            Toast.makeText(this, "AAC TEST AUDIO PRIPRAVLJEN", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "AAC TEST AUDIO NI PRIPRAVLJEN", Toast.LENGTH_SHORT).show()
        }

        txtTitle = findViewById(R.id.txtAacTitle)
        sentenceBar = findViewById(R.id.aacSentenceBar)
        txtPrompt = findViewById(R.id.txtAacPrompt)
        txtSentence = findViewById(R.id.txtAacSentence)
        btnOpenDrinksV2Test = findViewById(R.id.btnOpenDrinksV2Test)
        btnOpenDrinksV2Test.text = "TEST PIJAČA V2 1.2.84"
        btnSpeakSentence = findViewById(R.id.btnAacSpeakSentence)
        btnClearSentence = findViewById(R.id.btnAacClearSentence)
        recycler = findViewById(R.id.recyclerAacTiles)
        readAacGridSize()
        recycler.layoutManager = GridLayoutManager(this, aacGridSize)
        labelMode = readAacLabelMode()
        languageCode = AacLanguageResolver.readSelectedLanguageCode(this)
        speechTimingSettings = AacSpeechTimingSettings.read(this)
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
        btnOpenDrinksV2Test.setOnClickListener {
            resetWaterTraceDebug()
            updateWaterTraceDebug("TEST PIJAČA V2")
            openDrinksV2Test()
        }
        txtTitle.setOnLongClickListener {
            resetWaterTraceDebug()
            updateWaterTraceDebug("TITLE LONG PRESS")
            openDrinksV2Test()
            true
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
            oldGridSize != aacGridSize ||
            oldTopRowEnabled != persistentTopRowEnabled ||
            oldTopRowCount != persistentTopRowCount ||
            oldTopRowItemIds != persistentTopRowItemIds
        ) {
            labelMode = updatedLabelMode
            languageCode = updatedLanguageCode
            speechTimingSettings = updatedSpeechTimingSettings
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
        if (isV2Page(page)) {
            currentV2ItemsById = page.items.associateBy { it.id }
            currentV2RootItems = getV2RootItems(page.items)
            currentV2VisibleHistory.clear()
            sentenceBar.visibility = View.VISIBLE
            clearPromptText()
            updateSentenceBar()
            showItems(currentV2RootItems)
            if (page.pageId == DRINKS_V2_PAGE_ID) {
                showDrinksV2WaterDebug(page)
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
    }

    private fun readAacLabelMode(): AacLabelMode {
        val prefs = getSharedPreferences(AacLabelMode.PREFS_FILE, MODE_PRIVATE)
        return AacLabelMode.fromPreference(
            prefs.getString(AacLabelMode.PREF_AAC_LABEL_MODE, AacLabelMode.DEFAULT.name)
        )
    }

    private fun handleItemClick(item: AacItem) {
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
            val childItems = resolveV2ChildItems(item)
            if (item.id == WATER_NODE_ID) {
                Log.d(TAG, "WATER clicked children=${childItems.size}")
                if (childItems.isEmpty()) {
                    updateWaterTraceDebug("WATER CLICK CHILDREN=0")
                    Toast.makeText(this, "WATER children missing", Toast.LENGTH_LONG).show()
                } else {
                    updateWaterTraceDebug("WATER CLICK CHILDREN=${childItems.size}")
                }
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

            if (childItems.isNotEmpty()) {
                speakSingleIconIfEnabled(singleIconText, speechRequestId)
                scheduleAutoSpeakSentenceIfEnabled(speechRequestId)
                setPromptText(AacLocalizedTextResolver.resolveQuestion(item, languageCode))
                currentV2VisibleHistory.addLast(currentVisibleItems)
                showItems(childItems)
                if (item.id == WATER_NODE_ID) {
                    Toast.makeText(this, "OPEN WATER OPTIONS", Toast.LENGTH_LONG).show()
                }
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

    private fun openTargetPage(targetPageId: String) {
        val normalizedTargetPageId = targetPageId.trim()
        if (normalizedTargetPageId.isBlank()) {
            Toast.makeText(this, "Stran ni določena", Toast.LENGTH_SHORT).show()
            return
        }

        val page = repository.loadPage(normalizedTargetPageId)
        if (page == null) {
            showRepositoryDebugStatus()
            return
        }

        pageHistory.addLast(currentPageId)
        showPage(page)
    }

    private fun openDrinksV2Test() {
        Toast.makeText(this, "TEST V2 CLICKED 1.2.84", Toast.LENGTH_LONG).show()
        val refreshResult = refreshBundledDrinksV2Page()
        showDrinksV2RefreshDebug(refreshResult)
        if (refreshResult.isReady) {
            updateWaterTraceDebug("RUNTIME PAGE REBUILT")
            Toast.makeText(this, "RUNTIME PAGE REBUILT", Toast.LENGTH_LONG).show()
            openTargetPage(DRINKS_V2_PAGE_ID)
            Toast.makeText(this, "OPEN $DRINKS_V2_PAGE_ID", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "DRINKS V2 REFRESH FAILED", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshBundledDrinksV2Page(): DrinksV2RefreshResult {
        val pagesDir = AacLocalStorage.getPagesDir(this)
        val runtimeFile = pagesDir?.let { File(it, "$DRINKS_V2_PAGE_ID.json") }
        val rebuilt = AacLocalStorage.rebuildBundledDrinksV2Page(this)
        val exists = runtimeFile?.exists() == true
        val size = runtimeFile?.takeIf { it.exists() }?.length() ?: 0L
        return DrinksV2RefreshResult(
            rebuilt = rebuilt,
            exists = exists,
            size = size,
            path = runtimeFile?.absolutePath.orEmpty()
        )
    }

    private fun showDrinksV2RefreshDebug(result: DrinksV2RefreshResult) {
        val existsText = if (result.exists) "yes" else "no"
        val message = "page=$DRINKS_V2_PAGE_ID runtime exists=$existsText size=${result.size}"
        Log.d(TAG, "$message path=${result.path} rebuilt=${result.rebuilt}")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showDrinksV2WaterDebug(page: AacPage) {
        val waterItem = page.items.firstOrNull { it.id == WATER_NODE_ID }
        waterPageModelChildrenCount = waterItem?.children?.size ?: -1
        logWaterTrace("page model", waterItem)
        updateWaterTraceDebug("page model")
        val waterChildrenCount = waterItem?.children?.size ?: 0
        val message = if (waterChildrenCount == 4) {
            "WATER children=4"
        } else {
            "WATER children=$waterChildrenCount — runtime JSON stale or wrong page"
        }
        Log.d(TAG, "page=${page.pageId} $message")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun logWaterTrace(stage: String, item: AacItem?) {
        val children = item?.children.orEmpty()
        Log.d(TAG, "TRACE water $stage children=${children.size} ids=$children")
    }

    private fun addWaterTraceDebugView() {
        val root = findViewById<FrameLayout>(android.R.id.content)
        txtWaterTraceDebug = TextView(this).apply {
            text = "WATER TRACE: waiting"
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
        txtWaterTraceDebug.visibility = View.VISIBLE
        txtWaterTraceDebug.text = buildString {
            appendLine("WATER TRACE 1.2.84: $stage")
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
        val gridSize = getAacGridSize()
        persistentTopRowEnabled = prefs.getBoolean(PREF_AAC_PERSISTENT_TOP_ROW_ENABLED, true)
        val rawTopRowCount = prefs.getInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, DEFAULT_PERSISTENT_TOP_ROW_COUNT)
        persistentTopRowCount = normalizePersistentTopRowCount(rawTopRowCount, gridSize)
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

    private fun getPersistentTopRowItems(): List<AacItem> {
        if (!persistentTopRowEnabled) return emptyList()

        val homeItems = repository.loadPage("home")?.items.orEmpty().associateBy { it.id }
        return persistentTopRowItemIds
            .take(getPersistentTopRowCount())
            .mapNotNull { id -> homeItems[id] }
            .map { item ->
                item.copy(
                    actionType = "speak",
                    targetPageId = "",
                    conceptId = item.conceptId ?: item.id,
                    sentenceRole = item.sentenceRole ?: "quick"
                )
            }
    }

    private fun mergePersistentTopRowWithCurrentMenuItems(items: List<AacItem>): List<AacItem> {
        val topRowItems = getPersistentTopRowItems()
        val maxItems = getAacItemsPerPage()
        if (topRowItems.isEmpty()) return items.take(maxItems)

        val topRowIds = topRowItems.map { it.id }.toSet()
        val remainingSlots = (maxItems - topRowItems.size).coerceAtLeast(0)
        val menuItemsWithoutDuplicates = items.filter { it.id !in topRowIds }.take(remainingSlots)
        return topRowItems + menuItemsWithoutDuplicates
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
        return if (repository.lastDebugCode == "OK") {
            baseTitle
        } else {
            "$baseTitle\n${repository.lastDebugCode}"
        }
    }

    private fun showRepositoryDebugStatus() {
        if (repository.lastDebugCode == "OK") {
            return
        }

        txtTitle.text = buildTitleText(txtTitle.text.toString().lineSequence().firstOrNull().orEmpty())
        Toast.makeText(this, repository.lastDebugStatus, Toast.LENGTH_LONG).show()
        Toast.makeText(this, AacLocalStorage.lastStorageDebugStatus, Toast.LENGTH_LONG).show()
    }

    private class AacAdapter(
        private val items: List<AacItem>,
        private val labelMode: AacLabelMode,
        private val languageCode: String,
        private val onItemClick: (AacItem) -> Unit,
        private val onWaterBindTrace: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacAdapter.AacViewHolder>() {

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
            private val image: ImageView = itemView.findViewById(R.id.imgAacTile)
            private val label: TextView = itemView.findViewById(R.id.txtAacTileLabel)

            fun bind(item: AacItem) {
                if (item.id == "water") {
                    Log.d("AacCommunicatorActivity", "TRACE water adapter bind children=${item.children.size} ids=${item.children}")
                    onWaterBindTrace(item)
                }
                label.text = AacLocalizedTextResolver.resolveLabel(item, languageCode)
                label.gravity = Gravity.CENTER
                label.setTypeface(label.typeface, Typeface.BOLD)
                applyLabelMode()
                image.setImageBitmap(null)

                val imageFile = item.imagePath.takeIf { it.isNotBlank() }?.let { File(it) }
                if (imageFile != null && imageFile.exists() && imageFile.isFile) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap)
                    } else {
                        image.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                } else {
                    image.setImageResource(android.R.drawable.ic_menu_gallery)
                }

                itemView.setOnClickListener { onItemClick(item) }
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

    private data class DrinksV2RefreshResult(
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
        const val DRINKS_V2_PAGE_ID = "drinks_v2"
        const val WATER_NODE_ID = "water"
        const val AAC_PREFS_FILE = "rehab2_prefs"
        const val PREF_AAC_GRID_SIZE = "aac_grid_size"
        const val DEFAULT_AAC_GRID_SIZE = 3
        const val PREF_AAC_PERSISTENT_TOP_ROW_ENABLED = "aac_persistent_top_row_enabled"
        const val PREF_AAC_PERSISTENT_TOP_ROW_COUNT = "aac_persistent_top_row_count"
        const val PREF_AAC_PERSISTENT_TOP_ROW_ITEM_IDS = "aac_persistent_top_row_item_ids"
        const val MIN_PERSISTENT_TOP_ROW_COUNT = 3
        const val MAX_PERSISTENT_TOP_ROW_COUNT = 5
        const val DEFAULT_PERSISTENT_TOP_ROW_COUNT = 4
        val DEFAULT_PERSISTENT_TOP_ROW_ITEM_IDS = listOf("yes", "no", "help", "pain", "stop")
    }
}
