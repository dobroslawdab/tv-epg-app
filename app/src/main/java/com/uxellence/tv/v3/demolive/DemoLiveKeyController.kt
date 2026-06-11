package com.uxellence.tv.v3.demolive

import android.view.KeyEvent

/**
 * Warstwy ekranu demo live (maszyna stanów).
 */
enum class DemoLayer { LIVE_INFO, DETAIL, FULLSCREEN, SEEK_OVERLAY }

/**
 * Akcje wywoływane przez kontroler klawiszy — implementowane w DemoLiveScreen.
 */
class DemoLiveActions(
    val showLiveInfo: () -> Unit,
    val showDetail: () -> Unit,
    val goFullscreen: () -> Unit,
    val exit: () -> Unit,
    val seekStep: (direction: Int) -> Unit,
    val seekConfirm: () -> Unit,
    val seekCancel: () -> Unit,
    val returnToLive: () -> Unit,
    val isReturnToLiveFocused: () -> Boolean,
    val seekFocusChange: (toReturnButton: Boolean) -> Unit,
    val detailFocusedIndex: () -> Int,
    val detailFocusChange: (Int) -> Unit,
    val startOver: () -> Unit
)

/**
 * DEMO LIVE KEY CONTROLLER — jedyny punkt obsługi klawiszy ekranu demo.
 *
 * KEY HANDLER: DemoLiveScreen Navigation
 * Scope: wszystkie klawisze ekranu demo (LIVE_INFO/DETAIL/FULLSCREEN/SEEK_OVERLAY)
 * Delegation: root Box w DemoLiveScreen → handleKey() → funkcja warstwy
 * Conflicts: brak — żaden element warstw nie ma własnego handlera (delegation pattern, Issue #4)
 */
object DemoLiveKeyController {

    fun handleKey(keyCode: Int, layer: DemoLayer, a: DemoLiveActions): Boolean = when (layer) {
        DemoLayer.LIVE_INFO -> handleLiveInfoKeys(keyCode, a)
        DemoLayer.DETAIL -> handleDetailKeys(keyCode, a)
        DemoLayer.FULLSCREEN -> handleFullscreenKeys(keyCode, a)
        DemoLayer.SEEK_OVERLAY -> handleSeekOverlayKeys(keyCode, a)
    }

    private fun handleLiveInfoKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            a.showDetail(); true
        }
        KeyEvent.KEYCODE_BACK -> {
            a.goFullscreen(); true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true  // konsumuj, nic nie rób
        else -> false
    }

    private fun handleDetailKeys(keyCode: Int, a: DemoLiveActions): Boolean {
        val index = a.detailFocusedIndex()
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (index > 0) a.detailFocusChange(index - 1); true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (index in 0..1) a.detailFocusChange(index + 1); true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (index >= 0) a.detailFocusChange(-1); true  // -1 = rząd "Części serii"
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (index == -1) a.detailFocusChange(0); true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (index == 0) a.startOver()  // "Zacznij od początku"; pozostałe = atrapy
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                a.showLiveInfo(); true
            }
            else -> false
        }
    }

    private fun handleFullscreenKeys(keyCode: Int, a: DemoLiveActions): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            a.seekStep(-1); true   // wejście w SEEK_OVERLAY + pierwszy krok
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            a.seekStep(+1); true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
            a.showLiveInfo(); true
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
