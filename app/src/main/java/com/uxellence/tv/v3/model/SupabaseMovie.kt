package com.uxellence.tv.v3.model

import com.uxellence.tv.v3.VodSlideData
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.serialization.Serializable

/**
 * Model danych filmu z Supabase (tabela: movies)
 * Single Source of Truth - wszystkie dane o filmach w jednym miejscu
 */
@Serializable
data class SupabaseMovie(
    val id: String,
    val title: String,
    val genre: String? = null,
    val description: String? = null,
    val short_description: String? = null,
    val poster_url: String? = null,
    val backdrop_url: String? = null,
    val logo_url: String? = null,
    val runtime: String? = null,
    val release_year: String? = null,
    val price: Int? = null,
    val is_top10: Boolean = false,
    val top10_order: Int? = null,
    val display_order: Int? = null,
    val created_at: String? = null,
    val is_active: Boolean = true,
    val tmdb_id: Int? = null,
    val vote_average: Double? = null,
    // Nowe pola dla sekcji
    val is_slider: Boolean = false,
    val slider_order: Int? = null,
    val is_recommended: Boolean = false,
    val youtube_url: String? = null
)

/**
 * Konwersja do VodContent (dla kanałów VOD - Polecane, Top 10, gatunki)
 */
fun SupabaseMovie.toVodContent(): VodContent = VodContent(
    id = "supabase_$id",
    title = title,
    description = short_description ?: description ?: "",
    category = genre ?: "",
    imageUrl = poster_url ?: "",
    channelLogoUrl = logo_url ?: "",
    link = "", // Filmy płatne - brak bezpośredniego linku
    price = price?.let { "${it} zł/48h" }
)

/**
 * Konwersja do VodSlideData (dla Hero Slidera)
 */
fun SupabaseMovie.toVodSlideData(): VodSlideData = VodSlideData(
    title = title,
    genre = genre ?: "",
    duration = runtime ?: "",
    year = release_year?.let { "$it r." } ?: "",
    country = "USA",
    ageRating = "13 lat",
    description = short_description ?: description ?: "",
    price = price?.let { "$it zł/48h" } ?: "24 zł/48h",
    backgroundUrl = backdrop_url ?: "",
    posterUrl = poster_url ?: "",
    youtubeUrl = youtube_url
)
