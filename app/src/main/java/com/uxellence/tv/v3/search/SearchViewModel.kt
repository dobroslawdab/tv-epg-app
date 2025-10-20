package com.uxellence.tv.v3.search

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uxellence.tv.v3.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private var speechRecognitionHelper: NativeSpeechHelper? = null

    fun updateQuery(query: String) {
        _searchState.value = _searchState.value.copy(currentQuery = query)
    }

    fun toggleInternetSearch() {
        _searchState.value = _searchState.value.copy(
            useInternetSearch = !_searchState.value.useInternetSearch
        )
    }

    fun setRecording(isRecording: Boolean) {
        _searchState.value = _searchState.value.copy(isRecording = isRecording)
    }

    fun setInputMode(mode: SearchInputMode) {
        _searchState.value = _searchState.value.copy(inputMode = mode)
    }

    fun sendQuery(query: String = _searchState.value.currentQuery) {
        if (query.trim().isEmpty() || _searchState.value.isLoading) return

        viewModelScope.launch {
            try {
                _searchState.value = _searchState.value.copy(
                    isLoading = true,
                    error = null
                )

                // Create new conversation turn
                val newTurn = ConversationTurn(
                    id = System.currentTimeMillis(),
                    query = query.trim(),
                    isLoading = true
                )

                // Add the turn to history (new on top)
                _searchState.value = _searchState.value.copy(
                    conversationHistory = listOf(newTurn) + _searchState.value.conversationHistory,
                    currentQuery = ""
                )

                // Call Gemini API
                val response = GeminiService.getGeminiResponse(
                    query.trim(),
                    _searchState.value.useInternetSearch
                )

                // Update the turn with response
                val updatedTurn = newTurn.copy(
                    answer = response.answer,
                    recommendations = response.recommendations,
                    isLoading = false
                )

                // Update history
                val updatedHistory = _searchState.value.conversationHistory.map { turn ->
                    if (turn.id == newTurn.id) updatedTurn else turn
                }

                _searchState.value = _searchState.value.copy(
                    conversationHistory = updatedHistory,
                    isLoading = false
                )

                // Start loading posters progressively
                loadPostersForRecommendations(updatedTurn)

            } catch (e: Exception) {
                Log.e("SearchViewModel", "Error sending query: ${e.message}")

                // Update the turn with error (new turn is first now)
                val errorTurn = _searchState.value.conversationHistory.first().copy(
                    isLoading = false,
                    error = e.message ?: "Wystąpił nieznany błąd"
                )

                val updatedHistory = listOf(errorTurn) + _searchState.value.conversationHistory.drop(1)

                _searchState.value = _searchState.value.copy(
                    conversationHistory = updatedHistory,
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadPostersForRecommendations(turn: ConversationTurn) {
        if (turn.recommendations.isNullOrEmpty()) return

        viewModelScope.launch {
            val updatedRecommendations = turn.recommendations.map { recommendation ->
                if (recommendation.posterUrl == null) {
                    try {
                        val posterUrl = GeminiService.fetchPosterFromTMDB(
                            recommendation.title,
                            recommendation.releaseYear
                        )
                        recommendation.copy(posterUrl = posterUrl)
                    } catch (e: Exception) {
                        Log.w("SearchViewModel", "Failed to load poster for ${recommendation.title}: ${e.message}")
                        recommendation
                    }
                } else {
                    recommendation
                }
            }

            // Update the conversation history with new posters
            val updatedHistory = _searchState.value.conversationHistory.map { historyTurn ->
                if (historyTurn.id == turn.id) {
                    historyTurn.copy(recommendations = updatedRecommendations)
                } else {
                    historyTurn
                }
            }

            _searchState.value = _searchState.value.copy(conversationHistory = updatedHistory)
        }
    }

    fun clearError() {
        _searchState.value = _searchState.value.copy(error = null)
    }

    fun clearHistory() {
        _searchState.value = _searchState.value.copy(conversationHistory = emptyList())
    }

    fun initializeSpeechRecognition(context: Context) {
        speechRecognitionHelper = NativeSpeechHelper(
            context = context,
            coroutineScope = viewModelScope
        ).apply {
            onResult = { recognizedText ->
                Log.d("SearchViewModel", "Native Speech result: $recognizedText")
                _searchState.value = _searchState.value.copy(
                    currentQuery = recognizedText,
                    isRecording = false
                )
                // Auto-send the query after voice recognition
                sendQuery(recognizedText)
            }
            onError = { errorMessage ->
                Log.e("SearchViewModel", "Native Speech error: $errorMessage")
                _searchState.value = _searchState.value.copy(
                    isRecording = false,
                    error = errorMessage
                )
            }
            onStart = {
                Log.d("SearchViewModel", "Native Speech recording started")
                _searchState.value = _searchState.value.copy(
                    isRecording = true,
                    currentQuery = ""
                )
            }
            onEnd = {
                Log.d("SearchViewModel", "Native Speech recording ended")
                _searchState.value = _searchState.value.copy(isRecording = false)
            }
        }
    }

    fun toggleVoiceRecording() {
        if (_searchState.value.isRecording) {
            stopVoiceRecording()
        } else {
            startVoiceRecording()
        }
    }

    private fun startVoiceRecording() {
        speechRecognitionHelper?.startRecognition()
    }

    private fun stopVoiceRecording() {
        speechRecognitionHelper?.stopRecognition()
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognitionHelper?.release()
    }
}