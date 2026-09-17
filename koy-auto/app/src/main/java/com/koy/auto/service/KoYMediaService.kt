package com.koy.auto.service

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.koy.auto.player.KoYPlayerManager

class KoYMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {
        super.onCreate()

        KoYPlayerManager.initialize(this)

        mediaSession = MediaSessionCompat(this, "KoYMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)

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
                    KoYPlayerManager.resume()
                }

                override fun onPause() {
                    KoYPlayerManager.pause()
                }

                override fun onSkipToNext() {
                    KoYPlayerManager.seekForward(10000L)
                }

                override fun onSkipToPrevious() {
                    KoYPlayerManager.seekBack(10000L)
                }

                override fun onSeekTo(pos: Long) {
                    KoYPlayerManager.seekTo(pos)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val id = mediaId ?: "music"
                    KoYPlayerManager.playPredefined(id)
                }
            })

            isActive = true
        }

        // Lắng nghe cập nhật trạng thái từ KoYPlayerManager để đẩy lên Android Auto
        KoYPlayerManager.onSessionStateChange = { title, artist, isPlaying, durationMs, positionMs ->
            val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, positionMs, 1.0f)
            mediaSession.setPlaybackState(stateBuilder.build())

            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "KoY Auto")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .build()
            mediaSession.setMetadata(metadata)
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
        KoYPlayerManager.onSessionStateChange = null
        mediaSession.release()
        super.onDestroy()
    }

    companion object {
        private const val MEDIA_ROOT_ID = "koy_media_root"
    }
}
