package com.uxellence.tv.v3

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * HomeButtonAccessibilityService - Przechwytuje przycisk HOME
 *
 * Purpose: AccessibilityService ma dostęp do wszystkich key events w systemie,
 * w tym KEYCODE_HOME, który jest normalnie zablokowany dla aplikacji.
 *
 * Jak działa:
 * 1. Service nasłuchuje wszystkie key events (onKeyEvent)
 * 2. Gdy wykryje KEYCODE_HOME → wysyła broadcast do LauncherActivity
 * 3. LauncherActivity odbiera broadcast i nawiguje do TopMenuScreen2/START
 *
 * Włączenie service:
 * 1. Settings → Accessibility
 * 2. Znajdź "BOX TV Home Button Handler"
 * 3. Toggle ON
 * 4. Potwierdź dialog
 *
 * Uwagi techniczne:
 * - Wymaga uprawnień Accessibility (user musi włączyć w Settings)
 * - Działa tylko gdy service jest enabled
 * - Broadcast: "com.uxellence.tv.HOME_PRESSED"
 *
 * @see LauncherActivity BroadcastReceiver odbierający HOME event
 * @see EpgActivity BroadcastReceiver odbierający HOME event
 */
class HomeButtonAccessibilityService : AccessibilityService() {

    companion object {
        const val HOME_PRESSED_ACTION = "com.uxellence.tv.HOME_PRESSED"
    }

    /**
     * Przechwytuje wszystkie key events w systemie
     *
     * KEYCODE_HOME jest zablokowany dla zwykłych aplikacji,
     * ale AccessibilityService ma do niego dostęp.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // DEBUG: Log EVERY key event to verify onKeyEvent is being called
        if (event != null) {
            val actionName = when (event.action) {
                KeyEvent.ACTION_DOWN -> "ACTION_DOWN"
                KeyEvent.ACTION_UP -> "ACTION_UP"
                else -> "ACTION_${event.action}"
            }
            android.util.Log.d("ACCESSIBILITY_DEBUG", "onKeyEvent called - keyCode: ${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}), action: $actionName")
        }

        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_HOME -> {
                    android.util.Log.d("ACCESSIBILITY_HOME", "HOME button detected - sending broadcast")

                    // Wyślij broadcast do LauncherActivity/EpgActivity
                    val intent = Intent(HOME_PRESSED_ACTION)
                    intent.setPackage(packageName)  // Tylko dla naszej aplikacji
                    sendBroadcast(intent)

                    // Return false - nie blokuj HOME button, pozwól systemowi obsłużyć
                    // (aplikacja launcher zostanie na pierwszym planie)
                    return false
                }
            }
        }
        return false  // Przepuść wszystkie inne klawisze
    }

    /**
     * Called when accessibility event occurs
     * Nie używamy tego dla przechwytywania klawiszy
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for key interception
    }

    /**
     * Called when service is interrupted
     */
    override fun onInterrupt() {
        android.util.Log.d("ACCESSIBILITY_HOME", "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("ACCESSIBILITY_HOME", "Service connected - requesting key event filtering")

        // Request key event filtering
        val info = serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        android.util.Log.d("ACCESSIBILITY_HOME", "Key event filtering enabled - HOME button monitoring active")
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("ACCESSIBILITY_HOME", "Service destroyed")
    }
}
