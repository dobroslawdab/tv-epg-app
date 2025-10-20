package com.uxellence.tv.v3.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * MiniCard - Converted from Figma (Component Set "mini")
 * Figma File: Hydepark
 * Node ID: 5972-6468
 *
 * Component with two variants:
 * - Default: 887x503px, no border, metadata/description hidden
 * - Focused: 887x661px, 6px aqua border, metadata/description visible
 *
 * Features:
 * - Background image with gradient overlay
 * - Channel logo (top-left)
 * - Title (always visible)
 * - Metadata row (visible on focus)
 * - Description text (visible on focus, max 2 lines)
 * - Smooth animations on focus state change
 *
 * @param imageUrl Background image URL
 * @param channelLogoResId Channel logo drawable resource ID (use 0 for URL-based logo)
 * @param channelLogoUrl Channel logo URL (used if channelLogoResId is 0)
 * @param title Content title (e.g., "Grand Budapest Hotel")
 * @param category Category text (e.g., "program informacyjny")
 * @param duration Duration text (e.g., "25 min")
 * @param year Year text (e.g., "2020 r.")
 * @param country Country text (e.g., "Polska")
 * @param ageRating Age rating text (e.g., "7 lat")
 * @param description Long description text (max 2 lines when focused)
 * @param isFocused Whether the card is currently focused
 * @param modifier Modifier for the component
 */
@Composable
fun MiniCard(
    imageUrl: String,
    channelLogoResId: Int = 0,
    channelLogoUrl: String = "",
    title: String,
    category: String,
    duration: String,
    year: String,
    country: String,
    ageRating: String,
    description: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Animations
    val cardHeight by animateDpAsState(
        targetValue = if (isFocused) sy(661) else sy(503),
        animationSpec = tween(durationMillis = 350),
        label = "cardHeight"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) sx(6) else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "borderWidth"
    )

    val metadataOpacity by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "metadataOpacity"
    )

    val descriptionOpacity by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "descriptionOpacity"
    )

    Box(
        modifier = modifier
            .width(sx(887))
            .height(cardHeight)
            .clip(RoundedCornerShape(sx(20)))
            .background(
                color = if (isFocused) Color(0xFF5B3987) else Color(0xFF5A3888) // Figma background color
            )
            .border(
                width = borderWidth,
                color = if (isFocused) Color(0xFF5FEDD4) else Color.Transparent,
                shape = RoundedCornerShape(sx(20))
            )
    ) {
        // Background image
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(499)),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay (bottom to top: dark to transparent)
        // Figma position: x: -5, y: 253 (extends slightly beyond card edges)
        Box(
            modifier = Modifier
                .width(sx(892))  // Figma: 892px (wider than card to extend left)
                .height(sy(250))
                .align(Alignment.TopStart)
                .offset(x = sx(-5), y = sy(253))  // Figma exact position
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isFocused) {
                            listOf(
                                Color(0xFF5B3987), // rgba(91, 57, 135, 1) - Figma focused
                                Color(0x005A3887)  // rgba(90, 56, 135, 0)
                            )
                        } else {
                            listOf(
                                Color(0xFF000000), // rgba(0, 0, 0, 1) - Figma default
                                Color(0x005A3887)  // rgba(90, 56, 135, 0)
                            )
                        },
                        startY = Float.POSITIVE_INFINITY,  // Bottom (0deg in Figma = bottom-to-top)
                        endY = 0f  // Top
                    )
                )
        )

        // Content overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(29), bottom = sy(29))
                .width(sx(845)),
            verticalArrangement = Arrangement.spacedBy(sy(31))
        ) {
            // Frame 99: Channel logo + Text content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(425))
            ) {
                // Channel logo (top-left)
                if (channelLogoResId != 0) {
                    AsyncImage(
                        model = channelLogoResId,
                        contentDescription = "Channel logo",
                        modifier = Modifier
                            .size(sx(168), sy(168))
                            .align(Alignment.TopStart),
                        contentScale = ContentScale.Fit
                    )
                } else if (channelLogoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = channelLogoUrl,
                        contentDescription = "Channel logo",
                        modifier = Modifier
                            .size(sx(168), sy(168))
                            .align(Alignment.TopStart),
                        contentScale = ContentScale.Fit
                    )
                }

                // Text content (Frame 63)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(sy(14))
                ) {
                    // Title (always visible)
                    Text(
                        text = title,
                        style = TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default, // Use Manrope if available
                            fontWeight = FontWeight.Bold,
                            fontSize = (46 * sy(1).value / 1).sp,
                            lineHeight = (46 * 1.91f * sy(1).value / 1).sp,
                            color = Color(0xFFEEEEEE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Metadata row (Frame 62) - visible only on focus
                    if (metadataOpacity > 0f) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .wrapContentHeight(),
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MetadataText(category, metadataOpacity, sy(1).value)
                            MetadataDivider(metadataOpacity, sx(2), sy(24))
                            MetadataText(duration, metadataOpacity, sy(1).value)
                            MetadataDivider(metadataOpacity, sx(2), sy(24))
                            MetadataText(year, metadataOpacity, sy(1).value)
                            MetadataDivider(metadataOpacity, sx(2), sy(24))
                            MetadataText(country, metadataOpacity, sy(1).value)
                            MetadataDivider(metadataOpacity, sx(2), sy(24))
                            MetadataText(ageRating, metadataOpacity, sy(1).value)
                        }
                    }

                    // Description text - visible only on focus
                    if (descriptionOpacity > 0f) {
                        Text(
                            text = description,
                            style = TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Default, // Use Manrope if available
                                fontWeight = FontWeight.Medium,
                                fontSize = (28 * sy(1).value / 1).sp,
                                lineHeight = (28 * 1.43f * sy(1).value / 1).sp,
                                color = Color(0xFFEEEEEE).copy(alpha = descriptionOpacity)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(sy(82)),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataText(
    text: String,
    opacity: Float,
    scaleValue: Float
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Default, // Use Manrope if available
            fontWeight = FontWeight.Bold,
            fontSize = (20 * scaleValue / 1).sp,
            lineHeight = (20 * 1.4f * scaleValue / 1).sp,
            letterSpacing = (20 * 0.02f * scaleValue / 1).sp,
            color = Color(0xCCEEEEEE).copy(alpha = opacity) // rgba(238, 238, 238, 0.8)
        )
    )
}

@Composable
private fun MetadataDivider(
    opacity: Float,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(Color(0xCCEEEEEE).copy(alpha = opacity)) // rgba(238, 238, 238, 0.8)
    )
}
