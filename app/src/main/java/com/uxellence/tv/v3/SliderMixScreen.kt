package com.uxellence.tv.v3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import coil.request.ImageRequest
import coil.size.Size
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.loadVodContentFromAssets
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import android.widget.VideoView
import android.net.Uri
import android.util.Log

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SliderMixScreen(
    shouldAutoFocus: Boolean = false,
    isInTelewizjaSection: Boolean = false,
    shouldShowFocusBorder: Boolean = true,
    isShortcutsFocused: Boolean = false,
    externalSx: ((Int) -> androidx.compose.ui.unit.Dp)? = null,
    externalSy: ((Int) -> androidx.compose.ui.unit.Dp)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    val sx: (Int) -> androidx.compose.ui.unit.Dp = externalSx ?: { px -> (px * scaleX).dp }
    val sy: (Int) -> androidx.compose.ui.unit.Dp = externalSy ?: { px -> (px * scaleY).dp }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose { 
            player.release()
        }
    }

    val channels = remember {
        listOf(
            VodContent(
                id = "test_tvp1",
                title = "TVP1",
                description = "Telewizja Polska - pierwszy program",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8"
            ),
            VodContent(
                id = "test_polsat",
                title = "Polsat",
                description = "Telewizja Polsat",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://lb2-e2-19.pluscdn.pl/ch/1502600/308/dash/20a18c30/live.mpd"
            ),
            VodContent(
                id = "test_polsat_news",
                title = "Polsat News",
                description = "Polsat News - informacje 24h",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "http://cdn-s-lb2.pluscdn.pl/lv/1517830/349/dash/81ec4c32/live.mpd"
            ),
            VodContent(
                id = "test_polsat_news_polityka",
                title = "Polsat News Polityka",
                description = "Polsat News Polityka - polityka i komentarze",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://lb2-e3-20.pluscdn.pl/lv/1511888/322/dash/52a9b70b/live.mpd"
            ),
            VodContent(
                id = "test_polsat_viasat_nature",
                title = "Polsat Viasat Nature",
                description = "Polsat Viasat Nature - natura i przyroda",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://liveovh010.cda.pl/enc104/polsatviasatnaturehdraw/polsatviasatnaturehdraw.mpd"
            ),
            VodContent(
                id = "test_4fun_tv",
                title = "4Fun TV",
                description = "4Fun TV - muzyka i rozrywka",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://stream.4fun.tv:8888/hls/4f.m3u8"
            ),
            VodContent(
                id = "test_viasat_explore_classic",
                title = "Viasat Explore Classic",
                description = "Viasat Explore Classic - dokumenty i historia",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://da9c49fa.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLXBsX1ZpYXNhdEV4cGxvcmVfSExT/playlist.m3u8"
            ),
            VodContent(
                id = "test_euronews",
                title = "Euronews",
                description = "Euronews - wiadomości z Europy i świata",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://7060743b4b224241b86325460b14d152.mediatailor.eu-west-1.amazonaws.com/v1/master/0547f18649bd788bec7b67b746e47670f558b6b2/production-LiveChannel-6769/bitok/eyJzdGlkIjoiMTE3OTllNGEtYmU1OC00ZjQyLTkxOTYtY2VlYWQzZGU2MDJjIiwibWt0IjoicGwiLCJjaCI6Njc2OSwicHRmIjo1fQ==/26235/euronews-pl.m3u8"
            ),
            VodContent(
                id = "test_top_movies_polska",
                title = "Top Movies Polska",
                description = "Top Movies Polska - najlepsze filmy",
                category = "Live TV",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://top-movies-rakuten-tv-pl.fast.rakuten.tv/v1/master/0547f18649bd788bec7b67b746e47670f558b6b2/production-LiveChannel-6059/master.m3u8"
            )
        )
    }

    val movies = remember(isInTelewizjaSection) {
        val vodContent = loadVodContentFromAssets(context)
        if (isInTelewizjaSection) {
            // TELEWIZJA/START: TV live + VOD + empty slots
            val mixedList = mutableListOf<VodContent>()
            mixedList.add(channels[0])
            vodContent.shuffled().take(7).forEach { mixedList.add(it) }

            // Dodaj 3 puste slajdy na końcu
            repeat(3) { index ->
                mixedList.add(
                    VodContent(
                        id = "test_empty_slide_${index + 1}",
                        title = "Pusty slajd ${index + 1}",
                        description = "Miejsce na przyszłą zawartość",
                        category = "Empty",
                        imageUrl = "",
                        channelLogoUrl = "",
                        link = ""
                    )
                )
            }
            mixedList
        } else {
            // WIDEO: tylko 10 VOD (bez TV live, bez pustych slajdów)
            vodContent.shuffled().take(10)
        }
    }

    val sharedPrefs = context.getSharedPreferences("tv_settings", android.content.Context.MODE_PRIVATE)
    var currentChannelIndex by remember { mutableStateOf(sharedPrefs.getInt("last_selected_channel", 0)) }
    
    var lastChannelChangeTime by remember { mutableStateOf(0L) }
    var isLiveTvSlideFocused by remember { mutableStateOf(false) }
    var scrollTargetIndex by remember { mutableStateOf<Int?>(null) }
    var isError by remember { mutableStateOf(false) }
    var lastFocusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(movies.size) { List(movies.size) { FocusRequester() } }
    val isScrolling = listState.isScrollInProgress

    LaunchedEffect(scrollTargetIndex) {
        scrollTargetIndex?.let {
            scope.launch {
                kotlinx.coroutines.delay(50)
                listState.scrollToItem(it)
            }
        }
    }

    val shouldPlayLiveTV = isInTelewizjaSection && (shouldAutoFocus == false || isLiveTvSlideFocused) && !isShortcutsFocused
    LaunchedEffect(shouldPlayLiveTV, currentChannelIndex) {
        if (shouldPlayLiveTV) {
            try {
                isError = false
                player.stop()
                val currentChannel = channels[currentChannelIndex]
                val mediaItem = MediaItem.fromUri(currentChannel.link)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                isError = true
            }
        } else {
            player.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .padding(top = sy(20))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || isScrolling) return@onPreviewKeyEvent true

                if (isLiveTvSlideFocused) {
                    val keyCode = event.nativeKeyEvent?.keyCode
                    when (keyCode) {
                        166 -> { // Channel Up
                            val newChannelIndex = (currentChannelIndex + 1) % channels.size
                            currentChannelIndex = newChannelIndex
                            sharedPrefs.edit().putInt("last_selected_channel", newChannelIndex).apply()
                            lastChannelChangeTime = System.currentTimeMillis()
                            return@onPreviewKeyEvent true
                        }
                        167 -> { // Channel Down
                            val newChannelIndex = (currentChannelIndex - 1 + channels.size) % channels.size
                            currentChannelIndex = newChannelIndex
                            sharedPrefs.edit().putInt("last_selected_channel", newChannelIndex).apply()
                            lastChannelChangeTime = System.currentTimeMillis()
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                
                false
            },
        contentAlignment = Alignment.Center
    ) {
        LaunchedEffect(shouldAutoFocus) {
            if (shouldAutoFocus) {
                kotlinx.coroutines.delay(150)
                val safeTargetIndex = lastFocusedIndex.coerceIn(0, (movies.size - 1).coerceAtLeast(0))
                focusRequesters.getOrNull(safeTargetIndex)?.requestFocus()
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = sx(134), end = sx(500)),
            horizontalArrangement = Arrangement.spacedBy(sx(20)),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(movies) { index, movie ->
                MixMovieCard(
                    movie = movie,
                    focusRequester = focusRequesters[index],
                    sx = { sx(it) },
                    sy = { sy(it) },
                    enabled = shouldAutoFocus,
                    shouldShowFocusBorder = shouldShowFocusBorder,
                    player = if (index == 0 && movie.category == "Live TV") player else null,
                    currentChannelIndex = currentChannelIndex,
                    channels = channels,
                    lastChannelChangeTime = lastChannelChangeTime,
                    isError = isError,
                    index = index,
                    onCardFocused = { isFocused ->
                        if (isFocused) {
                            scrollTargetIndex = index
                            lastFocusedIndex = index
                        }
                        if (index == 0) {
                            isLiveTvSlideFocused = isFocused
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun MixMovieCard(
    movie: VodContent,
    focusRequester: FocusRequester,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    shouldShowFocusBorder: Boolean = true,
    player: ExoPlayer? = null,
    currentChannelIndex: Int = 0,
    channels: List<VodContent> = emptyList(),
    lastChannelChangeTime: Long = 0L,
    isError: Boolean = false,
    index: Int = 0,
    onCardFocused: (Boolean) -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .width(sx(1328))
            .height(sy(742)),
        shape = RoundedCornerShape(sx(20)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF5B3987)
        ),
        border = if (isCardFocused && shouldShowFocusBorder) BorderStroke(sx(10), Color(0xFF5FEDD4)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCardFocused) 16.dp else 8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                if (movie.category == "Live TV" && player != null) {
                    if (isError) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("⚠️ Błąd odtwarzania", color = Color.White, fontSize = sy(32).value.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        AndroidView(
                            factory = { ctx -> 
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (movie.category == "BoxTV" && isCardFocused) {
                    key(isCardFocused, index) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoURI(Uri.parse("https://www.dropbox.com/scl/fi/vr14sy7ygk5bwtig5w9cm/bob.mp4?rlkey=t1sjv5volsfyqkhjrmuh8mncm&st=fl2mmby7&dl=1"))
                                    setOnPreparedListener { it.isLooping = false; start() }
                                    setOnErrorListener { _, _, _ -> false }
                                }
                            },
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20)))
                        )
                    }
                } else if (movie.category == "Empty") {
                    // Pusty slajd placeholder
                    Box(
                        modifier = Modifier
                            .width(sx(1028))
                            .height(sy(742))
                            .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20)))
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF3A2B5A), // Ciemny fiolet w centrum
                                        Color(0xFF2A1B3D)  // Jeszcze ciemniejszy na brzegach
                                    ),
                                    radius = 800f
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(sy(20))
                        ) {
                            Text(
                                text = "📦",
                                fontSize = sy(80).value.sp
                            )
                            Text(
                                text = "PUSTY SLAJD",
                                color = Color(0xFF5FEDD4),
                                fontSize = sy(32).value.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Gotowe na nową zawartość",
                                color = Color(0xCCEEEEEE),
                                fontSize = sy(20).value.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                } else if (movie.imageUrl.isNotBlank()) {
                     val imageRequest = remember(movie.imageUrl) {
                        ImageRequest.Builder(context)
                            .data(movie.imageUrl)
                            .size(Size.ORIGINAL)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .width(sx(1028))
                            .height(sy(742))
                            .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20))),
                        contentScale = ContentScale.Crop
                    )
                }
                
                if (movie.category != "Live TV") {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF5A3887), Color(0x005A3887)),
                                    endX = sx(1028).value * 0.75f
                                )
                            )
                    )
                }
            }

            if (movie.category != "Live TV") {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(sx(874))
                        .padding(sx(100))
                        .zIndex(1f),
                    verticalArrangement = Arrangement.spacedBy(sy(31))
                ) {
                    if (movie.channelLogoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = movie.channelLogoUrl,
                            contentDescription = "Channel Logo",
                            modifier = Modifier.size(sx(168)).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(sy(14))) {
                        Text(movie.title, color = Color(0xFFEEEEEE), fontSize = sy(64).value.sp, fontWeight = FontWeight.W500, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (movie.category != "BoxTV") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sx(16))) {
                                Text(movie.category, style = mixMetadataStyle(sy))
                                MixMetadataSeparator(sy)
                                Text("25 min", style = mixMetadataStyle(sy))
                                MixMetadataSeparator(sy)
                                Text("2020 r.", style = mixMetadataStyle(sy))
                                MixMetadataSeparator(sy)
                                Text("Polska", style = mixMetadataStyle(sy))
                                MixMetadataSeparator(sy)
                                Text("7 lat", style = mixMetadataStyle(sy))
                            }
                        }
                        Text(movie.description, color = Color(0xFFEEEEEE), fontSize = sy(28).value.sp, fontWeight = FontWeight.W500, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = (sy(28).value * 1.43).sp)
                    }
                }
            }

            if (movie.category == "Live TV" && channels.isNotEmpty()) {
                val timeSinceChange = System.currentTimeMillis() - lastChannelChangeTime
                if (timeSinceChange < 3000 && lastChannelChangeTime > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(sx(20))
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(sx(8)))
                            .padding(sx(16))
                    ) {
                        Column {
                            Text("Kanał ${currentChannelIndex + 1}/9", color = Color(0xFF5FEDD4), fontSize = sy(18).value.sp, fontWeight = FontWeight.Bold)
                            Text(channels[currentChannelIndex].title, color = Color.White, fontSize = sy(16).value.sp)
                        }
                    }
                }
            }

            Button(
                onClick = { /* No action */ },
                enabled = enabled,
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.TopStart)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isCardFocused = focusState.isFocused
                        onCardFocused(focusState.isFocused)
                    },
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {}
        }
    }
}

@Composable
private fun mixMetadataStyle(sy: (Int) -> androidx.compose.ui.unit.Dp) = TextStyle(
    color = Color(0xCCEEEEEE),
    fontSize = sy(20).value.sp,
    fontWeight = FontWeight.W700,
    letterSpacing = 0.4.sp
)

@Composable
private fun MixMetadataSeparator(sy: (Int) -> androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.width(2.dp).height(sy(24)).background(Color(0xCCEEEEEE)))
}