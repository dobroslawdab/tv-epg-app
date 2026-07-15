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
    private const val KEY_HINT_BUBBLE = "long_press_hint_bubble"
    private const val KEY_PLAYER_VERSION = "player_version"

    val useFigmaButtons = mutableStateOf(false)
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
    private var loaded = false

    /** Wczytaj zapamiętany stan (idempotentne — robi to tylko raz na proces). */
    fun load(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        useFigmaButtons.value = prefs.getBoolean(KEY_FIGMA_BUTTONS, false)
        longPressHintBubble.value = prefs.getBoolean(KEY_HINT_BUBBLE, false)
        playerVersion.value = prefs.getInt(KEY_PLAYER_VERSION, 1)
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
