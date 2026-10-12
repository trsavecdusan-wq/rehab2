package com.rehab2.aac.icons

import android.graphics.BitmapFactory
import java.io.File

/** Bounded full decode for admin imports. Call on a worker; never on the AAC rendering path. */
object AndroidIconDecoder {
    fun read(file: File): AacIconFingerprint = AacIconFingerprint.read(file, ::decode)
    private fun decode(file: File): IconImageInfo? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..AacIconFingerprint.MAX_DIMENSION || bounds.outHeight !in 1..AacIconFingerprint.MAX_DIMENSION) return null
        val format = when (bounds.outMimeType) { "image/png" -> "PNG"; "image/jpeg" -> "JPEG"; "image/webp" -> "WEBP"; else -> return null }
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        return try { IconImageInfo(bitmap.width, bitmap.height, format, bitmap.hasAlpha()) } finally { bitmap.recycle() }
    }
}
