package com.uxellence.tv.v3.demolive2

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.uxellence.tv.v3.R
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
enum class PnControl(val label: String, val iconRes: Int) {
    PAUSE("Zatrzymaj", R.drawable.demo_ic_pause),
    RESTART("Zacznij od początku", R.drawable.demo_ic_startover),
    LIVE("Wróć do live", R.drawable.demo_ic_live),
    REC("Nagraj od początku", R.drawable.demo_ic_rec),
    INFO("Zobacz opis", R.drawable.demo_ic_info),
    GUIDE("Podgląd programu TV", R.drawable.demo_ic_epg),
    SETTINGS("Napisy, dźwięk, jakość", R.drawable.demo_ic_settings),
}

/** Etykiety zmienne: pauza wg stanu odtwarzania, LIVE wg tego, czy jesteśmy na żywo. */
fun pnControlLabel(control: PnControl, isPaused: Boolean, isAtLiveEdge: Boolean): String = when {
    control == PnControl.PAUSE && isPaused -> "Odtwórz"
    control == PnControl.LIVE && isAtLiveEdge -> "Oglądasz LIVE"
    else -> control.label
}

/**
 * Jedna pozycja pasa kontrolek: kafel fokusa 64x64 + glif.
 * [boxPx] to rozmiar kafla w px designu (PN.CTRL_BOX).
 */
@Composable
fun PnControlIcon(
    control: PnControl,
    focused: Boolean,
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
        // Ikona jest stała (jak w v1) — zmienia się tylko etykieta pod nią
        Image(
            painter = painterResource(control.iconRes),
            contentDescription = control.label,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(sx(boxPx - 18), sy(boxPx - 18))
        )
    }
}

/**
 * Znaczek "zacznij od początku" przy karcie programu — ten sam zasób co ikona
 * w rzędzie kontrolek (demo_ic_startover), żeby kształt się nie rozjeżdżał.
 */
@Composable
fun PnRestartBadge(sizePx: Int, tint: Color, sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Image(
        painter = painterResource(R.drawable.demo_ic_startover),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(sx(sizePx), sy(sizePx))
    )
}
