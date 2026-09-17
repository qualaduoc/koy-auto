package com.koy.auto.webview

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object AdBlockFilter {
    private val adHosts = setOf(
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "ad.doubleclick.net",
        "static.doubleclick.net",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "ads.youtube.com"
    )

    private val adPathSegments = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/get_midroll_info",
        "/ptracking",
        "doubleclick.net",
        "adunit"
    )

    fun isAdUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        for (host in adHosts) {
            if (lowerUrl.contains(host)) return true
        }
        for (segment in adPathSegments) {
            if (lowerUrl.contains(segment)) return true
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream("".toByteArray())
        )
    }
}
