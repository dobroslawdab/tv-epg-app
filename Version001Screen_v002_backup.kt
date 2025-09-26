package com.example.tv.version001

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.example.tv.ui.theme.figmaRadialBackground
import android.content.Context
import java.io.BufferedReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// Stała pozycja fokusu w widocznym oknie - fokus zawsze tutaj, lista się przewija
private const val FIXED_FOCUS_POSITION = 0 // Pozycja 0 = pierwsza miniaturka od lewej (fokus zawsze tutaj)

// Model danych dla treści VOD z CSV
@Serializable
data class VodContent(
    val title: String,
    val description: String,
    val category: String,
    val imageUrl: String
)

// Cache dla siatki treści - zapisuje losowe przypisania
object GridCache {
    private const val CACHE_FILE = "vod_grid_cache.json"
    
    fun save(context: Context, grid: Map<String, List<VodContent>>) {
        try {
            val json = Json.encodeToString(grid)
            context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).use {
                it.write(json.toByteArray())
            }
        } catch (e: Exception) {
            // Ignoruj błędy zapisu cache
        }
    }
    
    fun load(context: Context): Map<String, List<VodContent>>? {
        return try {
            context.openFileInput(CACHE_FILE).use {
                val json = it.readBytes().toString(Charsets.UTF_8)
                Json.decodeFromString<Map<String, List<VodContent>>>(json)
            }
        } catch (e: Exception) {
            null // Cache nie istnieje lub jest nieprawidłowy
        }
    }
}

// Parser CSV - odczytuje dane z assets
fun loadVodContentFromAssets(context: Context): List<VodContent> {
    return try {
        val inputStream = context.assets.open("vod_data.csv")
        val reader = BufferedReader(inputStream.reader())
        val vodList = mutableListOf<VodContent>()
        
        // Pomiń nagłówek
        reader.readLine()
        
        // Przeczytaj każdy wiersz danych
        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.isNotBlank()) {
                    // Proste parsowanie CSV - dzielenie po przecinkach z obsługą cudzysłowów
                    val parts = line.split("\",\"")
                    if (parts.size >= 7) {
                        // Wyczyść cudzysłowy z początku i końca
                        val cleanParts = parts.map { it.trim('"') }
                        
                        val title = cleanParts[5] // data5 - tytuł
                        val description = cleanParts[3] // data - opis
                        val category = cleanParts[4] // data4 - kategoria
                        val imageUrl = cleanParts[6] // image2-src - landscape
                        
                        vodList.add(VodContent(title, description, category, imageUrl))
                    }
                }
            }
        }
        vodList
    } catch (e: Exception) {
        // Fallback - jeśli CSV nie załaduje się, zwróć puste dane
        emptyList()
    }
}

@Composable
fun Version001Screen() {
    // Skalowanie względem projektu 1920x1080
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val channels = listOf(
        "Polecane", "Nowości", "Filmy", "Seriale", "Sport", "Dzieci", "Dokumenty", "Muzyka"
    )

    // Logika ładowania/tworzenia i zapisywania siatki VOD
    val gridContent = remember {
        GridCache.load(context) ?: run {
            val vodContentList = loadVodContentFromAssets(context)
            if (vodContentList.isNotEmpty()) {
                val newGrid = channels.associateWith { 
                    List(10) { vodContentList.random() } 
                }
                GridCache.save(context, newGrid)
                newGrid
            } else {
                emptyMap()
            }
        }
    }

    // Stany nawigacji - dodajemy Szukaj przed Start
    val tabs = listOf("Szukaj", "Start", "Moje", "Telewizja", "Kino Play", "Wideo", "Aplikacje")

    var selectedTab by remember { mutableStateOf("Start") }
    var focusedTab by remember { mutableStateOf("Szukaj") } // focus na Szukaj na początku
    
    // Focus requesters dla każdej zakładki
    val focusRequesters = remember { tabs.associateWith { FocusRequester() } }
    
    var focusedChannel by remember { mutableStateOf("Polecane") }
    
    // Stan nawigacji uproszczony - tylko TOP_TABS i CHANNEL_ROWS
    var currentFocus by remember { mutableStateOf(NavigationFocus.TOP_TABS) }
    
    // Focus requesters dla channel rows - każdy wiersz: CategoryIcon (col=-1) + widoczne pozycje (col=0-4)
    val channelFocusRequesters = remember { 
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex -> // każdy kanał/wiersz
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon na pozycji -1
                repeat(5) { visibleColIndex -> // 5 widocznych pozycji (0-4) 
                    put(Pair(rowIndex, visibleColIndex), FocusRequester())
                }
            }
        }
    }
    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-1) } // -1 = CategoryIcon, 0 = FIXED_FOCUS_POSITION (fokus zawsze tutaj podczas przewijania)
    var focusedVodContent by remember { mutableStateOf<VodContent?>(null) }
    
    // LazyListStates dla każdego wiersza kanału - tworzone raz i stabilne
    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }
    
    // CoroutineScope dla animacji scrollowania
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                handleChannelNavigation(
                    event = event,
                    currentFocus = currentFocus,
                    onFocusChange = { currentFocus = it },
                    focusedTab = focusedTab,
                    focusedChannel = focusedChannel,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onTabFocusChange = { focusedTab = it },
                    onChannelFocusChange = { focusedChannel = it },
                    onChannelContentFocusChange = { row, col, content -> 
                        focusedRowIndex = row
                        focusedColIndex = col
                        if (col == -1) {
                            focusedVodContent = null
                        } else {
                            focusedVodContent = content
                        }
                    },
                    focusRequesters = focusRequesters,
                    channelFocusRequesters = channelFocusRequesters,
                    tabs = tabs,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent
                )
            }
            .focusable()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Nawigacja główna na górze
            MainNavigation(
                tabs = tabs,
                selectedTab = selectedTab,
                focusedTab = focusedTab,
                onTabSelected = { selectedTab = it },
                onTabFocused = { focusedTab = it },
                focusRequesters = focusRequesters,
                currentFocus = currentFocus,
                sx = { sx(it) },
                sy = { sy(it) }
            )
            
            // Główna zawartość - wiersze kanałów z fixed CategoryIcon + scrollable content
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                ChannelRowsLayout(
                    channels = channels,
                    gridContent = gridContent,
                    focusedChannel = focusedChannel,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelFocusChange = { focusedChannel = it },
                    onChannelContentFocusChange = { row, col, content ->
                        focusedRowIndex = row
                        focusedColIndex = col
                        focusedVodContent = content
                    },
                    currentFocus = currentFocus,
                    lazyListStates = lazyListStates,
                    sx = { sx(it) },
                    sy = { sy(it) }
                )
                
                // Overlay z detalami gdy focus na content items (nie na CategoryIcon)
                if (currentFocus == NavigationFocus.CHANNEL_ROWS && focusedColIndex >= 0) {
                    ContentDetailOverlay(
                        vodContent = focusedVodContent,
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
        }
    }
    
    // Auto focus na pierwszej zakładce
    LaunchedEffect(Unit) {
        focusRequesters["Szukaj"]?.requestFocus()
    }
}

// Enum dla nawigacji uproszczonej - tylko TOP_TABS i CHANNEL_ROWS
enum class NavigationFocus { TOP_TABS, CHANNEL_ROWS }

// Funkcja obsługi nawigacji dla nowej struktury kanałów
fun handleChannelNavigation(
    event: KeyEvent,
    currentFocus: NavigationFocus,
    onFocusChange: (NavigationFocus) -> Unit,
    focusedTab: String,
    focusedChannel: String,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onTabFocusChange: (String) -> Unit,
    onChannelFocusChange: (String) -> Unit,
    onChannelContentFocusChange: (Int, Int, VodContent?) -> Unit,
    focusRequesters: Map<String, FocusRequester>,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    tabs: List<String>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false
    
    val currentLazyListState = lazyListStates[focusedRowIndex]
    val rowContentSize = gridContent[channels[focusedRowIndex]]?.size ?: 0

    when (event.key) {
        Key.DirectionUp -> {
            when (currentFocus) {
                NavigationFocus.CHANNEL_ROWS -> {
                    if (focusedRowIndex > 0) {
                        val newRowIndex = focusedRowIndex - 1
                        val newChannel = channels[newRowIndex]
                        onChannelFocusChange(newChannel)
                        onChannelContentFocusChange(newRowIndex, focusedColIndex, null)
                        channelFocusRequesters[Pair(newRowIndex, focusedColIndex)]?.requestFocus()
                    } else {
                        onFocusChange(NavigationFocus.TOP_TABS)
                        focusRequesters[focusedTab]?.requestFocus()
                    }
                    return true
                }
                else -> return false
            }
        }
        
        Key.DirectionDown -> {
            when (currentFocus) {
                NavigationFocus.TOP_TABS -> {
                    onFocusChange(NavigationFocus.CHANNEL_ROWS)
                    val firstChannel = channels.firstOrNull() ?: return false
                    onChannelFocusChange(firstChannel)
                    onChannelContentFocusChange(0, -1, null) // start na CategoryIcon (col=-1)
                    channelFocusRequesters[Pair(0, -1)]?.requestFocus()
                    return true
                }
                NavigationFocus.CHANNEL_ROWS -> {
                    if (focusedRowIndex < channels.size - 1) {
                        val newRowIndex = focusedRowIndex + 1
                        val newChannel = channels[newRowIndex]
                        onChannelFocusChange(newChannel)
                        onChannelContentFocusChange(newRowIndex, focusedColIndex, null)
                        channelFocusRequesters[Pair(newRowIndex, focusedColIndex)]?.requestFocus()
                    }
                    return true
                }
                else -> return false
            }
        }
        
        Key.DirectionLeft -> {
            when (currentFocus) {
                NavigationFocus.TOP_TABS -> {
                    val currentIndex = tabs.indexOf(focusedTab)
                    if (currentIndex > 0) {
                        val newTab = tabs[currentIndex - 1]
                        onTabFocusChange(newTab)
                        focusRequesters[newTab]?.requestFocus()
                    }
                    return true
                }
                NavigationFocus.CHANNEL_ROWS -> {
                    if (focusedColIndex == -1) {
                        // Już na CategoryIcon - nie robimy nic
                        return true
                    } else if (focusedColIndex == FIXED_FOCUS_POSITION) {
                        // Fokus zawsze na pozycji 0 - sprawdzamy czy możemy przewijać w lewo
                        if (currentLazyListState != null && currentLazyListState.firstVisibleItemIndex > 0) {
                            // Przewijamy listę w lewo (poprzednia miniaturka pojawi się pod fokusem)
                            coroutineScope.launch {
                                currentLazyListState.animateScrollToItem(currentLazyListState.firstVisibleItemIndex - 1)
                            }
                        } else {
                            // Jesteśmy na początku listy lub currentLazyListState is null - wracamy do CategoryIcon
                            onChannelContentFocusChange(focusedRowIndex, -1, null)
                            channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                        }
                    }
                    return true
                }
                else -> return false
            }
        }
        
        Key.DirectionRight -> {
            when (currentFocus) {
                NavigationFocus.TOP_TABS -> {
                    val currentIndex = tabs.indexOf(focusedTab)
                    if (currentIndex < tabs.size - 1) {
                        val newTab = tabs[currentIndex + 1]
                        onTabFocusChange(newTab)
                        focusRequesters[newTab]?.requestFocus()
                    }
                    return true
                }
                NavigationFocus.CHANNEL_ROWS -> {
                    if (focusedColIndex == -1) {
                        // Z CategoryIcon do pierwszej widocznej pozycji (fokus zawsze na 0)
                        onChannelContentFocusChange(focusedRowIndex, FIXED_FOCUS_POSITION, null)
                        channelFocusRequesters[Pair(focusedRowIndex, FIXED_FOCUS_POSITION)]?.requestFocus()
                    } else if (focusedColIndex == FIXED_FOCUS_POSITION) {
                        // Fokus zawsze na pozycji 0 - sprawdzamy czy możemy przewijać w prawo
                        val currentFirstVisible = currentLazyListState?.firstVisibleItemIndex ?: 0
                        // Pozwalamy przewijać aż ostatni element dotrze do pierwszej pozycji
                        // Maksymalny firstVisibleItemIndex = rowContentSize - 1 (ostatni element na pierwszej pozycji)
                        val maxScrollPosition = maxOf(0, rowContentSize - 1)
                        if (currentLazyListState != null && currentFirstVisible < maxScrollPosition) {
                            // Przewijamy listę w prawo (następna miniaturka pojawi się pod fokusem)
                            coroutineScope.launch {
                                currentLazyListState.animateScrollToItem(currentFirstVisible + 1)
                            }
                        }
                        // Jeśli ostatni element jest już na pierwszej pozycji - nie przewijamy dalej
                    }
                    return true
                }
                else -> return false
            }
        }
        
        else -> return false
    }
}

@Composable
fun MainNavigation(
    tabs: List<String>,
    selectedTab: String,
    focusedTab: String,
    onTabSelected: (String) -> Unit,
    onTabFocused: (String) -> Unit,
    focusRequesters: Map<String, FocusRequester>,
    currentFocus: NavigationFocus,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sx(48), vertical = sy(32)),
        horizontalArrangement = Arrangement.spacedBy(sx(40)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar użytkownika (lewa sekcja)
        Box(
            modifier = Modifier
                .background(
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(sx(60))
                )
                .padding(sx(10))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(sx(80))
                        .background(
                            Color(0x33EEEEEE),
                            RoundedCornerShape(sx(64))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        color = Color(0xFFE2247C),
                        fontSize = (48 * (sx(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.96 * (sx(1).value / 1.dp.value)).sp
                    )
                }
                
                // Placeholder dla ikony (24x24)
                Box(modifier = Modifier.size(sx(24)))
            }
        }
        
        // Zakładki nawigacyjne (prawa sekcja)
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                NavigationTab(
                    text = tab,
                    isSelected = tab == selectedTab,
                    isFocused = tab == focusedTab && currentFocus == NavigationFocus.TOP_TABS,
                    onClick = { 
                        onTabSelected(tab)
                        onTabFocused(tab)
                    },
                    onFocused = { onTabFocused(tab) },
                    focusRequester = focusRequesters[tab] ?: FocusRequester(),
                    sx = sx,
                    sy = sy,
                    isSearchTab = tab == "Szukaj" // oznaczamy zakładkę wyszukiwania
                )
            }
        }
    }
}

@Composable
fun NavigationTab(
    text: String,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    isSearchTab: Boolean = false
) {
    // Określ kolor tła i tekstu na podstawie stanu
    val backgroundColor = when {
        isFocused -> Color(0xFF5AECD3) // FOCUSED - aqua
        isSelected && !isFocused -> Color(0xFFEEEEEE) // SELECTED - białe
        else -> Color.Transparent // DEFAULT - przezroczyste
    }
    
    val textColor = when {
        isFocused || isSelected -> Color(0xFF303030) // ciemny tekst
        else -> Color(0xFFEEEEEE) // biały tekst
    }

    Box(
        modifier = Modifier
            .background(
                backgroundColor,
                RoundedCornerShape(sx(30))
            )
            .padding(horizontal = sx(30), vertical = sy(10))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center // wyśrodkowanie zawartości
    ) {
        if (isSearchTab) {
            // Dla zakładki Szukaj - pokaż tylko ikonę lupki z taką samą wysokością jak tekst
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Szukaj",
                tint = textColor,
                modifier = Modifier.size((28 * (sy(1).value / 1.dp.value)).dp) // identyczny rozmiar jak font
            )
        } else {
            // Dla pozostałych zakładek - pokaż tekst
            Text(
                text = text,
                color = textColor,
                fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (0.20 * (sy(1).value / 1.dp.value)).sp
            )
        }
    }
}


@Composable
fun CategoryIcon(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Rozmiary z Figma
    val containerWidth = sx(240)
    val containerHeight = sy(216)
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
    
    Box(
        modifier = Modifier
            .width(containerWidth) // 240 z Figma
            .height(containerHeight) // 216 z Figma
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = (6 * sx(1).value / 1.dp.value).dp, // border width: 6 z Figma
                        color = borderColor,
                        shape = RoundedCornerShape(sx(4))
                    )
                } else {
                    Modifier // brak obramowania gdy nie focused
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Pozycjonowanie jak w Figma - left: 72, top: 40
        Box(
            modifier = Modifier
                .offset(x = sx(0), y = sy(-8)) // Wyśrodkowanie w kontenerze
                .width(sx(200))
                .height(sy(144)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sy(16)), // spacing: 16 z Figma
                modifier = Modifier.fillMaxSize()
            ) {
                // Placeholder dla ikony 96x96
                Box(
                    modifier = Modifier
                        .size(sx(96)) // 96x96 z Figma
                        .background(
                            Color(0x33EEEEEE), // półprzezroczyste tło dla ikony
                            RoundedCornerShape(sx(8))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Placeholder - tutaj można dodać konkretne ikony dla kategorii
                    Text(
                        text = text.take(2).uppercase(), // pierwsze 2 litery jako placeholder
                        color = Color(0xFFEEEEEE),
                        fontSize = (24 * (sx(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Tekst kategorii
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp, // fontSize: 24 z Figma
                    fontWeight = FontWeight.Medium, // fontWeight: 500 z Figma
                    letterSpacing = (0.48 * (sy(1).value / 1.dp.value)).sp, // letterSpacing: 0.48 z Figma
                    lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp, // lineHeight: 1.33 z Figma
                    modifier = Modifier.widthIn(max = sx(200)) // maxWidth: 200 z Figma
                )
            }
        }
    }
}

@Composable
fun ChannelRowsLayout(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    focusedChannel: String,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelFocusChange: (String) -> Unit,
    onChannelContentFocusChange: (Int, Int, VodContent?) -> Unit, // Pass VodContent up
    currentFocus: NavigationFocus,
    lazyListStates: Map<Int, LazyListState>, // Receive LazyListStates from parent
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(sy(20))
    ) {
        items(channels.size) { rowIndex ->
            val channelName = channels[rowIndex]
            val contentForChannel = gridContent[channelName] ?: emptyList()
            
            // Get the pre-created LazyListState for this row
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

            ChannelRow(
                channel = channelName,
                rowIndex = rowIndex,
                rowContent = contentForChannel,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channelFocusRequesters = channelFocusRequesters,
                onChannelFocusChange = onChannelFocusChange,
                onChannelContentFocusChange = onChannelContentFocusChange,
                currentFocus = currentFocus,
                sx = sx,
                sy = sy,
                lazyListState = lazyListState // Pass the state
            )
        }
    }
}

@Composable
fun ChannelRow(
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIcon(
            text = channel,
            isFocused = rowIndex == focusedRowIndex && focusedColIndex == -1 && currentFocus == NavigationFocus.CHANNEL_ROWS,
            onClick = { onChannelFocusChange(channel) },
            onFocused = { isFocused -> if (isFocused) onChannelFocusChange(channel) },
            focusRequester = channelFocusRequesters[Pair(rowIndex, -1)] ?: FocusRequester(),
            sx = sx,
            sy = sy
        )
        
        Spacer(modifier = Modifier.width(sx(20)))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = lazyListState, // Pass the state here
            contentPadding = PaddingValues(end = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            // Rzeczywiste content items
            items(rowContent.size) { colIndex ->
                val vodContent = rowContent[colIndex]
                ContentCard(
                    vodContent = vodContent,
                    channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                    isFocused = rowIndex == focusedRowIndex && currentFocus == NavigationFocus.CHANNEL_ROWS && colIndex == lazyListState.firstVisibleItemIndex && focusedColIndex == FIXED_FOCUS_POSITION,
                    focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester(),
                    onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex, vodContent) },
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState // Pass lazyListState to ContentCard
                )
            }
            
            // Spacer items na końcu - żeby wymusić przewijanie do ostatniego elementu
            items(5) { spacerIndex ->
                Spacer(
                    modifier = Modifier
                        .width(sx(368)) // Taka sama szerokość jak ContentCard
                        .height(sy(208))
                )
            }
        }
    }
}

@Composable
fun ContentCard(
    vodContent: VodContent,
    channelNumber: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    lazyListState: LazyListState // NEW parameter
) {
    val itemWidth = sx(368)
    val itemHeight = sy(208)
    
    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12)))
            .then(
                if (isFocused) Modifier.border(
                    width = (6 * sx(1).value / 1.dp.value).dp,
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable()
    ) {
        AsyncImage(
            model = vodContent.imageUrl,
            contentDescription = vodContent.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Gradient zgodny ze specyfikacją
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sy(88))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    ),
                    shape = RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )

        // Numer kanału
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = sx(12), top = sy(12))
                .border(width = sx(1), color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(sx(4)))
                .padding(horizontal = sx(12), vertical = sy(8)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelNumber,
                color = Color.White,
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        // Tytuł
        Text(
            text = vodContent.title,
            color = Color.White,
            fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(12), bottom = sy(12), end = sx(12))
        )
    }
}

@Composable
fun ContentDetailOverlay(
    vodContent: VodContent?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val alpha by animateFloatAsState(
        targetValue = if (vodContent != null) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "overlay_alpha"
    )

    if (vodContent != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(sx(48))
                    .width(sx(500))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(sy(16))) {
                    Text(
                        text = vodContent.title,
                        color = Color.White,
                        fontSize = (32 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (32 * 1.2f * (sy(1).value / 1.dp.value)).sp
                    )
                    
                    Text(
                        text = vodContent.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                        lineHeight = (18 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}