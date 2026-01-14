package com.uxellence.tv.v3.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uxellence.tv.v3.model.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RECORDING CARD COMPONENT (410x232px)
 *
 * Figma: RecordingCardBig variants
 * Based on Nagrywanie-serii design spec
 *
 * Card Types:
 * - OBEJRZANE: ✓ + "Oglądaj do DD.MM.YYYY" badge, 100% progress
 * - OGLADAJ: ✓ + date badge, partial progress
 * - SERIA: Stack icon + "X odcinków (Y h)" badge
 * - NAGRYWANIE: Red "REC" badge with border, partial progress
 * - ZAPLANOWANE: White "REC" badge with border, 30% card opacity
 *
 * Dimensions:
 * - Cover: 410x232px
 * - Logo: 89x71px (bottom-left)
 * - Badge: 40px height (top-left)
 * - Progress: 8px height (below card)
 * - Border radius: 8px
 * - Focus border: 6px aqua (#5AECD3)
 */

// Colors from Figma
private val ColorBadgeBg = Color(0xFF1A0C2C)
private val ColorRecRed = Color(0xFFDD1538)
private val ColorRecWhite = Color(0xFFEEEEEE)
private val ColorFocusBorder = Color(0xFF5AECD3)
private val ColorProgressBg = Color(0x66EEEEEE)  // rgba(238,238,238,0.4) from Figma
private val ColorProgressFill = Color(0xFFEEEEEE)  // #eee (white) from Figma - NOT aqua!
private val ColorCheckmark = Color(0xFF5AECD3)  // Aqua checkmark (matching focus color)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RecordingCard(
    recording: Recording,
    cardType: RecordingCardType,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    // Card dimensions from Figma: 410x232px
    val cardWidth = sx(410)
    val cardHeight = sy(232)
    val cornerRadius = sx(8)
    val progressHeight = sy(8)
    val totalHeight = cardHeight + progressHeight + sy(4)  // Card + progress + gap

    // Card content opacity for ZAPLANOWANE: always 30% (border stays 100%)
    val contentAlpha = if (cardType == RecordingCardType.ZAPLANOWANE) 0.3f else 1.0f

    Column(
        modifier = modifier.width(cardWidth)
    ) {
        // Main card - outer Box for border (no alpha), inner content has alpha
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .clip(RoundedCornerShape(cornerRadius))
                .then(
                    if (isFocused) Modifier.border(
                        width = sx(6),
                        color = ColorFocusBorder,
                        shape = RoundedCornerShape(cornerRadius)
                    ) else Modifier
                )
                .focusRequester(focusRequester)
                .onFocusChanged { if (it.isFocused) onFocusChange() }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter)
                    ) {
                        onClick()
                        true
                    } else {
                        false
                    }
                }
                .focusable()
        ) {
            // Content wrapper with alpha (for ZAPLANOWANE 30% opacity)
            Box(modifier = Modifier.fillMaxSize().alpha(contentAlpha)) {
            // Cover image
            AsyncImage(
                model = recording.imageUrl ?: "https://via.placeholder.com/410x232",
                contentDescription = recording.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Bottom gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sy(120))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.95f)
                            )
                        )
                    )
            )

            // Channel logo (bottom-left, 89x71px)
            recording.channelLogoUrl?.let { logoUrl ->
                AsyncImage(
                    model = logoUrl,
                    contentDescription = recording.channelName,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = sx(12), bottom = sy(12))
                        .width(sx(89))
                        .height(sy(71)),
                    contentScale = ContentScale.Fit
                )
            }

            // Title and info (bottom-right)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = sx(12), bottom = sy(12), start = sx(110))
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = recording.title,
                    color = Color.White,
                    fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle (episode info or duration)
                recording.subTitle?.let { sub ->
                    Text(
                        text = sub,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } ?: run {
                    Text(
                        text = recording.durationFormatted,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }

            // Badge (top-left)
            RecordingBadge(
                cardType = cardType,
                expirationDate = recording.expirationDate,
                sx = sx,
                sy = sy,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sx(12), top = sy(12))
            )
            } // End of content wrapper Box with alpha
        }

        // Progress bar (below card)
        if (cardType in listOf(
                RecordingCardType.OBEJRZANE,
                RecordingCardType.OGLADAJ,
                RecordingCardType.NAGRYWANIE
            )
        ) {
            Spacer(modifier = Modifier.height(sy(4)))
            RecordingProgressBar(
                progress = recording.watchProgress,
                sx = sx,
                sy = sy,
                modifier = Modifier.width(cardWidth)
            )
        }
    }
}

/**
 * Recording Card for Series Bundle
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SeriesBundleCard(
    bundle: SeriesBundle,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    val cardWidth = sx(410)
    val cardHeight = sy(232)
    val cornerRadius = sx(8)

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (isFocused) Modifier.border(
                    width = sx(6),
                    color = ColorFocusBorder,
                    shape = RoundedCornerShape(cornerRadius)
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        // Cover image
        AsyncImage(
            model = bundle.imageUrl ?: "https://via.placeholder.com/410x232",
            contentDescription = bundle.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sy(120))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Channel logo (bottom-left)
        bundle.channelLogoUrl?.let { logoUrl ->
            AsyncImage(
                model = logoUrl,
                contentDescription = "Channel",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sx(12), bottom = sy(12))
                    .width(sx(89))
                    .height(sy(71)),
                contentScale = ContentScale.Fit
            )
        }

        // Title and episode count (bottom-right)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = sx(12), bottom = sy(12), start = sx(110))
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = bundle.title,
                color = Color.White,
                fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = bundle.cardSubtitle,  // "20 odcinków (11 h)"
                color = Color.White.copy(alpha = 0.7f),
                fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }

        // Series badge (top-left) - Stack icon
        SeriesBadge(
            episodeCount = bundle.episodeCount,
            sx = sx,
            sy = sy,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = sx(12), top = sy(12))
        )
    }
}

/**
 * Recording Badge Component
 *
 * Badge variants:
 * - OBEJRZANE/OGLADAJ: ✓ + "Oglądaj do DD.MM"
 * - NAGRYWANIE: Red border + "REC"
 * - ZAPLANOWANE: White border + "REC"
 */
@Composable
private fun RecordingBadge(
    cardType: RecordingCardType,
    expirationDate: java.time.Instant?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    when (cardType) {
        RecordingCardType.OBEJRZANE, RecordingCardType.OGLADAJ -> {
            // Checkmark + expiration date badge
            Row(
                modifier = modifier
                    .height(sy(40))
                    .background(ColorBadgeBg, RoundedCornerShape(sx(4)))
                    .padding(horizontal = sx(12), vertical = sy(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(8))
            ) {
                // Checkmark icon
                Text(
                    text = "✓",
                    color = ColorCheckmark,
                    fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold
                )

                // Expiration date
                expirationDate?.let { date ->
                    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                        .withZone(ZoneId.systemDefault())
                    Text(
                        text = "Oglądaj do ${formatter.format(date)}",
                        color = Color.White,
                        fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        RecordingCardType.NAGRYWANIE -> {
            // Red REC badge
            Row(
                modifier = modifier
                    .height(sy(40))
                    .border(sx(3), ColorRecRed, RoundedCornerShape(sx(4)))
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(sx(4)))
                    .padding(horizontal = sx(12), vertical = sy(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(6))
            ) {
                // Red dot
                Box(
                    modifier = Modifier
                        .size(sx(12))
                        .background(ColorRecRed, RoundedCornerShape(sx(6)))
                )

                Text(
                    text = "REC",
                    color = ColorRecRed,
                    fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        RecordingCardType.ZAPLANOWANE -> {
            // White REC badge
            Row(
                modifier = modifier
                    .height(sy(40))
                    .border(sx(3), ColorRecWhite, RoundedCornerShape(sx(4)))
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(sx(4)))
                    .padding(horizontal = sx(12), vertical = sy(8)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(6))
            ) {
                // White dot
                Box(
                    modifier = Modifier
                        .size(sx(12))
                        .background(ColorRecWhite, RoundedCornerShape(sx(6)))
                )

                Text(
                    text = "REC",
                    color = ColorRecWhite,
                    fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        RecordingCardType.SERIA -> {
            // Series badge handled by SeriesBadge component
        }
    }
}

/**
 * Series Badge Component (Stack icon + episode count)
 */
@Composable
private fun SeriesBadge(
    episodeCount: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(sy(40))
            .background(ColorBadgeBg, RoundedCornerShape(sx(4)))
            .padding(horizontal = sx(12), vertical = sy(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(8))
    ) {
        // Stack icon (layered squares representing series)
        Box(
            modifier = Modifier.size(sx(24), sy(20))
        ) {
            // Back layer
            Box(
                modifier = Modifier
                    .size(sx(18), sy(14))
                    .offset(x = sx(6), y = sy(0))
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(sx(2)))
            )
            // Middle layer
            Box(
                modifier = Modifier
                    .size(sx(18), sy(14))
                    .offset(x = sx(3), y = sy(3))
                    .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(sx(2)))
            )
            // Front layer
            Box(
                modifier = Modifier
                    .size(sx(18), sy(14))
                    .offset(x = sx(0), y = sy(6))
                    .background(Color.White, RoundedCornerShape(sx(2)))
            )
        }

        Text(
            text = "$episodeCount odc.",
            color = Color.White,
            fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Progress Bar Component (8px height)
 */
@Composable
private fun RecordingProgressBar(
    progress: Float,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(sy(8))
            .clip(RoundedCornerShape(sx(4)))
            .background(ColorProgressBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                .background(ColorProgressFill, RoundedCornerShape(sx(4)))
        )
    }
}

/**
 * Unified RecordingContentCard that handles both Individual and Series
 */
@Composable
fun RecordingContentCard(
    content: RecordingContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    when (content) {
        is RecordingContent.Individual -> {
            RecordingCard(
                recording = content.recording,
                cardType = content.recording.toCardType(),
                isFocused = isFocused,
                focusRequester = focusRequester,
                onFocusChange = onFocusChange,
                onClick = onClick,
                sx = sx,
                sy = sy,
                modifier = modifier
            )
        }
        is RecordingContent.Series -> {
            SeriesBundleCard(
                bundle = content.bundle,
                isFocused = isFocused,
                focusRequester = focusRequester,
                onFocusChange = onFocusChange,
                onClick = onClick,
                sx = sx,
                sy = sy,
                modifier = modifier
            )
        }
    }
}
