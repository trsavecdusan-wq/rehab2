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
        val apkUrl: String?,
        val debugSummary: String
    )

    class UpdateCheckException(
        val debugSummary: String,
        message: String
    ) : IllegalStateException(message)

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
            val responseMessage = connection.responseMessage.orEmpty()
            if (responseCode !in 200..299) {
                val errorBody = readPreview(connection.errorStream)
                val debugSummary = buildString {
                    appendLine("URL: $LATEST_RELEASE_URL")
                    appendLine("HTTP: $responseCode ${responseMessage.ifBlank { "Unknown" }}")
                    appendLine("Napaka: HTTP")
                    appendLine("Sporočilo: GitHub release endpoint ni vrnil uspešnega odgovora.")
                    if (errorBody.isNotBlank()) {
                        append("Body: ").append(errorBody)
                    }
                }.trim()
                throw UpdateCheckException(
                    debugSummary = debugSummary,
                    message = "GitHub release ni dosegljiv."
                )
            }

            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(responseText)
            val tagName = root.optString("tag_name")
            val body = root.optString("body")
            val assets = root.optJSONArray("assets") ?: JSONArray()
            val assetNames = mutableListOf<String>()
            val apkUrl = findApkUrl(assets, assetNames)
            val debugSummary = buildString {
                appendLine("URL: $LATEST_RELEASE_URL")
                appendLine("HTTP: $responseCode ${responseMessage.ifBlank { "OK" }}")
                appendLine("JSON tag_name: ${if (tagName.isNotBlank()) "da" else "ne"}")
                appendLine("tag_name: ${if (tagName.isNotBlank()) tagName else "-"}")
                appendLine("Assets: ${assets.length()}")
                append("Imena assetov: ${if (assetNames.isEmpty()) "[]" else assetNames.joinToString(prefix = "[", postfix = "]")}")
            }.trim()
            if (tagName.isBlank()) {
                throw UpdateCheckException(
                    debugSummary = debugSummary,
                    message = "Preverjanje ni uspelo: neveljaven odgovor GitHub"
                )
            }
            if (apkUrl.isNullOrBlank()) {
                throw UpdateCheckException(
                    debugSummary = "$debugSummary\nRelease najden, APK asset ni najden.",
                    message = "Release najden, APK asset ni najden."
                )
            }

            ReleaseInfo(
                tagName = tagName,
                body = body,
                apkUrl = apkUrl,
                debugSummary = debugSummary
            )
        } catch (error: UpdateCheckException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throwUpdateException("Ni internetne povezave ali je zahteva potekla.", error)
        } catch (error: UnknownHostException) {
            throwUpdateException("Ni internetne povezave.", error)
        } catch (error: JSONException) {
            throwUpdateException("Preverjanje ni uspelo: neveljaven odgovor GitHub", error)
        } catch (error: IOException) {
            throwUpdateException("Napaka pri branju GitHub odgovora.", error)
        } finally {
            connection.disconnect()
        }
    }

    fun getLatestReleaseUrl(): String {
        return LATEST_RELEASE_URL
    }

    private fun findApkUrl(assets: JSONArray, assetNames: MutableList<String>): String? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.isNotBlank()) {
                assetNames.add(name)
            }
            if (name in APK_ASSET_NAMES) {
                return asset.optString("browser_download_url")
            }
        }
        return null
    }

    private fun readPreview(stream: java.io.InputStream?): String {
        if (stream == null) {
            return ""
        }
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText().take(500)
        }
    }

    private fun throwUpdateException(message: String, error: Exception): Nothing {
        val debugSummary = buildString {
            appendLine("URL: $LATEST_RELEASE_URL")
            appendLine("Napaka: ${error.javaClass.name}")
            appendLine("Sporočilo: ${error.message ?: "-"}")
        }.trim()
        throw UpdateCheckException(debugSummary = debugSummary, message = message)
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/trsavecdusan-wq/rehab2/releases/latest"
        private val APK_ASSET_NAMES = setOf(
            "app-debug.apk",
            "app-release.apk",
            "rehab-debug.apk",
            "rehab-release.apk"
        )
    }
}
