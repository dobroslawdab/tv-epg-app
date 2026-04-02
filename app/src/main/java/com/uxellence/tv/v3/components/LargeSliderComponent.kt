package com.uxellence.tv.v3.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.loadVodContentFromAssets
import kotlinx.coroutines.launch

/**
 * Large Slider Component with full-size cards
 * Based on SliderScreen but as reusable component with isSectionFocused support
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LargeSliderComponent(
    modifier: Modifier = Modifier,
    isSectionFocused: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val movies = remember { loadVodContentFromAssets(context) }
    var focusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(movies.size) { List(movies.size) { FocusRequester() } }
    val isScrolling = listState.isScrollInProgress

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < movies.size) {
            scope.launch { listState.animateScrollToItem(focusedIndex) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(sy(742)) // Natural height of large cards
            .onPreviewKeyEvent { event ->
                if (!isSectionFocused || event.type != KeyEventType.KeyDown || isScrolling) 
                    return@onPreviewKeyEvent false

                val newIndex = when (event.key) {
                    Key.DirectionLeft -> if (focusedIndex > 0) focusedIndex - 1 else -1
                    Key.DirectionRight -> if (focusedIndex < movies.size - 1) focusedIndex + 1 else -1
                    else -> -1
                }
                if (newIndex != -1) {
                    if (newIndex < focusRequesters.size) {
                        focusRequesters[newIndex].requestFocus()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            },
        contentAlignment = Alignment.Center
    ) {
        // Handle focus gain and loss
        LaunchedEffect(isSectionFocused) {
            if (isSectionFocused && movies.isNotEmpty()) {
                focusRequesters.firstOrNull()?.requestFocus()
            }
            // Note: Focus clearing is handled by Compose automatically when component becomes inactive
        }

        if (movies.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = sx(296)), // (1920 - 1328) / 2
                horizontalArrangement = Arrangement.spacedBy(sx(20)),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(movies, key = { _, movie -> movie.id }) { index, movie ->
                    LargeMovieCard(
                        movie = movie,
                        isFocused = index == focusedIndex && isSectionFocused,
                        focusRequester = focusRequesters[index],
                        onFocusChanged = { isFocused ->
                            if (isFocused) focusedIndex = index
                        },
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun LargeMovieCard(
    movie: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var isButtonFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(sx(1328))  // Narrower width like LiveScreen
            .height(sy(742))  // Same height
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (nowFocused != isButtonFocused) {
                    isButtonFocused = nowFocused
                    onFocusChanged(nowFocused)
                }
            },
        shape = RoundedCornerShape(sx(20)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF5B3987)
        ),
        border = if (isFocused) BorderStroke(sx(10), Color(0xFF5FEDD4)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 16.dp else 8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Background Image and Gradient Overlay
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                AsyncImage(
                    model = movie.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(sx(1028))
                        .height(sy(742))
                        .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20))),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF5A3887),
                                    Color(0x005A3887) // rgba(90, 56, 135, 0)
                                ),
                                endX = sx(1028).value * 0.75f
                            )
                        )
                )
            }

            // Content on the left
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(sx(40)),
                verticalArrangement = Arrangement.spacedBy(sy(20))
            ) {
                // Category
                Text(
                    text = movie.category,
                    style = TextStyle(
                        fontSize = sy(20).value.sp,
                        fontWeight = FontWeight.W600,
                        color = Color(0xFFEEEEEE)
                    )
                )

                // Title
                Text(
                    text = movie.title,
                    style = TextStyle(
                        fontSize = sy(48).value.sp,
                        fontWeight = FontWeight.W700,
                        color = Color(0xFFEEEEEE)
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(sx(500))
                )

                // Description
                Text(
                    text = movie.description,
                    style = TextStyle(
                        fontSize = sy(18).value.sp,
                        fontWeight = FontWeight.W400,
                        color = Color(0xFFEEEEEE)
                    ),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(sx(500))
                )

            }
        }
    }
}