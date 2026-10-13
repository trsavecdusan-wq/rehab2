package com.rehab2.trialupdate

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.regex.Pattern

/**
 * Trial-only update channel. Compiled only into the "trial" build type (app/src/trial),
 * so debug and release APKs never contain it. Reads a public GitHub release, no token.
 */
class TrialUpdateClient {
    data class TrialRelease(
        val tagName: String,
        val versionCode: Long,
        val apkUrl: String
    )

    enum class FailureReason {
        RELEASE_NOT_FOUND,
        HTTP_ERROR,
        APK_ASSET_MISSING,
        VERSION_CODE_MISSING,
        NO_CONNECTION,
        INVALID_RESPONSE
    }

    class TrialUpdateException(
        val reason: FailureReason,
        message: String
    ) : IllegalStateException(message)

    fun fetchTrialRelease(): TrialRelease {
        val root = fetchReleaseJson()
        val tagName = root.optString("tag_name")
        if (tagName.isBlank()) {
            throw TrialUpdateException(
                FailureReason.INVALID_RESPONSE,
                "Preverjanje ni uspelo: neveljaven odgovor GitHub (ni tag_name)."
            )
        }

        val apkUrl = findApkUrl(root)
            ?: throw TrialUpdateException(
                FailureReason.APK_ASSET_MISSING,
                "Release $tagName nima datoteke $APK_ASSET_NAME."
            )

        val versionCode = extractVersionCode(root.optString("body"))
            ?: throw TrialUpdateException(
                FailureReason.VERSION_CODE_MISSING,
                "Release $tagName nima vrstice TRIAL_VERSION_CODE=<n> v opisu."
            )

        Log.i(TAG, "Trial release tag=$tagName versionCode=$versionCode apk=$apkUrl")
        return TrialRelease(tagName = tagName, versionCode = versionCode, apkUrl = apkUrl)
    }

    private fun fetchReleaseJson(): JSONObject {
        val connection = URL(RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "NovaRehab-TrialUpdater")

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw TrialUpdateException(
                    FailureReason.RELEASE_NOT_FOUND,
                    "Trial release $RELEASE_TAG ni najden (HTTP 404)."
                )
            }
            if (responseCode !in 200..299) {
                throw TrialUpdateException(
                    FailureReason.HTTP_ERROR,
                    "GitHub ni vrnil uspešnega odgovora (HTTP $responseCode)."
                )
            }
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(responseText)
        } catch (error: TrialUpdateException) {
            throw error
        } catch (error: UnknownHostException) {
            throw TrialUpdateException(FailureReason.NO_CONNECTION, "Ni internetne povezave.")
        } catch (error: SocketTimeoutException) {
            throw TrialUpdateException(FailureReason.NO_CONNECTION, "Ni internetne povezave ali je zahteva potekla.")
        } catch (error: JSONException) {
            throw TrialUpdateException(FailureReason.INVALID_RESPONSE, "Preverjanje ni uspelo: neveljaven odgovor GitHub.")
        } catch (error: IOException) {
            throw TrialUpdateException(FailureReason.NO_CONNECTION, "Napaka pri branju GitHub odgovora.")
        } finally {
            connection.disconnect()
        }
    }

    private fun findApkUrl(root: JSONObject): String? {
        val assets = root.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (asset.optString("name") == APK_ASSET_NAME) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun extractVersionCode(body: String): Long? {
        val matcher = VERSION_CODE_REGEX.matcher(body)
        return if (matcher.find()) matcher.group(1)?.toLongOrNull() else null
    }

    companion object {
        private const val TAG = "NovaRehabTrialUpdater"
        private const val RELEASE_TAG = "trial-latest"
        private const val RELEASE_URL =
            "https://api.github.com/repos/trsavecdusan-wq/rehab2-trial/releases/tags/$RELEASE_TAG"
        private const val APK_ASSET_NAME = "app-trial.apk"
        private val VERSION_CODE_REGEX =
            Pattern.compile("(?m)^\\s*TRIAL_VERSION_CODE\\s*=\\s*(\\d+)\\s*$")
    }
}
