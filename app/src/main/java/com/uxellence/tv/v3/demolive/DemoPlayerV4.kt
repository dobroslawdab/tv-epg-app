package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.uxellence.tv.v3.R

/**
 * PLAYER WERSJA 4 — odwzorowanie 1:1 prototypu player-scrub
 * (~/Downloads/player-scrub, makiety Figma "Nowy player – scrubb preview 2026",
 * canvas 1920×1080 → wszystkie wymiary wprost z public/style.css przez sx/sy).
 *
 * Trzy poziomy fokusa (jak w prototypie: kontrolki → pasek/taśma → karta):
 *  - BUTTONS: rząd 7 ikon 152px (box 64, fokus aqua + label pod ikoną),
 *  - STRIP:   taśma slotów 291.6/486 (środkowy z białą ramką W taśmie) + timeline,
 *  - SNIPPET: fokus na CAŁEJ karcie "TERAZ OGLĄDASZ" (ramka aqua -20px wokół
 *    covera+tytułu+metadanych) ze strzałkami ‹ › do sąsiednich programów.
 *
 * Timeline wspólny (segment aktywny x=389..1531, sąsiednie 0..341 / 1579..1920,
 * kropki na granicach, glow aqua, playhead z bąbelkiem czasu, badge LIVE z linią).
 */

private val V4_TEXT = Color(0xFFEEEEEE)
private val V4_TEXT_DIM = Color(0x66EEEEEE)      // text-disabled 40%
private val V4_WHITE_40 = Color(0x66EEEEEE)
private val V4_AQUA = Color(0xFF5FEDD4)
private val V4_BRAND = Color(0xFF48227C)
private val V4_BTN_BG = Color(0x33EEEEEE)

/** Dane jednej pozycji karty (bieżący/poprzedni/następny program). */
data class V4CardBlock(
    val title: String,
    val meta: List<String>,          // np. ["serial obyczajowy", "12 lat"]
    val coverUrl: String?,
    val isLive: Boolean,             // badge NA ŻYWO
    val isRecording: Boolean,        // marker NAGRYWASZ / kropka rec
    val startWallMs: Long,
    val endWallMs: Long,
)

@Composable
fun DemoPlayerV4Ui(
    isVisible: Boolean,
    zone: PlayerZone,                // BUTTONS / STRIP / SNIPPET
    cardBlocks: List<V4CardBlock>,   // [prev?, current, next?] — karta wg cardIndex
    cardIndex: Int,                  // indeks bieżąco pokazywanego na karcie
    nextBlock: V4CardBlock?,         // wyszarzony tytuł po prawej (zawsze następny)
    channelName: String,
    channelNumber: Int,
    channelLogoUrl: String?,
    buttonsFocusIndex: Int,
    isPaused: Boolean,
    isAtLiveEdge: Boolean,
    recScheduledNext: Boolean,       // "ZLECONO NAGRYWANIE" przy następnym
    // Oś czasu (wirtualna) bieżącego bloku + sąsiadów
    blockStartMs: Long, blockEndMs: Long,
    prevStartMs: Long?, nextEndMs: Long?,
    positionMs: Long,                // pozycja odtwarzania
    cursorMs: Long,                  // kursor scrubu (w STRIP)
    liveEdgeMs: Long,
    antennaStartWallMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    blockTitleFor: ((Long) -> String?)? = null,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    AnimatedVisibility(
        visible = isVisible, enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.fillMaxSize().zIndex(12f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Overlay: gradient od dołu (overlay-scrub z CSS — 741px, stopy 0/30/46/60%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(741))
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x00281443),
                            0.30f to Color(0x80281443),
                            0.46f to Color(0xEB281443),
                            0.60f to Color(0xFF281443)
                        )
                    )
            )
            // Zegar: left 1716, top 60, 32/48 bold
            Text(
                text = formatWall(antennaStartWallMs + liveEdgeMs, withSeconds = false),
                color = V4_TEXT, fontSize = demoSp(32, sy), fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = sx(1716), y = sy(60))
            )

            if (zone == PlayerZone.STRIP) {
                V4ScrubStrip(
                    centerVirtualMs = cursorMs, liveEdgeMs = liveEdgeMs,
                    frames = frames, blockTitleFor = blockTitleFor, sx = sx, sy = sy
                )
            } else {
                V4PlayerLayer(
                    cardBlocks = cardBlocks, cardIndex = cardIndex, nextBlock = nextBlock,
                    cardFocused = zone == PlayerZone.SNIPPET,
                    channelName = channelName, channelNumber = channelNumber,
                    channelLogoUrl = channelLogoUrl,
                    buttonsFocusIndex = if (zone == PlayerZone.BUTTONS) buttonsFocusIndex else -1,
                    isPaused = isPaused, isAtLiveEdge = isAtLiveEdge,
                    recScheduledNext = recScheduledNext, sx = sx, sy = sy
                )
            }

            V4Timeline(
                atPlayer = zone != PlayerZone.STRIP,
                blockStartMs = blockStartMs, blockEndMs = blockEndMs,
                prevStartMs = prevStartMs, nextEndMs = nextEndMs,
                positionMs = positionMs,
                cursorMs = if (zone == PlayerZone.STRIP) cursorMs else null,
                liveEdgeMs = liveEdgeMs, antennaStartWallMs = antennaStartWallMs,
                playheadFocused = zone == PlayerZone.STRIP,
                sx = sx, sy = sy
            )
        }
    }
}

// ═════════════ warstwa PLAYER (kontrolki + karty) ═════════════

@Composable
private fun V4PlayerLayer(
    cardBlocks: List<V4CardBlock>, cardIndex: Int, nextBlock: V4CardBlock?,
    cardFocused: Boolean,
    channelName: String, channelNumber: Int, channelLogoUrl: String?,
    buttonsFocusIndex: Int, isPaused: Boolean, isAtLiveEdge: Boolean,
    recScheduledNext: Boolean,
    sx: (Int) -> Dp, sy: (Int) -> Dp
) {
    val card = cardBlocks.getOrNull(cardIndex) ?: return

    // channel-card: left 105, top 937 (pattern 485 + 452); logo 120, numer h40
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(24)),
        modifier = Modifier.offset(x = sx(105), y = sy(937)).width(sx(172))
    ) {
        Box(
            modifier = Modifier
                .size(sx(120), sy(120))
                .clip(RoundedCornerShape(sx(16)))
                .background(Color(0x1FEEEEEE)),
            contentAlignment = Alignment.Center
        ) {
            if (channelLogoUrl != null) {
                AsyncImage(
                    model = channelLogoUrl, contentDescription = channelName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(sx(16))
                )
            } else {
                Text(
                    text = channelName, color = V4_TEXT, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = demoSp(24, sy), fontWeight = FontWeight.Bold, lineHeight = demoSp(28, sy)
                )
            }
        }
        Box(
            modifier = Modifier
                .height(sy(40)).widthIn(min = sx(64))
                .border(sx(2), V4_WHITE_40, RoundedCornerShape(sx(8)))
                .padding(horizontal = sx(12)),
            contentAlignment = Alignment.Center
        ) {
            Text("$channelNumber", color = V4_TEXT, fontSize = demoSp(24, sy), fontWeight = FontWeight.Medium)
        }
    }

    // watch-prev: left 73, top 675 (485+190) — kółko 64 ze strzałką + tekst 2 linie
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16)),
        modifier = Modifier.offset(x = sx(73), y = sy(675))
    ) {
        Box(
            modifier = Modifier.size(sx(64), sy(64)).clip(CircleShape).background(V4_BRAND),
            contentAlignment = Alignment.Center
        ) {
            Text("◀", color = V4_TEXT, fontSize = demoSp(24, sy))
        }
        Text(
            "Oglądaj\npoprzednie", color = V4_TEXT,
            fontSize = demoSp(24, sy), lineHeight = demoSp(32, sy), fontWeight = FontWeight.Medium
        )
    }

    // info-now: left 389, top 562 (485+77), w1142 — caption + body (cover 280×168 + treść)
    Column(
        verticalArrangement = Arrangement.spacedBy(sy(16)),
        modifier = Modifier.offset(x = sx(389), y = sy(562)).width(sx(1142))
    ) {
        Text("TERAZ OGLĄDASZ", color = V4_TEXT, fontSize = demoSp(20, sy), fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.width(sx(1142)).height(sy(168))) {
            // Ramka fokusa CAŁEJ grupy: inset -20, border 4 aqua, radius 16, tło black 20%
            if (cardFocused) {
                Box(
                    modifier = Modifier
                        .offset(x = -sx(20), y = -sy(20))
                        .width(sx(1142 + 40)).height(sy(168 + 40))
                        .clip(RoundedCornerShape(sx(16)))
                        .background(Color(0x33000000))
                        .border(sx(4), V4_AQUA, RoundedCornerShape(sx(16)))
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(24)),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxSize()
            ) {
                // cover 280×168
                Box(
                    modifier = Modifier
                        .size(sx(280), sy(168))
                        .clip(RoundedCornerShape(sx(8)))
                        .background(Color(0x14EEEEEE))
                ) {
                    if (card.coverUrl != null) {
                        AsyncImage(
                            model = card.coverUrl, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // treść: markery / tytuł 64 / meta
                Column(
                    verticalArrangement = Arrangement.spacedBy(sy(8)),
                    modifier = Modifier.width(sx(838))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(sx(16)),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(sy(32))
                    ) {
                        if (card.isLive) {
                            Box(
                                modifier = Modifier
                                    .height(sy(24)).widthIn(min = sx(88))
                                    .clip(RoundedCornerShape(sx(4)))
                                    .background(V4_TEXT)
                                    .padding(horizontal = sx(8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("NA ŻYWO", color = V4_BRAND, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
                            }
                        }
                        // ODTWÓRZ: białe kółko 32 z ikoną startover 24 (brand)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(8))) {
                            Box(
                                modifier = Modifier.size(sx(32), sy(32)).clip(CircleShape).background(V4_TEXT),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.demo_ic_startover),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(V4_BRAND),
                                    modifier = Modifier.size(sx(24), sy(24))
                                )
                            }
                            Text("ODTWÓRZ", color = V4_TEXT, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
                        }
                        if (card.isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(8))) {
                                Box(
                                    modifier = Modifier.size(sx(32), sy(32)).clip(CircleShape).background(V4_TEXT),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(modifier = Modifier.size(sx(14), sy(14)).clip(CircleShape).background(Color(0xFFE53935)))
                                }
                                Text("NAGRYWASZ", color = V4_TEXT, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = card.title, color = V4_TEXT,
                        fontSize = demoSp(64, sy), fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(sx(838)).height(sy(80))
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(sx(16)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        card.meta.forEachIndexed { i, m ->
                            if (i > 0) Box(modifier = Modifier.size(sx(2), sy(24)).background(V4_WHITE_40))
                            Text(m, color = V4_TEXT, fontSize = demoSp(20, sy), fontWeight = FontWeight.Bold)
                        }
                        // KRRiT: S W N P — kwadraciki 20×20 border 2 radius 4
                        Box(modifier = Modifier.size(sx(2), sy(24)).background(V4_WHITE_40))
                        Row(horizontalArrangement = Arrangement.spacedBy(sx(20))) {
                            listOf("S", "W", "N", "P").forEach { k ->
                                Box(
                                    modifier = Modifier
                                        .size(sx(20), sy(20))
                                        .border(sx(2), V4_TEXT, RoundedCornerShape(sx(4))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(k, color = V4_TEXT, fontSize = demoSp(13, sy), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            // Strzałki ‹ › na coverze — tylko przy fokusie karty; left 2/214, top 56, 56×56
            if (cardFocused) {
                if (cardIndex > 0) V4CoverNav("‹", 2, sx, sy)
                if (cardIndex < cardBlocks.lastIndex) V4CoverNav("›", 214, sx, sy)
            }
        }
    }

    // info-next: left 1579, top 592 (485+107) — markery dim + tytuł dim
    if (nextBlock != null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(sy(16)),
            modifier = Modifier.offset(x = sx(1579), y = sy(592)).width(sx(340)).clipToBounds()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(16)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(sy(32))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(8))) {
                    Box(
                        modifier = Modifier.size(sx(32), sy(32)).clip(CircleShape).background(V4_WHITE_40),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔔", fontSize = demoSp(16, sy))
                    }
                    Text("USTAWIONO PRZYPOMNIENIE", color = V4_TEXT_DIM, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                if (recScheduledNext) {
                    Box(modifier = Modifier.size(sx(14), sy(14)).clip(CircleShape).background(Color(0xFFE53935)))
                }
            }
            Text(
                text = nextBlock.title, color = V4_TEXT_DIM,
                fontSize = demoSp(64, sy), fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Clip,
                modifier = Modifier.height(sy(80))
            )
        }
    }

    // controls: wycentrowane, top 880 (485+395); slot 152, box 64 na (44,36), label top 116
    val controls = v4Controls(isPaused, isAtLiveEdge)
    Row(
        modifier = Modifier
            .offset(y = sy(880))
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
    ) {
        controls.forEachIndexed { i, (iconRes, label) ->
            val focused = i == buttonsFocusIndex
            Box(modifier = Modifier.size(sx(152), sy(152))) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(44), y = sy(36))
                        .size(sx(64), sy(64))
                        .clip(RoundedCornerShape(sx(8)))
                        .background(if (focused) V4_AQUA else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        colorFilter = ColorFilter.tint(if (focused) V4_BRAND else V4_TEXT),
                        modifier = Modifier.size(sx(48), sy(48))
                    )
                }
                if (focused) {
                    Text(
                        text = label, color = V4_AQUA,
                        fontSize = demoSp(24, sy), fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier
                            .offset(x = -sx(44), y = sy(116))
                            .width(sx(240))
                    )
                }
            }
        }
    }
}

@Composable
private fun V4CoverNav(glyph: String, leftPx: Int, sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Box(
        modifier = Modifier
            .offset(x = sx(leftPx), y = sy(56))
            .size(sx(56), sy(56))
            .shadow(sy(10), CircleShape)
            .clip(CircleShape)
            .background(V4_TEXT),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph, color = V4_BRAND, fontSize = demoSp(34, sy), fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(y = -sy(3))
        )
    }
}

/** 7 kontrolek wg prototypu: pauza · od początku · live · rec · opis · EPG · ustawienia */
private fun v4Controls(isPaused: Boolean, isAtLiveEdge: Boolean): List<Pair<Int, String>> = listOf(
    R.drawable.demo_ic_pause to if (isPaused) "Wznów" else "Pauza",
    R.drawable.demo_ic_startover to "Od początku",
    R.drawable.demo_ic_live to if (isAtLiveEdge) "Oglądasz live" else "Wróć na żywo",
    R.drawable.demo_ic_rec to "Nagrywaj",
    R.drawable.demo_ic_info to "Opis",
    R.drawable.demo_ic_epg to "Podgląd programu TV",
    R.drawable.demo_ic_settings to "Napisy i dźwięk",
)

// ═════════════ warstwa SCRUB (taśma slotów) ═════════════

@Composable
private fun V4ScrubStrip(
    centerVirtualMs: Long, liveEdgeMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    blockTitleFor: ((Long) -> String?)?,
    sx: (Int) -> Dp, sy: (Int) -> Dp
) {
    // pattern-scrub: bottom 0 h543 → y od 537; strip top 29 (y=566) h292, gap 20;
    // slot 291.6×175.2 radius 9.6; fokus (środek) 486×292 radius 16 border 4 white
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = sy(566))
            .height(sy(292))
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(20)),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.wrapContentWidth(unbounded = true)
        ) {
            frames.forEachIndexed { index, (offsetMs, bitmap) ->
                val isCenter = index == frames.size / 2
                val v = centerVirtualMs + offsetMs
                val inRange = v in 0..liveEdgeMs
                val w = if (isCenter) 486 else 292
                val h = if (isCenter) 292 else 175
                Box(
                    modifier = Modifier
                        .size(sx(w), sy(h))
                        .then(
                            if (isCenter) Modifier
                                .shadow(sy(20), RoundedCornerShape(sx(16)))
                                .clip(RoundedCornerShape(sx(16)))
                                .background(Color(0x59000000))
                                .border(sx(4), V4_TEXT, RoundedCornerShape(sx(16)))
                            else Modifier
                                .clip(RoundedCornerShape(sx(10)))
                                .background(if (inRange) Color(0x59000000) else Color.Transparent)
                        )
                ) {
                    if (inRange && bitmap != null && !bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
    // Tytuł materiału pod kursorem — pas na y=890 (537+353), font 32/64 bold.
    // Prototyp trzyma tytuł przy lewej krawędzi materiału; dla taśmy krokowej
    // wystarczy tytuł bieżącego materiału przy lewym marginesie 64.
    val title = blockTitleFor?.invoke(centerVirtualMs.coerceIn(0, liveEdgeMs))
    if (title != null) {
        Text(
            text = title, color = V4_TEXT,
            fontSize = demoSp(32, sy), fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = sx(64), y = sy(890)).width(sx(1400))
        )
    }
}

// ═════════════ TIMELINE (wspólny) ═════════════

@Composable
private fun V4Timeline(
    atPlayer: Boolean,
    blockStartMs: Long, blockEndMs: Long,
    prevStartMs: Long?, nextEndMs: Long?,
    positionMs: Long, cursorMs: Long?,
    liveEdgeMs: Long, antennaStartWallMs: Long,
    playheadFocused: Boolean,
    sx: (Int) -> Dp, sy: (Int) -> Dp
) {
    val top = if (atPlayer) 804 else 978
    // Geometria z makiety: aktywny segment 389..1531 (1142), przerwy 48 z granicami
    // w 365 i 1555; sąsiednie segmenty do krawędzi ekranu
    val segL = 389f; val segR = 1531f
    val blockDur = (blockEndMs - blockStartMs).coerceAtLeast(1L)
    fun xFor(ms: Long): Float = when {
        ms < blockStartMs -> {
            val ps = prevStartMs ?: blockStartMs
            val d = (blockStartMs - ps).coerceAtLeast(1L)
            (341f * (ms - ps).coerceAtLeast(0L) / d)
        }
        ms > blockEndMs -> {
            val ne = nextEndMs ?: blockEndMs
            val d = (ne - blockEndMs).coerceAtLeast(1L)
            1579f + (341f * (ms - blockEndMs).coerceAtMost(d) / d)
        }
        else -> segL + (segR - segL) * (ms - blockStartMs) / blockDur
    }

    Box(modifier = Modifier.fillMaxWidth().offset(y = sy(top)).height(sy(138))) {
        // glow aqua pod paskiem — do pozycji odtwarzania
        Box(
            modifier = Modifier
                .offset(y = sy(22))
                .width(sx(xFor(positionMs).toInt()))
                .height(sy(80))
                .background(Brush.verticalGradient(0f to Color(0x995AECD3), 1f to Color.Transparent))
        )
        // segmenty: prev 0..341, aktywny 389..1531, next 1579..1920 (h12, top10)
        @Composable fun seg(l: Int, w: Int, fillTo: Float?) {
            Box(
                modifier = Modifier
                    .offset(x = sx(l), y = sy(10))
                    .size(sx(w), sy(12))
                    .clip(RoundedCornerShape(sx(8)))
                    .background(V4_WHITE_40)
            ) {
                if (fillTo != null && fillTo > l) {
                    Box(
                        modifier = Modifier
                            .width(sx((fillTo - l).toInt().coerceAtMost(w)))
                            .height(sy(12))
                            .clip(RoundedCornerShape(sx(8)))
                            .background(V4_TEXT)
                    )
                }
            }
        }
        val posX = xFor(positionMs)
        if (prevStartMs != null) seg(0, 341, posX)
        seg(389, 1142, posX)
        if (nextEndMs != null) seg(1579, 341, posX)

        // kropki granic + czasy (start/koniec bieżącego bloku) — centra 365 i 1555
        @Composable fun bullet(cx: Int, wallMs: Long, future: Boolean) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sy(4)),
                modifier = Modifier.offset(x = sx(cx - 32), y = sy(8)).width(sx(64))
            ) {
                Box(modifier = Modifier.size(sx(16), sy(16)).clip(CircleShape)
                    .background(if (future) V4_WHITE_40 else V4_TEXT))
                Text(
                    formatWall(wallMs, withSeconds = false),
                    color = if (future) V4_WHITE_40 else V4_TEXT,
                    fontSize = demoSp(24, sy), fontWeight = FontWeight.Medium
                )
            }
        }
        bullet(365, antennaStartWallMs + blockStartMs, future = false)
        bullet(1555, antennaStartWallMs + blockEndMs, future = blockEndMs > liveEdgeMs)

        // znacznik pozycji odtwarzania w scrubie (4×20)
        if (cursorMs != null) {
            Box(
                modifier = Modifier
                    .offset(x = sx(posX.toInt() - 2), y = sy(6))
                    .size(sx(4), sy(20))
                    .clip(RoundedCornerShape(sx(2)))
                    .background(Color(0xD9EEEEEE))
            )
        }
        // LIVE: badge + pionowa linia 4×94 na pozycji live edge
        val liveX = xFor(liveEdgeMs).toInt()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(x = sx(liveX - 32), y = -sy(18)).width(sx(64))
        ) {
            Box(
                modifier = Modifier
                    .size(sx(52), sy(28))
                    .clip(RoundedCornerShape(sx(4)))
                    .background(V4_TEXT),
                contentAlignment = Alignment.Center
            ) {
                Text("LIVE", color = Color(0xFF16101F), fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.size(sx(4), sy(94)).background(V4_TEXT))
        }
        // playhead: kropka 32 + bąbelek czasu (bg brand) pod nią
        val headMs = cursorMs ?: positionMs
        val headX = xFor(headMs).toInt()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sy(8)),
            // Szerokość 160 (nie 64): bąbelek "HH:mm:ss" (~110px) zawijał się
            // w dwie linie przy węższej kolumnie
            modifier = Modifier.offset(x = sx(headX - 80), y = sy(0)).width(sx(160))
        ) {
            Box(
                modifier = Modifier
                    .size(sx(32), sy(32))
                    .shadow(if (playheadFocused) sy(12) else sy(6), CircleShape,
                        ambientColor = if (playheadFocused) V4_AQUA else Color.Black,
                        spotColor = if (playheadFocused) V4_AQUA else Color.Black)
                    .clip(CircleShape)
                    .background(if (playheadFocused) V4_AQUA else V4_TEXT)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(sx(4)))
                    .background(V4_BRAND)
                    .padding(horizontal = sx(8), vertical = sy(4)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    formatWall(antennaStartWallMs + headMs, withSeconds = true),
                    color = V4_TEXT, fontSize = demoSp(24, sy),
                    fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false
                )
            }
        }
    }
}
