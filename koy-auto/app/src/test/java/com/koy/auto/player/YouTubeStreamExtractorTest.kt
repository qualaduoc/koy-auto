package com.koy.auto.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeStreamExtractorTest {

    @Test
    fun extractsSupportedYouTubeUrlShapes() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, YouTubeStreamExtractor.extractVideoId(id))
        assertEquals(id, YouTubeStreamExtractor.extractVideoId("https://youtu.be/$id?t=10"))
        assertEquals(id, YouTubeStreamExtractor.extractVideoId("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, YouTubeStreamExtractor.extractVideoId("https://youtube.com/shorts/$id"))
        assertEquals(id, YouTubeStreamExtractor.extractVideoId("https://youtube.com/live/$id"))
    }

    @Test
    fun rejectsSearchTextAndMalformedIds() {
        assertNull(YouTubeStreamExtractor.extractVideoId("nhac lai xe"))
        assertNull(YouTubeStreamExtractor.extractVideoId("https://youtube.com/watch?v=too-short"))
    }
}
