package com.uxellence.tv.v3.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Version Tracker
 *
 * Śledzi wersję aplikacji i określa czy pokazać ekran "Co nowego"
 * po aktualizacji aplikacji.
 *
 * Używa SharedPreferences do zapamiętania ostatniej pokazanej wersji.
 */
object VersionTracker {
    private const val PREFS_NAME = "tv_settings"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_STARTUP_MODE = "startup_mode"

    // Aktualna wersja aplikacji - zaktualizuj przy każdej zmianie wersji
    private const val CURRENT_VERSION_CODE = 15  // Wersja 3.4.0

    // Tryby startowe
    const val MODE_EPG_DAY = "epg_day"
    const val MODE_TOP_MENU = "top_menu"

    /**
     * Sprawdza czy pokazać ekran "Co nowego"
     *
     * @param context Context aplikacji
     * @return true jeśli ekran powinien być pokazany (nowa instalacja lub aktualizacja)
     */
    fun shouldShowWhatsNew(context: Context): Boolean {
        val prefs = getPreferences(context)
        val lastVersionCode = prefs.getInt(KEY_LAST_VERSION_CODE, -1)

        // Pokaż ekran jeśli:
        // 1. Pierwsza instalacja (lastVersionCode == -1)
        // 2. Aktualizacja (lastVersionCode < CURRENT_VERSION_CODE)
        return lastVersionCode < CURRENT_VERSION_CODE
    }

    /**
     * Zapisuje informację że ekran "Co nowego" został pokazany
     *
     * @param context Context aplikacji
     */
    fun markWhatsNewShown(context: Context) {
        getPreferences(context)
            .edit()
            .putInt(KEY_LAST_VERSION_CODE, CURRENT_VERSION_CODE)
            .apply()
    }

    /**
     * Pobiera SharedPreferences używane przez aplikację
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Sprawdza czy to pierwsza instalacja LUB aktualizacja aplikacji
     *
     * @param context Context aplikacji
     * @return true jeśli to pierwsza instalacja lub aktualizacja
     */
    fun isFirstLaunchOrUpdate(context: Context): Boolean {
        return shouldShowWhatsNew(context)
    }

    /**
     * Sprawdza czy to pierwsza instalacja aplikacji
     *
     * @param context Context aplikacji
     * @return true jeśli to pierwsza instalacja (nie aktualizacja)
     */
    fun isFirstInstall(context: Context): Boolean {
        val lastVersionCode = getPreferences(context).getInt(KEY_LAST_VERSION_CODE, -1)
        return lastVersionCode == -1
    }

    /**
     * Pobiera zapisany tryb startowy aplikacji
     *
     * @param context Context aplikacji
     * @return Tryb startowy (MODE_EPG_DAY lub MODE_TOP_MENU), domyślnie MODE_TOP_MENU
     */
    fun getStartupMode(context: Context): String {
        return getPreferences(context)
            .getString(KEY_STARTUP_MODE, MODE_TOP_MENU) ?: MODE_TOP_MENU
    }

    /**
     * Zapisuje wybrany tryb startowy aplikacji
     *
     * @param context Context aplikacji
     * @param mode Tryb startowy do zapisania (MODE_EPG_DAY lub MODE_TOP_MENU)
     */
    fun setStartupMode(context: Context, mode: String) {
        getPreferences(context)
            .edit()
            .putString(KEY_STARTUP_MODE, mode)
            .apply()
    }
}
