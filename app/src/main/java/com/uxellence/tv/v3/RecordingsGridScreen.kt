package com.uxellence.tv.v3

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.uxellence.tv.v3.components.RecordingContentCard
import com.uxellence.tv.v3.model.*
import com.uxellence.tv.v3.repository.RecordingRepository
import kotlinx.coroutines.launch

/**
 * RECORDINGS GRID SCREEN
 *
 * Full-screen grid view for managing recordings (Zarządzaj nagraniami)
 *
 * Features:
 * - 4-column grid layout (410x232px cards)
 * - Figma-styled dropdown chips: "Sortuj:" and "Nagrania:"
 * - Custom sorting: recorded (newest first) + scheduled (at end, newest first)
 * - Filter options: wszystkie | pojedyncze | serie
 * - Storage info panel (220h/300h)
 *
 * Based on Figma: Nagrywanie-serii → node 5819:16951
 */

// Grid layout constants (Figma: 4 columns, 40px/64px gap)
private const val GRID_COLUMNS = 4
private const val GRID_HORIZONTAL_GAP = 40
private const val GRID_VERTICAL_GAP = 64
private const val GRID_PADDING = 80
private const val GRADIENT_OVERLAY_HEIGHT = 300

// Sorting options
private enum class SortOption(val label: String) {
    NEWEST_FIRST("od najnowszych"),
    OLDEST_FIRST("od najstarszych"),
    TITLE_AZ("tytuł A-Z"),
    TITLE_ZA("tytuł Z-A")
}

// Filter options for content type - each with display label and screen title
private enum class ContentFilter(val label: String, val screenTitle: String) {
    ALL("Wszystkie", "Nagrania wszystkie"),
    INDIVIDUAL("Pojedyncze", "Pojedyncze nagrania"),
    SERIES("Serie", "Nagrane serie"),
    SCHEDULED("Zaplanowane", "Zaplanowane")
}

// Focus levels
private enum class RecordingFocusLevel {
    SORT_CHIP,     // Sort dropdown (left)
    FILTER_CHIP,   // Filter dropdown (right)
    GRID           // Recording cards
}

private const val TAG = "RecordingsGridScreen"

@Composable
fun RecordingsGridScreen(
    onBackPressed: () -> Unit,
    onSeriesClick: (String, String) -> Unit,  // seriesId, title -> navigate to drill-down
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
    var allRecordings by remember { mutableStateOf<List<RecordingContent>>(emptyList()) }
    var displayedRecordings by remember { mutableStateOf<List<RecordingContent>>(emptyList()) }
    var selectedSort by remember { mutableStateOf(SortOption.NEWEST_FIRST) }
    var selectedFilter by remember { mutableStateOf(ContentFilter.ALL) }
    var storageInfo by remember { mutableStateOf<RecordingStorageInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Dropdown expanded states
    var isSortDropdownExpanded by remember { mutableStateOf(false) }
    var isFilterDropdownExpanded by remember { mutableStateOf(false) }

    // Focus management
    var currentFocusLevel by remember { mutableStateOf(RecordingFocusLevel.SORT_CHIP) }
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

    // Load recordings on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            allRecordings = recordingRepository.getAllRecordings()
            storageInfo = recordingRepository.getStorageInfo()
            Log.d(TAG, "Loaded ${allRecordings.size} recordings")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recordings", e)
        } finally {
            isLoading = false
        }
    }

    // Apply filter and sorting
    LaunchedEffect(selectedFilter, selectedSort, allRecordings) {
        // Step 1: Filter by content type
        val filtered = when (selectedFilter) {
            ContentFilter.ALL -> allRecordings
            ContentFilter.INDIVIDUAL -> allRecordings.filterIsInstance<RecordingContent.Individual>()
            ContentFilter.SERIES -> allRecordings.filterIsInstance<RecordingContent.Series>()
            ContentFilter.SCHEDULED -> allRecordings.filter { content ->
                when (content) {
                    is RecordingContent.Individual ->
                        content.recording.status == RecordingStatus.SCHEDULED
                    is RecordingContent.Series ->
                        content.bundle.scheduledCount > 0
                }
            }
        }

        // Step 2: For SCHEDULED filter, just sort and display. For others, separate recorded from scheduled
        val finalList = if (selectedFilter == ContentFilter.SCHEDULED) {
            // Only scheduled items - just sort them
            when (selectedSort) {
                SortOption.NEWEST_FIRST -> filtered.sortedByDescending { getContentDate(it) }
                SortOption.OLDEST_FIRST -> filtered.sortedBy { getContentDate(it) }
                SortOption.TITLE_AZ -> filtered.sortedBy { getContentTitle(it).lowercase() }
                SortOption.TITLE_ZA -> filtered.sortedByDescending { getContentTitle(it).lowercase() }
            }
        } else {
            // Separate recorded/recording from scheduled
            val recordedItems = filtered.filter { content ->
                when (content) {
                    is RecordingContent.Individual ->
                        content.recording.status == RecordingStatus.RECORDED ||
                        content.recording.status == RecordingStatus.RECORDING
                    is RecordingContent.Series ->
                        content.bundle.recordedCount > 0 || content.bundle.recordingCount > 0
                }
            }

            val scheduledItems = filtered.filter { content ->
                when (content) {
                    is RecordingContent.Individual ->
                        content.recording.status == RecordingStatus.SCHEDULED
                    is RecordingContent.Series ->
                        content.bundle.scheduledCount > 0 &&
                        content.bundle.recordedCount == 0 &&
                        content.bundle.recordingCount == 0
                }
            }

            // Sort each group
            val sortedRecorded = when (selectedSort) {
                SortOption.NEWEST_FIRST -> recordedItems.sortedByDescending { getContentDate(it) }
                SortOption.OLDEST_FIRST -> recordedItems.sortedBy { getContentDate(it) }
                SortOption.TITLE_AZ -> recordedItems.sortedBy { getContentTitle(it).lowercase() }
                SortOption.TITLE_ZA -> recordedItems.sortedByDescending { getContentTitle(it).lowercase() }
            }

            val sortedScheduled = when (selectedSort) {
                SortOption.NEWEST_FIRST -> scheduledItems.sortedByDescending { getContentDate(it) }
                SortOption.OLDEST_FIRST -> scheduledItems.sortedBy { getContentDate(it) }
                SortOption.TITLE_AZ -> scheduledItems.sortedBy { getContentTitle(it).lowercase() }
                SortOption.TITLE_ZA -> scheduledItems.sortedByDescending { getContentTitle(it).lowercase() }
            }

            // Combine - recorded first, scheduled at the end
            sortedRecorded + sortedScheduled
        }

        displayedRecordings = finalList

        Log.d(TAG, "Displaying ${displayedRecordings.size} recordings for filter ${selectedFilter.label}")

        // Reset focus when filter changes
        if (displayedRecordings.isNotEmpty()) {
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
                                    RecordingFocusLevel.GRID -> {
                                        if (focusedRow > 0) {
                                            focusedRow--
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        } else {
                                            // First row - move to chips
                                            currentFocusLevel = RecordingFocusLevel.SORT_CHIP
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                sortChipFocusRequester.requestFocus()
                                            }
                                        }
                                    }
                                    RecordingFocusLevel.SORT_CHIP, RecordingFocusLevel.FILTER_CHIP -> {
                                        // Already at top, do nothing
                                    }
                                }
                                true
                            }

                            Key.DirectionDown -> {
                                when (currentFocusLevel) {
                                    RecordingFocusLevel.SORT_CHIP, RecordingFocusLevel.FILTER_CHIP -> {
                                        if (displayedRecordings.isNotEmpty()) {
                                            currentFocusLevel = RecordingFocusLevel.GRID
                                            focusedRow = 0
                                            focusedCol = 0
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                            }
                                        }
                                    }
                                    RecordingFocusLevel.GRID -> {
                                        val numRows = (displayedRecordings.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                        if (focusedRow < numRows - 1) {
                                            focusedRow++
                                            val maxCol = if (focusedRow == numRows - 1) {
                                                (displayedRecordings.size - 1) % GRID_COLUMNS
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
                                    RecordingFocusLevel.FILTER_CHIP -> {
                                        currentFocusLevel = RecordingFocusLevel.SORT_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            sortChipFocusRequester.requestFocus()
                                        }
                                    }
                                    RecordingFocusLevel.SORT_CHIP -> {
                                        // Already at leftmost chip
                                    }
                                    RecordingFocusLevel.GRID -> {
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
                                    RecordingFocusLevel.SORT_CHIP -> {
                                        currentFocusLevel = RecordingFocusLevel.FILTER_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            filterChipFocusRequester.requestFocus()
                                        }
                                    }
                                    RecordingFocusLevel.FILTER_CHIP -> {
                                        // At rightmost chip
                                    }
                                    RecordingFocusLevel.GRID -> {
                                        val numRows = (displayedRecordings.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                        val maxCol = if (focusedRow == numRows - 1) {
                                            (displayedRecordings.size - 1) % GRID_COLUMNS
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
            // GRID ITEMS: Recording cards
            itemsIndexed(displayedRecordings) { index, content ->
                val row = index / GRID_COLUMNS
                val col = index % GRID_COLUMNS
                val isItemFocused = currentFocusLevel == RecordingFocusLevel.GRID &&
                        row == focusedRow && col == focusedCol

                val focusRequester = gridFocusRequesters.getOrPut(Pair(row, col)) { FocusRequester() }

                RecordingContentCard(
                    content = content,
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = {
                        currentFocusLevel = RecordingFocusLevel.GRID
                        focusedRow = row
                        focusedCol = col
                    },
                    onClick = {
                        when (content) {
                            is RecordingContent.Individual -> {
                                Log.d(TAG, "Recording clicked: ${content.recording.title}")
                                // TODO: Play recording or show details
                            }
                            is RecordingContent.Series -> {
                                Log.d(TAG, "Series clicked: ${content.bundle.title}")
                                onSeriesClick(content.bundle.id, content.bundle.title)
                            }
                        }
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
        }

        // LAYER 2: Gradient overlay for header area
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
            // ROW 1: Dropdown chips centered at top (menu height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = sy(24)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sort dropdown chip
                FigmaDropdownChip(
                    prefix = "Sortuj:",
                    value = selectedSort.label,
                    isFocused = currentFocusLevel == RecordingFocusLevel.SORT_CHIP,
                    isExpanded = isSortDropdownExpanded,
                    focusRequester = sortChipFocusRequester,
                    onFocusChange = { currentFocusLevel = RecordingFocusLevel.SORT_CHIP },
                    onClick = { isSortDropdownExpanded = !isSortDropdownExpanded },
                    sx = ::sx,
                    sy = ::sy
                )

                Spacer(modifier = Modifier.width(sx(20)))

                // Filter dropdown chip
                FigmaDropdownChip(
                    prefix = "Nagrania:",
                    value = selectedFilter.label,
                    isFocused = currentFocusLevel == RecordingFocusLevel.FILTER_CHIP,
                    isExpanded = isFilterDropdownExpanded,
                    focusRequester = filterChipFocusRequester,
                    onFocusChange = { currentFocusLevel = RecordingFocusLevel.FILTER_CHIP },
                    onClick = { isFilterDropdownExpanded = !isFilterDropdownExpanded },
                    sx = ::sx,
                    sy = ::sy
                )
            }

            Spacer(modifier = Modifier.height(sy(32)))

            // ROW 2: Dynamic title based on selected filter
            Text(
                text = selectedFilter.screenTitle,
                fontSize = (48 * scaleY).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFEEEEEE),
                lineHeight = (56 * scaleY).sp,
                modifier = Modifier.padding(horizontal = sx(GRID_PADDING))
            )
        }

        // LAYER 4: Sort Dropdown Menu (when expanded)
        if (isSortDropdownExpanded) {
            DropdownMenu(
                options = SortOption.values().map { it.label },
                selectedIndex = SortOption.values().indexOf(selectedSort),
                onSelect = { index ->
                    selectedSort = SortOption.values()[index]
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
            DropdownMenu(
                options = ContentFilter.values().map { it.label },
                selectedIndex = ContentFilter.values().indexOf(selectedFilter),
                onSelect = { index ->
                    selectedFilter = ContentFilter.values()[index]
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
                    text = "Ładowanie nagrań...",
                    color = Color.White,
                    fontSize = (24 * scaleY).sp
                )
            }
        }

        // Empty state
        if (!isLoading && displayedRecordings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak nagrań do wyświetlenia",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = (24 * scaleY).sp
                )
            }
        }
    }

    // Scroll to focused item
    LaunchedEffect(focusedRow, focusedCol, currentFocusLevel) {
        if (currentFocusLevel == RecordingFocusLevel.GRID && displayedRecordings.isNotEmpty()) {
            val targetIndex = focusedRow * GRID_COLUMNS + focusedCol
            if (targetIndex in displayedRecordings.indices) {
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -400
                )
            }
        }
    }
}

/**
 * Figma-styled Dropdown Chip
 *
 * Design: Nagrywanie-serii node 5819:16980
 * - Background: rgba(0,0,0,0.2)
 * - Height: 64px
 * - Border radius: 64px (fully rounded)
 * - Padding: 24px horizontal
 * - Font: Manrope Bold 20px
 * - Prefix: 80% opacity, Value: 100% opacity
 * - Chevron icon: 24x24px
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
        isFocused -> Color(0xFF5AECD3)  // Aqua when focused
        else -> Color(0x33000000)  // rgba(0,0,0,0.2) default
    }

    val textColor = when {
        isFocused -> Color(0xFF48227C)  // Purple when focused
        else -> Color(0xFFEEEEEE)  // White default
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
        // Text with mixed opacity
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

        // Chevron icon (rotated when expanded)
        Text(
            text = "▼",
            color = textColor,
            fontSize = (16 * sy(1).value / 1.dp.value).sp,
            modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
        )
    }
}

/**
 * Dropdown Menu Component
 */
@Composable
private fun DropdownMenu(
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

    // Auto-focus on selected item when opened
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
                    // Checkmark for selected item
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

// Helper functions for sorting
private fun getContentDate(content: RecordingContent): java.time.Instant {
    return when (content) {
        is RecordingContent.Individual -> content.recording.endUtc
        is RecordingContent.Series -> content.bundle.lastRecordedDate ?: java.time.Instant.EPOCH
    }
}

private fun getContentTitle(content: RecordingContent): String {
    return when (content) {
        is RecordingContent.Individual -> content.recording.title
        is RecordingContent.Series -> content.bundle.title
    }
}
