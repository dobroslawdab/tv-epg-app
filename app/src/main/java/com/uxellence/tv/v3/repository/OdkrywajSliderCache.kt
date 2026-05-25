package com.uxellence.tv.v3.repository

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache for ODKRYWAJ hero slider raw items (pre-shuffle). Pattern mirrors
 * `AplikacjeBannerCache`. Items are cached as fetched from Supabase; each
 * ODKRYWAJ screen entry shuffles the cached list locally so the user keeps
 * the per-entry randomness while the network round-trip happens only once
 * per session (or after a 10-minute TTL).
 *
 * Pre-warmed in `MainActivity.onCreate` so the first ODKRYWAJ visit also
 * skips the network wait (provided the warm-up finished in time).
 */
object OdkrywajSliderCache {
    private const val TAG = "OdkrywajSliderCache"
    private const val TTL_MS = 10L * 60L * 1000L  // 10 minutes

    @Volatile private var items: List<OdkrywajSliderItem>? = null
    @Volatile private var lastFetchMs: Long = 0L
    private val mutex = Mutex()

    fun getCached(): List<OdkrywajSliderItem>? = items

    fun isFresh(): Boolean =
        items != null && (System.currentTimeMillis() - lastFetchMs) < TTL_MS

    suspend fun ensureLoaded(): List<OdkrywajSliderItem> {
        if (isFresh()) return items ?: emptyList()
        mutex.withLock {
            if (isFresh()) return items ?: emptyList()
            val fetched = SupabaseOdkrywajRepository.fetchOdkrywajSlider()
            if (fetched.isNotEmpty()) {
                items = fetched
                lastFetchMs = System.currentTimeMillis()
                Log.d(TAG, "Cached ${fetched.size} slider items")
            }
            return items ?: emptyList()
        }
    }

    fun invalidate() {
        items = null
        lastFetchMs = 0L
    }
}
