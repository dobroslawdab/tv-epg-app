package com.uxellence.tv.v3.demolive2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 * Układ zmierzony na boxie: zafokusowany rząd kanału stoi NAD pasem przewijania,
 * kolejne kanały pod nim. Pas przewijania jest w tym stanie niżej (929 px) i pełni
 * rolę separatora między kanałem oglądanym a listą.
 *
 * Kolumny rzędu: szyna kanału (MOJE / numer / logo) → okładka+opis bieżącego
 * programu (z ramką fokusa) → zajawka następnego programu (wygaszona).
 */

private const val ROW_FOCUS_TOP = 745
private const val ROW_NEXT_TOP = 953

// przesunięcia wewnątrz rzędu (względem jego górnej krawędzi)
private const val DY_MOJE = 33
private const val DY_BADGE = 67
private const val DY_LOGO = 65
private const val DY_COVER = 23
private const val DY_TIME = 25
private const val DY_TITLE = 63
private const val DY_META = 125

@Composable
fun PlayNowMiniEpg(
    rows: List<PnChannelRow>,
    rowIndex: Int,
    programIndex: Int,
    antennaStartWallMs: Long,
    positionMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    if (rows.isEmpty()) return
    val focusedRow = rows[rowIndex.coerceIn(rows.indices)]
    val shown = focusedRow.programs.getOrNull(programIndex)
        ?: focusedRow.programs.firstOrNull() ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        // ── zafokusowany kanał (nad pasem przewijania) ──
        PnEpgRow(
            row = focusedRow,
            topPx = ROW_FOCUS_TOP,
            programIndex = programIndex,
            focused = true,
            antennaStartWallMs = antennaStartWallMs,
            sx = sx, sy = sy
        )

        // ── pas przewijania: oś programu pod fokusem ──
        PlayNowSeekBar(
            trackTopPx = PN.EPG_TRACK_TOP,
            blockStartMs = shown.startMs,
            blockEndMs = shown.endMs,
            positionMs = positionMs,
            cursorMs = null,
            antennaStartWallMs = antennaStartWallMs,
            focused = false,
            sx = sx, sy = sy
        )

        // ── kolejne kanały pod paskiem (wygaszone, dolny ucięty krawędzią) ──
        val following = rows.indices
            .map { (rowIndex + 1 + it) % rows.size }
            .take((rows.size - 1).coerceAtMost(2))
        following.forEachIndexed { k, idx ->
            PnEpgRow(
                row = rows[idx],
                topPx = ROW_NEXT_TOP + k * PN.EPG_ROW_H,
                programIndex = rows[idx].liveIndex,
                focused = false,
                antennaStartWallMs = antennaStartWallMs,
                sx = sx, sy = sy
            )
        }
    }
}

@Composable
private fun PnEpgRow(
    row: PnChannelRow,
    topPx: Int,
    programIndex: Int,
    focused: Boolean,
    antennaStartWallMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val program = row.programs.getOrNull(programIndex) ?: return
    val next = row.programs.getOrNull(programIndex + 1)
    val textColor = if (focused) PN_TEXT else PN_TEXT_DIM

    // ── szyna kanału ──
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
            .border(sx(2), if (focused) PN_TEXT_DIM else PN_TEXT_DIM, RoundedCornerShape(sx(6)))
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
                .width(sx(120)).height(sy(40))
        )
    } else {
        Text(
            text = row.name,
            color = textColor,
            fontSize = pnSp(24, sy),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = sx(145), y = sy(topPx + DY_LOGO + 6))
                .width(sx(170))
        )
    }

    // ── etykieta dnia nad okładką (mint, tylko zafokusowany rząd) ──
    if (focused) {
        Text(
            text = "DZIŚ",
            color = PN_MINT,
            fontSize = pnSp(PN.EPG_RAIL_LABEL_SIZE, sy),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.0.sp,
            modifier = Modifier.offset(x = sx(PN.EPG_COVER_LEFT), y = sy(topPx + 1))
        )
    }

    // ── okładka bieżącego programu (ramka fokusa mint) ──
    Box(
        modifier = Modifier
            .offset(x = sx(PN.EPG_COVER_LEFT), y = sy(topPx + DY_COVER))
            .size(sx(PN.EPG_COVER_W), sy(PN.EPG_COVER_H))
            .background(Color(0x33000000))
            .then(if (focused) Modifier.border(sx(3), PN_MINT) else Modifier)
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

    // ── opis bieżącego programu ──
    val timeText = "${pnClock(antennaStartWallMs + program.startMs)} - " +
        pnClock(antennaStartWallMs + program.endMs)
    Text(
        text = timeText,
        color = textColor,
        fontSize = pnSp(PN.EPG_TIME_SIZE, sy),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.offset(x = sx(PN.EPG_TEXT_LEFT), y = sy(topPx + DY_TIME))
    )
    if (focused && programIndex == row.liveIndex) {
        Text(
            text = "TERAZ OGLĄDASZ",
            color = PN_TEXT_SOFT,
            fontSize = pnSp(PN.EPG_RAIL_LABEL_SIZE, sy),
            letterSpacing = 0.6.sp,
            modifier = Modifier.offset(x = sx(PN.EPG_TEXT_LEFT + 155), y = sy(topPx + DY_TIME + 4))
        )
        Box(
            modifier = Modifier.offset(
                x = sx(PN.EPG_TEXT_LEFT + 340), y = sy(topPx + DY_TIME - 2)
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
            .offset(x = sx(PN.EPG_TEXT_LEFT), y = sy(topPx + DY_TITLE))
            .width(sx(PN.EPG_NEXT_LEFT - PN.EPG_TEXT_LEFT - 40))
    )
    Text(
        text = pnMeta(program.meta),
        color = textColor,
        fontSize = pnSp(PN.EPG_META_SIZE, sy),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x = sx(PN.EPG_TEXT_LEFT), y = sy(topPx + DY_META))
            .width(sx(PN.EPG_NEXT_LEFT - PN.EPG_TEXT_LEFT - 40))
    )

    // ── zajawka następnego programu ──
    if (next != null) {
        Text(
            text = "${pnClock(antennaStartWallMs + next.startMs)} - " +
                pnClock(antennaStartWallMs + next.endMs),
            color = PN_TEXT_DIM,
            fontSize = pnSp(PN.EPG_TIME_SIZE, sy),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = sx(PN.EPG_NEXT_LEFT), y = sy(topPx + DY_TIME))
        )
        Text(
            text = next.title,
            color = PN_TEXT_DIM,
            fontSize = pnSp(PN.EPG_TITLE_SIZE, sy),
            maxLines = 1,
            modifier = Modifier.offset(x = sx(PN.EPG_NEXT_LEFT), y = sy(topPx + DY_TITLE))
        )
    }
}
