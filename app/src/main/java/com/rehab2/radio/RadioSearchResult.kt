package com.rehab2.radio

data class RadioSearchResult(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val country: String,
    val tags: String,
    val faviconUrl: String?,
    val codec: String?,
    val bitrate: Int?
)