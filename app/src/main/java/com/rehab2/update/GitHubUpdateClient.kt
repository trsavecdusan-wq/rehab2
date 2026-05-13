package com.rehab2.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GitHubUpdateClient {
    data class ReleaseInfo(
        val tagName: String,
        val body: String,
        val apkUrl: String?
    )

    fun fetchLatestRelease(): ReleaseInfo {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "rehab2-update-check")

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Preverjanje ni uspelo")
            }

            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(responseText)
            val tagName = root.optString("tag_name")
            val body = root.optString("body")
            val assets = root.optJSONArray("assets") ?: JSONArray()
            val apkUrl = findApkUrl(assets)

            ReleaseInfo(
                tagName = tagName,
                body = body,
                apkUrl = apkUrl
            )
        } finally {
            connection.disconnect()
        }
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
