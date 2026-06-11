package com.uxellence.tv.v3.demolive

import android.view.KeyEvent

/**
 * Warstwy ekranu demo live (maszyna stanów):
 *  EPG (start) → BACK → FULLSCREEN → OK → CONTROLS
 *                FULLSCREEN → LEFT/RIGHT → SEEK_OVERLAY
 *                FULLSCREEN → UP/DOWN → EPG
 */
enum class DemoLayer { EPG, CONTROLS, FULLSCREEN, SEEK_OVERLAY }

/**
 * Akcje wywoływane przez kontroler klawiszy — implementowane w DemoLiveScreen.
 */
class DemoLiveActions(
    val showEpg: () -> Unit,
    val epgMove: (direction: Int) -> Unit,
    val epgMoveChannel: (direction: Int) -> Unit,
    val epgSelect: () -> Unit,
    val showControls: () -> Unit,
    val controlsMove: (direction: Int) -> Unit,
    val controlsSelect: () -> Unit,
    val goFullscreen: () -> Unit,
    val exit: () -> Unit,
    val seekStep: (direction: Int) -> Unit,
    val seekConfirm: () -> Unit,
    val seekCancel: () -> Unit,
    val returnToLive: () -> Unit,
    val isReturnToLiveFocused: () -> Boolean,
    val seekFocusChange: (toReturnButton: Boolean) -> Unit
)

/**
 * DEMO LIVE KEY CONTROLLER — jedyny punkt obsługi klawiszy ekranu demo.
 *
 * KEY HANDLER: DemoLiveScreen Navigation
 * Scope: wszystkie klawisze ekranu demo (EPG/CONTROLS/FULLSCREEN/SEEK_OVERLAY)
 * Delegation: root Box w DemoLiveScreen → handleKey() → funkcja warstwy
 * Conflicts: brak — żaden element warstw nie ma własnego handlera (delegation pattern, Issue #4)
 */
object DemoLiveKeyController {

    fun handleKey(keyCode: Int, layer: DemoLayer, a: DemoLiveActions): Boolean = when (layer) {
        DemoLayer.EPG -> handleEpgKeys(keyCode, a)
        DemoLayer.CONTROLS -> handleControlsKeys(keyCode, a)
        DemoLayer.FULLSCREEN -> handleFullscreenKeys(keyCode, a)
        DemoLayer.SEEK_OVERLAY -> handleSeekOverlayKeys(keyCode, a)
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
            a.goFullscreen(); true
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
            a.epgMoveChannel(-1); true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            a.epgMoveChannel(+1); true
        }
        else -> false
    }

    private fun handleControlsKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            a.controlsMove(-1); true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            a.controlsMove(+1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            a.controlsSelect(); true
        }
        KeyEvent.KEYCODE_BACK -> {
            a.goFullscreen(); true
        }
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true  // konsumuj
        else -> false
    }

    private fun handleFullscreenKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            a.seekStep(-1); true   // wejście w SEEK_OVERLAY + pierwszy krok
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            a.seekStep(+1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            a.showEpg(); true   // OK na czystym playerze = warstwa EPG
        }
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            a.showEpg(); true
        }
        KeyEvent.KEYCODE_BACK -> {
            a.exit(); true
        }
        else -> false
    }

    private fun handleSeekOverlayKeys(keyCode: Int, a: DemoLiveActions): Boolean {
        val onReturnButton = a.isReturnToLiveFocused()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!onReturnButton) a.seekStep(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!onReturnButton) a.seekStep(+1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!onReturnButton) a.seekFocusChange(true)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (onReturnButton) a.seekFocusChange(false)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (onReturnButton) a.returnToLive() else a.seekConfirm()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                a.seekCancel(); true
            }
            else -> false
        }
    }
}
