package com.rehab2.music

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileOutputStream

class LocalMusicImporter(private val context: Context) {
    data class ImportResult(
        val totalFound: Int,
        val importedTracks: List<String>,
        val skippedTracks: List<String>,
        val failedTracks: List<String>
    )

    private data class SourceAudioFile(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String
    )

    private val supportedExtensions = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac")

    fun importFromTree(treeUri: Uri, onProgress: (String) -> Unit): ImportResult {
        val store = LocalMusicStore(context)
        onProgress("Priprava uvoza")

        val foundFiles = mutableListOf<SourceAudioFile>()
        try {
            onProgress("Pregledujem mapo...")
            collectAudioFiles(treeUri, foundFiles)
        } catch (_: Exception) {
            onProgress("Napaka pri branju datoteke")
        }

        onProgress("Najdene datoteke: ${foundFiles.size}")
        onProgress("Primerjam z obstoječo knjižnico...")

        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val newFiles = foundFiles.filterNot { store.hasTrack(it.displayName, it.sizeBytes) }

        newFiles.forEachIndexed { index, source ->
            try {
                onProgress("Kopiram datoteko ${index + 1} od ${newFiles.size}")
                val targetFile = store.createUniqueLibraryFile(source.displayName)
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Napaka pri branju datoteke")

                val track = store.createTrack(
                    displayName = source.displayName,
                    localFileName = targetFile.name,
                    localPath = targetFile.absolutePath,
                    sourceDisplayName = source.displayName,
                    sizeBytes = source.sizeBytes,
                    mimeType = source.mimeType
                )
                store.addImportedTrack(track)
                imported += source.displayName
            } catch (_: Exception) {
                failed += source.displayName
            }
        }

        foundFiles.filter { store.hasTrack(it.displayName, it.sizeBytes) && it.displayName !in imported }
            .forEach { skipped += it.displayName }

        onProgress("Uvoz končan")
        return ImportResult(
            totalFound = foundFiles.size,
            importedTracks = imported,
            skippedTracks = skipped,
            failedTracks = failed
        )
    }

    private fun collectAudioFiles(treeUri: Uri, results: MutableList<SourceAudioFile>) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        scanDocumentRecursively(rootDocumentUri, results)
    }

    private fun scanDocumentRecursively(documentUri: Uri, results: MutableList<SourceAudioFile>) {
        val documentId = DocumentsContract.getDocumentId(documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(documentUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val childDocumentId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mimeType = cursor.getString(mimeIndex) ?: ""
                val sizeBytes = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(documentUri, childDocumentId)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanDocumentRecursively(childUri, results)
                } else if (isSupportedAudio(displayName, mimeType)) {
                    results += SourceAudioFile(
                        uri = childUri,
                        displayName = displayName,
                        sizeBytes = sizeBytes,
                        mimeType = mimeType
                    )
                }
            }
        }
    }

    private fun isSupportedAudio(displayName: String, mimeType: String): Boolean {
        if (mimeType.startsWith("audio/")) {
            return true
        }
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return extension in supportedExtensions
    }
}
