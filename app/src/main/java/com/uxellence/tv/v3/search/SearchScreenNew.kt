package com.uxellence.tv.v3.search

import com.uxellence.tv.v3.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import com.uxellence.tv.v3.VodDataCache
import com.uxellence.tv.v3.ShortcutItem
import com.uxellence.tv.v3.ShortcutIcon
import com.uxellence.tv.v3.version001.VodContent
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Channel type for unified channels system
 * VOICE_BUTTON: Voice search button (Row 0)
 * CONVERSATION: Dynamic channels from conversation history (search results)
 * STATIC: Pre-defined channels (Skróty, Seriale, Filmy fabularne, Kino Play)
 */
enum class ChannelType { VOICE_BUTTON, CONVERSATION, STATIC }

/**
 * Unified channel data structure
 * Allows treating both conversation results and static channels uniformly
 *
 * @param type Channel type (CONVERSATION or STATIC)
 * @param name Channel name (query text for CONVERSATION, channel name for STATIC)
 * @param rowIndex Row position in manual positioning system (1, 2, 3...)
 * @param data Actual data (ConversationTurn for CONVERSATION, String for STATIC)
 */
data class ChannelData(
    val type: ChannelType,
    val name: String,
    val rowIndex: Int,
    val data: Any // ConversationTurn or String
)

/**
 * New SearchScreen with channels-based layout
 *
 * Layout:
 * - Row 0: Voice Button (manual positioning, scrolls with content)
 * - Row 1+: Unified channels (conversation results + static channels)
 *
 * Navigation:
 * - UP/DOWN: Between voice button and channels
 * - LEFT/RIGHT: Within channel posters
 */
@Composable
fun SearchScreenNew(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val searchState by viewModel.searchState.collectAsState()

    // Static channels (WIDEO pattern)
    val staticChannels = remember {
        listOf("Skróty", "Seriale", "Filmy fabularne", "Kino Play")
    }

    // Shortcuts for "Skróty" channel
    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "Historia", ShortcutIcon.MaterialIcon("history")),
            ShortcutItem("2", "Popularne", ShortcutIcon.MaterialIcon("trending_up")),
            ShortcutItem("3", "Filmy", ShortcutIcon.MaterialIcon("movie")),
            ShortcutItem("4", "Seriale", ShortcutIcon.MaterialIcon("tv"))
        )
    }

    // Grid content for static channels
    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        val kinoPlayMovies = VodDataCache.getKinoPlayMovies()

        staticChannels.associateWith { channel ->
            when (channel) {
                "Skróty" -> emptyList() // Shortcuts don't use VodContent
                "Seriale", "Filmy fabularne" -> vodContentList.shuffled().take(10)
                "Kino Play" -> kinoPlayMovies.shuffled().take(10)
                else -> emptyList()
            }
        }
    }

    // Unified channels list - VoiceButton (Row 0) + conversation results + static channels
    // Row 0: Voice Button (always first)
    // If conversation history exists: conversation channels (Row 1, 2, 3...) + static channels (Row N+1...)
    // Otherwise: static channels only (Row 1, 2, 3, 4)
    val unifiedChannels = remember(searchState.conversationHistory, staticChannels) {
        // Row 0: Voice Button (always first)
        val voiceButtonChannel = ChannelData(
            type = ChannelType.VOICE_BUTTON,
            name = "Voice Search",
            rowIndex = 0,
            data = Unit
        )

        if (searchState.conversationHistory.isNotEmpty()) {
            // Conversation turns as channels (Row 1, 2, 3...)
            val conversationChannels = searchState.conversationHistory.mapIndexed { index, turn ->
                ChannelData(
                    type = ChannelType.CONVERSATION,
                    name = turn.query,
                    rowIndex = index + 1,
                    data = turn
                )
            }

            // Static channels after conversation (Row N+1, N+2, N+3, N+4)
            val staticChannelsData = staticChannels.mapIndexed { index, name ->
                ChannelData(
                    type = ChannelType.STATIC,
                    name = name,
                    rowIndex = conversationChannels.size + index + 1,
                    data = name
                )
            }

            listOf(voiceButtonChannel) + conversationChannels + staticChannelsData
        } else {
            // Only static channels (Row 1, 2, 3, 4)
            val staticChannelsData = staticChannels.mapIndexed { index, name ->
                ChannelData(
                    type = ChannelType.STATIC,
                    name = name,
                    rowIndex = index + 1,
                    data = name
                )
            }

            listOf(voiceButtonChannel) + staticChannelsData
        }
    }

    // Focus state - unified system (no separate voice button area)
    var focusedRowIndex by remember { mutableStateOf(0) } // Start at Row 0 (VoiceButton)
    var focusedColIndex by remember { mutableStateOf(0) } // VoiceButton has col=0

    // Permission state for microphone
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
    }

    // Unified FocusRequesters for all channels (VoiceButton + conversation + static)
    val channelFocusRequesters = remember(unifiedChannels.size, shortcuts.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            for (channel in unifiedChannels) {
                when (channel.type) {
                    ChannelType.VOICE_BUTTON -> {
                        // VoiceButton at Row 0: Single focus point
                        put(Pair(0, 0), FocusRequester())
                    }
                    ChannelType.CONVERSATION -> {
                        // CONVERSATION channel: NO CategoryIcon, ONLY poster content
                        put(Pair(channel.rowIndex, 0), FocusRequester()) // Poster content (LazyRow)
                    }
                    ChannelType.STATIC -> {
                        if (channel.name == "Skróty") {
                            // Skróty: 4 shortcuts (no CategoryIcon)
                            repeat(shortcuts.size) { colIndex ->
                                put(Pair(channel.rowIndex, colIndex), FocusRequester())
                            }
                        } else {
                            // Standard channel: CategoryIcon + content
                            put(Pair(channel.rowIndex, -1), FocusRequester()) // CategoryIcon
                            put(Pair(channel.rowIndex, 0), FocusRequester()) // Fixed focus position
                        }
                    }
                }
            }
        }
    }

    // Unified LazyListStates for all scrollable channels (conversation + static horizontal)
    val lazyListStates = remember(unifiedChannels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            for (channel in unifiedChannels) {
                // Both conversation and static horizontal channels have LazyRow
                // Skróty doesn't have LazyRow (direct shortcuts focus)
                if (channel.type == ChannelType.CONVERSATION || channel.name != "Skróty") {
                    put(channel.rowIndex, LazyListState())
                }
            }
        }
    }

    // CoroutineScope for scrolling
    val coroutineScope = rememberCoroutineScope()

    // LazyColumn state for main scrolling (all channels including VoiceButton)
    val mainScrollState = rememberLazyListState()

    // Auto-scroll LazyColumn to focused channel
    LaunchedEffect(focusedRowIndex) {
        if (focusedRowIndex in unifiedChannels.indices) {
            mainScrollState.animateScrollToItem(focusedRowIndex)
        }
    }

    // LazyRow auto-reset pattern (Version 0.05) - reset unfocused channels to position 0
    // Works for both conversation and static channels
    LaunchedEffect(focusedRowIndex, focusedColIndex, unifiedChannels.size) {
        for (channel in unifiedChannels) {
            val shouldReset = when {
                // Reset all channels when VoiceButton (Row 0) is focused
                focusedRowIndex == 0 -> true
                // Reset channels that are not focused
                channel.rowIndex != focusedRowIndex -> true
                // Reset when CategoryIcon/QueryName is focused (no content visible)
                focusedColIndex == -1 -> true
                // Don't reset when content is focused in this channel
                else -> false
            }
            if (shouldReset) {
                lazyListStates[channel.rowIndex]?.let { state ->
                    if (state.firstVisibleItemIndex > 0) {
                        state.animateScrollToItem(0, 0)
                    }
                }
            }
        }
    }

    // Initialize speech recognition and request permission if needed
    LaunchedEffect(Unit) {
        viewModel.initializeSpeechRecognition(context)

        // Auto-request permission if missing
        if (!hasAudioPermission) {
            delay(500) // Let screen render first
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-focus Voice Button (Row 0) when entering screen (only when DOWN from menu)
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            delay(200)
            channelFocusRequesters[Pair(0, 0)]?.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C)) // Purple background like other sections
            .onPreviewKeyEvent { event ->
                // Navigation handler - UNIFIED SYSTEM (Row 0 = VoiceButton, Row 1+ = Channels)
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            // UP navigation
                            when (focusedRowIndex) {
                                0 -> {
                                    // From VoiceButton (Row 0) → Menu
                                    onReturnToMenu()
                                    return@onPreviewKeyEvent true
                                }
                                1 -> {
                                    // From Row 1 → VoiceButton (Row 0)
                                    focusedRowIndex = 0
                                    focusedColIndex = 0
                                    channelFocusRequesters[Pair(0, 0)]?.requestFocus()
                                    return@onPreviewKeyEvent true
                                }
                                else -> {
                                    // Between channels - determine target based on channel type
                                    val newRowIndex = focusedRowIndex - 1
                                    val targetChannel = unifiedChannels.getOrNull(newRowIndex)

                                    val targetColIndex = when {
                                        // To VoiceButton (Row 0)
                                        newRowIndex == 0 -> 0
                                        // To CONVERSATION channel → Poster content (0, NO CategoryIcon)
                                        targetChannel?.type == ChannelType.CONVERSATION -> 0
                                        // To Skróty → First shortcut (0)
                                        targetChannel?.name == "Skróty" -> 0
                                        // To STATIC channel → Preserve type (CategoryIcon->CategoryIcon, content->content)
                                        else -> if (focusedColIndex == -1) -1 else 0
                                    }

                                    focusedRowIndex = newRowIndex
                                    focusedColIndex = targetColIndex
                                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        Key.DirectionDown -> {
                            // DOWN navigation
                            when (focusedRowIndex) {
                                0 -> {
                                    // From VoiceButton (Row 0) → First channel (Row 1)
                                    if (unifiedChannels.size > 1) { // Need at least 2 items (VoiceButton + first channel)
                                        focusedRowIndex = 1

                                        // Determine first colIndex based on first actual channel type
                                        val firstChannel = unifiedChannels[1] // Index 1 = first channel (Row 1)
                                        focusedColIndex = when {
                                            firstChannel.type == ChannelType.CONVERSATION -> 0 // Poster content (NO CategoryIcon)
                                            firstChannel.name == "Skróty" -> 0 // First shortcut
                                            else -> -1 // CategoryIcon (static channels)
                                        }

                                        channelFocusRequesters[Pair(1, focusedColIndex)]?.requestFocus()
                                        return@onPreviewKeyEvent true
                                    }
                                    return@onPreviewKeyEvent false
                                }
                                else -> {
                                    // Between channels
                                    if (focusedRowIndex < unifiedChannels.size - 1) {
                                        val newRowIndex = focusedRowIndex + 1
                                        val nextChannel = unifiedChannels.getOrNull(newRowIndex)

                                        if (nextChannel != null) {
                                            // Determine targetColIndex based on channel types
                                            val targetColIndex = when {
                                                // To CONVERSATION channel → Poster content (0, NO CategoryIcon)
                                                nextChannel.type == ChannelType.CONVERSATION -> 0
                                                // To Skróty → First shortcut (0)
                                                nextChannel.name == "Skróty" -> 0
                                                // To STATIC channel → CategoryIcon (-1) or preserve content (0)
                                                else -> if (focusedColIndex == -1) -1 else 0
                                            }

                                            focusedRowIndex = newRowIndex
                                            focusedColIndex = targetColIndex
                                            channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    // At last channel - nowhere to go
                                    return@onPreviewKeyEvent false
                                }
                            }
                        }
                        Key.DirectionLeft -> {
                            // LEFT navigation - Only for channels (Row 1+), NOT VoiceButton
                            if (focusedRowIndex > 0) {
                                val currentChannel = unifiedChannels.getOrNull(focusedRowIndex)

                                if (currentChannel != null) {
                                    // Check if Skróty (direct shortcuts navigation)
                                    if (currentChannel.type == ChannelType.STATIC && currentChannel.name == "Skróty") {
                                        // Skróty row: Direct focus navigation between shortcuts
                                        if (focusedColIndex > 0) {
                                            focusedColIndex--
                                            channelFocusRequesters[Pair(focusedRowIndex, focusedColIndex)]?.requestFocus()
                                            return@onPreviewKeyEvent true
                                        }
                                        return@onPreviewKeyEvent false
                                    } else {
                                        // Channel with LazyRow (conversation or static horizontal)
                                        when (currentChannel.type) {
                                            ChannelType.VOICE_BUTTON -> {
                                                // VoiceButton doesn't process LEFT/RIGHT
                                                return@onPreviewKeyEvent false
                                            }
                                            ChannelType.CONVERSATION -> {
                                                // CONVERSATION channel: NO CategoryIcon, ONLY LazyRow scrolling
                                                when (focusedColIndex) {
                                                    0 -> {
                                                        // On posters: scroll left if possible
                                                        val lazyListState = lazyListStates[focusedRowIndex]
                                                        if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                                                            coroutineScope.launch {
                                                                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                                                            }
                                                        }
                                                        // At beginning: stay on posters (NO CategoryIcon to navigate to)
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    else -> return@onPreviewKeyEvent false
                                                }
                                            }
                                            ChannelType.STATIC -> {
                                                // STATIC channel: CategoryIcon + LazyRow scrolling
                                                when (focusedColIndex) {
                                                    -1 -> {
                                                        // Already on CategoryIcon
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    0 -> {
                                                        // On content: scroll left or move to CategoryIcon
                                                        val lazyListState = lazyListStates[focusedRowIndex]
                                                        if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                                                            // Scroll left
                                                            coroutineScope.launch {
                                                                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                                                            }
                                                        } else {
                                                            // At beginning -> move to CategoryIcon
                                                            focusedColIndex = -1
                                                            channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                                                        }
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    else -> return@onPreviewKeyEvent false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            false
                        }
                        Key.DirectionRight -> {
                            // RIGHT navigation - Only for channels (Row 1+), NOT VoiceButton
                            if (focusedRowIndex > 0) {
                                val currentChannel = unifiedChannels.getOrNull(focusedRowIndex)

                                if (currentChannel != null) {
                                    // Check if Skróty (direct shortcuts navigation)
                                    if (currentChannel.type == ChannelType.STATIC && currentChannel.name == "Skróty") {
                                        // Skróty row: Direct focus navigation between shortcuts (max 3)
                                        if (focusedColIndex < 3) {
                                            focusedColIndex++
                                            channelFocusRequesters[Pair(focusedRowIndex, focusedColIndex)]?.requestFocus()
                                            return@onPreviewKeyEvent true
                                        }
                                        return@onPreviewKeyEvent false
                                    } else {
                                        // Channel with LazyRow (conversation or static horizontal)
                                        when (currentChannel.type) {
                                            ChannelType.VOICE_BUTTON -> {
                                                // VoiceButton doesn't process LEFT/RIGHT
                                                return@onPreviewKeyEvent false
                                            }
                                            ChannelType.CONVERSATION -> {
                                                // CONVERSATION channel: NO CategoryIcon, ONLY LazyRow scrolling
                                                when (focusedColIndex) {
                                                    0 -> {
                                                        // On posters: scroll right if possible
                                                        val lazyListState = lazyListStates[focusedRowIndex]
                                                        val turn = currentChannel.data as ConversationTurn
                                                        val contentSize = turn.recommendations?.size ?: 0

                                                        if (lazyListState != null && lazyListState.firstVisibleItemIndex < contentSize - 1) {
                                                            coroutineScope.launch {
                                                                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                                                            }
                                                        }
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    else -> return@onPreviewKeyEvent false
                                                }
                                            }
                                            ChannelType.STATIC -> {
                                                // STATIC channel: CategoryIcon + LazyRow scrolling
                                                when (focusedColIndex) {
                                                    -1 -> {
                                                        // From CategoryIcon -> content
                                                        val content = gridContent[currentChannel.name]
                                                        if (!content.isNullOrEmpty()) {
                                                            focusedColIndex = 0
                                                            channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                                                        }
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    0 -> {
                                                        // On content: scroll right
                                                        val lazyListState = lazyListStates[focusedRowIndex]
                                                        val content = gridContent[currentChannel.name] ?: emptyList()
                                                        val contentSize = content.size

                                                        if (lazyListState != null && lazyListState.firstVisibleItemIndex < contentSize - 1) {
                                                            coroutineScope.launch {
                                                                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                                                            }
                                                        }
                                                        return@onPreviewKeyEvent true
                                                    }
                                                    else -> return@onPreviewKeyEvent false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            false
                        }
                        Key.Enter -> {
                            // ENTER key
                            if (focusedRowIndex == 0) {
                                // VoiceButton (Row 0) - toggle recording
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleVoiceRecording()
                                }
                                return@onPreviewKeyEvent true
                            }
                            // TODO: Handle shortcut/content click for other rows
                            false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // UNIFIED RENDERING - LazyColumn with scrolling for ALL channels (Row 0 = VoiceButton, Row 1+ = Channels)
        LazyColumn(
            state = mainScrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = sy(170))  // Start below menu (120px) + space for VoiceButton visibility
        ) {
            itemsIndexed(unifiedChannels) { index, channel ->
                when (channel.type) {
                    ChannelType.VOICE_BUTTON -> {
                        // Row 0: VoiceButton
                        VoiceButtonArea(
                            isRecording = searchState.isRecording,
                            isFocused = focusedRowIndex == 0,
                            focusRequester = channelFocusRequesters[Pair(0, 0)] ?: FocusRequester(),
                            onFocusChanged = { focused ->
                                if (focused) {
                                    focusedRowIndex = 0
                                    focusedColIndex = 0
                                }
                            },
                            onClick = {
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleVoiceRecording()
                                }
                            },
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )
                    }
                    ChannelType.CONVERSATION -> {
                        // CONVERSATION channel: Tall vertical layout with query + answer + vertical posters
                        SearchChannelRow(
                            turn = channel.data as ConversationTurn,
                            rowIndex = channel.rowIndex,
                            focusedRowIndex = focusedRowIndex,
                            focusedColIndex = focusedColIndex,
                            channelFocusRequesters = channelFocusRequesters,
                            lazyListState = lazyListStates[channel.rowIndex],
                            onChannelContentFocusChange = { row, col ->
                                focusedRowIndex = row
                                focusedColIndex = col
                            },
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )
                    }
                    ChannelType.STATIC -> {
                        // Static channel: Skróty or standard channel
                        StaticChannelRow(
                            channel = channel.name,
                            rowIndex = channel.rowIndex,
                            shortcuts = if (channel.name == "Skróty") shortcuts else emptyList(),
                            rowContent = gridContent[channel.name] ?: emptyList(),
                            focusedRowIndex = focusedRowIndex,
                            focusedColIndex = focusedColIndex,
                            channelFocusRequesters = channelFocusRequesters,
                            lazyListState = lazyListStates[channel.rowIndex],
                            onChannelContentFocusChange = { row, col ->
                                focusedRowIndex = row
                                focusedColIndex = col
                            },
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )

                        // Add spacer for expansion when this channel is focused with content visible
                        if (channel.rowIndex == focusedRowIndex && focusedColIndex >= 0 && channel.name != "Skróty") {
                            Spacer(modifier = Modifier.height(sy(290)))  // Miniatures slide-down height
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculate Y position for ALL channels including VoiceButton (Row 0) - UNIFIED SYSTEM
 *
 * Row 0 (VoiceButton):
 * - When focused (focusedRowIndex == 0): Y = MENU_HEIGHT (120px)
 * - When rows 1-2 focused: Y = MENU_HEIGHT (stay visible)
 * - When row 3 focused: Y = MENU_HEIGHT - 100 (partially hidden)
 * - When deeper rows focused: Y = MENU_HEIGHT - 170 (fully hidden)
 *
 * Rows 1+ (Channels):
 * - Pattern: Focused channel ALWAYS at Y=340px (SEARCH_FIXED_FOCUS_Y)
 * - Channels above: cumulative height going up
 * - Focused channel: at fixed Y=340px
 * - Channels below: cumulative height going down + expansion when content focused
 */
private fun calculateSearchChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    unifiedChannels: List<ChannelData>,
    sy: (Int) -> Dp
): Dp {
    // Constants
    val MENU_HEIGHT = 120                  // Top menu height (from ReusableTopMenu)
    val VOICEBUTTON_HEIGHT = 170           // VoiceButton height (Row 0)
    val SEARCH_FIXED_FOCUS_Y = 340         // Focused channel always at this Y position
    val SHORTCUTS_ROW_HEIGHT = 219         // Skróty - horizontal shortcuts (179px + 40px spacing)
    val HORIZONTAL_NORMAL_HEIGHT = 240     // CategoryIcon (216px) + spacing (24px) - for STATIC channels (matches WIDEO)
    val HORIZONTAL_EXPANDED_HEIGHT = 546   // CategoryIcon (216px) + miniatures (290px) + spacing (40px) - for STATIC channels
    val SEARCH_CHANNEL_HEIGHT = 515        // CONVERSATION channels: query (32px) + answer (38px) + posters (300px) + padding (105px) + spacing (60px)

    // Helper: Get channel height by row index
    fun getChannelHeight(channelRowIndex: Int): Int {
        if (channelRowIndex == 0) return VOICEBUTTON_HEIGHT // Row 0 = VoiceButton
        val channel = unifiedChannels.getOrNull(channelRowIndex)
        return when {
            channel?.type == ChannelType.CONVERSATION -> SEARCH_CHANNEL_HEIGHT
            channel?.name == "Skróty" -> SHORTCUTS_ROW_HEIGHT
            else -> HORIZONTAL_NORMAL_HEIGHT
        }
    }

    // SPECIAL CASE: Row 0 (VoiceButton) positioning
    if (rowIndex == 0) {
        return when {
            // VoiceButton focused - below top menu
            focusedRowIndex == 0 -> sy(MENU_HEIGHT)
            // First two channels focused - keep visible
            focusedRowIndex in 1..2 -> sy(MENU_HEIGHT)
            // Third channel focused - move up partially
            focusedRowIndex == 3 -> sy(MENU_HEIGHT - 100)
            // Deeper channels focused - move off-screen
            else -> sy(MENU_HEIGHT - 170)
        }
    }

    // SPECIAL CASE: When VoiceButton (Row 0) is focused, channels start below it
    if (focusedRowIndex == 0) {
        var cumulativeY = VOICEBUTTON_HEIGHT
        for (i in 1 until rowIndex) {
            cumulativeY += getChannelHeight(i)
        }
        return sy(cumulativeY)
    }

    // NORMAL CASE: Channels use FIXED_FOCUS_Y pattern
    return when {
        // SCENARIO 1: Rows ABOVE focused row
        rowIndex < focusedRowIndex -> {
            // Start from FIXED_FOCUS_Y and subtract heights going up
            var cumulativeHeight = SEARCH_FIXED_FOCUS_Y - getChannelHeight(rowIndex)

            // Subtract heights of all rows between rowIndex and focusedRowIndex
            for (i in (rowIndex + 1) until focusedRowIndex) {
                cumulativeHeight -= getChannelHeight(i)
            }

            sy(cumulativeHeight)
        }

        // SCENARIO 2: Focused row (ALWAYS at Y=340px)
        rowIndex == focusedRowIndex -> {
            sy(SEARCH_FIXED_FOCUS_Y)
        }

        // SCENARIO 3: Rows BELOW focused row
        else -> {
            // Calculate focused row height (expanded if content is selected)
            val focusedChannel = unifiedChannels.getOrNull(focusedRowIndex)
            val focusedRowExpansion = when {
                focusedChannel?.name == "Skróty" -> SHORTCUTS_ROW_HEIGHT  // Skróty don't expand
                focusedChannel?.type == ChannelType.CONVERSATION -> SEARCH_CHANNEL_HEIGHT  // CONVERSATION: NO expansion
                else -> {
                    // STATIC channels: Expand when content focused
                    if (focusedColIndex >= 0) HORIZONTAL_EXPANDED_HEIGHT
                    else HORIZONTAL_NORMAL_HEIGHT
                }
            }

            // Start from FIXED_FOCUS_Y + focused row height
            var cumulativeHeight = SEARCH_FIXED_FOCUS_Y + focusedRowExpansion

            // Add heights of all rows between focusedRowIndex and rowIndex
            for (i in (focusedRowIndex + 1) until rowIndex) {
                cumulativeHeight += getChannelHeight(i)
            }

            sy(cumulativeHeight)
        }
    }
}

/**
 * Voice Button - Figma design (pill-shaped, icon + text)
 * Height: 170px, Border radius: 129px (pill shape)
 */
@Composable
fun VoiceButtonArea(
    isRecording: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .padding(start = sx(110), top = sy(60), bottom = sy(60))
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()  // Let button expand naturally
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    onFocusChanged(focusState.isFocused)
                }
                .focusable()
                .onKeyEvent { event ->
                    if (isFocused &&
                        event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter)
                    ) {
                        onClick()
                        true
                    } else false
                }
                .border(
                    width = if (isFocused) sx(12) else 0.dp,
                    color = if (isFocused) Color(0xFF5FEDD4) else Color.Transparent,
                    shape = RoundedCornerShape(sx(129))
                )
                .background(
                    color = if (isRecording) Color(0xFF5B3987) else Color(0x14FFFFFF), // 0.08 alpha = 14 hex
                    shape = RoundedCornerShape(sx(129))
                )
                .clickable { onClick() }
        ) {
        Row(
            modifier = Modifier.padding(
                start = sx(40),
                end = sx(60),
                top = sy(42),
                bottom = sy(42)
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(8))
        ) {
            // Microphone icon (90x65px)
            Image(
                painter = painterResource(id = R.drawable.ic_microphone),
                contentDescription = "Microphone",
                modifier = Modifier
                    .width(sx(90))
                    .height(sy(65))
            )

            // Text: "Powiedz" (bold) + "co chcesz obejrzeć" (medium)
            if (isRecording) {
                Text(
                    text = "Nagrywanie...",
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(32).value.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.64.sp,
                    lineHeight = sy(32).value.sp
                )
            } else {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = sy(32).value.sp,
                            color = Color(0xFFEEEEEE),
                            letterSpacing = 0.64.sp
                        )) {
                            append("Powiedz ")
                        }
                        withStyle(style = SpanStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = sy(32).value.sp,
                            color = Color(0xFFEEEEEE),
                            letterSpacing = 0.64.sp
                        )) {
                            append("co chcesz obejrzeć")
                        }
                    },
                    lineHeight = sy(32).value.sp
                )
            }
        }
        }
    }
}

/**
 * Search Channel Row - One conversation turn as a channel
 *
 * Structure:
 * - Channel name (query)
 * - LazyRow with posters (recommendations)
 */
/**
 * StaticChannelRow - Renders one static channel (Skróty or standard channel)
 * Based on WideoUnifiedChannelRow pattern
 */
/**
 * SearchChannelRow - Conversation turn displayed as tall vertical channel
 *
 * Structure (Column - top to bottom):
 * 1. Query text (NO CategoryIcon) - what user said
 * 2. "Myślę..." (loading state with spinner)
 * 3. AI Answer text (when ready)
 * 4. LazyRow with vertical portrait posters (NewPosterCard)
 *
 * Height: ~500px (TALL, no slide-down animation)
 * Focus: Only poster content (col=0), NO CategoryIcon (col=-1)
 */
@Composable
fun SearchChannelRow(
    turn: ConversationTurn,
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    lazyListState: LazyListState?,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val isCurrentRow = rowIndex == focusedRowIndex

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = sy(60),      // Increased from 45px to 60px
                bottom = sy(60)    // Spacing between channels
            )
    ) {
        // 1. Query text (NO CategoryIcon)
        Text(
            text = turn.query,
            color = if (isCurrentRow) Color(0xFF5FEDD4) else Color.White,
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = sx(110), bottom = sy(20))  // Increased from 50px to 110px
        )

        // 2. AI Answer (if exists and not loading)
        if (turn.answer != null && !turn.isLoading) {
            Text(
                text = turn.answer,
                color = Color(0xFFEEEEEE),
                fontSize = sy(28).value.sp,
                lineHeight = sy(38).value.sp,
                modifier = Modifier.padding(start = sx(110), bottom = sy(20))  // Increased from 50px to 110px
            )
        }

        // 3. Content area (loading/error/posters)
        when {
            turn.isLoading -> {
                // Loading state
                Row(
                    modifier = Modifier.padding(start = sx(110)),  // Increased from 50px to 110px
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sx(16))
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(sy(32)),
                        color = Color(0xFF5FEDD4),
                        strokeWidth = sy(3)
                    )
                    Text(
                        text = "Myślę...",
                        color = Color(0xFFAAAAAA),
                        fontSize = sy(24).value.sp
                    )
                }
            }
            turn.error != null -> {
                // Error state
                Text(
                    text = "❌ ${turn.error}",
                    color = Color(0xFFFF6B6B),
                    fontSize = sy(24).value.sp,
                    modifier = Modifier.padding(start = sx(110))  // Increased from 50px to 110px
                )
            }
            turn.recommendations?.isNotEmpty() == true && lazyListState != null -> {
                // Posters LazyRow
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = lazyListState,
                    contentPadding = PaddingValues(horizontal = sx(110)),  // Increased from 50px to 110px
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(turn.recommendations.size) { colIndex ->
                        val recommendation = turn.recommendations[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0

                        val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
                            channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                        } else {
                            FocusRequester()
                        }

                        NewPosterCard(
                            recommendation = recommendation,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = {
                                onChannelContentFocusChange(rowIndex, 0)
                            },
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StaticChannelRow(
    channel: String,
    rowIndex: Int,
    shortcuts: List<ShortcutItem>,
    rowContent: List<VodContent>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    lazyListState: LazyListState?,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val isSkrotyRow = channel == "Skróty"
    val isCurrentRow = rowIndex == focusedRowIndex

    // Slide-down animation (290px) - WIDEO pattern
    val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0 && !isSkrotyRow
    val miniaturesYOffset by animateDpAsState(
        targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
        animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
        label = "search_miniatures_y_offset_$rowIndex"
    )

    // DetailedContentOverlay delayed appearance (WIDEO pattern)
    var showDetailsWithDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && focusedColIndex == 0 && !isSkrotyRow

        if (shouldShowDetails) {
            // Opóźnienie równe czasowi animacji slide-down
            kotlinx.coroutines.delay(350) // 350ms = czas animacji
            showDetailsWithDelay = true
        } else {
            // Natychmiastowe ukrycie gdy fokus opuszcza miniaturki
            showDetailsWithDelay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sy(20))
    ) {
        if (isSkrotyRow) {
            // Skróty row: Horizontal Row with shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sx(100)),
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                shortcuts.forEachIndexed { colIndex, shortcut ->
                    val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
                    
                    SearchShortcutCard(
                        shortcut = shortcut,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        sx = sx,
                        sy = sy,
                        onFocusChange = { if (it) onChannelContentFocusChange(rowIndex, colIndex) }
                    )
                }
            }
        } else {
            // Standard channel: CategoryIcon + LazyRow
            // CategoryIcon
            Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
                val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]
                
                if (categoryFocusRequester != null) {
                    CategoryIcon(
                        text = channel,
                        isFocused = categoryIsFocused,
                        onClick = {},
                        onFocused = { if (it) onChannelContentFocusChange(rowIndex, -1) },
                        focusRequester = categoryFocusRequester,
                        sx = sx,
                        sy = sy,
                        showIcon = false, // Text-only for search channels
                        showBackgroundWhenFocused = true // Black background when focused
                    )
                }
            }
            
            // LazyRow content
            if (lazyListState != null) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset), // Animated slide-down
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0
                        
                        val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
                            channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                        } else {
                            FocusRequester()
                        }
                        
                        ContentCard(
                            vodContent = vodContent,
                            channelNumber = String.format("%03d", colIndex + 1),
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy,
                            lazyListState = lazyListState
                        )
                    }
                }

                // DetailedContentOverlay (WIDEO pattern) - z-index: 1 (warstwa środkowa)
                if (showDetailsWithDelay && lazyListState != null) {
                    val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
                    if (firstVisibleContent != null) {
                        Box(
                            modifier = Modifier
                                .offset(x = sx(380), y = sy(0)) // X: 380px (aligned with first card), Y: 0px (same as CategoryIcon)
                                .width(sx(1500)) // Nearly full width
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(sy(14))
                            ) {
                                // Title (64sp, 1 line)
                                Text(
                                    text = firstVisibleContent.title,
                                    color = Color(0xFFEEEEEE),
                                    fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                    modifier = Modifier.width(sx(1500))
                                )

                                // Metadata row (category only - we don't have duration/year/country in VodContent)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(sx(16)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Category
                                    Text(
                                        text = firstVisibleContent.category,
                                        color = Color(0xCCEEEEEE),
                                        fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                        letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                                    )
                                }

                                // Description (28sp, 3 lines)
                                Text(
                                    text = firstVisibleContent.description,
                                    color = Color(0xFFEEEEEE),
                                    fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(sx(874))
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
 * SearchShortcutCard - Simplified ShortcutCardV2 (310×179px)
 */
@Composable
private fun SearchShortcutCard(
    shortcut: ShortcutItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onFocusChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(sx(310))
            .height(sy(179))
            .border(
                width = sy(6),
                color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                shape = RoundedCornerShape(sx(20))
            )
            .background(
                color = Color(0x3B000000), // rgba(0, 0, 0, 0.23)
                shape = RoundedCornerShape(sx(20))
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { onFocusChange(it.isFocused) }
            .clickable { /* TODO */ },
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            text = shortcut.title,
            color = Color(0xFFEEEEEE),
            fontSize = sy(24).value.sp,
            fontWeight = FontWeight.W500,
            modifier = Modifier.padding(sx(20))
        )
    }
}

/**
 * NewPosterCard - Vertical portrait poster with title and year
 *
 * Used for search result recommendations in SearchChannelRow
 * Dimensions: 200×300px (portrait orientation)
 * - Poster image: 200×240px
 * - Text area: 200×60px (title + year)
 */
@Composable
private fun NewPosterCard(
    recommendation: MediaRecommendation,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        label = "poster_scale"
    )

    Card(
        modifier = Modifier
            .width(sx(200))
            .height(sy(300))
            .scale(scale)
            .border(
                width = if (isFocused) sx(4) else 0.dp,
                color = if (isFocused) Color(0xFF5FEDD4) else Color.Transparent,
                shape = RoundedCornerShape(sx(12))
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable(),
        shape = RoundedCornerShape(sx(12)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Poster image (240px tall)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(240))
                    .background(Color(0xFF1A1A2E))
            ) {
                if (recommendation.posterUrl != null) {
                    AsyncImage(
                        model = recommendation.posterUrl,
                        contentDescription = recommendation.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder when no poster URL
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "?",
                            color = Color(0xFF5FEDD4),
                            fontSize = sy(48).value.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title and year (60px area)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(sx(8)),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = recommendation.title,
                    color = Color.White,
                    fontSize = sy(14).value.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recommendation.releaseYear,
                    color = Color(0xFFAAAAAA),
                    fontSize = sy(12).value.sp
                )
            }
        }
    }
}

/**
 * CategoryIcon - Full-featured category icon with logo support
 * Copied from Version001Screen.kt for consistency with WIDEO pattern
 */
@Composable
private fun CategoryIcon(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit = {},
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    logoUrl: String? = null,
    logoDrawableId: Int? = null,
    showIcon: Boolean = true,
    showBackgroundWhenFocused: Boolean = false,
    isExpanded: Boolean = false,
    showChevron: Boolean = false,
    onChevronClick: (() -> Unit)? = null
) {
    // Rozmiary z Figma
    val containerWidth = sx(240)
    val containerHeight = sy(216)
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent

    // Chevron rotation animation (350ms smooth rotation)
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
        label = "chevron_rotation"
    )

    Box(
        modifier = Modifier
            .width(containerWidth) // 240 z Figma
            .height(containerHeight) // 216 z Figma
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = (6 * sx(1).value / 1.dp.value).dp, // border width: 6 z Figma
                        color = borderColor,
                        shape = RoundedCornerShape(sx(4))
                    )
                } else {
                    Modifier // brak obramowania gdy nie focused
                }
            )
            .clip(RoundedCornerShape(sx(4)))
            .then(
                if (showBackgroundWhenFocused) {
                    if (isFocused) {
                        Modifier.background(Color(0x4D000000)) // rgba(0, 0, 0, 0.30) - focused
                    } else {
                        Modifier.background(Color(0x1A000000)) // rgba(0, 0, 0, 0.10) - unfocused
                    }
                } else {
                    Modifier // brak tła
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Pozycjonowanie jak w Figma - left: 72, top: 40
        Box(
            modifier = Modifier
                .offset(x = sx(0), y = sy(-8)) // Wyśrodkowanie w kontenerze
                .width(sx(200))
                .height(sy(144)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (showIcon) Arrangement.spacedBy(sy(16)) else Arrangement.Center, // spacing: 16 z Figma tylko gdy pokazujemy ikonę
                modifier = Modifier.fillMaxSize()
            ) {
                // Ikona 96x96 - logo lub placeholder (tylko gdy showIcon = true)
                if (showIcon) {
                    Box(
                        modifier = Modifier
                            .size(sx(96)) // 96x96 z Figma
                            .clip(RoundedCornerShape(sx(8))),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            logoDrawableId != null -> {
                                // Pokazuj lokalne SVG drawable
                                Icon(
                                    painter = painterResource(id = logoDrawableId),
                                    contentDescription = text,
                                    modifier = Modifier.size(sx(68)),
                                    tint = Color(0xFFEEEEEE)
                                )
                            }
                            logoUrl != null -> {
                                // Pokazuj logo z URL
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = text,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            else -> {
                                // Placeholder - pierwsze 2 litery
                                Text(
                                    text = text.take(2).uppercase(),
                                    color = Color(0xFFEEEEEE),
                                    fontSize = (24 * (sx(1).value / 1.dp.value)).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Tekst kategorii + chevron (dla expandable channels)
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = sx(200))
                ) {
                    Text(
                        text = text,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFEEEEEE),
                        fontSize = (24 * (sy(1).value / 1.dp.value)).sp, // fontSize: 24 z Figma
                        fontWeight = FontWeight.Medium, // fontWeight: 500 z Figma
                        letterSpacing = (0.48 * (sy(1).value / 1.dp.value)).sp, // letterSpacing: 0.48 z Figma
                        lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp, // lineHeight: 1.33 z Figma
                        maxLines = 2, // Umożliwia 2 linie dla dłuższych nazw EPG
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Chevron icon for expandable channels (NAGRANIA)
                    if (showChevron) {
                        Spacer(modifier = Modifier.width(sx(8)))
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFFEEEEEE),
                            modifier = Modifier
                                .size(sx(20), sy(20))
                                .rotate(chevronRotation)
                        )
                    }
                }
            }
        }
    }
}

/**
 * SearchContentCard - Simplified ContentCard (368×208px)
 */
/**
 * ContentCard - Full-featured content card with image, gradient, title, channel number
 * Copied from Version001Screen.kt for consistency with WIDEO pattern
 */
@Composable
private fun ContentCard(
    vodContent: VodContent,
    channelNumber: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    lazyListState: LazyListState
) {
    val itemWidth = sx(368)
    val itemHeight = sy(208)

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12)))
            .then(
                if (isFocused) Modifier.border(
                    width = (6 * sx(1).value / 1.dp.value).dp,
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable()
    ) {
        AsyncImage(
            model = vodContent.imageUrl,
            contentDescription = vodContent.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient zgodny ze specyfikacją
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sy(88))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    ),
                    shape = RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )

        // Numer kanału
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = sx(12), top = sy(12))
                .border(width = sx(1), color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(sx(4)))
                .padding(horizontal = sx(12), vertical = sy(8)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelNumber,
                color = Color.White,
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Tytuł
        Text(
            text = vodContent.title,
            color = Color.White,
            fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(12), bottom = sy(12), end = sx(12))
        )
    }
}
