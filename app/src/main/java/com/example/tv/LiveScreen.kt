package com.example.tv

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
import com.example.tv.ui.theme.figmaRadialBackground

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
 * Live TV Screen - Multiple channels with numeric remote control
 * Odtwarzacz telewizji w bloku o wysokości 742px z obsługą przycisków 1-9
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LiveScreen(
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Channels list with stream URLs
    val channels = remember {
        listOf(
            LiveChannel(1, "TVP1", "https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8"),
            LiveChannel(2, "Polsat", "https://lb2-e2-19.pluscdn.pl/ch/1502600/308/dash/20a18c30/live.mpd", true),
            LiveChannel(3, "Polsat News", "http://cdn-s-lb2.pluscdn.pl/lv/1517830/349/dash/81ec4c32/live.mpd", true),
            LiveChannel(4, "Polsat News Polityka", "https://lb2-e3-20.pluscdn.pl/lv/1511888/322/dash/52a9b70b/live.mpd", true),
            LiveChannel(5, "Polsat Viasat Nature", "https://liveovh010.cda.pl/enc104/polsatviasatnaturehdraw/polsatviasatnaturehdraw.mpd"),
            LiveChannel(6, "4Fun TV", "https://stream.4fun.tv:8888/hls/4f.m3u8"),
            LiveChannel(7, "Viasat Explore Classic", "https://da9c49fa.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLXBsX1ZpYXNhdEV4cGxvcmVfSExT/playlist.m3u8"),
            LiveChannel(8, "Euronews", "https://7060743b4b224241b86325460b14d152.mediatailor.eu-west-1.amazonaws.com/v1/master/0547f18649bd788bec7b67b746e47670f558b6b2/production-LiveChannel-6769/bitok/eyJzdGlkIjoiMTE3OTllNGEtYmU1OC00ZjQyLTkxOTYtY2VlYWQzZGU2MDJjIiwibWt0IjoicGwiLCJjaCI6Njc2OSwicHRmIjo1fQ==/26235/euronews-pl.m3u8"),
            LiveChannel(9, "Top Movies Polska", "https://top-movies-rakuten-tv-pl.fast.rakuten.tv/v1/master/0547f18649bd788bec7b67b746e47670f558b6b2/production-LiveChannel-6059/master.m3u8")
        )
    }
    
    var currentChannelIndex by remember { mutableStateOf(3) } // Start on channel 4 (Polsat News Polityka)
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
                                // Native key codes
                                keyCode == 8 -> { currentChannelIndex = 0; true }  // KEYCODE_1
                                keyCode == 9 -> { currentChannelIndex = 1; true }  // KEYCODE_2
                                keyCode == 10 -> { currentChannelIndex = 2; true } // KEYCODE_3
                                keyCode == 11 -> { currentChannelIndex = 3; true } // KEYCODE_4
                                keyCode == 12 -> { currentChannelIndex = 4; true } // KEYCODE_5
                                keyCode == 13 -> { currentChannelIndex = 5; true } // KEYCODE_6
                                keyCode == 14 -> { currentChannelIndex = 6; true } // KEYCODE_7
                                keyCode == 15 -> { currentChannelIndex = 7; true } // KEYCODE_8
                                keyCode == 16 -> { currentChannelIndex = 8; true } // KEYCODE_9
                                
                                // Compose Key enum
                                key == Key.One -> { currentChannelIndex = 0; true }
                                key == Key.Two -> { currentChannelIndex = 1; true }
                                key == Key.Three -> { currentChannelIndex = 2; true }
                                key == Key.Four -> { currentChannelIndex = 3; true }
                                key == Key.Five -> { currentChannelIndex = 4; true }
                                key == Key.Six -> { currentChannelIndex = 5; true }
                                key == Key.Seven -> { currentChannelIndex = 6; true }
                                key == Key.Eight -> { currentChannelIndex = 7; true }
                                key == Key.Nine -> { currentChannelIndex = 8; true }
                                
                                // Backup controls
                                key == Key.DirectionUp -> { 
                                    if (currentChannelIndex < channels.size - 1) currentChannelIndex++
                                    true 
                                }
                                key == Key.DirectionDown -> { 
                                    if (currentChannelIndex > 0) currentChannelIndex--
                                    true 
                                }
                                
                                else -> false
                            }
                            
                            if (handled) {
                                lastKeyPressed = "SUCCESS: $key -> Channel ${currentChannelIndex + 1}"
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
                    useController = false // Disable controls
                    controllerAutoShow = false
                    controllerHideOnTouch = true
                    hideController()
                    isFocusable = false // Prevent PlayerView from taking focus
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