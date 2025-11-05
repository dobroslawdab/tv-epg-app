package com.uxellence.tv.v3.channels

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * TV Channel Data Model
 *
 * Represents a live TV channel with stream URL, logo, and EPG matching info
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
    val country: String = "PL"
)

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

    private var channels: List<TvChannelData> = emptyList()
    private var isInitialized = false

    /**
     * Initialize channel database from JSON asset
     *
     * Should be called once in MainActivity.onCreate()
     * Safe to call multiple times - subsequent calls are no-op
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }

        try {
            val json = context.assets.open(CHANNELS_JSON_FILE)
                .bufferedReader()
                .use { it.readText() }

            val jsonParser = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            channels = jsonParser.decodeFromString(json)
            isInitialized = true

            Log.i(TAG, "Successfully loaded ${channels.size} channels")
            Log.d(TAG, "Categories: ${channels.mapNotNull { it.category }.distinct().joinToString()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load channels: ${e.message}", e)
            channels = emptyList()
        }
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
