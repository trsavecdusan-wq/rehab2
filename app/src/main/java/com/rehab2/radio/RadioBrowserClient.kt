package com.rehab2.radio

import android.net.Uri
import org.json.JSONArray
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class RadioBrowserClient {
    enum class SearchMode {
        NAME,
        GENRE,
        COUNTRY
    }

    fun search(mode: SearchMode, query: String): List<RadioSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            throw IOException("Vpi\u0161i iskalni pojem")
        }

        val mappedQuery = when (trimmed.lowercase()) {
            "slovenija" -> "Slovenia"
            "ukrajina" -> "Ukraine"
            else -> trimmed
        }

        val uriBuilder = Uri.parse("https://de1.api.radio-browser.info/json/stations/search").buildUpon()
            .appendQueryParameter("hidebroken", "true")
            .appendQueryParameter("limit", "20")
            .appendQueryParameter("order", "clickcount")
            .appendQueryParameter("reverse", "true")

        when (mode) {
            SearchMode.NAME -> uriBuilder.appendQueryParameter("name", mappedQuery)
            SearchMode.GENRE -> uriBuilder.appendQueryParameter("tag", mappedQuery)
            SearchMode.COUNTRY -> {
                if (mappedQuery.length == 2) {
                    uriBuilder.appendQueryParameter("countrycode", mappedQuery.uppercase())
                } else {
                    uriBuilder.appendQueryParameter("country", mappedQuery)
                }
            }
        }

        val connection = (URL(uriBuilder.build().toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            requestMethod = "GET"
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            if (stream == null) {
                throw IOException("Napaka pri iskanju postaj")
            }

            val response = BufferedReader(InputStreamReader(stream)).use { reader ->
                buildString {
                    while (true) {
                        val line = reader.readLine() ?: break
                        append(line)
                    }
                }
            }

            if (code !in 200..299) {
                throw IOException("Napaka pri iskanju postaj")
            }

            return parseResults(response)
        } catch (error: Exception) {
            if (error is IOException) throw error
            throw IOException("Napaka pri iskanju postaj")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResults(response: String): List<RadioSearchResult> {
        val array = JSONArray(response)
        val results = mutableListOf<RadioSearchResult>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val streamUrl = item.optString("url_resolved").ifBlank { item.optString("url") }.trim()
            val stationUuid = item.optString("stationuuid").trim()
            val name = item.optString("name").trim()

            results += RadioSearchResult(
                stationUuid = stationUuid,
                name = name,
                streamUrl = streamUrl,
                country = item.optString("country").trim(),
                tags = item.optString("tags").trim(),
                faviconUrl = item.optString("favicon").trim().ifBlank { null },
                codec = item.optString("codec").trim().ifBlank { null },
                bitrate = item.optInt("bitrate").takeIf { it > 0 }
            )
        }

        return results
    }
}