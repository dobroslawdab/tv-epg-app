package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.utils.VersionTracker

// Kolory z Figma (reused from StartupModeSelectionScreen)
private val colorWarningYellow = Color(0xFFF0AB43)
private val colorCardBackground = Color(0x33000000) // rgba(0,0,0,0.2)
private val colorButtonUnfocused = Color(0x33EEEEEE) // rgba(238,238,238,0.2)
private val colorAquaBorder = Color(0xFF5AECD3)
private val colorPurpleText = Color(0xFF48227C)
private val colorWhiteBase = Color(0xFFEEEEEE)

/**
 * LauncherSetupScreen - Ekran konfiguracji launchera przy pierwszym uruchomieniu
 *
 * Design pattern: Similar to StartupModeSelectionScreen
 * - Dwie karty obok siebie
 * - Lewy komponent: "Ustaw jako launcher" (focused by default)
 * - Prawy komponent: "Pomiń"
 * - Background: radial gradient
 * - Nawigacja: LEFT/RIGHT między kartami
 *
 * @param onOpenSettings Callback when user selects "Ustaw jako launcher" - opens Android TV Settings
 * @param onSkip Callback when user selects "Pomiń" - continues to StartupModeSelectionScreen
 * @param onBackPressed Callback when user presses BACK - same as Skip
 * @param sx Horizontal scaling function (baseline: 1920px)
 * @param sy Vertical scaling function (baseline: 1080px)
 */
@Composable
fun LauncherSetupScreen(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Focus state: 0 = left card (Setup), 1 = right card (Skip)
    var focusedCard by remember { mutableStateOf(0) }
    val leftFocusRequester = remember { FocusRequester() }
    val rightFocusRequester = remember { FocusRequester() }

    // Auto-focus na lewej karcie po załadowaniu ekranu
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        leftFocusRequester.requestFocus()
    }

    // Full screen radial gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                when {
                    // LEFT: Switch to left card
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                        if (focusedCard == 1) {
                            focusedCard = 0
                            leftFocusRequester.requestFocus()
                        }
                        true
                    }
                    // RIGHT: Switch to right card
                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                        if (focusedCard == 0) {
                            focusedCard = 1
                            rightFocusRequester.requestFocus()
                        }
                        true
                    }
                    // BACK: Mark setup as completed (skipped) and exit
                    event.type == KeyEventType.KeyDown && event.key == Key.Back -> {
                        VersionTracker.markLauncherSetupCompleted(context)
                        onBackPressed()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Header: Title + Warning (top: 102px)
        Column(
            modifier = Modifier
                .offset(x = sx(98), y = sy(102))
                .width(sx(1721)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main title
            Text(
                text = "Konfiguracja aplikacji jako launcher",
                style = TextStyle(
                    fontSize = (48 * sy(1).value / 1).sp,
                    fontWeight = FontWeight.Medium,
                    color = colorWhiteBase
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(sy(24)))

            // Warning message
            WarningMessage(sx = sx, sy = sy)
        }

        // Cards container (top: 300px)
        Row(
            modifier = Modifier
                .offset(x = sx(123), y = sy(300))
                .height(sy(679)),
            horizontalArrangement = Arrangement.spacedBy(sx(32))
        ) {
            // LEFT CARD: "Ustaw jako launcher" (focused by default)
            LauncherOptionCard(
                title = "Ustaw jako launcher",
                description = "Aplikacja uruchomi się automatycznie po naciśnięciu przycisku HOME na pilocie.",
                cardType = LauncherCardType.SETUP,
                isFocused = focusedCard == 0,
                onSelect = {
                    VersionTracker.markLauncherSetupCompleted(context)
                    onOpenSettings()
                },
                sx = sx,
                sy = sy,
                focusRequester = leftFocusRequester
            )

            // RIGHT CARD: "Pomiń"
            LauncherOptionCard(
                title = "Pomiń",
                description = "Możesz ustawić aplikację jako launcher później w ustawieniach Androida.",
                cardType = LauncherCardType.SKIP,
                isFocused = focusedCard == 1,
                onSelect = {
                    VersionTracker.markLauncherSetupCompleted(context)
                    onSkip()
                },
                sx = sx,
                sy = sy,
                focusRequester = rightFocusRequester
            )
        }
    }
}

/**
 * WarningMessage - Żółty banner z ikoną megafonu
 */
@Composable
private fun WarningMessage(
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        modifier = Modifier
            .wrapContentWidth(),
        horizontalArrangement = Arrangement.spacedBy(sx(8)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tekst ostrzeżenia
        Text(
            text = "Pamiętaj: ustawienie launchera możesz zmienić później w ustawieniach Androida.",
            style = TextStyle(
                fontSize = (28 * sy(1).value / 1).sp,
                fontWeight = FontWeight.Medium,
                color = colorWarningYellow
            )
        )
    }
}

/**
 * Typ karty launchera
 */
enum class LauncherCardType {
    SETUP,   // Ustaw jako launcher
    SKIP     // Pomiń
}

/**
 * LauncherOptionCard - Pojedyncza karta wyboru opcji launchera
 *
 * @param title Tytuł opcji (np. "Ustaw jako launcher")
 * @param description Opis opcji
 * @param cardType Typ karty (SETUP lub SKIP)
 * @param isFocused Czy karta jest obecnie zfokusowana
 * @param onSelect Callback po wybraniu opcji
 */
@Composable
private fun LauncherOptionCard(
    title: String,
    description: String,
    cardType: LauncherCardType,
    isFocused: Boolean,
    onSelect: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    focusRequester: FocusRequester
) {
    val buttonFocusRequester = remember { FocusRequester() }

    // Animowana szerokość: focused = 957px, unfocused = 685px
    val animatedWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isFocused) sx(957) else sx(685),
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        label = "card_width"
    )

    // Focus button when card becomes focused
    LaunchedEffect(isFocused) {
        if (isFocused) {
            kotlinx.coroutines.delay(100)
            buttonFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .width(animatedWidth)
            .fillMaxHeight()
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = sx(8),
                        color = colorAquaBorder,
                        shape = RoundedCornerShape(sx(32))
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(sx(32)))
            .background(colorCardBackground)
            .padding(
                horizontal = if (isFocused) sx(160) else sx(24),
                vertical = sy(48)
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ikona (437x287px region for consistency)
            LauncherIcon(
                type = cardType,
                sx = sx,
                sy = sy
            )

            Spacer(modifier = Modifier.height(sy(32)))

            // Info section: title + description + button
            Column(
                modifier = Modifier.width(sx(637)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tytuł
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = (48 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Medium,
                        color = colorWhiteBase
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(sy(16)))

                // Opis
                Text(
                    text = description,
                    style = TextStyle(
                        fontSize = (28 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Medium,
                        color = colorWhiteBase,
                        lineHeight = (40 * sy(1).value / 1).sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(sy(32)))

                // Przycisk "Wybieram"
                Button(
                    onClick = onSelect,
                    modifier = Modifier
                        .focusRequester(buttonFocusRequester)
                        .height(sy(72))
                        .widthIn(max = sx(472)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused) colorAquaBorder else colorButtonUnfocused,
                        contentColor = if (isFocused) colorPurpleText else colorWhiteBase
                    ),
                    shape = RoundedCornerShape(sx(8))
                ) {
                    Text(
                        text = "Wybieram",
                        style = TextStyle(
                            fontSize = (24 * sy(1).value / 1).sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * LauncherIcon - Ikona reprezentująca opcję launchera
 *
 * Uses Material Icons (home icon for SETUP, arrow forward for SKIP)
 */
@Composable
private fun LauncherIcon(
    type: LauncherCardType,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .size(sx(437), sy(287)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(
                id = when (type) {
                    LauncherCardType.SETUP -> android.R.drawable.ic_menu_preferences // Placeholder - will create custom drawable
                    LauncherCardType.SKIP -> android.R.drawable.ic_media_ff // Placeholder - will create custom drawable
                }
            ),
            contentDescription = when (type) {
                LauncherCardType.SETUP -> "Setup Launcher"
                LauncherCardType.SKIP -> "Skip Setup"
            },
            tint = colorWhiteBase,
            modifier = Modifier.size(sx(200), sy(200)) // Large icon
        )
    }
}
