package com.koy.auto.webview

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class KoYWebViewClient(
    var isAdBlockEnabled: Boolean = true,
    private val onPageFinishedCallback: ((String) -> Unit)? = null
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        if (isAdBlockEnabled && AdBlockFilter.isAdUrl(url)) {
            return AdBlockFilter.createEmptyResponse()
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val uri = Uri.parse(url)

        // Block external intents and opening outside apps (keep inside KoY-Auto)
        val scheme = uri.scheme?.lowercase() ?: ""
        if (scheme == "vnd.youtube" || scheme == "intent" || scheme == "market") {
            return true
        }

        // Allow normal web navigation (http, https, javascript, about)
        // Returning false lets Chromium internal engine handle clicks, SPA navigation, and History API
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        // 1. Inject CSS to hide YouTube "Open App" banner & auto-maximize player
        val hideBannersJs = """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = '.open-app-button, ytm-pivot-bar-renderer[is-open-app-banner-present], ytm-mobile-topbar-renderer .open-app-button, .banner-badge-container, .ytm-promoted-sparkles-web-renderer { display: none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()
        view?.evaluateJavascript(hideBannersJs, null)

        // 2. Prevent YouTube from pausing when screen turns off or loses focus (Background playback hack)
        val backgroundPlayJs = """
            (function() {
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                } catch(e) {}

                // Ngăn chặn YouTube lắng nghe sự kiện ẩn màn hình
                window.addEventListener('visibilitychange', function(e) {
                    e.stopImmediatePropagation();
                }, true);

                // Theo dõi tương tác người dùng: nếu người dùng bấm chạm thì ghi nhận
                document.addEventListener('pointerdown', function() {
                    window._koyUserInteracted = Date.now();
                }, true);

                if (!window._koyVideoHooked) {
                    window._koyVideoHooked = true;
                    setInterval(function() {
                        var v = document.querySelector('video');
                        if (v && !v._koyListened) {
                            v._koyListened = true;
                            v.addEventListener('pause', function(e) {
                                var elapsedSinceTouch = Date.now() - (window._koyUserInteracted || 0);
                                // Nếu tạm dừng do tắt màn hình (hệ thống tự pause, không có touch gần đây)
                                if (elapsedSinceTouch > 600 && !window._koyUserWantsPause && !v.ended) {
                                    setTimeout(function() {
                                        try { v.play(); } catch(err) {}
                                    }, 150);
                                }
                            });
                        }
                    }, 1000);
                }
            })();
        """.trimIndent()
        view?.evaluateJavascript(backgroundPlayJs, null)

        // 3. Auto-skip in-stream video ads if adblock filter misses them
        val autoSkipAdsJs = """
            (function() {
                if (window._koyAdSkipInterval) return;
                window._koyAdSkipInterval = setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-skip-ad-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton');
                    if (skipBtn) {
                        skipBtn.click();
                    }
                    var adVideo = document.querySelector('.ad-showing video, .ad-interrupting video');
                    if (adVideo && !isNaN(adVideo.duration)) {
                        adVideo.currentTime = adVideo.duration;
                    }
                }, 500);
            })();
        """.trimIndent()
        view?.evaluateJavascript(autoSkipAdsJs, null)

        // 4. Destroy YouTube anti-adblock enforcement modal if it pops up
        val antiAdblockBypassJs = """
            (function() {
                if (window._koyAntiAdblockInterval) return;
                window._koyAntiAdblockInterval = setInterval(function() {
                    var adblockDialog = document.querySelector('ytd-enforcement-message-view-model, tp-yt-paper-dialog.ytd-popup-container');
                    if (adblockDialog) {
                        adblockDialog.remove();
                        var backdrop = document.querySelector('tp-yt-iron-overlay-backdrop');
                        if (backdrop) backdrop.remove();
                        var v = document.querySelector('video');
                        if (v && v.paused) { v.play(); }
                    }
                }, 1000);
            })();
        """.trimIndent()
        view?.evaluateJavascript(antiAdblockBypassJs, null)

        url?.let { onPageFinishedCallback?.invoke(it) }
    }
}
