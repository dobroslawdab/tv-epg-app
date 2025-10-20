package com.uxellence.tv.v3.search

import kotlinx.serialization.Serializable

/**
 * Models for AI Search functionality
 * Based on Gemini TV Assistant React app
 */

data class MediaRecommendation(
    val title: String,
    val releaseYear: String,
    val posterUrl: String? = null
)

@Serializable
data class ApiRecommendation(
    val title: String,
    val releaseYear: String
)

@Serializable
data class ApiGeminiResponse(
    val answer: String,
    val recommendations: List<ApiRecommendation>
)

data class GeminiResponse(
    val answer: String,
    val recommendations: List<MediaRecommendation>
)

data class ConversationTurn(
    val id: Long,
    val query: String,
    val answer: String? = null,
    val recommendations: List<MediaRecommendation>? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class SearchInputMode {
    TEXT, VOICE
}

data class SearchState(
    val conversationHistory: List<ConversationTurn> = emptyList(),
    val currentQuery: String = "",
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val inputMode: SearchInputMode = SearchInputMode.TEXT,
    val useInternetSearch: Boolean = false,
    val error: String? = null
)