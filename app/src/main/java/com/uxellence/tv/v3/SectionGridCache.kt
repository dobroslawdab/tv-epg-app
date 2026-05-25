package com.uxellence.tv.v3

import com.uxellence.tv.v3.version001.VodContent

/**
 * Cache for heavy gridContent computations (filter + distinctBy + shuffled).
 * Once a section computes its channel→content mapping, the result is held for
 * the app's lifetime so re-entering the section reads the same map without
 * re-shuffling. Volatile content (rentals, watchlist) is NOT cached here —
 * those sections still query their reactive singletons directly.
 *
 * Memory: ~1253 VodContent items mapped across 6 keys × refs into the shared
 * VodDataCache list → negligible (~tens of KB).
 */
object SectionGridCache {
    private val cache = mutableMapOf<String, Map<String, List<VodContent>>>()

    fun getOrCompute(
        key: String,
        compute: () -> Map<String, List<VodContent>>
    ): Map<String, List<VodContent>> = cache.getOrPut(key, compute)

    fun invalidate(key: String) {
        cache.remove(key)
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun isCached(key: String): Boolean = cache.containsKey(key)
}
