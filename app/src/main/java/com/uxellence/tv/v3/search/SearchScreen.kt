package com.uxellence.tv.v3.search

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.uxellence.tv.v3.R
import androidx.compose.ui.platform.LocalContext
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.focus.GlobalFocusState
import com.uxellence.tv.v3.model.SupabaseMovie
import com.uxellence.tv.v3.repository.EpgRepository
import com.uxellence.tv.v3.repository.SupabaseMoviesRepository
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.loadVodContentFromAssets
import kotlinx.coroutines.launch

private val ManropeFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private val COLOR_BG = Color(0xFF281443)
private val COLOR_TEXT_PRIMARY = Color(0xFFEEEEEE)
private val COLOR_TEXT_TERTIARY = Color(0x99EEEEEE)
private val COLOR_FOCUS_BORDER = Color(0xFF5FEDD4)
private val COLOR_INPUT_BG = Color(0x33000000)
private val COLOR_KEY_BG = Color(0x33EEEEEE)

// Keyboard layout — ABC 6 columns
private val KB_ABC = listOf(
    listOf("a", "b", "c", "d", "e", "f"),
    listOf("g", "h", "i", "j", "k", "l"),
    listOf("m", "n", "o", "p", "q", "r"),
    listOf("s", "t", "u", "v", "w", "x"),
    listOf("y", "z"),
    listOf("ABC", "SPACE", "\u232B")
)

private val KB_123 = listOf(
    listOf("1", "2", "3", "4", "5", "6"),
    listOf("7", "8", "9", "0", "@", "#"),
    listOf("\$", "%", "&", "*", "(", ")"),
    listOf("-", "_", "+", "=", "!", "?"),
    listOf(".", ",", ":", ";", "/", "'"),
    listOf("ABC", "SPACE", "\u232B")
)

private const val KEY_BACKSPACE = "\u232B"

// Polish diacritics map — long press on letter shows variant
private val POLISH_VARIANTS = mapOf(
    "a" to "ą", "c" to "ć", "e" to "ę", "l" to "ł",
    "n" to "ń", "o" to "ó", "s" to "ś", "z" to "ż", "x" to "ź"
)

// Popular search suggestions (Mode 2 chips)
private val POPULAR_SEARCHES = listOf(
    "Jak wytresować smoka", "Furioza", "Zwierzogród",
    "Piotruś Pan", "Kevin Sam w Domu",
    "Mecz Barcelona", "Lalka"
)

// Focus areas
private const val FOCUS_MIC = "MIC"
private const val FOCUS_TAB = "TAB"
private const val FOCUS_KEYBOARD = "KEYBOARD"
private const val FOCUS_CHANNELS = "CHANNELS"
private const val FOCUS_SUGGESTIONS = "SUGGESTIONS"

// Unified item for search results (works for both Supabase movies and EPG programs)
private data class SearchItem(
    val title: String,
    val posterUrl: String?,
    val supabaseMovie: SupabaseMovie? = null, // non-null if it's a Kino Play movie
    val channelLogoUrl: String? = null,        // EPG: channel logo
    val channelName: String? = null,           // EPG: channel name (e.g. "TVP 1")
    val startTime: java.time.Instant? = null,  // EPG: program start
    val endTime: java.time.Instant? = null,    // EPG: program end
    val vodLink: String? = null                // Wideo: link to content
)

// Channel types
private const val CHANNEL_TYPE_EPG = "epg"       // horizontal landscape thumbnails
private const val CHANNEL_TYPE_KINO = "kino"     // vertical posters
private const val CHANNEL_TYPE_WIDEO = "wideo"   // horizontal landscape thumbnails (from VOD)
private const val CHANNEL_TYPE_TMDB = "tmdb"     // vertical posters (from TMDB API)

// Channel definitions for search results
private data class SearchChannel(
    val title: String,
    val items: List<SearchItem>,
    val type: String = CHANNEL_TYPE_KINO  // default to vertical posters
)

@Composable
fun SearchScreen(
    globalFocusState: MutableState<GlobalFocusState>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onNavigateToMovieDetail: (com.uxellence.tv.v3.VodSlideData) -> Unit = {},
    onReturnToMenu: () -> Unit = {},
    searchKeyboardMode: Int = 0
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val useSystemKeyboard = searchKeyboardMode == 1
    val isMode2 = searchKeyboardMode == 2

    var searchQuery by remember { mutableStateOf("") }
    var isNumberMode by remember { mutableStateOf(false) }
    var keyboardRow by remember { mutableIntStateOf(0) }
    var keyboardCol by remember { mutableIntStateOf(0) }

    // Focus — starts NONE (no highlight until user presses DOWN from menu)
    var focusArea by remember { mutableStateOf("NONE") }

    // Long press tracking for keyboard
    var longPressKey by remember { mutableStateOf<String?>(null) }
    var longPressHandled by remember { mutableStateOf(false) }

    // Channel focus
    var channelRow by remember { mutableIntStateOf(0) }
    var channelCol by remember { mutableIntStateOf(0) }

    // System keyboard mode: track if TextField is actively editing
    var isInputEditing by remember { mutableStateOf(false) }

    val epgRepository = remember { EpgRepository.getInstance(context) }

    // All movies from Supabase (Kino Play)
    var allMovies by remember { mutableStateOf<List<SupabaseMovie>>(emptyList()) }
    // All VOD content (Wideo)
    var allVodContent by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            allMovies = SupabaseMoviesRepository.fetchAllMovies()
        } catch (e: Exception) {
            Log.e("SearchScreen", "Failed to fetch movies", e)
        }
        try {
            allVodContent = loadVodContentFromAssets(context)
            Log.d("SearchScreen", "Loaded ${allVodContent.size} VOD items")
        } catch (e: Exception) {
            Log.e("SearchScreen", "Failed to load VOD content", e)
        }
    }

    // Channel logo lookup
    val channelManager = remember { ChannelManager }
    LaunchedEffect(Unit) { channelManager.initialize(context) }

    // EPG search results (debounced)
    var epgResults by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            epgResults = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        try {
            val programs = epgRepository.searchByTitle(searchQuery, maxResults = 20)
            epgResults = programs.map { prog ->
                val channelInfo = channelManager.matchWithEpg(prog.channelId)
                SearchItem(
                    title = prog.title,
                    posterUrl = prog.iconUrl,
                    channelLogoUrl = channelInfo?.logoUrl,
                    channelName = channelInfo?.name ?: prog.channelId,
                    startTime = prog.startUtc,
                    endTime = prog.endUtc
                )
            }
        } catch (e: Exception) {
            Log.e("SearchScreen", "EPG search error", e)
        }
    }

    // TMDB search results (debounced)
    var tmdbResults by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            tmdbResults = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(500)
        try {
            val results = GeminiService.searchMovies(searchQuery, maxResults = 20)
            tmdbResults = results.map { tmdb ->
                SearchItem(
                    title = tmdb.displayTitle,
                    posterUrl = tmdb.poster_path?.let { "https://image.tmdb.org/t/p/w342$it" }
                )
            }
        } catch (e: Exception) {
            Log.e("SearchScreen", "TMDB search error", e)
        }
    }

    // Channels — dynamic: default when empty, search results when typing
    val channels = remember(allMovies, allVodContent, searchQuery, epgResults, tmdbResults) {
        fun SupabaseMovie.toSearchItem() = SearchItem(
            title = title,
            posterUrl = poster_url,
            supabaseMovie = this
        )
        fun VodContent.toSearchItem() = SearchItem(
            title = title,
            posterUrl = imageUrl,
            vodLink = link
        )

        if (searchQuery.length < 2) {
            // Default channels
            val newest = allMovies.sortedByDescending { it.created_at }.take(10).map { it.toSearchItem() }
            val recommended = allMovies.filter { it.is_recommended }.take(10).map { it.toSearchItem() }
            val top10 = allMovies.filter { it.is_top10 }.sortedBy { it.top10_order }.take(10).map { it.toSearchItem() }
            listOf(
                SearchChannel("Odkryj nowości", newest, CHANNEL_TYPE_KINO),
                SearchChannel("Polecane", recommended, CHANNEL_TYPE_KINO),
                SearchChannel("Popularne", top10, CHANNEL_TYPE_KINO)
            ).filter { it.items.isNotEmpty() }
        } else {
            // Search results — EPG + Kino Play + Wideo
            val query = searchQuery.lowercase()
            val kinoPlayMatched = allMovies.filter {
                it.title.lowercase().contains(query) ||
                    (it.genre?.lowercase()?.contains(query) == true) ||
                    (it.description?.lowercase()?.contains(query) == true)
            }.take(20).map { it.toSearchItem() }

            val wideoMatched = allVodContent.filter {
                it.title.lowercase().contains(query) ||
                    it.category.lowercase().contains(query) ||
                    it.description.lowercase().contains(query)
            }.take(20).map { it.toSearchItem() }

            listOf(
                SearchChannel("W Telewizji", epgResults, CHANNEL_TYPE_EPG),
                SearchChannel("Kino Play", kinoPlayMatched, CHANNEL_TYPE_KINO),
                SearchChannel("Wideo", wideoMatched, CHANNEL_TYPE_WIDEO),
                SearchChannel("Filmy i seriale", tmdbResults, CHANNEL_TYPE_TMDB)
            ).filter { it.items.isNotEmpty() }
        }
    }

    // Reset channel position when channels change (e.g. default → search results)
    LaunchedEffect(channels.size) {
        channelRow = 0
        channelCol = 0
    }

    // TMDB autocomplete suggestions (debounced)
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(searchQuery) {
        if (searchQuery.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400) // debounce
        try {
            suggestions = GeminiService.searchTitles(searchQuery, maxResults = 6)
        } catch (e: Exception) {
            Log.e("SearchScreen", "TMDB suggestions error", e)
        }
    }

    // Keyboard
    val kbRows = if (isNumberMode) KB_123 else KB_ABC
    val kbRow = keyboardRow.coerceIn(0, kbRows.size - 1)
    val kbCol = keyboardCol.coerceIn(0, kbRows[kbRow].size - 1)
    val chRow = channelRow.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    val chColMax = if (channels.isNotEmpty()) (channels[chRow].items.size - 1).coerceAtLeast(0) else 0
    val chCol = channelCol.coerceIn(0, chColMax)

    // Lazy list states for channels
    val lazyListStates = remember(channels.size) {
        List(channels.size.coerceAtLeast(1)) { androidx.compose.foundation.lazy.LazyListState() }
    }

    // Animate keyboard slide (only in ABC mode — mode 0)
    val isAbcMode = searchKeyboardMode == 0
    val showKeyboard = isAbcMode && focusArea != FOCUS_CHANNELS
    val keyboardOffset by animateDpAsState(
        targetValue = if (showKeyboard) 0.dp else -sx(480),
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "kb_offset"
    )
    val contentOffset by animateDpAsState(
        targetValue = if (isAbcMode && showKeyboard) sx(450) else 0.dp,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "content_offset"
    )

    // Mode 2: left panel slide animation (hide when channels focused)
    val mode2PanelFixedWidth = sx(920)
    val mode2ShowPanel = isMode2 && focusArea != FOCUS_CHANNELS
    val mode2PanelWidth = mode2PanelFixedWidth
    val mode2PanelOffset by animateDpAsState(
        targetValue = if (mode2ShowPanel) 0.dp else -mode2PanelWidth,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "m2_panel"
    )
    val mode2ContentStart by animateDpAsState(
        targetValue = if (mode2ShowPanel) mode2PanelWidth else 0.dp,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "m2_content"
    )
    // When panel hidden (channels focused), use same padding as mode 0/1
    val mode2ChannelPadding by animateDpAsState(
        targetValue = if (mode2ShowPanel) sx(20) else sx(80),
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "m2_ch_pad"
    )

    // Vertical scroll: focused channel always at first position
    // Calculate cumulative offset based on actual channel heights
    val channelHeights = remember(channels) {
        channels.map { ch ->
            when (ch.type) {
                CHANNEL_TYPE_EPG, CHANNEL_TYPE_WIDEO -> 310  // title(28) + spacer(8) + card(214) + title(30) + gap(20)
                else -> 410  // title(28) + spacer(8) + poster(310) + title(30) + spacer(6) + gap(20)
            }
        }
    }
    val cumulativeOffset = remember(channelHeights, chRow) {
        channelHeights.take(chRow).sum()
    }
    val channelsVerticalOffset by animateDpAsState(
        targetValue = if (focusArea == FOCUS_CHANNELS && chRow > 0) -sy(cumulativeOffset) else 0.dp,
        animationSpec = tween(300, easing = EaseInOutCubic),
        label = "channels_y_offset"
    )

    fun onKeyPressed(key: String) {
        when (key) {
            KEY_BACKSPACE -> { if (searchQuery.isNotEmpty()) searchQuery = searchQuery.dropLast(1); suggestionIndex = 0 }
            "SPACE" -> { searchQuery += " "; suggestionIndex = 0 }
            "ABC" -> { isNumberMode = !isNumberMode; keyboardRow = 0; keyboardCol = 0 }
            else -> { searchQuery += key; suggestionIndex = 0 }
        }
    }

    // Scroll channel LazyRow to focused item
    LaunchedEffect(channelCol, channelRow, focusArea) {
        if (focusArea == FOCUS_CHANNELS && channels.isNotEmpty()) {
            val stateIdx = chRow.coerceIn(0, lazyListStates.size - 1)
            lazyListStates[stateIdx].animateScrollToItem(chCol.coerceAtLeast(0))
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow > 0) {
            rootFocusRequester.requestFocus()
            if (focusArea == "NONE") {
                // Both modes start on input (FOCUS_KEYBOARD = input in system mode)
                focusArea = FOCUS_KEYBOARD
                keyboardRow = 0
                keyboardCol = 0
            }
        } else {
            // Returned to menu — clear focus
            focusArea = "NONE"
        }
    }

    // OVERLAY-MODE refocus: fired by MainActivity when MovieDetail / Purchase /
    // RentalProcessing closes back onto a still-mounted SEARCH tab. SearchScreen
    // is rendered inside TopMenuScreen2, which stays alive via movableContentOf
    // — focusArea / channel positions / lazyListStates all survive — but Compose
    // focus owner is gone (MovieDetail captured it). We re-grab it on rootFocusRequester.
    // Skip-first-fire pattern: trigger is a session-wide counter so the initial value
    // would otherwise refocus on every remount and steal focus from the menu tab.
    var didSearchRefocusInitialFire by remember { mutableStateOf(false) }
    LaunchedEffect(com.uxellence.tv.v3.VodDataCache.searchRefocusTrigger.value) {
        if (!didSearchRefocusInitialFire) {
            didSearchRefocusInitialFire = true
            return@LaunchedEffect
        }
        if (com.uxellence.tv.v3.VodDataCache.searchRefocusTrigger.value > 0 &&
            globalFocusState.value.sectionId == "SEARCH" &&
            globalFocusState.value.currentRow > 0) {
            kotlinx.coroutines.delay(50)
            try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(COLOR_BG)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Long press detection for keyboard keys
                if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter)) {
                    longPressKey = null
                    longPressHandled = false
                    return@onPreviewKeyEvent false
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Check if this is a long press (repeated key event)
                val isLongPress = event.nativeKeyEvent.repeatCount > 0

                // Helper: reset all channel scroll positions
                fun resetChannels() {
                    channelRow = 0
                    channelCol = 0
                    coroutineScope.launch {
                        lazyListStates.forEach { it.scrollToItem(0) }
                    }
                }

                when (event.key) {
                    Key.Back -> {
                        when {
                            focusArea == FOCUS_SUGGESTIONS -> {
                                focusArea = FOCUS_KEYBOARD
                                suggestionIndex = 0
                            }
                            focusArea == FOCUS_CHANNELS -> {
                                focusArea = FOCUS_KEYBOARD
                                resetChannels()
                            }
                            focusArea == FOCUS_KEYBOARD -> {
                                if (isInputEditing) {
                                    // Exit editing mode first
                                    isInputEditing = false
                                } else if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    onReturnToMenu()
                                }
                            }
                            else -> onReturnToMenu()
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        when (focusArea) {
                            FOCUS_MIC -> { onReturnToMenu() }
                            FOCUS_KEYBOARD -> {
                                if (useSystemKeyboard || isMode2) {
                                    // System keyboard modes (1 & 2): UP → menu
                                    isInputEditing = false
                                    onReturnToMenu()
                                    return@onPreviewKeyEvent true
                                }
                                if (kbRow > 0) {
                                    keyboardRow = kbRow - 1
                                    keyboardCol = kbCol.coerceIn(0, kbRows[keyboardRow].size - 1)
                                } else {
                                    onReturnToMenu()
                                }
                            }
                            FOCUS_SUGGESTIONS -> {
                                if (isMode2) {
                                    val cols = 3
                                    val prevIdx = suggestionIndex - cols
                                    if (prevIdx >= 0) {
                                        suggestionIndex = prevIdx
                                    } else {
                                        focusArea = FOCUS_KEYBOARD
                                        suggestionIndex = 0
                                    }
                                } else {
                                    focusArea = FOCUS_KEYBOARD
                                    suggestionIndex = 0
                                }
                            }
                            FOCUS_CHANNELS -> {
                                if (chRow > 0) {
                                    channelRow = chRow - 1
                                    channelCol = 0
                                    coroutineScope.launch {
                                        lazyListStates.forEach { it.scrollToItem(0) }
                                    }
                                } else if (isMode2) {
                                    // Mode 2: first channel UP → chips or input
                                    val chipItems = if (searchQuery.length < 2) POPULAR_SEARCHES else suggestions
                                    if (chipItems.isNotEmpty()) {
                                        focusArea = FOCUS_SUGGESTIONS
                                        suggestionIndex = 0
                                    } else {
                                        focusArea = FOCUS_KEYBOARD
                                    }
                                    resetChannels()
                                } else if (suggestions.isNotEmpty()) {
                                    focusArea = FOCUS_SUGGESTIONS
                                    suggestionIndex = 0
                                } else if (useSystemKeyboard) {
                                    focusArea = FOCUS_KEYBOARD
                                    resetChannels()
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionDown -> {
                        when (focusArea) {
                            FOCUS_MIC -> {
                                focusArea = FOCUS_KEYBOARD
                                keyboardRow = 0
                                keyboardCol = 0
                            }
                            FOCUS_KEYBOARD -> {
                                if (useSystemKeyboard || isMode2) {
                                    // System keyboard modes (1 & 2): DOWN → chips or channels
                                    isInputEditing = false
                                    val chipItems = if (isMode2 && searchQuery.length < 2) POPULAR_SEARCHES
                                        else suggestions
                                    if (isMode2 && chipItems.isNotEmpty()) {
                                        focusArea = FOCUS_SUGGESTIONS
                                        suggestionIndex = 0
                                    } else if (suggestions.isNotEmpty() && !isMode2) {
                                        focusArea = FOCUS_SUGGESTIONS
                                        suggestionIndex = 0
                                    } else {
                                        focusArea = FOCUS_CHANNELS
                                        channelRow = 0
                                        channelCol = 0
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                // Mode 0 only: navigate keyboard rows
                                if (kbRow < kbRows.size - 1) {
                                    keyboardRow = kbRow + 1
                                    keyboardCol = kbCol.coerceIn(0, kbRows[keyboardRow].size - 1)
                                }
                            }
                            FOCUS_SUGGESTIONS -> {
                                if (isMode2) {
                                    // Mode 2: DOWN from chips → next chip row or channels
                                    val chipItems = if (searchQuery.length < 2) POPULAR_SEARCHES else suggestions
                                    val cols = 3
                                    val nextIdx = suggestionIndex + cols
                                    if (nextIdx < chipItems.size) {
                                        suggestionIndex = nextIdx
                                    } else {
                                        focusArea = FOCUS_CHANNELS
                                        channelRow = 0
                                        channelCol = 0
                                        suggestionIndex = 0
                                    }
                                } else {
                                    focusArea = FOCUS_CHANNELS
                                    channelCol = 0
                                    suggestionIndex = 0
                                }
                            }
                            FOCUS_CHANNELS -> {
                                if (chRow < channels.size - 1) {
                                    channelRow = chRow + 1
                                    channelCol = 0
                                    coroutineScope.launch {
                                        lazyListStates.forEach { it.scrollToItem(0) }
                                    }
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionLeft -> {
                        when (focusArea) {
                            FOCUS_KEYBOARD -> {
                                if (kbCol > 0) keyboardCol = kbCol - 1
                            }
                            FOCUS_SUGGESTIONS -> {
                                if (isMode2) {
                                    // Mode 2: chips are in left panel, navigate within chip grid
                                    val chipItems = if (searchQuery.length < 2) POPULAR_SEARCHES else suggestions
                                    val cols = 3
                                    val col = suggestionIndex % cols
                                    if (col > 0) suggestionIndex -= 1
                                } else if (suggestionIndex > 0) {
                                    suggestionIndex -= 1
                                } else {
                                    focusArea = FOCUS_KEYBOARD
                                    suggestionIndex = 0
                                }
                            }
                            FOCUS_CHANNELS -> {
                                if (chCol > 0) {
                                    channelCol = chCol - 1
                                } else {
                                    focusArea = FOCUS_KEYBOARD
                                    resetChannels()
                                }
                            }
                        }
                        true
                    }

                    Key.DirectionRight -> {
                        when (focusArea) {
                            FOCUS_KEYBOARD -> {
                                if (isMode2) {
                                    // Mode 2: RIGHT from input → channels
                                    focusArea = FOCUS_CHANNELS
                                    channelCol = 0
                                    return@onPreviewKeyEvent true
                                }
                                if (kbCol < kbRows[kbRow].size - 1) {
                                    keyboardCol = kbCol + 1
                                } else {
                                    if (suggestions.isNotEmpty()) {
                                        focusArea = FOCUS_SUGGESTIONS
                                        suggestionIndex = 0
                                    } else {
                                        focusArea = FOCUS_CHANNELS
                                        channelCol = 0
                                    }
                                }
                            }
                            FOCUS_SUGGESTIONS -> {
                                if (isMode2) {
                                    // Mode 2: chips in grid — RIGHT within row or to channels
                                    val chipItems = if (searchQuery.length < 2) POPULAR_SEARCHES else suggestions
                                    val cols = 3
                                    val col = suggestionIndex % cols
                                    if (col < cols - 1 && suggestionIndex < chipItems.size - 1) {
                                        suggestionIndex += 1
                                    } else {
                                        focusArea = FOCUS_CHANNELS
                                        channelCol = 0
                                    }
                                } else if (suggestionIndex < suggestions.size - 1) {
                                    suggestionIndex += 1
                                }
                            }
                            FOCUS_CHANNELS -> {
                                val maxCol = if (channels.isNotEmpty()) channels[chRow].items.size - 1 else 0
                                if (chCol < maxCol) channelCol = chCol + 1
                            }
                        }
                        true
                    }

                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                        when (focusArea) {
                            FOCUS_MIC -> { /* TODO: voice search */ }
                            FOCUS_KEYBOARD -> {
                                if (useSystemKeyboard || isMode2) {
                                    // System keyboard modes (1 & 2): toggle editing on input
                                    isInputEditing = !isInputEditing
                                    return@onPreviewKeyEvent true
                                }
                                // Mode 0 only: custom keyboard
                                val currentKey = kbRows[kbRow][kbCol]
                                if (isLongPress && !longPressHandled) {
                                    // Long press on letter → Polish diacritic
                                    val polishVariant = POLISH_VARIANTS[currentKey]
                                    if (polishVariant != null) {
                                        // Replace last char with Polish variant
                                        if (searchQuery.isNotEmpty() && searchQuery.last().toString() == currentKey) {
                                            searchQuery = searchQuery.dropLast(1) + polishVariant
                                        } else {
                                            searchQuery += polishVariant
                                        }
                                        suggestionIndex = 0
                                        longPressHandled = true
                                    } else if (currentKey == KEY_BACKSPACE) {
                                        // Long press backspace → clear all
                                        searchQuery = ""
                                        suggestionIndex = 0
                                        longPressHandled = true
                                    }
                                } else if (!isLongPress) {
                                    longPressKey = currentKey
                                    longPressHandled = false
                                    onKeyPressed(currentKey)
                                }
                            }
                            FOCUS_SUGGESTIONS -> {
                                // Apply suggestion/chip to search query
                                val chipItems = if (isMode2 && searchQuery.length < 2) POPULAR_SEARCHES
                                    else suggestions
                                if (chipItems.isNotEmpty()) {
                                    searchQuery = chipItems[suggestionIndex.coerceIn(0, chipItems.size - 1)]
                                    focusArea = FOCUS_KEYBOARD
                                    suggestionIndex = 0
                                }
                            }
                            FOCUS_CHANNELS -> {
                                if (channels.isNotEmpty()) {
                                    val item = channels[chRow].items.getOrNull(chCol)
                                    if (item?.supabaseMovie != null) {
                                        onNavigateToMovieDetail(item.supabaseMovie.toVodSlideData())
                                    }
                                }
                            }
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        if (isMode2) {
            // ==================== MODE 2: Left panel + Right panel ====================
            Mode2Layout(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it; suggestionIndex = 0 },
                isInputEditing = isInputEditing,
                focusArea = focusArea,
                suggestionIndex = suggestionIndex,
                suggestions = suggestions,
                channels = channels,
                channelRow = chRow,
                channelCol = chCol,
                channelsVerticalOffset = channelsVerticalOffset,
                lazyListStates = lazyListStates,
                panelOffset = mode2PanelOffset,
                panelWidth = mode2PanelWidth,
                contentStart = mode2ContentStart,
                channelPadding = mode2ChannelPadding,
                sx = sx,
                sy = sy
            )
        } else {
        // ==================== MODES 0 & 1: Full width input + keyboard/channels ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = sy(180))
                .padding(horizontal = sx(62))
                .zIndex(10f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(16))
        ) {
            // Mic button
            val micFocused = focusArea == FOCUS_MIC
            Box(
                modifier = Modifier
                    .size(sx(56))
                    .then(
                        if (micFocused) Modifier
                            .background(COLOR_FOCUS_BORDER, CircleShape)
                        else Modifier
                            .background(Color(0x66000000), CircleShape)
                            .border(1.dp, Color(0x66EEEEEE), CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Mikrofon",
                    tint = if (micFocused) COLOR_BG else COLOR_TEXT_PRIMARY,
                    modifier = Modifier.size(sx(32))
                )
            }

            if (useSystemKeyboard) {
                // System keyboard mode: real TextField, OK activates editing with system keyboard
                val inputFocusRequester = remember { FocusRequester() }
                val inputFocused = focusArea == FOCUS_KEYBOARD
                val borderColor = if (isInputEditing) Color.White
                    else if (inputFocused) COLOR_FOCUS_BORDER
                    else Color.Transparent
                val borderWidth = if (inputFocused || isInputEditing) 1.5.dp else 0.dp

                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; suggestionIndex = 0 },
                    singleLine = true,
                    readOnly = !isInputEditing,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = (28 * sx(1).value).sp,
                        fontFamily = ManropeFamily,
                        fontWeight = FontWeight.Medium,
                        color = COLOR_TEXT_PRIMARY
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(COLOR_INPUT_BG, RoundedCornerShape(sx(8)))
                                .padding(horizontal = sx(16), vertical = sy(8)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Szukaj filmów, seriali, programów...",
                                    color = COLOR_TEXT_TERTIARY,
                                    fontFamily = ManropeFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = (28 * sx(1).value).sp
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(sy(72))
                        .border(borderWidth, borderColor, RoundedCornerShape(sx(8)))
                        .focusRequester(inputFocusRequester)
                )
                // When entering editing mode, focus the TextField so IME opens
                LaunchedEffect(isInputEditing) {
                    if (isInputEditing) {
                        inputFocusRequester.requestFocus()
                    }
                }
            } else {
                // ABC keyboard mode: display-only text box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(sy(72))
                        .background(COLOR_INPUT_BG, RoundedCornerShape(sx(8)))
                        .padding(horizontal = sx(16)),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = COLOR_TEXT_TERTIARY,
                                modifier = Modifier.size(sx(28))
                            )
                            Spacer(modifier = Modifier.width(sx(8)))
                            Text(
                                text = "Szukaj filmów, seriali, programów...",
                                color = COLOR_TEXT_TERTIARY,
                                fontSize = (28 * sx(1).value).sp,
                                fontFamily = ManropeFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = searchQuery,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = (28 * sx(1).value).sp,
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ==================== CONTENT ROW (clipped 20px below input) ====================
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sy(180) + sy(72) + sy(20))
                .clipToBounds()
        ) {

                // === KEYBOARD PANEL (slides left when channels focused) — hidden in system keyboard mode ===
                if (!useSystemKeyboard) Column(
                    modifier = Modifier
                        .width(sx(440))
                        .offset(x = keyboardOffset + sx(62))
                ) {
                    // Keyboard grid
                    Column(verticalArrangement = Arrangement.spacedBy(sy(4))) {
                        kbRows.forEachIndexed { rowIdx, rowKeys ->
                            Row(horizontalArrangement = Arrangement.spacedBy(sx(4))) {
                                rowKeys.forEachIndexed { colIdx, key ->
                                    val isFocused = focusArea == FOCUS_KEYBOARD && rowIdx == kbRow && colIdx == kbCol
                                    // ABC and backspace = letter width, SPACE = fill remaining
                                    val letterW = sx(64)
                                    val gapW = sx(4)
                                    val keyWidth = when (key) {
                                        "SPACE" -> letterW * 4 + gapW * 3 // ~4 letter keys wide
                                        "ABC" -> letterW
                                        KEY_BACKSPACE -> letterW
                                        else -> letterW
                                    }

                                    // Focused: aqua bg + dark text. Normal: gray bg + white text
                                    val keyBg = if (isFocused) COLOR_FOCUS_BORDER else COLOR_KEY_BG
                                    val keyText = if (isFocused) COLOR_BG else COLOR_TEXT_PRIMARY
                                    val keyShape = RoundedCornerShape(sx(8))

                                    Box(
                                        modifier = Modifier
                                            .width(keyWidth)
                                            .height(sy(64))
                                            .background(keyBg, keyShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (key) {
                                                "SPACE" -> "SPACE"
                                                KEY_BACKSPACE -> "\u232B"
                                                "ABC" -> if (isNumberMode) "abc" else "123"
                                                else -> key
                                            },
                                            color = keyText,
                                            fontSize = (if (key == "SPACE") 18 else 24).let { (it * sx(1).value).sp },
                                            fontFamily = ManropeFamily,
                                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // === CHANNELS PANEL (slides left when focused, scrolls vertically) ===
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .offset(x = contentOffset, y = channelsVerticalOffset),
                    verticalArrangement = Arrangement.spacedBy(sy(20))
                ) {
                    // === SUGGESTION CHIPS (horizontal row, like Apple TV) ===
                    if (suggestions.isNotEmpty() && searchQuery.length >= 2) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(sx(12)),
                            contentPadding = PaddingValues(start = sx(80), end = sx(40)),
                            modifier = Modifier.padding(bottom = sy(8))
                        ) {
                            itemsIndexed(suggestions) { idx, title ->
                                val isFocused = focusArea == FOCUS_SUGGESTIONS && idx == suggestionIndex
                                Row(
                                    modifier = Modifier
                                        .then(
                                            if (isFocused) Modifier.background(COLOR_FOCUS_BORDER, RoundedCornerShape(sx(24)))
                                            else Modifier.background(Color(0x44EEEEEE), RoundedCornerShape(sx(24)))
                                        )
                                        .padding(horizontal = sx(20), vertical = sy(10)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(sx(8))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = if (isFocused) COLOR_BG else COLOR_TEXT_TERTIARY,
                                        modifier = Modifier.size(sx(22))
                                    )
                                    // Highlight typed query in white, rest at 50% opacity
                                    val matchIdx = title.lowercase().indexOf(searchQuery.lowercase())
                                    val baseColor = if (isFocused) COLOR_BG else COLOR_TEXT_PRIMARY
                                    val dimColor = if (isFocused) COLOR_BG.copy(alpha = 0.5f) else COLOR_TEXT_PRIMARY.copy(alpha = 0.5f)
                                    Text(
                                        text = if (matchIdx >= 0) {
                                            buildAnnotatedString {
                                                // Before match — dim
                                                if (matchIdx > 0) {
                                                    withStyle(SpanStyle(color = dimColor)) {
                                                        append(title.substring(0, matchIdx))
                                                    }
                                                }
                                                // Matched part — full color, bold
                                                withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Bold)) {
                                                    append(title.substring(matchIdx, matchIdx + searchQuery.length))
                                                }
                                                // After match — dim
                                                val afterIdx = matchIdx + searchQuery.length
                                                if (afterIdx < title.length) {
                                                    withStyle(SpanStyle(color = dimColor)) {
                                                        append(title.substring(afterIdx))
                                                    }
                                                }
                                            }
                                        } else {
                                            buildAnnotatedString {
                                                withStyle(SpanStyle(color = baseColor)) { append(title) }
                                            }
                                        },
                                        fontSize = (22 * sx(1).value).sp,
                                        fontFamily = ManropeFamily,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    channels.forEachIndexed { rowIdx, channel ->
                        val isCurrentRow = focusArea == FOCUS_CHANNELS && rowIdx == chRow
                        val listState = lazyListStates.getOrElse(rowIdx) { rememberLazyListState() }

                        // Wrap each channel in its own Column so spacedBy only applies between channels
                        Column {
                        // Channel title
                        Text(
                            text = channel.title,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = (24 * sx(1).value).sp,
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.48.sp,
                            modifier = Modifier.padding(start = sx(80))
                        )

                        Spacer(modifier = Modifier.height(sy(8)))

                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(sx(20)),
                            contentPadding = PaddingValues(start = sx(80), end = sx(40))
                        ) {
                            itemsIndexed(channel.items) { colIdx, item ->
                                val isFocused = isCurrentRow && colIdx == chCol

                                if (channel.type == CHANNEL_TYPE_EPG) {
                                    // === EPG: Horizontal landscape card ===
                                    val now = remember { java.time.Instant.now() }
                                    val isAiringNow = item.startTime != null && item.endTime != null &&
                                        !now.isBefore(item.startTime) && now.isBefore(item.endTime)
                                    val isPast = item.endTime != null && item.endTime.isBefore(now) // catch-up
                                    val isFuture = item.startTime != null && item.startTime.isAfter(now)
                                    Column(modifier = Modifier.width(sx(380))) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(sy(214))
                                                .clip(RoundedCornerShape(sx(12)))
                                                .background(Color(0x33EEEEEE))
                                                .then(
                                                    if (isFocused) Modifier.border(3.dp, COLOR_FOCUS_BORDER, RoundedCornerShape(sx(12)))
                                                    else Modifier
                                                )
                                        ) {
                                            if (!item.posterUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.posterUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            // Bottom gradient
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(sy(80))
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color(0xCC000000))
                                                        )
                                                    )
                                            )
                                            // Channel logo (bottom-left)
                                            if (!item.channelLogoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.channelLogoUrl,
                                                    contentDescription = item.channelName,
                                                    modifier = Modifier
                                                        .size(sx(40))
                                                        .align(Alignment.BottomStart)
                                                        .offset(x = sx(10), y = -sy(10))
                                                        .clip(CircleShape)
                                                )
                                            }
                                            // Label: "Oglądaj teraz" or time badge (top-right)
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(sx(8))
                                                    .background(
                                                        if (isAiringNow || isPast) Color(0xCC00C853) else Color(0xCC000000),
                                                        RoundedCornerShape(sx(6))
                                                    )
                                                    .padding(horizontal = sx(10), vertical = sy(4))
                                            ) {
                                                Text(
                                                    text = if (isAiringNow || isPast) "Oglądaj teraz"
                                                        else if (isFuture && item.startTime != null) {
                                                            val zoned = item.startTime.atZone(java.time.ZoneId.systemDefault())
                                                            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")
                                                            zoned.format(formatter)
                                                        } else "Oglądaj teraz",
                                                    color = Color.White,
                                                    fontSize = (13 * sx(1).value).sp,
                                                    fontFamily = ManropeFamily,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(sy(6)))
                                        HighlightedTitle(title = item.title, query = searchQuery, sx = sx, fontSize = 18, maxWidth = sx(380))
                                        // Channel + time info
                                        val infoText = buildString {
                                            item.channelName?.let { append(it) }
                                            if (isFuture && item.startTime != null) {
                                                val zoned = item.startTime.atZone(java.time.ZoneId.systemDefault())
                                                if (isNotEmpty()) append(" · ")
                                                append(java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(zoned))
                                            }
                                        }
                                        if (infoText.isNotBlank()) {
                                            Text(
                                                text = infoText,
                                                color = COLOR_TEXT_TERTIARY,
                                                fontSize = (14 * sx(1).value).sp,
                                                fontFamily = ManropeFamily,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                } else if (channel.type == CHANNEL_TYPE_WIDEO) {
                                    // === WIDEO: Horizontal landscape card (like EPG but no time badge) ===
                                    Column(modifier = Modifier.width(sx(380))) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(sy(214))
                                                .clip(RoundedCornerShape(sx(12)))
                                                .background(Color(0x33EEEEEE))
                                                .then(
                                                    if (isFocused) Modifier.border(3.dp, COLOR_FOCUS_BORDER, RoundedCornerShape(sx(12)))
                                                    else Modifier
                                                )
                                        ) {
                                            if (!item.posterUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.posterUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            // Bottom gradient
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(sy(80))
                                                    .align(Alignment.BottomCenter)
                                                    .background(
                                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color(0xCC000000))
                                                        )
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(sy(6)))
                                        HighlightedTitle(title = item.title, query = searchQuery, sx = sx, fontSize = 18, maxWidth = sx(380))
                                    }
                                } else {
                                    // === KINO PLAY: Vertical poster card (2:3 ratio) ===
                                    val posterW = sx(220)
                                    val posterH = sy(310) // 2:3 visual ratio with sy for proper height
                                    Column(modifier = Modifier.width(posterW)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(posterH)
                                                .clip(RoundedCornerShape(sx(8)))
                                                .background(Color(0x33EEEEEE))
                                                .then(
                                                    if (isFocused) Modifier.border(3.dp, COLOR_FOCUS_BORDER, RoundedCornerShape(sx(8)))
                                                    else Modifier
                                                )
                                        ) {
                                            if (!item.posterUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.posterUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(sy(6)))
                                        HighlightedTitle(title = item.title, query = searchQuery, sx = sx, fontSize = 16, maxWidth = posterW)
                                    }
                                }
                        }
                    }
                    } // end channel Column wrapper
                }
            }
        }

        } // end else (modes 0 & 1)
    }
}

@Composable
private fun Mode2Layout(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isInputEditing: Boolean,
    focusArea: String,
    suggestionIndex: Int,
    suggestions: List<String>,
    channels: List<SearchChannel>,
    channelRow: Int,
    channelCol: Int,
    channelsVerticalOffset: Dp,
    lazyListStates: List<androidx.compose.foundation.lazy.LazyListState>,
    panelOffset: Dp,
    panelWidth: Dp,
    contentStart: Dp,
    channelPadding: Dp,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = sy(150))
    ) {
        // === LEFT PANEL (slides out when channels focused) ===
        Column(
            modifier = Modifier
                .width(panelWidth)
                .fillMaxHeight()
                .offset(x = panelOffset)
                .padding(start = sx(40), top = sy(40), end = sx(20))
        ) {
            // System keyboard input (BasicTextField aligned to left panel)
            val inputFocusRequester = remember { FocusRequester() }
            val inputFocused = focusArea == FOCUS_KEYBOARD
            val borderColor = if (isInputEditing) Color.White
                else if (inputFocused) COLOR_FOCUS_BORDER
                else Color(0x66EEEEEE)

            // AndroidView EditText with privateImeOptions for Gboard left alignment
            val editTextRef = remember { mutableStateOf<android.widget.EditText?>(null) }
            val density = androidx.compose.ui.platform.LocalDensity.current
            val inputHeightPx = with(density) { sy(60).roundToPx() }
            val fontSizePx = with(density) { (24 * sx(1).value).sp.toPx() }
            val hPadPx = with(density) { sx(16).roundToPx() }
            val borderWidthPx = with(density) { 1.5.dp.roundToPx() }

            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.EditText(ctx).apply {
                        privateImeOptions = "horizontalAlignment=left"
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        isSingleLine = true
                        setTextColor(0xFFEEEEEE.toInt())
                        setHintTextColor(0x99EEEEEE.toInt())
                        hint = "Szukaj filmów, seriali, programów..."
                        setBackgroundColor(0x33000000)
                        setPadding(hPadPx, 0, hPadPx, 0)
                        textSize = fontSizePx / ctx.resources.displayMetrics.scaledDensity
                        try {
                            val tf = android.graphics.Typeface.createFromAsset(ctx.assets, "font/manrope_medium.ttf")
                            typeface = tf
                        } catch (_: Exception) {}
                        isFocusable = true
                        isFocusableInTouchMode = true

                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                onSearchQueryChange(s?.toString() ?: "")
                            }
                        })
                        editTextRef.value = this
                    }
                },
                update = { editText ->
                    if (editText.text.toString() != searchQuery) {
                        editText.setText(searchQuery)
                        editText.setSelection(searchQuery.length)
                    }
                    // Update border based on focus state
                    val gd = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x33000000)
                        cornerRadius = with(density) { sx(8).toPx() }
                        setStroke(borderWidthPx, borderColor.toArgb())
                    }
                    editText.background = gd
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(60))
            )

            // Show/hide keyboard when editing state changes
            LaunchedEffect(isInputEditing) {
                val et = editTextRef.value ?: return@LaunchedEffect
                if (isInputEditing) {
                    et.requestFocus()
                    val imm = et.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } else {
                    val imm = et.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(et.windowToken, 0)
                }
            }

            Spacer(modifier = Modifier.height(sy(16)))

            // Chips: popular (when no query) or autocomplete suggestions
            val chipItems = if (searchQuery.length >= 2 && suggestions.isNotEmpty()) suggestions
                else POPULAR_SEARCHES
            val chipCols = 3

            chipItems.chunked(chipCols).forEachIndexed { rowIdx, rowChips ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(10)),
                    modifier = Modifier.padding(bottom = sy(10))
                ) {
                    rowChips.forEachIndexed { colIdx, chip ->
                        val globalIdx = rowIdx * chipCols + colIdx
                        val isFocused = focusArea == FOCUS_SUGGESTIONS && globalIdx == suggestionIndex
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isFocused) COLOR_FOCUS_BORDER else Color(0x44EEEEEE),
                                    RoundedCornerShape(sx(20))
                                )
                                .padding(horizontal = sx(16), vertical = sy(8))
                        ) {
                            Text(
                                text = chip,
                                color = if (isFocused) COLOR_BG else COLOR_TEXT_PRIMARY,
                                fontSize = (18 * sx(1).value).sp,
                                fontFamily = ManropeFamily,
                                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

        }

        // === RIGHT PANEL (channels — slides to full width when panel hidden) ===
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = contentStart)
                .clipToBounds()
        ) {
            Column(
                modifier = Modifier
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .offset(y = channelsVerticalOffset)
                    .padding(top = sy(40)),
                verticalArrangement = Arrangement.spacedBy(sy(20))
            ) {
                // Search header
                if (searchQuery.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = channelPadding, bottom = sy(8))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = COLOR_TEXT_PRIMARY,
                            modifier = Modifier.size(sx(32))
                        )
                        Spacer(modifier = Modifier.width(sx(12)))
                        Text(
                            text = searchQuery,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = (28 * sx(1).value).sp,
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Channels
                channels.forEachIndexed { rowIdx, channel ->
                    val isCurrentRow = focusArea == FOCUS_CHANNELS && rowIdx == channelRow
                    val listState = lazyListStates.getOrElse(rowIdx) { androidx.compose.foundation.lazy.rememberLazyListState() }

                    Column {
                        Text(
                            text = channel.title,
                            color = COLOR_TEXT_PRIMARY,
                            fontSize = (22 * sx(1).value).sp,
                            fontFamily = ManropeFamily,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.44.sp,
                            modifier = Modifier.padding(start = channelPadding)
                        )

                        Spacer(modifier = Modifier.height(sy(8)))

                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            contentPadding = PaddingValues(start = channelPadding, end = sx(20))
                        ) {
                            itemsIndexed(channel.items) { colIdx, item ->
                                val isFocused = isCurrentRow && colIdx == channelCol
                                val posterW = sx(220)
                                val posterH = sy(310)

                                Column(modifier = Modifier.width(posterW)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(posterH)
                                            .clip(RoundedCornerShape(sx(8)))
                                            .background(Color(0x33EEEEEE))
                                            .then(
                                                if (isFocused) Modifier.border(3.dp, COLOR_FOCUS_BORDER, RoundedCornerShape(sx(8)))
                                                else Modifier
                                            )
                                    ) {
                                        if (!item.posterUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = item.posterUrl,
                                                contentDescription = item.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(sy(4)))
                                    HighlightedTitle(title = item.title, query = searchQuery, sx = sx, fontSize = 16, maxWidth = posterW)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightedTitle(
    title: String,
    query: String,
    sx: (Int) -> Dp,
    fontSize: Int = 16,
    maxWidth: Dp = Dp.Unspecified
) {
    val matchIdx = if (query.length >= 2) title.lowercase().indexOf(query.lowercase()) else -1
    val text = if (matchIdx >= 0) {
        val fullColor = COLOR_TEXT_PRIMARY
        val dimColor = COLOR_TEXT_PRIMARY.copy(alpha = 0.5f)
        buildAnnotatedString {
            if (matchIdx > 0) {
                withStyle(SpanStyle(color = dimColor)) { append(title.substring(0, matchIdx)) }
            }
            withStyle(SpanStyle(color = fullColor, fontWeight = FontWeight.Bold)) {
                append(title.substring(matchIdx, matchIdx + query.length))
            }
            val afterIdx = matchIdx + query.length
            if (afterIdx < title.length) {
                withStyle(SpanStyle(color = dimColor)) { append(title.substring(afterIdx)) }
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = COLOR_TEXT_PRIMARY)) { append(title) }
        }
    }
    Text(
        text = text,
        fontSize = (fontSize * sx(1).value).sp,
        fontFamily = ManropeFamily,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier
    )
}

private fun SupabaseMovie.toVodSlideData(): com.uxellence.tv.v3.VodSlideData = com.uxellence.tv.v3.VodSlideData(
    title = title,
    genre = genre ?: "",
    duration = runtime ?: "",
    year = release_year?.let { "$it r." } ?: "",
    country = country ?: "",
    ageRating = age_rating ?: "",
    description = short_description ?: description ?: "",
    price = price?.let { "$it zł/48h" } ?: "",
    backgroundUrl = backdrop_url ?: "",
    posterUrl = poster_url ?: "",
    youtubeUrl = youtube_url,
    selectedLogoUrl = selected_logo_url,
    isKinoPlay = true,
    filmwebRating = filmweb_rating,
    audioLanguages = audio_languages,
    subtitleLanguages = subtitle_languages,
    director = director,
    cast = cast
)
