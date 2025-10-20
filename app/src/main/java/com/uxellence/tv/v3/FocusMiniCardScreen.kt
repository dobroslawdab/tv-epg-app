package com.uxellence.tv.v3

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uxellence.tv.v3.components.MiniCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FocusMiniCardScreen - Demo screen with 3×6 grid of MiniCard components
 *
 * Pattern: Pattern 1 (Full Delegation)
 * - Parent (MainActivity/TopMenu) delegates all keys
 * - This screen handles all navigation via handleFocusMiniCardNavigation()
 * - Uses onReturnToMenu callback for menu transition
 *
 * Navigation:
 * - UP/DOWN: Between rows (0, 1, 2)
 * - LEFT/RIGHT: Between columns (0-5)
 * - BACK: Return to menu (from any position)
 * - UP from row 0: Return to menu
 *
 * Focus Architect Approved: 2025-10-03
 */

/**
 * Navigation handler for FocusMiniCardScreen
 *
 * Follows Pattern 1: Full Delegation
 * Based on MOJE/START/APLIKACJE pattern
 *
 * @param event Keyboard event
 * @param focusedRowIndex Current focused row (0-2)
 * @param focusedColIndex Current focused column (0-5)
 * @param onFocusChange Callback to update focus state
 * @param cardFocusRequesters Map of FocusRequesters for all cards
 * @param totalRows Total number of rows (default: 3)
 * @param cardsPerRow Cards per row (default: 6)
 * @param onReturnToMenu Callback to return to menu
 * @return true if event consumed, false otherwise
 */
fun handleFocusMiniCardNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (row: Int, col: Int) -> Unit,
    cardFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    totalRows: Int = 3,
    cardsPerRow: Int = 6,
    onReturnToMenu: () -> Unit
): Boolean {
    // Only handle key down events (prevent double-firing)
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.DirectionUp -> {
            when {
                focusedRowIndex > 0 -> {
                    // Move to previous row, same column
                    val newRow = focusedRowIndex - 1
                    onFocusChange(newRow, focusedColIndex)
                    try {
                        cardFocusRequesters[Pair(newRow, focusedColIndex)]?.requestFocus()
                    } catch (e: IllegalStateException) {
                        // FocusRequester not initialized yet, ignore
                    }
                    true
                }
                focusedRowIndex == 0 -> {
                    // From first row - return to menu
                    onReturnToMenu()
                    true
                }
                else -> false
            }
        }
        Key.DirectionDown -> {
            if (focusedRowIndex < totalRows - 1) {
                // Move to next row, same column
                val newRow = focusedRowIndex + 1
                onFocusChange(newRow, focusedColIndex)
                try {
                    cardFocusRequesters[Pair(newRow, focusedColIndex)]?.requestFocus()
                } catch (e: IllegalStateException) {
                    // FocusRequester not initialized yet, ignore
                }
                true
            } else false
        }
        Key.DirectionLeft -> {
            if (focusedColIndex > 0) {
                // Move to previous card in same row
                val newCol = focusedColIndex - 1
                onFocusChange(focusedRowIndex, newCol)
                try {
                    cardFocusRequesters[Pair(focusedRowIndex, newCol)]?.requestFocus()
                } catch (e: IllegalStateException) {
                    // FocusRequester not initialized yet, ignore
                }
                true
            } else false
        }
        Key.DirectionRight -> {
            if (focusedColIndex < cardsPerRow - 1) {
                // Move to next card in same row
                val newCol = focusedColIndex + 1
                onFocusChange(focusedRowIndex, newCol)
                try {
                    cardFocusRequesters[Pair(focusedRowIndex, newCol)]?.requestFocus()
                } catch (e: IllegalStateException) {
                    // FocusRequester not initialized yet, ignore
                }
                true
            } else false
        }
        Key.Back -> {
            // BACK from any position returns to menu
            onReturnToMenu()
            true
        }
        else -> false
    }
}

// VOD data parsing is in ComponentShowcaseScreen.kt (parseVodData function)
// VodItem data class is also there

/**
 * FocusMiniCardScreen - 3×6 grid of MiniCard components
 *
 * @param onReturnToMenu Callback when user wants to return to menu
 * @param shouldAutoFocus Whether to auto-focus first card on entry
 * @param sx Horizontal scaling function
 * @param sy Vertical scaling function
 */
@Composable
fun FocusMiniCardScreen(
    onReturnToMenu: () -> Unit,
    shouldAutoFocus: Boolean = true,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Focus state: Pair<rowIndex, colIndex>
    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(0) }

    // LazyListState for each row (horizontal scroll)
    val rowLazyListState0 = rememberLazyListState()
    val rowLazyListState1 = rememberLazyListState()
    val rowLazyListState2 = rememberLazyListState()
    val rowLazyListStates = remember { listOf(rowLazyListState0, rowLazyListState1, rowLazyListState2) }

    // FocusRequesters for all 18 cards (3 rows × 6 columns)
    val cardFocusRequesters = remember {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(3) { row ->
                repeat(6) { col ->
                    put(Pair(row, col), FocusRequester())
                }
            }
        }
    }

    // Load VOD data (first 18 items)
    val vodData = remember { parseVodData(context).take(18) }

    // Card full size (no scaling!)
    val cardWidth = 887
    val cardDefaultHeight = 503
    val cardFocusedHeight = 661
    val cardSpacing = 20

    // Auto-scroll horizontally when focus changes column
    LaunchedEffect(focusedRowIndex, focusedColIndex) {
        if (focusedColIndex >= 0 && focusedRowIndex >= 0 && focusedRowIndex < 3) {
            coroutineScope.launch {
                rowLazyListStates[focusedRowIndex].animateScrollToItem(
                    index = focusedColIndex.coerceAtLeast(0),
                    scrollOffset = 0
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                handleFocusMiniCardNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    cardFocusRequesters = cardFocusRequesters,
                    onReturnToMenu = onReturnToMenu
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sx(60), top = sy(100)),
            verticalArrangement = Arrangement.spacedBy(sy(cardSpacing))
        ) {
            // 3 rows
            items(3) { rowIndex ->
                LazyRow(
                    state = rowLazyListStates[rowIndex],
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sx(cardSpacing)),
                    contentPadding = PaddingValues(end = sx(200)) // Right padding for last card
                ) {
                    // 6 cards per row
                    items(6) { colIndex ->
                        val cardIndex = rowIndex * 6 + colIndex
                        val vodItem = vodData.getOrNull(cardIndex)

                        if (vodItem != null) {
                            val isFocused = focusedRowIndex == rowIndex && focusedColIndex == colIndex

                            // Get FocusRequester for this card
                            val focusRequester = cardFocusRequesters[Pair(rowIndex, colIndex)]

                            Box(
                                modifier = Modifier
                                    .width(sx(cardWidth))
                                    .height(sy(if (isFocused) cardFocusedHeight else cardDefaultHeight))
                                    .then(
                                        // Only add focusRequester if it exists
                                        if (focusRequester != null) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            focusedRowIndex = rowIndex
                                            focusedColIndex = colIndex
                                        }
                                    }
                                    .focusable()
                            ) {
                                MiniCard(
                                    imageUrl = vodItem.thumbnailUrl,
                                    channelLogoUrl = vodItem.logoUrl,
                                    title = vodItem.title,
                                    category = vodItem.category,
                                    duration = "45 min",
                                    year = "2024 r.",
                                    country = "Polska",
                                    ageRating = "12 lat",
                                    description = vodItem.description,
                                    isFocused = isFocused
                                )
                            }
                        } else {
                            // Placeholder for missing data
                            Spacer(modifier = Modifier.width(sx(cardWidth)))
                        }
                    }
                }
            }
        }
    }

    // Auto-focus on first card when entering screen
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            delay(100) // Small delay to ensure composables are ready
            try {
                cardFocusRequesters[Pair(0, 0)]?.requestFocus()
            } catch (e: IllegalStateException) {
                // FocusRequester not initialized yet, will focus via onFocusChanged
            }
        }
    }
}
