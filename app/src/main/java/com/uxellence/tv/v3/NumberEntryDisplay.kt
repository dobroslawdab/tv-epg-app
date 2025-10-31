package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * NUMBER ENTRY DISPLAY
 *
 * Shows large channel number while user is typing
 * (e.g., "1", "12", "123")
 *
 * Layout Engineer specs:
 * - Position: Top right corner (below time label)
 * - Same as ChannelNumberCard from ZappingBarScreen
 * - Size: 256x272px
 * - Background: #1a0c2c (container-banner)
 * - Number: 88px Medium, #5fedd4 (aqua)
 */
@Composable
fun NumberEntryDisplay(
    digits: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopEnd)
            .padding(top = sy(150), end = sx(264))  // Below time label
    ) {
        Box(
            modifier = Modifier
                .width(sx(256))  // Figma: 256px
                .height(sy(272))  // Figma: 272px
                .clip(RoundedCornerShape(sx(16)))  // Figma: radius=16px
                .background(Color(0xFF1A0C2C)),  // Figma: container-banner
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large channel number being entered (aqua)
                Text(
                    text = digits,
                    style = TextStyle(
                        fontSize = (88 * sy(1).value / 1).sp,  // Figma: 88px
                        fontWeight = FontWeight.Medium,
                        lineHeight = (120 * sy(1).value / 1).sp,  // Figma: 120px
                        color = Color(0xFF5FEDD4),  // Figma: text-status (aqua)
                        letterSpacing = (-1.76 * sy(1).value / 1).sp,  // Figma: -1.76px
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(sy(8)))

                // Hint text
                Text(
                    text = "Wpisz numer kanału",
                    style = TextStyle(
                        fontSize = (16 * sy(1).value / 1).sp,  // Smaller hint text
                        fontWeight = FontWeight.Bold,
                        lineHeight = (24 * sy(1).value / 1).sp,
                        color = Color(0xCCEEEEEE),  // 80% opacity
                        textAlign = TextAlign.Center,
                        letterSpacing = (0.32 * sy(1).value / 1).sp
                    ),
                    modifier = Modifier.width(sx(224))  // Max width
                )
            }
        }
    }
}
