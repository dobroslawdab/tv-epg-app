package com.uxellence.tv.v3.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStart: () -> Unit,
    private val onEnd: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val tag = "SpeechRecognition"

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // RMS value changes - could be used for volume indicator
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            Log.d(tag, "End of speech")
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Błąd audio"
                SpeechRecognizer.ERROR_CLIENT -> "Błąd klienta"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Brak uprawnień"
                SpeechRecognizer.ERROR_NETWORK -> "Błąd sieci"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout sieci"
                SpeechRecognizer.ERROR_NO_MATCH -> "Nie rozpoznano mowy"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Rozpoznawanie zajęte"
                SpeechRecognizer.ERROR_SERVER -> "Błąd serwera"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout mowy"
                else -> "Nieznany błąd: $error"
            }
            Log.e(tag, "Speech recognition error: $errorMessage")
            onError(errorMessage)
            onEnd()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val recognizedText = matches[0]
                Log.d(tag, "Recognition result: $recognizedText")
                onResult(recognizedText)
            }
            onEnd()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partialText = matches[0]
                Log.d(tag, "Partial result: $partialText")
                // Could be used for real-time text updates
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "Speech recognition not available on this device")
            onError("Rozpoznawanie mowy nie jest dostępne na tym urządzeniu")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pl-PL")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "pl-PL")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            }

            Log.d(tag, "Starting speech recognition with Polish language settings")
            onStart()
            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(tag, "Error starting speech recognition: ${e.message}")
            onError("Błąd uruchamiania rozpoznawania mowy: ${e.message}")
            onEnd()
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping speech recognition: ${e.message}")
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(tag, "Error destroying speech recognition: ${e.message}")
        }
    }
}