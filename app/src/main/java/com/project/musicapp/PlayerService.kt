package com.project.musicapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.browse.MediaBrowser.MediaItem.FLAG_PLAYABLE as FLAG_PL
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.project.musicapp.repository.MusicRepository
import com.project.musicapp.ui.MainActivity
import java.io.File

class PlayerService : MediaBrowserServiceCompat() {

    private val stateBuilder = PlaybackStateCompat.Builder().setActions(
        PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
    )

    private val metadataBuilder = MediaMetadataCompat.Builder()
    private var _catalog: MusicRepository? = null
    private val musicCatalog: MusicRepository by lazy { getMusiccCatalog() }

    private fun getMusiccCatalog(): MusicRepository {
        return if (_catalog == null) {
            val catalog = MusicRepository(this.applicationContext)
            _catalog = catalog
            catalog
        } else {
            requireNotNull(_catalog)
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusRequested = false
    private var currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private var playOnFocusGain = false
    private var exoPlayer: SimpleExoPlayer? = null
    private var extractorsFactory: ExtractorsFactory? = null
    private var dataSourceFactory: com.google.android.exoplayer2.upstream.DataSource.Factory? = null

    @SuppressLint("WrongConstant")
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Player controls",
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(notificationChannel)

            val audioAtributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setAudioAttributes(audioAtributes)
                .build()
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaSession = MediaSessionCompat(this, "PlayerService")
        mediaSession?.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                    or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        mediaSession?.setCallback(mediaSessionCallback)

        val activityIntent = Intent(applicationContext, MainActivity::class.java)
        mediaSession?.setSessionActivity(
            PendingIntent.getActivity(
                applicationContext, 0, activityIntent, 0
            )
        )

        val mediaButtonIntent = Intent(
            Intent.ACTION_MEDIA_BUTTON, null, applicationContext,
            MediaButtonReceiver::class.java
        )
        mediaSession?.setMediaButtonReceiver(
            PendingIntent.getBroadcast(applicationContext, 0, mediaButtonIntent, 0)
        )

        // Build a HttpDataSource.Factory with cross-protocol redirects enabled.
        val httpDataSourceFactory2: HttpDataSource.Factory =
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val cache = SimpleCache(
            File(this.cacheDir.absolutePath + "/exoplayer"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
        )

        dataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory2)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE or CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        exoPlayer?.addListener(exoPlayerListener)

        extractorsFactory = DefaultExtractorsFactory()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        exoPlayer?.release()
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (SERVICE_INTERFACE == intent?.action) {
            return super.onBind(intent)
        }
        return MediaServiceBinder()
    }

    inner class MediaServiceBinder : Binder() {
        fun getMediaSessionToken() = mediaSession?.sessionToken
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot("Root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val data = ArrayList<MediaBrowserCompat.MediaItem>(musicCatalog.countTracks)
        val descriptionBuilder = MediaDescriptionCompat.Builder()

        for ((i, track) in musicCatalog.getTrackCatalog().withIndex()) {
            Log.i("TAG", "track = ${track.title}")
            //val track = musicCatalog.getTrackByIndex(i)
            val description = descriptionBuilder
                .setDescription(track.artist)
                .setTitle(track.title)
                .setSubtitle(track.artist)
                .setIconUri(Uri.parse(track.bitmapUri))
                .setMediaId(i.toString())
                .build()
            data.add(MediaBrowserCompat.MediaItem(description, FLAG_PL))
        }
        result.sendResult(data)
    }

    private val exoPlayerListener = object : Player.EventListener {

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            super.onPlaybackParametersChanged(playbackParameters)
        }

        override fun onTracksChanged(
            trackGroups: TrackGroupArray,
            trackSelections: TrackSelectionArray
        ) {}

        override fun onPlayerError(error: PlaybackException) {}

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                mediaSessionCallback.onScipToNext()
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {}

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {}

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {}
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> currentAudioFocusState = AUDIO_FOCUSED
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            currentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
            playOnFocusGain = exoPlayer != null && exoPlayer?.playWhenReady!!
        }
            AudioManager.AUDIOFOCUS_LOSS -> currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }

        if (exoPlayer != null) configurePlayerState()
    }

    private fun configurePlayerState() {
        if (currentAudioFocusState == AUDIO_NO_FOCUS_NO_DUCK) {
            playerPause()
        } else {
            registerReceiver(
                becomingNoiseReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
            if (currentAudioFocusState == AUDIO_NO_FOCUS_CAN_DUCK) {
                exoPlayer?.volume = VOLUME_DUCK
            } else {
                exoPlayer?.volume = VOLUME_NORMAL
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (playOnFocusGain) {
                exoPlayer?.playWhenReady = true
                playOnFocusGain = false
            }
        }
    }

    private fun playerPause() {
        exoPlayer?.let {
            it.playWhenReady = false
            try {
                unregisterReceiver(becomingNoiseReceiver)
            } catch (e: IllegalArgumentException) {
                e.stackTrace
            }
        }
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        private var currentUri: Uri? = null
        private var currentState = PlaybackStateCompat.STATE_STOPPED

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            playTrack(musicCatalog.getTrackByIndex(Integer.parseInt(mediaId!!)))
        }

        private fun playTrack(trackByIndex: MusicRepository.MusicCatalog) {

        }

    }

    companion object {

        private const val NOTIFICATION_ID = 33
        private const val NOTIFICATION_CHANNEL_ID = "media_channel"
        private const val AUDIO_NO_FOCUS_NO_DUCK = 0
        private const val AUDIO_NO_FOCUS_CAN_DUCK = 1
        private const val AUDIO_FOCUSED = 2
        private const val VOLUME_DUCK = 0.2F
        private const val VOLUME_NORMAL = 1.0F
        private const val CACHE_SIZE = 1024 * 1024 * 100L //100Mb
    }
}