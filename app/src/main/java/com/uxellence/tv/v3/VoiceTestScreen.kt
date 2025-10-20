package com.uxellence.tv.v3
import com.uxellence.tv.v3.R
import com.uxellence.tv.v3.BuildConfig

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.uxellence.tv.v3.search.NativeSpeechHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * VoiceTestScreen - Dedicated test screen for Native Android Speech-to-Text
 *
 * Features:
 * - Simple, TV-friendly UI
 * - Large microphone button
 * - Real-time status updates and audio visualizer
 * - Clear result display
 * - Fast: 1-3s response (vs 60-180s with ElevenLabs)
 * - Proper Polish language support
 *
 * Usage:
 * 1. Press OK to start/stop recording
 * 2. Speak in Polish
 * 3. Get instant results (1-3 seconds)
 * 4. Press BACK to return to menu
 */

object VoiceTestColors {
    val Background = Color(0xFF1A1A2E)       // Dark purple-blue
    val CardBackground = Color(0xFF2E2E3E)    // Lighter purple
    val AccentBlue = Color(0xFF2196F3)        // Primary action (ready)
    val AccentRed = Color(0xFFF44336)         // Recording state
    val Success = Color(0xFF4CAF50)           // Success messages
    val TextPrimary = Color(0xFFFFFFFF)       // White
    val TextSecondary = Color(0xFFAAAAAA)     // Gray
}

@Composable
fun VoiceTestScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Responsive scaling for 1920x1080 baseline
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px.toFloat() * scaleX).dp
    fun sy(px: Int) = (px.toFloat() * scaleY).dp

    // State management
    var isRecording by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("INITIAL TEST") }  // DEBUG: Test if UI renders
    var status by remember { mutableStateOf("Gotowy do nagrywania") }
    var statusColor by remember { mutableStateOf(VoiceTestColors.TextPrimary) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var debugLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var elapsedTime by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentAmplitude by remember { mutableStateOf(0) }  // Audio amplitude (0-32767)
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Helper function to add debug logs with timestamp (must be defined before permission launcher)
    fun addDebugLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        debugLogs = (listOf("[$timestamp] $message") + debugLogs).take(20)
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            addDebugLog("✅ Uprawnienie RECORD_AUDIO przyznane")
            status = "Gotowy do nagrywania"
            statusColor = VoiceTestColors.TextPrimary
            error = null
        } else {
            addDebugLog("❌ Uprawnienie RECORD_AUDIO odrzucone")
            error = "Brak uprawnień do nagrywania dźwięku"
            status = "❌ Brak uprawnień"
            statusColor = VoiceTestColors.AccentRed
        }
    }

    // Timer for processing duration
    LaunchedEffect(isProcessing) {
        if (isProcessing) {
            elapsedTime = 0
            while (isProcessing) {
                delay(1000)
                elapsedTime++
            }
        } else {
            elapsedTime = 0
        }
    }

    // Native Speech Helper initialization
    val speechHelper = remember {
        NativeSpeechHelper(
            context = context,
            coroutineScope = coroutineScope
        ).apply {
            onResult = { text ->
                // Log first to ensure it's captured
                addDebugLog("SUCCESS: \"$text\"")
                android.util.Log.d("VoiceTestScreen", "⭐ SETTING recognizedText to: \"$text\"")
                // Update UI state
                recognizedText = text
                android.util.Log.d("VoiceTestScreen", "⭐ recognizedText is now: \"$recognizedText\"")
                android.util.Log.d("VoiceTestScreen", "⭐ recognizedText.isNotEmpty(): ${recognizedText.isNotEmpty()}")
                status = "✅ Rozpoznano!"
                statusColor = VoiceTestColors.Success
                isProcessing = false
                isRecording = false  // Hide visualizer after recognition
                // Add to history
                history = (listOf(text) + history).take(5)
            }
            onError = { err ->
                error = err
                status = "❌ Błąd"
                statusColor = VoiceTestColors.AccentRed
                isProcessing = false
                addDebugLog("ERROR: $err")
            }
            onStart = {
                status = "🎤 Nagrywanie..."
                statusColor = VoiceTestColors.AccentBlue
                isRecording = true
                isProcessing = false
                error = null
                recognizedText = ""
                addDebugLog("START: Nagrywanie rozpoczęte")
            }
            onEnd = {
                status = "⏳ Przetwarzanie..."
                statusColor = VoiceTestColors.TextSecondary
                isRecording = false
                isProcessing = true
                currentAmplitude = 0  // Reset amplitude when stopped
                addDebugLog("END: Nagrywanie zakończone, rozpoczęcie transkrypcji")
            }
            onAmplitude = { amplitude ->
                // Update amplitude for visualizer (runs on Main thread)
                currentAmplitude = amplitude
            }
        }
    }

    // Check if Polish language is available
    val isPolishAvailable = remember { speechHelper.isPolishLanguageAvailable() }

    LaunchedEffect(Unit) {
        addDebugLog("🔍 Sprawdzanie konfiguracji...")

        // Check permission - AUTO REQUEST if missing
        if (!hasAudioPermission) {
            addDebugLog("⚠️ WARNING: Brak uprawnień RECORD_AUDIO")
            addDebugLog("📋 Automatyczne żądanie uprawnień...")
            delay(500) // Small delay to let screen render
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            addDebugLog("✅ Uprawnienie RECORD_AUDIO przyznane")
        }

        // Check Polish language pack
        if (!isPolishAvailable) {
            error = "Brak pakietu języka polskiego. Zainstaluj rozpoznawanie mowy po polsku."
            status = "❌ Brak języka polskiego"
            statusColor = VoiceTestColors.AccentRed
            addDebugLog("ERROR: Brak pakietu języka polskiego (pl-PL)")
        } else {
            addDebugLog("✅ Pakiet języka polskiego (pl-PL) dostępny")
        }

        addDebugLog("ℹ️ Używa natywnego Android SpeechRecognizer")
        addDebugLog("ℹ️ Szybka odpowiedź: 1-3s (vs 60-180s z ElevenLabs)")
    }

    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VoiceTestColors.Background)
            .onKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                    onBackPressed()
                    true
                } else false
            }
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(40.dp),
            contentPadding = PaddingValues(vertical = 60.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    // Status (małe info)
            Text(
                text = status,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )

            // Audio Visualizer (shows during recording)
            if (isRecording) {
                AudioVisualizer(
                    amplitude = currentAmplitude,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            // Figma Voice Button
            FigmaVoiceButton(
                isRecording = isRecording,
                isEnabled = hasAudioPermission && isPolishAvailable,
                onClick = {
                    if (!hasAudioPermission) {
                        // Request permission
                        addDebugLog("📋 Requesting RECORD_AUDIO permission...")
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else if (isRecording) {
                        speechHelper.stopRecognition()
                    } else {
                        recognizedText = ""
                        error = null
                        speechHelper.startRecognition()
                    }
                },
                sx = ::sx,
                sy = ::sy
            )

            // Processing Indicator
            if (isProcessing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        strokeWidth = 8.dp,
                        color = VoiceTestColors.AccentBlue
                    )
                    Text(
                        text = "Przetwarzanie: ${elapsedTime}s",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoiceTestColors.TextPrimary
                    )
                    if (elapsedTime > 5) {
                        Text(
                            text = "⚠️ To trwa zbyt długo (>5s)...",
                            fontSize = 18.sp,
                            color = Color(0xFFFFAA00)
                        )
                    }
                    if (elapsedTime > 10) {
                        Text(
                            text = "Sprawdź logi poniżej →",
                            fontSize = 16.sp,
                            color = VoiceTestColors.AccentRed
                        )
                    }
                }
            }

            // Result Box - Large Figma-styled text
            if (recognizedText.isNotEmpty()) {
                android.util.Log.d("VoiceTestScreen", "🎨 RENDERING recognized text UI: \"$recognizedText\"")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF00FF00))  // DEBUG: Bright green background to see if it renders
                        .padding(horizontal = 40.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // MAIN TEXT - Large and prominent (Figma style: 42sp, Manrope Bold)
                    Text(
                        text = recognizedText,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp,  // Adjusted for better readability with Polish text
                        letterSpacing = 0.84.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )

                    // Small label below
                    Text(
                        text = "Rozpoznany tekst:",
                        fontSize = 18.sp,
                        color = VoiceTestColors.TextSecondary,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            // Error display
            if (error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF3D1F1F)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "❌ $error",
                        fontSize = 20.sp,
                        color = VoiceTestColors.AccentRed,
                        modifier = Modifier.padding(24.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Instructions
            Text(
                text = "• Naciśnij OK aby rozpocząć/zatrzymać nagrywanie\n• Mów po polsku przez kilka sekund\n• Wynik: 1-3 sekundy (szybkie!)\n• Naciśnij BACK aby wrócić do menu",
                fontSize = 20.sp,
                color = VoiceTestColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            // History (optional, last 3 results)
            if (history.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Historia ostatnich rozpoznań:",
                    fontSize = 18.sp,
                    color = VoiceTestColors.TextSecondary
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    history.take(3).forEachIndexed { index, text ->
                        Text(
                            text = "${index + 1}. $text",
                            fontSize = 16.sp,
                            color = VoiceTestColors.TextSecondary.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
                }  // Close inner Column
            }  // Close LazyColumn item
        }  // Close LazyColumn

        // Debug Panel (fixed at bottom of screen, only in DEBUG builds)
        if (BuildConfig.DEBUG && debugLogs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color(0xDD000000))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🐛 Debug Logs",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFF00)
                        )
                        Text(
                            text = "Last ${debugLogs.size} events",
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = false
                    ) {
                        items(debugLogs.size) { index ->
                            val log = debugLogs[index]
                            Text(
                                text = log,
                                fontSize = 12.sp,
                                color = when {
                                    log.contains("ERROR") || log.contains("❌") -> Color(0xFFFF6B6B)
                                    log.contains("SUCCESS") || log.contains("✅") -> Color(0xFF51CF66)
                                    log.contains("WARNING") || log.contains("⚠️") -> Color(0xFFFFD43B)
                                    else -> Color(0xFFCCCCCC)
                                },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechHelper.release()
        }
    }
}

/**
 * FigmaVoiceButton - Button zgodny z designem z Figmy
 *
 * Design: https://www.figma.com/design/amSVOTqmxh8LHgIL9aVmnW/Hydepark?node-id=6063-14888
 *
 * Dwa stany:
 * - Default: białe tło 8% opacity, transparent border
 * - Focused: transparent tło, aqua border (#5FEDD4)
 *
 * Wymiary z Figmy (dla 1920x1080):
 * - Height: 149px
 * - Padding: 42px 60px 42px 40px
 * - Border radius: 129px
 * - Border width: 12px
 */
@Composable
fun FigmaVoiceButton(
    isRecording: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Auto-focus on first composition
    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    // Colors based on focus state
    val backgroundColor = if (isFocused) Color.Transparent else Color(0x14FFFFFF) // rgba(255,255,255,0.08)
    val borderColor = if (isFocused) Color(0xFF5FEDD4) else Color.Transparent
    val textColor = if (isFocused) Color(0xFF5AECD3) else Color(0xFFEEEEEE)

    Box(
        modifier = Modifier
            .height(sy(149))  // 149px z Figmy, skalowane
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable(enabled = isEnabled)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(sy(129))  // Skalowany border radius
            )
            .border(
                width = sy(12),  // Skalowany border
                color = borderColor,
                shape = RoundedCornerShape(sy(129))
            )
            .padding(start = sx(40), end = sx(60), top = sy(42), bottom = sy(42)),  // Skalowany padding
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(8)),  // Skalowany gap
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Microphone icon (emoji scaled to 56px equivalent)
            val iconSize = sy(56)
            Text(
                text = "🎤",
                fontSize = iconSize.value.sp,
                modifier = Modifier.padding(end = sx(8))
            )

            // Text with mixed font weights
            val textSize = sy(32)
            val letterSpacing = sy(1).value.sp  // ~0.64sp scaled

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Powiedz",
                    fontSize = textSize.value.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    letterSpacing = letterSpacing,
                    modifier = Modifier.padding(end = sx(4))
                )
                Text(
                    text = " co chcesz obejrzeć",
                    fontSize = textSize.value.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    letterSpacing = letterSpacing
                )
            }
        }
    }
}

@Composable
fun MicrophoneButton(
    isRecording: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = when {
            !isEnabled -> 0.9f
            isRecording -> 1.1f
            isFocused -> 1.05f
            else -> 1.0f
        },
        animationSpec = tween(300), label = "button_scale"
    )

    // Auto-focus on first composition
    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .size(220.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable(enabled = isEnabled)
            .onKeyEvent { event ->
                if ((event.key == Key.Enter || event.key == Key.DirectionCenter)
                    && event.type == KeyEventType.KeyDown
                    && isEnabled
                ) {
                    onClick()
                    true
                } else false
            }
            .background(
                color = when {
                    !isEnabled -> Color(0xFF555555)
                    isRecording -> VoiceTestColors.AccentRed
                    isFocused -> VoiceTestColors.AccentBlue.copy(alpha = 0.9f)
                    else -> VoiceTestColors.AccentBlue
                },
                shape = CircleShape
            )
            .border(
                width = if (isFocused) 6.dp else if (isRecording) 4.dp else 0.dp,
                color = VoiceTestColors.TextPrimary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when {
                    !isEnabled -> "⚠️"
                    isRecording -> "⏹️"
                    else -> "🎤"
                },
                fontSize = 80.sp
            )
            Text(
                text = when {
                    !isEnabled -> "Disabled"
                    isRecording -> "Stop"
                    else -> "Start"
                },
                fontSize = 20.sp,
                color = VoiceTestColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Audio Visualizer - Animated bars showing microphone input level
 *
 * @param amplitude Current audio amplitude (0-32767)
 * @param modifier Modifier for customization
 */
@Composable
fun AudioVisualizer(
    amplitude: Int,
    modifier: Modifier = Modifier
) {
    // Adjusted amplitude mapping: treat ambient noise (0-12000) as silence
    // NOTE: This is ONLY for visualizer - does NOT affect speech recognition!
    val adjustedAmplitude = if (amplitude < 12000) {
        0f  // Ignore background noise/ambient sound
    } else {
        // Remap 12000-32767 to 0.0-1.0 for smooth scaling
        ((amplitude - 12000f) / (32767f - 12000f)).coerceIn(0f, 1f)
    }

    // Running bars: History of last 11 amplitude samples
    val amplitudeHistory = remember {
        mutableStateListOf<Float>().apply {
            repeat(11) { add(0f) } // Initialize with 11 dots (silence)
        }
    }

    // Update history every 83ms (12 FPS) - new samples enter from left
    LaunchedEffect(adjustedAmplitude) {
        while (isActive) {
            delay(83) // 12 FPS

            // Add new sample at the beginning (left)
            amplitudeHistory.add(0, adjustedAmplitude)

            // Remove oldest sample (right)
            if (amplitudeHistory.size > 11) {
                amplitudeHistory.removeAt(11)
            }
        }
    }

    // Constants
    val barWidth = 16.dp
    val barSpacing = 10.dp
    val baseHeight = 16.dp  // Circle/dot at silence
    val maxHeight = 120.dp

    Box(
        modifier = modifier
            .height(140.dp)
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        amplitudeHistory.forEachIndexed { index, historicAmplitude ->
            // Calculate target X position (slot position)
            val slotWidth = barWidth + barSpacing
            val targetOffsetX = slotWidth * index

            // Animate X position (sliding effect)
            val animatedOffsetX by animateDpAsState(
                targetValue = targetOffsetX,
                animationSpec = tween(83), // Same as update rate
                label = "offset_x_$index"
            )

            // Calculate bar height from historic sample
            val targetHeight = baseHeight + ((maxHeight - baseHeight) * historicAmplitude)

            // Smooth animation between samples
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(83),
                label = "bar_height_$index"
            )

            // Color: gray at silence, gradient when sound detected
            val barColor = if (historicAmplitude == 0f) {
                Color(0xFF444444)  // Gray dot
            } else {
                // Smooth gradient: Blue → Cyan → Green → Neon Green
                when {
                    historicAmplitude < 0.2f -> Color(0xFF2196F3)   // Blue
                    historicAmplitude < 0.4f -> Color(0xFF00BCD4)   // Cyan
                    historicAmplitude < 0.6f -> Color(0xFF4CAF50)   // Green
                    historicAmplitude < 0.8f -> Color(0xFF66BB6A)   // Light green
                    else -> Color(0xFF00E676)  // Neon green
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = animatedOffsetX, y = 0.dp) // Sliding animation
                    .width(barWidth)
                    .height(animatedHeight)
                    .shadow(
                        elevation = if (historicAmplitude > 0.3f) 8.dp else 0.dp,
                        shape = RoundedCornerShape(10.dp),
                        spotColor = barColor.copy(alpha = 0.6f)
                    )
                    .background(
                        color = barColor,
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }
    }

    // Debug overlay - pokazuje mapowanie amplitude i historię
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "RAW: $amplitude | ADJ: ${"%.3f".format(adjustedAmplitude)} | HISTORY: ${amplitudeHistory.size}",
            fontSize = 12.sp,
            color = Color(0xFFFFFF00), // Żółty dla widoczności
            fontWeight = FontWeight.Bold
        )
    }

    // Level indicator text (based on adjusted amplitude)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                adjustedAmplitude < 0.05f -> "🔇 Cisza"
                adjustedAmplitude < 0.3f -> "🔉 Mów głośniej"
                adjustedAmplitude < 0.6f -> "🔊 Dobry poziom"
                else -> "📢 Bardzo głośno!"
            },
            fontSize = 18.sp,
            color = VoiceTestColors.TextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
