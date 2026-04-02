package com.uxellence.tv.v3.epg

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * TIMESHIFT OVERLAY — Full-Width Filmstrip
 *
 * Full-screen-width strip of thumbnails at the bottom:
 *
 *   |cut|  [side]  [side]  [== CURRENT ==]  [side]  [side]  |cut|
 *   -40s   -30s     -20s      -10s/current    +10s    +20s   +30s
 *
 * - Center: 480x270 with aqua border (16:9, large)
 * - Sides: 320x180 (16:9, dimmed)
 * - Outermost: clipped by screen edge
 * - Background video is frozen (player paused)
 */
@Composable
fun TimeshiftOverlay(
    isVisible: Boolean,
    offsetMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    isAtLiveEdge: Boolean,
    maxBufferMs: Long,
    totalOffsetMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier,
    isVodMode: Boolean = false,
    vodDurationMs: Long = 0L
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .zIndex(20f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000)),  // Semi-transparent background over frozen frame
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = sy(40))
            ) {
                // ============ FULL-WIDTH FILMSTRIP ============
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.wrapContentWidth(unbounded = true)
                    ) {
                        frames.forEachIndexed { index, (frameOffsetMs, bitmap) ->
                            val isCenter = index == frames.size / 2
                            val absolutePositionMs = offsetMs + frameOffsetMs

                            // In VOD mode: hide slots outside 0..duration (but keep spacer for alignment)
                            val isOutOfRange = isVodMode && !isCenter &&
                                (absolutePositionMs < 0 || (vodDurationMs > 0 && absolutePositionMs > vodDurationMs))

                            // Center: 480x270, sides: 320x180
                            val thumbWidth = if (isCenter) sx(480) else sx(320)
                            val thumbHeight = if (isCenter) sy(270) else sy(180)
                            val timeLabel = if (isVodMode) {
                                formatVodPosition(absolutePositionMs.coerceAtLeast(0))
                            } else {
                                formatOffset(offsetMs - frameOffsetMs)
                            }

                            if (index > 0) {
                                Spacer(modifier = Modifier.width(sx(10)))
                            }

                            // Out-of-range: invisible spacer to keep center aligned
                            if (isOutOfRange) {
                                Spacer(modifier = Modifier.width(thumbWidth).height(thumbHeight))
                                return@forEachIndexed
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Time label
                                Text(
                                    text = timeLabel,
                                    color = if (isCenter) Color(0xFF5AECD3) else Color(0x99EEEEEE),
                                    fontSize = if (isCenter) 18.sp else 13.sp,
                                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                                )

                                Spacer(modifier = Modifier.height(sy(4)))

                                // Thumbnail card
                                Box(
                                    modifier = Modifier
                                        .width(thumbWidth)
                                        .height(thumbHeight)
                                        .clip(RoundedCornerShape(sx(8)))
                                        .background(Color(0x40000000))
                                        .then(
                                            if (isCenter) {
                                                Modifier.border(3.dp, Color(0xFF5AECD3), RoundedCornerShape(sx(8)))
                                            } else {
                                                Modifier.border(1.dp, Color(0x50EEEEEE), RoundedCornerShape(sx(8)))
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bitmap != null && !bitmap.isRecycled) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(sx(8)))
                                        )
                                    } else {
                                        // Placeholder
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = timeLabel,
                                                color = Color(0x66EEEEEE),
                                                fontSize = if (isCenter) 22.sp else 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(sy(12)))

                // ============ STATUS + SEEK BAR ============
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isVodMode) {
                        Text(
                            text = "${formatVodPosition(totalOffsetMs)} / ${formatVodPosition(vodDurationMs)}",
                            color = Color(0xFF5AECD3),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(sx(20)))
                        Text(
                            text = "OK = odtwarzaj   BACK = wróć",
                            color = Color(0x99EEEEEE),
                            fontSize = 14.sp
                        )
                    } else if (isAtLiveEdge) {
                        Box(modifier = Modifier.size(10.dp).background(Color.Red, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LIVE", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            text = formatOffset(totalOffsetMs),
                            color = Color(0xFF5AECD3),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(sx(20)))
                        Text(
                            text = "OK = odtwarzaj   BACK = live",
                            color = Color(0x99EEEEEE),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sy(8)))

                // Seek bar — full width with padding
                SeekBar(
                    offsetMs = totalOffsetMs,
                    maxBufferMs = maxBufferMs,
                    isAtLiveEdge = isAtLiveEdge,
                    sx = sx,
                    sy = sy,
                    isVodMode = isVodMode
                )
            }
        }
    }
}

@Composable
private fun SeekBar(
    offsetMs: Long,
    maxBufferMs: Long,
    isAtLiveEdge: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    isVodMode: Boolean = false
) {
    val barHeight = sy(6)
    val progress = if (isVodMode) {
        // VOD: offsetMs = current position, maxBufferMs = total duration
        if (maxBufferMs > 0) (offsetMs.toFloat() / maxBufferMs.toFloat()).coerceIn(0f, 1f) else 0f
    } else if (maxBufferMs > 0) {
        1f - (offsetMs.toFloat() / maxBufferMs.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sx(60))
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(Color(0x40EEEEEE))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress)
                .clip(RoundedCornerShape(barHeight / 2))
                .background(Color(0xFF5AECD3))
        )

        // Live edge dot (hidden in VOD mode)
        if (!isVodMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(sy(12))
                    .background(if (isAtLiveEdge) Color.Red else Color(0xFFEEEEEE), CircleShape)
            )
        }
    }
}

private fun formatOffset(offsetMs: Long): String {
    if (offsetMs <= 0) return "LIVE"
    val totalSeconds = offsetMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "-${minutes}:${seconds.toString().padStart(2, '0')}" else "-${seconds}s"
}

private fun formatVodPosition(positionMs: Long): String {
    if (positionMs < 0) return "0:00"
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}
