package com.koy.auto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.koy.auto.MainActivity
import com.koy.auto.R
import com.koy.auto.presentation.CarDisplayManager
import com.koy.auto.util.Constants

class CarProjectionService : Service() {

    private val binder = LocalBinder()
    lateinit var carDisplayManager: CarDisplayManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): CarProjectionService = this@CarProjectionService
    }

    override fun onCreate() {
        super.onCreate()
        carDisplayManager = CarDisplayManager(this)
        carDisplayManager.startListening()

        // ExoPlayer owns audio focus and its network wake lock. The service only
        // keeps playback foreground-visible, avoiding a permanent 8-hour lock.
        KoYMediaSessionManager.initialize(this)

        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                Constants.ACTION_STOP_PROJECTION -> {
                    com.koy.auto.player.KoYPlayerManager.stop()
                    stopForeground(true)
                    stopSelf()
                    return START_NOT_STICKY
                }
                Constants.ACTION_TOGGLE_PLAYBACK -> {
                    com.koy.auto.player.KoYPlayerManager.togglePlayPause()
                }
                Constants.ACTION_LOAD_URL -> {
                    val url = it.getStringExtra(Constants.EXTRA_URL)
                    if (!url.isNullOrEmpty()) {
                        carDisplayManager.loadUrlOnCar(url)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = "koy_auto_projection_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "KoY-Auto Projection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì phát âm thanh nền và kết nối màn hình phụ"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = Intent(this, CarProjectionService::class.java).apply {
            action = Constants.ACTION_TOGGLE_PLAYBACK
        }
        val pendingPlayPause = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, CarProjectionService::class.java).apply {
            action = Constants.ACTION_STOP_PROJECTION
        }
        val pendingStop = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("KoY-Auto đang phát ngầm")
            .setContentText("Điều khiển âm thanh trên điện thoại hoặc ô tô")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_refresh, "Phát / Dừng", pendingPlayPause)
            .addAction(R.drawable.ic_back, "Tắt", pendingStop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(KoYMediaSessionManager.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        carDisplayManager.stopListening()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, CarProjectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CarProjectionService::class.java).apply {
                action = Constants.ACTION_STOP_PROJECTION
            }
            context.startService(intent)
        }
    }
}
