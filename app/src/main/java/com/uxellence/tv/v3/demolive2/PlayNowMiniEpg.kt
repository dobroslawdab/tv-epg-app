package com.uxellence.tv.v3.demolive2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * MINI-EPG nad wideo — stan po DÓŁ z pasa kontrolek (jak w launcherze Play).
 *
 * PRZEWIJANIE jak w 1. wersji (DemoMiniEpgBar): dwa [AnimatedContent] ze
 * slide+fade, a nie ciągłe przesuwanie siatki.
 *  - GÓRA/DÓŁ: cały blok wierszy wjeżdża z kierunku nawigacji (250 ms, h/3),
 *  - LEWO/PRAWO: karta programu zjeżdża w bok, nowa wjeżdża (250 ms, w/2).
 * Zafokusowany wiersz zostaje na [ROW_FOCUS_TOP], zafokusowany program
 * w kolumnie startowej — rusza się treść, nie ramka fokusa.
 *
 * Rzędy są przycinane do obszaru od [ROW_FOCUS_TOP] w dół — na boxie nad
 * zafokusowanym kanałem nie ma nic poza wideo.
 *
 * Czasy programów są w zegarze ściennym (patrz [PnProgram]) — mini-EPG zestawia
 * kanały o RÓŻNYCH osiach (każde nagranie ma własny recordedAtWallMs, kanały
 * mockupowe nie mają żadnej), więc wspólnym mianownikiem może być tylko zegar.
 */

private const val ROW_FOCUS_TOP = 745
private const val ROW_PITCH = 208          // 953 - 745, zmierzone na boxie
private const val COL_PITCH = 961          // 1305 - 344, zmierzone na boxie
private const val VISIBLE_ROWS = 3

// przesunięcia wewnątrz rzędu (względem jego górnej krawędzi)
private const val DY_MOJE = 33
private const val DY_BADGE = 67
private const val DY_LOGO = 65
private const val DY_COVER = 23
private const val DY_TIME = 25
private const val DY_TITLE = 63
private const val DY_META = 125

// przesunięcia wewnątrz kolumny programu (względem jej lewej krawędzi)
private const val DX_TEXT = 241            // 585 - 344
private const val TEXT_W = 680             // 1305 - 585 - 40

private const val SCROLL_MS = 250   // jak w 1. wersji (DemoMiniEpgBar)

@Composable
fun PlayNowMiniEpg(
    rows: List<PnChannelRow>,
    rowIndex: Int,
    programIndex: Int,
    /** Pozycja odtwarzania w zegarze ściennym — steruje wypełnieniem paska. */
    positionWallMs: Long,
    /** Live edge — poświata na pasku sięga DO NIEGO, nie do pozycji. */
    liveEdgeWallMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    if (rows.isEmpty()) return
    val safeRow = rowIndex.coerceIn(rows.indices)
    val focusedRow = rows[safeRow]
    val shown = focusedRow.programs.getOrNull(programIndex)
        ?: focusedRow.programs.firstOrNull() ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // ── kanały: cały blok wierszy wjeżdża z kierunku nawigacji ──
        AnimatedContent(
            targetState = safeRow,
            transitionSpec = {
                val dir = if (targetState >= initialState) 1 else -1
                (slideInVertically(tween(SCROLL_MS)) { h -> dir * h / 3 } +
                    fadeIn(tween(SCROLL_MS - 50))).togetherWith(
                    slideOutVertically(tween(SCROLL_MS)) { h -> -dir * h / 3 } +
                        fadeOut(tween(SCROLL_MS - 100))
                )
            },
            label = "miniEpgChannels",
            modifier = Modifier
                .offset(y = sy(ROW_FOCUS_TOP))
                .fillMaxWidth()
                .height(sy(1080 - ROW_FOCUS_TOP))
                .clipToBounds()
        ) { chIdx ->
            Box(modifier = Modifier.fillMaxSize()) {
                for (k in 0 until VISIBLE_ROWS) {
                    val row = rows.getOrNull(chIdx + k) ?: continue
                    PnEpgRow(
                        row = row,
                        topPx = k * ROW_PITCH,
                        programIndex = if (k == 0) programIndex else row.liveIndex,
                        animatePrograms = k == 0,
                        focused = k == 0,
                        sx = sx, sy = sy
                    )
                }
            }
        }

        // ── pas przewijania: separator między oglądanym kanałem a listą ──
        PlayNowSeekBar(
            trackTopPx = PN.EPG_TRACK_TOP,
            blockStartWallMs = shown.startWallMs,
            blockEndWallMs = shown.endWallMs,
            positionWallMs = positionWallMs,
            cursorWallMs = null,
            liveEdgeWallMs = liveEdgeWallMs,
            focused = false,
            // W mini-EPG pasek pokazuje, GDZIE jest odtwarzanie, ale bulletu
            // i bąbelka z czasem NIE MA — zmierzone na boxie (patrz PnBarStyle).
            style = PnBarStyle.POSITION_ONLY,
            sx = sx, sy = sy
        )
    }
}

@Composable
private fun PnEpgRow(
    row: PnChannelRow,
    topPx: Int,
    programIndex: Int,
    /** Tylko zafokusowany wiersz animuje zmianę programu. */
    animatePrograms: Boolean,
    focused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val textColor = if (focused) PN_TEXT else PN_TEXT_DIM

    // ── szyna kanału (nie jedzie poziomo) ──
    Text(
        text = "MOJE",
        color = if (focused) PN_TEXT_SOFT else PN_TEXT_DIM,
        fontSize = pnSp(PN.EPG_RAIL_LABEL_SIZE, sy),
        modifier = Modifier.offset(x = sx(PN.EPG_RAIL_LEFT), y = sy(topPx + DY_MOJE))
    )
    Box(
        modifier = Modifier
            .offset(x = sx(PN.EPG_RAIL_LEFT + 4), y = sy(topPx + DY_BADGE))
            .widthIn(min = sx(PN.NUM_MIN_W))
            .height(sy(PN.NUM_H))
            .border(sx(2), PN_TEXT_DIM, RoundedCornerShape(sx(6)))
            .padding(horizontal = sx(9)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (row.number < 10) "0${row.number}" else "${row.number}",
            color = textColor,
            fontSize = pnSp(PN.NUM_SIZE, sy)
        )
    }
    if (row.logoUrl != null) {
        AsyncImage(
            model = row.logoUrl,
            contentDescription = row.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = sx(145), y = sy(topPx + DY_LOGO))
                .width(sx(150)).height(sy(40))
        )
    } else {
        // Bez logo (nagrania, kanały mockupowe) — nazwa tekstem, do dwóch linii,
        // bo "Polsat News Retro" nie mieści się w jednej.
        Text(
            text = row.name,
            color = textColor,
            fontSize = pnSp(24, sy),
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = pnSp(28, sy),
            modifier = Modifier
                .offset(x = sx(145), y = sy(topPx + DY_LOGO - 4))
                .width(sx(185))
        )
    }

    // ── kolumny programów: jadą poziomo ──
    // Kontener kolumn ma WYSOKOŚĆ RZĘDU i stoi na y=topPx — przycinanie jest
    // wtedy wyłącznie poziome (żeby programy nie wjeżdżały na szynę kanału).
    // Modifier.offset NIE powiększa zmierzonego rozmiaru, więc bez jawnej
    // wysokości ten Box mierzył się okładką (120) albo warstwą (335)
    // i clipToBounds ucinał metadane na DY_META=125.
    val columns: @Composable (Int) -> Unit = { idx ->
        Box(modifier = Modifier.fillMaxSize()) {
            // bieżący program + zajawka następnego
            for (k in 0..1) {
                val program = row.programs.getOrNull(idx + k) ?: continue
                PnEpgProgramColumn(
                    program = program,
                    leftPx = k * COL_PITCH,
                    focused = focused && k == 0,
                    isLive = (idx + k) == row.liveIndex,
                    dim = !(focused && k == 0),
                    sx = sx, sy = sy
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .offset(x = sx(PN.EPG_COVER_LEFT), y = sy(topPx))
            .fillMaxWidth()
            .height(sy(ROW_PITCH))
            .clipToBounds()
    ) {
        if (animatePrograms) {
            AnimatedContent(
                targetState = programIndex,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(tween(SCROLL_MS)) { w -> dir * w / 2 } +
                        fadeIn(tween(SCROLL_MS - 50))).togetherWith(
                        slideOutHorizontally(tween(SCROLL_MS)) { w -> -dir * w / 2 } +
                            fadeOut(tween(SCROLL_MS - 100))
                    )
                },
                label = "miniEpgPrograms"
            ) { idx -> columns(idx) }
        } else {
            columns(programIndex)
        }
    }
}

@Composable
private fun PnEpgProgramColumn(
    program: PnProgram,
    leftPx: Int,
    focused: Boolean,
    isLive: Boolean,
    dim: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val textColor = if (dim) PN_TEXT_DIM else PN_TEXT

    if (focused) {
        Text(
            text = "DZIŚ",
            color = PN_MINT,
            fontSize = pnSp(PN.EPG_RAIL_LABEL_SIZE, sy),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
            modifier = Modifier.offset(x = sx(leftPx), y = sy(1))
        )
    }

    Box(
        modifier = Modifier
            .offset(x = sx(leftPx), y = sy(DY_COVER))
            .size(sx(PN.EPG_COVER_W), sy(PN.EPG_COVER_H))
            .background(Color(0x33000000))
            // ramka fokusa sx(6) — tak jak zafokusowana miniaturka w 1. wersji (DemoMiniEpgBar)
            .then(if (focused) Modifier.border(sx(6), PN_MINT) else Modifier)
    ) {
        if (program.coverUrl != null) {
            AsyncImage(
                model = program.coverUrl,
                contentDescription = program.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Text(
        text = "${pnClock(program.startWallMs)} - ${pnClock(program.endWallMs)}",
        color = textColor,
        fontSize = pnSp(PN.EPG_TIME_SIZE, sy),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.offset(x = sx(leftPx + DX_TEXT), y = sy(DY_TIME))
    )
    if (focused && isLive) {
        Text(
            text = "TERAZ OGLĄDASZ",
            color = PN_TEXT_SOFT,
            fontSize = pnSp(PN.EPG_RAIL_LABEL_SIZE, sy),
            letterSpacing = 0.6.sp,
            modifier = Modifier.offset(
                x = sx(leftPx + DX_TEXT + 155), y = sy(DY_TIME + 4)
            )
        )
        Box(
            modifier = Modifier.offset(
                x = sx(leftPx + DX_TEXT + 358), y = sy(DY_TIME - 2)
            )
        ) {
            PnRestartBadge(26, PN_TEXT_SOFT, sx, sy)
        }
    }
    Text(
        text = program.title,
        color = textColor,
        fontSize = pnSp(PN.EPG_TITLE_SIZE, sy),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x = sx(leftPx + DX_TEXT), y = sy(DY_TITLE))
            .width(sx(TEXT_W))
    )
    if (focused) {
        Text(
            text = pnMeta(program.meta),
            color = textColor,
            fontSize = pnSp(PN.EPG_META_SIZE, sy),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = sx(leftPx + DX_TEXT), y = sy(DY_META))
                .width(sx(TEXT_W))
        )
    }
}
