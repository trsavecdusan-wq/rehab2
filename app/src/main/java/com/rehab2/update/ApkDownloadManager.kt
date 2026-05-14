package com.rehab2.update

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class ApkDownloadManager(private val context: Context) {
    data class DownloadResult(
        val success: Boolean,
        val file: File?,
        val message: String
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
                val suffix = connection.responseMessage?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                return DownloadResult(
                    success = false,
                    file = null,
                    message = "Prenos ni uspel: HTTP ${connection.responseCode}$suffix"
                )
            }

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                DownloadResult(
                    success = false,
                    file = null,
                    message = "Prenos ni uspel: prazna APK datoteka"
                )
            } else {
                DownloadResult(
                    success = true,
                    file = targetFile,
                    message = "APK prenesen"
                )
            }
        } catch (_: UnknownHostException) {
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: ni internetne povezave"
            )
        } catch (_: SocketTimeoutException) {
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: časovna omejitev"
            )
        } catch (_: IOException) {
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: napaka pri branju datoteke"
            )
        } catch (_: Exception) {
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: neznana napaka"
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val APK_FILE_NAME = "rehab-release.apk"
    }
}
