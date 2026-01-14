package com.uxellence.tv.v3

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.components.RecordingCard
import com.uxellence.tv.v3.model.*
import com.uxellence.tv.v3.repository.RecordingRepository
import kotlinx.coroutines.launch

/**
 * SERIES EPISODES GRID SCREEN
 *
 * Drill-down view showing individual episodes of a series bundle
 *
 * Features:
 * - 4-column grid layout (same as RecordingsGridScreen)
 * - Back button returns to RecordingsGridScreen
 * - Filter tabs: Wszystkie | Nagrane (10) | Nagrywanie (1) | Zaplanowane (1)
 * - Shows episode counts in filter labels
 * - Click on episode → play or show details
 *
 * Layout:
 * ┌──────────────────────────────────────────────────────────────┐
 * │ [← Wstecz] "Klan" - 12 odcinków                             │
 * │ [Wszystkie] [Nagrane (10)] [Nagrywanie (1)] [Zaplanowane]   │
 * ├──────────────────────────────────────────────────────────────┤
 * │ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐             │
 * │ │ E1234   │ │ E1235   │ │ E1236   │ │ E1237   │             │
 * │ │ NAGRANE │ │ NAGRANE │ │● LIVE  │ │ZAPLANOW.│             │
 * │ └─────────┘ └─────────┘ └─────────┘ └─────────┘             │
 * └──────────────────────────────────────────────────────────────┘
 */

// Grid layout constants
private const val GRID_COLUMNS = 4
private const val GRID_HORIZONTAL_GAP = 40
private const val GRID_VERTICAL_GAP = 64
private const val GRID_PADDING = 80
private const val GRADIENT_OVERLAY_HEIGHT = 100

// Sorting options
private enum class EpisodeSortOption(val label: String) {
    NEWEST_FIRST("od najnowszych"),
    OLDEST_FIRST("od najstarszych"),
    EPISODE_ASC("odc. rosnąco"),
    EPISODE_DESC("odc. malejąco")
}

// Filter options for episodes - with dynamic title
private enum class EpisodeFilter(val label: String, val titleSuffix: String) {
    ALL("Wszystkie", "wszystkie odcinki"),
    RECORDED("Nagrane", "nagrane odcinki"),
    RECORDING("Nagrywanie", "nagrywane"),
    SCHEDULED("Zaplanowane", "zaplanowane")
}

// Focus levels
private enum class EpisodeFocusLevel {
    SORT_CHIP,
    FILTER_CHIP,
    WATCH_BUTTON,    // "Oglądaj dalej" button
    DELETE_BUTTON,   // "Opcje usuwania" button
    GRID
}

private const val TAG = "SeriesEpisodesGridScreen"

@Composable
fun SeriesEpisodesGridScreen(
    seriesId: String,
    seriesTitle: String,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Responsive scaling
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Repository instance
    val recordingRepository = remember { RecordingRepository.getInstance(context) }

    // State management
    var allEpisodes by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var displayedEpisodes by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var selectedSort by remember { mutableStateOf(EpisodeSortOption.NEWEST_FIRST) }
    var selectedFilter by remember { mutableStateOf(EpisodeFilter.ALL) }
    var seriesBundle by remember { mutableStateOf<SeriesBundle?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Dropdown expanded states
    var isSortDropdownExpanded by remember { mutableStateOf(false) }
    var isFilterDropdownExpanded by remember { mutableStateOf(false) }

    // Focus management
    var currentFocusLevel by remember { mutableStateOf(EpisodeFocusLevel.SORT_CHIP) }
    var focusedRow by remember { mutableStateOf(0) }
    var focusedCol by remember { mutableStateOf(0) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    val sortChipFocusRequester = remember { FocusRequester() }
    val filterChipFocusRequester = remember { FocusRequester() }
    val watchButtonFocusRequester = remember { FocusRequester() }
    val deleteButtonFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Grid state
    val lazyGridState = rememberLazyGridState()

    // Initial focus on sort dropdown
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        sortChipFocusRequester.requestFocus()
    }

    // Load episodes on first composition
    LaunchedEffect(seriesId) {
        isLoading = true
        try {
            allEpisodes = recordingRepository.getSeriesEpisodes(seriesId)
            seriesBundle = recordingRepository.getSeriesBundleById(seriesId)
            Log.d(TAG, "Loaded ${allEpisodes.size} episodes for series: $seriesTitle")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading episodes", e)
        } finally {
            isLoading = false
        }
    }

    // Apply filter and sorting
    LaunchedEffect(selectedFilter, selectedSort, allEpisodes) {
        // Step 1: Filter
        val filtered = when (selectedFilter) {
            EpisodeFilter.ALL -> allEpisodes
            EpisodeFilter.RECORDED -> allEpisodes.filter { it.status == RecordingStatus.RECORDED }
            EpisodeFilter.RECORDING -> allEpisodes.filter { it.status == RecordingStatus.RECORDING }
            EpisodeFilter.SCHEDULED -> allEpisodes.filter { it.status == RecordingStatus.SCHEDULED }
        }

        // Step 2: Sort
        displayedEpisodes = when (selectedSort) {
            EpisodeSortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.endUtc }
            EpisodeSortOption.OLDEST_FIRST -> filtered.sortedBy { it.endUtc }
            EpisodeSortOption.EPISODE_ASC -> filtered.sortedBy { it.subTitle ?: it.title }
            EpisodeSortOption.EPISODE_DESC -> filtered.sortedByDescending { it.subTitle ?: it.title }
        }

        // Reset focus when filter changes
        if (displayedEpisodes.isNotEmpty()) {
            focusedRow = 0
            focusedCol = 0
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    // Handle dropdown navigation if expanded
                    if (isSortDropdownExpanded || isFilterDropdownExpanded) {
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                isSortDropdownExpanded = false
                                isFilterDropdownExpanded = false
                                true
                            }
                            else -> false // Let dropdown handle its own navigation
                        }
                    } else {
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                onBackPressed()
                                true
                            }

                            Key.DirectionUp -> {
                                when (currentFocusLevel) {
                                    EpisodeFocusLevel.GRID -> {
                                        if (focusedRow > 0) {
                                            focusedRow--
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        } else {
                                            // First row - move to buttons
                                            currentFocusLevel = EpisodeFocusLevel.WATCH_BUTTON
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                watchButtonFocusRequester.requestFocus()
                                            }
                                        }
                                    }
                                    EpisodeFocusLevel.WATCH_BUTTON, EpisodeFocusLevel.DELETE_BUTTON -> {
                                        // Move up to chips
                                        currentFocusLevel = EpisodeFocusLevel.SORT_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            sortChipFocusRequester.requestFocus()
                                        }
                                    }
                                    EpisodeFocusLevel.SORT_CHIP, EpisodeFocusLevel.FILTER_CHIP -> {
                                        // Already at top
                                    }
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                when (currentFocusLevel) {
                                    EpisodeFocusLevel.SORT_CHIP, EpisodeFocusLevel.FILTER_CHIP -> {
                                        // Move to buttons
                                        currentFocusLevel = EpisodeFocusLevel.WATCH_BUTTON
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            watchButtonFocusRequester.requestFocus()
                                        }
                                    }
                                    EpisodeFocusLevel.WATCH_BUTTON, EpisodeFocusLevel.DELETE_BUTTON -> {
                                        // Move to grid
                                        if (displayedEpisodes.isNotEmpty()) {
                                            currentFocusLevel = EpisodeFocusLevel.GRID
                                            focusedRow = 0
                                            focusedCol = 0
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                            }
                                        }
                                    }
                                    EpisodeFocusLevel.GRID -> {
                                        val numRows = (displayedEpisodes.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                        if (focusedRow < numRows - 1) {
                                            focusedRow++
                                            val maxCol = if (focusedRow == numRows - 1) {
                                                (displayedEpisodes.size - 1) % GRID_COLUMNS
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
                                    EpisodeFocusLevel.FILTER_CHIP -> {
                                        currentFocusLevel = EpisodeFocusLevel.SORT_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            sortChipFocusRequester.requestFocus()
                                        }
                                    }
                                    EpisodeFocusLevel.DELETE_BUTTON -> {
                                        currentFocusLevel = EpisodeFocusLevel.WATCH_BUTTON
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            watchButtonFocusRequester.requestFocus()
                                        }
                                    }
                                    EpisodeFocusLevel.SORT_CHIP, EpisodeFocusLevel.WATCH_BUTTON -> {
                                        // Already at leftmost
                                    }
                                    EpisodeFocusLevel.GRID -> {
                                        if (focusedCol > 0) {
                                            focusedCol--
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        }
                                    }
                                }
                                true
                            }

                            Key.DirectionRight -> {
                                when (currentFocusLevel) {
                                    EpisodeFocusLevel.SORT_CHIP -> {
                                        currentFocusLevel = EpisodeFocusLevel.FILTER_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            filterChipFocusRequester.requestFocus()
                                        }
                                    }
                                    EpisodeFocusLevel.WATCH_BUTTON -> {
                                        currentFocusLevel = EpisodeFocusLevel.DELETE_BUTTON
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            deleteButtonFocusRequester.requestFocus()
                                        }
                                    }
                                    EpisodeFocusLevel.FILTER_CHIP, EpisodeFocusLevel.DELETE_BUTTON -> {
                                        // At rightmost position
                                    }
                                    EpisodeFocusLevel.GRID -> {
                                        val numRows = (displayedEpisodes.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                        val maxCol = if (focusedRow == numRows - 1) {
                                            (displayedEpisodes.size - 1) % GRID_COLUMNS
                                        } else {
                                            GRID_COLUMNS - 1
                                        }
                                        if (focusedCol < maxCol) {
                                            focusedCol++
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        }
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
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sx(GRID_PADDING),
                end = sx(GRID_PADDING),
                top = sy(465),  // Space for chips + info panel + buttons + 45px gap
                bottom = sy(40)
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(GRID_HORIZONTAL_GAP)),
            verticalArrangement = Arrangement.spacedBy(sy(GRID_VERTICAL_GAP))
        ) {
            // GRID ITEMS: Episode cards
            itemsIndexed(displayedEpisodes) { index, episode ->
                val row = index / GRID_COLUMNS
                val col = index % GRID_COLUMNS
                val isItemFocused = currentFocusLevel == EpisodeFocusLevel.GRID &&
                        row == focusedRow && col == focusedCol

                val focusRequester = gridFocusRequesters.getOrPut(Pair(row, col)) { FocusRequester() }

                RecordingCard(
                    recording = episode,
                    cardType = episode.toCardType(),
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = {
                        currentFocusLevel = EpisodeFocusLevel.GRID
                        focusedRow = row
                        focusedCol = col
                    },
                    onClick = {
                        Log.d(TAG, "Episode clicked: ${episode.title} - ${episode.subTitle}")
                        // TODO: Play episode or show details
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
        }

        // LAYER 2: Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(GRADIENT_OVERLAY_HEIGHT))
                .align(Alignment.TopCenter)
                .zIndex(5f)
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xFF48227C),
                        0.7f to Color(0xFF48227C),
                        1.0f to Color(0x0048227C)
                    )
                )
        )

        // LAYER 3: Header - Chips, Series Info Panel, Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
        ) {
            // ROW 1: Dropdown chips centered at top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sy(24)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort dropdown chip
                EpisodeDropdownChip(
                    prefix = "Sortuj:",
                    value = selectedSort.label,
                    isFocused = currentFocusLevel == EpisodeFocusLevel.SORT_CHIP,
                    isExpanded = isSortDropdownExpanded,
                    focusRequester = sortChipFocusRequester,
                    onFocusChange = { currentFocusLevel = EpisodeFocusLevel.SORT_CHIP },
                    onClick = { isSortDropdownExpanded = !isSortDropdownExpanded },
                    sx = ::sx,
                    sy = ::sy
                )

                Spacer(modifier = Modifier.width(sx(20)))

                // Filter dropdown chip
                EpisodeDropdownChip(
                    prefix = "Odcinki:",
                    value = selectedFilter.label,
                    isFocused = currentFocusLevel == EpisodeFocusLevel.FILTER_CHIP,
                    isExpanded = isFilterDropdownExpanded,
                    focusRequester = filterChipFocusRequester,
                    onFocusChange = { currentFocusLevel = EpisodeFocusLevel.FILTER_CHIP },
                    onClick = { isFilterDropdownExpanded = !isFilterDropdownExpanded },
                    sx = ::sx,
                    sy = ::sy
                )
            }

            Spacer(modifier = Modifier.height(sy(24)))

            // ROW 2: Series Info Panel
            seriesBundle?.let { bundle ->
                SeriesInfoPanel(
                    bundle = bundle,
                    watchButtonFocused = currentFocusLevel == EpisodeFocusLevel.WATCH_BUTTON,
                    deleteButtonFocused = currentFocusLevel == EpisodeFocusLevel.DELETE_BUTTON,
                    watchButtonFocusRequester = watchButtonFocusRequester,
                    deleteButtonFocusRequester = deleteButtonFocusRequester,
                    onWatchFocusChange = { currentFocusLevel = EpisodeFocusLevel.WATCH_BUTTON },
                    onDeleteFocusChange = { currentFocusLevel = EpisodeFocusLevel.DELETE_BUTTON },
                    onWatchClick = {
                        // Find first unwatched episode and play
                        val firstUnwatched = displayedEpisodes.firstOrNull { it.watchProgress < 1.0f }
                        Log.d(TAG, "Watch clicked - first unwatched: ${firstUnwatched?.title}")
                    },
                    onDeleteClick = {
                        // TODO: Show delete options dialog
                        Log.d(TAG, "Delete options clicked for series: ${bundle.title}")
                    },
                    sx = ::sx,
                    sy = ::sy,
                    scaleY = scaleY
                )
            }
        }

        // LAYER 4: Sort FullScreen Picker (when expanded)
        if (isSortDropdownExpanded) {
            FullScreenPicker(
                title = "Sortuj",
                options = EpisodeSortOption.values().map { it.label },
                selectedIndex = EpisodeSortOption.values().indexOf(selectedSort),
                onSelect = { index ->
                    selectedSort = EpisodeSortOption.values()[index]
                    isSortDropdownExpanded = false
                },
                onDismiss = { isSortDropdownExpanded = false },
                sx = ::sx,
                sy = ::sy,
                scaleY = scaleY
            )
        }

        // LAYER 5: Filter FullScreen Picker (when expanded)
        if (isFilterDropdownExpanded) {
            FullScreenPicker(
                title = "Filtruj",
                options = EpisodeFilter.values().map { it.label },
                selectedIndex = EpisodeFilter.values().indexOf(selectedFilter),
                onSelect = { index ->
                    selectedFilter = EpisodeFilter.values()[index]
                    isFilterDropdownExpanded = false
                },
                onDismiss = { isFilterDropdownExpanded = false },
                sx = ::sx,
                sy = ::sy,
                scaleY = scaleY
            )
        }

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ładowanie odcinków...",
                    color = Color.White,
                    fontSize = (24 * sy(1).value / 1).sp
                )
            }
        }

        // Empty state
        if (!isLoading && displayedEpisodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak odcinków do wyświetlenia",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = (24 * scaleY).sp
                )
            }
        }
    }

    // Scroll to focused item
    LaunchedEffect(focusedRow, focusedCol, currentFocusLevel) {
        if (currentFocusLevel == EpisodeFocusLevel.GRID && displayedEpisodes.isNotEmpty()) {
            val targetIndex = focusedRow * GRID_COLUMNS + focusedCol
            if (targetIndex in displayedEpisodes.indices) {
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -400
                )
            }
        }
    }
}

/**
 * Episode Dropdown Chip Component
 */
@Composable
private fun EpisodeDropdownChip(
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
 * Episode Dropdown Menu Component
 */
@Composable
private fun EpisodeDropdownMenu(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    var focusedIndex by remember { mutableStateOf(selectedIndex) }
    val focusRequesters = remember { options.map { FocusRequester() } }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequesters.getOrNull(selectedIndex)?.requestFocus()
    }

    Column(
        modifier = modifier
            .width(sx(300))
            .background(Color(0xFF2A1450), RoundedCornerShape(sx(16)))
            .padding(vertical = sy(8))
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
            .zIndex(100f)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            val isItemFocused = index == focusedIndex

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(56))
                    .background(
                        when {
                            isItemFocused -> Color(0xFF5AECD3)
                            isSelected -> Color.White.copy(alpha = 0.1f)
                            else -> Color.Transparent
                        }
                    )
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { if (it.isFocused) focusedIndex = index }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.DirectionCenter)
                        ) {
                            onSelect(index)
                            true
                        } else false
                    }
                    .focusable()
                    .padding(horizontal = sx(24)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sx(12))
                ) {
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = if (isItemFocused) Color(0xFF48227C) else Color(0xFF5AECD3),
                            fontSize = (20 * sy(1).value / 1.dp.value).sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Spacer(modifier = Modifier.width(sx(20)))
                    }

                    Text(
                        text = option,
                        color = if (isItemFocused) Color(0xFF48227C) else Color.White,
                        fontSize = (20 * sy(1).value / 1.dp.value).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Series Info Panel (Figma: node 4661-7878)
 *
 * Contains:
 * - Title (64px, Manrope Medium)
 * - Metadata row (category | duration | year | country | age | KRRIT)
 * - Description (28px, max 2 lines)
 * - Action buttons: "Oglądaj dalej" + "Opcje usuwania"
 */
@Composable
private fun SeriesInfoPanel(
    bundle: SeriesBundle,
    watchButtonFocused: Boolean,
    deleteButtonFocused: Boolean,
    watchButtonFocusRequester: FocusRequester,
    deleteButtonFocusRequester: FocusRequester,
    onWatchFocusChange: () -> Unit,
    onDeleteFocusChange: () -> Unit,
    onWatchClick: () -> Unit,
    onDeleteClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    scaleY: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sx(GRID_PADDING))
    ) {
        // Title (64px)
        Text(
            text = bundle.title,
            fontSize = (64 * scaleY).sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFEEEEEE),
            lineHeight = (72 * scaleY).sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(sy(12)))

        // Metadata row
        MetadataRow(
            category = bundle.primaryCategory,
            duration = bundle.averageEpisodeDuration,
            year = bundle.year,
            country = bundle.country,
            ageRating = bundle.ageRating,
            krritLabels = bundle.krritLabels,
            sx = sx,
            sy = sy,
            scaleY = scaleY
        )

        // Description (if available)
        bundle.description?.let { desc ->
            Spacer(modifier = Modifier.height(sy(16)))
            Text(
                text = "\u201E$desc\u201D",
                fontSize = (28 * scaleY).sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFEEEEEE),
                lineHeight = (36 * scaleY).sp,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(0.7f)
            )
        }

        Spacer(modifier = Modifier.height(sy(24)))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(16))
        ) {
            SeriesActionButton(
                text = "Oglądaj dalej",
                isFocused = watchButtonFocused,
                focusRequester = watchButtonFocusRequester,
                onFocusChange = onWatchFocusChange,
                onClick = onWatchClick,
                sx = sx,
                sy = sy,
                scaleY = scaleY
            )

            SeriesActionButton(
                text = "Opcje usuwania",
                isFocused = deleteButtonFocused,
                focusRequester = deleteButtonFocusRequester,
                onFocusChange = onDeleteFocusChange,
                onClick = onDeleteClick,
                sx = sx,
                sy = sy,
                scaleY = scaleY
            )
        }
    }
}

/**
 * Metadata Row (Figma: node 4661-7878)
 *
 * Format: category | duration | year | country | age | KRRIT labels
 * Separators: "|" with spacing
 */
@Composable
private fun MetadataRow(
    category: String?,
    duration: String,
    year: String?,
    country: String?,
    ageRating: String?,
    krritLabels: Set<KrritLabel>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    scaleY: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16))
    ) {
        val textStyle = @Composable { text: String ->
            Text(
                text = text,
                fontSize = (20 * scaleY).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEEEEEE).copy(alpha = 0.8f)
            )
        }

        val divider = @Composable {
            Text(
                text = "|",
                fontSize = (20 * scaleY).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEEEEEE).copy(alpha = 0.4f)
            )
        }

        // Category
        category?.let {
            textStyle(it)
            divider()
        }

        // Duration
        textStyle(duration)

        // Year
        year?.let {
            divider()
            textStyle(it)
        }

        // Country
        country?.let {
            divider()
            textStyle(it)
        }

        // Age rating
        ageRating?.let {
            divider()
            textStyle(it)
        }

        // KRRIT labels
        if (krritLabels.isNotEmpty()) {
            divider()
            KrritLabelSet(
                labels = krritLabels,
                sx = sx,
                sy = sy,
                scaleY = scaleY
            )
        }
    }
}

/**
 * KRRIT Label Set (Figma: node 4661-7878)
 *
 * Displays KRRIT content rating labels:
 * S - Seks (Sexual content)
 * W - Wulgaryzmy (Vulgar language)
 * N - Narkotyki (Drug references)
 * P - Przemoc (Violence)
 *
 * Each label: 20x20px square, 2px border, 4px radius
 */
@Composable
private fun KrritLabelSet(
    labels: Set<KrritLabel>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    scaleY: Float
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(sx(4))
    ) {
        // Display in fixed order: S, W, N, P
        listOf(KrritLabel.S, KrritLabel.W, KrritLabel.N, KrritLabel.P)
            .filter { it in labels }
            .forEach { label ->
                Box(
                    modifier = Modifier
                        .size(sy(20))
                        .border(
                            width = sx(2),
                            color = Color(0xFFEEEEEE).copy(alpha = 0.8f),
                            shape = RoundedCornerShape(sx(4))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.name,
                        fontSize = (12 * scaleY).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.8f)
                    )
                }
            }
    }
}

/**
 * Series Action Button (Figma: node 4661-7878)
 *
 * - Height: 72px
 * - Padding: 32px horizontal
 * - Border radius: 8px
 * - Focused: aqua background (#5AECD3)
 * - Unfocused: rgba(0,0,0,0.2)
 */
@Composable
private fun SeriesActionButton(
    text: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    scaleY: Float
) {
    val backgroundColor = if (isFocused) Color(0xFF5AECD3) else Color(0x33000000)
    val textColor = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)

    Box(
        modifier = Modifier
            .height(sy(72))
            .background(backgroundColor, RoundedCornerShape(sx(8)))
            .then(
                if (isFocused) Modifier.border(
                    width = sx(4),
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(8))
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
            .padding(horizontal = sx(32)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = (24 * scaleY).sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/**
 * Full Screen Picker Component (Figma: nodes 7204-46378, 7204-46387)
 *
 * Pełnoekranowy wybór sortowania/filtrowania z:
 * - Label po lewej stronie (X=194, Y=344)
 * - Lista opcji jako pill buttons po prawej (X=582, Y=344)
 * - Selected item: checkmark + aqua border
 * - Pill: 80px height, 100px border-radius, rgba(0,0,0,0.2) background
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
    var focusedIndex by remember { mutableStateOf(selectedIndex) }
    val focusRequesters = remember { options.map { FocusRequester() } }

    // Auto-focus on selected item when opened
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequesters.getOrNull(selectedIndex)?.requestFocus()
    }

    // Full screen overlay with gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xFF48227C),
                    0.5f to Color(0xFF48227C),
                    1.0f to Color(0xFF2A1245)
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
        // Layout: Label on left (X=194), Options on right (X=582)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sx(194), top = sy(344))
        ) {
            // Title label on left
            Text(
                text = title,
                color = Color(0xFFEEEEEE),
                fontSize = (32 * scaleY).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(sx(368))
            )

            Spacer(modifier = Modifier.width(sx(20)))

            // Options list
            Column(
                verticalArrangement = Arrangement.spacedBy(sy(24))
            ) {
                options.forEachIndexed { index, option ->
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
 * Pill Option Component (Figma design)
 *
 * - Height: 80px
 * - Border-radius: 100px (fully rounded)
 * - Background: rgba(0,0,0,0.2)
 * - Selected: checkmark icon + aqua border 8px
 * - Font: Manrope Medium 28sp
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
        // Checkmark for selected item (or spacer for alignment)
        if (isSelected) {
            // Checkmark icon (48x48)
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
            // Spacer to maintain alignment (72px = 48 icon + 24 gap)
            Spacer(modifier = Modifier.width(sx(72)))
        }

        // Pill button
        Box(
            modifier = Modifier
                .width(sx(684))
                .height(sy(80))
                .background(
                    color = Color(0x33000000),  // rgba(0,0,0,0.2)
                    shape = RoundedCornerShape(percent = 50)  // Fully rounded (100px)
                )
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = sx(8),
                            color = Color(0xFF5AECD3),  // Aqua border
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
                fontWeight = FontWeight.Medium
            )
        }
    }
}
