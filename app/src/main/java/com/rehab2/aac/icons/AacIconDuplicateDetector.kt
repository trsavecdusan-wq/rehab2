package com.rehab2.aac.icons

import java.io.File
import java.util.Locale

data class RegisteredIcon(val itemId: String, val meaningId: String, val fingerprint: AacIconFingerprint, val scope: String = "SYSTEM")
data class IconDuplicateResult(val status: IconImageStatus, val itemIds: List<String> = emptyList(), val warning: String? = null, val explicitReviewRequired: Boolean = false)
enum class SharedImageAssessment { INTENTIONAL, SUSPICIOUS, WRONG }
data class SharedIconGroup(val canonicalPaths: List<String>, val itemIds: List<String>, val meanings: List<String>, val assessment: SharedImageAssessment, val scope: String = "SYSTEM") {
    val warning: String? get() = if (assessment == SharedImageAssessment.SUSPICIOUS) "SUSPICIOUS_SHARED_IMAGE" else null
}

/** Snapshot registry of all referenced icons. Same bytes are independent from same meaning. */
class AacIconDuplicateDetector(private val registry: List<RegisteredIcon>) {
    fun detect(candidate: AacIconFingerprint, scope: String = "SYSTEM"): IconDuplicateResult {
        if (candidate.status == IconImageStatus.MISSING_IMAGE || candidate.status == IconImageStatus.INVALID_IMAGE) return IconDuplicateResult(candidate.status)
        val ids = registry.filter { it.scope == scope && it.fingerprint.sha256 != null && it.fingerprint.sha256 == candidate.sha256 }.map { it.itemId }.distinct().sorted()
        return if (ids.isEmpty()) IconDuplicateResult(IconImageStatus.UNIQUE_IMAGE)
        else IconDuplicateResult(IconImageStatus.EXACT_IMAGE_DUPLICATE, ids, "EXACT IMAGE ALREADY EXISTS", true)
    }

    fun sharedImages(): List<SharedIconGroup> = registry.filter { it.fingerprint.sha256 != null }.groupBy { it.scope to it.fingerprint.sha256 }.values
        .filter { rows -> rows.map { it.itemId }.distinct().size > 1 }.map { rows ->
            val meanings = rows.map { it.meaningId.trim().lowercase(Locale.ROOT) }.distinct().sorted()
            SharedIconGroup(rows.map { it.fingerprint.canonicalPath }.distinct().sorted(), rows.map { it.itemId }.distinct().sorted(), meanings,
                if (meanings.size == 1 && meanings.single().isNotEmpty()) SharedImageAssessment.INTENTIONAL else SharedImageAssessment.SUSPICIOUS, rows.first().scope)
        }.sortedBy { it.itemIds.first() }

    /** Validates only: no copy, overwrite, approval, or live deployment is performed. */
    fun validateImport(candidate: AacIconFingerprint, target: File, systemDirectory: File, decode: (File) -> IconImageInfo?, scope: String = "SYSTEM"): IconImportResult {
        if (candidate.status in setOf(IconImageStatus.INVALID_IMAGE, IconImageStatus.MISSING_IMAGE)) return IconImportResult(candidate.status.name)
        val safe = runCatching { target.canonicalFile.parentFile == systemDirectory.canonicalFile && target.name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*\\.(png|jpg|jpeg|webp)", RegexOption.IGNORE_CASE)) }.getOrDefault(false)
        if (!safe) return IconImportResult("INVALID_TARGET")
        val expected = when (target.extension.lowercase(Locale.ROOT)) { "png" -> "PNG"; "jpg", "jpeg" -> "JPEG"; else -> "WEBP" }
        if (candidate.image?.format != expected) return IconImportResult("FORMAT_MISMATCH")
        if (target.exists()) {
            val existing = AacIconFingerprint.read(target, decode)
            if (existing.sha256 == null || existing.sha256 != candidate.sha256) return IconImportResult("FILENAME_COLLISION")
        }
        val duplicate = detect(candidate, scope)
        return IconImportResult(if (duplicate.explicitReviewRequired || target.exists()) "EXPLICIT_REVIEW_REQUIRED" else "VALID_FOR_REVIEW", duplicate, candidate.image?.alpha)
    }
}

data class IconImportResult(val status: String, val duplicate: IconDuplicateResult? = null, val alpha: Boolean? = null)

/** Explicit, versioned replacements only. Protected or unknown ownership fails closed. */
object AacSystemIconMigration {
    fun approvedPath(current: String, iconSource: String, source: String = "", oldPath: String, newPath: String,
                     approved: Boolean, assetPass: Boolean): String {
        val owners = listOf(iconSource, source).map { it.trim().uppercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        if (owners.isEmpty() || owners.any { it != "SYSTEM" } || !approved || !assetPass || current != oldPath) return current
        val pattern = Regex("system/[A-Za-z0-9][A-Za-z0-9_-]*_v[2-9][0-9]*\\.(png|jpg|jpeg|webp)")
        if (!current.startsWith("system/") || newPath == current || !pattern.matches(newPath)) return current
        return newPath
    }
}
