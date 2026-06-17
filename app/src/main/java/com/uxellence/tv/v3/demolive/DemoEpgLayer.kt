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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(GRADIENT_HEIGHT))
                    .offset(y = sy(GRADIENT_TOP))
                    .background(
                        if (isExpanded) {
                            Brush.verticalGradient(
                                0.45f to Color(0x0048227C),
                                0.63f to Color(0xFF48227C)
                            )
                        } else {
                            Brush.verticalGradient(
                                0.61f to Color(0x0048227C),
                                0.82f to Color(0xFF48227C)
                            )
                        }
                    )
                    .zIndex(1f)
            )

            // Viewport: 1 kanał (start) albo 3 kanały (po rozwinięciu) — jak EpgDayScreen
            val viewportHeight = if (isExpanded) VIEWPORT_HEIGHT_MULTI else VIEWPORT_HEIGHT_SINGLE
            val viewportOffset = if (isExpanded) {
                FIXED_FOCUS_Y_MULTI - (2 * CHANNEL_ROW_HEIGHT) - ROW_GAP
            } else {
                FIXED_FOCUS_Y_SINGLE - CHANNEL_ROW_HEIGHT
            }
            val columnState = rememberLazyListState()
            LaunchedEffect(focusedChannelIndex, isVisible) {
                if (isVisible && focusedChannelIndex in rows.indices) {
                    columnState.animateScrollToItem(focusedChannelIndex)
                }
            }

            // TIME SYNC jak w EpgDayScreen: wszystkie kanały przewijają się do programu
            // emitowanego o focusedTime — czasówki między wierszami się zgadzają.
            // Klucz zawiera isExpanded + focusedChannelIndex: w trybie 1-kanałowym
            // pozostałe LazyRow nie są skomponowane, więc scrollToItem na nich nie
            // zadziała; po rozwinięciu (DOWN) trzeba zsynchronizować je ponownie,
            // gdy już są w composition (stąd delay na layout świeżych wierszy).
            LaunchedEffect(focusedTime, rows, isVisible, isExpanded, focusedChannelIndex) {
                if (!isVisible || rows.isEmpty()) return@LaunchedEffect
                kotlinx.coroutines.delay(32)
                rows.forEachIndexed { index, channelRow ->
                    val matchingIndex = channelRow.programs.indexOfFirst { program ->
                        !focusedTime.isBefore(program.startUtc) && focusedTime.isBefore(program.endUtc)
                    }
                    if (matchingIndex >= 0) {
                        if (index == focusedChannelIndex) {
                            channelRow.lazyListState.animateScrollToItem(matchingIndex, 0)
                        } else {
                            channelRow.lazyListState.scrollToItem(matchingIndex, 0)
                        }
                    }
                }
            }
            LazyColumn(
                state = columnState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(viewportHeight))
                    .offset(y = sy(viewportOffset))
                    .zIndex(2f),
                // Tryb 1 kanału: bez paddingu (widać tylko fokusowany pasek);
                // tryb 3 kanałów: padding na pierwszy/ostatni kanał
                contentPadding = if (isExpanded) {
                    PaddingValues(
                        top = sy(CHANNEL_ROW_HEIGHT + ROW_GAP),
                        bottom = sy(CHANNEL_ROW_HEIGHT + ROW_GAP)
                    )
                } else PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(sy(ROW_GAP))
            ) {
                itemsIndexed(rows) { channelIndex, channelRow ->
                    val isFocusedChannel = channelIndex == focusedChannelIndex
                    val focusedProgramIndex = focusedProgramIndexFor(channelIndex)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sy(CHANNEL_ROW_HEIGHT))
                            .alpha(if (isFocusedChannel) 1f else 0.5f)
                    ) {
                        ChannelInfoOverlay(
                            channel = channelRow.channel,
                            channelNumber = channelRow.channelNumber,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = sx(CHANNEL_INFO_X))
                                .zIndex(3f),
                            sx = sx,
                            sy = sy
                        )

                        LazyRow(
                            state = channelRow.lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterStart),
                            contentPadding = PaddingValues(
                                start = sx(FOCUSED_X),
                                end = sx(END_PADDING)
                            ),
                            horizontalArrangement = Arrangement.spacedBy(sx(ITEM_GAP))
                        ) {
                            itemsIndexed(channelRow.programs) { programIndex, program ->
                                // Live teraz (zegar ścienny) → ciemniejsze tło kafelka
                                val isLiveNow = !nowInstant.isBefore(program.startUtc) &&
                                    nowInstant.isBefore(program.endUtc)
                                // Oglądany teraz: zatunowany kanał + program obejmujący
                                // POZYCJĘ ODTWARZANIA (przy timeshifcie to program miniony)
                                val isWatchedNow = channelIndex == tunedChannelIndex &&
                                    !playbackInstant.isBefore(program.startUtc) &&
                                    playbackInstant.isBefore(program.endUtc)
                                // Timeshift: oglądanie cofnięte względem live → pasek pokazuje
                                // pozycję oglądania (aqua) i punkt live (biała kropka)
                                val isTimeshifted = isWatchedNow &&
                                    java.time.Duration.between(playbackInstant, nowInstant)
                                        .toMillis() > 5_000L
                                val programSpanMs = java.time.Duration
                                    .between(program.startUtc, program.endUtc)
                                    .toMillis().coerceAtLeast(1L).toFloat()
                                val watchProgress = if (isTimeshifted) {
                                    (java.time.Duration.between(program.startUtc, playbackInstant)
                                        .toMillis().toFloat() / programSpanMs).coerceIn(0f, 1f)
                                } else null
                                val liveDotAt = if (isTimeshifted && isLiveNow) {
                                    (java.time.Duration.between(program.startUtc, nowInstant)
                                        .toMillis().toFloat() / programSpanMs).coerceIn(0f, 1f)
                                } else null
                                EpgDayItem(
                                    program = program,
                                    isFocused = isFocusedChannel && programIndex == focusedProgramIndex,
                                    focusedTime = focusedTime,
                                    liveNowBackground = isLiveNow,
                                    isWatchedNow = isWatchedNow,
                                    watchProgress = watchProgress,
                                    liveDotAt = liveDotAt,
                                    isBlackout = isBlackout(channelIndex, programIndex),
                                    sx = sx,
                                    sy = sy
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
