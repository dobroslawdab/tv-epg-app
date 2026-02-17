package com.uxellence.tv.v3.update

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Repository for fetching app update information from Supabase
 *
 * Uses the same Supabase instance as other repositories
 */
class UpdateRepository {

    companion object {
        private const val TAG = "UpdateRepository"

        // Same Supabase credentials as SupabaseConfigRepository
        private const val SUPABASE_URL = "https://kexrkaqxoadxugnnbnjh.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzNTkyMTksImV4cCI6MjA4MDkzNTIxOX0.o1mhA0iaEAcK9LftmYwt_KKvEf0zlcSuBLaYd7B_cuo"
        private const val TABLE_NAME = "app_updates"
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
     * Fetch the latest update info from Supabase
     * Returns the most recent version (ordered by version_code desc)
     */
    suspend fun fetchLatestVersion(): Result<AppUpdateInfo?> {
        return try {
            Log.d(TAG, "Checking for updates from Supabase...")

            val response: List<AppUpdateInfo> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("limit", "1")
                parameter("order", "version_code.desc")
            }.body()

            val updateInfo = response.firstOrNull()
            if (updateInfo != null) {
                Log.d(TAG, "Latest version: ${updateInfo.versionName} (code: ${updateInfo.versionCode})")
            } else {
                Log.d(TAG, "No update info found in Supabase")
            }
            Result.success(updateInfo)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
