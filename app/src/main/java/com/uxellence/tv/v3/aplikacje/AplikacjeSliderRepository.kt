package com.uxellence.tv.v3.aplikacje

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AplikacjeSliderItem(
    @SerialName("id") val id: String = "",
    @SerialName("slot_order") val slotOrder: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("backdrop_url") val backdropUrl: String = "",
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("button_type") val buttonType: String = "open_app",
    @SerialName("button_custom_text") val buttonCustomText: String = "",
    @SerialName("is_active") val isActive: Boolean = true
) {
    /** Tekst CTA buttona zgodnie z button_type (open_app/install_app/custom). */
    fun resolveButtonLabel(): String = when (buttonType) {
        "open_app" -> "Otwórz aplikację"
        "install_app" -> "Zainstaluj aplikację"
        "custom" -> buttonCustomText.ifBlank { "Otwórz aplikację" }
        else -> buttonCustomText.ifBlank { "Otwórz aplikację" }
    }
}

/**
 * Pobiera banery hero slidera APLIKACJE z Supabase (tabela `aplikacje_slider`).
 * Wzorowany na UpdateRepository.
 */
class AplikacjeSliderRepository {

    companion object {
        private const val TAG = "AplikacjeSliderRepo"
        private const val SUPABASE_URL = "https://kexrkaqxoadxugnnbnjh.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzNTkyMTksImV4cCI6MjA4MDkzNTIxOX0.o1mhA0iaEAcK9LftmYwt_KKvEf0zlcSuBLaYd7B_cuo"
        private const val TABLE_NAME = "aplikacje_slider"
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun fetchActiveBanners(): Result<List<AplikacjeSliderItem>> {
        return try {
            Log.d(TAG, "Fetching active aplikacje_slider banners...")
            val response: List<AplikacjeSliderItem> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_active", "eq.true")
                parameter("order", "slot_order.asc")
            }.body()
            Log.d(TAG, "Fetched ${response.size} active banners")
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch aplikacje_slider banners", e)
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
