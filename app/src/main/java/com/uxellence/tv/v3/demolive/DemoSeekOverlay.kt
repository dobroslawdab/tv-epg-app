package com.uxellence.tv.v3.demolive

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AQUA = Color(0xFF5AECD3)

/**
 * Filmstrip 7 miniatur — współdzielony przez overlay przewijania i pasek
 * postępu w warstwie kontrolek (środek 480x270 z aqua borderem, boki 320x180).
 */
@Composable
internal fun DemoFilmstrip(
    centerVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    antennaStartWallMs: Long,
    showTimeLabels: Boolean = true,   // false: czasy pokazuje pasek postępu (design)
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
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
                val slotVirtualMs = centerVirtualMs + frameOffsetMs
                val inRange = slotVirtualMs in 0..liveEdgeVirtualMs

                val thumbWidth = if (isCenter) sx(480) else sx(320)
                val thumbHeight = if (isCenter) sy(270) else sy(180)

                if (index > 0) Spacer(modifier = Modifier.width(sx(10)))

                if (!inRange && !isCenter) {
                    // Poza DVR/za live edge: niewidoczny spacer trzyma wyrównanie środka
                    Spacer(modifier = Modifier.width(thumbWidth).height(thumbHeight))
                    return@forEachIndexed
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showTimeLabels) {
                        Text(
                            text = formatWall(antennaStartWallMs + slotVirtualMs.coerceAtLeast(0), withSeconds = true),
                            color = if (isCenter) AQUA else Color(0x99EEEEEE),
                            fontSize = if (isCenter) demoSp(18, sy) else demoSp(13, sy),
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(sy(4)))
                    }
                    Box(
                        modifier = Modifier
                            .width(thumbWidth)
                            .height(thumbHeight)
                            .clip(RoundedCornerShape(sx(8)))
                            .background(Color(0x40000000))
                            .then(
                                // Środkowa miniatura: biała ramka (design przewijania)
                                if (isCenter) Modifier.border(3.dp, Color(0xFFEEEEEE), RoundedCornerShape(sx(8)))
                                else Modifier.border(1.dp, Color(0x50EEEEEE), RoundedCornerShape(sx(8)))
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
                        }
                    }
                }
            }
        }
    }
}

internal fun formatWall(wallMs: Long, withSeconds: Boolean): String {
    val pattern = if (withSeconds) "HH:mm:ss" else "HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(wallMs))
}

/**
 * Font skalowany jak w EpgDayScreen: rozmiar z designu 1920x1080 × współczynnik sy.
 * Surowe .sp na emulatorze TV (~0.5 skali) dawało fonty 2x za duże.
 */
internal fun demoSp(designPx: Int, sy: (Int) -> Dp): androidx.compose.ui.unit.TextUnit =
    (designPx * sy(1).value).sp
