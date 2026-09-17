package com.koy.auto.webview

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient

class KoYWebChromeClient(
    private val customViewContainer: ViewGroup,
    private val onFullscreenChanged: (Boolean) -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback

        customViewContainer.addView(
            customView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        customViewContainer.visibility = View.VISIBLE
        onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return

        customViewContainer.removeView(customView)
        customViewContainer.visibility = View.GONE
        customView = null

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        onFullscreenChanged(false)
    }

    fun isFullscreen(): Boolean = customView != null

    fun exitFullscreen() {
        if (isFullscreen()) {
            onHideCustomView()
        }
    }
}
