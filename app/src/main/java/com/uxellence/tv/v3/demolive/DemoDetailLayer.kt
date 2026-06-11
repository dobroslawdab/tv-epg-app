package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val AQUA = Color(0xFF5AECD3)
private val BG_PURPLE = Color(0xFF48227C)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)

/**
 * DEMO DETAIL LAYER — warstwa 2: detal programu z PIP (wg zrzutu designu Play).
 *
 * Pełnoekranowe tło; prawy dolny róg zostaje "wolny" — tam DemoLiveScreen
 * renderuje grający PlayerView (PIP) NA WIERZCHU tej warstwy.
 *
 * Przyciski: 0 = "Zacznij od początku" (działa), 1 = "Nagraj", 2 = "Napisy, dźwięk, jakość"
 * (atrapy). focusedButtonIndex == -1 → fokus na rzędzie "Części serii".
 */
@Composable
fun DemoDetailLayer(
    isVisible: Boolean,
    block: DemoChannelSchedule.EpgBlock,
    currentVirtualMs: Long,
    antennaStartWallMs: Long,
    thumbnail: Bitmap?,
    focusedButtonIndex: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF5B2D99), BG_PURPLE, Color(0xFF311257)),
                        radius = 1800f
                    )
                )
        ) {
            // Zegar
            val clockText by produceState(initialValue = formatWall(System.currentTimeMillis(), withSeconds = false)) {
                while (true) {
                    value = formatWall(System.currentTimeMillis(), withSeconds = false)
                    delay(1_000)
                }
            }
            Text(
                text = clockText,
                color = TEXT_PRIMARY,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = sy(44), end = sx(64))
            )

            // Numer kanału + "logo" przy lewej krawędzi
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = sx(32))
                    .offset(y = -sy(40))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(sx(6)))
                        .border(1.5.dp, Color(0x66EEEEEE), RoundedCornerShape(sx(6)))
                        .padding(horizontal = sx(12), vertical = sy(6))
                ) {
                    Text("122", color = TEXT_PRIMARY, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(sx(16)))
                Text("DEMO TV", color = TEXT_PRIMARY, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }

            // Główna kolumna treści
            Column(
                modifier = Modifier
                    .padding(start = sx(330), top = sy(150), end = sx(560))
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    // Miniatura programu (klatka z początku bloku)
                    Box(
                        modifier = Modifier
                            .width(sx(244))
                            .height(sy(140))
                            .clip(RoundedCornerShape(sx(8)))
                            .background(Color(0x40000000))
                    ) {
                        if (thumbnail != null && !thumbnail.isRecycled) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(sx(36)))
                    Column {
                        Text(
                            text = formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false) +
                                "–" + formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false),
                            color = TEXT_PRIMARY,
                            fontSize = 22.sp
                        )
                        Spacer(modifier = Modifier.height(sy(6)))
                        Text(
                            text = block.title,
                            color = TEXT_PRIMARY,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(sy(10)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val blockMin = (block.endVirtualMs - block.startVirtualMs) / 60_000
                            Text(
                                text = "${block.genre}  |  $blockMin min  |  ${block.year}  |  ${block.country}  |  ${block.age}  |",
                                color = Color(0xCCEEEEEE),
                                fontSize = 19.sp
                            )
                            Spacer(modifier = Modifier.width(sx(12)))
                            listOf("S", "W", "N", "P").forEach { letter ->
                                Box(
                                    modifier = Modifier
                                        .padding(end = sx(8))
                                        .clip(RoundedCornerShape(sx(4)))
                                        .border(1.dp, Color(0x99EEEEEE), RoundedCornerShape(sx(4)))
                                        .padding(horizontal = sx(7), vertical = sy(2))
                                ) {
                                    Text(letter, color = Color(0xCCEEEEEE), fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(sy(24)))

                // Pasek postępu emisji (wg ramówki)
                val blockDur = (block.endVirtualMs - block.startVirtualMs).coerceAtLeast(1L)
                val progress = ((currentVirtualMs - block.startVirtualMs).toFloat() / blockDur).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(6))
                        .clip(RoundedCornerShape(sy(3)))
                        .background(Color(0x40EEEEEE))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(sy(3)))
                            .background(Color(0xCCEEEEEE))
                    )
                }

                Spacer(modifier = Modifier.height(sy(28)))

                // Przyciski
                Row {
                    DetailButton("↺  Zacznij od początku", isFocused = focusedButtonIndex == 0, sx = sx, sy = sy)
                    Spacer(modifier = Modifier.width(sx(20)))
                    DetailButton("REC  Nagraj", isFocused = focusedButtonIndex == 1, sx = sx, sy = sy)
                    Spacer(modifier = Modifier.width(sx(20)))
                    DetailButton("⚙  Napisy, dźwięk, jakość", isFocused = focusedButtonIndex == 2, sx = sx, sy = sy)
                }

                Spacer(modifier = Modifier.height(sy(32)))

                Text(
                    text = block.description,
                    color = TEXT_PRIMARY.copy(alpha = 0.95f),
                    fontSize = 21.sp,
                    lineHeight = 30.sp
                )
            }

            // Rząd "Części serii" na dole (atrapa) — nie wchodzi pod PIP (prawy dolny róg wolny)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sx(40), bottom = sy(28))
            ) {
                Box(
                    modifier = Modifier
                        .width(sx(150))
                        .height(sy(84))
                        .clip(RoundedCornerShape(sx(8)))
                        .background(
                            if (focusedButtonIndex == -1) Color(0x33FFFFFF) else Color(0x1AFFFFFF)
                        )
                        .then(
                            if (focusedButtonIndex == -1) Modifier.border(2.dp, AQUA, RoundedCornerShape(sx(8)))
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Części serii", color = TEXT_PRIMARY, fontSize = 18.sp)
                }
                repeat(4) { i ->
                    Spacer(modifier = Modifier.width(sx(16)))
                    Box(
                        modifier = Modifier
                            .width(sx(220))
                            .height(sy(84))
                            .clip(RoundedCornerShape(sx(8)))
                            .background(Color(0x26FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Część ${i + 1}", color = Color(0xAAEEEEEE), fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailButton(
    label: String,
    isFocused: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(sx(10)))
            .background(if (isFocused) AQUA else Color(0x33EEEEEE))
            .padding(horizontal = sx(24), vertical = sy(14))
    ) {
        Text(
            text = label,
            color = if (isFocused) BG_PURPLE else TEXT_PRIMARY,
            fontSize = 21.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
