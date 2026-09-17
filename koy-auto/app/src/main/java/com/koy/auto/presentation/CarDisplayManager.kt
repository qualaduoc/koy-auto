package com.koy.auto.presentation

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CarDisplayManager(private val context: Context) : DisplayManager.DisplayListener {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var activePresentation: CarPresentation? = null

    private val _isCarConnected = MutableStateFlow(false)
    val isCarConnected: StateFlow<Boolean> = _isCarConnected

    fun startListening() {
        displayManager.registerDisplayListener(this, null)
        checkCurrentDisplays()
    }

    fun stopListening() {
        displayManager.unregisterDisplayListener(this)
        dismissPresentation()
    }

    private fun checkCurrentDisplays() {
        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val targetDisplay = displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?: displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (targetDisplay != null) {
            showPresentation(targetDisplay)
        } else {
            dismissPresentation()
        }
    }

    private fun showPresentation(display: Display) {
        if (activePresentation != null && activePresentation?.display?.displayId == display.displayId) {
            return
        }

        dismissPresentation()

        try {
            activePresentation = CarPresentation(context, display).apply {
                if (context !is Activity) {
                    window?.setType(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                        }
                    )
                }
                show()
            }
            _isCarConnected.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _isCarConnected.value = false
        }
    }

    fun dismissPresentation() {
        activePresentation?.let {
            try {
                it.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activePresentation = null
        _isCarConnected.value = false
    }

    fun loadUrlOnCar(url: String) {
        activePresentation?.loadUrl(url)
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        activePresentation?.setAdBlockEnabled(enabled)
    }

    override fun onDisplayAdded(displayId: Int) {
        val display = displayManager.getDisplay(displayId)
        if (display != null && display.displayId != Display.DEFAULT_DISPLAY) {
            showPresentation(display)
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (activePresentation?.display?.displayId == displayId) {
            dismissPresentation()
        }
    }

    override fun onDisplayChanged(displayId: Int) {
        // Handle resolution or configuration changes
    }
}
