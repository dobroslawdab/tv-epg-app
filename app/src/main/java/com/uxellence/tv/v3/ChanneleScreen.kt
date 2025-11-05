package com.uxellence.tv.v3

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.version001.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val FIXED_FOCUS_POSITION = 0

enum class ContentType {
    HORIZONTAL,
    VERTICAL_POSTER
}

@Serializable
data class PlayNowMovie(
    val tytul: String,
    val kategoria: String,
    val opis: String,
    val plakat: String,
    val link: String
)

fun loadKinoPlayMoviesFromAssets(context: android.content.Context): List<VodContent> {
    return try {
        val inputStream = context.assets.open("kino_play.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val json = Json { ignoreUnknownKeys = true }
        val movies = json.decodeFromString<List<PlayNowMovie>>(jsonString)
        
        movies.map { item ->
            VodContent(
                id = "channel_${item.tytul.hashCode()}_${item.link.hashCode()}",  // Unique ID for focus restoration
                title = item.tytul,
                description = item.opis,
                category = item.kategoria,
                imageUrl = item.plakat,
                channelLogoUrl = "", // Not available in this data source
                link = item.link
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// Liczba spacer elementów na końcu LazyRow - zwiększony bufor dla ostatnich miniaturek
private const val SPACER_ITEMS_COUNT = 8

// brak fallbacków CSV w tej wersji — źródła muszą dostarczyć JSON

@Composable
fun ChanneleScreen(shouldAutoFocus: Boolean = false) {
    val context = LocalContext.current
    val channels = listOf("Polecane", "Nowości", "Filmy", "Seriale", "Kino Play")
    val gridContent = remember {
        val vodContentList = loadVodContentFromAssets(context)
        val kinoPlayMovies = loadKinoPlayMoviesFromAssets(context)
        if (vodContentList.isNotEmpty()) {
            channels.associateWith { channelName ->
                if (channelName == "Kino Play") {
                    kinoPlayMovies.shuffled().take(10)
                } else {
                    vodContentList.shuffled().take(10)
                }
            }
        } else {
            emptyMap()
        }
    }

    var currentFocus by remember { mutableStateOf(NavigationFocus.CHANNEL_ROWS) }
    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-1) }
    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            // OPTYMALIZACJA: Twórz FocusRequesters lazy - tylko gdy potrzebne
            repeat(channels.size) { rowIndex ->
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon zawsze potrzebny
                // Twórz tylko jeden FocusRequester na wiersz dla FIXED_FOCUS_POSITION
                put(Pair(rowIndex, FIXED_FOCUS_POSITION), FocusRequester())
            }
            println("ChanneleScreen: Created ${this.size} FocusRequesters instead of ${channels.size * 11}")
        }
    }
    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    // Debounced auto-reset LazyListState dla niefokusowanych wierszy
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            // DEBOUNCE: Opóźnij reset o 150ms aby uniknąć konfliktów z navigacją
            kotlinx.coroutines.delay(150)
            
            // DEBUG: Zredukuj logowanie dla wydajności 
            // println("ChanneleScreen: Debounced auto-reset - focusedRowIndex: $focusedRowIndex, focusedColIndex: $focusedColIndex")
            
            // Reset tylko wierszy które naprawdę potrzebują resetowania
            repeat(channels.size) { rowIndex ->
                // Reset tylko jeśli wiersz nie jest fokusowany i ma scroll position > 0
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        // println("ChanneleScreen: Smoothly resetting row $rowIndex to position 0")
                        // PŁYNNY reset zamiast synchronicznego
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            kotlinx.coroutines.delay(200) // Opóźnienie żeby komponenty się załadowały
            
            // Debug logging - sprawdzamy czy focus requesters są dostępne
            println("ChanneleScreen: Initializing focus. Available focus requesters:")
            channelFocusRequesters.keys.forEach { key ->
                println("  - $key")
            }
            
            val firstCategoryFocusRequester = channelFocusRequesters[Pair(0, -1)]
            if (firstCategoryFocusRequester != null) {
                println("ChanneleScreen: Requesting focus on first category icon Pair(0, -1)")
                firstCategoryFocusRequester.requestFocus()
            } else {
                println("ChanneleScreen: ERROR - Focus requester for Pair(0, -1) is null!")
            }
            
            kotlinx.coroutines.delay(100) // Krótkie opóźnienie
        }
        isInitialized = true
        println("ChanneleScreen: Initialization completed")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                handleChanneleScreenNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onChannelContentFocusChange = { row, col, content -> 
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent
                )
            }
            .focusable()
    ) {
        val configuration = LocalConfiguration.current
        val scaleX = configuration.screenWidthDp / 1920f
        val scaleY = configuration.screenHeightDp / 1080f
        fun sx(px: Int) = (px * scaleX).dp
        fun sy(px: Int) = (px * scaleY).dp

        ModifiedChannelRowsLayout(
            channels = channels,
            gridContent = gridContent,
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col, content ->
                println("ChanneleScreen: Focus changed to row $row, col $col")
                focusedRowIndex = row
                focusedColIndex = col
            },
            currentFocus = currentFocus,
            lazyListStates = lazyListStates,
            sx = { sx(it) },
            sy = { sy(it) }
        )
    }
}

@Composable
fun ModifiedChannelRowsLayout(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int, VodContent?) -> Unit, // Pass VodContent up
    currentFocus: NavigationFocus,
    lazyListStates: Map<Int, LazyListState>, // Receive LazyListStates from parent
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(channels.size) { rowIndex ->
            val channelName = channels[rowIndex]
            val contentForChannel = gridContent[channelName] ?: emptyList()
            
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()
            
            val targetY = calculateChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                currentFocus = currentFocus,
                sy = sy
            )
            
            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                label = "channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                val contentType = if (channelName == "Kino Play") 
                    ContentType.VERTICAL_POSTER 
                else 
                    ContentType.HORIZONTAL
                
                UnifiedChannelRow(
                    channel = channelName,
                    rowIndex = rowIndex,
                    rowContent = contentForChannel,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelFocusChange = {},
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    currentFocus = currentFocus,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState,
                    contentType = contentType
                )
            }
        }
    }
}

@Composable
fun UnifiedChannelRow(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelFocusChange: (String) -> Unit,
    onChannelContentFocusChange: (Int, Int, VodContent?) -> Unit,
    currentFocus: NavigationFocus,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    lazyListState: LazyListState,
    contentType: ContentType
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    
    var showDetailsWithDelay by remember { mutableStateOf(false) }
    
    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && focusedColIndex == FIXED_FOCUS_POSITION
        
        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350) // 350ms = czas animacji
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
            label = "miniatures_y_offset_$rowIndex"
        )
        
    LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = miniaturesYOffset),
            state = lazyListState, 
            contentPadding = PaddingValues(start = sx(380), end = sx(20)),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            items(rowContent.size) { colIndex ->
                val vodContent = rowContent[colIndex]
                val isItemFocused = rowIndex == focusedRowIndex && 
                                   colIndex == lazyListState.firstVisibleItemIndex && 
                                   focusedColIndex == FIXED_FOCUS_POSITION
                
                
                
                val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
                
                when (contentType) {
                    ContentType.HORIZONTAL -> {
                        ContentCard(
                            vodContent = vodContent,
                            channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex, vodContent) },
                            sx = sx,
                            sy = sy,
                            lazyListState = lazyListState
                        )
                    }
                    ContentType.VERTICAL_POSTER -> {
                        PosterContentCard(
                            vodContent = vodContent,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { isFocused -> 
                                if (isFocused) {
                                    onChannelContentFocusChange(rowIndex, colIndex, vodContent)
                                }
                            },
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }
            
            items(SPACER_ITEMS_COUNT) { 
                Spacer(
                    modifier = Modifier
                        .width(sx(220))
                        .height(sy(380))
                )
            }
        }
        
        if (isCurrentRow && focusedColIndex == FIXED_FOCUS_POSITION && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                val correctedOverlayX = 380
                
                Box(
                    modifier = Modifier
                        .offset(x = sx(correctedOverlayX), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }
                        
                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }
        
        val categoryIconY = if (contentType == ContentType.VERTICAL_POSTER) sy(32) else sy(0)
        
        Box(modifier = Modifier.offset(x = sx(80), y = categoryIconY)) {
            val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
            val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]
            
            if (rowIndex < 3) { // Debug tylko dla pierwszych 3 kanałów żeby nie zaśmiecać logów
                println("ChanneleScreen: Channel '$channel' (row $rowIndex) - categoryIsFocused: $categoryIsFocused, focusRequester available: ${categoryFocusRequester != null}")
            }
            
            CategoryIcon(
                text = channel,
                isFocused = categoryIsFocused,
                onClick = { onChannelFocusChange(channel) },
                onFocused = { isFocused -> 
                    if (isFocused) {
                        println("ChanneleScreen: CategoryIcon '$channel' (row $rowIndex) gained focus")
                        onChannelFocusChange(channel)
                    }
                },
                focusRequester = categoryFocusRequester ?: FocusRequester(),
                sx = sx,
                sy = sy
            )
        }
    }
}

@Composable
fun PosterChannelRow_OLD(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelFocusChange: (String) -> Unit,
    onChannelContentFocusChange: (Int, Int, VodContent?) -> Unit, // Pass VodContent up
    currentFocus: NavigationFocus,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    lazyListState: LazyListState // NEW parameter
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    
    var showDetailsWithDelay by remember { mutableStateOf(false) }
    
    LaunchedEffect(isCurrentRow, currentFocus, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && currentFocus == NavigationFocus.CHANNEL_ROWS && focusedColIndex == FIXED_FOCUS_POSITION
        
        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350) // 350ms = czas animacji
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
            label = "miniatures_y_offset_$rowIndex"
        )
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = miniaturesYOffset),
            state = lazyListState, 
            contentPadding = PaddingValues(start = sx(380), end = sx(20)),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
                items(rowContent.size) { colIndex ->
                    val vodContent = rowContent[colIndex]
                    val isItemFocused = rowIndex == focusedRowIndex && 
                                       currentFocus == NavigationFocus.CHANNEL_ROWS && 
                                       colIndex == lazyListState.firstVisibleItemIndex && 
                                       focusedColIndex == FIXED_FOCUS_POSITION
                    
                    // Używamy tej samej logiki co w ChannelRow - każdy colIndex ma swój focus requester
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
                    
                    PosterContentCard(
                        vodContent = vodContent,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { isFocused -> 
                            if (isFocused) {
                                onChannelContentFocusChange(rowIndex, colIndex, vodContent)
                            }
                        },
                        sx = sx,
                        sy = sy
                    )
                }
                
                items(SPACER_ITEMS_COUNT) { 
                    Spacer(
                        modifier = Modifier
                            .width(sx(220))
                            .height(sy(380))
                    )
                }
            }
        
        if (isCurrentRow && focusedColIndex == FIXED_FOCUS_POSITION && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                val correctedOverlayX = 380
                
                Box(
                    modifier = Modifier
                        .offset(x = sx(correctedOverlayX), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }
                        
                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }
        
        Box(modifier = Modifier.offset(x = sx(80), y = sy(32))) {
            CategoryIcon(
                text = channel,
                isFocused = rowIndex == focusedRowIndex && focusedColIndex == -1,
                onClick = { onChannelFocusChange(channel) },
                onFocused = { isFocused -> if (isFocused) onChannelFocusChange(channel) },
                focusRequester = channelFocusRequesters[Pair(rowIndex, -1)] ?: FocusRequester(),
                sx = sx,
                sy = sy
            )
        }
    }
}

@Composable
fun PosterContentCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val itemWidth = sx(220)
    val itemHeight = sy(380)
    
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f)

    Column(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(16))
    ) {
        Box(
            modifier = Modifier
                .width(sx(200))
                .height(sy(280))
                .clip(RoundedCornerShape(sx(12)))
                .border(
                    width = if (isFocused) (6 * sx(1).value / 1.dp.value).dp else 0.dp,
                    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                )
        ) {
            AsyncImage(
                model = vodContent.imageUrl,
                contentDescription = vodContent.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (isFocused) {
            Text(
                text = "10,00 zł",
                color = Color(0xFFEEEEEE),
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.4.sp
            )
        }
    }
}

// Funkcja obsługi nawigacji dedykowana dla ChanneleScreen (bez tabów)
fun handleChanneleScreenNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onChannelContentFocusChange: (Int, Int, VodContent?) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            if (focusedRowIndex > 0) {
                val newRowIndex = focusedRowIndex - 1
                // SMART RESET: Zachowaj typ pozycji (CategoryIcon vs miniaturka)
                val targetColIndex = if (focusedColIndex == -1) -1 else FIXED_FOCUS_POSITION
                onChannelContentFocusChange(newRowIndex, targetColIndex, null)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }
        
        Key.DirectionDown -> {
            if (focusedRowIndex < channels.size - 1) {
                val newRowIndex = focusedRowIndex + 1
                // SMART RESET: Zachowaj typ pozycji (CategoryIcon vs miniaturka)
                val targetColIndex = if (focusedColIndex == -1) -1 else FIXED_FOCUS_POSITION
                onChannelContentFocusChange(newRowIndex, targetColIndex, null)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }
        
        Key.DirectionLeft -> {
            if (focusedColIndex == -1) {
                // Już na CategoryIcon - nie robimy nic
                return true
            } else if (focusedColIndex == FIXED_FOCUS_POSITION) {
                // Fokus zawsze na pozycji 0 - sprawdzamy czy możemy przewijać w lewo
                val lazyListState = lazyListStates[focusedRowIndex]
                if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                    // Przewijamy w lewo o 1 pozycję
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                    }
                } else {
                    // Nie możemy przewijać w lewo - idź do CategoryIcon
                    onChannelContentFocusChange(focusedRowIndex, -1, null)
                    channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                }
            }
            return true
        }
        
        Key.DirectionRight -> {
            if (focusedColIndex == -1) {
                // Z CategoryIcon do pierwszej widocznej pozycji (fokus zawsze na 0)
                onChannelContentFocusChange(focusedRowIndex, FIXED_FOCUS_POSITION, null)
                channelFocusRequesters[Pair(focusedRowIndex, FIXED_FOCUS_POSITION)]?.requestFocus()
            } else if (focusedColIndex == FIXED_FOCUS_POSITION) {
                // Fokus zawsze na pozycji 0 - sprawdzamy czy możemy przewijać w prawo
                val lazyListState = lazyListStates[focusedRowIndex]
                val channelName = channels.getOrNull(focusedRowIndex)
                val channelContent = gridContent[channelName] ?: emptyList()
                
                // POPRAWKA: Uwzględniamy spacer elementy w maksymalnej pozycji przewijania
                val maxScrollPosition = channelContent.size + SPACER_ITEMS_COUNT - 1
                
                if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxScrollPosition) {
                    // Przewijamy w prawo o 1 pozycję  
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                    }
                }
                // Jeśli nie możemy przewijać w prawo - pozostajemy na miejscu
            }
            return true
        }
        
        else -> return false
    }
}
