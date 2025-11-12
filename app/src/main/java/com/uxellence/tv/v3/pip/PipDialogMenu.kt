package com.uxellence.tv.v3.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * PipDialogMenu - Modal dialog overlay for PIP control
 *
 * Extracted from TopMenuScreen2.kt (lines 967-1114) for better code organization.
 *
 * UI Structure:
 * - Black semi-transparent backdrop (alpha 0.7)
 * - Centered purple card (600px width)
 * - 2 options: Powiększ na pełny ekran, Zamknij PIP
 * - UP/DOWN navigation, ENTER to select, BACK to dismiss
 *
 * Focus Management:
 * - Auto-focus first option on appearance
 * - FocusRequesters for both options
 * - Clears background focus (focusManager.clearFocus)
 * - Cleanup on dismiss (DisposableEffect)
 *
 * Layout Engineer Pattern:
 * - sx/sy parameters for responsive scaling
 * - All dimensions use scaled values (sx/sy functions)
 *
 * @param onDismiss Callback when dialog dismissed (BACK key)
 * @param onFullscreen Callback when "Powiększ" selected
 * @param onClose Callback when "Zamknij" selected
 * @param sx Scaling function for X dimension (e.g., sx(600) for 600px width)
 * @param sy Scaling function for Y dimension (e.g., sy(60) for 60px height)
 */
@Composable
fun PipDialogMenu(
    onDismiss: () -> Unit,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Centralized state (tescik pattern)
    var focusedOption by remember { mutableStateOf(0) }
    var menuInputEnabled by remember { mutableStateOf(false) }  // Input gating
    val menuFocusRequester = remember { FocusRequester() }  // Single focus requester
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .zIndex(200f)  // Below PIP (1000f), above content
            .focusRequester(menuFocusRequester)  // Centralized focus
            .focusable()
            .onKeyEvent { keyEvent ->  // Centralized key handling
                // Input gating - block events until menu is ready
                if (!menuInputEnabled) {
                    android.util.Log.d("PipDialogMenu", "Input gated - menu not ready")
                    return@onKeyEvent true
                }

                // Delegate to controller
                PipDialogController.handleDialogKeys(
                    event = keyEvent,
                    focusedOption = focusedOption,
                    onNavigate = { newOption ->
                        focusedOption = newOption  // State change instead of focus change
                        android.util.Log.d("PipDialogMenu", "Navigate to option: $newOption")
                    },
                    onSelectFullscreen = {
                        onDismiss()
                        onFullscreen()
                    },
                    onSelectClose = {
                        onDismiss()
                        onClose()
                    },
                    onDismiss = onDismiss
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(sx(600))
                .background(Color(0xFF48227C), RoundedCornerShape(sx(16)))
                .border(sx(2), Color(0xFF5AECD3), RoundedCornerShape(sx(16)))
                .padding(sx(40)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sy(24))
        ) {
            // Dialog title
            Text(
                text = "Co chcesz zrobić z odtwarzaczem?",
                color = Color(0xFFEEEEEE),
                fontSize = (24 * sx(1).value / 1).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(sy(16)))

            // Option 1: Powiększ na pełny ekran (state-driven styling)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(60))
                    .background(
                        if (focusedOption == 0) Color(0xFF5AECD3) else Color(0x33EEEEEE),
                        RoundedCornerShape(sx(8))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Powiększ na pełny ekran",
                    color = if (focusedOption == 0) Color(0xFF48227C) else Color(0xFFEEEEEE),
                    fontSize = (20 * sx(1).value / 1).sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Option 2: Zamknij PIP (state-driven styling)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sy(60))
                    .background(
                        if (focusedOption == 1) Color(0xFF5AECD3) else Color(0x33EEEEEE),
                        RoundedCornerShape(sx(8))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Zamknij PIP",
                    color = if (focusedOption == 1) Color(0xFF48227C) else Color(0xFFEEEEEE),
                    fontSize = (20 * sx(1).value / 1).sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Proper focus initialization (tescik pattern)
            LaunchedEffect(Unit) {
                menuInputEnabled = false  // Disable input during initialization
                focusManager.clearFocus(force = true)
                delay(250)  // Wait for KEY_UP to complete (longer delay like tescik)
                menuFocusRequester.requestFocus()
                delay(150)  // Additional buffer
                menuInputEnabled = true  // Enable input only when fully ready
                android.util.Log.d("PipDialogMenu", "Dialog focus: ready, input enabled")
            }

            // Clean up focus when dialog closes
            DisposableEffect(Unit) {
                onDispose {
                    focusManager.clearFocus()
                    android.util.Log.d("PipDialogMenu", "Dialog closed - focus cleared")
                }
            }
        }
    }
}
