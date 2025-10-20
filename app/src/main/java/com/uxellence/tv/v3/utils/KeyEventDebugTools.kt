package com.uxellence.tv.v3.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.key.*
import android.util.Log
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Advanced Debug Tools for Key Event Management
 * 
 * These tools help identify and resolve key event conflicts during development.
 * All debug features are automatically disabled in release builds.
 */

/**
 * Data classes for key event logging
 */
data class KeyEventLog(
    val key: Key,
    val timestamp: Instant,
    val consumed: Boolean,
    val handlerId: String?
)

/**
 * Key event logger for debugging
 */
object KeyEventLogger {
    private val listeners = mutableListOf<(KeyEventLog) -> Unit>()
    
    fun addListener(listener: (KeyEventLog) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (KeyEventLog) -> Unit) {
        listeners.remove(listener)
    }
    
    fun logEvent(key: Key, consumed: Boolean, handlerId: String? = null) {
        val event = KeyEventLog(key, Instant.now(), consumed, handlerId)
        listeners.forEach { it(event) }
    }
}

/**
 * Enhanced Debug Overlay with Conflict Detection
 */
@Composable
fun AdvancedKeyEventDebugOverlay(
    modifier: Modifier = Modifier,
    showDetailedInfo: Boolean = false,
    onShowDetailedInfo: (Boolean) -> Unit = {}
) {
    // Debug tool - always enabled in utils
    
    val handlers by remember { derivedStateOf { KeyEventManager.registeredHandlers } }
    val conflicts by remember { derivedStateOf { KeyEventManager.detectConflicts() } }
    var showDetails by remember { mutableStateOf(showDetailedInfo) }
    
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.9f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
            .width(300.dp)
    ) {
        // Header with toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Key Event Debug",
                color = Color.Yellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = { 
                    showDetails = !showDetails
                    onShowDetailedInfo(showDetails)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue.copy(alpha = 0.7f)
                ),
                modifier = Modifier.height(24.dp)
            ) {
                Text(
                    if (showDetails) "Hide" else "Show",
                    fontSize = 10.sp,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Quick Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Handlers: ${handlers.size}",
                color = Color.White,
                fontSize = 11.sp
            )
            Text(
                "Conflicts: ${conflicts.size}",
                color = if (conflicts.isNotEmpty()) Color.Red else Color.Green,
                fontSize = 11.sp,
                fontWeight = if (conflicts.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
            )
        }
        
        // Conflicts Alert
        if (conflicts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "⚠️ CONFLICTS DETECTED!",
                color = Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            conflicts.take(3).forEach { conflict ->
                Text(
                    "• ${conflict.take(50)}...",
                    color = Color.Red,
                    fontSize = 9.sp
                )
            }
            if (conflicts.size > 3) {
                Text(
                    "+ ${conflicts.size - 3} more",
                    color = Color.Red,
                    fontSize = 9.sp
                )
            }
        }
        
        // Detailed View
        if (showDetails) {
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Gray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp)
            ) {
                items(handlers) { handler ->
                    HandlerDebugItem(handler)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun HandlerDebugItem(handler: KeyEventManager.KeyHandler) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Gray.copy(alpha = 0.3f),
                RoundedCornerShape(4.dp)
            )
            .padding(6.dp)
    ) {
        // Handler ID and Priority
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                handler.id,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                handler.priority.name,
                color = when (handler.priority) {
                    KeyEventManager.Priority.GLOBAL -> Color.Red
                    KeyEventManager.Priority.SCREEN -> Color.Yellow
                    KeyEventManager.Priority.COMPONENT -> Color.Green
                    KeyEventManager.Priority.WIDGET -> Color.Cyan
                },
                fontSize = 9.sp
            )
        }
        
        // Keys
        Text(
            "Keys: ${handler.keys.joinToString(", ") { it.keyCode.toString() }}",
            color = Color.LightGray,
            fontSize = 8.sp
        )
        
        // Description
        Text(
            handler.description,
            color = Color.LightGray,
            fontSize = 8.sp
        )
    }
}

/**
 * Real-time Key Event Monitor
 * Shows live key events as they happen
 */
@Composable
fun LiveKeyEventMonitor(
    modifier: Modifier = Modifier,
    maxEvents: Int = 10
) {
    // Debug tool - always enabled in utils
    
    var keyEvents by remember { mutableStateOf<List<KeyEventLog>>(emptyList()) }
    
    Column(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.85f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
            .width(250.dp)
    ) {
        Text(
            "Live Key Events",
            color = Color.Cyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LazyColumn(
            modifier = Modifier.heightIn(max = 150.dp),
            reverseLayout = true
        ) {
            items(keyEvents.take(maxEvents)) { event ->
                KeyEventLogItem(event)
            }
        }
    }
    
    // Register global key event listener
    LaunchedEffect(Unit) {
        KeyEventLogger.addListener { event ->
            keyEvents = (listOf(event) + keyEvents).take(maxEvents)
        }
    }
}

@Composable
private fun KeyEventLogItem(event: KeyEventLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Gray.copy(alpha = 0.2f),
                RoundedCornerShape(2.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            event.key.toString(),
            color = Color.White,
            fontSize = 9.sp
        )
        Text(
            if (event.consumed) "✓" else "✗",
            color = if (event.consumed) Color.Green else Color.Red,
            fontSize = 9.sp
        )
    }
}


/**
 * Conflict Analysis Dialog
 * Shows detailed analysis of key event conflicts
 */
@Composable
fun ConflictAnalysisDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (!true || !showDialog) return
    
    val conflicts = KeyEventManager.detectConflicts()
    val handlers = KeyEventManager.registeredHandlers
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Key Event Conflict Analysis",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red.copy(alpha = 0.7f)
                        )
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Summary
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (conflicts.isEmpty()) Color.Green.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (conflicts.isEmpty()) "✅ No Conflicts Detected" else "⚠️ ${conflicts.size} Conflicts Found",
                            color = if (conflicts.isEmpty()) Color.Green else Color.Red,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            "Total Handlers: ${handlers.size}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Conflicts List
                if (conflicts.isNotEmpty()) {
                    Text(
                        "Detected Conflicts:",
                        color = Color.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn {
                        items(conflicts) { conflict ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Red.copy(alpha = 0.1f)
                                )
                            ) {
                                Text(
                                    conflict,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Recommendations
                if (conflicts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "Recommendations:",
                        color = Color.Yellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        """
                        1. Use callback delegation pattern for BACK key
                        2. Assign different priority levels to handlers
                        3. Review handler scope and necessity
                        4. Consider using SafeNavigationScope wrapper
                        """.trimIndent(),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Quick Debug Floating Action Button
 * Provides easy access to debug tools
 */
@Composable
fun DebugFloatingActionButton(
    modifier: Modifier = Modifier
) {
    // Debug tool - always enabled in utils
    
    var showMenu by remember { mutableStateOf(false) }
    var showConflictDialog by remember { mutableStateOf(false) }
    var showLiveMonitor by remember { mutableStateOf(false) }
    
    Box(modifier = modifier) {
        // FAB
        FloatingActionButton(
            onClick = { showMenu = !showMenu },
            containerColor = Color.Blue.copy(alpha = 0.8f)
        ) {
            Text(
                "🔍",
                fontSize = 16.sp
            )
        }
        
        // Debug Menu
        if (showMenu) {
            Column(
                modifier = Modifier
                    .offset(y = (-60).dp)
                    .background(
                        Color.Black.copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                Button(
                    onClick = { showConflictDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red.copy(alpha = 0.7f)
                    )
                ) {
                    Text("Analyze Conflicts", fontSize = 10.sp)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Button(
                    onClick = { showLiveMonitor = !showLiveMonitor },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        if (showLiveMonitor) "Hide Monitor" else "Show Monitor",
                        fontSize = 10.sp
                    )
                }
            }
        }
        
        // Live Monitor
        if (showLiveMonitor) {
            LiveKeyEventMonitor(
                modifier = Modifier.offset(x = (-250).dp)
            )
        }
        
        // Conflict Dialog
        ConflictAnalysisDialog(
            showDialog = showConflictDialog,
            onDismiss = { showConflictDialog = false }
        )
    }
}

/**
 * Extension for easy debug integration
 */
@Composable
fun DebugWrapper(
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        
        if (true) {
            // Top-right: Advanced overlay
            AdvancedKeyEventDebugOverlay(
                modifier = Modifier.align(Alignment.TopEnd)
            )
            
            // Bottom-right: FAB
            DebugFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}