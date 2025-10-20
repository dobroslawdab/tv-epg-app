package com.uxellence.tv.v3

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.components.*

// Legacy enum for backward compatibility - now replaced by VerticalSection
enum class ContentSection { SLIDER, SHORTCUTS }

/**
 * Version 0.02 Screen
 * Advanced navigation system with hierarchical focus management
 * Supports vertical section navigation (UP/DOWN) separated from horizontal navigation (LEFT/RIGHT)
 */
@Composable
fun Version002Screen(
    onBackPressed: (isMenuFocused: Boolean) -> Boolean = { false }
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Initialize navigation manager for vertical sections
    val navigationManager = rememberVerticalNavigationManager()
    val navState by remember { derivedStateOf { navigationManager.state } }
    
    ReusableTopMenu(
        onBackPressed = onBackPressed,
        onContentKeyEvent = { event ->
            when (event.key) {
                Key.DirectionUp, Key.DirectionDown -> {
                    // Handle vertical section navigation
                    navigationManager.handleVerticalNavigation(event.key)
                }
                Key.DirectionLeft, Key.DirectionRight -> {
                    // Horizontal navigation is handled by individual sections
                    false
                }
                else -> false
            }
        }
    ) { selectedScreen, isMenuFocused ->
        // Set navigation lock during menu focus to prevent conflicts
        LaunchedEffect(isMenuFocused) {
            navigationManager.setNavigationLock(isMenuFocused)
            if (isMenuFocused) {
                // Reset to menu section when menu gains focus
                navigationManager.reset()
            }
        }
        
        // Content based on selected menu tab
        when (selectedScreen) {
            MenuScreen.START -> {
                StartScreenContent(
                    isMenuFocused = isMenuFocused,
                    navigationManager = navigationManager,
                    navState = navState,
                    sx = { sx(it) },
                    sy = { sy(it) }
                )
            }
            
            MenuScreen.MOJE -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // TODO: Integrate ChanneleScreen content
                    Text(
                        "Moje - Content from ChanneleScreen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            MenuScreen.KINO_PLAY -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // TODO: Create channel list with vertical posters from Channel component
                    Text(
                        "Kino Play - Vertical Channel List",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            MenuScreen.SEARCH -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Search - Placeholder",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            MenuScreen.TELEWIZJA -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Telewizja - Placeholder",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            MenuScreen.WIDEO -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Wideo - Placeholder",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            MenuScreen.APLIKACJE -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Aplikacje - Placeholder",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * START screen content with advanced vertical navigation
 */
@Composable
private fun StartScreenContent(
    isMenuFocused: Boolean,
    navigationManager: VerticalNavigationManager,
    navState: VerticalNavigationState,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // Slider Section with smooth positioning
        val sliderY = animateDpAsState(
            targetValue = sy(SectionConfigs.calculateYPosition(
                VerticalSection.SLIDER,
                navigationManager.isSectionActive(VerticalSection.SLIDER)
            )),
            animationSpec = tween(durationMillis = 300),
            label = "sliderY"
        )
        
        SliderSection(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = sliderY.value),
            isSectionFocused = navigationManager.isSectionActive(VerticalSection.SLIDER),
            navigationManager = navigationManager,
            onInternalFocusChange = { focusIndex ->
                // Handle internal focus changes if needed
            }
        )
        
        // Shortcuts Section with smooth positioning
        val shortcutsY = animateDpAsState(
            targetValue = sy(SectionConfigs.calculateYPosition(
                VerticalSection.SHORTCUTS,
                navigationManager.isSectionActive(VerticalSection.SHORTCUTS)
            )),
            animationSpec = tween(durationMillis = 300),
            label = "shortcutsY"
        )
        
        ShortcutsSection(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = shortcutsY.value),
            isSectionFocused = navigationManager.isSectionActive(VerticalSection.SHORTCUTS),
            navigationManager = navigationManager,
            onInternalFocusChange = { focusIndex ->
                // Handle internal focus changes if needed
            }
        )
        
        // Channels Section with smooth positioning
        val channelsY = animateDpAsState(
            targetValue = sy(SectionConfigs.calculateYPosition(
                VerticalSection.CHANNELS,
                navigationManager.isSectionActive(VerticalSection.CHANNELS)
            )),
            animationSpec = tween(durationMillis = 300),
            label = "channelsY"
        )
        
        ChannelsSection(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = channelsY.value),
            isSectionFocused = navigationManager.isSectionActive(VerticalSection.CHANNELS),
            navigationManager = navigationManager,
            onInternalFocusChange = { focusPair ->
                // Handle internal focus changes if needed
            }
        )
        
        // Debug overlay for development (can be removed in production)
        if (false) { // Set to true for debugging
            DebugNavigationOverlay(
                currentSection = navState.currentSection,
                sectionStates = navState.sectionStates,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

/**
 * Debug overlay to show navigation state (for development)
 */
@Composable
private fun DebugNavigationOverlay(
    currentSection: VerticalSection,
    sectionStates: Map<VerticalSection, SectionFocusState>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.8f),
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Navigation Debug",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Current: $currentSection",
            color = Color.Yellow,
            fontSize = 12.sp
        )
        sectionStates.forEach { (section, state) ->
            Text(
                text = "$section: Active=${state.isActive}, Focus=${state.lastInternalFocus}",
                color = if (state.isActive) Color.Green else Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}