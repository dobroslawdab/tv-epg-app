package com.uxellence.tv.v3.demolive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private val AQUA = Color(0xFF5AECD3)
private val BG_PURPLE = Color(0xFF48227C)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)

/**
 * DEMO CONTROLS LAYER — kontrolki playera na live (OK z pełnego ekranu):
 * tytuł bieżącego bloku, pasek postępu emisji i przyciski:
 *   0 = Pauza/Wznów (działa), 1 = Zacznij od początku (działa), 2 = Nagraj (atrapa).
 * LEFT/RIGHT przesuwa fokus, OK aktywuje, BACK chowa warstwę.
 */
@Composable
fun DemoControlsLayer(
    isVisible: Boolean,
    block: DemoChannelSchedule.EpgBlock,
    currentVirtualMs: Long,
    antennaStartWallMs: Long,
    isPaused: Boolean,
    focusedIndex: Int,            // -1 gdy fokus na pasku postępu
    isBarFocused: Boolean,
    barCursorVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    frames: List<Pair<Long, android.graphics.Bitmap?>>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(12f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(420))
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6311257))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sx(80), end = sx(80), bottom = sy(56))
                    .fillMaxWidth()
            ) {
                // Miniaturki nad paskiem — jak podczas przewijania (fokus na pasku)
                if (isBarFocused && frames.isNotEmpty()) {
                    DemoFilmstrip(
                        centerVirtualMs = barCursorVirtualMs,
                        liveEdgeVirtualMs = liveEdgeVirtualMs,
                        frames = frames,
                        antennaStartWallMs = antennaStartWallMs,
                        sx = sx,
                        sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(24)))
                }
                Text(
                    text = block.title,
                    color = TEXT_PRIMARY,
                    fontSize = demoSp(38, sy),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(sy(6)))
                Text(
                    text = formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false) +
                        "–" + formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false) +
                        if (isPaused) "   |   ⏸ wstrzymane" else "",
                    color = Color(0xCCEEEEEE),
                    fontSize = demoSp(20, sy)
                )
                Spacer(modifier = Modifier.height(sy(14)))

                // Pasek postępu bloku — fokusowalny (UP z przycisków): LEFT/RIGHT
                // przesuwa kursor, OK skacze do kursora, DOWN wraca na przyciski
                val blockDur = (block.endVirtualMs - block.startVirtualMs).coerceAtLeast(1L)
                val progress = ((currentVirtualMs - block.startVirtualMs).toFloat() / blockDur).coerceIn(0f, 1f)
                val cursorFraction = ((barCursorVirtualMs - block.startVirtualMs).toFloat() / blockDur).coerceIn(0f, 1f)
                val barHeight = if (isBarFocused) sy(10) else sy(6)

                if (isBarFocused) {
                    // Etykieta czasu nad kursorem
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.7f)) {
                        Text(
                            text = formatWall(antennaStartWallMs + barCursorVirtualMs, withSeconds = true),
                            color = AQUA,
                            fontSize = demoSp(22, sy),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(
                                x = (maxWidth * cursorFraction - sx(54)).coerceAtLeast(0.dp)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(sy(6)))
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(sy(28)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val barWidth = maxWidth
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(barHeight / 2))
                            .background(if (isBarFocused) Color(0x66EEEEEE) else Color(0x40EEEEEE))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(barHeight / 2))
                                .background(AQUA)
                        )
                    }
                    if (isBarFocused) {
                        // Kursor przewijania
                        Box(
                            modifier = Modifier
                                .offset(x = barWidth * cursorFraction - sy(12))
                                .size(sy(24))
                                .background(AQUA, androidx.compose.foundation.shape.CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sy(14)))

                Row {
                    ControlButton(
                        label = if (isPaused) "▶  Wznów" else "⏸  Pauza",
                        isFocused = focusedIndex == 0,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.width(sx(20)))
                    ControlButton(
                        label = "↺  Zacznij od początku",
                        isFocused = focusedIndex == 1,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.width(sx(20)))
                    ControlButton(
                        label = "REC  Nagraj",
                        isFocused = focusedIndex == 2,
                        sx = sx, sy = sy
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    isFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(sx(10)))
            .background(if (isFocused) AQUA else Color(0x33EEEEEE))
            .padding(horizontal = sx(28), vertical = sy(16))
    ) {
        Text(
            text = label,
            color = if (isFocused) BG_PURPLE else TEXT_PRIMARY,
            fontSize = demoSp(22, sy),
            fontWeight = FontWeight.Medium
        )
    }
}
