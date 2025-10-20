package com.uxellence.tv.v3

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
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.loadVodContentFromAssets
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SliderScreen() {
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || isScrolling) return@onPreviewKeyEvent true

                val newIndex = when (event.key) {
                    Key.DirectionLeft -> if (focusedIndex > 0) focusedIndex - 1 else -1
                    Key.DirectionRight -> if (focusedIndex < movies.size - 1) focusedIndex + 1 else -1
                    else -> -1
                }
                if (newIndex != -1) {
                    if (newIndex < focusRequesters.size) {
                        focusRequesters[newIndex].requestFocus()
                    }
                }
                true
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(Unit) {
            if (movies.isNotEmpty()) {
                focusRequesters.firstOrNull()?.requestFocus()
            }
        }

        if (movies.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = sx(134)), // (1920 - 1652) / 2
                horizontalArrangement = Arrangement.spacedBy(sx(20)),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(movies) { index, movie ->
                    MovieCard(
                        movie = movie,
                        isFocused = index == focusedIndex,
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
internal fun MovieCard(
    movie: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var isCardFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(sx(1328))  // Narrower width like LiveScreen
            .height(sy(742)),
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
                // Gradient is now inside this Box, so it has the same size and position
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF5A3887),
                                    Color(0x005A3887) // rgba(90, 56, 135, 0)
                                ),
                                // Sprawia, że gradient jest tylko na lewej połowie obrazu
                                endX = sx(1028).value * 0.75f
                            )
                        )
                )
            }

            // Content on the left
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sx(874))
                    .padding(sx(100))
                    .zIndex(1f), // Ensure content is drawn on top of the gradient
                verticalArrangement = Arrangement.spacedBy(sy(31))
            ) {
                // Channel Logo
                AsyncImage(
                    model = movie.channelLogoUrl,
                    contentDescription = "Channel Logo",
                    modifier = Modifier
                        .width(sx(168))
                        .height(sy(168))
                        .clip(RoundedCornerShape(8.dp))
                )

                // Text Details
                Column(verticalArrangement = Arrangement.spacedBy(sy(14))) {
                    Text(
                        text = movie.title,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(64).value.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sx(16))
                    ) {
                        Text(movie.category, style = metadataStyle(sy))
                        MetadataSeparator(sy)
                        Text("25 min", style = metadataStyle(sy))
                        MetadataSeparator(sy)
                        Text("2020 r.", style = metadataStyle(sy))
                        MetadataSeparator(sy)
                        Text("Polska", style = metadataStyle(sy))
                        MetadataSeparator(sy)
                        Text("7 lat", style = metadataStyle(sy))
                    }
                    Text(
                        text = movie.description,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(28).value.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (sy(28).value * 1.43).sp
                    )
                }

            }

            // Tiny invisible Button (1px x 1px) - working focus solution
            Button(
                onClick = { /* Handle play - invisible action */ },
                modifier = Modifier
                    .size(1.dp, 1.dp) // Tiny 1px x 1px size
                    .align(Alignment.TopStart)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        val nowFocused = focusState.isFocused
                        if (nowFocused != isCardFocused) {
                            isCardFocused = nowFocused
                            onFocusChanged(nowFocused)
                        }
                    },
                shape = RoundedCornerShape(0.dp), // No rounding for tiny button
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent, // Invisible
                    contentColor = Color.Transparent   // Invisible text
                ),
                border = null, // No border on button (card has border)
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp) // No padding
            ) {
                // Empty content
            }

        }
    }
}


@Composable
private fun metadataStyle(sy: (Int) -> androidx.compose.ui.unit.Dp) = TextStyle(
    color = Color(0xCCEEEEEE),
    fontSize = sy(20).value.sp,
    fontWeight = FontWeight.W700,
    letterSpacing = 0.4.sp
)

@Composable
private fun MetadataSeparator(sy: (Int) -> androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier
        .width(2.dp)
        .height(sy(24))
        .background(Color(0xCCEEEEEE)))
}
