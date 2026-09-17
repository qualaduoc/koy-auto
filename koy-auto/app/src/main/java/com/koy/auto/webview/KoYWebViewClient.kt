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

        // Prevent opening external YouTube app; keep browsing inside KoY-Auto
        if (uri.scheme == "vnd.youtube" || uri.scheme == "intent") {
            return true
        }

        view?.loadUrl(url)
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)

        // Inject CSS to hide YouTube "Open App" banner & auto-maximize player
        val hideBannersJs = """
            (function() {
                var style = document.createElement('style');
                style.type = 'text/css';
                style.innerHTML = '.open-app-button, ytm-pivot-bar-renderer[is-open-app-banner-present], ytm-mobile-topbar-renderer .open-app-button, .banner-badge-container { display: none !important; }';
                document.head.appendChild(style);
            })();
        """.trimIndent()

        view?.evaluateJavascript(hideBannersJs, null)

        url?.let { onPageFinishedCallback?.invoke(it) }
    }
}
