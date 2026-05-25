package com.uxellence.tv.v3.aplikacje

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cache for the APLIKACJE hero slider banners. Holds the latest Supabase
 * response so subsequent entries to the APLIKACJE tab render the slider
 * instantly without waiting for the network round-trip.
 *
 * Pre-warmed once at app startup via `MainActivity.onCreate` so even the
 * user's first entry sees Supabase banners (provided network is responsive).
 * If the network is slow or down, callers fall back to mock banners; the
 * cache absorbs the next successful fetch.
 *
 * TTL is 10 minutes — banners change rarely, and the user can always
 * `invalidate()` to force a fresh fetch (e.g. after an admin push).
 */
object AplikacjeBannerCache {
    private const val TAG = "AplikacjeBannerCache"
    private const val TTL_MS = 10L * 60L * 1000L  // 10 minutes

    @Volatile private var banners: List<AplikacjeSliderItem>? = null
    @Volatile private var lastFetchMs: Long = 0L
    private val mutex = Mutex()

    fun getCached(): List<AplikacjeSliderItem>? = banners

    fun isFresh(): Boolean =
        banners != null && (System.currentTimeMillis() - lastFetchMs) < TTL_MS

    /**
     * Fetch banners from Supabase if no fresh copy exists. Safe to call
     * concurrently — mutex guarantees only one in-flight request even if
     * pre-warm and the first AplikacjeWithHeroScreen mount race.
     */
    suspend fun ensureLoaded(): List<AplikacjeSliderItem> {
        if (isFresh()) return banners ?: emptyList()
        mutex.withLock {
            if (isFresh()) return banners ?: emptyList()
            val repo = AplikacjeSliderRepository()
            try {
                repo.fetchActiveBanners()
                    .onSuccess { items ->
                        banners = items
                        lastFetchMs = System.currentTimeMillis()
                        Log.d(TAG, "Cached ${items.size} banners")
                    }
                    .onFailure { Log.w(TAG, "Banner fetch failed, keeping prior cache", it) }
            } finally {
                repo.close()
            }
            return banners ?: emptyList()
        }
    }

    fun invalidate() {
        banners = null
        lastFetchMs = 0L
    }
}
