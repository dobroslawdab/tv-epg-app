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
private val TEXT_PRIMARY = Color(0xFFEEEEEE)

/**
 * DEMO SEEK OVERLAY — przewijanie symulowanego kanału live (wg designu Play):
 *
 * - filmstrip 7 miniatur (środek 480x270 z aqua borderem),
 * - tytuł bloku ramówki dla pozycji kursora,
 * - SEGMENTOWANY pasek czasu: skala 0 → live edge, kropki granic bloków ramówki
 *   z etykietami HH:mm, aqua wypełnienie do markera, marker z etykietą HH:mm:ss,
 *   czerwona kropka live edge na końcu,
 * - przycisk "Wróć do live" z badge LIVE (fokus sterowany z zewnątrz),
 * - zegar HH:mm w prawym górnym rogu.
 */
@Composable
fun DemoSeekOverlay(
    isVisible: Boolean,
    seekVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    boundaries: List<Long>,
    blockTitle: String,
    antennaStartWallMs: Long,
    isReturnToLiveFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dolny gradient pod kontrolkami
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(560))
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6311257))
                        )
                    )
            )

            // Zegar w prawym górnym rogu
            val clockText by produceState(initialValue = formatWall(System.currentTimeMillis(), withSeconds = false)) {
                while (true) {
                    value = formatWall(System.currentTimeMillis(), withSeconds = false)
                    delay(1_000)
                }
            }
            Text(
                text = clockText,
                color = TEXT_PRIMARY,
                fontSize = demoSp(32, sy),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = sy(48), end = sx(64))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = sy(48))
            ) {
                // ============ FILMSTRIP 7 MINIATUR ============
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
                            val slotVirtualMs = seekVirtualMs + frameOffsetMs
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
                                Text(
                                    text = formatWall(antennaStartWallMs + slotVirtualMs.coerceAtLeast(0), withSeconds = true),
                                    color = if (isCenter) AQUA else Color(0x99EEEEEE),
                                    fontSize = if (isCenter) demoSp(18, sy) else demoSp(13, sy),
                                    fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.height(sy(4)))
                                Box(
                                    modifier = Modifier
                                        .width(thumbWidth)
                                        .height(thumbHeight)
                                        .clip(RoundedCornerShape(sx(8)))
                                        .background(Color(0x40000000))
                                        .then(
                                            if (isCenter) Modifier.border(3.dp, AQUA, RoundedCornerShape(sx(8)))
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

                Spacer(modifier = Modifier.height(sy(28)))

                // ============ TYTUŁ BLOKU + INFO RAMÓWKI ============
                Text(
                    text = blockTitle,
                    color = TEXT_PRIMARY.copy(alpha = 0.85f),
                    fontSize = demoSp(38, sy),
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = sx(260))
                )
                Spacer(modifier = Modifier.height(sy(6)))
                // Ile do końca bieżącego bloku (od pozycji kursora) • kiedy następny • od kiedy poprzedni
                run {
                    val cursorBlock = DemoChannelSchedule.epgBlockAt(seekVirtualMs)
                    val nextBlock = DemoChannelSchedule.epgBlockAt(cursorBlock.endVirtualMs + 1)
                    val remainingMs = (cursorBlock.endVirtualMs - seekVirtualMs).coerceAtLeast(0L)
                    val remMin = remainingMs / 60_000
                    val remSec = (remainingMs % 60_000) / 1_000
                    val parts = mutableListOf(
                        "Do końca: ${remMin}:${remSec.toString().padStart(2, '0')}",
                        "Następny: ${nextBlock.title} o " +
                            formatWall(antennaStartWallMs + nextBlock.startVirtualMs, withSeconds = false)
                    )
                    if (cursorBlock.startVirtualMs > 0) {
                        val prevBlock = DemoChannelSchedule.epgBlockAt(cursorBlock.startVirtualMs - 1)
                        parts.add(
                            "Poprzedni: ${prevBlock.title} od " +
                                formatWall(antennaStartWallMs + prevBlock.startVirtualMs, withSeconds = false)
                        )
                    }
                    Text(
                        text = parts.joinToString("   •   "),
                        color = Color(0xB3EEEEEE),
                        fontSize = demoSp(20, sy),
                        modifier = Modifier.padding(start = sx(260))
                    )
                }

                Spacer(modifier = Modifier.height(sy(20)))

                // ============ SEGMENTOWANY PASEK ============
                SegmentedSeekBar(
                    seekVirtualMs = seekVirtualMs,
                    liveEdgeVirtualMs = liveEdgeVirtualMs,
                    boundaries = boundaries,
                    antennaStartWallMs = antennaStartWallMs,
                    sx = sx,
                    sy = sy
                )

                Spacer(modifier = Modifier.height(sy(36)))

                // ============ PRZYCISK "WRÓĆ DO LIVE" ============
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = sx(260))
                        .clip(RoundedCornerShape(sx(12)))
                        .background(
                            if (isReturnToLiveFocused) AQUA else Color(0x33EEEEEE)
                        )
                        .padding(horizontal = sx(28), vertical = sy(18))
                ) {
                    val contentColor = if (isReturnToLiveFocused) Color(0xFF48227C) else TEXT_PRIMARY
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(sx(4)))
                            .background(contentColor)
                            .padding(horizontal = sx(8), vertical = sy(2))
                    ) {
                        Text(
                            text = "LIVE",
                            color = if (isReturnToLiveFocused) AQUA else Color(0xFF311257),
                            fontSize = demoSp(14, sy),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(sx(14)))
                    Text(
                        text = "Wróć do live",
                        color = contentColor,
                        fontSize = demoSp(24, sy),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedSeekBar(
    seekVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    boundaries: List<Long>,
    antennaStartWallMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val barHeight = sy(6)
    val edge = liveEdgeVirtualMs.coerceAtLeast(1L)
    fun fractionOf(virtualMs: Long): Float = (virtualMs.toFloat() / edge.toFloat()).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sx(60))
            .height(sy(76))
    ) {
        val barWidth = maxWidth
        val markerFraction = fractionOf(seekVirtualMs)

        // Tor paska
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.TopStart)
                .offset(y = sy(20))
                .clip(RoundedCornerShape(barHeight / 2))
                .background(Color(0x40EEEEEE))
        ) {
            // Aqua wypełnienie do markera
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = markerFraction)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(AQUA)
            )
        }

        // Kropki granic bloków ramówki + etykiety HH:mm
        boundaries.forEach { boundary ->
            val f = fractionOf(boundary)
            Box(
                modifier = Modifier
                    .offset(x = barWidth * f - sy(8), y = sy(20) + barHeight / 2 - sy(8))
                    .size(sy(16))
                    .background(Color(0xFFCCCCCC), CircleShape)
            )
            Text(
                text = formatWall(antennaStartWallMs + boundary, withSeconds = false),
                color = TEXT_PRIMARY,
                fontSize = demoSp(20, sy),
                modifier = Modifier.offset(x = barWidth * f - sx(36), y = sy(44))
            )
        }

        // Czerwona kropka live edge (prawy koniec)
        Box(
            modifier = Modifier
                .offset(x = barWidth - sy(8), y = sy(20) + barHeight / 2 - sy(8))
                .size(sy(16))
                .background(Color.Red, CircleShape)
        )

        // Marker bieżącej pozycji kursora + etykieta HH:mm:ss
        Box(
            modifier = Modifier
                .offset(x = barWidth * markerFraction - sy(14), y = sy(20) + barHeight / 2 - sy(14))
                .size(sy(28))
                .background(AQUA, CircleShape)
        )
        Text(
            text = formatWall(antennaStartWallMs + seekVirtualMs, withSeconds = true),
            color = TEXT_PRIMARY,
            fontSize = demoSp(22, sy),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = (barWidth * markerFraction - sx(54)).coerceAtLeast(0.dp), y = sy(44))
        )
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
