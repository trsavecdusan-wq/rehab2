package com.rehab2

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AddRadioStationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_radio_station)

        findViewById<Button>(R.id.btnBackAddRadioStation).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnSearchByCountry).setOnClickListener {
            Toast.makeText(this, "Iskanje po državi", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSearchByGenre).setOnClickListener {
            Toast.makeText(this, "Iskanje po žanru", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSearchByName).setOnClickListener {
            Toast.makeText(this, "Iskanje po imenu", Toast.LENGTH_SHORT).show()
        }
    }
}