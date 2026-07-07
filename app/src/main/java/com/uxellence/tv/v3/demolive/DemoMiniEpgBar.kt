package com.uxellence.tv.v3.demolive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.uxellence.tv.v3.epg.ChannelEpgRow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * MINI-EPG — "player poziom 1" wg Figmy 5530-5603 (pasek 1 kanału)
 * i 5530-5949 (rozwinięte mini-EPG, 3 kanały).
 *
 * Wspólny budulec: [DemoMiniEpgChannelRow] — karta aktywnego programu
 * (okładka + czasy + markery + tytuł [+ metadane gdy fokus]), następny
 * program wyszarzony, pod spodem segmentowany timeline (segment na program,
 * kropki na granicach). Wiersz fokusowany dostaje eyebrow, aqua ramkę
 * okładki, metadane i aqua glow do pozycji live; pozostałe są przygaszone
 * i mają cieńszy timeline.
 *
 * Wymiary 1:1 z designu 1920×1080 (skalowanie sx/sy):
 *  - numer kanału 64×40 (border white40), logo/nazwa 184 szer., gap 40
 *  - okładka 208×116, tytuł 48/64 ls −0.96 (1 linia), czasy 24/32
 *  - NA ŻYWO 88×24 (białe tło, purpurowy tekst 16), startover 32, rec 24
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

/** Pasek pojedynczego kanału (tryb single warstwy EPG) — Figma 5530-5603. */
@Composable
internal fun DemoMiniEpgBar(
    row: ChannelEpgRow?,
    programIndex: Int,
    isTunedChannel: Boolean,
    playbackInstant: Instant,
    nowInstant: Instant,
    isRecording: (title: String, startUtc: Instant) -> Boolean = { _, _ -> false },
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    row ?: return
    val program = row.programs.getOrNull(programIndex) ?: return
    val isWatched = isTunedChannel &&
        !playbackInstant.isBefore(program.startUtc) && playbackInstant.isBefore(program.endUtc)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = sy(807))
            // Nad gradientem warstwy EPG (gradient ma zIndex 1)
            .zIndex(2f)
    ) {
        DemoMiniEpgChannelRow(
            row = row,
            programIndex = programIndex,
            focused = true,
            eyebrow = if (isWatched) "OGLĄDASZ TERAZ" else null,
            isTunedChannel = isTunedChannel,
            playbackInstant = playbackInstant,
            nowInstant = nowInstant,
            isRecording = isRecording,
            sx = sx, sy = sy
        )
    }
}

/**
 * Rozwinięte mini-EPG (3 kanały) — Figma 5530-5949. Wiersz fokusowany
 * w środku z eyebrow = data programu (np. "PONIEDZIAŁEK, 11.10"),
 * sąsiednie kanały przygaszone nad i pod nim.
 */
@Composable
internal fun DemoMiniEpgExpanded(
    rows: List<ChannelEpgRow>,
    focusedChannelIndex: Int,
    focusedProgramIndexFor: (Int) -> Int,
    focusedTime: Instant,
    tunedChannelIndex: Int,
    playbackInstant: Instant,
    nowInstant: Instant,
    isRecording: (title: String, startUtc: Instant) -> Boolean = { _, _ -> false },
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Indeks programu w wierszu niefokusowanym: TIME SYNC przez zawieranie
    // focusedTime (jak kafelkowa warstwa), fallback na bieżący
    fun programIdxFor(channelIdx: Int): Int {
        if (channelIdx == focusedChannelIndex) return focusedProgramIndexFor(channelIdx)
        val r = rows.getOrNull(channelIdx) ?: return 0
        val match = r.programs.indexOfFirst { p ->
            !focusedTime.isBefore(p.startUtc) && focusedTime.isBefore(p.endUtc)
        }
        return if (match >= 0) match else r.currentProgramIndex
    }

    // PRZEWIJANIE GORA/DOL: przy zmianie kanalu caly blok 3 wierszy
    // przesuwa sie pionowo (nowy zestaw wjezdza z kierunku nawigacji),
    // jak dawne animateScrollToItem na LazyColumn
    AnimatedContent(
        targetState = focusedChannelIndex,
        transitionSpec = {
            val dir = if (targetState >= initialState) 1 else -1
            (slideInVertically(tween(250)) { h -> dir * h / 3 } +
                fadeIn(tween(200))).togetherWith(
                slideOutVertically(tween(250)) { h -> -dir * h / 3 } +
                    fadeOut(tween(150))
            )
        },
        label = "miniepg_channel_slide",
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = sy(563))
            .zIndex(2f)
    ) { chIdx ->
        // Eyebrow fokusowanego wiersza: dzien tygodnia + data programu (Figma)
        val eyebrow = rows.getOrNull(chIdx)
            ?.programs?.getOrNull(focusedProgramIndexFor(chIdx))?.let { p ->
                val date = p.startUtc.atZone(ZoneId.systemDefault())
                val day = date.dayOfWeek.getDisplayName(
                    java.time.format.TextStyle.FULL, Locale("pl")
                ).uppercase(Locale("pl"))
                "$day, ${date.dayOfMonth}.${"%02d".format(date.monthValue)}"
            }
        Column(modifier = Modifier.fillMaxWidth()) {
            for (idx in (chIdx - 1)..(chIdx + 1)) {
                val r = rows.getOrNull(idx)
                if (r == null) {
                    // Brak kanalu (skraj listy): pusty slot trzyma fokusowany w srodku
                    Spacer(modifier = Modifier.height(sy(146)))
                    continue
                }
                DemoMiniEpgChannelRow(
                    row = r,
                    programIndex = programIdxFor(idx),
                    focused = idx == chIdx,
                    eyebrow = if (idx == chIdx) eyebrow else null,
                    isTunedChannel = idx == tunedChannelIndex,
                    playbackInstant = playbackInstant,
                    nowInstant = nowInstant,
                    isRecording = isRecording,
                    nextBlockX = 1276,
                    sx = sx, sy = sy
                )
                // Po fokusowanym +20: dolny (3.) kanal odsuniety od fokusa
                // i wypchniety glebiej pod dolna krawedz ekranu
                Spacer(modifier = Modifier.height(sy(if (idx == chIdx) 20 else 32)))
            }
        }
    }
}

/** Jeden wiersz kanału mini-EPG: karta programu + następny + timeline. */
@Composable
internal fun DemoMiniEpgChannelRow(
    row: ChannelEpgRow,
    programIndex: Int,
    focused: Boolean,
    eyebrow: String?,
    isTunedChannel: Boolean,
    playbackInstant: Instant,
    nowInstant: Instant,
    isRecording: (title: String, startUtc: Instant) -> Boolean,
    // X bloku nastepnego programu (design px): pasek 1 kanalu = 1567 (Figma
    // 5530-5603, next uciety prawa krawedzia), rozwiniete 3 kanaly = 1277
    // (Figma 5530-5949, next w calosci widoczny). Segment aktywnego programu
    // konczy sie przerwa+kropka tuz przed nim.
    nextBlockX: Int = 1567,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val program = row.programs.getOrNull(programIndex) ?: return
    val next = row.programs.getOrNull(programIndex + 1)

    val isLiveNow = !nowInstant.isBefore(program.startUtc) && nowInstant.isBefore(program.endUtc)
    val isWatched = isTunedChannel &&
        !playbackInstant.isBefore(program.startUtc) && playbackInstant.isBefore(program.endUtc)
    val durMs = Duration.between(program.startUtc, program.endUtc).toMillis().coerceAtLeast(1L)
    fun fracOf(instant: Instant): Float =
        (Duration.between(program.startUtc, instant).toMillis().toFloat() / durMs)

    val contentAlpha = if (focused) 1f else 0.5f
    val bodyH = 134   // Figma: body 134 na wszystkich wierszach

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.alpha(contentAlpha)
        ) {
            Spacer(modifier = Modifier.width(sx(32)))
            Box(
                modifier = Modifier
                    .size(sx(64), sy(40))
                    .border(sx(2), WHITE40, RoundedCornerShape(sx(4))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = row.channelNumber.toString(),
                    color = WHITE,
                    fontSize = demoSp(24, sy),
                    fontWeight = FontWeight.Medium
                )
            }
            // Logo kanału / nazwa (184 szer.)
            Box(
                modifier = Modifier.size(sx(184), sy(120)),
                contentAlignment = Alignment.Center
            ) {
                if (!row.channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = row.channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(sx(110), sy(110))
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

            // ===== Karta programu + nastepny: EFEKT PRZEWIJANIA TASMY =====
            // Jak w dawnej LazyRow: przy LEWO/PRAWO karta zjezdza w bok,
            // a nowa wjezdza z kierunku nawigacji (slide + fade)
            AnimatedContent(
                targetState = programIndex,
                transitionSpec = {
                    val dir = if (targetState >= initialState) 1 else -1
                    (slideInHorizontally(tween(250)) { w -> dir * w / 2 } +
                        fadeIn(tween(200))).togetherWith(
                        slideOutHorizontally(tween(250)) { w -> -dir * w / 2 } +
                            fadeOut(tween(150))
                    )
                },
                label = "miniepg_program_slide"
            ) { animIdx ->
                val program = row.programs.getOrNull(animIdx)
                val next = row.programs.getOrNull(animIdx + 1)
                val isLiveNow = program != null &&
                    !nowInstant.isBefore(program.startUtc) &&
                    nowInstant.isBefore(program.endUtc)
                if (program != null) Row(verticalAlignment = Alignment.CenterVertically) {
            // ===== Karta aktywnego programu (w=908) =====
                Column(modifier = Modifier.width(sx(908))) {
                    if (focused) {
                        Text(
                            text = eyebrow ?: "",
                            color = AQUA,
                            fontSize = demoSp(20, sy),
                            lineHeight = demoSp(28, sy),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.alpha(if (eyebrow != null) 1f else 0f)
                        )
                        Spacer(modifier = Modifier.height(sy(4)))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(sy(bodyH))
                    ) {
                        // Okładka 208×116 (fokus: aqua ramka jak w Figmie 5530-5949)
                        Box(
                            modifier = Modifier
                                .size(sx(208), sy(116))
                                .clip(RoundedCornerShape(sx(4)))
                                .background(Color(0x33000000))
                                .then(
                                    if (focused) Modifier.border(
                                        sx(6), AQUA, RoundedCornerShape(sx(4))
                                    ) else Modifier
                                )
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
                            // info_line: czasy + markery
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${formatWall(program.startUtc.toEpochMilli(), false)} – " +
                                        formatWall(program.endUtc.toEpochMilli(), false),
                                    color = WHITE,
                                    fontSize = demoSp(24, sy),
                                    lineHeight = demoSp(32, sy),
                                    fontWeight = FontWeight.Medium
                                )
                                if (isLiveNow && focused) {
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
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (isLiveNow) {
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
                                }
                                if (isRecording(program.title, program.startUtc)) {
                                    Spacer(modifier = Modifier.width(sx(12)))
                                    // recording dot: WYŁĄCZNIE gdy zlecono nagrywanie
                                    Box(
                                        modifier = Modifier
                                            .size(sx(24), sy(24))
                                            .border(sx(2), RED, CircleShape),
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
                            // Tytuł 48/64, 1 linia
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
                            if (focused) {
                                Spacer(modifier = Modifier.height(sy(8)))
                                // Metadane + znaczki KRRiT (tylko wiersz fokusowany)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val meta = program.categories.filter { it.isNotBlank() }.take(3)
                                    meta.forEachIndexed { i, m ->
                                        if (i > 0) MetaDivider(sx, sy)
                                        Text(
                                            text = m,
                                            color = WHITE80,
                                            fontSize = demoSp(20, sy),
                                            lineHeight = demoSp(28, sy),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (meta.isNotEmpty()) MetaDivider(sx, sy)
                                    listOf("S", "W", "N", "P").forEachIndexed { i, letter ->
                                        if (i > 0) Spacer(modifier = Modifier.width(sx(20)))
                                        Box(
                                            modifier = Modifier
                                                .size(sx(20), sy(20))
                                                .border(sx(2), WHITE80, RoundedCornerShape(sx(4))),
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
                }

                // ===== Nastepny program (40%): x = nextBlockX, POKRYWA sie ze
                // swoim segmentem na timeline =====
                if (next != null) {
                    Spacer(modifier = Modifier.width(sx((nextBlockX - 1228).coerceAtLeast(16))))
                    Column(modifier = Modifier.alpha(0.4f)) {
                        Text(
                            text = "${formatWall(next.startUtc.toEpochMilli(), false)} – " +
                                formatWall(next.endUtc.toEpochMilli(), false),
                            color = WHITE,
                            fontSize = demoSp(24, sy),
                            lineHeight = demoSp(32, sy),
                            fontWeight = FontWeight.Medium
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
            }
        }

        // ===== Timeline: box ma wysokosc SAMEGO paska (12 px) — glow
        // rysuje sie POZA layoutem (bleed w dol, bez clipa) i chowa sie POD
        // kolejny wiersz Column (rysowany pozniej), wiec NIE rozpycha
        // odstepow miedzy kanalami =====
        // Figma 5530-5949: pasek h=8 i kropki 12 na WSZYSTKICH kanalach
        // (niefokusowane przygasza tylko opacity wiersza)
        val barH = 8
        val bulletD = BULLET
        val segW = nextBlockX - SEG_GAP * 2 - BULLET - SEGMENT_X
        Box(modifier = Modifier.fillMaxWidth().height(sy(BULLET))) {
            val liveFrac = fracOf(nowInstant).coerceIn(0f, 1f)
            val liveX = if (!nowInstant.isBefore(program.startUtc)) {
                SEGMENT_X + (liveFrac * segW).toInt()
            } else 0
            // LIVE glow tylko na wierszu fokusowanym: aqua przy pasku, gasnie w dol
            if (focused && liveX > 0) {
                Box(
                    modifier = Modifier
                        .offset(y = sy(10))
                        // PULAPKA constraints: rodzic (box paska, h=12) narzuca
                        // maxHeight i size(48) zostalby ZMIERZONY jako 12 px —
                        // unbounded pozwala glow miec pelne 48 px z Figmy
                        // (bleed w dol poza box, align Top)
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .size(sx(liveX), sy(48))
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x995AECD3),
                                1f to Color(0x005AECD3)
                            )
                        )
                )
            }
            // Ogon poprzedniego programu (obejrzany — bialy)
            Box(
                modifier = Modifier
                    .offset(x = sx(0), y = sy((BULLET - barH) / 2))
                    .size(sx(SEGMENT_X - SEG_GAP * 2 - BULLET), sy(barH))
                    .background(WHITE, RoundedCornerShape(topEnd = sx(6), bottomEnd = sx(6)))
            )
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X - SEG_GAP - BULLET), y = sy((BULLET - bulletD) / 2))
                    .size(sx(bulletD), sy(bulletD))
                    .background(WHITE, CircleShape)
            )
            // Segment aktywnego programu
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X), y = sy((BULLET - barH) / 2))
                    .size(sx(segW), sy(barH))
                    .background(WHITE40, RoundedCornerShape(sx(6)))
            )
            // Bialy wskaznik postepu: pozycja odtwarzania gdy kanal ogladany,
            // inaczej postep LIVE programu (bialy pasek na wskazanym kanale)
            val progressFrac = when {
                isWatched -> fracOf(playbackInstant).coerceIn(0f, 1f)
                focused && isLiveNow -> liveFrac
                else -> 0f
            }
            val progressW = (progressFrac * segW).toInt()
            if (progressW > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(SEGMENT_X), y = sy((BULLET - barH) / 2))
                        .size(sx(progressW), sy(barH))
                        .background(WHITE, RoundedCornerShape(sx(6)))
                )
            }
            Box(
                modifier = Modifier
                    .offset(x = sx(SEGMENT_X + segW + SEG_GAP), y = sy((BULLET - bulletD) / 2))
                    .size(sx(bulletD), sy(bulletD))
                    .background(WHITE, CircleShape)
            )
            // Segment nastepnego programu — od nextBlockX do prawej krawedzi
            Box(
                modifier = Modifier
                    .offset(x = sx(nextBlockX), y = sy((BULLET - barH) / 2))
                    .fillMaxWidth()
                    .height(sy(barH))
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
