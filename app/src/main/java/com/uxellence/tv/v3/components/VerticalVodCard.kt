package com.uxellence.tv.v3.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uxellence.tv.v3.version001.VodContent

/**
 * Vertical VOD Card Component (220x380px outer, 200x280px poster)
 *
 * Used for displaying vertical movie posters in KINO PLAY grid
 *
 * Features:
 * - Aspect ratio: ~0.71 (portrait, movie poster style)
 * - Scale animation: 1.0 → 1.1 on focus
 * - Movie title displayed below poster (always visible)
 * - Aqua border on focus (#5AECD3)
 */
@Composable
fun VerticalVodCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val itemWidth = sx(245)
    val itemHeight = sy(425)

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        label = "verticalCardScale"
    )

    Column(
        modifier = modifier
            .width(itemWidth)
            .height(itemHeight)
            .zIndex(if (isFocused) 1f else 0f)  // Focused karta na wierzchu — bez odpychania sąsiadów
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(16))
    ) {
        // Poster image
        Box(
            modifier = Modifier
                .width(sx(280))
                .height(sy(392))
                .clip(RoundedCornerShape(sx(12)))
                .border(
                    width = if (isFocused) (6 * sx(1).value / 1.dp.value).dp else 0.dp,
                    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                )
        ) {
            AsyncImage(
                model = vodContent.imageUrl,
                contentDescription = vodContent.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Title label (always shown)
        Text(
            text = vodContent.title,
            color = Color(0xFFEEEEEE),
            fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.W500,
            letterSpacing = 0.4.sp,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = sx(8))
        )
    }
}
