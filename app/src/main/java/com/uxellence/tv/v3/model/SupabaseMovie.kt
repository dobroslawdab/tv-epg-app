package com.uxellence.tv.v3.model

import com.uxellence.tv.v3.VodSlideData
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    val youtube_url: String? = null,
    // Nowe pola dla MovieDetailScreen (Figma design)
    val country: String? = null,           // Kraj produkcji (np. "Polska")
    val age_rating: String? = null,        // Wiek (np. "7 lat")
    val filmweb_rating: Double? = null,    // Ocena Filmweb (np. 6.7)
    val audio_languages: String? = null,   // Dźwięk: "angielski | polski | hiszpański"
    val subtitle_languages: String? = null,// Napisy: "angielski | polski"
    val director: String? = null,          // Reżyser: "Álex Pina"
    val cast: String? = null,              // (legacy) Obsada — zwykle null w bazie
    val tmdb_cast: JsonElement? = null,    // Obsada z TMDB jako JSON array [{id, name, character, ...}]
    val selected_logo_url: String? = null  // Logo URL to display instead of title on slider (when unfocused)
)

/**
 * Konwersja do VodContent (dla kanałów VOD - Polecane, Top 10, gatunki)
 */
fun SupabaseMovie.toVodContent(): VodContent = VodContent(
    id = "supabase_$id",
    title = title,
    // Niektóre filmy mają w short_description tylko cenę (np. "19 zł", "19 zł/48h") zamiast opisu —
    // fallback na pełny description, jeśli short_description wygląda jak cena.
    description = short_description?.takeUnless { looksLikePrice(it) } ?: description ?: "",
    category = genre ?: "",
    imageUrl = poster_url ?: "",
    channelLogoUrl = logo_url ?: "",
    link = "", // Filmy płatne - brak bezpośredniego linku
    price = price?.let { "${it} zł/48h" },
    youtubeUrl = youtube_url,
    cast = cast ?: tmdbCastAsString(),  // tmdb_cast (JSON array) → "Tom Cruise, Brad Pitt, ..." dla filtrowania
    backdropUrl = backdrop_url  // wide image dla MovieDetailScreen (null gdy brak)
)

/** Czy text wygląda na samą cenę typu "19 zł" lub "19 zł/48h" (z opcjonalnymi spacjami). */
private fun looksLikePrice(text: String): Boolean =
    text.trim().matches(Regex("""^\d+\s*z[łl]\s*(/\s*\d+\s*[hH])?$"""))

/**
 * Konwertuje tmdb_cast (JSON array of {id, name, character}) na concatenated string imion
 * dla łatwego filtrowania (np. `cast.contains("Statham")`).
 */
private fun SupabaseMovie.tmdbCastAsString(): String? {
    val arr = tmdb_cast as? JsonArray ?: return null
    return arr.mapNotNull { el ->
        (el as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
    }.joinToString(", ").takeIf { it.isNotBlank() }
}

/**
 * Konwersja do VodSlideData (dla Hero Slidera)
 */
fun SupabaseMovie.toVodSlideData(): VodSlideData = VodSlideData(
    title = title,
    genre = genre ?: "",
    duration = runtime ?: "",
    year = release_year?.let { "$it r." } ?: "",
    country = country ?: "Polska",
    ageRating = age_rating ?: "13 lat",
    description = short_description ?: description ?: "",
    price = price?.let { "$it zł/48h" } ?: "24 zł/48h",
    backgroundUrl = backdrop_url ?: "",
    posterUrl = poster_url ?: "",
    youtubeUrl = youtube_url,
    selectedLogoUrl = selected_logo_url, // Logo URL to display instead of title when slider not focused
    // Nowe pola dla MovieDetailScreen (Figma design)
    filmwebRating = filmweb_rating,
    audioLanguages = audio_languages,
    subtitleLanguages = subtitle_languages,
    director = director,
    cast = cast,
    // Items from the KINO_PLAY Supabase table are paid rentals — keep the legacy 2-button
    // detail (Wypożycz / Zwiastun) and full Reżyser/Obsada metadata. Without this flag
    // MovieDetailScreen falls back to WIDEO mode (single "Oglądaj" button, no metadata),
    // which broke the Kino Play slider's "Dowiedz się więcej" path.
    isKinoPlay = true
)
