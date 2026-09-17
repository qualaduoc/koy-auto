package com.koy.auto

import android.app.Application
import android.webkit.WebView

class KoYAutoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Never expose WebView contents/cookies through chrome://inspect in release builds.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        // Initialize Media3 ExoPlayer Engine
        com.koy.auto.player.KoYPlayerManager.initialize(this)
        com.koy.auto.service.KoYMediaSessionManager.initialize(this)
    }
}
