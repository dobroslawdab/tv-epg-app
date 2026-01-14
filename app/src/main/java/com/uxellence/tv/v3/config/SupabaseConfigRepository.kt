package com.uxellence.tv.v3.config

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Supabase REST API client for fetching app configuration
 */
class SupabaseConfigRepository(private val context: Context) {

    companion object {
        private const val TAG = "SupabaseConfig"

        // Supabase credentials
        private const val SUPABASE_URL = "https://kexrkaqxoadxugnnbnjh.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzNTkyMTksImV4cCI6MjA4MDkzNTIxOX0.o1mhA0iaEAcK9LftmYwt_KKvEf0zlcSuBLaYd7B_cuo"
        private const val TABLE_NAME = "app_config"
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Fetch the latest config from Supabase
     * Returns the most recent config row (ordered by updated_at desc)
     */
    suspend fun fetchConfig(): Result<AppConfig> {
        return try {
            Log.d(TAG, "Fetching config from Supabase...")

            val response: List<AppConfig> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("limit", "1")
                parameter("order", "updated_at.desc")
            }.body()

            val config = response.firstOrNull() ?: AppConfig.DEFAULT
            Log.d(TAG, "Config fetched successfully: version=${config.version}")
            Result.success(config)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch config", e)
            Result.failure(e)
        }
    }

    /**
     * Close the HTTP client when done
     */
    fun close() {
        client.close()
    }
}
