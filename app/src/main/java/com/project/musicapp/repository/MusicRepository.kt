package com.project.musicapp.repository

import android.content.Context
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.project.musicapp.model.MusicCatalog
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MusicRepository(context: Context) {

    init {
        setCatalogFromJson(context)
    }

    val bitmaps = HashMap<String, Bitmap>(5)

    private var _catalog: List<MusicCatalog>? = null
    private val catalog: List<MusicCatalog> get() = requireNotNull(_catalog)

    private fun setCatalogFromJson(context: Context) {
        val moshi = Moshi.Builder()
            .build()

        val arrayType = Types.newParameterizedType(List::class.java, MusicCatalog::class.java)
        val adapter: JsonAdapter<List<MusicCatalog>> = moshi.adapter((arrayType))

        val file = "playlist.json"
        val musicJson = context.assets.open(file).bufferedReader().use { it.readText() }
        GlobalScope.launch(Dispatchers.Default) {
            try {
                _catalog?.forEach {
                    val bitmap =
                        Glide.with(context).asBitmap().load(it.bitmapUri).submit(200, 200).get()
                    bitmaps[it.bitmapUri] = bitmap
                }
            } catch (e: Exception) {
            }
        }
    }

    val maxTrackIndex = catalog.size - 1
    var currentTrackIndex = 0
    val countTracks = catalog.size

    var currentTrack = catalog[0]
        get() = catalog[currentTrackIndex]
        private set

    fun next(): MusicCatalog {
        if (currentTrackIndex == maxTrackIndex) {
            currentTrackIndex = 0
        } else {
            currentTrackIndex++
        }
        return  currentTrack
    }

    fun previous(): MusicCatalog {
        if (currentTrackIndex == 0) {
            currentTrackIndex = maxTrackIndex
        } else {
            currentTrackIndex--
        }
        return  currentTrack
    }

    fun getTrackByIndex(index: Int) = catalog[index]
    fun getTrackCatalog() = catalog
}