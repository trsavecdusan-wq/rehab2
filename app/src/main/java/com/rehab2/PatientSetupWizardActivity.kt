package com.rehab2

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.aac.AacContentBootstrap
import com.rehab2.aac.AacEditorStorage
import com.rehab2.aac.AacStarterContentV1
import com.rehab2.aac.AacStoragePaths
import com.rehab2.aac.PatientProfileSettings
import java.io.File
import java.util.Locale

class PatientSetupWizardActivity : AppCompatActivity() {
    private companion object {
        private const val PREFS_FILE = "rehab2_prefs"
        private const val PREF_AAC_GRID_SIZE = "aac_grid_size"
        private const val PREF_AAC_PERSISTENT_TOP_ROW_COUNT = "aac_persistent_top_row_count"
        private const val DEFAULT_AAC_GRID_SIZE = 5
        private const val DEFAULT_TOP_ROW_COUNT = 5
        private const val REQUEST_PICK_PATIENT_PHOTO = 4301
        private val GRID_OPTIONS = listOf(3, 4, 5)
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var content: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var helperView: TextView
    private lateinit var backButton: Button
    private lateinit var skipButton: Button
    private lateinit var nextButton: Button

    private var step = 0
    private var patientName = ""
    private var mainLanguage = ""
    private var gridSize = DEFAULT_AAC_GRID_SIZE
    private var selectedPhotoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_FILE, MODE_PRIVATE)
        loadExistingValues()
        buildLayout()
        renderStep()
    }

    private fun loadExistingValues() {
        val profile = PatientProfileSettings.load(this)
        patientName = profile.firstName
        mainLanguage = profile.mainLanguage
        gridSize = prefs.getInt(PREF_AAC_GRID_SIZE, DEFAULT_AAC_GRID_SIZE).coerceIn(3, 5)
        selectedPhotoFile = existingPatientPhotoFile()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121417.toInt())
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        titleView = TextView(this).apply {
            setTextColor(0xFFF4F7FA.toInt())
            textSize = 30f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val titleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(titleView, titleParams)

        helperView = TextView(this).apply {
            setTextColor(0xFFC8D0D8.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(20))
        }
        val helperParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(helperView, helperParams)

        val scrollView = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(content)
        val scrollParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        root.addView(scrollView, scrollParams)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(16), 0, 0)
        }
        backButton = actionButton("NAZAJ", 0xFF3A3F45.toInt()).apply {
            setOnClickListener {
                if (step == 0) finish() else {
                    step -= 1
                    renderStep()
                }
            }
        }
        skipButton = actionButton("PRESKOČI", 0xFF3A3F45.toInt()).apply {
            setOnClickListener {
                if (step == 3) {
                    step += 1
                    renderStep()
                }
            }
        }
        nextButton = actionButton("NAPREJ", 0xFF2F5F9E.toInt()).apply {
            setOnClickListener { handleNext() }
        }
        val backParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply {
            marginEnd = dp(8)
        }
        val skipParams = LinearLayout.LayoutParams(0, dp(64), 1f).apply {
            marginEnd = dp(8)
        }
        val nextParams = LinearLayout.LayoutParams(0, dp(64), 1f)
        buttonRow.addView(backButton, backParams)
        buttonRow.addView(skipButton, skipParams)
        buttonRow.addView(nextButton, nextParams)
        val buttonRowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(buttonRow, buttonRowParams)

        setContentView(root)
    }

    private fun renderStep() {
        content.removeAllViews()
        skipButton.visibility = if (step == 3) View.VISIBLE else View.GONE
        nextButton.text = if (step == 4) "KONČAJ" else "NAPREJ"
        titleView.text = "KORAK ${step + 1}/5"
        when (step) {
            0 -> renderNameStep()
            1 -> renderLanguageStep()
            2 -> renderGridStep()
            3 -> renderPhotoStep()
            else -> renderConfirmStep()
        }
    }

    private fun renderNameStep() {
        helperView.text = "Ime pacientke"
        content.addView(sectionText("Vpišite ime, ki ga bo aplikacija uporabljala v osebnih odgovorih."))
        content.addView(editText("Ime pacientke", patientName) { patientName = it })
    }

    private fun renderLanguageStep() {
        helperView.text = "Glavni jezik"
        content.addView(sectionText("Izberite ali vpišite glavni jezik pacientke."))
        content.addView(editText("Glavni jezik", mainLanguage.ifBlank { "slovenščina" }) { mainLanguage = it })
        content.addView(choiceButton("SLOVENŠČINA") {
            mainLanguage = "slovenščina"
            renderStep()
        })
        content.addView(choiceButton("UKRAJINŠČINA") {
            mainLanguage = "ukrajinščina"
            renderStep()
        })
    }

    private fun renderGridStep() {
        helperView.text = "Velikost mreže"
        content.addView(sectionText("Večja mreža pokaže več ikon. Manjša mreža je lažja za uporabo."))
        GRID_OPTIONS.forEach { size ->
            content.addView(choiceButton("${size}x$size", selected = gridSize == size) {
                gridSize = size
                renderStep()
            })
        }
    }

    private fun renderPhotoStep() {
        helperView.text = "Fotografija pacientke"
        content.addView(sectionText("Izberite fotografijo iz galerije ali ta korak preskočite."))
        val currentPhoto = selectedPhotoFile?.takeIf { it.exists() && it.isFile && it.length() > 0L }
        if (currentPhoto != null) {
            BitmapFactory.decodeFile(currentPhoto.absolutePath)?.let { bitmap ->
                val previewView = ImageView(this).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(0xFF1E2329.toInt())
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val previewParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(260)
                ).apply {
                    bottomMargin = dp(12)
                }
                content.addView(previewView, previewParams)
            }
            content.addView(summaryText("Izbrana fotografija", currentPhoto.name))
        } else {
            content.addView(summaryText("Fotografija", "Ni izbrana."))
        }
        content.addView(choiceButton("IZBERI FOTOGRAFIJO") {
            openPatientPhotoPicker()
        })
    }

    private fun renderConfirmStep() {
        helperView.text = "Potrditev"
        content.addView(sectionText("Preverite podatke in pritisnite KONČAJ."))
        content.addView(summaryText("Ime pacientke", patientName.ifBlank { "Ni vpisano" }))
        content.addView(summaryText("Glavni jezik", mainLanguage.ifBlank { "slovenščina" }))
        content.addView(summaryText("Velikost mreže", "${gridSize}x$gridSize"))
        content.addView(summaryText("Fotografija", selectedPhotoFile?.takeIf { it.exists() && it.length() > 0L }?.name ?: "Preskočeno"))
    }

    private fun handleNext() {
        when (step) {
            0 -> {
                if (patientName.isBlank()) {
                    Toast.makeText(this, "Vpišite ime pacientke.", Toast.LENGTH_SHORT).show()
                    return
                }
                step += 1
            }
            1 -> {
                if (mainLanguage.isBlank()) mainLanguage = "slovenščina"
                step += 1
            }
            2, 3 -> step += 1
            else -> {
                saveWizardValues()
                return
            }
        }
        renderStep()
    }

    private fun saveWizardValues() {
        val currentProfile = PatientProfileSettings.load(this)
        val updatedProfile = currentProfile.copy(
            firstName = patientName,
            mainLanguage = mainLanguage.ifBlank { "slovenščina" }
        )
        val profileSaved = PatientProfileSettings.save(this, updatedProfile)
        val speechSynced = syncPatientProfileAacSpeech(updatedProfile)
        val normalizedTopRowCount = prefs.getInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, DEFAULT_TOP_ROW_COUNT)
            .coerceIn(3, gridSize)
        prefs.edit()
            .putInt(PREF_AAC_GRID_SIZE, gridSize)
            .putInt(PREF_AAC_PERSISTENT_TOP_ROW_COUNT, normalizedTopRowCount)
            .apply()

        Toast.makeText(
            this,
            when {
                profileSaved && speechSynced -> "Pacientka je pripravljena za uporabo."
                profileSaved -> "Podatki so shranjeni. Govor ikon bo morda treba preveriti."
                else -> "Nekaterih podatkov ni bilo mogoče shraniti."
            },
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    @Deprecated("Uses the platform picker without adding new storage or permissions.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_PATIENT_PHOTO) return

        val selectedUri = data?.data
        if (resultCode == Activity.RESULT_OK && selectedUri != null && saveSelectedPatientPhoto(selectedUri)) {
            Toast.makeText(this, "Fotografija je shranjena.", Toast.LENGTH_SHORT).show()
            renderStep()
        } else {
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPatientPhotoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_PATIENT_PHOTO)
        } catch (_: Exception) {
            Toast.makeText(this, "Fotografije ni bilo mogoče shraniti.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSelectedPatientPhoto(uri: Uri): Boolean {
        val patientDir = AacStoragePaths.getIconsPatientDir(this) ?: return false
        return try {
            if (!patientDir.exists() && !patientDir.mkdirs()) return false
            val targetFile = File(patientDir, "patient_profile_photo.${photoExtension(uri)}")
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                return false
            }
            selectedPhotoFile = targetFile
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun photoExtension(uri: Uri): String {
        return when (contentResolver.getType(uri)?.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/jpeg", "image/jpg" -> "jpg"
            else -> "jpg"
        }
    }

    private fun existingPatientPhotoFile(): File? {
        val patientDir = AacStoragePaths.getIconsPatientDir(this) ?: return null
        return listOf("patient_profile_photo.jpg", "patient_profile_photo.png", "patient_profile_photo.webp")
            .map { File(patientDir, it) }
            .firstOrNull { it.exists() && it.isFile && it.length() > 0L }
    }

    private fun syncPatientProfileAacSpeech(profile: PatientProfileSettings): Boolean {
        return try {
            AacContentBootstrap.ensurePatientStartupContent(this, AacStarterContentV1.items())
            profile.speechByItemId().map { (itemId, speechText) ->
                AacEditorStorage.updateSpeechTextSl(this, itemId, speechText)
            }.all { it }
        } catch (_: Exception) {
            false
        }
    }

    private fun editText(hintText: String, value: String, onChange: (String) -> Unit): EditText {
        return EditText(this).apply {
            hint = hintText
            setText(value)
            setTextColor(0xFFF4F7FA.toInt())
            setHintTextColor(0xFF9EA8B2.toInt())
            textSize = 20f
            minHeight = dp(64)
            setPadding(dp(16), 0, dp(16), 0)
            backgroundTintList = ColorStateList.valueOf(0xFF2A3138.toInt())
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) onChange(text?.toString().orEmpty())
            }
            addTextChangedListener(SimpleTextWatcher { onChange(it) })
        }
    }

    private fun choiceButton(text: String, selected: Boolean = false, onClick: () -> Unit): Button {
        return actionButton(text, if (selected) 0xFF2F5F9E.toInt() else 0xFF3A3F45.toInt()).apply {
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply {
                bottomMargin = dp(12)
            }
        }
    }

    private fun actionButton(text: String, color: Int): Button {
        return Button(this).apply {
            this.text = text
            setAllCaps(false)
            setTextColor(0xFFF4F7FA.toInt())
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            backgroundTintList = ColorStateList.valueOf(color)
        }
    }

    private fun sectionText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFC8D0D8.toInt())
            textSize = 18f
            setPadding(0, 0, 0, dp(16))
        }
    }

    private fun summaryText(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label\n$value"
            setTextColor(0xFFF4F7FA.toInt())
            textSize = 20f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = android.graphics.drawable.ColorDrawable(0xFF1E2329.toInt())
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private class SimpleTextWatcher(private val onChanged: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onChanged(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }
}
