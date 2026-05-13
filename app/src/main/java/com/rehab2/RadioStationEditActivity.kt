package com.rehab2

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.radio.RadioStationStore
import com.rehab2.radio.SavedRadioStation

class RadioStationEditActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_STATION_UUID = "extra_station_uuid"
        const val EXTRA_STREAM_URL = "extra_stream_url"
    }

    private lateinit var store: RadioStationStore
    private lateinit var currentStation: SavedRadioStation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_station_edit)

        store = RadioStationStore(this)
        val stationUuid = intent.getStringExtra(EXTRA_STATION_UUID).orEmpty()
        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val station = store.findStation(stationUuid, streamUrl)

        if (station == null) {
            Toast.makeText(this, "Ni postaje", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentStation = station

        findViewById<TextView>(R.id.txtEditStationName).text = station.name
        findViewById<TextView>(R.id.txtEditStationUrl).text = station.streamUrl
        findViewById<TextView>(R.id.txtEditStationCountry).text = station.country
        findViewById<TextView>(R.id.txtEditStationGenre).text = station.genre

        val buttonLabelEdit: EditText = findViewById(R.id.editButtonLabel)
        val pageEdit: EditText = findViewById(R.id.editPage)
        val positionEdit: EditText = findViewById(R.id.editPosition)
        val visibleCheck: CheckBox = findViewById(R.id.checkVisible)

        buttonLabelEdit.setText(station.buttonLabel.ifBlank { station.name })
        pageEdit.setText(station.page.toString())
        positionEdit.setText(station.position.toString())
        visibleCheck.isChecked = station.visible

        findViewById<Button>(R.id.btnBackStationEdit).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSaveStationEdit).setOnClickListener {
            val page = pageEdit.text.toString().trim().toIntOrNull()
            val position = positionEdit.text.toString().trim().toIntOrNull()

            if (page == null || page < 1 || position == null || position !in 1..6) {
                Toast.makeText(this, "Neveljavna stran ali pozicija", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = currentStation.copy(
                buttonLabel = buttonLabelEdit.text.toString().trim(),
                visible = visibleCheck.isChecked,
                page = page,
                position = position
            )

            val result = store.updateStation(updated)
            if (result.invalidSlot || !result.success) {
                Toast.makeText(this, "Neveljavna stran ali pozicija", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Postaja prestavljena", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        findViewById<Button>(R.id.btnDeleteStation).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Izbriši postajo?")
                .setMessage("Postaja bo odstranjena iz seznama.")
                .setNegativeButton("NE", null)
                .setPositiveButton("DA") { _, _ ->
                    val deleted = store.deleteStation(currentStation.stationUuid, currentStation.streamUrl)
                    if (deleted) {
                        Toast.makeText(this, "Postaja izbrisana", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Ni postaje", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }
}
