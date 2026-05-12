package com.rehab2

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
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

        val btnAacZejna: Button = findViewById(R.id.btnAacZejna)
        btnAacZejna.setOnClickListener {
            Toast.makeText(this, btnAacZejna.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacLacna: Button = findViewById(R.id.btnAacLacna)
        btnAacLacna.setOnClickListener {
            Toast.makeText(this, btnAacLacna.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacPomoc: Button = findViewById(R.id.btnAacPomoc)
        btnAacPomoc.setOnClickListener {
            Toast.makeText(this, btnAacPomoc.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacWc: Button = findViewById(R.id.btnAacWc)
        btnAacWc.setOnClickListener {
            Toast.makeText(this, btnAacWc.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacDobro: Button = findViewById(R.id.btnAacDobro)
        btnAacDobro.setOnClickListener {
            Toast.makeText(this, btnAacDobro.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacSlabo: Button = findViewById(R.id.btnAacSlabo)
        btnAacSlabo.setOnClickListener {
            Toast.makeText(this, btnAacSlabo.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacUtrujena: Button = findViewById(R.id.btnAacUtrujena)
        btnAacUtrujena.setOnClickListener {
            Toast.makeText(this, btnAacUtrujena.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacMraz: Button = findViewById(R.id.btnAacMraz)
        btnAacMraz.setOnClickListener {
            Toast.makeText(this, btnAacMraz.text.toString(), Toast.LENGTH_SHORT).show()
        }
        val btnAacVroce: Button = findViewById(R.id.btnAacVroce)
        btnAacVroce.setOnClickListener {
            Toast.makeText(this, btnAacVroce.text.toString(), Toast.LENGTH_SHORT).show()
        }
    }
}
