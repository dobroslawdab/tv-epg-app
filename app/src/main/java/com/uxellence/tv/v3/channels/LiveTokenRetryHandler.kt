package com.uxellence.tv.v3.channels

import android.content.Context
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.upstream.HttpDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automatyczne odzyskiwanie po wygaśnięciu tokenu JWT w trakcie sesji.
 *
 * ## Problem
 * Token do kanałów live jest krótkożyciowy. Jeśli wygaśnie podczas badań UX, player dostaje
 * **401/403 z CDN** i po prostu się zatrzymuje z czarnym ekranem. Bez tego handlera jedynym
 * ratunkiem byłby restart aplikacji (albo — gdyby token był zapiekany w APK — nowy build).
 *
 * ## Rozwiązanie
 * Listener wykrywa błąd HTTP 401/403, dociąga świeży token z Supabase, i jeśli token
 * faktycznie się zmienił — przebudowuje MediaItem i robi ponowne `prepare()`. Użytkownik
 * widzi krótką przerwę zamiast martwego ekranu.
 *
 * ## Zabezpieczenia przed pętlą
 * - [MAX_ATTEMPTS] prób na jeden URL; licznik resetuje się po udanym odtworzeniu
 * - [COOLDOWN_MS] między próbami — nie DDoS-ujemy ani Supabase, ani CDN
 * - Gdy token po odświeżeniu jest **taki sam**, retry jest przerywany (nie ma czego poprawiać —
 *   to znaczy że operator jeszcze nie wgrał nowego tokenu)
 *
 * ## Użycie
 * ```kotlin
 * val retryHandler = remember {
 *     LiveTokenRetryHandler(
 *         context = context,
 *         scope = coroutineScope,
 *         currentRawUrl = { currentChannelRawUrl },
 *         player = { player }
 *     )
 * }
 * DisposableEffect(player) {
 *     player.addListener(retryHandler)
 *     onDispose { player.removeListener(retryHandler) }
 * }
 * ```
 *
 * @param currentRawUrl lambda zwracająca **szablon** URL-a aktualnego kanału (z `{JWT}`),
 *                      nie rozwiązany URL — token wstrzykuje [LiveMediaItemFactory]
 * @param player lambda zwracająca aktualną instancję playera (może się zmieniać)
 * @param onRetry opcjonalny callback do UI (np. pokaż "Odświeżam dostęp…")
 */
class LiveTokenRetryHandler(
    private val context: Context,
    private val scope: CoroutineScope,
    private val currentRawUrl: () -> String,
    private val player: () -> ExoPlayer?,
    private val onRetry: (attempt: Int) -> Unit = {},
    private val onGiveUp: (reason: String) -> Unit = {}
) : Player.Listener {

    companion object {
        private const val TAG = "LiveTokenRetry"
        private const val MAX_ATTEMPTS = 3
        private const val COOLDOWN_MS = 2000L

        /**
         * Czy ten błąd wygląda na problem z autoryzacją (wygasły/zły token).
         *
         * Przechodzi łańcuch `cause`, bo ExoPlayer zawija HTTP error w kilka warstw
         * (`ExoPlaybackException` → `HttpDataSourceException` → `InvalidResponseCodeException`).
         */
        fun isAuthError(error: PlaybackException): Boolean {
            var cause: Throwable? = error
            var depth = 0
            while (cause != null && depth < 10) {
                if (cause is HttpDataSource.InvalidResponseCodeException) {
                    if (cause.responseCode == 401 || cause.responseCode == 403) {
                        return true
                    }
                }
                cause = cause.cause
                depth++
            }
            return false
        }
    }

    private var attempts = 0
    private var isRefreshing = false

    override fun onPlayerError(error: PlaybackException) {
        val rawUrl = currentRawUrl()

        if (!LiveTokenProvider.requiresToken(rawUrl)) {
            // Kanał nie używa tokenu — nie nasza sprawa (np. stary m3u8 z asset JSON)
            return
        }

        if (!isAuthError(error)) {
            Log.d(TAG, "Błąd nie jest 401/403 (${error.errorCodeName}) — retry tokenu pominięty")
            return
        }

        if (isRefreshing) {
            Log.d(TAG, "Odświeżanie już w toku, pomijam")
            return
        }

        if (attempts >= MAX_ATTEMPTS) {
            val reason = "Wyczerpano $MAX_ATTEMPTS próby odświeżenia tokenu"
            Log.e(TAG, reason)
            onGiveUp(reason)
            return
        }

        attempts++
        isRefreshing = true
        Log.w(TAG, "401/403 na kanale — próba $attempts/$MAX_ATTEMPTS odświeżenia tokenu")
        onRetry(attempts)

        scope.launch {
            try {
                delay(COOLDOWN_MS)

                val tokenBefore = LiveTokenProvider.token
                val refreshed = ChannelManager.refreshLiveToken(context)

                if (refreshed.isFailure) {
                    val reason = "Nie udało się pobrać tokenu: ${refreshed.exceptionOrNull()?.message}"
                    Log.e(TAG, reason)
                    onGiveUp(reason)
                    return@launch
                }

                if (LiveTokenProvider.token == tokenBefore) {
                    val reason = "Supabase zwrócił ten sam token — operator nie wgrał jeszcze nowego"
                    Log.w(TAG, reason)
                    onGiveUp(reason)
                    return@launch
                }

                // Token się zmienił — przebuduj MediaItem i spróbuj ponownie
                val exo = player()
                if (exo == null) {
                    Log.w(TAG, "Player już nie istnieje, przerywam retry")
                    return@launch
                }

                Log.i(TAG, "Nowy token pobrany, ponawiam odtwarzanie")
                exo.setMediaItem(LiveMediaItemFactory.build(currentRawUrl()))
                exo.prepare()
                exo.playWhenReady = true
            } finally {
                isRefreshing = false
            }
        }
    }

    /**
     * Reset licznika prób po udanym odtworzeniu — inaczej trzy rotacje tokenu w ciągu
     * długiej sesji badawczej wyczerpałyby limit na stałe.
     */
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && attempts > 0) {
            Log.d(TAG, "Playback wrócił do READY — reset licznika prób")
            attempts = 0
        }
    }

    /** Ręczny reset (np. przy przełączeniu kanału). */
    fun reset() {
        attempts = 0
        isRefreshing = false
    }
}
