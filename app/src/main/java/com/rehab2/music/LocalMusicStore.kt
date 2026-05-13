package com.rehab2.music

import android.content.Context
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LocalMusicStore(private val context: Context) {
    fun getAllTracks(): List<LocalMusicTrack> {
        val file = getIndexFile()
        if (!file.exists()) {
            return emptyList()
        }

        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val tracksArray = root.optJSONArray("tracks") ?: JSONArray()
            buildList {
                for (index in 0 until tracksArray.length()) {
                    val item = tracksArray.optJSONObject(index) ?: continue
                    add(
                        LocalMusicTrack(
                            id = item.optString("id"),
                            displayName = item.optString("displayName"),
                            localFileName = item.optString("localFileName"),
                            localPath = item.optString("localPath"),
                            sourceDisplayName = item.optString("sourceDisplayName"),
                            sizeBytes = item.optLong("sizeBytes"),
                            mimeType = item.optString("mimeType"),
                            importedAt = item.optLong("importedAt")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun hasTrack(displayName: String, sizeBytes: Long): Boolean {
        return getAllTracks().any {
            it.displayName.equals(displayName, ignoreCase = true) && it.sizeBytes == sizeBytes
        }
    }

    fun addImportedTrack(track: LocalMusicTrack) {
        val tracks = getAllTracks().toMutableList()
        tracks += track
        writeTracks(tracks)
    }

    fun createUniqueLibraryFile(displayName: String): File {
        val libraryDir = getLibraryDir()
        val sanitized = sanitizeFileName(displayName)
        val dotIndex = sanitized.lastIndexOf('.')
        val baseName = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
        val extension = if (dotIndex > 0) sanitized.substring(dotIndex) else ""

        var counter = 0
        var candidate = File(libraryDir, sanitized)
        while (candidate.exists()) {
            counter += 1
            candidate = File(libraryDir, "${baseName}_$counter$extension")
        }
        return candidate
    }

    fun createTrack(
        displayName: String,
        localFileName: String,
        localPath: String,
        sourceDisplayName: String,
        sizeBytes: Long,
        mimeType: String
    ): LocalMusicTrack {
        return LocalMusicTrack(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            localFileName = localFileName,
            localPath = localPath,
            sourceDisplayName = sourceDisplayName,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            importedAt = System.currentTimeMillis()
        )
    }

    private fun writeTracks(tracks: List<LocalMusicTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject().apply {
                    put("id", track.id)
                    put("displayName", track.displayName)
                    put("localFileName", track.localFileName)
                    put("localPath", track.localPath)
                    put("sourceDisplayName", track.sourceDisplayName)
                    put("sizeBytes", track.sizeBytes)
                    put("mimeType", track.mimeType)
                    put("importedAt", track.importedAt)
                }
            )
        }

        val root = JSONObject().apply {
            put("tracks", array)
        }

        val file = getIndexFile()
        file.parentFile?.mkdirs()
        file.writeText(root.toString(2), Charsets.UTF_8)
    }

    private fun getMusicBaseDir(): File {
        val externalDirs = context.getExternalFilesDirs(null)
        val removableDir = externalDirs.firstOrNull { dir ->
            dir != null && Environment.isExternalStorageRemovable(dir)
        }
        return (removableDir ?: context.filesDir).resolve("music")
    }

    private fun getLibraryDir(): File {
        return getMusicBaseDir().resolve("library").apply { mkdirs() }
    }

    private fun getIndexFile(): File {
        return getMusicBaseDir().resolve("index.json")
    }

    private fun sanitizeFileName(displayName: String): String {
        val cleaned = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "audio_file" }
    }
}
