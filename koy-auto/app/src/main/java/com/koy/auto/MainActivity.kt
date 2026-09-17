package com.koy.auto

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Rational
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.koy.auto.databinding.ActivityMainBinding
import com.koy.auto.service.CarProjectionService
import com.koy.auto.util.Constants
import com.koy.auto.webview.KoYWebChromeClient
import com.koy.auto.webview.KoYWebViewClient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var projectionService: CarProjectionService? = null
    private var isBound = false
    private var requestedOverlayPermission = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CarProjectionService.LocalBinder
            projectionService = binder.getService()
            isBound = true
            ensureExternalDisplayPermission()
            observeCarStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            projectionService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure background projection service is running
        CarProjectionService.start(this)

        setupPhoneWebView()
        setupListeners()
        handleIncomingIntent(intent)
        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, CarProjectionService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        ensureExternalDisplayPermission()
    }

    override fun onPause() {
        super.onPause()
        // Giữ JavaScript timers tiếp tục chạy để âm thanh và auto-skip ad không bị đóng băng khi tắt màn hình
        binding.phoneWebView.resumeTimers()
    }

    override fun onStop() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        binding.phoneWebView.resumeTimers()
        super.onStop()
    }

    private fun setupPhoneWebView() {
        val webViewClient = KoYWebViewClient(isAdBlockEnabled = binding.switchAdBlock.isChecked)
        binding.phoneWebView.webViewClient = webViewClient

        val webChromeClient = KoYWebChromeClient(binding.phoneCustomViewContainer) { isFullscreen ->
            binding.cardInput.visibility = if (isFullscreen) View.GONE else View.VISIBLE
            binding.topBar.visibility = if (isFullscreen) View.GONE else View.VISIBLE
        }
        binding.phoneWebView.webChromeClient = webChromeClient
        binding.phoneWebView.loadUrl(Constants.YOUTUBE_URL_MOBILE)
    }

    private fun setupListeners() {
        binding.btnPlayOnCar.setOnClickListener {
            val url = binding.etYoutubeUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                playUrl(url)
            } else {
                Toast.makeText(this, "Vui lòng nhập link YouTube", Toast.LENGTH_SHORT).show()
            }
        }

        binding.chipMusic.setOnClickListener {
            com.koy.auto.player.KoYPlayerManager.playPredefined("music")
            Toast.makeText(this, "Đang phát: 🎵 VOV3 Âm Nhạc", Toast.LENGTH_SHORT).show()
        }

        binding.chipNews.setOnClickListener {
            com.koy.auto.player.KoYPlayerManager.playPredefined("news")
            Toast.makeText(this, "Đang phát: 📻 VOV Giao Thông trực tiếp", Toast.LENGTH_SHORT).show()
        }

        binding.chipPodcast.setOnClickListener {
            com.koy.auto.player.KoYPlayerManager.playPredefined("podcast")
            Toast.makeText(this, "Đang phát: 🎙️ VOV2 Văn Hóa & Xã Hội", Toast.LENGTH_SHORT).show()
        }

        binding.switchAdBlock.setOnCheckedChangeListener { _, isChecked ->
            (binding.phoneWebView.webViewClient as? KoYWebViewClient)?.isAdBlockEnabled = isChecked
            projectionService?.carDisplayManager?.setAdBlockEnabled(isChecked)
            binding.phoneWebView.reload()
            Toast.makeText(
                this,
                if (isChecked) "Đã BẬT chặn quảng cáo" else "Đã TẮT chặn quảng cáo",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.switchDimPhone.setOnCheckedChangeListener { _, isChecked ->
            applyScreenDimming(isChecked && (projectionService?.carDisplayManager?.isCarConnected?.value == true))
        }
    }

    private fun playUrl(urlOrSearch: String) {
        val videoId = com.koy.auto.player.YouTubeStreamExtractor.extractVideoId(urlOrSearch)

        if (videoId != null) {
            Toast.makeText(this, "Đang chuẩn bị luồng phát nền...", Toast.LENGTH_SHORT).show()
            if (projectionService?.carDisplayManager?.isCarConnected?.value == true) {
                // CarPresentation owns the one native playback request and its PlayerView.
                projectionService?.carDisplayManager?.loadUrlOnCar(urlOrSearch)
            } else {
                // Without a secondary display, keep native audio alive in the foreground service.
                com.koy.auto.player.KoYPlayerManager.playYouTube(
                    urlOrId = urlOrSearch,
                    audioOnly = true,
                    onStart = {
                        this@MainActivity.runOnUiThread {
                            Toast.makeText(this@MainActivity, "Đang phát nền", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { errMsg ->
                        this@MainActivity.runOnUiThread {
                            Toast.makeText(this@MainActivity, "Không thể mở luồng: $errMsg", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            return
        }

        val targetUrl = if (urlOrSearch.startsWith("http://") || urlOrSearch.startsWith("https://")) {
            urlOrSearch
        } else {
            Constants.YOUTUBE_SEARCH_PREFIX + Uri.encode(urlOrSearch)
        }

        // 1. If car is connected, send to car display
        if (projectionService?.carDisplayManager?.isCarConnected?.value == true) {
            projectionService?.carDisplayManager?.loadUrlOnCar(targetUrl)
            Toast.makeText(this, "Đang phát lên màn hình ô tô...", Toast.LENGTH_SHORT).show()
        } else {
            // 2. Otherwise play in phone preview webview
            binding.phoneWebView.loadUrl(targetUrl)
            Toast.makeText(this, "Đang phát trên màn hình điện thoại", Toast.LENGTH_SHORT).show()
        }
    }


    private fun observeCarStatus() {
        val service = projectionService ?: return
        lifecycleScope.launch {
            service.carDisplayManager.isCarConnected.collectLatest { isConnected ->
                runOnUiThread {
                    if (isConnected) {
                        binding.tvCarStatus.text = getString(R.string.status_car_connected)
                        binding.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(
                            this@MainActivity,
                            R.color.green_status
                        )
                        if (binding.switchDimPhone.isChecked) {
                            applyScreenDimming(true)
                        }
                    } else {
                        binding.tvCarStatus.text = getString(R.string.status_car_disconnected)
                        binding.statusIndicator.backgroundTintList = ContextCompat.getColorStateList(
                            this@MainActivity,
                            R.color.red_status
                        )
                        applyScreenDimming(false)
                    }
                }
            }
        }
    }

    private fun applyScreenDimming(dim: Boolean) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (dim) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams
    }

    private fun ensureExternalDisplayPermission() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val hasPresentationDisplay = displayManager
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .any { it.displayId != Display.DEFAULT_DISPLAY }

        if (!hasPresentationDisplay) return

        if (Settings.canDrawOverlays(this)) {
            projectionService?.carDisplayManager?.refreshDisplays()
            return
        }

        if (!requestedOverlayPermission) {
            requestedOverlayPermission = true
            Toast.makeText(
                this,
                "Cho phép hiển thị trên ứng dụng khác để xuất hình ra màn hình phụ",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            val urlString = uri.toString()
            binding.etYoutubeUrl.setText(urlString)
            playUrl(urlString)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.phoneWebView.canGoBack()) {
            binding.phoneWebView.goBack()
        } else {
            // Ẩn ứng dụng xuống nền thay vì đóng hẳn, giúp duy trì âm thanh chạy ngầm
            moveTaskToBack(true)
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1002)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Khi người dùng bấm nút Home hoặc chuyển sang app bản đồ (Google Maps), tự động thu nhỏ thành cửa sổ nổi PiP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            binding.cardInput.visibility = View.GONE
            binding.topBar.visibility = View.GONE
        } else {
            binding.cardInput.visibility = View.VISIBLE
            binding.topBar.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        binding.phoneWebView.cleanUp()
        super.onDestroy()
    }
}
