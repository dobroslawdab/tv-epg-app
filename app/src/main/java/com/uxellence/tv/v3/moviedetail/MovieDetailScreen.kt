package com.uxellence.tv.v3.moviedetail

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.uxellence.tv.v3.rental.RentalManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * MovieDetailScreen - Pełnoekranowy widok szczegółów filmu wg Figmy
 *
 * Layout wg Figmy (node 478-7670):
 * - Zegar w prawym górnym rogu
 * - Tytuł 64px
 * - Metadata: genre | duration | year | country | age | Filmweb + rating
 * - Opis 320px wysokość, scrollowalny
 * - Szczegóły: Dźwięk, Napisy, Reżyser, Obsada
 * - Poster 225x320px po prawej
 * - Scroll indicator (pionowe kropki)
 * - 3 przyciski (bez "Więcej informacji")
 *
 * @param item Dane filmu z VodSlideData
 * @param onBackPressed Callback dla przycisku BACK
 * @param onRentClicked Callback dla przycisku "Wypożycz"
 * @param onTrailerClicked Callback dla przycisku "Zwiastun"
 * @param onPreviewClicked Callback dla przycisku "Zobacz fragment"
 */
@Composable
fun MovieDetailScreen(
    item: VodSlideData,
    onBackPressed: () -> Unit,
    onRentClicked: () -> Unit = {},
    onWatchClicked: () -> Unit = {},
    onTrailerClicked: () -> Unit = {},
    onPreviewClicked: () -> Unit = {},
    onMoreInfoClicked: () -> Unit = {} // Zachowujemy dla kompatybilności, ale nie używamy
) {
    // Reactive rental state — recomposes when RentalManager.rentals changes (e.g. after
    // rental confirmation or debug clear). VodSlideData has no stable id field, so we
    // key rentals by movie title — same convention used in MainActivity.onConfirmPurchase.
    val rentalsMap = RentalManager.rentals.value
    val rentalKey = item.title
    val isRented = remember(rentalsMap, rentalKey) {
        (rentalsMap[rentalKey] ?: 0L) > System.currentTimeMillis()
    }
    val rentalExpiresAt = rentalsMap[rentalKey]
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int): Dp = (px * scaleX).dp
    fun sy(px: Int): Dp = (px * scaleY).dp

    // Focus management for 3 buttons
    var focusedButtonIndex by remember { mutableIntStateOf(0) }
    val buttonFocusRequesters = remember { List(3) { FocusRequester() } }

    // Scroll state for description
    val descriptionScrollState = rememberScrollState()

    // Calculate scroll position (0-2) for indicator
    val scrollPosition by remember {
        derivedStateOf {
            if (descriptionScrollState.maxValue == 0) 0
            else {
                val progress = descriptionScrollState.value.toFloat() / descriptionScrollState.maxValue
                when {
                    progress < 0.33f -> 0
                    progress < 0.66f -> 1
                    else -> 2
                }
            }
        }
    }

    // Request focus on first button when screen loads
    LaunchedEffect(Unit) {
        delay(100)
        buttonFocusRequesters.getOrNull(0)?.requestFocus()
    }

    // Button labels (2 buttons — "Zobacz fragment" usunięty).
    // After rental, the first button becomes "Oglądaj" (no price suffix).
    val buttons = listOf(
        if (isRented) "Oglądaj" else "Wypożycz: ${item.price}",
        "Zwiastun"
    )

    // Mock data fallbacks for missing fields
    val displayFilmwebRating = item.filmwebRating ?: 6.7
    val displayAudioLanguages = item.audioLanguages ?: "angielski  |  polski  |  hiszpański  |  niemiecki"
    val displaySubtitleLanguages = item.subtitleLanguages ?: "angielski  |  polski"
    val displayDirector = item.director ?: "Álex Pina"
    val displayCast = item.cast ?: "Úrsula Corberó, Álvaro Morte, Alba Flores, Miguel Herrán, Pedro Alonso"

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
                            0 -> if (isRented) onWatchClicked() else onRentClicked()
                            1 -> onTrailerClicked()
                            2 -> onPreviewClicked()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Layer 1: Backdrop (right-aligned, full height) — tylko gdy URL niepusty
        if (item.backgroundUrl.isNotBlank()) {
            AsyncImage(
                model = item.backgroundUrl,
                contentDescription = "Backdrop",
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
                contentScale = ContentScale.FillHeight
            )
        }

        // Layer 2: Glow overlay (slide_glow_left.png) - same as slider
        Image(
            painter = painterResource(id = R.drawable.slide_glow_left),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )

        // Layer 3: Additional gradient for text readability
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(sx(900))
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

        // Layer 4: Clock in top-right corner
        TimeLabel(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = sy(60), end = sx(100)),
            sx = ::sx,
            sy = ::sy
        )

        // Layer 5: Main content area
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sx(80), top = sy(168), end = sx(100), bottom = sy(40)),
            horizontalArrangement = Arrangement.spacedBy(sx(64))
        ) {
            // LEFT column — Poster (przeniesiony z prawej strony)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MoviePoster(
                    posterUrl = item.posterUrl,
                    sx = ::sx,
                    sy = ::sy
                )
            }

            // CENTER column - Text content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Title - 64px, Medium
                Text(
                    text = item.title,
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(64).value.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = sy(88).value.sp
                )

                Spacer(modifier = Modifier.height(sy(8)))

                // Metadata row - genre | duration | year | country | age | Filmweb + rating
                MetadataRow(
                    genre = item.genre,
                    duration = item.duration,
                    year = item.year,
                    country = item.country,
                    ageRating = item.ageRating,
                    filmwebRating = displayFilmwebRating,
                    sx = ::sx,
                    sy = ::sy
                )

                Spacer(modifier = Modifier.height(sy(32)))

                // Description - 28px, Medium, 320px max height, scrollable
                Box(
                    modifier = Modifier
                        .widthIn(max = sx(981))
                        .heightIn(max = sy(320))
                ) {
                    Text(
                        text = item.description,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(28).value.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = sy(40).value.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .verticalScroll(descriptionScrollState)
                    )
                }

                Spacer(modifier = Modifier.height(sy(48)))

                // Details section - Dźwięk, Napisy, Reżyser, Obsada (with mock fallbacks)
                MovieDetailsSection(
                    audioLanguages = displayAudioLanguages,
                    subtitleLanguages = displaySubtitleLanguages,
                    director = displayDirector,
                    cast = displayCast,
                    sx = ::sx,
                    sy = ::sy
                )

                Spacer(modifier = Modifier.weight(1f))

                // Rental countdown info — shown above buttons when movie is rented
                if (isRented && rentalExpiresAt != null) {
                    RentalCountdownInfo(
                        expiresAt = rentalExpiresAt,
                        totalDurationMs = RentalManager.DEFAULT_RENTAL_DURATION_MS,
                        sx = ::sx,
                        sy = ::sy,
                        modifier = Modifier.padding(bottom = sy(20))
                    )
                }

                // Action buttons at bottom (raised 40px)
                Row(
                    modifier = Modifier.padding(bottom = sy(40)),
                    horizontalArrangement = Arrangement.spacedBy(sx(24))
                ) {
                    buttons.forEachIndexed { index, label ->
                        ActionButton(
                            label = label,
                            isFocused = focusedButtonIndex == index,
                            focusRequester = buttonFocusRequesters[index],
                            sx = ::sx,
                            sy = ::sy,
                            onFocusChanged = { isFocused ->
                                if (isFocused) focusedButtonIndex = index
                            },
                            onClick = {
                                when (index) {
                                    0 -> if (isRented) onWatchClicked() else onRentClicked()
                                    1 -> onTrailerClicked()
                                }
                            }
                        )
                    }
                }
            }

            // RIGHT column — Scroll indicator (poster przeniesiony na lewą)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScrollIndicator(
                    currentPosition = scrollPosition,
                    totalPositions = 3,
                    sx = ::sx,
                    sy = ::sy,
                    modifier = Modifier.padding(top = sy(86))
                )
            }
        }
    }
}

/**
 * TimeLabel - Zegar w prawym górnym rogu (wg Figmy)
 * 32px, Bold, #EEEEEE
 */
@Composable
private fun TimeLabel(
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTime = sdf.format(Date())
            delay(1000)
        }
    }

    Text(
        text = currentTime,
        color = Color(0xFFEEEEEE),
        fontSize = sy(32).value.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.64.sp,
        modifier = modifier
    )
}

/**
 * MetadataRow - Metadata z dividerami wg Figmy
 * genre | duration | year | country | age | Filmweb + rating
 * 20px, Bold, #EEEEEE
 */
@Composable
private fun MetadataRow(
    genre: String?,
    duration: String?,
    year: String?,
    country: String?,
    ageRating: String?,
    filmwebRating: Double, // Always displayed (mock fallback if null)
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16))
    ) {
        // Genre
        if (!genre.isNullOrBlank()) {
            MetadataText(text = genre, sy = sy)
            MetadataDivider(sy = sy)
        }

        // Duration
        if (!duration.isNullOrBlank()) {
            MetadataText(text = duration, sy = sy)
            MetadataDivider(sy = sy)
        }

        // Year
        if (!year.isNullOrBlank()) {
            MetadataText(text = year, sy = sy)
            MetadataDivider(sy = sy)
        }

        // Country
        if (!country.isNullOrBlank()) {
            MetadataText(text = country, sy = sy)
            MetadataDivider(sy = sy)
        }

        // Age rating
        if (!ageRating.isNullOrBlank()) {
            MetadataText(text = ageRating, sy = sy)
            MetadataDivider(sy = sy)
        }

        // Filmweb logo + rating (always displayed)
        FilmwebRating(
            rating = filmwebRating,
            sx = sx,
            sy = sy
        )
    }
}

@Composable
private fun MetadataText(
    text: String,
    sy: (Int) -> Dp
) {
    Text(
        text = text,
        color = Color(0xFFEEEEEE),
        fontSize = sy(20).value.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp
    )
}

@Composable
private fun MetadataDivider(sy: (Int) -> Dp) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(sy(24))
            .background(Color(0xFFEEEEEE))
    )
}

/**
 * FilmwebRating - Logo Filmweb + ocena z gwiazdką
 * Wg Figmy: logo Filmweb (100x24) + ★ rating / 10
 */
@Composable
private fun FilmwebRating(
    rating: Double,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16))
    ) {
        // Filmweb logo
        Image(
            painter = painterResource(id = R.drawable.filmweb_logo),
            contentDescription = "Filmweb",
            modifier = Modifier
                .width(sx(100))
                .height(sy(24)),
            contentScale = ContentScale.Fit
        )

        // Star + rating
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(4))
        ) {
            // Star icon
            Image(
                painter = painterResource(id = R.drawable.ic_rating),
                contentDescription = null,
                modifier = Modifier.size(sy(24)),
                contentScale = ContentScale.Fit
            )

            // Rating text: "6,7 / 10"
            Text(
                text = String.format(Locale.getDefault(), "%.1f", rating).replace('.', ','),
                color = Color(0xFFEEEEEE),
                fontSize = sy(20).value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            Text(
                text = " / 10",
                color = Color(0xFFEEEEEE),
                fontSize = sy(16).value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.32.sp
            )
        }
    }
}

/**
 * MovieDetailsSection - Sekcja szczegółów wg Figmy
 * Dźwięk, Napisy, Reżyser, Obsada
 * Labels: 20px, Bold, 60% opacity, 160px width
 * Values: 20px, Bold, 100% opacity
 * All fields always displayed (mock fallback if null)
 */
@Composable
private fun MovieDetailsSection(
    audioLanguages: String,
    subtitleLanguages: String,
    director: String,
    cast: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(sy(24))
    ) {
        // Dźwięk
        DetailRow(
            label = "Dźwięk:",
            value = audioLanguages,
            sx = sx,
            sy = sy
        )

        // Napisy
        DetailRow(
            label = "Napisy",
            value = subtitleLanguages,
            sx = sx,
            sy = sy
        )

        // Reżyser
        DetailRow(
            label = "Reżyser:",
            value = director,
            sx = sx,
            sy = sy
        )

        // Obsada
        DetailRow(
            label = "Obsada:",
            value = cast,
            sx = sx,
            sy = sy,
            multiline = true
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    multiline: Boolean = false
) {
    Row(
        verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically
    ) {
        // Label - 160px width, 60% opacity
        Text(
            text = label,
            color = Color(0xFFEEEEEE).copy(alpha = 0.6f),
            fontSize = sy(20).value.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            modifier = Modifier.width(sx(160)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Value - 100% opacity
        Text(
            text = value,
            color = Color(0xFFEEEEEE),
            fontSize = sy(20).value.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            lineHeight = sy(28).value.sp
        )
    }
}

/**
 * ScrollIndicator - Pionowe kropki wg Figmy
 * 3 pozycje, aktywna = filled, nieaktywne = outline
 */
@Composable
private fun ScrollIndicator(
    currentPosition: Int,
    totalPositions: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(sx(32)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(16))
    ) {
        repeat(totalPositions) { index ->
            Box(
                modifier = Modifier
                    .size(sy(12))
                    .clip(CircleShape)
                    .background(
                        if (index == currentPosition) Color(0xFFEEEEEE)
                        else Color(0xFFEEEEEE).copy(alpha = 0.3f)
                    )
            )
        }
    }
}

/**
 * MoviePoster - Poster filmu wg Figmy
 * 225x320px z maską/border-radius
 */
@Composable
private fun MoviePoster(
    posterUrl: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    AsyncImage(
        model = posterUrl,
        contentDescription = "Movie Poster",
        modifier = Modifier
            .width(sx(225))
            .height(sy(320))
            .clip(RoundedCornerShape(sx(8))),
        contentScale = ContentScale.Crop
    )
}

/**
 * ActionButton - Przycisk akcji wg Figmy
 * 72px wysokość, 8px radius
 * Focused: bg #5FEDD4, text #48227C
 * Default: bg #EEEEEE 20%, text #EEEEEE
 */
@Composable
private fun ActionButton(
    label: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF5FEDD4) else Color(0xFFEEEEEE).copy(alpha = 0.2f),
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

/**
 * Rental countdown — text "Możesz oglądać przez Xh Ym" plus a horizontal bar that
 * shrinks from the LEFT edge over time (right edge is anchored). At full rental
 * (just purchased) the bar is 100% wide; at expiry it has shrunk to 0.
 *
 * Re-renders once per minute via a tick state read by `derivedStateOf`.
 */
@Composable
private fun RentalCountdownInfo(
    expiresAt: Long,
    totalDurationMs: Long,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(expiresAt) {
        while (true) {
            tick++
            delay(60_000L)
        }
    }

    val remainingMs by remember(expiresAt, tick) {
        derivedStateOf { (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L) }
    }
    val progress = (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

    val totalHours = remainingMs / (60L * 60 * 1000)
    val totalMinutes = (remainingMs / (60L * 1000)) % 60
    val timeLabel = when {
        totalHours >= 1 -> "${totalHours}h ${totalMinutes}m"
        else -> "${totalMinutes}m"
    }

    // 48 squares — one per hour. Each hour that has already passed becomes 10% white;
    // remaining hours are full white. ceil() so a partially-elapsed hour still counts
    // as "not yet expired" until it actually runs out.
    val totalHourSlots = (totalDurationMs / (60L * 60 * 1000)).toInt().coerceAtLeast(1)
    val remainingHourSlots = ((remainingMs + 60L * 60 * 1000 - 1) / (60L * 60 * 1000))
        .toInt()
        .coerceIn(0, totalHourSlots)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(sy(8))) {
        Text(
            text = "Możesz oglądać przez $timeLabel",
            color = Color(0xFFEEEEEE),
            fontSize = sy(20).value.sp,
            fontWeight = FontWeight.Medium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(sx(2))) {
            // Squares depleted FROM THE LEFT: leftmost N slots are "elapsed" (10% white),
            // rightmost remainingHourSlots are "active" (full white).
            val elapsedSlots = totalHourSlots - remainingHourSlots
            repeat(totalHourSlots) { idx ->
                val isElapsed = idx < elapsedSlots
                Box(
                    modifier = Modifier
                        .size(sx(8), sy(8))
                        .clip(RoundedCornerShape(sx(1)))
                        .background(
                            if (isElapsed) Color(0x1AFFFFFF)  // white 10%
                            else Color(0xFFFFFFFF)            // white 100%
                        )
                )
            }
        }
    }
}
