package com.uxellence.tv.v3.search

import android.util.Log
import com.uxellence.tv.v3.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

object GeminiService {
    private const val TAG = "GeminiService"
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private const val TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500"
    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(Logging) {
            logger = Logger.ANDROID
            level = LogLevel.INFO
        }
    }

    @Serializable
    data class TMDBSearchResponse(
        val results: List<TMDBResult>
    )

    @Serializable
    data class TMDBResult(
        val poster_path: String? = null
    )

    @Serializable
    data class GeminiRequestBody(
        val contents: List<GeminiContent>,
        val systemInstruction: GeminiContent? = null,
        val generationConfig: GeminiConfig? = null,
        val tools: List<GeminiTool>? = null
    )

    @Serializable
    data class GeminiContent(
        val parts: List<GeminiPart>
    )

    @Serializable
    data class GeminiPart(
        val text: String
    )

    @Serializable
    data class GeminiConfig(
        val temperature: Double = 0.7,
        val responseMimeType: String? = null,
        val responseSchema: JsonObject? = null
    )

    @Serializable
    data class GeminiTool(
        val googleSearch: JsonObject = JsonObject(emptyMap())
    )

    @Serializable
    data class GeminiResponseBody(
        val candidates: List<GeminiCandidate>? = null
    )

    @Serializable
    data class GeminiCandidate(
        val content: GeminiContent? = null
    )

    @Serializable
    data class GeminiErrorResponse(
        val error: GeminiError? = null
    )

    @Serializable
    data class GeminiError(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null
    )

    suspend fun fetchPosterFromTMDB(title: String, year: String): String {
        delay(350) // Rate limiting like in original
        val placeholderUrl = "https://picsum.photos/400/600?random=${title.hashCode()}"

        try {
            // Try movie search first
            var posterPath = searchTMDB("movie", title, year)

            // If no movie found, try TV show
            if (posterPath == null) {
                posterPath = searchTMDB("tv", title, year)
            }

            return posterPath ?: placeholderUrl
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch poster for \"$title\": ${e.message}")
            return placeholderUrl
        }
    }

    private suspend fun searchTMDB(endpoint: String, title: String, year: String): String? {
        try {
            val yearParam = if (endpoint == "movie") "primary_release_year" else "first_air_date_year"
            val response: TMDBSearchResponse = client.get("$TMDB_BASE_URL/search/$endpoint") {
                parameter("api_key", BuildConfig.TMDB_API_KEY)
                parameter("query", title)
                parameter(yearParam, year)
                parameter("language", "pl-PL")
            }.body()

            val posterPath = response.results.firstOrNull()?.poster_path
            return if (posterPath != null) "$TMDB_IMAGE_BASE_URL$posterPath" else null
        } catch (e: Exception) {
            Log.e(TAG, "TMDB API error for $endpoint \"$title\": ${e.message}")
            return null
        }
    }

    suspend fun getGeminiResponse(userQuery: String, useInternetSearch: Boolean): GeminiResponse {
        // Try latest model first, fallback to stable if needed
        var lastError: Exception? = null
        val modelsToTry = listOf("gemini-2.5-flash", "gemini-1.5-flash")

        for (model in modelsToTry) {
            try {
                Log.d(TAG, "Trying model: $model")
                return tryGeminiRequest(userQuery, useInternetSearch, model)
            } catch (e: Exception) {
                Log.w(TAG, "Model $model failed: ${e.message}")
                lastError = e
                // Continue to next model
            }
        }

        // If all models failed, throw the last error
        throw lastError ?: Exception("All Gemini models failed")
    }

    private suspend fun tryGeminiRequest(userQuery: String, useInternetSearch: Boolean, model: String): GeminiResponse {
        try {
            val systemInstruction = if (useInternetSearch) {
                "Jesteś zaawansowanym asystentem AI Gemini 2.0 specjalizującym się w rekomendowaniu filmów i seriali. Użyj narzędzia wyszukiwania w internecie, aby znaleźć najświeższe informacje, recenzje i trendy. Analizuj kontekst kulturowy i preferencje użytkownika. Odpowiedz konwersacyjnie w języku polskim, uwzględniając najnowsze premiery i aktualne rankingi. Na końcu odpowiedzi ZAWSZE umieść kompletny obiekt JSON w bloku markdown ```json ... ```, który zawiera pola 'answer' (twoja szczegółowa odpowiedź) i 'recommendations' (lista do 10 najlepszych rekomendacji z 'title' i 'releaseYear')."
            } else {
                "Jesteś zaawansowanym ekspertem AI Gemini 2.0 w dziedzinie kinematografii i telewizji. Wykorzystuj swoją rozległą wiedzę o filmach, serialach, reżyserach, aktorach i trendach branżowych. Analizuj preferencje użytkownika i dostarczaj spersonalizowane rekomendacje. Wszystkie zapytania interpretuj w kontekście rozrywki audiowizualnej. Udzielaj wyczerpujących, konwersacyjnych odpowiedzi w języku polskim z dokładnymi rekomendacjami uporządkowanymi według trafności. Dla każdej rekomendacji podaj TYLKO tytuł i rok wydania."
            }

            val requestBody = GeminiRequestBody(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(userQuery)
                        )
                    )
                ),
                systemInstruction = GeminiContent(
                    parts = listOf(
                        GeminiPart(systemInstruction)
                    )
                ),
                generationConfig = if (useInternetSearch) {
                    GeminiConfig(
                        temperature = 0.7
                    )
                } else {
                    GeminiConfig(
                        temperature = 0.7,
                        responseMimeType = "application/json",
                        responseSchema = getResponseSchema()
                    )
                },
                tools = if (useInternetSearch) {
                    listOf(GeminiTool())
                } else null
            )

            // Log request details for debugging
            Log.d(TAG, "Making request to: $GEMINI_BASE_URL/models/$model:generateContent")
            Log.d(TAG, "Request body: ${Json.encodeToString(GeminiRequestBody.serializer(), requestBody)}")
            Log.d(TAG, "Using internet search: $useInternetSearch")

            val httpResponse = client.post("$GEMINI_BASE_URL/models/$model:generateContent") {
                header("Content-Type", "application/json")
                parameter("key", BuildConfig.GEMINI_API_KEY)
                setBody(requestBody)
            }

            // Log raw response for debugging
            val rawResponseText = httpResponse.bodyAsText()
            Log.d(TAG, "HTTP Status: ${httpResponse.status}")
            Log.d(TAG, "Raw Gemini response: $rawResponseText")

            // Check if this is an error response
            if (rawResponseText.contains("\"error\"")) {
                try {
                    val errorResponse = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiErrorResponse>(rawResponseText)
                    val errorMessage = errorResponse.error?.message ?: "Unknown API error"
                    val errorCode = errorResponse.error?.code ?: 0
                    Log.e(TAG, "Gemini API error $errorCode: $errorMessage")
                    throw Exception("API Error $errorCode: $errorMessage")
                } catch (parseError: Exception) {
                    Log.e(TAG, "Could not parse error response: ${parseError.message}")
                    throw Exception("API returned error but could not parse it: $rawResponseText")
                }
            }

            // Try to parse successful response
            val response = try {
                Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponseBody>(rawResponseText)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse Gemini response: ${e.message}")
                Log.e(TAG, "Raw response: $rawResponseText")
                throw Exception("Invalid response format from Gemini API: ${e.message}")
            }

            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (responseText.isNullOrBlank()) {
                Log.e(TAG, "Empty response from Gemini API")
                Log.e(TAG, "Full response: $rawResponseText")
                throw Exception("Empty response from Gemini API")
            }

            return parseGeminiResponse(responseText, useInternetSearch)

        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}")
            throw Exception("Nie udało się uzyskać odpowiedzi od AI: ${e.message}")
        }
    }

    private fun getResponseSchema(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("answer") {
                    put("type", "string")
                    put("description", "Zwięzła, pomocna i przyjazna odpowiedź na zapytanie użytkownika, zawsze w kontekście filmów lub seriali.")
                }
                putJsonObject("recommendations") {
                    put("type", "array")
                    put("description", "Lista do 10 trafnych rekomendacji filmów lub seriali.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") {
                                put("type", "string")
                                put("description", "Tytuł filmu lub serialu.")
                            }
                            putJsonObject("releaseYear") {
                                put("type", "string")
                                put("description", "Rok wydania filmu lub serialu (np. '2023').")
                            }
                        }
                        putJsonArray("required") {
                            add("title")
                            add("releaseYear")
                        }
                    }
                }
            }
            putJsonArray("required") {
                add("answer")
                add("recommendations")
            }
        }
    }

    private fun parseGeminiResponse(responseText: String, useInternetSearch: Boolean): GeminiResponse {
        val jsonText = if (useInternetSearch) {
            // Extract JSON from markdown block
            val regex = "```json\\s*([\\s\\S]*?)\\s*```".toRegex()
            val match = regex.find(responseText)
            match?.groupValues?.get(1) ?: responseText
        } else {
            responseText
        }

        return try {
            Log.d(TAG, "Parsing JSON: $jsonText")
            val apiResponse = Json.decodeFromString<ApiGeminiResponse>(jsonText)

            // Convert API response to app model
            GeminiResponse(
                answer = apiResponse.answer,
                recommendations = apiResponse.recommendations.map { apiRec ->
                    MediaRecommendation(
                        title = apiRec.title,
                        releaseYear = apiRec.releaseYear,
                        posterUrl = null // Will be loaded separately
                    )
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON response: ${e.message}")
            Log.w(TAG, "Raw JSON: $jsonText")
            // Fallback response
            GeminiResponse(
                answer = if (useInternetSearch) responseText else "Przepraszam, wystąpił problem z przetworzeniem odpowiedzi.",
                recommendations = emptyList()
            )
        }
    }
}