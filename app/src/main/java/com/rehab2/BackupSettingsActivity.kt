package com.rehab2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.rehab2.update.ApkDownloadManager
import com.rehab2.update.GitHubUpdateClient
import java.io.File
import java.io.IOException
import java.util.Locale

class BackupSettingsActivity : AppCompatActivity() {
    data class BackupApkInfo(
        val exists: Boolean,
        val sizeBytes: Long,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val canRestore: Boolean,
        val downgradeBlocked: Boolean
    )

    data class ApkDiagnosticInfo(
        val exists: Boolean,
        val sizeBytes: Long,
        val packageName: String?,
        val versionName: String?,
        val versionCode: Long?,
        val readable: Boolean
    )

    companion object {
        private const val CHECK_BUTTON_COLOR = 0xFF214A78.toInt()
        private const val DOWNLOAD_BUTTON_COLOR = 0xFF3E7C4A.toInt()
        private const val RESTORE_BUTTON_COLOR = 0xFF7A5A2A.toInt()
        private const val BUSY_BUTTON_COLOR = 0xFF5B6672.toInt()
        private const val RESTORE_RESULT_HINT_DELAY_MS = 3000L
        private const val CURRENT_INSTALLED_RELEASE_FILE_NAME = "NovaRehab_current_installed_release.apk"
        private const val PREVIOUS_RESTORABLE_RELEASE_FILE_NAME = "NovaRehab_previous_restorable_release.apk"
        private const val DOWNLOADED_RELEASE_FILE_NAME = "rehab-release.apk"
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
    private var pendingInstallApkPath: String? = null

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
        btnDownloadApk.isClickable = true
        btnDownloadApk.isEnabled = true
        btnDownloadApkTest = findViewById(R.id.btnDownloadApkTest)
        btnDownloadApkTest.isClickable = true
        btnDownloadApkTest.isEnabled = true
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
        syncCurrentInstalledReleaseIfNeeded()
        updateReleaseNotes()

        findViewById<Button>(R.id.btnBackBackupSettings).setOnClickListener {
            finish()
        }

        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

        btnDownloadApk.setOnClickListener {
            txtUpdateStatus.text = "DOWNLOAD BUTTON CLICKED"
            Toast.makeText(this, "DOWNLOAD BUTTON CLICKED", Toast.LENGTH_SHORT).show()
            diagnosticToast("DOWNLOAD BUTTON CLICKED")
            if (!btnDownloadApk.isEnabled) {
                diagnosticToast("DOWNLOAD BUTTON DISABLED")
                diagnosticToast("DOWNLOAD FLOW STOPPED")
                return@setOnClickListener
            }

            val release = latestRelease
            if (release == null) {
                txtUpdateStatus.text = "LATEST RELEASE NULL"
                diagnosticToast("LATEST RELEASE NULL")
                diagnosticToast("DOWNLOAD FLOW STOPPED")
                return@setOnClickListener
            }

            diagnosticToast("CALLING DOWNLOAD MANAGER")
            downloadLatestApk(release)
        }

        btnDownloadApkTest.setOnClickListener {
            txtUpdateStatus.text = "TEST DOWNLOAD BUTTON CLICKED"
            Toast.makeText(this, "TEST DOWNLOAD BUTTON CLICKED", Toast.LENGTH_SHORT).show()

            val release = latestRelease
            if (release == null) {
                txtUpdateStatus.text = "LATEST RELEASE NULL"
                Toast.makeText(this, "LATEST RELEASE NULL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            downloadLatestApk(release)
        }

        btnRestorePreviousVersion.setOnClickListener {
            restorePreviousVersion()
        }

        refreshRestoreButtonState()
    }

    override fun onResume() {
        super.onResume()
        syncCurrentInstalledReleaseIfNeeded()
        resumePendingInstallIfAllowed()
        refreshRestoreButtonState()
    }

    private fun resumePendingInstallIfAllowed() {
        val pendingPath = pendingInstallApkPath ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            return
        }

        val pendingFile = File(pendingPath)
        if (!pendingFile.exists() || pendingFile.length() <= 0L) {
            diagnosticToast("APK FILE MISSING")
            txtUpdateStatus.text = "APK datoteka ni pripravljena."
            pendingInstallApkPath = null
            return
        }

        pendingInstallApkPath = null
        txtUpdateStatus.text = "Dovoljenje potrjeno. Odpiram namestitev ..."
        openInstallHandoff(pendingFile)
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
                val latestVersionCode = extractReleaseVersionCode(remoteVersion)

                mainHandler.post {
                    latestRelease = release
                    latestReleaseBody = release.body
                    txtLatestVersion.text = "Zadnja verzija: $remoteVersion"
                    updateReleaseNotes()

                    if (latestVersionCode != null &&
                        latestVersionCode > currentVersionCode &&
                        !release.apkUrl.isNullOrBlank()
                    ) {
                        txtUpdateStatus.text = "Posodobitev je na voljo: $remoteVersion"
                        btnDownloadApk.isEnabled = true
                    } else if (latestVersionCode != null &&
                        latestVersionCode <= currentVersionCode &&
                        currentVersionCode % 2L == 1L
                    ) {
                        txtUpdateStatus.text = "Ta posodobitev ni primerna za trenutno name\u0161\u010deno rollback verzijo. Po\u010dakajte na novej\u0161o normalno izdajo."
                        btnDownloadApk.isEnabled = false
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
        diagnosticToast("DOWNLOAD START")
        val apkUrl = release.apkUrl ?: return
        diagnosticToast("DOWNLOAD URL READY")
        setDownloadButtonLoading(true)
        txtUpdateStatus.text = "Prena\u0161am APK ..."

        Thread {
            val result = downloadManager.downloadLatestApk(apkUrl) { status ->
                mainHandler.post {
                    diagnosticToast(status)
                    if (status.contains("Prena", ignoreCase = true)) {
                        txtUpdateStatus.text = "Prena\u0161am APK ..."
                    }
                }
            }

            mainHandler.post {
                val downloadedFile = result.file
                if (result.success && downloadedFile != null) {
                    diagnosticToast("DOWNLOAD SUCCESS")
                    if (downloadedFile.exists() && downloadedFile.length() > 0L) {
                        diagnosticToast("APK FILE EXISTS")
                    } else {
                        diagnosticToast("APK FILE MISSING")
                    }
                    val backupPrepared = preparePreviousRestorableFromCurrentInstalled(downloadedFile)
                    txtUpdateStatus.text = if (backupPrepared) {
                        "Varnostna kopija pripravljena.\nPrenos kon\u010dan. Odpiram namestitev ..."
                    } else {
                        "Varnostne kopije ni bilo mogo\u010de pripraviti.\nPrenos kon\u010dan. Odpiram namestitev ..."
                    }
                    refreshRestoreButtonState()
                    openInstallHandoff(downloadedFile)
                } else {
                    diagnosticToast("DOWNLOAD FAILED: ${result.message}")
                    txtUpdateStatus.text = toUserFriendlyDownloadStatus(result.message)
                    restoreDownloadButtonState()
                }
            }
        }.start()
    }

    private fun restorePreviousVersion() {
        val backupFile = getBackupApkFile()
        val backupInfo = readBackupApkInfo()
        if (!backupFile.exists() || backupFile.length() <= 0L) {
            txtUpdateStatus.text = "Prej\u0161nja verzija ni na voljo."
            refreshRestoreButtonState()
            return
        }
        if (backupInfo.downgradeBlocked) {
            downloadRollbackRelease(backupInfo)
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

    private fun downloadRollbackRelease(backupInfo: BackupApkInfo) {
        val restoreTargetVersionName = backupInfo.versionName ?: run {
            txtUpdateStatus.text = "Posebna rollback izdaja ni na voljo."
            refreshRestoreButtonState()
            return
        }

        txtUpdateStatus.text = "I\u0161\u010dem rollback izdajo ..."
        setDownloadButtonLoading(true)

        Thread {
            try {
                val rollbackRelease =
                    updateClient.fetchRollbackRelease(currentVersionCode, restoreTargetVersionName)
                val rollbackUrl = rollbackRelease.apkUrl
                    ?: throw IllegalStateException("Rollback APK manjka")
                val result = downloadManager.downloadLatestApk(rollbackUrl) { status ->
                    mainHandler.post {
                        if (status.contains("Prena", ignoreCase = true)) {
                            txtUpdateStatus.text = "Prena\u0161am rollback APK ..."
                        }
                    }
                }

                mainHandler.post {
                    val rollbackFile = result.file
                    if (result.success && rollbackFile != null) {
                        if (isValidRollbackApk(rollbackFile)) {
                            txtUpdateStatus.text = "Rollback APK prenesen. Odpiram namestitev ..."
                            openInstallHandoff(rollbackFile)
                        } else {
                            txtUpdateStatus.text = "Rollback izdaja ni veljavna za to aplikacijo."
                            restoreDownloadButtonState()
                        }
                    } else {
                        txtUpdateStatus.text = "Posebna rollback izdaja ni na voljo."
                        restoreDownloadButtonState()
                    }
                }
            } catch (error: Exception) {
                Log.e("NovaRehabUpdater", "Rollback release lookup failed", error)
                mainHandler.post {
                    txtUpdateStatus.text = "Posebna rollback izdaja ni na voljo."
                    restoreDownloadButtonState()
                }
            }
        }.start()
    }

    private fun isValidRollbackApk(apkFile: File): Boolean {
        val rollbackInfo = readApkArchiveInfo(apkFile)
        val rollbackVersionCode = rollbackInfo?.versionCode
        return rollbackInfo?.packageName == packageName &&
            rollbackVersionCode != null &&
            rollbackVersionCode > currentVersionCode &&
            rollbackVersionCode % 2L == 1L
    }

    private fun syncCurrentInstalledReleaseIfNeeded() {
        try {
            val downloadedReleaseFile = getDownloadedReleaseApkFile()
            if (!downloadedReleaseFile.exists() || downloadedReleaseFile.length() <= 0L) {
                return
            }

            val downloadedReleaseInfo = readApkArchiveInfo(downloadedReleaseFile) ?: return
            if (downloadedReleaseInfo.packageName != packageName ||
                downloadedReleaseInfo.versionCode != currentVersionCode
            ) {
                return
            }

            val currentInstalledFile = getCurrentInstalledReleaseApkFile()
            val currentInstalledInfo = readApkArchiveInfo(currentInstalledFile)
            val currentInstalledVersionCode = currentInstalledInfo?.versionCode
            if (currentInstalledInfo?.packageName == packageName &&
                currentInstalledVersionCode == currentVersionCode
            ) {
                return
            }

            copyFileAtomically(
                sourceFile = downloadedReleaseFile,
                targetFile = currentInstalledFile,
                copyLabel = "Current installed release"
            )
        } catch (error: IOException) {
            Log.e("NovaRehabUpdater", "Current installed release sync failed", error)
        } catch (error: SecurityException) {
            Log.e("NovaRehabUpdater", "Current installed release sync blocked", error)
        }
    }

    private fun preparePreviousRestorableFromCurrentInstalled(downloadedFile: File): Boolean {
        return try {
            val downloadedReleaseInfo = readApkArchiveInfo(downloadedFile)
            val downloadedReleaseVersionCode = downloadedReleaseInfo?.versionCode
            if (downloadedReleaseInfo?.packageName != packageName ||
                downloadedReleaseVersionCode == null ||
                downloadedReleaseVersionCode <= currentVersionCode ||
                !isNormalReleaseVersion(downloadedReleaseVersionCode, downloadedReleaseInfo.versionName)
            ) {
                Log.e("NovaRehabUpdater", "Downloaded APK is not a valid newer normal release for backup rotation")
                return false
            }

            val currentInstalledFile = getCurrentInstalledReleaseApkFile()
            Log.i("NovaRehabUpdater", "Current installed cache APK: ${currentInstalledFile.absolutePath}")
            if (!currentInstalledFile.exists() || currentInstalledFile.length() <= 0L) {
                Log.e("NovaRehabUpdater", "Current installed cache APK missing or empty")
                return false
            }

            val currentInstalledInfo = readApkArchiveInfo(currentInstalledFile)
            val currentInstalledVersionCode = currentInstalledInfo?.versionCode
            if (currentInstalledInfo?.packageName != packageName ||
                currentInstalledVersionCode == null ||
                currentInstalledVersionCode != currentVersionCode ||
                !isNormalReleaseVersion(currentInstalledVersionCode, currentInstalledInfo.versionName)
            ) {
                Log.e("NovaRehabUpdater", "Current installed cache APK is not a valid normal restore source")
                return false
            }

            val previousRestorableFile = getPreviousRestorableReleaseApkFile()
            val success = copyFileAtomically(
                sourceFile = currentInstalledFile,
                targetFile = previousRestorableFile,
                copyLabel = "Previous restorable release"
            )
            if (success) {
                Log.i(
                    "NovaRehabUpdater",
                    "Prepared previous restorable release, size=${previousRestorableFile.length()}"
                )
            } else {
                Log.e("NovaRehabUpdater", "Preparing previous restorable release failed")
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

    private fun isNormalReleaseVersion(versionCode: Long, versionName: String?): Boolean {
        return versionCode % 2L == 0L && !versionName.orEmpty().contains("-rollback", ignoreCase = true)
    }

    private fun getBackupDirectory(): File {
        return File(filesDir, "backups")
    }

    private fun getCurrentInstalledReleaseApkFile(): File {
        return File(getBackupDirectory(), CURRENT_INSTALLED_RELEASE_FILE_NAME)
    }

    private fun getPreviousRestorableReleaseApkFile(): File {
        return File(getBackupDirectory(), PREVIOUS_RESTORABLE_RELEASE_FILE_NAME)
    }

    private fun getBackupApkFile(): File {
        return getPreviousRestorableReleaseApkFile()
    }

    private fun getDownloadedReleaseApkFile(): File {
        return File(File(filesDir, "updates"), DOWNLOADED_RELEASE_FILE_NAME)
    }

    private fun refreshRestoreButtonState() {
        val backupInfo = readBackupApkInfo()
        val canTriggerRestore = backupInfo.canRestore || backupInfo.downgradeBlocked
        btnRestorePreviousVersion.isEnabled = canTriggerRestore
        btnRestorePreviousVersion.backgroundTintList = ColorStateList.valueOf(
            if (canTriggerRestore) RESTORE_BUTTON_COLOR else BUSY_BUTTON_COLOR
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

    private fun extractReleaseVersionCode(versionName: String): Long? {
        return versionName.substringAfterLast('.', "").toLongOrNull()
    }

    private fun openInstallHandoff(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingInstallApkPath = apkFile.absolutePath
            diagnosticToast("DOVOLI NAMESTITEV IZ TE APLIKACIJE")
            txtUpdateStatus.text = "DOVOLI NAMESTITEV IZ TE APLIKACIJE"
            restoreDownloadButtonState()
            openUnknownAppsSettings()
            return
        }

        if (!apkFile.exists() || apkFile.length() <= 0L) {
            Log.e("NovaRehabUpdater", "Installer handoff aborted, APK missing or empty")
            diagnosticToast("APK FILE MISSING")
            txtUpdateStatus.text = "APK datoteka ni pripravljena."
            restoreDownloadButtonState()
            return
        }

        diagnosticToast("APK FILE EXISTS")

        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            diagnosticToast("INSTALL INTENT START")
            startActivity(installIntent)
            restoreDownloadButtonState()
        } catch (error: ActivityNotFoundException) {
            Log.e("NovaRehabUpdater", "Installer handoff failed", error)
            diagnosticToast("INSTALL INTENT FAILED: ${error.javaClass.simpleName}")
            txtUpdateStatus.text = "Namestitve ni bilo mogo\u010de odpreti."
            restoreDownloadButtonState()
        } catch (error: SecurityException) {
            Log.e("NovaRehabUpdater", "Installer handoff blocked by security policy", error)
            diagnosticToast("INSTALL INTENT FAILED: ${error.javaClass.simpleName}")
            txtUpdateStatus.text = "Namestitve ni bilo mogo\u010de odpreti."
            restoreDownloadButtonState()
        } catch (error: Exception) {
            Log.e("NovaRehabUpdater", "Installer handoff failed with unexpected error", error)
            diagnosticToast("INSTALL INTENT FAILED: ${error.javaClass.simpleName}")
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
        val currentCacheInfo = readApkDiagnosticInfo(getCurrentInstalledReleaseApkFile())
        val downloadedApkInfo = readApkDiagnosticInfo(getDownloadedReleaseApkFile())
        val backupInfo = readBackupApkInfo()
        val restoreTargetInfo = readApkDiagnosticInfo(getBackupApkFile())
        val backupSummary = buildString {
            appendLine("CURRENT CACHE:")
            appendLine("Current cache obstaja: ${yesNo(currentCacheInfo.exists)}")
            appendLine("Current cache velikost: ${formatSizeInMb(currentCacheInfo.sizeBytes)} MB")
            appendLine("Current cache verzija: ${formatVersionLabel(currentCacheInfo)}")
            appendLine()
            appendLine("DOWNLOADED APK:")
            appendLine("Downloaded APK obstaja: ${yesNo(downloadedApkInfo.exists)}")
            appendLine("Downloaded APK velikost: ${formatSizeInMb(downloadedApkInfo.sizeBytes)} MB")
            appendLine("Downloaded APK verzija: ${formatVersionLabel(downloadedApkInfo)}")
            appendLine()
            appendLine("RESTORE TARGET:")
            appendLine("Restore target obstaja: ${yesNo(restoreTargetInfo.exists)}")
            appendLine("Restore target verzija: ${formatVersionLabel(restoreTargetInfo)}")
            if (backupInfo.exists) {
                appendLine()
                if (backupInfo.packageName == null) {
                    append("Backup APK ni bilo mogo\u010de prebrati.")
                } else if (backupInfo.packageName != packageName) {
                    append("Backup APK ni za to aplikacijo.")
                } else if (backupInfo.downgradeBlocked) {
                    append(
                        "Android ne dovoli neposredne namestitve starej\u0161e verzije. Za obnovitev je potrebna ro\u010dna odstranitev aplikacije ali posebna rollback izdaja."
                    )
                } else if (backupInfo.versionCode != null &&
                    backupInfo.versionCode >= currentVersionCode
                ) {
                    append("Backup ni starej\u0161a verzija. Obnovitev ne bo vrnila prej\u0161nje verzije.")
                }
            }
        }.trim()

        txtReleaseNotes.text = if (latestReleaseBody.isNotBlank()) {
            latestReleaseBody + "\n\n" + backupSummary
        } else {
            backupSummary
        }
    }

    private fun readBackupApkInfo(): BackupApkInfo {
        val backupFile = getBackupApkFile()
        val archiveInfo = readApkArchiveInfo(backupFile)
        val backupVersionCode = archiveInfo?.versionCode
        val samePackage = archiveInfo?.packageName == packageName
        val downgradeBlocked = samePackage &&
            backupVersionCode != null &&
            backupVersionCode < currentVersionCode
        return BackupApkInfo(
            exists = backupFile.exists() && backupFile.length() > 0L,
            sizeBytes = if (backupFile.exists()) backupFile.length() else 0L,
            packageName = archiveInfo?.packageName,
            versionName = archiveInfo?.versionName,
            versionCode = backupVersionCode,
            canRestore = samePackage &&
                backupVersionCode != null &&
                backupVersionCode == currentVersionCode,
            downgradeBlocked = downgradeBlocked
        )
    }

    private fun readApkDiagnosticInfo(apkFile: File): ApkDiagnosticInfo {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return ApkDiagnosticInfo(
                exists = false,
                sizeBytes = 0L,
                packageName = null,
                versionName = null,
                versionCode = null,
                readable = false
            )
        }

        val archiveInfo = readApkArchiveInfo(apkFile)
        return ApkDiagnosticInfo(
            exists = true,
            sizeBytes = apkFile.length(),
            packageName = archiveInfo?.packageName,
            versionName = archiveInfo?.versionName,
            versionCode = archiveInfo?.versionCode,
            readable = archiveInfo != null
        )
    }

    private fun readApkArchiveInfo(apkFile: File): BackupApkInfo? {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return null
        }

        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }

        val backupPackageName = packageInfo?.packageName
        val backupVersionName = packageInfo?.versionName
        val backupVersionCode = if (packageInfo == null) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        return BackupApkInfo(
            exists = apkFile.exists() && apkFile.length() > 0L,
            sizeBytes = apkFile.length(),
            packageName = backupPackageName,
            versionName = backupVersionName,
            versionCode = backupVersionCode,
            canRestore = false,
            downgradeBlocked = false
        )
    }

    private fun copyFileAtomically(sourceFile: File, targetFile: File, copyLabel: String): Boolean {
        val parentDir = targetFile.parentFile?.apply { mkdirs() } ?: return false
        if (!parentDir.exists()) {
            Log.e("NovaRehabUpdater", "$copyLabel directory could not be created")
            return false
        }

        val tempFile = File(parentDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        sourceFile.inputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (!tempFile.exists() || tempFile.length() <= 0L) {
            tempFile.delete()
            Log.e("NovaRehabUpdater", "$copyLabel temp file missing or empty")
            return false
        }

        if (targetFile.exists() && !targetFile.delete()) {
            tempFile.delete()
            Log.e("NovaRehabUpdater", "$copyLabel could not replace existing file")
            return false
        }

        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        return targetFile.exists() && targetFile.length() > 0L
    }

    private fun formatVersionLabel(info: ApkDiagnosticInfo): String {
        return when {
            !info.exists -> "neznana"
            !info.readable -> "ni berljivo"
            !info.versionName.isNullOrBlank() && info.versionCode != null ->
                "${info.versionName} (${info.versionCode})"
            else -> "neznana"
        }
    }

    private fun formatSizeInMb(sizeBytes: Long): String {
        val sizeMb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f", sizeMb)
    }

    private fun yesNo(value: Boolean): String {
        return if (value) "da" else "ne"
    }

    private fun diagnosticToast(message: String) {
        Log.i("NovaRehabUpdater", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
