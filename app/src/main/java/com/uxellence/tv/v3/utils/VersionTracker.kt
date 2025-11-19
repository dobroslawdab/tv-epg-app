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
    private const val KEY_LAUNCHER_SETUP_COMPLETED = "launcher_setup_completed"
    private const val KEY_CANDYBAR_VISIBLE = "candybar_visible"
    private const val KEY_NOTIFICATION_BADGE = "notification_badge"
    private const val KEY_NAGRANIA_VERSION = "nagrania_version"
    private const val KEY_EPG_SECTION_EXPANDED = "epg_section_expanded"

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

    /**
     * Sprawdza czy launcher setup został zakończony
     *
     * @param context Context aplikacji
     * @return true jeśli setup został zakończony (użytkownik wybrał opcję lub pominął)
     */
    fun isLauncherSetupCompleted(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(KEY_LAUNCHER_SETUP_COMPLETED, false)
    }

    /**
     * Oznacza launcher setup jako zakończony
     *
     * Wywołuje się gdy użytkownik:
     * - Wybierze "Ustaw jako launcher" i otworzy ustawienia
     * - Wybierze "Pomiń" i przejdzie do wyboru trybu
     * - Naciśnie BACK na ekranie launcher setup
     *
     * @param context Context aplikacji
     */
    fun markLauncherSetupCompleted(context: Context) {
        getPreferences(context)
            .edit()
            .putBoolean(KEY_LAUNCHER_SETUP_COMPLETED, true)
            .apply()
    }

    /**
     * Pobiera zapisaną widoczność CandyBar (prawy przycisk punktów)
     *
     * @param context Context aplikacji
     * @return true jeśli CandyBar jest widoczny (domyślnie: true)
     */
    fun getCandyBarVisibility(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(KEY_CANDYBAR_VISIBLE, true)
    }

    /**
     * Zapisuje widoczność CandyBar
     *
     * @param context Context aplikacji
     * @param visible true = widoczny, false = ukryty
     */
    fun setCandyBarVisibility(context: Context, visible: Boolean) {
        getPreferences(context)
            .edit()
            .putBoolean(KEY_CANDYBAR_VISIBLE, visible)
            .apply()
    }

    /**
     * Pobiera zapisany stan powiadomienia profilu (czerwona kropka)
     *
     * @param context Context aplikacji
     * @return true jeśli powiadomienie jest widoczne (domyślnie: false)
     */
    fun getNotificationBadge(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(KEY_NOTIFICATION_BADGE, false)
    }

    /**
     * Zapisuje stan powiadomienia profilu
     *
     * @param context Context aplikacji
     * @param visible true = pokazuj kropkę, false = ukryj
     */
    fun setNotificationBadge(context: Context, visible: Boolean) {
        getPreferences(context)
            .edit()
            .putBoolean(KEY_NOTIFICATION_BADGE, visible)
            .apply()
    }

    /**
     * Pobiera zapisaną wersję kanałów nagrań w sekcji MOJE
     *
     * @param context Context aplikacji
     * @return "v1" (expandable) lub "v2" (4 przyciski), domyślnie "v1"
     */
    fun getNagraniaVersion(context: Context): String {
        return getPreferences(context)
            .getString(KEY_NAGRANIA_VERSION, "v1") ?: "v1"
    }

    /**
     * Zapisuje wersję kanałów nagrań
     *
     * @param context Context aplikacji
     * @param version "v1" lub "v2"
     */
    fun setNagraniaVersion(context: Context, version: String) {
        getPreferences(context)
            .edit()
            .putString(KEY_NAGRANIA_VERSION, version)
            .apply()
    }

    /**
     * Pobiera zapisany stan sekcji EPG "Było w TV" w TELEWIZJA
     *
     * @param context Context aplikacji
     * @return true jeśli sekcja jest rozwinięta (domyślnie: false)
     */
    fun getEpgSectionExpanded(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(KEY_EPG_SECTION_EXPANDED, false)
    }

    /**
     * Zapisuje stan sekcji EPG "Było w TV"
     *
     * @param context Context aplikacji
     * @param expanded true = rozwinięta, false = zwinięta
     */
    fun setEpgSectionExpanded(context: Context, expanded: Boolean) {
        getPreferences(context)
            .edit()
            .putBoolean(KEY_EPG_SECTION_EXPANDED, expanded)
            .apply()
    }
}
