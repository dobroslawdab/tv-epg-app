package com.uxellence.tv.v3.epg

import android.util.Log
import com.uxellence.tv.v3.PlayerInterfaceManager
import com.uxellence.tv.v3.PlayerInterfaceState

/**
 * BACK NAVIGATION CONTROLLER
 *
 * Focus Architect Compliance:
 * - Extracted from monolithic handler in EpgDayScreen
 * - UPDATED: 3 levels with Reset to "now" functionality
 * - Handles ONLY: BACK button logic
 * - Uses callback delegation for exit action
 *
 * 3-Level Logic:
 * Level 1: Expanded viewport (3 channels) → Collapse to 1 channel + Reset to currently playing program
 * Level 2: Interface visible (GUI) → Hide it
 * Level 3: All interfaces hidden → Exit screen
 *
 * Delegation Pattern:
 * - Parent (EpgDayScreen) provides callbacks: onCollapseAndResetToNow, onHideInterface, onExit
 * - Controller handles decision logic, delegates actions via callbacks
 */
class BackNavigationController(
    private val onExit: () -> Unit
) {
    private val TAG = "BackNavController"

    /**
     * Handle BACK button press
     *
     * @param interfaceState Current PlayerInterfaceManager state
     * @param isExpanded Whether viewport is expanded (3 channels visible)
     * @param onCollapseAndResetToNow Callback to collapse viewport + reset to currently playing channel/program
     * @param onHideInterface Callback to hide local interface (interfaceVisible = false)
     * @return true if key was consumed
     */
    fun handleBackKey(
        interfaceState: PlayerInterfaceState,
        isExpanded: Boolean,
        onCollapseAndResetToNow: () -> Unit,
        onHideInterface: () -> Unit
    ): Boolean {
        return when {
            // LEVEL 1: Expanded → Collapse to 1-channel view + Reset to "now"
            isExpanded -> {
                onCollapseAndResetToNow()
                Log.d(TAG, "Level 1: Collapse viewport + Reset to currently playing program")
                true
            }

            // LEVEL 2: Interface visible → Hide GUI
            interfaceState !is PlayerInterfaceState.Hidden -> {
                onHideInterface()
                PlayerInterfaceManager.hide()
                Log.d(TAG, "Level 2: Hide interface (state: ${interfaceState.javaClass.simpleName})")
                true
            }

            // LEVEL 3: Exit to TELEWIZJA tab
            else -> {
                Log.d(TAG, "Level 3: Exit screen (return to TELEWIZJA)")
                onExit()
                true
            }
        }
    }
}

/**
 * REFACTORING NOTES:
 *
 * Evolution of BACK Navigation Logic:
 *
 * Version 1 - Original 4-Level Logic (EpgDayScreen.kt:827-926):
 * - Level 1: Expanded → Collapse viewport
 * - Level 2: Not on current program → Reset to "now"
 * - Level 3: On current program + interface visible → Hide interface
 * - Level 4: Interface hidden → Exit
 * Problem: Too complex, state synchronization issues, race conditions
 *
 * Version 2 - Simplified (2-Level):
 * - Level 1: Expanded → Collapse viewport
 * - Level 2: Exit to TELEWIZJA
 * Problem: Lost "Reset to now" functionality, GUI hiding only via timer
 *
 * Version 3 - Current (3-Level) - FINAL:
 * - Level 1: Expanded → Collapse viewport + Reset to currently playing channel/program
 * - Level 2: Interface visible → Hide GUI
 * - Level 3: Exit to TELEWIZJA
 *
 * Benefits of Current Design:
 * 1. Predictable Flow: Collapse+Reset → Hide GUI → Exit
 * 2. "Reset to now": Returns user to currently playing content before hiding GUI
 * 3. Prevents Accidental Exit: User must explicitly hide GUI before exiting
 * 4. Clear State: wasManuallyHidden flag prevents GUI from re-appearing after BACK
 *
 * Design Decision:
 * User preference: Manual GUI hide via BACK (not just timer)
 * "Reset to now" ensures user knows what's currently playing before exiting
 */
