package com.uxellence.tv.v3.channels

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

/**
 * Wiersz tabeli `live_channels` w Supabase.
 *
 * `stream_url` trzyma **szablon** z placeholderem `{JWT}` — nigdy gotowy URL z tokenem.
 * Patrz [LiveTokenProvider] po uzasadnienie tego rozdziału.
 */
@Serializable
data class RemoteLiveChannel(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("stream_url") val streamUrl: String,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("epg_id") val epgId: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("is_geo_blocked") val isGeoBlocked: Boolean = false,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("country") val country: String = "PL",
    @SerialName("sort_order") val sortOrder: Int = 0,
    /** false dla kanałów Play (okno live 36 s) — player wyłącza pauzę/przewijanie. */
    @SerialName("supports_timeshift") val supportsTimeshift: Boolean = true
) {
    /** Konwersja do modelu używanego w całej aplikacji. */
    fun toTvChannelData(): TvChannelData = TvChannelData(
        id = id,
        name = name,
        streamUrl = streamUrl,
        logoUrl = logoUrl,
        epgId = epgId,
        category = category,
        isGeoBlocked = isGeoBlocked,
        isAvailable = isAvailable,
        country = country,
        supportsTimeshift = supportsTimeshift
    )
}

/**
 * Wiersz tabeli `live_config` (pojedynczy, id=1) — token + tempo odpytywania.
 */
@Serializable
data class RemoteLiveConfig(
    @SerialName("id") val id: Int = 1,
    @SerialName("jwt") val jwt: String? = null,
    /** Co ile sekund aplikacja ma dopytywać o token. Zdalnie regulowane. */
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 120,
    /** Czy w ogóle nadpisywać listę kanałów z asset JSON. Kill-switch. */
    @SerialName("channels_override_enabled") val channelsOverrideEnabled: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Notatka operacyjna (np. "token wygasa 29.07 18:00") — pokazywana w DevToggles. */
    @SerialName("note") val note: String? = null
)

/**
 * Supabase REST client dla kanałów live i tokenu JWT.
 *
 * Używa tego samego projektu i anon key co [com.uxellence.tv.v3.config.SupabaseConfigRepository].
 *
 * ⚠️ Token JWT do CDN leży w tabeli czytanej anon keyem — czyli każdy kto ma APK może go
 * odczytać. Dla makiety UX na potrzeby badań to akceptowalne (token i tak jest krótkożyciowy
 * i wydawany na czas badań), ale **nie jest to model produkcyjny**. Produkcyjnie token
 * powinien być wydawany per-user przez backend po autoryzacji.
 */
class SupabaseLiveChannelsRepository {

    companion object {
        private const val TAG = "SupabaseLiveChannels"

        // Te same credentiale co SupabaseConfigRepository
        private const val SUPABASE_URL = "https://kexrkaqxoadxugnnbnjh.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtleHJrYXF4b2FkeHVnbm5ibmpoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjUzNTkyMTksImV4cCI6MjA4MDkzNTIxOX0.o1mhA0iaEAcK9LftmYwt_KKvEf0zlcSuBLaYd7B_cuo"

        private const val TABLE_CHANNELS = "live_channels"
        private const val TABLE_CONFIG = "live_config"
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
     * Pobierz listę kanałów live (posortowaną wg `sort_order`).
     *
     * Zwraca `Result.failure` gdy tabela nie istnieje albo brak sieci — wywołujący ma wtedy
     * zostać na liście z asset JSON (fallback), nie wyczyścić kanałów.
     */
    suspend fun fetchChannels(): Result<List<TvChannelData>> {
        return try {
            val response: List<RemoteLiveChannel> = client.get("$SUPABASE_URL/rest/v1/$TABLE_CHANNELS") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("order", "sort_order.asc")
            }.body()

            Log.d(TAG, "Fetched ${response.size} remote live channels")
            Result.success(response.map { it.toTvChannelData() })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live channels: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Pobierz konfigurację live (token JWT + tempo pollingu).
     */
    suspend fun fetchConfig(): Result<RemoteLiveConfig> {
        return try {
            val response: List<RemoteLiveConfig> = client.get("$SUPABASE_URL/rest/v1/$TABLE_CONFIG") {
                headers {
                    append("apikey", SUPABASE_KEY)
                    append("Authorization", "Bearer $SUPABASE_KEY")
                }
                parameter("select", "*")
                parameter("limit", "1")
                parameter("order", "updated_at.desc")
            }.body()

            val config = response.firstOrNull()
                ?: return Result.failure(IllegalStateException("Tabela $TABLE_CONFIG jest pusta"))

            Log.d(TAG, "Fetched live config: poll=${config.pollIntervalSeconds}s, jwt=${config.jwt?.length ?: 0} zn.")
            Result.success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch live config: ${e.message}")
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
