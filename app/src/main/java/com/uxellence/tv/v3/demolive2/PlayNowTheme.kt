package com.uxellence.tv.v3.demolive2

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEMO LIVE 2 — tokeny warstwy playera odwzorowanej z LAUNCHERA PLAY (Play Now box).
 *
 * Referencja: zrzuty z PLAY BOX TV 4B (pl.play.playnow.box 3.10.10, adb screencap
 * 1920x1080, wideo pod spodem czarne przez DRM — dzięki temu piksele nakładki dały
 * się zmierzyć wprost). Wszystkie liczby poniżej są ZMIERZONE, nie oszacowane:
 *
 *  - kolory: próbka piksela (kolor nakładki nad czarnym = kolor własny)
 *    — UWAGA: podlanie/gradient celowo NIE jest z boxa, tylko nasze #281443,
 *    geometria pola alfy zostaje zmierzona,
 *  - poświata pod paskiem: #5AECD3 z alfą 0.60 gasnącą do 0 na 68 px
 *    (rozwiązane z równania mieszania nad #48227C — R/G/B zgadzają się co do bitu),
 *  - gradient tła: pole alfy wyliczone z siatki 77 próbek i dopasowane numerycznie
 *    (RMSE 0.030) do DWÓCH warstw — pionowej kurtyny dolnej + diagonalnej poświaty
 *    od lewej. Jedna warstwa liniowa NIE wystarcza: po prawej pole saturuje na
 *    y=720 niezależnie od x, czego pojedynczy gradient nie odda.
 *
 * Układ w px designu 1920x1080; do Compose przez sx/sy ekranu (jak reszta projektu).
 */

// ═══════════════ kolory (próbkowane z boxa) ═══════════════

/**
 * Podlanie/gradienty pod playerem — CIEMNY fiolet projektu (#281443, ten sam co
 * overlay-scrub w demolive V4 i tło modali). Launcher Play ma tu #48227C, ale
 * w naszej makiecie warstwa playera ma siedzieć na ciemniejszym podlaniu.
 */
val PN_SCRIM = Color(0xFF281443)

/**
 * Brand purple — #48227C. NIE jest już kolorem podlania: zostaje wyłącznie tam,
 * gdzie jest kolorem "na akcencie" (glif na kaflu mint, chip LIVE).
 */
val PN_PURPLE = Color(0xFF48227C)

/** Akcent/fokus — #5AECD3 (ten sam aqua co karty kanałów w projekcie). */
val PN_MINT = Color(0xFF5AECD3)

/** Tekst podstawowy. */
val PN_TEXT = Color(0xFFEEEEEE)

/** Tekst wygaszony (sąsiednie programy, etykiety pomocnicze) — 40%. */
val PN_TEXT_DIM = Color(0x66EEEEEE)

/** Tekst półwygaszony (np. "TERAZ OGLĄDASZ" w mini-EPG) — 70%. */
val PN_TEXT_SOFT = Color(0xB3EEEEEE)

/** Pasek przewijania: część obejrzana / bieżący blok. */
val PN_TRACK = Color(0xFFDFDFDF)

/** Pasek przewijania: bloki sąsiednie (nieaktywne). */
val PN_TRACK_DIM = Color(0x66EEEEEE)

// ═══════════════ geometria (px designu 1920x1080) ═══════════════

object PN {
    // --- gradient nakładki (DWIE warstwy) ---
    // Pole alfy zmierzone na boxie NIE jest jednym gradientem liniowym: po prawej
    // stronie saturuje na y=720 niezależnie od x. Dopasowanie numeryczne
    // (77 próbek, RMSE 0.030) daje dwie warstwy, B rysowana POD A:
    //   A — pionowa kurtyna dolna: przezroczysta 520 → pełna 720
    //   B — diagonalna poświata lewa: izolinia 0.5 przez (40,325), nachylenie 0.30,
    //       rampa 250 px w kierunku normalnej n = (-0.2873, 0.9578)
    const val GRAD_A_TOP = 520f
    const val GRAD_A_BOTTOM = 720f
    const val GRAD_B_START_X = 75.9f     // P - 125n
    const val GRAD_B_START_Y = 205.3f
    const val GRAD_B_END_X = 4.1f        // P + 125n
    const val GRAD_B_END_Y = 444.7f

    // --- zegar ---
    const val CLOCK_RIGHT = 100      // margines od prawej krawędzi (1920-1820)
    const val CLOCK_TOP = 52
    const val CLOCK_SIZE = 34

    // --- identyfikacja kanału ---
    const val LOGO_LEFT = 105
    const val LOGO_TOP = 394
    const val LOGO_H = 48
    const val LOGO_MAX_W = 160
    const val NUM_LEFT = 145
    const val NUM_TOP = 505
    const val NUM_H = 36
    const val NUM_MIN_W = 42
    const val NUM_SIZE = 22

    // --- karta programu (strefa CONTROLS/SCRUB) ---
    const val CARD_LABEL_LEFT = 336
    const val CARD_LABEL_TOP = 594
    const val CARD_LABEL_SIZE = 18
    const val COVER_LEFT = 336
    const val COVER_TOP = 633
    const val COVER_W = 280
    const val COVER_H = 170
    const val TEXT_LEFT = 648
    const val HINT_TOP = 641
    const val HINT_ICON = 26
    const val HINT_TEXT_LEFT = 684
    const val HINT_SIZE = 17
    const val TITLE_TOP = 697
    const val TITLE_SIZE = 52
    const val META_TOP = 764
    const val META_SIZE = 21
    const val NEXT_LEFT = 1608

    // --- pasek przewijania ---
    const val TRACK_TOP = 832
    const val TRACK_H = 12
    const val SEG_PREV_END = 258       // koniec segmentu poprzedniego bloku
    const val DOT_PREV_CX = 290        // kropka granicy: start bieżącego bloku
    const val SEG_CUR_START = 325
    const val SEG_CUR_END = 1530
    const val DOT_NEXT_CX = 1562       // kropka granicy: koniec bieżącego bloku
    const val SEG_NEXT_START = 1598
    const val DOT_R = 7
    const val LABEL_TOP = 856
    const val LABEL_SIZE = 26
    const val HEAD_R = 16
    const val GLOW_H = 68              // poświata: 0.60 alfy → 0
    const val GLOW_ALPHA = 0.60f
    const val BUBBLE_TOP = 866
    const val BUBBLE_H = 28
    const val BUBBLE_W = 122
    const val BUBBLE_SIZE = 26

    // --- pas kontrolek ---
    // Rząd 7 ikon; środek rzędu na 926 px (NIE 960 — zmierzone na boxie).
    const val CTRL_FIRST_CX = 524
    const val CTRL_STEP = 134
    const val CTRL_CY = 952
    const val CTRL_BOX = 64
    const val CTRL_LABEL_TOP = 998
    const val CTRL_LABEL_SIZE = 26

    // --- mini-EPG ---
    // Pasek przewijania zjeżdża niżej, a nad/pod nim stoją rzędy kanałów.
    const val EPG_TRACK_TOP = 929
    const val EPG_ROW_H = 215
    const val EPG_RAIL_LEFT = 38
    const val EPG_RAIL_LABEL_SIZE = 20
    const val EPG_COVER_LEFT = 344
    const val EPG_COVER_W = 214
    const val EPG_COVER_H = 120
    const val EPG_TEXT_LEFT = 585
    const val EPG_TIME_SIZE = 24
    const val EPG_TITLE_SIZE = 42
    const val EPG_META_SIZE = 20
    const val EPG_NEXT_LEFT = 1305
}

/** Warstwa B nakładki — diagonalna poświata od lewej (rysowana POD A). */
fun pnOverlayBrushDiagonal(sx: (Int) -> Dp, sy: (Int) -> Dp, density: Float): Brush {
    fun px(v: Float, f: (Int) -> Dp) = f(1).value * v * density
    return Brush.linearGradient(
        0f to Color.Transparent,
        1f to PN_SCRIM,
        start = Offset(px(PN.GRAD_B_START_X, sx), px(PN.GRAD_B_START_Y, sy)),
        end = Offset(px(PN.GRAD_B_END_X, sx), px(PN.GRAD_B_END_Y, sy))
    )
}

/** Warstwa A nakładki — pionowa kurtyna dolna (rysowana NAD B). */
fun pnOverlayBrushBottom(sy: (Int) -> Dp, density: Float): Brush {
    fun py(v: Float) = sy(1).value * v * density
    return Brush.verticalGradient(
        0f to Color.Transparent,
        1f to PN_SCRIM,
        startY = py(PN.GRAD_A_TOP),
        endY = py(PN.GRAD_A_BOTTOM)
    )
}

/**
 * Font skalowany jak w całym projekcie: rozmiar z designu 1920x1080 × sy.
 * (Surowe .sp na TV dawało fonty ~2x za duże — patrz demoSp w demolive.)
 */
fun pnSp(designPx: Int, sy: (Int) -> Dp): TextUnit = (designPx * sy(1).value).sp

/** Godzina zegara ściennego. */
fun pnClock(wallMs: Long, withSeconds: Boolean = false): String =
    SimpleDateFormat(if (withSeconds) "HH:mm:ss" else "HH:mm", Locale.getDefault())
        .format(Date(wallMs))

/** Metadane programu w formacie Play Now: "2023 r.  I  serial  I  60 min.  I  12 lat". */
fun pnMeta(parts: List<String>): String =
    parts.filter { it.isNotBlank() }.joinToString("   I   ")
