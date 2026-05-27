package com.rehab2.aac

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

object AacPackImporter {

    private const val MAX_IMPORTED_FILE_COUNT = 2_000
    private const val MAX_SINGLE_ENTRY_BYTES = 25L * 1024L * 1024L
    private const val MAX_TOTAL_EXTRACTED_BYTES = 100L * 1024L * 1024L
    private const val COPY_BUFFER_SIZE = 16 * 1024

    sealed class Result {
        data class Success(
            val importedCount: Int,
            val skippedExistingCount: Int
        ) : Result()

        data class Rejected(val reason: String) : Result()
        data class Failure(val reason: String) : Result()
    }

    fun importNoOverwrite(context: Context, uri: Uri): Result {
        return try {
            when (val preflight = AacPackImportPreflight.inspect(context.contentResolver, uri)) {
                is AacPackImportPreflight.Result.Success -> importCheckedZip(context, uri)
                is AacPackImportPreflight.Result.Rejected -> Result.Rejected(preflight.reason)
                is AacPackImportPreflight.Result.Failure -> Result.Failure(preflight.reason)
            }
        } catch (error: IOException) {
            Result.Failure("ZIP datoteke ni mogoce uvoziti: ${error.message ?: "neznana napaka"}")
        } catch (error: SecurityException) {
            Result.Failure("Ni dovoljenja za branje izbrane datoteke.")
        } catch (error: Exception) {
            Result.Failure("Uvoz ni uspel: ${error.message ?: "neznana napaka"}")
        }
    }

    private fun importCheckedZip(context: Context, uri: Uri): Result {
        val externalFilesDir = context.getExternalFilesDir(null)
            ?: return Result.Failure("Ciljne mape aplikacije ni mogoce odpreti.")

        var importedCount = 0
        var skippedExistingCount = 0
        var totalExtractedBytes = 0L

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    val unsafeReason = AacPackImportPreflight.rejectionReasonForEntry(name)
                    if (unsafeReason != null) {
                        return Result.Rejected("Nevarna pot v ZIP: $unsafeReason")
                    }

                    val relativeDestination = AacPackImportPreflight.relativeDestinationPath(name)
                    if (!entry.isDirectory && relativeDestination != null) {
                        if (entry.size > MAX_SINGLE_ENTRY_BYTES) {
                            return Result.Rejected("Datoteka je prevelika za uvoz: $name")
                        }
                        val destination = File(externalFilesDir, relativeDestination)
                        if (!isInsideDirectory(destination, externalFilesDir)) {
                            return Result.Rejected("Nevarna ciljna pot: $name")
                        }
                        if (destination.exists()) {
                            skippedExistingCount += 1
                        } else {
                            if (importedCount >= MAX_IMPORTED_FILE_COUNT) {
                                return Result.Rejected("ZIP vsebuje prevec datotek za uvoz.")
                            }
                            val parent = destination.parentFile
                            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                                return Result.Failure("Ciljne mape ni bilo mogoce ustvariti: ${parent.name}")
                            }
                            if (parent == null || !parent.isDirectory) {
                                return Result.Failure("Ciljna mapa ni veljavna za: $name")
                            }
                            val tempFile = File.createTempFile("aac_import_", ".tmp", parent)
                            val copyResult = copyEntryWithLimits(
                                zip = zip,
                                tempFile = tempFile,
                                entryName = name,
                                totalExtractedBytes = totalExtractedBytes
                            )
                            when (copyResult) {
                                is CopyResult.Success -> totalExtractedBytes = copyResult.totalExtractedBytes
                                is CopyResult.Rejected -> {
                                    tempFile.delete()
                                    return Result.Rejected(copyResult.reason)
                                }
                                is CopyResult.Failure -> {
                                    tempFile.delete()
                                    return Result.Failure(copyResult.reason)
                                }
                            }
                            if (destination.exists()) {
                                tempFile.delete()
                                skippedExistingCount += 1
                            } else if (!tempFile.renameTo(destination)) {
                                tempFile.delete()
                                return Result.Failure("Datoteke ni bilo mogoce shraniti: $name")
                            } else {
                                importedCount += 1
                            }
                        }
                    }

                    zip.closeEntry()
                }
            }
        } ?: return Result.Failure("Datoteke ni mogoce odpreti.")

        return Result.Success(
            importedCount = importedCount,
            skippedExistingCount = skippedExistingCount
        )
    }

    private sealed class CopyResult {
        data class Success(val totalExtractedBytes: Long) : CopyResult()
        data class Rejected(val reason: String) : CopyResult()
        data class Failure(val reason: String) : CopyResult()
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
                        return CopyResult.Rejected("Datoteka je prevelika za uvoz: $entryName")
                    }
                    if (newTotalBytes > MAX_TOTAL_EXTRACTED_BYTES) {
                        return CopyResult.Rejected("ZIP paket je prevelik za uvoz.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            CopyResult.Success(newTotalBytes)
        } catch (error: IOException) {
            CopyResult.Failure("Datoteke ni bilo mogoce prebrati: ${error.message ?: "neznana napaka"}")
        }
    }

    private fun isInsideDirectory(file: File, directory: File): Boolean {
        val basePath = directory.canonicalFile.path
        val filePath = file.canonicalFile.path
        return filePath == basePath || filePath.startsWith(basePath + File.separator)
    }
}
