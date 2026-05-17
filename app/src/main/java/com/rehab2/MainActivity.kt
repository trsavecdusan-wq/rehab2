package com.rehab2

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rehab2.radio.RadioPlayerController
import com.rehab2.radio.SavedRadioStation
import com.rehab2.radio.RadioStationStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    companion object {
        private const val RADIO_TILE_BLUE = 0xFF2F5F9E.toInt()
        private const val RADIO_TILE_GREEN = 0xFF2E8B57.toInt()
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_PATIENT_LANGUAGE_1 = "patient_language_1"
        private const val PREF_PATIENT_LANGUAGE_2 = "patient_language_2"
        private const val PREF_ACTIVE_SPEECH_LANGUAGE = "active_speech_language"
        private const val PREF_ADMIN_PIN = "admin_pin"
        private const val DEFAULT_ADMIN_PIN = "0416"
        private const val STATUS_REFRESH_INTERVAL_MS = 1000L
    }

    private lateinit var radioTiles: List<TextView>
    private lateinit var fallbackRadioLabels: List<CharSequence>
    private lateinit var seekVolume: SeekBar
    private lateinit var audioManager: AudioManager
    private lateinit var radioPlayerController: RadioPlayerController
    private lateinit var prefs: SharedPreferences
    private lateinit var locationManager: LocationManager
    private lateinit var txtStatusLanguageFlag: TextView
    private lateinit var txtStatusBattery: TextView
    private lateinit var txtStatusNetwork: TextView
    private lateinit var txtStatusDay: TextView
    private lateinit var txtStatusDate: TextView
    private lateinit var txtStatusYear: TextView
    private lateinit var txtStatusSpeed: TextView
    private var visibleRadioStations: List<SavedRadioStation?> = List(6) { null }
    private var activeStationKey: String? = null
    private var currentSpeedKmh = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
    private val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            refreshStatusModule()
            mainHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
        }
    }
    private val speedLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentSpeedKmh = if (location.hasSpeed()) {
                (location.speed * 3.6f).roundToInt().coerceAtLeast(0)
            } else {
                0
            }
            txtStatusSpeed.text = currentSpeedKmh.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        seekVolume = findViewById(R.id.seekVolume)
        txtStatusLanguageFlag = findViewById(R.id.txtStatusLanguageFlag)
        txtStatusBattery = findViewById(R.id.txtStatusBattery)
        txtStatusNetwork = findViewById(R.id.txtStatusNetwork)
        txtStatusDay = findViewById(R.id.txtStatusDay)
        txtStatusDate = findViewById(R.id.txtStatusDate)
        txtStatusYear = findViewById(R.id.txtStatusYear)
        txtStatusSpeed = findViewById(R.id.txtStatusSpeed)
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

        txtStatusLanguageFlag.setOnLongClickListener {
            showLanguagePicker()
            true
        }
        txtStatusSpeed.setOnLongClickListener {
            showAdminPinDialog()
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

        refreshStatusModule()
    }

    override fun onResume() {
        super.onResume()
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        syncVolumeSlider()
        refreshRadioTiles()
        refreshStatusModule()
        startStatusUpdates()
        startSpeedUpdates()
    }

    override fun onPause() {
        stopStatusUpdates()
        stopSpeedUpdates()
        super.onPause()
    }

    override fun onDestroy() {
        stopStatusUpdates()
        stopSpeedUpdates()
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

    private fun startStatusUpdates() {
        mainHandler.removeCallbacks(statusRefreshRunnable)
        mainHandler.post(statusRefreshRunnable)
    }

    private fun stopStatusUpdates() {
        mainHandler.removeCallbacks(statusRefreshRunnable)
    }

    private fun startSpeedUpdates() {
        if (!hasLocationPermission()) {
            currentSpeedKmh = 0
            txtStatusSpeed.text = "0"
            return
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val anyProviderEnabled = providers.any { provider ->
            try {
                locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }
        if (!anyProviderEnabled) {
            currentSpeedKmh = 0
            txtStatusSpeed.text = "0"
            return
        }

        var lastLocation: Location? = null
        for (provider in providers) {
            if (!locationManager.isProviderEnabled(provider)) {
                continue
            }
            try {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, speedLocationListener, Looper.getMainLooper())
                val candidate = locationManager.getLastKnownLocation(provider)
                if (candidate != null && (lastLocation == null || candidate.time > lastLocation.time)) {
                    lastLocation = candidate
                }
            } catch (_: SecurityException) {
                currentSpeedKmh = 0
            } catch (_: IllegalArgumentException) {
            }
        }

        currentSpeedKmh = lastLocation?.let {
            if (it.hasSpeed()) (it.speed * 3.6f).roundToInt().coerceAtLeast(0) else 0
        } ?: 0
        txtStatusSpeed.text = currentSpeedKmh.toString()
    }

    private fun stopSpeedUpdates() {
        try {
            locationManager.removeUpdates(speedLocationListener)
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun refreshStatusModule() {
        txtStatusLanguageFlag.text = flagForLanguage(getActiveSpeechLanguage())
        txtStatusBattery.text = "${readBatteryPercentage()}%"
        txtStatusNetwork.text = readNetworkLabel()
        val now = Date()
        txtStatusDay.text = dayFormat.format(now).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        txtStatusDate.text = dateFormat.format(now)
        txtStatusYear.text = yearFormat.format(now)
        txtStatusSpeed.text = currentSpeedKmh.toString()
    }

    private fun readBatteryPercentage(): Int {
        val batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        val propertyValue = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (propertyValue in 1..100) {
            return propertyValue
        }

        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private fun readNetworkLabel(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return "OFF"
            @Suppress("DEPRECATION")
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "4G"
                else -> "OFF"
            }
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return "OFF"
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "OFF"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "4G"
            else -> "OFF"
        }
    }

    private fun getActiveSpeechLanguage(): String {
        return prefs.getString(PREF_ACTIVE_SPEECH_LANGUAGE, null)
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: "sl"
    }

    private fun getConfiguredSpeechLanguages(): List<String> {
        val first = prefs.getString(PREF_PATIENT_LANGUAGE_1, "sl").orEmpty().trim().lowercase(Locale.ROOT)
        val second = prefs.getString(PREF_PATIENT_LANGUAGE_2, "uk").orEmpty().trim().lowercase(Locale.ROOT)
        return listOf(first.ifBlank { "sl" }, second.ifBlank { "uk" }).distinct()
    }

    private fun flagForLanguage(languageCode: String): String {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "sl" -> "🇸🇮"
            "uk" -> "🇺🇦"
            "en" -> "🇬🇧"
            "de" -> "🇩🇪"
            "hr" -> "🇭🇷"
            "sr" -> "🇷🇸"
            else -> "🇸🇮"
        }
    }

    private fun languageLabel(languageCode: String): String {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "sl" -> "🇸🇮 Slovenščina"
            "uk" -> "🇺🇦 Українська"
            "en" -> "🇬🇧 English"
            "de" -> "🇩🇪 Deutsch"
            "hr" -> "🇭🇷 Hrvatski"
            "sr" -> "🇷🇸 Српски"
            else -> "🇸🇮 Slovenščina"
        }
    }

    private fun showLanguagePicker() {
        val configuredLanguages = getConfiguredSpeechLanguages()
        val labels = configuredLanguages.map { languageLabel(it) }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setPadding(paddingLeft, 24, paddingRight, 24)
                }
                return view
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Izberi govorni jezik")
            .setAdapter(adapter) { _, which ->
                prefs.edit().putString(PREF_ACTIVE_SPEECH_LANGUAGE, configuredLanguages[which]).apply()
                refreshStatusModule()
            }
            .setNegativeButton("Prekliči", null)
            .show()
    }

    private fun showAdminPinDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = "Administratorski PIN"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        AlertDialog.Builder(this)
            .setTitle("Vnesite administratorski PIN")
            .setView(input)
            .setPositiveButton("V redu") { _, _ ->
                val enteredPin = input.text?.toString().orEmpty()
                val expectedPin = prefs.getString(PREF_ADMIN_PIN, DEFAULT_ADMIN_PIN).orEmpty().ifBlank { DEFAULT_ADMIN_PIN }
                if (enteredPin == expectedPin) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                } else {
                    Toast.makeText(this, "Napačen PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Prekliči", null)
            .show()
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
