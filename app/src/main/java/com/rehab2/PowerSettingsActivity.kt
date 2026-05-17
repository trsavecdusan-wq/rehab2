package com.rehab2

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class PowerSettingsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_POWER_MODE = "power_mode"
        private const val PREF_POWER_ALLOWED_UNPLUG_MINUTES = "power_allowed_unplug_minutes"
        private const val PREF_POWER_WARNING_GRACE_MINUTES = "power_warning_grace_minutes"
        private const val PREF_POWER_CRITICAL_BATTERY_PERCENT = "power_critical_battery_percent"

        private const val POWER_MODE_ALWAYS_ON = "ALWAYS_ON"
        private const val POWER_MODE_BATTERY_SAVER = "BATTERY_SAVER"
        private const val POWER_MODE_POWER_SLEEP = "POWER_SLEEP"

        private const val DEFAULT_POWER_MODE = POWER_MODE_ALWAYS_ON
        private const val DEFAULT_ALLOWED_UNPLUG_MINUTES = 15
        private const val DEFAULT_WARNING_GRACE_MINUTES = 5
        private const val DEFAULT_CRITICAL_BATTERY_PERCENT = 20
    }

    private lateinit var modeSpinner: Spinner
    private lateinit var unplugSpinner: Spinner
    private lateinit var graceSpinner: Spinner
    private lateinit var criticalSpinner: Spinner

    private val modeOptions = listOf(
        POWER_MODE_ALWAYS_ON,
        POWER_MODE_BATTERY_SAVER,
        POWER_MODE_POWER_SLEEP
    )
    private val unplugOptions = listOf("5", "10", "15", "30")
    private val graceOptions = listOf("1", "3", "5", "10")
    private val criticalOptions = listOf("10", "15", "20", "30")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_power_settings)

        modeSpinner = findViewById(R.id.spinnerPowerMode)
        unplugSpinner = findViewById(R.id.spinnerAllowedUnplug)
        graceSpinner = findViewById(R.id.spinnerWarningGrace)
        criticalSpinner = findViewById(R.id.spinnerCriticalBattery)

        setupSpinners()
        loadPowerSettings()

        findViewById<Button>(R.id.btnSavePowerSettings).setOnClickListener {
            savePowerSettings()
            finish()
        }

        findViewById<Button>(R.id.btnBackPowerSettings).setOnClickListener {
            finish()
        }
    }

    private fun setupSpinners() {
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeOptions)
        unplugSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, unplugOptions)
        graceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, graceOptions)
        criticalSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, criticalOptions)
    }

    private fun loadPowerSettings() {
        val prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)

        setSpinnerSelection(modeSpinner, modeOptions, prefs.getString(PREF_POWER_MODE, DEFAULT_POWER_MODE), DEFAULT_POWER_MODE)
        setSpinnerSelection(
            unplugSpinner,
            unplugOptions,
            prefs.getInt(PREF_POWER_ALLOWED_UNPLUG_MINUTES, DEFAULT_ALLOWED_UNPLUG_MINUTES).toString(),
            DEFAULT_ALLOWED_UNPLUG_MINUTES.toString()
        )
        setSpinnerSelection(
            graceSpinner,
            graceOptions,
            prefs.getInt(PREF_POWER_WARNING_GRACE_MINUTES, DEFAULT_WARNING_GRACE_MINUTES).toString(),
            DEFAULT_WARNING_GRACE_MINUTES.toString()
        )
        setSpinnerSelection(
            criticalSpinner,
            criticalOptions,
            prefs.getInt(PREF_POWER_CRITICAL_BATTERY_PERCENT, DEFAULT_CRITICAL_BATTERY_PERCENT).toString(),
            DEFAULT_CRITICAL_BATTERY_PERCENT.toString()
        )
    }

    private fun savePowerSettings() {
        val prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_POWER_MODE, modeSpinner.selectedItem.toString())
            .putInt(PREF_POWER_ALLOWED_UNPLUG_MINUTES, unplugSpinner.selectedItem.toString().toInt())
            .putInt(PREF_POWER_WARNING_GRACE_MINUTES, graceSpinner.selectedItem.toString().toInt())
            .putInt(PREF_POWER_CRITICAL_BATTERY_PERCENT, criticalSpinner.selectedItem.toString().toInt())
            .apply()
    }

    private fun setSpinnerSelection(
        spinner: Spinner,
        options: List<String>,
        selectedValue: String?,
        defaultValue: String
    ) {
        val safeSelected = selectedValue ?: defaultValue
        val index = options.indexOf(safeSelected).takeIf { it >= 0 }
            ?: options.indexOf(defaultValue).takeIf { it >= 0 }
            ?: 0
        spinner.setSelection(index)
    }
}
