package com.rehab2

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rehab2.aac.AacContentBootstrap
import com.rehab2.aac.AacEditorAudit
import com.rehab2.aac.AacEditorStorage
import com.rehab2.aac.AacImageGallery
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLocalizedTextResolver
import com.rehab2.aac.AacStarterContentV1
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.IconSource
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.Locale

class AacEditorActivity : AppCompatActivity() {
    private lateinit var pageButtons: LinearLayout
    private lateinit var pageTitle: TextView
    private lateinit var recycler: RecyclerView
    private var pages: List<AacEditorStorage.EditorPage> = emptyList()
    private var selectedPageIndex = 0
    private val adapter = AacEditorAdapter(::showEditIconDialog)
    private lateinit var galleryImagePicker: ActivityResultLauncher<Intent>
    private lateinit var cameraCaptureLauncher: ActivityResultLauncher<Uri>
    private var pendingGalleryItemId: String? = null
    private var pendingGalleryRefresh: (() -> Unit)? = null
    private var pendingCameraItemId: String? = null
    private var pendingCameraFile: File? = null
    private var pendingCameraRefresh: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        galleryImagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleGalleryImageResult(result.resultCode, result.data?.data)
        }
        cameraCaptureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            handleCameraCaptureResult(success)
        }
        setContentView(R.layout.activity_aac_editor)

        pageButtons = findViewById(R.id.aacEditorPageButtons)
        pageTitle = findViewById(R.id.txtAacEditorPageTitle)
        recycler = findViewById(R.id.recyclerAacEditorTiles)
        recycler.layoutManager = GridLayoutManager(this, 5)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnAacEditorBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAacEditorAudit).setOnClickListener { showCommunicatorAuditDialog() }

        loadEditorPages()
    }

    private fun loadEditorPages() {
        AacContentBootstrap.ensurePatientStartupContent(this, AacStarterContentV1.items())
        pages = AacEditorStorage.loadPages(this)
        if (pages.isEmpty()) {
            pageTitle.text = "NI AAC STRANI"
            adapter.submitItems(emptyList())
            return
        }
        selectedPageIndex = selectedPageIndex.coerceIn(0, pages.lastIndex)
        renderPageButtons()
        showPage(selectedPageIndex)
    }

    private fun renderPageButtons() {
        pageButtons.removeAllViews()
        pages.forEachIndexed { index, page ->
            val button = Button(this).apply {
                text = page.title
                isAllCaps = false
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    if (index == selectedPageIndex) 0xFF2F5F9E.toInt() else 0xFF34414D.toInt()
                )
                setOnClickListener {
                    selectedPageIndex = index
                    renderPageButtons()
                    showPage(index)
                }
            }
            val params = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.aac_editor_page_button_width),
                resources.getDimensionPixelSize(R.dimen.aac_editor_page_button_height)
            ).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.aac_editor_page_button_gap)
            }
            pageButtons.addView(button, params)
        }
    }

    private fun showPage(index: Int) {
        val page = pages.getOrNull(index) ?: return
        pageTitle.text = page.title
        adapter.submitItems(page.items, AacEditorStorage.hiddenItemIds(this))
    }

    private fun showCommunicatorAuditDialog() {
        val problems = AacEditorAudit.run(this)
        val message = AacEditorAudit.format(problems)
        AlertDialog.Builder(this)
            .setTitle("PREVERI KOMUNIKATOR (${problems.size})")
            .setMessage(message.take(12000))
            .setPositiveButton("ZAPRI", null)
            .show()
    }

    private fun showEditIconDialog(item: AacItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_aac_editor_icon, null)
        val preview = dialogView.findViewById<ImageView>(R.id.imgAacEditorPreview)
        val itemId = dialogView.findViewById<TextView>(R.id.txtAacEditorItemId)
        val label = dialogView.findViewById<TextView>(R.id.txtAacEditorLabel)
        val speech = dialogView.findViewById<TextView>(R.id.txtAacEditorSpeech)
        val imagePath = dialogView.findViewById<TextView>(R.id.txtAacEditorImagePath)
        val hiddenStatus = dialogView.findViewById<TextView>(R.id.txtAacEditorHiddenStatus)
        val childList = dialogView.findViewById<LinearLayout>(R.id.aacEditorChildrenList)
        val changeImage = dialogView.findViewById<Button>(R.id.btnAacEditorChangeImage)
        val pickCustomFolder = dialogView.findViewById<Button>(R.id.btnAacEditorPickCustomFolder)
        val pickPatientFolder = dialogView.findViewById<Button>(R.id.btnAacEditorPickPatientFolder)
        val pickSocaFolder = dialogView.findViewById<Button>(R.id.btnAacEditorPickSocaFolder)
        val pickArasaacFolder = dialogView.findViewById<Button>(R.id.btnAacEditorPickArasaacFolder)
        val pickGalleryImage = dialogView.findViewById<Button>(R.id.btnAacEditorPickGalleryImage)
        val capturePhoto = dialogView.findViewById<Button>(R.id.btnAacEditorCapturePhoto)
        val removeImage = dialogView.findViewById<Button>(R.id.btnAacEditorRemoveImage)
        val copyIcon = dialogView.findViewById<Button>(R.id.btnAacEditorCopyIcon)
        val toggleHidden = dialogView.findViewById<Button>(R.id.btnAacEditorToggleHidden)
        val addChild = dialogView.findViewById<Button>(R.id.btnAacEditorAddChild)
        val addNewChild = dialogView.findViewById<Button>(R.id.btnAacEditorAddNewChild)
        val editLabel = dialogView.findViewById<Button>(R.id.btnAacEditorEditLabel)
        val editSpeech = dialogView.findViewById<Button>(R.id.btnAacEditorEditSpeech)

        var currentItem = item
        lateinit var dialog: AlertDialog

        fun refreshDialogContent() {
            currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: currentItem
            val allItems = AacEditorStorage.loadItems(this)
            val hiddenIds = AacEditorStorage.hiddenItemIds(this)
            val isHidden = currentItem.id in hiddenIds
            bindPreview(preview, currentItem)
            itemId.text = "itemId: ${currentItem.id}"
            label.text = "labelSl: ${currentItem.labelSl}"
            speech.text = "speechTextSl: ${currentItem.resolvedSpeechText}"
            imagePath.text = imageStatusText(currentItem)
            hiddenStatus.text = if (isHidden) "SKRITO: editor marker, runtime skrivanje pride kasneje." else ""
            toggleHidden.text = if (isHidden) "POKA\u017dI IKONO" else "SKRIJ IKONO"
            renderChildSection(
                parent = currentItem,
                allItems = allItems,
                childList = childList,
                editDialog = dialog,
                refresh = { refreshDialogContent() }
            )
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("UREDI IKONO")
            .setView(dialogView)
            .setNegativeButton("ZAPRI", null)
            .create()

        changeImage.setOnClickListener {
            showImageSourceDialog(currentItem, dialog)
        }
        pickCustomFolder.setOnClickListener {
            showSimpleImageSourceBrowser(currentItem, IconSource.CUSTOM) { refreshDialogContent() }
        }
        pickPatientFolder.setOnClickListener {
            showSimpleImageSourceBrowser(currentItem, IconSource.PATIENT) { refreshDialogContent() }
        }
        pickSocaFolder.setOnClickListener {
            showSimpleImageSourceBrowser(currentItem, IconSource.SOCA) { refreshDialogContent() }
        }
        pickArasaacFolder.setOnClickListener {
            showSimpleImageSourceBrowser(currentItem, IconSource.ARASAAC) { refreshDialogContent() }
        }
        pickGalleryImage.setOnClickListener {
            openGalleryImagePicker(currentItem) { refreshDialogContent() }
        }
        capturePhoto.setOnClickListener {
            openCameraCapture(currentItem) { refreshDialogContent() }
        }
        removeImage.setOnClickListener {
            removeImageFromItem(currentItem) { refreshDialogContent() }
        }
        copyIcon.setOnClickListener {
            showCopyIconDialog(currentItem) { refreshDialogContent() }
        }
        toggleHidden.setOnClickListener {
            toggleHiddenMarker(currentItem) { refreshDialogContent() }
        }
        addChild.setOnClickListener {
            showAddChildDialog(currentItem) { refreshDialogContent() }
        }
        addNewChild.setOnClickListener {
            showAddNewChildIconDialog(currentItem) { refreshDialogContent() }
        }
        editLabel.setOnClickListener {
            showTextEditDialog(
                title = "UREDI TEKST",
                currentValue = currentItem.labelSl,
                onSave = { value ->
                    saveTextChange(
                        saved = AacEditorStorage.updateLabelSl(this, currentItem.id, value),
                        dialog = dialog
                    )
                }
            )
        }
        editSpeech.setOnClickListener {
            showTextEditDialog(
                title = "UREDI GOVOR",
                currentValue = currentItem.resolvedSpeechText,
                onSave = { value ->
                    saveTextChange(
                        saved = AacEditorStorage.updateSpeechTextSl(this, currentItem.id, value),
                        dialog = dialog
                    )
                }
            )
        }

        refreshDialogContent()
        dialog.show()
    }

    private fun renderChildSection(
        parent: AacItem,
        allItems: List<AacItem>,
        childList: LinearLayout,
        editDialog: AlertDialog,
        refresh: () -> Unit
    ) {
        childList.removeAllViews()
        val itemsById = allItems.associateBy { it.id }
        if (parent.children.isEmpty()) {
            childList.addView(TextView(this).apply {
                text = "Ni podikon."
                setTextColor(0xFFDCE6EF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(8, 8, 8, 8)
            })
            return
        }

        parent.children.forEachIndexed { index, childId ->
            val child = itemsById[childId]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 10, 10, 10)
                setBackgroundColor(0xFF1E2329.toInt())
            }
            row.addView(TextView(this).apply {
                text = "${index + 1}. ${child?.labelSl ?: childId} ($childId)"
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 0)
            }
            buttons.addView(buildChildActionButton("GOR") {
                if (index > 0) {
                    updateChildren(parent, parent.children.toMutableList().also {
                        val moved = it.removeAt(index)
                        it.add(index - 1, moved)
                    }, refresh)
                }
            })
            buttons.addView(buildChildActionButton("DOL") {
                if (index < parent.children.lastIndex) {
                    updateChildren(parent, parent.children.toMutableList().also {
                        val moved = it.removeAt(index)
                        it.add(index + 1, moved)
                    }, refresh)
                }
            })
            buttons.addView(buildChildActionButton("ODSTRANI") {
                confirmRemoveChild(parent, childId, child?.labelSl ?: childId, editDialog, refresh)
            })
            row.addView(buttons)
            childList.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8
                }
            )
        }
    }

    private fun buildChildActionButton(textValue: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = textValue
            isAllCaps = false
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF34414D.toInt())
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, 52, 1f).apply {
                marginEnd = 6
            }
        }
    }

    private fun confirmRemoveChild(
        parent: AacItem,
        childId: String,
        childLabel: String,
        editDialog: AlertDialog,
        refresh: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle("ODSTRANI PODIKONO")
            .setMessage("Odstranim povezavo, ikona ne bo izbrisana.\n\n$childLabel")
            .setPositiveButton("ODSTRANI") { _, _ ->
                updateChildren(parent, parent.children.filterNot { it == childId }, refresh)
            }
            .setNegativeButton("PREKLI\u010cI", null)
            .show()
    }

    private fun updateChildren(parent: AacItem, nextChildren: List<String>, refresh: () -> Unit) {
        val saved = AacEditorStorage.updateChildren(this, parent.id, nextChildren)
        if (saved) {
            Toast.makeText(this, "Podikone shranjene.", Toast.LENGTH_SHORT).show()
            loadEditorPages()
            refresh()
        } else {
            Toast.makeText(this, "Shranjevanje podikon ni uspelo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddChildDialog(parent: AacItem, refresh: () -> Unit) {
        val allItems = AacEditorStorage.loadItems(this)
        lateinit var pickerDialog: AlertDialog
        val adapter = AacItemPickerAdapter(emptyList()) { child ->
            addChildIfAllowed(parent, child, refresh)
            pickerDialog.dismiss()
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(14, 14, 14, 8)
        }
        val title = TextView(this).apply {
            text = "DODAJ PODIKONO ZA: ${parent.labelSl}"
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(4, 0, 4, 12)
        }
        val search = EditText(this).apply {
            hint = "Išči po labelSl ali itemId"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setTextColor(0xFFF4F7FA.toInt())
            setHintTextColor(0xFF9EA8B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(16, 10, 16, 10)
            setBackgroundColor(0xFF1E2329.toInt())
        }
        val list = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AacEditorActivity, 4)
            this.adapter = adapter
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        fun applyFilter(query: String) {
            val normalized = query.trim().lowercase()
            val filtered = allItems
                .filter { candidate ->
                    candidate.id != parent.id &&
                        candidate.id !in parent.children &&
                        !wouldCreateSimpleCycle(parent, candidate) &&
                        (
                            normalized.isBlank() ||
                                candidate.labelSl.lowercase().contains(normalized) ||
                                candidate.id.lowercase().contains(normalized)
                            )
                }
                .sortedWith(compareBy({ it.labelSl.lowercase() }, { it.id }))
            adapter.submitItems(filtered)
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        container.addView(title)
        container.addView(
            search,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 12
            }
        )
        container.addView(
            list,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.aac_editor_child_picker_height)
            )
        )
        applyFilter("")

        pickerDialog = AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton("PREKLI\u010cI", null)
            .create()
        pickerDialog.show()
    }

    private fun addChildIfAllowed(parent: AacItem, child: AacItem, refresh: () -> Unit) {
        when {
            child.id == parent.id -> {
                Toast.makeText(this, "Ikona ne more biti sama sebi podikona.", Toast.LENGTH_LONG).show()
            }
            child.id in parent.children -> {
                Toast.makeText(this, "Ta podikona je \u017ee dodana.", Toast.LENGTH_LONG).show()
            }
            wouldCreateSimpleCycle(parent, child) -> {
                Toast.makeText(this, "Ta povezava bi ustvarila cikel.", Toast.LENGTH_LONG).show()
            }
            else -> {
                updateChildren(parent, parent.children + child.id, refresh)
            }
        }
    }

    private fun wouldCreateSimpleCycle(parent: AacItem, child: AacItem): Boolean {
        return parent.id in child.children
    }

    private fun showCopyIconDialog(item: AacItem, refresh: () -> Unit) {
        val allItems = AacEditorStorage.loadItems(this)
        val parent = allItems.firstOrNull { item.id in it.children }
        if (parent == null) {
            Toast.makeText(this, "Root/page ikone ne kopiram, ker bi to spremenilo postavitev strani.", Toast.LENGTH_LONG).show()
            return
        }

        val existingIds = allItems.map { it.id }.toSet()
        var selectedImagePath = item.imagePath
        var selectedImageFile = resolveImageFile(item)
        var selectedIconSource = item.iconSource
        if (selectedImagePath.isNotBlank() && selectedImageFile?.isFile != true) {
            selectedImagePath = ""
            selectedImageFile = null
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(18, 18, 18, 8)
        }
        val labelInput = buildNewIconInput("Napis pod ikono / labelSl").apply { setText(item.labelSl) }
        val speechInput = buildNewIconInput("Govor / speechTextSl").apply { setText(item.resolvedSpeechText) }
        val itemIdInput = buildNewIconInput("itemId").apply { setText(suggestCopyItemId(item.id, existingIds)) }
        val imageInfo = TextView(this).apply {
            text = if (selectedImagePath.isBlank()) {
                "Slika: ni izbrana\nIkona bo prikazana samo kot tekst."
            } else {
                "Slika: $selectedImagePath\nVir: ${selectedIconSource.name}"
            }
            setTextColor(0xFFDCE6EF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 8, 0, 8)
        }
        val chooseImage = Button(this).apply {
            text = "IZBERI SLIKO"
            isAllCaps = false
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF34414D.toInt())
            setOnClickListener {
                showImageSourceDialogForNewIcon { entry ->
                    selectedImagePath = entry.imagePath
                    selectedImageFile = entry.file
                    selectedIconSource = entry.source.iconSource
                    imageInfo.text = "Slika: $selectedImagePath\nVir: ${selectedIconSource.name}"
                }
            }
        }

        container.addView(labelInput, newIconInputParams())
        container.addView(speechInput, newIconInputParams())
        container.addView(itemIdInput, newIconInputParams())
        container.addView(imageInfo)
        container.addView(chooseImage, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 58))

        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle("KOPIRAJ IKONO")
            .setView(container)
            .setPositiveButton("SHRANI", null)
            .setNegativeButton("PREKLI\u010cI", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveCopiedIcon(
                    source = item,
                    labelSl = labelInput.text?.toString().orEmpty(),
                    speechTextSl = speechInput.text?.toString().orEmpty(),
                    itemId = itemIdInput.text?.toString().orEmpty(),
                    iconSource = selectedIconSource,
                    imagePath = selectedImagePath,
                    imageFile = selectedImageFile,
                    existingIds = existingIds,
                    dialog = dialog,
                    refresh = refresh
                )
            }
        }
        dialog.show()
    }

    private fun saveCopiedIcon(
        source: AacItem,
        labelSl: String,
        speechTextSl: String,
        itemId: String,
        iconSource: IconSource,
        imagePath: String,
        imageFile: File?,
        existingIds: Set<String>,
        dialog: AlertDialog,
        refresh: () -> Unit
    ) {
        val normalizedLabel = labelSl.trim()
        val normalizedSpeech = speechTextSl.trim()
        val normalizedId = normalizeItemId(itemId)
        when {
            normalizedLabel.isBlank() -> {
                Toast.makeText(this, "Napis pod ikono ne sme biti prazen.", Toast.LENGTH_LONG).show()
                return
            }
            normalizedSpeech.isBlank() -> {
                Toast.makeText(this, "Govor ne sme biti prazen.", Toast.LENGTH_LONG).show()
                return
            }
            normalizedId.isBlank() || normalizedId in existingIds || normalizedId == source.id -> {
                Toast.makeText(this, "itemId mora biti unikaten.", Toast.LENGTH_LONG).show()
                return
            }
            imagePath.isBlank() -> {
                AlertDialog.Builder(this)
                    .setTitle("BREZ SLIKE")
                    .setMessage("Ikona bo prikazana samo kot tekst.")
                    .setPositiveButton("SHRANI") { _, _ ->
                        persistCopiedIcon(source, normalizedId, normalizedLabel, normalizedSpeech, iconSource, imagePath, dialog, refresh)
                    }
                    .setNegativeButton("PREKLI\u010cI", null)
                    .show()
            }
            imageFile == null || !imageFile.isFile || BitmapFactory.decodeFile(imageFile.absolutePath) == null -> {
                Toast.makeText(this, "Izbrana slika ni ve\u010d berljiva.", Toast.LENGTH_LONG).show()
            }
            else -> {
                persistCopiedIcon(source, normalizedId, normalizedLabel, normalizedSpeech, iconSource, imagePath, dialog, refresh)
            }
        }
    }

    private fun persistCopiedIcon(
        source: AacItem,
        itemId: String,
        labelSl: String,
        speechTextSl: String,
        iconSource: IconSource,
        imagePath: String,
        dialog: AlertDialog,
        refresh: () -> Unit
    ) {
        val saved = AacEditorStorage.copyIconAsSibling(
            this,
            AacEditorStorage.CopyIcon(
                sourceId = source.id,
                newId = itemId,
                labelSl = labelSl,
                speechTextSl = speechTextSl,
                iconSource = iconSource,
                imagePath = imagePath
            )
        )
        if (saved) {
            Toast.makeText(this, "Ikona je kopirana.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            loadEditorPages()
            refresh()
        } else {
            Toast.makeText(this, "Kopiranje ni uspelo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleHiddenMarker(item: AacItem, refresh: () -> Unit) {
        val hidden = item.id in AacEditorStorage.hiddenItemIds(this)
        val saved = AacEditorStorage.setHidden(this, item.id, !hidden)
        if (saved) {
            Toast.makeText(
                this,
                if (hidden) "Ikona je ozna\u010dena kot prikazana." else "Ikona je ozna\u010dena kot skrita v editorju.",
                Toast.LENGTH_SHORT
            ).show()
            loadEditorPages()
            refresh()
        } else {
            Toast.makeText(this, "Te ikone ni dovoljeno skriti ali pokazati.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddNewChildIconDialog(parent: AacItem, refresh: () -> Unit) {
        val allItems = AacEditorStorage.loadItems(this)
        val existingIds = allItems.map { it.id }.toSet()
        var selectedImagePath = ""
        var selectedImageFile: File? = null
        var selectedIconSource = IconSource.CUSTOM
        var itemIdManuallyEdited = false

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(18, 18, 18, 8)
        }
        val labelInput = buildNewIconInput("Napis pod ikono / labelSl")
        val speechInput = buildNewIconInput("Govor / speechTextSl")
        val itemIdInput = buildNewIconInput("itemId predlog")
        val imageInfo = TextView(this).apply {
            text = "Slika: ni izbrana\nIkona bo prikazana samo kot tekst."
            setTextColor(0xFFDCE6EF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 8, 0, 8)
        }
        val chooseImage = Button(this).apply {
            text = "IZBERI SLIKO"
            isAllCaps = false
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF34414D.toInt())
        }

        labelInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!itemIdManuallyEdited) {
                    itemIdInput.setText(suggestNewItemId(s?.toString().orEmpty(), existingIds))
                    itemIdInput.setSelection(itemIdInput.text?.length ?: 0)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        itemIdInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) itemIdManuallyEdited = true
        }
        chooseImage.setOnClickListener {
            showImageSourceDialogForNewIcon { entry ->
                selectedImagePath = entry.imagePath
                selectedImageFile = entry.file
                selectedIconSource = entry.source.iconSource
                imageInfo.text = "Slika: $selectedImagePath\nVir: ${selectedIconSource.name}"
            }
        }

        container.addView(labelInput, newIconInputParams())
        container.addView(speechInput, newIconInputParams())
        container.addView(itemIdInput, newIconInputParams())
        container.addView(imageInfo)
        container.addView(
            chooseImage,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                58
            ).apply { bottomMargin = 8 }
        )

        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle("NOVA IKONA ZA: ${parent.labelSl}")
            .setView(container)
            .setPositiveButton("SHRANI", null)
            .setNegativeButton("PREKLI\u010cI", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                saveNewChildIcon(
                    parent = parent,
                    labelSl = labelInput.text?.toString().orEmpty(),
                    speechTextSl = speechInput.text?.toString().orEmpty(),
                    itemId = itemIdInput.text?.toString().orEmpty(),
                    iconSource = selectedIconSource,
                    imagePath = selectedImagePath,
                    imageFile = selectedImageFile,
                    existingIds = existingIds,
                    dialog = dialog,
                    refresh = refresh
                )
            }
        }
        dialog.show()
    }

    private fun buildNewIconInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false)
            minLines = 1
            setTextColor(0xFFF4F7FA.toInt())
            setHintTextColor(0xFF9EA8B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFF1E2329.toInt())
        }
    }

    private fun newIconInputParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 10
        }
    }

    private fun saveNewChildIcon(
        parent: AacItem,
        labelSl: String,
        speechTextSl: String,
        itemId: String,
        iconSource: IconSource,
        imagePath: String,
        imageFile: File?,
        existingIds: Set<String>,
        dialog: AlertDialog,
        refresh: () -> Unit
    ) {
        val normalizedLabel = labelSl.trim()
        val normalizedSpeech = speechTextSl.trim()
        val normalizedId = normalizeItemId(itemId)
        when {
            normalizedLabel.isBlank() -> {
                Toast.makeText(this, "Napis pod ikono ne sme biti prazen.", Toast.LENGTH_LONG).show()
                return
            }
            normalizedSpeech.isBlank() -> {
                Toast.makeText(this, "Govor ne sme biti prazen.", Toast.LENGTH_LONG).show()
                return
            }
            normalizedId.isBlank() -> {
                Toast.makeText(this, "itemId ne sme biti prazen.", Toast.LENGTH_LONG).show()
                return
            }
            normalizedId in existingIds -> {
                Toast.makeText(this, "itemId \u017ee obstaja.", Toast.LENGTH_LONG).show()
                return
            }
            normalizedId == parent.id || normalizedId in parent.children -> {
                Toast.makeText(this, "Ta povezava ni dovoljena.", Toast.LENGTH_LONG).show()
                return
            }
            imagePath.isBlank() -> {
                AlertDialog.Builder(this)
                    .setTitle("BREZ SLIKE")
                    .setMessage("Ikona bo prikazana samo kot tekst.")
                    .setPositiveButton("SHRANI") { _, _ ->
                        persistNewChildIcon(parent, normalizedId, normalizedLabel, normalizedSpeech, iconSource, imagePath, dialog, refresh)
                    }
                    .setNegativeButton("PREKLI\u010cI", null)
                    .show()
            }
            imageFile == null || !imageFile.isFile || BitmapFactory.decodeFile(imageFile.absolutePath) == null -> {
                Toast.makeText(this, "Izbrana slika ni ve\u010d berljiva.", Toast.LENGTH_LONG).show()
            }
            else -> {
                persistNewChildIcon(parent, normalizedId, normalizedLabel, normalizedSpeech, iconSource, imagePath, dialog, refresh)
            }
        }
    }

    private fun persistNewChildIcon(
        parent: AacItem,
        itemId: String,
        labelSl: String,
        speechTextSl: String,
        iconSource: IconSource,
        imagePath: String,
        dialog: AlertDialog,
        refresh: () -> Unit
    ) {
        val saved = AacEditorStorage.addNewChildIcon(
            this,
            AacEditorStorage.NewIcon(
                id = itemId,
                parentId = parent.id,
                labelSl = labelSl,
                speechTextSl = speechTextSl,
                iconSource = iconSource,
                imagePath = imagePath
            )
        )
        if (saved) {
            Toast.makeText(this, "Nova podikona je shranjena.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            loadEditorPages()
            refresh()
        } else {
            Toast.makeText(this, "Shranjevanje nove ikone ni uspelo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun suggestNewItemId(labelSl: String, existingIds: Set<String>): String {
        val base = normalizeItemId("custom_${slugify(labelSl)}").ifBlank { "custom_icon" }
        if (base !in existingIds) return base
        for (index in 2..999) {
            val candidate = "${base}_$index"
            if (candidate !in existingIds) return candidate
        }
        return ""
    }

    private fun suggestCopyItemId(sourceId: String, existingIds: Set<String>): String {
        val base = normalizeItemId("${sourceId}_copy").ifBlank { "custom_icon_copy" }
        if (base !in existingIds) return base
        for (index in 2..999) {
            val candidate = "${base}_$index"
            if (candidate !in existingIds) return candidate
        }
        return ""
    }

    private fun normalizeItemId(value: String): String {
        return slugify(value).trim('_')
    }

    private fun slugify(value: String): String {
        val ascii = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return ascii
            .replace("[^a-z0-9]+".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')
    }

    private fun showImageSourceDialogForNewIcon(onSelected: (AacImageGallery.Entry) -> Unit) {
        val sources = AacImageGallery.sources(this)
        val labels = sources.map { source -> source.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("IZBERI VIR SLIKE")
            .setItems(labels) { _, which ->
                showImageGalleryDialogForNewIcon(sources[which], onSelected)
            }
            .setNegativeButton("PREKLI\u010cI", null)
            .show()
    }

    private fun showImageGalleryDialogForNewIcon(
        source: AacImageGallery.Source,
        onSelected: (AacImageGallery.Entry) -> Unit
    ) {
        val entries = AacImageGallery.scan(source)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("IZBERI SLIKO")
                .setMessage("V tej mapi \u0161e ni slik.")
                .setPositiveButton("NAZAJ") { _, _ ->
                    showImageSourceDialogForNewIcon(onSelected)
                }
                .setNegativeButton("PREKLI\u010cI", null)
                .show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(12, 12, 12, 12)
        }
        val title = TextView(this).apply {
            text = "IZBERI SLIKO"
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(4, 0, 4, 12)
        }
        lateinit var galleryDialog: AlertDialog
        val gallery = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AacEditorActivity, 4)
            adapter = AacImageGalleryAdapter(entries) { entry ->
                showImagePreviewDialogForNewIcon(entry) {
                    onSelected(entry)
                    galleryDialog.dismiss()
                }
            }
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        container.addView(title)
        container.addView(
            gallery,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.aac_editor_gallery_height)
            )
        )

        galleryDialog = AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton("PREKLI\u010cI", null)
            .setPositiveButton("NAZAJ") { _, _ ->
                showImageSourceDialogForNewIcon(onSelected)
            }
            .create()
        galleryDialog.show()
    }

    private fun showImagePreviewDialogForNewIcon(
        entry: AacImageGallery.Entry,
        onUse: () -> Unit
    ) {
        val decoded = BitmapFactory.decodeFile(entry.file.absolutePath)
        if (!entry.file.isFile || decoded == null) {
            Toast.makeText(this, "Slika ni berljiva.", Toast.LENGTH_LONG).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(18, 18, 18, 8)
        }
        val preview = ImageView(this).apply {
            setImageBitmap(decoded)
            setBackgroundColor(0xFF263746.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(8, 8, 8, 8)
        }
        val details = TextView(this).apply {
            text = buildString {
                appendLine(entry.file.name)
                appendLine(entry.imagePath)
                append("Vir: ${entry.source.iconSource.name}")
            }
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 14, 0, 0)
        }
        container.addView(
            preview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.aac_editor_preview_height)
            )
        )
        container.addView(details)

        AlertDialog.Builder(this)
            .setTitle("PREDOGLED SLIKE")
            .setView(container)
            .setPositiveButton("UPORABI") { _, _ -> onUse() }
            .setNegativeButton("PREKLI\u010cI", null)
            .show()
    }

    private fun showImageSourceDialog(item: AacItem, editDialog: AlertDialog) {
        val sources = AacImageGallery.sources(this)
        val labels = sources.map { source -> source.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("IZBERI VIR SLIKE")
            .setItems(labels) { _, which ->
                showImageGalleryDialog(item, editDialog, sources[which])
            }
            .setNegativeButton("PREKLI\u010cI", null)
            .show()
    }

    private fun showSimpleImageSourceBrowser(item: AacItem, iconSource: IconSource, refresh: () -> Unit) {
        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: item
        if (currentItem.locked) {
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }

        val source = AacImageGallery.sources(this).firstOrNull { it.iconSource == iconSource }
        if (source == null) {
            Toast.makeText(this, "V tej mapi ni slik.", Toast.LENGTH_SHORT).show()
            return
        }

        var entries = simpleImageEntries(source)

        if (entries.isEmpty()) {
            Toast.makeText(this, "V tej mapi ni slik.", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 8)
        }
        val searchInput = EditText(this).apply {
            hint = "IŠČI SLIKO"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
        }
        val emptyStatus = TextView(this).apply {
            text = ""
            setTextColor(0xFF9AA6B2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 10, 0, 8)
        }
        val listView = ListView(this)
        val filteredEntries = entries.toMutableList()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            filteredEntries.map { entry -> entry.file.name }.toMutableList()
        )
        listView.adapter = adapter

        fun applyFilter(query: String) {
            val normalizedQuery = query.trim().lowercase(Locale.ROOT)
            filteredEntries.clear()
            filteredEntries.addAll(
                if (normalizedQuery.isBlank()) {
                    entries
                } else {
                    entries.filter { entry -> entry.file.name.lowercase(Locale.ROOT).contains(normalizedQuery) }
                }
            )
            adapter.clear()
            adapter.addAll(filteredEntries.map { entry -> entry.file.name })
            adapter.notifyDataSetChanged()
            emptyStatus.text = if (filteredEntries.isEmpty()) "Ni najdenih slik." else ""
        }

        fun refreshEntries() {
            entries = simpleImageEntries(source)
            applyFilter(searchInput.text?.toString().orEmpty())
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        listView.setOnItemClickListener { _, _, position, _ ->
            filteredEntries.getOrNull(position)?.let { entry ->
                showSimpleImageEntryActions(currentItem, entry, refresh, ::refreshEntries)
            }
        }

        container.addView(searchInput)
        container.addView(emptyStatus)
        container.addView(
            listView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.aac_editor_gallery_height)
            )
        )

        AlertDialog.Builder(this)
            .setTitle(simpleImageSourceTitle(iconSource))
            .setView(container)
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun simpleImageEntries(source: AacImageGallery.Source): List<AacImageGallery.Entry> {
        return AacImageGallery.scan(source)
            .filter { entry -> !entry.file.name.startsWith(".") && entry.file.isFile }
            .sortedBy { entry -> entry.file.name.lowercase(Locale.ROOT) }
    }

    private fun showSimpleImageEntryActions(
        item: AacItem,
        entry: AacImageGallery.Entry,
        refreshEditor: () -> Unit,
        refreshBrowser: () -> Unit
    ) {
        val actions = arrayOf("UPORABI SLIKO", "PREIMENUJ SLIKO")
        AlertDialog.Builder(this)
            .setTitle(entry.file.name)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> applySimpleSelectedImage(item, entry, refreshEditor)
                    1 -> showRenameImageDialog(item, entry, refreshEditor, refreshBrowser)
                }
            }
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun showRenameImageDialog(
        item: AacItem,
        entry: AacImageGallery.Entry,
        refreshEditor: () -> Unit,
        refreshBrowser: () -> Unit
    ) {
        if (!canRenameImageSource(entry.source.iconSource)) {
            Toast.makeText(this, "Tega vira ni mogoče preimenovati.", Toast.LENGTH_SHORT).show()
            return
        }
        val parentDir = entry.file.parentFile
        if (parentDir == null || !parentDir.isDirectory || !entry.file.canWrite()) {
            Toast.makeText(this, "Tega vira ni mogoče preimenovati.", Toast.LENGTH_SHORT).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 0)
        }
        val help = TextView(this).apply {
            text = buildString {
                append("Vpišite jasno ime slike, na primer: zana, voda, kava, vozicek.")
                if (isImageUsedByAacItem(entry)) {
                    append("\n\nTa slika je morda že uporabljena. Po preimenovanju jo bo treba ponovno izbrati.")
                }
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 0, 0, 12)
        }
        val input = EditText(this).apply {
            setText(entry.file.nameWithoutExtension)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
        }
        container.addView(help)
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("PREIMENUJ SLIKO")
            .setView(container)
            .setPositiveButton("SHRANI") { _, _ ->
                renameImageFile(item, entry, input.text?.toString().orEmpty(), refreshEditor, refreshBrowser)
            }
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun renameImageFile(
        item: AacItem,
        entry: AacImageGallery.Entry,
        requestedName: String,
        refreshEditor: () -> Unit,
        refreshBrowser: () -> Unit
    ) {
        val parentDir = entry.file.parentFile
        if (parentDir == null || !parentDir.isDirectory) {
            Toast.makeText(this, "Tega vira ni mogoče preimenovati.", Toast.LENGTH_SHORT).show()
            return
        }
        val safeBaseName = safeLibraryImageBaseName(requestedName)
        if (safeBaseName.isBlank()) {
            Toast.makeText(this, "Vpišite jasno ime slike.", Toast.LENGTH_SHORT).show()
            return
        }
        val extension = entry.file.extension.lowercase(Locale.ROOT).ifBlank { "jpg" }
        val targetFile = File(parentDir, "$safeBaseName.$extension")
        if (targetFile.exists() && !targetFile.equals(entry.file)) {
            Toast.makeText(this, "Datoteka s tem imenom že obstaja.", Toast.LENGTH_SHORT).show()
            return
        }
        if (targetFile.equals(entry.file)) {
            Toast.makeText(this, "Slika je preimenovana.", Toast.LENGTH_SHORT).show()
            refreshBrowser()
            return
        }
        if (entry.file.renameTo(targetFile)) {
            val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: item
            if (currentItem.iconSource == entry.source.iconSource && currentItem.imagePath == entry.imagePath) {
                val nextImagePath = renamedImagePath(entry.imagePath, targetFile.name)
                val updated = AacEditorStorage.updateImage(
                    context = this,
                    itemId = currentItem.id,
                    iconSource = currentItem.iconSource,
                    imagePath = nextImagePath
                )
                if (updated) {
                    Toast.makeText(this, "Slika je preimenovana in posodobljena na ikoni.", Toast.LENGTH_SHORT).show()
                    refreshEditor()
                    refreshBrowser()
                    loadEditorPages()
                } else {
                    targetFile.renameTo(entry.file)
                    Toast.makeText(this, "Slike ni bilo mogoče preimenovati.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Slika je preimenovana.", Toast.LENGTH_SHORT).show()
                refreshBrowser()
            }
        } else {
            Toast.makeText(this, "Slike ni bilo mogoče preimenovati.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renamedImagePath(oldImagePath: String, newFileName: String): String {
        val normalizedPath = oldImagePath.replace('\\', '/')
        val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (parentPath.isBlank()) newFileName else "$parentPath/$newFileName"
    }

    private fun canRenameImageSource(iconSource: IconSource): Boolean {
        return iconSource == IconSource.CUSTOM || iconSource == IconSource.PATIENT
    }

    private fun isImageUsedByAacItem(entry: AacImageGallery.Entry): Boolean {
        return AacEditorStorage.loadItems(this).any { item ->
            item.iconSource == entry.source.iconSource && item.imagePath == entry.imagePath
        }
    }

    private fun safeLibraryImageBaseName(rawName: String): String {
        val withoutExtension = rawName.trim().substringBeforeLast('.', rawName.trim())
        return Normalizer.normalize(withoutExtension.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9_-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-')
    }

    private fun applySimpleSelectedImage(item: AacItem, entry: AacImageGallery.Entry, refresh: () -> Unit) {
        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: item
        if (currentItem.locked) {
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!entry.file.isFile) {
            Toast.makeText(this, "V tej mapi ni slik.", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = AacEditorStorage.updateImage(
            context = this,
            itemId = currentItem.id,
            iconSource = entry.source.iconSource,
            imagePath = entry.imagePath
        )
        if (saved) {
            Toast.makeText(this, "Slika je shranjena.", Toast.LENGTH_SHORT).show()
            refresh()
            loadEditorPages()
        } else {
            Toast.makeText(this, "Shranjevanje slike ni uspelo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun simpleImageSourceTitle(iconSource: IconSource): String {
        return when (iconSource) {
            IconSource.CUSTOM -> "IZBERI IZ MOJIH SLIK"
            IconSource.PATIENT -> "IZBERI IZ OSEB"
            IconSource.SOCA -> "IZBERI IZ SOČA"
            IconSource.ARASAAC -> "IZBERI IZ ARASAAC"
            IconSource.SYSTEM -> "SISTEMSKE IKONE"
        }
    }

    private fun openGalleryImagePicker(item: AacItem, refresh: () -> Unit) {
        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: item
        if (currentItem.locked) {
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }
        pendingGalleryItemId = currentItem.id
        pendingGalleryRefresh = refresh
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            galleryImagePicker.launch(intent)
        } catch (_: Exception) {
            pendingGalleryItemId = null
            pendingGalleryRefresh = null
            Toast.makeText(this, "Slike ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryImageResult(resultCode: Int, uri: Uri?) {
        val itemId = pendingGalleryItemId
        val refresh = pendingGalleryRefresh
        pendingGalleryItemId = null
        pendingGalleryRefresh = null
        if (resultCode != Activity.RESULT_OK || uri == null || itemId.isNullOrBlank()) {
            return
        }

        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == itemId }
        if (currentItem?.locked == true) {
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }

        val savedImagePath = copyGalleryImageToCustomIcons(uri, itemId)
        if (savedImagePath.isNullOrBlank()) {
            Toast.makeText(this, "Slike ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
            return
        }

        val saved = AacEditorStorage.updateImage(
            context = this,
            itemId = itemId,
            iconSource = IconSource.CUSTOM,
            imagePath = savedImagePath
        )
        if (saved) {
            Toast.makeText(this, "Slika je shranjena.", Toast.LENGTH_SHORT).show()
            refresh?.invoke()
            loadEditorPages()
        } else {
            Toast.makeText(this, "Slike ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCameraCapture(item: AacItem, refresh: () -> Unit) {
        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: item
        if (currentItem.locked) {
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }

        val targetFile = createCameraImageFile(currentItem.id)
        if (targetFile == null) {
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
            return
        }

        val outputUri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", targetFile)
        } catch (_: Exception) {
            targetFile.delete()
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
            return
        }

        pendingCameraItemId = currentItem.id
        pendingCameraFile = targetFile
        pendingCameraRefresh = refresh
        try {
            cameraCaptureLauncher.launch(outputUri)
        } catch (_: Exception) {
            clearPendingCameraCapture(deleteFile = true)
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCameraCaptureResult(success: Boolean) {
        val itemId = pendingCameraItemId
        val targetFile = pendingCameraFile
        val refresh = pendingCameraRefresh
        pendingCameraItemId = null
        pendingCameraFile = null
        pendingCameraRefresh = null

        if (!success || itemId.isNullOrBlank() || targetFile == null || !targetFile.exists() || targetFile.length() <= 0L) {
            targetFile?.delete()
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == itemId }
        if (currentItem?.locked == true) {
            targetFile.delete()
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }

        val saved = AacEditorStorage.updateImage(
            context = this,
            itemId = itemId,
            iconSource = IconSource.CUSTOM,
            imagePath = targetFile.name
        )
        if (saved) {
            Toast.makeText(this, "Fotografija je shranjena.", Toast.LENGTH_SHORT).show()
            refresh?.invoke()
            loadEditorPages()
        } else {
            targetFile.delete()
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeImageFromItem(item: AacItem, refresh: () -> Unit) {
        val currentItem = AacEditorStorage.loadItems(this).firstOrNull { it.id == item.id } ?: item
        if (currentItem.locked) {
            Toast.makeText(this, "Zaklenjene ikone ni mogoče spreminjati.", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = AacEditorStorage.clearImage(this, currentItem.id)
        if (saved) {
            Toast.makeText(this, "Slika je odstranjena iz ikone.", Toast.LENGTH_SHORT).show()
            refresh()
            loadEditorPages()
        } else {
            Toast.makeText(this, "Slike ni bilo mogoče odstraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCameraImageFile(itemId: String): File? {
        val customDir = AacStoragePaths.getIconsCustomDir(this) ?: return null
        if (!customDir.exists() && !customDir.mkdirs()) return null
        val safeItemId = safeImageItemId(itemId).ifBlank { "aac_icon" }
        return uniqueCustomImageFile(customDir, "custom_${safeItemId}_camera", "jpg")
    }

    private fun clearPendingCameraCapture(deleteFile: Boolean) {
        if (deleteFile) {
            pendingCameraFile?.delete()
        }
        pendingCameraItemId = null
        pendingCameraFile = null
        pendingCameraRefresh = null
    }

    private fun copyGalleryImageToCustomIcons(uri: Uri, itemId: String): String? {
        val customDir = AacStoragePaths.getIconsCustomDir(this) ?: return null
        if (!customDir.exists() && !customDir.mkdirs()) return null
        val extension = galleryImageExtension(uri)
        val safeItemId = safeImageItemId(itemId).ifBlank { "aac_icon" }
        val targetFile = uniqueCustomImageFile(customDir, "custom_$safeItemId", extension)
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                return null
            }
            targetFile.name
        } catch (_: Exception) {
            targetFile.delete()
            null
        }
    }

    private fun galleryImageExtension(uri: Uri): String {
        val mimeType = contentResolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        val lastPath = uri.lastPathSegment?.lowercase(Locale.ROOT).orEmpty()
        return when {
            mimeType.contains("png") || lastPath.endsWith(".png") -> "png"
            mimeType.contains("webp") || lastPath.endsWith(".webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun uniqueCustomImageFile(directory: File, baseName: String, extension: String): File {
        var candidate = File(directory, "$baseName.$extension")
        var index = 2
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_$index.$extension")
            index += 1
        }
        return candidate
    }

    private fun safeImageItemId(itemId: String): String {
        return Normalizer.normalize(itemId.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    private fun showImageGalleryDialog(
        item: AacItem,
        editDialog: AlertDialog,
        source: AacImageGallery.Source
    ) {
        val entries = AacImageGallery.scan(source)
        if (entries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("IZBERI SLIKO ZA: ${item.labelSl}")
                .setMessage("V tej mapi \u0161e ni slik.")
                .setPositiveButton("NAZAJ") { _, _ ->
                    showImageSourceDialog(item, editDialog)
                }
                .setNegativeButton("PREKLI\u010cI", null)
                .show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(12, 12, 12, 12)
        }
        val title = TextView(this).apply {
            text = "IZBERI SLIKO ZA: ${item.labelSl}"
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(4, 0, 4, 12)
        }
        val gallery = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@AacEditorActivity, 4)
            adapter = AacImageGalleryAdapter(entries) { entry ->
                showImagePreviewDialog(item, editDialog, entry)
            }
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        container.addView(title)
        container.addView(
            gallery,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.aac_editor_gallery_height)
            )
        )

        AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton("PREKLI\u010cI", null)
            .setPositiveButton("NAZAJ") { _, _ ->
                showImageSourceDialog(item, editDialog)
            }
            .show()
    }

    private fun showImagePreviewDialog(
        item: AacItem,
        editDialog: AlertDialog,
        entry: AacImageGallery.Entry
    ) {
        val decoded = BitmapFactory.decodeFile(entry.file.absolutePath)
        if (!entry.file.isFile || decoded == null) {
            Toast.makeText(this, "Slika ni berljiva.", Toast.LENGTH_LONG).show()
            return
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(18, 18, 18, 8)
        }
        val preview = ImageView(this).apply {
            setImageBitmap(decoded)
            setBackgroundColor(0xFF263746.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(8, 8, 8, 8)
        }
        val details = TextView(this).apply {
            text = buildString {
                appendLine(entry.file.name)
                appendLine(entry.imagePath)
                append("Vir: ${entry.source.iconSource.name}")
            }
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, 14, 0, 0)
        }
        container.addView(
            preview,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(R.dimen.aac_editor_preview_height)
            )
        )
        container.addView(details)

        AlertDialog.Builder(this)
            .setTitle("PREDOGLED SLIKE")
            .setView(container)
            .setPositiveButton("UPORABI") { _, _ ->
                applySelectedImage(item, editDialog, entry)
            }
            .setNegativeButton("PREKLI\u010cI", null)
            .show()
    }

    private fun applySelectedImage(
        item: AacItem,
        editDialog: AlertDialog,
        entry: AacImageGallery.Entry
    ) {
        if (!entry.file.isFile || BitmapFactory.decodeFile(entry.file.absolutePath) == null) {
            Toast.makeText(this, "Slika ni ve\u010d na voljo.", Toast.LENGTH_LONG).show()
            return
        }
        val saved = AacEditorStorage.updateImage(
            context = this,
            itemId = item.id,
            iconSource = entry.source.iconSource,
            imagePath = entry.imagePath
        )
        if (saved) {
            Toast.makeText(this, "Slika shranjena.", Toast.LENGTH_SHORT).show()
            editDialog.dismiss()
            loadEditorPages()
        } else {
            Toast.makeText(this, "Shranjevanje slike ni uspelo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTextEditDialog(title: String, currentValue: String, onSave: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(currentValue)
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("SHRANI") { _, _ ->
                onSave(input.text.toString())
            }
            .setNegativeButton("PREKLIČI", null)
            .show()
    }

    private fun saveTextChange(saved: Boolean, dialog: AlertDialog) {
        if (saved) {
            Toast.makeText(this, "Shranjeno.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            loadEditorPages()
        } else {
            Toast.makeText(this, "Shranjevanje ni uspelo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun bindPreview(imageView: ImageView, item: AacItem) {
        val imageFile = resolveImageFile(item)
        if (imageFile == null) {
            imageView.setImageDrawable(null)
            return
        }
        imageView.setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath))
    }

    private fun imageStatusText(item: AacItem): String {
        val sourceLabel = imageSourceDisplayName(item.iconSource)
        val imagePath = item.imagePath.trim()
        if (imagePath.isEmpty()) {
            return "Trenutna slika: ni izbrana"
        }
        val imageFile = resolveImageFile(item)
        val fileStatus = if (imageFile != null) "datoteka najdena" else "datoteka manjka"
        return "Trenutni vir: $sourceLabel\n$imagePath\n$fileStatus"
    }

    private fun imageSourceDisplayName(iconSource: IconSource): String {
        return when (iconSource) {
            IconSource.SYSTEM -> "SISTEMSKE IKONE (SYSTEM)"
            IconSource.CUSTOM -> "MOJE SLIKE (CUSTOM)"
            IconSource.PATIENT -> "OSEBE / PACIENT (PATIENT)"
            IconSource.SOCA -> "SOČA (SOCA)"
            IconSource.ARASAAC -> "ARASAAC"
        }
    }

    private fun resolveImageFile(item: AacItem): File? {
        return AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
            ?.takeIf { it.exists() && it.isFile }
    }

    private class AacEditorAdapter(
        private val onItemClick: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacEditorAdapter.ViewHolder>() {
        private var items: List<AacItem> = emptyList()
        private var hiddenIds: Set<String> = emptySet()

        fun submitItems(nextItems: List<AacItem>, nextHiddenIds: Set<String> = emptySet()) {
            items = nextItems
            hiddenIds = nextHiddenIds
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_aac_tile, parent, false)
            return ViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], items[position].id in hiddenIds)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(
            itemView: View,
            private val onItemClick: (AacItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val image: ImageView = itemView.findViewById(R.id.imgAacTile)
            private val label: TextView = itemView.findViewById(R.id.txtAacTileLabel)

            fun bind(item: AacItem, hidden: Boolean) {
                itemView.setBackgroundColor(0xFF263746.toInt())
                val resolvedLabel = AacLocalizedTextResolver.resolveLabel(item, "sl")
                label.text = if (hidden) "$resolvedLabel\nSKRITO" else resolvedLabel
                label.gravity = Gravity.CENTER
                label.visibility = View.VISIBLE
                image.setImageDrawable(null)

                val imageFile = AacStoragePaths.resolveIconFile(itemView.context, item.imagePath, item.iconSource)
                    ?.takeIf { it.exists() && it.isFile }
                if (imageFile != null) {
                    image.alpha = 1.0f
                    image.setImageBitmap(BitmapFactory.decodeFile(imageFile.absolutePath))
                } else {
                    image.alpha = 0.0f
                }

                itemView.setOnClickListener { onItemClick(item) }
            }
        }
    }

    private class AacImageGalleryAdapter(
        private val entries: List<AacImageGallery.Entry>,
        private val onEntryClick: (AacImageGallery.Entry) -> Unit
    ) : RecyclerView.Adapter<AacImageGalleryAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
                setBackgroundColor(0xFF263746.toInt())
                isClickable = true
                isFocusable = true
            }
            val image = ImageView(context).apply {
                id = View.generateViewId()
                setBackgroundColor(0xFF16202B.toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setPadding(4, 4, 4, 4)
            }
            val label = TextView(context).apply {
                id = View.generateViewId()
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2
                setPadding(2, 8, 2, 0)
            }
            container.addView(
                image,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.resources.getDimensionPixelSize(R.dimen.aac_editor_gallery_thumb_size)
                )
            )
            container.addView(label, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            return ViewHolder(container, image, label, onEntryClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(entries[position])
        }

        override fun getItemCount(): Int = entries.size

        class ViewHolder(
            itemView: View,
            private val image: ImageView,
            private val label: TextView,
            private val onEntryClick: (AacImageGallery.Entry) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            fun bind(entry: AacImageGallery.Entry) {
                label.text = entry.file.name
                val bitmap = BitmapFactory.decodeFile(entry.file.absolutePath)
                if (bitmap != null) {
                    image.alpha = 1.0f
                    image.setImageBitmap(bitmap)
                } else {
                    image.alpha = 0.0f
                    image.setImageDrawable(null)
                }
                itemView.setOnClickListener { onEntryClick(entry) }
            }
        }
    }

    private class AacItemPickerAdapter(
        private var items: List<AacItem>,
        private val onItemClick: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacItemPickerAdapter.ViewHolder>() {
        fun submitItems(nextItems: List<AacItem>) {
            items = nextItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
                setBackgroundColor(0xFF263746.toInt())
                isClickable = true
                isFocusable = true
            }
            val image = ImageView(context).apply {
                setBackgroundColor(0xFF16202B.toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                setPadding(4, 4, 4, 4)
            }
            val label = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2
                setPadding(2, 8, 2, 0)
            }
            container.addView(
                image,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.resources.getDimensionPixelSize(R.dimen.aac_editor_gallery_thumb_size)
                )
            )
            container.addView(label, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            return ViewHolder(container, image, label, onItemClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(
            itemView: View,
            private val image: ImageView,
            private val label: TextView,
            private val onItemClick: (AacItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            fun bind(item: AacItem) {
                label.text = "${item.labelSl}\n${item.id}"
                val imageFile = AacStoragePaths.resolveIconFile(itemView.context, item.imagePath, item.iconSource)
                    ?.takeIf { it.exists() && it.isFile }
                val bitmap = imageFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
                if (bitmap != null) {
                    image.alpha = 1.0f
                    image.setImageBitmap(bitmap)
                } else {
                    image.alpha = 0.0f
                    image.setImageDrawable(null)
                }
                itemView.setOnClickListener { onItemClick(item) }
            }
        }
    }
}
