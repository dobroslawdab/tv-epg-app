package com.uxellence.tv.v3.focus

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Global Focus State for Row-Based TV Navigation
 *
 * Implements single-focus guarantee across all UI components
 * Row 0: Menu (TOP_MENU2 items) - positions: SEARCH=0, ODKRYWAJ=1, MOJE=2, TELEWIZJA=3, KINO_PLAY=4, WIDEO=5, APLIKACJE=6
 * Row 1: SliderMix content
 * Row 2: ChanneleScreen content
 */
data class GlobalFocusState(
    val currentRow: Int = 0,           // 0=Menu, 1=SliderMix, 2=Channele
    val currentPosition: Int = 0,      // Position within row
    val sectionId: String = "ODKRYWAJ",   // Current section: "TELEWIZJA", "VOD", etc.
    val isActive: Boolean = true       // Whether focus system is active
)

/**
 * Menu item positions for Row 0
 * Layout: ODKRYWAJ=0, MOJE=1, TELEWIZJA=2, KINO_PLAY=3, WIDEO=4, APLIKACJE=5, SEARCH=6
 */
object MenuPositions {
    const val ODKRYWAJ = 0    // Moved from 1 - now first
    const val MOJE = 1        // Moved from 2
    const val TELEWIZJA = 2   // Moved from 3
    const val KINO_PLAY = 3   // Moved from 4
    const val WIDEO = 4       // Moved from 5
    const val APLIKACJE = 5   // Moved from 6
    const val SEARCH = 6      // Moved from 0 - now last (Lupa na koniec)

    fun getPositionForSection(sectionId: String): Int {
        return when (sectionId) {
            "SEARCH" -> SEARCH
            "ODKRYWAJ" -> ODKRYWAJ
            "MOJE" -> MOJE
            "TELEWIZJA" -> TELEWIZJA
            "KINO_PLAY" -> KINO_PLAY
            "WIDEO" -> WIDEO
            "APLIKACJE" -> APLIKACJE
            else -> ODKRYWAJ
        }
    }

    fun getSectionForPosition(position: Int): String {
        return when (position) {
            SEARCH -> "SEARCH"
            ODKRYWAJ -> "ODKRYWAJ"
            MOJE -> "MOJE"
            TELEWIZJA -> "TELEWIZJA"
            KINO_PLAY -> "KINO_PLAY"
            WIDEO -> "WIDEO"
            APLIKACJE -> "APLIKACJE"
            else -> "ODKRYWAJ"
        }
    }
}

/**
 * Interface for components that can receive focus in the row system
 */
interface FocusReceiver {
    fun shouldReceiveFocus(): Boolean
    fun onFocusReceived(position: Int)
    fun onFocusLost()
    fun getRowId(): Int
}

/**
 * Central Focus Coordinator - ensures only one element is focused at a time
 */
class GlobalFocusManager {
    companion object {
        /**
         * Creates a managed focus state that coordinates with global focus system
         */
        @Composable
        fun rememberGlobalFocusState(
            initialRow: Int = 0,
            initialPosition: Int = 0,
            initialSection: String = "START"
        ): MutableState<GlobalFocusState> {
            return remember {
                mutableStateOf(
                    GlobalFocusState(
                        currentRow = initialRow,
                        currentPosition = initialPosition,
                        sectionId = initialSection,
                        isActive = true
                    )
                )
            }
        }

        /**
         * Safely transitions focus between rows with proper cleanup
         */
        @Composable
        fun TransitionFocus(
            globalFocusState: MutableState<GlobalFocusState>,
            targetRow: Int,
            targetPosition: Int = 0,
            onTransition: () -> Unit = {}
        ) {
            LaunchedEffect(targetRow, targetPosition) {
                // Clear any existing focus
                globalFocusState.value = globalFocusState.value.copy(isActive = false)
                
                // Small delay to ensure focus is cleared
                kotlinx.coroutines.delay(50)
                
                // Set new focus
                globalFocusState.value = GlobalFocusState(
                    currentRow = targetRow,
                    currentPosition = targetPosition,
                    sectionId = globalFocusState.value.sectionId,
                    isActive = true
                )
                
                onTransition()
            }
        }

        /**
         * Determines if a specific row/position should be focused
         */
        fun shouldFocus(
            globalFocusState: GlobalFocusState,
            targetRow: Int,
            targetPosition: Int = 0
        ): Boolean {
            return globalFocusState.isActive &&
                   globalFocusState.currentRow == targetRow &&
                   globalFocusState.currentPosition == targetPosition
        }

        /**
         * Determines if a specific row/position should be focused within a section
         * This version also checks if we're currently in content mode (not menu)
         */
        fun shouldFocusInSection(
            globalFocusState: GlobalFocusState,
            targetRow: Int,
            targetPosition: Int = 0,
            requiredSection: String
        ): Boolean {
            return globalFocusState.isActive &&
                   globalFocusState.sectionId == requiredSection &&
                   globalFocusState.currentRow == targetRow &&
                   globalFocusState.currentPosition == targetPosition &&
                   globalFocusState.currentRow > 0 // Not on menu (row 0)
        }

        /**
         * Transitions from menu to content based on selected section
         */
        fun transitionToContent(
            currentState: GlobalFocusState,
            selectedSection: String
        ): GlobalFocusState {
            return when (selectedSection) {
                "TELEWIZJA" -> currentState.copy(
                    currentRow = 1, // Go to SliderMix row
                    currentPosition = 0,
                    sectionId = selectedSection,
                    isActive = true
                )
                else -> currentState.copy(
                    currentRow = 1, // Other sections also go to row 1 for now
                    currentPosition = 0,
                    sectionId = selectedSection,
                    isActive = true
                )
            }
        }

        /**
         * Returns to menu from content
         */
        fun returnToMenu(
            currentState: GlobalFocusState
        ): GlobalFocusState {
            return currentState.copy(
                currentRow = 0,
                currentPosition = MenuPositions.getPositionForSection(currentState.sectionId),
                isActive = true
            )
        }

        /**
         * Navigation helper for TV remote controls
         */
        fun navigateRow(
            currentState: GlobalFocusState,
            direction: RowDirection,
            maxRows: Int = 3,
            maxPositionsInRow: IntArray = intArrayOf(7, 10, 8) // Menu (7 items: SEARCH, ODKRYWAJ, MOJE, TELEWIZJA, KINO_PLAY, WIDEO, APLIKACJE), SliderMix, Channele
        ): GlobalFocusState {
            return when (direction) {
                RowDirection.UP -> {
                    if (currentState.currentRow > 0) {
                        currentState.copy(currentRow = currentState.currentRow - 1)
                    } else currentState
                }
                RowDirection.DOWN -> {
                    if (currentState.currentRow < maxRows - 1) {
                        currentState.copy(
                            currentRow = currentState.currentRow + 1,
                            currentPosition = 0 // Reset to first position in new row
                        )
                    } else currentState
                }
                RowDirection.LEFT -> {
                    if (currentState.currentPosition > 0) {
                        currentState.copy(currentPosition = currentState.currentPosition - 1)
                    } else currentState
                }
                RowDirection.RIGHT -> {
                    val maxInCurrentRow = maxPositionsInRow.getOrElse(currentState.currentRow) { 5 }
                    if (currentState.currentPosition < maxInCurrentRow - 1) {
                        currentState.copy(currentPosition = currentState.currentPosition + 1)
                    } else currentState
                }
            }
        }
    }
}

/**
 * Row navigation directions for TV remote control
 */
enum class RowDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * Composable wrapper that manages focus within a specific row
 */
@Composable
fun RowFocusScope(
    rowId: Int,
    position: Int = 0,
    globalFocusState: MutableState<GlobalFocusState>,
    onFocusReceived: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    content: @Composable (isFocused: Boolean) -> Unit
) {
    val isFocused = GlobalFocusManager.shouldFocus(
        globalFocusState.value,
        targetRow = rowId,
        targetPosition = position
    )

    // Handle focus change effects
    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocusReceived()
        } else {
            onFocusLost()
        }
    }

    content(isFocused)
}

/**
 * Debug overlay for focus state (development only)
 */
@Composable
fun FocusDebugOverlay(
    globalFocusState: GlobalFocusState,
    visible: Boolean = false
) {
    if (visible) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = androidx.compose.ui.Alignment.TopEnd
        ) {
            androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.8f)
                )
            ) {
                androidx.compose.material3.Text(
                    text = "Focus: Row ${globalFocusState.currentRow}, Pos ${globalFocusState.currentPosition}\nSection: ${globalFocusState.sectionId}\nActive: ${globalFocusState.isActive}",
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = androidx.compose.ui.Modifier.padding(8.dp)
                )
            }
        }
    }
}