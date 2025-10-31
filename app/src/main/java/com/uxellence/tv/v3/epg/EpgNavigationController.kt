package com.uxellence.tv.v3.epg

import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * EPG NAVIGATION CONTROLLER
 *
 * Focus Architect Compliance:
 * - Extracted from monolithic handler in EpgDayScreen
 * - Handles: UP/DOWN/LEFT/RIGHT/OK navigation
 * - Uses callback delegation for navigation actions
 * - Stateless: Does not mutate EpgDayScreen state directly
 *
 * Delegation Pattern:
 * - Parent (EpgDayScreen) provides callbacks:
 *   - onNavigate: Handle direction-based navigation
 *   - onShowGui: Show GUI when interface is hidden
 * - Controller handles key logic, delegates action via callbacks
 *
 * Navigation Directions:
 * - UP: Previous channel
 * - DOWN: Next channel
 * - LEFT: Previous program (time)
 * - RIGHT: Next program (time)
 * - SELECT: Confirm/Expand
 */
class EpgNavigationController(
    private val onNavigate: (NavigationDirection) -> Unit,
    private val onShowGui: () -> Unit
) {
    private val TAG = "EpgNavController"

    /**
     * Handle EPG navigation keys: UP/DOWN/LEFT/RIGHT/OK
     *
     * PRIORITY: If interface is hidden, UP/DOWN/OK restores it first
     * Then: Delegate to actual navigation logic in EpgDayScreen
     *
     * @return true if key was consumed
     */
    fun handleNavigation(
        event: KeyEvent,
        interfaceVisible: Boolean
    ): Boolean {
        // PRIORITY: Interface restore (from EpgDayScreen.kt:666-683)
        // If interface hidden, UP/DOWN/OK restores it (shows 1-channel view with current program)
        if (!interfaceVisible && event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.DirectionCenter)) {
            Log.d(TAG, "Interface hidden - restoring with ${event.key}")
            onShowGui()
            return true
        }

        // EPG Grid navigation
        return when (event.key) {
            Key.DirectionUp -> {
                Log.d(TAG, "Navigate: UP (previous channel)")
                onNavigate(NavigationDirection.UP)
                true
            }

            Key.DirectionDown -> {
                Log.d(TAG, "Navigate: DOWN (next channel)")
                onNavigate(NavigationDirection.DOWN)
                true
            }

            Key.DirectionLeft -> {
                Log.d(TAG, "Navigate: LEFT (previous program)")
                onNavigate(NavigationDirection.LEFT)
                true
            }

            Key.DirectionRight -> {
                Log.d(TAG, "Navigate: RIGHT (next program)")
                onNavigate(NavigationDirection.RIGHT)
                true
            }

            Key.Enter, Key.DirectionCenter -> {
                Log.d(TAG, "Navigate: SELECT (confirm/expand)")
                onNavigate(NavigationDirection.SELECT)
                true
            }

            else -> false // Not our responsibility
        }
    }
}

/**
 * Navigation directions for EPG grid
 */
enum class NavigationDirection {
    UP,      // Previous channel (vertical)
    DOWN,    // Next channel (vertical)
    LEFT,    // Previous program (horizontal - time)
    RIGHT,   // Next program (horizontal - time)
    SELECT   // Confirm/Expand (OK/ENTER)
}
