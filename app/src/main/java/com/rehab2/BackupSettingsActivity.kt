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
        private const val CURRENT_RELEASE_FILE_NAME = "current_release.apk"
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
    private var latestReleaseBody: String = ""

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
        updateReleaseNotes()

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
                    latestReleaseBody = release.body
                    txtLatestVersion.text = "Zadnja verzija: $remoteVersion"
                    updateReleaseNotes()

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
                    latestReleaseBody = ""
                    btnDownloadApk.isEnabled = false
                    txtUpdateStatus.text = toUserFriendlyCheckStatus(message)
                    updateReleaseNotes()
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
                    val backupPrepared = rotateReleaseBackupIfAvailable()
                    val currentReleaseFile = promoteDownloadedRelease(result.file)
                    if (currentReleaseFile == null) {
                        txtUpdateStatus.text = "Napaka pri prenosu APK."
                        restoreDownloadButtonState()
                        return@post
                    }

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

    private fun rotateReleaseBackupIfAvailable(): Boolean {
        return try {
            val previousReleaseFile = File(getUpdatesDirectory(), CURRENT_RELEASE_FILE_NAME)
            Log.i("NovaRehabUpdater", "Previous release APK: ${previousReleaseFile.absolutePath}")
            if (!previousReleaseFile.exists() || previousReleaseFile.length() <= 0L) {
                Log.e("NovaRehabUpdater", "Previous release APK missing or empty")
                return false
            }

            val backupDir = getBackupDirectory().apply { mkdirs() }
            if (!backupDir.exists()) {
                Log.e("NovaRehabUpdater", "Backup directory could not be created")
                return false
            }

            val backupFile = getBackupApkFile()
            val tempFile = File(backupDir, "${backupFile.name}.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            previousReleaseFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                Log.e("NovaRehabUpdater", "Backup temp file missing or empty")
                return false
            }

            if (backupFile.exists() && !backupFile.delete()) {
                tempFile.delete()
                Log.e("NovaRehabUpdater", "Previous backup could not be replaced")
                return false
            }

            if (!tempFile.renameTo(backupFile)) {
                tempFile.copyTo(backupFile, overwrite = true)
                tempFile.delete()
            }

            val success = backupFile.exists() && backupFile.length() > 0L
            if (success) {
                Log.i("NovaRehabUpdater", "Backup success, file length=${backupFile.length()}")
            } else {
                Log.e("NovaRehabUpdater", "Backup failed, resulting file missing or empty")
            }
            success
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

    private fun promoteDownloadedRelease(downloadedFile: File): File? {
        return try {
            val updatesDir = getUpdatesDirectory().apply { mkdirs() }
            if (!updatesDir.exists()) {
                Log.e("NovaRehabUpdater", "Updates directory could not be created")
                return null
            }

            val currentReleaseFile = File(updatesDir, CURRENT_RELEASE_FILE_NAME)
            val tempFile = File(updatesDir, "${currentReleaseFile.name}.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            downloadedFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                Log.e("NovaRehabUpdater", "Downloaded release temp file missing or empty")
                return null
            }

            if (currentReleaseFile.exists() && !currentReleaseFile.delete()) {
                tempFile.delete()
                Log.e("NovaRehabUpdater", "Current release APK could not be replaced")
                return null
            }

            if (!tempFile.renameTo(currentReleaseFile)) {
                tempFile.copyTo(currentReleaseFile, overwrite = true)
                tempFile.delete()
            }

            if (currentReleaseFile.exists() && currentReleaseFile.length() > 0L) {
                Log.i("NovaRehabUpdater", "Current release saved, file length=${currentReleaseFile.length()}")
                currentReleaseFile
            } else {
                Log.e("NovaRehabUpdater", "Current release APK missing or empty after save")
                null
            }
        } catch (error: IOException) {
            Log.e("NovaRehabUpdater", "Current release save failed", error)
            null
        }
    }

    private fun getBackupDirectory(): File {
        return File(filesDir, "backups")
    }

    private fun getUpdatesDirectory(): File {
        return File(filesDir, "updates")
    }

    private fun getBackupApkFile(): File {
        return File(getBackupDirectory(), "NovaRehab_last_working.apk")
    }

    private fun refreshRestoreButtonState() {
        val backupFile = getBackupApkFile()
        val hasBackup = backupFile.exists() && backupFile.length() > 0L
        btnRestorePreviousVersion.isEnabled = true
        btnRestorePreviousVersion.backgroundTintList = ColorStateList.valueOf(
            if (hasBackup) RESTORE_BUTTON_COLOR else BUSY_BUTTON_COLOR
        )
        updateReleaseNotes()
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
    private fun updateReleaseNotes() {
        val backupFile = getBackupApkFile()
        val backupSummary = if (backupFile.exists() && backupFile.length() > 0L) {
            "Backup obstaja.\nVelikost backup APK: da"
        } else {
            "Backup ne obstaja.\nVelikost backup APK: ne"
        }

        txtReleaseNotes.text = if (latestReleaseBody.isNotBlank()) {
            latestReleaseBody + "\n\n" + backupSummary
        } else {
            backupSummary
        }
    }
}
