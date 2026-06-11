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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
private const val VIEWPORT_HEIGHT_MULTI = 446          // 3 kanały: 3×142 + 2×10
private const val FIXED_FOCUS_Y_MULTI = 960
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
            // Gradient jak w EpgDayScreen (wariant 3-kanałowy: mocniejszy dla czytelności)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(GRADIENT_HEIGHT))
                    .offset(y = sy(GRADIENT_TOP))
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color(0x0048227C),
                            0.63f to Color(0xFF48227C)
                        )
                    )
                    .zIndex(1f)
            )

            // Viewport 3 kanałów — pozycjonowanie jak w EpgDayScreen (expanded mode)
            val viewportOffset = FIXED_FOCUS_Y_MULTI - (2 * CHANNEL_ROW_HEIGHT) - ROW_GAP
            val columnState = rememberLazyListState()
            LaunchedEffect(focusedChannelIndex, isVisible) {
                if (isVisible && focusedChannelIndex in rows.indices) {
                    columnState.animateScrollToItem(focusedChannelIndex)
                }
            }
            LazyColumn(
                state = columnState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(VIEWPORT_HEIGHT_MULTI))
                    .offset(y = sy(viewportOffset))
                    .zIndex(2f),
                contentPadding = PaddingValues(
                    top = sy(CHANNEL_ROW_HEIGHT + ROW_GAP),
                    bottom = sy(CHANNEL_ROW_HEIGHT + ROW_GAP)
                ),
                verticalArrangement = Arrangement.spacedBy(sy(ROW_GAP))
            ) {
                itemsIndexed(rows) { channelIndex, channelRow ->
                    val isFocusedChannel = channelIndex == focusedChannelIndex
                    val focusedProgramIndex = focusedProgramIndexFor(channelIndex)

                    // Auto-scroll wiersza: fokusowany/bieżący program na X=330
                    LaunchedEffect(focusedProgramIndex, isFocusedChannel, isVisible) {
                        if (isVisible && focusedProgramIndex in channelRow.programs.indices) {
                            channelRow.lazyListState.animateScrollToItem(focusedProgramIndex)
                        }
                    }

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
                                EpgDayItem(
                                    program = program,
                                    isFocused = isFocusedChannel && programIndex == focusedProgramIndex,
                                    focusedTime = focusedTime,
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
