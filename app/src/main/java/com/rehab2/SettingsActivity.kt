package com.rehab2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnBackSettings).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnRadioSettings).setOnClickListener {
            startActivity(Intent(this, RadioSettingsActivity::class.java))
        }
    }
}