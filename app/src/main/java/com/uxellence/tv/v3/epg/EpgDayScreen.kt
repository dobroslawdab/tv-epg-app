package com.uxellence.tv.v3.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

/**
 * EPG Day Screen - Simple single channel view
 *
 * Shows horizontal row of programs for the first available channel
 *
 * Navigation:
 * - LEFT/RIGHT: Navigate between programs
 * - BACK: Return to previous screen
 *
 * Design specs:
 * - Programs displayed horizontally at bottom
 * - Focused program at X=330px from left edge
 * - Gradient overlay (320px height) from bottom
 */

// EPG DAY CONSTANTS
private const val EPG_DAY_GRADIENT_HEIGHT = 320    // Gradient height from bottom
private const val EPG_DAY_FOCUSED_X = 330          // Focused item position from left edge
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

@Composable
fun EpgDayScreen(
    onBackPressed: () -> Unit,  // Focus Architect: callback delegation to MainActivity
    sx: (Int) -> Dp,            // Layout Engineer: ALWAYS sx/sy parameters
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val repository = remember { EpgRepository.getInstance(context) }

    // Simple state - single channel
    var programs by remember { mutableStateOf<List<EpgProgram>>(emptyList()) }
    var focusedProgramIndex by remember { mutableStateOf(0) }
    var currentlyPlayingProgramIndex by remember { mutableStateOf(0) }  // Track currently playing program for smart BACK
    var isLoading by remember { mutableStateOf(true) }
    var streamUrl by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val lazyListState = rememberLazyListState()
    val rootFocus = remember { FocusRequester() }

    // Channel info for overlay (logo + number)
    var currentChannel by remember { mutableStateOf<TvChannelData?>(null) }
    var channelNumber by remember { mutableStateOf(1) }

    // Load EPG data for first available channel only
    LaunchedEffect(Unit) {
        try {
            isLoading = true

            // Initialize ChannelManager if needed
            if (!ChannelManager.isInitialized()) {
                ChannelManager.initialize(context)
            }

            // DEBUG: Check channels from ChannelManager
            val allChannels = ChannelManager.getAllChannels(includeUnavailable = false)
            android.util.Log.d("EpgDayScreen", "=== EPG DAY DEBUG START ===")
            android.util.Log.d("EpgDayScreen", "Total channels from ChannelManager: ${allChannels.size}")

            val firstChannel = allChannels.firstOrNull()
            android.util.Log.d("EpgDayScreen", "First channel: ${firstChannel?.name} (ID: ${firstChannel?.id})")

            // Set channel info for overlay
            currentChannel = firstChannel
            channelNumber = if (firstChannel != null) {
                allChannels.indexOf(firstChannel) + 1
            } else {
                1
            }
            android.util.Log.d("EpgDayScreen", "Channel number: $channelNumber")

            // Get stream URL for live TV playback
            streamUrl = firstChannel?.streamUrl ?: ""
            android.util.Log.d("EpgDayScreen", "Stream URL: $streamUrl")

            if (firstChannel != null) {
                val now = Instant.now()

                // Use epgId for EPG database lookup, fallback to id if epgId is null
                val channelId = firstChannel.epgId ?: firstChannel.id

                // DEBUG: Call getFullDayPrograms
                android.util.Log.d("EpgDayScreen", "Calling getFullDayPrograms(channelId='$channelId' [epgId='${firstChannel.epgId}', id='${firstChannel.id}'], date=$now)")
                val programsList = repository.getFullDayPrograms(channelId, now)

                // DEBUG: Check programs
                android.util.Log.d("EpgDayScreen", "Programs found: ${programsList.size}")
                programsList.take(3).forEach { prog ->
                    android.util.Log.d("EpgDayScreen", "  - ${prog.title} (${prog.startUtc})")
                }

                programs = programsList.sortedBy { it.startUtc }

                // Find currently playing program and save it for smart BACK navigation
                val currentIndex = programs.indexOfFirst { program ->
                    !now.isBefore(program.startUtc) && now.isBefore(program.endUtc)
                }
                currentlyPlayingProgramIndex = if (currentIndex >= 0) currentIndex else 0
                focusedProgramIndex = currentlyPlayingProgramIndex

                android.util.Log.d("EpgDayScreen", "Focused program index: $focusedProgramIndex / ${programs.size}")
                android.util.Log.d("EpgDayScreen", "=== EPG DAY DEBUG END ===")
            } else {
                android.util.Log.e("EpgDayScreen", "ERROR: firstChannel is NULL! No channels available from ChannelManager")
            }
        } catch (e: Exception) {
            android.util.Log.e("EpgDayScreen", "EXCEPTION during EPG load: ${e.message}", e)
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Request focus on mount
    LaunchedEffect(Unit) {
        rootFocus.requestFocus()
    }

    // Auto-scroll to currently playing program
    LaunchedEffect(focusedProgramIndex, programs.size) {
        if (programs.isNotEmpty()) {
            lazyListState.animateScrollToItem(
                index = focusedProgramIndex,
                scrollOffset = 0
            )
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
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Simple navigation: LEFT/RIGHT for programs, BACK to exit
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (focusedProgramIndex > 0) {
                            focusedProgramIndex--
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (focusedProgramIndex < programs.lastIndex) {
                            focusedProgramIndex++
                            true
                        } else false
                    }
                    Key.Back -> {
                        // Smart BACK: First return to currently playing program, then exit
                        if (focusedProgramIndex != currentlyPlayingProgramIndex) {
                            // User is not on currently playing program - return to it first
                            focusedProgramIndex = currentlyPlayingProgramIndex
                            true
                        } else {
                            // User is already on currently playing program - exit screen
                            onBackPressed()
                            true
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

        // Gradient overlay: pełny fiolet na dole → przezroczysty na górze
        // Only 320px height from bottom (not full screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(EPG_DAY_GRADIENT_HEIGHT))
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x0048227C),  // Top: transparent purple
                            Color(0xFF48227C)   // Bottom: full purple #48227C
                        )
                    )
                )
                .zIndex(1f)
        )

        // EPG Programs Row
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
        } else if (programs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Brak programów do wyświetlenia",
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * sy(1).value / 1).sp
                )
            }
        } else {
            // Programs LazyRow
            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(bottom = sy(EPG_DAY_BOTTOM_PADDING))
                    .zIndex(2f),
                horizontalArrangement = Arrangement.spacedBy(sx(EPG_DAY_ITEM_GAP)),
                contentPadding = PaddingValues(
                    start = sx(EPG_DAY_FOCUSED_X),
                    end = sx(80)
                )
            ) {
                itemsIndexed(programs) { index, program ->
                    EpgDayItem(
                        program = program,
                        isFocused = index == focusedProgramIndex,
                        sx = sx,
                        sy = sy
                    )
                }
            }
        }

        // Channel Info Overlay (logo + number) - z-index 3f (above programs)
        if (currentChannel != null) {
            ChannelInfoOverlay(
                channel = currentChannel!!,
                channelNumber = channelNumber,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = sx(EPG_DAY_CHANNEL_INFO_X),
                        bottom = sy(EPG_DAY_BOTTOM_PADDING + EPG_DAY_BODY_HEIGHT / 2 - 24)  // Center of EPG row (adjusted for 48px height)
                    )
                    .zIndex(3f),
                sx = sx,
                sy = sy
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
    sx: (Int) -> Dp,  // Layout Engineer: ALWAYS sx/sy
    sy: (Int) -> Dp
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val startTime = program.startUtc.atZone(zone).format(timeFormatter)
    val endTime = program.endUtc.atZone(zone).format(timeFormatter)

    // Figma: epg_1 - Column with gap 4px
    Column(
        modifier = Modifier
            .width(sx(EPG_DAY_ITEM_WIDTH))  // Figma: 908px width
            .alpha(if (isFocused) 1f else 0.5f),  // Figma: opacity 0.5/1.0
        verticalArrangement = Arrangement.spacedBy(sy(4))  // Figma: gap 4px
    ) {
        // 1. Body - Figma: Row 908x134, gap 24px
        Row(
            modifier = Modifier
                .size(sx(EPG_DAY_ITEM_WIDTH), sy(EPG_DAY_BODY_HEIGHT)),  // Figma: 908x134
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(24))  // Figma: gap 24px
        ) {
            // Cover image (only when focused) - Figma: 208x116, border 6px aqua
            if (isFocused) {
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
                        .border(
                            width = sx(EPG_DAY_COVER_BORDER),  // Figma: 6px
                            color = Color(0xFF5AECD3),  // Figma: #5AECD3 aqua
                            shape = RoundedCornerShape(sx(8))
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

                // Metadata row (only when focused) - Figma: Row gap 12.8px
                if (isFocused) {
                    MetadataRow(
                        program = program,
                        sx = sx,
                        sy = sy
                    )
                }
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
