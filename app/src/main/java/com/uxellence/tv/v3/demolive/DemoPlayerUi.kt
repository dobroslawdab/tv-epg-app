package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

private val AQUA = Color(0xFF5AECD3)
private val BG_PURPLE = Color(0xFF48227C)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)

/** Strefy fokusu zunifikowanego UI playera. */
enum class PlayerZone { BUTTONS, STRIP, DESCRIPTION }

/**
 * DEMO PLAYER UI — jeden widok playera w trzech stanach (wg designu Play):
 *
 *  - BUTTONS: nagłówek kanału + tytuł + metadane, segmentowany pasek bloków
 *    ramówki, rząd przycisków (fokus domyślnie "Zatrzymaj"), skrót opisu,
 *  - STRIP (UP z przycisków / przewijanie): taśma miniatur nad TYM SAMYM
 *    paskiem, kursor z czasem, fokus na taśmie, przyciski wciąż widoczne,
 *  - DESCRIPTION (DOWN z przycisków): pełny opis na tle brandowym, obraz
 *    przechodzi do PIP (rysowany przez DemoLiveScreen NAD tą warstwą).
 */
@Composable
fun DemoPlayerUi(
    isVisible: Boolean,
    zone: PlayerZone,
    block: DemoChannelSchedule.EpgBlock,
    currentVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    dvrStartVirtualMs: Long,
    scrubCursorMs: Long,
    antennaStartWallMs: Long,
    isPaused: Boolean,
    buttonsFocusIndex: Int,     // 0..4; -1 gdy fokus poza przyciskami
    frames: List<Pair<Long, Bitmap?>>,
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
            if (zone == PlayerZone.DESCRIPTION) {
                // Stan OPIS: pełne tło brandowe (wideo idzie do PIP nad warstwą)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BG_PURPLE)
                )
                Column(modifier = Modifier.padding(start = sx(40), top = sy(40), end = sx(60))) {
                    PlayerHeader(block, antennaStartWallMs, sx, sy)
                    Spacer(modifier = Modifier.height(sy(24)))
                    DemoSegmentedBlockBar(
                        block = block,
                        positionMs = currentVirtualMs,
                        cursorMs = null,
                        liveEdgeVirtualMs = liveEdgeVirtualMs,
                        antennaStartWallMs = antennaStartWallMs,
                        showTimes = false,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(24)))
                    PlayerButtonsRow(isPaused, buttonsFocusIndex, sx, sy)
                    Spacer(modifier = Modifier.height(sy(32)))
                    Text(
                        text = block.description,
                        color = TEXT_PRIMARY,
                        fontSize = demoSp(24, sy),
                        lineHeight = demoSp(34, sy),
                        modifier = Modifier.fillMaxWidth(0.62f)
                    )
                }
                // Prawy dolny róg zostaje wolny — tam DemoLiveScreen rysuje PIP
            } else {
                // Stany BUTTONS / STRIP: gradient od dołu nad grającym wideo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (zone == PlayerZone.STRIP) sy(640) else sy(440))
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xF0311257))
                            )
                        )
                )

                if (zone == PlayerZone.STRIP) {
                    // Nagłówek u góry (jak w designie przewijania)
                    Box(modifier = Modifier.padding(start = sx(40), top = sy(40))) {
                        PlayerHeader(block, antennaStartWallMs, sx, sy)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = sx(80), end = sx(80), bottom = sy(48))
                ) {
                    if (zone == PlayerZone.STRIP) {
                        // Taśma miniatur — fokus na niej podczas przewijania
                        DemoFilmstrip(
                            centerVirtualMs = scrubCursorMs,
                            liveEdgeVirtualMs = liveEdgeVirtualMs,
                            frames = frames,
                            antennaStartWallMs = antennaStartWallMs,
                            sx = sx,
                            sy = sy
                        )
                        Spacer(modifier = Modifier.height(sy(24)))
                    } else {
                        PlayerHeader(block, antennaStartWallMs, sx, sy)
                        Spacer(modifier = Modifier.height(sy(18)))
                    }

                    DemoSegmentedBlockBar(
                        block = block,
                        positionMs = currentVirtualMs,
                        cursorMs = if (zone == PlayerZone.STRIP) scrubCursorMs else null,
                        liveEdgeVirtualMs = liveEdgeVirtualMs,
                        antennaStartWallMs = antennaStartWallMs,
                        showTimes = zone == PlayerZone.STRIP,
                        sx = sx, sy = sy
                    )

                    Spacer(modifier = Modifier.height(sy(20)))

                    PlayerButtonsRow(isPaused, buttonsFocusIndex, sx, sy)

                    if (zone == PlayerZone.BUTTONS) {
                        Spacer(modifier = Modifier.height(sy(18)))
                        Text(
                            text = block.description,
                            color = Color(0xCCEEEEEE),
                            fontSize = demoSp(20, sy),
                            lineHeight = demoSp(28, sy),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        )
                    }
                }
            }
        }
    }
}

/** Nagłówek: numer kanału + nazwa + tytuł bloku + metadane (wg designu). */
@Composable
private fun PlayerHeader(
    block: DemoChannelSchedule.EpgBlock,
    antennaStartWallMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(sx(6)))
                .border(1.5.dp, Color(0x99EEEEEE), RoundedCornerShape(sx(6)))
                .padding(horizontal = sx(12), vertical = sy(6))
        ) {
            Text("122", color = TEXT_PRIMARY, fontSize = demoSp(22, sy), fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(sx(20)))
        Text(
            "DEMO TV",
            color = TEXT_PRIMARY,
            fontSize = demoSp(26, sy),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = sy(4))
        )
        Spacer(modifier = Modifier.width(sx(36)))
        Column {
            Text(
                text = block.title,
                color = TEXT_PRIMARY,
                fontSize = demoSp(36, sy),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(sy(4)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val blockMin = (block.endVirtualMs - block.startVirtualMs) / 60_000
                Text(
                    text = formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false) +
                        "–" + formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false) +
                        "  |  ${block.genre}  |  $blockMin min  |  ${block.year}  |  ${block.country}  |  ${block.age}  |",
                    color = Color(0xCCEEEEEE),
                    fontSize = demoSp(18, sy)
                )
                Spacer(modifier = Modifier.width(sx(10)))
                listOf("S", "W", "N", "P").forEach { letter ->
                    Box(
                        modifier = Modifier
                            .padding(end = sx(6))
                            .clip(RoundedCornerShape(sx(4)))
                            .border(1.dp, Color(0x99EEEEEE), RoundedCornerShape(sx(4)))
                            .padding(horizontal = sx(6), vertical = sy(1))
                    ) {
                        Text(letter, color = Color(0xCCEEEEEE), fontSize = demoSp(13, sy))
                    }
                }
            }
        }
    }
}

/**
 * Segmentowany pasek bloków ramówki (poprzedni | bieżący | następny),
 * szerokości proporcjonalne do długości bloków, przerwy między segmentami.
 * Aqua wypełnienie do pozycji odtwarzania, kursor (STRIP) i znacznik live.
 */
@Composable
private fun DemoSegmentedBlockBar(
    block: DemoChannelSchedule.EpgBlock,
    positionMs: Long,
    cursorMs: Long?,
    liveEdgeVirtualMs: Long,
    antennaStartWallMs: Long,
    showTimes: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val prevBlock = if (block.startVirtualMs > 0) DemoChannelSchedule.epgBlockAt(block.startVirtualMs - 1) else null
    val nextBlock = DemoChannelSchedule.epgBlockAt(block.endVirtualMs + 1)

    val windowStart = prevBlock?.startVirtualMs ?: block.startVirtualMs
    val windowEnd = nextBlock.endVirtualMs
    val windowSpan = (windowEnd - windowStart).coerceAtLeast(1L)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showTimes) sy(64) else sy(28))
    ) {
        val fullWidth = maxWidth
        fun xOf(virtualMs: Long): Dp =
            fullWidth * ((virtualMs - windowStart).toFloat() / windowSpan.toFloat()).coerceIn(0f, 1f)

        val gap = sx(8)
        val barHeight = sy(6)
        val segments = listOfNotNull(prevBlock, block, nextBlock)

        // Segmenty
        segments.forEach { seg ->
            val segStartX = xOf(seg.startVirtualMs) + if (seg !== segments.first()) gap / 2 else 0.dp
            val segEndX = xOf(seg.endVirtualMs) - if (seg !== segments.last()) gap / 2 else 0.dp
            val segWidth = (segEndX - segStartX).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .offset(x = segStartX, y = sy(10))
                    .width(segWidth)
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(Color(0x40EEEEEE))
            ) {
                // Wypełnienie: do pozycji odtwarzania (w obrębie segmentu)
                val fillEnd = positionMs.coerceIn(seg.startVirtualMs, seg.endVirtualMs)
                val fillFraction = ((fillEnd - seg.startVirtualMs).toFloat() /
                    (seg.endVirtualMs - seg.startVirtualMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                if (fillFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fillFraction)
                            .clip(RoundedCornerShape(barHeight / 2))
                            .background(AQUA)
                    )
                }
            }
        }

        // Znacznik live (gdy live edge w oknie i różny od pozycji)
        if (liveEdgeVirtualMs in windowStart..windowEnd) {
            Box(
                modifier = Modifier
                    .offset(x = xOf(liveEdgeVirtualMs) - sy(5), y = sy(10) + barHeight / 2 - sy(5))
                    .size(sy(10))
                    .background(Color(0xFFEEEEEE), CircleShape)
            )
            if (showTimes) {
                Text(
                    text = "live: " + formatWall(antennaStartWallMs + liveEdgeVirtualMs, withSeconds = false),
                    color = Color(0xCCEEEEEE),
                    fontSize = demoSp(18, sy),
                    modifier = Modifier.offset(x = (xOf(liveEdgeVirtualMs) - sx(40)).coerceAtLeast(0.dp), y = sy(30))
                )
            }
        }

        // Kursor przewijania (STRIP)
        if (cursorMs != null) {
            Box(
                modifier = Modifier
                    .offset(x = xOf(cursorMs) - sy(12), y = sy(10) + barHeight / 2 - sy(12))
                    .size(sy(24))
                    .background(AQUA, CircleShape)
            )
        }

        if (showTimes) {
            // Czasy granic segmentów + pogrubiony czas kursora
            segments.forEach { seg ->
                Text(
                    text = formatWall(antennaStartWallMs + seg.startVirtualMs, withSeconds = false),
                    color = Color(0xCCEEEEEE),
                    fontSize = demoSp(18, sy),
                    modifier = Modifier.offset(x = xOf(seg.startVirtualMs), y = sy(30))
                )
            }
            if (cursorMs != null) {
                Text(
                    text = formatWall(antennaStartWallMs + cursorMs, withSeconds = true),
                    color = TEXT_PRIMARY,
                    fontSize = demoSp(20, sy),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (xOf(cursorMs) - sx(50)).coerceAtLeast(0.dp), y = sy(30))
                )
            }
        }
    }
}

/** Rząd przycisków playera (wg designu: Zatrzymaj | Wróć do live | Zacznij od początku | Nagraj | Napisy...). */
@Composable
private fun PlayerButtonsRow(
    isPaused: Boolean,
    focusedIndex: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row {
        PlayerButton(if (isPaused) "▶  Wznów" else "⏸  Zatrzymaj", focusedIndex == 0, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        PlayerButton("LIVE  Wróć do live", focusedIndex == 1, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        PlayerButton("↺  Zacznij od początku", focusedIndex == 2, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        PlayerButton("REC  Nagraj", focusedIndex == 3, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        PlayerButton("⚙  Napisy, dźwięk, jakość", focusedIndex == 4, sx, sy)
    }
}

@Composable
private fun PlayerButton(
    label: String,
    isFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(sx(10)))
            .background(if (isFocused) AQUA else Color(0x33EEEEEE))
            .padding(horizontal = sx(22), vertical = sy(14))
    ) {
        Text(
            text = label,
            color = if (isFocused) BG_PURPLE else TEXT_PRIMARY,
            fontSize = demoSp(20, sy),
            fontWeight = FontWeight.Medium
        )
    }
}
