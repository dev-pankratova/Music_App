package com.project.musicapp.ui

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.project.musicapp.R
import com.project.musicapp.databinding.ActivityMainBinding
import com.project.musicapp.utils.Response
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private var model = mutableListOf<Response>()
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        val jsonString: String = applicationContext.assets.open("playlist.json").bufferedReader().use { it.readText() }
        val ja = JSONArray(jsonString)
        for (i in 0 until ja.length()) {
            val jsonTrack: JSONObject = ja.getJSONObject(i)
            val title = jsonTrack.getString("title")
            val artist = jsonTrack.getString("artist")
            val bitmapUri = jsonTrack.getString("bitmapUri")
            val trackUri = jsonTrack.getString("trackUri")
            val duration = jsonTrack.getInt("duration").toLong()
            model.add(Response(title, artist, bitmapUri, trackUri, duration))
        }
        setTitleFirstTrack(model[0].title)
        setImage(model[0].bitmapUri)
    }

    private fun setTitleFirstTrack(title: String?) {
        binding?.titleTxt?.text = title
    }

    private fun setImage(uriStr: String?) {
        binding?.trackPic?.let {
            Glide
                .with(this)
                .load(uriStr)
                //.placeholder(R.drawable.ic_baseline_music_note_24)
                .into(it)
        }
    }
}