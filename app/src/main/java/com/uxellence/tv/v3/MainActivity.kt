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
    HOME, LIVE, COMPONENT_SHOWCASE, TOP_MENU, TOP_MENU2, SHORTCUT, CHANNELE, VIDEOSLIDER, SLIDER, SLIDER_MIX, EPG, FOCUS_MINI_CARD, VOICE_TEST, SPLASH
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
    var selectedChannelName by remember { mutableStateOf<String?>(null) }

    // Start background EPG loading on app start
    LaunchedEffect(Unit) {
        repository.startBackgroundRefresh()
    }

    // Menu items dla wszystkich ekranów - najnowsze na górze
    val menuItems = remember {
        listOf(
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
                LaunchedEffect(Unit) {
                    delay(2000) // 2 sekundy
                    currentScreen = NavigationScreen.TOP_MENU2
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
                            currentScreen = selectedScreen
                        },
                        columns = 3,
                        itemsPerColumn = 4,
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
                            windowEnd = windowEnd
                        )
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
                TopMenuScreen2(
                    onBackPressed = { isMenuFocused ->
                        if (isMenuFocused) {
                            // BACK from menu - return to HOME
                            currentScreen = NavigationScreen.HOME
                            true
                        } else {
                            // BACK from content - let TopMenuScreen2 handle (return to menu)
                            false
                        }
                    },
                    onShowMainMenu = {
                        currentScreen = NavigationScreen.HOME
                    },
                    onNavigateToLiveScreen = { channelName ->
                        selectedChannelName = channelName
                        currentScreen = NavigationScreen.LIVE
                    }
                )
            }
        }
    }
}
