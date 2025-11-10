package com.uxellence.tv.v3.pip

/**
 * PipMenuOption - Sealed class representing PIP dialog menu options
 *
 * Pure data model for PIP dialog menu system.
 * Each option represents a selectable action in the PIP overlay dialog.
 *
 * Current options:
 * - Fullscreen: Expand PIP to fullscreen (returns to EpgDayScreen)
 * - Close: Close PIP overlay (remains in current section)
 *
 * Usage:
 * ```kotlin
 * val options = listOf(
 *     PipMenuOption.Fullscreen,
 *     PipMenuOption.Close
 * )
 * ```
 */
sealed class PipMenuOption {
    /**
     * Powiększ na pełny ekran
     * Expands PIP to fullscreen, returns to EpgDayScreen
     */
    object Fullscreen : PipMenuOption() {
        val title = "Powiększ na pełny ekran"
        val index = 0
    }

    /**
     * Zamknij PIP
     * Closes PIP overlay, remains in current section
     */
    object Close : PipMenuOption() {
        val title = "Zamknij PIP"
        val index = 1
    }

    companion object {
        /**
         * All available options in display order
         */
        fun values(): List<PipMenuOption> = listOf(Fullscreen, Close)

        /**
         * Get option by index
         */
        fun fromIndex(index: Int): PipMenuOption? = when (index) {
            0 -> Fullscreen
            1 -> Close
            else -> null
        }
    }
}
