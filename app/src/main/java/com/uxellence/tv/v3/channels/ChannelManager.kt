package com.uxellence.tv.v3.channels

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * TV Channel Data Model
 *
 * Represents a live TV channel with stream URL, logo, and EPG matching info
 *
 * ⚠️ [streamUrl] to **szablon**, nie gotowy URL. Dla kanałów wymagających autoryzacji
 * zawiera placeholder `{JWT}` (patrz [LiveTokenProvider]). Nigdy nie podawaj go wprost do
 * `MediaItem.fromUri()` — użyj [LiveMediaItemFactory.build], które wstrzyknie token
 * i ustawi poprawny MIME dla DASH.
 */
@Serializable
data class TvChannelData(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val epgId: String? = null,
    val category: String? = null,
    val isGeoBlocked: Boolean = false,
    val isAvailable: Boolean = true,
    val country: String = "PL",
    /**
     * Czy kanał wspiera pauzę/przewijanie (timeshift).
     *
     * Kanały live Play (CDN Redge) mają `timeShiftBufferDepth=PT36S` — okno 36 s, więc
     * pauza/seek nie ma prawa działać sensownie i jest wyłączona w playerze.
     * Stare kanały z asset JSON nie mają tego pola → default true (przewijanie zostaje).
     */
    val supportsTimeshift: Boolean = true
) {
    /** Czy ten kanał wymaga tokenu JWT do odtworzenia. */
    val requiresToken: Boolean get() = LiveTokenProvider.requiresToken(streamUrl)
}

/**
 * Channel Manager Singleton
 *
 * Manages TV channels loaded from JSON asset file.
 * Provides matching logic for EPG integration and category filtering.
 *
 * Usage:
 * ```kotlin
 * // Initialize in MainActivity.onCreate()
 * ChannelManager.initialize(context)
 *
 * // Get all channels
 * val channels = ChannelManager.getAllChannels()
 *
 * // Find by name
 * val tvp1 = ChannelManager.getChannelByName("TVP1")
 *
 * // Match with EPG
 * val channel = ChannelManager.matchWithEpg("TVP1.pl")
 * ```
 */
object ChannelManager {
    private const val TAG = "ChannelManager"
    private const val CHANNELS_JSON_FILE = "tv_channels_with_streams.json"

    private const val PREFS_NAME = "live_channels_cache"
    private const val KEY_REMOTE_CHANNELS = "remote_channels_json"
    private const val KEY_POLL_INTERVAL = "poll_interval_seconds"

    private var channels: List<TvChannelData> = emptyList()
    private var isInitialized = false

    /** Lista z asset JSON — fallback gdy Supabase niedostępny. */
    private var assetChannels: List<TvChannelData> = emptyList()

    /** Czy aktualna lista pochodzi z Supabase (true) czy z asset JSON (false). */
    var isUsingRemoteChannels: Boolean = false
        private set

    /**
     * Licznik wersji listy kanałów. Rośnie przy każdej podmianie listy z Supabase.
     *
     * Ekrany trzymające kanały w `remember { ChannelManager.getAllChannels() }` **nie**
     * zauważą zdalnej aktualizacji. Użyj tego jako klucza:
     * ```kotlin
     * val version by ChannelManager.channelsVersion.collectAsState()
     * val channels = remember(version) { ChannelManager.getAllChannels() }
     * ```
     */
    private val _channelsVersion = MutableStateFlow(0)
    val channelsVersion: StateFlow<Int> = _channelsVersion.asStateFlow()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Initialize channel database from JSON asset
     *
     * Should be called once in MainActivity.onCreate()
     * Safe to call multiple times - subsequent calls are no-op
     *
     * Kolejność źródeł (od najwyższego priorytetu):
     * 1. Lista z Supabase zcache'owana w SharedPreferences (przetrwa restart bez sieci)
     * 2. Asset JSON `tv_channels_with_streams.json`
     *
     * Świeże dane z Supabase dociąga [refreshRemoteChannels] — wołane asynchronicznie po
     * inicjalizacji, żeby nie blokować startu aplikacji.
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }

        // Token JWT dla kanałów live — z cache, żeby pierwszy playback nie czekał na sieć
        LiveTokenProvider.initialize(context)

        // 1) Asset JSON jako baza / fallback
        try {
            val json = context.assets.open(CHANNELS_JSON_FILE)
                .bufferedReader()
                .use { it.readText() }

            assetChannels = jsonParser.decodeFromString(json)
            channels = assetChannels
            isInitialized = true

            Log.i(TAG, "Successfully loaded ${channels.size} channels from asset")
            Log.d(TAG, "Categories: ${channels.mapNotNull { it.category }.distinct().joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load channels from asset: ${e.message}", e)
            assetChannels = emptyList()
            channels = emptyList()
            isInitialized = true
        }

        // 2) Zcache'owana lista z Supabase nadpisuje asset
        loadCachedRemoteChannels(context)
    }

    // ========== REMOTE CONFIG (Supabase) ==========

    /**
     * Pobierz z Supabase listę kanałów **i** token JWT.
     *
     * Bezpieczne przy braku sieci: gdy fetch się nie uda, lista zostaje bez zmian
     * (asset albo poprzedni cache). Nigdy nie czyścimy kanałów na skutek błędu sieci —
     * to zamieniłoby chwilowy brak internetu w pustą aplikację.
     *
     * @return Result z liczbą pobranych kanałów
     */
    suspend fun refreshRemoteChannels(context: Context): Result<Int> {
        val repo = SupabaseLiveChannelsRepository()
        try {
            // Token najpierw — jeśli lista się nie uda, token i tak może być świeży
            val configResult = repo.fetchConfig()
            configResult.onSuccess { config ->
                LiveTokenProvider.setToken(context, config.jwt)
                savePollInterval(context, config.pollIntervalSeconds)

                if (!config.channelsOverrideEnabled) {
                    Log.i(TAG, "channels_override_enabled=false — zostaję na asset JSON")
                    return Result.success(0)
                }
            }

            val channelsResult = repo.fetchChannels()
            return channelsResult.fold(
                onSuccess = { remote ->
                    if (remote.isEmpty()) {
                        Log.w(TAG, "Supabase zwrócił 0 kanałów — zostaję na poprzedniej liście")
                        return@fold Result.success(0)
                    }
                    applyRemoteChannels(context, remote)
                    Result.success(remote.size)
                },
                onFailure = { error ->
                    Log.w(TAG, "Fetch kanałów nieudany, zostaję na ${if (isUsingRemoteChannels) "cache" else "asset"}: ${error.message}")
                    Result.failure(error)
                }
            )
        } finally {
            repo.close()
        }
    }

    /**
     * Pobierz **tylko** token JWT (bez listy kanałów).
     *
     * Używane przez [LiveTokenRetryHandler] po błędzie 401/403 i przez pętlę pollingu —
     * lżejsze niż pełny refresh.
     *
     * @return Result z zamaskowanym tokenem
     */
    suspend fun refreshLiveToken(context: Context): Result<String> {
        val repo = SupabaseLiveChannelsRepository()
        try {
            return repo.fetchConfig().map { config ->
                LiveTokenProvider.setToken(context, config.jwt)
                savePollInterval(context, config.pollIntervalSeconds)
                LiveTokenProvider.maskedToken()
            }
        } finally {
            repo.close()
        }
    }

    /**
     * Uruchom pętlę odpytywania o token.
     *
     * Interwał jest sterowany **zdalnie** (`live_config.poll_interval_seconds`), więc podczas
     * badań można go zacieśnić bez rebuilda. Domyślnie 120 s.
     *
     * Wołaj z `lifecycleScope` w MainActivity — pętla kończy się razem ze scope'em.
     */
    fun startTokenPolling(context: Context, scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                val intervalSeconds = getPollInterval(context)
                delay(intervalSeconds * 1000L)

                if (!isActive) break

                refreshLiveToken(context)
                    .onSuccess { Log.d(TAG, "Token poll OK: $it") }
                    .onFailure { Log.d(TAG, "Token poll failed: ${it.message}") }
            }
        }
    }

    private fun applyRemoteChannels(context: Context, remote: List<TvChannelData>) {
        channels = remote
        isUsingRemoteChannels = true
        _channelsVersion.value = _channelsVersion.value + 1

        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REMOTE_CHANNELS, jsonParser.encodeToString(remote))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Nie udało się zapisać cache kanałów: ${e.message}")
        }

        val withToken = remote.count { it.requiresToken }
        Log.i(TAG, "Zastosowano ${remote.size} kanałów z Supabase ($withToken wymaga JWT), version=${_channelsVersion.value}")
    }

    private fun loadCachedRemoteChannels(context: Context) {
        val cached = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REMOTE_CHANNELS, null) ?: return

        try {
            val parsed: List<TvChannelData> = jsonParser.decodeFromString(cached)
            if (parsed.isNotEmpty()) {
                channels = parsed
                isUsingRemoteChannels = true
                Log.i(TAG, "Wczytano ${parsed.size} kanałów z cache Supabase")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cache kanałów nieczytelny, zostaję na asset: ${e.message}")
        }
    }

    private fun savePollInterval(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_POLL_INTERVAL, seconds.coerceIn(30, 3600))
            .apply()
    }

    private fun getPollInterval(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_POLL_INTERVAL, 120)

    /**
     * Wróć na listę z asset JSON i wyczyść cache Supabase (debug / DevToggles).
     */
    fun resetToAssetChannels(context: Context) {
        channels = assetChannels
        isUsingRemoteChannels = false
        _channelsVersion.value = _channelsVersion.value + 1
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_REMOTE_CHANNELS)
            .apply()
        Log.i(TAG, "Powrót do ${assetChannels.size} kanałów z asset JSON")
    }

    /** Diagnostyka dla DevToggles: skąd lista, ile kanałów z JWT, wiek tokenu. */
    fun diagnostics(): String {
        val source = if (isUsingRemoteChannels) "Supabase" else "asset JSON"
        val withToken = channels.count { it.requiresToken }
        val age = LiveTokenProvider.ageMinutes()
        val ageText = if (age < 0) "brak tokenu" else "token: ${age}min"
        return "$source • ${channels.size} kanałów ($withToken z JWT) • $ageText • ${LiveTokenProvider.maskedToken()}"
    }

    /**
     * Get all available channels
     *
     * @param includeUnavailable If true, includes channels marked as unavailable
     * @return List of all channels (filtered by availability if requested)
     */
    fun getAllChannels(includeUnavailable: Boolean = false): List<TvChannelData> {
        return if (includeUnavailable) {
            channels
        } else {
            channels.filter { it.isAvailable }
        }
    }

    /**
     * Find channel by name (case-insensitive)
     *
     * Matches:
     * - Exact name match
     * - ID match (normalized)
     *
     * @param name Channel name (e.g. "TVP1", "Polsat")
     * @return Channel data or null if not found
     */
    fun getChannelByName(name: String): TvChannelData? {
        val normalizedName = name.trim().lowercase()
        return channels.find { channel ->
            channel.name.lowercase() == normalizedName ||
            channel.id.lowercase() == normalizedName ||
            channel.id.replace(" ", "").lowercase() == normalizedName.replace(" ", "")
        }
    }

    /**
     * Find channel by ID
     *
     * @param id Channel ID (e.g. "tvp1", "polsat")
     * @return Channel data or null if not found
     */
    fun getChannelById(id: String): TvChannelData? {
        return channels.find { it.id == id }
    }

    /**
     * Get channels by category
     *
     * @param category Category name (e.g. "general", "news", "sport", "kids")
     * @return List of channels in the specified category
     */
    fun getChannelsByCategory(category: String): List<TvChannelData> {
        return channels.filter {
            it.category?.equals(category, ignoreCase = true) == true
        }
    }

    /**
     * Match channel with EPG channel ID
     *
     * Used to enrich EPG data with stream URLs
     *
     * @param epgChannelId EPG channel identifier (e.g. "TVP 1", "Polsat", "TVP1.pl@SD" from iptv-org)
     * @return Channel data or null if no match found
     */
    fun matchWithEpg(epgChannelId: String): TvChannelData? {
        // Try exact epgId match first
        channels.find { it.epgId == epgChannelId }?.let { return it }

        // Normalize: remove @SD, .pl, .PL suffixes and lowercase
        val normalizedEpgId = epgChannelId
            .removeSuffix("@SD")
            .removeSuffix(".pl")
            .removeSuffix(".PL")
            .trim()
            .lowercase()
            .replace(" ", "")  // Remove spaces for fuzzy matching (TVP 1 → tvp1)

        return channels.find { channel ->
            // Normalize channel fields the same way
            val normalizedChannelName = channel.name.lowercase().replace(" ", "")
            val normalizedChannelId = channel.id.lowercase().replace(" ", "")
            val normalizedChannelEpgId = channel.epgId
                ?.removeSuffix("@SD")
                ?.removeSuffix(".pl")
                ?.removeSuffix(".PL")
                ?.lowercase()
                ?.replace(" ", "")

            normalizedChannelName == normalizedEpgId ||
            normalizedChannelId == normalizedEpgId ||
            normalizedChannelEpgId == normalizedEpgId
        }
    }

    /**
     * Get available categories
     *
     * @return List of all unique categories present in the channel database
     */
    fun getCategories(): List<String> {
        return channels
            .mapNotNull { it.category }
            .distinct()
            .sorted()
    }

    /**
     * Check if manager is initialized
     *
     * @return True if initialize() was called successfully
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Get channel count
     *
     * @param onlyAvailable If true, counts only available channels
     * @return Number of channels in database
     */
    fun getChannelCount(onlyAvailable: Boolean = false): Int {
        return getAllChannels(includeUnavailable = !onlyAvailable).size
    }

    /**
     * Get EPG IDs for filtering
     *
     * Returns all non-null epgId values from the channel database.
     * Used to filter EPG parsing to only relevant channels.
     *
     * Example usage:
     * ```kotlin
     * val filterIds = ChannelManager.getEpgIds()
     * // Returns: ["TVP 1", "Polsat", "4FUN TV", "TV 4", "Polsat News", "TVP Sport", "TVN 24", "TVP 3", "Polsat News Polityka"]
     * ```
     *
     * @return Set of EPG IDs for O(1) lookup performance
     */
    fun getEpgIds(): Set<String> {
        return channels
            .mapNotNull { it.epgId }
            .filter { it.isNotBlank() }
            .toSet()
    }

    // ========== ZAPPING BAR INTEGRATION ==========

    /**
     * Get channel by number
     *
     * Used for direct channel entry (user types "12" on remote)
     *
     * @param number Channel number (1, 2, 12, 123...)
     * @return Channel data or null if not found
     */
    fun getChannelByNumber(number: Int): TvChannelData? {
        // Assuming channel numbers match array index (1-based)
        // Adjust if your channels have explicit "number" field
        val index = number - 1
        return if (index >= 0 && index < channels.size) {
            channels[index]
        } else {
            null
        }
    }

    /**
     * Get next channel (for CH+ button)
     *
     * @param currentChannelId Current channel ID
     * @return Next channel in list (wraps to first if at end)
     */
    fun getNextChannel(currentChannelId: String): TvChannelData {
        val currentIndex = channels.indexOfFirst { it.id == currentChannelId }
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + 1) % channels.size
        } else {
            0
        }
        return channels[nextIndex]
    }

    /**
     * Get previous channel (for CH- button)
     *
     * @param currentChannelId Current channel ID
     * @return Previous channel in list (wraps to last if at beginning)
     */
    fun getPreviousChannel(currentChannelId: String): TvChannelData {
        val currentIndex = channels.indexOfFirst { it.id == currentChannelId }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            channels.size - 1
        }
        return channels[prevIndex]
    }
}
