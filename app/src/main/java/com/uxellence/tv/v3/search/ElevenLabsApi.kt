package com.uxellence.tv.v3.search

import android.util.Log
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs Speech-to-Text (Scribe) API Interface
 *
 * Documentation: https://elevenlabs.io/docs/api-reference/speech-to-text
 *
 * Features:
 * - Polish language support (WER ≤ 5%)
 * - Real-time transcription
 * - Automatic language detection
 * - Speaker diarization
 * - Word-level timestamps
 */

data class TranscriptionResponse(
    val text: String,
    val language_code: String? = null,
    val duration_seconds: Double? = null,
    val words: List<TranscriptionWord>? = null
)

data class TranscriptionWord(
    val word: String,
    val start: Double,
    val end: Double,
    val confidence: Double? = null
)

data class TranscriptionError(
    val detail: ErrorDetail
) {
    data class ErrorDetail(
        val status: String,
        val message: String
    )
}

interface ElevenLabsApiService {
    /**
     * Transcribe audio file to text
     *
     * @param apiKey ElevenLabs API key (xi-api-key header)
     * @param audio Audio file (WAV, MP3, etc.)
     * @param modelId Model ID (default: "scribe-v1")
     * @param language Language code (e.g., "pol" for Polish)
     * @return Transcription response with text
     */
    @Multipart
    @POST("v1/speech-to-text")
    suspend fun transcribe(
        @Header("xi-api-key") apiKey: String,
        @Part file: MultipartBody.Part,
        @Part("model_id") modelId: RequestBody,
        @Part("language") language: RequestBody
    ): TranscriptionResponse
}

/**
 * ElevenLabs API Client Singleton
 *
 * Usage:
 * ```kotlin
 * val api = ElevenLabsApiClient.create()
 * val response = api.transcribe(apiKey, audioPart, modelId, language)
 * ```
 */
object ElevenLabsApiClient {
    private const val BASE_URL = "https://api.elevenlabs.io/"

    fun create(): ElevenLabsApiService {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("ElevenLabsAPI", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS // Changed from BODY to reduce log noise
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)   // Increased for slow TV WiFi (was 30s)
            .readTimeout(120, TimeUnit.SECONDS)     // Increased for transcription time (was 60s)
            .writeTimeout(120, TimeUnit.SECONDS)    // Increased for slow upload (was 60s)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ElevenLabsApiService::class.java)
    }
}
