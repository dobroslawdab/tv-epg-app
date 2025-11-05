package com.uxellence.tv.v3

import android.content.Context
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.VodItem
import com.uxellence.tv.v3.PlayNowMovie
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Singleton cache for VOD data to avoid repeated I/O operations.
 *
 * Problem: loadVodContentFromAssets and loadKinoPlayMoviesFromAssets are called
 * multiple times (11 times total) when rendering sections, causing:
 * - Synchronous I/O in main thread (188KB+ JSON files)
 * - CPU-intensive JSON parsing
 * - UI blocking and slow tab switching
 *
 * Solution: Load data once at app startup, cache in memory, reuse everywhere.
 */
object VodDataCache {
    @Volatile
    private var vodContentList: List<VodContent>? = null

    @Volatile
    private var kinoPlayMovies: List<VodContent>? = null

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Initialize cache - call this once in MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        if (vodContentList == null) {
            vodContentList = loadVodContentFromAssets(context)
        }
        if (kinoPlayMovies == null) {
            kinoPlayMovies = loadKinoPlayMoviesFromAssets(context)
        }
    }

    /**
     * Get cached VOD content list (from vod_data.json)
     * Returns empty list if not initialized
     */
    fun getVodContentList(): List<VodContent> {
        return vodContentList ?: emptyList()
    }

    /**
     * Get cached Kino Play movies (from kino_play.json)
     * Returns empty list if not initialized
     */
    fun getKinoPlayMovies(): List<VodContent> {
        return kinoPlayMovies ?: emptyList()
    }

    /**
     * Check if cache is ready
     */
    fun isInitialized(): Boolean {
        return vodContentList != null && kinoPlayMovies != null
    }

    /**
     * Clear cache (for memory management if needed)
     */
    fun clear() {
        vodContentList = null
        kinoPlayMovies = null
    }

    // Private loaders - same implementation as original functions

    private fun loadVodContentFromAssets(context: Context): List<VodContent> {
        return try {
            val inputStream = context.assets.open("vod_data.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vodItems = json.decodeFromString<List<VodItem>>(jsonString)
            vodItems.map { item ->
                VodContent(
                    id = "vod_${item.tytul.hashCode()}_${item.link.hashCode()}",  // Unique ID for focus restoration
                    title = item.tytul,
                    description = item.opis,
                    category = item.kategoria,
                    imageUrl = item.miniaturka_programu,
                    channelLogoUrl = item.logo_kanalu,
                    link = item.link
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadKinoPlayMoviesFromAssets(context: Context): List<VodContent> {
        return try {
            val inputStream = context.assets.open("kino_play.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val movies = json.decodeFromString<List<PlayNowMovie>>(jsonString)

            movies.map { item ->
                VodContent(
                    id = "kino_${item.tytul.hashCode()}_${item.link.hashCode()}",  // Unique ID for focus restoration
                    title = item.tytul,
                    description = item.opis,
                    category = item.kategoria,
                    imageUrl = item.plakat,
                    channelLogoUrl = "",
                    link = item.link
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
