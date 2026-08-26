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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AQUA = Color(0xFF5AECD3)

/**
 * Filmstrip 7 miniatur — współdzielony przez overlay przewijania i pasek
 * postępu w warstwie kontrolek (środek 480x270 z aqua borderem, boki 320x180).
 */
@Composable
internal fun DemoFilmstrip(
    centerVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    antennaStartWallMs: Long,
    showTimeLabels: Boolean = true,   // false: czasy pokazuje pasek postępu (design)
    blockTitleFor: ((Long) -> String?)? = null,  // tytuły materiałów nad taśmą (Figma 5530-5395)
    // Tryb POŁĄCZONY (decyzja 2026-07-14, dawne A/B spod klawisza 8): tytuły
    // nad taśmą (Figma 5530-5395) ORAZ kafelek "Przechodzisz do…" na pierwszym
    // slocie nowego materiału (Figma 5530-5267) wyświetlane RAZEM
    blockMetaFor: ((Long) -> String?)? = null,   // metadane materiału (kafelek)
    // Przedział czasowy bloku "HH:mm – HH:mm" dla pozycji wirtualnej — nagłówek
    // kafelka przejścia (Figma 5628-3015: godziny NASTĘPNEGO programu + fifka)
    blockRangeFor: ((Long) -> String?)? = null,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        // Tytuły materiałów (Figma 5530-5395): PRZYPIĘTE do klatki, od której
        // materiał się zaczyna; materiał widoczny od lewej (start poza kadrem)
        // trzyma tytuł przy lewej krawędzi, aż przewiniemy do następnego.
        // Pasmo tytułów siedzi tuż nad MAŁYMI miniaturkami i jest rysowane
        // PRZED taśmą — duża środkowa miniatura (wyższa) przykrywa je z-indexem.
        if (blockTitleFor != null && frames.isNotEmpty()) {
            // Geometria slotów w px designu 1920: taśma wycentrowana, szersza
            // od ekranu (clipToBounds) — lewy slot częściowo poza kadrem
            val sideW = 320; val centerW = 480; val gapW = 10
            val totalW = (frames.size - 1) * sideW + centerW + (frames.size - 1) * gapW
            val lefts = IntArray(frames.size)
            var xAcc = (1920 - totalW) / 2
            for (i in frames.indices) {
                lefts[i] = xAcc
                xAcc += (if (i == frames.size / 2) centerW else sideW) + gapW
            }
            // Grupy kolejnych slotów tego samego materiału (tylko sloty w DVR)
            val groups = mutableListOf<Pair<String, Int>>()   // (tytuł, indeks 1. slotu)
            frames.forEachIndexed { i, (offsetMs, _) ->
                val v = centerVirtualMs + offsetMs
                if (v < 0 || v > liveEdgeVirtualMs) return@forEachIndexed
                val t = blockTitleFor(v) ?: return@forEachIndexed
                if (groups.isEmpty() || groups.last().first != t) groups.add(t to i)
            }
            val edgeMargin = 40
            Box(modifier = Modifier.matchParentSize()) {
                groups.forEachIndexed { gi, (title, firstIdx) ->
                    // Sticky: pierwszy widoczny materiał trzyma się lewej
                    // krawędzi, kolejne przypięte do swojej pierwszej klatki
                    val x = if (gi == 0) maxOf(edgeMargin, lefts[firstIdx])
                        else lefts[firstIdx]
                    val nextX = groups.getOrNull(gi + 1)?.let { lefts[it.second] }
                        ?: (1920 - edgeMargin)
                    val maxW = (minOf(nextX, 1920 - edgeMargin) - x - 32)
                        .coerceAtLeast(120)
                    Text(
                        text = title,
                        color = Color(0xFFEEEEEE),
                        fontSize = demoSp(32, sy),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            // 12px nad górną krawędzią małych miniatur (180 wys.)
                            .offset(x = sx(x), y = -sy(180 + 12))
                            .widthIn(max = sx(maxW))
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.wrapContentWidth(unbounded = true)
        ) {
            frames.forEachIndexed { index, (frameOffsetMs, bitmap) ->
                val isCenter = index == frames.size / 2
                val slotVirtualMs = centerVirtualMs + frameOffsetMs
                val inRange = slotVirtualMs in 0..liveEdgeVirtualMs

                val thumbWidth = if (isCenter) sx(480) else sx(320)
                val thumbHeight = if (isCenter) sy(270) else sy(180)

                if (index > 0) Spacer(modifier = Modifier.width(sx(10)))

                if (!inRange && !isCenter) {
                    // Poza DVR/za live edge: niewidoczny spacer trzyma wyrównanie środka
                    Spacer(modifier = Modifier.width(thumbWidth).height(thumbHeight))
                    return@forEachIndexed
                }

                // Kafelek przejścia: slot jest PIERWSZYM slotem nowego materiału
                // (tytuł inny niż w slocie po lewej), oba w oknie DVR
                val isTransitionTile = blockTitleFor != null && index > 0 &&
                    run {
                        val prevV = centerVirtualMs + frames[index - 1].first
                        val curT = blockTitleFor(slotVirtualMs)
                        val prevT = blockTitleFor(prevV)
                        prevV >= 0 && curT != null && prevT != null && curT != prevT
                    }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showTimeLabels) {
                        Text(
                            text = formatWall(antennaStartWallMs + slotVirtualMs.coerceAtLeast(0), withSeconds = true),
                            color = if (isCenter) AQUA else Color(0x99EEEEEE),
                            fontSize = if (isCenter) demoSp(18, sy) else demoSp(13, sy),
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(sy(4)))
                    }
                    Box(
                        modifier = Modifier
                            .width(thumbWidth)
                            .height(thumbHeight)
                            // Środkowa miniatura: aqua glow (kolorowy cień) wg Figmy
                            .then(
                                if (isCenter) Modifier.shadow(
                                    elevation = sy(16),
                                    shape = RoundedCornerShape(sx(8)),
                                    ambientColor = AQUA,
                                    spotColor = AQUA
                                ) else Modifier
                            )
                            .clip(RoundedCornerShape(sx(8)))
                            .background(Color(0x40000000))
                            .then(
                                // Środkowa miniatura: ramka w kolorze fokusa (aqua)
                                if (isCenter) Modifier.border(3.dp, AQUA, RoundedCornerShape(sx(8)))
                                else Modifier.border(1.dp, Color(0x50EEEEEE), RoundedCornerShape(sx(8)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTransitionTile) {
                            // Kafelek przejścia (Figma 5628-3015): przedział czasowy
                            // następnego programu + fifka ›, pod spodem tytuł i metadane
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xF2352052))
                                    .padding(horizontal = sx(24))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = blockRangeFor?.invoke(slotVirtualMs) ?: "",
                                        color = Color(0xFFEEEEEE),
                                        fontSize = demoSp(if (isCenter) 24 else 17, sy),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(sx(8)))
                                    Image(
                                        painter = androidx.compose.ui.res.painterResource(
                                            id = com.uxellence.tv.v3.R.drawable.demo_ic_chevron_right
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(demoSp(if (isCenter) 24 else 17, sy).value.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(sy(12)))
                                Text(
                                    text = blockTitleFor?.invoke(slotVirtualMs) ?: "",
                                    color = Color(0xFFEEEEEE),
                                    fontSize = demoSp(if (isCenter) 30 else 21, sy),
                                    lineHeight = demoSp(if (isCenter) 38 else 27, sy),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                val meta = blockMetaFor?.invoke(slotVirtualMs)
                                if (!meta.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(sy(8)))
                                    Text(
                                        text = meta,
                                        color = Color(0x99EEEEEE),
                                        fontSize = demoSp(if (isCenter) 20 else 14, sy)
                                    )
                                }
                            }
                        } else if (bitmap != null && !bitmap.isRecycled) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(sx(8)))
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun formatWall(wallMs: Long, withSeconds: Boolean): String {
    val pattern = if (withSeconds) "HH:mm:ss" else "HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(wallMs))
}

/**
 * Font skalowany jak w EpgDayScreen: rozmiar z designu 1920x1080 × współczynnik sy.
 * Surowe .sp na emulatorze TV (~0.5 skali) dawało fonty 2x za duże.
 */
internal fun demoSp(designPx: Int, sy: (Int) -> Dp): androidx.compose.ui.unit.TextUnit =
    (designPx * sy(1).value).sp

/**
 * Taśma scrub v2 — wygląd 1:1 wg prototypu player-scrub (Figma "Nowy player –
 * scrubb preview 2026", makieta z ~/Downloads/player-scrub):
 *  - ciągła taśma małych miniatur (254x143, gap 10) przez CAŁĄ szerokość ekranu,
 *  - nad nią DUŻY podgląd kursora (390x220) w białej ramce, wystaje ponad taśmę,
 *  - tytuł programu pod kursorem wycentrowany POD taśmą.
 * Czasy/kursor/LIVE pokazuje pasek postępu pod spodem (DemoFixedBlockBar) —
 * ten komponent renderuje wyłącznie miniatury i tytuł.
 *
 * Wybór wersji: dev menu pod "0" na demo (DemoPlayerPrefs.seekBarVersion).
 */
@Composable
internal fun DemoScrubTape(
    centerVirtualMs: Long,
    liveEdgeVirtualMs: Long,
    frames: List<Pair<Long, Bitmap?>>,
    blockTitleFor: ((Long) -> String?)? = null,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val slotW = 254; val slotH = 143         // małe miniatury 16:9
    val bigW = 390; val bigH = 220           // podgląd kursora
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(bigH + 16))
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            // Ciągła taśma — wycentrowana na slocie kursora, szersza niż ekran
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(10)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.wrapContentWidth(unbounded = true)
            ) {
                frames.forEachIndexed { index, (frameOffsetMs, bitmap) ->
                    val slotVirtualMs = centerVirtualMs + frameOffsetMs
                    val inRange = slotVirtualMs in 0..liveEdgeVirtualMs
                    Box(
                        modifier = Modifier
                            .width(sx(slotW))
                            .height(sy(slotH))
                            .clip(RoundedCornerShape(sx(6)))
                            .background(Color(0x33000000)),
                        contentAlignment = Alignment.Center
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
            // Duży podgląd kursora — klatka środkowego slotu w białej ramce,
            // przykrywa taśmę (wystaje równo nad i pod małe miniatury)
            val centerBitmap = frames.getOrNull(frames.size / 2)?.second
            Box(
                modifier = Modifier
                    .width(sx(bigW))
                    .height(sy(bigH))
                    .shadow(sy(18), RoundedCornerShape(sx(10)))
                    .clip(RoundedCornerShape(sx(10)))
                    .background(Color(0xFF16101F))
                    .border(sx(3), Color(0xFFF2F2F2), RoundedCornerShape(sx(10)))
            ) {
                if (centerBitmap != null && !centerBitmap.isRecycled) {
                    Image(
                        bitmap = centerBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        // Tytuł programu pod kursorem — wycentrowany pod taśmą (jak w prototypie)
        val title = blockTitleFor?.invoke(centerVirtualMs.coerceIn(0, liveEdgeVirtualMs))
        Spacer(modifier = Modifier.height(sy(20)))
        Text(
            text = title ?: "",
            color = Color(0xFFF5F2FA),
            fontSize = demoSp(36, sy),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
