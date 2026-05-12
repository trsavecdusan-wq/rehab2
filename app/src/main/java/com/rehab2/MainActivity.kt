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

        val aacButtonIds = listOf(
            R.id.btnAacZejna,
            R.id.btnAacLacna,
            R.id.btnAacPomoc,
            R.id.btnAacDa,
            R.id.btnAacWc,
            R.id.btnAacDobro,
            R.id.btnAacSlabo,
            R.id.btnAacNe,
            R.id.btnAacUtrujena,
            R.id.btnAacMraz,
            R.id.btnAacVroce,
            R.id.btnAacBolecina,
            R.id.btnAacDomov,
            R.id.btnAacZdravnik,
            R.id.btnAacDruzina,
            R.id.btnAacStop
        )

        aacButtonIds.forEach { buttonId ->
            val button: Button = findViewById(buttonId)
            button.setOnClickListener {
                Toast.makeText(this, button.text.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
