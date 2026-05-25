package com.uxellence.tv.v3

import android.content.Context
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed as listItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.components.AbcSearchKeyboard
import com.uxellence.tv.v3.components.HorizontalVodCard
import com.uxellence.tv.v3.components.KEY_ABC_TOGGLE
import com.uxellence.tv.v3.components.KEY_BACKSPACE
import com.uxellence.tv.v3.components.KEY_SPACE
import com.uxellence.tv.v3.components.keyboardRows
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.coroutines.launch

// Grid layout constants — horizontal thumbnails (344x194), 5 columns default
private const val GRID_COLUMNS = 5
private const val GRID_COLUMNS_WITH_KEYBOARD = 3       // bigger thumbnails when keyboard panel is up
private const val GRADIENT_OVERLAY_HEIGHT = 600
private const val TITLE_LEFT_PADDING = 0
private const val GRID_LEFT_PADDING = 80
private const val GRID_RIGHT_PADDING = 80
private const val GRID_HORIZONTAL_GAP = 20
private const val GRID_VERTICAL_GAP = 40
private const val FOCUSED_ROW_PIN_Y_DP = 240          // Pin focused row at this Y from viewport top

// Width reserved on the left when the search keyboard is visible (matches KinoGridScreen).
private const val VOD_SEARCH_KEYBOARD_SHIFT_DP = 480

// Sort options — mirror KinoSortOption so the spec parity is identical across
// both grids. "Data dodania", "Data produkcji" i "Ocena Filmweb" są POKAZANE
// w pickerze ale obecnie no-op (brak odpowiednich pól w VodContent).
private enum class VodSortOption(val label: String) {
    ALPHABETICAL("Alfabetycznie A-Z"),
    ALPHABETICAL_DESC("Alfabetycznie Z-A"),
    BY_CATEGORY("Po kategorii"),
    DATE_ADDED("Data dodania"),
    RELEASE_YEAR("Data produkcji"),
    FILMWEB_RATING("Ocena Filmweb")
}

// Focus management — mirrors KinoFocusLevel.
private enum class VodFocusLevel {
    SORT_CHIP,
    CATEGORY_CHIP,
    SEARCH_CHIP,
    KEYBOARD,
    GRID
}

/**
 * Snapshot of derived grid state computed BEFORE first composition. Mirrors
 * KinoGridInitState — lets us seed `remember`/`rememberLazyGridState` correctly so the
 * very first frame after returning from MovieDetail shows the user's last poster.
 */
private data class VodGridInitState(
    val allVod: List<VodContent>,
    val categories: List<String>,
    val selectedCategory: String,
    val filtered: List<VodContent>,
    val initialRow: Int,
    val initialCol: Int,
    val initialScrollIdx: Int,
    val initialScrollOffset: Int
)

private fun computeVodGridInitialState(
    preloadedData: List<VodContent>?,
    initialCategory: String?,
    initialFocusedMovieId: String?,
    dataCount: Int?,
    pinPx: Int,
    initialSearchQuery: String = ""
): VodGridInitState {
    val raw = preloadedData ?: VodDataCache.getVodContentList()
    val allVod = if (dataCount != null) raw.shuffled().take(dataCount) else raw

    // Categories ALWAYS come from the chip definition list (WIDEO_CHIP_CATEGORIES) — same
    // bag everywhere so user can switch from any category to any other without re-navigating.
    val categories = WIDEO_CHIP_CATEGORIES.map { it.first }

    val selectedCategory = when {
        initialCategory != null && categories.any { it.equals(initialCategory, ignoreCase = true) } ->
            categories.first { it.equals(initialCategory, ignoreCase = true) }
        else -> "Wszystkie"
    }

    val activeQuery = initialSearchQuery.trim()
    val filteredRaw = when {
        // Restored search wins — same precedence as runtime LaunchedEffect below.
        activeQuery.isNotEmpty() -> allVod.filter {
            it.title.contains(activeQuery, ignoreCase = true)
        }
        else -> applyWideoCategoryFilter(allVod, selectedCategory)
    }
    // Default sort matches `selectedSort` default in the composable
    val filtered = filteredRaw.sortedBy { it.title.lowercase() }

    val targetIdx = initialFocusedMovieId?.let { id ->
        filtered.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    } ?: 0
    val cols = if (activeQuery.isNotEmpty()) GRID_COLUMNS_WITH_KEYBOARD else GRID_COLUMNS
    val row = if (filtered.isEmpty()) 0 else targetIdx / cols
    val col = if (filtered.isEmpty()) 0 else targetIdx % cols

    val (scrollIdx, scrollOffset) = if (row == 0) {
        0 to 0
    } else {
        (row * cols + 1) to -pinPx  // +1 for header item
    }

    return VodGridInitState(
        allVod = allVod,
        categories = categories,
        selectedCategory = selectedCategory,
        filtered = filtered,
        initialRow = row,
        initialCol = col,
        initialScrollIdx = scrollIdx,
        initialScrollOffset = scrollOffset
    )
}

/**
 * VOD Grid Screen — full-screen grid view of WIDEO content.
 *
 * Mirrors KinoGridScreen architecture (FigmaDropdownChip + FullScreenPicker, AbcSearchKeyboard
 * overlay, BACK semantics, search query persistence) — only the cards differ:
 * horizontal 16:9 thumbnails (344x194 via HorizontalVodCard) instead of vertical posters.
 *
 * - 5 columns default, 3 when keyboard visible (bigger thumbnails per user preference)
 * - Categories from `VodContent.category` field (comma-split, deduped)
 * - Search by `VodContent.title.contains(...)` REPLACES category filter
 */
@Composable
fun VodGridScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    screenTitle: String = "Lista Wideo",
    preloadedData: List<VodContent>? = null,
    dataCount: Int? = null,
    initialCategory: String? = null,
    initialFocusedMovieId: String? = null,
    initialSearchQuery: String = "",
    onMovieClicked: (vodContent: VodContent, siblings: List<VodContent>, index: Int) -> Unit = { _, _, _ -> },
    onCategoryChanged: (String) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val focusedRowPinPx = remember(density, scaleY) {
        with(density) { sy(FOCUSED_ROW_PIN_Y_DP).toPx() }.toInt()
    }

    val initState = remember(preloadedData, initialCategory, initialFocusedMovieId, dataCount, focusedRowPinPx, initialSearchQuery) {
        computeVodGridInitialState(
            preloadedData = preloadedData,
            initialCategory = initialCategory,
            initialFocusedMovieId = initialFocusedMovieId,
            dataCount = dataCount,
            pinPx = focusedRowPinPx,
            initialSearchQuery = initialSearchQuery
        )
    }

    var allVodContent by remember { mutableStateOf(initState.allVod) }
    var filteredVodContent by remember { mutableStateOf(initState.filtered) }
    var selectedSort by remember { mutableStateOf(VodSortOption.ALPHABETICAL) }
    var selectedCategory by remember { mutableStateOf(initState.selectedCategory) }
    var categories by remember { mutableStateOf(initState.categories) }

    var isSortDropdownExpanded by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    var currentFocusLevel by remember { mutableStateOf(VodFocusLevel.GRID) }
    var focusedRow by remember { mutableStateOf(initState.initialRow) }
    var focusedCol by remember { mutableStateOf(initState.initialCol) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    val sortChipFocusRequester = remember { FocusRequester() }
    val categoryChipFocusRequester = remember { FocusRequester() }
    val searchChipFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    var searchActive by remember { mutableStateOf(initialSearchQuery.isNotBlank()) }
    var searchQuery by remember { mutableStateOf(initialSearchQuery) }
    var keyboardRow by remember { mutableIntStateOf(0) }
    var keyboardCol by remember { mutableIntStateOf(0) }
    var isNumberMode by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) { onSearchQueryChanged(searchQuery) }

    val keyboardVisible = searchActive
    val gridLeftPaddingDp by animateDpAsState(
        targetValue = if (keyboardVisible) sx(GRID_LEFT_PADDING + VOD_SEARCH_KEYBOARD_SHIFT_DP)
                      else sx(GRID_LEFT_PADDING),
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "vod_search_grid_padding"
    )
    val keyboardOffsetX by animateDpAsState(
        targetValue = if (keyboardVisible) 0.dp else -sx(VOD_SEARCH_KEYBOARD_SHIFT_DP),
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "vod_search_keyboard_offset"
    )
    val gridColumns = if (keyboardVisible) GRID_COLUMNS_WITH_KEYBOARD else GRID_COLUMNS

    val lazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initState.initialScrollIdx,
        initialFirstVisibleItemScrollOffset = initState.initialScrollOffset
    )

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    val noAutoBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float = 0f
        }
    }

    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50)
        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
        hasRequestedInitialFocus = true
    }

    var didInitialFilter by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCategory, selectedSort, allVodContent, searchQuery) {
        var result = allVodContent
        val activeQuery = searchQuery.trim()
        result = when {
            // Search overlay wins — title-match across the full bag, category ignored.
            activeQuery.isNotEmpty() -> result.filter {
                it.title.contains(activeQuery, ignoreCase = true)
            }
            // Otherwise: WIDEO chip filter pattern (same as Skróty v3 chip click)
            else -> applyWideoCategoryFilter(result, selectedCategory)
        }
        result = when (selectedSort) {
            VodSortOption.ALPHABETICAL -> result.sortedBy { it.title.lowercase() }
            VodSortOption.ALPHABETICAL_DESC -> result.sortedByDescending { it.title.lowercase() }
            VodSortOption.BY_CATEGORY -> result.sortedBy { it.category }
            VodSortOption.DATE_ADDED,
            VodSortOption.RELEASE_YEAR,
            VodSortOption.FILMWEB_RATING -> result
        }
        filteredVodContent = result

        // Reset focus only on user-driven re-filter (don't trash seeded restore on first frame)
        if (didInitialFilter) {
            focusedRow = 0
            focusedCol = 0
        } else {
            didInitialFilter = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF281443))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                if (isSortDropdownExpanded || isCategoryDropdownExpanded) {
                    return@onPreviewKeyEvent when (event.key) {
                        Key.Back, Key.Escape -> {
                            isSortDropdownExpanded = false
                            isCategoryDropdownExpanded = false
                            true
                        }
                        else -> false
                    }
                }

                when (event.key) {
                    Key.Back, Key.Escape -> {
                        when {
                            currentFocusLevel == VodFocusLevel.KEYBOARD -> {
                                searchQuery = ""
                                searchActive = false
                                currentFocusLevel = VodFocusLevel.GRID
                                focusedRow = 0
                                focusedCol = 0
                                coroutineScope.launch {
                                    lazyGridState.scrollToItem(0)
                                    kotlinx.coroutines.delay(50)
                                    gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                }
                                true
                            }
                            currentFocusLevel == VodFocusLevel.GRID && (focusedRow > 0 || focusedCol > 0) -> {
                                focusedRow = 0
                                focusedCol = 0
                                coroutineScope.launch {
                                    lazyGridState.animateScrollToItem(0)
                                    kotlinx.coroutines.delay(50)
                                    gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                }
                                true
                            }
                            searchActive -> {
                                searchQuery = ""
                                searchActive = false
                                true
                            }
                            else -> {
                                onBackPressed()
                                true
                            }
                        }
                    }

                    Key.DirectionUp -> {
                        when (currentFocusLevel) {
                            VodFocusLevel.GRID -> {
                                if (focusedRow > 0) {
                                    focusedRow--
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                } else {
                                    currentFocusLevel = VodFocusLevel.SORT_CHIP
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        sortChipFocusRequester.requestFocus()
                                    }
                                }
                            }
                            VodFocusLevel.KEYBOARD -> {
                                if (keyboardRow > 0) {
                                    keyboardRow--
                                    val rowKeys = keyboardRows(isNumberMode)[keyboardRow]
                                    if (keyboardCol > rowKeys.size - 1) keyboardCol = rowKeys.size - 1
                                }
                            }
                            else -> { /* already at top */ }
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        when (currentFocusLevel) {
                            VodFocusLevel.SORT_CHIP, VodFocusLevel.CATEGORY_CHIP, VodFocusLevel.SEARCH_CHIP -> {
                                if (filteredVodContent.isNotEmpty()) {
                                    currentFocusLevel = VodFocusLevel.GRID
                                    focusedRow = 0
                                    focusedCol = 0
                                    coroutineScope.launch {
                                        lazyGridState.scrollToItem(0)
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                    }
                                }
                            }
                            VodFocusLevel.KEYBOARD -> {
                                val rows = keyboardRows(isNumberMode)
                                if (keyboardRow < rows.size - 1) {
                                    keyboardRow++
                                    val rowKeys = rows[keyboardRow]
                                    if (keyboardCol > rowKeys.size - 1) keyboardCol = rowKeys.size - 1
                                }
                            }
                            VodFocusLevel.GRID -> {
                                val numRows = (filteredVodContent.size + gridColumns - 1) / gridColumns
                                if (focusedRow < numRows - 1) {
                                    focusedRow++
                                    val maxCol = if (focusedRow == numRows - 1) {
                                        (filteredVodContent.size - 1) % gridColumns
                                    } else {
                                        gridColumns - 1
                                    }
                                    if (focusedCol > maxCol) focusedCol = maxCol
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionLeft -> {
                        when (currentFocusLevel) {
                            VodFocusLevel.CATEGORY_CHIP -> {
                                currentFocusLevel = VodFocusLevel.SORT_CHIP
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    sortChipFocusRequester.requestFocus()
                                }
                            }
                            VodFocusLevel.SEARCH_CHIP -> {
                                currentFocusLevel = VodFocusLevel.CATEGORY_CHIP
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    categoryChipFocusRequester.requestFocus()
                                }
                            }
                            VodFocusLevel.GRID -> {
                                if (focusedCol > 0) {
                                    focusedCol--
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                } else if (searchActive) {
                                    currentFocusLevel = VodFocusLevel.KEYBOARD
                                }
                            }
                            VodFocusLevel.KEYBOARD -> {
                                if (keyboardCol > 0) keyboardCol--
                            }
                            else -> { /* already at leftmost */ }
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        when (currentFocusLevel) {
                            VodFocusLevel.SORT_CHIP -> {
                                currentFocusLevel = VodFocusLevel.CATEGORY_CHIP
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    categoryChipFocusRequester.requestFocus()
                                }
                            }
                            VodFocusLevel.CATEGORY_CHIP -> {
                                currentFocusLevel = VodFocusLevel.SEARCH_CHIP
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    searchChipFocusRequester.requestFocus()
                                }
                            }
                            VodFocusLevel.KEYBOARD -> {
                                val rowKeys = keyboardRows(isNumberMode)[keyboardRow]
                                if (keyboardCol < rowKeys.size - 1) {
                                    keyboardCol++
                                } else if (filteredVodContent.isNotEmpty()) {
                                    currentFocusLevel = VodFocusLevel.GRID
                                    focusedRow = 0
                                    focusedCol = 0
                                    coroutineScope.launch {
                                        lazyGridState.scrollToItem(0)
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                    }
                                }
                            }
                            VodFocusLevel.GRID -> {
                                val numRows = (filteredVodContent.size + gridColumns - 1) / gridColumns
                                val maxCol = if (focusedRow == numRows - 1) {
                                    (filteredVodContent.size - 1) % gridColumns
                                } else {
                                    gridColumns - 1
                                }
                                if (focusedCol < maxCol) {
                                    focusedCol++
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                } else if (focusedRow < numRows - 1) {
                                    focusedRow++
                                    focusedCol = 0
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                }
                            }
                            else -> { /* already at rightmost */ }
                        }
                        true
                    }

                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        when (currentFocusLevel) {
                            VodFocusLevel.SEARCH_CHIP -> {
                                searchActive = true
                                currentFocusLevel = VodFocusLevel.KEYBOARD
                                keyboardRow = 0
                                keyboardCol = 0
                                true
                            }
                            VodFocusLevel.KEYBOARD -> {
                                val key = keyboardRows(isNumberMode)[keyboardRow][keyboardCol]
                                when (key) {
                                    KEY_BACKSPACE -> if (searchQuery.isNotEmpty()) {
                                        searchQuery = searchQuery.dropLast(1)
                                    }
                                    KEY_SPACE -> searchQuery += " "
                                    KEY_ABC_TOGGLE -> {
                                        isNumberMode = !isNumberMode
                                        keyboardRow = 0
                                        keyboardCol = 0
                                    }
                                    else -> searchQuery += key
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        // LAYER 1: Scrollable grid (shrinks left padding + columns when keyboard is up)
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        CompositionLocalProvider(LocalBringIntoViewSpec provides noAutoBringIntoViewSpec) {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = gridLeftPaddingDp,
                    end = sx(GRID_RIGHT_PADDING),
                    top = sy(98),
                    bottom = sy(40)
                ),
                horizontalArrangement = Arrangement.spacedBy(sx(GRID_HORIZONTAL_GAP)),
                verticalArrangement = Arrangement.spacedBy(sy(GRID_VERTICAL_GAP))
            ) {
                // HEADER — hidden during active search (keyboard panel echoes the query instead)
                item(span = { GridItemSpan(gridColumns) }) {
                    Column {
                        Spacer(modifier = Modifier.height(sy(50)))
                        if (!searchActive) {
                            Text(
                                text = selectedCategory,  // chip label as header (Wszystkie / Viaplay Filmy / ...)
                                fontSize = (40 * sy(1).value / 1).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDBDBDB),
                                lineHeight = (38.28 * sy(1).value / 1).sp,
                                modifier = Modifier.padding(start = sx(TITLE_LEFT_PADDING))
                            )
                        }
                        Spacer(modifier = Modifier.height(sy(50)))
                    }
                }

                itemsIndexed(filteredVodContent, key = { _, vod -> vod.id }) { index, vodContent ->
                    val row = index / gridColumns
                    val col = index % gridColumns
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
                            // Pass the currently displayed (filtered+sorted) list + clicked
                            // index so MovieDetailScreen can prev/next-navigate through
                            // siblings with LEFT/RIGHT on the buttons row.
                            onMovieClicked(vodContent, filteredVodContent, index)
                        },
                        sx = ::sx,
                        sy = ::sy
                    )
                }
            }
        }

        // LAYER 2: Top gradient (hides chrome scrolling under chips)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(GRADIENT_OVERLAY_HEIGHT))
                .align(Alignment.TopCenter)
                .zIndex(5f)
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xFF281443),
                        0.3f to Color(0xFF281443),
                        0.6f to Color(0x00281443),
                        startY = 0f,
                        endY = sy(GRADIENT_OVERLAY_HEIGHT).value
                    )
                )
        )

        // LAYER 3: Sortuj + Kategoria + Lupa chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = sy(40))
                .zIndex(10f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FigmaDropdownChip(
                prefix = "Sortuj:",
                value = selectedSort.label,
                isFocused = currentFocusLevel == VodFocusLevel.SORT_CHIP,
                isExpanded = isSortDropdownExpanded,
                focusRequester = sortChipFocusRequester,
                onFocusChange = { currentFocusLevel = VodFocusLevel.SORT_CHIP },
                onClick = { isSortDropdownExpanded = !isSortDropdownExpanded },
                sx = ::sx,
                sy = ::sy
            )
            Spacer(modifier = Modifier.width(sx(20)))
            FigmaDropdownChip(
                prefix = "Kategoria:",
                value = selectedCategory,
                isFocused = currentFocusLevel == VodFocusLevel.CATEGORY_CHIP,
                isExpanded = isCategoryDropdownExpanded,
                focusRequester = categoryChipFocusRequester,
                onFocusChange = { currentFocusLevel = VodFocusLevel.CATEGORY_CHIP },
                onClick = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded },
                sx = ::sx,
                sy = ::sy
            )
            Spacer(modifier = Modifier.width(sx(20)))
            val searchChipFocused = currentFocusLevel == VodFocusLevel.SEARCH_CHIP
            Box(
                modifier = Modifier
                    .focusRequester(searchChipFocusRequester)
                    .focusable()
                    .onFocusChanged { fs ->
                        if (fs.isFocused) currentFocusLevel = VodFocusLevel.SEARCH_CHIP
                    }
                    .height(sy(64))
                    .background(
                        color = if (searchChipFocused) Color(0xFF5FEDD4) else Color(0x33000000),
                        shape = RoundedCornerShape(sx(64))
                    )
                    .border(
                        width = if (searchChipFocused) sx(2) else 0.dp,
                        color = if (searchChipFocused) Color(0xFF5FEDD4) else Color.Transparent,
                        shape = RoundedCornerShape(sx(64))
                    )
                    .padding(horizontal = sx(24)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Szukaj",
                    tint = if (searchChipFocused) Color(0xFF281443) else Color(0xFFEEEEEE),
                    modifier = Modifier.size(sx(28))
                )
            }
        }

        // LAYER 3.5: ABC keyboard panel — appears statically on the LEFT when search is active
        if (searchActive) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = keyboardOffsetX + sx(40), y = sy(140))
                    .zIndex(15f),
                verticalArrangement = Arrangement.spacedBy(sy(16))
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "Wpisz tytuł wideo…" else searchQuery,
                    color = if (searchQuery.isEmpty()) Color(0x99EEEEEE) else Color(0xFFEEEEEE),
                    fontSize = (28 * sy(1).value).sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x33000000), RoundedCornerShape(sx(12)))
                        .padding(horizontal = sx(16), vertical = sy(12))
                )
                AbcSearchKeyboard(
                    focusedRow = keyboardRow,
                    focusedCol = keyboardCol,
                    isNumberMode = isNumberMode,
                    sx = ::sx,
                    sy = ::sy
                )
            }
        }

        // LAYER 4: Sortuj picker
        if (isSortDropdownExpanded) {
            FullScreenPicker(
                title = "Sortuj",
                options = VodSortOption.values().map { it.label },
                selectedIndex = VodSortOption.values().indexOf(selectedSort),
                onSelect = { idx ->
                    selectedSort = VodSortOption.values()[idx]
                    isSortDropdownExpanded = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        sortChipFocusRequester.requestFocus()
                    }
                },
                onDismiss = {
                    isSortDropdownExpanded = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        sortChipFocusRequester.requestFocus()
                    }
                },
                sx = ::sx,
                sy = ::sy,
                scaleY = scaleY
            )
        }

        // LAYER 5: Kategoria picker — uses the same chip list, so user can switch from any
        // entry point. ("Wszystkie" is already the first chip, no need to prepend.)
        if (isCategoryDropdownExpanded) {
            val categoryOptions = categories
            FullScreenPicker(
                title = "Kategoria",
                options = categoryOptions,
                selectedIndex = categoryOptions.indexOf(selectedCategory).coerceAtLeast(0),
                onSelect = { idx ->
                    selectedCategory = categoryOptions[idx]
                    onCategoryChanged(categoryOptions[idx])
                    isCategoryDropdownExpanded = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        categoryChipFocusRequester.requestFocus()
                    }
                },
                onDismiss = {
                    isCategoryDropdownExpanded = false
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(100)
                        categoryChipFocusRequester.requestFocus()
                    }
                },
                sx = ::sx,
                sy = ::sy,
                scaleY = scaleY
            )
        }
    }

    // Pin focused row to a fixed Y position so cards don't get clipped at viewport edges.
    LaunchedEffect(focusedRow, currentFocusLevel) {
        if (!hasRequestedInitialFocus) return@LaunchedEffect
        if (currentFocusLevel == VodFocusLevel.GRID && filteredVodContent.isNotEmpty()) {
            val rowFirstIndex = focusedRow * gridColumns + 1  // +1 for header
            if (rowFirstIndex >= filteredVodContent.size + 1) return@LaunchedEffect
            if (focusedRow == 0) {
                lazyGridState.animateScrollToItem(0)
            } else {
                lazyGridState.animateScrollToItem(rowFirstIndex, -focusedRowPinPx)
            }
        }
    }
}

// ─── Helper composables (copies of KinoGridScreen helpers — kept private here so the
// two screens stay independent and can evolve separately). ────────────────────────────

@Composable
private fun FigmaDropdownChip(
    prefix: String,
    value: String,
    isFocused: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused) Color(0xFF5AECD3) else Color(0x33000000)
    val textColor = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)
    val prefixAlpha = if (isFocused) 1f else 0.8f

    Row(
        modifier = modifier
            .height(sy(64))
            .background(backgroundColor, RoundedCornerShape(sy(64)))
            .then(
                if (isFocused) Modifier.border(
                    width = sx(4),
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sy(64))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .padding(horizontal = sx(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(8))
    ) {
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = textColor.copy(alpha = prefixAlpha))) {
                    append("$prefix ")
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) {
                    append(value)
                }
            },
            fontSize = (20 * sy(1).value / 1.dp.value).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
        Text(
            text = "▼",
            color = textColor,
            fontSize = (16 * sy(1).value / 1.dp.value).sp,
            modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
        )
    }
}

@Composable
private fun FullScreenPicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    scaleY: Float
) {
    var focusedIndex by remember { mutableStateOf(selectedIndex.coerceAtLeast(0)) }
    val focusRequesters = remember(options) { options.map { FocusRequester() } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        listState.scrollToItem(focusedIndex.coerceAtLeast(0))
        focusRequesters.getOrNull(focusedIndex)?.requestFocus()
    }
    LaunchedEffect(focusedIndex) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val first = visible.firstOrNull()?.index ?: 0
        val last = visible.lastOrNull()?.index ?: 0
        if (focusedIndex < first || focusedIndex > last) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xFF281443),
                    0.5f to Color(0xFF281443),
                    1.0f to Color(0xFF1A0C2C)
                )
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (focusedIndex > 0) {
                                focusedIndex--
                                focusRequesters[focusedIndex].requestFocus()
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            if (focusedIndex < options.size - 1) {
                                focusedIndex++
                                focusRequesters[focusedIndex].requestFocus()
                            }
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .zIndex(200f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sx(194), top = sy(60), bottom = sy(40))
        ) {
            Text(
                text = title,
                color = Color(0xFFEEEEEE),
                fontSize = (32 * scaleY).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(sx(368))
            )
            Spacer(modifier = Modifier.width(sx(20)))
            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(sy(24)),
                contentPadding = PaddingValues(vertical = sy(20)),
                modifier = Modifier.fillMaxHeight()
            ) {
                listItemsIndexed(options) { index, option ->
                    val isSelected = index == selectedIndex
                    val isItemFocused = index == focusedIndex
                    PillOption(
                        text = option,
                        isSelected = isSelected,
                        isFocused = isItemFocused,
                        focusRequester = focusRequesters[index],
                        onFocusChange = { focusedIndex = index },
                        onClick = { onSelect(index) },
                        sx = sx,
                        sy = sy,
                        scaleY = scaleY
                    )
                }
            }
        }
    }
}

@Composable
private fun PillOption(
    text: String,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    scaleY: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(24)),
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
    ) {
        if (isSelected) {
            Box(modifier = Modifier.size(sx(48)), contentAlignment = Alignment.Center) {
                Text(
                    text = "✓",
                    color = Color(0xFF5AECD3),
                    fontSize = (32 * scaleY).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(modifier = Modifier.width(sx(72)))
        }
        Box(
            modifier = Modifier
                .width(sx(684))
                .height(sy(80))
                .background(color = Color(0x33000000), shape = RoundedCornerShape(percent = 50))
                .then(
                    if (isFocused) Modifier.border(
                        width = sx(8),
                        color = Color(0xFF5AECD3),
                        shape = RoundedCornerShape(percent = 50)
                    ) else Modifier
                )
                .padding(horizontal = sx(48)),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                color = Color(0xFFEEEEEE),
                fontSize = (28 * scaleY).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.56.sp
            )
        }
    }
}
