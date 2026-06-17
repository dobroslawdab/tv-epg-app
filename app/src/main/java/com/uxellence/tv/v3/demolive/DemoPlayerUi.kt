package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.uxellence.tv.v3.channels.TvChannelData
import com.uxellence.tv.v3.epg.ChannelInfoOverlay
import kotlin.math.abs

private val AQUA = Color(0xFF5AECD3)
private val BG_PURPLE = Color(0xFF48227C)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)
private val TEXT_SECONDARY = Color(0xCCEEEEEE)

// Siatka layoutu wspólna z warstwą EPG: badge kanału na X=40 (CHANNEL_INFO_X),
// główna kolumna treści na X=330 (FOCUSED_X) — "odstęp od lewej jak w aplikacji"
private const val LEFT_X = 40
private const val MAIN_X = 330
// Pasek postępu: bieżący materiał ma ZAWSZE stałą szerokość (niezależnie czy trwa
// 20 min czy 2 h); sąsiednie materiały to ścieśnione segmenty po bokach
private const val BAR_MAIN_W = 1230
private const val BAR_GAP = 16
// Fold opisu: w BUTTONS kolumna zsunięta w dół (opis częściowo pod ekranem),
// fokus na opisie (SNIPPET) podnosi ją tak, by opis był widoczny w całości
private const val FOLD_OFFSET = 150

/** Strefy fokusu zunifikowanego UI playera. */
enum class PlayerZone { BUTTONS, STRIP, SNIPPET, DETAIL }

/** Relacja bloku ramówki do "teraz" — steruje przyciskiem akcji w detalu. */
enum class BlockTiming { PAST, CURRENT, FUTURE }

/**
 * DEMO PLAYER UI — jeden widok playera w trzech stanach (wg designu Play):
 *
 *  - BUTTONS: nagłówek kanału (badge+logo jak w EPG) + tytuł + metadane,
 *    pasek stałej szerokości, rząd przycisków; opis pod przyciskami częściowo
 *    schowany pod foldem ekranu,
 *  - SNIPPET (DOWN z przycisków): kolumna podjeżdża do góry, opis w aqua ramce
 *    widoczny w całości (3 linie + wielokropek),
 *  - STRIP (UP z przycisków / przewijanie): nagłówek u góry, taśma miniatur,
 *    czasy NAD paskiem (start | kursor | live | koniec); materiał pod kursorem
 *    staje się głównym — jego metadane i jego segment paska na środku.
 *
 *  DETAIL renderuje DemoLiveScreen prawdziwym MovieDetailScreen (ten widok
 *  jest wtedy ukryty).
 */
@Composable
fun DemoPlayerUi(
    isVisible: Boolean,
    zone: PlayerZone,
    block: DemoChannelSchedule.EpgBlock,
    prevBlock: DemoChannelSchedule.EpgBlock?,
    nextBlock: DemoChannelSchedule.EpgBlock?,
    channel: TvChannelData?,
    channelNumber: Int,
    currentVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    dvrStartVirtualMs: Long,
    scrubCursorMs: Long,
    antennaStartWallMs: Long,
    isPaused: Boolean,
    isAtLiveEdge: Boolean,      // na live: status "Oglądasz live" zamiast "Wróć do live"
    buttonsFocusIndex: Int,     // 0..4; -1 gdy fokus poza przyciskami
    forwardBlockedMsgVisible: Boolean = false,  // banner blokady przewijania do przodu
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
            // Gradient od dołu — te same stopy i kolor co warstwa EPG (DemoEpgLayer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(804))
                    .offset(y = sy(276))
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color(0x0048227C),
                            0.63f to Color(0xFF48227C)
                        )
                    )
            )

            // Komunikat blokady przewijania do przodu (TVN/Disney) — wg wytycznych
            // zachowanie nie zmienia się, tylko informujemy że forward jest niedostępny
            if (forwardBlockedMsgVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = sy(120))
                        .clip(RoundedCornerShape(sx(8)))
                        .background(Color(0xCC1A0E2E))
                        .padding(horizontal = sx(28), vertical = sy(14))
                ) {
                    Text(
                        text = "Przewijanie tego programu do przodu nie jest dostępne",
                        color = TEXT_PRIMARY,
                        fontSize = demoSp(22, sy),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Zegar ścienny (prawy górny róg, jak w designie)
            Text(
                text = formatWall(antennaStartWallMs + liveEdgeVirtualMs, withSeconds = false),
                color = TEXT_PRIMARY,
                fontSize = demoSp(28, sy),
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = sy(40), end = sx(60))
            )

            if (zone == PlayerZone.STRIP) {
                // Nagłówek u góry; metadane bloku POD KURSOREM (materiał pod kursorem
                // jest głównym — przeskok na sąsiedni przepina nagłówek i pasek)
                PlayerHeader(
                    block = block,
                    channel = channel,
                    channelNumber = channelNumber,
                    modifier = Modifier.padding(top = sy(40)),
                    sx = sx, sy = sy
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = sy(48))
                ) {
                    DemoFilmstrip(
                        centerVirtualMs = scrubCursorMs,
                        liveEdgeVirtualMs = liveEdgeVirtualMs,
                        frames = frames,
                        antennaStartWallMs = antennaStartWallMs,
                        showTimeLabels = false,   // czasy są nad paskiem, nie nad miniaturami
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(16)))
                    DemoFixedBlockBar(
                        block = block,
                        prevBlock = prevBlock,
                        nextBlock = nextBlock,
                        positionMs = currentVirtualMs,
                        cursorMs = scrubCursorMs,
                        liveEdgeVirtualMs = liveEdgeVirtualMs,
                        antennaStartWallMs = antennaStartWallMs,
                        showTimes = true,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(20)))
                    Box(modifier = Modifier.padding(start = sx(MAIN_X))) {
                        PlayerButtonsRow(isPaused, buttonsFocusIndex, isAtLiveEdge, sx, sy)
                    }
                }
            } else {
                // BUTTONS / SNIPPET — dolna kolumna z animowanym foldem opisu
                val foldOffset by animateDpAsState(
                    targetValue = if (zone == PlayerZone.SNIPPET) 0.dp else sy(FOLD_OFFSET),
                    animationSpec = tween(350),
                    label = "demo_player_fold"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .offset(y = foldOffset)
                        .padding(bottom = sy(100))
                ) {
                    PlayerHeader(
                        block = block,
                        channel = channel,
                        channelNumber = channelNumber,
                        modifier = Modifier,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(18)))
                    DemoFixedBlockBar(
                        block = block,
                        prevBlock = prevBlock,
                        nextBlock = nextBlock,
                        positionMs = currentVirtualMs,
                        cursorMs = null,
                        liveEdgeVirtualMs = liveEdgeVirtualMs,
                        antennaStartWallMs = antennaStartWallMs,
                        showTimes = false,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(24)))
                    Box(modifier = Modifier.padding(start = sx(MAIN_X))) {
                        PlayerButtonsRow(isPaused, buttonsFocusIndex, isAtLiveEdge, sx, sy)
                    }
                    Spacer(modifier = Modifier.height(sy(28)))
                    // Opis: ramka zawsze zajmuje miejsce (transparentna gdy bez fokusu),
                    // żeby fokus nie przesuwał tekstu; aqua ramka tylko w SNIPPET
                    Box(
                        modifier = Modifier
                            .padding(start = sx(MAIN_X))
                            .width(sx(980))
                            .border(
                                2.dp,
                                if (zone == PlayerZone.SNIPPET) AQUA else Color.Transparent,
                                RoundedCornerShape(sx(6))
                            )
                            .padding(sx(12))
                    ) {
                        Text(
                            text = block.description,
                            color = TEXT_SECONDARY,
                            fontSize = demoSp(22, sy),
                            lineHeight = demoSp(32, sy),
                            maxLines = 3,
                            overflow = if (zone == PlayerZone.SNIPPET) {
                                TextOverflow.Ellipsis
                            } else {
                                TextOverflow.Clip   // reszta chowa się pod foldem
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Nagłówek playera wg designu: badge numeru + logo kanału przy lewej (X=40,
 * komponent ChannelInfoOverlay z warstwy EPG), tytuł + metadane w głównej
 * kolumnie (X=330). Metadane bez godzin — czasy pokazuje pasek przy przewijaniu.
 */
@Composable
private fun PlayerHeader(
    block: DemoChannelSchedule.EpgBlock,
    channel: TvChannelData?,
    channelNumber: Int,
    modifier: Modifier,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (channel != null) {
            ChannelInfoOverlay(
                channel = channel,
                channelNumber = channelNumber,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = sx(LEFT_X)),
                sx = sx, sy = sy
            )
        } else {
            // Fallback zanim warstwa EPG zbuduje wiersze kanałów
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = sx(LEFT_X))
                    .clip(RoundedCornerShape(sx(6)))
                    .border(1.5.dp, Color(0x99EEEEEE), RoundedCornerShape(sx(6)))
                    .padding(horizontal = sx(12), vertical = sy(6))
            ) {
                Text(
                    text = channelNumber.toString(),
                    color = TEXT_PRIMARY,
                    fontSize = demoSp(22, sy),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(modifier = Modifier.padding(start = sx(MAIN_X), end = sx(200))) {
            Text(
                text = block.title,
                color = TEXT_PRIMARY,
                fontSize = demoSp(44, sy),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(sy(8)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val blockMin = (block.endVirtualMs - block.startVirtualMs) / 60_000
                val meta = listOf(block.genre, "$blockMin min", block.year, block.country, block.age)
                    .filter { it.isNotBlank() }
                Text(
                    text = meta.joinToString("  |  ") + "  |",
                    color = TEXT_SECONDARY,
                    fontSize = demoSp(20, sy)
                )
                Spacer(modifier = Modifier.width(sx(12)))
                listOf("S", "W", "N", "P").forEach { letter ->
                    Box(
                        modifier = Modifier
                            .padding(end = sx(8))
                            .clip(RoundedCornerShape(sx(4)))
                            .border(1.dp, Color(0x99EEEEEE), RoundedCornerShape(sx(4)))
                            .padding(horizontal = sx(7), vertical = sy(2))
                    ) {
                        Text(letter, color = TEXT_SECONDARY, fontSize = demoSp(14, sy))
                    }
                }
            }
        }
    }
}

/**
 * Pasek postępu o STAŁEJ szerokości segmentu głównego (wg designu):
 * bieżący materiał zawsze zajmuje BAR_MAIN_W px od X=330 — niezależnie od czasu
 * trwania. Materiał poprzedni/następny to ścieśnione segmenty od krawędzi ekranu.
 * Znacznik live (biała kropka) wędruje po osi czasu; kursor przewijania (aqua)
 * tylko w STRIP. Czasy nad paskiem: start i koniec segmentu głównego, czas
 * kursora (pogrubiony, z sekundami) i "live: HH:mm".
 */
@Composable
private fun DemoFixedBlockBar(
    block: DemoChannelSchedule.EpgBlock,
    prevBlock: DemoChannelSchedule.EpgBlock?,
    nextBlock: DemoChannelSchedule.EpgBlock?,
    positionMs: Long,
    cursorMs: Long?,
    liveEdgeVirtualMs: Long,
    antennaStartWallMs: Long,
    showTimes: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val labelsH = if (showTimes) 40 else 0
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(sy(labelsH + 20))
    ) {
        val fullWidth = maxWidth
        val mainX = sx(MAIN_X)
        val mainW = sx(BAR_MAIN_W)
        val gap = sx(BAR_GAP)
        val prevW = (mainX - gap).coerceAtLeast(0.dp)
        val nextX = mainX + mainW + gap
        val nextW = (fullWidth - nextX).coerceAtLeast(0.dp)
        val barY = sy(labelsH + 7)
        val barH = sy(6)

        fun fracIn(b: DemoChannelSchedule.EpgBlock, t: Long): Float =
            ((t - b.startVirtualMs).toFloat() /
                (b.endVirtualMs - b.startVirtualMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

        // x dla czasu t: segment główny ma stałą skalę, sąsiednie własną (ścieśnioną);
        // null = czas poza widocznym oknem (nie rysuj znacznika)
        fun xOf(t: Long): Dp? = when {
            t >= block.startVirtualMs && t <= block.endVirtualMs ->
                mainX + mainW * fracIn(block, t)
            t < block.startVirtualMs ->
                if (prevBlock != null) prevW * fracIn(prevBlock, t) else 0.dp
            else ->
                if (nextBlock != null) nextX + nextW * fracIn(nextBlock, t) else null
        }

        val refMs = cursorMs ?: positionMs
        val fillColor = if (cursorMs != null) AQUA else Color(0xFFEEEEEE)

        // Segment poprzedniego materiału (od lewej krawędzi ekranu)
        if (prevW > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = barY)
                    .width(prevW)
                    .height(barH)
                    .clip(RoundedCornerShape(barH / 2))
                    .background(if (cursorMs == null) Color(0xB3EEEEEE) else Color(0x40EEEEEE))
            )
        }

        // Segment główny: tor + wypełnienie do pozycji (BUTTONS) / kursora (STRIP)
        Box(
            modifier = Modifier
                .offset(x = mainX, y = barY)
                .width(mainW)
                .height(barH)
                .clip(RoundedCornerShape(barH / 2))
                .background(Color(0x40EEEEEE))
        ) {
            val fillFraction = fracIn(block, refMs)
            if (fillFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fillFraction)
                        .clip(RoundedCornerShape(barH / 2))
                        .background(fillColor)
                )
            }
        }

        // Segment następnego materiału (do prawej krawędzi ekranu)
        if (nextW > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = nextX, y = barY)
                    .width(nextW)
                    .height(barH)
                    .clip(RoundedCornerShape(barH / 2))
                    .background(Color(0x40EEEEEE))
            )
        }

        // Znacznik live (biała kropka) — przesuwa się z zegarem; widoczny też
        // poza segmentem głównym (timeshift: live ucieka do następnego materiału)
        val liveX = xOf(liveEdgeVirtualMs)
        if (liveX != null) {
            Box(
                modifier = Modifier
                    .offset(x = liveX - sy(5), y = barY + barH / 2 - sy(5))
                    .size(sy(10))
                    .background(Color(0xFFEEEEEE), CircleShape)
            )
        }

        // Kursor przewijania (STRIP) — zawsze w segmencie głównym, bo materiał
        // pod kursorem JEST segmentem głównym
        val cursorX = cursorMs?.let { xOf(it) }
        if (cursorX != null) {
            Box(
                modifier = Modifier
                    .offset(x = cursorX - sy(11), y = barY + barH / 2 - sy(11))
                    .size(sy(22))
                    .background(AQUA, CircleShape)
            )
        }

        if (showTimes) {
            // Czasy NAD paskiem: granice segmentu głównego, kursor (bold), live.
            // Etykiety graniczne ustępują miejsca kursorowi i live (kolizje)
            val liveLabelVisible = liveX != null &&
                (cursorX == null || abs((liveX - cursorX).value) > sx(120).value)
            val startLabelX = (mainX - sx(24)).coerceAtLeast(0.dp)
            val endLabelX = mainX + mainW - sx(24)
            fun clearOf(labelX: Dp): Boolean =
                (cursorX == null || abs((labelX - cursorX).value) > sx(110).value) &&
                    (!liveLabelVisible || liveX == null || abs((labelX - liveX).value) > sx(110).value)
            if (clearOf(startLabelX)) {
                Text(
                    text = formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false),
                    color = TEXT_SECONDARY,
                    fontSize = demoSp(18, sy),
                    modifier = Modifier.offset(x = startLabelX, y = 0.dp)
                )
            }
            if (clearOf(endLabelX)) {
                Text(
                    text = formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false),
                    color = TEXT_SECONDARY,
                    fontSize = demoSp(18, sy),
                    modifier = Modifier.offset(x = endLabelX, y = 0.dp)
                )
            }
            if (liveLabelVisible && liveX != null) {
                Text(
                    text = "live: " + formatWall(antennaStartWallMs + liveEdgeVirtualMs, withSeconds = false),
                    color = TEXT_SECONDARY,
                    fontSize = demoSp(18, sy),
                    modifier = Modifier.offset(x = (liveX - sx(50)).coerceAtLeast(0.dp), y = 0.dp)
                )
            }
            if (cursorMs != null && cursorX != null) {
                Text(
                    text = formatWall(antennaStartWallMs + cursorMs, withSeconds = true),
                    color = TEXT_PRIMARY,
                    fontSize = demoSp(20, sy),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = (cursorX - sx(48)).coerceAtLeast(0.dp), y = 0.dp)
                )
            }
        }
    }
}

/**
 * Rząd przycisków playera (wg designu: Zatrzymaj | Wróć do live | Zacznij od początku |
 * Nagraj | Napisy...). Na live edge zamiast przycisku "Wróć do live" jest
 * niefokusowalny status "● Oglądasz live" — przycisk pojawia się po KAŻDYM
 * odsunięciu od live: przewinięciu wstecz LUB pauzie (pauza ≠ live).
 */
@Composable
private fun PlayerButtonsRow(
    isPaused: Boolean,
    focusedIndex: Int,
    isAtLiveEdge: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerButton(if (isPaused) "▶  Wznów" else "⏸  Zatrzymaj", focusedIndex == 0, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        if (isAtLiveEdge) {
            // Status, nie przycisk — fokus go omija
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = sx(10))
            ) {
                Box(
                    modifier = Modifier
                        .size(sy(12))
                        .background(Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.width(sx(10)))
                Text(
                    text = "Oglądasz live",
                    color = TEXT_PRIMARY,
                    fontSize = demoSp(20, sy),
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            PlayerButton("LIVE  Wróć do live", focusedIndex == 1, sx, sy)
        }
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
            .clip(RoundedCornerShape(sx(8)))
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
