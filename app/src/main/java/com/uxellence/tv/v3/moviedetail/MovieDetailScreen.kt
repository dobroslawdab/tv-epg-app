package com.uxellence.tv.v3.moviedetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
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
    onMoreInfoClicked: () -> Unit = {}, // Zachowujemy dla kompatybilności, ale nie używamy
    onNavigatePrev: (() -> Unit)? = null,  // WIDEO: LEFT from leftmost button → previous sibling
    onNavigateNext: (() -> Unit)? = null,  // WIDEO: RIGHT from rightmost button → next sibling
    // Nadpisanie przycisków (tryb WIDEO) — np. demo live: program przyszły ma
    // [Nagraj, Przypomnij] zamiast [Oglądaj, Do obejrzenia]. null = standard.
    customButtons: List<String>? = null,
    onCustomButtonClicked: ((index: Int) -> Unit)? = null
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int): Dp = (px * scaleX).dp
    fun sy(px: Int): Dp = (px * scaleY).dp

    // Focus management for 3 buttons
    var focusedButtonIndex by remember { mutableIntStateOf(0) }
    val buttonFocusRequesters = remember { List(3) { FocusRequester() } }

    // WIDEO sibling-browse mode: when user presses LEFT/RIGHT from the edge button (and
    // siblings are available), buttons hide, the bottom hint appears, and LEFT/RIGHT
    // continue to swap films. OK exits — buttons come back, hint hides, focus restored.
    // No `item` key: state must persist across sibling swaps (item changes, mode stays).
    var browseMode by remember { mutableStateOf(false) }
    val browseFocusRequester = remember { FocusRequester() }
    // Last pressed direction within browseMode — drives the brief chevron highlight in
    // the bottom hint. Cleared after a short delay (LaunchedEffect below) so the press
    // animation feels like a tap.
    var hintPressed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hintPressed) {
        if (hintPressed != null) {
            delay(180)
            hintPressed = null
        }
    }

    // Test toggle: parallax card-stacking transition between siblings (Key.O / Key.0 cycles).
    // -1 = sliding to previous, +1 = sliding to next, 0 = no animation pending.
    var useParallaxAnimation by remember { mutableStateOf(false) }
    var slideDirection by remember { mutableStateOf(0) }

    // Sibling-nav lock during parallax animation. Pressing the opposite direction
    // mid-flight caused state thrash / crashes; lock both LEFT and RIGHT for the
    // outgoing animation duration. Matches `tween(550)` in AnimatedContent + buffer.
    val parallaxLockMs = 600L
    var isAnimatingSwap by remember { mutableStateOf(false) }
    LaunchedEffect(item) {
        if (useParallaxAnimation) {
            isAnimatingSwap = true
            delay(parallaxLockMs)
            isAnimatingSwap = false
        }
    }

    // Description: KINO_PLAY uses fixed-height scrollable description; WIDEO uses
    // expandable description (6 lines collapsed, click to expand to full text).
    val descriptionScrollState = rememberScrollState()
    var isDescriptionExpanded by remember(item.title) { mutableStateOf(false) }
    var descriptionHasOverflow by remember(item.title) { mutableStateOf(false) }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    val descriptionFocusRequester = remember { FocusRequester() }
    // Page-level scroll for WIDEO so an expanded long description + buttons don't get
    // squeezed off-screen when content exceeds height.
    val pageScrollState = rememberScrollState()

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

    // After exiting browse mode, refocus on "Oglądaj" (button index 0) — the canonical
    // primary action of the WIDEO detail. Skip the initial false-state firing on first
    // composition (we already handle initial focus above).
    var didBrowseModeInitialFire by remember { mutableStateOf(false) }
    LaunchedEffect(browseMode) {
        if (!didBrowseModeInitialFire) {
            didBrowseModeInitialFire = true
            return@LaunchedEffect
        }
        if (!browseMode) {
            delay(50)  // let the Row + FocusRequesters reattach after browseMode flip
            focusedButtonIndex = 0
            try {
                buttonFocusRequesters.getOrNull(0)?.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // WIDEO content (item.isKinoPlay == false) is free streaming — Oglądaj + Do obejrzenia
    // buttons, no Wypożycz/Zwiastun and no director/cast/country metadata block. KINO_PLAY
    // keeps the 2-button layout (Wypożycz/Oglądaj + Zwiastun) as before.
    val isWideoMode = !item.isKinoPlay
    val watchlistItems = com.uxellence.tv.v3.watchlist.WatchlistManager.items.value
    val isOnWatchlist = remember(watchlistItems, item.title) { item.title in watchlistItems }
    val buttons = customButtons ?: when {
        isWideoMode -> listOf(
            "Oglądaj",
            if (isOnWatchlist) "Usuń z listy" else "Do obejrzenia"
        )
        else -> listOf(
            if (isRented) "Oglądaj" else "Wypożycz: ${item.price}",
            "Zwiastun"
        )
    }

    // Mock data fallbacks for missing fields
    val displayFilmwebRating = item.filmwebRating ?: 6.7
    val displayAudioLanguages = item.audioLanguages ?: "angielski  |  polski  |  hiszpański  |  niemiecki"
    val displaySubtitleLanguages = item.subtitleLanguages ?: "angielski  |  polski"
    val displayDirector = item.director ?: "Álex Pina"
    val displayCast = item.cast ?: "Úrsula Corberó, Álvaro Morte, Alba Flores, Miguel Herrán, Pedro Alonso"

    // Layer block (1-5) extracted as a lambda so it can be reused by both the direct-render
    // path AND the parallax AnimatedContent wrapper. Closure captures all surrounding state
    // (focus, scroll, callbacks) — only `displayedItem` (which item to render) varies between
    // invocations. This lets two cards (old + new) render simultaneously during a parallax
    // sibling-swap transition.
    //
    // NOTE: Inside the lambda body, we shadow `item` with `displayedItem` so the existing
    // layer code (which references `item.title`, `item.backgroundUrl`, etc.) keeps working
    // unchanged. Per-item-derived values like `displayFilmwebRating`, `displayDirector`,
    // etc. are computed from the OUTER `item` — they're only visible in KINO_PLAY mode
    // (not in WIDEO where parallax runs), so the staleness across renders is harmless.
    val renderLayers: @Composable BoxScope.(VodSlideData) -> Unit = { displayedItem ->
        val item = displayedItem
        // Layer 1: Backdrop. KINO_PLAY → full-screen height anchored right; WIDEO → smaller
        // 16:9 version anchored to TOP-RIGHT, with bottom + left gradients fading the edges
        // into the page bg.
        if (item.backgroundUrl.isNotBlank()) {
            if (isWideoMode) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.6667f)
                        .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                        .align(Alignment.TopEnd)
                ) {
                    AsyncImage(
                        model = item.backgroundUrl,
                        contentDescription = "Backdrop",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    0.0f to Color(0xFF281443),
                                    0.30f to Color.Transparent
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1.0f to Color(0xFF281443)
                                )
                            )
                    )
                }
            } else {
                AsyncImage(
                    model = item.backgroundUrl,
                    contentDescription = "Backdrop",
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd),
                    contentScale = ContentScale.FillHeight
                )
            }
        }

        // Layer 2: Glow overlay (KINO_PLAY only)
        if (!isWideoMode) {
            Image(
                painter = painterResource(id = R.drawable.slide_glow_left),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        // Layer 3: Left-side gradient (KINO_PLAY only)
        if (!isWideoMode) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(sx(900))
                    .align(Alignment.CenterStart)
                    .offset(x = sx(400))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF281443), Color.Transparent)
                        )
                    )
            )
        }

        // (Layer 4 / Clock moved OUTSIDE this lambda — it's static across sibling-swap
        // animations, like the hint.)

        // Layer 5: Main content area
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isWideoMode) sx(40) else sx(80),
                    top = if (isWideoMode) 0.dp else sy(168),
                    end = sx(100),
                    bottom = if (isWideoMode) 0.dp else sy(40)
                ),
            horizontalArrangement = Arrangement.spacedBy(sx(64))
        ) {
            // LEFT column
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isWideoMode) {
                    MoviePoster(posterUrl = item.posterUrl, sx = ::sx, sy = ::sy)
                } else {
                    Spacer(modifier = Modifier.width(sx(225)))
                }
            }

            // CENTER column
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .let { if (isWideoMode) it.verticalScroll(pageScrollState) else it }
            ) {
                if (isWideoMode) Spacer(modifier = Modifier.height(sy(168)))

                if (isWideoMode) {
                    Box(
                        modifier = Modifier
                            .size(width = sy(252), height = sy(268))
                            .padding(bottom = sy(16)),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        if (!item.channelLogoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.channelLogoUrl,
                                contentDescription = "Channel logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(sy(252))
                            )
                        }
                    }
                }

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

                MetadataRow(
                    genre = item.genre,
                    duration = item.duration,
                    year = item.year,
                    country = if (isWideoMode) null else item.country,
                    ageRating = item.ageRating,
                    filmwebRating = if (isWideoMode) null else displayFilmwebRating,
                    sx = ::sx,
                    sy = ::sy
                )
                Spacer(modifier = Modifier.height(sy(32)))

                if (isWideoMode) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = sx(981))
                            .border(
                                width = if (isDescriptionFocused) sx(4) else 0.dp,
                                color = if (isDescriptionFocused) Color(0xFF5FEDD4) else Color.Transparent,
                                shape = RoundedCornerShape(sx(8))
                            )
                            .padding(sx(8))
                            .focusRequester(descriptionFocusRequester)
                            .onFocusChanged { fs -> isDescriptionFocused = fs.isFocused }
                            .focusable()
                    ) {
                        Text(
                            text = item.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = sy(28).value.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = sy(40).value.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 6,
                            onTextLayout = { result ->
                                if (!descriptionHasOverflow) {
                                    descriptionHasOverflow = result.lineCount > 6 || result.hasVisualOverflow
                                }
                            }
                        )
                    }
                } else {
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
                            modifier = Modifier.verticalScroll(descriptionScrollState)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(sy(48)))

                if (!isWideoMode) {
                    MovieDetailsSection(
                        audioLanguages = displayAudioLanguages,
                        subtitleLanguages = displaySubtitleLanguages,
                        director = displayDirector,
                        cast = displayCast,
                        sx = ::sx,
                        sy = ::sy
                    )
                }

                if (isWideoMode) {
                    Spacer(modifier = Modifier.height(sy(30)))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (isRented && rentalExpiresAt != null) {
                    RentalCountdownInfo(
                        expiresAt = rentalExpiresAt,
                        totalDurationMs = RentalManager.DEFAULT_RENTAL_DURATION_MS,
                        sx = ::sx,
                        sy = ::sy,
                        modifier = Modifier.padding(bottom = sy(20))
                    )
                }

                if (browseMode) {
                    Box(
                        modifier = Modifier
                            .size(sx(1), sy(1))
                            .focusRequester(browseFocusRequester)
                            .focusable()
                    )
                    LaunchedEffect(browseMode) {
                        if (browseMode) {
                            try { browseFocusRequester.requestFocus() } catch (_: Exception) {}
                        }
                    }
                } else Row(
                    modifier = if (isWideoMode) Modifier else Modifier.padding(bottom = sy(40)),
                    horizontalArrangement = Arrangement.spacedBy(sx(24))
                ) {
                    buttons.forEachIndexed { index, label ->
                        ActionButton(
                            label = label,
                            isFocused = focusedButtonIndex == index && !isDescriptionFocused,
                            focusRequester = buttonFocusRequesters[index],
                            sx = ::sx,
                            sy = ::sy,
                            onFocusChanged = { isFocused ->
                                if (isFocused) focusedButtonIndex = index
                            },
                            onClick = {
                                if (customButtons != null) {
                                    onCustomButtonClicked?.invoke(index)
                                } else when (index) {
                                    0 -> if (isWideoMode || isRented) onWatchClicked() else onRentClicked()
                                    1 -> if (isWideoMode) {
                                        com.uxellence.tv.v3.watchlist.WatchlistManager.toggle(item.title, context)
                                    } else onTrailerClicked()
                                }
                            }
                        )
                    }
                }

                if (isWideoMode) Spacer(modifier = Modifier.height(sy(40)))
            }

            // RIGHT column - Scroll indicator (KINO_PLAY only)
            if (!isWideoMode) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF220F38)) // Darker purple — visible behind card during parallax swap
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.Back, Key.Escape -> {
                        onBackPressed()
                        true
                    }
                    Key.O, Key.Zero, Key.NumPad0 -> {
                        // Test toggle: switch between instant sibling swap and parallax
                        // card-stacking transition. Accepts O (PC keyboard testing) +
                        // 0/Numpad-0 (TV remote — most have a numeric "0" key).
                        useParallaxAnimation = !useParallaxAnimation
                        true
                    }
                    Key.DirectionLeft -> {
                        when {
                            isDescriptionFocused -> true
                            // Animation in flight — swallow the key (no callback, no
                            // hint flash) so a fast opposite-direction press can't
                            // re-enter AnimatedContent mid-transition.
                            isAnimatingSwap -> true
                            // In browseMode: LEFT keeps navigating siblings (no buttons to traverse).
                            browseMode && onNavigatePrev != null -> {
                                hintPressed = "left"
                                slideDirection = -1
                                onNavigatePrev.invoke()
                                true
                            }
                            browseMode -> {
                                hintPressed = "left"  // flash even when no prev — visual feedback
                                true
                            }
                            focusedButtonIndex > 0 -> {
                                focusedButtonIndex--
                                buttonFocusRequesters.getOrNull(focusedButtonIndex)?.requestFocus()
                                true
                            }
                            onNavigatePrev != null -> {
                                // From leftmost button: enter browse mode + jump to previous film.
                                browseMode = true
                                hintPressed = "left"
                                slideDirection = -1
                                onNavigatePrev.invoke()
                                true
                            }
                            else -> true
                        }
                    }
                    Key.DirectionRight -> {
                        when {
                            isDescriptionFocused -> true
                            isAnimatingSwap -> true
                            browseMode && onNavigateNext != null -> {
                                hintPressed = "right"
                                slideDirection = 1
                                onNavigateNext.invoke()
                                true
                            }
                            browseMode -> {
                                hintPressed = "right"
                                true
                            }
                            focusedButtonIndex < buttons.size - 1 -> {
                                focusedButtonIndex++
                                buttonFocusRequesters.getOrNull(focusedButtonIndex)?.requestFocus()
                                true
                            }
                            onNavigateNext != null -> {
                                browseMode = true
                                hintPressed = "right"
                                slideDirection = 1
                                onNavigateNext.invoke()
                                true
                            }
                            else -> true
                        }
                    }
                    Key.DirectionUp -> {
                        // WIDEO: UP from any button focuses the description.
                        if (isWideoMode && !isDescriptionFocused) {
                            try { descriptionFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        // From description (when focused) → first button
                        if (isDescriptionFocused) {
                            buttonFocusRequesters.getOrNull(0)?.requestFocus()
                            focusedButtonIndex = 0
                            true
                        } else false
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (isDescriptionFocused) {
                            isDescriptionExpanded = !isDescriptionExpanded
                            return@onPreviewKeyEvent true
                        }
                        if (browseMode) {
                            // OK in browse mode: just flip the flag. A LaunchedEffect on
                            // browseMode further down handles the requestFocus after the
                            // button Row remounts.
                            browseMode = false
                            return@onPreviewKeyEvent true
                        }
                        if (customButtons != null) {
                            onCustomButtonClicked?.invoke(focusedButtonIndex)
                        } else when (focusedButtonIndex) {
                            0 -> if (isWideoMode || isRented) onWatchClicked() else onRentClicked()
                            1 -> if (isWideoMode) {
                                com.uxellence.tv.v3.watchlist.WatchlistManager.toggle(item.title, context)
                            } else onTrailerClicked()
                            2 -> onPreviewClicked()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        // Layer block — render directly OR wrap in AnimatedContent for full-screen
        // card-stack-with-depth transition. Outgoing recedes in Z (scale 1→0.85 + fade
        // to 0.4 alpha) and stays in place. Incoming slides in from the leading edge
        // with a soft drop-shadow on its leading edge — visually "lands on top" of the
        // receding card. Clock + hint sit outside (static).
        if (useParallaxAnimation && isWideoMode) {
            AnimatedContent(
                targetState = item,
                // 20dp margin around the card so it sits inset from screen edges,
                // matching the KINO PLAY hero-slider card style.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(sx(20)),
                transitionSpec = {
                    val dir = slideDirection.takeIf { it != 0 } ?: 1
                    androidx.compose.animation.ContentTransform(
                        // Production speed (was 900/1100, slowed for inspection
                        // before that). No fade — incoming is fully opaque from
                        // first frame; outgoing only scales down (depth illusion).
                        targetContentEnter = slideInHorizontally(
                            initialOffsetX = { it * dir },
                            animationSpec = tween(450, easing = FastOutSlowInEasing)
                        ),
                        initialContentExit = scaleOut(
                            targetScale = 0.85f,
                            animationSpec = tween(550, easing = FastOutSlowInEasing)
                        ),
                        targetContentZIndex = 1f  // incoming above outgoing
                    )
                },
                label = "fullscreen_swap"
            ) { displayed ->
                // Solid background under each card so the incoming slide is fully
                // opaque — without it, the renderLayers content has transparent
                // regions (e.g. the right side past the backdrop's left-edge gradient)
                // and the outgoing card visibly shows through the incoming card.
                // Style mirrors KINO PLAY hero-slider non-focused card:
                //   - 4dp drop-shadow (CardDefaults.cardElevation defaultElevation)
                //   - 20dp rounded corners
                //   - 2dp white α=20% border (BorderStroke(2.dp, white α=0.2))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(4.dp, RoundedCornerShape(20.dp))
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF281443))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                ) {
                    renderLayers(displayed)
                }
            }
        } else {
            // V1 (parallax off): wrap renderLayers in a solid #281443 box so the
            // card covers the darker outer background (#220F38). No margins/border/
            // shadow/rounded corners — V1 is a full-bleed card.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF281443))
            ) {
                renderLayers(item)
            }
        }

        // Static Layer 4: Clock (rendered OUTSIDE the AnimatedContent so it doesn't
        // slide along with the card transition — matches the hint's static positioning).
        TimeLabel(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = sy(60), end = sx(100)),
            sx = ::sx,
            sy = ::sy
        )

        // WIDEO sibling-navigation hint (bottom-center). Only visible while in browseMode
        // — i.e. after the user pressed LEFT/RIGHT off the buttons row. Buttons are
        // hidden in browseMode (replaced by an invisible focus holder), and OK exits
        // browseMode so the buttons reappear and the hint disappears.
        if (isWideoMode && browseMode) {
            SiblingNavHint(
                hasPrev = onNavigatePrev != null,
                hasNext = onNavigateNext != null,
                pressedSide = hintPressed,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = sy(40)),
                sx = ::sx,
                sy = ::sy
            )
        }
    }
}

/**
 * Bottom-center "OK + ←/→ chevrons" hint that surfaces when the WIDEO MovieDetail screen
 * supports prev/next sibling navigation (LEFT/RIGHT from the buttons row swaps the film).
 *
 * Visual: dark-purple translucent disc, OK pill in middle, chevron arrows on the sides.
 */
@Composable
private fun SiblingNavHint(
    hasPrev: Boolean,
    hasNext: Boolean,
    pressedSide: String?,  // "left" | "right" | null — drives the brief tap highlight
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val leftActive = pressedSide == "left"
    val rightActive = pressedSide == "right"
    val leftBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (leftActive) Color(0xFF5FEDD4) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "hint_left_bg"
    )
    val rightBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (rightActive) Color(0xFF5FEDD4) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "hint_right_bg"
    )

    // Square box → CircleShape gives a true circle (oval before because we used different
    // sx vs sy dims).
    Box(
        modifier = modifier
            .size(sx(220))
            .clip(CircleShape)
            .background(Color(0x1A000000)),  // black 10% alpha
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(12))
        ) {
            // LEFT chevron with tap highlight
            Box(
                modifier = Modifier
                    .size(sx(56))
                    .clip(CircleShape)
                    .background(leftBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "‹",
                    color = when {
                        leftActive -> Color(0xFF281443)        // dark on aqua
                        hasPrev -> Color(0xFFEEEEEE)
                        else -> Color(0x44EEEEEE)              // disabled
                    },
                    fontSize = (40 * sx(1).value).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            // OK pill — central button mark
            Box(
                modifier = Modifier
                    .size(sx(64))
                    .clip(CircleShape)
                    .background(Color(0xFF1A0C2C)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "OK",
                    color = Color(0xFFEEEEEE),
                    fontSize = (16 * sx(1).value).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            // RIGHT chevron with tap highlight
            Box(
                modifier = Modifier
                    .size(sx(56))
                    .clip(CircleShape)
                    .background(rightBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "›",
                    color = when {
                        rightActive -> Color(0xFF281443)
                        hasNext -> Color(0xFFEEEEEE)
                        else -> Color(0x44EEEEEE)
                    },
                    fontSize = (40 * sx(1).value).sp,
                    fontWeight = FontWeight.Bold
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
    filmwebRating: Double?,  // null → Filmweb section hidden (WIDEO mode)
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Build the segments list first so we know whether to draw a trailing divider before
    // FilmwebRating without leaving a stray separator at the end.
    val segments = buildList {
        if (!genre.isNullOrBlank()) add(genre)
        if (!duration.isNullOrBlank()) add(duration)
        if (!year.isNullOrBlank()) add(year)
        if (!country.isNullOrBlank()) add(country)
        if (!ageRating.isNullOrBlank()) add(ageRating)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sx(16))
    ) {
        segments.forEachIndexed { idx, text ->
            MetadataText(text = text, sy = sy)
            // Divider after every segment except the last when Filmweb is hidden
            if (idx < segments.size - 1 || filmwebRating != null) {
                MetadataDivider(sy = sy)
            }
        }

        if (filmwebRating != null) {
            FilmwebRating(
                rating = filmwebRating,
                sx = sx,
                sy = sy
            )
        }
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
                        .size(sx(4), sy(10))
                        .clip(RoundedCornerShape(sx(2)))
                        .background(
                            if (isElapsed) Color(0x1AFFFFFF)  // white 10%
                            else Color(0xFFFFFFFF)            // white 100%
                        )
                )
            }
        }
    }
}
