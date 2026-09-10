package com.uxellence.tv.v3.demolive2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage

/** Program bloku ramówki na osi wirtualnej kanału. */
data class PnProgram(
    val title: String,
    val meta: List<String>,
    val coverUrl: String?,
    val startMs: Long,
    val endMs: Long,
)

/** Jeden kanał w mini-EPG. */
data class PnChannelRow(
    val name: String,
    val number: Int,
    val logoUrl: String?,
    val programs: List<PnProgram>,
    /** Indeks programu aktualnie emitowanego na tym kanale. */
    val liveIndex: Int,
)

/** Strefy fokusa warstwy playera (pas kontrolek / pas przewijania / mini-EPG). */
enum class PnZone { CONTROLS, SCRUB, MINI_EPG }

/**
 * WARSTWA PLAYERA "PLAY NOW" — wszystko, co rysuje się NAD wideo:
 * identyfikacja kanału, karta programu, pas przewijania, pas kontrolek, mini-EPG.
 * Sam player/wideo jest poza tym plikiem (DemoLive2Screen).
 */
@Composable
fun PlayNowOverlay(
    visible: Boolean,
    zone: PnZone,
    controlIndex: Int,
    isPaused: Boolean,
    channelName: String,
    channelNumber: Int,
    channelLogoUrl: String?,
    /** Program pokazywany na karcie (może być inny niż emitowany — po przewinięciu). */
    shownProgram: PnProgram,
    nextProgram: PnProgram?,
    /** Czy [shownProgram] jest tym, który właśnie leci. */
    shownIsLive: Boolean,
    positionMs: Long,
    cursorMs: Long?,
    antennaStartWallMs: Long,
    liveEdgeMs: Long,
    miniEpgRows: List<PnChannelRow>,
    miniEpgRowIndex: Int,
    miniEpgProgramIndex: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Gradient nakładki (diagonalny — patrz PlayNowTheme)
            PnOverlayGradient(sx, sy)

            // Zegar ścienny
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = pnClock(antennaStartWallMs + liveEdgeMs),
                    color = PN_TEXT,
                    fontSize = pnSp(PN.CLOCK_SIZE, sy),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = sy(PN.CLOCK_TOP), end = sx(PN.CLOCK_RIGHT))
                )
            }

            if (zone == PnZone.MINI_EPG) {
                PlayNowMiniEpg(
                    rows = miniEpgRows,
                    rowIndex = miniEpgRowIndex,
                    programIndex = miniEpgProgramIndex,
                    antennaStartWallMs = antennaStartWallMs,
                    positionMs = positionMs,
                    sx = sx, sy = sy
                )
            } else {
                PnChannelIdentity(channelName, channelNumber, channelLogoUrl, sx, sy)
                PnProgramCard(shownProgram, nextProgram, shownIsLive, antennaStartWallMs, sx, sy)
                PlayNowSeekBar(
                    trackTopPx = PN.TRACK_TOP,
                    blockStartMs = shownProgram.startMs,
                    blockEndMs = shownProgram.endMs,
                    positionMs = positionMs,
                    cursorMs = cursorMs,
                    antennaStartWallMs = antennaStartWallMs,
                    focused = zone == PnZone.SCRUB,
                    sx = sx, sy = sy
                )
                PnControlBar(
                    focusedIndex = if (zone == PnZone.CONTROLS) controlIndex else -1,
                    isPaused = isPaused,
                    sx = sx, sy = sy
                )
            }
        }
    }
}

/**
 * Gradient tła nakładki — DWIE warstwy z dopasowania pola alfy boxa:
 * najpierw diagonalna poświata od lewej (B), na niej pionowa kurtyna dolna (A).
 * Kolejność ma znaczenie — odwrotna daje inne złożenie alfy.
 */
@Composable
private fun PnOverlayGradient(sx: (Int) -> Dp, sy: (Int) -> Dp) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pnOverlayBrushDiagonal(sx, sy, density))
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pnOverlayBrushBottom(sy, density))
    )
}

// ═════════════ identyfikacja kanału (logo + numer) ═════════════

@Composable
private fun PnChannelIdentity(
    name: String,
    number: Int,
    logoUrl: String?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    if (logoUrl != null) {
        AsyncImage(
            model = logoUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = sx(PN.LOGO_LEFT), y = sy(PN.LOGO_TOP))
                .width(sx(PN.LOGO_MAX_W))
                .height(sy(PN.LOGO_H))
        )
    } else {
        // Brak logo (kanały z nagrań) — nazwa kanału w miejscu logotypu
        Text(
            text = name,
            color = PN_TEXT,
            fontSize = pnSp(28, sy),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .offset(x = sx(PN.LOGO_LEFT), y = sy(PN.LOGO_TOP + 8))
                .width(sx(PN.LOGO_MAX_W + 60))
        )
    }
    Box(
        modifier = Modifier
            .offset(x = sx(PN.NUM_LEFT), y = sy(PN.NUM_TOP))
            .widthIn(min = sx(PN.NUM_MIN_W))
            .height(sy(PN.NUM_H))
            .border(sx(2), PN_TEXT_DIM, RoundedCornerShape(sx(6)))
            .padding(horizontal = sx(9)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (number < 10) "0$number" else "$number",
            color = PN_TEXT,
            fontSize = pnSp(PN.NUM_SIZE, sy)
        )
    }
}

// ═════════════ karta programu ═════════════

@Composable
private fun PnProgramCard(
    program: PnProgram,
    next: PnProgram?,
    shownIsLive: Boolean,
    antennaStartWallMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    // Etykieta nad kartą: program emitowany → "TERAZ OGLĄDASZ", inny → "DZIŚ"
    Text(
        text = if (shownIsLive) "TERAZ OGLĄDASZ" else "DZIŚ",
        color = PN_TEXT,
        fontSize = pnSp(PN.CARD_LABEL_SIZE, sy),
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.offset(x = sx(PN.CARD_LABEL_LEFT), y = sy(PN.CARD_LABEL_TOP))
    )

    // Okładka programu
    Box(
        modifier = Modifier
            .offset(x = sx(PN.COVER_LEFT), y = sy(PN.COVER_TOP))
            .size(sx(PN.COVER_W), sy(PN.COVER_H))
            .background(Color_Cover)
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

    // Podpowiedź "ZACZNIJ OD POCZĄTKU"
    Box(modifier = Modifier.offset(x = sx(PN.TEXT_LEFT), y = sy(PN.HINT_TOP))) {
        PnRestartBadge(PN.HINT_ICON, PN_TEXT, sx, sy)
    }
    Text(
        text = "ZACZNIJ OD POCZĄTKU",
        color = PN_TEXT,
        fontSize = pnSp(PN.HINT_SIZE, sy),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier.offset(x = sx(PN.HINT_TEXT_LEFT), y = sy(PN.HINT_TOP + 4))
    )

    // Tytuł + metadane
    Text(
        text = program.title,
        color = PN_TEXT,
        fontSize = pnSp(PN.TITLE_SIZE, sy),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x = sx(PN.TEXT_LEFT), y = sy(PN.TITLE_TOP))
            .width(sx(PN.NEXT_LEFT - PN.TEXT_LEFT - 40))
    )
    Text(
        text = pnMeta(program.meta),
        color = PN_TEXT,
        fontSize = pnSp(PN.META_SIZE, sy),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x = sx(PN.TEXT_LEFT), y = sy(PN.META_TOP))
            .width(sx(PN.NEXT_LEFT - PN.TEXT_LEFT - 40))
    )

    // Zajawka następnego programu (wygaszona, ucięta krawędzią ekranu)
    if (next != null) {
        Text(
            text = next.title,
            color = PN_TEXT_DIM,
            fontSize = pnSp(PN.TITLE_SIZE, sy),
            maxLines = 1,
            modifier = Modifier
                .offset(x = sx(PN.NEXT_LEFT), y = sy(PN.TITLE_TOP))
        )
        Text(
            text = pnMeta(next.meta),
            color = PN_TEXT_DIM,
            fontSize = pnSp(PN.META_SIZE, sy),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .offset(x = sx(PN.NEXT_LEFT), y = sy(PN.META_TOP))
        )
    }
}

/** Tło okładki, gdy miniaturka jeszcze się nie wczytała. */
private val Color_Cover = androidx.compose.ui.graphics.Color(0x33000000)

// ═════════════ pas kontrolek ═════════════

@Composable
private fun PnControlBar(
    focusedIndex: Int,
    isPaused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val controls = PnControl.values().toList()
    controls.forEachIndexed { i, control ->
        val cx = PN.CTRL_FIRST_CX + i * PN.CTRL_STEP
        Box(
            modifier = Modifier.offset(
                x = sx(cx - PN.CTRL_BOX / 2),
                y = sy(PN.CTRL_CY - PN.CTRL_BOX / 2)
            )
        ) {
            PnControlIcon(
                control = control,
                focused = i == focusedIndex,
                isPaused = isPaused,
                boxPx = PN.CTRL_BOX,
                sx = sx, sy = sy
            )
        }
    }
    // Etykieta tylko pod zafokusowaną ikoną (tak jest w launcherze Play)
    if (focusedIndex in controls.indices) {
        val cx = PN.CTRL_FIRST_CX + focusedIndex * PN.CTRL_STEP
        val boxW = 460
        Box(
            modifier = Modifier
                .offset(x = sx(cx - boxW / 2), y = sy(PN.CTRL_LABEL_TOP))
                .width(sx(boxW)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pnControlLabel(controls[focusedIndex], isPaused),
                color = PN_MINT,
                fontSize = pnSp(PN.CTRL_LABEL_SIZE, sy),
                maxLines = 1
            )
        }
    }
}
