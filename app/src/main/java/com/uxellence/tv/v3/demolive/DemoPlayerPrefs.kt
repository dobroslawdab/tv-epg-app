package com.uxellence.tv.v3.demolive

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Trwały przełącznik wyglądu paska przycisków playera w demo (klawisz "3"):
 *  - false = pasek tekstowy,
 *  - true  = pasek ikonowy wg Figmy.
 *
 * Compose-observable [mutableStateOf] (każdy odczyt `.value` w scope Composable
 * subskrybuje recompose) + backing SharedPreferences, więc wybór przeżywa nawigację
 * (wyjście/powrót do demo) ORAZ restart aplikacji — aż do ponownego "3".
 *
 * Wzorzec jak RentalManager (object z publicznym MutableState bez `private`).
 */
object DemoPlayerPrefs {
    private const val PREFS = "demo_player_prefs"
    private const val KEY_FIGMA_BUTTONS = "use_figma_buttons"
    private const val KEY_SCRUB_NEXT_TILE = "scrub_next_tile"
    private const val KEY_HINT_BUBBLE = "long_press_hint_bubble"

    val useFigmaButtons = mutableStateOf(false)
    // Klawisz "8": sposób pokazania zmiany materiału na taśmie przewijania
    //  - false = tytuły materiałów NAD kafelkami (Figma 5530-5395)
    //  - true  = KAFELEK "Przechodzisz do…" między miniaturkami (Figma 5530-5267)
    val scrubNextTile = mutableStateOf(false)
    // Podpowiedź long-press na miniaturkach (Kino Play):
    //  - false = v1: ciemny toast-pigułka pod kaflem (Figma 4100-1747)
    //  - true  = v2: biały dymek z karetką i badge OK
    val longPressHintBubble = mutableStateOf(false)
    private var loaded = false

    /** Wczytaj zapamiętany stan (idempotentne — robi to tylko raz na proces). */
    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        useFigmaButtons.value = prefs.getBoolean(KEY_FIGMA_BUTTONS, false)
        scrubNextTile.value = prefs.getBoolean(KEY_SCRUB_NEXT_TILE, false)
        longPressHintBubble.value = prefs.getBoolean(KEY_HINT_BUBBLE, false)
        loaded = true
    }

    /** Przełącz wariant podpowiedzi long-press (dev modal "0"). */
    fun toggleLongPressHint(context: Context): Boolean {
        val newValue = !longPressHintBubble.value
        longPressHintBubble.value = newValue
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HINT_BUBBLE, newValue).apply()
        return newValue
    }

    /** Przełącz wariant przejścia materiałów na taśmie (klawisz "8"). */
    fun toggleScrubNextTile(context: Context): Boolean {
        val newValue = !scrubNextTile.value
        scrubNextTile.value = newValue
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SCRUB_NEXT_TILE, newValue).apply()
        return newValue
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
