package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.channels.ChannelManager
import android.util.Log

/**
 * Live Channel Data Model
 */
data class LiveChannel(
    val number: Int,
    val name: String,
    val streamUrl: String,
    val isGeoBlocked: Boolean = false,
    val logoUrl: String? = null
)

/**
 * Live TV Screen - Dynamic channels loaded from ChannelManager
 * Odtwarzacz telewizji w bloku o wysokości 742px z obsługą przycisków 1-9 oraz UP/DOWN
 *
 * @param onBackPressed Callback when BACK button is pressed
 * @param initialChannelName Optional channel name to start with (e.g. "TVP1", "Polsat")
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
    onBackPressed: () -> Unit = {},
    initialChannelName: String? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Initialize ChannelManager if needed
    LaunchedEffect(Unit) {
        if (!ChannelManager.isInitialized()) {
            Log.d("LiveScreen", "Initializing ChannelManager...")
            ChannelManager.initialize(context)
        }
    }

    // Load channels dynamically from ChannelManager
    val channels = remember {
        ChannelManager.getAllChannels(includeUnavailable = false).mapIndexed { index, channelData ->
            LiveChannel(
                number = index + 1,
                name = channelData.name,
                streamUrl = channelData.streamUrl,
                isGeoBlocked = channelData.isGeoBlocked,
                logoUrl = channelData.logoUrl
            )
        }
    }

    // Find initial channel by name, or default to first available
    var currentChannelIndex by remember {
        mutableStateOf(
            initialChannelName?.let { name ->
                channels.indexOfFirst {
                    it.name.equals(name, ignoreCase = true)
                }
            }?.takeIf { it >= 0 } ?: 0
        )
    }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var lastKeyPressed by remember { mutableStateOf("") }
    var keyPressTime by remember { mutableStateOf(0L) }

    // ExoPlayer instance
    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    // Channel switching logic
    LaunchedEffect(currentChannelIndex) {
        if (currentChannelIndex < channels.size) {
            val channel = channels[currentChannelIndex]
            try {
                player.stop()
                val mediaItem = MediaItem.fromUri(channel.streamUrl)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
                isError = false
            } catch (e: Exception) {
                isError = true
                errorMessage = "Błąd ładowania kanału ${channel.name}: ${e.message}"
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose { 
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                // Catch-all debug for keys that don't reach the FocusableVideoPlayer
                if (event.type == KeyEventType.KeyDown) {
                    val currentTime = System.currentTimeMillis()
                    lastKeyPressed = "FALLBACK: ${event.key} | Code: ${event.nativeKeyEvent?.keyCode}"
                    keyPressTime = currentTime
                }
                false // Don't consume - let FocusableVideoPlayer handle
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(sx(40)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sy(30))
        ) {
            // Header with current channel
            val currentChannel = channels.getOrNull(currentChannelIndex)
            Text(
                text = "Live TV - ${currentChannel?.name ?: "Nieznany kanał"}",
                color = Color.White,
                fontSize = sy(48).value.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = sy(40))
            )

            // Main video player container
            Box(
                modifier = Modifier
                    .width(sx(1328)) // Szerokość jak wymagana
                    .height(sy(742)) // Wysokość jak w LargeSliderComponent
                    .offset(x = sx(-100)) // Przesunięcie do lewej o 100px
                    .background(
                        Color(0xFF5B3987), // Purple jak w kartach
                        RoundedCornerShape(sx(20))
                    )
                    .clip(RoundedCornerShape(sx(20))),
                contentAlignment = Alignment.Center
            ) {
                if (isError) {
                    // Error state
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(sy(20))
                    ) {
                        Text(
                            text = "⚠️ Błąd odtwarzania",
                            color = Color.White,
                            fontSize = sy(32).value.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = errorMessage.ifEmpty { "Stream może być niedostępny lub geo-blokowany" },
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = sy(20).value.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = sx(40))
                        )
                        Text(
                            text = "Upewnij się, że masz połączenie z internetem",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = sy(16).value.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Custom focusable video player wrapper
                    FocusableVideoPlayer(
                        player = player,
                        onKeyPressed = { key, keyCode ->
                            lastKeyPressed = "Key: $key | Code: $keyCode"
                            keyPressTime = System.currentTimeMillis()

                            // Try different key mapping approaches
                            val handled = when {
                                // Native key codes for numeric buttons (1-9)
                                keyCode == 8 && channels.size > 0 -> { currentChannelIndex = 0; true }  // KEYCODE_1
                                keyCode == 9 && channels.size > 1 -> { currentChannelIndex = 1; true }  // KEYCODE_2
                                keyCode == 10 && channels.size > 2 -> { currentChannelIndex = 2; true } // KEYCODE_3
                                keyCode == 11 && channels.size > 3 -> { currentChannelIndex = 3; true } // KEYCODE_4
                                keyCode == 12 && channels.size > 4 -> { currentChannelIndex = 4; true } // KEYCODE_5
                                keyCode == 13 && channels.size > 5 -> { currentChannelIndex = 5; true } // KEYCODE_6
                                keyCode == 14 && channels.size > 6 -> { currentChannelIndex = 6; true } // KEYCODE_7
                                keyCode == 15 && channels.size > 7 -> { currentChannelIndex = 7; true } // KEYCODE_8
                                keyCode == 16 && channels.size > 8 -> { currentChannelIndex = 8; true } // KEYCODE_9

                                // Compose Key enum for numeric buttons
                                key == Key.One && channels.size > 0 -> { currentChannelIndex = 0; true }
                                key == Key.Two && channels.size > 1 -> { currentChannelIndex = 1; true }
                                key == Key.Three && channels.size > 2 -> { currentChannelIndex = 2; true }
                                key == Key.Four && channels.size > 3 -> { currentChannelIndex = 3; true }
                                key == Key.Five && channels.size > 4 -> { currentChannelIndex = 4; true }
                                key == Key.Six && channels.size > 5 -> { currentChannelIndex = 5; true }
                                key == Key.Seven && channels.size > 6 -> { currentChannelIndex = 6; true }
                                key == Key.Eight && channels.size > 7 -> { currentChannelIndex = 7; true }
                                key == Key.Nine && channels.size > 8 -> { currentChannelIndex = 8; true }

                                // Channel navigation: UP = next channel, DOWN = previous channel
                                key == Key.DirectionUp -> {
                                    if (currentChannelIndex < channels.size - 1) {
                                        currentChannelIndex++
                                    } else {
                                        // Wrap around to first channel
                                        currentChannelIndex = 0
                                    }
                                    true
                                }
                                key == Key.DirectionDown -> {
                                    if (currentChannelIndex > 0) {
                                        currentChannelIndex--
                                    } else {
                                        // Wrap around to last channel
                                        currentChannelIndex = channels.size - 1
                                    }
                                    true
                                }

                                else -> false
                            }

                            if (handled) {
                                lastKeyPressed = "SUCCESS: $key -> Channel ${currentChannelIndex + 1} (${channels.getOrNull(currentChannelIndex)?.name ?: "Unknown"})"
                            }

                            handled
                        }
                    )
                }
            }

            // Channel info and instructions
            Text(
                text = if (isError) {
                    "Kanał ${currentChannelIndex + 1}/9 • ${currentChannel?.name ?: ""}" +
                    if (currentChannel?.isGeoBlocked == true) " [Geo-blocked]" else "" +
                    " • Naciśnij BACK aby powrócić"
                } else {
                    "Kanał ${currentChannelIndex + 1}/9 • ${currentChannel?.name ?: ""}" +
                    if (currentChannel?.isGeoBlocked == true) " [Geo-blocked]" else "" +
                    " • Stream na żywo • Naciśnij 1-9 aby zmienić kanał"
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = sy(18).value.sp,
                textAlign = TextAlign.Center
            )
        }

        // Debug indicator in top-right corner
        if (lastKeyPressed.isNotEmpty()) {
            val timeSinceKeyPress = System.currentTimeMillis() - keyPressTime
            if (timeSinceKeyPress < 3000) { // Show for 3 seconds
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(sx(20))
                        .background(
                            Color.Black.copy(alpha = 0.8f),
                            RoundedCornerShape(sx(8))
                        )
                        .padding(sx(12))
                ) {
                    Column {
                        Text(
                            text = "DEBUG: Key Pressed",
                            color = Color.Yellow,
                            fontSize = sy(12).value.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lastKeyPressed,
                            color = Color.White,
                            fontSize = sy(14).value.sp
                        )
                        Text(
                            text = "Current Channel: ${currentChannelIndex + 1}",
                            color = Color.Green,
                            fontSize = sy(12).value.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom focusable video player with border focus indicator
 */
@Composable
fun FocusableVideoPlayer(
    player: ExoPlayer,
    onKeyPressed: (Key, Int?) -> Boolean
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    fun sx(px: Int) = (px * scaleX).dp
    
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // Auto-request focus when component appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .border(
                width = if (isFocused) sx(4) else sx(0),
                color = if (isFocused) Color.Cyan else Color.Transparent,
                shape = RoundedCornerShape(sx(20))
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    val keyCode = event.nativeKeyEvent?.keyCode
                    onKeyPressed(event.key, keyCode)
                } else false
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    // Enable controls for Play/Pause and seeking
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 5000 // Auto-hide after 5 seconds
                    controllerHideOnTouch = false // Keep controls accessible
                    isFocusable = false // Prevent PlayerView from taking focus (parent Box handles it)
                    isClickable = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Focus indicator overlay
        if (isFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(sx(16))
                    .background(
                        Color.Cyan.copy(alpha = 0.8f),
                        RoundedCornerShape(sx(8))
                    )
                    .padding(sx(8))
            ) {
                Text(
                    text = "FOCUSED",
                    color = Color.Black,
                    fontSize = (12 * scaleX).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}