package com.example.tv.components

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.tv.ui.theme.figmaRadialBackground
import com.example.tv.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Reusable TopMenu Component
 * Can be used across different screens to provide consistent navigation
 * 
 * @param onBackPressed Callback for back key handling
 * @param content Lambda that provides the content based on selected menu screen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReusableTopMenu(
    onBackPressed: (isMenuFocused: Boolean) -> Boolean = { false },
    onContentKeyEvent: ((KeyEvent) -> Boolean)? = null,
    content: @Composable (MenuScreen, Boolean) -> Unit
) {
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // Handle BACK key first - check with callback
                if (event.key == Key.Back) {
                    val handled = onBackPressed(menuState.isMenuFocused)
                    if (!handled && !menuState.isMenuFocused) {
                        // BACK from content - return to menu
                        menuState = menuState.copy(
                            isMenuFocused = true,
                            focusedArea = FocusArea.LEFT_MENU // Always return to left menu
                        )
                        contentShouldFocus = false
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent handled
                }

                when {
                    menuState.isMenuFocused -> {
                        // Menu navigation
                        when (event.key) {
                            Key.DirectionLeft -> {
                                when (menuState.focusedArea) {
                                    FocusArea.LEFT_MENU -> {
                                        // Navigate within left menu
                                        val currentIndex = menuItems.indexOfFirst { it.id == menuState.focusedItemId }
                                        val newIndex = if (currentIndex > 0) currentIndex - 1 else menuItems.size - 1
                                        menuState = menuState.copy(focusedItemId = menuItems[newIndex].id)
                                    }
                                    FocusArea.RIGHT_MENU -> {
                                        // Navigate within right menu or go to left menu
                                        when (menuState.rightMenuFocusedItem) {
                                            RightMenuItem.SETTINGS -> {
                                                // From settings to notifications
                                                menuState = menuState.copy(rightMenuFocusedItem = RightMenuItem.NOTIFICATIONS)
                                            }
                                            RightMenuItem.NOTIFICATIONS, null -> {
                                                // From notifications to left menu
                                                menuState = menuState.copy(
                                                    focusedArea = FocusArea.LEFT_MENU,
                                                    rightMenuFocusedItem = null
                                                )
                                            }
                                        }
                                    }
                                    else -> { /* No action for CONTENT */ }
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                when (menuState.focusedArea) {
                                    FocusArea.LEFT_MENU -> {
                                        val currentIndex = menuItems.indexOfFirst { it.id == menuState.focusedItemId }
                                        if (currentIndex < menuItems.size - 1) {
                                            // Navigate within left menu
                                            val newIndex = currentIndex + 1
                                            menuState = menuState.copy(focusedItemId = menuItems[newIndex].id)
                                        } else {
                                            // From last left menu item to right menu
                                            menuState = menuState.copy(
                                                focusedArea = FocusArea.RIGHT_MENU,
                                                rightMenuFocusedItem = RightMenuItem.NOTIFICATIONS
                                            )
                                        }
                                    }
                                    FocusArea.RIGHT_MENU -> {
                                        // Navigate within right menu
                                        when (menuState.rightMenuFocusedItem) {
                                            RightMenuItem.NOTIFICATIONS -> {
                                                menuState = menuState.copy(rightMenuFocusedItem = RightMenuItem.SETTINGS)
                                            }
                                            RightMenuItem.SETTINGS, null -> {
                                                // Stay on settings (end of navigation)
                                            }
                                        }
                                    }
                                    else -> { /* No action for CONTENT */ }
                                }
                                true
                            }
                            Key.DirectionDown, Key.Enter, Key.DirectionCenter -> {
                                when (menuState.focusedArea) {
                                    FocusArea.LEFT_MENU -> {
                                        // Select current item and move focus to content ONLY when user presses DOWN/OK
                                        menuState = menuState.copy(
                                            selectedItemId = menuState.focusedItemId,
                                            isMenuFocused = false,
                                            focusedArea = FocusArea.CONTENT
                                        )
                                        contentShouldFocus = true // Activate content focus
                                    }
                                    FocusArea.RIGHT_MENU -> {
                                        // Handle right menu item selection
                                        when (menuState.rightMenuFocusedItem) {
                                            RightMenuItem.NOTIFICATIONS -> {
                                                // Open notifications (placeholder)
                                            }
                                            RightMenuItem.SETTINGS -> {
                                                // Open settings (placeholder)
                                            }
                                            else -> {}
                                        }
                                    }
                                    else -> {}
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    else -> {
                        // Content navigation - delegate to content
                        when (event.key) {
                            Key.DirectionUp, Key.Back -> {
                                // BACK/UP pressed - returning to menu
                                menuState = menuState.copy(
                                    isMenuFocused = true,
                                    focusedArea = FocusArea.LEFT_MENU // Always return to left menu
                                )
                                contentShouldFocus = false
                                true
                            }
                            else -> {
                                // Delegate to content key handler
                                onContentKeyEvent?.invoke(event) ?: false
                            }
                        }
                    }
                }
            }
    ) {
        // Top Menu Bar (Left) - using private function from TopMenuScreen
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
        
        // Right Menu Bar (Icons + Clock) - using private function from TopMenuScreen
        RightMenuBar(
            menuState = menuState,
            onRightMenuItemFocused = { rightMenuItem ->
                menuState = menuState.copy(
                    focusedArea = if (rightMenuItem != null) FocusArea.RIGHT_MENU else FocusArea.LEFT_MENU,
                    rightMenuFocusedItem = rightMenuItem
                )
            },
            sx = { sx(it) },
            sy = { sy(it) },
            modifier = Modifier.align(Alignment.TopEnd)
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
                // Use provided content lambda with selected screen and menu focus state
                content(MenuScreen.valueOf(menuState.selectedItemId), menuState.isMenuFocused)
            }
        }
    }
}