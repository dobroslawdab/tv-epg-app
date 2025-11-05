package com.uxellence.tv.v3.epg

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * TOP MENU OVERLAY
 *
 * Purpose: Visual representation of top menu showing navigation structure
 *
 * Display conditions:
 * - Shows on EPG Day Test startup
 * - Auto-hides after 10 seconds
 * - Closes on any key press (UP/DOWN/OK)
 *
 * Design: Based on Figma (amSVOTqmxh8LHgIL9aVmnW:6268-78471)
 * Layout Engineer: ALL values use sx/sy responsive scaling system
 */
@Composable
fun TopMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    sx: (Int) -> Dp,  // Responsive horizontal scaling
    sy: (Int) -> Dp,  // Responsive vertical scaling
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1000f) // Ensure overlay is on top
                .onPreviewKeyEvent { event ->
                    // Close overlay on UP/DOWN/OK key press
                    if (event.type == KeyEventType.KeyDown &&
                        event.key in setOf(Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.DirectionCenter)) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                }
        ) {
            // Gradient overlay layer with color stops
            // CSS equivalent: linear-gradient(179deg, rgba(72, 34, 124, 0) 40%, #48227C 70%) + rotate(180deg)
            // Height: 600px (2x increased)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(600))  // 600px height (2x increase)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f)  // Behind all other elements
                    .background(
                        brush = Brush.verticalGradient(
                            0.0f to Color(0xFF48227C),    // 0%: Solid purple at top
                            0.3f to Color(0xFF48227C),    // 30%: Still solid purple (70% after rotation)
                            0.6f to Color(0x0048227C),    // 60%: Transparent purple (40% after rotation)
                            startY = 0f,
                            endY = sy(600).value
                        )
                    )
            )

            // Top Menu Bar
            // Figma: X=67, Y=15
            TopMenuBar(
                sx = sx,
                sy = sy,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sx(67), top = sy(15))
            )

            // Tooltip with instruction
            // Figma: X=82, Y=153
            TooltipInstruction(
                sx = sx,
                sy = sy,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sx(82), top = sy(153))
            )

            // Current time display
            // Figma: X=1920-1775-82=63, Y=40
            PureDigitalClock(
                sx = sx,
                sy = sy,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = sx(63), top = sy(40))
            )
        }
    }
}

@Composable
private fun TopMenuBar(
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    val menuItems = listOf("Start", "Moje", "Telewizja", "KinoPlay", "Wideo", "Aplikacje")

    Row(
        modifier = modifier
            .height(sy(97))        // Figma: 97px height
            .width(sx(1062))       // Figma: 1062px width
            .background(
                color = Color(0x4A000000), // rgba(0, 0, 0, 0.29)
                shape = RoundedCornerShape(sx(49)) // Figma: 48.5px → 49px
            )
            .padding(horizontal = sx(7), vertical = sy(8)), // Figma: 7px, 8px
        horizontalArrangement = Arrangement.spacedBy(sx(13)), // Figma: 13px gap
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search icon button
        // Figma: 80x80px
        Box(
            modifier = Modifier
                .size(sx(80), sy(80))
                .background(
                    color = Color(0x08FFFFFF), // rgba(255, 255, 255, 0.03)
                    shape = RoundedCornerShape(sx(64)) // Figma: 64px
                ),
            contentAlignment = Alignment.Center
        ) {
            // Search icon (magnifying glass) from Material Icons
            // Figma: 48x48px
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFFEEEEEE),  // White color #EEEEEE
                modifier = Modifier.size(sx(48), sy(48))  // Figma: 48x48px
            )
        }

        // Menu items
        menuItems.forEach { itemText ->
            MenuItem(
                text = itemText,
                sx = sx,
                sy = sy
            )
        }
    }
}

@Composable
private fun MenuItem(
    text: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(sy(80))  // Figma: 80px height
            .background(
                color = Color(0x0AFFFFFF), // rgba(255, 255, 255, 0.04)
                shape = RoundedCornerShape(sx(64)) // Figma: 64px
            )
            .padding(horizontal = sx(32)), // Figma: 32px horizontal padding
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFEEEEEE), // #EEEEEE
            fontSize = (24 * sy(1).value / 1).sp, // Figma: 24px
            fontWeight = FontWeight.Medium,
            letterSpacing = (0.48 * sy(1).value / 1).sp // Figma: 0.48px
        )
    }
}

@Composable
private fun TooltipInstruction(
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    // Figma: 6324-26213 - Tooltip with white background and arrow
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Arrow pointing up (triangle shape with curves)
        Canvas(
            modifier = Modifier
                .size(width = sx(40), height = sy(20))
        ) {
            val path = Path().apply {
                // Start at bottom left
                moveTo(0f, size.height)
                // Line to top center (peak of arrow)
                lineTo(size.width / 2, 0f)
                // Line to bottom right
                lineTo(size.width, size.height)
                // Close the path
                close()
            }
            drawPath(
                path = path,
                color = androidx.compose.ui.graphics.Color(0xFFEEEEEE) // White arrow #EEEEEE
            )
        }

        // Rectangle 491: 378x180px white background
        Box(
            modifier = Modifier
                .size(width = sx(378), height = sy(180)) // Figma: 378x180px
                .background(
                    color = Color(0xFFEEEEEE), // White background #EEEEEE
                    shape = RoundedCornerShape(sx(8)) // Figma: 8px radius
                )
                .padding(start = sx(39), top = sy(47)), // Figma: left 39px, top 47px
            contentAlignment = Alignment.TopStart
        ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(sy(10)) // Figma: 10px gap
        ) {
            // First row: "Naciśnij," + [home button] + "na pilocie,"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(10)) // Figma: 10px gap
            ) {
                Text(
                    text = "Naciśnij,",
                    color = Color(0xFF48227C), // Purple text
                    fontSize = sy(24).value.sp, // Figma: 24px
                    fontWeight = FontWeight.Bold,
                    lineHeight = sy(32).value.sp, // Figma: 32px line height
                    letterSpacing = (-0.48).sp // Figma: -0.48px
                )

                // Home button icon
                // Figma: 62x32px button with arrow icon inside
                Box(
                    modifier = Modifier
                        .size(width = sx(62), height = sy(32))
                        .background(
                            color = Color(0xFF452177), // Purple button #452177
                            shape = RoundedCornerShape(sx(12)) // Figma: 12px radius
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Home arrow icon (Path 872 from Figma)
                    // Position: left 17px, top 4px relative to button
                    // Size: 28.248x24px
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Color.White,
                        modifier = Modifier.size(sx(24), sy(24))
                    )
                }

                Text(
                    text = "na pilocie,",
                    color = Color(0xFF48227C),
                    fontSize = sy(24).value.sp, // Figma: 24px
                    fontWeight = FontWeight.Bold,
                    lineHeight = sy(32).value.sp, // Figma: 32px line height
                    letterSpacing = (-0.48).sp // Figma: -0.48px
                )
            }

            // Second row: "aby przejść do\nekranu głównego" (centered, 2 lines, width 300px)
            Text(
                text = "aby przejść do\nekranu głównego",
                color = Color(0xFF48227C),
                fontSize = sy(24).value.sp, // Figma: 24px
                fontWeight = FontWeight.Bold,
                lineHeight = sy(32).value.sp, // Figma: 32px line height
                letterSpacing = (-0.48).sp, // Figma: -0.48px
                textAlign = TextAlign.Center,
                modifier = Modifier.width(sx(300)) // Figma: width 300px
            )
        }
        }
    }
}

@Composable
private fun PureDigitalClock(
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    // Digital clock component - same as top menu (PureDigitalClock)
    // No height/padding constraints to prevent text clipping
    var currentTime by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }

    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val formattedTime = currentTime.format(timeFormatter)
    val (hours, minutes) = formattedTime.split(":")

    // Blinking colon state (optional - can be removed for static colon)
    var showColon by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            showColon = !showColon
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Hours
        Text(
            text = hours,
            color = Color(0xFFEEEEEE),
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.64).sp
        )

        // Colon (with blinking effect using alpha)
        Text(
            text = ":",
            color = Color(0xFFEEEEEE).copy(alpha = if (showColon) 1f else 0f),
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.64).sp,
            modifier = Modifier.width(sx(8)) // Fixed width to prevent shifting
        )

        // Minutes
        Text(
            text = minutes,
            color = Color(0xFFEEEEEE),
            fontSize = sy(32).value.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.64).sp
        )
    }
}
