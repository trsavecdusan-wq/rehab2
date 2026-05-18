package com.rehab2

import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_POWER_MODE = "power_mode"
        private const val PREF_POWER_ALLOWED_UNPLUG_MINUTES = "power_allowed_unplug_minutes"
        private const val PREF_POWER_WARNING_GRACE_MINUTES = "power_warning_grace_minutes"
        private const val PREF_POWER_CRITICAL_BATTERY_PERCENT = "power_critical_battery_percent"
        private const val PREF_POWER_ADMIN_BYPASS_UNTIL = "power_admin_bypass_until"
        private const val PREF_DISTANCE_TODAY_METERS = "distance_today_meters"
        private const val PREF_DISTANCE_TOTAL_METERS = "distance_total_meters"
        private const val PREF_DISTANCE_WEEK_METERS = "distance_week_meters"
        private const val PREF_DISTANCE_MONTH_METERS = "distance_month_meters"
        private const val PREF_DISTANCE_YEAR_METERS = "distance_year_meters"

        private const val POWER_MODE_ALWAYS_ON = "ALWAYS_ON"
        private const val POWER_MODE_BATTERY_SAVER = "BATTERY_SAVER"
        private const val POWER_MODE_POWER_SLEEP = "POWER_SLEEP"

        private const val DEFAULT_POWER_MODE = POWER_MODE_ALWAYS_ON
        private const val DEFAULT_ALLOWED_UNPLUG_MINUTES = 15
        private const val DEFAULT_WARNING_GRACE_MINUTES = 5
        private const val DEFAULT_CRITICAL_BATTERY_PERCENT = 20
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var btnPowerOff: Button
    private lateinit var btnPowerWarning: Button
    private lateinit var btnPowerSleep: Button
    private lateinit var txtAllowedUnplugValue: TextView
    private lateinit var txtWarningGraceValue: TextView
    private lateinit var txtCriticalBatteryValue: TextView
    private lateinit var txtPowerStatusValue: TextView
    private lateinit var txtTodayDistanceValue: TextView
    private lateinit var txtTotalDistanceValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)

        btnPowerOff = findViewById(R.id.btnPowerModeOff)
        btnPowerWarning = findViewById(R.id.btnPowerModeWarning)
        btnPowerSleep = findViewById(R.id.btnPowerModeSleep)
        txtAllowedUnplugValue = findViewById(R.id.txtAllowedUnplugValue)
        txtWarningGraceValue = findViewById(R.id.txtWarningGraceValue)
        txtCriticalBatteryValue = findViewById(R.id.txtCriticalBatteryValue)
        txtPowerStatusValue = findViewById(R.id.txtPowerStatusValue)
        txtTodayDistanceValue = findViewById(R.id.txtTodayDistanceValue)
        txtTotalDistanceValue = findViewById(R.id.txtTotalDistanceValue)

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

        refreshPowerSection()
        refreshStatisticsSection()
    }

    override fun onResume() {
        super.onResume()
        refreshPowerSection()
        refreshStatisticsSection()
    }

    private fun setPowerMode(mode: String) {
        prefs.edit().putString(PREF_POWER_MODE, mode).apply()
        refreshPowerSection()
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

        txtAllowedUnplugValue.text = "$allowedUnplugMinutes MIN"
        txtWarningGraceValue.text = "$warningGraceMinutes MIN"
        txtCriticalBatteryValue.text = "$criticalBatteryPercent %"
        txtPowerStatusValue.text = buildPowerStatus(powerMode)

        updateModeButtonStyles(powerMode)
    }

    private fun refreshStatisticsSection() {
        val todayMeters = prefs.getLong(PREF_DISTANCE_TODAY_METERS, 0L)
        val totalMeters = prefs.getLong(PREF_DISTANCE_TOTAL_METERS, 0L)
        txtTodayDistanceValue.text = formatDistance(todayMeters)
        txtTotalDistanceValue.text = formatDistance(totalMeters)
    }

    private fun updateModeButtonStyles(powerMode: String) {
        val activeColor = 0xFF2E8B57.toInt()
        val inactiveColor = 0xFF3A3F45.toInt()

        btnPowerOff.setBackgroundColor(if (powerMode == POWER_MODE_ALWAYS_ON) activeColor else inactiveColor)
        btnPowerWarning.setBackgroundColor(if (powerMode == POWER_MODE_BATTERY_SAVER) activeColor else inactiveColor)
        btnPowerSleep.setBackgroundColor(if (powerMode == POWER_MODE_POWER_SLEEP) activeColor else inactiveColor)
    }

    private fun buildPowerStatus(powerMode: String): String {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        if (plugged) {
            return "NAPAJANJE PRIKLOPLJENO"
        }

        if (powerMode == POWER_MODE_ALWAYS_ON) {
            return "DELO NA BATERIJI"
        }

        val bypassUntil = prefs.getLong(PREF_POWER_ADMIN_BYPASS_UNTIL, 0L)
        if (System.currentTimeMillis() < bypassUntil) {
            return "DELO NA BATERIJI"
        }

        val warningMinutes = prefs.getInt(PREF_POWER_ALLOWED_UNPLUG_MINUTES, DEFAULT_ALLOWED_UNPLUG_MINUTES)
        val graceMinutes = prefs.getInt(PREF_POWER_WARNING_GRACE_MINUTES, DEFAULT_WARNING_GRACE_MINUTES)
        val warningWindowMs = (warningMinutes + graceMinutes) * 60_000L
        val criticalBatteryPercent = prefs.getInt(PREF_POWER_CRITICAL_BATTERY_PERCENT, DEFAULT_CRITICAL_BATTERY_PERCENT)
        val batteryPercent = readBatteryPercent(batteryIntent)

        return if (powerMode == POWER_MODE_POWER_SLEEP && batteryPercent <= criticalBatteryPercent) {
            "POWER SLEEP AKTIVEN"
        } else if (powerMode == POWER_MODE_BATTERY_SAVER && batteryPercent <= criticalBatteryPercent) {
            "DELO NA BATERIJI"
        } else if (warningWindowMs > 0L && batteryPercent <= criticalBatteryPercent && powerMode != POWER_MODE_ALWAYS_ON) {
            if (powerMode == POWER_MODE_POWER_SLEEP) "POWER SLEEP AKTIVEN" else "DELO NA BATERIJI"
        } else {
            "DELO NA BATERIJI"
        }
    }

    private fun readBatteryPercent(batteryIntent: Intent?): Int {
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private fun formatDistance(meters: Long): String {
        return if (meters >= 1000L) {
            String.format(Locale.ROOT, "%.1f KM", meters / 1000f)
        } else {
            "$meters M"
        }
    }
}
