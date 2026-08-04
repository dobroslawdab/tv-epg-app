package com.uxellence.tv.v3.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Zaślepka prototypu — pełnoekranowy modal "W prototypie ta funkcja jest niedostępna"
 * (Figma 4679-49709). Pokazywany, gdy user kliknie coś, czego makieta jeszcze nie
 * obsługuje (np. skrót "Utwórz Moją listę kanałów").
 *
 * Compose-observable singleton (wzorzec RentalManager): `PrototypeStub.show()` z dowolnego
 * miejsca; render raz, na top-level MainActivity — NAD wszystkimi ekranami (zIndex 100).
 *
 * KEY HANDLER: PrototypeStubOverlay
 * Scope: pełna kradzież fokusu — BACK i OK zamykają, reszta klawiszy skonsumowana
 *        (modal input gate, jak PIP Dialog Pattern — nic nie przecieka pod spód)
 */
object PrototypeStub {
    /** true = modal widoczny. Publiczny MutableState — odczyt w kompozycji subskrybuje. */
    val visible = mutableStateOf(false)

    fun show() {
        visible.value = true
    }

    fun dismiss() {
        visible.value = false
    }
}

@Composable
fun PrototypeStubOverlay() {
    val isVisible by PrototypeStub.visible
    if (!isVisible) return

    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val buttonFocus = remember { FocusRequester() }
    var isButtonFocused by remember { mutableStateOf(false) }

    // BACK przez dispatcher (dwufazowy bug BACK: KeyUp bez fokusu zamykał apkę)
    BackHandler(enabled = true) { PrototypeStub.dismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            // SOLID ciemny fiolet (#281443) — bez gradientów; z radialu projekt
            // zrezygnował dawno temu i nie ma go w Figmie (decyzja 2026-08-04)
            .background(Color(0xFF281443))
            .onPreviewKeyEvent { event ->
                // Input gate: modal konsumuje WSZYSTKO. OK zamyka (fokus i tak
                // siedzi na jedynym przycisku), BACK łapie BackHandler wyżej.
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            PrototypeStub.dismiss()
                            true
                        }
                        Key.Back -> false   // zostaw dispatcherowi (BackHandler)
                        else -> true        // nie przepuszczaj strzałek pod modal
                    }
                } else {
                    event.key != Key.Back
                }
            }
    ) {
        // Layout wg Figmy: lewa kolumna, treść zaczyna się na x=390, pion wyśrodkowany
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = sx(390))
        ) {
            // Ikona narzędzi (Figma: skrzyżowane śrubokręt+młotek ~84px)
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = Color(0xFFEEEEEE),
                modifier = Modifier.size(sx(84))
            )

            Spacer(modifier = Modifier.height(sy(48)))

            Text(
                text = "W prototypie ta funkcja\njest niedostępna",
                color = Color(0xFFEEEEEE),
                fontSize = sy(57).value.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = sy(72).value.sp
            )

            Spacer(modifier = Modifier.height(sy(32)))

            Text(
                text = "To wersja testowa. Działa w niej tylko część ekranów.",
                color = Color(0xFFEEEEEE).copy(alpha = 0.9f),
                fontSize = sy(29).value.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(sy(72)))

            // Przycisk "Wróć" — aqua wg Figmy; jedyny focusable modala
            Box(
                modifier = Modifier
                    .focusRequester(buttonFocus)
                    .onFocusChanged { isButtonFocused = it.isFocused }
                    .focusable()
                    .background(
                        color = if (isButtonFocused) Color(0xFF5FEDD4) else Color(0xFF4ACCB6),
                        shape = RoundedCornerShape(sx(12))
                    )
                    .padding(horizontal = sx(48), vertical = sy(20)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Wróć",
                    color = Color(0xFF48227C),
                    fontSize = sy(29).value.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    // Fokus na przycisku od pierwszej klatki — pilot od razu gotowy na OK
    LaunchedEffect(Unit) {
        try {
            buttonFocus.requestFocus()
        } catch (_: Exception) {
        }
    }
}
