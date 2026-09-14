package com.uxellence.tv.v3.demolive2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/**
 * PAS PRZEWIJANIA — 1:1 z launchera Play.
 *
 * Model osi (zmierzony na boxie, nie zgadywany):
 *  - bieżący blok ramówki zajmuje x = 325..1530,
 *  - kropki granic bloku leżą POZA tym zakresem, w przerwach: 290 i 1562,
 *    a pod nimi godziny startu/końca bloku,
 *  - ogon bloku poprzedniego to segment 0..258, głowa następnego 1598..1920.
 *
 * Weryfikacja mapowania: 10:58:42 w bloku 10:00-11:00 → 325 + 0.978*1205 = 1503
 * (playhead na zrzucie: 1501); 11:00:08 → 328 (zrzut: 326).
 *
 * Wypełnienie: JASNE (#DFDFDF) tylko do playheada, dalej wygaszone (#EEEEEE 40%) —
 * dotyczy też segmentów sąsiednich (ogon poprzedniego bloku jest jasny).
 * Pod jasną częścią poświata #5AECD3 o alfie 0.60 gasnąca do 0 na 68 px.
 */

/**
 * Jak pasek ma się zachować — ZMIERZONE na boxie, to nie jest nasza inwencja:
 *
 *  PLAYING       fokus na kontrolkach lub na pasku → wypełnienie do pozycji,
 *                poświata, bullet i bąbelek z czasem
 *  POSITION_ONLY mini-EPG → wypełnienie i poświata zostają (pokazują, gdzie
 *                faktycznie jest odtwarzanie), ale BULLETA I BĄBELKA NIE MA
 *  DOTS_ONLY     karta pokazuje INNY program niż grany (poziom miniaturki /
 *                detal) → sam wygaszony tor i kropki granic, zero wypełnienia
 *
 * Weryfikacja: zrzut mini-EPG ma na wysokości paska tylko dwie 8-px kropki
 * (x≈300 i 1262) i jasny ogon 0..264 — żadnego szerokiego koła playheada
 * ani prostokąta bąbelka. Zrzut detalu programu z przyszłości ma WYŁĄCZNIE
 * dwie 12-px kropki (284-296, 1556-1568) i nic poza tym.
 */
enum class PnBarStyle { PLAYING, POSITION_ONLY, DOTS_ONLY }

/** Pozycja X playheada dla postępu w bloku (0..1), w px designu. */
private fun headXFor(progress: Float): Float =
    PN.SEG_CUR_START + progress.coerceIn(0f, 1f) * (PN.SEG_CUR_END - PN.SEG_CUR_START)

@Composable
fun PlayNowSeekBar(
    /** Górna krawędź paska w px designu — CONTROLS: 832, mini-EPG: 929. */
    trackTopPx: Int,
    blockStartMs: Long,
    blockEndMs: Long,
    /** Pozycja odtwarzania (oś wirtualna). */
    positionMs: Long,
    /** Kursor przewijania — gdy != null, playhead i bąbelek idą za kursorem. */
    cursorMs: Long?,
    antennaStartWallMs: Long,
    /** Fokus na pasku (strefa SCRUB) — playhead na mint. */
    focused: Boolean,
    /** Wariant paska — patrz [PnBarStyle]. */
    style: PnBarStyle = PnBarStyle.PLAYING,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val span = (blockEndMs - blockStartMs).coerceAtLeast(1L)
    val headMs = cursorMs ?: positionMs
    val progress = ((headMs - blockStartMs).toFloat() / span).coerceIn(0f, 1f)
    val headX = headXFor(progress)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ux = sx(1).toPx()          // 1 px designu w px ekranu (poziomo)
            val uy = sy(1).toPx()
            fun dx(v: Float) = v * ux
            fun dy(v: Float) = v * uy

            val top = dy(trackTopPx.toFloat())
            val h = dy(PN.TRACK_H.toFloat())
            val r = h / 2f
            val head = dx(headX)

            val fills = style != PnBarStyle.DOTS_ONLY

            // ── poświata pod obejrzaną częścią (mint 0.60 → 0) ──
            if (fills && head > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to PN_MINT.copy(alpha = PN.GLOW_ALPHA),
                        1f to PN_MINT.copy(alpha = 0f),
                        startY = top + h,
                        endY = top + h + dy(PN.GLOW_H.toFloat())
                    ),
                    topLeft = Offset(0f, top + h),
                    size = Size(head, dy(PN.GLOW_H.toFloat()))
                )
            }

            // ── segmenty toru: jasne do playheada, wygaszone dalej ──
            fun segment(fromPx: Float, toPx: Float) {
                if (toPx <= fromPx) return
                val a = dx(fromPx)
                val b = dx(toPx)
                val split = if (fills) head.coerceIn(a, b) else a
                if (split > a) drawRoundRectPart(a, split, top, h, r, PN_TRACK)
                if (b > split) drawRoundRectPart(split, b, top, h, r, PN_TRACK_DIM)
            }
            segment(0f, PN.SEG_PREV_END.toFloat())
            segment(PN.SEG_CUR_START.toFloat(), PN.SEG_CUR_END.toFloat())
            segment(PN.SEG_NEXT_START.toFloat(), 1920f)

            // ── kropki granic bloku ──
            val dotR = dx(PN.DOT_R.toFloat())
            drawCircle(PN_TEXT, dotR, Offset(dx(PN.DOT_PREV_CX.toFloat()), top + h / 2f))
            drawCircle(PN_TEXT, dotR, Offset(dx(PN.DOT_NEXT_CX.toFloat()), top + h / 2f))

            // ── playhead: TYLKO gdy pasek pokazuje granie (patrz PnBarStyle) ──
            if (style == PnBarStyle.PLAYING) {
                drawCircle(
                    color = if (focused) PN_MINT else PN_TEXT,
                    radius = dx(PN.HEAD_R.toFloat()),
                    center = Offset(head, top + h / 2f)
                )
            }
        }

        // ── godziny granic bloku (wyśrodkowane pod kropkami) ──
        PnCenteredLabel(
            text = pnClock(antennaStartWallMs + blockStartMs),
            centerXPx = PN.DOT_PREV_CX, topPx = trackTopPx + (PN.LABEL_TOP - PN.TRACK_TOP),
            sizePx = PN.LABEL_SIZE, color = PN_TEXT, sx = sx, sy = sy
        )
        PnCenteredLabel(
            text = pnClock(antennaStartWallMs + blockEndMs),
            centerXPx = PN.DOT_NEXT_CX, topPx = trackTopPx + (PN.LABEL_TOP - PN.TRACK_TOP),
            sizePx = PN.LABEL_SIZE, color = PN_TEXT, sx = sx, sy = sy
        )

        // ── bąbelek z czasem playheada (prostokąt w kolorze podlania na poświacie) ──
        val bubbleTop = trackTopPx + (PN.BUBBLE_TOP - PN.TRACK_TOP)
        if (style == PnBarStyle.PLAYING) Box(
            modifier = Modifier
                .offset(x = sx((headX - PN.BUBBLE_W / 2f).toInt()), y = sy(bubbleTop))
                .width(sx(PN.BUBBLE_W)).height(sy(PN.BUBBLE_H))
                .background(PN_SCRIM, RoundedCornerShape(sx(3))),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = pnClock(antennaStartWallMs + headMs, withSeconds = true),
                color = PN_TEXT,
                fontSize = pnSp(PN.BUBBLE_SIZE, sy),
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

/** Fragment toru z zaokrąglonymi końcami (zaokrąglenie tylko na skrajach segmentu). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectPart(
    left: Float, right: Float, top: Float, h: Float, r: Float, color: Color
) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left, top = top, right = right, bottom = top + h,
                cornerRadius = CornerRadius(r, r)
            )
        )
    }
    drawPath(path, color)
}

/** Tekst wyśrodkowany na zadanym X designu. */
@Composable
private fun PnCenteredLabel(
    text: String,
    centerXPx: Int,
    topPx: Int,
    sizePx: Int,
    color: Color,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    // Szerokość "HH:mm" przy 26 px to ~64 px designu — wyśrodkowanie przez stałą
    // szerokość pudełka jest tu stabilniejsze niż mierzenie tekstu.
    val boxW = 80
    Box(
        modifier = Modifier
            .offset(x = sx(centerXPx - boxW / 2), y = sy(topPx))
            .width(sx(boxW)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text = text, color = color, fontSize = pnSp(sizePx, sy))
    }
}
