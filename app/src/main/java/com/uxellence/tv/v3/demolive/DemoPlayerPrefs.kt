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

    val useFigmaButtons = mutableStateOf(false)
    private var loaded = false

    /** Wczytaj zapamiętany stan (idempotentne — robi to tylko raz na proces). */
    fun load(context: Context) {
        if (loaded) return
        useFigmaButtons.value = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIGMA_BUTTONS, false)
        loaded = true
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
