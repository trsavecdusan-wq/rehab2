package com.rehab2.aac

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

object WeatherClient {
    private const val TIMEOUT_MS = 1800
    private const val TAG = "WeatherClient"

    fun fetchOrientationSentence(sourceUrl: String): String? {
        Log.d(TAG, "STATUS_ORIENTATION weatherUrl=$sourceUrl")
        if (sourceUrl.isBlank()) {
            logWeatherFailure()
            return null
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(sourceUrl).openConnection() as? HttpURLConnection)?.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                useCaches = false
            }
            val activeConnection = connection ?: return null
            val responseCode = activeConnection.responseCode
            if (responseCode !in 200..299) {
                logWeatherFailure()
                return null
            }

            val body = activeConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseOpenMeteo(body).also { sentence ->
                Log.d(TAG, "STATUS_ORIENTATION weatherFetchOk=${sentence != null}")
            }
        } catch (_: Exception) {
            logWeatherFailure()
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseOpenMeteo(body: String): String? {
        val root = JSONObject(body)
        val current = root.optJSONObject("current")
            ?: root.optJSONObject("current_weather")
            ?: run {
                Log.d(TAG, "STATUS_ORIENTATION temperature=null")
                Log.d(TAG, "STATUS_ORIENTATION weatherCode=null")
                return null
            }
        val temperature = when {
            current.has("temperature_2m") -> current.optDouble("temperature_2m")
            current.has("temperature") -> current.optDouble("temperature")
            else -> Double.NaN
        }
        if (temperature.isNaN()) {
            Log.d(TAG, "STATUS_ORIENTATION temperature=null")
            Log.d(TAG, "STATUS_ORIENTATION weatherCode=null")
            return null
        }

        val weatherCode = when {
            current.has("weather_code") -> current.optInt("weather_code")
            current.has("weathercode") -> current.optInt("weathercode")
            else -> null
        }
        Log.d(TAG, "STATUS_ORIENTATION temperature=${temperature.roundToInt()}")
        Log.d(TAG, "STATUS_ORIENTATION weatherCode=$weatherCode")
        val weatherText = weatherCode?.let { weatherDescriptionSl(it) }
        return buildString {
            append("Zunaj je približno ${temperature.roundToInt()} stopinj")
            if (!weatherText.isNullOrBlank()) {
                append(", ")
                append(weatherText)
            }
            append(".")
        }
    }

    private fun weatherDescriptionSl(code: Int): String {
        return when (code) {
            0 -> "jasno"
            1, 2, 3 -> "delno obla\u010dno"
            45, 48 -> "megla"
            51, 53, 55 -> "rosenje"
            61, 63, 65 -> "de\u017e"
            71, 73, 75 -> "sneg"
            95 -> "je nevihta"
            else -> ""
        }
    }

    private fun logWeatherFailure() {
        Log.d(TAG, "STATUS_ORIENTATION weatherFetchOk=false")
        Log.d(TAG, "STATUS_ORIENTATION temperature=null")
        Log.d(TAG, "STATUS_ORIENTATION weatherCode=null")
    }
}
