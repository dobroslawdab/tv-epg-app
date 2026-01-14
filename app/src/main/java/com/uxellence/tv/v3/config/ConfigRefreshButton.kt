package com.uxellence.tv.v3.config

import android.widget.Toast
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * "Pobierz parametry" button for refreshing app config from Supabase
 *
 * Usage in any screen:
 * ```
 * ConfigRefreshButton(
 *     isFocused = isRefreshButtonFocused,
 *     focusRequester = refreshButtonFocusRequester,
 *     onFocusChanged = { isRefreshButtonFocused = it },
 *     sx = sx,
 *     sy = sy
 * )
 * ```
 */
@Composable
fun ConfigRefreshButton(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp = { it.dp },
    sy: (Int) -> Dp = { it.dp }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val refreshStatus by ConfigManager.lastRefreshStatus.collectAsState()

    val isLoading = refreshStatus is ConfigManager.RefreshStatus.Loading

    // Colors
    val backgroundColor = if (isFocused) Color(0xFF5FEDD4) else Color(0x66000000)
    val textColor = if (isFocused) Color(0xFF281443) else Color(0xFFEEEEEE)
    val borderColor = if (isFocused) Color(0xFF5FEDD4) else Color.Transparent

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter) &&
                    !isLoading) {
                    coroutineScope.launch {
                        val result = ConfigManager.refreshConfig(context)
                        result.onSuccess { config ->
                            Toast.makeText(
                                context,
                                "Pobrano konfigurację v${config.version}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        result.onFailure { error ->
                            Toast.makeText(
                                context,
                                "Błąd: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    true
                } else false
            }
            .height(sy(80))
            .background(backgroundColor, RoundedCornerShape(sx(40)))
            .border(
                width = if (isFocused) sx(4) else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(sx(40))
            )
            .padding(horizontal = sx(32)),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(sx(32)),
                color = textColor,
                strokeWidth = sx(3)
            )
        } else {
            Text(
                text = "Pobierz parametry",
                style = TextStyle(
                    fontSize = (24 * sy(1).value).sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            )
        }
    }
}

/**
 * Config status text showing last refresh result
 */
@Composable
fun ConfigStatusText(
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp = { it.dp },
    sy: (Int) -> Dp = { it.dp }
) {
    val refreshStatus by ConfigManager.lastRefreshStatus.collectAsState()

    val statusText = when (val status = refreshStatus) {
        is ConfigManager.RefreshStatus.Idle -> null
        is ConfigManager.RefreshStatus.Loading -> "Pobieranie..."
        is ConfigManager.RefreshStatus.Success -> status.message
        is ConfigManager.RefreshStatus.Error -> status.message
    }

    val statusColor = when (refreshStatus) {
        is ConfigManager.RefreshStatus.Error -> Color(0xFFFF6B6B)
        is ConfigManager.RefreshStatus.Success -> Color(0xFF5FEDD4)
        else -> Color(0xCCEEEEEE)
    }

    statusText?.let {
        Text(
            text = it,
            style = TextStyle(
                fontSize = (18 * sy(1).value).sp,
                fontWeight = FontWeight.Normal,
                color = statusColor
            ),
            modifier = modifier
        )
    }
}

/**
 * Full config settings section with button and status
 */
@Composable
fun ConfigSettingsSection(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sx: (Int) -> Dp = { it.dp },
    sy: (Int) -> Dp = { it.dp }
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(16))
    ) {
        Text(
            text = "Konfiguracja aplikacji",
            style = TextStyle(
                fontSize = (28 * sy(1).value).sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFEEEEEE)
            )
        )

        ConfigRefreshButton(
            isFocused = isFocused,
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            sx = sx,
            sy = sy
        )

        ConfigStatusText(sx = sx, sy = sy)

        // Show current config version
        val config by ConfigManager.configState.collectAsState()
        Text(
            text = "Wersja konfiguracji: ${config.version}",
            style = TextStyle(
                fontSize = (16 * sy(1).value).sp,
                fontWeight = FontWeight.Normal,
                color = Color(0x99EEEEEE)
            )
        )
    }
}
