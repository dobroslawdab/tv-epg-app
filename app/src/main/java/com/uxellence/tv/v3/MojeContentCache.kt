package com.uxellence.tv.v3

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
    fun getContentForChannel(channelName: String): List<VodContent> {
        // If already cached for this channel, return immediately
        channelContentCache[channelName]?.let { return it }

        // First-time initialization for this channel: shuffle and cache
        val vodContentList = VodDataCache.getVodContentList()
        val kinoPlayMovies = VodDataCache.getKinoPlayMovies()

        val content = if (vodContentList.isNotEmpty() && kinoPlayMovies.isNotEmpty()) {
            when (channelName) {
                "Aktywne pakiety" -> emptyList()
                "Wypożyczone" -> kinoPlayMovies.shuffled().take(10)
                "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE" -> vodContentList.shuffled().take(10)
                else -> vodContentList.shuffled().take(10) // Oglądaj dalej, Nagrania, Do obejrzenia
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
    fun getContent(channels: List<String>): Map<String, List<VodContent>> {
        return channels.associateWith { channelName ->
            getContentForChannel(channelName)
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
