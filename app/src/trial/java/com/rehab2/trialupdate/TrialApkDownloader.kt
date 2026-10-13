package com.rehab2.trialupdate

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Trial-only APK download into filesDir/updates/trial/app-trial.apk.
 * The "updates/" files-path in res/xml/file_paths.xml already covers this folder.
 */
class TrialApkDownloader(private val context: Context) {
    data class DownloadResult(
        val success: Boolean,
        val file: File?,
        val message: String
    )

    fun targetFile(): File {
        return File(File(context.filesDir, TRIAL_UPDATES_DIR), APK_FILE_NAME)
    }

    fun download(apkUrl: String): DownloadResult {
        val targetDir = File(context.filesDir, TRIAL_UPDATES_DIR)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return DownloadResult(false, null, "Prenos ni uspel: mape za prenos ni bilo mogoče ustvariti.")
        }
        val targetFile = File(targetDir, APK_FILE_NAME)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        var connection: HttpURLConnection? = null
        return try {
            Log.i(TAG, "Trial download url=$apkUrl target=${targetFile.absolutePath}")
            connection = openConnectionFollowingRedirects(apkUrl)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                targetFile.delete()
                return DownloadResult(false, null, "Prenos ni uspel: HTTP $responseCode")
            }

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                DownloadResult(false, null, "Prenos ni uspel: prazna APK datoteka.")
            } else {
                Log.i(TAG, "Trial APK downloaded size=${targetFile.length()}")
                DownloadResult(true, targetFile, "APK prenesen.")
            }
        } catch (error: UnknownHostException) {
            Log.e(TAG, "Trial download failed", error)
            targetFile.delete()
            DownloadResult(false, null, "Prenos ni uspel: ni internetne povezave.")
        } catch (error: SocketTimeoutException) {
            Log.e(TAG, "Trial download timed out", error)
            targetFile.delete()
            DownloadResult(false, null, "Prenos ni uspel: časovna omejitev.")
        } catch (error: IOException) {
            Log.e(TAG, "Trial download IO failure", error)
            targetFile.delete()
            DownloadResult(false, null, "Prenos ni uspel: napaka pri branju datoteke.")
        } catch (error: Exception) {
            Log.e(TAG, "Trial download unexpected failure", error)
            targetFile.delete()
            DownloadResult(false, null, "Prenos ni uspel: neznana napaka.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnectionFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirectCount = 0

        while (redirectCount <= MAX_REDIRECTS) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "NovaRehab-TrialUpdater")
                instanceFollowRedirects = false
            }
            connection.connect()
            val responseCode = connection.responseCode

            if (responseCode in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                if (location.isNullOrBlank()) {
                    return connection
                }
                connection.disconnect()
                currentUrl = URL(URL(currentUrl), location).toString()
                redirectCount++
                continue
            }
            return connection
        }

        throw IOException("Preveč preusmeritev pri prenosu APK")
    }

    companion object {
        private const val TAG = "NovaRehabTrialUpdater"
        private const val TRIAL_UPDATES_DIR = "updates/trial"
        private const val APK_FILE_NAME = "app-trial.apk"
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
