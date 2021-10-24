package com.project.musicapp.utils

import org.json.JSONObject

data class Response(
    val title: String? = null,
    val artist: String? = null,
    val bitmapUri: String?  = null,
    val trackUri: String? = null,
    val duration: Long?  = null
)
/*
class Response(json: String) : JSONObject(json) {
    val title: String? = this.optString("title")
    val artist: String? = this.optString("artist")
    val bitmapUri: String? = this.optString("bitmapUri")
    val trackUri: String? = this.optString("trackUri")
    val duration: Long? = this.optLong("duration")
}*/
