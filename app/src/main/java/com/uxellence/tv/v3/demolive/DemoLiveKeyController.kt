package com.uxellence.tv.v3.demolive

import android.view.KeyEvent

/**
 * Warstwy ekranu demo live (maszyna stanów):
 *  EPG (start) --OK na programie--> PLAYER_UI (strefy: BUTTONS / STRIP / DESCRIPTION)
 *  EPG --BACK--> FULLSCREEN --OK/UP/DOWN--> EPG, --LEFT/RIGHT--> PLAYER_UI:STRIP
 */
enum class DemoLayer { EPG, PLAYER_UI, FULLSCREEN }

/**
 * Akcje wywoływane przez kontroler klawiszy — implementowane w DemoLiveScreen.
 * Logika stref PLAYER_UI (BUTTONS/STRIP/DESCRIPTION) jest po stronie ekranu.
 */
class DemoLiveActions(
    val showEpg: () -> Unit,
    val epgMove: (direction: Int) -> Unit,
    val epgMoveChannel: (direction: Int) -> Unit,
    val epgSelect: () -> Unit,
    val epgBack: () -> Unit,
    val playerMove: (direction: Int) -> Unit,
    val playerSelect: () -> Unit,
    val playerUp: () -> Unit,
    val playerDown: () -> Unit,
    val playerBack: () -> Unit,
    val openStripWithStep: (direction: Int) -> Unit,
    val goFullscreen: () -> Unit,
    val exit: () -> Unit
)

/**
 * DEMO LIVE KEY CONTROLLER — jedyny punkt obsługi klawiszy ekranu demo.
 *
 * KEY HANDLER: DemoLiveScreen Navigation
 * Scope: wszystkie klawisze ekranu demo (EPG/PLAYER_UI/FULLSCREEN)
 * Delegation: root Box w DemoLiveScreen → handleKey() → funkcja warstwy;
 *             BACK wyłącznie przez BackHandler (dispatcher) — patrz DemoLiveScreen
 * Conflicts: brak — żaden element warstw nie ma własnego handlera (delegation pattern, Issue #4)
 */
object DemoLiveKeyController {

    fun handleKey(keyCode: Int, layer: DemoLayer, a: DemoLiveActions): Boolean = when (layer) {
        DemoLayer.EPG -> handleEpgKeys(keyCode, a)
        DemoLayer.PLAYER_UI -> handlePlayerKeys(keyCode, a)
        DemoLayer.FULLSCREEN -> handleFullscreenKeys(keyCode, a)
    }

    private fun handleEpgKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            a.epgMove(-1); true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            a.epgMove(+1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            a.epgSelect(); true
        }
        KeyEvent.KEYCODE_BACK -> {
            // Wielopoziomowy BACK: jeśli fokus jest na innym programie/kanale niż
            // oglądany → wróć do oglądanego kanału + bieżącego programu; dopiero gdy
            // już tam jesteśmy → zamknij warstwę EPG (oglądanie bez interfejsu)
            a.epgBack(); true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            a.epgMoveChannel(-1); true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            a.epgMoveChannel(+1); true
        }
        else -> false
    }

    private fun handlePlayerKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            a.playerMove(-1); true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            a.playerMove(+1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            a.playerSelect(); true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            a.playerUp(); true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            a.playerDown(); true
        }
        KeyEvent.KEYCODE_BACK -> {
            a.playerBack(); true
        }
        else -> false
    }

    private fun handleFullscreenKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            a.openStripWithStep(-1); true   // przewijanie: PLAYER_UI w strefie STRIP
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            a.openStripWithStep(+1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            a.showEpg(); true   // OK / góra / dół na czystym playerze = warstwa EPG
        }
        KeyEvent.KEYCODE_BACK -> {
            a.exit(); true   // wstecz na czystym playerze = wyjście z playera
        }
        else -> false
    }
}
