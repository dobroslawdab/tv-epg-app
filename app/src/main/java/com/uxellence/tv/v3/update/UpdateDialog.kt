package com.uxellence.tv.v3.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Update dialog for TV - shows when new version is available
 *
 * Features:
 * - Shows version info and release notes
 * - Download progress indicator
 * - Force update option (can't dismiss)
 * - D-pad navigation support
 */

// Colors
private val DIALOG_BG = Color(0xFF1E1E2E)
private val BUTTON_FOCUSED = Color(0xFF5AECD3)  // Aqua
private val BUTTON_UNFOCUSED = Color(0xFF3A3A4A)
private val TEXT_PRIMARY = Color(0xFFEEEEEE)
private val TEXT_SECONDARY = Color(0xFFAAAAAA)
private val PURPLE_DARK = Color(0xFF48227C)

enum class UpdateState {
    READY,      // Ready to download
    DOWNLOADING, // Download in progress
    INSTALLING   // Ready to install
}

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    currentVersion: String,
    updateState: UpdateState,
    downloadProgress: Int = 0,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    // True only after the downloaded APK has flushed + fsync'd to disk. The
    // INSTALLING screen hides the "Zainstaluj teraz" button until this flips
    // so users can't tap a button that would silently no-op (root cause of
    // "klikam Zainstaluj i nic się nie dzieje" complaints).
    isApkReady: Boolean = true,
    modifier: Modifier = Modifier
) {
    var focusedButton by remember { mutableStateOf(0) } // 0 = Update, 1 = Later
    val updateButtonFocus = remember { FocusRequester() }
    val laterButtonFocus = remember { FocusRequester() }

    // Auto-focus first button
    LaunchedEffect(Unit) {
        updateButtonFocus.requestFocus()
    }

    Dialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionRight -> {
                                // Navigation for READY and INSTALLING states
                                if ((updateState == UpdateState.READY || updateState == UpdateState.INSTALLING) && !updateInfo.forceUpdate) {
                                    focusedButton = if (focusedButton == 0) 1 else 0
                                    if (focusedButton == 0) {
                                        updateButtonFocus.requestFocus()
                                    } else {
                                        laterButtonFocus.requestFocus()
                                    }
                                }
                                true
                            }
                            Key.Back, Key.Escape -> {
                                // Allow dismiss in READY and INSTALLING states
                                if (!updateInfo.forceUpdate && (updateState == UpdateState.READY || updateState == UpdateState.INSTALLING)) {
                                    onDismiss()
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(600.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DIALOG_BG)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Dostępna aktualizacja",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TEXT_PRIMARY
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Version info
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentVersion,
                        fontSize = 20.sp,
                        color = TEXT_SECONDARY
                    )
                    Text(
                        text = "  →  ",
                        fontSize = 20.sp,
                        color = BUTTON_FOCUSED
                    )
                    Text(
                        text = updateInfo.versionName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BUTTON_FOCUSED
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Release notes — capped to 4 lines + ellipsis so the dialog
                // never grows tall enough to push action buttons off-screen.
                // Full changelog stays on GitHub Releases.
                if (!updateInfo.releaseNotes.isNullOrBlank()) {
                    Text(
                        text = updateInfo.releaseNotes,
                        fontSize = 18.sp,
                        color = TEXT_SECONDARY,
                        textAlign = TextAlign.Center,
                        lineHeight = 26.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // State-dependent content
                when (updateState) {
                    UpdateState.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = BUTTON_FOCUSED,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Pobieranie aktualizacji... ${downloadProgress}%",
                            fontSize = 18.sp,
                            color = TEXT_SECONDARY
                        )
                    }

                    UpdateState.INSTALLING -> {
                        if (!isApkReady) {
                            // File still flushing to disk — show a spinner +
                            // disabled placeholder so the user knows the box
                            // is working and the "Zainstaluj" tap they're
                            // about to do isn't a no-op.
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = BUTTON_FOCUSED,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Przygotowuję plik instalacyjny…",
                                fontSize = 18.sp,
                                color = TEXT_SECONDARY
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Za moment przycisk Zainstaluj będzie aktywny",
                                fontSize = 14.sp,
                                color = TEXT_SECONDARY.copy(alpha = 0.7f)
                            )
                        } else {
                            // Re-focus the install button the moment the file
                            // becomes ready (covers the case where the user
                            // was looking at the spinner and arrow-keys
                            // wouldn't have done anything before now).
                            LaunchedEffect(Unit) {
                                focusedButton = 0
                                try { updateButtonFocus.requestFocus() } catch (_: Exception) {}
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                UpdateButton(
                                    text = "Zainstaluj teraz",
                                    isFocused = focusedButton == 0,
                                    focusRequester = updateButtonFocus,
                                    onClick = onInstall,
                                    onFocused = { focusedButton = 0 }
                                )

                                UpdateButton(
                                    text = "Anuluj",
                                    isFocused = focusedButton == 1,
                                    focusRequester = laterButtonFocus,
                                    onClick = onDismiss,
                                    onFocused = { focusedButton = 1 }
                                )
                            }
                        }
                    }

                    UpdateState.READY -> {
                        // Buttons row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            UpdateButton(
                                text = "Aktualizuj",
                                isFocused = focusedButton == 0,
                                focusRequester = updateButtonFocus,
                                onClick = onDownload,
                                onFocused = { focusedButton = 0 }
                            )

                            if (!updateInfo.forceUpdate) {
                                UpdateButton(
                                    text = "Później",
                                    isFocused = focusedButton == 1,
                                    focusRequester = laterButtonFocus,
                                    onClick = onDismiss,
                                    onFocused = { focusedButton = 1 }
                                )
                            }
                        }
                    }
                }

                // Force update warning
                if (updateInfo.forceUpdate) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Ta aktualizacja jest wymagana",
                        fontSize = 14.sp,
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateButton(
    text: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit = {}
) {
    val bgColor = if (isFocused) BUTTON_FOCUSED else BUTTON_UNFOCUSED
    val textColor = if (isFocused) PURPLE_DARK else TEXT_PRIMARY
    val borderColor = if (isFocused) BUTTON_FOCUSED else Color.Transparent

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else false
            }
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
