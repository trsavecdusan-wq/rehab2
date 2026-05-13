package com.rehab2

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rehab2.update.ApkDownloadManager
import com.rehab2.update.GitHubUpdateClient

class BackupSettingsActivity : AppCompatActivity() {
    private lateinit var txtCurrentVersion: TextView
    private lateinit var txtLatestVersion: TextView
    private lateinit var txtUpdateStatus: TextView
    private lateinit var txtReleaseNotes: TextView
    private lateinit var btnDownloadApk: Button
    private lateinit var currentVersionName: String

    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateClient = GitHubUpdateClient()
    private lateinit var downloadManager: ApkDownloadManager
    private var latestRelease: GitHubUpdateClient.ReleaseInfo? = null

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

        txtCurrentVersion.text = "Trenutna verzija: $currentVersionName"
        txtLatestVersion.text = "Zadnja verzija: -"
        txtUpdateStatus.text = ""
        txtReleaseNotes.text = ""

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
        txtUpdateStatus.text = "Preverjam..."
        txtReleaseNotes.text = ""

        Thread {
            try {
                val release = updateClient.fetchLatestRelease()
                val remoteVersion = release.tagName.removePrefix("v")
                val comparison = compareVersions(remoteVersion, currentVersionName)

                mainHandler.post {
                    latestRelease = release
                    txtLatestVersion.text = "Zadnja verzija: $remoteVersion"

                    if (comparison > 0 && !release.apkUrl.isNullOrBlank()) {
                        txtUpdateStatus.text = "Nova verzija na voljo"
                        txtReleaseNotes.text = release.body
                        btnDownloadApk.isEnabled = true
                    } else {
                        txtUpdateStatus.text = "Ni nove posodobitve"
                        txtReleaseNotes.text = release.body
                        btnDownloadApk.isEnabled = false
                    }
                }
            } catch (error: Exception) {
                val message = error.message?.takeIf { it.isNotBlank() } ?: "Preverjanje ni uspelo"
                Log.e("NovaRehabUpdater", "Update check failed: $message", error)
                mainHandler.post {
                    txtUpdateStatus.text = message
                    txtReleaseNotes.text = ""
                    btnDownloadApk.isEnabled = false
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
                if (result.success) {
                    txtUpdateStatus.text = "APK prenesen"
                } else {
                    txtUpdateStatus.text = "Prenos ni uspel"
                }
                btnDownloadApk.isEnabled = latestRelease?.apkUrl?.isNotBlank() == true
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
}
