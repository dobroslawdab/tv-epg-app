package com.example.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tv.epg.*
import com.example.tv.repository.EpgRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tv.ui.theme.figmaRadialBackground
import com.example.tv.version001.Version001Screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvRoot() }
    }
}

enum class NavigationScreen {
    HOME, LIVE, COMPONENT_SHOWCASE, VERSION003, VERSION002, TOP_MENU, VERSION001, SHORTCUT, CHANNELE, VIDEOSLIDER, SLIDER, EPG
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
    var currentScreen by remember { mutableStateOf(NavigationScreen.HOME) }

    // Start background EPG loading on app start
    LaunchedEffect(Unit) {
        repository.startBackgroundRefresh()
    }

    // Menu items dla wszystkich ekranów - najnowsze na górze
    val menuItems = remember {
        listOf(
            MainMenuItem(id = "live", title = "Live TV", navigationScreen = NavigationScreen.LIVE),
            MainMenuItem(id = "component_showcase", title = "Component Showcase", navigationScreen = NavigationScreen.COMPONENT_SHOWCASE),
            MainMenuItem(id = "version003", title = "Wersja 0.03", navigationScreen = NavigationScreen.VERSION003),
            MainMenuItem(id = "version002", title = "Wersja 0.02", navigationScreen = NavigationScreen.VERSION002),
            MainMenuItem(id = "top_menu", title = "Top Menu", navigationScreen = NavigationScreen.TOP_MENU),
            MainMenuItem(id = "version001", title = "Wersja 0.01", navigationScreen = NavigationScreen.VERSION001),
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
            NavigationScreen.LIVE -> {
                LiveScreen(
                    onBackPressed = {
                        currentScreen = NavigationScreen.HOME
                    }
                )
            }
            NavigationScreen.COMPONENT_SHOWCASE -> {
                ComponentShowcaseScreen()
            }
            NavigationScreen.VERSION003 -> {
                Version003Screen(
                    onBackPressed = { isMenuFocused ->
                        if (isMenuFocused) {
                            // BACK from menu - return to HOME
                            currentScreen = NavigationScreen.HOME
                            true
                        } else {
                            // BACK from content - let Version003Screen handle (return to menu)
                            false
                        }
                    }
                )
            }
            NavigationScreen.VERSION002 -> {
                Version002Screen(
                    onBackPressed = { isMenuFocused ->
                        if (isMenuFocused) {
                            // BACK from menu - return to HOME
                            currentScreen = NavigationScreen.HOME
                            true
                        } else {
                            // BACK from content - let Version002Screen handle (return to menu)
                            false
                        }
                    }
                )
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
            NavigationScreen.VIDEOSLIDER -> {
                VideoSliderScreen()
            }
            NavigationScreen.CHANNELE -> {
                ChanneleScreen()
            }
            NavigationScreen.VERSION001 -> {
                Version001Screen()
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
        }
    }
}
