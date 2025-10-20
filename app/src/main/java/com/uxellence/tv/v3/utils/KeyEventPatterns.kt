package com.uxellence.tv.v3.utils

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp

/**
 * Standard Key Event Patterns for Android TV App
 * 
 * This file contains reusable patterns for common key event scenarios.
 * Follow these patterns to ensure consistency and prevent conflicts.
 */

/**
 * Pattern 1: Global Navigation (MainActivity Level)
 * 
 * Use this pattern for app-wide navigation that affects screen transitions.
 * Coordinates with child screens via callbacks to avoid conflicts.
 */
@Composable
fun GlobalNavigationKeyHandler(
    onBackToHome: () -> Boolean,
    content: @Composable () -> Unit
) {
    RegisterKeyHandler(
        id = "MainActivity.GlobalNavigation",
        priority = KeyEventManager.Priority.GLOBAL,
        keys = setOf(Key.Back),
        description = "Global app navigation - back to home screen"
    ) { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            onBackToHome()
        } else {
            false
        }
    }
    
    content()
}

/**
 * Pattern 2: Screen-Level Navigation
 * 
 * Use this pattern for navigation within a screen (e.g., menu ↔ content).
 * Coordinates with parent (MainActivity) via callbacks.
 */
@Composable
fun ScreenNavigationKeyHandler(
    screenId: String,
    onBackPressed: (String) -> Boolean,
    keys: Set<Key> = setOf(Key.Back, Key.DirectionUp, Key.DirectionDown),
    localKeyHandler: ((KeyEvent) -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    RegisterKeyHandler(
        id = "$screenId.ScreenNavigation", 
        priority = KeyEventManager.Priority.SCREEN,
        keys = keys,
        description = "Screen navigation for $screenId"
    ) { event ->
        if (event.type != KeyEventType.KeyDown) return@RegisterKeyHandler false
        
        // Try parent coordination first
        if (event.key == Key.Back) {
            val handled = onBackPressed(screenId)
            if (handled) return@RegisterKeyHandler true
        }
        
        // Handle locally if parent didn't consume
        localKeyHandler?.invoke(event) ?: false
    }
    
    content()
}

/**
 * Pattern 3: Component-Level Navigation
 * 
 * Use this pattern for individual component interactions (lists, grids, etc.).
 * Never handles BACK - always delegates to parent level.
 */
@Composable
fun ComponentNavigationKeyHandler(
    componentId: String,
    keys: Set<Key> = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown),
    onKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable () -> Unit
) {
    RegisterKeyHandler(
        id = "$componentId.ComponentNavigation",
        priority = KeyEventManager.Priority.COMPONENT,
        keys = keys,
        description = "Component navigation for $componentId"
    ) { event ->
        if (event.type == KeyEventType.KeyDown && event.key in keys) {
            onKeyEvent(event)
        } else {
            false
        }
    }
    
    content()
}

/**
 * Pattern 4: Widget-Level Micro-Interactions
 * 
 * Use this pattern for fine-grained control within widgets.
 * Typically handles only directional keys and Enter.
 */
@Composable
fun WidgetKeyHandler(
    widgetId: String,
    onLeftRight: ((isRight: Boolean) -> Boolean)? = null,
    onUpDown: ((isDown: Boolean) -> Boolean)? = null,
    onEnter: (() -> Boolean)? = null,
    content: @Composable () -> Unit
) {
    val keys = buildSet {
        if (onLeftRight != null) {
            add(Key.DirectionLeft)
            add(Key.DirectionRight)
        }
        if (onUpDown != null) {
            add(Key.DirectionUp)
            add(Key.DirectionDown)
        }
        if (onEnter != null) {
            add(Key.Enter)
            add(Key.DirectionCenter)
        }
    }
    
    if (keys.isNotEmpty()) {
        RegisterKeyHandler(
            id = "$widgetId.Widget",
            priority = KeyEventManager.Priority.WIDGET,
            keys = keys,
            description = "Widget interactions for $widgetId"
        ) { event ->
            if (event.type != KeyEventType.KeyDown) return@RegisterKeyHandler false
            
            when (event.key) {
                Key.DirectionLeft -> onLeftRight?.invoke(false) ?: false
                Key.DirectionRight -> onLeftRight?.invoke(true) ?: false
                Key.DirectionUp -> onUpDown?.invoke(false) ?: false
                Key.DirectionDown -> onUpDown?.invoke(true) ?: false
                Key.Enter, Key.DirectionCenter -> onEnter?.invoke() ?: false
                else -> false
            }
        }
    }
    
    content()
}

/**
 * Modifier Extension: Safe Key Event Handling
 * 
 * Use this modifier when you need traditional onPreviewKeyEvent behavior
 * but want to ensure it's documented and doesn't conflict with the central system.
 */
fun Modifier.safeKeyEventHandler(
    componentId: String,
    priority: KeyEventManager.Priority,
    keys: Set<Key>,
    description: String,
    handler: (KeyEvent) -> Boolean
): Modifier {
    return this.then(
        Modifier.onPreviewKeyEvent { event ->
            // Log the event for debugging
            android.util.Log.d("SafeKeyHandler", "$componentId: Processing ${event.key}")
            
            if (event.key in keys) {
                val result = handler(event)
                android.util.Log.d("SafeKeyHandler", "$componentId: ${event.key} -> $result")
                result
            } else {
                false
            }
        }
    )
}

/**
 * Helper: Back Key Delegation Pattern
 * 
 * Standard pattern for handling BACK key with parent coordination.
 * Use this in Screen-level components that need to coordinate with MainActivity.
 */
class BackKeyCoordinator(
    private val onBackPressed: (isAtTopLevel: Boolean) -> Boolean
) {
    fun handleBack(isAtTopLevel: Boolean): Boolean {
        return onBackPressed(isAtTopLevel)
    }
    
    fun createLocalHandler(
        onLocalBack: () -> Unit
    ): (KeyEvent) -> Boolean = { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
            val handled = handleBack(isAtTopLevel = false)
            if (!handled) {
                onLocalBack()
                true
            } else {
                handled
            }
        } else {
            false
        }
    }
}

/**
 * Composable: Safe Navigation Wrapper
 * 
 * Wrap your navigation-heavy components with this to ensure proper key handling.
 * Automatically registers/unregisters handlers and provides debugging.
 */
@Composable
fun SafeNavigationScope(
    scopeId: String,
    priority: KeyEventManager.Priority,
    keys: Set<Key>,
    onKeyEvent: (KeyEvent) -> Boolean,
    showDebugOverlay: Boolean = false,
    content: @Composable () -> Unit
) {
    RegisterKeyHandler(
        id = scopeId,
        priority = priority,
        keys = keys,
        description = "Safe navigation scope: $scopeId"
    ) { event ->
        onKeyEvent(event)
    }
    
    Box {
        content()
        
        if (showDebugOverlay) {
            KeyEventDebugOverlay(
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * Utility: Common Key Sets
 */
object CommonKeySets {
    val BACK_ONLY = setOf(Key.Back)
    val DIRECTIONAL = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
    val DIRECTIONAL_AND_ENTER = DIRECTIONAL + setOf(Key.Enter, Key.DirectionCenter)
    val NAVIGATION_FULL = setOf(Key.Back, Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
    val MENU_CONTROL = setOf(Key.Back, Key.DirectionUp, Key.DirectionDown, Key.Enter, Key.DirectionCenter)
}

/**
 * Builder: Fluent Key Handler Configuration
 */
class KeyHandlerBuilder(private val componentId: String) {
    private var priority: KeyEventManager.Priority = KeyEventManager.Priority.COMPONENT
    private var keys: Set<Key> = emptySet()
    private var description: String = ""
    private var handler: ((KeyEvent) -> Boolean)? = null
    
    fun withPriority(priority: KeyEventManager.Priority) = apply { this.priority = priority }
    fun withKeys(keys: Set<Key>) = apply { this.keys = keys }
    fun withDescription(description: String) = apply { this.description = description }
    fun withHandler(handler: (KeyEvent) -> Boolean) = apply { this.handler = handler }
    
    @Composable
    fun Register(content: @Composable () -> Unit) {
        requireNotNull(handler) { "Handler must be set" }
        require(keys.isNotEmpty()) { "Keys must be set" }
        require(description.isNotEmpty()) { "Description must be set" }
        
        RegisterKeyHandler(
            id = componentId,
            priority = priority,
            keys = keys,
            description = description,
            handler = handler!!
        )
        
        content()
    }
}

/**
 * DSL Entry Point
 */
fun keyHandler(componentId: String): KeyHandlerBuilder = KeyHandlerBuilder(componentId)

