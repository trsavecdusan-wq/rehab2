package com.rehab2

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
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
import java.io.File

class AacCommunicatorActivity : AppCompatActivity() {
    private lateinit var repository: AacRepository
    private val pageHistory = ArrayDeque<String>()
    private val sentenceManager = AacSentenceStateManager()
    private var currentV2ItemsById: Map<String, AacItem> = emptyMap()
    private val currentV2VisibleHistory: ArrayDeque<List<AacItem>> = ArrayDeque()
    private var currentVisibleItems: List<AacItem> = emptyList()
    private var currentV2RootItems: List<AacItem> = emptyList()
    private lateinit var audioPlayer: AacAudioPlayer
    private lateinit var txtTitle: TextView
    private lateinit var sentenceBar: View
    private lateinit var txtPrompt: TextView
    private lateinit var txtSentence: TextView
    private lateinit var btnOpenDrinksV2Test: Button
    private lateinit var btnSpeakSentence: Button
    private lateinit var btnClearSentence: Button
    private lateinit var recycler: RecyclerView
    private var labelMode: AacLabelMode = AacLabelMode.DEFAULT
    private var languageCode: String = AacLanguageResolver.DEFAULT_LANGUAGE_CODE
    private var currentPageId: String = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_communicator)
        repository = AacRepository(this)
        audioPlayer = AacAudioPlayer(this)

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
        btnOpenDrinksV2Test.text = "TEST PIJAČA V2 1.2.50"
        btnSpeakSentence = findViewById(R.id.btnAacSpeakSentence)
        btnClearSentence = findViewById(R.id.btnAacClearSentence)
        recycler = findViewById(R.id.recyclerAacTiles)
        recycler.layoutManager = GridLayoutManager(this, 5)
        labelMode = readAacLabelMode()
        languageCode = AacLanguageResolver.readSelectedLanguageCode(this)

        btnSpeakSentence.setOnClickListener {
            val text = sentenceManager.getSpeakText(languageCode)
            if (text.isNotBlank()) {
                audioPlayer.speakText(text, languageCode)
            }
        }
        btnClearSentence.setOnClickListener {
            sentenceManager.clear()
            currentV2VisibleHistory.clear()
            clearPromptText()
            if (currentV2RootItems.isNotEmpty()) {
                showItems(currentV2RootItems)
            }
            updateSentenceBar()
        }
        btnOpenDrinksV2Test.setOnClickListener {
            openDrinksV2Test()
        }
        txtTitle.setOnLongClickListener {
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
        if (updatedLabelMode != labelMode || updatedLanguageCode != languageCode) {
            labelMode = updatedLabelMode
            languageCode = updatedLanguageCode
            if (currentVisibleItems.isNotEmpty()) {
                showItems(currentVisibleItems)
            }
            updateSentenceBar()
        }
    }

    override fun onDestroy() {
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
        logWaterTrace("before adapter", items.firstOrNull { it.id == WATER_NODE_ID })
        recycler.adapter = AacAdapter(items, labelMode, languageCode) { item ->
            handleItemClick(item)
        }
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
                openTargetPage(item.targetPageId)
                return
            }
            "go_home" -> {
                goHome()
                return
            }
            "go_back" -> {
                goBack()
                return
            }
        }

        if (isV2Item(item)) {
            if (item.id == WATER_NODE_ID) {
                logWaterTrace("click item", item)
            }
            val childItems = item.children.mapNotNull { childId ->
                currentV2ItemsById[childId]
            }
            if (item.id == WATER_NODE_ID) {
                Log.d(TAG, "WATER clicked children=${childItems.size}")
                if (childItems.isEmpty()) {
                    Toast.makeText(this, "WATER children missing", Toast.LENGTH_LONG).show()
                }
            }
            sentenceManager.addItem(
                AacSentenceItem(
                    conceptId = item.conceptId ?: item.id,
                    text = AacLocalizedTextResolver.resolveSpeakText(item, languageCode),
                    role = item.sentenceRole
                )
            )
            updateSentenceBar()

            if (childItems.isNotEmpty()) {
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
            audioPlayer.playOrSpeak(item, languageCode)
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
        Toast.makeText(this, "TEST V2 CLICKED 1.2.50", Toast.LENGTH_LONG).show()
        val refreshResult = refreshBundledDrinksV2Page()
        showDrinksV2RefreshDebug(refreshResult)
        if (refreshResult.isReady) {
            Toast.makeText(this, "DRINKS V2 REFRESH OK", Toast.LENGTH_LONG).show()
            openTargetPage(DRINKS_V2_PAGE_ID)
            Toast.makeText(this, "OPEN $DRINKS_V2_PAGE_ID", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "DRINKS V2 REFRESH FAILED", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshBundledDrinksV2Page(): DrinksV2RefreshResult {
        val pagesDir = AacLocalStorage.getPagesDir(this)
        val runtimeFile = pagesDir?.let { File(it, "$DRINKS_V2_PAGE_ID.json") }
        if (runtimeFile != null && runtimeFile.exists()) {
            runtimeFile.delete()
        }
        val seeded = AacLocalStorage.seedBundledDrinksV2Page(this)
        val exists = runtimeFile?.exists() == true
        val size = runtimeFile?.takeIf { it.exists() }?.length() ?: 0L
        return DrinksV2RefreshResult(
            seeded = seeded,
            exists = exists,
            size = size,
            path = runtimeFile?.absolutePath.orEmpty()
        )
    }

    private fun showDrinksV2RefreshDebug(result: DrinksV2RefreshResult) {
        val existsText = if (result.exists) "yes" else "no"
        val message = "page=$DRINKS_V2_PAGE_ID runtime exists=$existsText size=${result.size}"
        Log.d(TAG, "$message path=${result.path} seeded=${result.seeded}")
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showDrinksV2WaterDebug(page: AacPage) {
        val waterItem = page.items.firstOrNull { it.id == WATER_NODE_ID }
        logWaterTrace("page model", waterItem)
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

    private fun goHome() {
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
        sentenceManager.clear()
        currentV2ItemsById = emptyMap()
        currentV2VisibleHistory.clear()
        currentVisibleItems = emptyList()
        currentV2RootItems = emptyList()
        clearPromptText()
        sentenceBar.visibility = View.GONE
        txtSentence.text = ""
        updateSentenceBar()
    }

    private fun isV2Page(page: AacPage): Boolean {
        return page.items.any { item -> isV2Item(item) }
    }

    private fun isV2Item(item: AacItem): Boolean {
        return item.conceptId != null || item.children.isNotEmpty() || item.sentenceRole != null
    }

    private fun getV2RootItems(items: List<AacItem>): List<AacItem> {
        val childIds = items.flatMap { it.children }.toSet()
        val rootItems = items.filter { it.id !in childIds }
        return rootItems.ifEmpty { items }
    }

    private fun updateSentenceBar() {
        val displayText = sentenceManager.getDisplayText()
        txtSentence.text = displayText
        val hasSentence = displayText.isNotBlank()
        btnSpeakSentence.isEnabled = hasSentence
        btnClearSentence.isEnabled = hasSentence
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
        private val onItemClick: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacAdapter.AacViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): AacViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_aac_tile, parent, false)
            return AacViewHolder(view, labelMode, languageCode, onItemClick)
        }

        override fun onBindViewHolder(holder: AacViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class AacViewHolder(
            itemView: View,
            private val labelMode: AacLabelMode,
            private val languageCode: String,
            private val onItemClick: (AacItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val image: ImageView = itemView.findViewById(R.id.imgAacTile)
            private val label: TextView = itemView.findViewById(R.id.txtAacTileLabel)

            fun bind(item: AacItem) {
                if (item.id == "water") {
                    Log.d("AacCommunicatorActivity", "TRACE water adapter bind children=${item.children.size} ids=${item.children}")
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
        val seeded: Boolean,
        val exists: Boolean,
        val size: Long,
        val path: String
    ) {
        val isReady: Boolean
            get() = seeded && exists && size > 0L
    }

    private companion object {
        const val TAG = "AacCommunicatorActivity"
        const val DRINKS_V2_PAGE_ID = "drinks_v2"
        const val WATER_NODE_ID = "water"
    }
}
