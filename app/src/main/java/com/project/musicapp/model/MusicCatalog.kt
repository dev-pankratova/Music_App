package com.project.musicapp.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MusicCatalog(
    val title: String? = null,
    val artist: String? = null,
    val bitmapUri: String?  = null,
    val trackUri: String? = null,
    val duration: Long?  = null
)