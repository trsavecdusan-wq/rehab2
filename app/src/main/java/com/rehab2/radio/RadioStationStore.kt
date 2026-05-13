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
                    add(
                        SavedRadioStation(
                            stationUuid = item.optString("stationUuid"),
                            name = item.optString("name"),
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
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveStation(candidate: SavedRadioStation): SaveResult {
        val stations = loadStations().toMutableList()
        val duplicate = stations.any { existing ->
            candidate.stationUuid.isNotBlank() && existing.stationUuid.isNotBlank() &&
                existing.stationUuid == candidate.stationUuid
        } || (
            candidate.stationUuid.isBlank() &&
                stations.any { existing -> existing.streamUrl.equals(candidate.streamUrl, ignoreCase = true) }
            )

        if (duplicate) {
            return SaveResult(duplicate = true, page = 0, position = 0)
        }

        val freeSlot = findFirstFreeSlot(stations)
        val stationToSave = candidate.copy(
            visible = true,
            page = freeSlot.first,
            position = freeSlot.second
        )
        stations += stationToSave
        writeStations(stations)
        return SaveResult(duplicate = false, page = freeSlot.first, position = freeSlot.second)
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
}
