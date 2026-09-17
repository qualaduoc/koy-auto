package com.koy.auto.service

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat

class KoYMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "KoYMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)

            setPlaybackState(stateBuilder.build())

            val mediaButtonIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).apply {
                setClass(this@KoYMediaService, androidx.media.session.MediaButtonReceiver::class.java)
            }
            val pendingMediaButton = android.app.PendingIntent.getBroadcast(
                this@KoYMediaService,
                0,
                mediaButtonIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            setMediaButtonReceiver(pendingMediaButton)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    val intent = android.content.Intent(com.koy.auto.util.Constants.ACTION_TOGGLE_PLAYBACK).apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }

                override fun onPause() {
                    val intent = android.content.Intent(com.koy.auto.util.Constants.ACTION_TOGGLE_PLAYBACK).apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val targetUrl = when (mediaId) {
                        "music" -> com.koy.auto.util.Constants.URL_DRIVER_MUSIC
                        "news" -> com.koy.auto.util.Constants.URL_VOV_NEWS
                        "podcast" -> com.koy.auto.util.Constants.URL_PODCAST
                        else -> com.koy.auto.util.Constants.YOUTUBE_URL_MOBILE
                    }

                    // Báo cho Android Auto chuyển ngay sang trạng thái đang phát (PLAYING) để không bị lỗi timeout
                    val playingState = PlaybackStateCompat.Builder()
                        .setActions(
                            PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        )
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                        .build()
                    mediaSession.setPlaybackState(playingState)

                    val intent = android.content.Intent(this@KoYMediaService, CarProjectionService::class.java).apply {
                        action = com.koy.auto.util.Constants.ACTION_LOAD_URL
                        putExtra(com.koy.auto.util.Constants.EXTRA_URL, targetUrl)
                    }
                    startService(intent)
                }
            })

            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        // Also ensure projection service starts
        CarProjectionService.start(this)
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        androidx.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val mediaItems = mutableListOf<MediaBrowserCompat.MediaItem>()

        fun createItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
            val desc = android.support.v4.media.MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build()
            return MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        }

        mediaItems.add(createItem("music", "🎵 Nhạc Lái Xe", "YouTube Lo-fi không lời"))
        mediaItems.add(createItem("news", "📻 VOV Giao Thông", "Tin tức giao thông trực tiếp"))
        mediaItems.add(createItem("podcast", "🎙️ Sách Nói & Podcast", "Kinh doanh, kỹ năng sống"))
        mediaItems.add(createItem("youtube", "▶️ Trình phát YouTube", "Phát luồng từ điện thoại"))

        result.sendResult(mediaItems)
    }

    override fun onDestroy() {
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val MEDIA_ROOT_ID = "koy_media_root"
    }
}
