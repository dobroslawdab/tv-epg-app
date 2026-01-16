package com.uxellence.tv.v3.moviedetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uxellence.tv.v3.R
import com.uxellence.tv.v3.VodSlideData
import kotlinx.coroutines.delay

/**
 * MovieDetailScreen - Pełnoekranowy widok szczegółów filmu
 *
 * Wyświetla się po naciśnięciu OK na sliderze KINO PLAY.
 * Style identyczne jak na sliderze (ContentLabelV2, SliderMetadataRowV2, etc.)
 *
 * @param item Dane filmu z VodSlideData
 * @param onBackPressed Callback dla przycisku BACK
 * @param onRentClicked Callback dla przycisku "Wypożycz" - przechodzi do PurchaseScreen
 * @param onTrailerClicked Callback dla przycisku "Zwiastun"
 * @param onPreviewClicked Callback dla przycisku "Zobacz fragment"
 * @param onMoreInfoClicked Callback dla przycisku "Więcej informacji"
 */
@Composable
fun MovieDetailScreen(
    item: VodSlideData,
    onBackPressed: () -> Unit,
    onRentClicked: () -> Unit = {},
    onTrailerClicked: () -> Unit = {},
    onPreviewClicked: () -> Unit = {},
    onMoreInfoClicked: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int): Dp = (px * scaleX).dp
    fun sy(px: Int): Dp = (px * scaleY).dp

    // Focus management for buttons
    var focusedButtonIndex by remember { mutableIntStateOf(0) }
    val buttonFocusRequesters = remember { List(4) { FocusRequester() } }

    // Request focus on first button when screen loads
    LaunchedEffect(Unit) {
        delay(100)
        buttonFocusRequesters.getOrNull(0)?.requestFocus()
    }

    // Button labels
    val buttons = listOf(
        "Wypożycz: ${item.price}",
        "Zwiastun",
        "Zobacz fragment",
        "Więcej informacji"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF281443)) // Dark purple from slider
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onBackPressed()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (focusedButtonIndex > 0) {
                            focusedButtonIndex--
                            buttonFocusRequesters.getOrNull(focusedButtonIndex)?.requestFocus()
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (focusedButtonIndex < buttons.size - 1) {
                            focusedButtonIndex++
                            buttonFocusRequesters.getOrNull(focusedButtonIndex)?.requestFocus()
                        }
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        when (focusedButtonIndex) {
                            0 -> onRentClicked()
                            1 -> onTrailerClicked()
                            2 -> onPreviewClicked()
                            3 -> onMoreInfoClicked()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Layer 1: Backdrop (right-aligned, full height)
        AsyncImage(
            model = item.backgroundUrl,
            contentDescription = "Backdrop",
            modifier = Modifier
                .fillMaxHeight()
                .align(Alignment.CenterEnd),
            contentScale = ContentScale.FillHeight
        )

        // Layer 2: Glow overlay (slide_glow_left.png) - same as slider
        Image(
            painter = painterResource(id = R.drawable.slide_glow_left),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Layer 3: Additional gradient for text readability (same as slider)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(sx(800))
                .align(Alignment.CenterStart)
                .offset(x = sx(400))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF281443),
                            Color.Transparent
                        )
                    )
                )
        )

        // Layer 4: Content (same layout as slider)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sx(164), top = sy(200), bottom = sy(40))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // EMBLEM: Logo + labels (same as slider)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sx(30))
                ) {
                    // Logo KINO PLAY
                    Image(
                        painter = painterResource(id = R.drawable.logo_kino_pay),
                        contentDescription = "KINO PLAY"
                    )

                    // Content labels (Premiera premium + 4K) - EXACT same as slider
                    ContentLabelV2(
                        label = "Premiera premium",
                        show4K = true,
                        sx = ::sx,
                        sy = ::sy
                    )
                }

                Spacer(modifier = Modifier.height(sy(24)))

                // Title - EXACT same style as slider
                Text(
                    text = item.title,
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(48).value.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = sy(56).value.sp,
                    modifier = Modifier.widthIn(max = sx(600))
                )

                Spacer(modifier = Modifier.height(sy(16)))

                // Metadata row - EXACT same style as slider
                SliderMetadataRowV2(
                    genre = item.genre,
                    duration = item.duration,
                    ageRating = item.ageRating,
                    showKrritImage = true,
                    sx = ::sx,
                    sy = ::sy
                )

                Spacer(modifier = Modifier.height(sy(16)))

                // Description - EXACT same style as slider
                Text(
                    text = item.description,
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(24).value.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = sy(32).value.sp,
                    modifier = Modifier.widthIn(max = sx(550))
                )

                // 100px spacing between description and buttons
                Spacer(modifier = Modifier.height(sy(100)))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(24))
                ) {
                    buttons.forEachIndexed { index, label ->
                        ActionButton(
                            label = label,
                            isFocused = focusedButtonIndex == index,
                            focusRequester = buttonFocusRequesters[index],
                            isPrimary = index == 0,
                            sx = ::sx,
                            sy = ::sy,
                            onFocusChanged = { isFocused ->
                                if (isFocused) focusedButtonIndex = index
                            },
                            onClick = {
                                when (index) {
                                    0 -> onRentClicked()
                                    1 -> onTrailerClicked()
                                    2 -> onPreviewClicked()
                                    3 -> onMoreInfoClicked()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Content Label component - EXACT same as slider ContentLabelV2
 * Shows "Premiera premium" label with "4K" badge
 */
@Composable
private fun ContentLabelV2(
    label: String = "Premiera premium",
    show4K: Boolean = true,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        modifier = Modifier.height(sy(40)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main label - white background
        Box(
            modifier = Modifier
                .height(sy(40))
                .background(
                    color = Color(0xFFEEEEEE),
                    shape = if (show4K) {
                        RoundedCornerShape(topStart = sx(4), bottomStart = sx(4))
                    } else {
                        RoundedCornerShape(sx(4))
                    }
                )
                .padding(horizontal = sx(16)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color(0xFF48227C), // Purple
                fontSize = sy(20).value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }

        // 4K badge - purple background
        if (show4K) {
            Box(
                modifier = Modifier
                    .height(sy(40))
                    .width(sx(58))
                    .background(
                        color = Color(0xFF5F2DA4), // Purple container
                        shape = RoundedCornerShape(topEnd = sx(4), bottomEnd = sx(4))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "4K",
                    color = Color(0xFF5FEDD4), // Aqua
                    fontSize = sy(20).value.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

/**
 * Metadata row - EXACT same as slider SliderMetadataRowV2
 * Shows: genre | duration | age | KRRIT labels
 */
@Composable
private fun SliderMetadataRowV2(
    genre: String?,
    duration: String?,
    ageRating: String?,
    showKrritImage: Boolean = false,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val metadataColor = Color(0x66EEEEEE) // 40% opacity white (disabled)
    val dividerColor = Color(0x66EEEEEE) // Same as text

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16))
    ) {
        // Genre
        if (!genre.isNullOrBlank()) {
            Text(
                text = genre,
                color = metadataColor,
                fontSize = sy(20).value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(sy(24))
                    .background(dividerColor)
            )
        }

        // Duration
        if (!duration.isNullOrBlank()) {
            Text(
                text = duration,
                color = metadataColor,
                fontSize = sy(20).value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )

            if (!ageRating.isNullOrBlank() || showKrritImage) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(sy(24))
                        .background(dividerColor)
                )
            }
        }

        // Age rating
        if (!ageRating.isNullOrBlank()) {
            Text(
                text = ageRating,
                color = metadataColor,
                fontSize = sy(20).value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )

            if (showKrritImage) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(sy(24))
                        .background(dividerColor)
                )
            }
        }

        // KRRIT Labels image
        if (showKrritImage) {
            Image(
                painter = painterResource(id = R.drawable.krrit_label_set),
                contentDescription = "KRRIT Labels",
                modifier = Modifier.height(sy(24)),
                contentScale = ContentScale.FillHeight
            )
        }
    }
}

/**
 * Action button for movie detail screen
 */
@Composable
private fun ActionButton(
    label: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    isPrimary: Boolean,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF5FEDD4) else Color(0x33EEEEEE),
        animationSpec = tween(150),
        label = "buttonBgColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE),
        animationSpec = tween(150),
        label = "buttonTextColor"
    )

    Box(
        modifier = Modifier
            .height(sy(72))
            .clip(RoundedCornerShape(sx(8)))
            .background(backgroundColor)
            .focusRequester(focusRequester)
            .onFocusChanged { state -> onFocusChanged(state.isFocused) }
            .focusable()
            .padding(horizontal = sx(32)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = sy(24).value.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.48).sp
        )
    }
}
