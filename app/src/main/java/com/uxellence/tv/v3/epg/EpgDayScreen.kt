package com.uxellence.tv.v3.epg

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
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
import com.uxellence.tv.v3.PlayerInterfaceManager
import com.uxellence.tv.v3.PlayerInterfaceState
import com.uxellence.tv.v3.ZappingBarOverlay
import com.uxellence.tv.v3.ZappingMode
import com.uxellence.tv.v3.epg.ZappingBarController
import com.uxellence.tv.v3.epg.BackNavigationController
import com.uxellence.tv.v3.epg.EpgNavigationController
import com.uxellence.tv.v3.epg.NavigationDirection
import com.uxellence.tv.v3.epg.TimeshiftController
import com.uxellence.tv.v3.epg.FrameCaptureManager
import com.google.android.exoplayer2.DefaultLoadControl
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
import android.view.KeyEvent

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
    onNavigateToPipMode: (ExoPlayer?, String) -> Unit = { _, _ -> },  // PIP callback
    initialChannelId: String? = null,  // Optional: Start EPG on specific channel (for "Moja lista kanałów")
    showTopMenuOverlay: Boolean = false,  // Show overlay only when launched from startup mode (MODE_EPG_DAY)
    shouldNavigateHomeWithPip: Boolean = false,  // HOME button PIP navigation request from MainActivity
    onHomeNavigationComplete: () -> Unit = {},    // Callback to reset flag after HOME navigation
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
    var playerResetTrigger by remember { mutableStateOf(0) }  // Force player recreation when URL doesn't change
    var isPipMode by remember { mutableStateOf(false) }  // Track PIP transfer - don't release player
    val rootFocus = remember { FocusRequester() }
    val columnScrollState = rememberLazyListState()  // For vertical scrolling between channels

    // NEW: Dynamic expand/collapse state
    var isExpanded by remember { mutableStateOf(false) }  // false = 1 channel, true = 3+ channels

    // NEW: Interface visibility state (for BACK navigation)
    // Start with GUI hidden in startup mode (showTopMenuOverlay=true)
    // GUI visible when launched from menu (showTopMenuOverlay=false)
    var interfaceVisible by remember { mutableStateOf(!showTopMenuOverlay) }

    // Track manual hide (BACK button) vs automatic hide (Zapping Bar auto-hide)
    // This prevents GUI from re-appearing after BACK button hide
    var wasManuallyHidden by remember { mutableStateOf(false) }

    // TOP MENU OVERLAY: Show on startup, hide when GUI appears
    var overlayVisible by remember { mutableStateOf(true) }

    // TIMESHIFT: Seek state for live stream rewind with thumbnail preview
    var isTimeshiftActive by remember { mutableStateOf(false) }
    var timeshiftOffsetMs by remember { mutableLongStateOf(0L) }
    var isAtLiveEdge by remember { mutableStateOf(true) }
    var playerViewRef by remember { mutableStateOf<com.google.android.exoplayer2.ui.PlayerView?>(null) }
    val frameCaptureManager = remember { FrameCaptureManager() }

    // Set initial PlayerInterfaceManager state based on launch mode
    LaunchedEffect(showTopMenuOverlay) {
        if (showTopMenuOverlay) {
            // Startup mode: hide GUI initially (user sees TopMenuOverlay, GUI appears on key press)
            PlayerInterfaceManager.hide()
            android.util.Log.d("EpgDayScreen", "Startup mode: GUI hidden initially")
        } else {
            // Menu mode: show GUI immediately (expected behavior when launched from TELEWIZJA)
            PlayerInterfaceManager.showGui()
            android.util.Log.d("EpgDayScreen", "Menu mode: GUI visible initially")
        }
    }

    // NEW: Reference time for synchronization (instead of always using now())
    var focusedTime by remember { mutableStateOf(Instant.now()) }

    // NEW: Start channel for BACK navigation and channel switching
    var startChannelIndex by remember { mutableStateOf(0) }

    // ZAPPING BAR: Subscribe to PlayerInterfaceManager state
    val interfaceState by PlayerInterfaceManager.state.collectAsState()

    // ZAPPING BAR: Initialize PlayerInterfaceManager
    LaunchedEffect(Unit) {
        // Set EPG repository reference
        PlayerInterfaceManager.epgRepository = repository
    }

    // ZAPPING BAR: Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            PlayerInterfaceManager.reset()
        }
    }

    // ZAPPING BAR: Synchronize interfaceVisible with PlayerInterfaceManager.state
    // This ensures mutual exclusion: GUI hides when Zapping Bar shows
    LaunchedEffect(interfaceState) {
        when (interfaceState) {
            is PlayerInterfaceState.ZappingBarVisible -> {
                // Zapping Bar visible → hide GUI
                interfaceVisible = false
                android.util.Log.d("EpgDayScreen", "Zapping Bar visible → GUI hidden")
            }
            is PlayerInterfaceState.GuiVisible -> {
                // GUI should be visible
                interfaceVisible = true
                android.util.Log.d("EpgDayScreen", "GUI visible → showing interface")
            }
            is PlayerInterfaceState.Hidden -> {
                // After Zapping Bar auto-hide: restore focus to keep CH+/CH- working
                // GUI stays hidden (clean player) until user presses UP/DOWN/OK
                if (!wasManuallyHidden) {
                    // Restore focus to keep CH+/CH- working, but DON'T show GUI automatically
                    rootFocus.requestFocus()
                    android.util.Log.d("EpgDayScreen", "Zapping Bar auto-hide → focus restored (GUI stays hidden)")
                } else {
                    android.util.Log.d("EpgDayScreen", "GUI manually hidden via BACK - staying hidden")
                }
            }
        }
    }

    // HOME button PIP navigation (identical logic to Key "0")
    // When MainActivity requests PIP navigation via shouldNavigateHomeWithPip flag
    LaunchedEffect(shouldNavigateHomeWithPip) {
        if (shouldNavigateHomeWithPip && player != null && streamUrl.isNotEmpty()) {
            android.util.Log.d("EpgDayScreen", "HOME button pressed - Transferring to PIP mode and START tab")

            // Set PIP mode flag to prevent player release on dispose
            isPipMode = true

            // Transfer player to PIP and navigate to TopMenu2/START (same as Key "0")
            onNavigateToPipMode(player, streamUrl)

            // Notify MainActivity that navigation is complete (reset flag)
            onHomeNavigationComplete()
        }
    }

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

            // Debug: Log all channels in EPG database
            repository.debugLogChannelIds()

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

            // Set initial channel - either specified initialChannelId or first one
            if (rows.isNotEmpty()) {
                // Try to find requested channel if initialChannelId provided
                var targetIndex = 0
                if (initialChannelId != null) {
                    android.util.Log.d("EpgDayScreen", "=== CHANNEL SEARCH DEBUG ===")
                    android.util.Log.d("EpgDayScreen", "Searching for initialChannelId: '$initialChannelId'")
                    android.util.Log.d("EpgDayScreen", "Available channels (${rows.size}):")
                    rows.forEachIndexed { index, row ->
                        val epgId = row.channel.epgId ?: row.channel.id
                        android.util.Log.d("EpgDayScreen", "  [$index] name='${row.channel.name}', epgId='$epgId', id='${row.channel.id}'")
                    }

                    // Try parsing as channel INDEX first (from "Kategorie EPG" row: 0-8)
                    val channelIndex = initialChannelId.toIntOrNull()
                    if (channelIndex != null && channelIndex in rows.indices) {
                        targetIndex = channelIndex
                        android.util.Log.d("EpgDayScreen", "✅ Using channel INDEX: $channelIndex (${rows[channelIndex].channel.name})")
                    } else {
                        // Fallback: Search by epgId/name (backwards compatibility for other rows)
                        val foundIndex = rows.indexOfFirst { row ->
                            val epgId = row.channel.epgId ?: row.channel.id
                            val matchesEpgId = epgId.equals(initialChannelId, ignoreCase = true)
                            val matchesName = row.channel.name.equals(initialChannelId, ignoreCase = true)
                            android.util.Log.d("EpgDayScreen", "  Checking channel '${row.channel.name}': epgId='$epgId' matchesEpgId=$matchesEpgId, matchesName=$matchesName")
                            matchesEpgId || matchesName
                        }
                        if (foundIndex >= 0) {
                            targetIndex = foundIndex
                            android.util.Log.d("EpgDayScreen", "✅ Found requested channel by epgId/name: '$initialChannelId' at index $targetIndex")
                        } else {
                            android.util.Log.e("EpgDayScreen", "❌ Requested channel '$initialChannelId' not found, using first channel (index 0)")
                        }
                    }
                }

                val targetRow = rows[targetIndex]
                currentChannel = targetRow.channel
                channelNumber = targetRow.channelNumber
                programs = targetRow.programs
                focusedProgramIndex = targetRow.currentProgramIndex
                initialChannelIndex = targetIndex  // Start on requested channel
                initialProgramIndex = targetRow.currentProgramIndex

                // Player starts on selected channel
                streamUrl = targetRow.channel.streamUrl
                android.util.Log.d("EpgDayScreen", "Initial channel: ${targetRow.channel.name}, stream URL: $streamUrl")
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

    // Auto-hide GUI after 10 seconds of inactivity
    LaunchedEffect(interfaceVisible) {
        if (interfaceVisible) {
            delay(10000) // 10 seconds
            interfaceVisible = false
            android.util.Log.d("EpgDayScreen", "Auto-hide: GUI hidden after 10s inactivity")
        }
    }

    // ZAPPING BAR: Set up channel change callback after data is loaded
    LaunchedEffect(allChannelRows.size) {
        if (allChannelRows.isNotEmpty()) {
            // Initialize PlayerInterfaceManager with current channel
            val initialChannel = allChannelRows.getOrNull(focusedChannelIndex)?.channel
            PlayerInterfaceManager.initialize(initialChannel?.id)
        }
    }

    // ZAPPING BAR: Set up channel change callback (needs to be able to call switchChannel)
    // This callback is set up in the Box scope where switchChannel is defined

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

    // Helper function: Switch to a different channel
    // Used by ENTER key (switch to focused LIVE program) and CH+/CH- keys
    fun switchChannel(newChannelIndex: Int, resetToLive: Boolean) {
        if (newChannelIndex !in allChannelRows.indices) {
            android.util.Log.d("EpgDayScreen", "SWITCH: Invalid channel index $newChannelIndex")
            return
        }

        android.util.Log.d("EpgDayScreen", "=== CHANNEL SWITCH: $newChannelIndex (reset to live: $resetToLive) ===")

        // Update focused channel
        focusedChannelIndex = newChannelIndex
        val newRow = allChannelRows[newChannelIndex]

        // Update UI state
        currentChannel = newRow.channel
        channelNumber = newRow.channelNumber
        programs = newRow.programs

        // Find program to focus
        if (resetToLive) {
            // Reset to current LIVE program
            val now = Instant.now()
            val currentProgram = programs.firstOrNull {
                !now.isBefore(it.startUtc) && now.isBefore(it.endUtc)
            }
            focusedProgramIndex = if (currentProgram != null) {
                programs.indexOf(currentProgram)
            } else {
                newRow.currentProgramIndex
            }
            focusedTime = now
            android.util.Log.d("EpgDayScreen", "SWITCH: Reset to LIVE program at index $focusedProgramIndex")
        } else {
            // Keep focus on program matching current focusedTime
            val matchingIndex = programs.indexOfFirst { program ->
                !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
            }
            focusedProgramIndex = if (matchingIndex >= 0) matchingIndex else newRow.currentProgramIndex
            android.util.Log.d("EpgDayScreen", "SWITCH: Found program at index $focusedProgramIndex for time $focusedTime")
        }

        // Switch player stream
        streamUrl = newRow.channel.streamUrl
        android.util.Log.d("EpgDayScreen", "SWITCH: Stream URL: $streamUrl")

        // Update startChannelIndex (for BACK navigation)
        startChannelIndex = newChannelIndex
        android.util.Log.d("EpgDayScreen", "SWITCH: Updated startChannelIndex to $startChannelIndex")

        // Force player recreation (even if URL is the same as before)
        playerResetTrigger++
        android.util.Log.d("EpgDayScreen", "SWITCH: Force player reset (trigger=$playerResetTrigger)")

        // Collapse to 1-row view
        isExpanded = false
        // NOTE: interfaceVisible is managed by LaunchedEffect(interfaceState)
        // Don't set it here - it causes flashing when switching via CH+/CH- (Zapping Bar)

        // Jump to new channel position
        coroutineScope.launch {
            columnScrollState.scrollToItem(newChannelIndex, 0)
            if (resetToLive) {
                syncAllChannelsToTime(
                    targetTime = focusedTime,
                    animateFocused = false,
                    forceFocusedScroll = true
                )
            }
        }

        android.util.Log.d("EpgDayScreen", "=== CHANNEL SWITCH COMPLETE ===")
    }

    // Create ExoPlayer ONCE at screen startup (reused for all channel switches)
    // This eliminates flashing when changing channels
    DisposableEffect(Unit) {
        android.util.Log.d("EpgDayScreen", "Creating persistent ExoPlayer instance")

        // Timeshift: 10-minute back-buffer for rewind capability (even without server-side DVR)
        // retainBackBufferFromKeyframe=false → keeps full duration (not truncated to keyframes)
        val loadControl = DefaultLoadControl.Builder()
            .setBackBuffer(10 * 60 * 1000, false)
            .build()

        // Disable automatic live edge catch-up (allows timeshift to stay at seeked position)
        val liveSpeedControl = com.google.android.exoplayer2.DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMaxPlaybackSpeed(1.0f)
            .setTargetLiveOffsetIncrementOnRebufferMs(0)
            .build()

        val localPlayer = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(liveSpeedControl)
            .build().apply {
            playWhenReady = true

            // Debug listener to track player state
            addListener(object : com.google.android.exoplayer2.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val state = when (playbackState) {
                        com.google.android.exoplayer2.Player.STATE_IDLE -> "IDLE"
                        com.google.android.exoplayer2.Player.STATE_BUFFERING -> "BUFFERING"
                        com.google.android.exoplayer2.Player.STATE_READY -> "READY"
                        com.google.android.exoplayer2.Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN($playbackState)"
                    }
                    android.util.Log.d("EpgDayScreen", "Player state changed: $state")
                }

                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    android.util.Log.e("EpgDayScreen", "Player ERROR: ${error.errorCodeName} - ${error.message}")
                    android.util.Log.e("EpgDayScreen", "  Error code: ${error.errorCode}")
                    error.printStackTrace()
                }
            })
        }

        player = localPlayer
        android.util.Log.d("EpgDayScreen", "Persistent ExoPlayer created successfully")

        onDispose {
            // Only release player if NOT in PIP transfer mode
            if (!isPipMode) {
                localPlayer.stop()
                localPlayer.release()
                player = null
                android.util.Log.d("EpgDayScreen", "Player stopped and released")
            } else {
                android.util.Log.d("EpgDayScreen", "Player transferred to PIP - not releasing resources")
            }
            frameCaptureManager.release()
        }
    }

    // Change media source when streamUrl changes (smooth transition, no recreation)
    LaunchedEffect(streamUrl, playerResetTrigger) {
        if (streamUrl.isNotEmpty() && player != null) {
            android.util.Log.d("EpgDayScreen", "Changing stream source to: $streamUrl (trigger=$playerResetTrigger)")

            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMinPlaybackSpeed(1.0f)   // Disable auto speed-down
                        .setMaxPlaybackSpeed(1.0f)   // Disable auto catch-up to live edge
                        .build()
                )
                .build()
            player?.apply {
                stop()
                setMediaItem(mediaItem)
                prepare()
                play()
            }

            // Reset timeshift state on channel switch
            frameCaptureManager.clear()
            isTimeshiftActive = false
            timeshiftOffsetMs = 0L
            isAtLiveEdge = true

            android.util.Log.d("EpgDayScreen", "Stream source changed successfully")
        } else if (streamUrl.isEmpty()) {
            android.util.Log.d("EpgDayScreen", "Empty stream URL - player remains idle")
        }
    }

    // ==================== FOCUS ARCHITECT: CONTROLLER INITIALIZATION ====================
    // Extract monolithic key handler into specialized controllers (delegation pattern)

    // Navigation handler - delegates to existing logic (defined before controllers to avoid forward reference)
    fun handleNavigation(direction: NavigationDirection) {
        // Show GUI interface on any navigation key
        PlayerInterfaceManager.showGui()
        wasManuallyHidden = false  // Reset flag when user shows GUI
        overlayVisible = false  // Hide TopMenuOverlay when GUI appears

        when (direction) {
            NavigationDirection.UP -> {
                // UP: Move to previous channel (only when expanded)
                if (!isExpanded) {
                    // State 1 channel: UP does nothing
                    android.util.Log.d("EpgDayScreen", "UP: Ignored (not expanded)")
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

                        android.util.Log.d("EpgDayScreen", "UP: Channel ${newRow.channel.name}, program index $focusedProgramIndex")
                    }
                }
            }
            NavigationDirection.DOWN -> {
                // DOWN: First press expands + moves to next channel, subsequent presses navigate
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

                        android.util.Log.d("EpgDayScreen", "DOWN (EXPAND): Channel ${newRow.channel.name}, program index $focusedProgramIndex")
                    }
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

                        android.util.Log.d("EpgDayScreen", "DOWN: Channel ${newRow.channel.name}, program index $focusedProgramIndex")
                    }
                }
            }
            NavigationDirection.LEFT -> {
                // LEFT: Navigate to previous program + update focusedTime for sync
                if (focusedProgramIndex > 0) {
                    focusedProgramIndex--

                    // Update focusedTime for synchronization across channels
                    if (programs.isNotEmpty() && focusedProgramIndex in programs.indices) {
                        focusedTime = programs[focusedProgramIndex].startUtc
                    }

                    android.util.Log.d("EpgDayScreen", "LEFT: Program $focusedProgramIndex, time: $focusedTime")
                }
            }
            NavigationDirection.RIGHT -> {
                // RIGHT: Navigate to next program + update focusedTime for sync
                if (focusedProgramIndex < programs.lastIndex) {
                    focusedProgramIndex++

                    // Update focusedTime for synchronization across channels
                    if (programs.isNotEmpty() && focusedProgramIndex in programs.indices) {
                        focusedTime = programs[focusedProgramIndex].startUtc
                    }

                    android.util.Log.d("EpgDayScreen", "RIGHT: Program $focusedProgramIndex, time: $focusedTime")
                }
            }
            NavigationDirection.SELECT -> {
                // SELECT (OK/ENTER): Switch to the selected channel and reset to LIVE program
                if (focusedChannelIndex in allChannelRows.indices) {
                    val selectedChannel = allChannelRows[focusedChannelIndex].channel
                    android.util.Log.d("EpgDayScreen", "SELECT: Switching to channel ${selectedChannel.name} (index: $focusedChannelIndex)")

                    // Switch to selected channel and reset player to current LIVE program
                    switchChannel(focusedChannelIndex, resetToLive = true)
                }
            }
        }
    }

    // Controller 1: Zapping (CH+/CH-/Digits)
    val zappingController = remember {
        ZappingBarController(
            onChannelSwitch = ::switchChannel,
            onNavigateToStartWithPip = {
                android.util.Log.d("EpgDayScreen", "Digit 0 pressed - Transferring to PIP mode and START tab")
                isPipMode = true
                onNavigateToPipMode(player, streamUrl)
            }
        )
    }

    // Controller 2: BACK navigation (conditional exit based on launch mode)
    val backController = remember {
        BackNavigationController(
            onExit = onBackPressed,
            isStartupMode = showTopMenuOverlay  // Startup mode = stay on screen, Menu mode = exit
        )
    }

    // Controller 3: EPG Grid navigation (UP/DOWN/LEFT/RIGHT/OK)
    val navController = remember {
        EpgNavigationController(
            onNavigate = { direction -> handleNavigation(direction) },
            onShowGui = {
                interfaceVisible = true
                PlayerInterfaceManager.showGui()
                overlayVisible = false  // Hide TopMenuOverlay when GUI appears
            }
        )
    }

    // Controller 4: Timeshift (LEFT/RIGHT when GUI hidden → seek ±10s with thumbnail preview)
    // PAUSE on first seek, RESUME on OK/BACK. Capture frame after each seek (player paused = safe).
    val seekStepMs = TimeshiftController.SEEK_STEP_MS

    // Helper: calculate seek position for live HLS using timeshiftOffsetMs
    // Live HLS: player.duration = end of seekable window (live edge in window coordinates)
    // Seek to (liveEdge - offset) to go backwards from live edge
    fun calculateSeekPosition(p: ExoPlayer): Long {
        val dur = p.duration
        val bufPos = p.bufferedPosition
        val curPos = p.currentPosition
        // Use best available "live edge" reference
        val liveEdge = when {
            dur > 0 && dur != Long.MIN_VALUE + 1 -> dur  // C.TIME_UNSET = Long.MIN_VALUE + 1
            bufPos > 0 -> bufPos
            curPos > 0 -> curPos
            else -> 0L
        }
        val target = (liveEdge - timeshiftOffsetMs).coerceAtLeast(0)
        android.util.Log.d("TimeshiftCtrl", "SEEK: liveEdge=$liveEdge, offset=$timeshiftOffsetMs, target=$target (dur=$dur, buf=$bufPos, cur=$curPos)")
        return target
    }

    val timeshiftController = remember {
        TimeshiftController(
            onSeekBack = {
                val p = player ?: return@TimeshiftController
                if (!isTimeshiftActive) {
                    p.pause()
                    android.util.Log.d("TimeshiftCtrl", "PAUSED player for timeshift")
                }
                // Limit offset to seekable window (duration = live window size from HLS server)
                val maxOffset = p.duration.takeIf { it > 0 && it != Long.MIN_VALUE + 1 } ?: 0L
                if (timeshiftOffsetMs + seekStepMs > maxOffset && maxOffset > 0) {
                    timeshiftOffsetMs = maxOffset  // Cap at max
                    android.util.Log.d("TimeshiftCtrl", "Seek back: HIT LIMIT (max=${maxOffset}ms / ${maxOffset/1000}s)")
                } else {
                    timeshiftOffsetMs += seekStepMs
                }
                val seekPos = calculateSeekPosition(p)
                p.seekTo(seekPos)
                isTimeshiftActive = true
                isAtLiveEdge = false
                android.util.Log.d("TimeshiftCtrl", "Seek back: offset=${timeshiftOffsetMs}ms, seekPos=${seekPos}ms, max=${maxOffset}ms")
                // Capture frame at new position (player paused = safe PixelCopy)
                coroutineScope.launch {
                    kotlinx.coroutines.delay(200)
                    frameCaptureManager.captureFrame(playerViewRef, timeshiftOffsetMs)
                }
            },
            onSeekForward = {
                val p = player ?: return@TimeshiftController
                if (timeshiftOffsetMs <= seekStepMs) {
                    // Return to live edge + resume
                    p.seekToDefaultPosition()
                    p.play()
                    isAtLiveEdge = true
                    isTimeshiftActive = false
                    timeshiftOffsetMs = 0L
                    android.util.Log.d("TimeshiftCtrl", "Returned to LIVE edge + RESUMED")
                } else {
                    timeshiftOffsetMs = (timeshiftOffsetMs - seekStepMs).coerceAtLeast(0)
                    val seekPos = calculateSeekPosition(p)
                    p.seekTo(seekPos)
                    isTimeshiftActive = true
                    android.util.Log.d("TimeshiftCtrl", "Seek forward: offset=${timeshiftOffsetMs}ms, seekPos=${seekPos}ms")
                    // Capture frame at new position
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(200)
                        frameCaptureManager.captureFrame(playerViewRef, timeshiftOffsetMs)
                    }
                }
            },
            onReturnToLive = {
                player?.seekToDefaultPosition()
                player?.play()
                isAtLiveEdge = true
                isTimeshiftActive = false
                timeshiftOffsetMs = 0L
                android.util.Log.d("TimeshiftCtrl", "Return to LIVE + RESUMED")
            },
            onConfirmPosition = {
                // OK: re-seek to confirm position, then play
                val p = player ?: return@TimeshiftController
                val seekPos = calculateSeekPosition(p)
                p.seekTo(seekPos)
                p.play()
                isTimeshiftActive = false
                android.util.Log.d("TimeshiftCtrl", "OK: offset=${timeshiftOffsetMs}ms, seekPos=${seekPos}ms, RESUMED")
            }
        )
    }

    // TIMESHIFT: Auto-hide overlay after 5 seconds, resume playback from timeshifted position
    var timeshiftLastInputTime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timeshiftOffsetMs, isTimeshiftActive) {
        if (isTimeshiftActive) {
            timeshiftLastInputTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(5000)
            if (System.currentTimeMillis() - timeshiftLastInputTime >= 4900) {
                // Auto-confirm: re-seek + resume from timeshifted position
                player?.let { p ->
                    val seekPos = calculateSeekPosition(p)
                    p.seekTo(seekPos)
                    p.play()
                }
                isTimeshiftActive = false
                android.util.Log.d("TimeshiftCtrl", "Auto-confirm: RESUMED at offset ${timeshiftOffsetMs}ms")
            }
        }
    }

    // TIMESHIFT: Frame capture — pre-capture immediately on ready, then every 5s
    // Tag frames with offset from live: 0 = live edge, 5000 = 5s ago, etc.
    // During live playback, each successive capture is 5s older (offset increments by 5000)
    var liveCaptureOffsetMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        liveCaptureOffsetMs = 0L
        // Wait for player to be ready
        while (p.playbackState != com.google.android.exoplayer2.Player.STATE_READY) {
            kotlinx.coroutines.delay(200)
        }
        // Pre-capture: 3 quick frames at 1s intervals
        repeat(3) {
            if (p.isPlaying) {
                frameCaptureManager.captureFrame(playerViewRef, liveCaptureOffsetMs)
            }
            kotlinx.coroutines.delay(1000)
            liveCaptureOffsetMs += 1000
        }
        // Normal periodic capture every 5s
        while (true) {
            kotlinx.coroutines.delay(FrameCaptureManager.CAPTURE_INTERVAL_MS)
            if (p.playbackState == com.google.android.exoplayer2.Player.STATE_READY && p.isPlaying) {
                frameCaptureManager.captureFrame(playerViewRef, liveCaptureOffsetMs)
                liveCaptureOffsetMs += FrameCaptureManager.CAPTURE_INTERVAL_MS
            }
        }
    }

    // ZAPPING BAR: Set up channel change callback (now switchChannel is available)
    LaunchedEffect(allChannelRows.size) {
        if (allChannelRows.isNotEmpty()) {
            PlayerInterfaceManager.onChannelChange = { newChannel ->
                android.util.Log.d("EpgDayScreen", "Channel switch requested: ${newChannel.name} (${newChannel.streamUrl})")

                // Find channel index and trigger switch
                val newIndex = allChannelRows.indexOfFirst { it.channel.id == newChannel.id }
                if (newIndex >= 0) {
                    switchChannel(newIndex, true)
                }
            }
        }
    }

    // Auto-hide TopMenuOverlay after 20 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(20000) // 20 seconds
        overlayVisible = false
        android.util.Log.d("EpgDayScreen", "TopMenuOverlay auto-hidden after 20 seconds")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))  // Figma: #48227C purple background
            .clipToBounds()  // Clip content outside bounds (hides channels above visible area)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // ==================== FOCUS ARCHITECT: SIMPLIFIED KEY HANDLER ====================
                // Delegation Pattern: Route keys to specialized controllers
                // Priority: Zapping → BACK → Navigation → Special keys

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // PRIORITY 1: Zapping (CH+/CH-/Digits) → ZappingBarController
                if (zappingController.handleZappingKeys(event.nativeKeyEvent, allChannelRows, focusedChannelIndex)) {
                    return@onPreviewKeyEvent true
                }

                // PRIORITY 2: BACK → Cancel timeshift first, then BackNavigationController
                if (event.key == Key.Back && (isTimeshiftActive || timeshiftOffsetMs > 0)) {
                    // Cancel timeshift, return to live, resume playback
                    player?.seekToDefaultPosition()
                    player?.play()
                    isTimeshiftActive = false
                    isAtLiveEdge = true
                    timeshiftOffsetMs = 0L
                    android.util.Log.d("TimeshiftCtrl", "BACK: Cancelled timeshift, returned to LIVE + RESUMED")
                    return@onPreviewKeyEvent true
                }

                if (event.key == Key.Back) {
                    return@onPreviewKeyEvent backController.handleBackKey(
                        interfaceState = interfaceState,
                        isExpanded = isExpanded,
                        onCollapseAndResetToNow = {
                            // Level 1: Collapse viewport + Reset to currently playing channel/program
                            isExpanded = false

                            // Find the channel that's currently playing (startChannelIndex)
                            val currentlyPlayingIndex = startChannelIndex
                            if (currentlyPlayingIndex != focusedChannelIndex && allChannelRows.isNotEmpty()) {
                                focusedChannelIndex = currentlyPlayingIndex
                                val currentRow = allChannelRows[currentlyPlayingIndex]
                                currentChannel = currentRow.channel
                                channelNumber = currentRow.channelNumber
                                programs = currentRow.programs

                                android.util.Log.d("EpgDayScreen", "BACK: Reset to currently playing channel: ${currentChannel?.name}")
                            }

                            // Reset to "now" program
                            val now = Instant.now()
                            val currentProgram = programs.firstOrNull {
                                !now.isBefore(it.startUtc) && now.isBefore(it.endUtc)
                            }
                            if (currentProgram != null) {
                                focusedProgramIndex = programs.indexOf(currentProgram)
                                focusedTime = now
                                android.util.Log.d("EpgDayScreen", "BACK: Reset to current program: ${currentProgram.title}")
                            }

                            android.util.Log.d("EpgDayScreen", "BACK: Collapsed to 1-channel view + Reset to now")
                        },
                        onHideInterface = {
                            interfaceVisible = false
                            wasManuallyHidden = true  // Mark as manually hidden via BACK
                            android.util.Log.d("EpgDayScreen", "BACK: Hidden GUI interface")
                        }
                    )
                }

                // PRIORITY 2.5: Timeshift (LEFT/RIGHT when GUI hidden → seek ±5s)
                // When GUI visible: return false → LEFT/RIGHT goes to EPG navigation below
                // When GUI hidden: LEFT=seek back, RIGHT=seek forward
                if (timeshiftController.handleTimeshiftKeys(
                        event = event,
                        interfaceVisible = interfaceVisible,
                        isTimeshiftAvailable = player != null,
                        isTimeshiftActive = isTimeshiftActive || timeshiftOffsetMs > 0
                    )
                ) {
                    return@onPreviewKeyEvent true
                }

                // PRIORITY 3: EPG Navigation (UP/DOWN/LEFT/RIGHT/OK) → EpgNavigationController
                if (navController.handleNavigation(event, interfaceVisible)) {
                    return@onPreviewKeyEvent true
                }

                // PRIORITY 4: Special keys (P = PIP, D = Debug)
                when (event.key) {
                    Key.P -> {
                        // PIP Mode: Transfer player to TopMenuScreen2
                        android.util.Log.d("EpgDayScreen", "P pressed - Transferring to PIP mode")
                        isPipMode = true
                        onNavigateToPipMode(player, streamUrl)
                        true
                    }
                    Key.D -> {
                        // Debug: Print state
                        android.util.Log.d("EpgDayScreen", """
                            === EPG DAY SCREEN STATE ===
                            Channels: ${allChannelRows.size}
                            Focused Channel: $focusedChannelIndex (${currentChannel?.name})
                            Focused Program: $focusedProgramIndex
                            Focused Time: $focusedTime
                            Expanded: $isExpanded
                            Interface Visible: $interfaceVisible
                            Interface State: ${interfaceState.javaClass.simpleName}
                            Stream URL: $streamUrl
                            Player: ${if (player != null) "ACTIVE" else "NULL"}
                            ===========================
                        """.trimIndent())
                        true
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
                    }.also { playerViewRef = it }
                },
                update = { playerView ->
                    // Update PlayerView with new player when it changes
                    playerView.player = player
                    playerViewRef = playerView
                    android.util.Log.d("EpgDayScreen", "PlayerView updated with new player: ${player != null}")
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
                    .background(Color(0xFF48227C))  // Solid background during loading
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(sy(16))
                ) {
                    Text(
                        text = "Ładowanie kanałów...",
                        color = Color(0xFFEEEEEE),
                        fontSize = (32 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "⏳",
                        fontSize = (48 * sy(1).value / 1).sp
                    )
                }
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
            // LazyColumn with LIMITED VIEWPORT (1 or 3 channels max) - shows when interface is visible
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

        // ZAPPING BAR: Conditional overlay rendering based on PlayerInterfaceManager state
        when (val state = interfaceState) {
            is PlayerInterfaceState.ZappingBarVisible -> {
                // Show Zapping Bar overlay
                ZappingBarOverlay(
                    mode = state.mode,
                    epgRepository = repository,
                    onDismiss = {
                        PlayerInterfaceManager.hide()
                    },
                    sx = sx,
                    sy = sy
                )
            }
            is PlayerInterfaceState.GuiVisible -> {
                // GUI already visible (the existing EPG interface)
                // No additional overlay needed - just let auto-hide timer run
                android.util.Log.d("EpgDayScreen", "GUI interface visible (10s auto-hide)")
            }
            is PlayerInterfaceState.Hidden -> {
                // Both interfaces hidden - clean player view
                // No overlay rendering
            }
        }

        // TIMESHIFT OVERLAY: Filmstrip of thumbnails during timeshift
        // Shows 5 thumbnails: [-20s, -10s, current, +10s, +20s] with center enlarged
        TimeshiftOverlay(
            isVisible = isTimeshiftActive,
            offsetMs = timeshiftOffsetMs,
            frames = if (isTimeshiftActive) {
                frameCaptureManager.getFramesAround(
                    centerPositionMs = timeshiftOffsetMs,  // Offset from live edge
                    stepMs = TimeshiftController.SEEK_STEP_MS,
                    sideCount = 3  // 3 on each side = 7 total (full width)
                )
            } else emptyList(),
            isAtLiveEdge = isAtLiveEdge,
            maxBufferMs = player?.duration?.takeIf { it > 0 && it != Long.MIN_VALUE + 1 } ?: 40_000L,
            totalOffsetMs = timeshiftOffsetMs,
            sx = sx,
            sy = sy
        )

        // TOP MENU OVERLAY: Show ONLY when launched from startup mode (MODE_EPG_DAY)
        // NOT shown when launched from TOP_MENU2 (clicking on channel)
        if (showTopMenuOverlay) {
            TopMenuOverlay(
                visible = overlayVisible,
                onDismiss = {
                    overlayVisible = false
                    android.util.Log.d("EpgDayScreen", "TopMenuOverlay dismissed by user")
                },
                sx = sx,  // Layout Engineer: responsive horizontal scaling
                sy = sy,  // Layout Engineer: responsive vertical scaling
                modifier = Modifier.fillMaxSize()
            )
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
    liveNowBackground: Boolean? = null,  // non-null: tryb demo EPG — true = ciemniejsze tło (program live teraz)
    isWatchedNow: Boolean = false,       // migająca playka: program aktualnie oglądany (zatunowany)
    watchProgress: Float? = null,        // timeshift: wypełnienie paska do pozycji oglądania (aqua)
    liveDotAt: Float? = null,            // timeshift: biała kropka live na pasku (0..1)
    isBlackout: Boolean = false,         // brak praw — wyszarzony kafelek + plakietka „kłódka"
    sx: (Int) -> Dp,  // Layout Engineer: ALWAYS sx/sy
    sy: (Int) -> Dp
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTime = program.startUtc.atZone(zone).format(timeFormatter)
    val endTime = program.endUtc.atZone(zone).format(timeFormatter)

    // Alpha logic: blackout = mocno wyszarzony; inaczej focused/playing = pełny, reszta przygaszona
    val isCurrentlyPlaying = !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
    val alpha = when {
        isBlackout -> 0.38f
        isFocused || isCurrentlyPlaying -> 1f
        else -> 0.5f
    }

    // Figma: epg_1 - Column with gap 4px
    Column(
        modifier = Modifier
            .width(sx(EPG_DAY_ITEM_WIDTH))  // Figma: 908px width
            .alpha(alpha)  // Full opacity for focused or currently playing, dimmed for past/future
            .then(
                // Tryb demo: program nadawany TERAZ (live) dostaje ciemniejsze tło;
                // padding stosowany dla wszystkich itemów trybu, żeby treść się nie przesuwała
                if (liveNowBackground == true) {
                    Modifier.background(Color(0x66000000))   // bez zaokrągleń
                } else Modifier
            )
            .then(
                if (liveNowBackground != null) Modifier.padding(horizontal = sx(16)) else Modifier
            ),
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
                        if (isWatchedNow) {
                            // Migająca playka: ten program jest teraz na ekranie (zatunowany)
                            val blink = rememberInfiniteTransition(label = "watch_blink")
                                .animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.15f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(600),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "watch_blink_alpha"
                                )
                            Text(
                                text = "▶",
                                fontSize = (24 * sy(1).value / 1).sp,
                                color = Color(0xFF5AECD3),
                                modifier = Modifier
                                    .alpha(blink.value)
                                    .padding(end = sx(6))
                            )
                        }
                        if (isBlackout) {
                            // Plakietka braku praw — program nieodtwarzalny (blackout)
                            Text(
                                text = "🔒 Niedostępne",
                                fontSize = (18 * sy(1).value / 1).sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFEEEEEE),
                                modifier = Modifier.padding(end = sx(10))
                            )
                        }
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
            watchProgress = watchProgress,
            liveDotAt = liveDotAt,
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
    watchProgress: Float? = null,  // timeshift: wypełnienie do pozycji oglądania (aqua zamiast bieli)
    liveDotAt: Float? = null,      // timeshift: biała kropka "gdzie jest live" (0..1)
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

    BoxWithConstraints(modifier = modifier) {
        val barWidth = maxWidth

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

        // Progress fill: standardowo biel (zegar ścienny); przy timeshifcie aqua
        // do pozycji oglądania — widać, że oglądanie jest cofnięte względem live
        val fillFraction = watchProgress ?: progress
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .fillMaxHeight()
                .background(
                    if (watchProgress != null) Color(0xFF5AECD3) else Color(0xFFEEEEEE),
                    RoundedCornerShape(sx(4))
                )
        )

        // Punkt live (biała kropka) — gdzie jest teraz live, gdy oglądanie cofnięte
        if (liveDotAt != null) {
            Box(
                modifier = Modifier
                    .offset(x = barWidth * liveDotAt.coerceIn(0f, 1f) - sy(7), y = -sy(3))
                    .size(sy(14))
                    .background(Color(0xFFEEEEEE), CircleShape)
            )
        }
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
