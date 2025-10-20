package com.uxellence.tv.v3.components

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import com.uxellence.tv.v3.*

/**
 * Unified Focus System for Version003Screen
 * Single focus management system that handles both menu and channel navigation
 */

// Main focus areas in Version003Screen
enum class UnifiedFocus {
    MENU,       // Focus is in the top menu (left menu or right menu)
    CHANNELS    // Focus is in the channel content area
}

// Submenu areas within MENU focus
enum class MenuArea {
    LEFT_MENU,  // Navigation tabs (Start, Moje, etc.)
    RIGHT_MENU  // Icons (notifications, settings) + clock
}

// State for unified focus system
data class UnifiedFocusState(
    val currentFocus: UnifiedFocus = UnifiedFocus.MENU,
    val menuArea: MenuArea = MenuArea.LEFT_MENU,
    val selectedMenuTab: String = "START",
    val focusedMenuTab: String = "START",
    val rightMenuFocusedItem: RightMenuItem? = null,
    val isContentLoaded: Boolean = false
)

// Note: Using RightMenuItem from TopMenuModel.kt

/**
 * Unified Focus Manager for Version003Screen
 */
class UnifiedFocusManager {
    private var _state by mutableStateOf(UnifiedFocusState())
    val state: UnifiedFocusState get() = _state
    
    /**
     * Handle navigation within menu area
     */
    fun handleMenuNavigation(
        key: Key,
        menuItems: List<MenuItem>,
        menuFocusRequesters: Map<String, FocusRequester>,
        rightMenuFocusRequesters: Map<RightMenuItem, FocusRequester>,
        channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>? = null
    ): Boolean {
        when (key) {
            Key.DirectionLeft -> {
                when (_state.menuArea) {
                    MenuArea.LEFT_MENU -> {
                        // Navigate within left menu tabs
                        val currentIndex = menuItems.indexOfFirst { it.id == _state.focusedMenuTab }
                        val newIndex = if (currentIndex > 0) currentIndex - 1 else menuItems.size - 1
                        val newTabId = menuItems[newIndex].id
                        updateFocusedTab(newTabId)
                        menuFocusRequesters[newTabId]?.requestFocus()
                    }
                    MenuArea.RIGHT_MENU -> {
                        // Navigate within right menu or go to left menu
                        when (_state.rightMenuFocusedItem) {
                            RightMenuItem.SETTINGS -> {
                                _state = _state.copy(rightMenuFocusedItem = RightMenuItem.NOTIFICATIONS)
                                rightMenuFocusRequesters[RightMenuItem.NOTIFICATIONS]?.requestFocus()
                            }
                            RightMenuItem.NOTIFICATIONS, null -> {
                                // Go to left menu
                                _state = _state.copy(
                                    menuArea = MenuArea.LEFT_MENU,
                                    rightMenuFocusedItem = null
                                )
                                menuFocusRequesters[_state.focusedMenuTab]?.requestFocus()
                            }
                        }
                    }
                }
                return true
            }
            
            Key.DirectionRight -> {
                when (_state.menuArea) {
                    MenuArea.LEFT_MENU -> {
                        val currentIndex = menuItems.indexOfFirst { it.id == _state.focusedMenuTab }
                        if (currentIndex < menuItems.size - 1) {
                            // Navigate within left menu tabs
                            val newIndex = currentIndex + 1
                            val newTabId = menuItems[newIndex].id
                            updateFocusedTab(newTabId)
                            menuFocusRequesters[newTabId]?.requestFocus()
                        } else {
                            // Go to right menu
                            _state = _state.copy(
                                menuArea = MenuArea.RIGHT_MENU,
                                rightMenuFocusedItem = RightMenuItem.NOTIFICATIONS
                            )
                            rightMenuFocusRequesters[RightMenuItem.NOTIFICATIONS]?.requestFocus()
                        }
                    }
                    MenuArea.RIGHT_MENU -> {
                        // Navigate within right menu
                        when (_state.rightMenuFocusedItem) {
                            RightMenuItem.NOTIFICATIONS -> {
                                _state = _state.copy(rightMenuFocusedItem = RightMenuItem.SETTINGS)
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
                when (_state.menuArea) {
                    MenuArea.LEFT_MENU -> {
                        // Transition from menu to channels
                        selectCurrentTab()
                        transitionToChannels(channelFocusRequesters)
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
     * Transition from menu to channels
     */
    fun transitionToChannels(channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>? = null) {
        _state = _state.copy(
            currentFocus = UnifiedFocus.CHANNELS,
            // Reset menu area and right menu when transitioning to channels
            menuArea = MenuArea.LEFT_MENU, // Reset to default
            rightMenuFocusedItem = null // Clear right menu focus
        )
        // Request focus on first channel's CategoryIcon
        channelFocusRequesters?.get(Pair(0, -1))?.requestFocus()
    }
    
    /**
     * Transition from channels back to menu
     */
    fun transitionToMenu(menuFocusRequesters: Map<String, FocusRequester>) {
        _state = _state.copy(
            currentFocus = UnifiedFocus.MENU,
            menuArea = MenuArea.LEFT_MENU,
            rightMenuFocusedItem = null
        )
        // Request focus on current tab
        menuFocusRequesters[_state.focusedMenuTab]?.requestFocus()
    }
    
    /**
     * Update focused tab in menu
     */
    private fun updateFocusedTab(tabId: String) {
        _state = _state.copy(focusedMenuTab = tabId)
        
        // Auto-select tab after 500ms (same logic as ReusableTopMenu)
        // This would need to be handled by the caller with LaunchedEffect
    }
    
    /**
     * Select current tab and load content
     */
    fun selectCurrentTab() {
        _state = _state.copy(
            selectedMenuTab = _state.focusedMenuTab,
            isContentLoaded = true
        )
    }
    
    /**
     * Check if currently in menu focus
     */
    fun isMenuFocused(): Boolean = _state.currentFocus == UnifiedFocus.MENU
    
    /**
     * Check if currently in channels focus
     */
    fun isChannelsFocused(): Boolean = _state.currentFocus == UnifiedFocus.CHANNELS
    
    /**
     * Get current selected screen
     */
    fun getCurrentScreen(): MenuScreen {
        return when (_state.selectedMenuTab) {
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
    
    /**
     * Reset to initial state
     */
    fun reset() {
        _state = UnifiedFocusState()
    }
}

/**
 * Composable wrapper for unified focus management
 */
@Composable
fun rememberUnifiedFocusManager(): UnifiedFocusManager {
    return remember { UnifiedFocusManager() }
}

// Note: Using MenuItem from TopMenuModel.kt