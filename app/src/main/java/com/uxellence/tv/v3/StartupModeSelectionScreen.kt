package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.utils.VersionTracker

// Kolory z Figma
private val colorWarningYellow = Color(0xFFF0AB43)
private val colorCardBackground = Color(0x33000000) // rgba(0,0,0,0.2)
private val colorButtonUnfocused = Color(0x33EEEEEE) // rgba(238,238,238,0.2)
private val colorAquaBorder = Color(0xFF5AECD3)
private val colorPurpleText = Color(0xFF48227C)
private val colorWhiteBase = Color(0xFFEEEEEE)

// URLs obrazów z Figma (ważne przez 7 dni)
private const val IMG_MEGAPHONE = "https://www.figma.com/api/mcp/asset/e0d926dc-c48c-4bae-b783-bfc9fb04b717"

/**
 * StartupModeSelectionScreen - Nowa wersja z Figma
 *
 * Design: Legacy mode selection (node-id: 1-3154)
 * - Dwie karty obok siebie
 * - Lewy komponent focused (aqua border)
 * - Prawy komponent unfocused
 * - Background: radial gradient
 * - Nawigacja: LEFT/RIGHT między kartami
 */
@Composable
fun StartupModeSelectionScreen(
    onModeSelected: (String) -> Unit,
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Focus state: 0 = left card, 1 = right card
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
                    // BACK: Set default mode and exit
                    event.type == KeyEventType.KeyDown && event.key == Key.Back -> {
                        VersionTracker.setStartupMode(context, VersionTracker.MODE_TOP_MENU)
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
                text = "Wybierz swój ekran startowy",
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
            // LEFT CARD: "Telewizja + aplikacje" (focused by default)
            ModeCard(
                title = "Telewizja + aplikacje",
                description = "Dekoder włącza się na ekranie z łatwym dostępem do kanałów TV i aplikacji, np. Netflix.",
                illustrationType = IllustrationType.SMART_TV,
                isFocused = focusedCard == 0,
                onSelect = {
                    VersionTracker.setStartupMode(context, VersionTracker.MODE_TOP_MENU)
                    onModeSelected(VersionTracker.MODE_TOP_MENU)
                },
                sx = sx,
                sy = sy,
                focusRequester = leftFocusRequester
            )

            // RIGHT CARD: "Telewizja"
            ModeCard(
                title = "Telewizja",
                description = "Dekoder włącza się od razu na kanale TV \ni nadal masz dostęp do aplikacji.",
                illustrationType = IllustrationType.CLASSIC_TV,
                isFocused = focusedCard == 1,
                onSelect = {
                    VersionTracker.setStartupMode(context, VersionTracker.MODE_EPG_DAY)
                    onModeSelected(VersionTracker.MODE_EPG_DAY)
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
        // Ikona megafonu
        AsyncImage(
            model = IMG_MEGAPHONE,
            contentDescription = "Warning",
            modifier = Modifier.size(sx(40), sy(40))
        )

        // Tekst ostrzeżenia
        Text(
            text = "Pamiętaj: ekran startowy możesz zmienić później w Koncie.",
            style = TextStyle(
                fontSize = (28 * sy(1).value / 1).sp,
                fontWeight = FontWeight.Medium,
                color = colorWarningYellow
            )
        )
    }
}

/**
 * Typ ilustracji dekodera
 */
enum class IllustrationType {
    SMART_TV,    // Dekoder Smart (z aplikacjami)
    CLASSIC_TV   // Dekoder TV (prosty)
}

/**
 * ModeCard - Pojedyncza karta wyboru trybu
 *
 * @param title Tytuł trybu (np. "Telewizja + aplikacje")
 * @param description Opis trybu
 * @param illustrationType Typ ilustracji dekodera
 * @param isFocused Czy karta jest obecnie zfokusowana
 * @param onSelect Callback po wybraniu trybu
 */
@Composable
private fun ModeCard(
    title: String,
    description: String,
    illustrationType: IllustrationType,
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
            // Ilustracja dekodera (437x287px)
            DecoderIllustration(
                type = illustrationType,
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
 * DecoderIllustration - Ilustracja dekodera TV
 *
 * Używa lokalnych plików z drawable
 */
@Composable
private fun DecoderIllustration(
    type: IllustrationType,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Lokalne pliki ilustracji
    val illustrationRes = when (type) {
        IllustrationType.SMART_TV -> R.drawable.startup_smart_illustration
        IllustrationType.CLASSIC_TV -> R.drawable.startup_tv_illustration
    }

    Box(
        modifier = Modifier
            .size(sx(437), sy(287)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = illustrationRes),
            contentDescription = when (type) {
                IllustrationType.SMART_TV -> "Smart TV Decoder"
                IllustrationType.CLASSIC_TV -> "Classic TV Decoder"
            },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
