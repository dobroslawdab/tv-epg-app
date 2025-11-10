package com.uxellence.tv.v3.pip

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import android.util.Log

/**
 * PipDialogController - Handles key events for PIP dialog menu
 *
 * Extracted from TopMenuScreen2.kt (lines 1013-1079) following EpgDayScreen refactoring pattern.
 *
 * Responsibilities:
 * - Key event handling for 2-option dialog (UP/DOWN/ENTER/BACK)
 * - Navigation between options (focusedOption 0 ↔ 1)
 * - Action delegation via callbacks
 *
 * Pattern: Controller Pattern (delegation-based, stateless)
 * - Doesn't manage state (focusedOption passed in)
 * - Delegates actions via callbacks
 * - Returns Boolean (key consumed or not)
 *
 * Usage:
 * ```kotlin
 * .onPreviewKeyEvent { event ->
 *     PipDialogController.handleDialogKeys(
 *         event = event,
 *         focusedOption = focusedOption,
 *         onNavigate = { newOption -> focusedOption = newOption },
 *         onSelectFullscreen = { onReturnToEpgDay(); onClosePip() },
 *         onSelectClose = { onClosePip() },
 *         onDismiss = { showPipDialog = false }
 *     )
 * }
 * ```
 */
object PipDialogController {
    private const val TAG = "PipDialogController"

    /**
     * Handle key events for dialog option boxes
     *
     * @param event KeyEvent to process
     * @param focusedOption Currently focused option index (0 or 1)
     * @param onNavigate Callback when UP/DOWN pressed (passes new option index)
     * @param onSelectFullscreen Callback when ENTER on option 0 (Powiększ)
     * @param onSelectClose Callback when ENTER on option 1 (Zamknij)
     * @param onDismiss Callback when BACK pressed (close dialog)
     * @return true if key consumed, false otherwise
     */
    fun handleDialogKeys(
        event: KeyEvent,
        focusedOption: Int,
        onNavigate: (Int) -> Unit,
        onSelectFullscreen: () -> Unit,
        onSelectClose: () -> Unit,
        onDismiss: () -> Unit
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }

        return when (event.key) {
            Key.Enter, Key.DirectionCenter -> {
                when (focusedOption) {
                    0 -> {
                        Log.d(TAG, "Dialog: Powiększ selected")
                        onSelectFullscreen()
                        true
                    }
                    1 -> {
                        Log.d(TAG, "Dialog: Zamknij selected")
                        onSelectClose()
                        true
                    }
                    else -> false
                }
            }

            Key.DirectionUp -> {
                when (focusedOption) {
                    1 -> {
                        Log.d(TAG, "Dialog: Navigate UP (1 → 0)")
                        onNavigate(0)
                        true
                    }
                    else -> false  // At top, can't go up
                }
            }

            Key.DirectionDown -> {
                when (focusedOption) {
                    0 -> {
                        Log.d(TAG, "Dialog: Navigate DOWN (0 → 1)")
                        onNavigate(1)
                        true
                    }
                    else -> false  // At bottom, can't go down
                }
            }

            Key.Back, Key.Escape -> {
                Log.d(TAG, "Dialog: BACK pressed - dismissing")
                onDismiss()
                true
            }

            else -> {
                // All other keys not handled
                false
            }
        }
    }
}
