package com.uxellence.tv.v3.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uxellence.tv.v3.version001.VodContent

/**
 * Horizontal VOD Card Component (344x194px)
 *
 * Used for displaying horizontal thumbnails in WIDEO grid
 *
 * Features:
 * - Aspect ratio: 16:9 (horizontal)
 * - Gradient overlay at bottom
 * - Optional channel number badge (top-right)
 * - Title text over gradient
 * - Aqua border on focus (#5AECD3)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HorizontalVodCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onClick: () -> Unit = {},
    onLongPress: () -> Unit = {},   // długie OK → menu kontekstowe (0 = wyłączone)
    channelNumber: String? = null,  // Optional channel number badge
    modifier: Modifier = Modifier
) {
    // Rozdział krótkiego OK (KeyUp) od długiego (KeyDown repeatCount>0) —
    // wzorzec VerticalVodCard; long NIGDY nie odpala onClick
    var longPressHandled by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }
    // Adjusted for 4 columns: 425x239px (16:9 ratio preserved)
    // 1920 - 160 (L+R padding) - 60 (3 gaps × 20) = 1700 / 4 = 425
    val itemWidth = sx(425)
    val itemHeight = sy(239)

    Box(
        modifier = modifier
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
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Enter || event.key == Key.DirectionCenter ||
                    event.key == Key.NumPadEnter
                ) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (event.nativeKeyEvent.repeatCount == 0) {
                            longPressHandled = false
                        } else if (!longPressHandled) {
                            longPressHandled = true
                            onLongPress()
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (!longPressHandled) onClick()
                        longPressHandled = false
                    }
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        // Image
        AsyncImage(
            model = vodContent.imageUrl,
            contentDescription = vodContent.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay at bottom
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

        // Optional channel number badge (top-right)
        if (channelNumber != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = sx(12), top = sy(12))
                    .border(
                        width = sx(1),
                        color = Color.White.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(sx(4))
                    )
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
        }

        // Bottom-left row: channel logo + title
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(12), bottom = sy(12), end = sx(12)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(8))
        ) {
            if (vodContent.channelLogoUrl.isNotBlank()) {
                AsyncImage(
                    model = vodContent.channelLogoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(sx(64))
                )
            }
            Text(
                text = vodContent.title,
                color = Color.White,
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
