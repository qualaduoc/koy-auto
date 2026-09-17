package com.koy.auto.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object KoYPlayerManager {

    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentStream = MutableStateFlow<StreamInfo?>(null)
    val currentStream: StateFlow<StreamInfo?> = _currentStream

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    // Callback cập nhật cho MediaSessionCompat của Android Auto
    var onSessionStateChange: ((title: String, artist: String, isPlaying: Boolean, durationMs: Long, positionMs: Long) -> Unit)? = null

    fun initialize(context: Context) {
        if (exoPlayer != null) return

        val appContext = context.applicationContext

        // 1. Tối ưu bộ giải mã phần cứng GPU (Hardware Acceleration)
        val renderersFactory = DefaultRenderersFactory(appContext).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }

        // 2. Tối ưu bộ đệm thông minh: nạp sẵn 15s-30s chống đứt quãng mạng 4G
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs
                30_000, // maxBufferMs
                1_500,  // bufferForPlaybackMs (phát cực nhanh trong 1.5s)
                3_000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 3. Cấu hình Audio Focus và đầu ra âm thanh loa ô tô
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        notifySessionUpdate()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                        if (playbackState == Player.STATE_READY) {
                            notifySessionUpdate()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        error.printStackTrace()
                        _isPlaying.value = false
                        _isBuffering.value = false
                    }
                })
            }
    }

    fun attachPlayerView(playerView: PlayerView) {
        mainHandler.post {
            exoPlayer?.let { player ->
                PlayerView.switchTargetView(player, null, playerView)
                playerView.player = player
            }
        }
    }

    fun detachPlayerView(playerView: PlayerView) {
        mainHandler.post {
            if (playerView.player == exoPlayer) {
                playerView.player = null
            }
        }
    }

    fun playDirectStream(
        url: String,
        title: String,
        artist: String,
        isHls: Boolean = false,
        thumbnailUrl: String? = null
    ) {
        mainHandler.post {
            val player = exoPlayer ?: return@post

            val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))
            if (isHls || url.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
            }

            val info = StreamInfo(
                videoId = "direct",
                title = title,
                author = artist,
                streamUrl = url,
                isHls = isHls,
                thumbnailUrl = thumbnailUrl
            )
            _currentStream.value = info

            player.setMediaItem(mediaItemBuilder.build())
            player.prepare()
            player.play()
            notifySessionUpdate()
        }
    }

    fun playYouTube(
        urlOrId: String,
        onStart: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        scope.launch {
            _isBuffering.value = true
            val result = YouTubeStreamExtractor.extractStream(urlOrId)
            _isBuffering.value = false

            if (result.isSuccess) {
                val streamInfo = result.getOrNull()
                if (streamInfo != null) {
                    _currentStream.value = streamInfo
                    playDirectStream(
                        url = streamInfo.streamUrl,
                        title = streamInfo.title,
                        artist = streamInfo.author,
                        isHls = streamInfo.isHls,
                        thumbnailUrl = streamInfo.thumbnailUrl
                    )
                    onStart?.invoke()
                } else {
                    onError?.invoke("Không lấy được dữ liệu luồng")
                }
            } else {
                onError?.invoke(result.exceptionOrNull()?.message ?: "Lỗi giải mã luồng YouTube")
            }
        }
    }

    fun playPredefined(mediaId: String) {
        when (mediaId) {
            "news" -> {
                // VOV Giao thông Hà Nội (91 MHz) trực tiếp
                playDirectStream(
                    url = "https://radiovovlive.vov.vn/vovgt-hn",
                    title = "📻 VOV Giao Thông (Trực tiếp)",
                    artist = "Đài Tiếng Nói Việt Nam (91.0 MHz)",
                    isHls = false
                )
            }
            "music" -> {
                // Nhạc lái xe Lo-fi thư giãn chất lượng cao
                playDirectStream(
                    url = "https://stream.zeno.fm/f3wvbbqmdg8uv",
                    title = "🎵 Nhạc Lái Xe Thư Giãn",
                    artist = "KoY Auto Lofi Chill",
                    isHls = false
                )
            }
            "podcast" -> {
                // Sách nói & Podcast kinh doanh kỹ năng sống
                playDirectStream(
                    url = "https://stream.zeno.fm/0r0xa792kwzuv",
                    title = "🎙️ Sách Nói & Kỹ Năng Lái Xe",
                    artist = "KoY Auto Podcast",
                    isHls = false
                )
            }
            else -> {
                // Mặc định: tiếp tục phát hoặc mở kênh lái xe
                if (exoPlayer?.playbackState == Player.STATE_READY) {
                    exoPlayer?.play()
                } else {
                    playPredefined("music")
                }
            }
        }
    }

    fun togglePlayPause() {
        mainHandler.post {
            exoPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                } else {
                    it.play()
                }
            }
        }
    }

    fun pause() {
        mainHandler.post { exoPlayer?.pause() }
    }

    fun resume() {
        mainHandler.post { exoPlayer?.play() }
    }

    fun seekForward(ms: Long = 10_000L) {
        mainHandler.post {
            exoPlayer?.let {
                val current = it.currentPosition
                val duration = it.duration
                val target = if (duration > 0) (current + ms).coerceAtMost(duration) else current + ms
                it.seekTo(target)
            }
        }
    }

    fun seekBack(ms: Long = 10_000L) {
        mainHandler.post {
            exoPlayer?.let {
                val current = it.currentPosition
                val target = (current - ms).coerceAtLeast(0L)
                it.seekTo(target)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
        }
    }


    private fun notifySessionUpdate() {
        val player = exoPlayer ?: return
        val stream = _currentStream.value
        val title = stream?.title ?: "KoY Auto"
        val artist = stream?.author ?: "Trình phát ô tô"
        val isPlaying = player.isPlaying
        val duration = if (player.duration > 0) player.duration else 0L
        val position = player.currentPosition

        onSessionStateChange?.invoke(title, artist, isPlaying, duration, position)
    }

    fun release() {
        mainHandler.post {
            exoPlayer?.release()
            exoPlayer = null
        }
    }
}
