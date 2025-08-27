package com.example.tv.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.min

// RadialGradient z Figmy:
// center: Alignment(0.74, -0.14)  -> (x, y) w 0..1: (0.87, 0.43)
// radius: 1.51 (jako mnożnik najkrótszego boku)
// colors: [#5AECD3 (center), #583085 (outer)]

fun Modifier.figmaRadialBackground(): Modifier = this.then(
    Modifier.drawBehind {
        val center = Offset(size.width * 0.87f, size.height * 0.43f)
        val radius = 1.51f * min(size.width, size.height)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF5AECD3),
                    Color(0xFF583085)
                ),
                center = center,
                radius = radius
            )
        )
    }
)


