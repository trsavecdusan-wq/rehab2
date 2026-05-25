package com.rehab2.update

import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.regex.Pattern

class GitHubUpdateClient {
    data class ReleaseInfo(
        val tagName: String,
        val body: String,
        val apkUrl: String?,
        val versionCode: Long?,
        val versionCodeFromBody: Long?,
        val versionCodeSource: String,
        val debugSummary: String
    )

    class UpdateCheckException(
        val debugSummary: String,
        message: String
    ) : IllegalStateException(message)

    fun fetchLatestRelease(): ReleaseInfo {
        val releases = fetchReleases()
        val stableSelection = findLatestStableRelease(releases)
        val root = stableSelection.release
            ?: throw UpdateCheckException(
                debugSummary = buildString {
                    appendLine("URL: $RELEASES_URL")
                    appendLine("No valid OTA release found.")
                    appendLine("Scanned releases: ${releases.length()}")
                    appendLine("Skipped missing VERSION_CODE: ${stableSelection.skippedMissingVersionCode}")
                    appendLine("VERSION_CODE missing from release body: ${stableSelection.skippedMissingVersionCode}")
                    appendLine("Skipped odd VERSION_CODE: ${stableSelection.skippedOddVersionCode}")
                    appendLine("Skipped prerelease/draft: ${stableSelection.skippedDraftOrPrerelease}")
                    appendLine("Selected release: ${stableSelection.selectedTagName ?: "none"}")
                    appendLine("Selected VERSION_CODE: ${stableSelection.selectedVersionCode ?: "-"}")
                    append("Napaka: Veljaven navaden release ni bil najden.")
                }.trim(),
                message = "Navaden GitHub release ni na voljo."
            )
        return releaseInfoFromJson(root, releases.length())
    }

    fun fetchRollbackRelease(currentVersionCode: Long): ReleaseInfo {
        val releases = fetchReleases()
        val root = findLatestRollbackRelease(releases)
            ?: throw UpdateCheckException(
                debugSummary = buildString {
                    appendLine("URL: $RELEASES_URL")
                    appendLine("Releases: ${releases.length()}")
                    append("Napaka: Veljaven rollback release ni bil najden.")
                }.trim(),
                message = "Rollback izdaja ni na voljo."
            )

        val releaseInfo = releaseInfoFromJson(root, releases.length())
        val rollbackVersionName = releaseInfo.tagName.removePrefix(ROLLBACK_TAG_PREFIX)
        val rollbackVersionCode = releaseInfo.versionCode ?: extractReleaseVersionCode(rollbackVersionName)
        if (rollbackVersionCode == null || rollbackVersionCode <= currentVersionCode || rollbackVersionCode % 2L != 1L) {
            throw UpdateCheckException(
                debugSummary = buildString {
                    appendLine("URL: $RELEASES_URL")
                    appendLine("Releases: ${releases.length()}")
                    appendLine("Rollback tag: ${releaseInfo.tagName}")
                    appendLine("Rollback versionCode: ${rollbackVersionCode ?: -1}")
                    append("Napaka: Rollback izdaja ni veljavna za trenutno verzijo $currentVersionCode.")
                }.trim(),
                message = "Rollback izdaja ni veljavna."
            )
        }

        return releaseInfo
    }

    fun getLatestReleaseUrl(): String {
        return RELEASES_URL
    }

    private data class ParsedReleaseVersion(
        val versionCode: Long?,
        val versionCodeFromBody: Long?,
        val source: String
    )

    private data class StableReleaseSelection(
        val release: JSONObject?,
        val skippedMissingVersionCode: Int,
        val skippedOddVersionCode: Int,
        val skippedDraftOrPrerelease: Int,
        val selectedTagName: String?,
        val selectedVersionCode: Long?
    )

    private fun findLatestStableRelease(releases: JSONArray): StableReleaseSelection {
        var latestRelease: JSONObject? = null
        var latestVersionCode: Long? = null
        var skippedMissingVersionCode = 0
        var skippedOddVersionCode = 0
        var skippedDraftOrPrerelease = 0

        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            val tagName = release.optString("tag_name")
            if (!STABLE_RELEASE_TAG_REGEX.matcher(tagName).matches()) {
                continue
            }
            if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
                skippedDraftOrPrerelease++
                continue
            }

            val parsedVersion = parseReleaseVersion(release)
            val bodyVersionCode = parsedVersion.versionCodeFromBody
            if (bodyVersionCode == null) {
                skippedMissingVersionCode++
                continue
            }
            if (bodyVersionCode % 2L != 0L) {
                skippedOddVersionCode++
                continue
            }
            if (latestVersionCode == null || bodyVersionCode > latestVersionCode) {
                latestRelease = release
                latestVersionCode = bodyVersionCode
            }
        }

        return StableReleaseSelection(
            release = latestRelease,
            skippedMissingVersionCode = skippedMissingVersionCode,
            skippedOddVersionCode = skippedOddVersionCode,
            skippedDraftOrPrerelease = skippedDraftOrPrerelease,
            selectedTagName = latestRelease?.optString("tag_name")?.takeIf { it.isNotBlank() },
            selectedVersionCode = latestVersionCode
        )
    }

    private fun findLatestRollbackRelease(releases: JSONArray): JSONObject? {
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            val tagName = release.optString("tag_name")
            if (ROLLBACK_RELEASE_TAG_REGEX.matcher(tagName).matches()) {
                return release
            }
        }
        return null
    }

    private fun extractReleaseVersionCode(versionName: String): Long? {
        val parts = versionName.split('.').filter { it.isNotBlank() }
        if (parts.size >= 2) {
            return (parts.first() + parts.last()).toLongOrNull()
        }
        return parts.lastOrNull()?.toLongOrNull()
    }

    private fun extractReleaseVersionCodeFromBody(body: String): Long? {
        val matcher = RELEASE_BODY_VERSION_CODE_REGEX.matcher(body)
        return if (matcher.find()) {
            matcher.group(1)?.toLongOrNull()
        } else {
            null
        }
    }

    private fun parseReleaseVersion(root: JSONObject): ParsedReleaseVersion {
        val tagName = root.optString("tag_name")
        val body = root.optString("body")
        val fallbackVersionName = tagName.removePrefix(ROLLBACK_TAG_PREFIX).removePrefix("v")
        val bodyVersionCode = extractReleaseVersionCodeFromBody(body)
        val fallbackVersionCode = extractReleaseVersionCode(fallbackVersionName)
        return when {
            bodyVersionCode != null -> ParsedReleaseVersion(
                versionCode = bodyVersionCode,
                versionCodeFromBody = bodyVersionCode,
                source = "release_body"
            )
            fallbackVersionCode != null -> ParsedReleaseVersion(
                versionCode = fallbackVersionCode,
                versionCodeFromBody = null,
                source = "tag_fallback"
            )
            else -> ParsedReleaseVersion(
                versionCode = null,
                versionCodeFromBody = null,
                source = "missing"
            )
        }
    }

    private fun fetchReleases(): JSONArray {
        val releaseUrl = URL(RELEASES_URL)
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
                    appendLine("URL: $RELEASES_URL")
                    appendLine("HTTP: $responseCode ${responseMessage.ifBlank { "Unknown" }}")
                    appendLine("Napaka: HTTP")
                    appendLine("Sporocilo: GitHub release endpoint ni vrnil uspesnega odgovora.")
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
            JSONArray(responseText)
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

    private fun releaseInfoFromJson(root: JSONObject, releaseCount: Int): ReleaseInfo {
        val tagName = root.optString("tag_name")
        val body = root.optString("body")
        val assets = root.optJSONArray("assets") ?: JSONArray()
        val assetNames = mutableListOf<String>()
        val apkUrl = findApkUrl(assets, assetNames)
        val parsedVersion = parseReleaseVersion(root)
        val debugSummary = buildString {
            appendLine("URL: $RELEASES_URL")
            appendLine("Releases: $releaseCount")
            appendLine("JSON tag_name: ${if (tagName.isNotBlank()) "da" else "ne"}")
            appendLine("tag_name: ${if (tagName.isNotBlank()) tagName else "-"}")
            appendLine("VERSION_CODE body: ${parsedVersion.versionCodeFromBody ?: "-"}")
            appendLine("versionCode used: ${parsedVersion.versionCode ?: "-"}")
            appendLine("versionCode source: ${parsedVersion.source}")
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

        return ReleaseInfo(
            tagName = tagName,
            body = body,
            apkUrl = apkUrl,
            versionCode = parsedVersion.versionCode,
            versionCodeFromBody = parsedVersion.versionCodeFromBody,
            versionCodeSource = parsedVersion.source,
            debugSummary = debugSummary
        )
    }

    private fun findApkUrl(assets: JSONArray, assetNames: MutableList<String>): String? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.isNotBlank()) {
                assetNames.add(name)
            }
            if (name in APK_ASSET_NAMES) {
                val assetUrl = asset.optString("browser_download_url")
                Log.i("NovaRehabUpdater", "ASSET: $name URL: $assetUrl")
                return assetUrl
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
            appendLine("URL: $RELEASES_URL")
            appendLine("Napaka: ${error.javaClass.name}")
            appendLine("Sporocilo: ${error.message ?: "-"}")
        }.trim()
        throw UpdateCheckException(debugSummary = debugSummary, message = message)
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/trsavecdusan-wq/rehab2/releases"
        private val APK_ASSET_NAMES = setOf(
            "app-debug.apk",
            "app-release.apk",
            "rehab-debug.apk",
            "rehab-release.apk"
        )
        private val STABLE_RELEASE_TAG_REGEX = Pattern.compile("^v\\d+\\.\\d+\\.\\d+$")
        private const val ROLLBACK_TAG_PREFIX = "rollback-v"
        private val RELEASE_BODY_VERSION_CODE_REGEX =
            Pattern.compile("(?m)^\\s*VERSION_CODE\\s*=\\s*(\\d+)\\s*$")
        private val ROLLBACK_RELEASE_TAG_REGEX =
            Pattern.compile("^rollback-v\\d+\\.\\d+\\.\\d+$")
    }
}
