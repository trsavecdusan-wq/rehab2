package com.rehab2.trialupdate

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Hard check before any trial install. Every failed check deletes the downloaded file.
 */
class TrialApkVerifier(private val context: Context) {
    enum class FailureReason {
        APK_UNREADABLE,
        PACKAGE_MISMATCH,
        NOT_NEWER,
        VERSION_CODE_MISMATCH
    }

    data class VerificationResult(
        val valid: Boolean,
        val reason: FailureReason?,
        val message: String
    )

    fun verify(apkFile: File, trialVersionCode: Long): VerificationResult {
        val archiveInfo = readArchiveInfo(apkFile)
            ?: return reject(apkFile, FailureReason.APK_UNREADABLE, "Prenesenega APK ni bilo mogoče prebrati.")

        val expectedPackage = context.packageName
        if (archiveInfo.packageName != expectedPackage) {
            return reject(
                apkFile,
                FailureReason.PACKAGE_MISMATCH,
                "APK ni za to aplikacijo (${archiveInfo.packageName} namesto $expectedPackage)."
            )
        }

        val installedVersionCode = installedVersionCode()
        if (trialVersionCode <= installedVersionCode) {
            return reject(
                apkFile,
                FailureReason.NOT_NEWER,
                "Trial verzija $trialVersionCode ni novejša od nameščene $installedVersionCode."
            )
        }

        val archiveVersionCode = versionCodeOf(archiveInfo)
        if (archiveVersionCode != trialVersionCode) {
            return reject(
                apkFile,
                FailureReason.VERSION_CODE_MISMATCH,
                "versionCode v APK ($archiveVersionCode) se ne ujema s TRIAL_VERSION_CODE ($trialVersionCode)."
            )
        }

        Log.i(TAG, "Trial APK verified package=$expectedPackage versionCode=$archiveVersionCode installed=$installedVersionCode")
        return VerificationResult(valid = true, reason = null, message = "APK preverjen.")
    }

    private fun reject(apkFile: File, reason: FailureReason, message: String): VerificationResult {
        Log.e(TAG, "Trial APK rejected reason=$reason: $message")
        apkFile.delete()
        return VerificationResult(valid = false, reason = reason, message = message)
    }

    private fun readArchiveInfo(apkFile: File): PackageInfo? {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return null
        }
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }
    }

    private fun installedVersionCode(): Long {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }
        return versionCodeOf(packageInfo)
    }

    private fun versionCodeOf(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    companion object {
        private const val TAG = "NovaRehabTrialUpdater"
    }
}
