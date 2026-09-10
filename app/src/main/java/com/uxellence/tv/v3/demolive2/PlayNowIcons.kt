package com.uxellence.tv.v3.demolive2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * Ikony pasa kontrolek — odwzorowanie rzędu z launchera Play (Play Now box).
 * Kolejność i etykiety ZMIERZONE na urządzeniu (każda pozycja zafokusowana
 * i zrzucona osobno), bo etykieta pojawia się tylko pod aktywną ikoną:
 *
 *  0 LIVE      chip "LIVE"           → "Oglądasz LIVE"
 *  1 REC       kropka + "REC"        → "Nagraj od początku"
 *  2 RESTART   strzałka w kółko      → "Zacznij od początku"
 *  3 PAUSE     dwie belki            → "Zatrzymaj"
 *  4 GUIDE     ekran z listą         → "Podgląd programu TV"
 *  5 INFO      kółko z "i"           → "Zobacz opis"
 *  6 SETTINGS  dymek z zębatką       → "Napisy, dźwięk, jakość"
 *
 * Fokus: kafel 64x64 w #5AECD3, glif przemalowany na #48227C (bez zmiany rozmiaru).
 * Rysowane wektorowo (Canvas) — w APK launchera zasoby są zaciemnione, więc
 * ikony są odtworzone z geometrii zrzutów, nie wyciągnięte z pliku.
 */
enum class PnControl(val label: String) {
    LIVE("Oglądasz LIVE"),
    REC("Nagraj od początku"),
    RESTART("Zacznij od początku"),
    PAUSE("Zatrzymaj"),
    GUIDE("Podgląd programu TV"),
    INFO("Zobacz opis"),
    SETTINGS("Napisy, dźwięk, jakość"),
}

/** Etykieta pozycji PAUSE zależy od stanu odtwarzania. */
fun pnControlLabel(control: PnControl, isPaused: Boolean): String =
    if (control == PnControl.PAUSE && isPaused) "Odtwórz" else control.label

/**
 * Jedna pozycja pasa kontrolek: kafel fokusa 64x64 + glif.
 * [boxPx] to rozmiar kafla w px designu (PN.CTRL_BOX).
 */
@Composable
fun PnControlIcon(
    control: PnControl,
    focused: Boolean,
    isPaused: Boolean,
    boxPx: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val tint = if (focused) PN_PURPLE else PN_TEXT
    Box(
        modifier = Modifier
            .size(sx(boxPx), sy(boxPx))
            .then(
                if (focused) Modifier.background(PN_MINT, RoundedCornerShape(sx(10)))
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when (control) {
            PnControl.LIVE -> PnLiveChip(focused = focused, sx = sx, sy = sy)
            PnControl.REC -> PnRecGlyph(tint = tint, sx = sx, sy = sy)
            PnControl.RESTART -> PnVectorGlyph(tint, boxPx, sx, sy) { s, c -> restartPath(s, c) }
            PnControl.PAUSE ->
                if (isPaused) PnVectorGlyph(tint, boxPx, sx, sy) { s, c -> playPath(s, c) }
                else PnVectorGlyph(tint, boxPx, sx, sy) { s, c -> pausePath(s, c) }
            PnControl.GUIDE -> PnVectorGlyph(tint, boxPx, sx, sy) { s, c -> guidePath(s, c) }
            PnControl.INFO -> PnVectorGlyph(tint, boxPx, sx, sy) { s, c -> infoPath(s, c) }
            PnControl.SETTINGS -> PnVectorGlyph(tint, boxPx, sx, sy) { s, c -> settingsPath(s, c) }
        }
    }
}

/**
 * Chip "LIVE" — jedyna pozycja, która nie jest glifem, a plakietką.
 * Niezafokusowana: białe tło + granatowe litery. Zafokusowana: kafel jest mint,
 * więc chip odwraca się na granatowe tło + mint litery (tak jest na boxie).
 */
@Composable
private fun PnLiveChip(focused: Boolean, sx: (Int) -> Dp, sy: (Int) -> Dp) {
    val bg = if (focused) PN_PURPLE else PN_TEXT
    val fg = if (focused) PN_MINT else PN_PURPLE
    Box(
        modifier = Modifier
            .width(sx(46)).height(sy(24))
            .background(bg, RoundedCornerShape(sx(4))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "LIVE",
            color = fg,
            fontSize = pnSp(16, sy),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

/** REC — kropka nad napisem "REC". */
@Composable
private fun PnRecGlyph(tint: Color, sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(sx(12), sy(12))) {
            drawCircle(color = tint, radius = size.minDimension / 2f)
        }
        Text(
            text = "REC",
            color = tint,
            fontSize = pnSp(15, sy),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = sy(3))
        )
    }
}

/**
 * Glif wektorowy: [build] dostaje rozmiar kanwy i grubość kreski i zwraca
 * (ścieżka wypełniana, ścieżka obrysowywana).
 */
@Composable
private fun PnVectorGlyph(
    tint: Color,
    boxPx: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    build: (Size, Float) -> Pair<Path?, Path?>,
) {
    Canvas(modifier = Modifier.size(sx(boxPx), sy(boxPx))) {
        val stroke = size.minDimension * 0.055f
        val (fill, outline) = build(size, stroke)
        fill?.let { drawPath(it, color = tint) }
        outline?.let {
            drawPath(it, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
    }
}

// ═════════════ ścieżki glifów ═════════════
// Kształty odczytane ze zbliżenia (3x) rzędu kontrolek z boxa:
//   restart  — otwarte kółko z przerwą u góry + podwójny szewron « przy końcu łuku
//   pauza    — dwie PROSTOKĄTNE belki (bez zaokrągleń)
//   guide    — obrys ekranu TV z nóżką + lista: pasek, kropka+pasek, kropka+pasek
//   info     — obrys kółka + kropka i słupek "i"
//   settings — obrys dymka z ogonkiem w lewym dolnym narożniku + wypełniona zębatka
// Wszystko liczone we frakcjach rozmiaru kanwy (s), więc skaluje się z kaflem.

/** Strzałka zawinięta w otwarte kółko + podwójny szewron — "zacznij od początku". */
private fun restartPath(size: Size, stroke: Float): Pair<Path?, Path?> {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f + s * 0.035f
    val r = s * 0.30f
    // łuk 285° zgodnie z zegarem: przerwa u góry po prawej, koniec u góry po lewej
    val arc = Path().apply {
        addArc(Rect(cx - r, cy - r, cx + r, cy + r), -55f, 285f)
    }
    // dwa szewrony "<" wyprowadzone Z KOŃCA łuku (230°), skierowane w lewo
    val w = s * 0.135f
    val endX = cx + r * kotlin.math.cos(Math.toRadians(230.0)).toFloat()
    val endY = cy + r * kotlin.math.sin(Math.toRadians(230.0)).toFloat()
    val chevrons = Path().apply {
        for (i in 0..1) {
            val ax = endX - i * w * 0.62f
            val ay = endY - i * w * 0.10f
            moveTo(ax + w, ay - w * 0.85f)
            lineTo(ax, ay)
            lineTo(ax + w, ay + w * 0.85f)
        }
    }
    val outline = Path().apply { addPath(arc); addPath(chevrons) }
    return null to outline
}

/** Dwie prostokątne belki — pauza. */
private fun pausePath(size: Size, stroke: Float): Pair<Path?, Path?> {
    val s = size.minDimension
    val w = s * 0.115f
    val h = s * 0.55f
    val gap = s * 0.095f
    val top = (size.height - h) / 2f
    val leftX = size.width / 2f - gap / 2f - w
    val rightX = size.width / 2f + gap / 2f
    val p = Path().apply {
        addRect(Rect(leftX, top, leftX + w, top + h))
        addRect(Rect(rightX, top, rightX + w, top + h))
    }
    return p to null
}

/** Trójkąt play — pozycja PAUSE po zatrzymaniu. */
private fun playPath(size: Size, stroke: Float): Pair<Path?, Path?> {
    val s = size.minDimension
    val h = s * 0.50f
    val w = h * 0.86f
    val left = size.width / 2f - w * 0.42f
    val top = (size.height - h) / 2f
    val p = Path().apply {
        moveTo(left, top)
        lineTo(left + w, top + h / 2f)
        lineTo(left, top + h)
        close()
    }
    return p to null
}

/** Ekran TV z nóżką + lista w środku — "podgląd programu TV". */
private fun guidePath(size: Size, stroke: Float): Pair<Path?, Path?> {
    val s = size.minDimension
    val w = s * 0.62f
    val h = s * 0.47f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f - s * 0.04f
    val outline = Path().apply {
        addRoundRect(RoundRect(
            left = left, top = top, right = left + w, bottom = top + h,
            cornerRadius = CornerRadius(s * 0.045f, s * 0.045f)
        ))
    }
    val fills = Path().apply {
        // nóżka pod ekranem
        val footW = s * 0.15f
        addRect(Rect(
            size.width / 2f - footW / 2f, top + h,
            size.width / 2f + footW / 2f, top + h + s * 0.055f
        ))
        // lista: pierwszy wiersz sam pasek, dwa kolejne kropka + pasek
        val barH = s * 0.035f
        val dotR = s * 0.030f
        val innerLeft = left + s * 0.10f
        val innerRight = left + w - s * 0.09f
        val rows = floatArrayOf(0.24f, 0.50f, 0.74f)
        for ((i, fy) in rows.withIndex()) {
            val y = top + h * fy - barH / 2f
            val hasDot = i > 0
            if (hasDot) {
                val cxDot = innerLeft + dotR
                addOval(Rect(cxDot - dotR, y + barH / 2f - dotR, cxDot + dotR, y + barH / 2f + dotR))
            }
            val barLeft = if (hasDot) innerLeft + dotR * 3.2f else innerLeft
            addRoundRect(RoundRect(
                left = barLeft, top = y, right = innerRight, bottom = y + barH,
                cornerRadius = CornerRadius(barH / 2f, barH / 2f)
            ))
        }
    }
    return fills to outline
}

/** Obrys kółka z "i" — "zobacz opis". */
private fun infoPath(size: Size, stroke: Float): Pair<Path?, Path?> {
    val s = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = s * 0.29f
    val ring = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    val dotR = s * 0.037f
    val stemW = s * 0.062f
    val glyph = Path().apply {
        addOval(Rect(cx - dotR, cy - s * 0.155f - dotR, cx + dotR, cy - s * 0.155f + dotR))
        addRoundRect(RoundRect(
            left = cx - stemW / 2f, top = cy - s * 0.055f,
            right = cx + stemW / 2f, bottom = cy + s * 0.16f,
            cornerRadius = CornerRadius(stemW / 2f, stemW / 2f)
        ))
    }
    return glyph to ring
}

/** Dymek z ogonkiem + wypełniona zębatka — "napisy, dźwięk, jakość". */
private fun settingsPath(size: Size, stroke: Float): Pair<Path?, Path?> {
    val s = size.minDimension
    val w = s * 0.56f
    val h = s * 0.42f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f - s * 0.055f
    val bottom = top + h
    val outline = Path().apply {
        addRoundRect(RoundRect(
            left = left, top = top, right = left + w, bottom = bottom,
            cornerRadius = CornerRadius(s * 0.05f, s * 0.05f)
        ))
    }
    val fills = Path().apply {
        // ogonek dymka: wypełniony trójkąt w lewym dolnym narożniku
        moveTo(left + s * 0.07f, bottom - stroke * 0.6f)
        lineTo(left + s * 0.05f, bottom + s * 0.17f)
        lineTo(left + s * 0.27f, bottom - stroke * 0.6f)
        close()
        // zębatka: rdzeń + 8 zębów blisko rdzenia, z wyciętym oczkiem
        val gx = left + w / 2f
        val gy = top + h / 2f
        // Zębatka jako PIERŚCIEŃ + 8 promienistych kresek. Wersja z wypełnionymi
        // kulkami zębów zlewała się przy 64 px w "słoneczko" — pierścień czyta się
        // jak koło zębate nawet w tym rozmiarze.
        val core = s * 0.082f
        val ringW = s * 0.030f
        val toothW = s * 0.026f
        val gear = Path().apply {
            // pierścień = duże koło minus wewnętrzne
            addOval(Rect(gx - core, gy - core, gx + core, gy + core))
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val ca = kotlin.math.cos(a).toFloat()
                val sa = kotlin.math.sin(a).toFloat()
                val r0 = core * 0.85f
                val r1 = core * 1.42f
                // ząb jako krótki prostokąt wzdłuż promienia (przez 4 wierzchołki)
                moveTo(gx + ca * r0 - sa * toothW / 2f, gy + sa * r0 + ca * toothW / 2f)
                lineTo(gx + ca * r1 - sa * toothW / 2f, gy + sa * r1 + ca * toothW / 2f)
                lineTo(gx + ca * r1 + sa * toothW / 2f, gy + sa * r1 - ca * toothW / 2f)
                lineTo(gx + ca * r0 + sa * toothW / 2f, gy + sa * r0 - ca * toothW / 2f)
                close()
            }
        }
        val hole = Path().apply {
            val hr = core - ringW
            addOval(Rect(gx - hr, gy - hr, gx + hr, gy + hr))
        }
        addPath(Path().apply { op(gear, hole, PathOperation.Difference) })
    }
    return fills to outline
}

/**
 * Okrągły znaczek "zacznij od początku" przy karcie programu (26 px designu):
 * obrys kółka + mały łuk ze grotem w środku.
 */
@Composable
fun PnRestartBadge(sizePx: Int, tint: Color, sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Canvas(modifier = Modifier.size(sx(sizePx), sy(sizePx))) {
        val s = size.minDimension
        val ring = s * 0.085f
        drawCircle(color = tint, radius = s / 2f - ring / 2f, style = Stroke(ring))
        // łuk wewnętrzny (przerwa u góry) + grot po lewej
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.24f
        drawArc(
            color = tint,
            startAngle = -60f,
            sweepAngle = 285f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = s * 0.10f, cap = StrokeCap.Round)
        )
        val tip = s * 0.10f
        drawPath(
            Path().apply {
                moveTo(cx - r + tip * 0.1f, cy - tip * 1.1f)
                lineTo(cx - r + tip * 1.1f, cy - tip * 0.1f)
                lineTo(cx - r - tip * 0.9f, cy + tip * 0.1f)
                close()
            },
            color = tint
        )
    }
}
