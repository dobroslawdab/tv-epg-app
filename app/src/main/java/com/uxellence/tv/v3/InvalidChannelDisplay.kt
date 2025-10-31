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
 * INVALID CHANNEL DISPLAY
 *
 * Shows error message when user enters non-existent channel number
 *
 * Layout Engineer specs:
 * - Position: Center of screen
 * - Background: #1a0c2c (container-banner) with padding
 * - Border radius: 16px
 * - Text: Red color for error, white for message
 * - Auto-hide after 5 seconds (handled by PlayerInterfaceManager)
 */
@Composable
fun InvalidChannelDisplay(
    number: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(sx(600))  // Larger box for message
                .wrapContentHeight()
                .clip(RoundedCornerShape(sx(16)))  // Figma: radius=16px
                .background(Color(0xFF1A0C2C))  // Figma: container-banner
                .padding(
                    horizontal = sx(48),
                    vertical = sy(48)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sy(24))
            ) {
                // Invalid channel number (red)
                Text(
                    text = number,
                    style = TextStyle(
                        fontSize = (64 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (80 * sy(1).value / 1).sp,
                        color = Color(0xFFFF5555),  // Red for error
                        letterSpacing = (-1.28 * sy(1).value / 1).sp,
                        textAlign = TextAlign.Center
                    )
                )

                // Error icon or X mark
                Text(
                    text = "✕",
                    style = TextStyle(
                        fontSize = (48 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5555),  // Red
                        textAlign = TextAlign.Center
                    )
                )

                // Error message
                Text(
                    text = "Nie ma takiego kanału",
                    style = TextStyle(
                        fontSize = (32 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (48 * sy(1).value / 1).sp,
                        color = Color(0xFFEEEEEE),  // White
                        letterSpacing = (0.64 * sy(1).value / 1).sp,
                        textAlign = TextAlign.Center
                    )
                )

                // Hint text
                Text(
                    text = "Wpisz poprawny numer kanału",
                    style = TextStyle(
                        fontSize = (20 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = (28 * sy(1).value / 1).sp,
                        color = Color(0xCCEEEEEE),  // 80% opacity
                        letterSpacing = (0.4 * sy(1).value / 1).sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}
