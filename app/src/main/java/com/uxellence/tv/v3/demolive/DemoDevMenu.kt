package com.uxellence.tv.v3.demolive

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Dev menu playera demo — otwierane klawiszem "0" w Demo: Kanał live + ramówka.
 *
 * MODAL STANOWY BEZ WŁASNYCH FOCUSABLE (lekcja #11 CLAUDE.md): Compose Dialog
 * na TV nie zawsze przejmuje fokus okna (klawisze z pilota losowo omijały menu
 * i sterowały playerem pod spodem), więc overlay jest zwykłym Boxem z zIndex,
 * a WSZYSTKIE klawisze obsługuje keyHandler DemoLiveScreen (branch showDevMenu
 * na samej górze — input gate: nieobsłużone klawisze są BLOKOWANE, nie przeciekają).
 * BACK zamyka przez [BackHandler] (zarejestrowany tu = później = wyższy priorytet
 * niż BackHandler ekranu).
 */
object DemoDevMenuModel {
    /** Pozycje: (etykieta, bieżąca wartość) — wartości czytane z DemoPlayerPrefs. */
    fun items(): List<Pair<String, String>> = listOf(
        "Wersja playera" to DemoPlayerPrefs.playerVersionLabel(),
        "Pasek przewijania" to DemoPlayerPrefs.seekBarLabel(),
        "Przyciski playera" to
            if (DemoPlayerPrefs.useFigmaButtons.value) "ikonowe (Figma)" else "tekstowe",
    )

    const val COUNT = 3

    /** OK na pozycji [index] — cyklowanie wartości. */
    fun activate(index: Int, context: Context) {
        when (index) {
            0 -> DemoPlayerPrefs.cyclePlayerVersion(context)
            1 -> DemoPlayerPrefs.cycleSeekBarVersion(context)
            2 -> DemoPlayerPrefs.toggle(context)
        }
    }
}

@Composable
internal fun DemoDevMenu(selectedIndex: Int, onDismiss: () -> Unit) {
    BackHandler(enabled = true) { onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(40f)
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .wrapContentHeight()
                .background(Color(0xFF281443), RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF5FEDD4), RoundedCornerShape(16.dp))
                .padding(32.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Player demo (debug)",
                    color = Color(0xFFEEEEEE),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                DemoDevMenuModel.items().forEachIndexed { idx, (label, value) ->
                    val isItemFocused = selectedIndex == idx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isItemFocused) Color(0xFF5FEDD4).copy(alpha = 0.18f)
                                else Color(0x10FFFFFF),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = Color(0xFFEEEEEE), fontSize = 10.sp)
                        Text(value, color = Color(0xFF5FEDD4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "↑↓ wybór  ·  OK zmiana  ·  BACK/0 zamknij",
                    color = Color(0x88EEEEEE),
                    fontSize = 8.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
