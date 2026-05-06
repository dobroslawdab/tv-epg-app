package com.uxellence.tv.v3.rental

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Persistent rental state for movies. Backed by SharedPreferences so rentals survive
 * app restart. Kept in a Compose-observable [MutableState] so MovieDetailScreen and
 * the "Wypożyczone" channel in MOJE auto-recompose when state changes.
 *
 * Map shape: movieId -> expiresAtEpochMs
 *
 * Init must be called once early (MainActivity.onCreate) before first read.
 */
object RentalManager {
    private const val PREFS_NAME = "rental_prefs"
    private const val KEY_RENTALS = "rentals_v1"
    const val DEFAULT_RENTAL_DURATION_MS = 48L * 60 * 60 * 1000  // 48h

    val rentals: MutableState<Map<String, Long>> = mutableStateOf(emptyMap())

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        val raw = prefs(context).getString(KEY_RENTALS, "") ?: ""
        rentals.value = parseStored(raw).pruneExpired()
        persist(context)
    }

    fun rent(
        movieId: String,
        context: Context,
        durationMs: Long = DEFAULT_RENTAL_DURATION_MS
    ) {
        if (movieId.isBlank()) return
        val expiresAt = System.currentTimeMillis() + durationMs
        rentals.value = rentals.value + (movieId to expiresAt)
        persist(context)
    }

    fun isRented(movieId: String): Boolean = remainingMs(movieId) != null

    fun expiresAt(movieId: String): Long? = rentals.value[movieId]?.takeIf { it > System.currentTimeMillis() }

    fun remainingMs(movieId: String): Long? {
        val exp = rentals.value[movieId] ?: return null
        val rem = exp - System.currentTimeMillis()
        return if (rem > 0) rem else null
    }

    fun rentedMovieIds(): List<String> {
        val now = System.currentTimeMillis()
        return rentals.value.entries.filter { it.value > now }.map { it.key }
    }

    fun clearAll(context: Context) {
        rentals.value = emptyMap()
        persist(context)
    }

    private fun persist(context: Context) {
        val raw = rentals.value.entries.joinToString(";") { "${it.key}=${it.value}" }
        prefs(context).edit().putString(KEY_RENTALS, raw).apply()
    }

    private fun parseStored(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { entry ->
                val idx = entry.lastIndexOf('=')
                if (idx <= 0) return@mapNotNull null
                val id = entry.substring(0, idx)
                val ts = entry.substring(idx + 1).toLongOrNull() ?: return@mapNotNull null
                id to ts
            }
            .toMap()
    }

    private fun Map<String, Long>.pruneExpired(): Map<String, Long> {
        val now = System.currentTimeMillis()
        return filterValues { it > now }
    }
}
