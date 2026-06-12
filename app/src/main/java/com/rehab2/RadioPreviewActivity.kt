package com.rehab2

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.radio.RadioPlayerController
import com.rehab2.radio.RadioStationStore
import com.rehab2.radio.SavedRadioStation

class RadioPreviewActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_STATION_UUID = "extra_station_uuid"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_COUNTRY = "extra_country"
        const val EXTRA_TAGS = "extra_tags"
        const val EXTRA_CODEC = "extra_codec"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_FAVICON_URL = "extra_favicon_url"
    }

    private lateinit var playerController: RadioPlayerController
    private lateinit var statusText: TextView
    private var streamUrl: String = ""
    private var testSucceeded: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_preview)

        val stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
        streamUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
        val country = intent.getStringExtra(EXTRA_COUNTRY).orEmpty()
        val tags = intent.getStringExtra(EXTRA_TAGS).orEmpty()
        val stationUuid = intent.getStringExtra(EXTRA_STATION_UUID).orEmpty()
        val codec = intent.getStringExtra(EXTRA_CODEC).orEmpty()
        val bitrate = intent.getStringExtra(EXTRA_BITRATE).orEmpty().toIntOrNull() ?: 0
        val faviconUrl = intent.getStringExtra(EXTRA_FAVICON_URL).orEmpty()

        statusText = findViewById(R.id.txtPreviewStatus)
        statusText.text = ""

        findViewById<TextView>(R.id.txtPreviewName).text = stationName
        findViewById<TextView>(R.id.txtPreviewCountry).text = country
        findViewById<TextView>(R.id.txtPreviewTags).text = tags
        findViewById<TextView>(R.id.txtPreviewUrl).text = streamUrl

        playerController = RadioPlayerController(this, onStatusChanged = { status ->
            runOnUiThread {
                statusText.text = status
                if (status == "Postaja deluje") {
                    testSucceeded = true
                }
                if (status == "Napaka pri predvajanju") {
                    Toast.makeText(this, "Postaja se ne predvaja", Toast.LENGTH_SHORT).show()
                }
            }
        })

        findViewById<Button>(R.id.btnTestStation).setOnClickListener {
            if (streamUrl.isBlank()) {
                Toast.makeText(this, "Postaja nima veljavnega URL", Toast.LENGTH_SHORT).show()
            } else {
                playerController.play(streamUrl)
            }
        }

        findViewById<Button>(R.id.btnStopStation).setOnClickListener {
            playerController.stop()
            statusText.text = ""
        }

        findViewById<Button>(R.id.btnSaveAssignStation).setOnClickListener {
            if (!testSucceeded) {
                Toast.makeText(this, "Najprej testiraj postajo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val saveResult = RadioStationStore(this).saveStation(
                SavedRadioStation(
                    stationUuid = stationUuid,
                    name = stationName,
                    streamUrl = streamUrl,
                    country = country,
                    genre = tags,
                    faviconUrl = faviconUrl,
                    codec = codec,
                    bitrate = bitrate,
                    visible = true,
                    page = 0,
                    position = 0
                )
            )

            if (saveResult.duplicate) {
                Toast.makeText(this, "Postaja je že shranjena", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Shranjeno: stran ${saveResult.page}, pozicija ${saveResult.position}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<Button>(R.id.btnBackRadioPreview).setOnClickListener {
            playerController.stop()
            finish()
        }
    }

    override fun onDestroy() {
        playerController.release()
        super.onDestroy()
    }
}
