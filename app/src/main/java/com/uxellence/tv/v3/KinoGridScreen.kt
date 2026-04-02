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
import com.uxellence.tv.v3.components.VerticalVodCard
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.coroutines.launch

// Grid layout constants
private const val GRID_COLUMNS = 7              // 7 columns for vertical posters (220x380px)
private const val GRADIENT_OVERLAY_HEIGHT = 600
private const val TITLE_LEFT_PADDING = 0
private const val GRID_LEFT_PADDING = 80
private const val GRID_RIGHT_PADDING = 80
private const val GRID_HORIZONTAL_GAP = 20      // Reduced by 50% (was 40px)
private const val GRID_VERTICAL_GAP = 10        // Reduced by 50% (was 20px)

// Focus management (Focus Architect pattern)
private enum class KinoFocusLevel {
    FILTERS,  // Focus on filter chips (Search, Sort, Category)
    GRID      // Focus on Kino grid
}

/**
 * Kino Grid Screen - Full-screen grid view of KINO PLAY content
 *
 * Features:
 * - 6-column grid layout with vertical posters (220x380px, 200x280px image)
 * - Scale animation on focus (1.0 → 1.1)
 * - Filter chips: Search + Sort + Category
 * - Categories from VodContent.category field
 * - Search by VodContent.title
 * - Data source: VodDataCache.getKinoPlayMovies()
 */
@Composable
fun KinoGridScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    screenTitle: String = "Lista Kino",
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
    var allKinoContent by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var filteredKinoContent by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var selectedSort by remember { mutableStateOf("Alfabetycznie") }
    var selectedCategory by remember { mutableStateOf("Wszystkie") }
    var searchQuery by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }

    // Focus management (Focus Architect pattern)
    var currentFocusLevel by remember { mutableStateOf(KinoFocusLevel.GRID) }
    var focusedRow by remember { mutableStateOf(0) }
    var focusedCol by remember { mutableStateOf(0) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    var filterFirstFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Grid state for scrolling
    val lazyGridState = rememberLazyGridState()

    // Load Kino content on first composition
    LaunchedEffect(preloadedData, dataCount) {
        val data = preloadedData ?: VodDataCache.getKinoPlayMovies()

        // Apply data count limit if specified
        allKinoContent = if (dataCount != null) {
            data.shuffled().take(dataCount)
        } else {
            data
        }

        // Extract unique categories
        categories = allKinoContent
            .map { it.category }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        filteredKinoContent = allKinoContent
    }

    // Apply filters and sorting
    LaunchedEffect(selectedCategory, selectedSort, searchQuery, allKinoContent) {
        var result = allKinoContent

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

        filteredKinoContent = result

        // Reset focus to first item when filters change
        if (filteredKinoContent.isNotEmpty()) {
            focusedRow = 0
            focusedCol = 0
        } else {
            currentFocusLevel = KinoFocusLevel.FILTERS
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
                            if (currentFocusLevel == KinoFocusLevel.GRID) {
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
                            if (currentFocusLevel == KinoFocusLevel.GRID) {
                                val numRows = (filteredKinoContent.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                if (focusedRow < numRows - 1) {
                                    focusedRow++
                                    val maxCol = if (focusedRow == numRows - 1) {
                                        (filteredKinoContent.size - 1) % GRID_COLUMNS
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
                            if (currentFocusLevel == KinoFocusLevel.GRID) {
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
                            if (currentFocusLevel == KinoFocusLevel.GRID) {
                                val numRows = (filteredKinoContent.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                val maxCol = if (focusedRow == numRows - 1) {
                                    (filteredKinoContent.size - 1) % GRID_COLUMNS
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
            // HEADER: Title "Lista Kino" (spans all columns)
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

            // GRID ITEMS: Kino content cards (vertical with scale animation)
            itemsIndexed(filteredKinoContent, key = { _, vod -> vod.id }) { index, vodContent ->
                val row = index / GRID_COLUMNS
                val col = index % GRID_COLUMNS
                val isItemFocused = currentFocusLevel == KinoFocusLevel.GRID &&
                                   row == focusedRow && col == focusedCol

                val focusRequester = gridFocusRequesters.getOrPut(Pair(row, col)) { FocusRequester() }

                VerticalVodCard(
                    vodContent = vodContent,
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = {
                        currentFocusLevel = KinoFocusLevel.GRID
                        focusedRow = row
                        focusedCol = col
                    },
                    onClick = {
                        android.util.Log.d("KINO_GRID", "Kino clicked: ${vodContent.title}")
                        // TODO: Navigate to Kino detail or player
                    },
                    sx = ::sx,
                    sy = ::sy
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
                android.util.Log.d("KINO_GRID", "Search clicked")
            },
            onNavigateDown = {
                if (filteredKinoContent.isNotEmpty()) {
                    currentFocusLevel = KinoFocusLevel.GRID
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
            isFiltersFocused = currentFocusLevel == KinoFocusLevel.FILTERS,
            sx = ::sx,
            sy = ::sy,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )
    }

    // ✅ FIXED FOCUS Y: Smooth scroll to keep focused row at fixed position
    LaunchedEffect(focusedRow, focusedCol, currentFocusLevel) {
        if (currentFocusLevel == KinoFocusLevel.GRID && filteredKinoContent.isNotEmpty()) {
            // +1 for header item
            val targetIndex = focusedRow * GRID_COLUMNS + focusedCol + 1
            if (targetIndex >= 0 && targetIndex < filteredKinoContent.size + 1) {
                // Dynamic offset: center focused row on screen (responsive to resolution)
                val cardHeightPx = (380 * scaleY).toInt()
                val screenCenterOffset = -(configuration.screenHeightDp.toInt() / 2 - cardHeightPx / 2)
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = screenCenterOffset
                )
            }
        }
    }
}
