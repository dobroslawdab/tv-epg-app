package com.uxellence.tv.v3

import com.uxellence.tv.v3.version001.VodContent

/**
 * Cache for the KINO_PLAY single-pass channel classification
 * (~1253 movies × 26 channels). Classification runs in `produceState` on
 * Dispatchers.Default and yields a Map<channelName, List<VodContent>>; the
 * cache stores the most recent result so subsequent KINO_PLAY mounts see
 * content on the first frame instead of the brief "empty channels" flash
 * while the background coroutine runs.
 *
 * Keyed on (supabaseInitialized, rentalsHash) — invalidates when Supabase
 * finishes loading or the user rents/clears a movie. Slider shuffle happens
 * in `VodHeroSliderV4` (separate remember block), unaffected by this cache.
 */
object KinoPlayGridCache {
    @Volatile private var cachedKey: Pair<Boolean, Int>? = null
    @Volatile private var cachedValue: Map<String, List<VodContent>>? = null

    fun get(
        supabaseInitialized: Boolean,
        rentalsSnapshot: Map<String, Long>
    ): Map<String, List<VodContent>>? {
        val key = supabaseInitialized to rentalsSnapshot.hashCode()
        return if (key == cachedKey) cachedValue else null
    }

    fun put(
        supabaseInitialized: Boolean,
        rentalsSnapshot: Map<String, Long>,
        value: Map<String, List<VodContent>>
    ) {
        cachedKey = supabaseInitialized to rentalsSnapshot.hashCode()
        cachedValue = value
    }

    fun invalidate() {
        cachedKey = null
        cachedValue = null
    }
}
