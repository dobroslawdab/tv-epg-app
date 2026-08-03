package com.uxellence.tv.v3.channels

import android.util.Log
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.util.MimeTypes

/**
 * Fabryka MediaItem dla kanałów live.
 *
 * Robi dwie rzeczy, których nie da się zrobić poprawnie przy `MediaItem.fromUri(url)`:
 *
 * ### 1. Leniwe wstrzyknięcie tokenu JWT
 * Token jest brany z [LiveTokenProvider] **w momencie wywołania**, nie przy ładowaniu listy
 * kanałów. To jest cały mechanizm "podmiana tokenu bez rebuilda": wystarczy że token zmieni
 * się w Supabase, a następne przełączenie kanału użyje już nowego.
 *
 * ### 2. Jawny MIME type dla DASH
 * Kanały Play mają rozszerzenie **`.livx`**, którego `Util.inferContentType()` nie rozpoznaje —
 * ExoPlayer potraktowałby manifest DASH jako progresywny plik i playback padłby na
 * `ParserException: Input does not start with the #EXTM3U header` lub podobnym. Dlatego MIME
 * ustawiamy ręcznie na podstawie ścieżki URL-a (query z tokenem jest wcześniej odcinany).
 *
 * ## Wymagana zależność
 * `exoplayer-dash` musi być w `build.gradle.kts` — bez niego ustawienie
 * [MimeTypes.APPLICATION_MPD] rzuci `IllegalStateException: DASH module not found` przy prepare.
 *
 * @see LiveTokenProvider źródło tokenu
 * @see LiveTokenRetryHandler retry gdy token wygasł w trakcie sesji (401/403)
 */
object LiveMediaItemFactory {
    private const val TAG = "LiveMediaItemFactory"

    /**
     * Zbuduj MediaItem gotowy do odtworzenia.
     *
     * @param rawUrl szablon URL-a z bazy/asset (może zawierać `{JWT}` lub kończyć się `?jwt=`)
     * @param isLive gdy true, dokłada [MediaItem.LiveConfiguration] blokujące auto-catch-up
     *               (zachowanie zgodne z istniejącym playerem w EpgDayScreen)
     */
    fun build(rawUrl: String, isLive: Boolean = true): MediaItem {
        val resolvedUrl = LiveTokenProvider.resolve(rawUrl)
        val mimeType = inferMimeType(rawUrl)

        if (LiveTokenProvider.requiresToken(rawUrl) && !LiveTokenProvider.hasToken) {
            Log.w(TAG, "URL wymaga tokenu, ale token jest pusty — spodziewaj się 401: ${maskUrl(rawUrl)}")
        }

        val builder = MediaItem.Builder().setUri(resolvedUrl)

        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }

        if (isLive) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMinPlaybackSpeed(1.0f)   // brak auto speed-down
                    .setMaxPlaybackSpeed(1.0f)   // brak auto catch-up do live edge
                    .build()
            )
        }

        Log.d(TAG, "MediaItem: mime=${mimeType ?: "auto"} url=${maskUrl(resolvedUrl)}")
        return builder.build()
    }

    /**
     * Wywnioskuj MIME z **ścieżki** URL-a (bez query — token by zaburzył detekcję rozszerzenia).
     *
     * @return MIME dla DASH/HLS, albo null gdy zostawiamy autodetekcję ExoPlayera
     */
    fun inferMimeType(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#').lowercase()

        return when {
            // Kanały live Play: /livedash/.../live.livx
            path.endsWith(".livx") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.contains("/livedash/") -> MimeTypes.APPLICATION_MPD
            path.contains("/dash/") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            else -> null  // MP4 i inne — autodetekcja działa poprawnie
        }
    }

    /** Czy URL wskazuje na strumień DASH (przydatne w logach/diagnostyce). */
    fun isDash(url: String): Boolean = inferMimeType(url) == MimeTypes.APPLICATION_MPD

    /**
     * Ukryj token w URL-u przed zalogowaniem — JWT to sekret, nie trafia do logcata.
     */
    fun maskUrl(url: String): String {
        val idx = url.indexOf("jwt=")
        if (idx < 0) return url
        val prefix = url.substring(0, idx + 4)
        val tokenPart = url.substring(idx + 4)
        return if (tokenPart.isEmpty()) "$prefix(pusty)" else "$prefix***(${tokenPart.length} zn.)"
    }
}
