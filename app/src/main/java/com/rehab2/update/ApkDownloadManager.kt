package com.rehab2.update

import android.content.Context
import android.util.Log
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

        var connection: HttpURLConnection? = null

        return try {
            onStatus("DOWNLOAD URL: $apkUrl")
            onStatus("HTTP START")
            onStatus("Prena\u0161am APK...")
            Log.i("NovaRehabUpdater", "Download target path: ${targetFile.absolutePath}")

            connection = openConnectionFollowingRedirects(apkUrl, onStatus)
            onStatus("HTTP CODE: ${connection.responseCode}")

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
                onStatus("FILE SAVED")
                onStatus("DOWNLOADED SIZE: ${targetFile.length()}")
                Log.i("NovaRehabUpdater", "Downloaded APK path=${targetFile.absolutePath} size=${targetFile.length()}")
                DownloadResult(
                    success = true,
                    file = targetFile,
                    message = "APK prenesen"
                )
            }
        } catch (error: UnknownHostException) {
            onStatus("DOWNLOAD EXCEPTION: ${error.message ?: error.javaClass.simpleName}")
            Log.e("NovaRehabUpdater", "APK download failed", error)
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: ni internetne povezave"
            )
        } catch (error: SocketTimeoutException) {
            onStatus("DOWNLOAD EXCEPTION: ${error.message ?: error.javaClass.simpleName}")
            Log.e("NovaRehabUpdater", "APK download timed out", error)
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: \u010dasovna omejitev"
            )
        } catch (error: IOException) {
            onStatus("DOWNLOAD EXCEPTION: ${error.message ?: error.javaClass.simpleName}")
            Log.e("NovaRehabUpdater", "APK download IO failure", error)
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: napaka pri branju datoteke"
            )
        } catch (error: Exception) {
            onStatus("DOWNLOAD EXCEPTION: ${error.message ?: error.javaClass.simpleName}")
            Log.e("NovaRehabUpdater", "APK download unexpected failure", error)
            targetFile.delete()
            DownloadResult(
                success = false,
                file = null,
                message = "Prenos ni uspel: neznana napaka"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnectionFollowingRedirects(
        initialUrl: String,
        onStatus: (String) -> Unit
    ): HttpURLConnection {
        var currentUrl = initialUrl
        var redirectCount = 0

        while (redirectCount <= MAX_REDIRECTS) {
            val currentConnection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "rehab2-apk-download")
                instanceFollowRedirects = false
            }

            currentConnection.connect()
            val responseCode = currentConnection.responseCode
            Log.i("NovaRehabUpdater", "HTTP ${responseCode} for $currentUrl")

            if (responseCode in REDIRECT_CODES) {
                val location = currentConnection.getHeaderField("Location")
                if (location.isNullOrBlank()) {
                    return currentConnection
                }
                val redirectedUrl = URL(URL(currentUrl), location).toString()
                Log.i("NovaRehabUpdater", "Redirecting download to $redirectedUrl")
                onStatus("HTTP CODE: $responseCode")
                currentConnection.disconnect()
                currentUrl = redirectedUrl
                redirectCount += 1
                continue
            }

            return currentConnection
        }

        throw IOException("Prevec preusmeritev pri prenosu APK")
    }

    companion object {
        private const val APK_FILE_NAME = "rehab-release.apk"
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
