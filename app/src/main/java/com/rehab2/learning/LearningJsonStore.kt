package com.rehab2.learning

import java.io.File

/** Pattern from aac/ai/AiJsonStore. A file over the limit is never deleted or overwritten. */
class LearningJsonStore(private val file: File) {
    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
    private val backup = File(file.path + ".bak")

    private fun recover() {
        if (backup.exists()) {
            check(!file.exists() || file.delete()) { "Cannot recover learning storage" }
            check(backup.renameTo(file)) { "Cannot restore learning storage backup" }
        }
    }

    /** Returns null when the file does not exist. */
    fun readText(): String? {
        require(file.length() <= MAX_BYTES && backup.length() <= MAX_BYTES) { "Learning storage limit exceeded" }
        recover()
        if (!file.exists()) return null
        return file.readText(Charsets.UTF_8)
    }

    fun writeText(text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Learning storage limit exceeded" }
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        recover()
        // API 21 compatible transaction: retain the previous generation until the new file is complete.
        val temporary = File(file.path + ".tmp")
        temporary.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
        check(!file.exists() || file.renameTo(backup)) { "Cannot back up learning storage" }
        try {
            check(temporary.renameTo(file)) { "Cannot commit learning storage" }
            check(!backup.exists() || backup.delete()) { "Cannot finish learning storage commit" }
        } catch (error: Exception) { recover(); throw error }
    }
}
