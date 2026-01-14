package com.uxellence.tv.v3.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "YouTubeExtractor"

/**
 * Utility do ekstrakcji direct stream URL z YouTube.
 *
 * Używa Vercel API z yt-dlp do wyciągnięcia URL który można odtwarzać w ExoPlayer.
 */
object YouTubeStreamExtractor {

    private const val API_URL = "https://yt-extract-api.vercel.app/api/extract"

    /**
     * Ekstrahuje direct stream URL z YouTube video URL.
     *
     * @param youtubeUrl URL YouTube (watch?v=..., youtu.be/..., embed/...)
     * @return Direct stream URL do odtworzenia w ExoPlayer, lub null jeśli błąd
     */
    suspend fun extractStreamUrl(youtubeUrl: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "extractStreamUrl called with: '$youtubeUrl' (length=${youtubeUrl.length})")

        if (youtubeUrl.isBlank()) {
            Log.w(TAG, "Empty URL provided")
            return@withContext null
        }

        // Direct MP4/M3U8 URLs - bypass API call, return immediately
        val isDirectMp4 = youtubeUrl.endsWith(".mp4")
        val isDirectM3u8 = youtubeUrl.endsWith(".m3u8")
        val isSupabase = youtubeUrl.contains("supabase.co/storage")

        Log.d(TAG, "URL checks: mp4=$isDirectMp4, m3u8=$isDirectM3u8, supabase=$isSupabase")

        if (isDirectMp4 || isDirectM3u8 || isSupabase) {
            Log.d(TAG, "Direct URL detected! Returning as-is")
            return@withContext youtubeUrl
        }

        try {
            val encodedUrl = URLEncoder.encode(youtubeUrl, "UTF-8")
            val requestUrl = "$API_URL?url=$encodedUrl"

            Log.d(TAG, "Requesting stream URL from API...")

            val url = URL(requestUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000  // 15s timeout
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val streamUrl = json.optString("stream_url", null)

                if (streamUrl != null) {
                    val title = json.optString("title", "Unknown")
                    Log.d(TAG, "Got stream URL for: $title")
                    return@withContext streamUrl
                }

                Log.w(TAG, "No stream_url in response")
                return@withContext null
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "API error $responseCode: $errorStream")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract stream: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Ekstrahuje Video ID z różnych formatów URL YouTube:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://www.youtube.com/embed/VIDEO_ID
     * - https://www.youtube.com/v/VIDEO_ID
     */
    fun extractVideoId(url: String): String? {
        if (url.isBlank()) return null

        val patterns = listOf(
            Regex("v=([a-zA-Z0-9_-]{11})"),           // watch?v=...
            Regex("youtu\\.be/([a-zA-Z0-9_-]{11})"),  // youtu.be/...
            Regex("embed/([a-zA-Z0-9_-]{11})"),       // embed/...
            Regex("/v/([a-zA-Z0-9_-]{11})")           // /v/...
        )

        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.get(1)?.let {
                return it
            }
        }

        // Maybe it's just a video ID directly
        if (url.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return url
        }

        return null
    }
}
