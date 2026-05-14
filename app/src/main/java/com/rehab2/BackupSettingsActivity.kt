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
import java.io.IOException

class BackupSettingsActivity : AppCompatActivity() {
    companion object {
        private const val CHECK_BUTTON_COLOR = 0xFF214A78.toInt()
        private const val DOWNLOAD_BUTTON_COLOR = 0xFF3E7C4A.toInt()
        private const val RESTORE_BUTTON_COLOR = 0xFF7A5A2A.toInt()
        private const val BUSY_BUTTON_COLOR = 0xFF5B6672.toInt()
        private const val RESTORE_STAGED_FILE_NAME = "NovaRehab_restore.apk"
    }

    private lateinit var txtCurrentVersion: TextView
    private lateinit var txtLatestVersion: TextView
    private lateinit var txtUpdateStatus: TextView
    private lateinit var txtReleaseNotes: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var btnDownloadApk: Button
    private lateinit var btnRestorePreviousVersion: Button
    private lateinit var currentVersionName: String
    private var currentVersionCode: Long = 0L

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
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnDownloadApk = findViewById(R.id.btnDownloadApk)
        btnRestorePreviousVersion = findViewById(R.id.btnRestorePreviousVersion)

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

        btnRestorePreviousVersion.setOnClickListener {
            restorePreviousVersion()
        }

        refreshRestoreButtonState()
    }

    override fun onResume() {
        super.onResume()
        refreshRestoreButtonState()
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
        txtUpdateStatus.text = "Prena\u0161am APK ..."

        Thread {
            val result = downloadManager.downloadLatestApk(apkUrl) { status ->
                mainHandler.post {
                    if (status.contains("Prena", ignoreCase = true)) {
                        txtUpdateStatus.text = "Prena\u0161am APK ..."
                    }
                }
            }

            mainHandler.post {
                if (result.success && result.file != null) {
                    val backupPrepared = prepareInstalledApkBackup()
                    txtUpdateStatus.text = if (backupPrepared) {
                        "Varnostna kopija pripravljena.\nPrenos kon\u010dan. Odpiram namestitev ..."
                    } else {
                        "Varnostne kopije ni bilo mogo\u010de pripraviti.\nPrenos kon\u010dan. Odpiram namestitev ..."
                    }
                    refreshRestoreButtonState()
                    openInstallHandoff(result.file)
                } else {
                    txtUpdateStatus.text = toUserFriendlyDownloadStatus(result.message)
                    restoreDownloadButtonState()
                }
            }
        }.start()
    }

    private fun restorePreviousVersion() {
        val backupFile = getBackupApkFile()
        if (!backupFile.exists() || backupFile.length() <= 0L) {
            txtUpdateStatus.text = "Prej\u0161nja verzija ni na voljo."
            refreshRestoreButtonState()
            return
        }

        txtUpdateStatus.text = "Odpiram obnovitev prej\u0161nje verzije ..."
        val stagedRestoreFile = stageBackupForRestore(backupFile)
        if (stagedRestoreFile == null) {
            txtUpdateStatus.text = "Namestitve ni bilo mogo\u010de odpreti."
            refreshRestoreButtonState()
            return
        }

        openInstallHandoff(stagedRestoreFile)
    }

    private fun prepareInstalledApkBackup(): Boolean {
        return try {
            val sourceApk = File(applicationInfo.sourceDir)
            if (!sourceApk.exists() || sourceApk.length() <= 0L) {
                return false
            }

            val backupDir = getBackupDirectory().apply { mkdirs() }
            if (!backupDir.exists()) {
                return false
            }

            val backupFile = getBackupApkFile()
            val tempFile = File(backupDir, "${backupFile.name}.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            sourceApk.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return false
            }

            if (backupFile.exists() && !backupFile.delete()) {
                tempFile.delete()
                return false
            }

            if (!tempFile.renameTo(backupFile)) {
                tempFile.copyTo(backupFile, overwrite = true)
                tempFile.delete()
            }

            backupFile.exists() && backupFile.length() > 0L
        } catch (error: IOException) {
            Log.e("NovaRehabUpdater", "Backup copy failed", error)
            false
        } catch (error: SecurityException) {
            Log.e("NovaRehabUpdater", "Backup copy blocked", error)
            false
        }
    }

    private fun stageBackupForRestore(backupFile: File): File? {
        return try {
            val updatesDir = File(filesDir, "updates").apply { mkdirs() }
            val stagedFile = File(updatesDir, RESTORE_STAGED_FILE_NAME)
            if (stagedFile.exists()) {
                stagedFile.delete()
            }
            backupFile.copyTo(stagedFile, overwrite = true)
            if (stagedFile.exists() && stagedFile.length() > 0L) stagedFile else null
        } catch (error: IOException) {
            Log.e("NovaRehabUpdater", "Restore staging failed", error)
            null
        }
    }

    private fun getBackupDirectory(): File {
        return File("/storage/emulated/0/NovaRehab/backups")
    }

    private fun getBackupApkFile(): File {
        return File(getBackupDirectory(), "NovaRehab_last_working.apk")
    }

    private fun refreshRestoreButtonState() {
        val hasBackup = getBackupApkFile().exists() && getBackupApkFile().length() > 0L
        btnRestorePreviousVersion.isEnabled = hasBackup
        btnRestorePreviousVersion.backgroundTintList = ColorStateList.valueOf(
            if (hasBackup) RESTORE_BUTTON_COLOR else BUSY_BUTTON_COLOR
        )
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
            txtUpdateStatus.text = "Dovoli name\u0161\u010danje iz te aplikacije in poskusi znova."
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
            txtUpdateStatus.text = "Namestitve ni bilo mogo\u010de odpreti."
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
        btnDownloadApk.text = if (isLoading) "PRENA\u0160ANJE..." else "PRENESI APK"
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
            message.contains("ni internetne povezave", ignoreCase = true) ->
                "Napaka pri prenosu APK.\nNi internetne povezave."
            message.contains("časovna omejitev", ignoreCase = true) ->
                "Napaka pri prenosu APK.\nPovezava je potekla."
            else -> "Napaka pri prenosu APK."
        }
    }
}
