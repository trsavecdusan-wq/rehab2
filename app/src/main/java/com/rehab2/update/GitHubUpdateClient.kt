package com.rehab2.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class GitHubUpdateClient {
    data class ReleaseInfo(
        val tagName: String,
        val body: String,
        val apkUrl: String?
    )

    fun fetchLatestRelease(): ReleaseInfo {
        val releaseUrl = URL(LATEST_RELEASE_URL)
        val connection = releaseUrl.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "NovaRehab-Updater")

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val suffix = connection.responseMessage?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                throw IllegalStateException("Preverjanje ni uspelo: HTTP $responseCode$suffix ($LATEST_RELEASE_URL)")
            }

            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(responseText)
            val tagName = root.optString("tag_name")
            val body = root.optString("body")
            val assets = root.optJSONArray("assets") ?: JSONArray()
            val apkUrl = findApkUrl(assets)
            if (tagName.isBlank()) {
                throw IllegalStateException("Preverjanje ni uspelo: neveljaven odgovor GitHub ($LATEST_RELEASE_URL)")
            }
            if (apkUrl.isNullOrBlank()) {
                throw IllegalStateException("Preverjanje ni uspelo: manjka rehab-release.apk ($LATEST_RELEASE_URL)")
            }

            ReleaseInfo(
                tagName = tagName,
                body = body,
                apkUrl = apkUrl
            )
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("Preverjanje ni uspelo: ni internetne povezave ($LATEST_RELEASE_URL)")
        } catch (_: UnknownHostException) {
            throw IllegalStateException("Preverjanje ni uspelo: ni internetne povezave ($LATEST_RELEASE_URL)")
        } catch (_: JSONException) {
            throw IllegalStateException("Preverjanje ni uspelo: neveljaven odgovor GitHub ($LATEST_RELEASE_URL)")
        } catch (_: IOException) {
            throw IllegalStateException("Preverjanje ni uspelo: ni internetne povezave ($LATEST_RELEASE_URL)")
        } finally {
            connection.disconnect()
        }
    }

    fun getLatestReleaseUrl(): String {
        return LATEST_RELEASE_URL
    }

    private fun findApkUrl(assets: JSONArray): String? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") == APK_ASSET_NAME) {
                return asset.optString("browser_download_url")
            }
        }
        return null
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/trsavecdusan-wq/rehab2/releases/latest"
        private const val APK_ASSET_NAME = "rehab-release.apk"
    }
}
