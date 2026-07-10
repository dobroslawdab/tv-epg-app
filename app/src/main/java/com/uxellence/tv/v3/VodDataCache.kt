package com.uxellence.tv.v3

import android.content.Context
import android.util.Log
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.VodItem
import com.uxellence.tv.v3.PlayNowMovie
import com.uxellence.tv.v3.model.SupabaseMovie
import com.uxellence.tv.v3.model.toVodContent
import com.uxellence.tv.v3.model.toVodSlideData
import com.uxellence.tv.v3.repository.SupabaseMoviesRepository
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Singleton cache for VOD data to avoid repeated I/O operations.
 *
 * Problem: loadVodContentFromAssets and loadKinoPlayMoviesFromAssets are called
 * multiple times (11 times total) when rendering sections, causing:
 * - Synchronous I/O in main thread (188KB+ JSON files)
 * - CPU-intensive JSON parsing
 * - UI blocking and slow tab switching
 *
 * Solution: Load data once at app startup, cache in memory, reuse everywhere.
 *
 * NOWE: Obsługa danych z Supabase (Kino Play z bazy danych)
 */
object VodDataCache {
    private const val TAG = "VodDataCache"

    @Volatile
    private var vodContentList: List<VodContent>? = null

    @Volatile
    private var kinoPlayMovies: List<VodContent>? = null

    // Nowe cache dla danych z Supabase
    @Volatile
    private var supabaseMovies: List<VodContent>? = null

    @Volatile
    private var top10Movies: List<VodContent>? = null

    @Volatile
    private var newestMovies: List<VodContent>? = null

    @Volatile
    private var moviesByGenre: Map<String, List<VodContent>> = emptyMap()

    @Volatile
    private var supabaseInitialized: Boolean = false

    // Cache dla slider movies z Supabase (tabela slider_movies)
    @Volatile
    private var sliderMovies: List<VodSlideData>? = null

    // Cache for KINO_PLAY channel→movies mapping (collections like "Gwiezdne Wojny",
    // "Disney", franchise sagas etc.). Populated by VodScreenContent after its
    // produceState classification finishes — so KinoGridScreen can expose those
    // collections as filterable categories without re-running the classification.
    @Volatile
    var kinoChannelMap: Map<String, List<VodContent>> = emptyMap()

    // Saved focus position inside Kino Play tab when navigating away to MovieDetail.
    // Consumed once on the next VodWithChannels mount.
    //   row, col          — focusedRowIndex / focusedColIndex (logical state)
    //   listFirstVisible  — channel LazyRow's firstVisibleItemIndex (visual scroll position;
    //                       for regular channels visual focus tracks this, not col)
    data class SavedKinoFocus(val row: Int, val col: Int, val listFirstVisible: Int)
    @Volatile
    var savedKinoPlayFocus: SavedKinoFocus? = null

    // Compose-observable trigger fired by MainActivity when MovieDetail closes back into a
    // still-mounted Kino Play tab (overlay scenario). VodWithChannels watches this to
    // re-grab keyboard focus on the previously-focused poster — TopMenuScreen2 stays
    // mounted, so all state (LazyListStates, channelFocusRequesters, focusedRowIndex)
    // is preserved; we just need to re-issue requestFocus() because MovieDetail's content
    // captured Compose focus while it was visible.
    val kinoPlayRefocusTrigger: MutableState<Int> = mutableStateOf(0)

    // Skrót "Oglądaj telewizję" (Start/Odkrywaj) otwiera Demo: kanał live —
    // trigger obserwowany w MainActivity (nawigacja między ekranami)
    val openDemoLiveTrigger: MutableState<Int> = mutableStateOf(0)

    // true = demo live otwarte ze skrótu Start/Odkrywaj → BACK wraca do
    // TopMenu; false = otwarte z menu deweloperskiego → BACK wraca do HOME
    var demoLiveOpenedFromShortcut: Boolean = false

    // Sekcja TopMenu, z której otwarto ChannelGrid skrótem (np. "ODKRYWAJ") —
    // czytana jednorazowo przez MainActivity przy nawigacji, żeby BACK wracał
    // na właściwą zakładkę (callback grid-u nie ma parametru sekcji)
    var pendingChannelGridSourceSection: String? = null

    // Same mechanism for the MOJE tab — used when MovieDetail closes and user originally
    // navigated to MovieDetail from MOJE→Wypożyczone (poster click). MojeChannelsScreen's
    // outer Box watches this and re-grabs keyboard focus; without it, BACK from MovieDetail
    // leaves Compose with no focus owner and the user has to press BACK twice.
    val mojeRefocusTrigger: MutableState<Int> = mutableStateOf(0)

    // Same mechanism for the SEARCH tab — used when MovieDetail closes after the user
    // rented a movie from search results. SearchScreen's rootFocusRequester watches this.
    // Without it, navigation locks up (no Compose focus owner) and BACK exits the app.
    val searchRefocusTrigger: MutableState<Int> = mutableStateOf(0)

    // Same mechanism for the WIDEO tab — used when MovieDetail closes after the user
    // clicked a horizontal channel miniature. WideoChannelsScreen rootBoxFocusRequester
    // watches this. Bumped by MainActivity.MovieDetail.onBackPressed.
    val wideoRefocusTrigger: MutableState<Int> = mutableStateOf(0)

    // Same mechanism for the ODKRYWAJ tab — used when MovieDetail closes after the
    // user clicked a slide / Top 10 poster on the home screen. Bumped by
    // MainActivity.MovieDetail.onBackPressed, watched by OdkrywajChannelsScreen.
    val odkrywajRefocusTrigger: MutableState<Int> = mutableStateOf(0)

    // True while a full-screen overlay (MovieDetail / Purchase / RentalProcessing) sits
    // on top of TopMenuScreen2. The Kino Play slider's auto-trailer loop reads this so
    // it doesn't keep spinning ExoPlayer in the background — that was suspected to be
    // the culprit when navigation became unresponsive after sitting on MovieDetail for
    // a while (~CPU-bound trailer playback under the overlay).
    val overlayActive: MutableState<Boolean> = mutableStateOf(false)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Initialize cache - call this once in MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        if (vodContentList == null) {
            vodContentList = loadVodContentFromAssets(context)
        }
        if (kinoPlayMovies == null) {
            kinoPlayMovies = loadKinoPlayMoviesFromAssets(context)
        }
    }

    /**
     * Get cached VOD content list (from vod_data.json)
     * Returns empty list if not initialized
     */
    fun getVodContentList(): List<VodContent> {
        return vodContentList ?: emptyList()
    }

    /**
     * Get cached Kino Play movies (from kino_play.json)
     * Returns empty list if not initialized
     */
    fun getKinoPlayMovies(): List<VodContent> {
        return kinoPlayMovies ?: emptyList()
    }

    /**
     * Check if cache is ready
     */
    fun isInitialized(): Boolean {
        return vodContentList != null && kinoPlayMovies != null
    }

    /**
     * Clear cache (for memory management if needed)
     */
    fun clear() {
        vodContentList = null
        kinoPlayMovies = null
        supabaseMovies = null
        top10Movies = null
        newestMovies = null
        moviesByGenre = emptyMap()
        sliderMovies = null
        supabaseInitialized = false
    }

    // ===== NOWE METODY DLA SUPABASE =====

    /**
     * Inicjalizacja danych z Supabase
     * Pobiera wszystkie filmy, Top 10, najnowsze i cache po gatunkach
     * Fallback do lokalnego JSON jeśli Supabase nie działa
     */
    suspend fun initializeFromSupabase(context: Context) {
        if (supabaseInitialized) {
            Log.d(TAG, "Supabase already initialized, skipping...")
            return
        }

        try {
            Log.d(TAG, "Initializing from Supabase - SLIDER FIRST strategy...")

            // ========================================
            // 0. FALLBACK: Załaduj JSON natychmiast (channele mają dane od razu)
            // ========================================
            initialize(context)
            Log.d(TAG, "JSON fallback loaded: ${kinoPlayMovies?.size ?: 0} movies")

            // ========================================
            // 1. SLIDER FIRST - jedyne na co user czeka
            // Single Source of Truth: cena jest w tym samym rekordzie SupabaseMovie
            // ========================================
            val sliderData = SupabaseMoviesRepository.fetchSliderMovies()

            sliderMovies = sliderData
                .filter { movie -> movie.title.isNotBlank() && !movie.backdrop_url.isNullOrBlank() }
                .map { movie -> movie.toVodSlideData() }
            Log.d(TAG, "SLIDER READY: ${sliderMovies?.size ?: 0} movies (user can see slider now!)")

            // ========================================
            // 2. REMAINING DATA - fire-and-forget w tle (zastąpi JSON gdy gotowe)
            // ========================================
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d(TAG, "Loading remaining data in background...")

                    val allMovies = SupabaseMoviesRepository.fetchAllMovies()
                    if (allMovies.isNotEmpty()) {
                        supabaseMovies = allMovies.map { movie -> movie.toVodContent() }

                        val top10 = SupabaseMoviesRepository.fetchTop10()
                        top10Movies = top10.map { movie -> movie.toVodContent() }

                        val newest = SupabaseMoviesRepository.fetchNewest(20)
                        newestMovies = newest.map { movie -> movie.toVodContent() }

                        // Cache po gatunkach
                        val genres = listOf("Akcja", "Komedia", "Horror", "Dramat", "Biograficzny", "Thriller", "Sci-Fi", "Romans")
                        moviesByGenre = genres.associateWith { genreName ->
                            allMovies.filter { movie ->
                                movie.genre?.contains(genreName, ignoreCase = true) == true
                            }.map { filteredMovie -> filteredMovie.toVodContent() }
                        }

                        // Aktualizuj też kinoPlayMovies dla kompatybilności
                        kinoPlayMovies = supabaseMovies

                        supabaseInitialized = true
                        Log.d(TAG, "Background data ready: ${allMovies.size} movies, ${top10.size} top10, ${newest.size} newest")

                    } else {
                        Log.w(TAG, "No movies from Supabase in background, falling back to JSON")
                        initialize(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background loading failed, falling back to JSON", e)
                    initialize(context)
                }
            }
            // Funkcja zwraca NATYCHMIAST po załadowaniu slidera
            // Pozostałe dane ładują się w tle

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load slider from Supabase, falling back to JSON", e)
            initialize(context)
        }
    }

    /**
     * Pobierz filmy Top 10 (z Supabase)
     * Jeśli brak - zwraca pierwsze 10 z kinoPlayMovies
     */
    fun getTop10(): List<VodContent> {
        return top10Movies?.takeIf { it.isNotEmpty() }
            ?: kinoPlayMovies?.take(10)
            ?: emptyList()
    }

    /**
     * Pobierz najnowsze filmy (z Supabase)
     * Jeśli brak - zwraca pierwsze 20 z kinoPlayMovies
     */
    fun getNewest(): List<VodContent> {
        return newestMovies?.takeIf { it.isNotEmpty() }
            ?: kinoPlayMovies?.take(20)
            ?: emptyList()
    }

    /**
     * Pobierz filmy po gatunku (z Supabase cache)
     * Jeśli brak - filtruje kinoPlayMovies po kategorii
     */
    fun getByGenre(genre: String): List<VodContent> {
        // Najpierw sprawdź cache z Supabase
        moviesByGenre[genre]?.takeIf { it.isNotEmpty() }?.let { return it }

        // Fallback: filtruj lokalny cache
        return kinoPlayMovies?.filter { movie ->
            movie.category.contains(genre, ignoreCase = true)
        } ?: emptyList()
    }

    /**
     * Sprawdź czy dane z Supabase są załadowane
     */
    fun isSupabaseInitialized(): Boolean = supabaseInitialized

    /**
     * Pobierz wszystkie filmy z Supabase (lub fallback)
     */
    fun getSupabaseMovies(): List<VodContent> {
        return supabaseMovies ?: kinoPlayMovies ?: emptyList()
    }

    /**
     * Pobierz slider movies z Supabase (bez fallbacku)
     * Zwraca pustą listę jeśli dane nie są jeszcze załadowane
     */
    fun getSliderMovies(): List<VodSlideData> {
        return sliderMovies ?: emptyList()
    }

    // ===== KONIEC NOWYCH METOD =====

    // Private loaders - same implementation as original functions

    // Delegate to the top-level loader in version001/Version001Screen.kt — single source
    // of truth for WIDEO content (merges vod_data.json + viaplay_filmy.json + future packs).
    // Previously this was a duplicate that loaded only vod_data.json, silently shadowing the
    // top-level loader and causing newly-added Viaplay items to never reach the cache.
    private fun loadVodContentFromAssets(context: Context): List<VodContent> {
        return com.uxellence.tv.v3.version001.loadVodContentFromAssets(context)
    }

    private fun loadKinoPlayMoviesFromAssets(context: Context): List<VodContent> {
        return try {
            val inputStream = context.assets.open("kino_play.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val movies = json.decodeFromString<List<PlayNowMovie>>(jsonString)

            movies.map { item ->
                VodContent(
                    id = "kino_${item.tytul.hashCode()}_${item.link.hashCode()}",  // Unique ID for focus restoration
                    title = item.tytul,
                    description = item.opis,
                    category = item.kategoria,
                    imageUrl = item.plakat,
                    channelLogoUrl = "",
                    link = item.link
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
