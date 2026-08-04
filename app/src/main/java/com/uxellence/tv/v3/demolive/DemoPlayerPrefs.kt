package com.uxellence.tv.v3.demolive

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Trwały przełącznik wyglądu paska przycisków playera w demo (klawisz "3"):
 *  - false = pasek tekstowy,
 *  - true  = pasek ikonowy wg Figmy (DOMYŚLNY od 2026-08-04).
 *
 * Compose-observable [mutableStateOf] (każdy odczyt `.value` w scope Composable
 * subskrybuje recompose) + backing SharedPreferences, więc wybór przeżywa nawigację
 * (wyjście/powrót do demo) ORAZ restart aplikacji — aż do ponownego "3".
 *
 * Wzorzec jak RentalManager (object z publicznym MutableState bez `private`).
 *
 * ⚠️ Zmiana domyślnej wartości NIE dotknie urządzeń, które już raz przełączyły
 * klawiszem "3" (mają zapisany stan w SharedPreferences) — tylko świeże instalacje
 * i te, które nigdy nie dotknęły przełącznika.
 */
object DemoPlayerPrefs {
    private const val PREFS = "demo_player_prefs"
    private const val KEY_FIGMA_BUTTONS = "use_figma_buttons"
    private const val KEY_HINT_BUBBLE = "long_press_hint_bubble"
    private const val KEY_HINT_ENABLED = "long_press_hint_enabled"
    private const val KEY_PLAYER_VERSION = "player_version"

    // Default true: pasek IKONOWY wg Figmy (decyzja 2026-08-04)
    val useFigmaButtons = mutableStateOf(true)
    // Klawisz "0" NA PLAYERZE demo: wersja playera (badanie A/B/C)
    //  1 = obecny (opis pod przyciskami)
    //  2 = jak 1, ale zamiast bloku opisu ikonka ⓘ "Zobacz opis"
    //  3 = player wg Figmy 5530-5203: pasek EPG + ikonowe kontrolki,
    //      poziomy kontrolki→pasek→miniaturka, wyżej/niżej widok 3 kanałów
    val playerVersion = mutableStateOf(1)
    // Dawny klawisz "8" (A/B: tytuły nad taśmą ⇄ kafelek "Przechodzisz do…")
    // USUNIĘTY 2026-07-14 — obowiązuje tryb połączony (oba naraz, DemoFilmstrip)
    // Podpowiedź long-press na miniaturkach (Kino Play):
    //  - false = v1: ciemny toast-pigułka pod kaflem (Figma 4100-1747)
    //  - true  = v2: biały dymek z karetką i badge OK
    val longPressHintBubble = mutableStateOf(false)
    // Czy podpowiedź long-press w ogóle się pokazuje (dev modal "0", 3 stany:
    // Wyłączona → Toast v1 → Dymek v2). DOMYŚLNIE WYŁĄCZONA (decyzja 2026-08-04).
    val longPressHintEnabled = mutableStateOf(false)
    private var loaded = false

    /** Wczytaj zapamiętany stan (idempotentne — robi to tylko raz na proces). */
    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        useFigmaButtons.value = prefs.getBoolean(KEY_FIGMA_BUTTONS, true)
        longPressHintBubble.value = prefs.getBoolean(KEY_HINT_BUBBLE, false)
        longPressHintEnabled.value = prefs.getBoolean(KEY_HINT_ENABLED, false)
        playerVersion.value = prefs.getInt(KEY_PLAYER_VERSION, 1)
        loaded = true
    }

    /**
     * Cykl podpowiedzi long-press (dev modal "0"):
     * Wyłączona → Toast v1 → Dymek v2 → Wyłączona…
     * @return etykieta nowego stanu (do wyświetlenia w modalu)
     */
    fun cycleLongPressHint(context: Context): String {
        val (enabled, bubble) = when {
            !longPressHintEnabled.value -> true to false          // → Toast v1
            !longPressHintBubble.value -> true to true            // → Dymek v2
            else -> false to false                                 // → Wyłączona
        }
        longPressHintEnabled.value = enabled
        longPressHintBubble.value = bubble
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HINT_ENABLED, enabled)
            .putBoolean(KEY_HINT_BUBBLE, bubble)
            .apply()
        return longPressHintLabel()
    }

    /** Etykieta bieżącego stanu podpowiedzi long-press (dev modal "0"). */
    fun longPressHintLabel(): String = when {
        !longPressHintEnabled.value -> "Wyłączona"
        !longPressHintBubble.value -> "v1: toast (Figma 4100-1747)"
        else -> "v2: dymek pod miniaturą"
    }

    /** Cykl wersji playera 1→2→3→1 (klawisz "0" na playerze demo). */
    fun cyclePlayerVersion(context: Context): Int {
        val next = (playerVersion.value % 3) + 1
        playerVersion.value = next
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PLAYER_VERSION, next).apply()
        return next
    }

    /** Przełącz i zapisz. Zwraca nowy stan. */
    fun toggle(context: Context): Boolean {
        val newValue = !useFigmaButtons.value
        useFigmaButtons.value = newValue
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FIGMA_BUTTONS, newValue).apply()
        return newValue
    }
}
