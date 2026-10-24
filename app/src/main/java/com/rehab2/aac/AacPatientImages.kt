package com.rehab2.aac

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.rehab2.patient.PatientImageStore
import java.io.File

/** Separate from AacProfile (DOM/TRGOVINA). No global AAC item or patient JSON is rewritten. */
object AacPatientImages {
    fun store(context: Context) = PatientImageStore(File(context.noBackupFilesDir, "aac-patients"))

    fun personalFile(context: Context, itemId: String): File? = try {
        val store = store(context)
        store.resolve(store.currentPatientId(), itemId)?.let(::preview)
    } catch (_: Exception) {
        null // Fail closed on a damaged private index; retain the existing system image.
    }

    fun resolve(context: Context, item: AacItem): File? {
        personalFile(context, item.id)?.let { return it }
        val showLegacyImage = try {
            val store = store(context)
            val patientId = store.currentPatientId()
            store.ownsLegacyImages(patientId) && !store.hasBinding(patientId, item.id)
        } catch (_: Exception) { false }
        val generic = item.iconSource in setOf(IconSource.SYSTEM, IconSource.SOCA, IconSource.ARASAAC) &&
            listOf("system/", "soca/", "arasaac/").any { item.imagePath.startsWith(it) }
        if (showLegacyImage || generic) {
            AacStoragePaths.resolveIconFile(context, item.imagePath, item.iconSource)
                ?.takeIf { it.isFile }?.let { return it }
        }
        val original = AacStarterContentV1.items().firstOrNull { it.id == item.id } ?: return null
        return AacStoragePaths.resolveIconFile(context, original.imagePath, original.iconSource)
            ?.takeIf { it.isFile }
    }

    /** Patient captured before opening picker; never attach a pending result to a different patient. */
    fun importPhoto(context: Context, patientId: String, itemId: String, uri: Uri): Boolean = try {
        val store = store(context)
        require(store.currentPatientId() == patientId)
        val item = AacEditorStorage.loadItems(context).firstOrNull { it.id == itemId }
        require(item != null && !item.locked)
        val extension = when (context.contentResolver.getType(uri)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("Unsupported image format")
        }
        store.backupLegacy(patientId, AacStoragePaths.getPatientProfileFile(context),
            AacStoragePaths.getAacItemsFile(context))
        context.contentResolver.openInputStream(uri)?.use { input ->
            store.importImage(patientId, itemId, extension, input, ::readableImage)
        } ?: error("Cannot open photo")
        true
    } catch (_: Exception) {
        false
    }

    fun clear(context: Context, patientId: String, itemId: String): Boolean = try {
        val store = store(context)
        require(store.currentPatientId() == patientId)
        val item = AacEditorStorage.loadItems(context).firstOrNull { it.id == itemId }
        require(item != null && !item.locked)
        store.clearBinding(patientId, itemId)
        true
    } catch (_: Exception) { false }

    private fun readableImage(file: File): Boolean {
        val bitmap = decodePreview(file) ?: return false
        bitmap.recycle()
        return true
    }

    // Display copies keep full-resolution originals immutable and bound bitmap memory on older tablets.
    private fun decodePreview(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun preview(original: File): File? {
        val dir = File(original.parentFile!!.parentFile, "previews")
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val target = File(dir, original.nameWithoutExtension + ".png")
        if (target.isFile && target.length() > 0) return target
        val bitmap = decodePreview(original) ?: return null
        var oriented = bitmap
        val pending = File.createTempFile("preview-", ".tmp", dir)
        try {
            val orientation = try {
                ExifInterface(original.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            } catch (_: Exception) { 1 }
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }
                    8 -> setRotate(-90f)
                }
            }
            if (!matrix.isIdentity) oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            pending.outputStream().use { output ->
                check(oriented.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.fd.sync()
            }
            if (target.exists() && !target.delete()) return null // Derived preview only.
            return if (pending.renameTo(target)) target else null
        } finally {
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
            pending.delete()
        }
    }
}
