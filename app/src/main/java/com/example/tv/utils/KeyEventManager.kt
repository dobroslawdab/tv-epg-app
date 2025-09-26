package com.example.tv.utils

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central Key Event Management System
 * 
 * Prevents conflicts by providing a single source of truth for key event handling.
 * Implements hierarchical delegation pattern to coordinate between multiple components.
 * 
 * Usage:
 * 1. Register handlers with priority levels
 * 2. System automatically routes events to highest priority handler
 * 3. Handlers can delegate to lower priority handlers
 */
object KeyEventManager {
    
    private const val TAG = "KeyEventManager"
    
    enum class Priority(val level: Int) {
        GLOBAL(100),      // MainActivity - app-wide navigation
        SCREEN(50),       // Screen components - screen-specific logic  
        COMPONENT(25),    // Individual components - local interactions
        WIDGET(10)        // Micro-widgets - fine-grained control
    }
    
    data class KeyHandler(
        val id: String,
        val priority: Priority,
        val keys: Set<Key>,
        val description: String,
        val handler: (KeyEvent) -> Boolean
    )
    
    private val _registeredHandlers = mutableStateListOf<KeyHandler>()
    val registeredHandlers: List<KeyHandler> = _registeredHandlers
    
    /**
     * Register a key event handler with priority
     * 
     * @param id Unique identifier for debugging (e.g., "MainActivity.back", "TopMenu.navigation")
     * @param priority Handler priority level
     * @param keys Set of keys this handler manages
     * @param description Human-readable description for debugging
     * @param handler Function returning true if event was consumed
     */
    fun registerHandler(
        id: String,
        priority: Priority,
        keys: Set<Key>,
        description: String,
        handler: (KeyEvent) -> Boolean
    ) {
        // Prevent duplicate registrations
        unregisterHandler(id)
        
        val keyHandler = KeyHandler(id, priority, keys, description, handler)
        _registeredHandlers.add(keyHandler)
        _registeredHandlers.sortByDescending { it.priority.level }
        
        Log.d(TAG, "Registered handler: $id (${priority.name}) for keys: ${keys.map { it.keyCode }}")
        logCurrentHandlers()
    }
    
    /**
     * Unregister a key event handler
     */
    fun unregisterHandler(id: String) {
        val removed = _registeredHandlers.removeAll { it.id == id }
        if (removed) {
            Log.d(TAG, "Unregistered handler: $id")
            logCurrentHandlers()
        }
    }
    
    /**
     * Process key event through registered handlers
     * Returns true if event was consumed by any handler
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val relevantHandlers = _registeredHandlers.filter { handler ->
            handler.keys.contains(event.key)
        }
        
        Log.d(TAG, "Processing key through ${relevantHandlers.size} handlers")
        
        for (handler in relevantHandlers) {
            Log.d(TAG, "Trying handler: ${handler.id} (${handler.priority.name})")
            
            val consumed = try {
                handler.handler(event)
            } catch (e: Exception) {
                Log.e(TAG, "Handler ${handler.id} threw exception", e)
                false
            }
            
            if (consumed) {
                Log.d(TAG, "Event consumed by: ${handler.id}")
                return true
            } else {
                Log.d(TAG, "Event not consumed by: ${handler.id}, trying next...")
            }
        }
        
        Log.d(TAG, "Event not consumed by any handler")
        return false
    }
    
    /**
     * Get handlers for a specific key (for debugging)
     */
    fun getHandlersForKey(key: Key): List<KeyHandler> {
        return _registeredHandlers.filter { it.keys.contains(key) }
    }
    
    /**
     * Check for potential conflicts (multiple handlers for same key at same priority)
     */
    fun detectConflicts(): List<String> {
        val conflicts = mutableListOf<String>()
        
        val groupedByKeyAndPriority = _registeredHandlers
            .flatMap { handler -> handler.keys.map { key -> Triple(key, handler.priority, handler.id) } }
            .groupBy { (key, priority) -> key to priority }
        
        groupedByKeyAndPriority.forEach { (keyPriority, handlers) ->
            if (handlers.size > 1) {
                val (key, priority) = keyPriority
                val handlerIds = handlers.map { it.third }
                conflicts.add("Key at ${priority.name} priority: ${handlerIds.joinToString(", ")}")
            }
        }
        
        return conflicts
    }
    
    private fun logCurrentHandlers() {
        Log.d(TAG, "=== Current Key Handlers (${_registeredHandlers.size}) ===")
        _registeredHandlers.forEach { handler ->
            Log.d(TAG, "  ${handler.id} (${handler.priority.name}): ${handler.keys.map { it.keyCode }} - ${handler.description}")
        }
        
        val conflicts = detectConflicts()
        if (conflicts.isNotEmpty()) {
            Log.w(TAG, "⚠️ CONFLICTS DETECTED:")
            conflicts.forEach { Log.w(TAG, "  $it") }
        }
    }
    
    /**
     * Clear all handlers (useful for testing)
     */
    fun clearAllHandlers() {
        _registeredHandlers.clear()
        Log.d(TAG, "Cleared all handlers")
    }
}

/**
 * Composable helper for registering/unregistering handlers
 */
@Composable
fun RegisterKeyHandler(
    id: String,
    priority: KeyEventManager.Priority,
    keys: Set<Key>,
    description: String,
    handler: (KeyEvent) -> Boolean
) {
    DisposableEffect(id, priority, keys, description) {
        KeyEventManager.registerHandler(id, priority, keys, description, handler)
        
        onDispose {
            KeyEventManager.unregisterHandler(id)
        }
    }
}

/**
 * Debug composable showing active key handlers
 */
@Composable
fun KeyEventDebugOverlay(
    showConflicts: Boolean = true,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val handlers by remember { derivedStateOf { KeyEventManager.registeredHandlers } }
    val conflicts by remember { derivedStateOf { KeyEventManager.detectConflicts() } }
    
    if (handlers.isNotEmpty()) {
        Column(
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(8.dp)
        ) {
            Text(
                "Key Handlers (${handlers.size})",
                color = Color.Yellow,
                fontSize = 12.sp
            )
            
            handlers.forEach { handler ->
                Text(
                    "${handler.id} (${handler.priority.name})",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
            
            if (showConflicts && conflicts.isNotEmpty()) {
                Text(
                    "⚠️ CONFLICTS:",
                    color = Color.Red,
                    fontSize = 10.sp
                )
                conflicts.forEach { conflict ->
                    Text(
                        conflict,
                        color = Color.Red,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}