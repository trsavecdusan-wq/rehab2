package com.rehab2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.radio.RadioBrowserClient
import com.rehab2.radio.RadioBrowserClient.SearchMode
import com.rehab2.radio.RadioSearchResult

class AddRadioStationActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout
    private val radioBrowserClient = RadioBrowserClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_radio_station)

        searchInput = findViewById(R.id.editSearchQuery)
        statusText = findViewById(R.id.txtSearchStatus)
        resultsContainer = findViewById(R.id.resultsContainer)

        findViewById<Button>(R.id.btnBackAddRadioStation).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSearchByCountry).setOnClickListener {
            performSearch(SearchMode.COUNTRY)
        }

        findViewById<Button>(R.id.btnSearchByGenre).setOnClickListener {
            performSearch(SearchMode.GENRE)
        }

        findViewById<Button>(R.id.btnSearchByName).setOnClickListener {
            performSearch(SearchMode.NAME)
        }
    }

    private fun performSearch(mode: SearchMode) {
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(this, "Vpi\u0161i iskalni pojem", Toast.LENGTH_SHORT).show()
            return
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
}