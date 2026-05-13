package com.rehab2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.radio.RadioStationStore

class RadioSettingsActivity : AppCompatActivity() {
    private var currentPage = 1
    private lateinit var pageLabel: TextView
    private lateinit var stationLabels: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_settings)

        pageLabel = findViewById(R.id.txtPageLabel)
        stationLabels = listOf(
            findViewById(R.id.txtStationSlot1),
            findViewById(R.id.txtStationSlot2),
            findViewById(R.id.txtStationSlot3),
            findViewById(R.id.txtStationSlot4),
            findViewById(R.id.txtStationSlot5),
            findViewById(R.id.txtStationSlot6)
        )

        findViewById<Button>(R.id.btnBackRadioSettings).setOnClickListener {
            finish()
        }

        val visibleButtons = listOf(
            R.id.btnVisibleStation1,
            R.id.btnVisibleStation2,
            R.id.btnVisibleStation3,
            R.id.btnVisibleStation4,
            R.id.btnVisibleStation5,
            R.id.btnVisibleStation6
        )

        val editButtons = listOf(
            R.id.btnEditStation1,
            R.id.btnEditStation2,
            R.id.btnEditStation3,
            R.id.btnEditStation4,
            R.id.btnEditStation5,
            R.id.btnEditStation6
        )

        visibleButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                Toast.makeText(this, "Vidnost postaje", Toast.LENGTH_SHORT).show()
            }
        }

        editButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                Toast.makeText(this, "Uredi postajo", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnPreviousPage).setOnClickListener {
            if (currentPage > 1) {
                currentPage -= 1
                renderCurrentPage()
            }
        }

        findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            currentPage += 1
            renderCurrentPage()
        }

        findViewById<Button>(R.id.btnAddStation).setOnClickListener {
            startActivity(Intent(this, AddRadioStationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val stationsOnPage = RadioStationStore(this)
            .loadStations()
            .filter { it.page == currentPage }
            .sortedBy { it.position }

        pageLabel.text = "STRAN $currentPage"
        stationLabels.forEachIndexed { index, textView ->
            val station = stationsOnPage.firstOrNull { it.position == index + 1 }
            textView.text = station?.name ?: "PRAZNO"
        }
    }
}
