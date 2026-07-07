package com.uxellence.tv.v3.demolive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.uxellence.tv.v3.epg.ChannelEpgRow
import java.time.Duration
import java.time.Instant

/**
 * MINI-EPG BAR — "player poziom 1: bez kontrolek" wg Figmy 5530-5603.
 * Pasek pojedynczego kanału (tryb single warstwy EPG): karta aktywnego
 * programu (okładka + czasy + markery + tytuł + metadane), następny program
 * wyszarzony (40%), pod spodem segmentowany timeline (segment na program,
 * kropki na granicach) z aqua glow od lewej do pozycji live.
 *
 * Wymiary 1:1 z designu 1920×1080 (skalowanie sx/sy):
 *  - kolumna od y=807; rząd kanału h=184; timeline h=58
 *  - numer kanału 64×40 (border white40), logo/nazwa 184×184, gap 40
 *  - okładka 208×116, tytuł 48/64 ls −0.96 (1 linia, ellipsis, w=684)
 *  - czasy 24/32 ls 0.48; NA ŻYWO 88×24 (białe tło, purpurowy tekst 16)
 *  - metadane 20/28 Bold white80, znaczki KRRiT 20×20 border white80
 *  - segment aktywnego programu: x=320, szerokość 1200; przerwa 16+12+16
 */
private val AQUA = Color(0xFF5AECD3)
private val WHITE = Color(0xFFEEEEEE)
private val WHITE40 = Color(0x66EEEEEE)
private val WHITE80 = Color(0xCCEEEEEE)
private val PURPLE = Color(0xFF48227C)
private val RED = Color(0xFFDD1538)

private const val SEGMENT_X = 320       // start segmentu aktywnego programu
private const val SEGMENT_W = 1200      // szerokość segmentu aktywnego programu
private const val SEG_GAP = 16          // przerwa segment—kropka
private const val BULLET = 12           // średnica kropki na granicy programów

@Composable
internal fun DemoMiniEpgBar(
    row: ChannelEpgRow?,
    programIndex: Int,
    isTunedChannel: Boolean,
    playbackInstant: Instant,
    nowInstant: Instant,
    // Czerwona kropka nagrywania: TYLKO gdy dla programu zlecono nagrywanie
    isRecording: (title: String, startUtc: Instant) -> Boolean = { _, _ -> false },
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    row ?: return
    val program = row.programs.getOrNull(programIndex) ?: return
    val next = row.programs.getOrNull(programIndex + 1)

    val isLiveNow = !nowInstant.isBefore(program.startUtc) && nowInstant.isBefore(program.endUtc)
    val isWatched = isTunedChannel &&
        !playbackInstant.isBefore(program.startUtc) && playbackInstant.isBefore(program.endUtc)
    val durMs = Duration.between(program.startUtc, program.endUtc).toMillis().coerceAtLeast(1L)
    fun fracOf(instant: Instant): Float =
        (Duration.between(program.startUtc, instant).toMillis().toFloat() / durMs)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = sy(807))
            // Nad gradientem warstwy EPG (gradient ma zIndex 1)
            .zIndex(2f)
            .clipToBounds()
    ) {
        // ===== Rząd kanału: numer + logo/nazwa + karta programu + następny =====
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(sy(184))
        ) {
            Spacer(modifier = Modifier.width(sx(32)))
            Box(
                modifier = Modifier
                    .size(sx(64), sy(40))
                    .border(2.dpScaled(sx), WHITE40, RoundedCornerShape(sx(4))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = row.channelNumber.toString(),
                    color = WHITE,
                    fontSize = demoSp(24, sy),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = demoSp(1, sy) * 0.48f
                )
            }
            // Logo kanału 184×184 (obraz gdy jest, inaczej nazwa)
            Box(modifier = Modifier.size(sx(184), sy(184)), contentAlignment = Alignment.Center) {
                if (!row.channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = row.channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(sx(120), sy(120))
                    )
                } else {
                    Text(
                        text = row.channel.name,
                        color = WHITE,
                        fontSize = demoSp(28, sy),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(sx(40)))

            // ===== Karta aktywnego programu (w=908) =====
            Column(modifier = Modifier.width(sx(908))) {
                Text(
                    text = "OGLĄDASZ TERAZ",
                    color = AQUA,
                    fontSize = demoSp(20, sy),
                    lineHeight = demoSp(28, sy),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = demoSp(1, sy) * 0.4f,
                    modifier = Modifier.alpha(if (isWatched) 1f else 0f)
                )
                Spacer(modifier = Modifier.height(sy(4)))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(sy(134))
                ) {
                    // Okładka 208×116
                    Box(
                        modifier = Modifier
                            .size(sx(208), sy(116))
                            .clip(RoundedCornerShape(sx(4)))
                            .background(Color(0x33000000))
                    ) {
                        if (!program.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = program.iconUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(sx(24)))
                    Column {
                        // info_line: czasy + markery (NA ŻYWO / startover / rec)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatWall(program.startUtc.toEpochMilli(), false)} – " +
                                    formatWall(program.endUtc.toEpochMilli(), false),
                                color = WHITE,
                                fontSize = demoSp(24, sy),
                                lineHeight = demoSp(32, sy),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = demoSp(1, sy) * 0.48f
                            )
                            if (isLiveNow) {
                                Spacer(modifier = Modifier.width(sx(16)))
                                Box(
                                    modifier = Modifier
                                        .size(sx(88), sy(24))
                                        .background(WHITE, RoundedCornerShape(sx(4))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "NA ŻYWO",
                                        color = PURPLE,
                                        fontSize = demoSp(16, sy),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = demoSp(1, sy) * 0.32f
                                    )
                                }
                                Spacer(modifier = Modifier.width(sx(12)))
                                // startover: białe kółko 32 z ikoną
                                Box(
                                    modifier = Modifier
                                        .size(sx(32), sy(32))
                                        .background(WHITE, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(
                                            com.uxellence.tv.v3.R.drawable.demo_ic_startover
                                        ),
                                        contentDescription = null,
                                        colorFilter = androidx.compose.ui.graphics.ColorFilter
                                            .tint(PURPLE),
                                        modifier = Modifier.size(sx(24), sy(24))
                                    )
                                }
                                if (isRecording(program.title, program.startUtc)) {
                                    Spacer(modifier = Modifier.width(sx(12)))
                                    // recording dot: czerwone kółko z obwódką — pokazywane
                                    // WYŁĄCZNIE gdy nagrywanie zlecone dla tego programu
                                    Box(
                                        modifier = Modifier
                                            .size(sx(24), sy(24))
                                            .border(2.dpScaled(sx), RED, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(sx(12), sy(12))
                                                .background(RED, CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                        // Tytuł 48/64, 1 linia, ellipsis
                        Text(
                            text = program.title,
                            color = WHITE,
                            fontSize = demoSp(48, sy),
                            lineHeight = demoSp(64, sy),
                            fontWeight = FontWeight.Medium,
                            letterSpacing = -demoSp(1, sy) * 0.96f,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(684))
                        )
                        Spacer(modifier = Modifier.height(sy(8)))
                        // Metadane: kategorie + znaczki KRRiT
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val meta = program.categories.filter { it.isNotBlank() }.take(3)
                            meta.forEachIndexed { i, m ->
                                if (i > 0) MetaDivider(sx, sy)
                                Text(
                                    text = m,
                                    color = WHITE80,
                                    fontSize = demoSp(20, sy),
                                    lineHeight = demoSp(28, sy),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = demoSp(1, sy) * 0.4f
                                )
                            }
                            if (meta.isNotEmpty()) MetaDivider(sx, sy)
                            listOf("S", "W", "N", "P").forEachIndexed { i, letter ->
                                if (i > 0) Spacer(modifier = Modifier.width(sx(20)))
                                Box(
                                    modifier = Modifier
                                        .size(sx(20), sy(20))
                                        .border(
                                            2.dpScaled(sx), WHITE80,
                                            RoundedCornerShape(sx(4))
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter,
                                        color = WHITE80,
                                        fontSize = demoSp(13, sy),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ===== Następny program (40%) =====
            if (next != null) {
                Spacer(modifier = Modifier.width(sx(339 - 224)))
                Column(modifier = Modifier.alpha(0.4f)) {
                    Text(
                        text = "${formatWall(next.startUtc.toEpochMilli(), false)} – " +
                            formatWall(next.endUtc.toEpochMilli(), false),
                        color = WHITE,
                        fontSize = demoSp(24, sy),
                        lineHeight = demoSp(32, sy),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = demoSp(1, sy) * 0.48f
                    )
                    Text(
                        text = next.title,
                        color = WHITE,
                        fontSize = demoSp(48, sy),
                        lineHeight = demoSp(64, sy),
                        fontWeight = FontWeight.Medium,
                        letterSpacing = -demoSp(1, sy) * 0.96f,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false
                    )
                }
            }
        }

        // ===== Timeline h=58: glow + segmenty z kropkami =====
        Box(modifier = Modifier.fillMaxWidth().height(sy(58))) {
            // Pozycja live w px designu (oś segmentu aktywnego programu)
            val liveFrac = fracOf(nowInstant).coerceIn(0f, 1f)
            val liveX = if (!nowInstant.isBefore(program.startUtc)) {
                SEGMENT_X + (liveFrac * SEGMENT_W).toInt()
            } else 0
            // LIVE indicator glow: od lewej krawędzi do pozycji live, h=48,
            // gradient pionowy transparent → aqua 60% (pod paskiem)
            if (liveX > 0) {
                Box(
                    modifier = Modifier
                        .offset(y = sy(10))
                        .size(sx(liveX), sy(48))
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x005AECD3),
                                1f to Color(0x995AECD3)
                            )
                        )
                )
            }
            // Ogon poprzedniego programu: 0..272.8, biały (obejrzany), h=8
            Box(
                modifier = Modifier
                    .offset(x = sx(0), y = sy(2))
                    .size(sx(SEGMENT_X - SEG_GAP * 2 - BULLET), sy(8))
                    .background(
                        WHITE,
                        RoundedCornerShape(topEnd = sx(6), bottomEnd = sx(6))
                    )
            )
            // Kropka na granicy poprzedni|aktywny
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X - SEG_GAP - BULLET), y = sy(0))
                    .size(sx(BULLET), sy(BULLET))
                    .background(WHITE, CircleShape)
            )
            // Segment aktywnego programu (tło white40)
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X), y = sy(2))
                    .size(sx(SEGMENT_W), sy(8))
                    .background(WHITE40, RoundedCornerShape(sx(6)))
            )
            // Obejrzana część (pozycja odtwarzania) — biały wskaźnik
            if (isWatched) {
                val watchedW = (fracOf(playbackInstant).coerceIn(0f, 1f) * SEGMENT_W).toInt()
                if (watchedW > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = sx(SEGMENT_X), y = sy(2))
                            .size(sx(watchedW), sy(8))
                            .background(WHITE, RoundedCornerShape(sx(6)))
                    )
                }
            }
            // Kropka na granicy aktywny|następny
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X + SEGMENT_W + SEG_GAP), y = sy(0))
                    .size(sx(BULLET), sy(BULLET))
                    .background(WHITE, CircleShape)
            )
            // Segment następnego programu — do prawej krawędzi ekranu
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X + SEGMENT_W + SEG_GAP * 2 + BULLET), y = sy(2))
                    .fillMaxWidth()
                    .height(sy(8))
                    .background(WHITE40, RoundedCornerShape(topStart = sx(6), bottomStart = sx(6)))
            )
        }
    }
}

@Composable
private fun MetaDivider(sx: (Int) -> Dp, sy: (Int) -> Dp) {
    Spacer(modifier = Modifier.width(sx(16)))
    Box(
        modifier = Modifier
            .size(sx(2), sy(24))
            .background(WHITE40)
    )
    Spacer(modifier = Modifier.width(sx(16)))
}

/** Border 2px designu przeskalowany przez sx (Dp). */
private fun Int.dpScaled(sx: (Int) -> Dp): Dp = sx(this)
