package com.rehab2

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rehab2.radio.RadioPlayerController
import com.rehab2.radio.SavedRadioStation
import com.rehab2.radio.RadioStationStore

class MainActivity : AppCompatActivity() {
    companion object {
        private const val RADIO_TILE_BLUE = 0xFF2F5F9E.toInt()
        private const val RADIO_TILE_GREEN = 0xFF2E8B57.toInt()
    }

    private lateinit var radioTiles: List<TextView>
    private lateinit var fallbackRadioLabels: List<CharSequence>
    private lateinit var seekVolume: SeekBar
    private lateinit var audioManager: AudioManager
    private lateinit var radioPlayerController: RadioPlayerController
    private var visibleRadioStations: List<SavedRadioStation?> = List(6) { null }
    private var activeStationKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        seekVolume = findViewById(R.id.seekVolume)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.statusBars())
        val content: ViewGroup = findViewById(android.R.id.content)
        val root = content.getChildAt(0)
        val baseLeft = root.paddingLeft
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(baseLeft, 0, baseRight, baseBottom + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        radioTiles = listOf(
            findViewById(R.id.txtRadioTile1),
            findViewById(R.id.txtRadioTile2),
            findViewById(R.id.txtRadioTile3),
            findViewById(R.id.txtRadioTile4),
            findViewById(R.id.txtRadioTile5),
            findViewById(R.id.txtRadioTile6)
        )
        fallbackRadioLabels = radioTiles.map { it.text }
        configureVolumeSlider()
        radioPlayerController = RadioPlayerController(this) { status ->
            if (status == "Napaka pri predvajanju") {
                runOnUiThread {
                    Toast.makeText(this, "Postaja se ne predvaja", Toast.LENGTH_SHORT).show()
                    radioPlayerController.stop()
                    activeStationKey = null
                    updateRadioTileColors()
                }
            }
        }

        radioTiles.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                handleRadioTileClick(index)
            }
        }

        findViewById<View>(R.id.statusModule).setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        val aacTiles = listOf(
            R.id.tileAacZejna to "ŽEJNA",
            R.id.tileAacLacna to "LAČNA",
            R.id.tileAacPomoc to "POMOČ",
            R.id.tileAacDa to "DA",
            R.id.tileAacWc to "WC",
            R.id.tileAacDobro to "DOBRO",
            R.id.tileAacSlabo to "SLABO",
            R.id.tileAacNe to "NE",
            R.id.tileAacUtrujena to "UTRUJENA",
            R.id.tileAacMraz to "MRAZ",
            R.id.tileAacVroce to "VROČE",
            R.id.tileAacBolecina to "BOLEČINA",
            R.id.tileAacDomov to "DOMOV",
            R.id.tileAacZdravnik to "ZDRAVNIK",
            R.id.tileAacDruzina to "DRUŽINA",
            R.id.tileAacStop to "STOP"
        )

        aacTiles.forEach { (tileId, label) ->
            findViewById<View>(tileId).setOnClickListener {
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        syncVolumeSlider()
        refreshRadioTiles()
    }

    override fun onDestroy() {
        radioPlayerController.release()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun refreshRadioTiles() {
        val stations = RadioStationStore(this)
            .getStationsForPage(1)
            .filter { it.visible }

        visibleRadioStations = List(6) { index ->
            stations.firstOrNull { it.position == index + 1 }
        }

        radioTiles.forEachIndexed { index, textView ->
            val station = visibleRadioStations[index]
            textView.text = station?.buttonLabel?.ifBlank { station.name } ?: fallbackRadioLabels[index]
        }

        if (activeStationKey != null) {
            val stillVisible = visibleRadioStations.any { station ->
                station != null && stationKey(station) == activeStationKey
            }
            if (!stillVisible) {
                radioPlayerController.stop()
                activeStationKey = null
            }
        }

        updateRadioTileColors()
    }

    private fun handleRadioTileClick(index: Int) {
        val station = visibleRadioStations.getOrNull(index)
        if (station == null) {
            Toast.makeText(this, "Ni postaje", Toast.LENGTH_SHORT).show()
            return
        }

        val key = stationKey(station)
        if (activeStationKey == key) {
            radioPlayerController.stop()
            activeStationKey = null
            updateRadioTileColors()
            return
        }

        activeStationKey = key
        updateRadioTileColors()
        radioPlayerController.play(station.streamUrl)
    }

    private fun stationKey(station: SavedRadioStation): String {
        return station.stationUuid.ifBlank { station.streamUrl }
    }

    private fun updateRadioTileColors() {
        radioTiles.forEachIndexed { index, textView ->
            val station = visibleRadioStations.getOrNull(index)
            val isActive = station != null && activeStationKey == stationKey(station)
            textView.setBackgroundColor(if (isActive) RADIO_TILE_GREEN else RADIO_TILE_BLUE)
        }
    }

    private fun configureVolumeSlider() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (audioManager.isVolumeFixed || maxVolume <= 0) {
            seekVolume.max = if (maxVolume > 0) maxVolume else 1
            seekVolume.progress = 0
            seekVolume.isEnabled = false
            return
        }

        seekVolume.isEnabled = true
        seekVolume.max = maxVolume
        syncVolumeSlider()
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || audioManager.isVolumeFixed) {
                    return
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun syncVolumeSlider() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVolume <= 0) {
            seekVolume.isEnabled = false
            return
        }

        seekVolume.max = maxVolume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        seekVolume.progress = currentVolume.coerceIn(0, maxVolume)
        seekVolume.isEnabled = !audioManager.isVolumeFixed
    }
}
