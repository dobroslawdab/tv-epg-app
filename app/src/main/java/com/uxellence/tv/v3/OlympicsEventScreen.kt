package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.coroutines.launch

/**
 * Olympics Event Screen - Dedicated screen for Winter Olympics 2026 content
 *
 * Design based on RecordingsGridScreen pattern:
 * - Header (logo + title + description) scrolls WITH the grid
 * - 4 columns grid (from Figma)
 * - Card dimensions: 410x232px (same as RecordingsGridScreen)
 * - Background: #281443 with Olympics rings overlay
 *
 * Data source: EpgRepository.getOlympicsPrograms() - EPG content from last week + next week
 */

// Grid layout constants (4 columns, same as RecordingsGridScreen)
private const val GRID_COLUMNS = 4
private const val GRID_LEFT_PADDING = 80
private const val GRID_RIGHT_PADDING = 80
private const val GRID_HORIZONTAL_GAP = 40
private const val GRID_VERTICAL_GAP = 45  // Same as RecordingsGridScreen

// Card dimensions from RecordingsGridScreen: 410x232px
private const val CARD_WIDTH = 410
private const val CARD_HEIGHT = 232

// Layout positions from Figma
private const val LOGO_WIDTH = 196
private const val LOGO_HEIGHT = 219
private const val DESCRIPTION_WIDTH = 874

// Colors from Figma
private val BACKGROUND_COLOR = Color(0xFF281443)    // gradient-purple-dark
private val TEXT_COLOR = Color(0xFFEEEEEE)          // text-primary
private val FOCUS_BORDER_COLOR = Color(0xFF5AECD3)  // aqua

// Assets - Olympics 2026 Milano-Cortina logo
private const val OLYMPICS_LOGO_URL = "https://upload.wikimedia.org/wikipedia/en/thumb/5/59/2026_Winter_Olympics_logo.svg/800px-2026_Winter_Olympics_logo.svg.png"
private const val OLYMPICS_RINGS_URL = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5c/Olympic_rings_without_rims.svg/1280px-Olympic_rings_without_rims.svg.png"

@Composable
fun OlympicsEventScreen(
    onBackPressed: () -> Unit,
    content: List<VodContent>,
    sourceSection: String = "ODKRYWAJ",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Responsive scaling
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Focus management - row 0 is header (non-focusable), content starts at row 1
    var focusedRow by remember { mutableStateOf(0) }
    var focusedCol by remember { mutableStateOf(0) }
    val gridFocusRequesters = remember { mutableMapOf<Pair<Int, Int>, FocusRequester>() }
    val coroutineScope = rememberCoroutineScope()

    // Grid state for scrolling
    val lazyGridState = rememberLazyGridState()

    // Request initial focus on first content item
    LaunchedEffect(content) {
        if (content.isNotEmpty()) {
            kotlinx.coroutines.delay(100)
            gridFocusRequesters[Pair(0, 0)]?.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BACKGROUND_COLOR)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Back, Key.Escape -> {
                            onBackPressed()
                            true
                        }
                        Key.DirectionUp -> {
                            if (focusedRow > 0) {
                                focusedRow--
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            val numRows = (content.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                            if (focusedRow < numRows - 1) {
                                focusedRow++
                                val maxCol = if (focusedRow == numRows - 1) {
                                    (content.size - 1) % GRID_COLUMNS
                                } else {
                                    GRID_COLUMNS - 1
                                }
                                if (focusedCol > maxCol) {
                                    focusedCol = maxCol
                                }
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionLeft -> {
                            if (focusedCol > 0) {
                                focusedCol--
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            val numRows = (content.size + GRID_COLUMNS - 1) / GRID_COLUMNS
                            val maxCol = if (focusedRow == numRows - 1) {
                                (content.size - 1) % GRID_COLUMNS
                            } else {
                                GRID_COLUMNS - 1
                            }

                            if (focusedCol < maxCol) {
                                focusedCol++
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(50)
                                    gridFocusRequesters[Pair(focusedRow, focusedCol)]?.requestFocus()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // LAYER 1: Olympics rings background (45% opacity) - fixed position on the right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = sx(0), y = sy(-158))
                .width(sx(1440))
                .height(sy(961))
                .alpha(0.45f)
        ) {
            AsyncImage(
                model = OLYMPICS_RINGS_URL,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                onError = { /* Ignore if image not available */ }
            )
        }

        // LAYER 2: Scrollable content (header + grid) - everything scrolls together
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sx(GRID_LEFT_PADDING),
                end = sx(GRID_RIGHT_PADDING),
                top = sy(80),  // Top padding for header
                bottom = sy(40)
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(GRID_HORIZONTAL_GAP)),
            verticalArrangement = Arrangement.spacedBy(sy(GRID_VERTICAL_GAP))
        ) {
            // HEADER ITEM (spans all 4 columns, scrolls with grid)
            item(span = { GridItemSpan(GRID_COLUMNS) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = sy(40))  // Space between header and first row of cards
                ) {
                    // Olympics 2026 Milano-Cortina logo
                    AsyncImage(
                        model = OLYMPICS_LOGO_URL,
                        contentDescription = "Winter Olympics 2026 Milano-Cortina Logo",
                        modifier = Modifier
                            .width(sx(LOGO_WIDTH))
                            .height(sy(LOGO_HEIGHT)),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.height(sy(16)))

                    // Title: "Igrzyska olimpijskie"
                    Text(
                        text = "Igrzyska olimpijskie",
                        fontSize = (64 * scaleX).sp,
                        fontWeight = FontWeight.Medium,
                        color = TEXT_COLOR,
                        lineHeight = (88 * scaleY).sp
                    )

                    Spacer(modifier = Modifier.height(sy(20)))

                    // Description
                    Text(
                        text = "Zimowe Igrzyska Olimpijskie 2026 odbywają się w Milano-Cortina we Włoszech. " +
                               "Znajdziesz tu wszystkie transmisje i relacje z najważniejszych wydarzeń sportowych.",
                        fontSize = (28 * scaleX).sp,
                        fontWeight = FontWeight.Medium,
                        color = TEXT_COLOR,
                        lineHeight = (40 * scaleY).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(sx(DESCRIPTION_WIDTH))
                    )
                }
            }

            // GRID ITEMS: EPG content cards (410x232px - same as RecordingsGridScreen)
            itemsIndexed(content) { index, vodContent ->
                val row = index / GRID_COLUMNS
                val col = index % GRID_COLUMNS
                val isItemFocused = row == focusedRow && col == focusedCol

                val focusRequester = gridFocusRequesters.getOrPut(Pair(row, col)) { FocusRequester() }

                OlympicsContentCard(
                    vodContent = vodContent,
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = {
                        focusedRow = row
                        focusedCol = col
                    },
                    onClick = {
                        android.util.Log.d("OLYMPICS", "Olympics content clicked: ${vodContent.title}")
                        // TODO: Navigate to EPG player or content detail
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }

            // Empty state message
            if (content.isEmpty()) {
                item(span = { GridItemSpan(GRID_COLUMNS) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sy(200)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak treści olimpijskich w EPG",
                            fontSize = (28 * scaleX).sp,
                            fontWeight = FontWeight.Medium,
                            color = TEXT_COLOR.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    // Scroll to focused row when it changes (offset by 1 for header)
    LaunchedEffect(focusedRow) {
        if (content.isNotEmpty() && focusedRow > 0) {
            // +1 because header is item 0
            val targetIndex = focusedRow * GRID_COLUMNS + 1
            if (targetIndex <= content.size) {
                lazyGridState.animateScrollToItem(
                    index = targetIndex,
                    scrollOffset = -100
                )
            }
        }
    }
}

/**
 * Olympics Content Card (410x232px)
 * Same proportions as RecordingCard in RecordingsGridScreen
 */
@Composable
private fun OlympicsContentCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    modifier: Modifier = Modifier
) {
    // Card dimensions from RecordingsGridScreen: 410x232px
    val cardWidth = sx(CARD_WIDTH)
    val cardHeight = sy(CARD_HEIGHT)
    val cornerRadius = sx(8)

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(cornerRadius))
            .then(
                if (isFocused) Modifier.border(
                    width = sx(6),
                    color = FOCUS_BORDER_COLOR,
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
            model = vodContent.imageUrl,
            contentDescription = vodContent.title,
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

        // Channel logo (bottom-left, 89x71px - same as RecordingCard)
        vodContent.channelLogoUrl?.let { logoUrl ->
            if (logoUrl.isNotBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = sx(12), bottom = sy(12))
                        .width(sx(89))
                        .height(sy(71)),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Title and metadata (bottom-right area)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = sx(if (vodContent.channelLogoUrl.isNullOrBlank()) 12 else 110),
                    end = sx(12),
                    bottom = sy(12)
                )
                .fillMaxWidth()
        ) {
            // Title
            Text(
                text = vodContent.title,
                color = TEXT_COLOR,
                fontSize = (20 * sx(1).value / 1.dp.value).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Category/metadata (if available)
            if (vodContent.category.isNotBlank()) {
                Text(
                    text = vodContent.category,
                    color = TEXT_COLOR.copy(alpha = 0.7f),
                    fontSize = (16 * sx(1).value / 1.dp.value).sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
