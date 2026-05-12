package com.rehab2

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class RadioSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_settings)

        findViewById<Button>(R.id.btnBackRadioSettings).setOnClickListener {
            finish()
        }
    }
}