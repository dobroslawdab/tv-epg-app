package com.uxellence.tv.v3.channels

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live JWT Token Provider
 *
 * Kanały live Play (`r.playcdn.tv/livedash/.../live.livx?jwt=<TOKEN>`) wymagają tokenu JWT,
 * który jest krótkożyciowy i podawany "na życzenie" przez zespół CDN. Token NIE MOŻE być
 * zapiekany w APK — inaczej każda rotacja tokenu wymaga nowego builda i releasu.
 *
 * ## Kontrakt
 *
 * URL kanału w bazie/asset trzyma **szablon**, nigdy gotowy URL z tokenem:
 * ```
 * https://r.playcdn.tv/livedash/play/playtv/indigo/live/yVJZ2dq8bJ8/live.livx?jwt={JWT}
 * ```
 * Token jest wstrzykiwany **leniwie, w momencie budowania MediaItem** ([LiveMediaItemFactory]),
 * nigdy przy ładowaniu listy kanałów. Dzięki temu podmiana tokenu w Supabase działa na
 * następnym przełączeniu kanału — bez restartu aplikacji, bez rebuilda.
 *
 * ## Dlaczego nie trzymamy gotowego URL-a w stanie
 *
 * Gdyby lista kanałów przechowywała URL z już wklejonym tokenem, to po rotacji tokenu
 * wszystkie zcache'owane URL-e byłyby martwe do czasu przeładowania listy. Rozdzielenie
 * "szablon URL-a" (rzadko się zmienia) od "token" (rotuje często) to jedyny sensowny podział.
 *
 * @see LiveMediaItemFactory buduje MediaItem z rozwiązanym tokenem + poprawnym MIME (DASH)
 * @see ChannelManager.refreshRemoteChannels pobiera listę kanałów + token z Supabase
 */
object LiveTokenProvider {
    private const val TAG = "LiveTokenProvider"
    private const val PREFS_NAME = "live_token_cache"
    private const val KEY_TOKEN = "jwt"
    private const val KEY_UPDATED_AT = "jwt_updated_at"

    /** Placeholder w szablonie URL-a, podmieniany na aktualny token. */
    const val JWT_PLACEHOLDER = "{JWT}"

    private val _tokenState = MutableStateFlow("")

    /** Obserwowalny token — Compose recompose'uje przy rotacji. */
    val tokenState: StateFlow<String> = _tokenState.asStateFlow()

    /** Aktualny token; pusty string gdy nieustawiony. */
    val token: String get() = _tokenState.value

    /** Czy mamy jakikolwiek token do wstrzyknięcia. */
    val hasToken: Boolean get() = _tokenState.value.isNotBlank()

    /** Timestamp ostatniej podmiany tokenu (millis), 0 gdy nigdy. */
    var lastUpdatedAt: Long = 0L
        private set

    /**
     * Wczytaj token z cache (SharedPreferences).
     *
     * Wołane przy starcie aplikacji — pozwala odtworzyć kanał od razu, bez czekania na
     * round-trip do Supabase. Token z cache może być wygasły; wtedy zadziała retry na 401
     * ([LiveTokenRetryHandler]).
     */
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_TOKEN, null)
        lastUpdatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)

        if (!cached.isNullOrBlank()) {
            _tokenState.value = cached
            Log.d(TAG, "Loaded cached token (len=${cached.length}, age=${ageMinutes()}min)")
        } else {
            Log.d(TAG, "No cached token")
        }
    }

    /**
     * Ustaw nowy token i zapisz do cache.
     *
     * ⚠️ Pusty/`null` token **nie kasuje** już posiadanego tokenu. Powód: `live_config.jwt`
     * startuje jako NULL i bywa czyszczony przy edycji wiersza — bez tego zabezpieczenia
     * jeden pusty odczyt z Supabase ubiłby działający token w trakcie badań. Do celowego
     * wyczyszczenia służy [clear].
     *
     * @return true gdy token faktycznie się zmienił (przydatne do decyzji o re-prepare playera)
     */
    fun setToken(context: Context, newToken: String?): Boolean {
        val clean = newToken?.trim().orEmpty()

        if (clean.isEmpty() && hasToken) {
            Log.w(TAG, "Supabase zwrócił pusty token — zachowuję poprzedni (age=${ageMinutes()}min)")
            return false
        }

        if (clean == _tokenState.value) {
            Log.d(TAG, "Token unchanged, skipping")
            return false
        }

        _tokenState.value = clean
        lastUpdatedAt = System.currentTimeMillis()

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, clean)
            .putLong(KEY_UPDATED_AT, lastUpdatedAt)
            .apply()

        Log.i(TAG, "Token updated (len=${clean.length})")
        return true
    }

    /**
     * Wstrzyknij aktualny token do szablonu URL-a.
     *
     * Obsługiwane formy wejścia:
     * - `.../live.livx?jwt={JWT}` → placeholder podmieniony na token
     * - `.../live.livx?jwt=` → token dopisany do pustego parametru
     * - `.../live.m3u8` → zwrócony bez zmian (kanał nie wymaga tokenu)
     *
     * Gdy token jest pusty, szablon zostaje "rozwinięty" do pustego jwt — playback
     * dostanie 401, co jest sygnałem dla [LiveTokenRetryHandler] żeby dociągnąć token.
     */
    fun resolve(rawUrl: String): String {
        if (rawUrl.isBlank()) return rawUrl

        return when {
            rawUrl.contains(JWT_PLACEHOLDER) ->
                rawUrl.replace(JWT_PLACEHOLDER, token)

            // Pusty parametr jwt na końcu URL-a (forma z ticketu: "?jwt=")
            rawUrl.endsWith("?jwt=") || rawUrl.endsWith("&jwt=") ->
                rawUrl + token

            else -> rawUrl
        }
    }

    /** Czy dany URL w ogóle potrzebuje tokenu (czyli czy ma placeholder / puste jwt). */
    fun requiresToken(rawUrl: String): Boolean =
        rawUrl.contains(JWT_PLACEHOLDER) ||
            rawUrl.endsWith("?jwt=") ||
            rawUrl.endsWith("&jwt=")

    /** Wiek tokenu w minutach; -1 gdy brak tokenu. */
    fun ageMinutes(): Long =
        if (lastUpdatedAt == 0L) -1
        else (System.currentTimeMillis() - lastUpdatedAt) / 60_000

    /** Wyczyść token (debug / DevToggles). */
    fun clear(context: Context) {
        _tokenState.value = ""
        lastUpdatedAt = 0L
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Log.d(TAG, "Token cleared")
    }

    /**
     * Zamaskowany token do logów/UI — nigdy nie logujemy pełnego JWT.
     */
    fun maskedToken(): String = when {
        !hasToken -> "(brak)"
        token.length <= 12 -> "***"
        else -> "${token.take(6)}…${token.takeLast(4)} (${token.length} zn.)"
    }
}
