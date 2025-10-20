package com.uxellence.tv.v3
import com.uxellence.tv.v3.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.compose.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import kotlinx.coroutines.launch

// Model danych dla skrótu
sealed class ShortcutIcon {
    data class VectorIcon(val iconRes: Int) : ShortcutIcon()
    data class LottieIcon(val fileName: String) : ShortcutIcon()
    data class MaterialIcon(val iconName: String) : ShortcutIcon()
}

data class ShortcutItem(
    val id: String,
    val title: String,
    val icon: ShortcutIcon
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShortcutScreen() {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Lista skrótów (5 elementów z Lottie i SVG)
    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "Moja lista kanałów", ShortcutIcon.LottieIcon("tvaa.lottie")),
            ShortcutItem("2", "Nagrania", ShortcutIcon.LottieIcon("nagrania.lottie")),
            ShortcutItem("3", "Wypożyczone", ShortcutIcon.LottieIcon("wypozyczone.lottie")),
            ShortcutItem("4", "Disney Plus", ShortcutIcon.VectorIcon(R.drawable.disney_plus_icon)),
            ShortcutItem("5", "Do obejrzenia", ShortcutIcon.LottieIcon("doobejzenia.lottie"))
        )
    }
    
    var focusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(shortcuts.size) { List(shortcuts.size) { FocusRequester() } }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < shortcuts.size) {
            scope.launch { 
                listState.animateScrollToItem(focusedIndex)
                focusRequesters[focusedIndex].requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                val newIndex = when (event.key) {
                    Key.DirectionLeft -> if (focusedIndex > 0) focusedIndex - 1 else focusedIndex
                    Key.DirectionRight -> if (focusedIndex < shortcuts.size - 1) focusedIndex + 1 else focusedIndex
                    else -> focusedIndex
                }

                if (newIndex != focusedIndex) {
                    focusedIndex = newIndex
                    return@onPreviewKeyEvent true
                }

                false
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(Unit) {
            if (shortcuts.isNotEmpty()) {
                focusRequesters.firstOrNull()?.requestFocus()
            }
        }

        if (shortcuts.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = sx(134)),
                horizontalArrangement = Arrangement.spacedBy(sx(20)), // Reduced by 1/3: 30 → 20
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false
            ) {
                itemsIndexed(shortcuts) { index, shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        isFocused = index == focusedIndex,
                        focusRequester = focusRequesters[index],
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
            
            // Confetti animation positioned over Disney Plus (index 3)
            if (focusedIndex == 3) {
                val confettiComposition by rememberLottieComposition(LottieCompositionSpec.Asset("confetti.lottie"))
                val confettiProgress by animateLottieCompositionAsState(
                    composition = confettiComposition,
                    isPlaying = true,
                    restartOnPlay = true,
                    iterations = 1 // Play once only
                )
                
                // Calculate Disney Plus position: horizontal padding + (index * (cardWidth + spacing))
                val disneyPlusIndex = 3
                val cardWidth = sx(210)
                val spacing = sx(20)
                val horizontalPadding = sx(134)
                val disneyPlusX = horizontalPadding + (cardWidth + spacing) * disneyPlusIndex + cardWidth / 2
                
                LottieAnimation(
                    composition = confettiComposition,
                    progress = { confettiProgress },
                    modifier = Modifier
                        .size(sx(840), sy(1116)) // 4x larger than card (210*4, 279*4)
                        .align(Alignment.TopStart)
                        .offset(
                            x = disneyPlusX - sx(420), // Center confetti over Disney Plus (confetti width / 2)
                            y = sy(26) // Podniesione o 80px: 106 - 80 = 26
                        )
                        .zIndex(10f) // Highest z-index
                )
            }
        }
    }
}

@Composable
internal fun ShortcutCard(
    shortcut: ShortcutItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    // New dimensions: increased by 1/3 from previous size
    val cardWidth = sx(210)  // 158 × 4/3 = 210
    val cardHeight = sy(279) // 209 × 4/3 = 279
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
    
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .border(
                width = (6 * sx(1).value / 1.dp.value).dp, // border width: 6 z Figma (matches Version001Screen)
                color = borderColor,
                shape = RoundedCornerShape(sx(12)) // Changed from 20 to 12
            )
            .clip(RoundedCornerShape(sx(12)))
            .background(
                Color(0x33000000) // rgba(0,0,0,0.20) base background
            )
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Content background - darker when focused
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isFocused) Color(0x4D000000) else Color.Transparent // Darker content when focused
                )
        ) {
            // Icon centered in upper area (SVG or Lottie)
            when (shortcut.icon) {
                is ShortcutIcon.VectorIcon -> {
                    Icon(
                        painter = painterResource(shortcut.icon.iconRes),
                        contentDescription = shortcut.title,
                        modifier = Modifier
                            .size(sx(255)) // 3x większy: 85 * 3 = 255
                            .align(Alignment.Center)
                            .offset(y = sy(-15)), // Obniżone o 30px: -45 + 30 = -15
                        tint = Color(0xFFEEEEEE)
                    )
                }
                is ShortcutIcon.LottieIcon -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(shortcut.icon.fileName))
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        isPlaying = isFocused,
                        restartOnPlay = true,
                        iterations = if (isFocused) 1 else 1 // Play once only
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { if (isFocused) progress else 1f }, // Show last frame when unfocused
                        modifier = Modifier
                            .size(sx(255)) // 3x większy: 85 * 3 = 255
                            .align(Alignment.Center)
                            .offset(y = sy(-15)) // Obniżone o 30px: -45 + 30 = -15
                    )
                }
                is ShortcutIcon.MaterialIcon -> {
                    // Material icons not used in this screen, but added for completeness
                    Icon(
                        painter = painterResource(R.drawable.netflix_logo),
                        contentDescription = shortcut.title,
                        modifier = Modifier
                            .size(sx(255))
                            .align(Alignment.Center)
                            .offset(y = sy(-15)),
                        tint = Color(0xFFEEEEEE)
                    )
                }
            }
            
            // Text at bottom
            Text(
                text = shortcut.title,
                color = Color(0xFFEEEEEE),
                fontSize = sy(18).value.sp, // Increased from 16 to 18 for larger card
                fontWeight = FontWeight.W500,
                textAlign = TextAlign.Center,
                lineHeight = sy(24).value.sp, // Increased line height (20 × 1.2 = 24)
                letterSpacing = 0.36.sp, // Adjusted letter spacing (0.32 × 1.125 ≈ 0.36)
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = sx(16), vertical = sy(20)) // Increased padding
                    .fillMaxWidth()
            )
        }
    }
}