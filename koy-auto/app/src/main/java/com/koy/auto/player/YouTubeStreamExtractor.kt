package com.koy.auto.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class StreamInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val streamUrl: String,
    val isHls: Boolean = false,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L
)

object YouTubeStreamExtractor {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val VIDEO_ID_REGEX = Pattern.compile(
        "^.*(?:(?:youtu\\.be\\/|v\\/|vi\\/|u\\/\\w\\/|embed\\/|shorts\\/)|(?:(?:watch)?\\?v(?:i)?=|\\&v(?:i)?=))([^#\\&\\?]*).*"
    )

    fun extractVideoId(urlOrId: String): String? {
        val trimmed = urlOrId.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }
        val matcher = VIDEO_ID_REGEX.matcher(trimmed)
        return if (matcher.matches()) {
            val id = matcher.group(1)
            if (!id.isNullOrEmpty() && id.length == 11) id else null
        } else {
            null
        }
    }

    suspend fun extractStream(urlOrId: String): Result<StreamInfo> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(urlOrId)
            ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy YouTube Video ID hợp lệ"))

        // 1. Thử lấy luồng trực tiếp qua YouTube InnerTube Android API
        try {
            val innerTubeResult = fetchFromInnerTube(videoId)
            if (innerTubeResult.isSuccess) {
                return@withContext innerTubeResult
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Dự phòng: Lấy luồng qua các cụm Piped API mở nếu InnerTube gặp captcha/chặn
        try {
            val pipedResult = fetchFromPipedApi(videoId)
            if (pipedResult.isSuccess) {
                return@withContext pipedResult
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Result.failure(Exception("Không thể trích xuất luồng video cho ID: $videoId"))
    }

    private fun fetchFromInnerTube(videoId: String): Result<StreamInfo> {
        val endpoint = "https://www.youtube.com/youtubei/v1/player"
        val payload = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "ANDROID")
                    put("clientVersion", "19.05.36")
                    put("androidSdkVersion", 34)
                    put("hl", "vi")
                    put("gl", "VN")
                })
            })
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .addHeader("User-Agent", "com.google.android.youtube/19.05.36 (Linux; U; Android 14; vi_VN)")
            .addHeader("X-YouTube-Client-Name", "3")
            .addHeader("X-YouTube-Client-Version", "19.05.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(Exception("InnerTube HTTP ${response.code}"))
            }

            val bodyString = response.body?.string() ?: return Result.failure(Exception("Empty body"))
            val json = JSONObject(bodyString)

            val videoDetails = json.optJSONObject("videoDetails")
            val title = videoDetails?.optString("title") ?: "YouTube Video"
            val author = videoDetails?.optString("author") ?: "KoY Auto"
            val durationSec = videoDetails?.optLong("lengthSeconds") ?: 0L

            var thumbUrl: String? = null
            videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let { thumbs ->
                if (thumbs.length() > 0) {
                    thumbUrl = thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                }
            }

            val streamingData = json.optJSONObject("streamingData") ?: return Result.failure(Exception("Không có streamingData"))

            // 1. Kiểm tra luồng HLS (.m3u8) - Tốt nhất cho ExoPlayer tự co giãn 720p/1080p
            val hlsManifestUrl = streamingData.optString("hlsManifestUrl")
            if (hlsManifestUrl.isNotEmpty()) {
                return Result.success(
                    StreamInfo(
                        videoId = videoId,
                        title = title,
                        author = author,
                        streamUrl = hlsManifestUrl,
                        isHls = true,
                        thumbnailUrl = thumbUrl,
                        durationSeconds = durationSec
                    )
                )
            }

            // 2. Kiểm tra formats có sẵn cả Video + Audio (itag 22: 720p, itag 18: 360p)
            val formats = streamingData.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                var chosenUrl: String? = null
                // Ưu tiên 720p (itag 22)
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    val url = f.optString("url")
                    val itag = f.optInt("itag")
                    if (url.isNotEmpty()) {
                        if (itag == 22) {
                            chosenUrl = url
                            break
                        } else if (chosenUrl == null) {
                            chosenUrl = url
                        }
                    }
                }

                if (!chosenUrl.isNullOrEmpty()) {
                    return Result.success(
                        StreamInfo(
                            videoId = videoId,
                            title = title,
                            author = author,
                            streamUrl = chosenUrl,
                            isHls = false,
                            thumbnailUrl = thumbUrl,
                            durationSeconds = durationSec
                        )
                    )
                }
            }
        }

        return Result.failure(Exception("Không tìm thấy link phát trực tiếp trong InnerTube"))
    }

    private fun fetchFromPipedApi(videoId: String): Result<StreamInfo> {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://piped-api.garudalinux.org"
        )

        for (instance in instances) {
            try {
                val request = Request.Builder()
                    .url("$instance/streams/$videoId")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)

                    val title = json.optString("title", "YouTube Video")
                    val author = json.optString("uploader", "KoY Auto")
                    val duration = json.optLong("duration", 0L)
                    val thumbnail = json.optString("thumbnailUrl")

                    // HLS Stream
                    val hlsUrl = json.optString("hls")
                    if (hlsUrl.isNotEmpty()) {
                        return Result.success(
                            StreamInfo(
                                videoId = videoId,
                                title = title,
                                author = author,
                                streamUrl = hlsUrl,
                                isHls = true,
                                thumbnailUrl = thumbnail,
                                durationSeconds = duration
                            )
                        )
                    }

                    // Direct MP4 Streams (Ưu tiên 720p)
                    val videoStreams = json.optJSONArray("videoStreams")
                    if (videoStreams != null && videoStreams.length() > 0) {
                        var directUrl: String? = null
                        for (i in 0 until videoStreams.length()) {
                            val stream = videoStreams.getJSONObject(i)
                            val quality = stream.optString("quality")
                            val isVideoOnly = stream.optBoolean("videoOnly", false)
                            val url = stream.optString("url")

                            if (!isVideoOnly && url.isNotEmpty()) {
                                if (quality.contains("720p")) {
                                    directUrl = url
                                    break
                                } else if (directUrl == null) {
                                    directUrl = url
                                }
                            }
                        }

                        if (!directUrl.isNullOrEmpty()) {
                            return Result.success(
                                StreamInfo(
                                    videoId = videoId,
                                    title = title,
                                    author = author,
                                    streamUrl = directUrl,
                                    isHls = false,
                                    thumbnailUrl = thumbnail,
                                    durationSeconds = duration
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        return Result.failure(Exception("Toàn bộ máy chủ dự phòng Piped đều không phản hồi"))
    }
}
