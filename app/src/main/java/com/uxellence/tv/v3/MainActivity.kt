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
        // Initialize ChannelManager with TV channels database
        ChannelManager.initialize(this)
        // Initialize ConfigManager - load cached config from Supabase
        ConfigManager.initialize(this)
        // Auto-refresh config from Supabase on app startup
        lifecycleScope.launch {
            ConfigManager.refreshConfig(this@MainActivity)
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
    HOME, LIVE, COMPONENT_SHOWCASE, TOP_MENU2, SHORTCUT, CHANNELE, VIDEOSLIDER, SLIDER, SLIDER_MIX, EPG, EPG_DAY, FOCUS_MINI_CARD, VOICE_TEST, SPLASH, WHATS_NEW, STARTUP_MODE_SELECTION, LAUNCHER_SETUP, ZAPPING_BAR, CHANNEL_GRID, WIDEO_GRID, KINO_GRID, VOD_GRID, RECORDINGS_GRID, SERIES_EPISODES, MOVIE_DETAIL, PURCHASE
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

    // RecordingsGridScreen navigation parameters
    var recordingsGridSourceSection by remember { mutableStateOf<String?>(null) }  // "MOJE"

    // SeriesEpisodesGridScreen navigation parameters
    var seriesEpisodesId by remember { mutableStateOf("") }
    var seriesEpisodesTitle by remember { mutableStateOf("") }

    // MovieDetailScreen navigation parameters
    var selectedMovieData by remember { mutableStateOf<VodSlideData?>(null) }
    var cameFromQuickPurchase by remember { mutableStateOf(false) }  // Track if we used quick purchase mode

    // Save TELEWIZJA focus state for smart BACK navigation (ID-based)
    var savedTelewizjaFocus by remember { mutableStateOf<FocusState?>(null) }

    // Save TOP_MENU2 section for smart BACK navigation: "TELEWIZJA", "MOJE", etc.
    var savedTelewizjaSection by remember { mutableStateOf<String?>(null) }

    // PIP (Picture-in-Picture) state
    var pipPlayer by remember { mutableStateOf<com.google.android.exoplayer2.ExoPlayer?>(null) }
    var pipStreamUrl by remember { mutableStateOf<String?>(null) }
    var pipMode by remember { mutableStateOf(false) }

    // Track if EPG_DAY was launched from startup (show overlay) or from TOP_MENU2 (no overlay)
    var isEpgDayFromStartup by remember { mutableStateOf(false) }

    // State flag for HOME button PIP navigation request
    var shouldNavigateHomeWithPip by remember { mutableStateOf(false) }

    // Start background EPG loading on app start
    LaunchedEffect(Unit) {
        repository.startBackgroundRefresh()
    }

    // Handle HOME button navigation (from LauncherActivity)
    // When homePressedTrigger changes, navigate based on current state
    LaunchedEffect(homePressedTrigger) {
        if (homePressedTrigger > 0) {  // Ignore initial value (0)
            android.util.Log.d("HOME_NAVIGATION", "HOME pressed (trigger=$homePressedTrigger)")

            when {
                // Scenariusz 1: PIP aktywny w TopMenu2 → Wróć do fullscreen EPG
                pipPlayer != null && pipMode && currentScreen == NavigationScreen.TOP_MENU2 -> {
                    android.util.Log.d("HOME_NAVIGATION", "PIP active - returning to fullscreen EPG")

                    currentScreen = NavigationScreen.EPG_DAY

                    // Close PIP (same as onClosePip callback)
                    pipPlayer?.stop()
                    pipPlayer?.release()
                    pipPlayer = null
                    pipStreamUrl = null
                    pipMode = false
                }

                // Scenariusz 2: Fullscreen EPG → Nawiguj do TopMenu2 z PIP (jak klawisz "0")
                currentScreen == NavigationScreen.EPG_DAY -> {
                    android.util.Log.d("HOME_NAVIGATION", "EPG Day Test - requesting PIP navigation")
                    shouldNavigateHomeWithPip = true  // EpgDayScreen wykryje i przeniesie player do PIP
                }

                // Scenariusz 3: Inne ekrany → Nawiguj do TopMenu2/START (bez PIP)
                else -> {
                    android.util.Log.d("HOME_NAVIGATION", "Other screen - navigating to TOP_MENU2/START")

                    currentScreen = NavigationScreen.TOP_MENU2
                    savedTelewizjaSection = "ODKRYWAJ"  // START tab

                    // Clear PIP if active (HOME should reset to clean state)
                    pipPlayer?.stop()
                    pipPlayer?.release()
                    pipPlayer = null
                    pipStreamUrl = null
                    pipMode = false
                }
            }
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
                        // Return to source section in TOP_MENU2
                        currentScreen = NavigationScreen.TOP_MENU2
                        savedTelewizjaSection = vodGridSourceSection ?: "TELEWIZJA"
                    },
                    screenTitle = vodGridTitle,
                    preloadedData = vodGridPrefiltered
                )
            }
            NavigationScreen.KINO_GRID -> {
                KinoGridScreen(
                    onBackPressed = {
                        // Smart BACK: return to previous screen
                        when (previousScreen) {
                            NavigationScreen.TOP_MENU2 -> {
                                // Return to TOP_MENU2 (came from KINO PLAY section)
                                currentScreen = NavigationScreen.TOP_MENU2
                                previousScreen = NavigationScreen.HOME
                            }
                            else -> {
                                // Default: return to HOME
                                currentScreen = NavigationScreen.HOME
                            }
                        }
                    },
                    screenTitle = kinoGridTitle,
                    preloadedData = kinoGridPrefiltered
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
                selectedMovieData?.let { movieData ->
                    MovieDetailScreen(
                        item = movieData,
                        onBackPressed = {
                            // Return to KINO_PLAY section in TOP_MENU2
                            currentScreen = NavigationScreen.TOP_MENU2
                            savedTelewizjaSection = "KINO_PLAY"
                        },
                        onRentClicked = {
                            // Navigate to PurchaseScreen (normal flow from MovieDetail)
                            cameFromQuickPurchase = false  // Normal flow, not quick purchase
                            currentScreen = NavigationScreen.PURCHASE
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
                        }
                    )
                } ?: run {
                    // Fallback if no movie data - return to TOP_MENU2
                    LaunchedEffect(Unit) {
                        currentScreen = NavigationScreen.TOP_MENU2
                    }
                }
            }
            NavigationScreen.PURCHASE -> {
                selectedMovieData?.let { movieData ->
                    PurchaseScreen(
                        item = movieData,
                        userEmail = "adres@domena.pl",
                        userPhoneNumber = "690100003",
                        onBackPressed = {
                            // Return based on how we got here
                            if (cameFromQuickPurchase) {
                                // Quick purchase mode: go back to slider (KINO_PLAY)
                                currentScreen = NavigationScreen.TOP_MENU2
                                savedTelewizjaSection = "KINO_PLAY"
                            } else {
                                // Normal flow: go back to MovieDetailScreen
                                currentScreen = NavigationScreen.MOVIE_DETAIL
                            }
                        },
                        onConfirmPurchase = {
                            // TODO: Implement purchase confirmation
                            android.util.Log.d("PURCHASE", "Purchase confirmed: ${movieData.title}")
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
                // Clear saved focus after delay when returning from EPG Day Test
                LaunchedEffect(currentScreen, savedTelewizjaFocus) {
                    if (currentScreen == NavigationScreen.TOP_MENU2 && savedTelewizjaFocus != null) {
                        android.util.Log.d("TELEWIZJA_FOCUS", "Returned to TOP_MENU2 with saved focus, will clear after 5000ms")
                        kotlinx.coroutines.delay(5000) // Give restoration time (4000ms timeout + 1000ms buffer) before clearing
                        savedTelewizjaFocus = null
                        android.util.Log.d("TELEWIZJA_FOCUS", "Cleared savedTelewizjaFocus after delay")
                    }
                }

                TopMenuScreen2(
                    onBackPressed = { isMenuFocused ->
                        // Never handle BACK from TopMenuScreen2
                        // PIP mode: TopMenuScreen2 returns to EPG Day Test via onReturnToEpgDay
                        // Normal mode: BACK from menu is end of path (do nothing)
                        false
                    },
                    onReturnToEpgDay = {
                        // Return to EPG Day Test from PIP mode
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
                        // Save current focus state (ID-based) and section for smart BACK navigation
                        savedTelewizjaFocus = FocusState(
                            channelId = channelId,
                            itemId = itemId,
                            scrollPosition = scrollPosition
                        )
                        savedTelewizjaSection = sectionId
                        previousScreen = currentScreen
                        isEpgDayFromStartup = false  // Launched from TOP_MENU2 - NO overlay
                        currentScreen = NavigationScreen.EPG_DAY
                    },
                    onNavigateToStartupMode = {
                        // Navigate to startup mode selection from ACCOUNT section
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.STARTUP_MODE_SELECTION
                    },
                    onFocusRestored = {
                        // Callback no longer used - clearing handled by LaunchedEffect above
                    },
                    restoredTelewizjaFocus = savedTelewizjaFocus,
                    restoredSection = savedTelewizjaSection,
                    pipPlayer = pipPlayer,
                    onClosePip = {
                        // Close PIP: stop and release player
                        pipPlayer?.stop()
                        pipPlayer?.release()
                        pipPlayer = null
                        pipStreamUrl = null
                        pipMode = false
                    },
                    onNavigateToChannelGrid = { title, category, filter, channelList ->
                        // Save current screen and section before navigation (for BACK button)
                        channelGridSourceScreen = NavigationScreen.TOP_MENU2
                        channelGridSourceSection = savedTelewizjaSection ?: "TELEWIZJA"  // Default to TELEWIZJA if null

                        // Navigate to ChannelGridScreen with dynamic parameters
                        channelGridTitle = title
                        channelGridCategory = category
                        channelGridFilter = filter
                        channelGridChannelList = channelList
                        currentScreen = NavigationScreen.CHANNEL_GRID
                    },
                    onNavigateToVodGrid = { title, prefiltered, sourceSection ->
                        // Navigate to VOD grid (Nagrania, Wypożyczone, Do obejrzenia, etc.)
                        vodGridTitle = title
                        vodGridPrefiltered = prefiltered
                        vodGridSourceSection = sourceSection
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.VOD_GRID
                    },
                    onNavigateToKinoGrid = { title, prefiltered, sourceSection ->
                        // Navigate to KINO grid (Akcja, Horror, etc. - vertical posters)
                        kinoGridTitle = title
                        kinoGridPrefiltered = prefiltered
                        kinoGridSourceSection = sourceSection
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.KINO_GRID
                    },
                    onNavigateToRecordingsGrid = { _, sourceSection ->
                        // Navigate to Recordings grid (title is now dynamic based on filter)
                        recordingsGridSourceSection = sourceSection
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.RECORDINGS_GRID
                    },
                    onNavigateToMovieDetail = { movieData ->
                        // Navigate to MovieDetailScreen from KINO PLAY slider
                        selectedMovieData = movieData
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.MOVIE_DETAIL
                    },
                    onNavigateToPurchase = { movieData ->
                        // Navigate directly to PurchaseScreen (quick purchase mode)
                        selectedMovieData = movieData
                        cameFromQuickPurchase = true  // Mark that we skipped MovieDetailScreen
                        previousScreen = NavigationScreen.TOP_MENU2
                        currentScreen = NavigationScreen.PURCHASE
                    }
                )

                // PIP lifecycle management: cleanup player when leaving TOP_MENU2
                DisposableEffect(currentScreen) {
                    onDispose {
                        if (currentScreen != NavigationScreen.TOP_MENU2 && pipPlayer != null) {
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
    }
}
