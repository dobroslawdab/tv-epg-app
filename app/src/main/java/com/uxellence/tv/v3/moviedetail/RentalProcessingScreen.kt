package com.uxellence.tv.v3.moviedetail

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay

/**
 * Two-stage payment confirmation:
 *  - Stage 0 (~1.5s): "Przetwarzanie płatności" + spinning indicator
 *  - Stage 1 (~1.5s): "Film wypożyczony!" + animated checkmark + movie title
 *
 * After both stages [onComplete] is invoked. BACK key is intentionally NOT handled —
 * the screen is short and the user shouldn't be able to abort mid-confirmation.
 */
@Composable
fun RentalProcessingScreen(
    movieTitle: String,
    onComplete: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int): Dp = (px * scaleX).dp
    fun sy(px: Int): Dp = (px * scaleY).dp

    var stage by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(1500)
        stage = 1
        delay(1500)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF281443)),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = stage, animationSpec = tween(300), label = "rental_stage") { current ->
            when (current) {
                0 -> ProcessingStage(sx = ::sx, sy = ::sy)
                else -> SuccessStage(movieTitle = movieTitle, sx = ::sx, sy = ::sy)
            }
        }
    }
}

@Composable
private fun ProcessingStage(
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(32))
    ) {
        Canvas(modifier = Modifier.size(sx(96)).rotate(rotation)) {
            val stroke = Stroke(width = sx(6).toPx(), cap = StrokeCap.Round)
            // Three-quarter arc — trailing tail is invisible so it visually spins.
            drawArc(
                color = Color(0xFF5FEDD4),
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = stroke
            )
        }
        Text(
            text = "Przetwarzanie płatności...",
            color = Color(0xFFEEEEEE),
            fontSize = (32 * scaleYRatio()).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SuccessStage(
    movieTitle: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val pathProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500),
        label = "checkmark_path"
    )
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 350),
        label = "checkmark_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(28))
    ) {
        Canvas(
            modifier = Modifier
                .size(sx(120))
                .scale(scale)
        ) {
            val w = size.width
            val h = size.height
            val strokeWidth = sx(8).toPx()
            // Circle outline
            drawCircle(
                color = Color(0xFF5FEDD4),
                radius = (w / 2f) - strokeWidth / 2f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = strokeWidth)
            )
            // Checkmark path: short stroke up-right then long stroke up-right
            val cm = Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.45f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(cm, false) }
            val total = measure.length
            val drawn = Path()
            measure.getSegment(0f, total * pathProgress, drawn, true)
            drawPath(
                path = drawn,
                color = Color(0xFF5FEDD4),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }
        Text(
            text = "Film wypożyczony!",
            color = Color(0xFFEEEEEE),
            fontSize = (40 * scaleYRatio()).sp,
            fontWeight = FontWeight.Bold
        )
        if (movieTitle.isNotBlank()) {
            Text(
                text = movieTitle,
                color = Color(0xCCEEEEEE),
                fontSize = (22 * scaleYRatio()).sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun scaleYRatio(): Float =
    LocalConfiguration.current.screenHeightDp / 1080f
