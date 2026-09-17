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
import com.koy.auto.R
import com.koy.auto.util.Constants
import com.koy.auto.webview.KoYWebChromeClient
import com.koy.auto.webview.KoYWebView
import com.koy.auto.webview.KoYWebViewClient

class CarPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display, R.style.Theme_KoYAuto_Presentation) {

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
        webView = findViewById(R.id.carWebView)
        customViewContainer = findViewById(R.id.customViewContainer)
        floatingControlBar = findViewById(R.id.floatingControlBar)
        btnBack = findViewById(R.id.btnBack)
        btnHome = findViewById(R.id.btnHome)
        btnReload = findViewById(R.id.btnReload)
        btnFullscreen = findViewById(R.id.btnFullscreen)
        ivAdBlockStatus = findViewById(R.id.ivAdBlockStatus)

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
            loadUrl(Constants.YOUTUBE_URL_MOBILE)
        }

        btnReload.setOnClickListener {
            webView.reload()
        }

        btnFullscreen.setOnClickListener {
            webView.triggerFullscreenJs()
        }

        ivAdBlockStatus.setOnClickListener {
            val current = webViewClient?.isAdBlockEnabled ?: true
            webViewClient?.isAdBlockEnabled = !current
            ivAdBlockStatus.alpha = if (!current) 1.0f else 0.4f
            webView.reload()
        }
    }

    private fun loadInitialPage() {
        webView.loadUrl(initialUrlToLoad)
    }

    fun loadUrl(url: String) {
        if (::webView.isInitialized) {
            webView.loadUrl(url)
        } else {
            initialUrlToLoad = url
        }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        webViewClient?.isAdBlockEnabled = enabled
        if (::ivAdBlockStatus.isInitialized) {
            ivAdBlockStatus.alpha = if (enabled) 1.0f else 0.4f
        }
    }

    fun togglePlayback() {
        if (::webView.isInitialized) {
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
        if (::webView.isInitialized) {
            webView.cleanUp()
        }
        super.onDetachedFromWindow()
    }
}
