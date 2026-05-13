package com.rehab2

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.radio.RadioBrowserClient
import com.rehab2.radio.RadioBrowserClient.SearchMode
import com.rehab2.radio.RadioSearchPresetsStore
import com.rehab2.radio.RadioSearchResult

class AddRadioStationActivity : AppCompatActivity() {
    companion object {
        private val GENRE_PRESETS = listOf(
            "pop",
            "rock",
            "news",
            "folk",
            "classical",
            "dance",
            "talk",
            "children",
            "ukrainian",
            "slovenian"
        )
    }

    private lateinit var searchInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var scrollCountryPresets: HorizontalScrollView
    private lateinit var scrollGenrePresets: HorizontalScrollView
    private lateinit var countryPresetsContainer: LinearLayout
    private lateinit var genrePresetsContainer: LinearLayout
    private val radioBrowserClient = RadioBrowserClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var presetsStore: RadioSearchPresetsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_radio_station)

        presetsStore = RadioSearchPresetsStore(this)
        searchInput = findViewById(R.id.editSearchQuery)
        statusText = findViewById(R.id.txtSearchStatus)
        resultsContainer = findViewById(R.id.resultsContainer)
        scrollCountryPresets = findViewById(R.id.scrollCountryPresets)
        scrollGenrePresets = findViewById(R.id.scrollGenrePresets)
        countryPresetsContainer = findViewById(R.id.countryPresetsContainer)
        genrePresetsContainer = findViewById(R.id.genrePresetsContainer)

        findViewById<Button>(R.id.btnBackAddRadioStation).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnClearSearch).setOnClickListener {
            clearSearchUi()
        }

        findViewById<Button>(R.id.btnSearchByCountry).setOnClickListener {
            handleCountryAction()
        }

        findViewById<Button>(R.id.btnAddCountryPreset).setOnClickListener {
            showCountryPresetMenu()
            searchInput.requestFocus()
            showKeyboard()
        }

        findViewById<Button>(R.id.btnSearchByGenre).setOnClickListener {
            handleGenreAction()
        }

        findViewById<Button>(R.id.btnSearchByName).setOnClickListener {
            hidePresetMenus()
            prepareForSearch()
            performSearch(SearchMode.NAME)
        }

        findViewById<Button>(R.id.btnLocalMusic).setOnClickListener {
            startActivity(Intent(this, LocalMusicActivity::class.java))
        }

        renderCountryPresets()
        renderGenrePresets()
    }

    private fun performSearch(mode: SearchMode) {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Vpi\u0161i iskalni pojem", Toast.LENGTH_SHORT).show()
            return
        }

        hidePresetMenus()

        if (mode == SearchMode.COUNTRY) {
            presetsStore.addCountryIfMissing(query)
            renderCountryPresets()
        }

        statusText.text = "Nalagam..."
        resultsContainer.removeAllViews()

        Thread {
            try {
                val results = radioBrowserClient.search(mode, query)
                mainHandler.post {
                    if (results.isEmpty()) {
                        statusText.text = "Ni rezultatov"
                    } else {
                        statusText.text = "Najdenih postaj: ${results.size}"
                        renderResults(results)
                    }
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Napaka pri iskanju postaj"
                mainHandler.post {
                    statusText.text = message
                    resultsContainer.removeAllViews()
                }
            }
        }.start()
    }

    private fun renderResults(results: List<RadioSearchResult>) {
        resultsContainer.removeAllViews()
        results.forEach { result ->
            resultsContainer.addView(createResultRow(result))
        }
    }

    private fun createResultRow(result: RadioSearchResult): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E2329.toInt())
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(12)
            layoutParams = params
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (result.streamUrl.isBlank()) {
                    Toast.makeText(context, "Postaja nima veljavnega URL", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(context, RadioPreviewActivity::class.java).apply {
                        putExtra(RadioPreviewActivity.EXTRA_STATION_UUID, result.stationUuid)
                        putExtra(RadioPreviewActivity.EXTRA_STATION_NAME, result.name)
                        putExtra(RadioPreviewActivity.EXTRA_STREAM_URL, result.streamUrl)
                        putExtra(RadioPreviewActivity.EXTRA_COUNTRY, result.country)
                        putExtra(RadioPreviewActivity.EXTRA_TAGS, result.tags)
                        putExtra(RadioPreviewActivity.EXTRA_CODEC, result.codec ?: "")
                        putExtra(RadioPreviewActivity.EXTRA_BITRATE, result.bitrate?.toString() ?: "")
                        putExtra(RadioPreviewActivity.EXTRA_FAVICON_URL, result.faviconUrl ?: "")
                    }
                    startActivity(intent)
                }
            }
        }

        row.addView(createResultText(result.name.ifBlank { "Brez imena" }, 20f, true))
        row.addView(createResultText(result.country.ifBlank { "Neznana dr\u017Eava" }, 15f, false))
        row.addView(createResultText(result.tags.ifBlank { "Brez oznak" }, 14f, false))

        val extraLine = buildString {
            if (!result.codec.isNullOrBlank()) append(result.codec)
            if (result.bitrate != null && result.bitrate > 0) {
                if (isNotEmpty()) append(" \u2022 ")
                append(result.bitrate).append(" kbps")
            }
            if (isEmpty()) append(result.streamUrl)
        }
        row.addView(createResultText(extraLine, 13f, false))

        return row
    }

    private fun createResultText(text: String, sizeSp: Float, bold: Boolean): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            this.text = text
            textSize = sizeSp
            setTextColor(0xFFF4F7FA.toInt())
            gravity = Gravity.START
            if (bold) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun renderCountryPresets() {
        renderPresetButtons(
            container = countryPresetsContainer,
            values = presetsStore.loadCountries()
        ) { country ->
            runPresetSearch(country, SearchMode.COUNTRY)
        }
    }

    private fun renderGenrePresets() {
        renderPresetButtons(
            container = genrePresetsContainer,
            values = GENRE_PRESETS
        ) { genre ->
            runPresetSearch(genre, SearchMode.GENRE)
        }
    }

    private fun renderPresetButtons(
        container: LinearLayout,
        values: List<String>,
        onClick: (String) -> Unit
    ) {
        container.removeAllViews()
        values.forEachIndexed { index, value ->
            val button = Button(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(48)
                ).also { params ->
                    if (index < values.lastIndex) {
                        params.marginEnd = dp(8)
                    }
                }
                text = value
                textSize = 16f
                isAllCaps = false
                gravity = Gravity.CENTER
                setTextColor(0xFFF4F7FA.toInt())
                setPadding(dp(16), 0, dp(16), 0)
                backgroundTintList = ColorStateList.valueOf(0xFF3A3F45.toInt())
                setOnClickListener { onClick(value) }
            }
            container.addView(button)
        }
    }

    private fun runPresetSearch(value: String, mode: SearchMode) {
        searchInput.setText(value)
        searchInput.setSelection(value.length)
        prepareForSearch()
        performSearch(mode)
    }

    private fun handleCountryAction() {
        if (searchInput.text.toString().trim().isEmpty()) {
            showCountryPresetMenu()
        } else {
            hidePresetMenus()
            prepareForSearch()
            performSearch(SearchMode.COUNTRY)
        }
    }

    private fun handleGenreAction() {
        if (searchInput.text.toString().trim().isEmpty()) {
            showGenrePresetMenu()
        } else {
            hidePresetMenus()
            prepareForSearch()
            performSearch(SearchMode.GENRE)
        }
    }

    private fun showCountryPresetMenu() {
        hideKeyboard()
        searchInput.clearFocus()
        renderCountryPresets()
        scrollCountryPresets.visibility = View.VISIBLE
        findViewById<Button>(R.id.btnAddCountryPreset).visibility = View.VISIBLE
        scrollGenrePresets.visibility = View.GONE
    }

    private fun showGenrePresetMenu() {
        hideKeyboard()
        searchInput.clearFocus()
        renderGenrePresets()
        scrollGenrePresets.visibility = View.VISIBLE
        scrollCountryPresets.visibility = View.GONE
        findViewById<Button>(R.id.btnAddCountryPreset).visibility = View.GONE
    }

    private fun hidePresetMenus() {
        scrollCountryPresets.visibility = View.GONE
        scrollGenrePresets.visibility = View.GONE
        findViewById<Button>(R.id.btnAddCountryPreset).visibility = View.GONE
    }

    private fun clearSearchUi() {
        hideKeyboard()
        searchInput.clearFocus()
        searchInput.setText("")
        statusText.text = ""
        resultsContainer.removeAllViews()
        hidePresetMenus()
    }

    private fun prepareForSearch() {
        hideKeyboard()
        searchInput.clearFocus()
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val tokenView: View = currentFocus ?: searchInput
        inputMethodManager?.hideSoftInputFromWindow(tokenView.windowToken, 0)
    }

    private fun showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        searchInput.inputType = InputType.TYPE_CLASS_TEXT
        inputMethodManager?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }
}
