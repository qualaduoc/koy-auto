package com.koy.auto.presentation

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import com.koy.auto.R
import com.koy.auto.player.KoYPlayerManager
import com.koy.auto.util.Constants
import com.koy.auto.webview.KoYWebChromeClient
import com.koy.auto.webview.KoYWebView
import com.koy.auto.webview.KoYWebViewClient

class CarPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display, R.style.Theme_KoYAuto_Presentation) {

    private lateinit var carPlayerView: PlayerView
    private lateinit var webView: KoYWebView
    private lateinit var customViewContainer: FrameLayout
    private lateinit var floatingControlBar: View
    private lateinit var btnBack: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnReload: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var ivAdBlockStatus: ImageView

    private var webChromeClient: KoYWebChromeClient? = null
    private var webViewClient: KoYWebViewClient? = null
    private var initialUrlToLoad: String = Constants.YOUTUBE_URL_MOBILE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_car_presentation)

        initViews()
        setupListeners()
        loadInitialPage()
    }

    private fun initViews() {
        carPlayerView = findViewById(R.id.carPlayerView)
        webView = findViewById(R.id.carWebView)
        customViewContainer = findViewById(R.id.customViewContainer)
        floatingControlBar = findViewById(R.id.floatingControlBar)
        btnBack = findViewById(R.id.btnBack)
        btnHome = findViewById(R.id.btnHome)
        btnReload = findViewById(R.id.btnReload)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        ivAdBlockStatus = findViewById(R.id.ivAdBlockStatus)

        // Gắn PlayerView vào KoYPlayerManager để xuất video trực tiếp lên màn hình xe
        KoYPlayerManager.attachPlayerView(carPlayerView)

        webViewClient = KoYWebViewClient(isAdBlockEnabled = true)
        webView.webViewClient = webViewClient!!

        webChromeClient = KoYWebChromeClient(customViewContainer) { isFullscreen ->
            floatingControlBar.visibility = if (isFullscreen) View.GONE else View.VISIBLE
        }
        webView.webChromeClient = webChromeClient
    }


    private fun setupListeners() {
        btnBack.setOnClickListener {
            if (webChromeClient?.isFullscreen() == true) {
                webChromeClient?.exitFullscreen()
            } else if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        btnHome.setOnClickListener {
            showBrowserView()
            loadUrl(Constants.YOUTUBE_URL_MOBILE)
        }

        btnReload.setOnClickListener {
            if (carPlayerView.visibility == View.VISIBLE) {
                KoYPlayerManager.resume()
            } else {
                webView.reload()
            }
        }

        btnFullscreen.setOnClickListener {
            if (carPlayerView.visibility == View.VISIBLE) {
                // Đổi chế độ fit / zoom tràn viền màn hình xe
                val currentMode = carPlayerView.resizeMode
                carPlayerView.resizeMode = if (currentMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            } else {
                webView.triggerFullscreenJs()
            }
        }

        ivAdBlockStatus.setOnClickListener {
            val current = webViewClient?.isAdBlockEnabled ?: true
            webViewClient?.isAdBlockEnabled = !current
            ivAdBlockStatus.alpha = if (!current) 1.0f else 0.4f
            webView.reload()
        }
    }

    fun showNativePlayer() {
        carPlayerView.visibility = View.VISIBLE
        webView.visibility = View.GONE
    }

    fun showBrowserView() {
        carPlayerView.visibility = View.GONE
        webView.visibility = View.VISIBLE
    }

    private fun loadInitialPage() {
        webView.loadUrl(initialUrlToLoad)
    }

    fun loadUrl(url: String) {
        val videoId = com.koy.auto.player.YouTubeStreamExtractor.extractVideoId(url)
        if (videoId != null) {
            // Là link video YouTube: Chuyển sang Native ExoPlayer chạy mượt 60 FPS, không dùng WebView
            showNativePlayer()
            KoYPlayerManager.playYouTube(url)
        } else {
            // Là link trang web tìm kiếm: Hiển thị giao diện web
            showBrowserView()
            if (::webView.isInitialized) {
                webView.loadUrl(url)
            } else {
                initialUrlToLoad = url
            }
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        webViewClient?.isAdBlockEnabled = enabled
        if (::ivAdBlockStatus.isInitialized) {
            ivAdBlockStatus.alpha = if (enabled) 1.0f else 0.4f
        }
    }

    fun togglePlayback() {
        if (carPlayerView.visibility == View.VISIBLE) {
            KoYPlayerManager.togglePlayPause()
        } else if (::webView.isInitialized) {
            val jsToggle = """
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        if (v.paused) {
                            window._koyUserWantsPause = false;
                            v.play();
                        } else {
                            window._koyUserWantsPause = true;
                            v.pause();
                        }
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(jsToggle, null)
        }
    }

    override fun onDetachedFromWindow() {
        if (::carPlayerView.isInitialized) {
            KoYPlayerManager.detachPlayerView(carPlayerView)
        }
        if (::webView.isInitialized) {
            webView.cleanUp()
        }
        super.onDetachedFromWindow()
    }
}

