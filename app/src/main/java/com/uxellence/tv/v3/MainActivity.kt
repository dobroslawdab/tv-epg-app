package com.uxellence.tv.v3

import android.content.Context
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Force Polish locale for the entire application
        setAppLocale(this, "pl")
        // Initialize VOD data cache once at startup (avoids repeated I/O)
        VodDataCache.initialize(this)
        // Initialize ChannelManager with TV channels database
        ChannelManager.initialize(this)
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
    HOME, LIVE, COMPONENT_SHOWCASE, TOP_MENU, TOP_MENU2, SHORTCUT, CHANNELE, VIDEOSLIDER, SLIDER, SLIDER_MIX, EPG, EPG_DAY, FOCUS_MINI_CARD, VOICE_TEST, SPLASH, WHATS_NEW, STARTUP_MODE_SELECTION, ZAPPING_BAR
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
fun TvRoot() {
    val context = LocalContext.current
    val repository = remember { EpgRepository.getInstance(context) }
    var currentScreen by remember { mutableStateOf(NavigationScreen.SPLASH) }
    var previousScreen by remember { mutableStateOf(NavigationScreen.HOME) }
    var selectedChannelName by remember { mutableStateOf<String?>(null) }

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

    // Start background EPG loading on app start
    LaunchedEffect(Unit) {
        repository.startBackgroundRefresh()
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
            MainMenuItem(id = "top_menu", title = "Top Menu", navigationScreen = NavigationScreen.TOP_MENU),
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

                        // PIERWSZA INSTALACJA: wybór trybu (bez changelog)
                        com.uxellence.tv.v3.utils.VersionTracker.isFirstInstall(context) ->
                            NavigationScreen.STARTUP_MODE_SELECTION

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
                        // ZAWSZE przejdź do wybranego trybu (nawet z menu HOME)
                        currentScreen = when (mode) {
                            com.uxellence.tv.v3.utils.VersionTracker.MODE_EPG_DAY -> {
                                isEpgDayFromStartup = true  // Selected startup mode - show overlay
                                NavigationScreen.EPG_DAY
                            }
                            else -> NavigationScreen.TOP_MENU2
                        }
                    },
                    onBackPressed = {
                        currentScreen = if (previousScreen == NavigationScreen.HOME) {
                            NavigationScreen.HOME
                        } else {
                            NavigationScreen.TOP_MENU2  // Domyślnie wróć do menu
                        }
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
                    showTopMenuOverlay = isEpgDayFromStartup,  // Show overlay only when launched from startup
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
            NavigationScreen.TOP_MENU -> {
                TopMenuScreen(
                    onBackPressed = { isMenuFocused ->
                        if (isMenuFocused) {
                            // BACK from menu - return to HOME
                            currentScreen = NavigationScreen.HOME
                            true
                        } else {
                            // BACK from content - let TopMenuScreen handle (return to menu)
                            false
                        }
                    }
                )
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
