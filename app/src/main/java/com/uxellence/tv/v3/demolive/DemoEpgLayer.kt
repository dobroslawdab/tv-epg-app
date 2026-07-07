package com.uxellence.tv.v3.demolive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.uxellence.tv.v3.epg.ChannelEpgRow
import com.uxellence.tv.v3.epg.ChannelInfoOverlay
import com.uxellence.tv.v3.epg.EpgDayItem
import kotlinx.coroutines.launch
import java.time.Instant

// Stałe layoutu skopiowane z EpgDayScreen (tam są private) — IDENTYCZNY wygląd
private const val GRADIENT_TOP = 276
private const val GRADIENT_HEIGHT = 804
private const val FOCUSED_X = 330
private const val SCREEN_WIDTH = 1920
private const val END_PADDING = SCREEN_WIDTH - FOCUSED_X
private const val CHANNEL_INFO_X = 40
private const val CHANNEL_ROW_HEIGHT = 142
private const val VIEWPORT_HEIGHT_SINGLE = 142         // 1 kanał (pasek programów danego kanału)
private const val VIEWPORT_HEIGHT_MULTI = 446          // 3 kanały: 3×142 + 2×10
private const val FIXED_FOCUS_Y_SINGLE = 1000          // 80px od dołu (tryb 1 kanału)
private const val FIXED_FOCUS_Y_MULTI = 960            // 120px od dołu (tryb 3+ kanałów)
private const val ITEM_GAP = 48
private const val ROW_GAP = 10

/**
 * DEMO EPG LAYER — wierna replika warstwy EPG z EpgDayScreen (zakładka Telewizja),
 * zbudowana z TYCH SAMYCH komponentów: ChannelEpgRow + ChannelInfoOverlay + EpgDayItem.
 *
 * Wiersz 0 = kanał testowy DEMO TV (programy ze sztucznej ramówki DemoChannelSchedule),
 * wiersze 1..N = prawdziwe kanały z prawdziwym EPG (ChannelManager + EpgRepository).
 *
 * Tryb 3-kanałowy (isExpanded w EpgDayScreen): fokusowany kanał w środku viewportu,
 * sąsiednie wiersze z alpha 0.5; fokusowany program na X=330.
 */
@Composable
fun DemoEpgLayer(
    isVisible: Boolean,
    rows: List<ChannelEpgRow>,
    focusedChannelIndex: Int,
    focusedProgramIndexFor: (Int) -> Int,
    focusedTime: Instant,
    isExpanded: Boolean,        // false = pasek 1 kanału (start); true = 3 kanały (po DOWN)
    isBlackout: (channelIdx: Int, programIdx: Int) -> Boolean = { _, _ -> false },  // brak praw → wyszarzenie + kłódka
    tunedChannelIndex: Int,     // kanał na ekranie — jego oglądany program dostaje playkę
    playbackInstant: Instant,   // pozycja oglądania (przy timeshifcie cofnięta względem live)
    nowInstant: Instant,        // zegar ścienny = live; program live-now ma ciemniejsze tło
    // Kropka nagrywania na mini-EPG: tylko programy ze zleconym nagraniem
    isRecording: (title: String, startUtc: Instant) -> Boolean = { _, _ -> false },
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Zegar ścienny w prawym górnym rogu — widoczny razem z GUI (warstwą EPG)
            Text(
                text = formatWall(nowInstant.toEpochMilli(), withSeconds = false),
                color = Color(0xFFEEEEEE),
                fontSize = demoSp(28, sy),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = sy(40), end = sx(60))
                    .zIndex(4f)
            )

            // Gradient jak w EpgDayScreen: tryb 1 kanału = subtelny/niższy (0.61→0.82),
            // tryb 3 kanałów = mocniejszy/wyższy (0.45→0.63) dla czytelności listy
            // Gradient ciemny #281443 (parametry z Figmy).
            // Pasek 1 kanalu: pas o wysokosci 265 przy dolnej krawedzi;
            // rozwiniete 3 kanaly: wyzszy (15%/45% z 804), przykrywa caly blok
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(GRADIENT_HEIGHT))
                        .offset(y = sy(GRADIENT_TOP))
                        .background(
                            Brush.verticalGradient(
                                0.15f to Color(0x00281443),
                                0.45f to Color(0xFF281443)
                            )
                        )
                        .zIndex(1f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(265))
                        .offset(y = sy(1080 - 265))
                        .background(
                            Brush.verticalGradient(
                                0f to Color(0x00281443),
                                1f to Color(0xFF281443)
                            )
                        )
                        .zIndex(1f)
                )
            }

            // Mini-EPG wg Figmy: tryb 1 kanału (5530-5603) i rozwinięte
            // 3 kanały (5530-5949). Wiersze rysowane wprost z indeksów programów
            // (bez LazyRow/scrolli — TIME SYNC to czysty layout, nie animacje).
            if (!isExpanded) {
                DemoMiniEpgBar(
                    row = rows.getOrNull(focusedChannelIndex),
                    programIndex = focusedProgramIndexFor(focusedChannelIndex),
                    isTunedChannel = focusedChannelIndex == tunedChannelIndex,
                    playbackInstant = playbackInstant,
                    nowInstant = nowInstant,
                    isRecording = isRecording,
                    sx = sx, sy = sy
                )
            } else {
                DemoMiniEpgExpanded(
                    rows = rows,
                    focusedChannelIndex = focusedChannelIndex,
                    focusedProgramIndexFor = focusedProgramIndexFor,
                    focusedTime = focusedTime,
                    tunedChannelIndex = tunedChannelIndex,
                    playbackInstant = playbackInstant,
                    nowInstant = nowInstant,
                    isRecording = isRecording,
                    sx = sx, sy = sy
                )
            }
        }
    }
}
