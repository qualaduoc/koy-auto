package com.koy.auto.util

object Constants {
    const val YOUTUBE_URL_MOBILE = "https://m.youtube.com"
    const val YOUTUBE_URL_DESKTOP = "https://www.youtube.com"
    const val YOUTUBE_SEARCH_PREFIX = "https://m.youtube.com/results?search_query="

    // Quick Driver Playlists
    const val URL_DRIVER_MUSIC = "https://m.youtube.com/results?search_query=nhac+lofi+lai+xe+khong+quang+cao"
    const val URL_VOV_NEWS = "https://m.youtube.com/results?search_query=vov+giao+thong+truc+tiep"
    const val URL_PODCAST = "https://m.youtube.com/results?search_query=sach+noi+kinh+doanh+hay+nhat"

    // Custom Desktop/Car User-Agent (Enables high performance HTML5 player)
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    // Intent Actions
    const val ACTION_START_PROJECTION = "com.koy.auto.action.START_PROJECTION"
    const val ACTION_STOP_PROJECTION = "com.koy.auto.action.STOP_PROJECTION"
    const val ACTION_LOAD_URL = "com.koy.auto.action.LOAD_URL"
    const val EXTRA_URL = "extra_url"
}
