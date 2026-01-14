package com.uxellence.tv.v3.repository

import android.util.Log
import com.uxellence.tv.v3.model.SupabaseMovie
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Repository do pobierania filmów z Supabase REST API
 * Single Source of Truth - wszystkie dane z tabeli movies
 */
object SupabaseMoviesRepository {

    private const val TAG = "SupabaseMovies"

    // Supabase credentials
    private const val SUPABASE_URL = "https://kexrkaqxoadxugnnbnjh.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzNTkyMTksImV4cCI6MjA4MDkzNTIxOX0.o1mhA0iaEAcK9LftmYwt_KKvEf0zlcSuBLaYd7B_cuo"
    private const val TABLE_NAME = "movies"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Pobierz wszystkie aktywne filmy
     */
    suspend fun fetchAllMovies(): List<SupabaseMovie> {
        return try {
            Log.d(TAG, "Fetching all movies from Supabase...")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_active", "eq.true")
                parameter("order", "display_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} movies")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch movies", e)
            emptyList()
        }
    }

    /**
     * Pobierz filmy oznaczone jako Top 10
     * Sortowane po top10_order (1-10)
     */
    suspend fun fetchTop10(): List<SupabaseMovie> {
        return try {
            Log.d(TAG, "Fetching Top 10 movies...")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_top10", "eq.true")
                parameter("is_active", "eq.true")
                parameter("order", "top10_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} Top 10 movies")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Top 10", e)
            emptyList()
        }
    }

    /**
     * Pobierz najnowsze filmy (sortowane po created_at DESC)
     */
    suspend fun fetchNewest(limit: Int = 20): List<SupabaseMovie> {
        return try {
            Log.d(TAG, "Fetching newest $limit movies...")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_active", "eq.true")
                parameter("order", "created_at.desc")
                parameter("limit", limit.toString())
            }.body()

            Log.d(TAG, "Fetched ${response.size} newest movies")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch newest movies", e)
            emptyList()
        }
    }

    /**
     * Pobierz filmy po gatunku (partial match - ILIKE)
     */
    suspend fun fetchByGenre(genre: String): List<SupabaseMovie> {
        return try {
            Log.d(TAG, "Fetching movies by genre: $genre")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_active", "eq.true")
                parameter("genre", "ilike.*$genre*")
                parameter("order", "display_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} movies for genre: $genre")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch movies by genre", e)
            emptyList()
        }
    }

    /**
     * Pobierz pojedynczy film po ID
     */
    suspend fun fetchMovieById(movieId: String): SupabaseMovie? {
        return try {
            Log.d(TAG, "Fetching movie by ID: $movieId")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("id", "eq.$movieId")
            }.body()

            response.firstOrNull()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch movie by ID", e)
            null
        }
    }

    /**
     * Pobierz filmy do slidera (z tabeli movies, flaga is_slider=true)
     * Sortowane po slider_order (1-10)
     * Single Source of Truth - nie ma już osobnej tabeli slider_movies
     */
    suspend fun fetchSliderMovies(): List<SupabaseMovie> {
        return try {
            Log.d(TAG, "Fetching slider movies from movies table...")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_slider", "eq.true")
                parameter("is_active", "eq.true")
                parameter("order", "slider_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} slider movies")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch slider movies", e)
            emptyList()
        }
    }

    /**
     * Pobierz polecane filmy (is_recommended=true)
     */
    suspend fun fetchRecommended(): List<SupabaseMovie> {
        return try {
            Log.d(TAG, "Fetching recommended movies...")

            val response: List<SupabaseMovie> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_recommended", "eq.true")
                parameter("is_active", "eq.true")
                parameter("order", "display_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} recommended movies")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch recommended movies", e)
            emptyList()
        }
    }
}
