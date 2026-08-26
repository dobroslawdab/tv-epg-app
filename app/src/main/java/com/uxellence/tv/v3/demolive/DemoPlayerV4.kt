package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
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
    // Poziom paska (STRIP) bez taśmy: taśma miniatur pojawia się DOPIERO po
    // pierwszym LEFT/RIGHT (jak w prototypie); wcześniej fokus na playheadzie
    stripEngaged: Boolean,
    cardBlocks: List<V4CardBlock>,   // [prev?, current, next?] — sloty karuzeli
    cardIndex: Int,                  // slot pod ramką fokusa (karuzela dojeżdża do niego)
    watchedIndex: Int,               // slot AKTUALNIE OGLĄDANEGO programu ("TERAZ OGLĄDASZ")
    channelName: String,
    channelNumber: Int,
    channelLogoUrl: String?,
    buttonsFocusIndex: Int,
    isPaused: Boolean,
    isAtLiveEdge: Boolean,
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

            val showScrubTape = zone == PlayerZone.STRIP && stripEngaged
            if (showScrubTape) {
                V4ScrubStrip(
                    centerVirtualMs = cursorMs, liveEdgeMs = liveEdgeMs,
                    frames = frames, blockTitleFor = blockTitleFor, sx = sx, sy = sy
                )
            } else {
                V4PlayerLayer(
                    cardBlocks = cardBlocks, cardIndex = cardIndex,
                    watchedIndex = watchedIndex,
                    cardFocused = zone == PlayerZone.SNIPPET,
                    channelName = channelName, channelNumber = channelNumber,
                    channelLogoUrl = channelLogoUrl,
                    buttonsFocusIndex = if (zone == PlayerZone.BUTTONS) buttonsFocusIndex else -1,
                    isPaused = isPaused, isAtLiveEdge = isAtLiveEdge,
                    sx = sx, sy = sy
                )
            }

            V4Timeline(
                atPlayer = !showScrubTape,
                blockStartMs = blockStartMs, blockEndMs = blockEndMs,
                prevStartMs = prevStartMs, nextEndMs = nextEndMs,
                positionMs = positionMs,
                cursorMs = if (zone == PlayerZone.STRIP) cursorMs else null,
                liveEdgeMs = liveEdgeMs, antennaStartWallMs = antennaStartWallMs,
                playheadFocused = zone == PlayerZone.STRIP,   // fokus na kropce także PRZED taśmą
                sx = sx, sy = sy
            )
        }
    }
}

// ═════════════ warstwa PLAYER (kontrolki + karty) ═════════════

@Composable
private fun V4PlayerLayer(
    cardBlocks: List<V4CardBlock>, cardIndex: Int, watchedIndex: Int,
    cardFocused: Boolean,
    channelName: String, channelNumber: Int, channelLogoUrl: String?,
    buttonsFocusIndex: Int, isPaused: Boolean, isAtLiveEdge: Boolean,
    sx: (Int) -> Dp, sy: (Int) -> Dp
) {
    if (cardBlocks.isEmpty()) return

    // channel-card: left 105, top 452 — w CSS siedzi bezpośrednio w .layer-player
    // (inset 0), NIE w .pattern-player; NAD karuzelą kart (uwaga usera 2026-08-26)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(24)),
        modifier = Modifier.offset(x = sx(105), y = sy(452)).width(sx(172)).zIndex(3f)
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

    // watch-prev: left 73, top 675 (485+190) — WARSTWA NAD karuzelą kart
    // (karta poprzedniego programu przejeżdża POD przyciskiem)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16)),
        modifier = Modifier.offset(x = sx(73), y = sy(675)).zIndex(3f)
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

    // ===== KARUZELA PROGRAMÓW (info-now + sąsiedzi) =====
    // Sloty co 1190 px (karta 1142 + przerwa 48): fokusowany slot na x=389,
    // następny wystaje z prawej od x=1579 (CSS .info-next left 1579 = 389+1190),
    // poprzedni chowa się z lewej POD "Oglądaj poprzednie". LEFT/RIGHT przewija
    // karuzelę animacją — ramka fokusa STOI na x=389, znika na czas przejazdu
    // i wraca na programie, który wjechał w jej miejsce (uwaga usera 2026-08-26).
    val slotPitch = 1190
    val animIndex by animateFloatAsState(
        targetValue = cardIndex.toFloat(),
        animationSpec = tween(320), label = "v4_carousel"
    )
    val isSettled = kotlin.math.abs(animIndex - cardIndex) < 0.01f
    Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
        cardBlocks.forEachIndexed { idx, blk ->
            val slotX = (389 + (idx - animIndex) * slotPitch).toInt()
            if (slotX > -1200 && slotX < 1990) {
                V4ProgramCard(
                    block = blk,
                    // Pełne kolory tylko w slocie fokusa; sąsiedzi wyszarzeni
                    dimmed = idx != cardIndex,
                    caption = when {
                        idx != cardIndex -> null
                        idx == watchedIndex -> "TERAZ OGLĄDASZ"
                        else -> formatWall(blk.startWallMs, false) + " – " +
                            formatWall(blk.endWallMs, false)
                    },
                    isWatched = idx == watchedIndex,
                    modifier = Modifier.offset(x = sx(slotX), y = sy(562)),
                    sx = sx, sy = sy
                )
            }
        }
        // Ramka fokusa CAŁEJ grupy (cover+markery+tytuł+meta): stała pozycja
        // nad slotem x=389 (body y=606: 562 + caption 28 + gap 16), inset -20
        if (cardFocused && isSettled) {
            Box(
                modifier = Modifier
                    .offset(x = sx(389 - 20), y = sy(606 - 20))
                    .width(sx(1142 + 40)).height(sy(168 + 40))
                    .clip(RoundedCornerShape(sx(16)))
                    .background(Color(0x33000000))
                    .border(sx(4), V4_AQUA, RoundedCornerShape(sx(16)))
            )
            // Strzałki ‹ › na coverze slotu fokusa (cover: x=389..669, body y=606)
            if (cardIndex > 0) V4CoverNav("‹", 389 + 2, 606 + 56, sx, sy)
            if (cardIndex < cardBlocks.lastIndex) V4CoverNav("›", 389 + 214, 606 + 56, sx, sy)
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
                    // Wyśrodkowanie do IKONY (środek boxa = 76px slotu), tekst może
                    // wystawać poza slot symetrycznie (unbounded) — bez ucinania
                    Box(
                        modifier = Modifier.offset(y = sy(116)).width(sx(152)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label, color = V4_AQUA,
                            fontSize = demoSp(24, sy), fontWeight = FontWeight.Bold,
                            maxLines = 1, softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Karta programu w karuzeli: caption (TERAZ OGLĄDASZ / godziny) + body:
 * cover 280×168, markery, tytuł 64, metadane + KRRiT. Poza slotem fokusa
 * wyszarzona (jak .info-next w CSS prototypu).
 */
@Composable
private fun V4ProgramCard(
    block: V4CardBlock,
    dimmed: Boolean,
    caption: String?,
    isWatched: Boolean,
    modifier: Modifier,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val text = if (dimmed) V4_TEXT_DIM else V4_TEXT
    Column(verticalArrangement = Arrangement.spacedBy(sy(16)), modifier = modifier.width(sx(1142))) {
        Text(
            text = caption ?: " ",
            color = text, fontSize = demoSp(20, sy), fontWeight = FontWeight.Bold,
            modifier = Modifier.height(sy(28))
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(24)),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.width(sx(1142)).height(sy(168))
        ) {
            // Miniaturka TYLKO na karcie zafokusowanej — sąsiednie karty
            // (np. następny program wchodzący pod prawą krawędź) bez covera,
            // jak .info-next w prototypie (uwaga usera 2026-08-26)
            if (!dimmed) Box(
                modifier = Modifier
                    .size(sx(280), sy(168))
                    .clip(RoundedCornerShape(sx(8)))
                    .background(Color(0x14EEEEEE))
            ) {
                if (block.coverUrl != null) {
                    AsyncImage(
                        model = block.coverUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(sy(8)), modifier = Modifier.width(sx(838))) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(16)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(sy(32))
                ) {
                    if (isWatched && block.isLive) {
                        Box(
                            modifier = Modifier
                                .height(sy(24)).widthIn(min = sx(88))
                                .clip(RoundedCornerShape(sx(4)))
                                .background(if (dimmed) V4_WHITE_40 else V4_TEXT)
                                .padding(horizontal = sx(8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("NA ŻYWO", color = V4_BRAND, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isWatched) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(8))) {
                            Box(
                                modifier = Modifier.size(sx(32), sy(32)).clip(CircleShape)
                                    .background(if (dimmed) V4_WHITE_40 else V4_TEXT),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.demo_ic_startover),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(V4_BRAND),
                                    modifier = Modifier.size(sx(24), sy(24))
                                )
                            }
                            Text("ODTWÓRZ", color = text, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
                        }
                    } else if (block.startWallMs > System.currentTimeMillis()) {
                        // Program przyszły: marker przypomnienia (dim, jak .info-next)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(8))) {
                            Box(
                                modifier = Modifier.size(sx(32), sy(32)).clip(CircleShape).background(V4_WHITE_40),
                                contentAlignment = Alignment.Center
                            ) { Text("🔔", fontSize = demoSp(16, sy)) }
                            Text("USTAWIONO PRZYPOMNIENIE", color = text, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    if (block.isRecording) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(8))) {
                            Box(
                                modifier = Modifier.size(sx(32), sy(32)).clip(CircleShape)
                                    .background(if (dimmed) V4_WHITE_40 else V4_TEXT),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(sx(14), sy(14)).clip(CircleShape).background(Color(0xFFE53935)))
                            }
                            Text(if (isWatched) "NAGRYWASZ" else "ZLECONO NAGRYWANIE", color = text, fontSize = demoSp(16, sy), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = block.title, color = text,
                    fontSize = demoSp(64, sy), fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(sx(838)).height(sy(80))
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(16)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    block.meta.forEachIndexed { i, m ->
                        if (i > 0) Box(modifier = Modifier.size(sx(2), sy(24)).background(V4_WHITE_40))
                        Text(m, color = text, fontSize = demoSp(20, sy), fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.size(sx(2), sy(24)).background(V4_WHITE_40))
                    Row(horizontalArrangement = Arrangement.spacedBy(sx(20))) {
                        listOf("S", "W", "N", "P").forEach { k ->
                            Box(
                                modifier = Modifier
                                    .size(sx(20), sy(20))
                                    .border(sx(2), text, RoundedCornerShape(sx(4))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(k, color = text, fontSize = demoSp(13, sy), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V4CoverNav(glyph: String, leftPx: Int, topPx: Int, sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Box(
        modifier = Modifier
            .offset(x = sx(leftPx), y = sy(topPx))
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
    // Tytuł materiału pod kursorem — WYCENTROWANY pod dużą miniaturką
    // (uwaga usera 2026-08-26: nie przy lewej krawędzi), pas na y=890 (537+353)
    val title = blockTitleFor?.invoke(centerVirtualMs.coerceIn(0, liveEdgeMs))
    if (title != null) {
        Box(modifier = Modifier.fillMaxWidth().offset(y = sy(890))) {
            Text(
                text = title, color = V4_TEXT,
                fontSize = demoSp(32, sy), fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).widthIn(max = sx(1400))
            )
        }
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
        // glow aqua pod paskiem — ZAWSZE do znacznika LIVE (uwaga usera 2026-08-26)
        Box(
            modifier = Modifier
                .offset(y = sy(22))
                .width(sx(xFor(liveEdgeMs).toInt()))
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
