package com.uxellence.tv.v3

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
private const val GRID_VERTICAL_GAP = 45
private const val GRID_PADDING = 80
private const val GRADIENT_OVERLAY_HEIGHT = 100

// Sorting options (Figma: node 7204-46378)
private enum class SortOption(val label: String) {
    OLDEST_FIRST("Najstarsze"),
    NEWEST_FIRST("Najnowsze"),
    ALPHABETICAL("Alfabetycznie"),
    LARGEST_FIRST("Zajmujące najwięcej miejsca")
}

// Filter options for content type (Figma: node 7204-46387)
private enum class ContentFilter(val label: String, val screenTitle: String) {
    ALL("Wszystkie nagrania", "Wszystkie nagrania"),
    INDIVIDUAL("Pojedyncze nagrania", "Pojedyncze nagrania"),
    SERIES("Serie", "Serie"),
    SCHEDULED("Zaplanowane", "Zaplanowane"),
    RECORDED("Nagrane", "Nagrane"),
    WATCHED("Obejrzane", "Obejrzane")
}

// Focus levels
private enum class RecordingFocusLevel {
    SORT_CHIP,       // Sort dropdown (left)
    FILTER_CHIP,     // Filter dropdown (right)
    DELETE_BUTTON,   // "Opcje usuwania" button (left section)
    STORAGE_BUTTON,  // "Dokup przestrzeń" button (right section)
    GRID             // Recording cards
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
    var currentFocusLevel by remember { mutableStateOf(RecordingFocusLevel.GRID) }
    var focusedRow by remember { mutableStateOf(0) }
    var focusedCol by remember { mutableStateOf(0) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    val sortChipFocusRequester = remember { FocusRequester() }
    val filterChipFocusRequester = remember { FocusRequester() }
    val deleteButtonFocusRequester = remember { FocusRequester() }
    val storageButtonFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Grid state
    val lazyGridState = rememberLazyGridState()

    // Initial focus on first grid item (after data loads)
    var initialFocusSet by remember { mutableStateOf(false) }
    LaunchedEffect(displayedRecordings, initialFocusSet) {
        if (displayedRecordings.isNotEmpty() && !initialFocusSet) {
            kotlinx.coroutines.delay(150)
            gridFocusRequesters[Pair(0, 0)]?.requestFocus()
            initialFocusSet = true
        }
    }

    // Raw recordings for SCHEDULED and WATCHED filters (includes series episodes)
    var scheduledRecordings by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var watchedRecordings by remember { mutableStateOf<List<Recording>>(emptyList()) }

    // Load recordings on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            allRecordings = recordingRepository.getAllRecordings()
            scheduledRecordings = recordingRepository.getScheduledRecordings()
            watchedRecordings = recordingRepository.getWatchedRecordings()
            storageInfo = recordingRepository.getStorageInfo()
            Log.d(TAG, "Loaded ${allRecordings.size} recordings, ${scheduledRecordings.size} scheduled, ${watchedRecordings.size} watched")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recordings", e)
        } finally {
            isLoading = false
        }
    }

    // Apply filter and sorting
    LaunchedEffect(selectedFilter, selectedSort, allRecordings, scheduledRecordings, watchedRecordings) {
        // Step 1: Filter by content type
        val filtered: List<RecordingContent> = when (selectedFilter) {
            ContentFilter.ALL -> allRecordings
            // INDIVIDUAL: Only RECORDED individual recordings (no scheduled)
            ContentFilter.INDIVIDUAL -> allRecordings.filterIsInstance<RecordingContent.Individual>()
                .filter { it.recording.status == RecordingStatus.RECORDED }
            ContentFilter.SERIES -> allRecordings.filterIsInstance<RecordingContent.Series>()
            // SCHEDULED: All scheduled programs (individual films + series episodes)
            ContentFilter.SCHEDULED -> scheduledRecordings.map { RecordingContent.Individual(it) }
            ContentFilter.RECORDED -> allRecordings.filter { content ->
                when (content) {
                    is RecordingContent.Individual ->
                        content.recording.status == RecordingStatus.RECORDED
                    is RecordingContent.Series ->
                        content.bundle.recordedCount > 0
                }
            }
            // WATCHED: All watched programs (individual films + series episodes with watchProgress >= 1.0)
            ContentFilter.WATCHED -> watchedRecordings.map { RecordingContent.Individual(it) }
        }

        // Step 2: Sort the filtered list
        val sortedList = when (selectedSort) {
            SortOption.NEWEST_FIRST -> {
                // Partition: RECORDED first, then SCHEDULED
                val (recorded, scheduled) = filtered.partition { content ->
                    when (content) {
                        is RecordingContent.Individual -> content.recording.status != RecordingStatus.SCHEDULED
                        is RecordingContent.Series -> content.bundle.scheduledCount == 0
                    }
                }
                // Sort each group by date (newest first), then combine
                recorded.sortedByDescending { getContentDate(it) } +
                    scheduled.sortedByDescending { getContentDate(it) }
            }
            SortOption.OLDEST_FIRST -> filtered.sortedBy { getContentDate(it) }
            SortOption.ALPHABETICAL -> filtered.sortedBy { getContentTitle(it).lowercase() }
            SortOption.LARGEST_FIRST -> filtered.sortedByDescending { getContentDuration(it) }
        }

        displayedRecordings = sortedList

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
            .background(Color(0xFF281443))  // Same as start pages
            .onPreviewKeyEvent { event ->
                // BACK KeyUp: konsumuj zawsze — akcja idzie na KeyDown, a KeyUp
                // po przełączeniu ekranu trafiałby do nowej kompozycji i domyślny
                // handler aktywności zamykałby całą aplikację
                if ((event.key == Key.Back || event.key == Key.Escape) &&
                    event.type == KeyEventType.KeyUp
                ) {
                    return@onPreviewKeyEvent true
                }
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
                                // First BACK: scroll to top + focus first item (if not already there)
                                if (currentFocusLevel == RecordingFocusLevel.GRID && (focusedRow > 0 || focusedCol > 0)) {
                                    focusedRow = 0
                                    focusedCol = 0
                                    coroutineScope.launch {
                                        lazyGridState.animateScrollToItem(0)
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                    }
                                    true
                                } else {
                                    // Second BACK (already at 0,0) or from other focus levels: exit
                                    onBackPressed()
                                    true
                                }
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
                                            // First row - move to delete button
                                            currentFocusLevel = RecordingFocusLevel.DELETE_BUTTON
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                deleteButtonFocusRequester.requestFocus()
                                            }
                                        }
                                    }
                                    RecordingFocusLevel.DELETE_BUTTON -> {
                                        // Move from delete button to sort chip
                                        currentFocusLevel = RecordingFocusLevel.SORT_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            sortChipFocusRequester.requestFocus()
                                        }
                                    }
                                    RecordingFocusLevel.STORAGE_BUTTON -> {
                                        // Move from storage button to filter chip
                                        currentFocusLevel = RecordingFocusLevel.FILTER_CHIP
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            filterChipFocusRequester.requestFocus()
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
                                        // Move to delete button first
                                        currentFocusLevel = RecordingFocusLevel.DELETE_BUTTON
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            deleteButtonFocusRequester.requestFocus()
                                        }
                                    }
                                    RecordingFocusLevel.DELETE_BUTTON -> {
                                        // Move from delete button to grid (first column)
                                        if (displayedRecordings.isNotEmpty()) {
                                            currentFocusLevel = RecordingFocusLevel.GRID
                                            focusedRow = 0
                                            focusedCol = 0
                                            coroutineScope.launch {
                                                // Scroll to top first to prevent auto-scroll
                                                lazyGridState.scrollToItem(0)
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                                            }
                                        }
                                    }
                                    RecordingFocusLevel.STORAGE_BUTTON -> {
                                        // Move from storage button to grid (last column)
                                        if (displayedRecordings.isNotEmpty()) {
                                            currentFocusLevel = RecordingFocusLevel.GRID
                                            focusedRow = 0
                                            focusedCol = minOf(GRID_COLUMNS - 1, displayedRecordings.size - 1)
                                            coroutineScope.launch {
                                                // Scroll to top first to prevent auto-scroll
                                                lazyGridState.scrollToItem(0)
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(0, focusedCol)]?.requestFocus()
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
                                    RecordingFocusLevel.STORAGE_BUTTON -> {
                                        // Move from storage button to delete button
                                        currentFocusLevel = RecordingFocusLevel.DELETE_BUTTON
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            deleteButtonFocusRequester.requestFocus()
                                        }
                                    }
                                    RecordingFocusLevel.SORT_CHIP, RecordingFocusLevel.DELETE_BUTTON -> {
                                        // Already at leftmost position, do nothing
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
                                    RecordingFocusLevel.DELETE_BUTTON -> {
                                        // Move from delete button to storage button
                                        currentFocusLevel = RecordingFocusLevel.STORAGE_BUTTON
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(50)
                                            storageButtonFocusRequester.requestFocus()
                                        }
                                    }
                                    RecordingFocusLevel.FILTER_CHIP, RecordingFocusLevel.STORAGE_BUTTON -> {
                                        // At rightmost position, do nothing
                                    }
                                    RecordingFocusLevel.GRID -> {
                                        val numRows = (displayedRecordings.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                        val maxCol = if (focusedRow == numRows - 1) {
                                            (displayedRecordings.size - 1) % GRID_COLUMNS
                                        } else {
                                            GRID_COLUMNS - 1
                                        }
                                        if (focusedCol < maxCol) {
                                            // Move right within same row
                                            focusedCol++
                                            coroutineScope.launch {
                                                kotlinx.coroutines.delay(50)
                                                gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                            }
                                        } else if (focusedRow < numRows - 1) {
                                            // At last column, wrap to first column of next row
                                            focusedRow++
                                            focusedCol = 0
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
                top = sy(140),  // Space for dropdown chips + 40px lower header
                bottom = sy(40)
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(GRID_HORIZONTAL_GAP)),
            verticalArrangement = Arrangement.spacedBy(sy(GRID_VERTICAL_GAP))
        ) {
            // HEADER: Title + Button + Storage Info (scrolls with grid)
            item(span = { GridItemSpan(GRID_COLUMNS) }) {
                Column {
                    // Two sections - LEFT (title + button) and RIGHT (storage panel)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // LEFT SECTION: Title + Delete button (vertical layout)
                        Column {
                            // Title with count (Figma: 64sp)
                            Text(
                                text = buildAnnotatedString {
                                    append(selectedFilter.screenTitle)
                                    withStyle(SpanStyle(color = Color(0xFFEEEEEE).copy(alpha = 0.6f))) {
                                        append(" ${displayedRecordings.size}")
                                    }
                                },
                                fontSize = (64 * scaleY).sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFEEEEEE),
                                lineHeight = (72 * scaleY).sp
                            )

                            Spacer(modifier = Modifier.height(sy(24)))

                            // "Opcje usuwania" button UNDER title (Figma-styled)
                            FigmaButton(
                                text = "Opcje usuwania",
                                isFocused = currentFocusLevel == RecordingFocusLevel.DELETE_BUTTON,
                                focusRequester = deleteButtonFocusRequester,
                                onFocusChange = { currentFocusLevel = RecordingFocusLevel.DELETE_BUTTON },
                                onClick = {
                                    Log.d(TAG, "Opcje usuwania clicked")
                                    // TODO: Open delete options dialog
                                },
                                sx = ::sx,
                                sy = ::sy
                            )
                        }

                        // RIGHT SECTION: Storage info panel
                        StorageInfoPanel(
                            usedHours = 220,  // TODO: Get from repository
                            totalHours = 300, // TODO: Get from repository
                            isBuyButtonFocused = currentFocusLevel == RecordingFocusLevel.STORAGE_BUTTON,
                            buyButtonFocusRequester = storageButtonFocusRequester,
                            onBuyButtonFocusChange = { currentFocusLevel = RecordingFocusLevel.STORAGE_BUTTON },
                            onBuyButtonClick = {
                                Log.d(TAG, "Dokup przestrzeń clicked")
                                // TODO: Open storage purchase dialog
                            },
                            sx = ::sx,
                            sy = ::sy,
                            scaleY = scaleY
                        )
                    }

                }
            }

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
                        0.0f to Color(0xFF281443),
                        0.7f to Color(0xFF281443),
                        1.0f to Color(0x00281443)
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

        }

        // LAYER 4: Sort FullScreen Picker (when expanded)
        if (isSortDropdownExpanded) {
            FullScreenPicker(
                title = "Sortuj",
                options = SortOption.values().map { it.label },
                selectedIndex = SortOption.values().indexOf(selectedSort),
                onSelect = { index ->
                    selectedSort = SortOption.values()[index]
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
                options = ContentFilter.values().map { it.label },
                selectedIndex = ContentFilter.values().indexOf(selectedFilter),
                onSelect = { index ->
                    selectedFilter = ContentFilter.values()[index]
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

    // Scroll to focused row (only when row changes, not on left/right navigation)
    // Don't scroll for row 0 - header and first row should be visible without scrolling
    LaunchedEffect(focusedRow, currentFocusLevel) {
        if (currentFocusLevel == RecordingFocusLevel.GRID && displayedRecordings.isNotEmpty() && focusedRow > 0) {
            // Scroll to first item of the focused row
            // +1 because header item is at index 0
            val targetIndex = focusedRow * GRID_COLUMNS + 1
            if (focusedRow * GRID_COLUMNS in displayedRecordings.indices) {
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -200
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
                // BACK KeyUp: konsumuj zawsze — akcja idzie na KeyDown, a KeyUp
                // po przełączeniu ekranu trafiałby do nowej kompozycji i domyślny
                // handler aktywności zamykałby całą aplikację
                if ((event.key == Key.Back || event.key == Key.Escape) &&
                    event.type == KeyEventType.KeyUp
                ) {
                    return@onPreviewKeyEvent true
                }
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
                    0.0f to Color(0xFF281443),
                    0.5f to Color(0xFF281443),
                    1.0f to Color(0xFF1A0C2C)
                )
            )
            .onPreviewKeyEvent { event ->
                // BACK KeyUp: konsumuj zawsze — akcja idzie na KeyDown, a KeyUp
                // po przełączeniu ekranu trafiałby do nowej kompozycji i domyślny
                // handler aktywności zamykałby całą aplikację
                if ((event.key == Key.Back || event.key == Key.Escape) &&
                    event.type == KeyEventType.KeyUp
                ) {
                    return@onPreviewKeyEvent true
                }
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
                // BACK KeyUp: konsumuj zawsze — akcja idzie na KeyDown, a KeyUp
                // po przełączeniu ekranu trafiałby do nowej kompozycji i domyślny
                // handler aktywności zamykałby całą aplikację
                if ((event.key == Key.Back || event.key == Key.Escape) &&
                    event.type == KeyEventType.KeyUp
                ) {
                    return@onPreviewKeyEvent true
                }
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

private fun getContentDuration(content: RecordingContent): Long {
    return when (content) {
        is RecordingContent.Individual -> content.recording.durationMinutes.toLong()
        is RecordingContent.Series -> content.bundle.totalDurationMinutes.toLong()
    }
}

/**
 * Figma-styled Button (Figma design token: container-button-default)
 *
 * Used for "Opcje usuwania" and "Dokup przestrzeń" buttons
 * - Unfocused: rgba(238,238,238,0.2) background, white text
 * - Focused: #5AECD3 (aqua) background, #48227C (purple) text
 * - Height: 72px, border-radius: 8px, padding: 32px horizontal
 * - Font: Manrope Bold 24sp, letter-spacing: -0.48
 */
@Composable
private fun FigmaButton(
    text: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused)
        Color(0xFF5AECD3)  // Aqua when focused
    else
        Color(0x33EEEEEE)  // rgba(238,238,238,0.2) default

    val textColor = if (isFocused)
        Color(0xFF48227C)  // Purple when focused
    else
        Color(0xFFEEEEEE)  // White default

    Box(
        modifier = modifier
            .height(sy(72))
            .background(backgroundColor, RoundedCornerShape(sx(8)))
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .onPreviewKeyEvent { event ->
                // BACK KeyUp: konsumuj zawsze — akcja idzie na KeyDown, a KeyUp
                // po przełączeniu ekranu trafiałby do nowej kompozycji i domyślny
                // handler aktywności zamykałby całą aplikację
                if ((event.key == Key.Back || event.key == Key.Escape) &&
                    event.type == KeyEventType.KeyUp
                ) {
                    return@onPreviewKeyEvent true
                }
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
            .padding(horizontal = sx(32)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = (24 * sy(1).value / 1.dp.value).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.48).sp
        )
    }
}

/**
 * Storage Info Panel (from Figma)
 *
 * Container: #1a0c2c background, border-radius: 32px, padding: 24px
 * Contains:
 * - "Dokup przestrzeń" button (FigmaButton style)
 * - "Miejsce na Twoje nagrania" label
 * - Progress bar (527px width, 8px height)
 * - "220 h zajęte" / "pozostało 80 h" labels
 */
@Composable
private fun StorageInfoPanel(
    usedHours: Int,
    totalHours: Int,
    isBuyButtonFocused: Boolean,
    buyButtonFocusRequester: FocusRequester,
    onBuyButtonFocusChange: () -> Unit,
    onBuyButtonClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    scaleY: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFF1A0C2C), RoundedCornerShape(sx(32)))
            .padding(sx(24)),
        horizontalArrangement = Arrangement.spacedBy(sx(32)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Dokup przestrzeń" button
        FigmaButton(
            text = "Dokup przestrzeń",
            isFocused = isBuyButtonFocused,
            focusRequester = buyButtonFocusRequester,
            onFocusChange = onBuyButtonFocusChange,
            onClick = onBuyButtonClick,
            sx = sx,
            sy = sy
        )

        // Storage info text + progress bar
        Column(
            modifier = Modifier.width(sx(527)),
            verticalArrangement = Arrangement.spacedBy(sy(16))
        ) {
            // Label
            Text(
                text = "Miejsce na Twoje nagrania",
                color = Color(0xFFEEEEEE),
                fontSize = (24 * scaleY).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.48.sp
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(8))
                    .background(Color(0x66EEEEEE), RoundedCornerShape(sy(8)))
            ) {
                val progress = usedHours.toFloat() / totalHours
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Color(0xFFEEEEEE), RoundedCornerShape(sy(8)))
                )
            }

            // Labels row: "220 h zajęte" / "pozostało 80 h"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // "220 h zajęte"
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (32 * scaleY).sp,
                            letterSpacing = 0.64.sp
                        )) {
                            append("$usedHours h ")
                        }
                        withStyle(SpanStyle(
                            fontSize = (24 * scaleY).sp
                        )) {
                            append("zajęte")
                        }
                    },
                    color = Color(0xFFEEEEEE)
                )

                // "pozostało 80 h"
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(
                            fontSize = (24 * scaleY).sp
                        )) {
                            append("pozostało ")
                        }
                        withStyle(SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = (32 * scaleY).sp,
                            letterSpacing = 0.64.sp
                        )) {
                            append("${totalHours - usedHours} h")
                        }
                    },
                    color = Color(0xFFEEEEEE).copy(alpha = 0.8f)
                )
            }
        }
    }
}
