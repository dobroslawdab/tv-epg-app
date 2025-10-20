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
     * @param epgChannelId EPG channel identifier (e.g. "TVP1.pl", "Polsat.pl")
     * @return Channel data or null if no match found
     */
    fun matchWithEpg(epgChannelId: String): TvChannelData? {
        // Try exact epgId match first
        channels.find { it.epgId == epgChannelId }?.let { return it }

        // Try normalized name match (remove .pl, .PL suffix)
        val normalizedEpgId = epgChannelId
            .removeSuffix(".pl")
            .removeSuffix(".PL")
            .trim()
            .lowercase()

        return channels.find { channel ->
            channel.name.lowercase() == normalizedEpgId ||
            channel.id.lowercase() == normalizedEpgId ||
            channel.epgId?.removeSuffix(".pl")?.removeSuffix(".PL")?.lowercase() == normalizedEpgId
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
}
