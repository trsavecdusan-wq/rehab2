package com.rehab2.patient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.rehab2.aac.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Private overlay on the 810 store, not a parallel patient catalog. */
class PatientIconPersonalizer(private val context: Context) {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(210, TimeUnit.SECONDS).build()
    @Volatile private var call: Call? = null
    fun cancel() { call?.cancel() }
    private fun directory(patient: String): File {
        require(UUID.fromString(patient).toString() == patient)
        return File(context.noBackupFilesDir, "personalization/$patient").apply { check(isDirectory || mkdirs()) }
    }
    fun reference(patient: String) = File(directory(patient), "reference.png")
    private fun checkPatient(patient: String) { check(AacPatientImages.store(context).currentPatientId() == patient) }

    /** Decode, orient and re-encode; original gallery file and EXIF are never uploaded. */
    fun importReference(patient: String, uri: Uri) {
        checkPatient(patient)
        val source = File.createTempFile("reference-", ".tmp", directory(patient))
        val pending = File.createTempFile("normalized-", ".png", directory(patient))
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 25 * 1024 * 1024) { "Fotografija je prevelika." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Fotografije ni mogoče odpreti.")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Neveljavna fotografija." }
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("Fotografije ni mogoče prebrati.")
            var oriented = bitmap
            try {
                val orientation = runCatching { android.media.ExifInterface(source.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                val matrix = android.graphics.Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                        5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                        7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f)
                    }
                }
                if (!matrix.isIdentity) oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                pending.outputStream().use { check(oriented.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { if (oriented !== bitmap) oriented.recycle(); bitmap.recycle() }
            checkPatient(patient)
            check(pending.renameTo(reference(patient))) { "Fotografije ni mogoče shraniti." }
        } finally { source.delete(); pending.delete() }
    }

    fun eligible(): List<AacItem> = AacEditorStorage.loadItems(context).filter {
        it.id in personIds && !it.locked && !it.userEdited && it.fixedTopRowPosition == null
    }
    private fun systemImage(itemId: String): File {
        // Always use the reviewed SYSTEM reference, never an existing patient overlay.
        val item = AacStarterContentV1.items().single { it.id == itemId }
        return AacStoragePaths.resolveIconFile(context, item.imagePath, item.iconSource)
            ?.takeIf { it.isFile } ?: error("Sistemska ikona ni dosegljiva.")
    }
    fun systemPreview(itemId: String): File = systemImage(itemId)
    fun configuration(): JSONObject {
        val file = File(context.noBackupFilesDir, "personalization-service.json")
        return if (file.isFile) JSONObject(file.readText()) else JSONObject()
    }
    fun configure(endpoint: String, token: String) {
        val uri = java.net.URI(endpoint.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(token.isNotBlank() && token.length <= 8192 && token.none { it == '\r' || it == '\n' })
        val file = File(context.noBackupFilesDir, "personalization-service.json")
        val pending = File.createTempFile("service-", ".tmp", context.noBackupFilesDir)
        try {
            pending.writeText(JSONObject().put("endpoint", endpoint.trim()).put("token", token.trim()).toString())
            check(pending.renameTo(file))
        } finally { pending.delete() }
    }
    fun generate(patient: String, itemId: String, cancelled: () -> Boolean = { false }): File {
        check(!cancelled()) { "Izdelava preklicana." }
        checkPatient(patient)
        val item = eligible().single { it.id == itemId }
        val reference = reference(patient); require(reference.isFile) { "Najprej izberi fotografijo." }
        val config = configuration()
        val endpoint = config.optString("endpoint"); val token = config.optString("token")
        require(endpoint.isNotBlank() && token.isNotBlank()) { "Storitev za izdelavo ikon še ni nastavljena." }
        val uri = java.net.URI(endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
        require(token.length <= 8192 && token.none { it == '\r' || it == '\n' })
        val requestId = UUID.randomUUID().toString()
        val metadata = JSONObject().put("requestId", requestId).put("itemId", item.id)
            .put("meaning", AacLocalizedTextResolver.resolveSpeakText(item, "sl"))
            .put("label", item.labelSl)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", metadata.toString())
            .addFormDataPart("reference", "reference.png", reference.asRequestBody("image/png".toMediaType()))
            .addFormDataPart("system", "system.png", systemImage(item.id).asRequestBody("image/png".toMediaType())).build()
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $token")
            .header("Accept", "image/png").post(body).build()
        val active = client.newCall(request); call = active
        try {
            if (cancelled()) active.cancel()
            active.execute().use { response ->
                check(!cancelled()) { "Izdelava preklicana." }
                require(response.isSuccessful) { "Izdelava ni uspela (HTTP ${response.code})." }
                require(response.header("X-Request-Id") == requestId && response.header("X-Item-Id") == item.id)
                require(response.header("Content-Type")?.substringBefore(';') == "image/png")
                val candidate = File.createTempFile("candidate-", ".png", directory(patient))
                try {
                    response.body?.byteStream()?.use { input ->
                        candidate.outputStream().use { output ->
                            val bytes = ByteArray(8192); var total = 0
                            while (true) {
                                val n = input.read(bytes); if (n < 0) break
                                total += n; require(total <= 8 * 1024 * 1024); output.write(bytes, 0, n)
                            }
                        }
                    } ?: error("Prazen odgovor.")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(candidate.path, bounds)
                    require(bounds.outMimeType == "image/png" && bounds.outWidth in 256..2048 && bounds.outHeight in 256..2048)
                    val bitmap = BitmapFactory.decodeFile(candidate.path) ?: error("Neveljavna slika.")
                    try {
                        require(bitmap.hasAlpha()); require(bitmap.width == bitmap.height)
                        var transparent = false; var nonempty = false
                        val row = IntArray(bitmap.width)
                        for (y in 0 until bitmap.height) {
                            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                            for (pixel in row) { val alpha = pixel ushr 24; if (alpha < 255) transparent = true; if (alpha > 0) nonempty = true }
                        }
                        require(transparent && nonempty)
                    } finally { bitmap.recycle() }
                    checkPatient(patient)
                    check(!cancelled()) { "Izdelava preklicana." }
                    return candidate
                } catch (error: Exception) { candidate.delete(); throw error }
            }
        } finally { if (call === active) call = null }
    }
    fun approve(patient: String, itemId: String, candidate: File) {
        checkPatient(patient)
        require(candidate.canonicalFile.parentFile == directory(patient).canonicalFile && candidate.name.startsWith("candidate-"))
        eligible().single { it.id == itemId }
        val store = AacPatientImages.store(context)
        store.backupLegacy(patient, AacStoragePaths.getPatientProfileFile(context), AacStoragePaths.getAacItemsFile(context))
        candidate.inputStream().use { input -> store.importImage(patient, itemId, "png", input) { file ->
            val bitmap = BitmapFactory.decodeFile(file.path)
            if (bitmap == null) false else { bitmap.recycle(); true }
        } }
        candidate.delete()
    }
    companion object {
        val personIds = setOf("tired", "walk", "rest", "sleepy", "need_rest", "sad", "happy", "afraid", "bad", "good",
            "feeling", "love_you", "rada_te_imam", "choose_myself", "show_you", "not_this", "not_here", "i_want",
            "cannot", "cannot_speak", "cannot_eat", "cannot_drink", "cannot_stand", "cannot_move", "cannot_understand",
            "pain", "head", "arm", "leg", "belly", "back", "chest", "throat", "chest_pain", "cannot_breathe",
            "stroke_info", "speech_difficulty", "i_understand", "give_me_time", "be_patient", "cold", "hot", "uncomfortable",
            "smoking", "music", "tv", "go_outside_with_wheelchair", "visit", "person_see", "need_help", "peace")
    }
}
