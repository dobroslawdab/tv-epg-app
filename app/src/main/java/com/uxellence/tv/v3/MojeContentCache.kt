package com.uxellence.tv.v3

import android.content.Context
import com.uxellence.tv.v3.rental.RentalManager
import com.uxellence.tv.v3.version001.VodContent

/**
 * MojeContentCache - Singleton for persistent content in MOJE section
 *
 * Purpose: Cache shuffled content selections so they remain consistent
 * across navigation (MOJE → other section → MOJE)
 *
 * Problem solved: gridContent in TopMenuScreen2 was regenerating on every
 * composition due to `.shuffled()` being called in `remember()` block
 *
 * Implementation: Lazy initialization pattern - content is shuffled once
 * per channel on first access and then cached for the lifetime of the application
 */
object MojeContentCache {

    // Cached content for each channel (channel name -> content list)
    private val channelContentCache = mutableMapOf<String, List<VodContent>>()

    /**
     * Get cached content for a specific channel
     *
     * @param channelName Name of the channel
     * @return Cached or newly shuffled content for this channel
     */
    fun getContentForChannel(channelName: String, context: Context? = null): List<VodContent> {
        // "Aktywne pakiety" is dynamic too — driven by PaketRepository's
        // includedInSubscription flag (only "Pakiet Optymalny" / TELEWIZJA i VOD).
        // Mapped to VodContent with category="Pakiet" so ContentCard renders the
        // logo as the main illustration (same treatment as in the PAKIETY section).
        if (channelName == "Aktywne pakiety") {
            if (context == null) return emptyList()
            val active = com.uxellence.tv.v3.pakiety.PaketRepository
                .loadAll(context)
                .filter { it.includedInSubscription }
            return active.map { p ->
                VodContent(
                    id = "pakiet_active_${p.url.hashCode()}",
                    title = p.name,
                    description = p.description,
                    category = "Pakiet",
                    imageUrl = p.logoHd.ifBlank { p.logo },
                    channelLogoUrl = "",
                    link = p.url,
                    price = p.pointsPerCycle
                )
            }
        }

        // "Wypożyczone" is intentionally never cached: the rental list changes whenever
        // the user rents a new movie, so we always query RentalManager on demand.
        if (channelName == "Wypożyczone") {
            val kinoPlayMovies = VodDataCache.getKinoPlayMovies()
            if (kinoPlayMovies.isEmpty()) return emptyList()
            val rentedTitles = RentalManager.rentedMovieIds().toSet()
            if (rentedTitles.isEmpty()) return emptyList()
            return kinoPlayMovies.filter { it.title in rentedTitles }
        }

        // "ZAPLANOWANE" ma na początku zlecenia użytkownika z demo live (flow
        // "Nagrywanie serii") — dynamiczne jak Wypożyczone, więc bez cache;
        // reszta wiersza to dotychczasowe mocki (stały ogon z cache poniżej).
        if (channelName == "ZAPLANOWANE") {
            android.util.Log.i(
                "DemoRec",
                "ZAPLANOWANE row: user entries = " +
                    com.uxellence.tv.v3.demolive.DemoRecordingScheduler.recordings.value.size
            )
            val user = com.uxellence.tv.v3.demolive.DemoRecordingScheduler
                .recordings.value.values
                .sortedBy { it.startUtcMs }
                .map { r ->
                    VodContent(
                        id = "user_rec_${r.title}_${r.startUtcMs}",
                        title = r.title,
                        description = r.subTitle,
                        category = if (r.isSeries) "Seria" else "Zaplanowane",
                        imageUrl = r.imageUrl ?: "",
                        channelLogoUrl = "",
                        link = "",
                        price = null
                    )
                }
            val mocks = channelContentCache.getOrPut(channelName) {
                VodDataCache.getVodContentList().shuffled().take(10)
            }
            return user + mocks
        }

        // "Do obejrzenia" is dynamic too — driven by WatchlistManager. Each tap on the
        // "Do obejrzenia" button on a WIDEO MovieDetail toggles a movie's title here.
        if (channelName == "Do obejrzenia") {
            val watchlist = com.uxellence.tv.v3.watchlist.WatchlistManager.items.value.toSet()
            if (watchlist.isEmpty()) return emptyList()
            val vodList = VodDataCache.getVodContentList()
            // Preserve user's add order: iterate watchlist (ordered list) and resolve to
            // VodContent by title. Items missing from current catalog are silently skipped.
            return com.uxellence.tv.v3.watchlist.WatchlistManager.items.value
                .mapNotNull { title -> vodList.firstOrNull { it.title == title } }
        }

        // If already cached for this channel, return immediately
        channelContentCache[channelName]?.let { return it }

        // First-time initialization for this channel: shuffle and cache
        val vodContentList = VodDataCache.getVodContentList()
        val kinoPlayMovies = VodDataCache.getKinoPlayMovies()

        val content = if (vodContentList.isNotEmpty() && kinoPlayMovies.isNotEmpty()) {
            when (channelName) {
                // "Aktywne pakiety" handled at the top of this function (needs Context).
                "Skróty" -> emptyList()  // Uses custom MojeSingleShortcutRow component
                "[HEADER-RIGHT] Miejsce na nagrania" -> emptyList()  // Header - no content needed
                "Skróty v2 Moje" -> emptyList()  // Shortcuts - no grid content needed
                "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE" -> vodContentList.shuffled().take(10)
                "Nagrania" -> vodContentList.shuffled().take(10)  // New channel (renamed from "Nagrania v2")
                else -> vodContentList.shuffled().take(10) // Oglądaj dalej, Moje nagrania, Do obejrzenia
            }
        } else {
            emptyList()
        }

        // Cache and return
        channelContentCache[channelName] = content
        return content
    }

    /**
     * Get cached content for all channels in the provided list
     *
     * @param channels List of channel names
     * @return Map of channel names to their content lists
     */
    fun getContent(channels: List<String>, context: Context? = null): Map<String, List<VodContent>> {
        return channels.associateWith { channelName ->
            getContentForChannel(channelName, context)
        }
    }

    /**
     * Invalidate cache to force content refresh on next access
     *
     * Use this when:
     * - New VOD content is loaded from server
     * - User explicitly requests content refresh
     * - App data is cleared
     */
    fun invalidate() {
        channelContentCache.clear()
    }

    /**
     * Check if content is currently cached for a specific channel
     *
     * @param channelName Name of the channel to check
     * @return true if content is cached for this channel
     */
    fun isCached(channelName: String): Boolean {
        return channelContentCache.containsKey(channelName)
    }

    /**
     * Check if any content is cached
     *
     * @return true if any channel has cached content
     */
    fun hasAnyCache(): Boolean {
        return channelContentCache.isNotEmpty()
    }
}
