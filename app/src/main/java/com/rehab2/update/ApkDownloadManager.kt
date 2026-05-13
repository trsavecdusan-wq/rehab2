package com.rehab2.update

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ApkDownloadManager(private val context: Context) {
    data class DownloadResult(
        val success: Boolean,
        val file: File?
    )

    fun downloadLatestApk(apkUrl: String, onStatus: (String) -> Unit): DownloadResult {
        val updatesDir = File(context.filesDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, APK_FILE_NAME)
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "rehab2-apk-download")

        return try {
            onStatus("Prena\u0161am APK...")
            connection.connect()

            if (connection.responseCode !in 200..299) {
                return DownloadResult(success = false, file = null)
            }

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                DownloadResult(success = false, file = null)
            } else {
                DownloadResult(success = true, file = targetFile)
            }
        } catch (_: Exception) {
            targetFile.delete()
            DownloadResult(success = false, file = null)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val APK_FILE_NAME = "rehab-release.apk"
    }
}
