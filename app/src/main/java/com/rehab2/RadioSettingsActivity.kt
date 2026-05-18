package com.rehab2

import android.content.Intent
import android.os.Bundle
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.radio.SavedRadioStation
import com.rehab2.radio.RadioStationStore

class RadioSettingsActivity : AppCompatActivity() {
    companion object {
        private const val MP3_SLOT_INDEX = 5
    }

    private var currentPage = 1
    private lateinit var pageLabel: TextView
    private lateinit var stationLabels: List<TextView>
    private lateinit var visibleButtons: List<Button>
    private lateinit var editButtons: List<Button>
    private lateinit var store: RadioStationStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_settings)

        store = RadioStationStore(this)
        pageLabel = findViewById(R.id.txtPageLabel)
        stationLabels = listOf(
            findViewById(R.id.txtStationSlot1),
            findViewById(R.id.txtStationSlot2),
            findViewById(R.id.txtStationSlot3),
            findViewById(R.id.txtStationSlot4),
            findViewById(R.id.txtStationSlot5),
            findViewById(R.id.txtStationSlot6)
        )
        visibleButtons = listOf(
            findViewById(R.id.btnVisibleStation1),
            findViewById(R.id.btnVisibleStation2),
            findViewById(R.id.btnVisibleStation3),
            findViewById(R.id.btnVisibleStation4),
            findViewById(R.id.btnVisibleStation5),
            findViewById(R.id.btnVisibleStation6)
        )
        editButtons = listOf(
            findViewById(R.id.btnEditStation1),
            findViewById(R.id.btnEditStation2),
            findViewById(R.id.btnEditStation3),
            findViewById(R.id.btnEditStation4),
            findViewById(R.id.btnEditStation5),
            findViewById(R.id.btnEditStation6)
        )

        findViewById<Button>(R.id.btnBackRadioSettings).setOnClickListener {
            finish()
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

        findViewById<Button>(R.id.btnOpenLocalMusicSettings).setOnClickListener {
            startActivity(Intent(this, LocalMusicActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderCurrentPage()
    }

    private fun renderCurrentPage() {
        val stationsOnPage = store.getStationsForPage(currentPage)

        pageLabel.text = getString(R.string.radio_page_format, currentPage)
        stationLabels.forEachIndexed { index, textView ->
            if (index == MP3_SLOT_INDEX) {
                textView.text = getString(R.string.mp3_settings_label)
                bindMp3ButtonState(index)
            } else {
                val station = stationsOnPage.firstOrNull { it.position == index + 1 }
                textView.text = station?.buttonLabel?.ifBlank { station.name } ?: getString(R.string.radio_empty_label)
                bindVisibleButton(index, station)
                bindEditButton(index, station)
            }
        }
    }

    private fun bindMp3ButtonState(index: Int) {
        val visibleButton = visibleButtons[index]
        visibleButton.text = getString(R.string.mp3_open_label)
        visibleButton.isEnabled = true
        visibleButton.backgroundTintList = ColorStateList.valueOf(0xFF356B73.toInt())
        visibleButton.setOnClickListener {
            startActivity(Intent(this, LocalMusicActivity::class.java))
        }

        val editButton = editButtons[index]
        editButton.text = getString(R.string.mp3_settings_button_label)
        editButton.setOnClickListener {
            startActivity(Intent(this, LocalMusicActivity::class.java))
        }
    }

    private fun bindVisibleButton(index: Int, station: SavedRadioStation?) {
        val button = visibleButtons[index]
        if (station == null) {
            button.text = "-"
            button.isEnabled = true
            button.backgroundTintList = ColorStateList.valueOf(0xFF3A3F45.toInt())
            button.setOnClickListener {
                Toast.makeText(this, getString(R.string.radio_station_missing), Toast.LENGTH_SHORT).show()
            }
            return
        }

        button.isEnabled = true
        button.text = if (station.visible) "VIDNO" else "NEVIDNO"
        button.backgroundTintList = ColorStateList.valueOf(
            if (station.visible) 0xFF2E8B57.toInt() else 0xFFA64040.toInt()
        )
        button.setOnClickListener {
            val updated = store.toggleVisibility(station.stationUuid, station.streamUrl) ?: return@setOnClickListener
            val toastMessage = if (updated.visible) "Postaja vidna" else "Postaja nevidna"
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
            renderCurrentPage()
        }
    }

    private fun bindEditButton(index: Int, station: SavedRadioStation?) {
        val button = editButtons[index]
        button.setOnClickListener {
            if (station == null) {
                Toast.makeText(this, getString(R.string.radio_station_missing), Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, RadioStationEditActivity::class.java).apply {
                    putExtra(RadioStationEditActivity.EXTRA_STATION_UUID, station.stationUuid)
                    putExtra(RadioStationEditActivity.EXTRA_STREAM_URL, station.streamUrl)
                }
                startActivity(intent)
            }
        }
    }
}
