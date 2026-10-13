package com.rehab2.trialupdate

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.rehab2.R
import java.io.File

/**
 * Trial-only update screen. Everything runs only on a button press:
 * no check on start, no WorkManager, no alarms.
 */
class TrialUpdateActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateClient = TrialUpdateClient()

    private lateinit var txtStatus: TextView
    private lateinit var btnCheck: Button
    private lateinit var btnInstall: Button

    private var latestRelease: TrialUpdateClient.TrialRelease? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trial_update)

        txtStatus = findViewById(R.id.trial_status)
        btnCheck = findViewById(R.id.trial_btn_check)
        btnInstall = findViewById(R.id.trial_btn_install)

        txtStatus.text = "${installedVersionLabel()}\n\nPritisnite »Preveri posodobitev«."
        btnCheck.setOnClickListener { checkForUpdate() }
        btnInstall.setOnClickListener { downloadAndInstall() }
        refreshButtons()
    }

    private fun checkForUpdate() {
        if (busy) return
        busy = true
        latestRelease = null
        refreshButtons()
        txtStatus.text = "Preverjam posodobitev ..."

        Thread {
            val statusText: String
            var release: TrialUpdateClient.TrialRelease? = null
            try {
                val fetched = updateClient.fetchTrialRelease()
                val installed = installedVersionCode()
                statusText = buildString {
                    appendLine(installedVersionLabel())
                    appendLine("Oddaljena verzija: ${fetched.versionCode} (${fetched.tagName})")
                    appendLine()
                    if (fetched.versionCode > installed) {
                        append("Na voljo je novejša trial verzija.")
                    } else {
                        append("Nameščena je že najnovejša trial verzija.")
                    }
                }
                if (fetched.versionCode > installed) {
                    release = fetched
                }
            } catch (error: TrialUpdateClient.TrialUpdateException) {
                Log.e(TAG, "Trial update check failed reason=${error.reason}", error)
                showResult("${installedVersionLabel()}\n\n${error.message}", null)
                return@Thread
            } catch (error: Exception) {
                Log.e(TAG, "Trial update check failed", error)
                showResult("${installedVersionLabel()}\n\nPreverjanje ni uspelo: neznana napaka.", null)
                return@Thread
            }
            showResult(statusText, release)
        }.start()
    }

    private fun downloadAndInstall() {
        if (busy) return
        val release = latestRelease ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            txtStatus.text = "Dovolite nameščanje iz te aplikacije, nato se vrnite in znova pritisnite »Prenesi in namesti«."
            openUnknownAppsSettings()
            return
        }

        busy = true
        refreshButtons()
        txtStatus.text = "Prenašam trial verzijo ${release.versionCode} ..."

        Thread {
            val downloadResult = TrialApkDownloader(this).download(release.apkUrl)
            val apkFile = downloadResult.file
            if (!downloadResult.success || apkFile == null) {
                showResult(downloadResult.message, release)
                return@Thread
            }

            val verification = TrialApkVerifier(this).verify(apkFile, release.versionCode)
            if (!verification.valid) {
                showResult("Namestitev zavrnjena: ${verification.message}", null)
                return@Thread
            }

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                busy = false
                refreshButtons()
                openInstaller(apkFile)
            }
        }.start()
    }

    private fun openInstaller(apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            txtStatus.text = "APK preverjen. Odpiram namestitev ..."
            startActivity(installIntent)
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Trial installer handoff failed", error)
            txtStatus.text = "Namestitve ni bilo mogoče odpreti."
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Trial APK not shareable via FileProvider", error)
            txtStatus.text = "Namestitve ni bilo mogoče odpreti: datoteka ni dosegljiva."
        } catch (error: SecurityException) {
            Log.e(TAG, "Trial installer handoff blocked", error)
            txtStatus.text = "Namestitve ni bilo mogoče odpreti."
        }
    }

    private fun openUnknownAppsSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
        } catch (error: ActivityNotFoundException) {
            Log.e(TAG, "Unknown app sources settings not available", error)
            txtStatus.text = "Nastavitve za nameščanje ni bilo mogoče odpreti."
        }
    }

    private fun showResult(text: String, release: TrialUpdateClient.TrialRelease?) {
        mainHandler.post {
            if (isFinishing || isDestroyed) return@post
            busy = false
            latestRelease = release
            txtStatus.text = text
            refreshButtons()
        }
    }

    private fun refreshButtons() {
        btnCheck.isEnabled = !busy
        btnInstall.isEnabled = !busy && latestRelease != null
    }

    private fun installedVersionLabel(): String {
        val info = installedPackageInfo()
        return "Nameščena verzija: ${info.versionName ?: "-"} (${versionCodeOf(info)})"
    }

    private fun installedVersionCode(): Long = versionCodeOf(installedPackageInfo())

    private fun installedPackageInfo(): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun versionCodeOf(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    companion object {
        private const val TAG = "NovaRehabTrialUpdater"
    }
}
