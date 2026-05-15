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
        private const val RESTORE_RESULT_HINT_DELAY_MS = 3000L
        private const val CURRENT_BACKUP_RELEASE_FILE_NAME = "NovaRehab_current_downloaded_release.apk"
        private const val PREVIOUS_BACKUP_RELEASE_FILE_NAME = "NovaRehab_previous_downloaded_release.apk"
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
                val downloadedFile = result.file
                if (result.success && downloadedFile != null) {
                    val backupPrepared = rotateDownloadedReleaseBackups(downloadedFile)
                    txtUpdateStatus.text = if (backupPrepared) {
                        "Varnostna kopija pripravljena.\nPrenos kon\u010dan. Odpiram namestitev ..."
                    } else {
                        "Varnostne kopije ni bilo mogo\u010de pripraviti.\nPrenos kon\u010dan. Odpiram namestitev ..."
                    }
                    refreshRestoreButtonState()
                    openInstallHandoff(downloadedFile)
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
        openInstallHandoff(backupFile)
        mainHandler.postDelayed({
            if (txtUpdateStatus.text.toString() == "Odpiram obnovitev prej\u0161nje verzije ...") {
                txtUpdateStatus.text =
                    "\u010ce se namestitev ni odprla ali ni uspela, prej\u0161nje verzije ni bilo mogo\u010de obnoviti."
            }
        }, RESTORE_RESULT_HINT_DELAY_MS)
    }

    private fun rotateDownloadedReleaseBackups(downloadedFile: File): Boolean {
        return try {
            Log.i("NovaRehabUpdater", "Downloaded release APK: ${downloadedFile.absolutePath}")
            if (!downloadedFile.exists() || downloadedFile.length() <= 0L) {
                Log.e("NovaRehabUpdater", "Downloaded release APK missing or empty")
                return false
            }

            val backupDir = getBackupDirectory().apply { mkdirs() }
            if (!backupDir.exists()) {
                Log.e("NovaRehabUpdater", "Backup directory could not be created")
                return false
            }

            val currentFile = getCurrentBackupApkFile()
            val previousFile = getPreviousBackupApkFile()

            if (currentFile.exists() && currentFile.length() > 0L) {
                val previousTempFile = File(backupDir, "${previousFile.name}.tmp")
                if (previousTempFile.exists()) {
                    previousTempFile.delete()
                }

                currentFile.inputStream().use { input ->
                    previousTempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (!previousTempFile.exists() || previousTempFile.length() <= 0L) {
                    previousTempFile.delete()
                    Log.e("NovaRehabUpdater", "Previous backup temp file missing or empty")
                    return false
                }

                if (previousFile.exists() && !previousFile.delete()) {
                    previousTempFile.delete()
                    Log.e("NovaRehabUpdater", "Previous backup could not be replaced")
                    return false
                }

                if (!previousTempFile.renameTo(previousFile)) {
                    previousTempFile.copyTo(previousFile, overwrite = true)
                    previousTempFile.delete()
                }
            }

            val currentTempFile = File(backupDir, "${currentFile.name}.tmp")
            if (currentTempFile.exists()) {
                currentTempFile.delete()
            }

            downloadedFile.inputStream().use { input ->
                currentTempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!currentTempFile.exists() || currentTempFile.length() <= 0L) {
                currentTempFile.delete()
                Log.e("NovaRehabUpdater", "Current backup temp file missing or empty")
                return false
            }

            if (currentFile.exists() && !currentFile.delete()) {
                currentTempFile.delete()
                Log.e("NovaRehabUpdater", "Current backup could not be replaced")
                return false
            }

            if (!currentTempFile.renameTo(currentFile)) {
                currentTempFile.copyTo(currentFile, overwrite = true)
                currentTempFile.delete()
            }

            val success = currentFile.exists() && currentFile.length() > 0L
            if (success) {
                Log.i(
                    "NovaRehabUpdater",
                    "Backup rotation success, current=${currentFile.length()}, previous=${if (previousFile.exists()) previousFile.length() else 0L}"
                )
            } else {
                Log.e("NovaRehabUpdater", "Backup rotation failed, current file missing or empty")
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

    private fun getBackupDirectory(): File {
        return File(filesDir, "backups")
    }

    private fun getCurrentBackupApkFile(): File {
        return File(getBackupDirectory(), CURRENT_BACKUP_RELEASE_FILE_NAME)
    }

    private fun getPreviousBackupApkFile(): File {
        return File(getBackupDirectory(), PREVIOUS_BACKUP_RELEASE_FILE_NAME)
    }

    private fun getBackupApkFile(): File {
        return getPreviousBackupApkFile()
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

        if (!apkFile.exists() || apkFile.length() <= 0L) {
            Log.e("NovaRehabUpdater", "Installer handoff aborted, APK missing or empty")
            txtUpdateStatus.text = "APK datoteka ni pripravljena."
            restoreDownloadButtonState()
            return
        }

        try {
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

            startActivity(installIntent)
            restoreDownloadButtonState()
        } catch (error: ActivityNotFoundException) {
            Log.e("NovaRehabUpdater", "Installer handoff failed", error)
            txtUpdateStatus.text = "Namestitve ni bilo mogo\u010de odpreti."
            restoreDownloadButtonState()
        } catch (error: SecurityException) {
            Log.e("NovaRehabUpdater", "Installer handoff blocked by security policy", error)
            txtUpdateStatus.text = "Namestitve ni bilo mogo\u010de odpreti."
            restoreDownloadButtonState()
        } catch (error: Exception) {
            Log.e("NovaRehabUpdater", "Installer handoff failed with unexpected error", error)
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
            message.contains("\u010dasovna omejitev", ignoreCase = true) ->
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
