package com.rehab2

import android.content.ActivityNotFoundException
import android.content.Intent
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
    private lateinit var txtCurrentVersion: TextView
    private lateinit var txtLatestVersion: TextView
    private lateinit var txtUpdateStatus: TextView
    private lateinit var txtReleaseNotes: TextView
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
        txtUpdateStatus.text = "Preverjen URL: $checkedReleaseUrl"
        txtReleaseNotes.text = "Preverjen URL: $checkedReleaseUrl"

        findViewById<Button>(R.id.btnBackBackupSettings).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            checkForUpdate()
        }

        btnDownloadApk.setOnClickListener {
            val release = latestRelease ?: return@setOnClickListener
            downloadLatestApk(release)
        }
    }

    private fun checkForUpdate() {
        btnDownloadApk.isEnabled = false
        txtUpdateStatus.text = "Preverjam...\nURL: $checkedReleaseUrl"
        txtReleaseNotes.text = "Preverjen URL: $checkedReleaseUrl"

        Thread {
            try {
                val release = updateClient.fetchLatestRelease()
                val remoteVersion = release.tagName.removePrefix("v")
                val comparison = compareVersions(remoteVersion, currentVersionName)

                mainHandler.post {
                    latestRelease = release
                    txtLatestVersion.text = "Zadnja verzija: $remoteVersion"
                    txtReleaseNotes.text = buildString {
                        append("Preverjen URL: ").append(checkedReleaseUrl)
                        if (release.body.isNotBlank()) {
                            append("\n\n").append(release.body)
                        }
                    }

                    if (comparison > 0 && !release.apkUrl.isNullOrBlank()) {
                        txtUpdateStatus.text = "Nova verzija na voljo\nURL: $checkedReleaseUrl"
                        btnDownloadApk.isEnabled = true
                    } else {
                        txtUpdateStatus.text = "Ni nove posodobitve\nURL: $checkedReleaseUrl"
                        btnDownloadApk.isEnabled = false
                    }
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Preverjanje ni uspelo"
                Log.e("NovaRehabUpdater", "Update check failed: $message", error)
                mainHandler.post {
                    latestRelease = null
                    btnDownloadApk.isEnabled = false
                    txtUpdateStatus.text = if (message.contains("HTTP 404")) {
                        "Posodobitve ni mogoče preveriti. GitHub release ni dosegljiv.\nURL: $checkedReleaseUrl"
                    } else {
                        "$message\nURL: $checkedReleaseUrl"
                    }
                    txtReleaseNotes.text = buildString {
                        append("Preverjen URL: ").append(checkedReleaseUrl)
                        if (message.contains("HTTP 404")) {
                            append("\n\nČe je repozitorij zaseben, posodabljanje brez javnega GitHub release URL ne more delovati.")
                        }
                    }
                }
            }
        }.start()
    }

    private fun downloadLatestApk(release: GitHubUpdateClient.ReleaseInfo) {
        val apkUrl = release.apkUrl ?: return
        btnDownloadApk.isEnabled = false

        Thread {
            val result = downloadManager.downloadLatestApk(apkUrl) { status ->
                mainHandler.post {
                    txtUpdateStatus.text = status
                }
            }

            mainHandler.post {
                if (result.success && result.file != null) {
                    txtUpdateStatus.text = "APK prenesen: ${result.file.absolutePath}"
                    openInstallHandoff(result.file)
                    btnDownloadApk.isEnabled = latestRelease?.apkUrl?.isNotBlank() == true
                } else {
                    txtUpdateStatus.text = result.message
                    btnDownloadApk.isEnabled = false
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
        } catch (error: ActivityNotFoundException) {
            Log.e("NovaRehabUpdater", "Installer handoff failed", error)
            txtUpdateStatus.text = "Namestitve ni bilo mogoče odpreti."
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
}
