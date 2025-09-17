package com.example.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tv.ui.theme.figmaRadialBackground
import com.example.tv.version001.loadVodContentFromAssets

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopMenuScreen() {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Menu items data
    val menuItems = remember {
        listOf(
            MenuItem("SEARCH", "Wyszukaj", MenuScreen.SEARCH),
            MenuItem("MOJE", "Moje", MenuScreen.MOJE),
            MenuItem("START", "Start", MenuScreen.START),
            MenuItem("TELEWIZJA", "Telewizja", MenuScreen.TELEWIZJA),
            MenuItem("KINO_PLAY", "Kino Play", MenuScreen.KINO_PLAY),
            MenuItem("WIDEO", "Wideo", MenuScreen.WIDEO),
            MenuItem("APLIKACJE", "Aplikacje", MenuScreen.APLIKACJE)
        )
    }

    var menuState by remember { 
        mutableStateOf(TopMenuState(focusedItemId = "START", selectedItemId = "START", isMenuFocused = true))
    }
    
    // Loading states for each section
    var isContentLoading by remember { mutableStateOf(false) }
    var contentShouldFocus by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(menuItems.size) { 
        menuItems.associate { it.id to FocusRequester() } 
    }

    // Auto content loading without losing menu focus
    LaunchedEffect(menuState.focusedItemId) {
        if (menuState.isMenuFocused) {
            delay(500) // 500ms delay before switching screen
            if (menuState.focusedItemId == menuState.focusedItemId) { // Still the same focus
                isContentLoading = true
                delay(300) // Show preloader for 300ms
                menuState = menuState.copy(
                    selectedItemId = menuState.focusedItemId
                    // Keep isMenuFocused = true for preview mode
                )
                isContentLoading = false
            }
        }
    }

    // Focus on focused item when returning from content (preserves menu position)
    LaunchedEffect(menuState.isMenuFocused) {
        if (menuState.isMenuFocused) {
            focusRequesters[menuState.focusedItemId]?.requestFocus()
        }
    }

    // Debug overlay
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(8.dp)
        ) {
            Text(
                text = "DEBUG INFO:",
                color = Color.Yellow,
                fontSize = 12.sp
            )
            Text(
                text = "Menu focused: ${menuState.isMenuFocused}",
                color = Color.White,
                fontSize = 10.sp
            )
            Text(
                text = "Selected: ${menuState.selectedItemId}",
                color = Color.White,
                fontSize = 10.sp
            )
            Text(
                text = "Focused: ${menuState.focusedItemId}",
                color = Color.White,
                fontSize = 10.sp
            )
            Text(
                text = "Content should focus: $contentShouldFocus",
                color = Color.White,
                fontSize = 10.sp
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                when {
                    menuState.isMenuFocused -> {
                        // Menu navigation
                        when (event.key) {
                            Key.DirectionLeft -> {
                                val currentIndex = menuItems.indexOfFirst { it.id == menuState.focusedItemId }
                                val newIndex = if (currentIndex > 0) currentIndex - 1 else menuItems.size - 1
                                menuState = menuState.copy(focusedItemId = menuItems[newIndex].id)
                                true
                            }
                            Key.DirectionRight -> {
                                val currentIndex = menuItems.indexOfFirst { it.id == menuState.focusedItemId }
                                val newIndex = if (currentIndex < menuItems.size - 1) currentIndex + 1 else 0
                                menuState = menuState.copy(focusedItemId = menuItems[newIndex].id)
                                true
                            }
                            Key.DirectionDown, Key.Enter, Key.DirectionCenter -> {
                                // Select current item and move focus to content ONLY when user presses DOWN/OK
                                menuState = menuState.copy(
                                    selectedItemId = menuState.focusedItemId,
                                    isMenuFocused = false // Menu loses focus only when user explicitly navigates down
                                )
                                contentShouldFocus = true // Activate content focus
                                true
                            }
                            else -> false
                        }
                    }
                    else -> {
                        // Content area - handle BACK and UP to return to menu
                        when (event.key) {
                            Key.Back, Key.DirectionUp -> {
                                // Return focus to menu and reset content focus
                                menuState = menuState.copy(isMenuFocused = true)
                                contentShouldFocus = false
                                true
                            }
                            else -> false // Let content handle other keys
                        }
                    }
                }
            }
    ) {
        // Top Menu Bar
        TopMenuBar(
            menuItems = menuItems,
            menuState = menuState,
            focusRequesters = focusRequesters,
            onMenuItemFocused = { itemId ->
                menuState = menuState.copy(focusedItemId = itemId)
            },
            sx = { sx(it) },
            sy = { sy(it) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Content Area (below menu)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sy(120)) // Space for menu
        ) {
            if (isContentLoading) {
                // Preloader
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF5AECD3), // Aqua color
                        strokeWidth = sy(4).value.dp,
                        modifier = Modifier.size(sx(60), sy(60))
                    )
                }
            } else {
                when (MenuScreen.valueOf(menuState.selectedItemId)) {
                    MenuScreen.SEARCH -> SearchContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                    MenuScreen.MOJE -> MojeContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                    MenuScreen.START -> StartContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                    MenuScreen.TELEWIZJA -> TelewizjaContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                    MenuScreen.KINO_PLAY -> KinoPlayContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                    MenuScreen.WIDEO -> WideoContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                    MenuScreen.APLIKACJE -> AplikacjeContentScreen(
                        onReturnToMenu = {
                            menuState = menuState.copy(isMenuFocused = true)
                            contentShouldFocus = false
                        },
                        shouldAutoFocus = contentShouldFocus
                    )
                }
            }
        }
    }
}

@Composable
private fun TopMenuBar(
    menuItems: List<MenuItem>,
    menuState: TopMenuState,
    focusRequesters: Map<String, FocusRequester>,
    onMenuItemFocused: (String) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(top = sy(20))
            .width(sx(1077))
            .height(sy(97))
            .background(
                color = Color(0x4A000000), // rgba(0,0,0,0.29)
                shape = RoundedCornerShape(sx(49)) // 48.5px rounded
            )
            .padding(sx(9)) // 9px internal padding
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(sx(13)), // 13px gap
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(menuItems) { index, item ->
                if (index == 0) {
                    // Search Icon (first item)
                    MenuSearchIcon(
                        isSelected = menuState.selectedItemId == item.id,
                        isFocused = menuState.focusedItemId == item.id && menuState.isMenuFocused,
                        focusRequester = focusRequesters[item.id]!!,
                        onFocused = { onMenuItemFocused(item.id) },
                        sx = sx,
                        sy = sy
                    )
                } else {
                    // Menu Button
                    MenuButton(
                        title = item.title,
                        isSelected = menuState.selectedItemId == item.id,
                        isFocused = menuState.focusedItemId == item.id && menuState.isMenuFocused,
                        focusRequester = focusRequesters[item.id]!!,
                        onFocused = { onMenuItemFocused(item.id) },
                        sx = sx,
                        sy = sy
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuSearchIcon(
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val backgroundColor = when {
        isFocused -> Color(0xFF5AECD3) // Focus: aqua
        isSelected -> Color.White // Selected: white
        else -> Color(0x08FFFFFF) // Normal: rgba(255,255,255,0.03)
    }
    
    val iconColor = when {
        isFocused -> Color(0xFF48227C) // Focus: purple text
        isSelected -> Color(0xFF48227C) // Selected: dark text
        else -> Color(0xFFEEEEEE) // Normal: light text
    }

    Box(
        modifier = Modifier
            .size(sx(80), sy(80)) // 80x80px
            .background(
                color = backgroundColor,
                shape = CircleShape // radius 64px (circular)
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Wyszukaj",
            tint = iconColor,
            modifier = Modifier.size(sx(48), sy(48)) // 48x48px icon
        )
    }
}

@Composable
private fun MenuButton(
    title: String,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val backgroundColor = when {
        isFocused -> Color(0xFF5AECD3) // Focus: aqua #5AECD3
        isSelected -> Color.White // Selected: white
        else -> Color(0x0DEEEEEE) // Normal: rgba(238,238,238,0.05)
    }
    
    val textColor = when {
        isFocused -> Color(0xFF48227C) // Focus: purple #48227C
        isSelected -> Color(0xFF48227C) // Selected: dark text
        else -> Color(0xFFEEEEEE) // Normal: light text #EEEEEE
    }

    Box(
        modifier = Modifier
            .height(sy(80)) // 80px height
            .background(
                color = backgroundColor,
                shape = CircleShape // radius 64px
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .padding(horizontal = sx(32)), // 32px horizontal padding
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = sy(24).value.sp, // 24px font size
            fontWeight = FontWeight.Medium, // 500 weight
            lineHeight = sy(32).value.sp, // 32px line height
            letterSpacing = 0.48.sp, // 0.48px letter spacing
            textAlign = TextAlign.Center
        )
    }
}

// Focusable content card component - styled like shortcuts
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusableContentCard(
    title: String,
    onReturnToMenu: () -> Unit,
    shouldAutoFocus: Boolean = false // Control auto-focus behavior
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp
    
    val focusRequester = remember { FocusRequester() }
    // Force focus state when shouldAutoFocus is true
    var isFocused by remember { mutableStateOf(false) }
    
    // Set focus state when shouldAutoFocus changes
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            isFocused = true // Set visual focus state
            // Also try FocusRequester for proper focus management
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        } else {
            isFocused = false // Reset when not auto-focusing
        }
    }
    
    // Remove debug overlay when everything works
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && isFocused) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onReturnToMenu()
                            true
                        }
                        Key.Back -> {
                            onReturnToMenu()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // Styled like ShortcutCard
        val cardWidth = sx(210)
        val cardHeight = sy(279)
        val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
        
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .border(
                    width = (6 * sx(1).value / 1.dp.value).dp, // Same border width as shortcuts
                    color = borderColor,
                    shape = RoundedCornerShape(sx(12))
                )
                .clip(RoundedCornerShape(sx(12)))
                .background(Color(0x33000000)) // Same background as shortcuts
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    // Update focus state when system focus changes
                    if (!shouldAutoFocus) { // Only update if not in manual mode
                        isFocused = focusState.isFocused
                    }
                }
        ) {
            // Content background - darker when focused (like shortcuts)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isFocused) Color(0x4D000000) else Color.Transparent
                    )
            ) {
                // Icon area - placeholder circle like in shortcuts
                Box(
                    modifier = Modifier
                        .size(sx(85)) // Same as shortcut icon size
                        .align(Alignment.Center)
                        .offset(y = sy(-45))
                        .background(
                            color = Color(0xFFEEEEEE),
                            shape = RoundedCornerShape(sx(42))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2), // First 2 letters as icon
                        color = Color(0xFF48227C),
                        fontSize = sy(24).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Text at bottom (like shortcuts)
                Text(
                    text = "$title Content",
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(16).value.sp, // Shortcut font size
                    fontWeight = FontWeight.W500,
                    textAlign = TextAlign.Center,
                    lineHeight = sy(20).value.sp,
                    letterSpacing = 0.32.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = sx(12), vertical = sy(16))
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StartContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) {
    FocusableContentCard(
        title = "START",
        onReturnToMenu = onReturnToMenu,
        shouldAutoFocus = shouldAutoFocus
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ShortcutsComponent(isSectionFocused: Boolean) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Lista skrótów (5 elementów z Lottie i SVG)
    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "Moja lista kanałów", ShortcutIcon.LottieIcon("tvaa.lottie")),
            ShortcutItem("2", "Nagrania", ShortcutIcon.LottieIcon("nagrania.lottie")),
            ShortcutItem("3", "Wypożyczone", ShortcutIcon.LottieIcon("wypozyczone.lottie")),
            ShortcutItem("4", "Disney Plus", ShortcutIcon.VectorIcon(R.drawable.disney_plus_icon)),
            ShortcutItem("5", "Do obejrzenia", ShortcutIcon.LottieIcon("doobejzenia.lottie"))
        )
    }
    
    var focusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(shortcuts.size) { List(shortcuts.size) { FocusRequester() } }

    // Only manage internal focus when this section is focused
    LaunchedEffect(focusedIndex, isSectionFocused) {
        if (isSectionFocused && focusedIndex >= 0 && focusedIndex < shortcuts.size) {
            scope.launch { 
                listState.animateScrollToItem(focusedIndex)
                focusRequesters[focusedIndex].requestFocus()
            }
        }
    }

    // Debug info for shortcuts
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Red.copy(alpha = 0.2f)) // Debug background
    ) {
        // Debug text for shortcuts
        Text(
            "SHORTCUTS SECTION - isSectionFocused: $isSectionFocused", 
            color = Color.White,
            fontSize = sy(12).value.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(sy(5))
        )
    }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = sx(50)),
            horizontalArrangement = Arrangement.spacedBy(sx(20)),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (!isSectionFocused || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    val newIndex = when (event.key) {
                        Key.DirectionLeft -> if (focusedIndex > 0) focusedIndex - 1 else focusedIndex
                        Key.DirectionRight -> if (focusedIndex < shortcuts.size - 1) focusedIndex + 1 else focusedIndex
                        else -> focusedIndex
                    }

                    if (newIndex != focusedIndex) {
                        focusedIndex = newIndex
                        return@onPreviewKeyEvent true
                    }

                    false
                },
            userScrollEnabled = false
        ) {
            itemsIndexed(shortcuts) { index, shortcut ->
                ShortcutCard(
                    shortcut = shortcut,
                    isFocused = index == focusedIndex && isSectionFocused,
                    focusRequester = focusRequesters[index],
                    sx = { sx(it) },
                    sy = { sy(it) }
                )
            }
        }

        // Confetti animation positioned over Disney Plus (index 3)
        if (focusedIndex == 3 && isSectionFocused) {
            val confettiComposition by rememberLottieComposition(LottieCompositionSpec.Asset("confetti.lottie"))
            val confettiProgress by animateLottieCompositionAsState(
                composition = confettiComposition,
                isPlaying = true,
                restartOnPlay = true,
                iterations = 1
            )
            
            val disneyPlusIndex = 3
            val cardWidth = sx(210)
            val spacing = sx(20)
            val horizontalPadding = sx(50)
            val disneyPlusX = horizontalPadding + (cardWidth + spacing) * disneyPlusIndex + cardWidth / 2
            
            LottieAnimation(
                composition = confettiComposition,
                progress = { confettiProgress },
                modifier = Modifier
                    .size(sx(840), sy(1116))
                    .align(Alignment.TopStart)
                    .offset(
                        x = disneyPlusX - sx(420),
                        y = sy(26)
                    )
                    .zIndex(10f)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SliderComponentScreen(isSectionFocused: Boolean) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val movies = remember { loadVodContentFromAssets(context) }
    var focusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(movies.size) { List(movies.size) { FocusRequester() } }

    // Focus on first slide when section becomes focused
    LaunchedEffect(focusedIndex, isSectionFocused) {
        if (isSectionFocused && focusedIndex >= 0 && focusedIndex < movies.size) {
            scope.launch { 
                listState.animateScrollToItem(focusedIndex)
                focusRequesters[focusedIndex].requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!isSectionFocused || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                val newIndex = when (event.key) {
                    Key.DirectionLeft -> if (focusedIndex > 0) focusedIndex - 1 else focusedIndex
                    Key.DirectionRight -> if (focusedIndex < movies.size - 1) focusedIndex + 1 else focusedIndex
                    else -> focusedIndex
                }

                if (newIndex != focusedIndex) {
                    focusedIndex = newIndex
                    return@onPreviewKeyEvent true
                }

                false
            }
    ) {
        if (movies.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = sx(50)),
                horizontalArrangement = Arrangement.spacedBy(sx(20)),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false
            ) {
                itemsIndexed(movies) { index, movie ->
                    MovieCard(
                        movie = movie,
                        isFocused = index == focusedIndex && isSectionFocused,
                        focusRequester = focusRequesters[index],
                        onFocusChanged = { isFocused ->
                            if (isFocused && isSectionFocused) focusedIndex = index
                        },
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
        }
    }
}

enum class StartFocusLevel {
    MENU, SLIDER, SHORTCUTS
}

// Content screens with focusable elements and return to menu capability
@Composable 
private fun SearchContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) = FocusableContentCard(
    title = "SEARCH",
    onReturnToMenu = onReturnToMenu,
    shouldAutoFocus = shouldAutoFocus
)

@Composable 
private fun MojeContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) = FocusableContentCard(
    title = "MOJE",
    onReturnToMenu = onReturnToMenu,
    shouldAutoFocus = shouldAutoFocus
)

@Composable 
private fun TelewizjaContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) = FocusableContentCard(
    title = "TELEWIZJA",
    onReturnToMenu = onReturnToMenu,
    shouldAutoFocus = shouldAutoFocus
)

@Composable 
private fun KinoPlayContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) = FocusableContentCard(
    title = "KINO PLAY",
    onReturnToMenu = onReturnToMenu,
    shouldAutoFocus = shouldAutoFocus
)

@Composable 
private fun WideoContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) = FocusableContentCard(
    title = "WIDEO",
    onReturnToMenu = onReturnToMenu,
    shouldAutoFocus = shouldAutoFocus
)

@Composable 
private fun AplikacjeContentScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false
) = FocusableContentCard(
    title = "APLIKACJE",
    onReturnToMenu = onReturnToMenu,
    shouldAutoFocus = shouldAutoFocus
)