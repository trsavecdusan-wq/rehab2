package com.rehab2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.music.LocalMusicImporter

class LocalMusicActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_PICK_FOLDER = 1001
        private const val PREFS_NAME = "local_music_prefs"
        private const val PREF_SELECTED_TREE_URI = "selected_tree_uri"
    }

    private lateinit var statusText: TextView
    private lateinit var resultsContainer: LinearLayout
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selectedTreeUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_music)

        statusText = findViewById(R.id.txtLocalMusicStatus)
        resultsContainer = findViewById(R.id.localMusicResultsContainer)

        selectedTreeUri = restoreTreeUri()
        selectedTreeUri?.let {
            statusText.text = "Mapa izbrana"
        }

        findViewById<Button>(R.id.btnBackLocalMusic).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnPickLocalMusicFolder).setOnClickListener {
            openFolderPicker()
        }

        findViewById<Button>(R.id.btnImportLocalMusic).setOnClickListener {
            val treeUri = selectedTreeUri
            if (treeUri == null) {
                Toast.makeText(this, "Najprej izberi mapo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            resultsContainer.removeAllViews()
            Thread {
                val importer = LocalMusicImporter(this)
                val result = importer.importFromTree(treeUri) { message ->
                    mainHandler.post {
                        statusText.text = message
                    }
                }

                mainHandler.post {
                    statusText.text = buildString {
                        append("Uvoz končan")
                        append("\nUvoženih novih datotek: ${result.importedTracks.size}")
                        append("\nPreskočenih obstoječih datotek: ${result.skippedTracks.size}")
                        if (result.failedTracks.isNotEmpty()) {
                            append("\nNapaka pri branju datoteke")
                        }
                    }
                    renderResults(result)
                }
            }.start()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_FOLDER || resultCode != RESULT_OK) {
            return
        }

        val treeUri = data?.data ?: return
        val flags = data.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(treeUri, flags)
        selectedTreeUri = treeUri
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_SELECTED_TREE_URI, treeUri.toString())
            .apply()
        statusText.text = "Mapa izbrana"
    }

    @Suppress("DEPRECATION")
    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_FOLDER)
    }

    private fun restoreTreeUri(): Uri? {
        val uriString = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(PREF_SELECTED_TREE_URI, null)
        return uriString?.let(Uri::parse)
    }

    private fun renderResults(result: LocalMusicImporter.ImportResult) {
        resultsContainer.removeAllViews()

        result.importedTracks.forEach { name ->
            resultsContainer.addView(createResultText("UVOŽENO: $name"))
        }
        result.skippedTracks.forEach { name ->
            resultsContainer.addView(createResultText("PRESKOČENO: $name"))
        }
        result.failedTracks.forEach { name ->
            resultsContainer.addView(createResultText("NAPAKA: $name"))
        }
    }

    private fun createResultText(text: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8) }
            this.text = text
            textSize = 16f
            setTextColor(0xFFF4F7FA.toInt())
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
