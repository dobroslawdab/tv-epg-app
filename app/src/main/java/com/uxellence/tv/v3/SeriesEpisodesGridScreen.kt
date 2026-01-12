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
private const val GRADIENT_OVERLAY_HEIGHT = 300

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
                                            // First row - move to chips
                                            currentFocusLevel = EpisodeFocusLevel.SORT_CHIP
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                sortChipFocusRequester.requestFocus()
                                            }
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
                                    EpisodeFocusLevel.SORT_CHIP -> {
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
                                    EpisodeFocusLevel.FILTER_CHIP -> {
                                        // At rightmost chip
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
                top = sy(200),  // Space for dropdowns + title
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

        // LAYER 3: Header - Dropdowns at top (centered), then dynamic title
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

            Spacer(modifier = Modifier.height(sy(32)))

            // ROW 2: Dynamic title based on selected filter
            Text(
                text = "$seriesTitle - ${selectedFilter.titleSuffix}",
                fontSize = (48 * scaleY).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEEEEEE),
                lineHeight = (56 * scaleY).sp,
                modifier = Modifier.padding(horizontal = sx(GRID_PADDING))
            )
        }

        // LAYER 4: Sort Dropdown Menu (when expanded)
        if (isSortDropdownExpanded) {
            EpisodeDropdownMenu(
                options = EpisodeSortOption.values().map { it.label },
                selectedIndex = EpisodeSortOption.values().indexOf(selectedSort),
                onSelect = { index ->
                    selectedSort = EpisodeSortOption.values()[index]
                    isSortDropdownExpanded = false
                },
                onDismiss = { isSortDropdownExpanded = false },
                sx = ::sx,
                sy = ::sy,
                modifier = Modifier
                    .padding(top = sy(100))
                    .align(Alignment.TopCenter)
                    .offset(x = sx(-120))
            )
        }

        // LAYER 5: Filter Dropdown Menu (when expanded)
        if (isFilterDropdownExpanded) {
            EpisodeDropdownMenu(
                options = EpisodeFilter.values().map { it.label },
                selectedIndex = EpisodeFilter.values().indexOf(selectedFilter),
                onSelect = { index ->
                    selectedFilter = EpisodeFilter.values()[index]
                    isFilterDropdownExpanded = false
                },
                onDismiss = { isFilterDropdownExpanded = false },
                sx = ::sx,
                sy = ::sy,
                modifier = Modifier
                    .padding(top = sy(100))
                    .align(Alignment.TopCenter)
                    .offset(x = sx(120))
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
