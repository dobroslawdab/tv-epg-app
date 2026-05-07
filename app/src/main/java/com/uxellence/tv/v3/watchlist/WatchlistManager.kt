package com.uxellence.tv.v3.watchlist

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Persistent watchlist ("Do obejrzenia") for WIDEO content. Backed by SharedPreferences so
 * the user's saves survive app restart. Compose-observable [MutableState] so MOJE → "Do
 * obejrzenia" channel and the toggle button in MovieDetailScreen recompose on changes.
 *
 * Storage: ordered set of movie keys (we use VodSlideData.title — same convention as
 * RentalManager since VodSlideData has no stable id field).
 *
 * Init must be called once early (MainActivity.onCreate) before first read.
 */
object WatchlistManager {
    private const val PREFS_NAME = "watchlist_prefs"
    private const val KEY_WATCHLIST = "watchlist_v1"

    val items: MutableState<List<String>> = mutableStateOf(emptyList())

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        items.value = parseStored(prefs(context).getString(KEY_WATCHLIST, "") ?: "")
    }

    fun contains(key: String): Boolean = key.isNotBlank() && key in items.value

    /** Add to watchlist (no-op if already present). New entries go to the end. */
    fun add(key: String, context: Context) {
        if (key.isBlank() || key in items.value) return
        items.value = items.value + key
        persist(context)
    }

    /** Remove from watchlist (no-op if absent). */
    fun remove(key: String, context: Context) {
        if (key !in items.value) return
        items.value = items.value - key
        persist(context)
    }

    /** Toggle membership; returns the new state (true = now on list). */
    fun toggle(key: String, context: Context): Boolean {
        if (key.isBlank()) return false
        return if (key in items.value) {
            remove(key, context); false
        } else {
            add(key, context); true
        }
    }

    fun clearAll(context: Context) {
        items.value = emptyList()
        persist(context)
    }

    private fun persist(context: Context) {
        // Newline separator avoids collision with title characters; titles can't contain \n.
        val raw = items.value.joinToString("\n")
        prefs(context).edit().putString(KEY_WATCHLIST, raw).apply()
    }

    private fun parseStored(raw: String): List<String> =
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
}
