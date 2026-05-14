package com.rehab2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
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
import com.rehab2.update.ApkDownloadManager
import com.rehab2.update.GitHubUpdateClient
import java.io.File

class BackupSettingsActivity : AppCompatActivity() {
    companion object {
        private const val CHECK_BUTTON_COLOR = 0xFF214A78.toInt()
        private const val DOWNLOAD_BUTTON_COLOR = 0xFF3E7C4A.toInt()
        private const val BUSY_BUTTON_COLOR = 0xFF5B6672.toInt()
    }

    private lateinit var txtCurrentVersion: TextView
    private lateinit var txtLatestVersion: TextView
    private lateinit var txtUpdateStatus: TextView
    private lateinit var txtReleaseNotes: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnDownloadApk: Button
    private lateinit var currentVersionName: String
    private var currentVersionCode: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateClient = GitHubUpdateClient()
    private lateinit var downloadManager: ApkDownloadManager
    private var latestRelease: GitHubUpdateClient.ReleaseInfo? = null
    private val checkedReleaseUrl: String by lazy { updateClient.getLatestReleaseUrl() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_settings)

        downloadManager = ApkDownloadManager(this)
        txtCurrentVersion = findViewById(R.id.txtCurrentVersion)
        txtLatestVersion = findViewById(R.id.txtLatestVersion)
        txtUpdateStatus = findViewById(R.id.txtUpdateStatus)
        txtReleaseNotes = findViewById(R.id.txtReleaseNotes)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnDownloadApk = findViewById(R.id.btnDownloadApk)
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        currentVersionName = packageInfo.versionName ?: "unknown"
        currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        txtCurrentVersion.text = "Trenutna verzija: $currentVersionName ($currentVersionCode)"
        txtLatestVersion.text = "Zadnja verzija: -"
        txtUpdateStatus.text = ""
        txtReleaseNotes.text = ""

        findViewById<Button>(R.id.btnBackBackupSettings).setOnClickListener {
            finish()
        }

        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

        btnDownloadApk.setOnClickListener {
            val release = latestRelease ?: return@setOnClickListener
            downloadLatestApk(release)
        }
    }

    private fun checkForUpdate() {
        setCheckButtonLoading(true)
        btnDownloadApk.isEnabled = false
        txtUpdateStatus.text = "Preverjam posodobitev ..."
        txtReleaseNotes.text = ""

        Thread {
            try {
                val release = updateClient.fetchLatestRelease()
                val remoteVersion = release.tagName.removePrefix("v")
                val comparison = compareVersions(remoteVersion, currentVersionName)

                mainHandler.post {
                    latestRelease = release
                    txtLatestVersion.text = "Zadnja verzija: $remoteVersion"
                    txtReleaseNotes.text = release.body

                    if (comparison > 0 && !release.apkUrl.isNullOrBlank()) {
                        txtUpdateStatus.text = "Posodobitev je na voljo: $remoteVersion"
                        btnDownloadApk.isEnabled = true
                    } else {
                        txtUpdateStatus.text = "Ni nove posodobitve."
                        btnDownloadApk.isEnabled = false
                    }
                    setCheckButtonLoading(false)
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Preverjanje ni uspelo"
                Log.e("NovaRehabUpdater", "Update check failed: $message", error)
                mainHandler.post {
                    latestRelease = null
                    btnDownloadApk.isEnabled = false
                    txtUpdateStatus.text = toUserFriendlyCheckStatus(message)
                    txtReleaseNotes.text = ""
                    setCheckButtonLoading(false)
                }
            }
        }.start()
    }

    private fun downloadLatestApk(release: GitHubUpdateClient.ReleaseInfo) {
        val apkUrl = release.apkUrl ?: return
        setDownloadButtonLoading(true)
        txtUpdateStatus.text = "Prenašam APK ..."

        Thread {
            val result = downloadManager.downloadLatestApk(apkUrl) { status ->
                mainHandler.post {
                    txtUpdateStatus.text = when {
                        status.contains("Prena") -> "Prenašam APK ..."
                        else -> txtUpdateStatus.text
                    }
                }
            }

            mainHandler.post {
                if (result.success && result.file != null) {
                    txtUpdateStatus.text = "Prenos končan. Odpiram namestitev ..."
                    openInstallHandoff(result.file)
                } else {
                    txtUpdateStatus.text = toUserFriendlyDownloadStatus(result.message)
                    restoreDownloadButtonState()
                }
            }
        }.start()
    }

    private fun compareVersions(remote: String, local: String): Int {
        val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(remoteParts.size, localParts.size)

        for (index in 0 until maxSize) {
            val remoteValue = remoteParts.getOrElse(index) { 0 }
            val localValue = localParts.getOrElse(index) { 0 }
            if (remoteValue != localValue) {
                return remoteValue.compareTo(localValue)
            }
        }
        return 0
    }

    private fun openInstallHandoff(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            txtUpdateStatus.text = "Dovoli nameščanje iz te aplikacije in poskusi znova."
            restoreDownloadButtonState()
            openUnknownAppsSettings()
            return
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(installIntent)
            txtUpdateStatus.text = "Namestitev je bila predana Androidu."
            restoreDownloadButtonState()
        } catch (error: ActivityNotFoundException) {
            Log.e("NovaRehabUpdater", "Installer handoff failed", error)
            txtUpdateStatus.text = "Namestitve ni bilo mogoče odpreti."
            restoreDownloadButtonState()
        }
    }

    private fun openUnknownAppsSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Log.e("NovaRehabUpdater", "Unknown app sources settings unavailable", error)
        }
    }

    private fun setCheckButtonLoading(isLoading: Boolean) {
        btnCheckUpdate.isEnabled = !isLoading
        btnCheckUpdate.text = if (isLoading) "PREVERJANJE..." else "PREVERI POSODOBITEV"
        btnCheckUpdate.backgroundTintList = ColorStateList.valueOf(
            if (isLoading) BUSY_BUTTON_COLOR else CHECK_BUTTON_COLOR
        )
    }

    private fun setDownloadButtonLoading(isLoading: Boolean) {
        btnDownloadApk.isEnabled = !isLoading
        btnDownloadApk.text = if (isLoading) "PRENAŠANJE..." else "PRENESI APK"
        btnDownloadApk.backgroundTintList = ColorStateList.valueOf(
            if (isLoading) BUSY_BUTTON_COLOR else DOWNLOAD_BUTTON_COLOR
        )
    }

    private fun restoreDownloadButtonState() {
        setDownloadButtonLoading(false)
        btnDownloadApk.isEnabled = latestRelease?.apkUrl?.isNotBlank() == true
    }

    private fun toUserFriendlyCheckStatus(message: String): String {
        return when {
            message.contains("ni internetne povezave", ignoreCase = true) -> "Ni internetne povezave."
            message.contains("APK asset ni najden", ignoreCase = true) ||
                message.contains("apk datoteka ni najdena", ignoreCase = true) ||
                message.contains("manjka rehab-release.apk", ignoreCase = true) ->
                "APK datoteka ni najdena v release-u."
            message.contains("HTTP 404", ignoreCase = true) ||
                message.contains("GitHub release ni dosegljiv", ignoreCase = true) ->
                "Napaka pri preverjanju posodobitve.\nGitHub release ni dosegljiv."
            else -> "Napaka pri preverjanju posodobitve."
        }
    }

    private fun toUserFriendlyDownloadStatus(message: String): String {
        return when {
            message.contains("ni internetne povezave", ignoreCase = true) -> "Napaka pri prenosu APK.\nNi internetne povezave."
            message.contains("časovna omejitev", ignoreCase = true) -> "Napaka pri prenosu APK.\nPovezava je potekla."
            else -> "Napaka pri prenosu APK."
        }
    }
}
