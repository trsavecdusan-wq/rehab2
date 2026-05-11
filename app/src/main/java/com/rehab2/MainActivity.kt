package com.rehab2

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnAacZejna).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacLacna).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacPomoc).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacWc).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacDobro).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacSlabo).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacUtrujena).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacMraz).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAacVroce).setOnClickListener {
            Toast.makeText(this, it.text.toString(), Toast.LENGTH_SHORT).show()
        }
    }
}
