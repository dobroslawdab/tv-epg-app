package com.uxellence.tv.v3.repository

import android.util.Log
import com.uxellence.tv.v3.VodSlideData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data model for odkrywaj_slider table
 */
@Serializable
data class OdkrywajSliderItem(
    val id: String,
    @SerialName("slot_order") val slotOrder: Int,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_movie_id") val sourceMovieId: String? = null,
    @SerialName("source_vod_id") val sourceVodId: String? = null,
    val title: String,
    @SerialName("title_max_lines") val titleMaxLines: Int = 2,
    val description: String? = null,
    @SerialName("show_metadata") val showMetadata: Boolean = true,
    @SerialName("metadata_text") val metadataText: String? = null,
    @SerialName("backdrop_url") val backdropUrl: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("button_type") val buttonType: String = "rent",
    @SerialName("button_custom_text") val buttonCustomText: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

/**
 * Repository for ODKRYWAJ slider data from Supabase
 */
object SupabaseOdkrywajRepository {

    private const val TAG = "SupabaseOdkrywaj"

    // Supabase credentials (same as SupabaseMoviesRepository)
    private const val SUPABASE_URL = "https://kexrkaqxoadxugnnbnjh.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzNTkyMTksImV4cCI6MjA4MDkzNTIxOX0.o1mhA0iaEAcK9LftmYwt_KKvEf0zlcSuBLaYd7B_cuo"
    private const val TABLE_NAME = "odkrywaj_slider"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Fetch all active ODKRYWAJ slider items, ordered by slot_order
     */
    suspend fun fetchOdkrywajSlider(): List<OdkrywajSliderItem> {
        return try {
            Log.d(TAG, "Fetching ODKRYWAJ slider items from Supabase...")

            val response: List<OdkrywajSliderItem> = client.get("$SUPABASE_URL/rest/v1/$TABLE_NAME") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("is_active", "eq.true")
                parameter("order", "slot_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} ODKRYWAJ slider items")
            response

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch ODKRYWAJ slider", e)
            emptyList()
        }
    }

    /**
     * Get button text based on button_type
     * For "rent" - shows "Wypożycz: price / 48h" (like KINO_PLAY)
     * For "watch" - shows "Oglądaj"
     * For "custom" - shows custom text (no icon)
     */
    fun getButtonText(item: OdkrywajSliderItem): String {
        return when (item.buttonType) {
            "rent" -> {
                // Format like KINO_PLAY: "Wypożycz: cena / 48h"
                if (!item.buttonCustomText.isNullOrBlank()) {
                    val priceText = item.buttonCustomText
                    // Add "/ 48h" if not already included
                    if (priceText.contains("/")) {
                        "Wypożycz: $priceText"
                    } else {
                        "Wypożycz: $priceText / 48h"
                    }
                } else {
                    "Wypożycz"
                }
            }
            "watch" -> "Oglądaj"
            "custom" -> item.buttonCustomText ?: ""
            else -> ""
        }
    }

    /**
     * Check if item is from Kino Play (movie source)
     */
    fun isKinoPlay(item: OdkrywajSliderItem): Boolean {
        return item.sourceType == "movie"
    }

    /**
     * Check if item is from VOD (video on demand)
     */
    fun isVod(item: OdkrywajSliderItem): Boolean {
        return item.sourceType == "vod_json"
    }
}

/**
 * Convert OdkrywajSliderItem to VodSlideData for VodHeroSliderV2
 */
fun OdkrywajSliderItem.toVodSlideData(): VodSlideData = VodSlideData(
    title = title,
    genre = metadataText?.split("|")?.getOrNull(1)?.trim() ?: "",
    duration = metadataText?.split("|")?.getOrNull(2)?.trim() ?: "",
    year = metadataText?.split("|")?.getOrNull(0)?.trim() ?: "",
    country = "",
    ageRating = "",
    description = description ?: "",
    price = SupabaseOdkrywajRepository.getButtonText(this),
    backgroundUrl = backdropUrl,
    posterUrl = backdropUrl,
    youtubeUrl = youtubeUrl,
    channelLogoUrl = logoUrl,
    showKrrit = sourceType in listOf("movie", "vod_json"), // Show KRRIT for movie/vod content
    isKinoPlay = sourceType == "movie" // True for KINO PLAY movies
)
