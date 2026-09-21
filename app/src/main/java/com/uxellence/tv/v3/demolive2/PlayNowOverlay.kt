package com.uxellence.tv.v3.demolive2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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

/**
 * Program bloku ramówki. Czasy w ZEGARZE ŚCIENNYM, nie na osi wirtualnej —
 * mini-EPG zestawia kanały o RÓŻNYCH osiach (każde nagranie ma własny
 * recordedAtWallMs, kanały mockupowe nie mają żadnej), więc wspólnym
 * mianownikiem może być tylko zegar.
 */
data class PnProgram(
    val title: String,
    val meta: List<String>,
    val coverUrl: String?,
    val startWallMs: Long,
    val endWallMs: Long,
    /** Opis do detalu — pokazywany pod przyciskami akcji. */
    val description: String = "",
)

/** Jeden kanał w mini-EPG. */
data class PnChannelRow(
    val name: String,
    val number: Int,
    val logoUrl: String?,
    val programs: List<PnProgram>,
    /** Indeks programu aktualnie emitowanego na tym kanale. */
    val liveIndex: Int,
    /** Czy da się na niego przełączyć (kanały mockupowe są tylko do ramówki). */
    val tunable: Boolean = true,
)

/** Strefy fokusa warstwy playera (pas kontrolek / pas przewijania / mini-EPG). */
/**
 * Strefy fokusa warstwy playera. Trzy poziomy nad wideo (od dołu):
 * pas kontrolek → pas przewijania → miniaturka/karta programu (wejście w detal),
 * plus mini-EPG wywoływane DOŁEM z pasa kontrolek.
 */
enum class PnZone { CONTROLS, SCRUB, CARD, MINI_EPG }

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
    /** Który wiersz mini-EPG to kanał aktualnie dostrojony. */
    miniEpgTunedIndex: Int = 0,
    /** Taśma podglądu przewijania — widoczna po pierwszym LEWO/PRAWO na pasku. */
    scrubTapeVisible: Boolean,
    scrubFrames: List<Pair<android.graphics.Bitmap?, Long>> = emptyList(),
    dvrStartMs: Long = 0L,
    blockTitleFor: (Long) -> String? = { null },
    /** Detal programu (OK na miniaturce) — karta rozwija się w opis + akcje. */
    detailOpen: Boolean = false,
    detailDescription: String = "",
    detailActionIndex: Int = 0,
    /** Program pokazywany w detalu (z karty albo z mini-EPG). */
    detailProgram: PnProgram? = null,
    detailActions: List<PnDetailAction> = emptyList(),
    detailIsToday: Boolean = true,
    /** Czas programu w detalu — decyduje o wypełnieniu paska (jak v1). */
    detailTiming: PnTiming = PnTiming.CURRENT,
    /** Czy pokazywany program to ten AKTUALNIE GRANY (steruje wariantem paska). */
    shownIsPlaying: Boolean = true,
    /** Czy jest poprzedni program — pokazuje podpowiedź "Oglądaj poprzednie". */
    hasPrevProgram: Boolean = true,
    /** Czy odtwarzanie jest na żywo — etykieta LIVE (jak v1). */
    isAtLiveEdge: Boolean = true,
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

            if (zone == PnZone.MINI_EPG && !detailOpen) {
                PlayNowMiniEpg(
                    rows = miniEpgRows,
                    rowIndex = miniEpgRowIndex,
                    programIndex = miniEpgProgramIndex,
                    positionWallMs = antennaStartWallMs + positionMs,
                    liveEdgeWallMs = antennaStartWallMs + liveEdgeMs,
                    tunedRowIndex = miniEpgTunedIndex,
                    sx = sx, sy = sy
                )
            } else if (detailOpen) {   // także gdy przyszliśmy z mini-EPG
                PlayNowDetail(
                    program = detailProgram ?: shownProgram,
                    description = detailDescription,
                    isToday = detailIsToday,
                    focusedAction = detailActionIndex,
                    actions = detailActions,
                    sx = sx, sy = sy
                )
                val dp = detailProgram ?: shownProgram
                PlayNowSeekBar(
                    trackTopPx = PN.TRACK_TOP,
                    blockStartWallMs = dp.startWallMs,
                    blockEndWallMs = dp.endWallMs,
                    // Miniony materiał ma pasek CAŁY WYPEŁNIONY (jak v1) —
                    // pozycja "po końcu bloku" daje pełne wypełnienie.
                    positionWallMs = when (detailTiming) {
                        PnTiming.PAST -> dp.endWallMs
                        PnTiming.FUTURE -> dp.startWallMs
                        PnTiming.CURRENT -> antennaStartWallMs + positionMs
                    },
                    cursorWallMs = null,
                    focused = false,
                    style = if (detailTiming == PnTiming.FUTURE) PnBarStyle.DOTS_ONLY
                    else PnBarStyle.POSITION_ONLY,
                    sx = sx, sy = sy
                )
            } else {
                if (scrubTapeVisible) {
                    // Podgląd przewijania: karta programu i pas kontrolek ustępują
                    // taśmie miniatur; pas przewijania zostaje na swoim miejscu.
                    PlayNowScrubTape(
                        cursorMs = cursorMs ?: positionMs,
                        liveEdgeMs = liveEdgeMs,
                        dvrStartMs = dvrStartMs,
                        frames = scrubFrames.map { (bmp, off) -> off to bmp },
                        blockTitleFor = blockTitleFor,
                        sx = sx, sy = sy
                    )
                } else {
                    PnChannelIdentity(channelName, channelNumber, channelLogoUrl, sx, sy)
                    if (hasPrevProgram) PnWatchPrev(sx, sy)
                    PnProgramCard(
                        program = shownProgram,
                        next = nextProgram,
                        shownIsLive = shownIsLive,
                        focused = zone == PnZone.CARD,
                        sx = sx, sy = sy
                    )
                }
                PlayNowSeekBar(
                    // Na czas taśmy pas zjeżdża niżej — tak samo jak w V4
                    trackTopPx = if (scrubTapeVisible) PN.TRACK_TOP_TAPE else PN.TRACK_TOP,
                    blockStartWallMs = shownProgram.startWallMs,
                    blockEndWallMs = shownProgram.endWallMs,
                    positionWallMs = antennaStartWallMs + positionMs,
                    cursorWallMs = cursorMs?.let { antennaStartWallMs + it },
                    liveEdgeWallMs = antennaStartWallMs + liveEdgeMs,
                    focused = zone == PnZone.SCRUB,
                    // Bullet z godziną należy do POZYCJI ODTWARZANIA/kursora: na
                    // kontrolkach i pasku jest zawsze, a znika dopiero gdy karta
                    // wędruje po ramówce na inny program niż grany.
                    style = if (zone == PnZone.CARD && !shownIsPlaying) {
                        PnBarStyle.DOTS_ONLY
                    } else {
                        PnBarStyle.PLAYING
                    },
                    sx = sx, sy = sy
                )
                if (!scrubTapeVisible) {
                    PnControlBar(
                        focusedIndex = if (zone == PnZone.CONTROLS) controlIndex else -1,
                        isPaused = isPaused,
                        isAtLiveEdge = isAtLiveEdge,
                        sx = sx, sy = sy
                    )
                }
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
    /** 3. poziom fokusa — ramka mint wokół miniaturki. */
    focused: Boolean,
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
    isAtLiveEdge: Boolean,
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
                text = pnControlLabel(controls[focusedIndex], isPaused, isAtLiveEdge),
                color = PN_MINT,
                fontSize = pnSp(PN.CTRL_LABEL_SIZE, sy),
                maxLines = 1
            )
        }
    }
}

/**
 * "Oglądaj poprzednie" — podpowiedź, że LEWO cofa do wcześniejszego programu.
 * Przeniesione z 1. wersji (tam stało w V4PlayerLayer); w v2 siedzi po LEWEJ
 * od karty programu, na wysokości okładki. Element czysto wizualny — nie bierze
 * fokusa, samo przewijanie robi LEWO na poziomie karty.
 */
@Composable
private fun PnWatchPrev(sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(PN.PREV_GAP)),
        modifier = Modifier.offset(x = sx(PN.PREV_LEFT), y = sy(PN.PREV_TOP))
    ) {
        Box(
            modifier = Modifier
                .size(sx(PN.PREV_CIRCLE), sy(PN.PREV_CIRCLE))
                .clip(CircleShape)
                .background(PN_PURPLE),
            contentAlignment = Alignment.Center
        ) {
            Text("◀", color = PN_TEXT, fontSize = pnSp(24, sy))
        }
        Text(
            text = "Oglądaj\npoprzednie",
            color = PN_TEXT,
            fontSize = pnSp(PN.PREV_TEXT_SIZE, sy),
            lineHeight = pnSp(32, sy),
            fontWeight = FontWeight.Medium
        )
    }
}
