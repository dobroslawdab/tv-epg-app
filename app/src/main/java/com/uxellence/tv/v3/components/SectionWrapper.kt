package com.uxellence.tv.v3.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.NavigationFocus
import androidx.compose.foundation.lazy.LazyListState
import com.uxellence.tv.v3.version001.loadVodContentFromAssets
import com.uxellence.tv.v3.loadKinoPlayMoviesFromAssets
import kotlinx.coroutines.CoroutineScope

/**
 * Section wrapper components for Version002Screen navigation system
 * Each wrapper manages its own internal focus and delegates UP/DOWN to parent
 */

/**
 * Wrapper for LargeSliderComponent with navigation management
 */
@Composable
fun SliderSection(
    modifier: Modifier = Modifier,
    isSectionFocused: Boolean,
    navigationManager: VerticalNavigationManager,
    onInternalFocusChange: (Int) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    fun sx(px: Int) = (px * scaleX).dp

    // Internal focus management for slider
    var internalFocusIndex by remember { mutableStateOf(0) }
    val lastFocus = navigationManager.getLastInternalFocus(VerticalSection.SLIDER)
    
    // Restore last focus when section becomes active
    LaunchedEffect(isSectionFocused) {
        if (isSectionFocused && lastFocus != internalFocusIndex) {
            internalFocusIndex = lastFocus
        }
    }
    
    Box(
        modifier = modifier
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                
                when {
                    isSectionFocused -> {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (internalFocusIndex > 0) {
                                    internalFocusIndex--
                                    navigationManager.updateSectionInternalFocus(
                                        VerticalSection.SLIDER, 
                                        internalFocusIndex, 
                                        true
                                    )
                                    onInternalFocusChange(internalFocusIndex)
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                // Assume max 10 items in slider (can be made configurable)
                                if (internalFocusIndex < 9) {
                                    internalFocusIndex++
                                    navigationManager.updateSectionInternalFocus(
                                        VerticalSection.SLIDER,
                                        internalFocusIndex,
                                        true
                                    )
                                    onInternalFocusChange(internalFocusIndex)
                                }
                                true
                            }
                            Key.DirectionUp, Key.DirectionDown -> {
                                // Delegate vertical navigation to parent
                                false
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        LargeSliderComponent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sx(40)),
            isSectionFocused = isSectionFocused
        )
    }
}

/**
 * Wrapper for ShortcutComponent with navigation management
 */
@Composable
fun ShortcutsSection(
    modifier: Modifier = Modifier,
    isSectionFocused: Boolean,
    navigationManager: VerticalNavigationManager,
    onInternalFocusChange: (Int) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    fun sx(px: Int) = (px * scaleX).dp

    // Internal focus management for shortcuts
    var internalFocusIndex by remember { mutableStateOf(0) }
    val lastFocus = navigationManager.getLastInternalFocus(VerticalSection.SHORTCUTS)
    
    // Restore last focus when section becomes active
    LaunchedEffect(isSectionFocused) {
        if (isSectionFocused && lastFocus != internalFocusIndex) {
            internalFocusIndex = lastFocus
        }
    }
    
    Box(
        modifier = modifier
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                
                when {
                    isSectionFocused -> {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (internalFocusIndex > 0) {
                                    internalFocusIndex--
                                    navigationManager.updateSectionInternalFocus(
                                        VerticalSection.SHORTCUTS,
                                        internalFocusIndex,
                                        true
                                    )
                                    onInternalFocusChange(internalFocusIndex)
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                // Assume 5 shortcuts (based on ShortcutScreen)
                                if (internalFocusIndex < 4) {
                                    internalFocusIndex++
                                    navigationManager.updateSectionInternalFocus(
                                        VerticalSection.SHORTCUTS,
                                        internalFocusIndex,
                                        true
                                    )
                                    onInternalFocusChange(internalFocusIndex)
                                }
                                true
                            }
                            Key.DirectionUp, Key.DirectionDown -> {
                                // Delegate vertical navigation to parent
                                false
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        ShortcutComponent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sx(40)),
            isSectionFocused = isSectionFocused
        )
    }
}

/**
 * Wrapper for Channels section with ChanneleScreen-like functionality
 */
@Composable
fun ChannelsSection(
    modifier: Modifier = Modifier,
    isSectionFocused: Boolean,
    navigationManager: VerticalNavigationManager,
    onInternalFocusChange: (Pair<Int, Int>) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Channel data (simplified from ChanneleScreen)
    val channels = remember { listOf("Polecane", "Nowości", "Filmy", "Seriale") }
    val gridContent = remember {
        val vodContentList = loadVodContentFromAssets(context)
        val kinoPlayMovies = loadKinoPlayMoviesFromAssets(context)
        if (vodContentList.isNotEmpty()) {
            channels.associateWith { channelName ->
                vodContentList.shuffled().take(10)
            }
        } else {
            emptyMap()
        }
    }

    // Internal focus management for channels (row, col)
    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-1) } // Start on category icon

    // Focus requesters for channels
    val channelFocusRequesters = remember {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                put(Pair(rowIndex, -1), FocusRequester()) // Category icon
                repeat(10) { colIndex ->
                    put(Pair(rowIndex, colIndex), FocusRequester()) // Content items
                }
            }
        }
    }

    // LazyListStates for scrolling
    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Restore last focus when section becomes active
    LaunchedEffect(isSectionFocused) {
        if (isSectionFocused) {
            // Request focus on category icon of first channel
            channelFocusRequesters[Pair(0, -1)]?.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                
                when {
                    isSectionFocused -> {
                        // Handle horizontal navigation within channels
                        when (event.key) {
                            Key.DirectionLeft -> {
                                handleChannelNavigation(
                                    event.key,
                                    focusedRowIndex,
                                    focusedColIndex,
                                    channels,
                                    channelFocusRequesters,
                                    lazyListStates,
                                    coroutineScope,
                                    gridContent
                                ) { row, col ->
                                    focusedRowIndex = row
                                    focusedColIndex = col
                                    navigationManager.updateSectionInternalFocus(
                                        VerticalSection.CHANNELS,
                                        row * 100 + col, // Encode row/col as single int
                                        true
                                    )
                                    onInternalFocusChange(Pair(row, col))
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                handleChannelNavigation(
                                    event.key,
                                    focusedRowIndex,
                                    focusedColIndex,
                                    channels,
                                    channelFocusRequesters,
                                    lazyListStates,
                                    coroutineScope,
                                    gridContent
                                ) { row, col ->
                                    focusedRowIndex = row
                                    focusedColIndex = col
                                    navigationManager.updateSectionInternalFocus(
                                        VerticalSection.CHANNELS,
                                        row * 100 + col,
                                        true
                                    )
                                    onInternalFocusChange(Pair(row, col))
                                }
                                true
                            }
                            Key.DirectionUp, Key.DirectionDown -> {
                                // Check if this is channel row navigation or section navigation
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        if (focusedRowIndex > 0) {
                                            // Navigate within channels
                                            val newRowIndex = focusedRowIndex - 1
                                            val targetColIndex = if (focusedColIndex == -1) -1 else 0
                                            focusedRowIndex = newRowIndex
                                            focusedColIndex = targetColIndex
                                            channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                                            navigationManager.updateSectionInternalFocus(
                                                VerticalSection.CHANNELS,
                                                newRowIndex * 100 + targetColIndex,
                                                true
                                            )
                                            onInternalFocusChange(Pair(newRowIndex, targetColIndex))
                                            true
                                        } else {
                                            // At top of channels - delegate to parent for section navigation
                                            false
                                        }
                                    }
                                    Key.DirectionDown -> {
                                        if (focusedRowIndex < channels.size - 1) {
                                            // Navigate within channels
                                            val newRowIndex = focusedRowIndex + 1
                                            val targetColIndex = if (focusedColIndex == -1) -1 else 0
                                            focusedRowIndex = newRowIndex
                                            focusedColIndex = targetColIndex
                                            channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                                            navigationManager.updateSectionInternalFocus(
                                                VerticalSection.CHANNELS,
                                                newRowIndex * 100 + targetColIndex,
                                                true
                                            )
                                            onInternalFocusChange(Pair(newRowIndex, targetColIndex))
                                            true
                                        } else {
                                            // At bottom of channels - stay here (no section below)
                                            true
                                        }
                                    }
                                    else -> false
                                }
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        // Simplified channel rows layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sy(20))
        ) {
            repeat(channels.size) { rowIndex ->
                val channelName = channels[rowIndex]
                val contentForChannel = gridContent[channelName] ?: emptyList()

                if (contentForChannel.isNotEmpty()) {
                    // Use simplified UnifiedChannelRow from ChanneleScreen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sy(64)) // Channel row height
                    ) {
                        // Category Icon (simplified)
                        Box(
                            modifier = Modifier.offset(x = sx(80), y = sy(0))
                        ) {
                            val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                            val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]

                            if (categoryFocusRequester != null) {
                                // Simplified category display
                                Text(
                                    text = channelName,
                                    color = if (categoryIsFocused) Color(0xFF5AECD3) 
                                           else Color(0xFFEEEEEE),
                                    fontSize = sx(16).value.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .focusRequester(categoryFocusRequester)
                                        .focusable()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simplified channel navigation function
 */
private fun handleChannelNavigation(
    key: Key,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onFocusChange: (Int, Int) -> Unit
): Boolean {
    when (key) {
        Key.DirectionLeft -> {
            if (focusedColIndex == -1) {
                // Already on CategoryIcon - do nothing
                return true
            } else if (focusedColIndex == 0) {
                // Go to CategoryIcon
                onFocusChange(focusedRowIndex, -1)
                channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
            }
            return true
        }
        Key.DirectionRight -> {
            if (focusedColIndex == -1) {
                // From CategoryIcon to first content
                onFocusChange(focusedRowIndex, 0)
                channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
            }
            return true
        }
        else -> return false
    }
}