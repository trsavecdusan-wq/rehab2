package com.rehab2

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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

        val aacTiles = listOf(
            R.id.tileAacZejna to "ŽEJNA",
            R.id.tileAacLacna to "LAČNA",
            R.id.tileAacPomoc to "POMOČ",
            R.id.tileAacDa to "DA",
            R.id.tileAacWc to "WC",
            R.id.tileAacDobro to "DOBRO",
            R.id.tileAacSlabo to "SLABO",
            R.id.tileAacNe to "NE",
            R.id.tileAacUtrujena to "UTRUJENA",
            R.id.tileAacMraz to "MRAZ",
            R.id.tileAacVroce to "VROČE",
            R.id.tileAacBolecina to "BOLEČINA",
            R.id.tileAacDomov to "DOMOV",
            R.id.tileAacZdravnik to "ZDRAVNIK",
            R.id.tileAacDruzina to "DRUŽINA",
            R.id.tileAacStop to "STOP"
        )

        aacTiles.forEach { (tileId, label) ->
            findViewById<View>(tileId).setOnClickListener {
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
            }
        }
    }
}