package com.uxellence.tv.v3.components

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
import com.uxellence.tv.v3.R
import kotlinx.coroutines.launch

// Model danych dla skrótu
sealed class ShortcutIcon {
    data class VectorIcon(val iconRes: Int) : ShortcutIcon()
    data class LottieIcon(val fileName: String) : ShortcutIcon()
}

data class ShortcutItem(
    val id: String,
    val title: String,
    val icon: ShortcutIcon
)

/**
 * Reusable Shortcut Component
 * Horizontal shortcuts row that can be used across different screens
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShortcutComponent(
    modifier: Modifier = Modifier,
    isSectionFocused: Boolean = true
) {
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
                if (isSectionFocused) {
                    focusRequesters[focusedIndex].requestFocus()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sy(200)) // Fixed height for component
            .onPreviewKeyEvent { event ->
                if (!isSectionFocused || event.type != KeyEventType.KeyDown) {
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
        LaunchedEffect(isSectionFocused) {
            if (isSectionFocused && shortcuts.isNotEmpty()) {
                focusRequesters[focusedIndex].requestFocus()
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sx(134)),
            horizontalArrangement = Arrangement.spacedBy(sx(50)),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(shortcuts) { index, shortcut ->
                ShortcutCard(
                    shortcut = shortcut,
                    isFocused = index == focusedIndex && isSectionFocused,
                    focusRequester = focusRequesters[index],
                    onFocusChanged = { isFocused ->
                        if (isFocused) focusedIndex = index
                    },
                    sx = { sx(it) },
                    sy = { sy(it) }
                )
            }
        }
    }
}

@Composable
private fun ShortcutCard(
    shortcut: ShortcutItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var isCardFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .size(sx(195), sy(139))
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (nowFocused != isCardFocused) {
                    isCardFocused = nowFocused
                    onFocusChanged(nowFocused)
                }
            },
        shape = RoundedCornerShape(sx(12)),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF5AECD3) else Color(0x0DEEEEEE)
        ),
        border = if (isFocused) 
            androidx.compose.foundation.BorderStroke(sx(2), Color(0xFF5AECD3))
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(sx(16)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon section
            Box(
                modifier = Modifier
                    .size(sx(60))
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (val icon = shortcut.icon) {
                    is ShortcutIcon.VectorIcon -> {
                        Icon(
                            painter = painterResource(id = icon.iconRes),
                            contentDescription = shortcut.title,
                            modifier = Modifier.fillMaxSize(),
                            tint = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)
                        )
                    }
                    is ShortcutIcon.LottieIcon -> {
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.Asset(icon.fileName)
                        )
                        val progress by animateLottieCompositionAsState(
                            composition,
                            isPlaying = isFocused,
                            restartOnPlay = false,
                            speed = 1f
                        )

                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(sy(8)))

            // Title
            Text(
                text = shortcut.title,
                fontSize = sy(12).value.sp,
                fontWeight = FontWeight.Medium,
                color = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE),
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = sy(14).value.sp
            )
        }
    }
}