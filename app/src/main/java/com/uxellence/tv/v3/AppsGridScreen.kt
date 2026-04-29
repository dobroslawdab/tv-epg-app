package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Grid wszystkich aplikacji — 6 na szerokość, wzorowany layoutowo na RecordingsGridScreen.
 * Każda karta = ikona aplikacji (drawable) + tytuł pod spodem.
 */

private data class GridApp(
    val id: String,
    val name: String,
    val drawableId: Int
)

@Composable
fun AppsGridScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val apps = remember {
        listOf(
            GridApp("netflix", "Netflix", R.drawable.imgi_57_netflix_2x),
            GridApp("youtube", "YouTube", R.drawable.imgi_58_youtube_2x),
            GridApp("prime", "Prime Video", R.drawable.imgi_59_prime_video_2x),
            GridApp("spotify", "Spotify", R.drawable.imgi_61_spotify_2x),
            GridApp("disney", "Disney+", R.drawable.imgi_63_disney_2x),
            GridApp("apple_tv", "Apple TV", R.drawable.imgi_68_apple_tv_2x),
            GridApp("hbo_max", "HBO Max", R.drawable.hbo_max_logo),
            GridApp("netflix2", "Netflix Premium", R.drawable.netflix_logo),
            GridApp("youtube2", "YouTube Music", R.drawable.youtube_logo),
            GridApp("prime2", "Prime", R.drawable.prime_video_logo),
            GridApp("disney2", "Disney+ Hotstar", R.drawable.disney_plus_logo),
            GridApp("appli", "Aplikacje", R.drawable.appli)
        )
    }

    val columnsCount = 6
    var focusedIndex by remember { mutableStateOf(0) }
    val focusRequesters = remember(apps.size) { List(apps.size) { FocusRequester() } }
    LaunchedEffect(Unit) {
        if (focusRequesters.isNotEmpty()) focusRequesters[0].requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF281443))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBackPressed(); true
                } else false
            }
            .focusable()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = sy(120), start = sx(80), end = sx(80))) {
            Text(
                text = "Wszystkie aplikacje",
                color = Color(0xFFEEEEEE),
                fontSize = (40 * scaleY).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = sy(40))
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(sx(24)),
                verticalArrangement = Arrangement.spacedBy(sy(32)),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(apps) { index, app ->
                    val isFocused = focusedIndex == index

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .focusRequester(focusRequesters[index])
                            .onFocusChanged { state ->
                                if (state.isFocused) focusedIndex = index
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (event.key) {
                                    // Ostatni kafelek w wierszu → przejście do pierwszego w następnym wierszu
                                    Key.DirectionRight -> {
                                        val isLastInRow = (index % columnsCount) == columnsCount - 1
                                        val hasNext = index + 1 < apps.size
                                        if (isLastInRow && hasNext) {
                                            focusRequesters[index + 1].requestFocus()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    // Symetrycznie: pierwszy w wierszu → ostatni w poprzednim
                                    Key.DirectionLeft -> {
                                        val isFirstInRow = (index % columnsCount) == 0
                                        if (isFirstInRow && index > 0) {
                                            focusRequesters[index - 1].requestFocus()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                                false
                            }
                            .focusable()
                            .clickable {
                                android.util.Log.d("AppsGridScreen", "App clicked: ${app.name}")
                            }
                    ) {
                        // Banner Android TV: 832×468 (proporcja 16:9). Kafelek ma tę samą proporcję,
                        // więc logo wypełnia go bez pasów ani crop'u.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(sx(16)))
                                .background(Color(0xFF180C28))
                                .then(
                                    if (isFocused) {
                                        Modifier.border(
                                            width = sx(4),
                                            color = Color(0xFF5FEDD4),
                                            shape = RoundedCornerShape(sx(16))
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = app.drawableId,
                                contentDescription = app.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.height(sy(12)))

                        Text(
                            text = app.name,
                            color = if (isFocused) Color(0xFF5FEDD4) else Color(0xCCEEEEEE),
                            fontSize = (20 * scaleY).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
