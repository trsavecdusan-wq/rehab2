package com.rehab2

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rehab2.aac.AacContentBootstrap
import com.rehab2.aac.AacEditorStorage
import com.rehab2.aac.AacImageGallery
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLocalizedTextResolver
import com.rehab2.aac.AacStarterContentV1
import com.rehab2.aac.AacStoragePaths
import java.io.File

class AacEditorActivity : AppCompatActivity() {
    private lateinit var pageButtons: LinearLayout
    private lateinit var pageTitle: TextView
    private lateinit var recycler: RecyclerView
    private var pages: List<AacEditorStorage.EditorPage> = emptyList()
    private var selectedPageIndex = 0
    private val adapter = AacEditorAdapter(::showEditIconDialog)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aac_editor)

        pageButtons = findViewById(R.id.aacEditorPageButtons)
        pageTitle = findViewById(R.id.txtAacEditorPageTitle)
        recycler = findViewById(R.id.recyclerAacEditorTiles)
        recycler.layoutManager = GridLayoutManager(this, 5)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnAacEditorBack).setOnClickListener { finish() }

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
        adapter.submitItems(page.items)
    }

    private fun showEditIconDialog(item: AacItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_aac_editor_icon, null)
        val preview = dialogView.findViewById<ImageView>(R.id.imgAacEditorPreview)
        val itemId = dialogView.findViewById<TextView>(R.id.txtAacEditorItemId)
        val label = dialogView.findViewById<TextView>(R.id.txtAacEditorLabel)
        val speech = dialogView.findViewById<TextView>(R.id.txtAacEditorSpeech)
        val changeImage = dialogView.findViewById<Button>(R.id.btnAacEditorChangeImage)
        val editLabel = dialogView.findViewById<Button>(R.id.btnAacEditorEditLabel)
        val editSpeech = dialogView.findViewById<Button>(R.id.btnAacEditorEditSpeech)

        bindPreview(preview, item)
        itemId.text = "itemId: ${item.id}"
        label.text = "labelSl: ${item.labelSl}"
        speech.text = "speechTextSl: ${item.resolvedSpeechText}"

        val dialog = AlertDialog.Builder(this)
            .setTitle("UREDI IKONO")
            .setView(dialogView)
            .setNegativeButton("ZAPRI", null)
            .create()

        changeImage.setOnClickListener {
            showImageSourceDialog(item, dialog)
        }
        editLabel.setOnClickListener {
            showTextEditDialog(
                title = "UREDI TEKST",
                currentValue = item.labelSl,
                onSave = { value ->
                    saveTextChange(
                        saved = AacEditorStorage.updateLabelSl(this, item.id, value),
                        dialog = dialog
                    )
                }
            )
        }
        editSpeech.setOnClickListener {
            showTextEditDialog(
                title = "UREDI GOVOR",
                currentValue = item.resolvedSpeechText,
                onSave = { value ->
                    saveTextChange(
                        saved = AacEditorStorage.updateSpeechTextSl(this, item.id, value),
                        dialog = dialog
                    )
                }
            )
        }

        dialog.show()
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

    private fun resolveImageFile(item: AacItem): File? {
        return AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
            ?.takeIf { it.exists() && it.isFile }
    }

    private class AacEditorAdapter(
        private val onItemClick: (AacItem) -> Unit
    ) : RecyclerView.Adapter<AacEditorAdapter.ViewHolder>() {
        private var items: List<AacItem> = emptyList()

        fun submitItems(nextItems: List<AacItem>) {
            items = nextItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_aac_tile, parent, false)
            return ViewHolder(view, onItemClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(
            itemView: View,
            private val onItemClick: (AacItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val image: ImageView = itemView.findViewById(R.id.imgAacTile)
            private val label: TextView = itemView.findViewById(R.id.txtAacTileLabel)

            fun bind(item: AacItem) {
                itemView.setBackgroundColor(0xFF263746.toInt())
                label.text = AacLocalizedTextResolver.resolveLabel(item, "sl")
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
}
