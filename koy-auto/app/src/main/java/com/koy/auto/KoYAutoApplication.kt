package com.koy.auto

import android.app.Application
import android.webkit.WebView

class KoYAutoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Enable remote debugging of WebView via Chrome devtools on PC (chrome://inspect)
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
