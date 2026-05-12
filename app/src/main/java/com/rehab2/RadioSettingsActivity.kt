package com.rehab2

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RadioSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radio_settings)

        findViewById<Button>(R.id.btnBackRadioSettings).setOnClickListener {
            finish()
        }

        val visibleButtons = listOf(
            R.id.btnVisibleStation1,
            R.id.btnVisibleStation2,
            R.id.btnVisibleStation3,
            R.id.btnVisibleStation4,
            R.id.btnVisibleStation5,
            R.id.btnVisibleStation6
        )

        val editButtons = listOf(
            R.id.btnEditStation1,
            R.id.btnEditStation2,
            R.id.btnEditStation3,
            R.id.btnEditStation4,
            R.id.btnEditStation5,
            R.id.btnEditStation6
        )

        visibleButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                Toast.makeText(this, "Vidnost postaje", Toast.LENGTH_SHORT).show()
            }
        }

        editButtons.forEach { buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                Toast.makeText(this, "Uredi postajo", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnPreviousPage).setOnClickListener {
            Toast.makeText(this, "Prejšnja stran", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnNextPage).setOnClickListener {
            Toast.makeText(this, "Naslednja stran", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnAddStation).setOnClickListener {
            Toast.makeText(this, "Dodaj postajo", Toast.LENGTH_SHORT).show()
        }
    }
}