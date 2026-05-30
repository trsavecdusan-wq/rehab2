package com.rehab2

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
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
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rehab2.aac.AacAudioPlayer
import com.rehab2.aac.AacContentBootstrap
import com.rehab2.aac.AacItem
import com.rehab2.aac.AacLocalJsonLoader
import com.rehab2.aac.AacLocalizedTextResolver
import com.rehab2.aac.AacSentenceItem
import com.rehab2.aac.AacSentenceStateManager
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.AacStoredTranslationCache
import com.rehab2.aac.IconSource
import com.rehab2.radio.RadioPlayerController
import com.rehab2.radio.SavedRadioStation
import com.rehab2.radio.RadioStationStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MainActivity : AppCompatActivity() {
    companion object {
        private const val RADIO_TILE_BLUE = 0xFF2F5F9E.toInt()
        private const val RADIO_TILE_GREEN = 0xFF2E8B57.toInt()
        private const val RADIO_TILE_EMPTY = 0xFF3A3F45.toInt()
        private const val RADIO_TILE_MP3 = 0xFF356B73.toInt()
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_PATIENT_LANGUAGE_1 = "patient_language_1"
        private const val PREF_PATIENT_LANGUAGE_2 = "patient_language_2"
        private const val PREF_PATIENT_LANGUAGE_3 = "patient_language_3"
        private const val PREF_ACTIVE_SPEECH_LANGUAGE = "active_speech_language"
        private const val PREF_ADMIN_PIN = "admin_pin"
        private const val DEFAULT_ADMIN_PIN = "0416"
        private const val MAIN_AAC_HOME_PAGE_ID = "home"
        private const val PREFS_AAC_PATIENT_PAGES = "aac_patient_pages"
        private const val KEY_DEFAULT_PATIENT_PAGE_ID = "default_patient_page_id"
        private const val PREFS_AAC_GRID_SETTINGS = "aac_grid_settings"
        private const val KEY_AAC_GRID_SIZE = "aac_grid_size"
        private const val KEY_SHOW_SUBICONS_ON_MAIN_PAGES = "show_subicons_on_main_pages"
        private const val DEFAULT_AAC_GRID_SIZE = 4
        private const val MAIN_AAC_FIXED_TOP_ROW_MAX = 5
        private const val STATUS_REFRESH_INTERVAL_MS = 1000L
        private const val PREF_DISTANCE_TODAY_METERS = "distance_today_meters"
        private const val PREF_DISTANCE_TOTAL_METERS = "distance_total_meters"
        private const val PREF_DISTANCE_DAY_STAMP = "distance_day_stamp"
        private const val PREF_DISTANCE_WEEK_METERS = "distance_week_meters"
        private const val PREF_DISTANCE_MONTH_METERS = "distance_month_meters"
        private const val PREF_DISTANCE_YEAR_METERS = "distance_year_meters"
        private const val PREF_GPS_LAST_ACCURACY_METERS = "gps_last_accuracy_meters"
        private const val PREF_GPS_LAST_SIGNAL = "gps_last_signal"
        private const val PREF_GPS_LAST_IGNORED_REASON = "gps_last_ignored_reason"
        private const val PREF_GPS_RESET_BASELINE_REQUESTED = "gps_reset_baseline_requested"
        private const val MAX_REASONABLE_DISTANCE_METERS = 250f
        private const val MAX_REASONABLE_ACCURACY_METERS = 30f
        private const val MIN_REASONABLE_DISTANCE_METERS = 3f
        private const val MAX_REASONABLE_SPEED_KMH = 10f
        private const val GPS_SIGNAL_GOOD = "GOOD"
        private const val GPS_SIGNAL_WEAK = "WEAK"
        private const val GPS_REASON_NONE = "NONE"
        private const val GPS_REASON_NO_ACCURACY = "NO_ACCURACY"
        private const val GPS_REASON_POOR_ACCURACY = "POOR_ACCURACY"
        private const val GPS_REASON_INVALID_TIME = "INVALID_TIME"
        private const val GPS_REASON_TOO_FAST = "TOO_FAST"
        private const val GPS_REASON_JITTER = "JITTER"

        private const val PREF_POWER_MODE = "power_mode"
        private const val PREF_POWER_ALLOWED_UNPLUG_MINUTES = "power_allowed_unplug_minutes"
        private const val PREF_POWER_WARNING_GRACE_MINUTES = "power_warning_grace_minutes"
        private const val PREF_POWER_CRITICAL_BATTERY_PERCENT = "power_critical_battery_percent"
        private const val PREF_POWER_ADMIN_BYPASS_UNTIL = "power_admin_bypass_until"
        private const val PREF_KEEP_SCREEN_ON_WHILE_CHARGING = "keep_screen_on_while_charging"

        private const val POWER_MODE_ALWAYS_ON = "ALWAYS_ON"
        private const val POWER_MODE_BATTERY_SAVER = "BATTERY_SAVER"
        private const val POWER_MODE_POWER_SLEEP = "POWER_SLEEP"

        private const val DEFAULT_POWER_MODE = POWER_MODE_ALWAYS_ON
        private const val DEFAULT_POWER_ALLOWED_UNPLUG_MINUTES = 15
        private const val DEFAULT_POWER_WARNING_GRACE_MINUTES = 5
        private const val DEFAULT_POWER_CRITICAL_BATTERY_PERCENT = 20
        private const val DEFAULT_POWER_ADMIN_BYPASS_UNTIL = 0L
        private const val DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING = true

        private const val POWER_BYPASS_DURATION_MS = 15 * 60 * 1000L
        private const val POWER_CHECK_INTERVAL_MS = 1000L
        private const val RADIO_STATION_TILE_COUNT = 5
        private const val MP3_TILE_INDEX = 5
        private const val RADIO_SWIPE_THRESHOLD_PX = 80f
        private const val MAIN_AAC_AUTO_SENTENCE_DELAY_MS = 4_000L
        private const val MAIN_AAC_SENTENCE_CLEAR_DELAY_MS = 30_000L
        private const val MAIN_AAC_INPUT_LOCK_TIMEOUT_MS = 5_000L
    }

    private data class MainAacTileBinding(
        val view: View,
        val icon: TextView?,
        val label: TextView,
        val fallbackIconText: CharSequence,
        var item: AacItem? = null
    ) {
        companion object {
            fun from(view: View): MainAacTileBinding {
                val icon = findFirstTextView(view)
                return MainAacTileBinding(
                    view = view,
                    icon = icon,
                    fallbackIconText = icon?.text ?: "",
                    label = findLastTextView(view) ?: error("AAC tile label missing")
                )
            }

            private fun findFirstTextView(view: View): TextView? {
                if (view is TextView) {
                    return view
                }
                val group = view as? ViewGroup ?: return null
                for (index in 0 until group.childCount) {
                    findFirstTextView(group.getChildAt(index))?.let { return it }
                }
                return null
            }

            private fun findLastTextView(view: View): TextView? {
                if (view is TextView) {
                    return view
                }
                val group = view as? ViewGroup ?: return null
                var result: TextView? = null
                for (index in 0 until group.childCount) {
                    findLastTextView(group.getChildAt(index))?.let { result = it }
                }
                return result
            }
        }
    }

    private lateinit var radioTiles: List<TextView>
    private lateinit var fallbackRadioLabels: List<CharSequence>
    private lateinit var seekVolume: SeekBar
    private lateinit var audioManager: AudioManager
    private lateinit var aacAudioPlayer: AacAudioPlayer
    private lateinit var radioPlayerController: RadioPlayerController
    private lateinit var prefs: SharedPreferences
    private lateinit var locationManager: LocationManager
    private lateinit var txtStatusLanguageFlag: TextView
    private lateinit var txtStatusBatteryNetwork: TextView
    private lateinit var txtStatusDay: TextView
    private lateinit var txtStatusDate: TextView
    private lateinit var txtStatusYearTime: TextView
    private lateinit var txtStatusSpeed: TextView
    private lateinit var txtStatusTodayDistance: TextView
    private lateinit var powerOverlay: View
    private lateinit var txtPowerOverlayTitle: TextView
    private lateinit var txtPowerOverlaySubtitle: TextView
    private var visibleRadioStations: List<SavedRadioStation?> = List(6) { null }
    private var activeStationKey: String? = null
    private var currentRadioPage = 1
    private var radioTouchStartX = 0f
    private var currentSpeedKmh = 0f
    private var previousTrackedLocation: Location? = null
    private var isPowerConnected = true
    private var powerDisconnectedAtMs = 0L
    private var powerWarningShownAtMs = 0L
    private var isPowerOverlayVisible = false
    private var isSleepDimActive = false
    private var isKeepScreenOnApplied = false
    private val mainAacSentenceManager = AacSentenceStateManager()
    private lateinit var mainAacGridContainer: LinearLayout
    private lateinit var mainAacTileBindings: List<MainAacTileBinding>
    private var mainAacItemsById: Map<String, AacItem> = emptyMap()
    private var currentMainAacItems: List<AacItem> = emptyList()
    private var currentMainAacOrderedItems: List<AacItem> = emptyList()
    private var currentMainAacGridPageIndex = 0
    private var lastMainAacGridPageCount = 1
    private var mainAacTouchStartX = 0f
    private var mainAacTouchStartY = 0f
    private var configuredMainAacGridSize: Int = 0
    private var currentMainAacPageDebugId: String = "root"
    private var lastMainAacPageItemCountBeforeFixed = 0
    private var lastMainAacFixedRowItemCount = 0
    private var lastMainAacNormalItemCount = 0
    private var lastMainAacFinalOrderedItemCount = 0
    private var lastMainAacFinalItemCountAfterCap = 0
    private val mainAacHistory = ArrayDeque<List<AacItem>>()
    private val pendingMainAacTranslationKeys = mutableSetOf<String>()
    private val pendingMainAacPretranslationLanguages = mutableSetOf<String>()
    private var mainAacAutoSentenceMaySpeakSingle = false
    private var isMainAacInputLocked = false
    private var isMainAacTerminalSelectionAccepted = false
    private var shouldResetMainAacRootAfterSpeech = false
    private var selectedMainAacItemId: String? = null
    private var lastMainAacRenderedLanguage = ""
    private var savedScreenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    private var isPowerReceiverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainAacSentenceClearRunnable = Runnable {
        mainAacSentenceManager.clear()
        mainAacAutoSentenceMaySpeakSingle = false
    }
    private val mainAacAutoSentenceSpeakRunnable = Runnable {
        speakMainAacSentenceIfReady()
    }
    private val mainAacInputUnlockRunnable = Runnable {
        completeMainAacSpeechLock()
    }
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
    private val yearTimeFormat = SimpleDateFormat("yyyy HH:mm", Locale.getDefault())
    private val distanceDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    private val statusRefreshRunnable = object : Runnable {
        override fun run() {
            refreshStatusModule()
            mainHandler.postDelayed(this, STATUS_REFRESH_INTERVAL_MS)
        }
    }

    // TODO 1.0.54+: derive daily/weekly/monthly/yearly/total GPS distance from these updates with distanceTo() and ignore unrealistic jumps.

    private val speedLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentSpeedKmh = resolveSpeedKmh(previousTrackedLocation, location)
            trackDistance(location)
            txtStatusSpeed.text = formatSpeedKmh(currentSpeedKmh)
        }
    }
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED,
                Intent.ACTION_BATTERY_CHANGED -> {
                    updatePowerConnectedState(isPluggedFromBatteryIntent(intent))
                    refreshStatusModule()
                    applyKeepScreenOnPolicy()
                    evaluatePowerState()
                }
            }
        }
    }
    private val powerMonitorRunnable = object : Runnable {
        override fun run() {
            evaluatePowerState()
            mainHandler.postDelayed(this, POWER_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        aacAudioPlayer = AacAudioPlayer(this)
        aacAudioPlayer.setSpeechListener(object : AacAudioPlayer.SpeechListener {
            override fun onSpeechStarted() {
                lockMainAacInput()
            }

            override fun onSpeechCompleted() {
                completeMainAacSpeechLock()
            }

            override fun onSpeechCancelled() {
                completeMainAacSpeechLock()
            }

            override fun onSpeechError() {
                completeMainAacSpeechLock()
            }
        })
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        seekVolume = findViewById(R.id.seekVolume)
        txtStatusLanguageFlag = findViewById(R.id.txtStatusLanguageFlag)
        txtStatusBatteryNetwork = findViewById(R.id.txtStatusBatteryNetwork)
        txtStatusDay = findViewById(R.id.txtStatusDay)
        txtStatusDate = findViewById(R.id.txtStatusDate)
        txtStatusYearTime = findViewById(R.id.txtStatusYearTime)
        txtStatusSpeed = findViewById(R.id.txtStatusSpeed)
        txtStatusTodayDistance = findViewById(R.id.txtStatusTodayDistance)
        powerOverlay = findViewById(R.id.powerOverlay)
        txtPowerOverlayTitle = findViewById(R.id.txtPowerOverlayTitle)
        txtPowerOverlaySubtitle = findViewById(R.id.txtPowerOverlaySubtitle)
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
            textView.setOnTouchListener { _, event ->
                handleRadioSwipe(event)
            }
        }

        txtStatusLanguageFlag.setOnClickListener {
            showLanguagePicker()
        }
        txtStatusSpeed.setOnLongClickListener {
            showAdminPinDialog()
            true
        }
        powerOverlay.setOnLongClickListener {
            showAdminPinDialog {
                activatePowerBypass()
            }
            true
        }

        configureMainAacTiles()

        refreshStatusModule()
        refreshInitialPowerState()
        applyKeepScreenOnPolicy()
        evaluatePowerState()
    }

    override fun onResume() {
        super.onResume()
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        syncVolumeSlider()
        refreshRadioTiles()
        refreshStatusModule()
        applyKeepScreenOnPolicy()
        startStatusUpdates()
        startSpeedUpdates()
        registerPowerReceiver()
        refreshInitialPowerState()
        startPowerMonitoring()
        resetMainAacRootOnResume()
    }

    override fun onPause() {
        radioPlayerController.stop()
        activeStationKey = null
        updateRadioTileColors()
        stopStatusUpdates()
        stopSpeedUpdates()
        stopPowerMonitoring()
        unregisterPowerReceiver()
        super.onPause()
    }

    override fun onDestroy() {
        stopStatusUpdates()
        stopSpeedUpdates()
        stopPowerMonitoring()
        unregisterPowerReceiver()
        mainHandler.removeCallbacks(mainAacAutoSentenceSpeakRunnable)
        mainHandler.removeCallbacks(mainAacSentenceClearRunnable)
        mainHandler.removeCallbacks(mainAacInputUnlockRunnable)
        radioPlayerController.release()
        aacAudioPlayer.setSpeechListener(null)
        aacAudioPlayer.release()
        super.onDestroy()
    }

    private fun configureMainAacTiles() {
        mainAacGridContainer = findViewById(R.id.mainAacGridContainer)
        ensureMainAacGridBindings()
        val fallbackItems = buildMainAacItems()
        AacContentBootstrap.ensurePatientStartupContent(this, fallbackItems)
        val loadedItems = AacLocalJsonLoader.loadItems(this, fallbackItems)
        val items = mergeMainAacFallbackItems(fallbackItems, loadedItems)
        val startPageItems = selectMainStartPlacementItems(items)
        mainAacItemsById = items.associateBy { it.id }
        val fallbackRootItems = orderedMainAacItemsWithFixedTopRow(
            items.filter { it.isRootItem && !it.isHiddenUntilParent }
        )
        if (startPageItems.isEmpty()) {
            currentMainAacPageDebugId = "fallback_root"
            updateMainAacGridSelectionDebug("fallback_root", fallbackRootItems)
        }
        showMainAacItems(startPageItems.ifEmpty { fallbackRootItems })
    }

    private fun ensureMainAacGridBindings() {
        val gridSize = mainAacSelectedGridSize()
        if (
            ::mainAacTileBindings.isInitialized &&
            configuredMainAacGridSize == gridSize &&
            mainAacTileBindings.size == gridSize * gridSize
        ) {
            return
        }

        configuredMainAacGridSize = gridSize
        mainAacGridContainer.removeAllViews()
        mainAacGridContainer.orientation = LinearLayout.VERTICAL
        mainAacGridContainer.setOnLongClickListener {
            resetMainAacRoot()
            true
        }

        val bindings = mutableListOf<MainAacTileBinding>()
        repeat(gridSize) { rowIndex ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(0xFF232A31.toInt())
                minimumHeight = 0
                setPadding(dp(1), dp(1), dp(1), dp(1))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    if (rowIndex < gridSize - 1) bottomMargin = dp(1)
                }
            }
            repeat(gridSize) { columnIndex ->
                val tile = createMainAacTileView(gridSize).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1f
                    ).apply {
                        if (columnIndex > 0) leftMargin = dp(1)
                    }
                }
                row.addView(tile)
                bindings += MainAacTileBinding.from(tile)
            }
            mainAacGridContainer.addView(row)
        }
        mainAacTileBindings = bindings
    }

    private fun createMainAacTileView(gridSize: Int): LinearLayout {
        val iconTextSize = when (gridSize) {
            3 -> 46f
            4 -> 38f
            5 -> 26f
            else -> 20f
        }
        val labelTextSize = when (gridSize) {
            3 -> 13f
            4 -> 10f
            5 -> 8f
            else -> 7f
        }
        return LinearLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF34414D.toInt())
                cornerRadius = 0f
            }
            minimumHeight = 0
            isClickable = true
            isFocusable = true
            gravity = android.view.Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(dp(1), dp(1), dp(1), dp(1))
            addView(TextView(context).apply {
                minimumHeight = 0
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, iconTextSize)
            })
            addView(TextView(context).apply {
                minimumHeight = 0
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                maxLines = 2
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, labelTextSize)
                setTypeface(typeface, Typeface.BOLD)
            })
        }
    }

    private fun showMainAacItems(items: List<AacItem>, resetPaging: Boolean = true) {
        ensureMainAacGridBindings()
        if (resetPaging) {
            currentMainAacGridPageIndex = 0
        }
        currentMainAacOrderedItems = items
        val visibleItems = mainAacVisiblePageItems(items)
        lastMainAacFinalItemCountAfterCap = visibleItems.size
        currentMainAacItems = visibleItems
        val languageCode = getActiveSpeechLanguage()
        lastMainAacRenderedLanguage = languageCode
        mainAacTileBindings.forEachIndexed { index, binding ->
            val item = visibleItems.getOrNull(index)
            binding.item = item
            binding.view.visibility = View.VISIBLE
            item?.let {
                binding.view.isEnabled = !isMainAacInputLocked
                binding.label.text = AacLocalizedTextResolver.resolveLabel(it, languageCode)
                    .uppercase(Locale.ROOT)
                bindMainAacIcon(binding, it)
                binding.view.setOnClickListener(null)
                binding.view.setOnTouchListener { _, event ->
                    handleMainAacTileTouch(event, item)
                }
            } ?: run {
                binding.view.isEnabled = false
                binding.view.alpha = 0.45f
                binding.view.scaleX = 1f
                binding.view.scaleY = 1f
                binding.label.text = ""
                binding.icon?.apply {
                    setCompoundDrawables(null, null, null, null)
                    text = ""
                }
                binding.view.setOnClickListener(null)
                binding.view.setOnTouchListener(null)
            }
        }
        refreshMainAacInputLockVisualState()
        logMainAacGridDebug(
            boundItemCount = visibleItems.size,
            visibleTileCount = mainAacTileBindings.count { it.view.visibility == View.VISIBLE }
        )
        mainAacGridContainer.post {
            logMainAacGridDebug(
                boundItemCount = currentMainAacItems.size,
                visibleTileCount = mainAacTileBindings.count { it.view.visibility == View.VISIBLE }
            )
        }
    }

    private fun lockMainAacInput() {
        isMainAacInputLocked = true
        mainHandler.removeCallbacks(mainAacInputUnlockRunnable)
        mainHandler.postDelayed(mainAacInputUnlockRunnable, MAIN_AAC_INPUT_LOCK_TIMEOUT_MS)
        refreshMainAacInputLockVisualState()
    }

    private fun unlockMainAacInput() {
        mainHandler.removeCallbacks(mainAacInputUnlockRunnable)
        isMainAacInputLocked = false
        selectedMainAacItemId = null
        refreshMainAacInputLockVisualState()
    }

    private fun completeMainAacSpeechLock() {
        val resetAfterSpeech = shouldResetMainAacRootAfterSpeech
        shouldResetMainAacRootAfterSpeech = false
        unlockMainAacInput()
        if (resetAfterSpeech) {
            resetMainAacAfterTerminalSpeech()
        }
    }

    private fun refreshMainAacInputLockVisualState() {
        if (!::mainAacTileBindings.isInitialized) return
        mainAacTileBindings.forEach { binding ->
            if (binding.item != null) {
                binding.view.isEnabled = !isMainAacInputLocked
                val isSelected = selectedMainAacItemId != null && binding.item?.id == selectedMainAacItemId
                binding.view.alpha = when {
                    isSelected -> 1f
                    isMainAacInputLocked -> 0.62f
                    else -> 1f
                }
                binding.view.scaleX = if (isSelected) 1.05f else 1f
                binding.view.scaleY = if (isSelected) 1.05f else 1f
            }
        }
    }

    private fun showPreviousMainAacItems() {
        if (mainAacHistory.isEmpty()) {
            return
        }
        showMainAacItems(mainAacHistory.removeLast())
    }

    private fun showPreviousMainAacGridPage() {
        if (isMainAacInputLocked) {
            return
        }
        if (currentMainAacGridPageIndex <= 0) {
            return
        }
        currentMainAacGridPageIndex -= 1
        showMainAacItems(currentMainAacOrderedItems, resetPaging = false)
    }

    private fun showNextMainAacGridPage() {
        if (isMainAacInputLocked) {
            return
        }
        if (currentMainAacGridPageIndex >= lastMainAacGridPageCount - 1) {
            return
        }
        currentMainAacGridPageIndex += 1
        showMainAacItems(currentMainAacOrderedItems, resetPaging = false)
    }

    private fun handleMainAacTileTouch(event: MotionEvent, item: AacItem): Boolean {
        if (isMainAacInputLocked) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mainAacTouchStartX = event.rawX
                mainAacTouchStartY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.rawX - mainAacTouchStartX
                val deltaY = event.rawY - mainAacTouchStartY
                val absDeltaX = if (deltaX < 0) -deltaX else deltaX
                val absDeltaY = if (deltaY < 0) -deltaY else deltaY
                mainAacTouchStartX = 0f
                mainAacTouchStartY = 0f
                if (lastMainAacGridPageCount > 1 && absDeltaX >= RADIO_SWIPE_THRESHOLD_PX && absDeltaX > absDeltaY) {
                    if (deltaX < 0) {
                        showNextMainAacGridPage()
                    } else {
                        showPreviousMainAacGridPage()
                    }
                    return true
                }
                selectedMainAacItemId = item.id
                refreshMainAacInputLockVisualState()
                handleMainAacItemAction(item)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                mainAacTouchStartX = 0f
                mainAacTouchStartY = 0f
                return true
            }
        }
        return true
    }

    private fun mainAacVisiblePageItems(items: List<AacItem>): List<AacItem> {
        val capacity = mainAacVisibleContentCapacity()
        if (capacity <= 0) {
            lastMainAacGridPageCount = 1
            currentMainAacGridPageIndex = 0
            return emptyList()
        }
        val allFixedItems = fixedTopRowItems(mainAacItemsById.values.toList())
        val visibleFixedItems = visibleFixedTopRowItems(allFixedItems).take(capacity)
        val visibleFixedIds = visibleFixedItems.map { it.id }.toSet()
        val allFixedIds = allFixedItems.map { it.id }.toSet()
        val overflowFixedItems = allFixedItems.filter { item -> item.id !in visibleFixedIds }
        val normalItems = (overflowFixedItems + items.filter { item ->
            item.id !in allFixedIds
        }).distinctBy { item -> item.id }
        val normalCapacity = (capacity - visibleFixedItems.size).coerceAtLeast(0)
        if (normalCapacity <= 0) {
            lastMainAacGridPageCount = 1
            currentMainAacGridPageIndex = 0
            return visibleFixedItems.take(capacity)
        }
        if (normalItems.size <= normalCapacity) {
            lastMainAacGridPageCount = 1
            currentMainAacGridPageIndex = 0
            return (visibleFixedItems + normalItems).take(capacity)
        }

        val normalPageSize = normalCapacity.coerceAtLeast(1)
        lastMainAacGridPageCount = ((normalItems.size + normalPageSize - 1) / normalPageSize).coerceAtLeast(1)
        currentMainAacGridPageIndex = currentMainAacGridPageIndex.coerceIn(0, lastMainAacGridPageCount - 1)
        val pageItems = normalItems
            .drop(currentMainAacGridPageIndex * normalPageSize)
            .take(normalPageSize)
        return (visibleFixedItems + pageItems).take(capacity)
    }

    private fun resetMainAacRoot() {
        mainAacHistory.clear()
        isMainAacTerminalSelectionAccepted = false
        shouldResetMainAacRootAfterSpeech = false
        selectedMainAacItemId = null
        val fallbackItems = buildMainAacItems()
        AacContentBootstrap.ensurePatientStartupContent(this, fallbackItems)
        val loadedItems = AacLocalJsonLoader.loadItems(this, fallbackItems)
        val items = mergeMainAacFallbackItems(fallbackItems, loadedItems)
        val startPageItems = selectMainStartPlacementItems(items)
        mainAacItemsById = items.associateBy { it.id }
        val fallbackRootItems = orderedMainAacItemsWithFixedTopRow(
            items.filter { it.isRootItem && !it.isHiddenUntilParent }
        )
        if (startPageItems.isEmpty()) {
            currentMainAacPageDebugId = "fallback_root"
            updateMainAacGridSelectionDebug("fallback_root", fallbackRootItems)
        }
        showMainAacItems(startPageItems.ifEmpty { fallbackRootItems })
    }

    private fun resetMainAacRootOnResume() {
        if (::mainAacTileBindings.isInitialized) {
            clearMainAacSentenceState()
            resetMainAacRoot()
        }
    }

    private fun handleMainAacItemAction(item: AacItem) {
        if (isMainAacInputLocked) {
            return
        }
        val targetPageItems = mainAacPageItems(item.targetPageId)
        if (targetPageItems.isNotEmpty()) {
            isMainAacTerminalSelectionAccepted = false
            selectedMainAacItemId = item.id
            prepareMainAacContextPrompt(item)
            lockMainAacInput()
            mainAacHistory.addLast(currentMainAacOrderedItems.ifEmpty { currentMainAacItems })
            currentMainAacPageDebugId = item.targetPageId
            showMainAacItems(targetPageItems)
            return
        }

        val childItems = mainAacChildrenFor(item)
        if (item.opensSubicons || childItems.isNotEmpty()) {
            if (childItems.isNotEmpty()) {
                isMainAacTerminalSelectionAccepted = false
                selectedMainAacItemId = item.id
                prepareMainAacContextPrompt(item)
                lockMainAacInput()
                mainAacHistory.addLast(currentMainAacOrderedItems.ifEmpty { currentMainAacItems })
                currentMainAacPageDebugId = "children:${item.id}"
                updateMainAacGridSelectionDebug(currentMainAacPageDebugId, childItems)
                showMainAacItems(childItems)
            }
            return
        }

        val languageCode = getActiveSpeechLanguage()
        if (mainAacHistory.isNotEmpty()) {
            if (isMainAacTerminalSelectionAccepted) {
                return
            }
            isMainAacTerminalSelectionAccepted = true
            selectedMainAacItemId = item.id
            lockMainAacInput()
        }
        if (needsMainAacTranslation(item, languageCode)) {
            translateMainAacItemForAction(item, languageCode)
            return
        }
        handleMainAacResolvedSpeech(
            item = item,
            languageCode = languageCode,
            resolvedLabel = AacLocalizedTextResolver.resolveLabel(item, languageCode),
            resolvedSpeechText = AacLocalizedTextResolver.resolveSpeakText(item, languageCode),
            inContextFlow = mainAacHistory.isNotEmpty()
        )
    }

    private fun prepareMainAacContextPrompt(item: AacItem) {
        clearMainAacSentenceState()
        val languageCode = getActiveSpeechLanguage()
        val prompt = AacLocalizedTextResolver.resolveQuestion(item, languageCode)?.trim().orEmpty()
        if (prompt.isNotBlank()) {
            aacAudioPlayer.speakText(prompt, languageCode)
        }
    }

    private fun handleMainAacResolvedSpeech(
        item: AacItem,
        languageCode: String,
        resolvedLabel: String,
        resolvedSpeechText: String,
        inContextFlow: Boolean
    ) {
        mainHandler.removeCallbacks(mainAacAutoSentenceSpeakRunnable)
        mainHandler.removeCallbacks(mainAacSentenceClearRunnable)
        if (inContextFlow && item.addsToSentence) {
            selectedMainAacItemId = item.id
            lockMainAacInput()
            mainAacSentenceManager.addItem(
                AacSentenceItem(
                    conceptId = item.conceptId ?: item.id,
                    text = resolvedSpeechText.ifBlank { resolvedLabel },
                    role = item.sentenceRole
                )
            )
            speakMainAacSentenceNow(languageCode)
            return
        }
        if (resolvedSpeechText.isNotBlank()) {
            selectedMainAacItemId = item.id
            lockMainAacInput()
            aacAudioPlayer.speakText(resolvedSpeechText, languageCode)
            shouldResetMainAacRootAfterSpeech = true
        }
        clearMainAacSentenceState()
    }

    private fun needsMainAacTranslation(item: AacItem, languageCode: String): Boolean {
        val normalizedLanguage = languageCode.trim().lowercase(Locale.ROOT)
        if (normalizedLanguage.isBlank() || normalizedLanguage == "sl") {
            return false
        }
        return AacStoredTranslationCache.needsTranslationRefresh(item, normalizedLanguage)
    }

    private fun translateMainAacItemForAction(item: AacItem, languageCode: String) {
        val normalizedLanguage = languageCode.trim().lowercase(Locale.ROOT)
        val translationKey = "${item.id}:$normalizedLanguage"
        synchronized(pendingMainAacTranslationKeys) {
            if (!pendingMainAacTranslationKeys.add(translationKey)) {
                return
            }
        }
        Thread {
            val translation = AacStoredTranslationCache.ensureTranslation(
                context = applicationContext,
                item = item,
                languageCode = normalizedLanguage
            )
            mainHandler.post {
                synchronized(pendingMainAacTranslationKeys) {
                    pendingMainAacTranslationKeys.remove(translationKey)
                }
                if (translation == null) {
                    handleMainAacResolvedSpeech(
                        item = item,
                        languageCode = normalizedLanguage,
                        resolvedLabel = AacLocalizedTextResolver.resolveLabel(item, normalizedLanguage),
                        resolvedSpeechText = AacLocalizedTextResolver.resolveSpeakText(item, normalizedLanguage),
                        inContextFlow = mainAacHistory.isNotEmpty()
                    )
                    return@post
                }
                val updatedItem = item.copy(
                    labelByLanguage = item.labelByLanguage + (normalizedLanguage to translation.label),
                    speechTextByLanguage = item.speechTextByLanguage + (normalizedLanguage to translation.speechText),
                    translationCacheMeta = translation.cacheEntry?.let { cacheEntry ->
                        item.translationCacheMeta + (normalizedLanguage to cacheEntry)
                    } ?: item.translationCacheMeta,
                    translationGenerated = true,
                    translationSource = "ai"
                )
                replaceMainAacItem(updatedItem)
                handleMainAacResolvedSpeech(
                    item = updatedItem,
                    languageCode = normalizedLanguage,
                    resolvedLabel = translation.label,
                    resolvedSpeechText = translation.speechText,
                    inContextFlow = mainAacHistory.isNotEmpty()
                )
            }
        }.start()
    }

    private fun replaceMainAacItem(updatedItem: AacItem) {
        mainAacItemsById = mainAacItemsById + (updatedItem.id to updatedItem)
        currentMainAacItems = currentMainAacItems.map { item ->
            if (item.id == updatedItem.id) updatedItem else item
        }
        currentMainAacOrderedItems = currentMainAacOrderedItems.map { item ->
            if (item.id == updatedItem.id) updatedItem else item
        }
        val remappedHistory = mainAacHistory.map { pageItems ->
            pageItems.map { item -> if (item.id == updatedItem.id) updatedItem else item }
        }
        mainAacHistory.clear()
        remappedHistory.forEach { mainAacHistory.addLast(it) }
        showMainAacItems(currentMainAacOrderedItems.ifEmpty { currentMainAacItems }, resetPaging = false)
    }

    private fun startMainAacPretranslation(languageCode: String) {
        val normalizedLanguage = languageCode.trim().lowercase(Locale.ROOT)
        if (normalizedLanguage.isBlank() || normalizedLanguage == "sl") {
            return
        }
        synchronized(pendingMainAacPretranslationLanguages) {
            if (!pendingMainAacPretranslationLanguages.add(normalizedLanguage)) {
                return
            }
        }
        val itemsSnapshot = mainAacItemsById.values
            .ifEmpty {
                val fallbackItems = buildMainAacItems()
                mergeMainAacFallbackItems(
                    fallbackItems = fallbackItems,
                    loadedItems = AacLocalJsonLoader.loadItems(this, fallbackItems)
                )
            }
            .toList()
        Thread {
            val result = AacStoredTranslationCache.ensureTranslationsForLanguage(
                context = applicationContext,
                languageCode = normalizedLanguage,
                items = itemsSnapshot
            )
            mainHandler.post {
                synchronized(pendingMainAacPretranslationLanguages) {
                    pendingMainAacPretranslationLanguages.remove(normalizedLanguage)
                }
                if (result.translatedCount > 0) {
                    refreshMainAacLanguageText()
                }
                Log.d(
                    "MainAAC",
                    "AAC_PRETRANSLATION language=${result.languageCode} missing=${result.missingCount} translated=${result.translatedCount} failed=${result.failedCount}"
                )
            }
        }.start()
    }

    private fun refreshMainAacLanguageText() {
        if (!::mainAacTileBindings.isInitialized || currentMainAacItems.isEmpty()) {
            return
        }
        val fallbackItems = buildMainAacItems()
        val mergedItemsById = mergeMainAacFallbackItems(
            fallbackItems = fallbackItems,
            loadedItems = AacLocalJsonLoader.loadItems(this, fallbackItems)
        ).associateBy { it.id }
        if (mergedItemsById.isNotEmpty()) {
            mainAacItemsById = mergedItemsById
            currentMainAacItems = currentMainAacItems.map { item -> mergedItemsById[item.id] ?: item }
            currentMainAacOrderedItems = currentMainAacOrderedItems.map { item -> mergedItemsById[item.id] ?: item }
            val remappedHistory = mainAacHistory.map { pageItems ->
                pageItems.map { item -> mergedItemsById[item.id] ?: item }
            }
            mainAacHistory.clear()
            remappedHistory.forEach { mainAacHistory.addLast(it) }
        }
        showMainAacItems(currentMainAacOrderedItems.ifEmpty { currentMainAacItems }, resetPaging = false)
    }

    private fun mergeMainAacFallbackItems(
        fallbackItems: List<AacItem>,
        loadedItems: List<AacItem>
    ): List<AacItem> {
        val loadedById = loadedItems.associateBy { it.id }
        val fallbackIds = fallbackItems.map { it.id }.toSet()
        val mergedFallbackItems = fallbackItems.map { fallbackItem ->
            loadedById[fallbackItem.id]?.let { storedItem ->
                mergeMainAacFallbackItem(fallbackItem, storedItem)
            } ?: fallbackItem
        }
        val extraLoadedItems = loadedItems.filter { it.id !in fallbackIds }
        return mergedFallbackItems + extraLoadedItems
    }

    private fun mergeMainAacFallbackItem(fallbackItem: AacItem, storedItem: AacItem): AacItem {
        return fallbackItem.copy(
            labelSl = storedItem.labelSl.ifBlank { fallbackItem.labelSl },
            speakTextSl = storedItem.speakTextSl ?: fallbackItem.speakTextSl,
            labelUk = storedItem.labelUk ?: fallbackItem.labelUk,
            labelEn = storedItem.labelEn ?: fallbackItem.labelEn,
            speakTextUk = storedItem.speakTextUk ?: fallbackItem.speakTextUk,
            speechText = storedItem.speechText ?: fallbackItem.speechText,
            speechTextEn = storedItem.speechTextEn ?: fallbackItem.speechTextEn,
            labelByLanguage = storedItem.labelByLanguage.ifEmpty { fallbackItem.labelByLanguage },
            speechTextByLanguage = storedItem.speechTextByLanguage.ifEmpty { fallbackItem.speechTextByLanguage },
            activeLanguages = storedItem.activeLanguages.ifEmpty { fallbackItem.activeLanguages },
            questionByLanguage = storedItem.questionByLanguage.ifEmpty { fallbackItem.questionByLanguage },
            baseLanguage = storedItem.baseLanguage,
            translationGenerated = storedItem.translationGenerated,
            translationSource = storedItem.translationSource,
            translationManualOverride = storedItem.translationManualOverride,
            imagePath = storedItem.imagePath.ifBlank { fallbackItem.imagePath },
            iconSource = if (storedItem.imagePath.isNotBlank()) storedItem.iconSource else fallbackItem.iconSource,
            categoryId = storedItem.categoryId ?: fallbackItem.categoryId,
            scenarioIds = storedItem.scenarioIds.ifEmpty { fallbackItem.scenarioIds },
            conceptId = storedItem.conceptId ?: fallbackItem.conceptId,
            children = storedItem.children.ifEmpty { fallbackItem.children },
            visibleUnderIds = storedItem.visibleUnderIds.ifEmpty { fallbackItem.visibleUnderIds },
            placements = storedItem.placements.ifEmpty { fallbackItem.placements },
            isRootItem = storedItem.isRootItem,
            isHiddenUntilParent = storedItem.isHiddenUntilParent,
            fixedTopRowPosition = storedItem.fixedTopRowPosition ?: fallbackItem.fixedTopRowPosition,
            addsToSentence = storedItem.addsToSentence,
            speaksImmediately = storedItem.speaksImmediately,
            opensSubicons = storedItem.opensSubicons,
            priority = storedItem.priority
        )
    }

    private fun selectMainStartPlacementItems(items: List<AacItem>): List<AacItem> {
        val defaultPageId = getSharedPreferences(PREFS_AAC_PATIENT_PAGES, MODE_PRIVATE)
            .getString(KEY_DEFAULT_PATIENT_PAGE_ID, "")
            .orEmpty()
            .trim()
            .takeIf { isSafeMainAacPageId(it) }
            ?: MAIN_AAC_HOME_PAGE_ID
        currentMainAacPageDebugId = defaultPageId
        return mainAacPageItems(defaultPageId, items)
    }

    private fun mainAacPageItems(pageId: String): List<AacItem> {
        return mainAacPageItems(pageId, mainAacItemsById.values.toList())
    }

    private fun mainAacPageItems(pageId: String, items: List<AacItem>): List<AacItem> {
        val normalizedPageId = pageId.trim()
        if (!isSafeMainAacPageId(normalizedPageId)) {
            return emptyList()
        }
        val itemsById = items.associateBy { it.id }
        val fixedItems = fixedTopRowItems(items)
        val visibleFixedItems = visibleFixedTopRowItems(fixedItems)
        val overflowFixedItems = fixedItems.filter { item -> item.id !in visibleFixedItems.map { it.id } }
        val fixedItemIds = fixedItems.map { it.id }.toSet()
        val placedItems = items
            .flatMap { item ->
                item.placements
                    .filter { placement -> placement.pageId == normalizedPageId }
                    .map { placement -> placement.position5x5 to item.id }
            }
            .filter { (position, itemId) -> position in 1..mainAacSelectedGridCellCount() && itemsById.containsKey(itemId) }
            .sortedBy { (position, _) -> position }
            .mapNotNull { (_, itemId) -> itemsById[itemId] }
            .filter { item -> item.id !in fixedItemIds }
            .distinctBy { item -> item.id }
        val usedItemIds = fixedItemIds + placedItems.map { it.id }.toSet()
        val rootFillItems = items
            .filter { item -> item.isRootItem && !item.isHiddenUntilParent }
            .filter { item -> item.id !in usedItemIds }
            .sortedWith(compareBy<AacItem> { it.priority }.thenBy { it.id })
        val usedAfterRootFillIds = usedItemIds + rootFillItems.map { it.id }.toSet()
        val terminalFillItems = if (showSubiconsOnMainPages()) {
            items
                .filter { item -> item.id !in usedAfterRootFillIds }
                .filter { item -> !item.opensSubicons && item.children.isEmpty() && item.addsToSentence }
                .sortedWith(compareBy<AacItem> { it.priority }.thenBy { it.id })
        } else {
            emptyList()
        }
        val fillItems = rootFillItems + terminalFillItems
        val finalOrderedItems = (visibleFixedItems + overflowFixedItems + placedItems + fillItems)
            .distinctBy { item -> item.id }
        lastMainAacPageItemCountBeforeFixed = placedItems.size
        lastMainAacFixedRowItemCount = fixedItems.size
        lastMainAacNormalItemCount = placedItems.size + fillItems.size
        lastMainAacFinalOrderedItemCount = finalOrderedItems.size
        lastMainAacFinalItemCountAfterCap = finalOrderedItems.take(mainAacVisibleContentCapacity()).size
        Log.d(
            "MainAAC",
            "PAGE FILL DEBUG pageId=$normalizedPageId totalItems=${items.size} placed=${placedItems.size} fixed=${fixedItems.size} rootFill=${rootFillItems.size} terminalFill=${terminalFillItems.size} finalOrdered=${finalOrderedItems.size} capacity=${mainAacVisibleContentCapacity()} excluded=${items.size - finalOrderedItems.size}"
        )
        return finalOrderedItems
    }

    private fun orderedMainAacItemsWithFixedTopRow(items: List<AacItem>): List<AacItem> {
        val fixedItems = fixedTopRowItems(items)
        val visibleFixedItems = visibleFixedTopRowItems(fixedItems)
        val overflowFixedItems = fixedItems.filter { item -> item.id !in visibleFixedItems.map { it.id } }
        val fixedItemIds = fixedItems.map { it.id }.toSet()
        return visibleFixedItems + overflowFixedItems + items.filter { item -> item.id !in fixedItemIds }
    }

    private fun updateMainAacGridSelectionDebug(pageId: String, orderedItems: List<AacItem>) {
        currentMainAacPageDebugId = pageId
        val fixedCount = fixedTopRowItems(orderedItems).size
        lastMainAacPageItemCountBeforeFixed = orderedItems.count { item ->
            (item.fixedTopRowPosition ?: 0) !in 1..MAIN_AAC_FIXED_TOP_ROW_MAX
        }
        lastMainAacFixedRowItemCount = fixedCount
        lastMainAacNormalItemCount = orderedItems.size - fixedCount
        lastMainAacFinalOrderedItemCount = orderedItems.size
        lastMainAacFinalItemCountAfterCap = orderedItems.take(mainAacVisibleContentCapacity()).size
    }

    private fun fixedTopRowItems(items: List<AacItem>): List<AacItem> {
        return items
            .filter { item -> item.fixedTopRowPosition in 1..MAIN_AAC_FIXED_TOP_ROW_MAX }
            .sortedBy { item -> item.fixedTopRowPosition ?: Int.MAX_VALUE }
            .distinctBy { item -> item.fixedTopRowPosition }
    }

    private fun visibleFixedTopRowItems(items: List<AacItem>): List<AacItem> {
        val visibleFixedCount = mainAacFixedTopRowCapacity()
        return items.filter { item -> item.fixedTopRowPosition in 1..visibleFixedCount }
    }

    private fun mainAacFixedTopRowCapacity(): Int {
        return mainAacSelectedGridSize().coerceAtMost(MAIN_AAC_FIXED_TOP_ROW_MAX)
    }

    private fun mainAacVisibleContentCapacity(): Int {
        return mainAacSelectedGridCellCount()
    }

    private fun mainAacSelectedGridCellCount(): Int {
        val gridSize = mainAacSelectedGridSize()
        return gridSize * gridSize
    }

    private fun mainAacSelectedGridSize(): Int {
        return getSharedPreferences(PREFS_AAC_GRID_SETTINGS, MODE_PRIVATE)
            .getInt(KEY_AAC_GRID_SIZE, DEFAULT_AAC_GRID_SIZE)
            .takeIf { it in 3..6 }
            ?: DEFAULT_AAC_GRID_SIZE
    }

    private fun showSubiconsOnMainPages(): Boolean {
        return getSharedPreferences(PREFS_AAC_GRID_SETTINGS, MODE_PRIVATE)
            .getBoolean(KEY_SHOW_SUBICONS_ON_MAIN_PAGES, false)
    }

    private fun logMainAacGridDebug(boundItemCount: Int, visibleTileCount: Int) {
        val selectedGridSize = mainAacSelectedGridSize()
        val expectedSlotCount = selectedGridSize * selectedGridSize
        val createdTileViewCount = if (::mainAacTileBindings.isInitialized) mainAacTileBindings.size else 0
        val attachedChildCount = if (::mainAacGridContainer.isInitialized) mainAacGridContainer.childCount else 0
        val gridRowCount = attachedChildCount
        val firstRow = if (::mainAacGridContainer.isInitialized && mainAacGridContainer.childCount > 0) {
            mainAacGridContainer.getChildAt(0) as? ViewGroup
        } else {
            null
        }
        val gridColumnCount = firstRow?.childCount ?: 0
        val containerHeight = if (::mainAacGridContainer.isInitialized) mainAacGridContainer.height else 0
        val tileHeight = mainAacTileBindings.firstOrNull()?.view?.height ?: 0
        var rowsHeight = 0
        if (::mainAacGridContainer.isInitialized) {
            for (index in 0 until mainAacGridContainer.childCount) {
                rowsHeight += mainAacGridContainer.getChildAt(index).height
            }
        }
        val isClippedOrScrollable = containerHeight > 0 && rowsHeight > containerHeight
        val emptySlots = (expectedSlotCount - boundItemCount).coerceAtLeast(0)
        Log.d(
            "MainAAC",
            "GRID DEBUG selectedGridSize=$selectedGridSize runtimeGridSize=$configuredMainAacGridSize expectedSlotCount=$expectedSlotCount createdTileViewCount=$createdTileViewCount attachedChildCount=$attachedChildCount visibleTileCount=$visibleTileCount gridRowCount=$gridRowCount gridColumnCount=$gridColumnCount containerHeight=$containerHeight tileHeight=$tileHeight isClippedOrScrollable=$isClippedOrScrollable currentPageId=$currentMainAacPageDebugId pageItemCountBeforeFixed=$lastMainAacPageItemCountBeforeFixed fixedRowItemCount=$lastMainAacFixedRowItemCount normalItemCount=$lastMainAacNormalItemCount finalOrderedItemCountBeforeTake=$lastMainAacFinalOrderedItemCount finalItemCountAfterTake=$lastMainAacFinalItemCountAfterCap boundItemCount=$boundItemCount cappedAtOldLimit=${boundItemCount < expectedSlotCount && createdTileViewCount < expectedSlotCount} emptySlots=$emptySlots pageIndex=${currentMainAacGridPageIndex + 1} pageCount=$lastMainAacGridPageCount"
        )
    }

    private fun bindMainAacIcon(binding: MainAacTileBinding, item: AacItem) {
        val iconView = binding.icon ?: return
        val iconFile = AacStoragePaths.resolveIconFile(this, item.imagePath, item.iconSource)
        if (iconFile?.isFile != true) {
            restoreMainAacFallbackIcon(binding, item)
            return
        }
        val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
        if (bitmap == null) {
            restoreMainAacFallbackIcon(binding, item)
            return
        }
        val drawable = BitmapDrawable(resources, bitmap).apply {
            val size = dp(mainAacIconBitmapSizeDp())
            setBounds(0, 0, size, size)
        }
        iconView.text = ""
        iconView.setCompoundDrawables(null, drawable, null, null)
    }

    private fun mainAacIconBitmapSizeDp(): Int {
        return when (mainAacSelectedGridSize()) {
            3 -> 76
            4 -> 58
            5 -> 38
            else -> 30
        }
    }

    private fun restoreMainAacFallbackIcon(binding: MainAacTileBinding, item: AacItem?) {
        binding.icon?.apply {
            setCompoundDrawables(null, null, null, null)
            text = mainAacFallbackIconFor(item) ?: binding.fallbackIconText
        }
    }

    private fun mainAacFallbackIconFor(item: AacItem?): CharSequence? {
        val keys = listOfNotNull(
            item?.id?.trim()?.lowercase(Locale.ROOT),
            item?.conceptId?.trim()?.lowercase(Locale.ROOT)
        ).filter { it.isNotBlank() }
        keys.forEach { key ->
            when (key) {
                "drink", "drinks", "thirsty", "water", "juice", "tea", "coffee", "soca_water" -> return "\uD83E\uDD64"
                "food", "hungry", "soup", "bread", "fruit" -> return "\uD83C\uDF7D"
                "help", "soca_help" -> return "\uD83C\uDD98"
                "yes" -> return "✅"
                "wc", "toilet", "soca_wc" -> return "\uD83D\uDEBD"
                "good" -> return "\uD83D\uDE42"
                "bad" -> return "\uD83D\uDE1F"
                "no" -> return "❌"
                "no_understand", "dont_understand" -> return "❓"
                "will" -> return "\uD83D\uDCAC"
                "calm" -> return "\uD83C\uDF3F"
                "turn_me" -> return "\uD83D\uDD04"
                "tired" -> return "\uD83D\uDE34"
                "night", "sleep", "rest", "pocitek", "spanje" -> return "\uD83C\uDF19"
                "cold" -> return "❄"
                "hot" -> return "☀"
                "pain", "soca_pain", "head", "arm", "leg", "belly" -> return "\uD83E\uDD15"
                "doctor" -> return "\uD83D\uDC68\u200D⚕️"
                "family" -> return "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67"
                "stop" -> return "\uD83D\uDED1"
            }
        }
        return null
    }

    private fun isSafeMainAacPageId(pageId: String): Boolean {
        return pageId.isNotBlank() && pageId.matches(Regex("[A-Za-z0-9_-]+"))
    }

    private fun mainAacChildrenFor(item: AacItem): List<AacItem> {
        val explicitChildren = item.children.mapNotNull { childId -> mainAacItemsById[childId] }
        val visibleUnderChildren = mainAacItemsById.values
            .filter { child -> item.id in child.visibleUnderIds && child.id !in item.children }
            .sortedBy { it.priority }
        return explicitChildren + visibleUnderChildren
    }

    private fun scheduleMainAacSentenceSpeech(itemSpeaksImmediately: Boolean) {
        mainAacAutoSentenceMaySpeakSingle = mainAacAutoSentenceMaySpeakSingle || !itemSpeaksImmediately
        mainHandler.removeCallbacks(mainAacAutoSentenceSpeakRunnable)
        mainHandler.postDelayed(mainAacAutoSentenceSpeakRunnable, MAIN_AAC_AUTO_SENTENCE_DELAY_MS)
        mainHandler.removeCallbacks(mainAacSentenceClearRunnable)
        mainHandler.postDelayed(mainAacSentenceClearRunnable, MAIN_AAC_SENTENCE_CLEAR_DELAY_MS)
    }

    private fun speakMainAacSentenceIfReady() {
        val items = mainAacSentenceManager.getItems()
        val shouldSpeakSentence = items.size > 1 || mainAacAutoSentenceMaySpeakSingle
        val languageCode = getActiveSpeechLanguage()
        val sentence = mainAacSentenceManager.getSpeakText(languageCode).trim()
        if (shouldSpeakSentence && sentence.isNotBlank()) {
            lockMainAacInput()
            aacAudioPlayer.speakText(sentence, languageCode)
            clearMainAacSentenceState()
            shouldResetMainAacRootAfterSpeech = true
        }
    }

    private fun speakMainAacSentenceNow(languageCode: String) {
        val sentence = mainAacSentenceManager.getSpeakText(languageCode).trim()
        if (sentence.isNotBlank()) {
            lockMainAacInput()
            aacAudioPlayer.speakText(sentence, languageCode)
            shouldResetMainAacRootAfterSpeech = true
        }
        clearMainAacSentenceState()
    }

    private fun resetMainAacAfterTerminalSpeech() {
        mainAacHistory.clear()
        resetMainAacRoot()
    }

    private fun clearMainAacSentenceState() {
        mainAacSentenceManager.clear()
        mainAacAutoSentenceMaySpeakSingle = false
        mainHandler.removeCallbacks(mainAacAutoSentenceSpeakRunnable)
        mainHandler.removeCallbacks(mainAacSentenceClearRunnable)
    }

    private fun buildMainAacItems(): List<AacItem> {
        return listOf(
            mainAacItem(
                "drink",
                "PIJAČA",
                opensSubicons = true,
                children = listOf("water", "juice", "tea", "coffee"),
                questionByLanguage = mapOf(
                    "sl" to "Kaj želiš piti?",
                    "uk" to "Що ти хочеш пити?",
                    "en" to "What do you want to drink?"
                )
            ),
            mainAacItem(
                "food",
                "HRANA",
                opensSubicons = true,
                children = listOf("soup", "bread", "fruit"),
                questionByLanguage = mapOf(
                    "sl" to "Kaj želiš jesti?",
                    "uk" to "Що ти хочеш їсти?",
                    "en" to "What do you want to eat?"
                )
            ),
            mainAacItem(
                "thirsty",
                "ŽEJNA",
                "žejna sem",
                labelUk = "ХОЧУ ПИТИ",
                labelEn = "THIRSTY",
                speakTextUk = "Я хочу пити",
                speechTextEn = "I am thirsty"
            ),
            mainAacItem(
                "hungry",
                "LAČNA",
                "lačna sem",
                labelUk = "ХОЧУ ЇСТИ",
                labelEn = "HUNGRY",
                speakTextUk = "Я хочу їсти",
                speechTextEn = "I am hungry"
            ),
            mainAacItem(
                "help",
                "POMOČ",
                "pomagaj mi",
                labelUk = "ДОПОМОГА",
                labelEn = "HELP",
                speakTextUk = "Допоможіть мені",
                speechTextEn = "Help me"
            ),
            mainAacItem(
                "yes",
                "DA",
                "da",
                labelUk = "ТАК",
                labelEn = "YES",
                speakTextUk = "так",
                speechTextEn = "yes"
            ),
            mainAacItem(
                "wc",
                "WC",
                "moram na WC",
                labelUk = "ТУАЛЕТ",
                labelEn = "TOILET",
                speakTextUk = "Мені потрібно в туалет",
                speechTextEn = "I need the toilet"
            ),
            mainAacItem(
                "good",
                "DOBRO",
                "dobro sem",
                labelUk = "ДОБРЕ",
                labelEn = "GOOD",
                speakTextUk = "Мені добре",
                speechTextEn = "I am good"
            ),
            mainAacItem(
                "bad",
                "SLABO",
                "slabo mi je",
                labelUk = "ПОГАНО",
                labelEn = "BAD",
                speakTextUk = "Мені погано",
                speechTextEn = "I feel bad"
            ),
            mainAacItem(
                "turn_me",
                "OBRNI ME",
                "prosim, obrni me",
                labelUk = "ПОВЕРНІТЬ МЕНЕ",
                labelEn = "TURN ME",
                speakTextUk = "Будь ласка, поверніть мене",
                speechTextEn = "Please turn me"
            ),
            mainAacItem(
                "no_understand",
                "NE RAZUMEM",
                "ne razumem",
                labelUk = "Я НЕ РОЗУМІЮ",
                labelEn = "I DON'T UNDERSTAND",
                speakTextUk = "Я не розумію",
                speechTextEn = "I don't understand"
            ),
            mainAacItem(
                "no",
                "NE",
                "ne",
                labelUk = "НІ",
                labelEn = "NO",
                speakTextUk = "ні",
                speechTextEn = "no"
            ),
            mainAacItem(
                "sleep",
                "SPATI",
                "želim spati",
                labelUk = "СПАТИ",
                labelEn = "SLEEP",
                speakTextUk = "Я хочу спати",
                speechTextEn = "I want to sleep"
            ),
            mainAacItem(
                "tired",
                "UTRUJENA",
                "utrujena sem",
                labelUk = "ВТОМЛЕНА",
                labelEn = "TIRED",
                speakTextUk = "Я втомлена",
                speechTextEn = "I am tired"
            ),
            mainAacItem(
                "cold",
                "MRAZ",
                "zebe me",
                labelUk = "ХОЛОДНО",
                labelEn = "COLD",
                speakTextUk = "Мені холодно",
                speechTextEn = "I am cold"
            ),
            mainAacItem(
                "hot",
                "VROČE",
                "vroče mi je",
                labelUk = "ЖАРКО",
                labelEn = "HOT",
                speakTextUk = "Мені жарко",
                speechTextEn = "I am hot"
            ),
            mainAacItem(
                "pain",
                "BOLI",
                "boli me",
                labelUk = "БОЛИТЬ",
                labelEn = "PAIN",
                speakTextUk = "Мені болить",
                speechTextEn = "I am in pain",
                opensSubicons = true,
                children = listOf("head", "arm", "leg", "belly", "back", "chest", "throat"),
                questionByLanguage = mapOf(
                    "sl" to "Kje te boli?",
                    "uk" to "Де тебе болить?",
                    "en" to "Where does it hurt?"
                )
            ),
            mainAacItem(
                "doctor",
                "ZDRAVNIK",
                "pokličite zdravnika",
                labelUk = "ЛІКАР",
                labelEn = "DOCTOR",
                speakTextUk = "Покличте лікаря",
                speechTextEn = "Call the doctor"
            ),
            mainAacItem(
                "family",
                "DRUŽINA",
                "pokličite družino",
                labelUk = "СІМ'Я",
                labelEn = "FAMILY",
                speakTextUk = "Покличте сім'ю",
                speechTextEn = "Call my family"
            ),
            mainAacItem("stop", "STOP", "stop"),
            mainAacItem(
                "water",
                "VODA",
                "želim piti vodo",
                labelUk = "ВОДА",
                labelEn = "WATER",
                speakTextUk = "Я хочу пити воду",
                speechTextEn = "I want to drink water",
                isRootItem = false,
                visibleUnderIds = listOf("drink")
            ),
            mainAacItem(
                "juice",
                "SOK",
                "želim piti sok",
                labelUk = "СІК",
                labelEn = "JUICE",
                speakTextUk = "Я хочу пити сік",
                speechTextEn = "I want to drink juice",
                isRootItem = false,
                visibleUnderIds = listOf("drink")
            ),
            mainAacItem(
                "tea",
                "ČAJ",
                "želim piti čaj",
                labelUk = "ЧАЙ",
                labelEn = "TEA",
                speakTextUk = "Я хочу пити чай",
                speechTextEn = "I want to drink tea",
                isRootItem = false,
                visibleUnderIds = listOf("drink")
            ),
            mainAacItem(
                "coffee",
                "KAVA",
                "želim piti kavo",
                labelUk = "КАВА",
                labelEn = "COFFEE",
                speakTextUk = "Я хочу пити каву",
                speechTextEn = "I want to drink coffee",
                isRootItem = false,
                visibleUnderIds = listOf("drink")
            ),
            mainAacItem(
                "soup",
                "JUHA",
                "želim jesti juho",
                labelUk = "СУП",
                labelEn = "SOUP",
                speakTextUk = "Я хочу їсти суп",
                speechTextEn = "I want to eat soup",
                isRootItem = false,
                visibleUnderIds = listOf("food")
            ),
            mainAacItem(
                "bread",
                "KRUH",
                "želim jesti kruh",
                labelUk = "ХЛІБ",
                labelEn = "BREAD",
                speakTextUk = "Я хочу їсти хліб",
                speechTextEn = "I want to eat bread",
                isRootItem = false,
                visibleUnderIds = listOf("food")
            ),
            mainAacItem(
                "fruit",
                "SADJE",
                "želim jesti sadje",
                labelUk = "ФРУКТИ",
                labelEn = "FRUIT",
                speakTextUk = "Я хочу їсти фрукти",
                speechTextEn = "I want to eat fruit",
                isRootItem = false,
                visibleUnderIds = listOf("food")
            ),
            mainAacItem("head", "GLAVA", "boli me glava", labelUk = "ГОЛОВА", labelEn = "HEAD", speakTextUk = "У мене болить голова", speechTextEn = "My head hurts", isRootItem = false, visibleUnderIds = listOf("pain")),
            mainAacItem("arm", "ROKA", "boli me roka", labelUk = "РУКА", labelEn = "ARM", speakTextUk = "У мене болить рука", speechTextEn = "My arm hurts", isRootItem = false, visibleUnderIds = listOf("pain")),
            mainAacItem("leg", "NOGA", "boli me noga", labelUk = "НОГА", labelEn = "LEG", speakTextUk = "У мене болить нога", speechTextEn = "My leg hurts", isRootItem = false, visibleUnderIds = listOf("pain")),
            mainAacItem("belly", "TREBUH", "boli me trebuh", labelUk = "ЖИВІТ", labelEn = "BELLY", speakTextUk = "У мене болить живіт", speechTextEn = "My stomach hurts", isRootItem = false, visibleUnderIds = listOf("pain")),
            mainAacItem("back", "HRBET", "boli me hrbet", labelUk = "СПИНА", labelEn = "BACK", speakTextUk = "У мене болить спина", speechTextEn = "My back hurts", isRootItem = false, visibleUnderIds = listOf("pain")),
            mainAacItem("chest", "PRSI", "boli me v prsih", labelUk = "ГРУДИ", labelEn = "CHEST", speakTextUk = "У мене болить у грудях", speechTextEn = "My chest hurts", isRootItem = false, visibleUnderIds = listOf("pain")),
            mainAacItem("throat", "GRLO", "boli me grlo", labelUk = "ГОРЛО", labelEn = "THROAT", speakTextUk = "У мене болить горло", speechTextEn = "My throat hurts", isRootItem = false, visibleUnderIds = listOf("pain"))
        )
    }

    private fun mainAacItem(
        id: String,
        labelSl: String,
        speechText: String = labelSl.lowercase(Locale.ROOT),
        opensSubicons: Boolean = false,
        children: List<String> = emptyList(),
        isRootItem: Boolean = true,
        visibleUnderIds: List<String> = emptyList(),
        fixedTopRowPosition: Int? = null,
        labelUk: String? = null,
        labelEn: String? = null,
        speakTextUk: String? = null,
        speechTextEn: String? = null,
        questionByLanguage: Map<String, String> = emptyMap()
    ): AacItem {
        return AacItem(
            id = id,
            labelSl = labelSl,
            labelUk = labelUk,
            labelEn = labelEn,
            imagePath = "",
            actionType = if (opensSubicons) "open_subicons" else "speak",
            targetPageId = "",
            speakTextSl = speechText,
            speakTextUk = speakTextUk,
            speechText = speechText,
            speechTextEn = speechTextEn,
            iconSource = IconSource.SYSTEM,
            visibleUnderIds = visibleUnderIds,
            children = children,
            questionByLanguage = questionByLanguage,
            isRootItem = isRootItem,
            fixedTopRowPosition = fixedTopRowPosition,
            addsToSentence = !opensSubicons,
            speaksImmediately = !opensSubicons,
            opensSubicons = opensSubicons
        )
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
            previousTrackedLocation = null
            currentSpeedKmh = 0f
            txtStatusSpeed.text = formatSpeedKmh(currentSpeedKmh)
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
            previousTrackedLocation = null
            currentSpeedKmh = 0f
            txtStatusSpeed.text = formatSpeedKmh(currentSpeedKmh)
            return
        }

        resetDailyDistanceIfNeeded()
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
                currentSpeedKmh = 0f
            } catch (_: IllegalArgumentException) {
            }
        }

        currentSpeedKmh = 0f
        previousTrackedLocation = lastLocation
        txtStatusSpeed.text = formatSpeedKmh(currentSpeedKmh)
    }

    private fun stopSpeedUpdates() {
        previousTrackedLocation = null
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
        val activeLanguage = getActiveSpeechLanguage()
        if (::mainAacTileBindings.isInitialized &&
            currentMainAacItems.isNotEmpty() &&
            activeLanguage != lastMainAacRenderedLanguage
        ) {
            refreshMainAacLanguageText()
            startMainAacPretranslation(activeLanguage)
        }
        val flagDrawable = flagDrawableForLanguage(activeLanguage)
        if (flagDrawable != 0) {
            txtStatusLanguageFlag.text = ""
            txtStatusLanguageFlag.background = ContextCompat.getDrawable(this, flagDrawable)
            txtStatusLanguageFlag.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
        } else {
            txtStatusLanguageFlag.background = null
            txtStatusLanguageFlag.text = flagForLanguage(activeLanguage)
        }
        val batteryLabel = readBatteryPercentage()?.let { "$it%" } ?: getString(R.string.battery_unknown_short)
        txtStatusBatteryNetwork.text = "$batteryLabel ${readNetworkLabel()}"
        val now = Date()
        txtStatusDay.text = dayFormat.format(now).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        txtStatusDate.text = dateFormat.format(now)
        txtStatusYearTime.text = yearTimeFormat.format(now)
        txtStatusSpeed.text = formatSpeedKmh(currentSpeedKmh)
        txtStatusTodayDistance.text = formatTodayDistance(readTodayDistanceMeters())
    }

    private fun shortDayLabel(now: Date): String {
        return when (dayFormat.format(now).lowercase(Locale.getDefault())) {
            "monday", "ponedeljek" -> "Pon"
            "tuesday", "torek" -> "Tor"
            "wednesday", "sreda" -> "Sre"
            "thursday", "četrtek", "cetrtek" -> "Čet"
            "friday", "petek" -> "Pet"
            "saturday", "sobota" -> "Sob"
            "sunday", "nedelja" -> "Ned"
            else -> dayFormat.format(now).take(3).replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }

    private fun readBatteryPercentage(): Int? {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            null
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
        val third = prefs.getString(PREF_PATIENT_LANGUAGE_3, "").orEmpty().trim().lowercase(Locale.ROOT)
        return listOf(first.ifBlank { "sl" }, second.ifBlank { "uk" }, third)
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun flagForLanguage(languageCode: String): String {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "sl" -> "SI"
            "uk" -> "UA"
            "en" -> "EN"
            "de" -> "DE"
            "hr" -> "HR"
            "sr" -> "RS"
            else -> "SI"
        }
    }

    private fun languageLabel(languageCode: String): String {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "sl" -> getString(R.string.language_label_sl)
            "uk" -> getString(R.string.language_label_uk)
            "en" -> getString(R.string.language_label_en)
            "de" -> getString(R.string.language_label_de)
            "hr" -> getString(R.string.language_label_hr)
            "sr" -> getString(R.string.language_label_sr)
            else -> getString(R.string.language_label_sl)
        }
    }

    private fun showLanguagePicker() {
        val configuredLanguages = getConfiguredSpeechLanguages()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val content = ScrollView(this).apply {
            addView(container)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.language_picker_title)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        configuredLanguages.forEach { languageCode ->
            container.addView(createLanguageOptionView(languageCode) {
                prefs.edit().putString(PREF_ACTIVE_SPEECH_LANGUAGE, languageCode).apply()
                refreshStatusModule()
                refreshMainAacLanguageText()
                startMainAacPretranslation(languageCode)
                dialog.dismiss()
            })
        }

        dialog.show()
    }

    private fun createLanguageOptionView(languageCode: String, onClick: () -> Unit): View {
        val option = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(120)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = dp(12)
            layoutParams = params
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xFF1E2329.toInt())
                setStroke(dp(2), 0xFF5B6773.toInt())
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val flagDrawable = flagDrawableForLanguage(languageCode)
        if (flagDrawable != 0) {
            option.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(72)).apply {
                    marginEnd = dp(18)
                }
                scaleType = ImageView.ScaleType.FIT_XY
                setImageResource(flagDrawable)
            })
        } else {
            option.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(120), dp(72)).apply {
                    marginEnd = dp(18)
                }
                gravity = android.view.Gravity.CENTER
                setTextColor(0xFFF4F7FA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                setTypeface(typeface, Typeface.BOLD)
                text = flagForLanguage(languageCode)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(0xFF2B3138.toInt())
                }
            })
        }

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        labels.addView(TextView(this).apply {
            setTextColor(0xFFF4F7FA.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, Typeface.BOLD)
            text = flagForLanguage(languageCode)
        })

        labels.addView(TextView(this).apply {
            setTextColor(0xFFD6DDE4.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            text = languageLabel(languageCode).substringAfter(' ', languageLabel(languageCode))
        })

        option.addView(labels)
        return option
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun showAdminPinDialog(onSuccess: (() -> Unit)? = null) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = getString(R.string.admin_pin_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.admin_pin_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val enteredPin = input.text?.toString().orEmpty()
                val expectedPin = prefs.getString(PREF_ADMIN_PIN, DEFAULT_ADMIN_PIN).orEmpty().ifBlank { DEFAULT_ADMIN_PIN }
                if (enteredPin == expectedPin) {
                    if (onSuccess != null) {
                        onSuccess()
                    } else {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                } else {
                    Toast.makeText(this, getString(R.string.wrong_pin), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.postDelayed({
                val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 120L)
        }
        dialog.show()
    }

    private fun trackDistance(location: Location) {
        resetDailyDistanceIfNeeded()
        if (consumePendingGpsBaselineReset()) {
            previousTrackedLocation = null
        }
        if (!location.hasAccuracy()) {
            updateGpsDiagnostics(location, GPS_SIGNAL_WEAK, GPS_REASON_NO_ACCURACY)
            return
        }
        if (location.accuracy > MAX_REASONABLE_ACCURACY_METERS) {
            updateGpsDiagnostics(location, GPS_SIGNAL_WEAK, GPS_REASON_POOR_ACCURACY)
            return
        }
        val previousLocation = previousTrackedLocation
        if (previousLocation == null) {
            previousTrackedLocation = location
            updateGpsDiagnostics(location, GPS_SIGNAL_GOOD, GPS_REASON_NONE)
            return
        }

        val distanceMeters = previousLocation.distanceTo(location)
        if (!isReasonableDistance(previousLocation, location, distanceMeters)) {
            return
        }

        previousTrackedLocation = location
        updateGpsDiagnostics(location, GPS_SIGNAL_GOOD, GPS_REASON_NONE)
        val roundedDistance = distanceMeters.roundToLong().coerceAtLeast(0L)
        if (roundedDistance <= 0L) {
            return
        }

        prefs.edit()
            .putLong(PREF_DISTANCE_TODAY_METERS, prefs.getLong(PREF_DISTANCE_TODAY_METERS, 0L) + roundedDistance)
            .putLong(PREF_DISTANCE_TOTAL_METERS, prefs.getLong(PREF_DISTANCE_TOTAL_METERS, 0L) + roundedDistance)
            .apply()
    }

    private fun isReasonableDistance(previousLocation: Location, location: Location, distanceMeters: Float): Boolean {
        if (distanceMeters <= 0f) {
            updateGpsDiagnostics(location, gpsSignalForAccuracy(location), GPS_REASON_INVALID_TIME, distanceMeters)
            return false
        }
        if (distanceMeters < MIN_REASONABLE_DISTANCE_METERS) {
            updateGpsDiagnostics(location, gpsSignalForAccuracy(location), GPS_REASON_JITTER, distanceMeters)
            return false
        }
        if (distanceMeters > MAX_REASONABLE_DISTANCE_METERS) {
            updateGpsDiagnostics(location, GPS_SIGNAL_WEAK, GPS_REASON_TOO_FAST, distanceMeters)
            return false
        }
        val elapsedSeconds = (location.time - previousLocation.time) / 1000f
        if (elapsedSeconds <= 0f) {
            updateGpsDiagnostics(location, gpsSignalForAccuracy(location), GPS_REASON_INVALID_TIME, distanceMeters)
            return false
        }
        val speedKmh = resolveCalculatedSpeedKmh(distanceMeters, elapsedSeconds)
        if (speedKmh > MAX_REASONABLE_SPEED_KMH) {
            updateGpsDiagnostics(location, gpsSignalForAccuracy(location), GPS_REASON_TOO_FAST, distanceMeters)
            return false
        }
        return true
    }

    private fun updateGpsDiagnostics(
        location: Location,
        signal: String,
        ignoredReason: String,
        rawDistanceMeters: Float = -1f
    ) {
        val accuracyMeters = if (location.hasAccuracy()) location.accuracy else -1f
        Log.d(
            "Rehab2Gps",
            "decision=${if (ignoredReason == GPS_REASON_NONE) "ACCEPTED" else "REJECTED"} " +
                "signal=$signal accuracy=$accuracyMeters rawDistance=$rawDistanceMeters reason=$ignoredReason"
        )
        prefs.edit()
            .putFloat(PREF_GPS_LAST_ACCURACY_METERS, accuracyMeters)
            .putString(PREF_GPS_LAST_SIGNAL, signal)
            .putString(PREF_GPS_LAST_IGNORED_REASON, ignoredReason)
            .apply()
    }

    private fun resolveSpeedKmh(previousLocation: Location?, location: Location): Float {
        if (!location.hasAccuracy() || location.accuracy > MAX_REASONABLE_ACCURACY_METERS) {
            return 0f
        }
        if (location.hasSpeed()) {
            val speedKmh = location.speed * 3.6f
            return if (speedKmh <= MAX_REASONABLE_SPEED_KMH) {
                speedKmh.coerceAtLeast(0f)
            } else {
                0f
            }
        }
        if (previousLocation == null || !previousLocation.hasAccuracy() || previousLocation.accuracy > MAX_REASONABLE_ACCURACY_METERS) {
            return 0f
        }
        val distanceMeters = previousLocation.distanceTo(location)
        if (distanceMeters < MIN_REASONABLE_DISTANCE_METERS || distanceMeters > MAX_REASONABLE_DISTANCE_METERS) {
            return 0f
        }
        val elapsedSeconds = (location.time - previousLocation.time) / 1000f
        if (elapsedSeconds <= 0f) {
            return 0f
        }
        val speedKmh = resolveCalculatedSpeedKmh(distanceMeters, elapsedSeconds)
        return if (speedKmh <= MAX_REASONABLE_SPEED_KMH) {
            speedKmh.coerceAtLeast(0f)
        } else {
            0f
        }
    }

    private fun resolveCalculatedSpeedKmh(distanceMeters: Float, elapsedSeconds: Float): Float {
        return (distanceMeters / elapsedSeconds) * 3.6f
    }

    private fun formatSpeedKmh(speedKmh: Float): String {
        if (speedKmh < 0.05f) {
            return "0"
        }
        val roundedToOneDecimal = (speedKmh * 10f).roundToInt() / 10f
        val wholePart = roundedToOneDecimal.roundToInt().toFloat()
        return if (kotlin.math.abs(roundedToOneDecimal - wholePart) < 0.05f) {
            wholePart.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", roundedToOneDecimal)
        }
    }

    private fun readTodayDistanceMeters(): Long {
        return prefs.getLong(PREF_DISTANCE_TODAY_METERS, 0L).coerceAtLeast(0L)
    }

    private fun formatTodayDistance(distanceMeters: Long): String {
        return if (distanceMeters < 1000L) {
            "${distanceMeters} m"
        } else {
            String.format(Locale.US, "%.2f km", distanceMeters / 1000f)
        }
    }

    private fun gpsSignalForAccuracy(location: Location): String {
        return if (location.hasAccuracy() && location.accuracy <= MAX_REASONABLE_ACCURACY_METERS) {
            GPS_SIGNAL_GOOD
        } else {
            GPS_SIGNAL_WEAK
        }
    }

    private fun consumePendingGpsBaselineReset(): Boolean {
        if (!prefs.getBoolean(PREF_GPS_RESET_BASELINE_REQUESTED, false)) {
            return false
        }
        prefs.edit().putBoolean(PREF_GPS_RESET_BASELINE_REQUESTED, false).apply()
        return true
    }

    private fun resetDailyDistanceIfNeeded() {
        val todayStamp = distanceDayFormat.format(Date())
        val storedStamp = prefs.getString(PREF_DISTANCE_DAY_STAMP, null)
        if (storedStamp == todayStamp) {
            return
        }
        prefs.edit()
            .putString(PREF_DISTANCE_DAY_STAMP, todayStamp)
            .putLong(PREF_DISTANCE_TODAY_METERS, 0L)
            .apply()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun getPowerMode(): String =
        prefs.getString(PREF_POWER_MODE, DEFAULT_POWER_MODE).orEmpty().ifBlank { DEFAULT_POWER_MODE }

    private fun getAllowedUnplugMinutes(): Int =
        prefs.getInt(PREF_POWER_ALLOWED_UNPLUG_MINUTES, DEFAULT_POWER_ALLOWED_UNPLUG_MINUTES)

    private fun getWarningGraceMinutes(): Int =
        prefs.getInt(PREF_POWER_WARNING_GRACE_MINUTES, DEFAULT_POWER_WARNING_GRACE_MINUTES)

    private fun getCriticalBatteryPercent(): Int =
        prefs.getInt(PREF_POWER_CRITICAL_BATTERY_PERCENT, DEFAULT_POWER_CRITICAL_BATTERY_PERCENT)

    private fun getPowerBypassUntil(): Long =
        prefs.getLong(PREF_POWER_ADMIN_BYPASS_UNTIL, DEFAULT_POWER_ADMIN_BYPASS_UNTIL)

    private fun isKeepScreenOnWhileChargingEnabled(): Boolean =
        prefs.getBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING)

    private fun isPowerBypassActive(): Boolean =
        System.currentTimeMillis() < getPowerBypassUntil()

    private fun activatePowerBypass() {
        prefs.edit()
            .putLong(PREF_POWER_ADMIN_BYPASS_UNTIL, System.currentTimeMillis() + POWER_BYPASS_DURATION_MS)
            .apply()
        hidePowerOverlay()
        setScreenDimmed(false)
    }

    private fun registerPowerReceiver() {
        if (isPowerReceiverRegistered) {
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(powerReceiver, filter)
        }
        isPowerReceiverRegistered = true
    }

    private fun unregisterPowerReceiver() {
        if (!isPowerReceiverRegistered) {
            return
        }
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: IllegalArgumentException) {
        } finally {
            isPowerReceiverRegistered = false
        }
    }

    private fun refreshInitialPowerState() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updatePowerConnectedState(isPluggedFromBatteryIntent(batteryIntent))
    }

    private fun isPluggedFromBatteryIntent(intent: Intent?): Boolean {
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    private fun startPowerMonitoring() {
        mainHandler.removeCallbacks(powerMonitorRunnable)
        mainHandler.post(powerMonitorRunnable)
    }

    private fun stopPowerMonitoring() {
        mainHandler.removeCallbacks(powerMonitorRunnable)
    }

    private fun updatePowerConnectedState(isConnected: Boolean) {
        if (isConnected) {
            isPowerConnected = true
            powerDisconnectedAtMs = 0L
            powerWarningShownAtMs = 0L
            hidePowerOverlay()
            setScreenDimmed(false)
        } else {
            if (isPowerConnected) {
                powerDisconnectedAtMs = System.currentTimeMillis()
                powerWarningShownAtMs = 0L
            }
            isPowerConnected = false
        }
        applyKeepScreenOnPolicy()
    }

    private fun applyKeepScreenOnPolicy() {
        val shouldKeepScreenOn = isPowerConnected && isKeepScreenOnWhileChargingEnabled()
        if (shouldKeepScreenOn == isKeepScreenOnApplied) {
            return
        }
        if (shouldKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        isKeepScreenOnApplied = shouldKeepScreenOn
    }

    private fun evaluatePowerState() {
        if (isPowerConnected) {
            hidePowerOverlay()
            setScreenDimmed(false)
            return
        }

        if (isPowerBypassActive()) {
            hidePowerOverlay()
            setScreenDimmed(false)
            return
        }

        if (getPowerMode() == POWER_MODE_ALWAYS_ON) {
            hidePowerOverlay()
            setScreenDimmed(false)
            return
        }

        val now = System.currentTimeMillis()
        if (powerDisconnectedAtMs == 0L) {
            powerDisconnectedAtMs = now
        }

        val warningAt = powerDisconnectedAtMs + getAllowedUnplugMinutes() * 60_000L
        val sleepAt = warningAt + getWarningGraceMinutes() * 60_000L

        if (now < warningAt) {
            hidePowerOverlay()
            setScreenDimmed(false)
            return
        }

        if (powerWarningShownAtMs == 0L) {
            powerWarningShownAtMs = now
        }

        if (now < sleepAt) {
            showPowerOverlay(
                getString(R.string.power_overlay_connect_power),
                getString(R.string.power_overlay_power_disconnected)
            )
            setScreenDimmed(false)
            return
        }

        val secondaryText = if (getPowerMode() == POWER_MODE_BATTERY_SAVER) {
            getString(R.string.power_overlay_battery_saver_active)
        } else {
            getString(R.string.power_overlay_sleep_mode_active)
        }
        showPowerOverlay(
            getString(R.string.power_overlay_connect_power),
            secondaryText
        )
        setScreenDimmed(true)
    }

    private fun showPowerOverlay(primaryText: String, secondaryText: String) {
        txtPowerOverlayTitle.text = primaryText
        txtPowerOverlaySubtitle.text = secondaryText
        powerOverlay.visibility = View.VISIBLE
        isPowerOverlayVisible = true
    }

    private fun hidePowerOverlay() {
        powerOverlay.visibility = View.GONE
        isPowerOverlayVisible = false
    }

    private fun setScreenDimmed(dimmed: Boolean) {
        if (isSleepDimActive == dimmed) {
            return
        }

        val attributes = window.attributes
        if (dimmed) {
            savedScreenBrightness = attributes.screenBrightness
            attributes.screenBrightness = 0.05f
            window.attributes = attributes
            isSleepDimActive = true
        } else {
            attributes.screenBrightness = savedScreenBrightness
            window.attributes = attributes
            isSleepDimActive = false
        }
    }

    private fun refreshRadioTiles() {
        val stations = RadioStationStore(this)
            .getStationsForPage(currentRadioPage)
            .filter { it.visible }

        visibleRadioStations = List(6) { index ->
            if (index < RADIO_STATION_TILE_COUNT) {
                stations.firstOrNull { it.position == index + 1 }
            } else {
                null
            }
        }

        radioTiles.forEachIndexed { index, textView ->
            if (index == MP3_TILE_INDEX) {
                textView.text = getString(R.string.mp3_tile_label)
            } else {
                val station = visibleRadioStations[index]
                textView.text = station?.buttonLabel?.ifBlank { station.name } ?: getString(R.string.radio_empty_label)
            }
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
    private fun handleRadioSwipe(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                radioTouchStartX = event.x
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - radioTouchStartX
                if (deltaX <= -RADIO_SWIPE_THRESHOLD_PX) {
                    currentRadioPage += 1
                    refreshRadioTiles()
                    return true
                }
                if (deltaX >= RADIO_SWIPE_THRESHOLD_PX) {
                    currentRadioPage = (currentRadioPage - 1).coerceAtLeast(1)
                    refreshRadioTiles()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                radioTouchStartX = 0f
            }
        }
        return false
    }


    private fun handleRadioTileClick(index: Int) {
        if (index == MP3_TILE_INDEX) {
            radioPlayerController.stop()
            activeStationKey = null
            updateRadioTileColors()
            startActivity(Intent(this, LocalMusicActivity::class.java))
            return
        }

        val station = visibleRadioStations.getOrNull(index)
        if (station == null) {
            Toast.makeText(this, getString(R.string.radio_station_missing), Toast.LENGTH_SHORT).show()
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
            if (index == MP3_TILE_INDEX) {
                textView.setBackgroundColor(RADIO_TILE_MP3)
                return@forEachIndexed
            }
            val station = visibleRadioStations.getOrNull(index)
            if (station == null) {
                textView.setBackgroundColor(RADIO_TILE_EMPTY)
            } else {
                val isActive = activeStationKey == stationKey(station)
                textView.setBackgroundColor(if (isActive) RADIO_TILE_GREEN else RADIO_TILE_BLUE)
            }
        }
    }

    private fun flagDrawableForLanguage(languageCode: String): Int {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "sl" -> R.drawable.flag_si
            "uk" -> R.drawable.flag_ua
            else -> 0
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

