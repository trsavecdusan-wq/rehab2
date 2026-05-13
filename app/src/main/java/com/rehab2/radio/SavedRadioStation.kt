package com.rehab2.radio

data class SavedRadioStation(
    val stationUuid: String,
    val name: String,
    val buttonLabel: String = "",
    val streamUrl: String,
    val country: String,
    val genre: String,
    val faviconUrl: String,
    val codec: String,
    val bitrate: Int,
    val visible: Boolean,
    val page: Int,
    val position: Int
)
