package com.koy.auto

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CarProjectionService.LocalBinder
            projectionService = binder.getService()
            isBound = true
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

    override fun onStop() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
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
            playUrl(Constants.URL_DRIVER_MUSIC)
        }

        binding.chipNews.setOnClickListener {
            playUrl(Constants.URL_VOV_NEWS)
        }

        binding.chipPodcast.setOnClickListener {
            playUrl(Constants.URL_PODCAST)
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
        val targetUrl = if (urlOrSearch.startsWith("http://") || urlOrSearch.startsWith("https://")) {
            urlOrSearch
        } else {
            Constants.YOUTUBE_SEARCH_PREFIX + urlOrSearch
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

    override fun onBackPressed() {
        if (binding.phoneWebView.canGoBack()) {
            binding.phoneWebView.goBack()
        } else {
            super.onBackPressed()
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

    override fun onDestroy() {
        binding.phoneWebView.cleanUp()
        super.onDestroy()
    }
}
