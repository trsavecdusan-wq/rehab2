package com.rehab2

import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_POWER_MODE = "power_mode"
        private const val PREF_POWER_ALLOWED_UNPLUG_MINUTES = "power_allowed_unplug_minutes"
        private const val PREF_POWER_WARNING_GRACE_MINUTES = "power_warning_grace_minutes"
        private const val PREF_POWER_CRITICAL_BATTERY_PERCENT = "power_critical_battery_percent"
        private const val PREF_POWER_ADMIN_BYPASS_UNTIL = "power_admin_bypass_until"
        private const val PREF_KEEP_SCREEN_ON_WHILE_CHARGING = "keep_screen_on_while_charging"
        private const val PREF_DISTANCE_TODAY_METERS = "distance_today_meters"
        private const val PREF_DISTANCE_TOTAL_METERS = "distance_total_meters"
        private const val PREF_GPS_LAST_ACCURACY_METERS = "gps_last_accuracy_meters"
        private const val PREF_GPS_LAST_SIGNAL = "gps_last_signal"
        private const val PREF_GPS_LAST_IGNORED_REASON = "gps_last_ignored_reason"
        private const val PREF_GPS_RESET_BASELINE_REQUESTED = "gps_reset_baseline_requested"
        private const val PREF_ADMIN_PIN = "admin_pin"
        private const val PREF_DISTANCE_WEEK_METERS = "distance_week_meters"
        private const val PREF_DISTANCE_MONTH_METERS = "distance_month_meters"
        private const val PREF_DISTANCE_YEAR_METERS = "distance_year_meters"
        private const val DEFAULT_ADMIN_PIN = "0416"

        private const val POWER_MODE_ALWAYS_ON = "ALWAYS_ON"
        private const val POWER_MODE_BATTERY_SAVER = "BATTERY_SAVER"
        private const val POWER_MODE_POWER_SLEEP = "POWER_SLEEP"

        private const val DEFAULT_POWER_MODE = POWER_MODE_ALWAYS_ON
        private const val DEFAULT_ALLOWED_UNPLUG_MINUTES = 15
        private const val DEFAULT_WARNING_GRACE_MINUTES = 5
        private const val DEFAULT_CRITICAL_BATTERY_PERCENT = 20
        private const val DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING = true
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var btnPowerOff: Button
    private lateinit var btnPowerWarning: Button
    private lateinit var btnPowerSleep: Button
    private lateinit var btnKeepScreenOnWhileCharging: Button
    private lateinit var txtAllowedUnplugValue: TextView
    private lateinit var txtWarningGraceValue: TextView
    private lateinit var txtCriticalBatteryValue: TextView
    private lateinit var txtPowerStatusValue: TextView
    private lateinit var txtTodayDistanceValue: TextView
    private lateinit var txtTotalDistanceValue: TextView
    private lateinit var txtGpsSignalValue: TextView
    private lateinit var txtGpsAccuracyValue: TextView
    private lateinit var txtGpsIgnoredReasonValue: TextView
    private lateinit var btnResetGpsStatistics: Button
    private var latestBatteryPercent: Int? = null
    private var latestPluggedIn = false
    private var isBatteryReceiverRegistered = false
    private val gpsDiagnosticsRefreshHandler = Handler(Looper.getMainLooper())
    private val powerFeedbackHandler = Handler(Looper.getMainLooper())
    private val gpsDiagnosticsRefreshRunnable = object : Runnable {
        override fun run() {
            refreshGpsDiagnosticsSection()
            refreshStatisticsSection()
            gpsDiagnosticsRefreshHandler.postDelayed(this, 1500L)
        }
    }
    private var pendingPowerStatusRefresh: Runnable? = null
    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatterySnapshot(intent)
            refreshPowerSection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)

        btnPowerOff = findViewById(R.id.btnPowerModeOff)
        btnPowerWarning = findViewById(R.id.btnPowerModeWarning)
        btnPowerSleep = findViewById(R.id.btnPowerModeSleep)
        btnKeepScreenOnWhileCharging = findViewById(R.id.btnKeepScreenOnWhileCharging)
        txtAllowedUnplugValue = findViewById(R.id.txtAllowedUnplugValue)
        txtWarningGraceValue = findViewById(R.id.txtWarningGraceValue)
        txtCriticalBatteryValue = findViewById(R.id.txtCriticalBatteryValue)
        txtPowerStatusValue = findViewById(R.id.txtPowerStatusValue)
        txtTodayDistanceValue = findViewById(R.id.txtTodayDistanceValue)
        txtTotalDistanceValue = findViewById(R.id.txtTotalDistanceValue)
        txtGpsSignalValue = findViewById(R.id.txtGpsSignalValue)
        txtGpsAccuracyValue = findViewById(R.id.txtGpsAccuracyValue)
        txtGpsIgnoredReasonValue = findViewById(R.id.txtGpsIgnoredReasonValue)
        btnResetGpsStatistics = findViewById(R.id.btnResetGpsStatistics)
        styleStepperButtons(
            findViewById(R.id.btnAllowedUnplugMinus),
            findViewById(R.id.btnAllowedUnplugPlus),
            findViewById(R.id.btnWarningGraceMinus),
            findViewById(R.id.btnWarningGracePlus),
            findViewById(R.id.btnCriticalBatteryMinus),
            findViewById(R.id.btnCriticalBatteryPlus)
        )

        findViewById<Button>(R.id.btnBackSettings).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnRadioSettings).setOnClickListener {
            startActivity(Intent(this, RadioSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnBackupSettings).setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAacSettings).setOnClickListener {
            Toast.makeText(this, "Funkcija še ni pripravljena", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStatusSettings).setOnClickListener {
            Toast.makeText(this, "STATISTIKA JE SPODAJ", Toast.LENGTH_SHORT).show()
        }

        btnPowerOff.setOnClickListener {
            setPowerMode(POWER_MODE_ALWAYS_ON)
        }

        btnPowerWarning.setOnClickListener {
            setPowerMode(POWER_MODE_BATTERY_SAVER)
        }

        btnPowerSleep.setOnClickListener {
            setPowerMode(POWER_MODE_POWER_SLEEP)
        }

        btnKeepScreenOnWhileCharging.setOnClickListener {
            val enabled = prefs.getBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING)
            prefs.edit().putBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, !enabled).apply()
            refreshPowerSection()
            applyKeepScreenOnWhileCharging()
        }

        findViewById<Button>(R.id.btnAllowedUnplugMinus).setOnClickListener {
            adjustIntPreference(PREF_POWER_ALLOWED_UNPLUG_MINUTES, -1, 1, 180, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        }

        findViewById<Button>(R.id.btnAllowedUnplugPlus).setOnClickListener {
            adjustIntPreference(PREF_POWER_ALLOWED_UNPLUG_MINUTES, 1, 1, 180, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        }

        findViewById<Button>(R.id.btnWarningGraceMinus).setOnClickListener {
            adjustIntPreference(PREF_POWER_WARNING_GRACE_MINUTES, -1, 1, 60, DEFAULT_WARNING_GRACE_MINUTES)
        }

        findViewById<Button>(R.id.btnWarningGracePlus).setOnClickListener {
            adjustIntPreference(PREF_POWER_WARNING_GRACE_MINUTES, 1, 1, 60, DEFAULT_WARNING_GRACE_MINUTES)
        }

        findViewById<Button>(R.id.btnCriticalBatteryMinus).setOnClickListener {
            adjustIntPreference(PREF_POWER_CRITICAL_BATTERY_PERCENT, -1, 5, 50, DEFAULT_CRITICAL_BATTERY_PERCENT)
        }

        findViewById<Button>(R.id.btnCriticalBatteryPlus).setOnClickListener {
            adjustIntPreference(PREF_POWER_CRITICAL_BATTERY_PERCENT, 1, 5, 50, DEFAULT_CRITICAL_BATTERY_PERCENT)
        }

        btnResetGpsStatistics.setOnClickListener {
            showAdminPinDialog {
                prefs.edit()
                    .putLong(PREF_DISTANCE_TODAY_METERS, 0L)
                    .putLong(PREF_DISTANCE_TOTAL_METERS, 0L)
                    .putBoolean(PREF_GPS_RESET_BASELINE_REQUESTED, true)
                    .apply()
                refreshStatisticsSection()
                refreshGpsDiagnosticsSection()
                Toast.makeText(this, R.string.gps_statistics_reset_done, Toast.LENGTH_SHORT).show()
            }
        }

        refreshBatterySnapshot()
        refreshPowerSection()
        refreshStatisticsSection()
        refreshGpsDiagnosticsSection()
        applyKeepScreenOnWhileCharging()
    }

    override fun onResume() {
        super.onResume()
        registerBatteryReceiver()
        refreshBatterySnapshot()
        refreshPowerSection()
        refreshStatisticsSection()
        refreshGpsDiagnosticsSection()
        applyKeepScreenOnWhileCharging()
        startGpsDiagnosticsRefresh()
    }

    override fun onPause() {
        stopGpsDiagnosticsRefresh()
        unregisterBatteryReceiver()
        super.onPause()
    }

    private fun setPowerMode(mode: String) {
        prefs.edit().putString(PREF_POWER_MODE, mode).apply()
        showPowerClickFeedback(mode)
        updateModeButtonStyles(mode)
    }

    private fun adjustIntPreference(
        key: String,
        delta: Int,
        min: Int,
        max: Int,
        defaultValue: Int
    ) {
        val current = prefs.getInt(key, defaultValue)
        val updated = (current + delta).coerceIn(min, max)
        prefs.edit().putInt(key, updated).apply()
        refreshPowerSection()
    }

    private fun refreshPowerSection() {
        val powerMode = prefs.getString(PREF_POWER_MODE, DEFAULT_POWER_MODE).orEmpty().ifBlank { DEFAULT_POWER_MODE }
        val allowedUnplugMinutes = prefs.getInt(PREF_POWER_ALLOWED_UNPLUG_MINUTES, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        val warningGraceMinutes = prefs.getInt(PREF_POWER_WARNING_GRACE_MINUTES, DEFAULT_WARNING_GRACE_MINUTES)
        val criticalBatteryPercent = prefs.getInt(PREF_POWER_CRITICAL_BATTERY_PERCENT, DEFAULT_CRITICAL_BATTERY_PERCENT)
        val keepScreenOnWhileCharging = prefs.getBoolean(
            PREF_KEEP_SCREEN_ON_WHILE_CHARGING,
            DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING
        )

        txtAllowedUnplugValue.text = "$allowedUnplugMinutes MIN"
        txtWarningGraceValue.text = "$warningGraceMinutes MIN"
        txtCriticalBatteryValue.text = "$criticalBatteryPercent %"
        txtPowerStatusValue.text = buildPowerStatus(powerMode)
        btnKeepScreenOnWhileCharging.text = if (keepScreenOnWhileCharging) {
            getString(R.string.keep_screen_on_charging_enabled)
        } else {
            getString(R.string.keep_screen_on_charging_disabled)
        }
        btnKeepScreenOnWhileCharging.backgroundTintList = ColorStateList.valueOf(
            if (keepScreenOnWhileCharging) 0xFF2E8B57.toInt() else 0xFF3A3F45.toInt()
        )

        updateModeButtonStyles(powerMode)
    }

    private fun refreshStatisticsSection() {
        val todayMeters = prefs.getLong(PREF_DISTANCE_TODAY_METERS, 0L)
        val totalMeters = prefs.getLong(PREF_DISTANCE_TOTAL_METERS, 0L)
        txtTodayDistanceValue.text = formatDistance(todayMeters)
        txtTotalDistanceValue.text = formatDistance(totalMeters)
    }

    private fun refreshGpsDiagnosticsSection() {
        val signal = prefs.getString(PREF_GPS_LAST_SIGNAL, "WEAK").orEmpty().ifBlank { "WEAK" }
        val accuracyMeters = prefs.getFloat(PREF_GPS_LAST_ACCURACY_METERS, -1f)
        val ignoredReason = prefs.getString(PREF_GPS_LAST_IGNORED_REASON, "NONE").orEmpty().ifBlank { "NONE" }

        txtGpsSignalValue.text = signal
        txtGpsAccuracyValue.text = if (accuracyMeters >= 0f) {
            String.format(Locale.ROOT, "%.1f M", accuracyMeters)
        } else {
            getString(R.string.gps_no_accuracy)
        }
        txtGpsIgnoredReasonValue.text = ignoredReason
    }

    private fun updateModeButtonStyles(powerMode: String) {
        val activeColor = 0xFF3AAE63.toInt()
        val inactiveColor = 0xFF3A3F45.toInt()

        btnPowerOff.backgroundTintList =
            ColorStateList.valueOf(if (powerMode == POWER_MODE_ALWAYS_ON) activeColor else inactiveColor)
        btnPowerWarning.backgroundTintList =
            ColorStateList.valueOf(if (powerMode == POWER_MODE_BATTERY_SAVER) activeColor else inactiveColor)
        btnPowerSleep.backgroundTintList =
            ColorStateList.valueOf(if (powerMode == POWER_MODE_POWER_SLEEP) activeColor else inactiveColor)
    }

    private fun buildPowerStatus(powerMode: String): String {
        val selectedModeText = getString(R.string.power_selected_mode_format, powerModeLabel(powerMode))
        val descriptionText = powerModeDescription(powerMode)

        val powerSourceText = if (latestPluggedIn) {
            getString(R.string.power_status_plugged)
        } else {
            getString(R.string.power_status_on_battery)
        }
        return "$selectedModeText\n$descriptionText\n$powerSourceText\n${batteryStatusLine()}"
    }

    private fun readBatteryPercent(batteryIntent: Intent?): Int? {
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
    }

    private fun formatDistance(meters: Long): String {
        return if (meters >= 1000L) {
            String.format(Locale.ROOT, "%.1f KM", meters / 1000f)
        } else {
            "$meters M"
        }
    }

    private fun applyKeepScreenOnWhileCharging() {
        val keepScreenOn = isCurrentlyPluggedIn() &&
            prefs.getBoolean(PREF_KEEP_SCREEN_ON_WHILE_CHARGING, DEFAULT_KEEP_SCREEN_ON_WHILE_CHARGING)
        if (keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun isCurrentlyPluggedIn(): Boolean {
        return latestPluggedIn
    }

    private fun registerBatteryReceiver() {
        if (isBatteryReceiverRegistered) {
            return
        }
        registerReceiver(batteryStatusReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        isBatteryReceiverRegistered = true
    }

    private fun unregisterBatteryReceiver() {
        if (!isBatteryReceiverRegistered) {
            return
        }
        try {
            unregisterReceiver(batteryStatusReceiver)
        } catch (_: IllegalArgumentException) {
        } finally {
            isBatteryReceiverRegistered = false
        }
    }

    private fun refreshBatterySnapshot() {
        updateBatterySnapshot(registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
    }

    private fun updateBatterySnapshot(batteryIntent: Intent?) {
        latestPluggedIn = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val freshPercent = readBatteryPercent(batteryIntent)
        if (freshPercent != null) {
            latestBatteryPercent = freshPercent
        }
    }

    private fun startGpsDiagnosticsRefresh() {
        gpsDiagnosticsRefreshHandler.removeCallbacks(gpsDiagnosticsRefreshRunnable)
        gpsDiagnosticsRefreshHandler.post(gpsDiagnosticsRefreshRunnable)
    }

    private fun stopGpsDiagnosticsRefresh() {
        gpsDiagnosticsRefreshHandler.removeCallbacks(gpsDiagnosticsRefreshRunnable)
    }

    private fun showPowerClickFeedback(mode: String) {
        txtPowerStatusValue.text = buildSavedPowerStatus(mode)
        pendingPowerStatusRefresh?.let { powerFeedbackHandler.removeCallbacks(it) }
        val refreshRunnable = Runnable {
            refreshPowerSection()
            pendingPowerStatusRefresh = null
        }
        pendingPowerStatusRefresh = refreshRunnable
        powerFeedbackHandler.postDelayed(refreshRunnable, 450L)
    }

    private fun powerModeLabel(mode: String): String {
        return when (mode) {
            POWER_MODE_BATTERY_SAVER -> getString(R.string.power_mode_warning_label)
            POWER_MODE_POWER_SLEEP -> getString(R.string.power_mode_sleep_label)
            else -> getString(R.string.power_mode_off_label)
        }
    }

    private fun powerModeDescription(mode: String): String {
        return when (mode) {
            POWER_MODE_BATTERY_SAVER -> getString(R.string.power_mode_warning_description)
            POWER_MODE_POWER_SLEEP -> getString(R.string.power_mode_sleep_description)
            else -> getString(R.string.power_mode_off_description)
        }
    }

    private fun buildSavedPowerStatus(mode: String): String {
        return buildString {
            append(getString(R.string.power_mode_saved))
            append('\n')
            append(getString(R.string.power_selected_mode_format, powerModeLabel(mode)))
            append('\n')
            append(powerModeDescription(mode))
        }
    }

    private fun batteryStatusLine(): String {
        return latestBatteryPercent?.let {
            getString(R.string.power_status_battery_percent_format, it)
        } ?: getString(R.string.power_status_battery_unknown)
    }

    private fun styleStepperButtons(vararg buttons: Button) {
        val strokeColor = Color.parseColor("#D6DDE4")
        val fillColor = Color.parseColor("#3A3F45")
        val horizontalPadding = dpToPx(20)
        val verticalPadding = dpToPx(8)
        buttons.forEach { button ->
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(fillColor)
                setStroke(dpToPx(2), strokeColor)
            }
            button.background = background
            button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    private fun showAdminPinDialog(onSuccess: () -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            transformationMethod = PasswordTransformationMethod.getInstance()
            hint = getString(R.string.admin_pin_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_pin_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val enteredPin = input.text?.toString().orEmpty()
                val expectedPin = prefs.getString(PREF_ADMIN_PIN, DEFAULT_ADMIN_PIN).orEmpty().ifBlank { DEFAULT_ADMIN_PIN }
                if (enteredPin == expectedPin) {
                    onSuccess()
                } else {
                    Toast.makeText(this, getString(R.string.wrong_pin), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
