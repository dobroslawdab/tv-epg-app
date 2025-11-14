package com.uxellence.tv.v3

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.components.FilterChipRow
import com.uxellence.tv.v3.components.HorizontalVodCard
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.coroutines.launch

// Grid layout constants
private const val GRID_COLUMNS = 5              // 5 columns for horizontal thumbnails (344x194px)
private const val GRADIENT_OVERLAY_HEIGHT = 600
private const val TITLE_LEFT_PADDING = 0
private const val GRID_LEFT_PADDING = 60        // Reduced for tighter layout
private const val GRID_RIGHT_PADDING = 60       // Reduced for tighter layout
private const val GRID_HORIZONTAL_GAP = 20      // Reduced for tighter layout
private const val GRID_VERTICAL_GAP = 40

// Focus management (Focus Architect pattern)
private enum class VodFocusLevel {
    FILTERS,  // Focus on filter chips (Search, Sort, Category)
    GRID      // Focus on VOD grid
}

/**
 * VOD Grid Screen - Full-screen grid view of WIDEO content
 *
 * Features:
 * - 5-column grid layout with horizontal thumbnails (344x194px)
 * - Filter chips: Search + Sort + Category
 * - Categories from VodContent.category field
 * - Search by VodContent.title
 * - Data source: VodDataCache.getVodContentList() or preloaded data
 */
@Composable
fun VodGridScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    screenTitle: String = "Lista Wideo",
    preloadedData: List<VodContent>? = null,
    dataCount: Int? = null  // Limit liczby pozycji (null = wszystkie)
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Responsive scaling
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // State management
    var allVodContent by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var filteredVodContent by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var selectedSort by remember { mutableStateOf("Alfabetycznie") }
    var selectedCategory by remember { mutableStateOf("Wszystkie") }
    var searchQuery by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }

    // Focus management (Focus Architect pattern)
    var currentFocusLevel by remember { mutableStateOf(VodFocusLevel.GRID) }
    var focusedRow by remember { mutableStateOf(0) }
    var focusedCol by remember { mutableStateOf(0) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    var filterFirstFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Grid state for scrolling
    val lazyGridState = rememberLazyGridState()

    // Load VOD content on first composition
    LaunchedEffect(preloadedData, dataCount) {
        val data = preloadedData ?: VodDataCache.getVodContentList()

        // Apply data count limit if specified
        allVodContent = if (dataCount != null) {
            data.shuffled().take(dataCount)
        } else {
            data
        }

        // Extract unique categories
        categories = allVodContent
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        filteredVodContent = allVodContent
    }

    // Apply filters and sorting
    LaunchedEffect(selectedCategory, selectedSort, searchQuery, allVodContent) {
        var result = allVodContent

        // Apply category filter
        result = if (selectedCategory == "Wszystkie") {
            result
        } else {
            result.filter { it.category == selectedCategory }
        }

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            result = result.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }

        // Apply sorting
        result = when (selectedSort) {
            "Alfabetycznie" -> result.sortedBy { it.title }
            "Po kategorii" -> result.sortedBy { it.category }
            else -> result
        }

        filteredVodContent = result

        // Reset focus to first item when filters change
        if (filteredVodContent.isNotEmpty()) {
            focusedRow = 0
            focusedCol = 0
        } else {
            currentFocusLevel = VodFocusLevel.FILTERS
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))  // Solid purple background
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            onBackPressed()
                            true
                        }
                        Key.DirectionUp -> {
                            if (currentFocusLevel == VodFocusLevel.GRID) {
                                if (focusedRow > 0) {
                                    focusedRow--
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (currentFocusLevel == VodFocusLevel.GRID) {
                                val numRows = (filteredVodContent.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                if (focusedRow < numRows - 1) {
                                    focusedRow++
                                    val maxCol = if (focusedRow == numRows - 1) {
                                        (filteredVodContent.size - 1) % GRID_COLUMNS
                                    } else {
                                        GRID_COLUMNS - 1
                                    }
                                    if (focusedCol > maxCol) {
                                        focusedCol = maxCol
                                    }
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (currentFocusLevel == VodFocusLevel.GRID) {
                                if (focusedCol > 0) {
                                    focusedCol--
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            if (currentFocusLevel == VodFocusLevel.GRID) {
                                val numRows = (filteredVodContent.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                val maxCol = if (focusedRow == numRows - 1) {
                                    (filteredVodContent.size - 1) % GRID_COLUMNS
                                } else {
                                    GRID_COLUMNS - 1
                                }

                                if (focusedCol < maxCol) {
                                    focusedCol++
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                } else if (focusedRow < numRows - 1) {
                                    // Wrap around: last column → first column of next row
                                    focusedRow++
                                    focusedCol = 0
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // LAYER 1: Scrollable grid content
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sx(GRID_LEFT_PADDING),
                end = sx(GRID_RIGHT_PADDING),
                top = sy(98),
                bottom = sy(40)
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(GRID_HORIZONTAL_GAP)),
            verticalArrangement = Arrangement.spacedBy(sy(GRID_VERTICAL_GAP))
        ) {
            // HEADER: Title "Lista Wideo" (spans all columns)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(GRID_COLUMNS) }) {
                Column {
                    Spacer(modifier = Modifier.height(sy(50)))

                    Text(
                        text = screenTitle,
                        fontSize = (40 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDBDBDB),
                        lineHeight = (38.28 * sy(1).value / 1).sp,
                        modifier = Modifier.padding(start = sx(TITLE_LEFT_PADDING))
                    )

                    Spacer(modifier = Modifier.height(sy(50)))
                }
            }

            // GRID ITEMS: VOD content cards (horizontal)
            itemsIndexed(filteredVodContent) { index, vodContent ->
                val row = index / GRID_COLUMNS
                val col = index % GRID_COLUMNS
                val isItemFocused = currentFocusLevel == VodFocusLevel.GRID &&
                                   row == focusedRow && col == focusedCol

                val focusRequester = gridFocusRequesters.getOrPut(Pair(row, col)) { FocusRequester() }

                HorizontalVodCard(
                    vodContent = vodContent,
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = {
                        currentFocusLevel = VodFocusLevel.GRID
                        focusedRow = row
                        focusedCol = col
                    },
                    onClick = {
                        android.util.Log.d("VOD_GRID", "VOD clicked: ${vodContent.title}")
                        // TODO: Navigate to VOD detail or player
                    },
                    sx = ::sx,
                    sy = ::sy,
                    channelNumber = null  // No channel number for VOD grid
                )
            }
        }

        // LAYER 2: Gradient overlay from top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(GRADIENT_OVERLAY_HEIGHT))
                .align(Alignment.TopCenter)
                .zIndex(5f)
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xFF48227C),
                        0.3f to Color(0xFF48227C),
                        0.6f to Color(0x0048227C),
                        startY = 0f,
                        endY = sy(GRADIENT_OVERLAY_HEIGHT).value
                    )
                )
        )

        // LAYER 3: Pinned filters at top
        FilterChipRow(
            selectedSort = selectedSort,
            onSortChange = { selectedSort = it },
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
            categories = listOf("Wszystkie") + categories,  // Dynamic categories from data
            onSearchClick = {
                android.util.Log.d("VOD_GRID", "Search clicked")
            },
            onNavigateDown = {
                if (filteredVodContent.isNotEmpty()) {
                    currentFocusLevel = VodFocusLevel.GRID
                    focusedRow = 0
                    focusedCol = 0
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(50)
                        gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                    }
                }
            },
            onNavigateRight = { false },
            onFocusReady = { focusRequester ->
                filterFirstFocusRequester = focusRequester
            },
            isFiltersFocused = currentFocusLevel == VodFocusLevel.FILTERS,
            sx = ::sx,
            sy = ::sy,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )
    }

    // ✅ FIXED FOCUS Y: Smooth scroll to keep focused row at fixed position
    LaunchedEffect(focusedRow, focusedCol, currentFocusLevel) {
        if (currentFocusLevel == VodFocusLevel.GRID && filteredVodContent.isNotEmpty()) {
            // +1 for header item
            val targetIndex = focusedRow * GRID_COLUMNS + focusedCol + 1
            if (targetIndex >= 0 && targetIndex < filteredVodContent.size + 1) {
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -400  // Offset to center focused row
                )
            }
        }
    }
}
