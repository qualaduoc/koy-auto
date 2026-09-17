package com.koy.auto.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import com.koy.auto.player.KoYPlayerManager

/**
 * A single process-wide media session shared by the phone UI, lock screen,
 * notification and Android Auto. Keeping one session avoids competing media
 * controls for the same ExoPlayer instance.
 */
object KoYMediaSessionManager {

    private lateinit var mediaSession: MediaSessionCompat

    val sessionToken: MediaSessionCompat.Token
        get() = mediaSession.sessionToken

    fun initialize(context: Context) {
        if (::mediaSession.isInitialized) return

        KoYPlayerManager.initialize(context)
        mediaSession = MediaSessionCompat(context.applicationContext, "KoYMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = KoYPlayerManager.resume()
                override fun onPause() = KoYPlayerManager.pause()
                override fun onStop() = KoYPlayerManager.stop()
                override fun onSkipToNext() = KoYPlayerManager.seekForward()
                override fun onSkipToPrevious() = KoYPlayerManager.seekBack()
                override fun onSeekTo(pos: Long) = KoYPlayerManager.seekTo(pos)

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    KoYPlayerManager.playPredefined(mediaId ?: "music")
                }
            })
            setPlaybackState(buildPlaybackState(false, 0L))
            isActive = true
        }

        KoYPlayerManager.onSessionStateChange = { title, artist, isPlaying, durationMs, positionMs ->
            mediaSession.setPlaybackState(buildPlaybackState(isPlaying, positionMs))
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "KoY Auto")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                    .build()
            )
        }
    }

    fun handleMediaButtonIntent(intent: Intent?) {
        if (::mediaSession.isInitialized) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }
    }

    private fun buildPlaybackState(isPlaying: Boolean, positionMs: Long): PlaybackStateCompat {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            )
            .setState(state, positionMs, 1.0f)
            .build()
    }
}
