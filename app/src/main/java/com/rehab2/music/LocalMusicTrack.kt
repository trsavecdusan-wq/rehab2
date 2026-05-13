package com.rehab2.music

data class LocalMusicTrack(
    val id: String,
    val displayName: String,
    val localFileName: String,
    val localPath: String,
    val sourceDisplayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val importedAt: Long
)
