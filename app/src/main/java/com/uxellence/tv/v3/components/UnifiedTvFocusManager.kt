package com.uxellence.tv.v3.components

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import com.uxellence.tv.v3.*
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import com.uxellence.tv.v3.version001.*

/**
 * UnifiedTvFocusManager - Single point of truth for focus management
 * 
 * Replaces the chaotic system of 3 competing focus handlers:
 * - MainActivity callbacks
 * - Version003Screen UnifiedFocusSystem  
 * - UnifiedVersion001Content handleChannelNavigation
 *
 * Now there's ONE coordinator that decides who handles which keys
 */

enum class FocusLevel {
    TOP_MENU,        // TopMenuScreen navigation (menu tabs)
    CHANNEL_MENU,    // Focus on channel names (CategoryIcon)
    CHANNEL_CONTENT, // Focus on channel content (LazyRow items)
    CONTENT_DETAILS  // Focus on content details/playback
}

data class TopMenuFocusState(
    val focusedTabId: String = "START",
    val selectedTabId: String = "START",
    val menuArea: MenuArea = MenuArea.LEFT_MENU,
    val rightMenuFocusedItem: RightMenuItem? = null
)

data class ChannelMenuFocusState(
    val focusedChannelIndex: Int = 0,
    val channelNames: List<String> = emptyList()
)

data class ChannelContentFocusState(
    val selectedContentIndex: Int = 0,
    val scrollPosition: Int = 0,
    val lastActiveTime: Long = System.currentTimeMillis(),
    val focusedRowIndex: Int = 0,
    val focusedColIndex: Int = -1 // -1 for CategoryIcon, 0+ for content
)

data class ContentDetailsFocusState(
    val selectedContent: VodContent? = null,
    val isPlaying: Boolean = false
)

class UnifiedTvFocusManager {
    
    // Current focus level state machine
    private var _currentLevel by mutableStateOf(FocusLevel.TOP_MENU)
    val currentLevel: FocusLevel get() = _currentLevel
    
    // State for each level
    private var _topMenuState by mutableStateOf(TopMenuFocusState())
    private var _channelMenuState by mutableStateOf(ChannelMenuFocusState())  
    private var _channelContentState by mutableStateOf(ChannelContentFocusState())
    private var _contentDetailsState by mutableStateOf(ContentDetailsFocusState())
    
    val topMenuState: TopMenuFocusState get() = _topMenuState
    val channelMenuState: ChannelMenuFocusState get() = _channelMenuState
    val channelContentState: ChannelContentFocusState get() = _channelContentState
    val contentDetailsState: ContentDetailsFocusState get() = _contentDetailsState
    
    /**
     * Single entry point for ALL key events in TV navigation
     * Decides which level should handle the key based on current focus level
     */
    fun handleKeyEvent(
        event: KeyEvent,
        menuItems: List<MenuItem>,
        menuFocusRequesters: Map<String, FocusRequester>,
        rightMenuFocusRequesters: Map<RightMenuItem, FocusRequester>,
        channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
        channels: List<String>,
        gridContent: Map<String, List<VodContent>>,
        lazyListStates: Map<Int, LazyListState>,
        coroutineScope: CoroutineScope
    ): Boolean {
        return when (_currentLevel) {
            FocusLevel.TOP_MENU -> handleTopMenuKeys(
                event, menuItems, menuFocusRequesters, rightMenuFocusRequesters
            )
            FocusLevel.CHANNEL_MENU -> handleChannelMenuKeys(
                event, channels, channelFocusRequesters
            )
            FocusLevel.CHANNEL_CONTENT -> handleChannelContentKeys(
                event, channels, channelFocusRequesters, gridContent, lazyListStates, coroutineScope
            )
            FocusLevel.CONTENT_DETAILS -> handleContentDetailsKeys(event)
        }
    }
    
    /**
     * Handle keys when focus is at TOP_MENU level
     */
    private fun handleTopMenuKeys(
        event: KeyEvent,
        menuItems: List<MenuItem>,
        menuFocusRequesters: Map<String, FocusRequester>,
        rightMenuFocusRequesters: Map<RightMenuItem, FocusRequester>
    ): Boolean {
        when (event.key) {
            Key.DirectionLeft -> {
                when (_topMenuState.menuArea) {
                    MenuArea.LEFT_MENU -> {
                        // Navigate within left menu tabs
                        val currentIndex = menuItems.indexOfFirst { it.id == _topMenuState.focusedTabId }
                        val newIndex = if (currentIndex > 0) currentIndex - 1 else menuItems.size - 1
                        val newTabId = menuItems[newIndex].id
                        _topMenuState = _topMenuState.copy(focusedTabId = newTabId)
                        menuFocusRequesters[newTabId]?.requestFocus()
                    }
                    MenuArea.RIGHT_MENU -> {
                        // Navigate within right menu or go to left menu
                        when (_topMenuState.rightMenuFocusedItem) {
                            RightMenuItem.SETTINGS -> {
                                _topMenuState = _topMenuState.copy(rightMenuFocusedItem = RightMenuItem.NOTIFICATIONS)
                                rightMenuFocusRequesters[RightMenuItem.NOTIFICATIONS]?.requestFocus()
                            }
                            RightMenuItem.NOTIFICATIONS, null -> {
                                // Go to left menu
                                _topMenuState = _topMenuState.copy(
                                    menuArea = MenuArea.LEFT_MENU,
                                    rightMenuFocusedItem = null
                                )
                                menuFocusRequesters[_topMenuState.focusedTabId]?.requestFocus()
                            }
                        }
                    }
                }
                return true
            }
            
            Key.DirectionRight -> {
                when (_topMenuState.menuArea) {
                    MenuArea.LEFT_MENU -> {
                        val currentIndex = menuItems.indexOfFirst { it.id == _topMenuState.focusedTabId }
                        if (currentIndex < menuItems.size - 1) {
                            // Navigate within left menu tabs
                            val newIndex = currentIndex + 1
                            val newTabId = menuItems[newIndex].id
                            _topMenuState = _topMenuState.copy(focusedTabId = newTabId)
                            menuFocusRequesters[newTabId]?.requestFocus()
                        } else {
                            // Go to right menu
                            _topMenuState = _topMenuState.copy(
                                menuArea = MenuArea.RIGHT_MENU,
                                rightMenuFocusedItem = RightMenuItem.NOTIFICATIONS
                            )
                            rightMenuFocusRequesters[RightMenuItem.NOTIFICATIONS]?.requestFocus()
                        }
                    }
                    MenuArea.RIGHT_MENU -> {
                        // Navigate within right menu
                        when (_topMenuState.rightMenuFocusedItem) {
                            RightMenuItem.NOTIFICATIONS -> {
                                _topMenuState = _topMenuState.copy(rightMenuFocusedItem = RightMenuItem.SETTINGS)
                                rightMenuFocusRequesters[RightMenuItem.SETTINGS]?.requestFocus()
                            }
                            RightMenuItem.SETTINGS, null -> {
                                // Stay on settings (end of navigation)
                            }
                        }
                    }
                }
                return true
            }
            
            Key.DirectionDown, Key.Enter, Key.DirectionCenter -> {
                when (_topMenuState.menuArea) {
                    MenuArea.LEFT_MENU -> {
                        // Transition to channel level
                        _topMenuState = _topMenuState.copy(selectedTabId = _topMenuState.focusedTabId)
                        switchLevel(FocusLevel.CHANNEL_MENU)
                        return true
                    }
                    MenuArea.RIGHT_MENU -> {
                        // Handle right menu item selection (placeholder)
                        return true
                    }
                }
            }
            
            else -> return false
        }
        return false
    }
    
    /**
     * Handle keys when focus is at CHANNEL_MENU level (CategoryIcon)
     */
    private fun handleChannelMenuKeys(
        event: KeyEvent,
        channels: List<String>,
        channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>
    ): Boolean {
        when (event.key) {
            Key.DirectionUp -> {
                if (_channelContentState.focusedRowIndex > 0) {
                    // Move to previous channel
                    val newRowIndex = _channelContentState.focusedRowIndex - 1
                    _channelContentState = _channelContentState.copy(
                        focusedRowIndex = newRowIndex,
                        focusedColIndex = -1 // Stay on CategoryIcon
                    )
                    channelFocusRequesters[Pair(newRowIndex, -1)]?.requestFocus()
                } else {
                    // Go back to TOP_MENU
                    switchLevel(FocusLevel.TOP_MENU)
                }
                return true
            }
            
            Key.DirectionDown -> {
                if (_channelContentState.focusedRowIndex < channels.size - 1) {
                    // Move to next channel
                    val newRowIndex = _channelContentState.focusedRowIndex + 1
                    _channelContentState = _channelContentState.copy(
                        focusedRowIndex = newRowIndex,
                        focusedColIndex = -1 // Stay on CategoryIcon
                    )
                    channelFocusRequesters[Pair(newRowIndex, -1)]?.requestFocus()
                }
                return true
            }
            
            Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                // Move to channel content
                _channelContentState = _channelContentState.copy(focusedColIndex = 0)
                channelFocusRequesters[Pair(_channelContentState.focusedRowIndex, 0)]?.requestFocus()
                switchLevel(FocusLevel.CHANNEL_CONTENT)
                return true
            }
            
            Key.Back -> {
                // Return to TOP_MENU
                switchLevel(FocusLevel.TOP_MENU)
                return true
            }
            
            else -> return false
        }
        return false
    }
    
    /**
     * Handle keys when focus is at CHANNEL_CONTENT level (LazyRow items)
     */
    private fun handleChannelContentKeys(
        event: KeyEvent,
        channels: List<String>,
        channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
        gridContent: Map<String, List<VodContent>>,
        lazyListStates: Map<Int, LazyListState>,
        coroutineScope: CoroutineScope
    ): Boolean {
        when (event.key) {
            Key.DirectionLeft -> {
                if (_channelContentState.focusedColIndex > 0) {
                    // Move left within content
                    val newColIndex = _channelContentState.focusedColIndex - 1
                    _channelContentState = _channelContentState.copy(focusedColIndex = newColIndex)
                    
                    // Safe access: only request focus if FocusRequester exists and is initialized
                    val focusRequester = channelFocusRequesters[Pair(_channelContentState.focusedRowIndex, newColIndex)]
                    if (focusRequester != null) {
                        try {
                            focusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            // FocusRequester not initialized - ignore silently
                        }
                    }
                } else {
                    // Go back to CategoryIcon
                    _channelContentState = _channelContentState.copy(focusedColIndex = -1)
                    
                    // Safe access: only request focus if FocusRequester exists and is initialized
                    val focusRequester = channelFocusRequesters[Pair(_channelContentState.focusedRowIndex, -1)]
                    if (focusRequester != null) {
                        try {
                            focusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            // FocusRequester not initialized - ignore silently
                        }
                    }
                    switchLevel(FocusLevel.CHANNEL_MENU)
                }
                return true
            }
            
            Key.DirectionRight -> {
                val currentChannel = channels[_channelContentState.focusedRowIndex]
                val maxItems = gridContent[currentChannel]?.size ?: 0
                if (_channelContentState.focusedColIndex < maxItems - 1) {
                    // Move right within content
                    val newColIndex = _channelContentState.focusedColIndex + 1
                    _channelContentState = _channelContentState.copy(focusedColIndex = newColIndex)
                    
                    // Safe access: only request focus if FocusRequester exists and is initialized
                    val focusRequester = channelFocusRequesters[Pair(_channelContentState.focusedRowIndex, newColIndex)]
                    if (focusRequester != null) {
                        try {
                            focusRequester.requestFocus()
                        } catch (e: IllegalStateException) {
                            // FocusRequester not initialized - ignore silently
                        }
                    }
                }
                return true
            }
            
            Key.DirectionUp -> {
                if (_channelContentState.focusedRowIndex > 0) {
                    // Move to same position in previous channel
                    val newRowIndex = _channelContentState.focusedRowIndex - 1
                    _channelContentState = _channelContentState.copy(focusedRowIndex = newRowIndex)
                    channelFocusRequesters[Pair(newRowIndex, _channelContentState.focusedColIndex)]?.requestFocus()
                } else {
                    // Go back to TOP_MENU
                    switchLevel(FocusLevel.TOP_MENU)
                }
                return true
            }
            
            Key.DirectionDown -> {
                if (_channelContentState.focusedRowIndex < channels.size - 1) {
                    // Move to same position in next channel
                    val newRowIndex = _channelContentState.focusedRowIndex + 1
                    _channelContentState = _channelContentState.copy(focusedRowIndex = newRowIndex)
                    channelFocusRequesters[Pair(newRowIndex, _channelContentState.focusedColIndex)]?.requestFocus()
                }
                return true
            }
            
            Key.Enter, Key.DirectionCenter -> {
                // Transition to content details
                val currentChannel = channels[_channelContentState.focusedRowIndex]
                val selectedContent = gridContent[currentChannel]?.getOrNull(_channelContentState.focusedColIndex)
                _contentDetailsState = _contentDetailsState.copy(selectedContent = selectedContent)
                switchLevel(FocusLevel.CONTENT_DETAILS)
                return true
            }
            
            Key.Back -> {
                // Return to TOP_MENU
                switchLevel(FocusLevel.TOP_MENU)
                return true
            }
            
            else -> return false
        }
        return false
    }
    
    /**
     * Handle keys when focus is at CONTENT_DETAILS level
     */
    private fun handleContentDetailsKeys(event: KeyEvent): Boolean {
        when (event.key) {
            Key.Back, Key.DirectionUp -> {
                // Return to channel content
                switchLevel(FocusLevel.CHANNEL_CONTENT)
                return true
            }
            
            Key.Enter, Key.DirectionCenter -> {
                // Start/pause playback
                _contentDetailsState = _contentDetailsState.copy(
                    isPlaying = !_contentDetailsState.isPlaying
                )
                return true
            }
            
            else -> return false
        }
        return false
    }
    
    /**
     * Switch focus level and restore appropriate focus
     */
    fun switchLevel(
        newLevel: FocusLevel,
        menuFocusRequesters: Map<String, FocusRequester>? = null,
        channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>? = null
    ) {
        _currentLevel = newLevel
        
        // Restore focus for the new level
        when (newLevel) {
            FocusLevel.TOP_MENU -> {
                // Reset menu area to left menu and focus on current tab
                _topMenuState = _topMenuState.copy(
                    menuArea = MenuArea.LEFT_MENU,
                    rightMenuFocusedItem = null
                )
                menuFocusRequesters?.get(_topMenuState.focusedTabId)?.requestFocus()
            }
            
            FocusLevel.CHANNEL_MENU -> {
                // Focus on current channel's CategoryIcon
                val rowIndex = _channelContentState.focusedRowIndex
                _channelContentState = _channelContentState.copy(focusedColIndex = -1)
                channelFocusRequesters?.get(Pair(rowIndex, -1))?.requestFocus()
            }
            
            FocusLevel.CHANNEL_CONTENT -> {
                // Focus on current channel content position
                val rowIndex = _channelContentState.focusedRowIndex
                val colIndex = _channelContentState.focusedColIndex
                channelFocusRequesters?.get(Pair(rowIndex, colIndex))?.requestFocus()
            }
            
            FocusLevel.CONTENT_DETAILS -> {
                // Focus handled by content details UI
            }
        }
    }
    
    /**
     * Initialize the focus manager with first channel focus
     */
    fun initializeChannelFocus(channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>) {
        if (_currentLevel == FocusLevel.CHANNEL_MENU) {
            // Focus on first channel's CategoryIcon
            channelFocusRequesters[Pair(0, -1)]?.requestFocus()
        }
    }
    
    /**
     * Check current focus level states
     */
    fun isAtTopMenu(): Boolean = _currentLevel == FocusLevel.TOP_MENU
    fun isAtChannelMenu(): Boolean = _currentLevel == FocusLevel.CHANNEL_MENU
    fun isAtChannelContent(): Boolean = _currentLevel == FocusLevel.CHANNEL_CONTENT
    fun isAtContentDetails(): Boolean = _currentLevel == FocusLevel.CONTENT_DETAILS
    
    /**
     * Get current selected screen based on top menu state
     */
    fun getCurrentScreen(): MenuScreen {
        return when (_topMenuState.selectedTabId) {
            "SEARCH" -> MenuScreen.SEARCH
            "MOJE" -> MenuScreen.MOJE
            "START" -> MenuScreen.START
            "TELEWIZJA" -> MenuScreen.TELEWIZJA
            "KINO_PLAY" -> MenuScreen.KINO_PLAY
            "WIDEO" -> MenuScreen.WIDEO
            "APLIKACJE" -> MenuScreen.APLIKACJE
            else -> MenuScreen.START
        }
    }
}

/**
 * Composable wrapper for unified TV focus management
 */
@Composable
fun rememberUnifiedTvFocusManager(): UnifiedTvFocusManager {
    return remember { UnifiedTvFocusManager() }
}