package com.rehab2.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RadioStationStore(private val context: Context) {
    data class SaveResult(
        val duplicate: Boolean,
        val page: Int,
        val position: Int
    )

    data class UpdateResult(
        val success: Boolean,
        val invalidSlot: Boolean = false
    )

    fun loadStations(): List<SavedRadioStation> {
        val file = getStoreFile()
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val stations = root.optJSONArray("stations") ?: JSONArray()
            buildList {
                for (index in 0 until stations.length()) {
                    val item = stations.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    add(
                        SavedRadioStation(
                            stationUuid = item.optString("stationUuid"),
                            name = name,
                            buttonLabel = normalizeButtonLabel(
                                item.optString("buttonLabel").ifBlank { deriveButtonLabel(name) }
                            ),
                            streamUrl = item.optString("streamUrl"),
                            country = item.optString("country"),
                            genre = item.optString("genre"),
                            faviconUrl = item.optString("faviconUrl"),
                            codec = item.optString("codec"),
                            bitrate = item.optInt("bitrate"),
                            visible = item.optBoolean("visible", true),
                            page = item.optInt("page", 1),
                            position = item.optInt("position", 1)
                        )
                    )
                }
            }.sortedWith(compareBy<SavedRadioStation> { it.page }.thenBy { it.position })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getStationsForPage(page: Int): List<SavedRadioStation> {
        return loadStations()
            .filter { it.page == page }
            .sortedBy { it.position }
    }

    fun findStation(stationUuid: String, streamUrl: String): SavedRadioStation? {
        return loadStations().firstOrNull { matchesIdentity(it, stationUuid, streamUrl) }
    }

    fun saveStation(candidate: SavedRadioStation): SaveResult {
        val stations = loadStations().toMutableList()
        val duplicate = stations.any { existing ->
            matchesIdentity(existing, candidate.stationUuid, candidate.streamUrl)
        }

        if (duplicate) {
            return SaveResult(duplicate = true, page = 0, position = 0)
        }

        val freeSlot = findFirstFreeSlot(stations)
        val stationToSave = candidate.copy(
            buttonLabel = normalizeButtonLabel(
                candidate.buttonLabel.ifBlank { deriveButtonLabel(candidate.name) }
            ),
            visible = true,
            page = freeSlot.first,
            position = freeSlot.second
        )
        stations += stationToSave
        writeStations(stations)
        return SaveResult(duplicate = false, page = freeSlot.first, position = freeSlot.second)
    }

    fun toggleVisibility(stationUuid: String, streamUrl: String): SavedRadioStation? {
        val stations = loadStations().toMutableList()
        val index = stations.indexOfFirst { matchesIdentity(it, stationUuid, streamUrl) }
        if (index == -1) {
            return null
        }

        val updated = stations[index].copy(visible = !stations[index].visible)
        stations[index] = updated
        writeStations(stations)
        return updated
    }

    fun updateStation(updatedStation: SavedRadioStation): UpdateResult {
        if (updatedStation.page < 1 || updatedStation.position !in 1..6) {
            return UpdateResult(success = false, invalidSlot = true)
        }

        val stations = loadStations().toMutableList()
        val currentIndex = stations.indexOfFirst {
            matchesIdentity(it, updatedStation.stationUuid, updatedStation.streamUrl)
        }
        if (currentIndex == -1) {
            return UpdateResult(success = false)
        }

        val currentStation = stations[currentIndex]
        val normalized = updatedStation.copy(
            buttonLabel = normalizeButtonLabel(
                updatedStation.buttonLabel.ifBlank { deriveButtonLabel(updatedStation.name) }
            )
        )

        val occupantIndex = stations.indexOfFirst { existing ->
            existing !== currentStation &&
                existing.page == normalized.page &&
                existing.position == normalized.position
        }

        if (occupantIndex >= 0) {
            val occupant = stations[occupantIndex]
            stations[occupantIndex] = occupant.copy(
                page = currentStation.page,
                position = currentStation.position
            )
        }

        stations[currentIndex] = normalized
        writeStations(stations)
        return UpdateResult(success = true)
    }

    private fun matchesIdentity(existing: SavedRadioStation, stationUuid: String, streamUrl: String): Boolean {
        return if (stationUuid.isNotBlank() && existing.stationUuid.isNotBlank()) {
            existing.stationUuid == stationUuid
        } else {
            existing.streamUrl.equals(streamUrl, ignoreCase = true)
        }
    }

    private fun findFirstFreeSlot(stations: List<SavedRadioStation>): Pair<Int, Int> {
        var page = 1
        while (true) {
            for (position in 1..6) {
                val taken = stations.any { it.page == page && it.position == position }
                if (!taken) {
                    return page to position
                }
            }
            page += 1
        }
    }

    private fun writeStations(stations: List<SavedRadioStation>) {
        val array = JSONArray()
        stations.sortedWith(compareBy<SavedRadioStation> { it.page }.thenBy { it.position }).forEach { station ->
            array.put(
                JSONObject().apply {
                    put("stationUuid", station.stationUuid)
                    put("name", station.name)
                    put("buttonLabel", normalizeButtonLabel(station.buttonLabel))
                    put("streamUrl", station.streamUrl)
                    put("country", station.country)
                    put("genre", station.genre)
                    put("faviconUrl", station.faviconUrl)
                    put("codec", station.codec)
                    put("bitrate", station.bitrate)
                    put("visible", station.visible)
                    put("page", station.page)
                    put("position", station.position)
                }
            )
        }

        val root = JSONObject().apply {
            put("stations", array)
        }

        val file = getStoreFile()
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2), Charsets.UTF_8)
    }

    private fun getStoreFile(): File {
        return File(File(context.filesDir, "radio"), "stations.json")
    }

    companion object {
        fun deriveButtonLabel(name: String): String {
            val cleanName = name.trim().replace(Regex("\\s+"), " ")
            if (cleanName.isBlank()) {
                return ""
            }

            val words = cleanName.split(" ")
            return when {
                words.size == 1 -> normalizeButtonLabel(words.first().take(12))
                else -> normalizeButtonLabel(
                    words.first().take(12) + "\n" + words.drop(1).joinToString(" ").take(12)
                )
            }
        }

        fun normalizeButtonLabel(raw: String): String {
            val lines = raw.replace("\r", "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(2)
                .map { it.take(12) }
            return lines.joinToString("\n")
        }
    }
}
