package com.example.tv.components

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

/**
 * Hierarchical navigation system for Version002Screen
 * Manages vertical section navigation (UP/DOWN) separately from horizontal navigation (LEFT/RIGHT)
 */

// Vertical sections in hierarchical order
enum class VerticalSection {
    MENU,     // Top menu (ReusableTopMenu)
    SLIDER,   // Large slider section (LargeSliderComponent)
    SHORTCUTS,// Shortcuts section (ShortcutComponent) 
    CHANNELS  // Channels section (ChanneleScreen-like)
}

// Navigation state for each section
data class SectionFocusState(
    val isActive: Boolean = false,
    val lastInternalFocus: Int = 0, // Remember last position within section
    val hasInternalFocus: Boolean = false // Whether section has active internal focus
)

// Main navigation state
data class VerticalNavigationState(
    val currentSection: VerticalSection = VerticalSection.MENU,
    val sectionStates: Map<VerticalSection, SectionFocusState> = mapOf(),
    val isNavigating: Boolean = false // Prevent navigation during animations
)

/**
 * Navigation Manager - Central control for vertical navigation
 */
class VerticalNavigationManager {
    private var _state by mutableStateOf(
        VerticalNavigationState(
            currentSection = VerticalSection.MENU,
            sectionStates = mapOf(
                VerticalSection.MENU to SectionFocusState(isActive = true),
                VerticalSection.SLIDER to SectionFocusState(),
                VerticalSection.SHORTCUTS to SectionFocusState(),
                VerticalSection.CHANNELS to SectionFocusState()
            )
        )
    )
    
    val state: VerticalNavigationState get() = _state

    /**
     * Handle vertical navigation (UP/DOWN keys)
     * Returns true if navigation was handled
     */
    fun handleVerticalNavigation(key: Key): Boolean {
        if (_state.isNavigating) return true // Block during animations
        
        return when (key) {
            Key.DirectionUp -> {
                navigateUp()
                true
            }
            Key.DirectionDown -> {
                navigateDown() 
                true
            }
            else -> false
        }
    }
    
    /**
     * Navigate to previous section
     */
    private fun navigateUp() {
        val sections = VerticalSection.values()
        val currentIndex = sections.indexOf(_state.currentSection)
        
        if (currentIndex > 0) {
            val newSection = sections[currentIndex - 1]
            switchToSection(newSection)
        }
    }
    
    /**
     * Navigate to next section  
     */
    private fun navigateDown() {
        val sections = VerticalSection.values()
        val currentIndex = sections.indexOf(_state.currentSection)
        
        if (currentIndex < sections.size - 1) {
            val newSection = sections[currentIndex + 1]
            switchToSection(newSection)
        }
    }
    
    /**
     * Switch active section and manage focus states
     */
    private fun switchToSection(newSection: VerticalSection) {
        val updatedStates = _state.sectionStates.mapValues { (section, state) ->
            when (section) {
                newSection -> state.copy(isActive = true, hasInternalFocus = true)
                _state.currentSection -> state.copy(isActive = false, hasInternalFocus = false)
                else -> state.copy(isActive = false, hasInternalFocus = false)
            }
        }
        
        _state = _state.copy(
            currentSection = newSection,
            sectionStates = updatedStates
        )
    }
    
    /**
     * Update internal focus state for a section
     */
    fun updateSectionInternalFocus(section: VerticalSection, focusIndex: Int, hasFocus: Boolean) {
        val currentState = _state.sectionStates[section] ?: return
        val updatedState = currentState.copy(
            lastInternalFocus = focusIndex,
            hasInternalFocus = hasFocus
        )
        
        _state = _state.copy(
            sectionStates = _state.sectionStates + (section to updatedState)
        )
    }
    
    /**
     * Check if section is currently active
     */
    fun isSectionActive(section: VerticalSection): Boolean {
        return _state.currentSection == section && 
               (_state.sectionStates[section]?.isActive == true)
    }
    
    /**
     * Get last internal focus position for section
     */
    fun getLastInternalFocus(section: VerticalSection): Int {
        return _state.sectionStates[section]?.lastInternalFocus ?: 0
    }
    
    /**
     * Set navigation lock during animations
     */
    fun setNavigationLock(isLocked: Boolean) {
        _state = _state.copy(isNavigating = isLocked)
    }
    
    /**
     * Reset to initial state
     */
    fun reset() {
        _state = VerticalNavigationState(
            currentSection = VerticalSection.MENU,
            sectionStates = mapOf(
                VerticalSection.MENU to SectionFocusState(isActive = true),
                VerticalSection.SLIDER to SectionFocusState(),
                VerticalSection.SHORTCUTS to SectionFocusState(),
                VerticalSection.CHANNELS to SectionFocusState()
            )
        )
    }
}

/**
 * Composable wrapper for vertical navigation management
 */
@Composable
fun rememberVerticalNavigationManager(): VerticalNavigationManager {
    return remember { VerticalNavigationManager() }
}

/**
 * Section configuration for positioning and animation
 */
data class SectionConfig(
    val section: VerticalSection,
    val baseY: Int, // Base Y position in pixels (1920x1080 baseline)
    val height: Int, // Section height in pixels
    val focusOffsetY: Int = 0, // Y offset when section is focused
    val spacingAfter: Int = 60 // Spacing after this section
)

/**
 * Default section configurations for Version002Screen
 */
object SectionConfigs {
    val MENU = SectionConfig(
        section = VerticalSection.MENU,
        baseY = 0,
        height = 120,
        spacingAfter = 60
    )
    
    val SLIDER = SectionConfig(
        section = VerticalSection.SLIDER, 
        baseY = 120, // After menu + spacing
        height = 742,
        focusOffsetY = -150, // Move up when focused to center on screen
        spacingAfter = 60
    )
    
    val SHORTCUTS = SectionConfig(
        section = VerticalSection.SHORTCUTS,
        baseY = 922, // 120 + 742 + 60
        height = 279,
        focusOffsetY = -300, // Move up significantly to center
        spacingAfter = 60  
    )
    
    val CHANNELS = SectionConfig(
        section = VerticalSection.CHANNELS,
        baseY = 1261, // 922 + 279 + 60
        height = 400, // Variable height for channels
        focusOffsetY = -500 // Move up to show properly
    )
    
    /**
     * Get configuration for section
     */
    fun getConfig(section: VerticalSection): SectionConfig {
        return when (section) {
            VerticalSection.MENU -> MENU
            VerticalSection.SLIDER -> SLIDER
            VerticalSection.SHORTCUTS -> SHORTCUTS
            VerticalSection.CHANNELS -> CHANNELS
        }
    }
    
    /**
     * Calculate Y position for section based on focus state
     */
    fun calculateYPosition(section: VerticalSection, isFocused: Boolean): Int {
        val config = getConfig(section)
        return if (isFocused) {
            config.baseY + config.focusOffsetY
        } else {
            config.baseY
        }
    }
}