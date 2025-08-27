package com.example.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tv.epg.*
import com.example.tv.repository.EpgRepository
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import com.example.tv.ui.theme.figmaRadialBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvRoot() }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvRoot() {
    val context = LocalContext.current
    val repository = remember { EpgRepository.getInstance(context) }
    val focusRequester = remember { FocusRequester() }
    var showEpg by remember { mutableStateOf(false) }

    // Start background EPG loading on app start
    LaunchedEffect(Unit) {
        repository.startBackgroundRefresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tło 1:1 z Figmy – radial gradient
            .figmaRadialBackground()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                val action = event.nativeKeyEvent.action
                when {
                    keyCode == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_DOWN && showEpg -> {
                        showEpg = false
                        true
                    }
                    else -> false
                }
            }
    ) {
        if (showEpg) {
            var guide by remember { mutableStateOf<EpgGuide?>(null) }
            var error by remember { mutableStateOf<String?>(null) }
            var isLoading by remember { mutableStateOf(true) }
            val now = java.time.Instant.now()
            val startOfDay = now.atZone(java.time.ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val endOfDay = startOfDay.plus(java.time.Duration.ofDays(1))
            val windowStart = startOfDay
            val windowEnd = endOfDay

            // Load EPG data from repository (cache-first)
            LaunchedEffect(Unit) {
                try {
                    isLoading = true
                    guide = repository.getEpgGuide(
                        startTime = windowStart,
                        endTime = windowEnd,
                        maxChannels = 50
                    )
                    error = null
                } catch (t: Throwable) {
                    error = t.message ?: t::class.java.simpleName
                } finally {
                    isLoading = false
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    error != null -> Text(
                        "Nie udało się pobrać EPG: ${error}",
                        color = Color.White
                    )
                    isLoading -> Text("Ładowanie EPG...", color = Color.White)
                    guide == null -> Text("Brak danych EPG", color = Color.White)
                    else -> EpgScreen(
                        guide = guide!!,
                        windowStart = windowStart,
                        windowEnd = windowEnd
                    )
                }
            }
        } else {
            // Strona główna: lista przycisków
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { showEpg = true }) {
                    Text("EPG")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
