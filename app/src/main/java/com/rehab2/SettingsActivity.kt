package com.rehab2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
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

        findViewById<Button>(R.id.btnBackupSettings).setOnClickListener {
            startActivity(Intent(this, BackupSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnAacSettings).setOnClickListener {
            Toast.makeText(this, "Funkcija še ni pripravljena", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStatusSettings).setOnClickListener {
            Toast.makeText(this, "Funkcija še ni pripravljena", Toast.LENGTH_SHORT).show()
        }
    }
}
