package com.koy.auto.service

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat

class KoYMediaService : MediaBrowserServiceCompat() {

    override fun onCreate() {
        super.onCreate()
        KoYMediaSessionManager.initialize(this)
        sessionToken = KoYMediaSessionManager.sessionToken

        // Also ensure projection service starts
        CarProjectionService.start(this)
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        KoYMediaSessionManager.handleMediaButtonIntent(intent)
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

        mediaItems.add(createItem("music", "🎵 VOV3 Âm Nhạc", "Kênh âm nhạc trực tiếp"))
        mediaItems.add(createItem("news", "📻 VOV Giao Thông", "Tin tức giao thông trực tiếp"))
        mediaItems.add(createItem("podcast", "🎙️ VOV2", "Văn hóa và đời sống xã hội"))
        result.sendResult(mediaItems)
    }

    companion object {
        private const val MEDIA_ROOT_ID = "koy_media_root"
    }
}
