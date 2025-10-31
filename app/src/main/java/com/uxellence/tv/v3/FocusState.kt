package com.uxellence.tv.v3

/**
 * ID-based focus state for stable focus restoration.
 *
 * Instead of storing position indices (row, col, scroll) which can change during recomposition
 * and async loading, we store unique identifiers that remain stable.
 *
 * @property channelId Unique channel identifier (e.g., "Teraz w TV", "FILMY, dzis były w TV")
 * @property itemId Unique item identifier or null for CategoryIcon
 * @property scrollPosition LazyRow scroll position for restoration
 */
data class FocusState(
    val channelId: String,
    val itemId: String?,
    val scrollPosition: Int = 0
)
