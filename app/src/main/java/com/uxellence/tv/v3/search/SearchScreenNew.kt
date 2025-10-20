package com.uxellence.tv.v3.search

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.animateFloatAsState
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

enum class SearchFocusArea { VOICE_BUTTON, CHANNELS }

/**
 * New SearchScreen with channels-based layout
 *
 * Layout:
 * - Top: Voice Button (170px height)
 * - Bottom: Channels (conversation turns as channel rows)
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

    // Focus state for new channels-based navigation
    var currentFocusArea by remember { mutableStateOf(SearchFocusArea.VOICE_BUTTON) }
    var focusedChannelIndex by remember { mutableStateOf(0) }
    var focusedPosterIndex by remember { mutableStateOf(0) }

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

    // FocusRequester for Voice Button
    val voiceButtonFocusRequester = remember { FocusRequester() }

    // LazyListState for channels scrolling
    val channelsListState = rememberLazyListState()

    // Initialize speech recognition and request permission if needed
    LaunchedEffect(Unit) {
        viewModel.initializeSpeechRecognition(context)

        // Auto-request permission if missing
        if (!hasAudioPermission) {
            delay(500) // Let screen render first
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Auto-focus Voice Button when entering screen (only when DOWN from menu)
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            delay(200)
            voiceButtonFocusRequester.requestFocus()
        }
    }

    // Scroll to focused channel
    LaunchedEffect(focusedChannelIndex, currentFocusArea) {
        if (currentFocusArea == SearchFocusArea.CHANNELS &&
            searchState.conversationHistory.isNotEmpty()) {
            channelsListState.animateScrollToItem(focusedChannelIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C)) // Purple background like other sections
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            when {
                                currentFocusArea == SearchFocusArea.CHANNELS && focusedChannelIndex == 0 -> {
                                    // From first channel → voice button
                                    currentFocusArea = SearchFocusArea.VOICE_BUTTON
                                    true
                                }
                                currentFocusArea == SearchFocusArea.VOICE_BUTTON -> {
                                    // From voice button → menu
                                    onReturnToMenu()
                                    true
                                }
                                currentFocusArea == SearchFocusArea.CHANNELS && focusedChannelIndex > 0 -> {
                                    // Between channels
                                    focusedChannelIndex--
                                    focusedPosterIndex = 0  // Reset poster focus
                                    true
                                }
                                else -> false
                            }
                        }
                        Key.DirectionDown -> {
                            when {
                                currentFocusArea == SearchFocusArea.VOICE_BUTTON && searchState.conversationHistory.isNotEmpty() -> {
                                    // Voice button → first channel
                                    currentFocusArea = SearchFocusArea.CHANNELS
                                    focusedChannelIndex = 0
                                    focusedPosterIndex = 0
                                    true
                                }
                                currentFocusArea == SearchFocusArea.CHANNELS &&
                                focusedChannelIndex < searchState.conversationHistory.size - 1 -> {
                                    // Between channels
                                    focusedChannelIndex++
                                    focusedPosterIndex = 0  // Reset poster focus
                                    true
                                }
                                else -> false
                            }
                        }
                        Key.DirectionLeft -> {
                            if (currentFocusArea == SearchFocusArea.CHANNELS && focusedPosterIndex > 0) {
                                focusedPosterIndex--
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (currentFocusArea == SearchFocusArea.CHANNELS) {
                                val currentChannel = searchState.conversationHistory.getOrNull(focusedChannelIndex)
                                val maxIndex = (currentChannel?.recommendations?.size ?: 0) - 1
                                if (focusedPosterIndex < maxIndex) {
                                    focusedPosterIndex++
                                    true
                                } else false
                            } else false
                        }
                        Key.Enter -> {
                            if (currentFocusArea == SearchFocusArea.VOICE_BUTTON) {
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleVoiceRecording()
                                }
                                true
                            } else false  // TODO: Poster click
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sy(170))
        ) {
            // Top: Voice Button
            VoiceButtonArea(
                isRecording = searchState.isRecording,
                isFocused = currentFocusArea == SearchFocusArea.VOICE_BUTTON,
                focusRequester = voiceButtonFocusRequester,
                onFocusChanged = { focused ->
                    if (focused) {
                        currentFocusArea = SearchFocusArea.VOICE_BUTTON
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

            // Bottom: Channels (conversation turns)
            if (searchState.conversationHistory.isNotEmpty()) {
                LazyColumn(
                    state = channelsListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = sy(20))
                ) {
                    itemsIndexed(searchState.conversationHistory) { index, turn ->
                        SearchChannelRow(
                            turn = turn,
                            isFocused = currentFocusArea == SearchFocusArea.CHANNELS && focusedChannelIndex == index,
                            focusedPosterIndex = focusedPosterIndex,
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Naciśnij OK aby zapytać\no filmy i seriale",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFEEEEEE),
                            fontSize = sy(48).value.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = sy(60).value.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Voice Button - Top area (170px height)
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
            .fillMaxWidth()
            .height(sy(170))
            .padding(horizontal = sx(50), vertical = sy(20))
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
                width = if (isFocused) sx(4) else 0.dp,
                color = if (isFocused) Color(0xFF5FEDD4) else Color.Transparent,
                shape = RoundedCornerShape(sx(12))
            )
            .background(
                color = if (isRecording) Color(0xFF5B3987) else Color(0x20FFFFFF),
                shape = RoundedCornerShape(sx(12))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isRecording) "⏺️ Nagrywanie..." else "🎤 Naciśnij OK aby mówić",
            color = Color.White,
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Search Channel Row - One conversation turn as a channel
 *
 * Structure:
 * - Channel name (query)
 * - LazyRow with posters (recommendations)
 */
@Composable
fun SearchChannelRow(
    turn: ConversationTurn,
    isFocused: Boolean,
    focusedPosterIndex: Int,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = sy(50))
    ) {
        // Channel name (query) - like CategoryIcon
        Text(
            text = turn.query,
            color = if (isFocused) Color(0xFF5FEDD4) else Color.White,
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = sx(50), bottom = sy(20))
        )

        // AI Answer (if exists and not loading)
        if (turn.answer != null && !turn.isLoading) {
            Text(
                text = turn.answer,
                color = Color(0xFFEEEEEE),
                fontSize = sy(28).value.sp,
                lineHeight = sy(38).value.sp,
                modifier = Modifier.padding(start = sx(50), bottom = sy(20))
            )
        }

        // Recommendations LazyRow or loading state
        when {
            turn.isLoading -> {
                Row(
                    modifier = Modifier.padding(start = sx(50)),
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
                Text(
                    text = "❌ ${turn.error}",
                    color = Color(0xFFFF6B6B),
                    fontSize = sy(24).value.sp,
                    modifier = Modifier.padding(start = sx(50))
                )
            }
            turn.recommendations?.isNotEmpty() == true -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = sx(50)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    itemsIndexed(turn.recommendations) { index, poster ->
                        NewPosterCard(
                            recommendation = poster,
                            isFocused = isFocused && focusedPosterIndex == index,
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }
        }
    }
}

/**
 * Poster Card - Individual recommendation poster
 */
@Composable
fun NewPosterCard(
    recommendation: MediaRecommendation,
    isFocused: Boolean,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f
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
            ),
        shape = RoundedCornerShape(sx(12)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3E))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Poster image
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
                    // Placeholder when no poster
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

            // Title and year
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
