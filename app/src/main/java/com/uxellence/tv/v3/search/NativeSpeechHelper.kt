package com.uxellence.tv.v3.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

/**
 * Native Android Speech-to-Text Helper
 *
 * Advantages over ElevenLabs:
 * - Fast: 1-3s response time (vs 60-180s)
 * - Offline capable with language pack
 * - No API costs or timeouts
 * - Real-time amplitude (RMS) callback for visualizer
 * - Proper Polish language support
 *
 * Polish Language Configuration:
 * - Locale: pl-PL
 * - Prevents phonetic English transcription
 * - Requires Polish language pack installed
 */
class NativeSpeechHelper(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "NativeSpeechHelper"
    }

    // Callbacks matching ElevenLabsSpeechHelper interface
    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onStart: (() -> Unit)? = null
    var onEnd: (() -> Unit)? = null
    var onAmplitude: ((Int) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private var hasReceivedResult = false  // Flag to prevent error from overwriting result

    /**
     * Check if Polish language is available
     * @return true if Polish is installed, false otherwise
     */
    fun isPolishLanguageAvailable(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")

        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, 0)

        val available = activities.isNotEmpty()
        Log.d(TAG, "Polish language pack available: $available")

        return available
    }

    /**
     * Start speech recognition with Polish language configuration
     */
    fun startRecognition() {
        if (isRecording) {
            Log.w(TAG, "Already recording, ignoring start request")
            return
        }

        // Reset result flag on new recognition
        hasReceivedResult = false

        // Check if SpeechRecognizer is available
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            val error = "Speech recognition not available on this device"
            Log.e(TAG, error)
            onError?.invoke(error)
            return
        }

        // Check if Polish is available
        if (!isPolishLanguageAvailable()) {
            val error = "Polish language pack not installed. Please install Polish voice recognition."
            Log.e(TAG, error)
            onError?.invoke(error)
            return
        }

        try {
            // Create speech recognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                        isRecording = true
                        coroutineScope.launch(Dispatchers.Main) {
                            onStart?.invoke()
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Convert RMS in dB to amplitude scale 0-32767
                        // RMS ranges from -50 to +10 dB (60 dB total range for better sensitivity at distance)
                        val normalizedRms = (rmsdB + 50f).coerceIn(0f, 60f) / 60f
                        val amplitude = (normalizedRms * 32767).toInt()

                        // Invoke on Main thread for Compose recomposition
                        coroutineScope.launch(Dispatchers.Main) {
                            onAmplitude?.invoke(amplitude)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        Log.d(TAG, "Buffer received: ${buffer?.size ?: 0} bytes")
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech")
                    }

                    override fun onError(error: Int) {
                        // Ignore errors if we already received a successful result
                        // (prevents auto-restart errors from overwriting the result)
                        if (hasReceivedResult) {
                            Log.d(TAG, "Ignoring error (code: $error) - result already received")
                            return
                        }

                        val errorMessage = getErrorMessage(error)
                        Log.e(TAG, "Recognition error: $errorMessage (code: $error)")

                        isRecording = false
                        coroutineScope.launch(Dispatchers.Main) {
                            onEnd?.invoke()
                            onError?.invoke(errorMessage)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcription = matches?.firstOrNull() ?: ""

                        Log.d(TAG, "Recognition results: $transcription")
                        Log.d(TAG, "All matches: ${matches?.joinToString(", ")}")

                        isRecording = false

                        // Mark that we received a result (prevents auto-restart errors from overwriting)
                        if (transcription.isNotEmpty()) {
                            hasReceivedResult = true
                        }

                        coroutineScope.launch(Dispatchers.Main) {
                            // Invoke result or error callback
                            if (transcription.isNotEmpty()) {
                                Log.d(TAG, "onResult callback exists: ${onResult != null}")
                                Log.d(TAG, "Invoking onResult with: \"$transcription\"")
                                onResult?.invoke(transcription)
                                Log.d(TAG, "onResult invoked successfully")

                                // Destroy recognizer after successful result (prevents auto-restart)
                                try {
                                    speechRecognizer?.destroy()
                                    speechRecognizer = null
                                    Log.d(TAG, "SpeechRecognizer destroyed after successful result")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error destroying SpeechRecognizer", e)
                                }
                            } else {
                                // No transcription - invoke onEnd then error
                                Log.d(TAG, "No transcription - invoking error")
                                onEnd?.invoke()
                                onError?.invoke("No transcription returned")
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        Log.d(TAG, "Partial results: ${matches?.firstOrNull()}")
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        Log.d(TAG, "Event: $eventType")
                    }
                })
            }

            // Configure recognition intent with Polish language
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // Core configuration
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

                // Polish language configuration (CRITICAL for avoiding phonetic English)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)

                // Additional settings
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) // Enable partial results
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // Get top 5 results
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // Online for better accuracy

                // Calling package
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            Log.d(TAG, "Starting recognition with Polish (pl-PL) configuration")
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recognition", e)
            isRecording = false
            coroutineScope.launch(Dispatchers.Main) {
                onError?.invoke("Failed to start: ${e.message}")
            }
        }
    }

    /**
     * Stop speech recognition
     */
    fun stopRecognition() {
        Log.d(TAG, "Stopping recognition")

        try {
            speechRecognizer?.stopListening()
            isRecording = false

            coroutineScope.launch(Dispatchers.Main) {
                onEnd?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognition", e)
        }
    }

    /**
     * Cancel speech recognition (no results callback)
     */
    fun cancelRecognition() {
        Log.d(TAG, "Cancelling recognition")

        try {
            speechRecognizer?.cancel()
            isRecording = false

            coroutineScope.launch(Dispatchers.Main) {
                onEnd?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recognition", e)
        }
    }

    /**
     * Release resources
     */
    fun release() {
        Log.d(TAG, "Releasing resources")

        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }

    /**
     * Convert error code to human-readable message
     */
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input (timeout)"
            else -> "Unknown error (code: $error)"
        }
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording
}
