package com.uxellence.tv.v3

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.components.VerticalVodCard
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.coroutines.launch

// Grid layout constants
private const val GRID_COLUMNS = 7              // 7 columns for vertical posters (220x380px)
private const val GRADIENT_OVERLAY_HEIGHT = 600
private const val TITLE_LEFT_PADDING = 0
private const val GRID_LEFT_PADDING = 80
private const val GRID_RIGHT_PADDING = 80
private const val GRID_HORIZONTAL_GAP = 5       // Halved (10 → 5) to give posters more space
private const val GRID_VERTICAL_GAP = 2         // Halved (5 → 2) to give posters more space
private const val FOCUSED_ROW_PIN_Y_DP = 240    // Pin focused row at this Y from viewport top

// Sort options (zaadaptowane z RecordingsGridScreen)
private enum class KinoSortOption(val label: String) {
    ALPHABETICAL("Alfabetycznie A-Z"),
    ALPHABETICAL_DESC("Alfabetycznie Z-A"),
    BY_CATEGORY("Według kategorii")
}

// Focus management (Focus Architect pattern)
private enum class KinoFocusLevel {
    SORT_CHIP,
    CATEGORY_CHIP,
    GRID
}

/**
 * Snapshot of all derived grid state computed BEFORE first composition. Lets us seed
 * `remember` and `rememberLazyGridState` with the right values immediately, so returning
 * from MovieDetail shows the user's previously focused poster on the very first frame
 * (no async restoration step → no visual flash of focus moving).
 */
private data class KinoGridInitState(
    val allKino: List<VodContent>,
    val collectionMap: Map<String, List<VodContent>>,
    val categories: List<String>,
    val selectedCategory: String,
    val filtered: List<VodContent>,
    val initialRow: Int,
    val initialCol: Int,
    val initialScrollIdx: Int,
    val initialScrollOffset: Int
)

private fun computeKinoGridInitialState(
    initialCategory: String?,
    initialFocusedMovieId: String?,
    dataCount: Int?,
    pinPx: Int
): KinoGridInitState {
    val raw = VodDataCache.getKinoPlayMovies()
    val allKino = if (dataCount != null) raw.shuffled().take(dataCount) else raw

    val excludedKinoChannels = setOf(
        "Wszystkie", "Więcej",
        "SCI-FI", "ROMANS", "KOMEDIA ROMANTYCZNA", "NA POPRAWĘ HUMORU",
        "FAMILIJNE", "ANIMOWANE", "PORUSZAJĄCE HISTORIE", "FILMY GROZY",
        "DRESZCZOWCE", "HISTORIE NA FAKTACH", "DOKUMENT"
    )
    val kinoMap = VodDataCache.kinoChannelMap
    val baseCollections = kinoMap.filter { (name, list) ->
        name !in excludedKinoChannels && list.isNotEmpty()
    }
    val nowosciList = baseCollections["Ostatnio dodane"]
    val collectionMap = if (!nowosciList.isNullOrEmpty()) {
        baseCollections + ("Nowości 🔥" to nowosciList)
    } else {
        baseCollections
    }
    val collectionNames = collectionMap.keys.sorted()

    val genreCategories = allKino
        .flatMap { it.category.split(",") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.replaceFirstChar { c -> c.uppercase() } }
        .distinct()
        .sorted()
    val categories = collectionNames + genreCategories.filter { it !in collectionNames }

    val initialMatchCollection = initialCategory?.let { ic ->
        collectionNames.firstOrNull { it.equals(ic, ignoreCase = true) }
    }
    val normalizedInitial = initialCategory
        ?.split(",")
        ?.firstOrNull()
        ?.trim()
        ?.replaceFirstChar { it.uppercase() }
    val selectedCategory = when {
        initialMatchCollection != null -> initialMatchCollection
        normalizedInitial != null && categories.contains(normalizedInitial) -> normalizedInitial
        else -> "Wszystkie"
    }

    val filteredRaw = when {
        selectedCategory == "Wszystkie" -> allKino
        collectionMap.containsKey(selectedCategory) -> collectionMap[selectedCategory] ?: emptyList()
        else -> allKino.filter { movie ->
            movie.category.split(",").any { segment ->
                segment.trim().equals(selectedCategory, ignoreCase = true)
            }
        }
    }
    // Default sort = ALPHABETICAL (matches `selectedSort` default in the composable)
    val filtered = filteredRaw.sortedBy { it.title.lowercase() }

    val targetIdx = initialFocusedMovieId?.let { id ->
        filtered.indexOfFirst { it.id == id }.takeIf { it >= 0 }
    } ?: 0
    val row = if (filtered.isEmpty()) 0 else targetIdx / GRID_COLUMNS
    val col = if (filtered.isEmpty()) 0 else targetIdx % GRID_COLUMNS

    val (scrollIdx, scrollOffset) = if (row == 0) {
        0 to 0
    } else {
        (row * GRID_COLUMNS + 1) to -pinPx  // +1 for header item
    }

    return KinoGridInitState(
        allKino = allKino,
        collectionMap = collectionMap,
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
 * Kino Grid Screen - Full-screen grid view of KINO PLAY content
 *
 * Features:
 * - 7-column grid layout with vertical posters (220x380px)
 * - Scale animation on focus (1.0 → 1.1) + zIndex lift (no displacement)
 * - Figma-styled dropdown chips: "Sortuj:" + "Kategoria:"
 * - FullScreenPicker modal for option selection
 * - Data source: VodDataCache.getKinoPlayMovies()
 */
@Composable
fun KinoGridScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    screenTitle: String = "Lista Kino",
    preloadedData: List<VodContent>? = null,
    dataCount: Int? = null,
    initialCategory: String? = null,
    initialFocusedMovieId: String? = null,
    onMovieClicked: (VodContent) -> Unit = {},
    onCategoryChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Responsive scaling
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Pin offset (px) used both for initial focus restore and ongoing arrow-key scroll.
    val focusedRowPinPx = remember(density, scaleY) {
        with(density) { sy(FOCUSED_ROW_PIN_Y_DP).toPx() }.toInt()
    }

    // Compute the entire initial state SYNCHRONOUSLY from cached data, so the very
    // first frame already shows the restored grid scroll + focused poster (no async
    // restoration step => no "mignięcie").
    val initState = remember(initialCategory, initialFocusedMovieId, dataCount, focusedRowPinPx) {
        computeKinoGridInitialState(
            initialCategory = initialCategory,
            initialFocusedMovieId = initialFocusedMovieId,
            dataCount = dataCount,
            pinPx = focusedRowPinPx
        )
    }

    // State management — seeded from initState so first composition is already correct
    var allKinoContent by remember { mutableStateOf(initState.allKino) }
    var filteredKinoContent by remember { mutableStateOf(initState.filtered) }
    var selectedSort by remember { mutableStateOf(KinoSortOption.ALPHABETICAL) }
    var selectedCategory by remember { mutableStateOf(initState.selectedCategory) }
    var categories by remember { mutableStateOf(initState.categories) }
    var collectionMap by remember { mutableStateOf(initState.collectionMap) }

    // Dropdown expanded states
    var isSortDropdownExpanded by remember { mutableStateOf(false) }
    var isCategoryDropdownExpanded by remember { mutableStateOf(false) }

    // Focus management — seeded from initState
    var currentFocusLevel by remember { mutableStateOf(KinoFocusLevel.GRID) }
    var focusedRow by remember { mutableStateOf(initState.initialRow) }
    var focusedCol by remember { mutableStateOf(initState.initialCol) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    val sortChipFocusRequester = remember { FocusRequester() }
    val categoryChipFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Grid state — already scrolled to the restored row before first render
    val lazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initState.initialScrollIdx,
        initialFirstVisibleItemScrollOffset = initState.initialScrollOffset
    )

    // Disable Compose's built-in BringIntoView auto-scroll inside the grid.
    // The default spec scrolls partially-visible focused items into full view, which
    // causes "jumping" when moving LEFT/RIGHT through middle rows. Our own LaunchedEffect
    // (below) handles scrolling when focus moves outside the visible window.
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

    // Initial focus is already correct from `initState` above — request it once on first
    // frame so Compose actually moves keyboard focus to the seeded card.
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(50) // give LazyVerticalGrid a frame to register requesters
        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
        hasRequestedInitialFocus = true
    }

    // Apply filters and sorting (re-runs on user-triggered category/sort change)
    var didInitialFilter by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCategory, selectedSort, allKinoContent, collectionMap) {
        var result = allKinoContent

        // Apply category filter
        // - "Wszystkie" → all movies
        // - Collection (franchise/studio/actor) → use pre-computed list from map
        // - Genre → match against any comma-separated segment in VodContent.category
        result = when {
            selectedCategory == "Wszystkie" -> result
            collectionMap.containsKey(selectedCategory) -> collectionMap[selectedCategory] ?: emptyList()
            else -> result.filter { movie ->
                movie.category.split(",").any { segment ->
                    segment.trim().equals(selectedCategory, ignoreCase = true)
                }
            }
        }

        // Apply sorting
        result = when (selectedSort) {
            KinoSortOption.ALPHABETICAL -> result.sortedBy { it.title.lowercase() }
            KinoSortOption.ALPHABETICAL_DESC -> result.sortedByDescending { it.title.lowercase() }
            KinoSortOption.BY_CATEGORY -> result.sortedBy { it.category }
        }

        filteredKinoContent = result

        // Reset focus to first item only when the user actively changes the filter,
        // never on the first composition (which would trash our seeded restored focus).
        if (didInitialFilter && filteredKinoContent.isNotEmpty()) {
            focusedRow = 0
            focusedCol = 0
        } else {
            didInitialFilter = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF281443))  // Dark purple (jak MovieDetailScreen)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    // Handle dropdown navigation if expanded
                    if (isSortDropdownExpanded || isCategoryDropdownExpanded) {
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                isSortDropdownExpanded = false
                                isCategoryDropdownExpanded = false
                                true
                            }
                            else -> false  // Let dropdown handle its own navigation
                        }
                    } else {
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                if (currentFocusLevel == KinoFocusLevel.GRID && (focusedRow > 0 || focusedCol > 0)) {
                                    // First BACK: scroll to top + focus first item
                                    focusedRow = 0
                                    focusedCol = 0
                                    coroutineScope.launch {
                                        lazyGridState.animateScrollToItem(0)
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                    }
                                    true
                                } else {
                                    onBackPressed()
                                    true
                                }
                            }

                            Key.DirectionUp -> {
                                when (currentFocusLevel) {
                                    KinoFocusLevel.GRID -> {
                                        if (focusedRow > 0) {
                                            focusedRow--
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        } else {
                                            // First row - move to sort chip
                                            currentFocusLevel = KinoFocusLevel.SORT_CHIP
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                sortChipFocusRequester.requestFocus()
                                            }
                                        }
                                    }
                                    else -> {
                                        // Already at top, do nothing
                                    }
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                when (currentFocusLevel) {
                                    KinoFocusLevel.SORT_CHIP, KinoFocusLevel.CATEGORY_CHIP -> {
                                        if (filteredKinoContent.isNotEmpty()) {
                                            currentFocusLevel = KinoFocusLevel.GRID
                                            focusedRow = 0
                                            focusedCol = 0
                                            coroutineScope.launch {
                                                lazyGridState.scrollToItem(0)
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                            }
                                        }
                                    }
                                    KinoFocusLevel.GRID -> {
                                        val numRows = (filteredKinoContent.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                        if (focusedRow < numRows - 1) {
                                            focusedRow++
                                            val maxCol = if (focusedRow == numRows - 1) {
                                                (filteredKinoContent.size - 1) % GRID_COLUMNS
                                            } else {
                                                GRID_COLUMNS - 1
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
                                    KinoFocusLevel.CATEGORY_CHIP -> {
                                        currentFocusLevel = KinoFocusLevel.SORT_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            sortChipFocusRequester.requestFocus()
                                        }
                                    }
                                    KinoFocusLevel.GRID -> {
                                        if (focusedCol > 0) {
                                            focusedCol--
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        }
                                    }
                                    else -> {
                                        // Already at leftmost, do nothing
                                    }
                                }
                                true
                            }

                            Key.DirectionRight -> {
                                when (currentFocusLevel) {
                                    KinoFocusLevel.SORT_CHIP -> {
                                        currentFocusLevel = KinoFocusLevel.CATEGORY_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            categoryChipFocusRequester.requestFocus()
                                        }
                                    }
                                    KinoFocusLevel.GRID -> {
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
                                    else -> {
                                        // Already at rightmost, do nothing
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    false
                }
            }
    ) {
        // LAYER 1: Scrollable grid content
        @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
        CompositionLocalProvider(LocalBringIntoViewSpec provides noAutoBringIntoViewSpec) {
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
            // HEADER: Title (spans all columns)
            item(span = { GridItemSpan(GRID_COLUMNS) }) {
                Column {
                    Spacer(modifier = Modifier.height(sy(50)))

                    Text(
                        text = if (selectedCategory == "Wszystkie") "Wszystkie filmy" else selectedCategory,
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
                    onClick = { onMovieClicked(vodContent) },
                    sx = ::sx,
                    sy = ::sy
                )
            }
        }
        }  // CompositionLocalProvider

        // LAYER 2: Gradient overlay from top
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

        // LAYER 3: Filter chips (Sortuj + Kategoria)
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
                isFocused = currentFocusLevel == KinoFocusLevel.SORT_CHIP,
                isExpanded = isSortDropdownExpanded,
                focusRequester = sortChipFocusRequester,
                onFocusChange = { currentFocusLevel = KinoFocusLevel.SORT_CHIP },
                onClick = { isSortDropdownExpanded = !isSortDropdownExpanded },
                sx = ::sx,
                sy = ::sy
            )

            Spacer(modifier = Modifier.width(sx(20)))

            FigmaDropdownChip(
                prefix = "Kategoria:",
                value = selectedCategory,
                isFocused = currentFocusLevel == KinoFocusLevel.CATEGORY_CHIP,
                isExpanded = isCategoryDropdownExpanded,
                focusRequester = categoryChipFocusRequester,
                onFocusChange = { currentFocusLevel = KinoFocusLevel.CATEGORY_CHIP },
                onClick = { isCategoryDropdownExpanded = !isCategoryDropdownExpanded },
                sx = ::sx,
                sy = ::sy
            )
        }

        // LAYER 4: Sort FullScreen Picker
        if (isSortDropdownExpanded) {
            FullScreenPicker(
                title = "Sortuj",
                options = KinoSortOption.values().map { it.label },
                selectedIndex = KinoSortOption.values().indexOf(selectedSort),
                onSelect = { index ->
                    selectedSort = KinoSortOption.values()[index]
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

        // LAYER 5: Category FullScreen Picker
        if (isCategoryDropdownExpanded) {
            val categoryOptions = listOf("Wszystkie") + categories
            FullScreenPicker(
                title = "Kategoria",
                options = categoryOptions,
                selectedIndex = categoryOptions.indexOf(selectedCategory).coerceAtLeast(0),
                onSelect = { index ->
                    selectedCategory = categoryOptions[index]
                    onCategoryChanged(categoryOptions[index])
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

    // Pin focused row to a fixed Y position so posters never get clipped at viewport edges.
    // Row 0 is special: keep the header (title + chips) visible by scrolling to top.
    // For all other rows we anchor the row at FOCUSED_ROW_PIN_Y_DP from the viewport top.
    // Skip while we haven't yet kicked off the initial requestFocus — otherwise this would
    // animate-scroll on top of the seeded initial scroll and visibly nudge the grid.
    LaunchedEffect(focusedRow, currentFocusLevel) {
        if (!hasRequestedInitialFocus) return@LaunchedEffect
        if (currentFocusLevel == KinoFocusLevel.GRID && filteredKinoContent.isNotEmpty()) {
            val rowFirstIndex = focusedRow * GRID_COLUMNS + 1  // +1 for header item
            if (rowFirstIndex >= filteredKinoContent.size + 1) return@LaunchedEffect

            if (focusedRow == 0) {
                lazyGridState.animateScrollToItem(0)
            } else {
                lazyGridState.animateScrollToItem(rowFirstIndex, -focusedRowPinPx)
            }
        }
    }
}

/**
 * Figma-styled Dropdown Chip (skopiowane z RecordingsGridScreen)
 *
 * Design: Nagrywanie-serii node 5819:16980
 * - Background: rgba(0,0,0,0.2)
 * - Height: 64px
 * - Border radius: 64px (fully rounded)
 * - Padding: 24px horizontal
 * - Font: Manrope Bold 20px
 */
@Composable
private fun FigmaDropdownChip(
    prefix: String,
    value: String,
    isFocused: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isFocused -> Color(0xFF5AECD3)
        else -> Color(0x33000000)
    }

    val textColor = when {
        isFocused -> Color(0xFF48227C)
        else -> Color(0xFFEEEEEE)
    }

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
                } else {
                    false
                }
            }
            .focusable()
            .padding(horizontal = sx(24)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(8))
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = textColor.copy(alpha = prefixAlpha))) {
                    append("$prefix ")
                }
                withStyle(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)) {
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

/**
 * Full Screen Picker Component (skopiowane z RecordingsGridScreen)
 *
 * Pełnoekranowy wybór sortowania/filtrowania:
 * - Label po lewej (X=194, Y=344)
 * - Lista opcji jako pill buttons po prawej (X=582, Y=344)
 */
@Composable
private fun FullScreenPicker(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    scaleY: Float
) {
    var focusedIndex by remember { mutableStateOf(selectedIndex.coerceAtLeast(0)) }
    val focusRequesters = remember(options) { options.map { FocusRequester() } }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        // Scroll so the selected option is visible before requesting focus
        listState.scrollToItem(focusedIndex.coerceAtLeast(0))
        focusRequesters.getOrNull(focusedIndex)?.requestFocus()
    }

    // Keep focused item visible when navigating with arrow keys
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

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(sy(24)),
                contentPadding = PaddingValues(vertical = sy(20)),
                modifier = Modifier.fillMaxHeight()
            ) {
                lazyItemsIndexed(options) { index, option ->
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

/**
 * Pill Option Component (skopiowane z RecordingsGridScreen)
 *
 * - Height: 80px, Border-radius: 100px
 * - Background: rgba(0,0,0,0.2)
 * - Selected: checkmark icon, Focused: aqua border 8px
 */
@Composable
private fun PillOption(
    text: String,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
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
            Box(
                modifier = Modifier.size(sx(48)),
                contentAlignment = Alignment.Center
            ) {
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
                .background(
                    color = Color(0x33000000),
                    shape = RoundedCornerShape(percent = 50)
                )
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = sx(8),
                            color = Color(0xFF5AECD3),
                            shape = RoundedCornerShape(percent = 50)
                        )
                    } else {
                        Modifier
                    }
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
