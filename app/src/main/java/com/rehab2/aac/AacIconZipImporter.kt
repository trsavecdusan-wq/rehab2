package com.rehab2.aac

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipInputStream

object AacIconZipImporter {
    private const val MAX_IMPORTED_FILE_COUNT = 2_000
    private const val MAX_SINGLE_ENTRY_BYTES = 25L * 1024L * 1024L
    private const val MAX_TOTAL_EXTRACTED_BYTES = 100L * 1024L * 1024L
    private const val COPY_BUFFER_SIZE = 16 * 1024

    sealed class Result {
        data class Success(
            val importedCount: Int,
            val skippedExistingCount: Int,
            val rejectedUnsafeCount: Int,
            val ignoredUnsupportedCount: Int,
            val importedSocaCount: Int,
            val importedCustomCount: Int,
            val importedArasaacCount: Int
        ) : Result()

        data class Failure(val reason: String) : Result()
    }

    fun importNoOverwrite(context: Context, uri: Uri): Result {
        return try {
            importZip(context, uri)
        } catch (error: IOException) {
            Result.Failure("ZIP datoteke ni mogoce uvoziti: ${error.message ?: "neznana napaka"}")
        } catch (error: SecurityException) {
            Result.Failure("Ni dovoljenja za branje izbrane datoteke.")
        } catch (error: Exception) {
            Result.Failure("Uvoz ikon ni uspel: ${error.message ?: "neznana napaka"}")
        }
    }

    private fun importZip(context: Context, uri: Uri): Result {
        val socaDir = AacStoragePaths.getIconsSocaDir(context)
            ?: return Result.Failure("Mape SOCA ikon ni mogoce odpreti.")
        val customDir = AacStoragePaths.getIconsCustomDir(context)
            ?: return Result.Failure("Mape custom ikon ni mogoce odpreti.")
        val arasaacDir = AacStoragePaths.getIconsArasaacDir(context)
            ?: return Result.Failure("Mape ARASAAC ikon ni mogoce odpreti.")

        val targetDirs = listOf(socaDir, customDir, arasaacDir)
        targetDirs.forEach { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                return Result.Failure("Ciljne mape ni bilo mogoce ustvariti: ${dir.name}")
            }
            if (!dir.isDirectory) {
                return Result.Failure("Ciljna pot ni mapa: ${dir.name}")
            }
        }

        var importedCount = 0
        var skippedExistingCount = 0
        var rejectedUnsafeCount = 0
        var ignoredUnsupportedCount = 0
        var importedSocaCount = 0
        var importedCustomCount = 0
        var importedArasaacCount = 0
        var totalExtractedBytes = 0L

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                zipLoop@ while (true) {
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name
                    val unsafeReason = rejectionReasonForEntry(entryName)
                    if (unsafeReason != null) {
                        rejectedUnsafeCount += 1
                        zip.closeEntry()
                        continue
                    }

                    val target = targetForEntry(entryName, socaDir, customDir, arasaacDir)
                    if (entry.isDirectory || target == null) {
                        if (!entry.isDirectory) {
                            ignoredUnsupportedCount += 1
                        }
                        zip.closeEntry()
                        continue
                    }

                    if (entry.size > MAX_SINGLE_ENTRY_BYTES) {
                        ignoredUnsupportedCount += 1
                        zip.closeEntry()
                        continue
                    }

                    if (!isInsideDirectory(target.file, target.dir)) {
                        rejectedUnsafeCount += 1
                        zip.closeEntry()
                        continue
                    }

                    if (target.file.exists()) {
                        skippedExistingCount += 1
                        zip.closeEntry()
                        continue
                    }

                    if (importedCount >= MAX_IMPORTED_FILE_COUNT) {
                        return Result.Failure("ZIP vsebuje prevec ikon za uvoz.")
                    }

                    val tempFile = File.createTempFile("aac_icon_import_", ".tmp", target.dir)
                    val copyResult = copyEntryWithLimits(
                        zip = zip,
                        tempFile = tempFile,
                        entryName = entryName,
                        totalExtractedBytes = totalExtractedBytes
                    )
                    when (copyResult) {
                        is CopyResult.Success -> totalExtractedBytes = copyResult.totalExtractedBytes
                        is CopyResult.Rejected -> {
                            tempFile.delete()
                            ignoredUnsupportedCount += 1
                            zip.closeEntry()
                            continue@zipLoop
                        }
                        is CopyResult.Failure -> {
                            tempFile.delete()
                            return Result.Failure(copyResult.reason)
                        }
                    }

                    if (target.file.exists()) {
                        tempFile.delete()
                        skippedExistingCount += 1
                    } else if (!tempFile.renameTo(target.file)) {
                        tempFile.delete()
                        return Result.Failure("Ikone ni bilo mogoce shraniti: $entryName")
                    } else {
                        importedCount += 1
                        when (target.kind) {
                            IconFolder.SOCA -> importedSocaCount += 1
                            IconFolder.CUSTOM -> importedCustomCount += 1
                            IconFolder.ARASAAC -> importedArasaacCount += 1
                        }
                    }

                    zip.closeEntry()
                }
            }
        } ?: return Result.Failure("Datoteke ni mogoce odpreti.")

        return Result.Success(
            importedCount = importedCount,
            skippedExistingCount = skippedExistingCount,
            rejectedUnsafeCount = rejectedUnsafeCount,
            ignoredUnsupportedCount = ignoredUnsupportedCount,
            importedSocaCount = importedSocaCount,
            importedCustomCount = importedCustomCount,
            importedArasaacCount = importedArasaacCount
        )
    }

    private fun rejectionReasonForEntry(name: String?): String? {
        if (name.isNullOrEmpty()) return "prazno ime vnosa"
        if (name.startsWith("/") || name.startsWith("\\")) return name
        if (name.length >= 2 && name[1] == ':' && name[0].isLetter()) return name
        if (name.contains('\\')) return name
        if (name.split('/').any { it == ".." }) return name
        return null
    }

    private fun targetForEntry(
        entryName: String,
        socaDir: File,
        customDir: File,
        arasaacDir: File
    ): TargetFile? {
        val normalized = entryName.trim().replace('\\', '/')
        val lowerName = normalized.lowercase(Locale.ROOT)
        if (!lowerName.endsWith(".png")) {
            return null
        }
        return when {
            normalized.startsWith("soca/") ->
                targetForDirectChild(normalized, "soca/", socaDir, IconFolder.SOCA)
            normalized.startsWith("custom/") ->
                targetForDirectChild(normalized, "custom/", customDir, IconFolder.CUSTOM)
            normalized.startsWith("arasaac/") ->
                targetForDirectChild(normalized, "arasaac/", arasaacDir, IconFolder.ARASAAC)
            else -> null
        }
    }

    private fun targetForDirectChild(
        entryName: String,
        prefix: String,
        dir: File,
        kind: IconFolder
    ): TargetFile? {
        val fileName = entryName.removePrefix(prefix)
        if (fileName.isEmpty() || fileName.contains('/')) {
            return null
        }
        return TargetFile(file = File(dir, fileName), dir = dir, kind = kind)
    }

    private fun copyEntryWithLimits(
        zip: ZipInputStream,
        tempFile: File,
        entryName: String,
        totalExtractedBytes: Long
    ): CopyResult {
        var entryBytes = 0L
        var newTotalBytes = totalExtractedBytes
        val buffer = ByteArray(COPY_BUFFER_SIZE)

        return try {
            tempFile.outputStream().buffered().use { output ->
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    entryBytes += read.toLong()
                    newTotalBytes += read.toLong()
                    if (entryBytes > MAX_SINGLE_ENTRY_BYTES) {
                        return CopyResult.Rejected("Ikona je prevelika za uvoz: $entryName")
                    }
                    if (newTotalBytes > MAX_TOTAL_EXTRACTED_BYTES) {
                        return CopyResult.Rejected("ZIP z ikonami je prevelik za uvoz.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            CopyResult.Success(newTotalBytes)
        } catch (error: IOException) {
            CopyResult.Failure("Ikone ni bilo mogoce prebrati: ${error.message ?: "neznana napaka"}")
        }
    }

    private fun isInsideDirectory(file: File, directory: File): Boolean {
        val basePath = directory.canonicalFile.path
        val filePath = file.canonicalFile.path
        return filePath == basePath || filePath.startsWith(basePath + File.separator)
    }

    private data class TargetFile(
        val file: File,
        val dir: File,
        val kind: IconFolder
    )

    private enum class IconFolder {
        SOCA,
        CUSTOM,
        ARASAAC
    }

    private sealed class CopyResult {
        data class Success(val totalExtractedBytes: Long) : CopyResult()
        data class Rejected(val reason: String) : CopyResult()
        data class Failure(val reason: String) : CopyResult()
    }
}
