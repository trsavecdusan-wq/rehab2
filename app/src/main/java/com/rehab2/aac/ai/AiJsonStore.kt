package com.rehab2.aac.ai

import java.io.File
import org.json.JSONArray

/** Separate internal store. A corrupt file is never silently overwritten. */
internal class AiJsonStore(private val file: File) {
    companion object { val lock = Any(); const val MAX_BYTES = 8 * 1024 * 1024 }
    private val backup = File(file.path + ".bak")
    private fun recover() {
        if (backup.exists()) {
            check(!file.exists() || file.delete()) { "Cannot recover AI storage" }
            check(backup.renameTo(file)) { "Cannot restore AI storage backup" }
        }
    }
    fun read(): JSONArray {
        require(file.length() <= MAX_BYTES && backup.length() <= MAX_BYTES) { "AI storage limit exceeded" }
        recover()
        if (!file.exists()) return JSONArray()
        return JSONArray(file.readText(Charsets.UTF_8))
    }
    fun write(rows: JSONArray) {
        val bytes = rows.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "AI storage limit exceeded" }
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        recover()
        // API 21 compatible transaction: retain the previous generation until the new file is complete.
        val temporary = File(file.path + ".tmp")
        temporary.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
        check(!file.exists() || file.renameTo(backup)) { "Cannot back up AI storage" }
        try {
            check(temporary.renameTo(file)) { "Cannot commit AI storage" }
            check(!backup.exists() || backup.delete()) { "Cannot finish AI storage commit" }
        } catch (error: Exception) { recover(); throw error }
    }
}

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
