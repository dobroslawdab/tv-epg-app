package com.uxellence.tv.v3.demolive2

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/**
 * PODGLĄD PRZEWIJANIA — jedyne miejsce, gdzie ŚWIADOMIE odchodzimy od launchera Play.
 *
 * Play Now przy przewijaniu nie pokazuje nic poza playheadem. My wchodzimy tu
 * taśmą miniatur z NASZEGO playera (prototyp „Nowy player – scrubb preview 2026",
 * ta sama geometria co V4ScrubStrip w demolive): ciągła taśma slotów 292x175
 * z powiększonym kadrem kursora 486x292 w białej ramce, pod spodem tytuł
 * materiału pod kursorem.
 *
 * Taśma pojawia się DOPIERO po pierwszym LEWO/PRAWO na pasku (jak w prototypie) —
 * samo wejście na pasek pokazuje tylko playhead. Gdy taśma jest widoczna,
 * karta programu i pas kontrolek chowają się; pas przewijania zostaje.
 *
 * Pozycje pionowe 1:1 z V4: taśma 566..858, tytuł 890, a pas przewijania na
 * czas taśmy zjeżdża z 832 na 988 (tak samo jak V4Timeline przełącza 804→978).
 */

// Pozycje 1:1 z V4ScrubStrip w demolive (prototyp „Nowy player – scrubb
// preview 2026"): taśma 566..858, tytuł 890. Pas przewijania zjeżdża wtedy
// na 988 — patrz PN.TRACK_TOP_TAPE.
private const val TAPE_TOP = 566
private const val TAPE_H = 292
private const val SLOT_W = 292
private const val SLOT_H = 175
private const val FOCUS_W = 486
private const val FOCUS_H = 292
private const val GAP = 20
private const val TITLE_TOP = 890
private const val TITLE_SIZE = 32

@Composable
fun PlayNowScrubTape(
    /** Kursor przewijania na osi wirtualnej — środek taśmy. */
    cursorMs: Long,
    liveEdgeMs: Long,
    dvrStartMs: Long,
    /** Sloty z [DemoFilmstripProvider.framesAround]: (offset od kursora, klatka). */
    frames: List<Pair<Long, Bitmap?>>,
    /** Tytuł bloku ramówki pod daną pozycją wirtualną. */
    blockTitleFor: (Long) -> String?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = sy(TAPE_TOP))
            .height(sy(TAPE_H))
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(GAP)),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.wrapContentWidth(unbounded = true)
        ) {
            frames.forEachIndexed { index, (offsetMs, bitmap) ->
                val isCenter = index == frames.size / 2
                val v = cursorMs + offsetMs
                val inRange = v in dvrStartMs..liveEdgeMs
                Box(
                    modifier = Modifier
                        .size(
                            sx(if (isCenter) FOCUS_W else SLOT_W),
                            sy(if (isCenter) FOCUS_H else SLOT_H)
                        )
                        .then(
                            if (isCenter) Modifier
                                .shadow(sy(20), RoundedCornerShape(sx(16)))
                                .clip(RoundedCornerShape(sx(16)))
                                .background(Color(0x59000000))
                                .border(sx(4), PN_TEXT, RoundedCornerShape(sx(16)))
                            else Modifier
                                .clip(RoundedCornerShape(sx(10)))
                                .background(if (inRange) Color(0x59000000) else Color.Transparent)
                        )
                ) {
                    if (inRange && bitmap != null && !bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Tytuł materiału pod kursorem — WYCENTROWANY pod dużym kadrem
    // (nie przy lewej krawędzi — uwaga z iteracji nad V4)
    val title = blockTitleFor(cursorMs.coerceIn(dvrStartMs, liveEdgeMs))
    if (title != null) {
        Box(modifier = Modifier.fillMaxWidth().offset(y = sy(TITLE_TOP))) {
            Text(
                text = title,
                color = PN_TEXT,
                fontSize = pnSp(TITLE_SIZE, sy),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).widthIn(max = sx(1400))
            )
        }
    }
}
