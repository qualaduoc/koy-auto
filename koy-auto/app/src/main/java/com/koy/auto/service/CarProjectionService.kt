package com.koy.auto.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.koy.auto.MainActivity
import com.koy.auto.R
import com.koy.auto.presentation.CarDisplayManager
import com.koy.auto.util.AudioFocusHelper
import com.koy.auto.util.Constants

class CarProjectionService : Service() {

    private val binder = LocalBinder()
    lateinit var carDisplayManager: CarDisplayManager
        private set

    private lateinit var audioFocusHelper: AudioFocusHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_TOGGLE_PLAYBACK) {
                carDisplayManager.togglePlayback()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): CarProjectionService = this@CarProjectionService
    }

    override fun onCreate() {
        super.onCreate()
        carDisplayManager = CarDisplayManager(this)
        carDisplayManager.startListening()

        // Đăng ký nhận sự kiện điều khiển Play/Pause từ Notification
        val filter = IntentFilter(Constants.ACTION_TOGGLE_PLAYBACK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceReceiver, filter)
        }

        // 1. Acquire WakeLock to keep CPU running when screen is locked/turned off
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KoYAuto:PlaybackWakeLock").apply {
                setReferenceCounted(false)
                acquire(8 * 60 * 60 * 1000L) // Keep alive up to 8 hours
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Acquire WifiLock for seamless streaming when screen is off
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "KoYAuto:WifiLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioFocusHelper = AudioFocusHelper(
            context = this,
            onAudioFocusLost = {
                // Pause or lower volume
            },
            onAudioFocusGained = {
                // Resume normal volume
            }
        )
        audioFocusHelper.requestAudioFocus()

        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                Constants.ACTION_STOP_PROJECTION -> {
                    stopForeground(true)
                    stopSelf()
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
                description = "Duy trì kết nối và phát video YouTube trên màn hình ô tô"
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

        val playPauseIntent = Intent(Constants.ACTION_TOGGLE_PLAYBACK).apply {
            setPackage(packageName)
        }
        val pendingPlayPause = PendingIntent.getBroadcast(
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
            .setContentText("YouTube đang phát qua loa ô tô")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_refresh, "Phát / Dừng", pendingPlayPause)
            .addAction(R.drawable.ic_back, "Tắt", pendingStop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try {
            unregisterReceiver(serviceReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioFocusHelper.abandonAudioFocus()
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
