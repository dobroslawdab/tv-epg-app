package com.uxellence.tv.v3.version001

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
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
data class VodItem(
    val tytul: String,
    val kategoria: String,
    val opis: String,
    val logo_kanalu: String,
    val miniaturka_programu: String,
    val link: String
)

@Serializable
data class VodContent(
    val id: String,  // Unique identifier for ID-based focus restoration
    val title: String,
    val description: String,
    val category: String,
    val imageUrl: String,
    val channelLogoUrl: String,
    val link: String,
    val price: String? = null,  // Cena filmu (np. "19 zł/48h")
    val youtubeUrl: String? = null,  // Trailer/VOD URL for playback
    val cast: String? = null,  // Obsada (np. "Jason Statham, ...") — do filtrowania channels po aktorze
    val backdropUrl: String? = null  // Wide image dla MovieDetailScreen (różny od poster)
)

// Cache dla siatki treści - zapisuje losowe przypisania
object GridCache {
    private const val CACHE_FILE = "vod_grid_cache_v3.json" // v3 dla nowego formatu JSON

    fun save(context: Context, grid: Map<String, List<VodContent>>) {
        try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonString = json.encodeToString(grid)
            context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).use {
                it.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            // Ignoruj błędy zapisu cache
        }
    }

    fun load(context: Context): Map<String, List<VodContent>>? {
        return try {
            context.openFileInput(CACHE_FILE).use {
                val jsonString = it.readBytes().toString(Charsets.UTF_8)
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<Map<String, List<VodContent>>>(jsonString)
            }
        } catch (e: Exception) {
            null // Cache nie istnieje lub jest nieprawidłowy
        }
    }
}

// Parser JSON — odczytuje dane z assets. Łączy bazę vod_data.json (legacy WIDEO) z dodatkowymi
// kolekcjami serwisów (Viaplay Filmy itp.) wczytanymi z osobnych plików, żeby obecny katalog
// pozostał nietknięty a nowe paczki dodawały się prostym dropowaniem JSON-a do assets.
fun loadVodContentFromAssets(context: Context): List<VodContent> {
    val json = Json { ignoreUnknownKeys = true }

    fun loadFile(asset: String, idPrefix: String): List<VodContent> = try {
        val raw = context.assets.open(asset).bufferedReader().use { it.readText() }
        val items = json.decodeFromString<List<VodItem>>(raw)
        items.map { item ->
            VodContent(
                id = "${idPrefix}_${item.tytul.hashCode()}_${item.link.hashCode()}",
                title = item.tytul,
                description = item.opis,
                category = item.kategoria,
                imageUrl = item.miniaturka_programu,
                channelLogoUrl = item.logo_kanalu,
                link = item.link
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    // Order matters for de-dup priority. Legacy vod_data is canonical first; then service
    // packs (so "Viaplay"/"Cinemax"/etc. id prefixes win over genre packs); then genre
    // packs add any leftovers not seen in services. Chip filters in WIDEO_CHIP_CATEGORIES
    // route by category substring (or by id prefix for service-specific splits — Viaplay
    // films/series and Wideoteka Play — see applyWideoCategoryFilter).
    val all = loadFile("vod_data.json", "legacy") +
              // Services
              loadFile("viaplay_filmy.json", "viaplay") +
              loadFile("viaplay_seriale.json", "viaplay_serial") +
              loadFile("bbc_player.json", "bbc") +
              loadFile("skyshowtime.json", "sky") +
              loadFile("cinemax.json", "cinemax") +
              loadFile("axn.json", "axn") +
              loadFile("disney.json", "disney") +
              loadFile("natgeo.json", "natgeo") +
              loadFile("wideoteka_play_filmy.json", "wpf") +
              loadFile("wideoteka_play_seriale.json", "wps") +
              loadFile("wideoteka_play_kids.json", "wpk") +
              // Genres
              loadFile("wideo_akcja.json", "akcja") +
              loadFile("wideo_dramat.json", "dramat") +
              loadFile("wideo_komedia.json", "komedia") +
              loadFile("wideo_thriller.json", "thriller") +
              loadFile("wideo_horror.json", "horror") +
              loadFile("wideo_dokument.json", "dokument") +
              loadFile("wideo_dla_dzieci.json", "kids") +
              loadFile("wideo_fantasy.json", "fantasy") +
              loadFile("wideo_historia.json", "historia") +
              loadFile("wideo_muzyka.json", "muzyka") +
              loadFile("wideo_popnauk.json", "popnauk") +
              loadFile("wideo_program.json", "program") +
              loadFile("wideo_rozrywka.json", "rozrywka") +
              loadFile("wideo_scifi.json", "scifi") +
              loadFile("wideo_serial.json", "serial") +
              loadFile("wideo_sport.json", "sport") +
              loadFile("wideo_lifestyle.json", "lifestyle") +
              loadFile("wideo_wojenne.json", "wojenne")
    return all.distinctBy { it.id }
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
    
    // LaunchedEffect: Auto-reset LazyListState dla niefokusowanych wierszy
    LaunchedEffect(focusedRowIndex, focusedColIndex, currentFocus) {
        // Reset wszystkich wierszy które nie są focusowane i nie mają fokusa na miniaturkach
        repeat(channels.size) { rowIndex ->
            if (rowIndex != focusedRowIndex || focusedColIndex == -1 || currentFocus == NavigationFocus.TOP_TABS) {
                val lazyListState = lazyListStates[rowIndex]
                if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                    // Smooth scrolling do pozycji 0 (pozycja wyjściowa)
                    lazyListState.scrollToItem(index = 0, scrollOffset = 0)
                }
            }
        }
    }

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
                        // SMART RESET: Zachowaj typ pozycji (CategoryIcon vs miniaturka)
                        val targetColIndex = if (focusedColIndex == -1) -1 else FIXED_FOCUS_POSITION
                        onChannelContentFocusChange(newRowIndex, targetColIndex, null)
                        // SAFE: Sprawdź czy FocusRequester istnieje
                        channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
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
                        // SMART RESET: Zachowaj typ pozycji (CategoryIcon vs miniaturka)
                        val targetColIndex = if (focusedColIndex == -1) -1 else FIXED_FOCUS_POSITION
                        onChannelContentFocusChange(newRowIndex, targetColIndex, null)
                        // SAFE: Sprawdź czy FocusRequester istnieje
                        channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
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
    sy: (Int) -> Dp,
    logoUrl: String? = null,
    logoDrawableId: Int? = null,
    showIcon: Boolean = true,
    showBackgroundWhenFocused: Boolean = false,
    isExpanded: Boolean = false,
    showChevron: Boolean = false,
    onChevronClick: (() -> Unit)? = null,
    containerHeightOverride: Dp? = null,    // optional override (np. dopasowanie do wysokości kafelków obok)
    containerWidthOverride: Dp? = null,     // optional override (np. kwadratowa kategoria 170×170)
    cornerRadiusOverride: Dp? = null,       // optional override (np. 12dp jak na AppIconCard)
    iconTextSpacingOverride: Dp? = null,    // optional override (np. 8 zamiast 16)
    okPromptAfterDelay: Boolean = false,    // gdy true: po 2s fokusa pokaż kółko OK + label
    okPromptLabel: String = ""              // tekst pod kółkiem OK (np. "Wszystkie aplikacje")
) {
    // Rozmiary z Figma
    val containerWidth = containerWidthOverride ?: sx(240)
    val containerHeight = containerHeightOverride ?: sy(216)
    val cornerShape = RoundedCornerShape(cornerRadiusOverride ?: sx(4))
    val iconTextSpacing = iconTextSpacingOverride ?: sy(16)

    // 4-sec delay focus → switch to "OK" prompt
    var showOkPrompt by remember(isFocused) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(isFocused, okPromptAfterDelay) {
        if (isFocused && okPromptAfterDelay) {
            kotlinx.coroutines.delay(4000)
            showOkPrompt = true
        } else {
            showOkPrompt = false
        }
    }
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent

    // Chevron rotation animation (350ms smooth rotation)
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
        label = "chevron_rotation"
    )

    Box(
        modifier = Modifier
            .width(containerWidth) // 240 z Figma
            .height(containerHeight) // 216 z Figma
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = (6 * sx(1).value / 1.dp.value).dp, // border width: 6 z Figma
                        color = borderColor,
                        shape = cornerShape
                    )
                } else {
                    Modifier // brak obramowania gdy nie focused
                }
            )
            .clip(cornerShape)
            .then(
                if (showBackgroundWhenFocused) {
                    if (isFocused) {
                        Modifier.background(Color(0x4D000000)) // rgba(0, 0, 0, 0.30) - focused
                    } else {
                        Modifier.background(Color(0x1A000000)) // rgba(0, 0, 0, 0.10) - unfocused
                    }
                } else {
                    Modifier // brak tła
                }
            )
            .focusRequester(focusRequester)
            .clickable { onClick() }
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Pozycjonowanie jak w Figma - left: 72, top: 40
        // Height increased from 144 to 180 to fit 2-line text labels
        Box(
            modifier = Modifier
                .offset(x = sx(0), y = sy(-8)) // Wyśrodkowanie w kontenerze
                .width(sx(200))
                .height(sy(180)), // 96 (icon) + 16 (spacing) + 68 (2 lines of text)
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (showIcon) Arrangement.spacedBy(iconTextSpacing) else Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Ikona 96x96 - logo lub placeholder. Z animowanym przejściem do "OK" prompt (slide-in od dołu).
                if (showIcon) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = showOkPrompt,
                        transitionSpec = {
                            (androidx.compose.animation.slideInVertically(
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                            ) { fullHeight -> fullHeight } +
                                androidx.compose.animation.fadeIn(
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                                )
                            ) togetherWith androidx.compose.animation.slideOutVertically(
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                            ) { fullHeight -> -fullHeight } + androidx.compose.animation.fadeOut(
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                            )
                        },
                        label = "category_icon_swap"
                    ) { isOk ->
                        Box(
                            modifier = Modifier
                                .size(sx(96))
                                .clip(RoundedCornerShape(sx(8))),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isOk -> {
                                    // OK przycisk — białe kółko z ciemnym tłem (jak na pilocie)
                                    Box(
                                        modifier = Modifier
                                            .size(sx(52))
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Color(0xFF281443))
                                            .border(
                                                width = sx(2),
                                                color = Color(0xFFEEEEEE),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "OK",
                                            color = Color(0xFFEEEEEE),
                                            fontSize = (15 * (sx(1).value / 1.dp.value)).sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                logoDrawableId != null -> {
                                    Icon(
                                        painter = painterResource(id = logoDrawableId),
                                        contentDescription = text,
                                        modifier = Modifier.size(sx(68)),
                                        tint = Color.Unspecified
                                    )
                                }
                                logoUrl != null -> {
                                    AsyncImage(
                                        model = logoUrl,
                                        contentDescription = text,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                else -> {
                                    Text(
                                        text = text.take(2).uppercase(),
                                        color = Color(0xFFEEEEEE),
                                        fontSize = (24 * (sx(1).value / 1.dp.value)).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Tekst kategorii + chevron (dla expandable channels). Tekst też z animacją slide.
                androidx.compose.animation.AnimatedContent(
                    targetState = showOkPrompt,
                    transitionSpec = {
                        (androidx.compose.animation.slideInVertically(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                        ) { fullHeight -> fullHeight } +
                            androidx.compose.animation.fadeIn(
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                            )
                        ) togetherWith androidx.compose.animation.slideOutVertically(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                        ) { fullHeight -> -fullHeight } + androidx.compose.animation.fadeOut(
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                        )
                    },
                    label = "category_text_swap"
                ) { isOk ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(max = sx(200))
                    ) {
                        Text(
                            text = if (isOk) okPromptLabel else text,
                            textAlign = TextAlign.Center,
                            color = Color(0xFFEEEEEE),
                            fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (0.48 * (sy(1).value / 1.dp.value)).sp,
                            lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 2,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // Chevron — tylko gdy CategoryIcon ma fokus i NIE w OK-prompt mode
                        if (showChevron && isFocused && !isOk) {
                            Spacer(modifier = Modifier.width(sx(8)))
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = Color(0xFFEEEEEE),
                                modifier = Modifier
                                    .size(sx(20), sy(20))
                                    .rotate(chevronRotation)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Funkcja obliczająca pozycję Y dla każdego kanału - zfokusowany zawsze na Y: 340px
fun calculateChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    currentFocus: NavigationFocus,
    sy: (Int) -> Dp
): Dp {
    val FIXED_FOCUS_Y = 340 // Zfokusowany kanał zawsze na tej wysokości
    val NORMAL_ROW_HEIGHT = 240 // CategoryIcon height (216) + spacing (20) + margin
    val EXPANDED_ROW_HEIGHT = 530 // 240 + 290 dla miniaturek przesuwanych w dół
    
    return when {
        // Zfokusowany kanał - zawsze na Y: 340px
        rowIndex == focusedRowIndex && currentFocus == NavigationFocus.CHANNEL_ROWS -> {
            sy(FIXED_FOCUS_Y)
        }
        // Kanały powyżej zfokusowanego - przesuwają się w górę
        rowIndex < focusedRowIndex && currentFocus == NavigationFocus.CHANNEL_ROWS -> {
            sy(FIXED_FOCUS_Y - (focusedRowIndex - rowIndex) * NORMAL_ROW_HEIGHT)
        }
        // Kanały poniżej zfokusowanego - przesuwają się w dół aby zrobić miejsce
        rowIndex > focusedRowIndex && currentFocus == NavigationFocus.CHANNEL_ROWS -> {
            // Sprawdzamy czy zfokusowany kanał ma miniaturkę zfokusowaną (powiększony)
            val focusedChannelExpansion = if (focusedColIndex >= 0) EXPANDED_ROW_HEIGHT else NORMAL_ROW_HEIGHT
            sy(FIXED_FOCUS_Y + focusedChannelExpansion + (rowIndex - focusedRowIndex - 1) * NORMAL_ROW_HEIGHT)
        }
        // Normalne pozycje gdy fokus nie na CHANNEL_ROWS
        else -> sy(140 + rowIndex * NORMAL_ROW_HEIGHT) // Zaczynamy od Y: 140px (po MainNavigation)
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
    // Zastąpić LazyColumn Box-em z pozycjonowaniem absolutnym
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(channels.size) { rowIndex ->
            val channelName = channels[rowIndex]
            val contentForChannel = gridContent[channelName] ?: emptyList()
            
            // Get the pre-created LazyListState for this row
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()
            
            // Oblicz pozycję Y dla tego kanału
            val targetY = calculateChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                currentFocus = currentFocus,
                sy = sy
            )
            
            // Animowane przesunięcie Y dla każdego kanału
            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                label = "channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
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
    val isCurrentRow = rowIndex == focusedRowIndex
    
    // State dla opóźnionego wyświetlania szczegółów
    var showDetailsWithDelay by remember { mutableStateOf(false) }
    
    // LaunchedEffect dla synchronizacji szczegółów z animacją
    LaunchedEffect(isCurrentRow, currentFocus, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && currentFocus == NavigationFocus.CHANNEL_ROWS && focusedColIndex == FIXED_FOCUS_POSITION
        
        if (shouldShowDetails) {
            // Opóźnienie równe czasowi animacji slide-down
            kotlinx.coroutines.delay(350) // 350ms = czas animacji
            showDetailsWithDelay = true
        } else {
            // Natychmiastowe ukrycie gdy fokus opuszcza miniaturki
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Miniaturki - animowane przesunięcie w dół o 290px gdy zfokusowane (z-index: 0 - tło)
        val isMiniaturesOnScreen = isCurrentRow && currentFocus == NavigationFocus.CHANNEL_ROWS && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
            label = "miniatures_y_offset_$rowIndex"
        )
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth() // Pełna szerokość ekranu 0px → 1920px (brak clippingu!)
                .offset(y = miniaturesYOffset), // Tylko animacja Y, brak offsetu X
            state = lazyListState, // Pass the state here  
            contentPadding = PaddingValues(start = sx(380), end = sx(20)), // start=380px - pierwsza miniaturka na pozycji 380px
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
        
        // DetailedContentOverlay nad zfokusowaną miniaturką w tym kanale - z opóźnieniem
        if (isCurrentRow && currentFocus == NavigationFocus.CHANNEL_ROWS && focusedColIndex == FIXED_FOCUS_POSITION && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                // Pozycja X: LazyRow contentPadding start = 380px (bezpośrednio wyrównane do pierwszej miniaturki)
                val correctedOverlayX = 380 // Dokładna pozycja pierwszej miniaturki
                
                Box(
                    modifier = Modifier
                        .offset(x = sx(correctedOverlayX), y = sy(0)) // Y: 0px - overlay zawsze na tym samym poziomie co CategoryIcon
                        .width(sx(1500)) // Rozszerzone do prawie pełnej szerokości ekranu (1920-380-40=1500)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        // Główny tytuł - jedna linia z elipsą
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500)) // Pełna szerokość nowego bloku
                        )
                        
                        // Metadane w jednym wierszu z separatorami
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kategoria
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                            
                            // Separator |
                            Box(
                                modifier = Modifier
                                    .width((2 * (sx(1).value / 1.dp.value)).dp)
                                    .height(sx(24))
                                    .background(Color(0xCCEEEEEE))
                            )
                            
                            // Czas trwania
                            Text(
                                text = "25 min",
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                            
                            // Separator |
                            Box(
                                modifier = Modifier
                                    .width((2 * (sx(1).value / 1.dp.value)).dp)
                                    .height(sx(24))
                                    .background(Color(0xCCEEEEEE))
                            )
                            
                            // Rok
                            Text(
                                text = "2020 r.",
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                            
                            // Separator |
                            Box(
                                modifier = Modifier
                                    .width((2 * (sx(1).value / 1.dp.value)).dp)
                                    .height(sx(24))
                                    .background(Color(0xCCEEEEEE))
                            )
                            
                            // Kraj
                            Text(
                                text = "Polska",
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                            
                            // Separator |
                            Box(
                                modifier = Modifier
                                    .width((2 * (sx(1).value / 1.dp.value)).dp)
                                    .height(sx(24))
                                    .background(Color(0xCCEEEEEE))
                            )
                            
                            // Wiek
                            Text(
                                text = "7 lat",
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }
                        
                        // Opis
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
        
        // CategoryIcon - floating nad LazyRow (z-index: 1 - na wierzchu)
        Box(modifier = Modifier.offset(x = sx(80))) {
            CategoryIcon(
                text = channel,
                isFocused = rowIndex == focusedRowIndex && focusedColIndex == -1 && currentFocus == NavigationFocus.CHANNEL_ROWS,
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
