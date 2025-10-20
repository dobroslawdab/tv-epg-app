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
 * Reusable Slider Component
 * Horizontal movie/content slider that can be used across different screens
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SliderComponent(
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
            .height(sy(400)) // Fixed height for component
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
        LaunchedEffect(isSectionFocused) {
            if (isSectionFocused && movies.isNotEmpty()) {
                focusRequesters.firstOrNull()?.requestFocus()
            }
        }

        if (movies.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = sx(134)),
                horizontalArrangement = Arrangement.spacedBy(sx(20)),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(movies) { index, movie ->
                    MovieCard(
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
internal fun MovieCard(
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
            .size(sx(376), sy(212))
            .clip(RoundedCornerShape(sx(8)))
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (nowFocused != isButtonFocused) {
                    isButtonFocused = nowFocused
                    onFocusChanged(nowFocused)
                }
            },
        shape = RoundedCornerShape(sx(8)),
        border = BorderStroke(
            width = if (isFocused) sx(4) else sx(2),
            color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
            AsyncImage(
                model = movie.imageUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(sx(8)))
            )

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )

            // Content overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(sx(16)),
                verticalArrangement = Arrangement.spacedBy(sy(4))
            ) {
                Text(
                    text = movie.title,
                    style = TextStyle(
                        fontSize = sy(14).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = movie.category,
                    style = TextStyle(
                        fontSize = sy(10).value.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play button (visible when focused)
            if (isFocused) {
                Button(
                    onClick = { /* Handle play */ },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(sx(60), sy(30)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5AECD3),
                        contentColor = Color(0xFF48227C)
                    ),
                    shape = RoundedCornerShape(sx(4))
                ) {
                    Text(
                        text = "▶",
                        fontSize = sy(14).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}