package com.koy.auto.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.koy.auto.util.Constants

class KoYWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setupSettings()
        setupFocusHandling()
    }

    private fun setupFocusHandling() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                    if (!v.hasFocus()) {
                        v.requestFocus()
                    }
                }
            }
            false
        }
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupSettings() {
        // Enable hardware layer for 60fps video
        setLayerType(View.LAYER_TYPE_HARDWARE, null)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false

            // Performance and cache settings
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true

            // Responsive & Zoom settings
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false

            // Text and layout
            textZoom = 100

            // User Agent (Default to mobile for touch responsiveness in car)
            userAgentString = Constants.MOBILE_USER_AGENT
        }

        // Enable third-party cookies for seamless YouTube authentication if needed
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
    }

    fun setDesktopMode(enabled: Boolean) {
        settings.userAgentString = if (enabled) {
            Constants.DESKTOP_USER_AGENT
        } else {
            Constants.MOBILE_USER_AGENT
        }
        reload()
    }

    fun triggerFullscreenJs() {
        // Programmatically trigger video fullscreen if user presses car fullscreen button
        val script = """
            (function() {
                var video = document.querySelector('video');
                if (video) {
                    if (video.requestFullscreen) {
                        video.requestFullscreen();
                    } else if (video.webkitRequestFullscreen) {
                        video.webkitRequestFullscreen();
                    }
                }
            })();
        """.trimIndent()
        evaluateJavascript(script, null)
    }

    fun cleanUp() {
        stopLoading()
        clearHistory()
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        destroy()
    }
}
