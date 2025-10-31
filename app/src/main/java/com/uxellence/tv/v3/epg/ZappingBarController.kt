package com.uxellence.tv.v3.epg

import android.util.Log
import android.view.KeyEvent
import com.uxellence.tv.v3.PlayerInterfaceManager
import com.uxellence.tv.v3.ZappingMode

/**
 * ZAPPING BAR CONTROLLER
 *
 * Focus Architect Compliance:
 * - Extracted from monolithic handler in EpgDayScreen
 * - Handles ONLY: CH+/CH-/Digits (0-9)
 * - Uses callback delegation for channel switching
 * - Single Responsibility Principle
 *
 * Delegation Pattern:
 * - Parent (EpgDayScreen) provides callbacks:
 *   - onChannelSwitch: Handle CH+/CH- channel switching
 *   - onNavigateToStartWithPip: Handle digit 0 special behavior (navigate to START with PIP)
 * - Controller handles key logic, delegates action via callback
 * - No direct state mutation in EpgDayScreen from here
 */
class ZappingBarController(
    private val onChannelSwitch: (newChannelIndex: Int, resetToLive: Boolean) -> Unit,
    private val onNavigateToStartWithPip: () -> Unit
) {
    private val TAG = "ZappingBarController"

    /**
     * Handle zapping keys: CH+/CH-/Digits
     *
     * @return true if key was consumed, false to delegate further
     */
    fun handleZappingKeys(
        event: android.view.KeyEvent,
        allChannelRows: List<ChannelEpgRow>,
        focusedChannelIndex: Int
    ): Boolean {
        return when (event.keyCode) {
            // Channel UP (CH+ button)
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                handleChannelUp(allChannelRows, focusedChannelIndex)
                true
            }

            // Channel DOWN (CH- button)
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                handleChannelDown(allChannelRows, focusedChannelIndex)
                true
            }

            // SPECIAL: Digit 0 → Navigate to START tab with PIP
            KeyEvent.KEYCODE_0 -> {
                Log.d(TAG, "Digit 0 pressed - Navigating to START with PIP")
                onNavigateToStartWithPip()
                true
            }

            // Digit keys (1-9) → Show Zapping Bar for channel selection
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                handleDigitKey(event.keyCode)
                true
            }

            else -> false // Not our responsibility, delegate further
        }
    }

    /**
     * Handle CH+ button press
     * Extracted from EpgDayScreen.kt:596-621
     */
    private fun handleChannelUp(
        allChannelRows: List<ChannelEpgRow>,
        focusedChannelIndex: Int
    ) {
        if (allChannelRows.isEmpty()) return

        // Find next channel in EPG data
        val nextIndex = (focusedChannelIndex + 1) % allChannelRows.size
        val nextRow = allChannelRows[nextIndex]

        // Get current program for next channel
        val now = java.time.Instant.now()
        val currentProgram = nextRow.programs.firstOrNull { program ->
            !now.isBefore(program.startUtc) && now.isBefore(program.endUtc)
        }

        // Show Zapping Bar with channel and program data
        PlayerInterfaceManager.showZappingBar(
            ZappingMode.ChannelUp(
                channel = nextRow.channel,
                program = currentProgram
            )
        )

        // Switch to next channel
        onChannelSwitch(nextIndex, true)

        Log.d(TAG, "CH+ pressed - Switching to ${nextRow.channel.name}, program: ${currentProgram?.title ?: "none"}")
    }

    /**
     * Handle CH- button press
     * Extracted from EpgDayScreen.kt:625-654
     */
    private fun handleChannelDown(
        allChannelRows: List<ChannelEpgRow>,
        focusedChannelIndex: Int
    ) {
        if (allChannelRows.isEmpty()) return

        // Find previous channel in EPG data
        val prevIndex = if (focusedChannelIndex == 0) {
            allChannelRows.size - 1
        } else {
            focusedChannelIndex - 1
        }
        val prevRow = allChannelRows[prevIndex]

        // Get current program for previous channel
        val now = java.time.Instant.now()
        val currentProgram = prevRow.programs.firstOrNull { program ->
            !now.isBefore(program.startUtc) && now.isBefore(program.endUtc)
        }

        // Show Zapping Bar with channel and program data
        PlayerInterfaceManager.showZappingBar(
            ZappingMode.ChannelDown(
                channel = prevRow.channel,
                program = currentProgram
            )
        )

        // Switch to previous channel
        onChannelSwitch(prevIndex, true)

        Log.d(TAG, "CH- pressed - Switching to ${prevRow.channel.name}, program: ${currentProgram?.title ?: "none"}")
    }

    /**
     * Handle digit key press (0-9)
     * Extracted from EpgDayScreen.kt:658-663
     */
    private fun handleDigitKey(keyCode: Int) {
        val digit = keyCode - KeyEvent.KEYCODE_0
        PlayerInterfaceManager.appendDigit(digit)
        Log.d(TAG, "Digit $digit pressed - Number entry shown")
    }
}
