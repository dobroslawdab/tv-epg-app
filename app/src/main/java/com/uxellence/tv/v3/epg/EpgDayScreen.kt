package com.uxellence.tv.v3.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.channels.TvChannelData
import com.uxellence.tv.v3.repository.EpgRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Channel EPG Row - Single channel with its programs
 */
data class ChannelEpgRow(
    val channel: TvChannelData,
    val channelNumber: Int,
    val programs: List<EpgProgram>,
    val currentProgramIndex: Int,
    val lazyListState: LazyListState  // Each channel has its own scroll state
)

/**
 * EPG Day Screen - Multi-channel view with vertical navigation
 *
 * Shows multiple channels with horizontal program rows
 *
 * Navigation:
 * - UP/DOWN: Navigate between channels (3 visible: previous, current, next)
 * - LEFT/RIGHT: Navigate between programs within channel
 * - BACK: Return to initial channel/program, then exit
 *
 * Design specs:
 * - Programs displayed horizontally per channel
 * - Focused channel at center with alpha 1.0
 * - Adjacent channels with alpha 0.5
 * - Focused program at X=330px from left edge
 * - Gradient overlay (320px height) from bottom
 */

// EPG DAY CONSTANTS
private const val EPG_DAY_GRADIENT_TOP = 276       // Gradient starts at Y=276px (from Figma)
private const val EPG_DAY_GRADIENT_HEIGHT = 804    // Gradient height from Figma
private const val EPG_DAY_SCREEN_WIDTH = 1920      // Screen width (1920x1080 resolution)
private const val EPG_DAY_FOCUSED_X = 330          // Focused item position from left edge
private const val EPG_DAY_END_PADDING = EPG_DAY_SCREEN_WIDTH - EPG_DAY_FOCUSED_X  // 1590px - allows last program to scroll to focus position
private const val EPG_DAY_BOTTOM_PADDING = 90      // Space from bottom (for tape/controls)
private const val EPG_DAY_ITEM_WIDTH = 908         // Figma: 908px item width
private const val EPG_DAY_BODY_HEIGHT = 134        // Figma: 134px body height
private const val EPG_DAY_PROGRESS_HEIGHT = 8      // Figma: 8px progress bar
private const val EPG_DAY_ITEM_GAP = 48            // Figma: 48px gap between items
private const val EPG_DAY_COVER_WIDTH = 208        // Figma: 208x116px cover
private const val EPG_DAY_COVER_HEIGHT = 116
private const val EPG_DAY_COVER_BORDER = 6         // Figma: 6px aqua border when focused
private const val EPG_DAY_CHANNEL_INFO_X = 40      // Channel info position from left edge (reduced by 50%)
private const val EPG_DAY_CHANNEL_LOGO_WIDTH = 140 // Logo width (HBO logo)
private const val EPG_DAY_CHANNEL_LOGO_HEIGHT = 48 // Logo height (reduced)
private const val EPG_DAY_CHANNEL_NUMBER_WIDTH = 70 // Number box width (rectangle)
private const val EPG_DAY_CHANNEL_NUMBER_HEIGHT = 48 // Number box height (matches logo)
private const val EPG_DAY_CHANNEL_NUMBER_FONT = 28 // Number font size (reduced)
private const val EPG_DAY_CHANNEL_GAP = 12         // Gap between number and logo (reduced)

// FIXED FOCUS PATTERN - Dynamic based on expanded state
private const val EPG_DAY_FIXED_FOCUS_Y_SINGLE = 1000  // 80px from bottom (1 channel mode)
private const val EPG_DAY_FIXED_FOCUS_Y_MULTI = 960    // 120px from bottom (3+ channels mode)
private const val EPG_DAY_CHANNEL_ROW_HEIGHT = 142     // Height per channel row (body 134 + progress 8)

// VIEWPORT HEIGHT - Limits how many channels are visible at once
private const val EPG_DAY_VIEWPORT_HEIGHT_SINGLE = 142  // 1 channel (142px)
private const val EPG_DAY_VIEWPORT_HEIGHT_MULTI = 446   // 3 channels (3×142 + 2×10 gap)

@Composable
fun EpgDayScreen(
    onBackPressed: () -> Unit,  // Focus Architect: callback delegation to MainActivity
    sx: (Int) -> Dp,            // Layout Engineer: ALWAYS sx/sy parameters
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val repository = remember { EpgRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()  // For async scrolling operations

    // Multi-channel state
    var allChannelRows by remember { mutableStateOf<List<ChannelEpgRow>>(emptyList()) }
    var focusedChannelIndex by remember { mutableStateOf(0) }  // Which channel is focused
    var focusedProgramIndex by remember { mutableStateOf(0) }  // Which program within channel
    var initialChannelIndex by remember { mutableStateOf(0) }  // For BACK navigation
    var initialProgramIndex by remember { mutableStateOf(0) }  // For BACK navigation
    var isLoading by remember { mutableStateOf(true) }
    var streamUrl by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val rootFocus = remember { FocusRequester() }
    val columnScrollState = rememberLazyListState()  // For vertical scrolling between channels

    // NEW: Dynamic expand/collapse state
    var isExpanded by remember { mutableStateOf(false) }  // false = 1 channel, true = 3+ channels

    // NEW: Interface visibility state (for BACK navigation)
    var interfaceVisible by remember { mutableStateOf(true) }

    // NEW: Reference time for synchronization (instead of always using now())
    var focusedTime by remember { mutableStateOf(Instant.now()) }

    // NEW: Start channel for BACK navigation
    val startChannelIndex = remember { focusedChannelIndex }

    // NEW: Dynamic Y position based on expanded state
    val currentFocusY = if (isExpanded) {
        EPG_DAY_FIXED_FOCUS_Y_MULTI  // 120px from bottom (3+ channels)
    } else {
        EPG_DAY_FIXED_FOCUS_Y_SINGLE  // 80px from bottom (1 channel)
    }

    // NEW: Dynamic viewport height - limits visible channels
    val currentViewportHeight = if (isExpanded) {
        EPG_DAY_VIEWPORT_HEIGHT_MULTI  // 446px (3 channels)
    } else {
        EPG_DAY_VIEWPORT_HEIGHT_SINGLE  // 142px (1 channel)
    }

    // NEW: Viewport offset - positions focused channel correctly
    val viewportOffset = if (isExpanded) {
        // Expanded mode: Focused channel (position 2) has bottom edge at currentFocusY (960px)
        // Offset = bottom_edge_of_focused - 2_channels_above - gap
        currentFocusY - (2 * EPG_DAY_CHANNEL_ROW_HEIGHT) - 10  // 960 - 284 - 10 = 666px
    } else {
        // Single channel mode: bottom edge at currentFocusY (1000px)
        currentFocusY - EPG_DAY_CHANNEL_ROW_HEIGHT  // 1000 - 142 = 858px
    }

    // Current channel state (for display)
    var currentChannel by remember { mutableStateOf<TvChannelData?>(null) }
    var channelNumber by remember { mutableStateOf(1) }
    var programs by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }

    // Load EPG data for multiple channels (15 channels)
    LaunchedEffect(Unit) {
        try {
            isLoading = true

            // Initialize ChannelManager if needed
            if (!ChannelManager.isInitialized()) {
                ChannelManager.initialize(context)
            }

            val allChannels = ChannelManager.getAllChannels(includeUnavailable = false)
            android.util.Log.d("EpgDayScreen", "=== MULTI-CHANNEL EPG START ===")
            android.util.Log.d("EpgDayScreen", "Total channels: ${allChannels.size}")

            // Load channels with EPG data (skip empty channels)
            val now = Instant.now()
            val rows = mutableListOf<ChannelEpgRow>()
            var channelIndex = 0  // Track actual channel number after filtering

            for (channel in allChannels) {
                try {
                    val channelId = channel.epgId ?: channel.id
                    android.util.Log.d("EpgDayScreen", "Checking channel: ${channel.name} (ID: $channelId)")

                    val programsList = repository.getFullDayPrograms(channelId, now)

                    // SKIP channels without EPG data
                    if (programsList.isEmpty()) {
                        android.util.Log.d("EpgDayScreen", "  - Skipping ${channel.name} - no EPG data")
                        continue  // Skip to next channel
                    }

                    val sortedPrograms = programsList.sortedBy { it.startUtc }

                    // Find currently playing program for this channel
                    val currentIndex = sortedPrograms.indexOfFirst { program ->
                        !now.isBefore(program.startUtc) && now.isBefore(program.endUtc)
                    }
                    val currentProgIndex = if (currentIndex >= 0) currentIndex else 0

                    channelIndex++  // Increment only for channels with EPG

                    rows.add(
                        ChannelEpgRow(
                            channel = channel,
                            channelNumber = channelIndex,
                            programs = sortedPrograms,
                            currentProgramIndex = currentProgIndex,
                            lazyListState = LazyListState()  // Each channel has its own scroll state
                        )
                    )

                    android.util.Log.d("EpgDayScreen", "  - Loaded ${sortedPrograms.size} programs for ${channel.name}, current at index $currentProgIndex")

                    // STOP when we have 15 channels with EPG
                    if (channelIndex >= 15) {
                        break  // Exit loop
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EpgDayScreen", "Failed to load channel ${channel.name}: ${e.message}")
                }
            }

            allChannelRows = rows

            // Set initial channel (first one) as active
            if (rows.isNotEmpty()) {
                val firstRow = rows[0]
                currentChannel = firstRow.channel
                channelNumber = firstRow.channelNumber
                programs = firstRow.programs
                focusedProgramIndex = firstRow.currentProgramIndex
                initialChannelIndex = 0
                initialProgramIndex = firstRow.currentProgramIndex

                // Player stays on first channel
                streamUrl = firstRow.channel.streamUrl
                android.util.Log.d("EpgDayScreen", "Initial stream URL: $streamUrl")
            }

            android.util.Log.d("EpgDayScreen", "=== MULTI-CHANNEL EPG END: ${rows.size} channels loaded ===")
        } catch (e: Exception) {
            android.util.Log.e("EpgDayScreen", "EXCEPTION during multi-channel load: ${e.message}", e)
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        rootFocus.requestFocus()
    }

    // Synchronize X position: ALL channels scroll to their currentProgram (same X position for all)
    LaunchedEffect(allChannelRows.size) {
        if (allChannelRows.isNotEmpty()) {
            allChannelRows.forEach { channelRow ->
                if (channelRow.programs.isNotEmpty() && channelRow.currentProgramIndex in channelRow.programs.indices) {
                    // Use scrollToItem (instant, no animation) for initial positioning
                    channelRow.lazyListState.scrollToItem(
                        index = channelRow.currentProgramIndex,
                        scrollOffset = 0
                    )
                    android.util.Log.d("EpgDayScreen", "Synced channel ${channelRow.channel.name} to program index ${channelRow.currentProgramIndex}")
                }
            }
        }
    }

    // Auto-scroll programs ONLY for focused channel
    LaunchedEffect(focusedProgramIndex, focusedChannelIndex) {
        if (allChannelRows.isNotEmpty() && focusedChannelIndex in allChannelRows.indices) {
            val focusedChannel = allChannelRows[focusedChannelIndex]
            if (focusedChannel.programs.isNotEmpty()) {
                focusedChannel.lazyListState.animateScrollToItem(
                    index = focusedProgramIndex,
                    scrollOffset = 0
                )
            }
        }
    }

    // Auto-scroll LazyColumn to keep focused channel at position 2 (middle) in expanded mode
    LaunchedEffect(focusedChannelIndex, isExpanded) {
        if (allChannelRows.isEmpty()) return@LaunchedEffect

        // Scroll directly to focused channel (contentPadding handles first/last positioning)
        columnScrollState.animateScrollToItem(
            index = focusedChannelIndex,
            scrollOffset = 0
        )
    }

    // NEW: Sync ALL channels to focused program's time (works in all modes)
    // Syncs in background for 1-channel mode (ready for expand), visible effect in 3-channel mode
    LaunchedEffect(focusedTime) {  // Trigger on focusedTime change (LEFT/RIGHT navigation)
        if (allChannelRows.isEmpty()) return@LaunchedEffect  // Only check if rows exist

        android.util.Log.d("EpgDayScreen", "=== TIME SYNC: Syncing all channels to $focusedTime (mode: ${if (isExpanded) "3-channel" else "1-channel"}) ===")

        // Sync ALL channels (including focused) to focusedTime
        allChannelRows.forEachIndexed { index, channelRow ->
            val matchingIndex = channelRow.programs.indexOfFirst { program ->
                !focusedTime.isBefore(program.startUtc) &&
                focusedTime.isBefore(program.endUtc)
            }

            if (matchingIndex >= 0) {
                if (index == focusedChannelIndex) {
                    // Focused channel - ANIMATED scroll
                    channelRow.lazyListState.animateScrollToItem(matchingIndex, 0)
                    android.util.Log.d("EpgDayScreen", "  [ANIMATED] Synced ${channelRow.channel.name} to program index $matchingIndex")
                } else {
                    // Other channels - INSTANT jump (no animation)
                    channelRow.lazyListState.scrollToItem(matchingIndex, 0)
                    android.util.Log.d("EpgDayScreen", "  [INSTANT] Synced ${channelRow.channel.name} to program index $matchingIndex")
                }
            } else {
                android.util.Log.d("EpgDayScreen", "  No matching program for ${channelRow.channel.name} at $focusedTime")
            }
        }
    }

    // Helper function: Sync all channels to a specific time
    // Used by BACK navigation to ensure all channels show correct programs
    fun syncAllChannelsToTime(
        targetTime: Instant,
        animateFocused: Boolean = true,
        forceFocusedScroll: Boolean = false
    ) {
        android.util.Log.d("EpgDayScreen", "=== MANUAL SYNC: All channels to $targetTime (animate focused: $animateFocused, force: $forceFocusedScroll) ===")

        coroutineScope.launch {
            allChannelRows.forEachIndexed { index, channelRow ->
                val matchingIndex = channelRow.programs.indexOfFirst { program ->
                    !targetTime.isBefore(program.startUtc) &&
                    targetTime.isBefore(program.endUtc)
                }

                if (matchingIndex >= 0) {
                    if (index == focusedChannelIndex) {
                        // Focused channel - ANIMATED or INSTANT based on parameter
                        if (animateFocused || forceFocusedScroll) {
                            channelRow.lazyListState.animateScrollToItem(matchingIndex, 0)
                            android.util.Log.d("EpgDayScreen", "  [FOCUSED-ANIMATED] Synced ${channelRow.channel.name} to program index $matchingIndex")
                        } else {
                            channelRow.lazyListState.scrollToItem(matchingIndex, 0)
                            android.util.Log.d("EpgDayScreen", "  [FOCUSED-INSTANT] Synced ${channelRow.channel.name} to program index $matchingIndex")
                        }
                    } else {
                        // Other channels - INSTANT jump (no animation)
                        channelRow.lazyListState.scrollToItem(matchingIndex, 0)
                        android.util.Log.d("EpgDayScreen", "  [INSTANT] Synced ${channelRow.channel.name} to program index $matchingIndex")
                    }
                } else {
                    android.util.Log.d("EpgDayScreen", "  No matching program for ${channelRow.channel.name} at $targetTime")
                }
            }
        }
    }

    // Initialize and cleanup ExoPlayer for live TV with FULL AUDIO
    DisposableEffect(streamUrl) {
        val localPlayer = if (streamUrl.isNotEmpty()) {
            ExoPlayer.Builder(context).build().apply {
                val item = MediaItem.fromUri(streamUrl)
                setMediaItem(item)
                playWhenReady = true
                // Full audio for watching TV
                prepare()
            }
        } else null

        player = localPlayer

        onDispose {
            // ALWAYS stop and release player when leaving screen
            localPlayer?.stop()
            localPlayer?.release()
            player = null
            android.util.Log.d("EpgDayScreen", "Player stopped and released")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))  // Figma: #48227C purple background
            .clipToBounds()  // Clip content outside bounds (hides channels above visible area)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Multi-channel navigation: UP/DOWN for channels, LEFT/RIGHT for programs
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // PRIORITY: If interface hidden, UP/DOWN/OK restores it (shows 1-channel view with current program)
                if (!interfaceVisible && event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.Enter)) {
                    interfaceVisible = true
                    android.util.Log.d("EpgDayScreen", "Navigation restored interface (UP/DOWN/OK)")
                    return@onPreviewKeyEvent true
                }

                when (event.key) {
                    Key.DirectionUp -> {
                        if (!isExpanded) {
                            // State 1 channel: UP does nothing
                            false
                        } else {
                            // State expanded: Normal scrolling
                            if (focusedChannelIndex > 0 && allChannelRows.isNotEmpty()) {
                                focusedChannelIndex--
                                val newRow = allChannelRows[focusedChannelIndex]
                                currentChannel = newRow.channel
                                channelNumber = newRow.channelNumber
                                programs = newRow.programs

                                // Find program matching focusedTime on new channel
                                val matchingIndex = programs.indexOfFirst { program ->
                                    !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
                                }
                                focusedProgramIndex = if (matchingIndex >= 0) matchingIndex else newRow.currentProgramIndex

                                android.util.Log.d("EpgDayScreen", "UP: Channel ${newRow.channel.name}, program index $focusedProgramIndex (time: $focusedTime)")
                                true
                            } else false
                        }
                    }
                    Key.DirectionDown -> {
                        if (!isExpanded) {
                            // FIRST DOWN: Expand + move to next channel
                            isExpanded = true
                            if (focusedChannelIndex < allChannelRows.lastIndex) {
                                focusedChannelIndex++
                                val newRow = allChannelRows[focusedChannelIndex]
                                currentChannel = newRow.channel
                                channelNumber = newRow.channelNumber
                                programs = newRow.programs

                                // Find program matching focusedTime on new channel
                                val matchingIndex = programs.indexOfFirst { program ->
                                    !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
                                }
                                focusedProgramIndex = if (matchingIndex >= 0) matchingIndex else newRow.currentProgramIndex

                                android.util.Log.d("EpgDayScreen", "DOWN (EXPAND): Channel ${newRow.channel.name}, program index $focusedProgramIndex (time: $focusedTime)")
                            }
                            true
                        } else {
                            // SUBSEQUENT DOWN: Normal scrolling
                            if (focusedChannelIndex < allChannelRows.lastIndex) {
                                focusedChannelIndex++
                                val newRow = allChannelRows[focusedChannelIndex]
                                currentChannel = newRow.channel
                                channelNumber = newRow.channelNumber
                                programs = newRow.programs

                                // Find program matching focusedTime on new channel
                                val matchingIndex = programs.indexOfFirst { program ->
                                    !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
                                }
                                focusedProgramIndex = if (matchingIndex >= 0) matchingIndex else newRow.currentProgramIndex

                                android.util.Log.d("EpgDayScreen", "DOWN: Channel ${newRow.channel.name}, program index $focusedProgramIndex (time: $focusedTime)")
                                true
                            } else false
                        }
                    }
                    Key.DirectionLeft -> {
                        // Navigate to previous program + UPDATE focusedTime for sync
                        if (focusedProgramIndex > 0) {
                            focusedProgramIndex--

                            // Update focusedTime for synchronization
                            if (programs.isNotEmpty() && focusedProgramIndex in programs.indices) {
                                focusedTime = programs[focusedProgramIndex].startUtc
                            }

                            android.util.Log.d("EpgDayScreen", "LEFT: Program $focusedProgramIndex, time: $focusedTime")
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        // Navigate to next program + UPDATE focusedTime for sync
                        if (focusedProgramIndex < programs.lastIndex) {
                            focusedProgramIndex++

                            // Update focusedTime for synchronization
                            if (programs.isNotEmpty() && focusedProgramIndex in programs.indices) {
                                focusedTime = programs[focusedProgramIndex].startUtc
                            }

                            android.util.Log.d("EpgDayScreen", "RIGHT: Program $focusedProgramIndex, time: $focusedTime")
                            true
                        } else false
                    }
                    Key.Back -> {
                        when {
                            // LEVEL 1: Expanded mode → Collapse + reset to "now"
                            isExpanded -> {
                                // STEP 1: Prepare data (synchronously)
                                focusedChannelIndex = startChannelIndex
                                val startRow = allChannelRows[startChannelIndex]
                                currentChannel = startRow.channel
                                channelNumber = startRow.channelNumber
                                programs = startRow.programs

                                // Find current program (now)
                                val now = Instant.now()
                                val currentProgram = programs.firstOrNull {
                                    !now.isBefore(it.startUtc) && now.isBefore(it.endUtc)
                                }
                                focusedProgramIndex = if (currentProgram != null) {
                                    programs.indexOf(currentProgram)
                                } else {
                                    startRow.currentProgramIndex
                                }

                                // STEP 2: Update focusedTime IMMEDIATELY (triggers LaunchedEffect sync)
                                focusedTime = now

                                // STEP 3: Collapse viewport IMMEDIATELY (before animations)
                                isExpanded = false
                                interfaceVisible = true
                                android.util.Log.d("EpgDayScreen", "BACK Level 1: Collapsed viewport, starting animations")

                                // STEP 4: Jump immediately to correct position (no animations)
                                coroutineScope.launch {
                                    // 4a) Vertical jump to start channel (INSTANT - no animation)
                                    columnScrollState.scrollToItem(startChannelIndex, 0)
                                    android.util.Log.d("EpgDayScreen", "BACK: Instant vertical jump to channel $startChannelIndex")

                                    // 4b) Horizontal jump using syncAllChannelsToTime (INSTANT - no animation)
                                    // Uses same logic as LEVEL 2 - forceFocusedScroll ensures scroll even if time didn't change
                                    syncAllChannelsToTime(
                                        targetTime = now,
                                        animateFocused = false,  // NO animation - instant jump
                                        forceFocusedScroll = true
                                    )
                                    android.util.Log.d("EpgDayScreen", "BACK: Instant horizontal jump via syncAllChannelsToTime")

                                    android.util.Log.d("EpgDayScreen", "BACK Level 1: Complete (collapse → instant jump)")
                                }

                                true
                            }

                            // LEVEL 2-4: Not expanded - check current program status
                            !isExpanded -> {
                                val now = Instant.now()
                                val currentProgram = programs.firstOrNull {
                                    !now.isBefore(it.startUtc) && now.isBefore(it.endUtc)
                                }
                                val isOnCurrentProgram = currentProgram != null &&
                                    programs.getOrNull(focusedProgramIndex) == currentProgram

                                when {
                                    // LEVEL 2: Not on current program → Reset to "now"
                                    !isOnCurrentProgram -> {
                                        if (currentProgram != null) {
                                            focusedProgramIndex = programs.indexOf(currentProgram)
                                            focusedTime = now

                                            // Sync ALL channels to current time
                                            // Use forceFocusedScroll to ensure scroll even if focusedTime didn't change
                                            syncAllChannelsToTime(
                                                targetTime = now,
                                                animateFocused = true,
                                                forceFocusedScroll = true
                                            )

                                            android.util.Log.d("EpgDayScreen", "BACK Level 2: Reset to current program (NOW), synced all channels")
                                        }
                                        true
                                    }

                                    // LEVEL 3: On current program + interface visible → Hide interface
                                    interfaceVisible -> {
                                        interfaceVisible = false
                                        android.util.Log.d("EpgDayScreen", "BACK Level 3: Hide interface")
                                        true
                                    }

                                    // LEVEL 4: Interface hidden → Exit
                                    else -> {
                                        android.util.Log.d("EpgDayScreen", "BACK Level 4: Exit screen")
                                        onBackPressed()
                                        true
                                    }
                                }
                            }

                            else -> false
                        }
                    }
                    else -> false
                }
            }
    ) {
        // Live TV Player in background with FULL AUDIO
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)  // Behind gradient and EPG overlay
            )
        }

        // Gradient overlay: 3 states based on interface visibility and expanded mode
        // State 1: Single channel + interface → 61%→82% (subtle, Figma data-state="bottom")
        // State 2: Expanded mode + interface → 45%→63% (stronger, better EPG readability)
        // State 3: Interface hidden → NO GRADIENT (clean player view)
        if (interfaceVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(EPG_DAY_GRADIENT_HEIGHT))  // 804px height
                    .offset(y = sy(EPG_DAY_GRADIENT_TOP))  // Start at Y=276px
                    .background(
                        brush = if (isExpanded) {
                            // 3-channel mode: stronger gradient for EPG readability
                            Brush.verticalGradient(
                                0.45f to Color(0x0048227C),  // Transparent until 45%
                                0.63f to Color(0xFF48227C),  // Full color from 63%
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        } else {
                            // 1-channel mode: subtle gradient from Figma
                            Brush.verticalGradient(
                                0.61f to Color(0x0048227C),  // Transparent until 61%
                                0.82f to Color(0xFF48227C),  // Full color from 82%
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        }
                    )
                    .zIndex(1f)
            )
        }
        // else: No gradient when interface is hidden (clean player view)

        // EPG Multi-Channel View (LazyColumn)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ładowanie EPG...",
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * sy(1).value / 1).sp
                )
            }
        } else if (allChannelRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak kanałów do wyświetlenia",
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * sy(1).value / 1).sp
                )
            }
        } else if (interfaceVisible) {
            // LazyColumn with LIMITED VIEWPORT (1 or 3 channels max) - visible when interface is shown
            LazyColumn(
                state = columnScrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(currentViewportHeight))  // Limited height: 142px (1 ch) or 446px (3 ch)
                    .offset(y = sy(viewportOffset))  // Position focused channel at 120px from bottom
                    .zIndex(2f),
                contentPadding = PaddingValues(
                    top = if (isExpanded) sy(EPG_DAY_CHANNEL_ROW_HEIGHT + 10) else 0.dp,    // 152px space for first channel
                    bottom = if (isExpanded) sy(EPG_DAY_CHANNEL_ROW_HEIGHT + 10) else 0.dp  // 152px space for last channel
                ),
                verticalArrangement = Arrangement.spacedBy(sy(10))  // 10px gap between channels
            ) {
                itemsIndexed(allChannelRows) { channelIndex, channelRow ->
                    val isFocusedChannel = channelIndex == focusedChannelIndex

                    // Each channel = Box with ChannelInfoOverlay + LazyRow of programs
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sy(EPG_DAY_CHANNEL_ROW_HEIGHT))  // 142px (body 134 + progress 8)
                    ) {
                        // Channel Info Overlay (logo + number) - centered to programs row
                        ChannelInfoOverlay(
                            channel = channelRow.channel,
                            channelNumber = channelRow.channelNumber,
                            modifier = Modifier
                                .align(Alignment.CenterStart)  // Centered to 142px Box = centered to LazyRow
                                .padding(start = sx(EPG_DAY_CHANNEL_INFO_X))
                                .zIndex(3f),
                            sx = sx,
                            sy = sy
                        )

                        // Programs LazyRow - horizontal scrolling (centered in Box)
                        LazyRow(
                            state = channelRow.lazyListState,  // Each channel has its own scroll state
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterStart)  // Centered in Box, aligned with ChannelInfoOverlay
                                .zIndex(2f),
                            horizontalArrangement = Arrangement.spacedBy(sx(EPG_DAY_ITEM_GAP)),
                            contentPadding = PaddingValues(
                                start = sx(EPG_DAY_FOCUSED_X),  // 330px - first program starts at focus position
                                end = sx(EPG_DAY_END_PADDING)   // 1590px - allows last program to scroll to focus position
                            )
                        ) {
                            itemsIndexed(channelRow.programs) { programIndex, program ->
                                EpgDayItem(
                                    program = program,
                                    isFocused = isFocusedChannel && programIndex == focusedProgramIndex,
                                    focusedTime = focusedTime,  // NEW: Pass focusedTime for sync
                                    sx = sx,
                                    sy = sy
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
 * EPG Day Item - Single program entry (HORIZONTAL)
 *
 * Figma specs (epg_1 component):
 * - Structure: Column { Body + ProgressBar }
 * - Total size: 908x146px (134 body + 4 gap + 8 progress)
 * - Unfocused: opacity 0.5, no cover
 * - Focused: opacity 1.0, cover 208x116px with 6px aqua border
 */
@Composable
fun EpgDayItem(
    program: EpgProgram,
    isFocused: Boolean,
    focusedTime: Instant,  // NEW: Use focusedTime instead of now() for sync
    sx: (Int) -> Dp,  // Layout Engineer: ALWAYS sx/sy
    sy: (Int) -> Dp
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTime = program.startUtc.atZone(zone).format(timeFormatter)
    val endTime = program.endUtc.atZone(zone).format(timeFormatter)

    // Alpha logic: focused OR currently playing (at focusedTime) = full opacity, otherwise dimmed
    val isCurrentlyPlaying = !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
    val alpha = if (isFocused || isCurrentlyPlaying) 1f else 0.5f

    // Figma: epg_1 - Column with gap 4px
    Column(
        modifier = Modifier
            .width(sx(EPG_DAY_ITEM_WIDTH))  // Figma: 908px width
            .alpha(alpha),  // Full opacity for focused or currently playing, dimmed for past/future
        verticalArrangement = Arrangement.spacedBy(sy(4))  // Figma: gap 4px
    ) {
        // 1. Body - Figma: Row 908x134, gap 24px
        Row(
            modifier = Modifier
                .size(sx(EPG_DAY_ITEM_WIDTH), sy(EPG_DAY_BODY_HEIGHT)),  // Figma: 908x134
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(24))  // Figma: gap 24px
        ) {
            // Cover image (for focused OR currently playing programs) - Figma: 208x116, border 6px aqua
            if (isFocused || isCurrentlyPlaying) {
                val context = LocalContext.current
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(program.iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover for ${program.title}",
                    modifier = Modifier
                        .size(sx(EPG_DAY_COVER_WIDTH), sy(EPG_DAY_COVER_HEIGHT))  // Figma: 208x116px
                        .clip(RoundedCornerShape(sx(8)))  // Figma: radius 8px
                        .then(
                            if (isFocused) {
                                Modifier.border(
                                    width = sx(EPG_DAY_COVER_BORDER),  // 6px aqua border
                                    color = Color(0xFF5AECD3),  // Figma: #5AECD3 aqua
                                    shape = RoundedCornerShape(sx(8))
                                )
                            } else {
                                Modifier  // No border for non-focused covers
                            }
                        ),
                    contentScale = ContentScale.Crop,
                    loading = {
                        // Placeholder podczas ładowania
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF48227C)), // Purple placeholder
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "⏳",
                                fontSize = (32 * sy(1).value / 1).sp,
                                color = Color(0xFFEEEEEE)
                            )
                        }
                    },
                    error = {
                        // Fallback gdy obrazek się nie załaduje
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF5A227C)), // Darker purple for error
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📺",
                                fontSize = (40 * sy(1).value / 1).sp,
                                color = Color(0xFFEEEEEE)
                            )
                        }
                    }
                )
            }

            // Date/Title/Metadata column - Figma: Column gap 8px
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(sy(8))  // Figma: gap 8px
            ) {
                // Date/Title column
                Column(
                    verticalArrangement = Arrangement.spacedBy(sy(4))
                ) {
                    // Time range - Figma: Row with start – end
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(sx(4)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = startTime,
                            fontSize = (24 * sy(1).value / 1).sp,  // Figma: 24px
                            fontWeight = FontWeight.Medium,  // Figma: Medium (500)
                            color = Color(0xFFEEEEEE),  // Figma: #EEEEEE white
                            letterSpacing = 0.02.sp  // Figma: 2% letter spacing
                        )
                        Text(
                            text = "–",
                            fontSize = (24 * sy(1).value / 1).sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEEEEEE)
                        )
                        Text(
                            text = endTime,
                            fontSize = (24 * sy(1).value / 1).sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEEEEEE)
                        )
                    }

                    // Title - Figma: 48px Medium
                    Text(
                        text = program.title,
                        fontSize = (48 * sy(1).value / 1).sp,  // Figma: 48px
                        fontWeight = FontWeight.Medium,  // Figma: Medium (500)
                        color = Color(0xFFEEEEEE),  // Figma: #EEEEEE
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (64 * sy(1).value / 1).sp  // Figma: line height 1.33
                    )
                }

                // Metadata row - always visible (zgodnie z Figma)
                MetadataRow(
                    program = program,
                    sx = sx,
                    sy = sy
                )
            }
        }

        // 2. Progress Bar - Figma: 908x8px
        ProgressBar(
            program = program,
            modifier = Modifier
                .width(sx(EPG_DAY_ITEM_WIDTH))  // Figma: 908px
                .height(sy(EPG_DAY_PROGRESS_HEIGHT)),  // Figma: 8px
            sx = sx,
            sy = sy
        )
    }
}

/**
 * Progress Bar - Shows elapsed time for current program
 *
 * Figma specs:
 * - Size: 912x8px (from layout data)
 * - Background: #EEEEEE 40% opacity
 * - Fill: #EEEEEE 100% opacity (partial based on time)
 * - Radius: 4px
 */
@Composable
fun ProgressBar(
    program: EpgProgram,
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val now = Instant.now()

    // Calculate progress (0.0 to 1.0)
    val progress = when {
        now.isBefore(program.startUtc) -> 0f  // Not started yet
        now.isAfter(program.endUtc) -> 1f     // Already finished
        else -> {
            // Currently playing - calculate percentage
            val total = Duration.between(program.startUtc, program.endUtc).toMillis().toFloat()
            val elapsed = Duration.between(program.startUtc, now).toMillis().toFloat()
            (elapsed / total).coerceIn(0f, 1f)
        }
    }

    Box(modifier = modifier) {
        // Background (40% opacity) - Figma: rgba(238, 238, 238, 0.4)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Color(0x66EEEEEE),  // Figma: #EEEEEE 40%
                    RoundedCornerShape(sx(4))  // Figma: radius 4px
                )
        )

        // Progress fill (100% opacity) - Figma: #EEEEEE
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .fillMaxHeight()
                .background(
                    Color(0xFFEEEEEE),  // Figma: #EEEEEE 100%
                    RoundedCornerShape(sx(4))
                )
        )
    }
}

/**
 * Channel Info Overlay - Channel logo and number
 *
 * Displays channel logo (e.g., HBO) and channel number (e.g., 122)
 * Positioned at left side of screen, vertically centered with EPG row
 *
 * Design from screenshot:
 * - Number in purple square (#6B2C91) with white text
 * - Logo next to number (white on transparent)
 * - Row layout with 16px gap
 */
@Composable
fun ChannelInfoOverlay(
    channel: TvChannelData,
    channelNumber: Int,
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(sx(EPG_DAY_CHANNEL_GAP)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel number in dark rectangle with white border
        Box(
            modifier = Modifier
                .width(sx(EPG_DAY_CHANNEL_NUMBER_WIDTH))
                .height(sy(EPG_DAY_CHANNEL_NUMBER_HEIGHT))
                .background(
                    color = Color(0x40000000),  // Dark transparent background
                    shape = RoundedCornerShape(sx(6))
                )
                .border(
                    width = sx(2),
                    color = Color(0xFFEEEEEE),  // White border (no transparency)
                    shape = RoundedCornerShape(sx(6))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelNumber.toString(),
                fontSize = (EPG_DAY_CHANNEL_NUMBER_FONT * sy(1).value / 1).sp,
                fontWeight = FontWeight.Medium,  // Medium instead of Bold
                color = Color(0xFFEEEEEE)
            )
        }

        // Channel logo
        val context = LocalContext.current
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(channel.logoUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Logo ${channel.name}",
            modifier = Modifier
                .size(sx(EPG_DAY_CHANNEL_LOGO_WIDTH), sy(EPG_DAY_CHANNEL_LOGO_HEIGHT))
                .clip(RoundedCornerShape(sx(8))),
            contentScale = ContentScale.Fit,
            loading = {
                // Placeholder during loading
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x1AEEEEEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name,
                        fontSize = (24 * sy(1).value / 1).sp,
                        color = Color(0xFFEEEEEE),
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            error = {
                // Fallback: channel name
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x1AEEEEEE)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = channel.name,
                        fontSize = (24 * sy(1).value / 1).sp,
                        color = Color(0xFFEEEEEE),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

/**
 * Metadata Row - Season/Category/Age/KRRIT labels
 *
 * Figma specs:
 * - Font: 20sp Small, #EEEEEE 80% opacity
 * - Divider: 2px width, 24px height, #EEEEEE 80%
 * - KRRIT labels: 16sp, 4px border, centered
 * - Gap: 12.8px (from Figma data)
 */
@Composable
fun MetadataRow(
    program: EpgProgram,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(sx(13)),  // Figma: gap 12.8 ≈ 13px
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Build metadata items
        val metadataItems = buildList {
            // Season/Episode (if available in description)
            program.description?.let { desc ->
                val episodePattern = Regex("S(\\d+)E(\\d+)|sezon\\s+(\\d+).*odc.*?\\s+(\\d+)", RegexOption.IGNORE_CASE)
                val match = episodePattern.find(desc)
                if (match != null) {
                    val season = match.groupValues.getOrNull(1) ?: match.groupValues.getOrNull(3)
                    val episode = match.groupValues.getOrNull(2) ?: match.groupValues.getOrNull(4)
                    if (!season.isNullOrEmpty() && !episode.isNullOrEmpty()) {
                        add("sezon $season, odc. $episode")
                    }
                }
            }

            // Category (first category if available)
            if (program.categories.isNotEmpty()) {
                val category = program.categories.first()
                    .split(":")
                    .lastOrNull()
                    ?.trim()
                    ?: program.categories.first()
                add(category)
            }

            // Age rating (mock - would come from real data)
            add("7 lat")
        }

        // Display metadata with dividers
        metadataItems.forEachIndexed { index, item ->
            if (index > 0) {
                // Divider - Figma: 2x24px, #EEEEEE 80%
                Box(
                    modifier = Modifier
                        .width(sx(2))  // Figma: 2px width
                        .height(sy(24))  // Figma: 24px height
                        .background(Color(0xCCEEEEEE))  // Figma: #EEEEEE 80% opacity
                )
            }

            Text(
                text = item,
                fontSize = (20 * sy(1).value / 1).sp,  // Figma: 20px
                fontWeight = FontWeight.Bold,  // Figma: Bold (700)
                color = Color(0xCCEEEEEE),  // Figma: #EEEEEE 80% opacity
                lineHeight = (28 * sy(1).value / 1).sp  // Figma: line height 1.4
            )
        }

        // KRRIT labels (mock - S/W/N/P)
        Box(
            modifier = Modifier
                .width(sx(2))
                .height(sy(24))
                .background(Color(0xCCEEEEEE))
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(8))
        ) {
            listOf("S", "W", "N", "P").forEach { label ->
                KrritLabel(
                    text = label,
                    sx = sx,
                    sy = sy
                )
            }
        }
    }
}

/**
 * KRRIT Label - Single rating label
 *
 * Figma specs:
 * - Size: 20x20px
 * - Border: 2px #EEEEEE 80%
 * - Radius: 4px
 * - Font: 16sp Extra Small, centered
 */
@Composable
fun KrritLabel(
    text: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .size(sx(20), sy(20))  // Figma: 20x20px
            .clip(RoundedCornerShape(sx(4)))  // Figma: radius 4px
            .border(
                width = sx(2),  // Figma: 2px border
                color = Color(0xCCEEEEEE),  // Figma: #EEEEEE 80%
                shape = RoundedCornerShape(sx(4))
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = (16 * sy(1).value / 1).sp,  // Figma: 16px
            fontWeight = FontWeight.Bold,  // Figma: Bold (700)
            color = Color(0xCCEEEEEE),  // Figma: #EEEEEE 80%
            lineHeight = (24 * sy(1).value / 1).sp  // Figma: line height 1.5
        )
    }
}
