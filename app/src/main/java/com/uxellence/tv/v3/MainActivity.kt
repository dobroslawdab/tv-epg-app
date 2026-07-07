package com.uxellence.tv.v3

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import java.util.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.epg.*
import com.uxellence.tv.v3.repository.EpgRepository
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.version001.VodContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.config.ConfigManager
import com.uxellence.tv.v3.moviedetail.MovieDetailScreen
import com.uxellence.tv.v3.moviedetail.PurchaseScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force Polish locale for the entire application
        setAppLocale(this, "pl")
        // Initialize VOD data cache once at startup (avoids repeated I/O)
        VodDataCache.initialize(this)
        // Load persisted rentals (so isRented + countdown are correct from first frame)
        com.uxellence.tv.v3.rental.RentalManager.init(this)
        com.uxellence.tv.v3.watchlist.WatchlistManager.init(this)
        // Zlecone nagrania z makiety demo live (flow "Nagrywanie serii") —
        // init tutaj, żeby MOJE → Nagrania widziało je bez otwierania demo
        com.uxellence.tv.v3.demolive.DemoRecordingScheduler.init(this)
        // Initialize ChannelManager with TV channels database
        ChannelManager.initialize(this)
        // Initialize ConfigManager - load cached config from Supabase
        ConfigManager.initialize(this)
        // Auto-refresh config from Supabase on app startup
        lifecycleScope.launch {
            ConfigManager.refreshConfig(this@MainActivity)
        }
        // Pre-warm the APLIKACJE hero banner cache so the slider renders
        // instantly on first entry instead of waiting for the Supabase round-trip.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.uxellence.tv.v3.aplikacje.AplikacjeBannerCache.ensureLoaded()
        }
        // Same pre-warm for the ODKRYWAJ slider items.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.uxellence.tv.v3.repository.OdkrywajSliderCache.ensureLoaded()
        }
        // Pre-warm KINO_PLAY Supabase data (slider movies + full catalog) so the
        // first entry to KINO_PLAY / ODKRYWAJ / MOJE skips the network round-trip
        // that's normally triggered lazily by those screens' LaunchedEffects.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            VodDataCache.initializeFromSupabase(this@MainActivity)
        }
        setContent { TvRoot() }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(setAppLocale(newBase, "pl"))
    }

    private fun setAppLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode, "PL")
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}

enum class NavigationScreen {
    HOME, LIVE, COMPONENT_SHOWCASE, TOP_MENU2, SHORTCUT, CHANNELE, VIDEOSLIDER, SLIDER, SLIDER_MIX, EPG, EPG_DAY, FOCUS_MINI_CARD, VOICE_TEST, SPLASH, WHATS_NEW, STARTUP_MODE_SELECTION, LAUNCHER_SETUP, ZAPPING_BAR, CHANNEL_GRID, WIDEO_GRID, KINO_GRID, VOD_GRID, RECORDINGS_GRID, APPS_GRID, SERIES_EPISODES, MOVIE_DETAIL, PURCHASE, RENTAL_PROCESSING, OLYMPICS, VOD_PLAYER, DEMO_LIVE, DEMO_VOD
}

// Helper functions for launcher setup

/**
 * Check if the app is set as the default launcher (HOME app)
 */
fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_HOME)
    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
    return resolveInfo?.activityInfo?.packageName == context.packageName
}

/**
 * Open Android TV launcher settings
 * Tries to open the role settings, falls back to home settings if unavailable
 */
fun openLauncherSettings(context: Context) {
    try {
        // Try to open role settings (Android 10+)
        val roleIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        roleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(roleIntent)
    } catch (e: Exception) {
        try {
            // Fallback to home settings
            val homeIntent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(homeIntent)
        } catch (e2: Exception) {
            // Last resort: main settings
            val settingsIntent = Intent(android.provider.Settings.ACTION_SETTINGS)
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
        }
    }
}

// Kolory z Figma dla nowego menu
object MenuColors {
    val FocusedBackground = Color(0xFFFFFFFF)
    val FocusedText = Color(0xFF5A3888)
    val UnfocusedBackground = Color.Transparent
    val UnfocusedText = Color(0xFFFFFFFF)
    val BorderColor = Color(0xFFFFFFFF)
}

// Model danych dla elementu menu głównego
data class MainMenuItem(
    val id: String,
    val title: String,
    val navigationScreen: NavigationScreen
)

@Composable
fun MenuGridItem(
    item: MainMenuItem,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onItemSelected: (NavigationScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused) MenuColors.FocusedBackground else MenuColors.UnfocusedBackground
    val textColor = if (isFocused) MenuColors.FocusedText else MenuColors.UnfocusedText
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = MenuColors.BorderColor,
                shape = RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 5.dp, vertical = 12.dp)
            .focusable()
            .onFocusChanged { focusState ->
                onFocusChanged(focusState.isFocused)
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && 
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter)) {
                    onItemSelected(item.navigationScreen)
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = item.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.24).sp // -2% z 12sp
        )
    }
}

@Composable
fun TVMenuGrid(
    menuItems: List<MainMenuItem>,
    onItemSelected: (NavigationScreen) -> Unit,
    columns: Int = 3,
    itemsPerColumn: Int = 10,
    initialFocusPosition: Pair<Int, Int> = Pair(0, 0), // kolumna, rząd
    modifier: Modifier = Modifier
) {
    var focusedPosition by remember { mutableStateOf(initialFocusPosition) }
    val focusRequesters = remember {
        List(columns) { col ->
            List(itemsPerColumn) { row ->
                FocusRequester()
            }
        }
    }
    
    // Automatyczne fokusowanie pierwszego elementu
    LaunchedEffect(Unit) {
        delay(100) // Krótkie opóźnienie dla stabilności
        if (menuItems.isNotEmpty()) {
            focusRequesters[initialFocusPosition.first][initialFocusPosition.second].requestFocus()
        }
    }
    
    Row(
        modifier = modifier
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    handleKeyNavigation(
                        keyEvent = keyEvent,
                        currentPosition = focusedPosition,
                        columns = columns,
                        itemsPerColumn = itemsPerColumn,
                        menuItems = menuItems,
                        focusRequesters = focusRequesters,
                        onPositionChanged = { newPosition ->
                            focusedPosition = newPosition
                        }
                    )
                } else false
            },
        horizontalArrangement = Arrangement.spacedBy(14.5.dp) // 50% z 29dp
    ) {
        repeat(columns) { columnIndex ->
            Column(
                modifier = Modifier.width(187.5.dp), // 50% z 375dp
                verticalArrangement = Arrangement.spacedBy((-1).dp) // -1px margin między buttonami
            ) {
                repeat(itemsPerColumn) { rowIndex ->
                    val itemIndex = columnIndex * itemsPerColumn + rowIndex
                    if (itemIndex < menuItems.size) {
                        val item = menuItems[itemIndex]
                        val isFocused = focusedPosition.first == columnIndex && focusedPosition.second == rowIndex
                        
                        MenuGridItem(
                            item = item,
                            isFocused = isFocused,
                            onFocusChanged = { focused ->
                                if (focused) {
                                    focusedPosition = Pair(columnIndex, rowIndex)
                                }
                            },
                            onItemSelected = onItemSelected,
                            modifier = Modifier
                                .focusRequester(focusRequesters[columnIndex][rowIndex])
                        )
                    }
                }
            }
        }
    }
}

// Funkcja obsługująca nawigację klawiaturą/pilotem
private fun handleKeyNavigation(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    currentPosition: Pair<Int, Int>,
    columns: Int,
    itemsPerColumn: Int,
    menuItems: List<MainMenuItem>,
    focusRequesters: List<List<FocusRequester>>,
    onPositionChanged: (Pair<Int, Int>) -> Unit
): Boolean {
    val (currentCol, currentRow) = currentPosition
    
    return when (keyEvent.key) {
        Key.DirectionLeft -> {
            if (currentCol > 0) {
                val newPosition = Pair(currentCol - 1, currentRow)
                val itemIndex = newPosition.first * itemsPerColumn + newPosition.second
                if (itemIndex < menuItems.size) {
                    focusRequesters[newPosition.first][newPosition.second].requestFocus()
                    onPositionChanged(newPosition)
                }
                true
            } else false
        }
        Key.DirectionRight -> {
            if (currentCol < columns - 1) {
                val newPosition = Pair(currentCol + 1, currentRow)
                val itemIndex = newPosition.first * itemsPerColumn + newPosition.second
                if (itemIndex < menuItems.size) {
                    focusRequesters[newPosition.first][newPosition.second].requestFocus()
                    onPositionChanged(newPosition)
                }
                true
            } else false
        }
        Key.DirectionUp -> {
            if (currentRow > 0) {
                val newPosition = Pair(currentCol, currentRow - 1)
                val itemIndex = newPosition.first * itemsPerColumn + newPosition.second
                if (itemIndex < menuItems.size) {
                    focusRequesters[newPosition.first][newPosition.second].requestFocus()
                    onPositionChanged(newPosition)
                }
                true
            } else false
        }
        Key.DirectionDown -> {
            if (currentRow < itemsPerColumn - 1) {
                val newPosition = Pair(currentCol, currentRow + 1)
                val itemIndex = newPosition.first * itemsPerColumn + newPosition.second
                if (itemIndex < menuItems.size) {
                    focusRequesters[newPosition.first][newPosition.second].requestFocus()
                    onPositionChanged(newPosition)
                }
                true
            } else false
        }
        else -> false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvRoot(
    startScreen: NavigationScreen = NavigationScreen.SPLASH,
    homePressedTrigger: Int = 0  // Trigger for HOME button navigation
) {
    val context = LocalContext.current
    val repository = remember { EpgRepository.getInstance(context) }
    var currentScreen by remember { mutableStateOf(startScreen) }
    var previousScreen by remember { mutableStateOf(NavigationScreen.HOME) }
    var selectedChannelName by remember { mutableStateOf<String?>(null) }

    // ChannelGridScreen navigation parameters
    var channelGridTitle by remember { mutableStateOf("Lista kanałów TV") }
    var channelGridCategory by remember { mutableStateOf("Wszystkie") }
    var channelGridFilter by remember { mutableStateOf<((TvChannel) -> Boolean)?>(null) }
    var channelGridChannelList by remember { mutableStateOf<List<TvChannel>?>(null) }

    // Track where ChannelGridScreen was launched from (for BACK navigation)
    var channelGridSourceScreen by remember { mutableStateOf<NavigationScreen?>(null) }
    var channelGridSourceSection by remember { mutableStateOf<String?>(null) }

    // VodGridScreen navigation parameters
    var vodGridTitle by remember { mutableStateOf("Lista Wideo") }
    var vodGridPrefiltered by remember { mutableStateOf<List<VodContent>?>(null) }  // Prefiltered VOD data
    var vodGridSourceSection by remember { mutableStateOf<String?>(null) }  // "TELEWIZJA", "START", etc.

    // KinoGridScreen navigation parameters
    var kinoGridTitle by remember { mutableStateOf("Lista Kino") }
    var kinoGridPrefiltered by remember { mutableStateOf<List<VodContent>?>(null) }  // Prefiltered KINO data
    var kinoGridSourceSection by remember { mutableStateOf<String?>(null) }  // "KINO_PLAY"
    var kinoGridInitialCategory by remember { mutableStateOf<String?>(null) }  // Pre-selected category filter
    var kinoGridLastClickedMovieId by remember { mutableStateOf<String?>(null) }  // Restore focus after MovieDetail back
    var kinoGridLastSearchQuery by remember { mutableStateOf("") }  // Persist search overlay across MovieDetail
    var vodGridInitialCategory by remember { mutableStateOf<String?>(null) }
    var vodGridLastClickedMovieId by remember { mutableStateOf<String?>(null) }
    var vodGridLastSearchQuery by remember { mutableStateOf("") }

    // MovieDetail sibling navigation (WIDEO mode only). Populated when user enters detail
    // from VodGridScreen — left/right at the buttons row swaps the film to siblings[idx±1].
    var movieDetailSiblings by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var movieDetailCurrentIndex by remember { mutableStateOf(0) }

    // RecordingsGridScreen navigation parameters
    var recordingsGridSourceSection by remember { mutableStateOf<String?>(null) }  // "MOJE"

    // OlympicsEventScreen navigation parameters
    var olympicsContent by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var olympicsSourceSection by remember { mutableStateOf<String?>(null) }  // "ODKRYWAJ" or "TELEWIZJA"

    // SeriesEpisodesGridScreen navigation parameters
    var seriesEpisodesId by remember { mutableStateOf("") }
    var seriesEpisodesTitle by remember { mutableStateOf("") }

    // MovieDetailScreen navigation parameters
    var selectedMovieData by remember { mutableStateOf<VodSlideData?>(null) }
    var cameFromQuickPurchase by remember { mutableStateOf(false) }  // Track if we used quick purchase mode

    // VOD Player navigation parameters
    var vodPlayerUrl by remember { mutableStateOf("") }
    var vodPlayerTitle by remember { mutableStateOf("") }

    // Save TELEWIZJA focus state for smart BACK navigation (ID-based)
    var savedTelewizjaFocus by remember { mutableStateOf<FocusState?>(null) }

    // Save TOP_MENU2 section for smart BACK navigation: "TELEWIZJA", "MOJE", etc.
    var savedTelewizjaSection by remember { mutableStateOf<String?>(null) }

    // Set przez VodGridScreen/KinoGridScreen empty state ("Szukaj w całym serwisie")
    // → MainActivity przełącza na TOP_MENU2/SEARCH z preseed query. Resetowane do null
    // po jednorazowym przekazaniu, żeby kolejne wejścia w SEARCH nie były pre-fillowane.
    var pendingGlobalSearchQuery by remember { mutableStateOf<String?>(null) }

    // Po przekazaniu query do SearchScreen przy mount (mutableStateOf(initialQuery)),
    // zerujemy pending żeby kolejne mount SearchScreen (np. po przełączeniu sekcji)
    // nie nadpisał użytkownikowi obecnej frazy. 500 ms = SectionContent zdąży się
    // wyrenderować i przekazać initialQuery dalej.
    LaunchedEffect(pendingGlobalSearchQuery) {
        if (pendingGlobalSearchQuery != null) {
            kotlinx.coroutines.delay(500)
            pendingGlobalSearchQuery = null
        }
    }

    // PIP (Picture-in-Picture) state
    var pipPlayer by remember { mutableStateOf<com.google.android.exoplayer2.ExoPlayer?>(null) }
    var pipStreamUrl by remember { mutableStateOf<String?>(null) }
    var pipMode by remember { mutableStateOf(false) }

    // Track if EPG_DAY was launched from startup (show overlay) or from TOP_MENU2 (no overlay)
    var isEpgDayFromStartup by remember { mutableStateOf(false) }

    // State flag for HOME button PIP navigation request
    var shouldNavigateHomeWithPip by remember { mutableStateOf(false) }

    // Counter bumped on every HOME button press. TopMenuScreen2 watches this and
    // force-focuses the ODKRYWAJ (START) menu tab — even when the user is already
    // on ODKRYWAJ content. Without this signal, just setting `currentScreen =
    // TOP_MENU2` doesn't move focus from a channel back to the top menu.
    var homeFocusTrigger by remember { mutableIntStateOf(0) }

    // ====== AUTO-UPDATE SYSTEM ======
    val updateManager = remember { com.uxellence.tv.v3.update.UpdateManager(context) }
    val coroutineScope = rememberCoroutineScope()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.uxellence.tv.v3.update.AppUpdateInfo?>(null) }
    var updateState by remember { mutableStateOf(com.uxellence.tv.v3.update.UpdateState.READY) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    // Flips to true once the APK has actually flushed to disk. Used to keep
    // the "Zainstaluj teraz" button hidden behind a spinner for the brief
    // post-download window so users can't tap a button that would silently
    // no-op. Polled by a LaunchedEffect on INSTALLING state.
    var isApkReadyForInstall by remember { mutableStateOf(false) }

    LaunchedEffect(updateState) {
        if (updateState == com.uxellence.tv.v3.update.UpdateState.INSTALLING) {
            isApkReadyForInstall = updateManager.isApkReady()
            while (!isApkReadyForInstall) {
                kotlinx.coroutines.delay(150)
                isApkReadyForInstall = updateManager.isApkReady()
            }
        } else {
            isApkReadyForInstall = false
        }
    }

    // Start background EPG loading on app start
    LaunchedEffect(Unit) {
        repository.startBackgroundRefresh()
    }

    // Check for app updates on startup (show dialog immediately like before)
    LaunchedEffect(Unit) {
        updateManager.cleanupDownloadedApk()
        kotlinx.coroutines.delay(2000)
        val availableUpdate = updateManager.checkForUpdate()
        if (availableUpdate != null) {
            updateInfo = availableUpdate
            showUpdateDialog = true  // Show dialog on startup (as before)
            com.uxellence.tv.v3.utils.VersionTracker.setKontoUpdateBadge(context, true)  // Also set badge on Konto
            android.util.Log.d("UPDATE", "Update available: ${availableUpdate.versionName}")
        }
    }

    // Periodic update check while app is running (every 30 min)
    // When user is watching and new version appears → badge on Konto + notification
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30 * 60 * 1000L) // 30 minutes
            if (updateInfo == null) {
                // Only check if we don't already know about an update
                val availableUpdate = updateManager.checkForUpdate()
                if (availableUpdate != null) {
                    updateInfo = availableUpdate
                    com.uxellence.tv.v3.utils.VersionTracker.setKontoUpdateBadge(context, true)
                    android.util.Log.d("UPDATE", "Periodic check: update found ${availableUpdate.versionName}")
                }
            }
        }
    }

    // Cleanup update manager on dispose
    DisposableEffect(Unit) {
        onDispose {
            updateManager.close()
        }
    }

    // Handle HOME button navigation (from LauncherActivity)
    // When homePressedTrigger changes, navigate based on current state
    LaunchedEffect(homePressedTrigger) {
        if (homePressedTrigger > 0) {  // Ignore initial value (0)
            android.util.Log.d("HOME_NAVIGATION", "HOME pressed (trigger=$homePressedTrigger)")

            // HOME always returns to TopMenu2 / ODKRYWAJ and tears down any active
            // playback. No more PIP transfer from EPG_DAY (user requested this be
            // disabled — pressing HOME during fullscreen TV should NOT spawn a
            // picture-in-picture window).
            android.util.Log.d("HOME_NAVIGATION", "HOME → TopMenu2/ODKRYWAJ, tearing down PIP/player")

            currentScreen = NavigationScreen.TOP_MENU2
            savedTelewizjaSection = "ODKRYWAJ"
            // Bump the trigger so TopMenuScreen2 force-focuses the START menu tab
            // (otherwise just re-mounting at TOP_MENU2 leaves the user wherever
            // they were if they're already on ODKRYWAJ, or stuck in another tab's
            // content row if the section persists).
            homeFocusTrigger += 1

            // Make sure no PIP window survives the navigation.
            pipPlayer?.stop()
            pipPlayer?.release()
            pipPlayer = null
            pipStreamUrl = null
            pipMode = false
        }
    }

    // Clear saved focus and section when leaving TOP_MENU2
    LaunchedEffect(currentScreen) {
        if (currentScreen != NavigationScreen.TOP_MENU2 && currentScreen != NavigationScreen.EPG_DAY) {
            savedTelewizjaFocus = null
            savedTelewizjaSection = null
        }
    }

    // Menu items dla wszystkich ekranów - najnowsze na górze
    val menuItems = remember {
        listOf(
            MainMenuItem(id = "demo_live", title = "📡 Demo: Kanał live + ramówka", navigationScreen = NavigationScreen.DEMO_LIVE),
            MainMenuItem(id = "demo_vod", title = "🎞 Demo: VOD + miniaturki", navigationScreen = NavigationScreen.DEMO_VOD),
            MainMenuItem(id = "kino_grid", title = "🎬 Lista Kino", navigationScreen = NavigationScreen.KINO_GRID),
            MainMenuItem(id = "vod_grid", title = "📹 Lista Wideo", navigationScreen = NavigationScreen.WIDEO_GRID),
            MainMenuItem(id = "channel_grid", title = "📺 Lista kanałów TV", navigationScreen = NavigationScreen.CHANNEL_GRID),
            MainMenuItem(id = "startup_mode", title = "⚙️ Tryb startowy", navigationScreen = NavigationScreen.STARTUP_MODE_SELECTION),
            MainMenuItem(id = "whats_new", title = "What's New", navigationScreen = NavigationScreen.WHATS_NEW),
            MainMenuItem(id = "epg_day", title = "📺 EPG Day Test", navigationScreen = NavigationScreen.EPG_DAY),
            MainMenuItem(id = "zapping_bar", title = "🔀 Zapping Bar", navigationScreen = NavigationScreen.ZAPPING_BAR),
            MainMenuItem(id = "voice_test", title = "🎤 Wyszukiwanie głosowe test", navigationScreen = NavigationScreen.VOICE_TEST),
            MainMenuItem(id = "top_menu2", title = "Top Menu 2", navigationScreen = NavigationScreen.TOP_MENU2),
            MainMenuItem(id = "slider_mix", title = "Slider_mix", navigationScreen = NavigationScreen.SLIDER_MIX),
            MainMenuItem(id = "live", title = "Live TV", navigationScreen = NavigationScreen.LIVE),
            MainMenuItem(id = "focus_mini_card", title = "Focus Mini Cards", navigationScreen = NavigationScreen.FOCUS_MINI_CARD),
            MainMenuItem(id = "component_showcase", title = "Component Showcase", navigationScreen = NavigationScreen.COMPONENT_SHOWCASE),
            MainMenuItem(id = "shortcut", title = "Shortcuts", navigationScreen = NavigationScreen.SHORTCUT),
            MainMenuItem(id = "channele", title = "Channels", navigationScreen = NavigationScreen.CHANNELE),
            MainMenuItem(id = "videoslider", title = "Video Slider", navigationScreen = NavigationScreen.VIDEOSLIDER),
            MainMenuItem(id = "slider", title = "Slider duży", navigationScreen = NavigationScreen.SLIDER),
            MainMenuItem(id = "epg", title = "TV Guide (EPG)", navigationScreen = NavigationScreen.EPG)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
    ) {
        // OVERLAY: keep TopMenuScreen2 mounted while MovieDetail is showing on top of it
        // (only when MovieDetail was launched from KINO_PLAY tab — i.e. previousScreen
        // was TOP_MENU2). This preserves all VodWithChannels state — focusedRowIndex,
        // focusedColIndex, lazyListStates, channelFocusRequesters, focus binding — so
        // BACK from MovieDetail can refocus the exact same poster the user was on.
        // See VodWithChannels' LaunchedEffect on `kinoPlayRefocusTrigger`.
        // Overlay active for the WHOLE navigation chain that originated from TOP_MENU2
        // (KINO_PLAY tab): MOVIE_DETAIL, PURCHASE, MOVIE_DETAIL ↔ PURCHASE round-trips.
        // `previousScreen == TOP_MENU2` stays set throughout (it's only changed by
        // TopMenuScreen2's own callbacks, not by inner MovieDetail/Purchase transitions).
        // `cameFromQuickPurchase` covers the slider-direct-to-PURCHASE path.
        val showTopMenuAsBase = (currentScreen == NavigationScreen.MOVIE_DETAIL ||
            currentScreen == NavigationScreen.PURCHASE ||
            currentScreen == NavigationScreen.RENTAL_PROCESSING) &&
            (previousScreen == NavigationScreen.TOP_MENU2 || cameFromQuickPurchase)

        // movableContentOf lets us render the SAME TopMenuScreen2 instance from two
        // different positions in the `when` (TOP_MENU2 case OR MOVIE_DETAIL case as base
        // layer) without losing internal state on transitions.
        val topMenuMovable = remember {
            movableContentOf {
                // Clear saved focus after delay when returning from EPG Day Test
                LaunchedEffect(currentScreen, savedTelewizjaFocus) {
                    if (currentScreen == NavigationScreen.TOP_MENU2 && savedTelewizjaFocus != null) {
                        android.util.Log.d("TELEWIZJA_FOCUS", "Returned to TOP_MENU2 with saved focus, will clear after 5000ms")
                        kotlinx.coroutines.delay(5000)
                        savedTelewizjaFocus = null
                        android.util.Log.d("TELEWIZJA_FOCUS", "Cleared savedTelewizjaFocus after delay")
                    }
                }

                TopMenuScreen2(
                    onBackPressed = { isMenuFocused ->
                        false
                    },
                    onReturnToEpgDay = {
                        android.util.Log.d("PIP_NAVIGATION", "Returning to EPG Day Test from PIP")
                        currentScreen = NavigationScreen.EPG_DAY
                    },
                    onShowMainMenu = {
                        currentScreen = NavigationScreen.HOME
                    },
                    onNavigateToLiveScreen = { channelName ->
                        selectedChannelName = channelName
                        currentScreen = NavigationScreen.LIVE
                    },
                    onNavigateToEpg = {
                        previousScreen = currentScreen
                        currentScreen = NavigationScreen.EPG
                    },
                    onNavigateToEpgDay = { channelId, itemId, scrollPosition, sectionId ->
                        savedTelewizjaFocus = FocusState(
                            channelId = channelId,
                            itemId = itemId,
                            scrollPosition = scrollPosition
                        )
                        savedTelewizjaSection = sectionId
                        previousScreen = currentScreen
                        isEpgDayFromStartup = false
                        currentScreen = NavigationScreen.EPG_DAY
                    },
                    onNavigateToStartupMode = {
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.STARTUP_MODE_SELECTION
                    },
                    onFocusRestored = { },
                    restoredTelewizjaFocus = savedTelewizjaFocus,
                    restoredSection = savedTelewizjaSection,
                    initialSearchQuery = pendingGlobalSearchQuery ?: "",
                    homeFocusTrigger = homeFocusTrigger,
                    pipPlayer = pipPlayer,
                    onClosePip = {
                        pipPlayer?.stop()
                        pipPlayer?.release()
                        pipPlayer = null
                        pipStreamUrl = null
                        pipMode = false
                    },
                    onNavigateToChannelGrid = { title, category, filter, channelList ->
                        channelGridSourceScreen = NavigationScreen.TOP_MENU2
                        channelGridSourceSection = savedTelewizjaSection ?: "TELEWIZJA"
                        channelGridTitle = title
                        channelGridCategory = category
                        channelGridFilter = filter
                        channelGridChannelList = channelList
                        currentScreen = NavigationScreen.CHANNEL_GRID
                    },
                    onNavigateToVodGrid = { title, prefiltered, sourceSection ->
                        vodGridTitle = title
                        vodGridPrefiltered = prefiltered
                        vodGridSourceSection = sourceSection
                        // For WIDEO chip → grid path: title IS the chip label (e.g. "Viaplay Filmy"),
                        // and we want the picker to start on that category. For other paths (legacy
                        // shortcut/etc.) the title may not match a known chip — VodGridScreen will
                        // fall back to "Wszystkie" if the label is unknown.
                        vodGridInitialCategory = title
                        // Reset stale focus/search state on a fresh chip-driven entry so the user
                        // doesn't land on a poster from a previous (unrelated) category visit.
                        vodGridLastClickedMovieId = null
                        vodGridLastSearchQuery = ""
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.VOD_GRID
                    },
                    onNavigateToKinoGrid = { title, prefiltered, sourceSection ->
                        kinoGridTitle = title
                        kinoGridPrefiltered = prefiltered
                        kinoGridSourceSection = sourceSection
                        kinoGridInitialCategory = when {
                            title == "Nowości 🔥" -> title
                            VodDataCache.kinoChannelMap.containsKey(title) -> title
                            else -> prefiltered?.firstOrNull()?.category
                        }
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.KINO_GRID
                    },
                    onNavigateToRecordingsGrid = { _, sourceSection ->
                        recordingsGridSourceSection = sourceSection
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.RECORDINGS_GRID
                    },
                    onNavigateToAppsGrid = {
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.APPS_GRID
                    },
                    onNavigateToMovieDetail = { movieData ->
                        selectedMovieData = movieData
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.MOVIE_DETAIL
                    },
                    onNavigateToPurchase = { movieData ->
                        selectedMovieData = movieData
                        cameFromQuickPurchase = true
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.PURCHASE
                    },
                    onNavigateToVodPlayer = { url, title ->
                        vodPlayerUrl = url
                        vodPlayerTitle = title
                        currentScreen = NavigationScreen.VOD_PLAYER
                    },
                    onNavigateToOlympics = { content, sourceSection ->
                        olympicsContent = content
                        olympicsSourceSection = sourceSection
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.OLYMPICS
                    },
                    availableUpdate = updateInfo,
                    updateState = updateState,
                    onUpdateDownload = { info ->
                        updateState = com.uxellence.tv.v3.update.UpdateState.DOWNLOADING
                        coroutineScope.launch {
                            updateManager.downloadApk(
                                updateInfo = info,
                                onProgress = { progress -> downloadProgress = progress },
                                onComplete = { success ->
                                    if (success) {
                                        updateState = com.uxellence.tv.v3.update.UpdateState.INSTALLING
                                    } else {
                                        updateState = com.uxellence.tv.v3.update.UpdateState.READY
                                    }
                                }
                            )
                        }
                    },
                    onUpdateInstall = {
                        // Same retry-with-feedback path as the main update
                        // dialog — never let the user tap "Zainstaluj" and
                        // see nothing happen.
                        coroutineScope.launch {
                            var result = updateManager.installApk()
                            var waited = 0
                            while (result is com.uxellence.tv.v3.update.UpdateManager.InstallResult.NotReady && waited < 5000) {
                                kotlinx.coroutines.delay(250)
                                waited += 250
                                if (updateManager.isApkReady()) {
                                    result = updateManager.installApk()
                                }
                            }
                            when (result) {
                                com.uxellence.tv.v3.update.UpdateManager.InstallResult.Success -> {
                                    android.util.Log.d("UPDATE", "Installer launched (Konto badge)")
                                }
                                com.uxellence.tv.v3.update.UpdateManager.InstallResult.NotReady -> {
                                    updateState = com.uxellence.tv.v3.update.UpdateState.READY
                                    android.widget.Toast.makeText(
                                        context,
                                        "Plik się jeszcze zapisuje, spróbuj ponownie",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                is com.uxellence.tv.v3.update.UpdateManager.InstallResult.Error -> {
                                    updateState = com.uxellence.tv.v3.update.UpdateState.READY
                                    android.widget.Toast.makeText(
                                        context,
                                        "Instalator niedostępny — pobierz ponownie",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    onDismissUpdateBadge = {
                        com.uxellence.tv.v3.utils.VersionTracker.setKontoUpdateBadge(context, false)
                    }
                )

                DisposableEffect(currentScreen) {
                    onDispose {
                        if (currentScreen != NavigationScreen.TOP_MENU2 &&
                            currentScreen != NavigationScreen.MOVIE_DETAIL &&
                            currentScreen != NavigationScreen.PURCHASE &&
                            currentScreen != NavigationScreen.RENTAL_PROCESSING &&
                            pipPlayer != null) {
                            android.util.Log.d("PIP", "Leaving TOP_MENU2 - cleaning up PIP player")
                            pipPlayer?.stop()
                            pipPlayer?.release()
                            pipPlayer = null
                            pipStreamUrl = null
                            pipMode = false
                        }
                    }
                }
            }
        }

        when (currentScreen) {
            NavigationScreen.SPLASH -> {
                // Splash screen with start.png logo
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    delay(2000) // 2 sekundy
                    // Określ początkowy ekran na podstawie stanu aplikacji
                    currentScreen = when {
                        // AKTUALIZACJA (nie pierwsza instalacja): changelog NAJPIERW
                        com.uxellence.tv.v3.utils.VersionTracker.shouldShowWhatsNew(context) &&
                        !com.uxellence.tv.v3.utils.VersionTracker.isFirstInstall(context) ->
                            NavigationScreen.WHATS_NEW

                        // PIERWSZA INSTALACJA: sprawdź czy launcher setup został zakończony
                        com.uxellence.tv.v3.utils.VersionTracker.isFirstInstall(context) ->
                            if (!com.uxellence.tv.v3.utils.VersionTracker.isLauncherSetupCompleted(context)) {
                                NavigationScreen.LAUNCHER_SETUP  // First run: show launcher setup
                            } else {
                                NavigationScreen.STARTUP_MODE_SELECTION  // Launcher setup done: show mode selection
                            }

                        // Normalny start: użyj zapisanego trybu startowego
                        else -> when (com.uxellence.tv.v3.utils.VersionTracker.getStartupMode(context)) {
                            com.uxellence.tv.v3.utils.VersionTracker.MODE_EPG_DAY -> {
                                isEpgDayFromStartup = true  // Launched from startup - show overlay
                                NavigationScreen.EPG_DAY
                            }
                            else -> NavigationScreen.TOP_MENU2
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF48227C)), // Tło jak w TopMenuScreen2
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.start),
                        contentDescription = "BOX TV Logo",
                        modifier = Modifier
                            .fillMaxWidth(0.4f) // 40% szerokości ekranu
                            .aspectRatio(1.78f), // Proporcje obrazka
                        contentScale = ContentScale.Fit
                    )
                }
            }
            NavigationScreen.STARTUP_MODE_SELECTION -> {
                val configuration = LocalConfiguration.current
                val scaleX = configuration.screenWidthDp / 1920f
                val scaleY = configuration.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp

                StartupModeSelectionScreen(
                    onModeSelected = { mode ->
                        // ZAWSZE przejdź do wybranego trybu (nawet z ACCOUNT)
                        currentScreen = when (mode) {
                            com.uxellence.tv.v3.utils.VersionTracker.MODE_EPG_DAY -> {
                                isEpgDayFromStartup = true  // Telewizja - uruchom EPG DAY z overlay
                                NavigationScreen.EPG_DAY
                            }
                            else -> NavigationScreen.TOP_MENU2  // Telewizja + aplikacje - zakładka START
                        }
                    },
                    onBackPressed = {
                        currentScreen = if (previousScreen == NavigationScreen.HOME) {
                            NavigationScreen.HOME
                        } else {
                            NavigationScreen.TOP_MENU2  // Return to ACCOUNT or default menu
                        }
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.LAUNCHER_SETUP -> {
                val context = LocalContext.current
                val configuration = LocalConfiguration.current
                val scaleX = configuration.screenWidthDp / 1920f
                val scaleY = configuration.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp

                LauncherSetupScreen(
                    onOpenSettings = {
                        // Open Android TV Settings to set as launcher
                        openLauncherSettings(context)
                        // Stay on this screen - user will come back after settings
                    },
                    onSkip = {
                        // User chose to skip launcher setup - go to mode selection
                        currentScreen = NavigationScreen.STARTUP_MODE_SELECTION
                    },
                    onBackPressed = {
                        // BACK = same as Skip
                        currentScreen = NavigationScreen.STARTUP_MODE_SELECTION
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.WHATS_NEW -> {
                val configuration = LocalConfiguration.current
                val scaleX = configuration.screenWidthDp / 1920f
                val scaleY = configuration.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp

                WhatsNewScreen(
                    onContinue = {
                        // Po changelog przejdź do wyboru trybu
                        currentScreen = NavigationScreen.STARTUP_MODE_SELECTION
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.VOICE_TEST -> {
                VoiceTestScreen(
                    onBackPressed = {
                        currentScreen = NavigationScreen.HOME
                    }
                )
            }
            NavigationScreen.LIVE -> {
                LiveScreen(
                    onBackPressed = {
                        currentScreen = NavigationScreen.HOME
                        selectedChannelName = null // Reset selected channel
                    },
                    initialChannelName = selectedChannelName
                )
            }
            NavigationScreen.FOCUS_MINI_CARD -> {
                val configuration = LocalConfiguration.current
                val scaleX = configuration.screenWidthDp / 1920f
                val scaleY = configuration.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp

                FocusMiniCardScreen(
                    onReturnToMenu = {
                        currentScreen = NavigationScreen.HOME
                    },
                    shouldAutoFocus = true,
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.COMPONENT_SHOWCASE -> {
                ComponentShowcaseScreen()
            }
            NavigationScreen.HOME -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TVMenuGrid(
                        menuItems = menuItems,
                        onItemSelected = { selectedScreen ->
                            if (selectedScreen == NavigationScreen.EPG) {
                                previousScreen = currentScreen
                            }
                            currentScreen = selectedScreen
                        },
                        columns = 3,
                        itemsPerColumn = 8,
                        initialFocusPosition = Pair(0, 0)
                    )
                }
            }
            NavigationScreen.EPG -> {
                var guide by remember { mutableStateOf<EpgGuide?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                val now = java.time.Instant.now()
                val startOfDay = now.atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                val endOfDay = startOfDay.plus(java.time.Duration.ofDays(1))
                val windowStart = startOfDay
                val windowEnd = endOfDay

                // Load EPG data from repository (cache-first)
                LaunchedEffect(Unit) {
                    try {
                        isLoading = true
                        guide = repository.getEpgGuide(
                            startTime = windowStart,
                            endTime = windowEnd,
                            maxChannels = 50
                        )
                        error = null
                    } catch (t: Throwable) {
                        error = t.message ?: t::class.java.simpleName
                    } finally {
                        isLoading = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        error != null -> Text(
                            "Nie udało się pobrać EPG: ${error}",
                            color = Color.White
                        )
                        isLoading -> Text("Ładowanie EPG...", color = Color.White)
                        guide == null -> Text("Brak danych EPG", color = Color.White)
                        else -> EpgScreen(
                            guide = guide!!,
                            windowStart = windowStart,
                            windowEnd = windowEnd,
                            onBackPressed = {
                                currentScreen = previousScreen
                            }
                        )
                    }
                }
            }
            NavigationScreen.EPG_DAY -> {
                // Layout Engineer: Setup sx/sy scaling functions
                val configuration = LocalConfiguration.current
                val scaleX = configuration.screenWidthDp / 1920f
                val scaleY = configuration.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp

                EpgDayScreen(
                    onBackPressed = {
                        // Focus Architect: callback delegation - return to previous screen
                        currentScreen = previousScreen
                    },
                    onNavigateToPipMode = { player, streamUrl ->
                        // PIP Mode: Save player state and navigate to TopMenuScreen2/START
                        pipPlayer = player
                        pipStreamUrl = streamUrl
                        pipMode = true
                        savedTelewizjaSection = "ODKRYWAJ"  // Navigate to START tab
                        previousScreen = NavigationScreen.EPG_DAY
                        currentScreen = NavigationScreen.TOP_MENU2
                    },
                    initialChannelId = savedTelewizjaFocus?.itemId,  // Start EPG on selected channel (itemId contains epgId like "Polsat")
                    showTopMenuOverlay = false,  // Disabled: was isEpgDayFromStartup
                    shouldNavigateHomeWithPip = shouldNavigateHomeWithPip,  // HOME button PIP navigation request
                    onHomeNavigationComplete = { shouldNavigateHomeWithPip = false },  // Reset flag after navigation
                    sx = ::sx,  // Layout Engineer: ALWAYS pass sx/sy
                    sy = ::sy
                )
            }
            NavigationScreen.ZAPPING_BAR -> {
                // Layout Engineer: Setup sx/sy scaling functions
                val configuration = LocalConfiguration.current
                val scaleX = configuration.screenWidthDp / 1920f
                val scaleY = configuration.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp

                ZappingBarScreen(
                    onBackPressed = {
                        // Focus Architect: callback delegation - return to HOME
                        currentScreen = NavigationScreen.HOME
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.CHANNEL_GRID -> {
                ChannelGridScreen(
                    onBackPressed = {
                        // Focus Architect: callback delegation - return to source screen
                        if (channelGridSourceScreen == NavigationScreen.TOP_MENU2 && channelGridSourceSection != null) {
                            // Restore TELEWIZJA section in TopMenuScreen2
                            savedTelewizjaSection = channelGridSourceSection
                            currentScreen = NavigationScreen.TOP_MENU2
                        } else {
                            // Fallback: return to HOME if source unknown
                            currentScreen = NavigationScreen.HOME
                        }

                        // Clear source tracking
                        channelGridSourceScreen = null
                        channelGridSourceSection = null
                    },
                    screenTitle = channelGridTitle,
                    initialCategory = channelGridCategory,
                    channelFilter = channelGridFilter,
                    preloadedChannels = channelGridChannelList
                )
            }
            NavigationScreen.WIDEO_GRID -> {
                VodGridScreen(
                    onBackPressed = {
                        // Return to HOME
                        currentScreen = NavigationScreen.HOME
                    }
                )
            }
            NavigationScreen.VOD_GRID -> {
                VodGridScreen(
                    onBackPressed = {
                        // Return to source section in TOP_MENU2 (WIDEO when chip→grid path)
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = vodGridSourceSection ?: "TELEWIZJA"
                    },
                    screenTitle = vodGridTitle,
                    preloadedData = vodGridPrefiltered,
                    initialCategory = vodGridInitialCategory,
                    initialFocusedMovieId = vodGridLastClickedMovieId,
                    initialSearchQuery = vodGridLastSearchQuery,
                    onCategoryChanged = { newCategory ->
                        vodGridInitialCategory = newCategory
                    },
                    onSearchQueryChanged = { newQuery ->
                        vodGridLastSearchQuery = newQuery
                    },
                    onMovieClicked = { vodContent, siblings, index ->
                        vodGridLastClickedMovieId = vodContent.id
                        movieDetailSiblings = siblings
                        movieDetailCurrentIndex = index
                        selectedMovieData = vodContentToWideoSlideData(vodContent)
                        previousScreen = NavigationScreen.VOD_GRID
                        currentScreen = NavigationScreen.MOVIE_DETAIL
                    },
                    onGlobalSearch = { query ->
                        // "Szukaj w całym serwisie" — przeskocz do TOP_MENU2/SEARCH
                        // z preseed query żeby user kontynuował pisanie tam.
                        pendingGlobalSearchQuery = query
                        savedTelewizjaSection = "SEARCH"
                        currentScreen = NavigationScreen.TOP_MENU2
                    }
                )
            }
            NavigationScreen.KINO_GRID -> {
                KinoGridScreen(
                    onBackPressed = {
                        // Restore Kino Play tab in TOP_MENU2 (default to KINO_PLAY when source missing)
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = kinoGridSourceSection ?: "KINO_PLAY"
                    },
                    screenTitle = kinoGridTitle,
                    initialCategory = kinoGridInitialCategory,
                    initialFocusedMovieId = kinoGridLastClickedMovieId,
                    initialSearchQuery = kinoGridLastSearchQuery,
                    onCategoryChanged = { newCategory ->
                        // Persist user's filter choice so coming back from MovieDetail
                        // re-mounts the grid with the same category preselected.
                        kinoGridInitialCategory = newCategory
                    },
                    onSearchQueryChanged = { newQuery ->
                        // Persist active search query so the overlay restores after
                        // MovieDetail close — same filtered grid, same focused poster.
                        kinoGridLastSearchQuery = newQuery
                    },
                    onMovieClicked = { vodContent ->
                        // Remember which poster was clicked so we can refocus it on BACK
                        kinoGridLastClickedMovieId = vodContent.id
                        // Match Kino Play tab behavior — open MovieDetailScreen with full slide data
                        selectedMovieData = VodSlideData(
                            title = vodContent.title,
                            genre = vodContent.category,
                            duration = "",
                            year = "",
                            country = "Polska",
                            ageRating = "13 lat",
                            description = vodContent.description,
                            price = vodContent.price ?: "19 zł/48h",
                            backgroundUrl = vodContent.backdropUrl ?: "",
                            posterUrl = vodContent.imageUrl,
                            youtubeUrl = vodContent.youtubeUrl,
                            isKinoPlay = true,
                            cast = vodContent.cast
                        )
                        previousScreen = NavigationScreen.KINO_GRID
                        currentScreen = NavigationScreen.MOVIE_DETAIL
                    },
                    onRentClicked = { vodContent ->
                        // Menu kontekstowe "Wypożycz" → ekran zakupu (jak przycisk Wypożycz
                        // w MovieDetail). Ta sama mapa co onMovieClicked.
                        kinoGridLastClickedMovieId = vodContent.id
                        selectedMovieData = VodSlideData(
                            title = vodContent.title,
                            genre = vodContent.category,
                            duration = "",
                            year = "",
                            country = "Polska",
                            ageRating = "13 lat",
                            description = vodContent.description,
                            price = vodContent.price ?: "19 zł/48h",
                            backgroundUrl = vodContent.backdropUrl ?: "",
                            posterUrl = vodContent.imageUrl,
                            youtubeUrl = vodContent.youtubeUrl,
                            isKinoPlay = true,
                            cast = vodContent.cast
                        )
                        cameFromQuickPurchase = false  // BACK/po zakupie → MovieDetail → grid
                        previousScreen = NavigationScreen.KINO_GRID
                        currentScreen = NavigationScreen.PURCHASE
                    }
                )
            }
            NavigationScreen.APPS_GRID -> {
                AppsGridScreen(
                    onBackPressed = {
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = "APLIKACJE"
                    }
                )
            }
            NavigationScreen.RECORDINGS_GRID -> {
                RecordingsGridScreen(
                    onBackPressed = {
                        // Return to MOJE section in TOP_MENU2
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = recordingsGridSourceSection ?: "MOJE"
                    },
                    onSeriesClick = { seriesId, title ->
                        // Navigate to series episodes drill-down
                        seriesEpisodesId = seriesId
                        seriesEpisodesTitle = title
                        previousScreen = NavigationScreen.RECORDINGS_GRID
                        currentScreen = NavigationScreen.SERIES_EPISODES
                    }
                )
            }
            NavigationScreen.OLYMPICS -> {
                OlympicsEventScreen(
                    onBackPressed = {
                        // Return to source section in TOP_MENU2
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = olympicsSourceSection ?: "ODKRYWAJ"
                    },
                    content = olympicsContent,
                    sourceSection = olympicsSourceSection ?: "ODKRYWAJ"
                )
            }
            NavigationScreen.SERIES_EPISODES -> {
                SeriesEpisodesGridScreen(
                    seriesId = seriesEpisodesId,
                    seriesTitle = seriesEpisodesTitle,
                    onBackPressed = {
                        // Return to recordings grid
                        currentScreen = NavigationScreen.RECORDINGS_GRID
                    }
                )
            }
            NavigationScreen.MOVIE_DETAIL -> {
                // Render TopMenuScreen2 underneath as base layer when MovieDetail was
                // launched from KINO_PLAY tab — preserves all VodWithChannels state so
                // BACK can refocus the exact poster the user was on.
                if (showTopMenuAsBase) {
                    topMenuMovable()
                }
                // Suspend the underlying Kino Play slider's trailer player while MovieDetail
                // is on top — otherwise ExoPlayer keeps decoding video in the background and
                // key handling on MovieDetail gets sluggish after a while.
                DisposableEffect(Unit) {
                    VodDataCache.overlayActive.value = true
                    onDispose { VodDataCache.overlayActive.value = false }
                }
                selectedMovieData?.let { movieData ->
                    MovieDetailScreen(
                        item = movieData,
                        onBackPressed = {
                            // Return to whichever screen launched MovieDetail
                            if (previousScreen == NavigationScreen.KINO_GRID) {
                                currentScreen = NavigationScreen.KINO_GRID
                            } else if (previousScreen == NavigationScreen.VOD_GRID) {
                                // VodGridScreen seeds initialFocusedMovieId from saved state
                                // and re-syncs scroll on its own first frame.
                                currentScreen = NavigationScreen.VOD_GRID
                            } else {
                                // From TopMenu2 tab (KINO_PLAY slider/channels OR
                                // MOJE→Wypożyczone) — TopMenuScreen2 is still mounted via
                                // movableContentOf overlay so the active tab is preserved
                                // in its internal globalFocusState.sectionId. We just need
                                // to re-grab keyboard focus on the right outer Box. Bump
                                // BOTH triggers — each section's listener acts only when
                                // its own sectionId is active.
                                VodDataCache.kinoPlayRefocusTrigger.value =
                                    VodDataCache.kinoPlayRefocusTrigger.value + 1
                                VodDataCache.mojeRefocusTrigger.value =
                                    VodDataCache.mojeRefocusTrigger.value + 1
                                VodDataCache.searchRefocusTrigger.value =
                                    VodDataCache.searchRefocusTrigger.value + 1
                                VodDataCache.wideoRefocusTrigger.value =
                                    VodDataCache.wideoRefocusTrigger.value + 1
                                VodDataCache.odkrywajRefocusTrigger.value =
                                    VodDataCache.odkrywajRefocusTrigger.value + 1
                                // Overlay path: state survives via movableContentOf, so we
                                // don't need savedKinoPlayFocus for restoration. Clear it
                                // here so a later, unrelated remount of VodWithChannels
                                // (user navigates KINO_PLAY → TELEWIZJA → KINO_PLAY) doesn't
                                // see a stale entry and steal focus from the top menu.
                                VodDataCache.savedKinoPlayFocus = null
                                currentScreen = NavigationScreen.TOP_MENU2
                                // Don't overwrite savedTelewizjaSection — internal section
                                // state inside TopMenuScreen2 is the source of truth here.
                            }
                        },
                        onRentClicked = {
                            // Navigate to PurchaseScreen (normal flow from MovieDetail)
                            cameFromQuickPurchase = false  // Normal flow, not quick purchase
                            currentScreen = NavigationScreen.PURCHASE
                        },
                        onWatchClicked = {
                            // TODO: route to VOD player once player integration lands.
                            // For now we just log — visual UX (Oglądaj button + countdown) is in place.
                            android.util.Log.d("MOVIE_DETAIL", "Watch clicked: ${movieData.title}")
                        },
                        onTrailerClicked = {
                            // TODO: Play trailer
                            android.util.Log.d("MOVIE_DETAIL", "Trailer clicked: ${movieData.title}")
                        },
                        onPreviewClicked = {
                            // TODO: Play preview
                            android.util.Log.d("MOVIE_DETAIL", "Preview clicked: ${movieData.title}")
                        },
                        onMoreInfoClicked = {
                            // TODO: Show more info
                            android.util.Log.d("MOVIE_DETAIL", "More info clicked: ${movieData.title}")
                        },
                        // Sibling prev/next — only when siblings list is populated AND
                        // we're on a WIDEO entry (isKinoPlay=false). Each invocation swaps
                        // selectedMovieData so the screen recomposes with the new film.
                        onNavigatePrev = if (
                            !movieData.isKinoPlay &&
                            movieDetailSiblings.isNotEmpty() &&
                            movieDetailCurrentIndex > 0
                        ) {
                            {
                                movieDetailCurrentIndex -= 1
                                val prev = movieDetailSiblings[movieDetailCurrentIndex]
                                vodGridLastClickedMovieId = prev.id
                                selectedMovieData = vodContentToWideoSlideData(prev)
                            }
                        } else null,
                        onNavigateNext = if (
                            !movieData.isKinoPlay &&
                            movieDetailSiblings.isNotEmpty() &&
                            movieDetailCurrentIndex < movieDetailSiblings.size - 1
                        ) {
                            {
                                movieDetailCurrentIndex += 1
                                val next = movieDetailSiblings[movieDetailCurrentIndex]
                                vodGridLastClickedMovieId = next.id
                                selectedMovieData = vodContentToWideoSlideData(next)
                            }
                        } else null
                    )
                } ?: run {
                    // Fallback if no movie data - return to TOP_MENU2
                    LaunchedEffect(Unit) {
                        currentScreen = NavigationScreen.TOP_MENU2
                    }
                }
            }
            NavigationScreen.PURCHASE -> {
                // Quick-purchase from KINO_PLAY slider — render TopMenuScreen2 underneath
                // (same overlay pattern as MovieDetail) so BACK refocuses the slider.
                if (showTopMenuAsBase) {
                    topMenuMovable()
                }
                DisposableEffect(Unit) {
                    VodDataCache.overlayActive.value = true
                    onDispose { VodDataCache.overlayActive.value = false }
                }
                selectedMovieData?.let { movieData ->
                    PurchaseScreen(
                        item = movieData,
                        userEmail = "adres@domena.pl",
                        userPhoneNumber = "690100003",
                        onBackPressed = {
                            // Return based on how we got here
                            if (cameFromQuickPurchase) {
                                // Quick purchase mode (Wypożycz on slider): TopMenuScreen2
                                // stayed mounted via overlay. Bump both refocus triggers —
                                // each section's listener acts only when its sectionId is
                                // active. Don't override savedTelewizjaSection (internal
                                // section state is the source of truth in overlay scenarios).
                                VodDataCache.kinoPlayRefocusTrigger.value =
                                    VodDataCache.kinoPlayRefocusTrigger.value + 1
                                VodDataCache.mojeRefocusTrigger.value =
                                    VodDataCache.mojeRefocusTrigger.value + 1
                                VodDataCache.searchRefocusTrigger.value =
                                    VodDataCache.searchRefocusTrigger.value + 1
                                VodDataCache.wideoRefocusTrigger.value =
                                    VodDataCache.wideoRefocusTrigger.value + 1
                                VodDataCache.odkrywajRefocusTrigger.value =
                                    VodDataCache.odkrywajRefocusTrigger.value + 1
                                // Clear stale savedKinoPlayFocus — see MovieDetail.onBackPressed
                                // for the same reasoning (state already survives via overlay).
                                VodDataCache.savedKinoPlayFocus = null
                                currentScreen = NavigationScreen.TOP_MENU2
                            } else {
                                // Normal flow: go back to MovieDetailScreen
                                currentScreen = NavigationScreen.MOVIE_DETAIL
                            }
                        },
                        onConfirmPurchase = {
                            android.util.Log.d("PURCHASE", "Purchase confirmed: ${movieData.title}")
                            // VodSlideData has no stable id; key rentals by title (same
                            // convention used in MovieDetailScreen and MojeContentCache).
                            com.uxellence.tv.v3.rental.RentalManager.rent(
                                movieId = movieData.title,
                                context = context
                            )
                            currentScreen = NavigationScreen.RENTAL_PROCESSING
                        },
                        onChangeEmail = {
                            // TODO: Implement email change
                            android.util.Log.d("PURCHASE", "Change email clicked")
                        },
                        onShowRegulations = {
                            // TODO: Show regulations
                            android.util.Log.d("PURCHASE", "Show regulations clicked")
                        }
                    )
                } ?: run {
                    // Fallback if no movie data - return based on how we got here
                    LaunchedEffect(Unit) {
                        if (cameFromQuickPurchase) {
                            currentScreen = NavigationScreen.TOP_MENU2
                            savedTelewizjaSection = "KINO_PLAY"
                        } else {
                            currentScreen = NavigationScreen.MOVIE_DETAIL
                        }
                    }
                }
            }
            NavigationScreen.RENTAL_PROCESSING -> {
                // Animated 2-stage screen ("Przetwarzanie płatności" → "Film wypożyczony!")
                // shown after onConfirmPurchase. TopMenuScreen2 stays mounted as base via
                // movableContentOf overlay so BACK from MovieDetail (after auto-return)
                // can still refocus the original poster.
                if (showTopMenuAsBase) {
                    topMenuMovable()
                }
                DisposableEffect(Unit) {
                    VodDataCache.overlayActive.value = true
                    onDispose { VodDataCache.overlayActive.value = false }
                }
                selectedMovieData?.let { movieData ->
                    com.uxellence.tv.v3.moviedetail.RentalProcessingScreen(
                        movieTitle = movieData.title,
                        onComplete = {
                            // Auto-return to MovieDetail (which now reflects "Oglądaj" state)
                            currentScreen = NavigationScreen.MOVIE_DETAIL
                        }
                    )
                } ?: run {
                    LaunchedEffect(Unit) {
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = "KINO_PLAY"
                    }
                }
            }
            NavigationScreen.VOD_PLAYER -> {
                val vodConfig = LocalConfiguration.current
                val scaleX = vodConfig.screenWidthDp / 1920f
                val scaleY = vodConfig.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp
                com.uxellence.tv.v3.vodplayer.VodPlayerScreen(
                    streamUrl = vodPlayerUrl,
                    title = vodPlayerTitle,
                    onBackPressed = {
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = "KINO_PLAY"
                    },
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.DEMO_LIVE -> {
                val demoConfig = LocalConfiguration.current
                val scaleX = demoConfig.screenWidthDp / 1920f
                val scaleY = demoConfig.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp
                com.uxellence.tv.v3.demolive.DemoLiveScreen(
                    onBackPressed = { currentScreen = NavigationScreen.HOME },
                    sx = ::sx,
                    sy = ::sy
                )
            }
            NavigationScreen.DEMO_VOD -> {
                val demoConfig = LocalConfiguration.current
                val scaleX = demoConfig.screenWidthDp / 1920f
                val scaleY = demoConfig.screenHeightDp / 1080f
                fun sx(px: Int) = (px * scaleX).dp
                fun sy(px: Int) = (px * scaleY).dp
                com.uxellence.tv.v3.vodplayer.VodPlayerScreen(
                    streamUrl = "https://archive.org/download/Sintel/sintel-2048-stereo_512kb.mp4",
                    title = "Demo: VOD + miniaturki (Sintel)",
                    onBackPressed = { currentScreen = NavigationScreen.HOME },
                    sx = ::sx,
                    sy = ::sy,
                    fullFilmstrip = true
                )
            }
            NavigationScreen.SLIDER -> {
                SliderScreen()
            }
            NavigationScreen.SLIDER_MIX -> {
                SliderMixScreen()
            }
            NavigationScreen.VIDEOSLIDER -> {
                VideoSliderScreen()
            }
            NavigationScreen.CHANNELE -> {
                ChanneleScreen()
            }
            NavigationScreen.SHORTCUT -> {
                ShortcutScreen()
            }
            NavigationScreen.TOP_MENU2 -> {
                topMenuMovable()
            }
        }

        // ====== UPDATE DIALOG ======
        if (showUpdateDialog && updateInfo != null) {
            com.uxellence.tv.v3.update.UpdateDialog(
                updateInfo = updateInfo!!,
                currentVersion = updateManager.getCurrentVersionName(),
                updateState = updateState,
                downloadProgress = downloadProgress,
                isApkReady = isApkReadyForInstall,
                onDownload = {
                    updateState = com.uxellence.tv.v3.update.UpdateState.DOWNLOADING
                    coroutineScope.launch {
                        updateManager.downloadApk(
                            updateInfo = updateInfo!!,
                            onProgress = { progress -> downloadProgress = progress },
                            onComplete = { success ->
                                if (success) {
                                    updateState = com.uxellence.tv.v3.update.UpdateState.INSTALLING
                                } else {
                                    updateState = com.uxellence.tv.v3.update.UpdateState.READY
                                    android.util.Log.e("UPDATE", "Download failed")
                                }
                            }
                        )
                    }
                },
                onInstall = {
                    // If the user races the FS sync (taps "Zainstaluj" the
                    // instant DOWNLOADING flips to INSTALLING but before the
                    // OS actually publishes the APK), `installApk()` returns
                    // NotReady. Poll up to ~5 s, then either retry the install
                    // or fall back to "download again" if the file is gone.
                    coroutineScope.launch {
                        var result = updateManager.installApk()
                        var waited = 0
                        while (result is com.uxellence.tv.v3.update.UpdateManager.InstallResult.NotReady && waited < 5000) {
                            kotlinx.coroutines.delay(250)
                            waited += 250
                            if (updateManager.isApkReady()) {
                                result = updateManager.installApk()
                            }
                        }
                        when (result) {
                            com.uxellence.tv.v3.update.UpdateManager.InstallResult.Success -> {
                                android.util.Log.d("UPDATE", "Installer launched")
                            }
                            com.uxellence.tv.v3.update.UpdateManager.InstallResult.NotReady -> {
                                android.util.Log.w("UPDATE", "APK not ready after 5s — dropping back to READY")
                                updateState = com.uxellence.tv.v3.update.UpdateState.READY
                                android.widget.Toast.makeText(
                                    context,
                                    "Plik się jeszcze zapisuje, spróbuj ponownie",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            is com.uxellence.tv.v3.update.UpdateManager.InstallResult.Error -> {
                                android.util.Log.e("UPDATE", "Install error: ${result.message}")
                                updateState = com.uxellence.tv.v3.update.UpdateState.READY
                                android.widget.Toast.makeText(
                                    context,
                                    "Instalator niedostępny — pobierz ponownie",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onDismiss = {
                    showUpdateDialog = false
                    updateState = com.uxellence.tv.v3.update.UpdateState.READY
                }
            )
        }
    }
}

/**
 * Maps a VodContent (WIDEO catalog item) to a VodSlideData with isKinoPlay=false so
 * MovieDetailScreen renders WIDEO mode (Oglądaj + Do obejrzenia, no Wypożycz). Used for
 * both initial entry and sibling prev/next swaps so the rebuild is consistent.
 */
private fun vodContentToWideoSlideData(vodContent: com.uxellence.tv.v3.version001.VodContent): VodSlideData =
    VodSlideData(
        title = vodContent.title,
        genre = vodContent.category,
        duration = "",
        year = "",
        country = "Polska",
        ageRating = "13 lat",
        description = vodContent.description,
        price = vodContent.price ?: "19 zł/48h",
        backgroundUrl = vodContent.backdropUrl ?: vodContent.imageUrl,
        posterUrl = vodContent.imageUrl,
        youtubeUrl = vodContent.youtubeUrl,
        isKinoPlay = false,
        cast = vodContent.cast,
        channelLogoUrl = vodContent.channelLogoUrl
    )
