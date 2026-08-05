package com.uxellence.tv.v3.channels

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Natychmiastowe wstrzyknięcie tokenu JWT z adb — bez Supabase, bez rebuilda.
 *
 * Token do kanałów live rotuje codziennie, a wpisywanie 285-znakowego JWT pilotem
 * na TV jest niewykonalne. Ten receiver pozwala ustawić token jedną komendą:
 *
 * ```bash
 * adb shell am broadcast -a com.uxellence.tv.prod.SET_LIVE_TOKEN \
 *   -n com.uxellence.tv.prod/com.uxellence.tv.v3.channels.LiveTokenReceiver \
 *   --es token "eyJ0eXAiOiJKV1QiLCJhbGci..."
 * ```
 *
 * Token ląduje w [LiveTokenProvider] (SharedPreferences), więc przeżywa restart
 * i działa od razu na wszystkich ekranach — kanał przełącza się sam, bo klucze
 * LaunchedEffect zawierają token.
 *
 * Dodatkowe akcje:
 * - `--es token ""` lub `-a …CLEAR_LIVE_TOKEN` → czyści token
 * - `-a …REFRESH_LIVE_TOKEN` → wymusza pobranie tokenu z Supabase
 *
 * ⚠️ Receiver jest `exported=true` (inaczej adb broadcast go nie dosięgnie), więc
 * teoretycznie inna aplikacja na urządzeniu mogłaby podmienić token. Dla makiety
 * badawczej to akceptowalne; w produkcie token musi pochodzić z autoryzowanego
 * backendu, nie z broadcastu.
 */
class LiveTokenReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LiveTokenReceiver"
        const val ACTION_SET = "com.uxellence.tv.prod.SET_LIVE_TOKEN"
        const val ACTION_CLEAR = "com.uxellence.tv.prod.CLEAR_LIVE_TOKEN"
        const val ACTION_REFRESH = "com.uxellence.tv.prod.REFRESH_LIVE_TOKEN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET -> {
                val token = intent.getStringExtra("token")?.trim().orEmpty()
                if (token.isEmpty()) {
                    LiveTokenProvider.clear(context)
                    report(context, "Token live wyczyszczony")
                    return
                }
                // Sanity-check: JWT ma trzy segmenty rozdzielone kropkami
                if (token.count { it == '.' } != 2) {
                    report(context, "To nie wygląda na JWT (brak 2 kropek) — pomijam")
                    Log.w(TAG, "Odrzucony token: ${token.length} zn., kropek=${token.count { it == '.' }}")
                    return
                }
                val changed = LiveTokenProvider.setToken(context, token)
                report(
                    context,
                    if (changed) "Token live ustawiony (${token.length} zn.)"
                    else "Token bez zmian"
                )
                Log.i(TAG, "SET_LIVE_TOKEN: ${LiveTokenProvider.maskedToken()}, changed=$changed")
            }

            ACTION_CLEAR -> {
                LiveTokenProvider.clear(context)
                report(context, "Token live wyczyszczony")
            }

            ACTION_REFRESH -> {
                // Pobranie z Supabase w tle (goAsync — receiver nie może blokować)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val result = ChannelManager.refreshLiveToken(context)
                        val msg = result.fold(
                            onSuccess = { "Token z Supabase: $it" },
                            onFailure = { "Błąd pobierania tokenu: ${it.message}" }
                        )
                        Log.i(TAG, msg)
                        withContext(Dispatchers.Main) {
                            report(context, msg)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun report(context: Context, message: String) {
        Log.i(TAG, message)
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            // Broadcast bez UI (apka zabita) — sam log wystarczy
        }
    }
}
