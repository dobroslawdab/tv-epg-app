package com.uxellence.tv.v3.demolive2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 * DETAL PROGRAMU — stan po OK na miniaturce (3. poziom fokusa).
 *
 * Odwzorowanie zrzutu z boxa: karta rozwija się w górę ekranu, pod metadanymi
 * wchodzi rząd akcji („Nagraj" / „Przypomnij") i pełny opis. Pasek przewijania
 * zostaje, ale w trybie DOTS_ONLY — program pokazywany na karcie nie jest tym
 * granym, więc bulletu ani bąbelka nie ma (patrz [PnBarStyle]).
 */

private const val LABEL_TOP = 165
private const val COVER_TOP = 203
private const val TITLE_TOP = 250
private const val META_TOP = 318
private const val BTN_TOP = 392
private const val BTN_H = 64
private const val BTN_GAP = 24
private const val DESC_TOP = 485
private const val DESC_W = 910
private const val DESC_SIZE = 28

/** Kiedy leci program względem teraz — decyduje o zestawie akcji (jak v1). */
enum class PnTiming { PAST, CURRENT, FUTURE }

/** Akcje detalu. */
enum class PnDetailAction(val label: String) {
    WATCH("Oglądaj"),
    RECORD("Nagraj"),
    REMIND("Przypomnij"),
}

/**
 * Zestaw akcji wg czasu programu — ten sam podział co w 1. wersji:
 * miniony → oglądanie od początku (timeshift), bieżący → oglądanie,
 * przyszły → nagrywanie/przypomnienie.
 */
fun pnDetailActions(timing: PnTiming): List<PnDetailAction> = when (timing) {
    // Miniony i bieżący: "Oglądaj" (dla minionego = od początku, timeshift).
    // Przyszły: nagrywanie.
    PnTiming.PAST, PnTiming.CURRENT -> listOf(PnDetailAction.WATCH, PnDetailAction.RECORD)
    PnTiming.FUTURE -> listOf(PnDetailAction.RECORD, PnDetailAction.REMIND)
}

@Composable
fun PlayNowDetail(
    program: PnProgram,
    description: String,
    isToday: Boolean,
    focusedAction: Int,
    /** Akcje do pokazania — zależne od czasu programu (patrz [pnDetailActions]). */
    actions: List<PnDetailAction>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = if (isToday) "DZIŚ" else "JUTRO",
            color = PN_TEXT,
            fontSize = pnSp(PN.CARD_LABEL_SIZE, sy),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.offset(x = sx(PN.CARD_LABEL_LEFT), y = sy(LABEL_TOP))
        )

        Box(
            modifier = Modifier
                .offset(x = sx(PN.COVER_LEFT), y = sy(COVER_TOP))
                .size(sx(PN.COVER_W), sy(PN.COVER_H))
                .background(Color(0x33000000))
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
            text = program.title,
            color = PN_TEXT,
            fontSize = pnSp(PN.TITLE_SIZE, sy),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = sx(PN.TEXT_LEFT), y = sy(TITLE_TOP))
                .width(sx(1100))
        )
        Text(
            text = pnMeta(program.meta),
            color = PN_TEXT,
            fontSize = pnSp(PN.META_SIZE, sy),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = sx(PN.TEXT_LEFT), y = sy(META_TOP))
                .width(sx(1100))
        )

        // ── rząd akcji ──
        var x = PN.CARD_LABEL_LEFT
        actions.forEachIndexed { i, action ->
            val w = when (action) {
                PnDetailAction.RECORD -> 130
                PnDetailAction.WATCH -> 160
                PnDetailAction.REMIND -> 200
            }
            PnActionButton(
                label = action.label,
                focused = i == focusedAction,
                leftPx = x,
                widthPx = w,
                sx = sx, sy = sy
            )
            x += w + BTN_GAP
        }

        // ── opis ──
        if (description.isNotBlank()) {
            Text(
                text = description,
                color = PN_TEXT,
                fontSize = pnSp(DESC_SIZE, sy),
                lineHeight = pnSp(48, sy),
                maxLines = 7,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = sx(PN.CARD_LABEL_LEFT), y = sy(DESC_TOP))
                    .width(sx(DESC_W))
            )
        }
    }
}

@Composable
private fun PnActionButton(
    label: String,
    focused: Boolean,
    leftPx: Int,
    widthPx: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    Box(
        modifier = Modifier
            .offset(x = sx(leftPx), y = sy(BTN_TOP))
            .width(sx(widthPx))
            .height(sy(BTN_H))
            .background(
                if (focused) PN_MINT else Color(0x33EEEEEE),
                RoundedCornerShape(sx(10))
            )
            .padding(horizontal = sx(16)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (focused) PN_PURPLE else PN_TEXT,
            fontSize = pnSp(26, sy),
            maxLines = 1
        )
    }
}
