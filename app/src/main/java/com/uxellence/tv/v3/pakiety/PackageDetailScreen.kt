package com.uxellence.tv.v3.pakiety

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Detal pakietu — Figma 4092-26732.
 *
 * Wejścia z różnych miejsc (kafle sekcji PAKIETY itd.); cena zależy od OFERTY:
 *  - oferta punktowa (CandyBar) → cena w punktach ("15 pkt / cykl")
 *  - oferta pakietowa → cena w złotych ("15 zł / mies.", makieta: ta sama liczba)
 * Zmiana względem Figmy (decyzja 2026-08-04): JEDEN przycisk z CENĄ NA przycisku
 * ("Aktywuj za …") — bez ceny obok kafla z logo i bez rzędu przycisków.
 *
 * KEY HANDLER: PackageDetail
 * Scope: OK na przycisku = aktywacja (zaślepka), DOWN/UP przycisk ⇄ rząd kanałów,
 *        LEFT/RIGHT przewija kanały, BACK = wyjście (BackHandler — dwufazowy bug)
 */
@Composable
fun PackageDetailScreen(
    paket: PaketDom,
    isPointsOffer: Boolean,
    onActivate: () -> Unit,
    onBackPressed: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val bgColor = Color(0xFF281443)
    val tileColor = Color(0xFF1D1030)

    // Cena wg oferty. pointsPerCycle z danych to np. "15 pkt / cykl" albo "15";
    // dla oferty pakietowej makieta pokazuje tę samą liczbę w złotych.
    val priceLabel = remember(paket, isPointsOffer) {
        val digits = paket.pointsPerCycle.takeWhile { it.isDigit() }.ifBlank { "15" }
        if (isPointsOffer) {
            if (paket.pointsPerCycle.contains("pkt")) paket.pointsPerCycle
            else "$digits pkt / cykl"
        } else {
            "$digits zł / mies."
        }
    }

    val buttonFocus = remember { FocusRequester() }
    var isButtonFocused by remember { mutableStateOf(false) }
    // Strefa fokusa: przycisk (false) / rząd kanałów (true)
    var channelsZoneFocused by remember { mutableStateOf(false) }
    var channelIndex by remember { mutableStateOf(0) }
    val channelsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) { onBackPressed() }

    LaunchedEffect(Unit) {
        try { buttonFocus.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)   // solid #281443 — te same kolory co MovieDetail
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent event.key != Key.Back
                }
                when (event.key) {
                    Key.Back -> false   // BackHandler (dispatcher) — nie konsumuj tu
                    Key.DirectionDown -> {
                        if (!channelsZoneFocused && paket.channels.isNotEmpty()) {
                            channelsZoneFocused = true
                        }
                        true
                    }
                    Key.DirectionUp -> {
                        if (channelsZoneFocused) {
                            channelsZoneFocused = false
                            try { buttonFocus.requestFocus() } catch (_: Exception) {}
                        }
                        true
                    }
                    Key.DirectionLeft -> {
                        if (channelsZoneFocused && channelIndex > 0) {
                            channelIndex--
                            scope.launch { channelsListState.animateScrollToItem(channelIndex) }
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (channelsZoneFocused && channelIndex < paket.channels.lastIndex) {
                            channelIndex++
                            scope.launch { channelsListState.animateScrollToItem(channelIndex) }
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (!channelsZoneFocused) onActivate()
                        true
                    }
                    else -> false
                }
            }
    ) {
        // ===== HERO: ilustracja pakietu (prawa góra) z miękkim przejściem w tło =====
        val heroUrl = paket.imageHd.ifBlank { paket.image }
        if (heroUrl.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(sx(1040))
                    .height(sy(640))
            ) {
                AsyncImage(
                    model = heroUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Fade w lewo (pod treść) i w dół (pod listę kanałów)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(bgColor, Color.Transparent),
                                endX = 600f
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, bgColor),
                                startY = 320f
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Lewy margines jak na ekranie Wypożycz (PurchaseScreen: start = 234)
                .padding(start = sx(234), top = sy(96), end = sx(80))
        ) {
            // ===== Kafel z logo pakietu (bez ceny obok — cena jest NA przycisku) =====
            Box(
                modifier = Modifier
                    .size(sx(366), sy(200))
                    .background(tileColor, RoundedCornerShape(sx(16))),
                contentAlignment = Alignment.Center
            ) {
                val logoUrl = paket.logoHd.ifBlank { paket.logo }
                if (logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = paket.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(sx(32))
                    )
                } else {
                    Text(
                        text = paket.name,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(30).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(sy(40)))

            // ===== JEDEN przycisk z ceną: "Aktywuj za 15 pkt / cykl" =====
            Box(
                modifier = Modifier
                    .focusRequester(buttonFocus)
                    .onFocusChanged { isButtonFocused = it.isFocused }
                    .focusable()
                    .background(
                        // Kolory 1:1 jak ActionButton w MovieDetail (Wideo/Kino Play)
                        color = if (isButtonFocused && !channelsZoneFocused)
                            Color(0xFF5FEDD4) else Color(0xFFEEEEEE).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(sx(12))
                    )
                    .padding(horizontal = sx(40), vertical = sy(20))
            ) {
                Text(
                    text = "Aktywuj za $priceLabel",
                    color = if (isButtonFocused && !channelsZoneFocused)
                        Color(0xFF48227C) else Color(0xFFEEEEEE),
                    fontSize = sy(28).value.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(sy(36)))

            // ===== Opis pakietu =====
            Text(
                text = paket.description,
                color = Color(0xFFEEEEEE),
                fontSize = sy(28).value.sp,
                lineHeight = sy(38).value.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = sx(760))
            )

            Spacer(modifier = Modifier.height(sy(44)))

            // ===== Kanały w pakiecie =====
            if (paket.channels.isNotEmpty()) {
                Text(
                    text = "${paket.channels.size} kanałów w pakiecie",
                    color = Color(0xCCEEEEEE),
                    fontSize = sy(24).value.sp
                )
                Spacer(modifier = Modifier.height(sy(20)))
                LazyRow(
                    state = channelsListState,
                    horizontalArrangement = Arrangement.spacedBy(sx(24))
                ) {
                    items(paket.channels.size) { i ->
                        val ch = paket.channels[i]
                        val isHighlighted = channelsZoneFocused && i == channelIndex
                        Column(
                            modifier = Modifier
                                .width(sx(200))
                                .clip(RoundedCornerShape(sx(12)))
                                .background(tileColor, RoundedCornerShape(sx(12)))
                                // Fokus = RAMKA aqua, jak kafle w sekcjach (nie fill)
                                .border(
                                    width = if (isHighlighted) sx(4) else 0.dp,
                                    color = if (isHighlighted) Color(0xFF5FEDD4) else Color.Transparent,
                                    shape = RoundedCornerShape(sx(12))
                                )
                                .padding(vertical = sy(20)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (ch.logo.isNotBlank()) {
                                AsyncImage(
                                    model = ch.logo,
                                    contentDescription = ch.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(sx(120), sy(64))
                                )
                            } else {
                                Text(
                                    text = ch.name,
                                    color = Color(0xFFEEEEEE),
                                    fontSize = sy(18).value.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(sy(16)))
                            // Numer kanału w ramce (makieta: kolejne numery od 201)
                            Box(
                                modifier = Modifier
                                    .background(Color(0x1AEEEEEE), RoundedCornerShape(sx(6)))
                                    .padding(horizontal = sx(14), vertical = sy(4))
                            ) {
                                Text(
                                    text = "${201 + i}",
                                    color = Color(0xFFEEEEEE),
                                    fontSize = sy(20).value.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(sy(40)))

            // ===== Kolekcje w pakiecie (nagłówek + chipy nazw) =====
            if (paket.collections.isNotEmpty()) {
                Text(
                    text = "${paket.collections.size} kolekcje w pakiecie",
                    color = Color(0xCCEEEEEE),
                    fontSize = sy(24).value.sp
                )
                Spacer(modifier = Modifier.height(sy(16)))
                Row(horizontalArrangement = Arrangement.spacedBy(sx(16))) {
                    paket.collections.take(4).forEach { col ->
                        Box(
                            modifier = Modifier
                                .background(tileColor, RoundedCornerShape(sx(12)))
                                .padding(horizontal = sx(24), vertical = sy(12))
                        ) {
                            Text(
                                text = col.name,
                                color = Color(0xFFEEEEEE),
                                fontSize = sy(22).value.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
