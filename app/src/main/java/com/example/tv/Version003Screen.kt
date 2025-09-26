package com.example.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.example.tv.components.*
import com.example.tv.version001.*
import com.example.tv.ui.theme.figmaRadialBackground
import kotlinx.coroutines.delay

/**
 * Version 0.03 Screen
 * Unified Focus System: Single focus navigation combining menu and channel content
 * 
 * Features:
 * - Top menu bar with all navigation tabs (pure rendering components)
 * - Right menu with notifications/settings icons and digital clock  
 * - Full Version001Screen channel functionality with CategoryIcon + LazyRow
 * - Single unified focus system - no focus conflicts
 * - DOWN/OK from menu transitions to first channel
 * - UP from first channel returns to menu
 * - BACK from any channel returns to menu
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Version003Screen(
    onBackPressed: (isMenuFocused: Boolean) -> Boolean = { false }
) {
    // Responsive scaling
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp
    
    // Unified TV focus manager - single point of truth for all navigation
    val tvFocusManager = rememberUnifiedTvFocusManager()
    val topMenuState = tvFocusManager.topMenuState
    val channelContentState = tvFocusManager.channelContentState
    
    // Menu items data
    val menuItems = remember {
        listOf(
            MenuItem("SEARCH", "Wyszukaj", MenuScreen.SEARCH),
            MenuItem("MOJE", "Moje", MenuScreen.MOJE),
            MenuItem("START", "Start", MenuScreen.START),
            MenuItem("TELEWIZJA", "Telewizja", MenuScreen.TELEWIZJA),
            MenuItem("KINO_PLAY", "Kino Play", MenuScreen.KINO_PLAY),
            MenuItem("WIDEO", "Wideo", MenuScreen.WIDEO),
            MenuItem("APLIKACJE", "Aplikacje", MenuScreen.APLIKACJE)
        )
    }
    
    // Focus requesters for menu items
    val menuFocusRequesters = remember(menuItems.size) { 
        menuItems.associate { it.id to FocusRequester() } 
    }
    
    // Focus requesters for right menu
    val rightMenuFocusRequesters = remember {
        mapOf(
            RightMenuItem.NOTIFICATIONS to FocusRequester(),
            RightMenuItem.SETTINGS to FocusRequester()
        )
    }
    
    // Loading state for content transitions
    var isContentLoading by remember { mutableStateOf(false) }
    
    // Channel focus requesters for smooth transitions
    val channels = listOf(
        "Polecane", "Nowości", "Filmy", "Seriale", "Sport", "Dzieci", "Dokumenty", "Muzyka"
    )
    val channelFocusRequesters = remember {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                repeat(10) { colIndex ->
                    put(Pair(rowIndex, colIndex), FocusRequester()) // Content items
                }
            }
        }
    }
    
    // State holders for content data (will be populated by CleanVersion001Content)
    var currentGridContent by remember { mutableStateOf<Map<String, List<VodContent>>>(emptyMap()) }
    var currentLazyListStates by remember { mutableStateOf<Map<Int, LazyListState>>(emptyMap()) }
    
    // Coroutine scope for key event handling
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-selection effect for menu focus changes
    LaunchedEffect(topMenuState.focusedTabId) {
        if (tvFocusManager.isAtTopMenu()) {
            delay(500) // 500ms delay before switching screen
            if (topMenuState.focusedTabId == tvFocusManager.topMenuState.focusedTabId) { // Still the same focus
                isContentLoading = true
                delay(300) // Show preloader for 300ms
                // Selection happens automatically in TV focus manager
                isContentLoading = false
            }
        }
    }
    
    // Auto-focus first menu item on startup
    LaunchedEffect(Unit) {
        delay(100)
        menuFocusRequesters[topMenuState.focusedTabId]?.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // Handle BACK key first - check with callback for MainActivity
                if (event.key == Key.Back) {
                    val handled = onBackPressed(tvFocusManager.isAtTopMenu())
                    if (!handled) {
                        // Let TV Focus Manager handle BACK (will return to appropriate level)
                        return@onPreviewKeyEvent tvFocusManager.handleKeyEvent(
                            event = event,
                            menuItems = menuItems,
                            menuFocusRequesters = menuFocusRequesters,
                            rightMenuFocusRequesters = rightMenuFocusRequesters,
                            channelFocusRequesters = channelFocusRequesters,
                            channels = channels,
                            gridContent = currentGridContent,
                            lazyListStates = currentLazyListStates,
                            coroutineScope = coroutineScope
                        )
                    }
                    return@onPreviewKeyEvent handled
                }

                // SINGLE POINT OF TRUTH - all keys handled by TV Focus Manager
                tvFocusManager.handleKeyEvent(
                    event = event,
                    menuItems = menuItems,
                    menuFocusRequesters = menuFocusRequesters,
                    rightMenuFocusRequesters = rightMenuFocusRequesters,
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    gridContent = currentGridContent,
                    lazyListStates = currentLazyListStates,
                    coroutineScope = coroutineScope
                )
            }
    ) {
        // Top Menu Bar using pure components
        PureTopMenuBar(
            menuItems = menuItems,
            selectedItemId = topMenuState.selectedTabId,
            focusedItemId = if (tvFocusManager.isAtTopMenu() && topMenuState.menuArea == MenuArea.LEFT_MENU) 
                            topMenuState.focusedTabId else "",
            focusRequesters = menuFocusRequesters,
            onMenuItemFocused = { /* Already handled by TV focus manager */ },
            sx = { sx(it) },
            sy = { sy(it) },
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // Right Menu Bar using pure components
        PureRightMenuBar(
            notificationsFocused = tvFocusManager.isAtTopMenu() && 
                                  topMenuState.menuArea == MenuArea.RIGHT_MENU &&
                                  topMenuState.rightMenuFocusedItem == RightMenuItem.NOTIFICATIONS,
            settingsFocused = tvFocusManager.isAtTopMenu() && 
                             topMenuState.menuArea == MenuArea.RIGHT_MENU &&
                             topMenuState.rightMenuFocusedItem == RightMenuItem.SETTINGS,
            notificationsFocusRequester = rightMenuFocusRequesters[RightMenuItem.NOTIFICATIONS]!!,
            settingsFocusRequester = rightMenuFocusRequesters[RightMenuItem.SETTINGS]!!,
            onNotificationsFocusChanged = { /* Already handled by unified focus manager */ },
            onSettingsFocusChanged = { /* Already handled by unified focus manager */ },
            sx = { sx(it) },
            sy = { sy(it) },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Content Area (below menu)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sy(120)) // Space for menu
        ) {
            if (isContentLoading) {
                // Preloader
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF5AECD3), // Aqua color
                        strokeWidth = sy(4).value.dp,
                        modifier = Modifier.size(sx(60), sy(60))
                    )
                }
            } else {
                // Content based on selected screen
                when (tvFocusManager.getCurrentScreen()) {
                    MenuScreen.START -> {
                        CleanVersion001Content(
                            tvFocusManager = tvFocusManager,
                            channelFocusRequesters = channelFocusRequesters,
                            channels = channels,
                            onGridContentLoaded = { gridContent -> 
                                currentGridContent = gridContent 
                            },
                            onLazyListStatesCreated = { lazyListStates ->
                                currentLazyListStates = lazyListStates
                            },
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )
                    }
                    MenuScreen.MOJE -> {
                        PlaceholderContent("Moje - Content from ChanneleScreen integrated here")
                    }
                    MenuScreen.KINO_PLAY -> {
                        PlaceholderContent("Kino Play - Vertical Channel List")
                    }
                    MenuScreen.SEARCH -> {
                        PlaceholderContent("Search - Placeholder")
                    }
                    MenuScreen.TELEWIZJA -> {
                        PlaceholderContent("Telewizja - Placeholder")
                    }
                    MenuScreen.WIDEO -> {
                        PlaceholderContent("Wideo - Placeholder")
                    }
                    MenuScreen.APLIKACJE -> {
                        PlaceholderContent("Aplikacje - Placeholder")
                    }
                }
            }
        }
    }
}

/**
 * Placeholder content for non-implemented menu screens
 */
@Composable
private fun PlaceholderContent(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Clean Version001 channel content with NO key handling
 * All navigation handled by UnifiedTvFocusManager - this is PURE UI rendering
 */
@Composable
private fun CleanVersion001Content(
    tvFocusManager: UnifiedTvFocusManager,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    onGridContentLoaded: (Map<String, List<VodContent>>) -> Unit,
    onLazyListStatesCreated: (Map<Int, LazyListState>) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val channelContentState = tvFocusManager.channelContentState
    
    // Load VOD content
    val gridContent = remember {
        GridCache.load(context) ?: run {
            val vodContentList = loadVodContentFromAssets(context)
            if (vodContentList.isNotEmpty()) {
                val newGrid = channels.associateWith { 
                    List(10) { vodContentList.random() } 
                }
                GridCache.save(context, newGrid)
                newGrid
            } else {
                emptyMap()
            }
        }
    }
    
    // Notify parent of loaded grid content
    LaunchedEffect(gridContent) {
        onGridContentLoaded(gridContent)
    }
    
    // LazyListState for each channel row
    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }
    
    // Notify parent of created lazy list states
    LaunchedEffect(lazyListStates) {
        onLazyListStatesCreated(lazyListStates)
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-reset LazyListState for unfocused rows
    LaunchedEffect(channelContentState.focusedRowIndex, channelContentState.focusedColIndex) {
        if (!tvFocusManager.isAtTopMenu()) { // Only when not in top menu
            repeat(channels.size) { rowIndex ->
                if (rowIndex != channelContentState.focusedRowIndex || channelContentState.focusedColIndex == -1) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.scrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }
    
    // Initialize channel focus when transitioning from menu
    LaunchedEffect(tvFocusManager.currentLevel) {
        if (tvFocusManager.isAtChannelMenu()) {
            delay(200)
            tvFocusManager.initializeChannelFocus(channelFocusRequesters)
        }
    }
    
    // PURE UI - NO KEY HANDLING (all handled by UnifiedTvFocusManager)
    Box(modifier = Modifier.fillMaxSize()) {
        // Channel rows layout using TV focus manager state
        CleanChannelRowsLayout(
            channels = channels,
            gridContent = gridContent,
            focusedRowIndex = channelContentState.focusedRowIndex,
            focusedColIndex = channelContentState.focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            tvFocusManager = tvFocusManager,
            lazyListStates = lazyListStates,
            sx = { sx(it) },
            sy = { sy(it) }
        )
    }
}

/**
 * Clean channel rows layout - PURE UI with no focus handling
 * All focus management handled by UnifiedTvFocusManager
 */
@Composable
private fun CleanChannelRowsLayout(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    tvFocusManager: UnifiedTvFocusManager,
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(channels.size) { rowIndex ->
            val channelName = channels[rowIndex]
            val contentForChannel = gridContent[channelName] ?: emptyList()
            
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()
            
            // Calculate Y position for each channel row (same logic as Version001Screen)
            val targetY = calculateChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                currentFocus = NavigationFocus.CHANNEL_ROWS, // Fixed for Version003
                sy = sy
            )
            
            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                label = "channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                // Use clean ChannelRow from Version001Screen but with no focus callbacks
                ChannelRow(
                    channel = channelName,
                    rowIndex = rowIndex,
                    rowContent = contentForChannel,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelFocusChange = { /* Handled by TV Focus Manager */ },
                    onChannelContentFocusChange = { _, _, _ -> /* Handled by TV Focus Manager */ },
                    currentFocus = NavigationFocus.CHANNEL_ROWS, // Fixed for Version003
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }
        }
    }
}