package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private val AQUA = Color(0xFF5AECD3)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)

/**
 * DEMO EPG LAYER — warstwa EPG na live, wzorowana na EpgDayScreen (zakładka Telewizja):
 * gradient od dołu, info kanału (numer + nazwa) po lewej, poziomy rail kart programów
 * ramówki (karta wg EpgDayItem: cover 208x116 z aqua borderem dla fokusu, czasy,
 * tytuł 48px, metadane, pasek postępu emisji dla bieżącego bloku).
 *
 * LEFT/RIGHT przegląda bloki, OK = przewiń antenę do początku bloku (jeśli w DVR),
 * BACK = schowaj warstwę.
 */
@Composable
fun DemoEpgLayer(
    isVisible: Boolean,
    blocks: List<DemoChannelSchedule.EpgBlock>,
    focusedIndex: Int,
    currentVirtualMs: Long,
    dvrStartVirtualMs: Long,
    antennaStartWallMs: Long,
    thumbnailFor: (DemoChannelSchedule.EpgBlock) -> Bitmap?,
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
            // Gradient jak w EpgDayScreen (1-channel mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(520))
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0.2f to Color(0x0048227C),
                            0.75f to Color(0xFF48227C)
                        )
                    )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = sy(56))
            ) {
                // Info kanału (numer + nazwa) — odpowiednik ChannelInfoOverlay
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(start = sx(56), end = sx(40))
                        .width(sx(170))
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(sx(8)))
                            .border(2.dp, Color(0x66EEEEEE), RoundedCornerShape(sx(8)))
                            .padding(horizontal = sx(16), vertical = sy(8))
                    ) {
                        Text("122", color = TEXT_PRIMARY, fontSize = demoSp(28, sy), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(sy(8)))
                    Text("DEMO TV", color = TEXT_PRIMARY, fontSize = demoSp(20, sy), fontWeight = FontWeight.Bold)
                }

                // Rail kart programów
                val listState = rememberLazyListState()
                LaunchedEffect(focusedIndex, isVisible) {
                    if (isVisible && focusedIndex >= 0) listState.animateScrollToItem(focusedIndex)
                }
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(sx(48))
                ) {
                    items(blocks.size) { index ->
                        val block = blocks[index]
                        DemoEpgCard(
                            block = block,
                            isFocused = index == focusedIndex,
                            isCurrent = currentVirtualMs in block.startVirtualMs until block.endVirtualMs,
                            inDvr = block.startVirtualMs >= dvrStartVirtualMs &&
                                block.startVirtualMs <= currentVirtualMs,
                            antennaStartWallMs = antennaStartWallMs,
                            currentVirtualMs = currentVirtualMs,
                            thumbnail = thumbnailFor(block),
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }

            Text(
                text = "OK — odtwarzaj od początku   |   BACK — pełny ekran",
                color = Color(0x99EEEEEE),
                fontSize = demoSp(16, sy),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sx(266), bottom = sy(20))
            )
        }
    }
}

@Composable
private fun DemoEpgCard(
    block: DemoChannelSchedule.EpgBlock,
    isFocused: Boolean,
    isCurrent: Boolean,
    inDvr: Boolean,
    antennaStartWallMs: Long,
    currentVirtualMs: Long,
    thumbnail: Bitmap?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val alpha = if (isFocused || isCurrent) 1f else 0.5f
    Column(
        modifier = Modifier
            .width(sx(908))
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(sy(4))
    ) {
        Row(
            modifier = Modifier.size(sx(908), sy(134)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(24))
        ) {
            if (isFocused || isCurrent) {
                Box(
                    modifier = Modifier
                        .size(sx(208), sy(116))
                        .clip(RoundedCornerShape(sx(8)))
                        .background(Color(0xFF5A227C))
                        .then(
                            if (isFocused) Modifier.border(sx(6), AQUA, RoundedCornerShape(sx(8)))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null && !thumbnail.isRecycled) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text("📺", fontSize = demoSp(40, sy))
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(sy(8))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false) +
                            " – " + formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false),
                        fontSize = demoSp(24, sy),
                        fontWeight = FontWeight.Medium,
                        color = TEXT_PRIMARY
                    )
                    if (!inDvr && !isCurrent) {
                        Spacer(modifier = Modifier.width(sx(16)))
                        Text(
                            text = if (block.startVirtualMs > currentVirtualMs) "wkrótce" else "poza buforem",
                            fontSize = demoSp(18, sy),
                            color = Color(0x99EEEEEE)
                        )
                    }
                }
                Text(
                    text = block.title,
                    fontSize = demoSp(48, sy),
                    fontWeight = FontWeight.Medium,
                    color = TEXT_PRIMARY,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${block.genre}  |  ${block.year}  |  ${block.country}  |  ${block.age}",
                    fontSize = demoSp(20, sy),
                    color = Color(0xCCEEEEEE)
                )
            }
        }

        // Pasek postępu emisji — tylko dla bieżącego bloku
        if (isCurrent) {
            val blockDur = (block.endVirtualMs - block.startVirtualMs).coerceAtLeast(1L)
            val progress = ((currentVirtualMs - block.startVirtualMs).toFloat() / blockDur).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(sx(908))
                    .height(sy(8))
                    .clip(RoundedCornerShape(sy(4)))
                    .background(Color(0x66EEEEEE))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(sy(4)))
                        .background(TEXT_PRIMARY)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(sy(8)))
        }
    }
}
