package com.uxellence.tv.v3

import android.content.Context
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.uxellence.tv.v3.components.FilterChipRow
import kotlinx.coroutines.launch

// Constants (same as TopMenuScreen2)
private const val CHANNEL_LIST_CARD_WIDTH = 208
private const val CHANNEL_LIST_CARD_HEIGHT = 208
private const val CHANNEL_LIST_CARD_LOGO_SIZE = 148

// Figma design constants (node-id=6059:105092)
private const val GRADIENT_OVERLAY_HEIGHT = 600  // Gradient overlay under filters (same as TopMenuScreen2)
private const val TITLE_LEFT_PADDING = 0        // Title position from left - x=0, przy lewej krawędzi
private const val GRID_LEFT_PADDING = 80        // Grid content padding left
private const val GRID_RIGHT_PADDING = 80       // Grid content padding right
private const val GRID_HORIZONTAL_GAP = 40      // Gap between cards horizontally (Figma LazyRow gap)
private const val GRID_VERTICAL_GAP = 40        // Gap between cards vertically

// Grid layout constants
private const val GRID_COLUMNS = 6              // Number of columns in grid

// Focus management (Focus Architect pattern)
private enum class FocusLevel {
    FILTERS,  // Focus on filter chips (Search, Sort, Category)
    GRID      // Focus on channel grid
}

/**
 * Channel Grid Screen - Full-screen grid view of all TV channels
 * Based on Figma design: node-id=6059:105092
 *
 * Features:
 * - Uniform background (figmaRadialBackground)
 * - Centered filter chips (Search + Sort + Category)
 * - Title "Lista kanałów TV" at x=98px
 * - 6-column grid layout with 40px gaps
 * - Channel cards: 208x208px (from TELEWIZJA section)
 * - D-pad navigation support
 */
@Composable
fun ChannelGridScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    screenTitle: String = "Lista kanałów TV",
    initialCategory: String = "Wszystkie",
    channelFilter: ((TvChannel) -> Boolean)? = null,
    preloadedChannels: List<TvChannel>? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Responsive scaling
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // State management
    var allChannels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var filteredChannels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var selectedSort by remember { mutableStateOf("Po numerze") }
    var selectedCategory by remember { mutableStateOf("Wszystkie") }
    var searchQuery by remember { mutableStateOf("") }

    // Focus management (Focus Architect pattern - single focus system)
    var currentFocusLevel by remember { mutableStateOf(FocusLevel.GRID) }  // ✅ Start on grid - no filters navigation
    var focusedRow by remember { mutableStateOf(0) }
    var focusedCol by remember { mutableStateOf(0) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    var filterFirstFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Grid state for scrolling
    val lazyGridState = rememberLazyGridState()

    // Load channels on first composition
    LaunchedEffect(preloadedChannels) {
        allChannels = if (preloadedChannels != null) {
            // Use preloaded channels from category-specific JSON (e.g., dladzieci.json, sport.json)
            preloadedChannels
        } else {
            // Fallback: Load from lista_kanalow.json and apply filter
            val loadedChannels = loadChannelsFromJson(context, "lista_kanalow.json", 1)
            if (channelFilter != null) {
                loadedChannels.filter(channelFilter)
            } else {
                loadedChannels
            }
        }
        filteredChannels = allChannels
    }

    // Set initial category from navigation parameters
    LaunchedEffect(initialCategory) {
        selectedCategory = initialCategory
    }

    // Apply filters and sorting whenever they change
    LaunchedEffect(selectedCategory, selectedSort, searchQuery, allChannels) {
        var result = allChannels

        // Apply category filter
        result = when (selectedCategory) {
            "Wszystkie" -> result
            "Ogólne" -> result.filter { (it.channelNumber ?: 0) in 1..100 }
            "Sport" -> result.filter { (it.channelNumber ?: 0) in 700..805 }
            "Dzieci" -> result.filter { (it.channelNumber ?: 0) in 600..687 }
            "Dokumenty" -> result.filter { (it.channelNumber ?: 0) in 500..987 }
            "Filmy i seriale" -> result.filter { (it.channelNumber ?: 0) in 50..529 }
            "Informacyjne" -> result.filter { (it.channelNumber ?: 0) in 200..209 }
            else -> result
        }

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            result = result.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.channelNumber.toString().contains(searchQuery)
            }
        }

        // Apply sorting
        result = when (selectedSort) {
            "Po numerze" -> result.sortedBy { it.channelNumber ?: 0 }
            "Alfabetycznie" -> result.sortedBy { it.name }
            "Po kategorii" -> result.sortedBy { it.channelNumber ?: 0 }  // Same as by number
            else -> result
        }

        filteredChannels = result

        // Reset focus to first item when filters change
        // ✅ CRASH FIX: Validate list is not empty before focusing
        if (filteredChannels.isNotEmpty()) {
            focusedRow = 0
            focusedCol = 0
        } else {
            // Empty list - return focus to filters
            // ✅ CRASH FIX: LaunchedEffect in FilterChipRow handles focus
            currentFocusLevel = FocusLevel.FILTERS
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))  // Same as TopMenuScreen2 - solid purple
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            onBackPressed()
                            true
                        }
                        Key.DirectionUp -> {
                            // Only handle if focus is on grid
                            if (currentFocusLevel == FocusLevel.GRID) {
                                if (focusedRow > 0) {
                                    // Move up within grid
                                    focusedRow--
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(50)
                                        gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                    }
                                } else {
                                    // At first row - STAY ON GRID (no filters navigation)
                                    // Fokus pozostaje na pierwszym wierszu
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            // Only handle if focus is on grid
                            if (currentFocusLevel == FocusLevel.GRID) {
                                val numRows = (filteredChannels.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                if (focusedRow < numRows - 1) {
                                    focusedRow++
                                    // Check if next row is shorter (last row)
                                    val maxCol = if (focusedRow == numRows - 1) {
                                        (filteredChannels.size - 1) % GRID_COLUMNS
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
                            // Only handle if focus is on grid
                            if (currentFocusLevel == FocusLevel.GRID) {
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
                            // Only handle if focus is on grid
                            if (currentFocusLevel == FocusLevel.GRID) {
                                val numRows = (filteredChannels.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                                val maxCol = if (focusedRow == numRows - 1) {
                                    (filteredChannels.size - 1) % GRID_COLUMNS
                                } else {
                                    GRID_COLUMNS - 1
                                }

                                if (focusedCol < maxCol) {
                                    // Move right within row
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
        // LAYER 1: Scrollable content (z-index: 0f default)
        // ✅ NAGŁÓWEK FIX: LazyVerticalGrid bezpośrednio, nagłówek jako item()
        LazyVerticalGrid(
            state = lazyGridState,  // ✅ Grid state for scroll control
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sx(GRID_LEFT_PADDING),
                end = sx(GRID_RIGHT_PADDING),
                top = sy(98),  // Space for pinned filters at top (34px top + 64px height)
                bottom = sy(40)
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(GRID_HORIZONTAL_GAP)),  // Figma: 40px gap
            verticalArrangement = Arrangement.spacedBy(sy(GRID_VERTICAL_GAP))  // Figma: 40px gap
        ) {
            // HEADER: Title "Lista kanałów TV" (spans all columns)
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(GRID_COLUMNS) }) {
                Column {
                    Spacer(modifier = Modifier.height(sy(50)))

                    // Title - Figma: x=98px, y=171px (Roboto Bold 40sp, #DBDBDB)
                    Text(
                        text = screenTitle,
                        fontSize = (40 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDBDBDB),  // Figma: #DBDBDB
                        lineHeight = (38.28 * sy(1).value / 1).sp,  // Figma: line-height 0.957 * 40 = 38.28
                        modifier = Modifier.padding(start = sx(TITLE_LEFT_PADDING))
                    )

                    Spacer(modifier = Modifier.height(sy(50)))
                }
            }

            // GRID ITEMS: Channel cards
            itemsIndexed(filteredChannels) { index, channel ->
                val row = index / GRID_COLUMNS
                val col = index % GRID_COLUMNS
                val isItemFocused = currentFocusLevel == FocusLevel.GRID &&
                                   row == focusedRow && col == focusedCol

                val focusRequester = gridFocusRequesters.getOrPut(Pair(row, col)) { FocusRequester() }

                ChannelListCard(
                    channel = channel,
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = {
                        currentFocusLevel = FocusLevel.GRID
                        focusedRow = row
                        focusedCol = col
                    },
                    onClick = {
                        android.util.Log.d("CHANNEL_GRID", "Channel clicked: ${channel.name}")
                        // TODO: Navigate to EPG Day or channel detail
                    },
                    currentFocusLevel = currentFocusLevel,
                    sx = ::sx,
                    sy = ::sy
                )
            }
        }

        // LAYER 2: Gradient overlay from top (same as TopMenuScreen2)
        // Solid purple at top (0-30%), fades to transparent (30-60%)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(GRADIENT_OVERLAY_HEIGHT))  // 600px height
                .align(Alignment.TopCenter)
                .zIndex(5f)  // Above content (0f), below filters (10f)
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xFF48227C),    // 0%: Solid purple at top
                        0.3f to Color(0xFF48227C),    // 30%: Still solid purple
                        0.6f to Color(0x0048227C),    // 60%: Transparent purple
                        startY = 0f,
                        endY = sy(GRADIENT_OVERLAY_HEIGHT).value
                    )
                )
        )

        // LAYER 3: Pinned filters at top (z-index: 10f)
        FilterChipRow(
            selectedSort = selectedSort,
            onSortChange = { selectedSort = it },
            selectedCategory = selectedCategory,
            onCategoryChange = { selectedCategory = it },
            onSearchClick = {
                android.util.Log.d("CHANNEL_GRID", "Search clicked")
            },
            onNavigateDown = {
                // Navigate from filters to grid
                // ✅ FOCUS ARCHITECT: Validate grid is ready before transition
                android.util.Log.d("ChannelGridScreen", "onNavigateDown - filteredChannels.size=${filteredChannels.size}, currentLevel=$currentFocusLevel")
                if (filteredChannels.isNotEmpty()) {
                    currentFocusLevel = FocusLevel.GRID
                    focusedRow = 0
                    focusedCol = 0
                    android.util.Log.d("ChannelGridScreen", "Requesting focus on grid (0,0)")
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(50)
                        gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                    }
                } else {
                    android.util.Log.d("ChannelGridScreen", "Grid is EMPTY - cannot navigate down")
                }
            },
            onNavigateRight = {
                // ✅ FOCUS ARCHITECT: No RIGHT section exists, return false for wrap-around
                false
            },
            onFocusReady = { focusRequester ->
                // ✅ FOCUS ARCHITECT: Store filter FocusRequester for transitions
                filterFirstFocusRequester = focusRequester
            },
            isFiltersFocused = currentFocusLevel == FocusLevel.FILTERS,
            sx = ::sx,
            sy = ::sy,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)  // Above gradient (5f) and content (0f)
        )
    }

    // ✅ FOCUS ARCHITECT: Conditional initial focus based on level
    LaunchedEffect(filteredChannels, filterFirstFocusRequester) {
        if (filteredChannels.isNotEmpty()) {
            launch {
                kotlinx.coroutines.delay(100)
                when (currentFocusLevel) {
                    FocusLevel.FILTERS -> filterFirstFocusRequester?.requestFocus()
                    FocusLevel.GRID -> gridFocusRequesters[Pair(0, 0)]?.requestFocus()
                }
            }
        }
    }

    // ✅ FIXED FOCUS Y: Smooth scroll to keep focused row centered
    LaunchedEffect(focusedRow, focusedCol, currentFocusLevel) {
        if (currentFocusLevel == FocusLevel.GRID && filteredChannels.isNotEmpty()) {
            // +1 for header item
            val targetIndex = focusedRow * GRID_COLUMNS + focusedCol + 1
            if (targetIndex >= 0 && targetIndex < filteredChannels.size + 1) {
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -400  // Offset to center focused row (adjust as needed)
                )
            }
        }
    }
}

/**
 * Channel card component (copied from TopMenuScreen2)
 * 208x208px square card with logo and channel number
 */
@Composable
private fun ChannelListCard(
    channel: TvChannel,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit = {},
    currentFocusLevel: FocusLevel,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Column(
        modifier = Modifier
            .size(sx(CHANNEL_LIST_CARD_WIDTH), sy(CHANNEL_LIST_CARD_HEIGHT))
            .clip(RoundedCornerShape(sx(12)))
            .background(Color(0xFF000000).copy(alpha = if (isFocused) 0.3f else 0.1f))
            .border(
                width = if (isFocused) sx(4) else 0.dp,
                color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                shape = RoundedCornerShape(sx(12))
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(enabled = currentFocusLevel == FocusLevel.GRID)  // ✅ DUAL FOCUS FIX
            .padding(bottom = sy(20)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(8))
    ) {
        // Logo (148x148)
        Box(
            modifier = Modifier.size(sx(CHANNEL_LIST_CARD_LOGO_SIZE), sy(CHANNEL_LIST_CARD_LOGO_SIZE)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Channel number label
        Box(
            modifier = Modifier
                .height(sy(54))
                .widthIn(min = sx(52))
                .clip(RoundedCornerShape(sx(4)))
                .border(
                    width = sx(2),
                    color = Color(0xFFEEEEEE).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(sx(4))
                )
                .padding(horizontal = sx(12)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (channel.channelNumber ?: 0).toString().padStart(2, '0'),
                fontSize = (24 * sy(1).value / 1).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (28 * sy(1).value / 1).sp,
                letterSpacing = 0.48.sp,
                color = Color(0xFFEEEEEE)
            )
        }
    }
}

/**
 * Helper function to load channels from JSON file
 * (Copied from TopMenuScreen2)
 */
private fun loadChannelsFromJson(context: Context, fileName: String, startNumber: Int): List<TvChannel> {
    return try {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(jsonString)
        val channels = mutableListOf<TvChannel>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            var logoUrl = obj.getString("logo")

            // Fix double "https:https://" prefix
            if (logoUrl.startsWith("https:https://")) {
                logoUrl = logoUrl.removePrefix("https:")
            }

            channels.add(
                TvChannel(
                    name = obj.getString("name"),
                    logo = logoUrl,
                    epgId = obj.optString("id", null),
                    channelNumber = startNumber + i
                )
            )
        }

        // Deduplicate by name
        channels.distinctBy { it.name }
            .mapIndexed { index, channel -> channel.copy(channelNumber = startNumber + index) }
    } catch (e: Exception) {
        android.util.Log.e("CHANNEL_GRID", "Error loading channels from $fileName: ${e.message}")
        emptyList()
    }
}
