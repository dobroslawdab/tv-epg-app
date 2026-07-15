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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.R
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
private const val FOLD_OFFSET = 200

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
    block: BarkerSchedule.EpgBlock,
    prevBlock: BarkerSchedule.EpgBlock?,
    nextBlock: BarkerSchedule.EpgBlock?,
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
    figmaButtons: Boolean = false,  // klawisz "3": ikonowy pasek wg designu Figma
    frames: List<Pair<Long, Bitmap?>>,
    blockTitleFor: ((Long) -> String?)? = null,  // tytuł materiału dla pozycji wirtualnej (taśma)
    // Wersja 2 playera (klawisz "0"): zamiast bloku opisu ikonka ⓘ "Zobacz opis"
    infoInsteadOfDescription: Boolean = false,
    // Player VOD (zwiastun): kontrolki bez LIVE i REC — [pauza, od początku, napisy]
    vodButtons: Boolean = false,
    // Bieżący program ma już ZLECONE nagranie → REC pokazuje "Anuluj nagranie"
    recScheduled: Boolean = false,
    blockMetaFor: ((Long) -> String?)? = null,
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
            // Gradient jedzie RAZEM z foldem: przy zejściu na opis (SNIPPET)
            // kolumna podnosi się o FOLD_OFFSET, więc pas gradientu rośnie
            // i podnosi się o tyle samo — nagłówek/tytuł nad paskiem postępu
            // zostaje na ciemnym tle i jest czytelny
            val gradientLift by animateDpAsState(
                targetValue = if (zone == PlayerZone.SNIPPET) sy(FOLD_OFFSET) else 0.dp,
                animationSpec = tween(350),
                label = "demo_player_gradient_lift"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(510) + gradientLift)
                    .offset(y = sy(1080 - 510) - gradientLift)
                    .background(
                        // Ten sam kolor co pod mini-EPG (#281443); pełne krycie
                        // od ~połowy pasa — kontrolki, opis i tytuł na
                        // jednolitym tle, bez przebić obrazu
                        Brush.verticalGradient(
                            0f to Color(0x00281443),
                            0.55f to Color(0xFF281443)
                        )
                    )
            )


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
                // Bez nagłówka u góry (tytuł + logo kanału): w trybie taśmy tytuły
                // materiałów są przypięte do klatek NA taśmie (Figma 5530-5395),
                // więc górny nagłówek by je dublował
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
                        blockTitleFor = blockTitleFor,
                        blockMetaFor = blockMetaFor,
                        sx = sx, sy = sy
                    )
                    Spacer(modifier = Modifier.height(sy(16)))
                    if (vodButtons) {
                        // Player VOD: pasek TYLKO odtwarzanego materiału, czasy
                        // elapsed (lewo) / pozostało (prawo) liczone od KURSORA
                        DemoVodProgressBar(
                            positionMs = currentVirtualMs,
                            durationMs = liveEdgeVirtualMs,
                            cursorMs = scrubCursorMs,
                            sx = sx, sy = sy
                        )
                    } else {
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
                    }
                    // Wariant ikonowy: w trybie przewijania (STRIP) chowamy ikonki playera,
                    // a pasek postępu obniżamy w miejsce kontrolek (sam pasek na dole).
                    // Wariant tekstowy: pasek + rząd przycisków jak dotąd.
                    if (!figmaButtons) {
                        Spacer(modifier = Modifier.height(sy(20)))
                        Box(modifier = Modifier.padding(start = sx(MAIN_X))) {
                            PlayerButtonsRow(isPaused, buttonsFocusIndex, isAtLiveEdge, vodButtons, recScheduled, infoInsteadOfDescription, sx, sy)
                        }
                    }
                }
            } else {
                // BUTTONS / SNIPPET — dolna kolumna z animowanym foldem opisu.
                // Wersja 2 (ⓘ w pasku, bez bloku opisu): bez folda — fold spychał
                // przyciski poza dolną krawędź ekranu
                val foldOffset by animateDpAsState(
                    targetValue = if (zone == PlayerZone.SNIPPET || infoInsteadOfDescription) {
                        0.dp
                    } else sy(FOLD_OFFSET),
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
                    if (vodButtons) {
                        // Player VOD: sam materiał, elapsed/pozostało
                        DemoVodProgressBar(
                            positionMs = currentVirtualMs,
                            durationMs = liveEdgeVirtualMs,
                            cursorMs = null,
                            sx = sx, sy = sy
                        )
                    } else {
                        DemoFixedBlockBar(
                            block = block,
                            prevBlock = prevBlock,
                            nextBlock = nextBlock,
                            positionMs = currentVirtualMs,
                            cursorMs = null,
                            liveEdgeVirtualMs = liveEdgeVirtualMs,
                            antennaStartWallMs = antennaStartWallMs,
                            showTimes = true,   // poziom kontrolek: ta sama linia czasu (kropki + czasy pod linią)
                            sx = sx, sy = sy
                        )
                    }
                    Spacer(modifier = Modifier.height(sy(24)))
                    Box(modifier = Modifier.padding(start = sx(MAIN_X))) {
                        if (figmaButtons) PlayerButtonsRowFigma(isPaused, buttonsFocusIndex, isAtLiveEdge, vodButtons, recScheduled, infoInsteadOfDescription, sx, sy)
                        else PlayerButtonsRow(isPaused, buttonsFocusIndex, isAtLiveEdge, vodButtons, recScheduled, infoInsteadOfDescription, sx, sy)
                    }
                    // Odstęp 28 - 24 (pół wiersza opisu, line-height 48) — opis
                    // podniesiony o pół linii tekstu (wytyczna 2026-07-14)
                    Spacer(modifier = Modifier.height(sy(4)))
                    if (!infoInsteadOfDescription) {
                    // Wersja 2: bez bloku opisu — "Zobacz opis" jest IKONĄ paska
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
                            // Figma: 32px / line-height 48px (1920×1080)
                            fontSize = demoSp(32, sy),
                            lineHeight = demoSp(48, sy),
                            maxLines = 3,
                            overflow = if (zone == PlayerZone.SNIPPET) {
                                TextOverflow.Ellipsis
                            } else {
                                TextOverflow.Clip   // reszta chowa się pod foldem
                            }
                        )
                        if (zone == PlayerZone.SNIPPET) {
                            // Fokus na opisie: wskaźnik "więcej ›" w prawym dolnym
                            // rogu bloczka, w kolorze fokusa; tło zasłania tekst
                            // ostatniej linii, żeby napis był czytelny
                            Text(
                                text = "więcej ›",
                                color = AQUA,
                                fontSize = demoSp(30, sy),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xFF281443))
                                    .padding(start = sx(16))
                            )
                        }
                    }
                    }   // koniec else (wersja 1: blok opisu)
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
    block: BarkerSchedule.EpgBlock,
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
        } else if (channelNumber > 0) {
            // Fallback zanim warstwa EPG zbuduje wiersze kanałów;
            // channelNumber == 0 (player VOD) = bez badge'a w ogóle
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
    block: BarkerSchedule.EpgBlock,
    prevBlock: BarkerSchedule.EpgBlock?,
    nextBlock: BarkerSchedule.EpgBlock?,
    positionMs: Long,
    cursorMs: Long?,
    liveEdgeVirtualMs: Long,
    antennaStartWallMs: Long,
    showTimes: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Czasy renderowane POD linią (wg Figmy 5513:2013) — rezerwujemy miejsce pod paskiem.
    val labelsH = if (showTimes) 44 else 0
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
        // Pasek u góry, czasy POD nim. Linia 8 px, zaokrąglone końce (wg Figmy).
        val barY = sy(8)
        val barH = sy(8)

        fun fracIn(b: BarkerSchedule.EpgBlock, t: Long): Float =
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
        // Pasek postępu zawsze biały (kolor fokusa zarezerwowany dla ramki miniaturki)
        val fillColor = Color(0xFFEEEEEE)

        // LIVE indicator glow (wg Figmy 5530:5354): aqua podcień pod paskiem od lewej
        // do pozycji LIVE — pionowy gradient aqua(0.6) przy pasku → transparent w dół.
        // Wskazuje obszar dostępny "na żywo". Rysowany jako pierwszy (POD paskiem).
        val liveGlowX = xOf(liveEdgeVirtualMs)
        if (liveGlowX != null && liveGlowX > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = barY)
                    .width(liveGlowX)
                    .height(sy(48))   // wg Figmy: LIVE indicator glow = 48 px
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AQUA.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
        }

        // Segment poprzedniego materiału (od lewej krawędzi ekranu) — wg Figmy "progress
        // filled": biały, prominentny, NAD aqua glow (LIVE indicator glow rysowany wyżej,
        // więc ten segment jest na nim widoczny).
        if (prevW > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = 0.dp, y = barY)
                    .width(prevW)
                    .height(barH)
                    .clip(RoundedCornerShape(barH / 2))
                    .background(Color(0xFFEEEEEE))
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

        // Kropki (12 px) w PRZERWACH między segmentami (granice programu). Końce samych
        // linii są zaokrąglone — BEZ kropek na końcach (wg Figmy 5530:5416).
        val dotCenterY = barY + barH / 2
        val startGapX = mainX - gap / 2           // granica prev|main = start bloku
        val endGapX = mainX + mainW + gap / 2     // granica main|next = koniec bloku
        for (dx in listOf(startGapX, endGapX)) {
            if (dx > 0.dp && dx < fullWidth) {
                Box(
                    modifier = Modifier
                        .offset(x = dx - sy(6), y = dotCenterY - sy(6))
                        .size(sy(12))
                        .background(Color(0xFFEEEEEE), CircleShape)
                )
            }
        }

        // (biała pionowa kreska pozycji odtwarzania USUNIĘTA — pozycję live
        // wskazuje LIVE glow, a pozycję przewijania kropka kursora)

        // Kursor przewijania (STRIP): biała kropka na linii.
        val cursorX = cursorMs?.let { xOf(it) }
        if (cursorX != null) {
            Box(
                modifier = Modifier
                    .offset(x = cursorX - sy(8), y = dotCenterY - sy(8))
                    .size(sy(16))
                    .background(Color(0xFFEEEEEE), CircleShape)
            )
        }

        if (showTimes) {
            // Czasy POD linią (wg Figmy 5513:2013): granice bloku zwykłym tekstem,
            // kursor w fioletowym pillu (#48227c). Wyśrodkowane pod znacznikiem.
            val labelY = barY + barH + sy(10)
            fun centeredAt(xc: Dp): Modifier =
                Modifier.offset(x = (xc - sx(80)).coerceAtLeast(0.dp), y = labelY).width(sx(160))
            fun farFromCursor(xc: Dp): Boolean =
                cursorX == null || abs((xc - cursorX).value) > sx(110).value
            if (farFromCursor(startGapX)) {
                Box(modifier = centeredAt(startGapX), contentAlignment = Alignment.Center) {
                    Text(
                        text = formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false),
                        color = TEXT_SECONDARY,
                        fontSize = demoSp(20, sy)
                    )
                }
            }
            if (farFromCursor(endGapX)) {
                Box(modifier = centeredAt(endGapX), contentAlignment = Alignment.Center) {
                    Text(
                        text = formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false),
                        color = TEXT_SECONDARY,
                        fontSize = demoSp(20, sy)
                    )
                }
            }
            if (cursorMs != null && cursorX != null) {
                Box(modifier = centeredAt(cursorX), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(sx(4)))
                            .background(BG_PURPLE)
                            .padding(horizontal = sx(8), vertical = sy(4))
                    ) {
                        Text(
                            text = formatWall(antennaStartWallMs + cursorMs, withSeconds = true),
                            color = TEXT_PRIMARY,
                            fontSize = demoSp(20, sy),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
    vodButtons: Boolean,
    recScheduled: Boolean,
    withInfoButton: Boolean = false,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PlayerButton(if (isPaused) "▶  Wznów" else "⏸  Zatrzymaj", focusedIndex == 0, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        if (vodButtons) {
            // Player VOD (zwiastun): bez LIVE i bez Nagraj
            PlayerButton("↺  Zacznij od początku", focusedIndex == 1, sx, sy)
            Spacer(modifier = Modifier.width(sx(16)))
            PlayerButton("⚙  Napisy, dźwięk, jakość", focusedIndex == 2, sx, sy)
            return
        }
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
        PlayerButton(if (recScheduled) "REC  Anuluj nagranie" else "REC  Nagraj",
            focusedIndex == 3, sx, sy)
        Spacer(modifier = Modifier.width(sx(16)))
        if (withInfoButton) {
            PlayerButton("ⓘ  Zobacz opis", focusedIndex == 4, sx, sy)
            Spacer(modifier = Modifier.width(sx(16)))
            PlayerButton("⚙  Napisy, dźwięk, jakość", focusedIndex == 5, sx, sy)
        } else {
            PlayerButton("⚙  Napisy, dźwięk, jakość", focusedIndex == 4, sx, sy)
        }
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

private val FIGMA_RED = Color(0xFFDD1538)  // Colours/Secondary/red_base (kropka REC, NA ŻYWO akcent)

/**
 * Linia info nad tytułem w detalu demo (wg Figmy "Detail" 5507:5100, info_line):
 * zakres czasu + opcjonalnie [NA ŻYWO] + kółko "od początku" + kropka REC.
 * Renderowana jako wideoHeaderSlot w MovieDetailScreen (zamiast logo w kolumnie —
 * logo kanału demo rysuje osobno, w rogu ekranu).
 */
@Composable
internal fun DemoDetailInfoLine(
    timeRange: String,
    isLive: Boolean,
    canStartOver: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16))
    ) {
        Text(
            text = timeRange,
            color = TEXT_PRIMARY,
            fontSize = demoSp(24, sy),
            fontWeight = FontWeight.Medium
        )
        if (isLive || canStartOver) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(12))
            ) {
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(sx(4)))
                            .background(TEXT_PRIMARY)
                            .padding(horizontal = sx(8), vertical = sy(2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NA ŻYWO",
                            color = BG_PURPLE,
                            fontSize = demoSp(16, sy),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (canStartOver) {
                    Box(
                        modifier = Modifier
                            .size(sy(32))
                            .clip(CircleShape)
                            .background(TEXT_PRIMARY),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.demo_ic_startover),
                            contentDescription = "Od początku",
                            tint = BG_PURPLE,
                            modifier = Modifier.size(sx(18))
                        )
                    }
                }
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(sy(20))
                            .clip(CircleShape)
                            .background(FIGMA_RED)
                    )
                }
            }
        }
    }
}

/**
 * Zastępczy "logo" kanału w rogu detalu, gdy kanał nie ma logoUrl (np. DEMO TV).
 * Brandowy kafelek 208x208 (wypełnia rodzica): numer kanału + nazwa.
 */
@Composable
internal fun DemoChannelLogoBadge(
    channelNumber: Int,
    channelName: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG_PURPLE),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channelNumber.toString(),
                color = Color(0xFF5FEDD4),
                fontSize = demoSp(44, sy),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(sy(6)))
            Text(
                text = channelName,
                color = TEXT_PRIMARY,
                fontSize = demoSp(22, sy),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// Kolory wariantu Figma (node 3454:4824 "Player_control_item")
private val FIGMA_CONTAINER_FOCUSED = Color(0xFF5FEDD4)  // Colours/Container/container focused
private val FIGMA_ICON_FOCUSED = Color(0xFF48227C)       // Colours/Icon/icon focused
private val FIGMA_ICON_DEFAULT = Color(0x99EEEEEE)       // Colours/Icon/icon primary (przygaszony)
private val FIGMA_TEXT_STATUS = Color(0xFF5FEDD4)        // Colours/Text/text status

/**
 * Ikonowy pasek przycisków wg designu Figma (Player_control_item, 152x152).
 * Te same 5 akcji i indeksy (0..4) co [PlayerButtonsRow] — zmienia się tylko
 * wygląd: ikona 48px w kafelku 64x64; zfokusowany = aqua kafelek + ikona
 * w kolorze tła + label aqua pod spodem. Przełączane klawiszem "3".
 */
@Composable
internal fun PlayerButtonsRowFigma(
    isPaused: Boolean,
    focusedIndex: Int,
    isAtLiveEdge: Boolean,
    vodButtons: Boolean,
    recScheduled: Boolean,
    // Wersja 2 playera: dodatkowa ikona ⓘ "Zobacz opis" (idx 4, napisy → 5)
    withInfoButton: Boolean = false,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(verticalAlignment = Alignment.Top) {
        FigmaControlItem(R.drawable.demo_ic_pause, if (isPaused) "Wznów" else "Zatrzymaj", focusedIndex == 0, sx, sy)
        if (vodButtons) {
            // Player VOD (zwiastun): bez LIVE i bez Nagraj
            FigmaControlItem(R.drawable.demo_ic_startover, "Zacznij od początku", focusedIndex == 1, sx, sy)
            FigmaControlItem(R.drawable.demo_ic_settings, "Napisy, dźwięk, jakość", focusedIndex == 2, sx, sy)
        } else {
            // Na live edge: sama ikonka LIVE bez podpisu "Oglądasz live", niefokusowalna
            // (w wariancie ikonowym ikona wystarcza za status)
            FigmaControlItem(R.drawable.demo_ic_live, "Wróć do live", !isAtLiveEdge && focusedIndex == 1, sx, sy)
            FigmaControlItem(R.drawable.demo_ic_startover, "Zacznij od początku", focusedIndex == 2, sx, sy)
            FigmaControlItem(R.drawable.demo_ic_rec,
                if (recScheduled) "Anuluj nagranie" else "Nagraj", focusedIndex == 3, sx, sy)
            if (withInfoButton) {
                FigmaControlItem(R.drawable.demo_ic_info, "Zobacz opis", focusedIndex == 4, sx, sy)
                FigmaControlItem(R.drawable.demo_ic_settings, "Napisy, dźwięk, jakość", focusedIndex == 5, sx, sy)
            } else {
                FigmaControlItem(R.drawable.demo_ic_settings, "Napisy, dźwięk, jakość", focusedIndex == 4, sx, sy)
            }
        }
    }
}

@Composable
private fun FigmaControlItem(
    iconRes: Int,
    label: String,
    isFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Item 152x152: kontener 64x64 u góry (inset 23.68%), label u dołu (inset 76.32%)
    Box(modifier = Modifier.size(sx(152), sy(152))) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = sy(36))
                .size(sx(64), sy(64))
                .clip(RoundedCornerShape(sx(8)))
                .background(if (isFocused) FIGMA_CONTAINER_FOCUSED else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = if (isFocused) FIGMA_ICON_FOCUSED else FIGMA_ICON_DEFAULT,
                modifier = Modifier.size(sx(48), sy(48))
            )
        }
        // Label tylko gdy zfokusowany — aqua, Manrope Bold 24,
        // może wychodzić poza szerokość itemu (whitespace-nowrap w designie)
        if (isFocused) {
            Text(
                text = label,
                color = FIGMA_TEXT_STATUS,
                fontSize = demoSp(24, sy),
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.48).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = sy(116))
                    .wrapContentWidth(unbounded = true)
            )
        }
    }
}


/**
 * Pasek postępu playera VOD (zwiastun): TYLKO segment odtwarzanego materiału
 * (bez poprzedniego/następnego), pod linią czasy: po lewej ile MINĘŁO,
 * po prawej ile ZOSTAŁO. Podczas przewijania (cursorMs != null) czasy
 * liczone od kursora, a na linii stoi biała kropka kursora.
 */
@Composable
internal fun DemoVodProgressBar(
    positionMs: Long,
    durationMs: Long,
    cursorMs: Long?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val dur = durationMs.coerceAtLeast(1L)
    val refMs = (cursorMs ?: positionMs).coerceIn(0L, dur)
    fun fmt(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val m = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(m, sec)
    }
    val barW = 1230
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.width(sx(barW)).height(sy(16))) {
            // Tło segmentu materiału
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sx(barW))
                    .height(sy(8))
                    .clip(RoundedCornerShape(sy(4)))
                    .background(Color(0x66EEEEEE))
            )
            // Wypełnienie do pozycji odtwarzania
            val fillW = ((positionMs.coerceIn(0L, dur).toFloat() / dur) * barW).toInt()
            if (fillW > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(sx(fillW))
                        .height(sy(8))
                        .clip(RoundedCornerShape(sy(4)))
                        .background(Color(0xFFEEEEEE))
                )
            }
            // Kropka kursora przewijania
            if (cursorMs != null) {
                val cx = ((cursorMs.coerceIn(0L, dur).toFloat() / dur) * barW).toInt()
                Box(
                    modifier = Modifier
                        .offset(x = sx(cx) - sy(8), y = sy(0))
                        .size(sy(16))
                        .background(Color(0xFFEEEEEE), CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(sy(6)))
        Row(modifier = Modifier.width(sx(barW))) {
            Text(
                text = fmt(refMs),
                color = if (cursorMs != null) AQUA else TEXT_PRIMARY,
                fontSize = demoSp(20, sy),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "-" + fmt(dur - refMs),
                color = Color(0x99EEEEEE),
                fontSize = demoSp(20, sy),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
