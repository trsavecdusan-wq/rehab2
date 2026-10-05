package com.rehab2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.AacEditorStorage
import com.rehab2.aac.AacStarterContentV1
import com.rehab2.aac.AacStoragePaths
import org.json.JSONArray
import org.json.JSONObject
import com.rehab2.aac.ai.*
import java.util.concurrent.Executors

class AdminSuggestionsActivity : AppCompatActivity() {
    companion object {
        private var grantUntil = 0L
        /** Called only by SettingsActivity after its existing admin PIN check. */
        @Synchronized fun openAfterPin(context: Context) {
            grantUntil = SystemClock.elapsedRealtime() + 10000L
            context.startActivity(Intent(context, AdminSuggestionsActivity::class.java))
        }
        @Synchronized private fun consumeGrant(): Boolean {
            val allowed = grantUntil > SystemClock.elapsedRealtime()
            grantUntil = 0L
            return allowed
        }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var cards: LinearLayout
    private lateinit var repository: AacSuggestionRepository
    private var filter: SuggestionStatus? = null
    private var page = 0
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No exported entry, intent bypass or persisted authorization. Re-entry requires the existing PIN path.
        if (!consumeGrant()) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 20, 20, 20) }
        root.addView(TextView(this).apply { text = "AI Foundation — admin pregled"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Odobritev shrani samo status. Predlog se ne doda v pacientkin AAC." })
        root.addView(Switch(this).apply {
            text = "Usage observation (samo lokalni AAC dogodki)"
            isChecked = AacObservation.enabled
            setOnCheckedChangeListener { _, value -> AacObservation.setEnabled(this@AdminSuggestionsActivity, value) }
        })
        val statuses = listOf(null, SuggestionStatus.ADMIN_REVIEW, SuggestionStatus.APPROVED, SuggestionStatus.REJECTED, SuggestionStatus.MERGED_WITH_EXISTING)
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@AdminSuggestionsActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("ALL", "ADMIN_REVIEW", "APPROVED", "REJECTED", "MERGED"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    filter = statuses[position]; page = 0; refresh()
                }
            }
        })
        cards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(cards) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(LinearLayout(this).apply {
            addView(Button(this@AdminSuggestionsActivity).apply { text = "PREJŠNJA"; setOnClickListener { page = (page - 1).coerceAtLeast(0); refresh() } })
            addView(Button(this@AdminSuggestionsActivity).apply { text = "NASLEDNJA"; setOnClickListener { page++; refresh() } })
        })
        root.addView(Button(this).apply { text = "NAZAJ"; setOnClickListener { finish() } })
        setContentView(root)
        val app = applicationContext
        repository = AacSuggestionRepository(AacObservation.directory(app)) {
            // Entire catalog, not profile-filtered HOME or currently visible page.
            // Fail closed for review if a local catalog is corrupt: do not approve against a silent fallback.
            val file = AacStoragePaths.getAacItemsFile(app)
            val expectedLocalCount = file?.takeIf { it.exists() }?.let {
                val raw = it.readText().trim()
                val rows = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw).getJSONArray("items")
                require(rows.length() > 0) { "Lokalni AAC katalog je prazen" }
                repeat(rows.length()) { index -> require(rows.getJSONObject(index).getString("id").isNotBlank()) }
                rows.length()
            }
            val local = AacEditorStorage.loadItems(app)
            require(expectedLocalCount == null || local.size == expectedLocalCount) { "Lokalni AAC katalog ni v celoti berljiv" }
            (AacStarterContentV1.items() + local).associateBy { it.id }.values.toList()
        }
        refresh()
    }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }

    private fun refresh() {
        if (!::repository.isInitialized || !::cards.isInitialized) return
        val currentGeneration = ++generation
        val selectedFilter = filter; val requestedPage = page
        worker.execute {
            runCatching {
                val all = repository.listAll().filter { selectedFilter == null || it.status == selectedFilter }.sortedByDescending { it.updatedAt }
                val lastPage = ((all.size - 1).coerceAtLeast(0) / 20)
                val selectedPage = requestedPage.coerceIn(0, lastPage)
                val rows = all.drop(selectedPage * 20).take(20).map {
                    Triple(it, repository.duplicate(it), TemporaryIconRenderer.loadCategoryImage(applicationContext, it.temporaryCategory))
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed || currentGeneration != generation) return@runOnUiThread
                    page = selectedPage; cards.removeAllViews()
                    cards.addView(TextView(this).apply { text = "${all.size} predlogov · stran ${page + 1}/${lastPage + 1}" })
                    rows.forEach { (s, duplicate, image) ->
                        cards.addView(TemporaryIconRenderer.create(this, s, image))
                        cards.addView(TextView(this).apply {
                            text = "SL: ${s.labelSl}\nUK: ${s.labelUk}\nVir: ${s.sourceType} · ${s.sourceContext}\nConfidence: ${s.confidence}\nParent: ${s.suggestedParentId.orEmpty()}\nDuplicate: ${duplicate.kind} ${duplicate.itemIds.joinToString()}\nStatus: ${s.status}"
                        })
                        fun action(label: String, run: () -> Unit) { cards.addView(Button(this).apply { text = label; setOnClickListener { run() } }) }
                        action("UREDI") { edit(s) }
                        action("ODOBRI") {
                            if (duplicate.kind == DuplicateKind.POSSIBLE_DUPLICATE) AlertDialog.Builder(this)
                                .setMessage("Možen duplikat: ${duplicate.itemIds.joinToString()}. Odobri samo vsebino predloga?")
                                .setPositiveButton("ODOBRI") { _, _ -> mutate { repository.approve(s.id, acknowledgePossibleDuplicate = true) } }.setNegativeButton("PREKLIČI", null).show()
                            else mutate { repository.approve(s.id) }
                        }
                        action("ZAVRNI") { mutate { repository.reject(s.id) } }
                        action("ZDRUŽI Z OBSTOJEČIM") {
                            val input = EditText(this).apply { hint = "Obstoječi AAC ID"; setText(duplicate.itemIds.firstOrNull().orEmpty()) }
                            AlertDialog.Builder(this).setTitle("Združi predlog").setView(input)
                                .setPositiveButton("ZDRUŽI") { _, _ -> val id = input.text.toString().trim(); mutate { repository.mergeWithExisting(s.id, id) } }
                                .setNegativeButton("PREKLIČI", null).show()
                        }
                    }
                }
            }.onFailure { showError(it) }
        }
    }
    private fun mutate(action: () -> Unit) {
        worker.execute { runCatching { action() }.onSuccess { runOnUiThread { if (!isFinishing && !isDestroyed) refresh() } }.onFailure { showError(it) } }
    }
    private fun showError(error: Throwable) = runOnUiThread {
        if (!isFinishing && !isDestroyed) Toast.makeText(this, "Predlog ni shranjen/prebran: ${error.message}", Toast.LENGTH_LONG).show()
    }
    private fun edit(s: AacSuggestion) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun field(label: String, value: String) = EditText(this).apply { hint = label; setText(value); form.addView(this) }
        val meaning = field("Normalizirani pomen", s.normalizedMeaning)
        val sl = field("SL label", s.labelSl); val uk = field("UK label", s.labelUk)
        val speechSl = field("SL govor", s.speechSl); val speechUk = field("UK govor", s.speechUk)
        val parent = field("Parent ID", s.suggestedParentId.orEmpty())
        val tags = field("Semantic tags (vejica)", s.semanticTags.joinToString(","))
        val note = field("Admin opomba", s.adminNote)
        val category = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdminSuggestionsActivity, android.R.layout.simple_spinner_dropdown_item, TemporaryCategory.values().map { it.name })
            setSelection(s.temporaryCategory.ordinal); form.addView(this)
        }
        AlertDialog.Builder(this).setTitle("Uredi predlog").setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("SHRANI") { _, _ ->
                val updated = s.copy(normalizedMeaning = meaning.text.toString().trim(), labelSl = sl.text.toString().trim(), labelUk = uk.text.toString().trim(),
                    speechSl = speechSl.text.toString(), speechUk = speechUk.text.toString(), suggestedParentId = parent.text.toString().trim().ifBlank { null },
                    semanticTags = tags.text.toString().split(',').map { it.trim() }.filter { it.isNotBlank() }, adminNote = note.text.toString(),
                    temporaryCategory = TemporaryCategory.values()[category.selectedItemPosition])
                mutate { repository.update(updated) }
            }.setNegativeButton("PREKLIČI", null).show()
    }
}
