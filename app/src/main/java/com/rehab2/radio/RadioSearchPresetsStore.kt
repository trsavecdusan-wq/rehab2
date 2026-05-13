package com.rehab2.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RadioSearchPresetsStore(private val context: Context) {
    fun loadCountries(): List<String> {
        val file = getStoreFile()
        if (!file.exists()) {
            writeCountries(DEFAULT_COUNTRIES)
            return DEFAULT_COUNTRIES
        }

        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val countries = root.optJSONArray("countries") ?: JSONArray()
            buildList {
                for (index in 0 until countries.length()) {
                    val value = countries.optString(index).trim()
                    if (value.isNotEmpty() && value !in this) {
                        add(value)
                    }
                }
            }.ifEmpty {
                writeCountries(DEFAULT_COUNTRIES)
                DEFAULT_COUNTRIES
            }
        } catch (_: Exception) {
            writeCountries(DEFAULT_COUNTRIES)
            DEFAULT_COUNTRIES
        }
    }

    fun addCountryIfMissing(country: String) {
        val normalized = country.trim()
        if (normalized.isEmpty()) {
            return
        }

        val current = loadCountries().toMutableList()
        if (current.none { it.equals(normalized, ignoreCase = true) }) {
            current.add(normalized)
            writeCountries(current)
        }
    }

    private fun writeCountries(countries: List<String>) {
        val radioDir = File(context.filesDir, "radio").apply { mkdirs() }
        val array = JSONArray()
        countries.forEach { array.put(it) }
        val root = JSONObject().put("countries", array)
        File(radioDir, FILE_NAME).writeText(root.toString(2), Charsets.UTF_8)
    }

    private fun getStoreFile(): File {
        val radioDir = File(context.filesDir, "radio").apply { mkdirs() }
        return File(radioDir, FILE_NAME)
    }

    companion object {
        private const val FILE_NAME = "search_presets.json"
        private val DEFAULT_COUNTRIES = listOf(
            "Slovenija",
            "Ukrajina",
            "Hrvaška",
            "Avstrija",
            "Nemčija"
        )
    }
}
