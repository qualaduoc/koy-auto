package com.koy.auto.webview

import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import androidx.core.content.ContextCompat

class KoYWebChromeClient(
    private val customViewContainer: ViewGroup,
    private val onFullscreenChanged: (Boolean) -> Unit
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest?) {
        val permissionRequest = request ?: return
        val canRecordAudio = ContextCompat.checkSelfPermission(
            customViewContainer.context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val requestedAudio = permissionRequest.resources
            .filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
            .toTypedArray()

        if (canRecordAudio && requestedAudio.isNotEmpty()) {
            permissionRequest.grant(requestedAudio)
        } else {
            permissionRequest.deny()
        }
    }

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
