package com.rehab2.aac

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

object WeatherClient {
    private const val TIMEOUT_MS = 1800

    fun fetchOrientationSentence(sourceUrl: String): String? {
        if (sourceUrl.isBlank()) return null

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
            if (responseCode !in 200..299) return null

            val body = activeConnection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseOpenMeteo(body)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseOpenMeteo(body: String): String? {
        val root = JSONObject(body)
        val current = root.optJSONObject("current")
            ?: root.optJSONObject("current_weather")
            ?: return null
        val temperature = when {
            current.has("temperature_2m") -> current.optDouble("temperature_2m")
            current.has("temperature") -> current.optDouble("temperature")
            else -> Double.NaN
        }
        if (temperature.isNaN()) return null

        val weatherCode = when {
            current.has("weather_code") -> current.optInt("weather_code")
            current.has("weathercode") -> current.optInt("weathercode")
            else -> null
        }
        val weatherText = weatherCode?.let { weatherDescriptionSl(it) }
        return buildString {
            append("Zunaj je ${temperature.roundToInt()} stopinj")
            if (!weatherText.isNullOrBlank()) {
                append(" in ")
                append(weatherText)
            }
            append(".")
        }
    }

    private fun weatherDescriptionSl(code: Int): String {
        return when (code) {
            0 -> "jasno"
            1, 2 -> "delno obla\u010dno"
            3 -> "obla\u010dno"
            45, 48 -> "megleno"
            51, 53, 55 -> "rosi"
            56, 57 -> "ledeno rosi"
            61, 63, 65 -> "de\u017euje"
            66, 67 -> "pada ledeni de\u017e"
            71, 73, 75 -> "sne\u017ei"
            77 -> "pada zrnat sneg"
            80, 81, 82 -> "so plohe"
            85, 86 -> "so sne\u017ene plohe"
            95 -> "je nevihta"
            96, 99 -> "je nevihta s to\u010do"
            else -> "vreme je preverjeno"
        }
    }
}
