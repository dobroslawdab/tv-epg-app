package com.uxellence.tv.v3.demolive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * DEMO LIVE INFO BAR — warstwa 1: informacje o bieżącym bloku ramówki na live.
 *
 * Pasek postępu emisji liczony Z RAMÓWKI (coerceAtMost 1f) — gdy materiał gra
 * dłużej niż blok (rozjazd wariant 1), pasek stoi na 100% a materiał leci dalej.
 * To celowo widoczny artefakt symulowanego case'u.
 */
@Composable
fun DemoLiveInfoBar(
    isVisible: Boolean,
    block: DemoChannelSchedule.EpgBlock,
    currentVirtualMs: Long,
    antennaStartWallMs: Long,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(420))
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6311257))
                        )
                    )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sx(80), bottom = sy(64), end = sx(80))
            ) {
                // Numer + nazwa kanału demo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(sx(8)))
                        .border(1.5.dp, Color(0x66EEEEEE), RoundedCornerShape(sx(8)))
                        .padding(horizontal = sx(16), vertical = sy(8))
                ) {
                    Text("122", color = Color(0xFFEEEEEE), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(sx(20)))
                Text("DEMO TV", color = Color(0xFFEEEEEE), fontSize = 24.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.width(sx(48)))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.title,
                        color = Color(0xFFEEEEEE),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(sy(8)))
                    Text(
                        text = "${formatWall(antennaStartWallMs + block.startVirtualMs, withSeconds = false)}–" +
                            formatWall(antennaStartWallMs + block.endVirtualMs, withSeconds = false) +
                            "  |  ${block.genre}  |  ${block.year}  |  ${block.country}  |  ${block.age}",
                        color = Color(0xCCEEEEEE),
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(sy(14)))
                    // Pasek postępu emisji wg ramówki
                    val blockDur = (block.endVirtualMs - block.startVirtualMs).coerceAtLeast(1L)
                    val progress = ((currentVirtualMs - block.startVirtualMs).toFloat() / blockDur)
                        .coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(sy(6))
                            .clip(RoundedCornerShape(sy(3)))
                            .background(Color(0x40EEEEEE))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(sy(3)))
                                .background(Color(0xFF5AECD3))
                        )
                    }
                    Spacer(modifier = Modifier.height(sy(10)))
                    Text(
                        text = "OK — szczegóły programu   |   BACK — pełny ekran",
                        color = Color(0x99EEEEEE),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
