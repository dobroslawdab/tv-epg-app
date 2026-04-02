package com.uxellence.tv.v3.epg

import android.util.Log
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key

/**
 * TIMESHIFT CONTROLLER
 *
 * Focus Architect Compliance:
 * - Follows same callback delegation pattern as ZappingBarController, BackNavigationController
 * - Handles: LEFT/RIGHT/OK keys when GUI is hidden (fullscreen player mode)
 * - LEFT = seek back 10 seconds with thumbnail preview
 * - RIGHT = seek forward 10 seconds (or return to live edge)
 * - OK = confirm current timeshifted position (hide overlay, keep playing)
 *
 * Gating Logic:
 * - GUI VISIBLE (interfaceVisible=true) → return false → keys go to EpgNavigationController
 * - GUI HIDDEN (interfaceVisible=false) → LEFT=seek back, RIGHT=seek forward, OK=confirm
 *
 * Priority: Between BACK (Priority 2) and EPG Navigation (Priority 3)
 */
class TimeshiftController(
    private val onSeekBack: () -> Unit,
    private val onSeekForward: () -> Unit,
    private val onReturnToLive: () -> Unit,
    private val onConfirmPosition: () -> Unit
) {
    private val TAG = "TimeshiftCtrl"

    companion object {
        const val SEEK_STEP_MS = 10_000L  // 10 seconds per step
    }

    /**
     * Handle timeshift keys: LEFT/RIGHT/OK when GUI is hidden
     *
     * @param event Key event
     * @param interfaceVisible Whether GUI/EPG overlay is visible
     * @param isTimeshiftAvailable Whether player has buffered content to seek into
     * @param isTimeshiftActive Whether user is currently in timeshift mode (for OK handling)
     * @return true if key was consumed
     */
    fun handleTimeshiftKeys(
        event: KeyEvent,
        interfaceVisible: Boolean,
        isTimeshiftAvailable: Boolean,
        isTimeshiftActive: Boolean
    ): Boolean {
        // Only active when GUI is hidden (fullscreen player mode)
        if (interfaceVisible) return false

        // OK confirms timeshifted position (only when actively timeshifting)
        if (isTimeshiftActive && (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
            Log.d(TAG, "OK: Confirm timeshifted position")
            onConfirmPosition()
            return true
        }

        // Only handle LEFT/RIGHT for seeking
        if (event.key != Key.DirectionLeft && event.key != Key.DirectionRight) return false

        // Need player to have buffer available
        if (!isTimeshiftAvailable) {
            Log.d(TAG, "Timeshift not available (no buffer)")
            return false
        }

        return when (event.key) {
            Key.DirectionLeft -> {
                Log.d(TAG, "SEEK BACK ${SEEK_STEP_MS / 1000}s")
                onSeekBack()
                true
            }

            Key.DirectionRight -> {
                Log.d(TAG, "SEEK FORWARD ${SEEK_STEP_MS / 1000}s")
                onSeekForward()
                true
            }

            else -> false
        }
    }
}
