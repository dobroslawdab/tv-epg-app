package com.uxellence.tv.v3.search

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * ElevenLabs Speech Recognition Helper
 *
 * Drop-in replacement for Google SpeechRecognitionHelper with:
 * - Polish language support (WER ≤ 5%)
 * - Same callback interface
 * - No language detection issues
 *
 * Usage:
 * ```kotlin
 * val helper = ElevenLabsSpeechHelper(
 *     context = context,
 *     apiKey = BuildConfig.ELEVENLABS_API_KEY,
 *     onResult = { text -> ... },
 *     onError = { error -> ... },
 *     onStart = { ... },
 *     onEnd = { ... }
 * )
 * helper.startListening()
 * // ... user speaks ...
 * helper.stopListening() // Triggers transcription
 * ```
 */
class ElevenLabsSpeechHelper(
    private val context: Context,
    private val apiKey: String,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit,
    private val onProgress: ((String) -> Unit)? = null,
    private val onAmplitude: ((Int) -> Unit)? = null  // NEW: Real-time amplitude callback
) {
    private var audioRecorder: AudioRecorder? = null
    private val api = ElevenLabsApiClient.create()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "ElevenLabsSpeech"
        private const val MODEL_ID = "scribe_v1" // Correct: underscore, not hyphen (scribe_v1 or scribe_v1_experimental)
        private const val LANGUAGE = "pol" // Polish language code
    }

    /**
     * Start listening - begins audio recording
     *
     * @throws SecurityException if RECORD_AUDIO permission not granted
     */
    fun startListening() {
        if (apiKey.isEmpty() || apiKey == "your_api_key_here") {
            Log.e(TAG, "ElevenLabs API key not configured")
            onError("API key not configured. Please add ELEVENLABS_API_KEY to local.properties")
            return
        }

        try {
            // Create temporary audio file
            val audioFile = File.createTempFile(
                "audio_${System.currentTimeMillis()}_",
                ".wav",
                context.cacheDir
            )

            // Start recording
            audioRecorder = AudioRecorder(audioFile).apply {
                // Set amplitude callback BEFORE starting (important!)
                onAmplitudeUpdate = { amplitude ->
                    Log.d(TAG, "Amplitude received: $amplitude")
                    // CRITICAL: Invoke on Main thread so Compose UI recomposes
                    coroutineScope.launch(Dispatchers.Main) {
                        onAmplitude?.invoke(amplitude)
                    }
                }
                Log.d(TAG, "Amplitude callback set, starting recording...")
                start()
            }

            Log.d(TAG, "Recording started for Polish transcription")
            onStart()

        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e)
            onError("Brak uprawnień do nagrywania dźwięku")
            onEnd()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            onError("Błąd uruchamiania nagrywania: ${e.message}")
            onEnd()
        }
    }

    /**
     * Stop listening - ends recording and triggers transcription
     *
     * Workflow:
     * 1. Stop audio recording
     * 2. Upload audio to ElevenLabs API
     * 3. Receive transcription
     * 4. Callback with result
     * 5. Cleanup temp file
     */
    fun stopListening() {
        val recorder = audioRecorder ?: run {
            Log.w(TAG, "No active recording to stop")
            return
        }

        try {
            // Stop recording
            recorder.stop()
            val audioFile = recorder.getFile()

            if (!audioFile.exists() || audioFile.length() == 0L) {
                Log.e(TAG, "Audio file is empty or doesn't exist")
                onProgress?.invoke("❌ Błąd: Pusty plik audio")
                onError("Nie nagrano żadnego dźwięku")
                onEnd()
                return
            }

            val fileSizeKb = audioFile.length() / 1024
            val estimatedDurationSeconds = audioFile.length() / (16000 * 2).toFloat() // 16kHz * 16-bit
            Log.d(TAG, "Recording stopped. File size: ${audioFile.length()} bytes ($fileSizeKb KB)")
            Log.d(TAG, "Estimated duration: ${String.format("%.1f", estimatedDurationSeconds)}s")
            onProgress?.invoke("📁 Plik audio: $fileSizeKb KB (~${String.format("%.1f", estimatedDurationSeconds)}s)")

            // VALIDATION: Reject very short recordings (<1s)
            if (estimatedDurationSeconds < 1.0f) {
                Log.e(TAG, "ERROR: Recording too short (${String.format("%.1f", estimatedDurationSeconds)}s, minimum 1s required)")
                onProgress?.invoke("❌ Za krótkie nagranie: ${String.format("%.1f", estimatedDurationSeconds)}s")
                onError("Nagranie za krótkie (${String.format("%.1f", estimatedDurationSeconds)}s). Mów przez minimum 1 sekundę.")
                onEnd()
                return
            }

            // Warn if file is short but acceptable (1-2s)
            if (estimatedDurationSeconds < 2.0f) {
                Log.w(TAG, "WARNING: Short recording (${String.format("%.1f", estimatedDurationSeconds)}s)")
                onProgress?.invoke("⚠️ Krótkie nagranie: ${String.format("%.1f", estimatedDurationSeconds)}s")
            }

            // Transcribe asynchronously
            transcribeAudio(audioFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onError("Błąd zatrzymywania nagrywania: ${e.message}")
            onEnd()
        } finally {
            audioRecorder = null
        }
    }

    /**
     * Test network connectivity to ElevenLabs API
     */
    private suspend fun testNetworkConnectivity(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.elevenlabs.io/")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                connection.connect()
                val responseCode = connection.responseCode
                connection.disconnect()

                Log.d(TAG, "Network test: HTTP $responseCode")
                // 200, 301, 404 are all fine - means server is reachable
                responseCode in 200..499
            } catch (e: Exception) {
                Log.e(TAG, "Network test failed: ${e.javaClass.simpleName} - ${e.message}")
                false
            }
        }
    }

    /**
     * Export audio file to Downloads for manual testing
     */
    private fun exportAudioForTesting(audioFile: File) {
        try {
            val downloadsDir = java.io.File("/sdcard/Download")
            if (!downloadsDir.exists()) {
                Log.w(TAG, "Downloads directory doesn't exist, skipping export")
                return
            }

            val exportFile = java.io.File(downloadsDir, "elevenlabs_test_${System.currentTimeMillis()}.wav")
            audioFile.copyTo(exportFile, overwrite = true)
            Log.d(TAG, "✅ Audio exported for testing: ${exportFile.absolutePath}")
            onProgress?.invoke("📦 Plik zapisany: ${exportFile.name}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export audio for testing: ${e.message}")
            // Don't fail the whole process if export fails
        }
    }

    /**
     * Transcribe audio file using ElevenLabs API
     */
    private fun transcribeAudio(audioFile: File) {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Transcribing audio with ElevenLabs (language: $LANGUAGE)")
                onProgress?.invoke("📡 Przygotowanie do wysłania...")

                // DETAILED FILE VALIDATION
                Log.d(TAG, "=== FILE VALIDATION ===")
                Log.d(TAG, "File path: ${audioFile.absolutePath}")
                Log.d(TAG, "File exists: ${audioFile.exists()}")
                Log.d(TAG, "File readable: ${audioFile.canRead()}")
                Log.d(TAG, "File size: ${audioFile.length()} bytes")

                if (!audioFile.exists()) {
                    throw IllegalStateException("Audio file does not exist!")
                }

                if (audioFile.length() == 0L) {
                    throw IllegalStateException("Audio file is empty (0 bytes)!")
                }

                if (!audioFile.canRead()) {
                    throw IllegalStateException("Cannot read audio file (permissions issue)!")
                }

                onProgress?.invoke("✅ Plik zweryfikowany: ${audioFile.length()} bajtów")

                // NETWORK CONNECTIVITY TEST
                Log.d(TAG, "=== NETWORK CONNECTIVITY TEST ===")
                onProgress?.invoke("🌐 Test połączenia z api.elevenlabs.io...")
                val networkOk = testNetworkConnectivity()

                if (!networkOk) {
                    Log.e(TAG, "❌ Cannot reach api.elevenlabs.io")
                    onProgress?.invoke("❌ Brak połączenia z api.elevenlabs.io")
                    throw java.net.UnknownHostException("Cannot connect to api.elevenlabs.io. Check internet connection on TV.")
                }

                Log.d(TAG, "✅ Network connectivity OK")
                onProgress?.invoke("✅ Połączenie OK")

                // EXPORT AUDIO FOR TESTING
                exportAudioForTesting(audioFile)

                // Prepare multipart request with detailed logging
                Log.d(TAG, "=== PREPARING MULTIPART REQUEST ===")

                val mediaType = "audio/wav".toMediaType()
                Log.d(TAG, "Media Type: $mediaType")

                val requestBody = audioFile.asRequestBody(mediaType)
                Log.d(TAG, "Request Body created, content length: ${audioFile.length()}")

                val filePart = MultipartBody.Part.createFormData(
                    "file",
                    audioFile.name,
                    requestBody
                )
                Log.d(TAG, "Multipart Part created:")
                Log.d(TAG, "  - Field name: file")
                Log.d(TAG, "  - File name: ${audioFile.name}")
                Log.d(TAG, "  - Content-Type: audio/wav")

                val modelId = MODEL_ID.toRequestBody("text/plain".toMediaType())
                val language = LANGUAGE.toRequestBody("text/plain".toMediaType())

                Log.d(TAG, "Model: $MODEL_ID, Language: $LANGUAGE")
                Log.d(TAG, "API Key: ${apiKey.take(20)}...")

                onProgress?.invoke("📤 Rozpoczynam upload...")

                // Call API with 180 second timeout (Polish transcription can take longer)
                Log.d(TAG, "=== SENDING REQUEST TO ELEVENLABS ===")
                val startTime = System.currentTimeMillis()

                val response = withTimeout(180_000L) {  // 3 minutes for Polish language
                    // Progress updates every 5 seconds
                    launch {
                        var elapsed = 0
                        while (true) {
                            delay(5000)
                            elapsed += 5
                            onProgress?.invoke("⏳ Czekam ${elapsed}s...")
                            Log.d(TAG, "API waiting: ${elapsed}s (max 180s)")
                        }
                    }

                    onProgress?.invoke("⏳ Oczekiwanie na odpowiedź API...")
                    Log.d(TAG, "Starting API call...")
                    withContext(Dispatchers.IO) {
                        api.transcribe(
                            apiKey = apiKey,
                            file = filePart,
                            modelId = modelId,
                            language = language
                        )
                    }
                }

                val uploadTime = (System.currentTimeMillis() - startTime) / 1000.0
                Log.d(TAG, "=== API CALL COMPLETED ===")
                Log.d(TAG, "Total time: ${uploadTime}s (upload + transcription)")

                Log.d(TAG, "=== RESPONSE RECEIVED ===")
                Log.d(TAG, "Raw response object: $response")
                Log.d(TAG, "Response class: ${response.javaClass.name}")
                Log.d(TAG, "Response.text: '${response.text}'")
                Log.d(TAG, "Response.text (length): ${response.text.length}")
                Log.d(TAG, "Response.text (isEmpty): ${response.text.isEmpty()}")
                Log.d(TAG, "Response.text (isBlank): ${response.text.isBlank()}")
                Log.d(TAG, "Response.language_code: ${response.language_code}")
                Log.d(TAG, "Response.duration_seconds: ${response.duration_seconds}")
                Log.d(TAG, "Response.words: ${response.words?.size ?: 0} words")

                if (response.words != null && response.words.isNotEmpty()) {
                    Log.d(TAG, "First word: ${response.words.first()}")
                    Log.d(TAG, "Last word: ${response.words.last()}")
                }

                // Success - extract text
                val transcribedText = response.text.trim()
                Log.d(TAG, "=== TEXT EXTRACTION ===")
                Log.d(TAG, "Transcribed text after trim: \"$transcribedText\"")
                Log.d(TAG, "Text length: ${transcribedText.length}")
                Log.d(TAG, "Detected language: ${response.language_code ?: "unknown"}")

                if (transcribedText.isNotEmpty()) {
                    Log.d(TAG, "✅ Transcription successful!")
                    onProgress?.invoke("✅ Otrzymano odpowiedź: ${transcribedText.take(50)}...")
                } else {
                    Log.w(TAG, "⚠️ WARNING: Empty transcription text!")
                    onProgress?.invoke("⚠️ Pusta transkrypcja")
                }

                Log.d(TAG, "=== CALLING CALLBACKS ===")
                // Callback on main thread
                withContext(Dispatchers.Main) {
                    if (transcribedText.isNotEmpty()) {
                        Log.d(TAG, "Calling onResult() with text: \"$transcribedText\"")
                        onResult(transcribedText)
                        Log.d(TAG, "onResult() called successfully")
                    } else {
                        Log.w(TAG, "Empty transcription result")
                        onProgress?.invoke("⚠️ Pusta odpowiedź od API")
                        onError("Nie rozpoznano mowy (pusta odpowiedź)")
                    }
                    Log.d(TAG, "Calling onEnd()")
                    onEnd()
                    Log.d(TAG, "onEnd() called successfully")
                }

            } catch (e: TimeoutCancellationException) {
                // Timeout waiting for API response (180s withTimeout)
                val elapsed = (System.currentTimeMillis() - System.currentTimeMillis()) / 1000
                Log.e(TAG, "API timeout (>180s)")
                Log.e(TAG, "This means Polish transcription took longer than 3 minutes")
                onProgress?.invoke("❌ TIMEOUT: API nie odpowiedziało w 180s")
                withContext(Dispatchers.Main) {
                    onError("Przekroczono czas oczekiwania (>3 minuty). Spróbuj krótszego nagrania.")
                    onEnd()
                }

            } catch (e: retrofit2.HttpException) {
                // HTTP error (API error response)
                val errorBody = e.response()?.errorBody()?.string()
                val errorHeaders = e.response()?.headers()?.toString()

                Log.e(TAG, "=== HTTP ERROR ===")
                Log.e(TAG, "Status Code: ${e.code()}")
                Log.e(TAG, "Error Body: $errorBody")
                Log.e(TAG, "Response Headers: $errorHeaders")
                Log.e(TAG, "Request URL: ${e.response()?.raw()?.request?.url}")

                onProgress?.invoke("❌ HTTP ${e.code()}: ${errorBody?.take(100)}")

                val errorMessage = when (e.code()) {
                    400 -> {
                        // Parse error details if available
                        val detailMsg = if (errorBody?.contains("model_id") == true) {
                            "Nieprawidłowy model ID. Sprawdź czy używasz 'scribe-v1'."
                        } else if (errorBody?.contains("file") == true) {
                            "Nieprawidłowy format pliku audio."
                        } else {
                            "Nieprawidłowe żądanie (400)"
                        }
                        Log.e(TAG, "Bad Request Details: $detailMsg")
                        detailMsg
                    }
                    401 -> "Nieprawidłowy klucz API (401 Unauthorized)"
                    429 -> "Przekroczono limit API (429 Too Many Requests)"
                    500, 502, 503 -> "Błąd serwera ElevenLabs (${e.code()})"
                    else -> "Błąd API: ${e.code()} - ${errorBody?.take(100)}"
                }

                withContext(Dispatchers.Main) {
                    onError(errorMessage)
                    onEnd()
                }

            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "=== NETWORK ERROR ===")
                Log.e(TAG, "UnknownHostException: ${e.message}")
                Log.e(TAG, "Cannot resolve hostname: api.elevenlabs.io")
                onProgress?.invoke("❌ Brak połączenia z ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Brak połączenia z internetem. Sprawdź WiFi na TV.")
                    onEnd()
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "=== TIMEOUT ERROR ===")
                Log.e(TAG, "SocketTimeoutException: ${e.message}")
                Log.e(TAG, "Network too slow or unstable")
                onProgress?.invoke("❌ Timeout połączenia")
                withContext(Dispatchers.Main) {
                    onError("Przekroczono czas oczekiwania na połączenie")
                    onEnd()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Transcription error", e)
                onProgress?.invoke("❌ Błąd: ${e.javaClass.simpleName} - ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("Błąd transkrypcji: ${e.javaClass.simpleName} - ${e.message}")
                    onEnd()
                }

            } finally {
                // Cleanup temp file
                withContext(Dispatchers.IO) {
                    try {
                        when {
                            audioFile.exists() -> {
                                val deleted = audioFile.delete()
                                when {
                                    deleted -> Log.d(TAG, "Temp audio file deleted")
                                    else -> Log.w(TAG, "Failed to delete temp file (delete returned false)")
                                }
                            }
                            else -> Log.d(TAG, "Temp file does not exist (already deleted)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete temp file", e)
                    }
                }
            }
        }
    }

    /**
     * Cleanup resources (call in onCleared or onDestroy)
     */
    fun destroy() {
        try {
            audioRecorder?.stop()
            audioRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying helper", e)
        }
    }

    /**
     * Check if API key is configured
     */
    fun isConfigured(): Boolean {
        return apiKey.isNotEmpty() && apiKey != "your_api_key_here"
    }
}
