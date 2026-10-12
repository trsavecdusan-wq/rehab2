package com.rehab2.aac.icons

import java.io.File
import java.security.MessageDigest

data class IconImageInfo(val width: Int, val height: Int, val format: String, val alpha: Boolean)
enum class IconImageStatus { UNIQUE_IMAGE, EXACT_IMAGE_DUPLICATE, POSSIBLE_VISUAL_DUPLICATE, MISSING_IMAGE, INVALID_IMAGE }
data class AacIconFingerprint(val canonicalPath: String, val size: Long, val image: IconImageInfo?, val sha256: String?, val status: IconImageStatus) {
    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        const val MAX_DIMENSION = 4096
        /** The decoder must fully read the image, not just trust its extension or header. No writes. */
        fun read(file: File, decode: (File) -> IconImageInfo?): AacIconFingerprint {
            var path = file.absolutePath
            var size = 0L
            return try {
                path = file.canonicalPath
                if (!file.exists()) return AacIconFingerprint(path, 0, null, null, IconImageStatus.MISSING_IMAGE)
                size = file.length()
                require(file.isFile && file.canRead() && size in 1..MAX_BYTES)
                val info = requireNotNull(decode(file))
                require(info.width in 1..MAX_DIMENSION && info.height in 1..MAX_DIMENSION)
                require(info.format in setOf("PNG", "JPEG", "WEBP"))
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        bytes += n; require(bytes <= MAX_BYTES); digest.update(buffer, 0, n)
                    }
                }
                require(bytes == size && file.length() == size)
                AacIconFingerprint(path, size, info, digest.digest().joinToString("") { "%02x".format(it) }, IconImageStatus.UNIQUE_IMAGE)
            } catch (_: Exception) { AacIconFingerprint(path, size, null, null, IconImageStatus.INVALID_IMAGE) }
        }
    }
}
