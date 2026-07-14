package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DemoLive"
private const val SEEK_STEP_MS = 10_000L
private const val EPG_TIMEOUT_MS = 5_000L
private const val PLAYER_UI_TIMEOUT_MS = 10_000L
// Stargaze — realny kanał FAST (Tivio Studio, w ofercie Play), stream HLS.
// Publicznego EPG brak (Tivio API wymaga klucza SDK) → syntetyczna ramówka
// z realnych formatów kanału w buildStargazeRow().
private const val STARGAZE_STREAM_URL =
    "https://play.streaming.tivio.studio/channels/MQniU6Lt1LKPU0V3UwJl/index.m3u8"
// DASH-IF livesim2 — publiczny symulator live (DASH) do testów playerów:
// tsbd_3600 = okno DVR 1 h; Manifest_thumbs = tor miniatur trick-play
// (AdaptationSet image/jpeg, kafelek 160x90 co 2 s pod thumbs/{unix/2}.jpg).
private const val LIVESIM2_STREAM_URL =
    "https://livesim2.dashif.org/livesim2/tsbd_3600/testpic_2s/Manifest_thumbs.mpd"
private const val LIVESIM2_THUMB_BASE =
    "https://livesim2.dashif.org/livesim2/tsbd_3600/testpic_2s/thumbs"
// Realny MATERIAŁ wideo jako live (livesim2 zapętla klip: safari w Namibii),
// avc+aac (gra wszędzie), okno DVR 1 h; bez toru miniatur → taśma ze zrzutów.
private const val SAFARI_STREAM_URL =
    "https://livesim2.dashif.org/livesim2/tsbd_3600/ads/namibia_safari_ad/manifest.mpd"

/** URL kafelka trick-play dla kanału (null = kanał bez toru miniatur). */
private fun trickThumbUrl(channelId: String?, wallMs: Long): String? = when (channelId) {
    "dashif" -> "$LIVESIM2_THUMB_BASE/${wallMs / 2000}.jpg"   // duration=2s, numeracja od epochy
    else -> null
}

/**
 * Długość pętli materiału kanału live (livesim2 zapętla asset zakotwiczony w epoce).
 * Pozwala mapować miniaturki CYKLICZNIE: klatka o fazie (wall mod loop) pasuje do
 * każdego slotu o tej samej fazie — po obejrzeniu jednej pętli taśma ma właściwe
 * kadry na całym oknie DVR. null = materiał bez pętli (dopasowanie po czasie).
 */
private fun liveLoopMs(channelId: String?): Long? = when (channelId) {
    "safari" -> 10_000L   // ads/namibia_safari_ad: loop 10000 ms (strona /assets livesim2)
    else -> null
}

/**
 * Widok wideo demo: TextureView (nie SurfaceView — z-order w Compose) z zachowaniem
 * proporcji obrazu (letterbox przez skalowanie TextureView) + przekazywaniem klawiszy
 * do handlera ekranu (wzorzec z VodPlayerScreen — fokus okna potrafi trafić w View).
 */
private class DemoVideoView(
    context: android.content.Context,
    private val onKey: (Int, Int) -> Boolean   // (keyCode, repeatCount)
) : android.widget.FrameLayout(context) {

    private val textureView = android.view.TextureView(context)
    private var attachedPlayer: com.google.android.exoplayer2.ExoPlayer? = null
    // Czarna zasłona na czas przełączania playerów: TextureView trzyma ostatnią
    // klatkę POPRZEDNIEGO kanału aż nowy wyrenderuje pierwszą — bez zasłony widać
    // stopklatkę starego materiału ("kanał się nie włącza").
    private val blackCover = android.view.View(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        visibility = GONE
    }

    private val videoListener = object : com.google.android.exoplayer2.Player.Listener {
        override fun onVideoSizeChanged(videoSize: com.google.android.exoplayer2.video.VideoSize) {
            applyAspect(videoSize)
        }

        override fun onRenderedFirstFrame() {
            blackCover.visibility = GONE
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(
            textureView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER)
        )
        addView(
            blackCover,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        isFocusable = true
        isFocusableInTouchMode = false
    }

    /** Zrzut bieżącej klatki (miniaturki taśmy dla realnego live). */
    fun captureFrame(): android.graphics.Bitmap? =
        if (textureView.isAvailable) textureView.getBitmap(320, 180) else null

    fun attach(player: com.google.android.exoplayer2.ExoPlayer?) {
        android.util.Log.i("DemoLive", "VideoView.attach: same=${attachedPlayer === player} new=${player?.hashCode()}")
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(videoListener)
        // KLUCZOWE przy przełączaniu playerów (barker ⇄ live HLS): odepnij TextureView
        // od starego playera — inaczej stary trzyma surface i na ekranie zostaje jego
        // zamrożona klatka, a nowy gra bez obrazu.
        attachedPlayer?.clearVideoTextureView(textureView)
        // Zasłoń stopklatkę poprzedniego kanału do pierwszej klatki nowego
        blackCover.visibility = VISIBLE
        attachedPlayer = player
        if (player != null) {
            player.addListener(videoListener)
            player.setVideoTextureView(textureView)
            applyAspect(player.videoSize)
        }
    }

    /** Letterbox: dopasuj wymiary TextureView do proporcji wideo (środek, czarne pasy). */
    private fun applyAspect(videoSize: com.google.android.exoplayer2.video.VideoSize) {
        if (videoSize.height == 0 || videoSize.width == 0 || width == 0 || height == 0) return
        val videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        val viewAspect = width.toFloat() / height.toFloat()
        val lp = textureView.layoutParams as LayoutParams
        if (videoAspect > viewAspect) {
            lp.width = LayoutParams.MATCH_PARENT
            lp.height = (width / videoAspect).toInt()
        } else {
            lp.width = (height * videoAspect).toInt()
            lp.height = LayoutParams.MATCH_PARENT
        }
        textureView.layoutParams = lp
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        attachedPlayer?.let { applyAspect(it.videoSize) }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            onKey(event.keyCode, event.repeatCount)
        ) return true
        return super.dispatchKeyEvent(event)
    }
}

/**
 * Kanał barker: schedule + kontroler + filmstrip + stan pobierania (Compose states —
 * odczyt w kompozycji subskrybuje recompose). Primary (DEMO TV) pobierany na starcie;
 * pozostałe leniwie przy pierwszym dostrojeniu.
 */
private class BarkerBundle(
    val channelId: String,
    val name: String,
    val number: Int,
    val schedule: BarkerSchedule,
    context: android.content.Context,
    dvrWindowMs: Long = 3_600_000L,
    // Kanał z nagrania: realny start nagrania (manifest.recordedAtWallMs) —
    // ramówka EPG zgodna z godzinami emisji zamiast wspólnej osi 9:00
    antennaStartWallMs: Long = BarkerSchedule.barkerStartWallMs()
) {
    val controller =
        DemoChannelPlayerController(context, schedule, dvrWindowMs, antennaStartWallMs)
    val filmstrip = DemoFilmstripProvider(schedule)
    val ready = androidx.compose.runtime.mutableStateOf(false)
    val downloading = androidx.compose.runtime.mutableStateOf(false)
    val progress = androidx.compose.runtime.mutableIntStateOf(0)
    val progressLabel = androidx.compose.runtime.mutableStateOf("")
}

/**
 * DEMO LIVE SCREEN — symulacja barker channel z ramówką (pełny flow):
 *
 *   EPG (start; replika EpgDayScreen) --OK na programie--> PLAYER_UI:BUTTONS
 *     | BACK / timeout 12s
 *     v
 *   FULLSCREEN --OK/UP/DOWN--> EPG, --LEFT/RIGHT--> PLAYER_UI:STRIP (przewijanie)
 *
 *   PLAYER_UI (jeden widok, trzy strefy — wg designu Play):
 *     BUTTONS (fokus "Zatrzymaj") --UP--> STRIP (taśma miniatur + kursor)
 *     BUTTONS --DOWN--> DESCRIPTION (pełny opis, wideo w PIP)
 *     STRIP --OK--> seek + powrót na BUTTONS;  BACK: STRIP/DESCRIPTION → BUTTONS → EPG
 */
@Composable
fun DemoLiveScreen(
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    // ===== KANAŁY BARKER (lokalne pliki + wspólna oś wall-clock od 9:00) =====
    // Każdy bundle = schedule (N materiałów) + kontroler + filmstrip. DEMO TV jest
    // primary (pobierany na starcie, blokuje isReady); pozostałe pobierane LENIWIE
    // przy pierwszym dostrojeniu (plansza z postępem).
    val barkers = remember {
        fun item(
            url: String, title: String, genre: String, year: String, country: String,
            age: String, desc: String, cover: String? = null, nominal: Long
        ) = BarkerSchedule.BarkerItem(url, title, genre, year, country, age, desc, cover, nominal)

        linkedMapOf(
            "demo" to BarkerBundle(
                channelId = "demo", name = "DEMO TV", number = 122,
                schedule = BarkerSchedule(listOf(
                    item(
                        "https://archive.org/download/Sintel/sintel-2048-stereo_512kb.mp4",
                        "Sintel", "fantasy", "2010 r.", "Holandia", "12 lat",
                        "Samotna wojowniczka Sintel przemierza świat w poszukiwaniu Scales — " +
                            "małego smoka, którego niegdyś uratowała i wychowała, a który został jej brutalnie " +
                            "odebrany. Wędrówka przez lodowe pustkowia i mroczne jaskinie wystawi jej " +
                            "determinację na ostateczną próbę. Nagradzany film studia Blender.",
                        "https://m.media-amazon.com/images/S/pv-target-images/6faeb35e463ad90c72c97d47d06367ec7bc4d9d0be63659d6c9fb18777cf3b12.png",
                        888_000L
                    ),
                    item(
                        "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                        "Big Buck Bunny", "animacja", "2008 r.", "Holandia", "7 lat",
                        "Ogromny, dobroduszny królik budzi się pewnego ranka, by cieszyć się " +
                            "urokami leśnej polany. Sielankę przerywa trójka złośliwych gryzoni, która dla zabawy " +
                            "dręczy mniejsze zwierzęta. Gdy ich ofiarą padają ukochane motyle królika, " +
                            "łagodny olbrzym postanawia dać łobuzom nauczkę.",
                        "https://m.media-amazon.com/images/M/MV5BMjMzNDM1ZmEtYzRjOC00Nzg5LWFlZTAtMTA1M2I0NDc2Njg3XkEyXkFqcGc@._V1_.jpg",
                        596_000L
                    )
                )),
                context = context
            ),
            "kino" to BarkerBundle(
                channelId = "kino", name = "Kino", number = 127,
                schedule = BarkerSchedule(listOf(
                    item(
                        "https://archive.org/download/Tears-of-Steel/tears_of_steel_720p.mp4",
                        "Tears of Steel", "sci-fi", "2012 r.", "Holandia", "12 lat",
                        "Grupa naukowców i żołnierzy próbuje odzyskać Amsterdam z rąk zbuntowanych " +
                            "robotów, odtwarzając wydarzenia sprzed lat. Aktorski film studia Blender " +
                            "łączący zdjęcia na żywo z efektami CGI.",
                        "https://archive.org/services/img/Tears-of-Steel", 734_000L
                    ),
                    item(
                        "https://archive.org/download/CosmosLaundromatFirstCycle/Cosmos%20Laundromat%20-%20First%20Cycle%20(1080p).mp4",
                        "Cosmos Laundromat", "animacja", "2015 r.", "Holandia", "12 lat",
                        "Samobójczo nastawiony baran Franck dostaje od tajemniczego Victora " +
                            "propozycję nie do odrzucenia: każde życie, jakie zechce. Surrealistyczna " +
                            "animacja studia Blender.",
                        "https://archive.org/services/img/CosmosLaundromatFirstCycle", 730_000L
                    )
                )),
                context = context
            ),
            "kosmos" to BarkerBundle(
                channelId = "kosmos", name = "Kosmos", number = 128,
                schedule = BarkerSchedule(listOf(
                    item(
                        "https://images-assets.nasa.gov/video/NHQ_2019_0311_Go%20Forward%20to%20the%20Moon/NHQ_2019_0311_Go%20Forward%20to%20the%20Moon~small.mp4",
                        "Naprzód na Księżyc", "dokument", "2019 r.", "USA", "bez ograniczeń",
                        "NASA przedstawia program Artemis — plan powrotu ludzi na Księżyc " +
                            "i pierwszy krok w stronę Marsa. Materiał NASA (domena publiczna).",
                        "https://images-assets.nasa.gov/video/NHQ_2019_0311_Go%20Forward%20to%20the%20Moon/NHQ_2019_0311_Go%20Forward%20to%20the%20Moon~thumb.jpg",
                        218_000L
                    ),
                    item(
                        "https://images-assets.nasa.gov/video/Artemis%20I%20Launches%20to%20the%20Moon%20%28Official%20NASA%20Recap%29/Artemis%20I%20Launches%20to%20the%20Moon%20%28Official%20NASA%20Recap%29~medium.mp4",
                        "Artemis I — start", "dokument", "2022 r.", "USA", "bez ograniczeń",
                        "Oficjalne podsumowanie startu misji Artemis I — pierwszego lotu rakiety " +
                            "SLS i statku Orion w stronę Księżyca. Materiał NASA (domena publiczna).",
                        "https://images-assets.nasa.gov/video/Artemis%20I%20Launches%20to%20the%20Moon%20%28Official%20NASA%20Recap%29/Artemis%20I%20Launches%20to%20the%20Moon%20%28Official%20NASA%20Recap%29~thumb.jpg",
                        205_000L
                    )
                )),
                context = context
            ),
            "kids" to BarkerBundle(
                channelId = "kids", name = "Retro Kids", number = 129,
                schedule = BarkerSchedule(listOf(
                    item(
                        "https://archive.org/download/Popeye_forPresident/Popeye_forPresident_512kb.mp4",
                        "Popeye for President", "kreskówka", "1956 r.", "USA", "bez ograniczeń",
                        "Popeye i Bluto rywalizują o głos Olive w wyborach prezydenckich. " +
                            "Klasyczna kreskówka z domeny publicznej.",
                        "https://archive.org/services/img/Popeye_forPresident", 364_000L
                    ),
                    item(
                        "https://archive.org/download/superman_1941/superman_1941_512kb.mp4",
                        "Superman: The Mad Scientist", "kreskówka", "1941 r.", "USA", "7 lat",
                        "Pierwszy animowany film o Supermanie — Człowiek ze Stali kontra szalony " +
                            "naukowiec i jego promień zagłady. Studio Fleischera, domena publiczna.",
                        "https://archive.org/services/img/superman_1941", 620_000L
                    )
                )),
                context = context
            )
        ).also { map ->
            // Kanały z NAGRAŃ realnej anteny: paczki program_NN.mp4 + manifest.json
            // (tools/record_tvp1.sh / cut_by_epg.py) wgrane do katalogu aplikacji
            // (pamięć wewnętrzna, karta SD lub filesDir przez run-as). Każdy
            // podkatalog z manifestem = osobny kanał; brak paczek = brak kanałów.
            RecordedChannelLoader.loadAll(context).forEach { rec ->
                map[rec.id] = BarkerBundle(
                    channelId = rec.id,
                    name = rec.name,
                    number = rec.number,
                    schedule = BarkerSchedule(rec.items),
                    context = context,
                    dvrWindowMs = 24L * 3_600_000L,  // przewijanie po całym nagraniu
                    // Oś = realny start nagrania (ramówka zgodna z godzinami anteny)
                    antennaStartWallMs = rec.recordedAtWallMs
                )
            }
        }
    }
    val demoBundle = barkers.getValue("demo")
    val controller = demoBundle.controller
    val filmstrip = demoBundle.filmstrip

    var layer by remember { mutableStateOf(DemoLayer.EPG) }
    var isReady by remember { mutableStateOf(false) }
    // Player jako stan Compose — inaczej update AndroidView nie wykona się ponownie
    // po utworzeniu playera (lambda obserwuje wyłącznie snapshot state)
    var playerRef by remember { mutableStateOf<com.google.android.exoplayer2.ExoPlayer?>(null) }
    var downloadLabel by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var currentVirtualMs by remember { mutableLongStateOf(0L) }
    var liveEdgeMs by remember { mutableLongStateOf(0L) }
    // Ile ms za live na realnym streamie (0 = na live); do kreski pozycji i "Wróć do live"
    var liveBehindMs by remember { mutableLongStateOf(0L) }
    // Miniaturki taśmy dla realnego live: ring buffer zrzutów klatek z TextureView
    // (wallMs → bitmapa), robionych co ~3 s podczas oglądania. Brak materiału sprzed
    // dostrojenia — te sloty zostają puste (jak poza DVR na barkerze).
    var videoViewRef by remember { mutableStateOf<DemoVideoView?>(null) }
    val liveThumbs = remember { ArrayDeque<Pair<Long, android.graphics.Bitmap>>() }
    // Prawdziwe miniaturki trick-play (kanały z torem obrazków, np. DASH-IF livesim2):
    // cache URL→bitmapa + zbiór trwających pobrań; po fetchu taśma się odświeża.
    val trickThumbCache = remember { HashMap<String, android.graphics.Bitmap?>() }
    val trickFetching = remember { mutableSetOf<String>() }
    val demoScope = rememberCoroutineScope()
    var isPaused by remember { mutableStateOf(false) }
    // Dostrojony kanał: 0 = DEMO TV (gra barker), >0 = realny kanał. Kanał z niepustym
    // streamUrl (np. Stargaze — HLS z Tivio) gra PRAWDZIWE live przez livePlayer;
    // pozostałe realne kanały pokazują planszę "Brak live". UI playera działa tak samo.
    var tunedChannelIndex by remember { mutableIntStateOf(0) }
    // Drugi ExoPlayer do realnego live (HLS) — barker (controller.player) zostaje
    // nietknięty i wraca po przełączeniu na DEMO TV.
    var livePlayer by remember { mutableStateOf<com.google.android.exoplayer2.ExoPlayer?>(null) }
    // Błąd odtwarzania live (DRM/geo/sieć) → plansza "Kanał niedostępny" zamiast
    // cichego czarnego ekranu; null = gra normalnie
    var livePlaybackError by remember { mutableStateOf<String?>(null) }

    /** Przełącz źródło wideo: url=null/blank → barker; inaczej realne live (HLS/DASH). */
    fun tuneLive(url: String?) {
        livePlayer?.release()
        livePlayer = null
        livePlaybackError = null
        liveBehindMs = 0L
        // Miniaturki POPRZEDNIEGO kanału nie mogą wyciec na nowy — czyść ring zrzutów
        liveThumbs.forEach { it.second.recycle() }
        liveThumbs.clear()
        if (url.isNullOrBlank()) {
            playerRef = controller.player
            return
        }
        val exo = com.google.android.exoplayer2.ExoPlayer.Builder(context).build()
        exo.setMediaItem(com.google.android.exoplayer2.MediaItem.fromUri(url))
        exo.playWhenReady = true
        // Okno live jest krótkie (~38 s): po dłuższej pauzie/cofnięciu pozycja wypada
        // z playlisty → BEHIND_LIVE_WINDOW. Standardowe recovery: resnap do live.
        exo.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                if (error.errorCode ==
                    com.google.android.exoplayer2.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
                ) {
                    Log.i(TAG, "live: behind window → resnap do live edge")
                    exo.seekToDefaultPosition()
                    exo.prepare()
                    exo.play()
                    isPaused = false
                } else {
                    Log.e(TAG, "live playback error: ${error.errorCodeName}")
                    livePlaybackError = error.errorCodeName
                }
            }
        })
        exo.prepare()
        livePlayer = exo
        playerRef = exo
        Log.i(TAG, "tuneLive → $url")
        // WATCHDOG: realny stream potrafi utknąć w buforowaniu BEZ błędu (sieć/DNS)
        // — czarna zasłona czeka na pierwszą klatkę w nieskończoność ("kanał się
        // nie włącza"). Po 6 s bez READY ponawiamy prepare; po kolejnych 8 s
        // pokazujemy istniejącą planszę "Kanał niedostępny".
        demoScope.launch {
            delay(6_000)
            if (livePlayer === exo && exo.playbackState != com.google.android.exoplayer2.Player.STATE_READY &&
                livePlaybackError == null
            ) {
                Log.w(TAG, "live watchdog: brak READY po 6 s → retry prepare")
                exo.stop()
                exo.setMediaItem(com.google.android.exoplayer2.MediaItem.fromUri(url))
                exo.prepare()
                exo.play()
                delay(8_000)
                if (livePlayer === exo && exo.playbackState != com.google.android.exoplayer2.Player.STATE_READY &&
                    livePlaybackError == null
                ) {
                    Log.e(TAG, "live watchdog: dalej brak READY → plansza")
                    livePlaybackError = "TIMEOUT_BUFOROWANIA"
                }
            }
        }
    }

    // Warstwa EPG: wiersz 0 = DEMO TV (sztuczna ramówka), 1..N = prawdziwe kanały z EPG
    var epgRows by remember { mutableStateOf<List<com.uxellence.tv.v3.epg.ChannelEpgRow>>(emptyList()) }
    // Trigger odświeżenia EPG (openEpg jest zdefiniowane niżej — lokalne funkcje
    // nie mogą być wołane przed deklaracją, więc ensureBarkerReady bije w licznik)
    var epgRefreshTick by remember { mutableIntStateOf(0) }

    // ===== Routing barkerów =====
    /** Bundle barkera dla wiersza EPG (null = kanał nie-barker: live stream / realny). */
    fun barkerFor(channelIdx: Int): BarkerBundle? =
        barkers[epgRows.getOrNull(channelIdx)?.channel?.id]

    /** Bundle barkera aktualnie dostrojonego kanału (null = live/realny). */
    fun activeBarker(): BarkerBundle? = barkerFor(tunedChannelIndex)

    /** Kontroler osi/playbacku dla dostrojonego kanału (fallback: primary demo). */
    fun activeCtl(): DemoChannelPlayerController = activeBarker()?.controller ?: controller

    /**
     * Kontroler OSI CZASU kanału pod danym indeksem EPG: barker (w tym kanał
     * z nagrania — własna oś od recordedAtWallMs!) → jego kontroler; realny
     * kanał → primary. Wszystkie konwersje wall↔virtual w kontekście KONKRETNEGO
     * kanału muszą iść przez jego oś — primary (9:00) dawał rozjazd o 1 h na
     * kanałach z nagrania (nagranie od 8:00).
     */
    fun ctlFor(channelIdx: Int): DemoChannelPlayerController =
        barkerFor(channelIdx)?.controller ?: controller

    /**
     * Snap kursora przewijania do granicy materiału: krok (zwłaszcza przyspieszony
     * przy szybkim przewijaniu) NIE przeskakuje zmiany materiału — kursor zatrzymuje
     * się na starcie nowego bloku, gdzie taśma pokazuje kafelek "Przechodzisz do…".
     */
    fun snapScrubToBlockEdge(oldMs: Long, candidateMs: Long, direction: Int): Long {
        val sched = activeBarker()?.schedule ?: demoBundle.schedule
        return if (direction > 0) {
            val end = sched.epgBlockAt(oldMs).endVirtualMs
            if (oldMs < end && candidateMs > end) end else candidateMs
        } else {
            // Blok dla pozycji tuż PRZED kursorem — stojąc na granicy, cofamy się
            // w głąb poprzedniego materiału aż do JEGO startu, nie w miejscu
            val start = sched.epgBlockAt((oldMs - 1).coerceAtLeast(0L)).startVirtualMs
            if (oldMs > start && candidateMs < start) start else candidateMs
        }
    }

    /** Leniwe pobranie materiałów barkera (pierwsze dostrojenie); po sukcesie podpina obraz. */
    fun ensureBarkerReady(bundle: BarkerBundle) {
        if (bundle.ready.value || bundle.downloading.value) return
        bundle.downloading.value = true
        demoScope.launch {
            try {
                val files = ArrayList<java.io.File>()
                bundle.schedule.items.forEachIndexed { i, it ->
                    bundle.progressLabel.value = "${it.title} (${i + 1}/${bundle.schedule.items.size})"
                    files += bundle.controller.downloadToCache(it.url) { p ->
                        bundle.progress.intValue = p
                    }
                }
                bundle.controller.preparePlayer(files)
                bundle.filmstrip.startExtraction(files.map { f -> f.absolutePath })
                bundle.ready.value = true
                if (activeBarker() === bundle) {
                    barkers.values.forEach { if (it !== bundle) it.controller.player?.pause() }
                    bundle.controller.seekToLiveEdge()   // dołącz na żywo, nie na pozycji z prepare
                    playerRef = bundle.controller.player
                    isPaused = false
                }
                // Realne duracje z Timeline nadpisały nominalne → przelicz bloki ramówki
                // (inaczej mini-EPG pokazuje granice programów z przybliżonych długości).
                // Krótka zwłoka: onTimelineChanged przychodzi async po prepare.
                delay(300)
                epgRefreshTick++
                Log.i(TAG, "barker ${bundle.channelId} ready")
            } catch (e: Exception) {
                Log.e(TAG, "barker ${bundle.channelId} download failed: ${e.message}")
                bundle.progressLabel.value = "Błąd pobierania — spróbuj ponownie"
            } finally {
                bundle.downloading.value = false
            }
        }
    }

    /** Dostrój kanał barker: zwolnij live playera, wznow ten, pauza pozostałych. */
    fun tuneBarker(bundle: BarkerBundle) {
        livePlayer?.release()
        livePlayer = null
        livePlaybackError = null
        liveBehindMs = 0L
        liveThumbs.forEach { it.second.recycle() }
        liveThumbs.clear()
        barkers.values.forEach { if (it !== bundle) it.controller.player?.pause() }
        if (bundle.ready.value) {
            // Wejście na kanał = live edge (jak prawdziwa TV) — nie stara pozycja
            // sprzed pauzy; seek wymusza też natychmiastowy render pierwszej klatki
            bundle.controller.seekToLiveEdge()
            playerRef = bundle.controller.player
            isPaused = false
        } else {
            playerRef = null   // plansza pobierania zamiast zamrożonej klatki
            ensureBarkerReady(bundle)
        }
    }
    var realChannelRows by remember { mutableStateOf<List<com.uxellence.tv.v3.epg.ChannelEpgRow>>(emptyList()) }
    var epgChannelIndex by remember { mutableIntStateOf(0) }
    // Warstwa EPG jak pod Telewizją: start = pasek 1 kanału (zatunowanego), pierwszy
    // DOWN rozwija do 3 kanałów; UP w trybie 1-kanałowym nic nie robi
    var epgExpanded by remember { mutableStateOf(false) }
    val epgProgramIndex = remember { mutableStateMapOf<Int, Int>() }
    var epgInteractionAt by remember { mutableLongStateOf(0L) }
    // Czas fokusu siatki EPG (TIME SYNC) — start fokusowanego programu;
    // wszystkie wiersze przewijają się do programu emitowanego o tym czasie
    var epgFocusedTime by remember { mutableStateOf(java.time.Instant.now()) }

    // PLAYER_UI: strefa + fokus przycisków (0..4) + kursor taśmy
    var playerZone by remember { mutableStateOf(PlayerZone.BUTTONS) }
    var playerButtonsFocus by remember { mutableIntStateOf(0) }
    // Klawisz "3": wygląd paska przycisków playera — false=tekstowy, true=ikonowy (Figma).
    // Stan trzymany w DemoPlayerPrefs (singleton + SharedPreferences): przeżywa nawigację
    // i restart, aż do ponownego "3". Odczyt .value subskrybuje recompose.
    remember { DemoPlayerPrefs.load(context) }
    val useFigmaButtons = DemoPlayerPrefs.useFigmaButtons.value
    var scrubCursorMs by remember { mutableLongStateOf(0L) }
    var playerInteractionAt by remember { mutableLongStateOf(0L) }
    var filmstripFrames by remember { mutableStateOf<List<Pair<Long, Bitmap?>>>(emptyList()) }
    // Scrub UX: pozycja sprzed rozpoczęcia przewijania (WSTECZ wraca do niej),
    // oraz timestamp ostatniej próby przewinięcia do przodu na kanale BACKWARD_ONLY
    // (wyzwala zanikający komunikat „Przewijanie do przodu nie jest dostępne")
    var scrubStartVirtualMs by remember { mutableLongStateOf(0L) }
    // DEMO: override polityki przewijania DEMO TV (klawisz "2") — by pokazać
    // blokady na żywym wideo (jedyny kanał z materiałem). null = klasyfikacja auto
    var demoPolicyOverride by remember { mutableStateOf<DemoSeekPolicy?>(null) }
    // Detal programu (strefa DETAIL): renderowany PRAWDZIWYM MovieDetailScreen
    // (tryb WIDEO — identyczny wygląd jak detale programów pod zakładką Wideo).
    // detailSlide = dane do ekranu; timing/isDemo sterują akcją "Oglądaj";
    // fromEpg steruje dokąd wraca BACK; detailStartVirtualMs = cel timeshiftu
    var detailSlide by remember { mutableStateOf<com.uxellence.tv.v3.VodSlideData?>(null) }
    var detailTiming by remember { mutableStateOf(BlockTiming.CURRENT) }
    var detailIsDemo by remember { mutableStateOf(true) }
    var detailFromEpg by remember { mutableStateOf(false) }
    var detailStartVirtualMs by remember { mutableLongStateOf(0L) }
    // Zakres czasu + logo kanału do nakładek detalu wg Figmy (info_line + logo w rogu)
    var detailStartWallMs by remember { mutableLongStateOf(0L) }
    var detailEndWallMs by remember { mutableLongStateOf(0L) }
    var detailChannelLogoUrl by remember { mutableStateOf<String?>(null) }
    var detailChannelName by remember { mutableStateOf("DEMO TV") }
    // Flow nagrywania (Figma "Nagrywanie serii"): kandydat zlecenia (modal
    // otwarty gdy != null) + toast potwierdzenia (nagłówek, tytuł)
    var recordingCandidate by remember {
        mutableStateOf<DemoRecordingScheduler.ScheduledRecording?>(null)
    }
    var recordingToast by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Pasek powiadomien dema (995x104 na y=841, wg dostarczonego layoutu) —
    // zamiast systemowego android.widget.Toast
    var demoToast by remember { mutableStateOf<String?>(null) }
    remember { DemoRecordingScheduler.init(context); true }
    var detailChannelNumber by remember { mutableIntStateOf(122) }
    var detailChannelIndex by remember { mutableIntStateOf(0) }  // indeks w epgRows (tuning z detalu)

    // Akceleracja seeka (wzorzec z VodPlayerScreen)
    var rapidPressCount by remember { mutableIntStateOf(0) }
    var lastPressTimeNano by remember { mutableLongStateOf(0L) }
    // Granica materiału, na której kursor stanął podczas trzymania strzałki
    // (-1 = brak locka); patrz scrubStepWithSnap
    var scrubSnapLockMs by remember { mutableLongStateOf(-1L) }
    // repeatCount ostatniego KeyDown (0 = nowe fizyczne naciśnięcie, >0 = trzymanie)
    var scrubKeyRepeat by remember { mutableIntStateOf(0) }
    fun getSeekStep(): Long {
        val now = System.nanoTime()
        val elapsedMs = (now - lastPressTimeNano) / 1_000_000
        lastPressTimeNano = now
        if (elapsedMs < 600) rapidPressCount++ else rapidPressCount = 0
        val multiplier = when {
            rapidPressCount >= 25 -> 10
            rapidPressCount >= 15 -> 5
            rapidPressCount >= 6 -> 2
            else -> 1
        }
        return SEEK_STEP_MS * multiplier
    }

    /**
     * PRAWDZIWY czas ścienny bieżącej pozycji odtwarzania live wg osi z manifestu
     * (windowStartTimeMs + position) — niezależny od zegara urządzenia (emulator
     * potrafi odjechać po uśpieniu Maca, a livesim2 numeruje kafelki po epochce).
     */
    fun livePlaybackWallMs(): Long? {
        val p = livePlayer ?: return null
        val t = p.currentTimeline
        if (t.isEmpty) return null
        val w = t.getWindow(p.currentMediaItemIndex, com.google.android.exoplayer2.Timeline.Window())
        if (w.windowStartTimeMs == com.google.android.exoplayer2.C.TIME_UNSET) return null
        return w.windowStartTimeMs + p.currentPosition
    }

    fun updateFilmstrip(centerMs: Long) {
        val barker = barkers[epgRows.getOrNull(tunedChannelIndex)?.channel?.id]
        filmstripFrames = if (barker != null && barker.ready.value) {
            barker.filmstrip.framesAround(
                centerVirtualMs = centerMs,
                liveEdgeVirtualMs = barker.controller.virtualNow(),
                stepMs = SEEK_STEP_MS,
                sideCount = 3,
                dvrStartVirtualMs = barker.controller.dvrStartMs()
            )
        } else if (livePlayer != null) {
            // Realny stream live. Kanał z torem trick-play (DASH-IF): prawdziwe kafelki
            // JPEG pobierane po URL (także sprzed dostrojenia!). Bez toru (Stargaze):
            // fallback — zrzuty TextureView z ring buffera (tylko obejrzany materiał).
            val chanId = epgRows.getOrNull(tunedChannelIndex)?.channel?.id
            // Korekta zegara: numer kafelka liczymy z PRAWDZIWEGO czasu treści
            // (oś manifestu DASH), nie z zegara urządzenia — emulator potrafi
            // odjechać i wtedy prosiliśmy o kafelki "z przyszłości" (404).
            val deviceWallAtPlayback =
                controller.antennaStartWallMs + (controller.virtualNow() - liveBehindMs)
            val clockDelta = livePlaybackWallMs()?.minus(deviceWallAtPlayback) ?: 0L
            (-3..3).map { i ->
                val offset = i * SEEK_STEP_MS
                val slotWall = controller.antennaStartWallMs + centerMs + offset + clockDelta
                val trickUrl = trickThumbUrl(chanId, slotWall)
                val bmp = if (trickUrl != null) {
                    if (!trickThumbCache.containsKey(trickUrl) && trickFetching.add(trickUrl)) {
                        demoScope.launch(Dispatchers.IO) {
                            val b = try {
                                java.net.URL(trickUrl).openStream().use {
                                    android.graphics.BitmapFactory.decodeStream(it)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "trick thumb fail: $trickUrl (${e.message})")
                                null
                            }
                            withContext(Dispatchers.Main) {
                                if (trickThumbCache.size > 200) trickThumbCache.clear()
                                // null NIE trafia do cache — kolejny RUCH kursora spróbuje
                                // ponownie (bez auto-refreshu, żeby fail nie pętlił fetchy)
                                if (b != null) trickThumbCache[trickUrl] = b
                                trickFetching.remove(trickUrl)
                                // odśwież taśmę po sukcesie, jeśli wciąż na niej jesteśmy
                                if (b != null && playerZone == PlayerZone.STRIP) {
                                    updateFilmstrip(scrubCursorMs)
                                }
                            }
                        }
                    }
                    trickThumbCache[trickUrl]
                } else {
                    val loop = liveLoopMs(chanId)
                    if (loop != null) {
                        // Materiał zapętlony: dopasuj klatkę po FAZIE pętli (dystans
                        // cykliczny) — działa dla całego okna DVR, także sprzed strojenia
                        val slotPhase = ((slotWall % loop) + loop) % loop
                        fun cyclicDist(wall: Long): Long {
                            val d = kotlin.math.abs((((wall % loop) + loop) % loop) - slotPhase)
                            return kotlin.math.min(d, loop - d)
                        }
                        liveThumbs.minByOrNull { cyclicDist(it.first) }
                            ?.takeIf { cyclicDist(it.first) <= 1_600L }
                            ?.second?.takeIf { !it.isRecycled }
                    } else {
                        liveThumbs.minByOrNull { kotlin.math.abs(it.first - slotWall) }
                            ?.takeIf { kotlin.math.abs(it.first - slotWall) <= 5_000L }
                            ?.second?.takeIf { !it.isRecycled }
                    }
                }
                offset to bmp
            }
        } else {
            // Realny kanał: brak materiału w demo — puste sloty z etykietami czasu
            (-3..3).map { i -> (i * SEEK_STEP_MS) to null }
        }
    }

    fun openPlayerButtons() {
        playerZone = PlayerZone.BUTTONS
        playerButtonsFocus = 0
        playerInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.PLAYER_UI
    }

    // Polityka przewijania dostrojonego kanału (blokady per kanał, nie per program)
    fun tunedSeekPolicy(): DemoSeekPolicy {
        if (tunedChannelIndex == 0 && demoPolicyOverride != null) return demoPolicyOverride!!
        val name = epgRows.getOrNull(tunedChannelIndex)?.channel?.name ?: ""
        // Każdy kanał barker (lokalne pliki) = pełne przewijanie
        return DemoSeekPolicyClassifier.policyForChannel(name, isDemoBarker = activeBarker() != null)
    }

    fun showForwardBlocked() {
        // Ten sam pasek powiadomień (995×104 na y=841) i forma co komunikat
        // o niedostępnym przewijaniu — zamiast osobnego banneru na górze
        demoToast = "Przewijanie tego programu do przodu nie jest dostępne"
        playerInteractionAt = System.currentTimeMillis()
        Log.i(TAG, "Forward seek blocked (policy=${tunedSeekPolicy()})")
    }

    // Blackout (brak praw) — kanały z realnym streamem (Stargaze) nigdy nie mają
    // blackoutów (wszystko odtwarzalne live); reszta wg deterministycznego klasyfikatora
    fun channelBlackout(chIdx: Int, progIdx: Int): Boolean {
        if (barkerFor(chIdx) != null) return false   // barkery: wszystko odtwarzalne
        val hasStream = epgRows.getOrNull(chIdx)?.channel?.streamUrl?.isNotBlank() == true
        return !hasStream && DemoSeekPolicyClassifier.isBlackout(chIdx, progIdx)
    }

    // Blackout (brak praw) programu pod daną pozycją wirtualną na dostrojonym kanale
    fun isBlackoutAtVirtual(virtualMs: Long): Boolean {
        val row = epgRows.getOrNull(tunedChannelIndex) ?: return false
        val instant = java.time.Instant.ofEpochMilli(activeCtl().antennaStartWallMs + virtualMs)
        val idx = row.programs.indexOfFirst { p ->
            !instant.isBefore(p.startUtc) && instant.isBefore(p.endUtc)
        }
        return idx >= 0 && channelBlackout(tunedChannelIndex, idx)
    }

    // Kanał z realnym streamem live (Stargaze): pauza/seek działają na livePlayer
    // w obrębie okna DVR strumienia (~38 s u Tivio), nie na osi barkera.
    fun isTunedLiveStream(): Boolean =
        tunedChannelIndex != 0 &&
            epgRows.getOrNull(tunedChannelIndex)?.channel?.streamUrl?.isNotBlank() == true

    /**
     * Krok przewijania taśmy ze snapem do granicy materiału. Przy TRZYMANIU
     * strzałki (autorepeat: repeatCount > 0) kursor ZATRZYMUJE SIĘ na granicy
     * (kafelek "Przechodzisz do…") i stoi aż user puści klawisz. NOWE fizyczne
     * naciśnięcie (repeatCount == 0) ZAWSZE zwalnia blokadę i rusza dalej —
     * sygnałem jest repeatCount, nie timing, żeby szybkie klikanie nigdy nie
     * dawało trwałej blokady. Realny live: bez snapu (oś wall-clock).
     */
    fun scrubStepWithSnap(dir: Int) {
        val step = getSeekStep()   // akceleracja przy serii naciśnięć
        if (scrubKeyRepeat == 0) scrubSnapLockMs = -1L   // nowy klik zwalnia lock
        if (scrubSnapLockMs >= 0 && scrubCursorMs == scrubSnapLockMs) {
            return   // autorepeat na granicy — czekaj na puszczenie klawisza
        }
        scrubSnapLockMs = -1L
        val cand = scrubCursorMs + step * dir
        val snapped = if (isTunedLiveStream()) cand
            else snapScrubToBlockEdge(scrubCursorMs, cand, dir)
        if (snapped != cand) scrubSnapLockMs = snapped
        scrubCursorMs = snapped.coerceIn(activeCtl().dvrStartMs(), activeCtl().virtualNow())
        updateFilmstrip(scrubCursorMs)
    }

    /** Seek względny na live (clamp do okna DVR playlisty). */
    fun liveSeekBy(deltaMs: Long) {
        val p = livePlayer ?: return
        val windowMs = p.duration.takeIf { it > 0 } ?: return   // okno jeszcze nieznane
        val target = (p.currentPosition + deltaMs).coerceIn(0L, windowMs)
        p.seekTo(target)
        p.play()
        isPaused = false
        playerInteractionAt = System.currentTimeMillis()
        Log.i(TAG, "liveSeekBy $deltaMs → $target/${windowMs}ms")
    }

    // Blok ramówki dostrojonego kanału w pozycji wirtualnej: DEMO TV = sztuczna
    // ramówka (barker), realny kanał = program z prawdziwego EPG przeliczony na
    // oś wirtualną; null = brak programu w EPG o tym czasie (realny kanał)
    fun blockForTunedChannel(virtualMs: Long): BarkerSchedule.EpgBlock? {
        activeBarker()?.let { return it.schedule.epgBlockAt(virtualMs) }
        val row = epgRows.getOrNull(tunedChannelIndex) ?: return null
        val instant = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + virtualMs)
        val program = row.programs.firstOrNull { p ->
            !instant.isBefore(p.startUtc) && instant.isBefore(p.endUtc)
        } ?: return null
        return BarkerSchedule.EpgBlock(
            title = program.title,
            startVirtualMs = program.startUtc.toEpochMilli() - controller.antennaStartWallMs,
            endVirtualMs = program.endUtc.toEpochMilli() - controller.antennaStartWallMs,
            genre = program.categories.firstOrNull { it.isNotBlank() } ?: "",
            year = "",
            country = "",
            age = "",
            description = program.description ?: "Brak opisu programu w danych EPG."
        )
    }

    fun openStrip(initialCursor: Long) {
        playerZone = PlayerZone.STRIP
        // Clamp jak przy LEFT/RIGHT: kursor sprzed live edge'a (patrz playerUp) —
        // bez tego środkowy slot taśmy bywa "za live" i dostaje pustą klatkę
        scrubCursorMs = initialCursor
            .coerceIn(activeCtl().dvrStartMs(), activeCtl().virtualNow())
        updateFilmstrip(scrubCursorMs)
        playerInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.PLAYER_UI
    }

    // Detal z EpgProgram (dowolny kanał) — dane VodSlideData jak w zakładce Wideo
    fun openDetail(
        program: com.uxellence.tv.v3.epg.EpgProgram,
        channelLogoUrl: String?,
        isDemo: Boolean,
        fromEpg: Boolean,
        channelName: String = "DEMO TV",
        channelNumber: Int = 122,
        channelIndex: Int = 0
    ) {
        val chCtl = ctlFor(channelIndex)
        val startV = program.startUtc.toEpochMilli() - chCtl.antennaStartWallMs
        val endV = program.endUtc.toEpochMilli() - chCtl.antennaStartWallMs
        val nowV = if (barkerFor(channelIndex) != null &&
            activeBarker() === barkerFor(channelIndex)
        ) chCtl.currentVirtualPositionMs() else chCtl.virtualNow()
        val durationMin = (program.endUtc.toEpochMilli() - program.startUtc.toEpochMilli()) / 60_000
        detailSlide = com.uxellence.tv.v3.VodSlideData(
            title = program.title,
            genre = program.categories.filter { it.isNotBlank() }.take(2).joinToString(", "),
            duration = "$durationMin min",
            year = "",
            country = "",
            ageRating = "13 lat",
            description = program.description ?: "Brak opisu programu w danych EPG.",
            price = "",
            backgroundUrl = program.iconUrl ?: "",
            posterUrl = program.iconUrl ?: "",
            isKinoPlay = false,
            channelLogoUrl = channelLogoUrl
        )
        detailTiming = when {
            isDemo && nowV in startV until endV -> BlockTiming.CURRENT
            endV <= chCtl.virtualNow() -> BlockTiming.PAST
            else -> BlockTiming.FUTURE
        }
        detailIsDemo = isDemo
        detailFromEpg = fromEpg
        detailStartVirtualMs = startV
        detailStartWallMs = program.startUtc.toEpochMilli()
        detailEndWallMs = program.endUtc.toEpochMilli()
        detailChannelLogoUrl = channelLogoUrl
        detailChannelName = channelName
        detailChannelNumber = channelNumber
        detailChannelIndex = channelIndex
        playerZone = PlayerZone.DETAIL
        playerInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.PLAYER_UI
    }

    // Wiersz kanału barker: bloki sztucznej ramówki jako EpgProgram (cover = klatka
    // z materiału, gdy zekstrahowana). Pozycja "oglądana" = playback (gdy gotowy
    // i dostrojony) albo live edge.
    fun buildBarkerRow(bundle: BarkerBundle): com.uxellence.tv.v3.epg.ChannelEpgRow {
        val ctl = bundle.controller
        val nowV = if (bundle.ready.value && activeBarker() === bundle) {
            ctl.currentVirtualPositionMs()
        } else {
            ctl.virtualNow()
        }
        val edge = ctl.virtualNow()
        // Bloki wstecz: tyle, by pokryć CAŁE okno DVR kanału (kanał z nagrania:
        // 8 h ramówki do przewijania; barkery 60 min ≈ dawne before=3).
        // Sztywne before=3 ucinało ramówkę kanału 130/131 na ~2 h wstecz.
        val dvrStart = ctl.dvrStartMs()
        val backBlocks = run {
            var b = bundle.schedule.epgBlockAt(nowV).startVirtualMs
            var count = 0
            while (b > dvrStart && b > 0 && count < 60) {
                b = bundle.schedule.epgBlockAt(b - 1).startVirtualMs
                count++
            }
            count.coerceAtLeast(3)
        }
        val blocks = bundle.schedule.blocksAround(nowV, before = backBlocks, after = 8)
        val programs = blocks.map { b ->
            com.uxellence.tv.v3.epg.EpgProgram(
                channelId = bundle.channelId,
                title = b.title,
                startUtc = java.time.Instant.ofEpochMilli(ctl.antennaStartWallMs + b.startVirtualMs),
                endUtc = java.time.Instant.ofEpochMilli(ctl.antennaStartWallMs + b.endVirtualMs),
                description = b.description,
                categories = listOf(b.genre, b.year, b.country, b.age),
                iconUrl = b.coverUrl
                    ?: bundle.filmstrip.thumbUriFor(b.startVirtualMs, edge, context.cacheDir)
            )
        }
        val nowInstant = java.time.Instant.ofEpochMilli(ctl.antennaStartWallMs + nowV)
        val currentIdx = programs.indexOfFirst { p ->
            !nowInstant.isBefore(p.startUtc) && nowInstant.isBefore(p.endUtc)
        }.coerceAtLeast(0)
        return com.uxellence.tv.v3.epg.ChannelEpgRow(
            channel = com.uxellence.tv.v3.channels.TvChannelData(
                id = bundle.channelId,
                name = bundle.name,
                streamUrl = "",
                logoUrl = null,
                epgId = bundle.channelId
            ),
            channelNumber = bundle.number,
            programs = programs,
            currentProgramIndex = currentIdx,
            lazyListState = androidx.compose.foundation.lazy.LazyListState()
        )
    }

    // Stargaze: realne live (HLS z Tivio) + syntetyczna ramówka z faktycznych
    // formatów kanału (5 Sposobów Na, Człowiek Absurdalny, ORB News, Sprytne
    // Babki…) — publicznego XMLTV brak. Bloki 30 min, wczoraj+dziś (spójnie
    // z realnymi kanałami, żeby TIME SYNC działał też tuż po północy).
    fun buildStargazeRow(): com.uxellence.tv.v3.epg.ChannelEpgRow {
        val formats = listOf(
            "5 Sposobów Na" to "Poradnikowy format twórców internetowych — szybkie sposoby na codzienne wyzwania.",
            "Człowiek Absurdalny" to "Podcast o absurdach codzienności prosto z internetu.",
            "ORB News" to "Przegląd najciekawszych wydarzeń ze świata twórców online.",
            "Sprytne Babki" to "Lifehacki, DIY i triki od znanych twórczyń.",
            "Stargaze Mix" to "Najlepsze fragmenty tygodnia na Stargaze.",
            "Kreatorzy" to "Rozmowy z twórcami internetowymi o kulisach ich pracy."
        )
        // Okładki formatów (mini-EPG): Flickr CC przez Openverse. UWAGA: celowo
        // NIE Wikimedia — upload.wikimedia.org odrzuca 403-ką User-Agenta
        // "okhttp/x", którym przedstawia się Coil (miniaturka nigdy się nie
        // załaduje). Flickr/archive.org/NASA przepuszczają.
        val covers = mapOf(
            "5 Sposobów Na" to "https://live.staticflickr.com/1830/42182307225_6817c029cb_b.jpg",
            "Człowiek Absurdalny" to "https://live.staticflickr.com/2745/4156902882_1c7240c4ae.jpg",
            "ORB News" to "https://live.staticflickr.com/2628/3810416472_63e04b9136_b.jpg",
            "Sprytne Babki" to "https://live.staticflickr.com/2018/2272444834_00e8d429c0_b.jpg",
            "Stargaze Mix" to "https://live.staticflickr.com/140/391656842_5dbdc6ac67_b.jpg",
            "Kreatorzy" to "https://live.staticflickr.com/3763/33273135505_9054377c10_b.jpg"
        )
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
        val slotMs = 30L * 60 * 1000
        val programs = (0 until 96).map { i ->   // 48 h w blokach 30 min
            val st = start.plusMillis(slotMs * i)
            val (title, desc) = formats[i % formats.size]
            com.uxellence.tv.v3.epg.EpgProgram(
                channelId = "stargaze",
                title = title,
                startUtc = st,
                endUtc = st.plusMillis(slotMs),
                description = desc,
                categories = listOf("rozrywka", "2026", "Polska", "12 lat"),
                iconUrl = covers[title]
            )
        }
        val now = java.time.Instant.now()
        val currentIdx = programs.indexOfFirst { p ->
            !now.isBefore(p.startUtc) && now.isBefore(p.endUtc)
        }.coerceAtLeast(0)
        return com.uxellence.tv.v3.epg.ChannelEpgRow(
            channel = com.uxellence.tv.v3.channels.TvChannelData(
                id = "stargaze",
                name = "Stargaze",
                streamUrl = STARGAZE_STREAM_URL,
                logoUrl = null,
                epgId = "1123"   // id ramówki wg Play; w epg.xml (jeszcze) go nie ma
            ),
            channelNumber = 1123,
            programs = programs,
            currentProgramIndex = currentIdx,
            lazyListState = androidx.compose.foundation.lazy.LazyListState()
        )
    }

    // DASH-IF livesim2: wieczny kanał testowy DASH z oknem DVR 1 h i torem miniatur
    // trick-play. Ramówka syntetyczna (bloki 30 min) — treść to plansza testowa
    // z zegarem, więc miniaturki same "pokazują" swój czas (idealne do testów taśmy).
    fun buildLivesim2Row(): com.uxellence.tv.v3.epg.ChannelEpgRow {
        val titles = listOf(
            "Trick-play demo" to "Kafelki miniatur z toru image/jpeg (co 2 s).",
            "Okno DVR 1 h" to "Przewijanie do godziny wstecz — retencja segmentów po stronie źródła.",
            "Plansza testowa" to "Wzór DASH-IF z bieżącym czasem — weryfikacja trafności skoków."
        )
        // Okładki (mini-EPG): Flickr CC — patrz komentarz o UA przy Stargaze
        val covers = mapOf(
            "Trick-play demo" to "https://live.staticflickr.com/7422/13668736373_39d525da45_b.jpg",
            "Okno DVR 1 h" to "https://live.staticflickr.com/7099/7169630025_daa44ae929_b.jpg",
            "Plansza testowa" to "https://live.staticflickr.com/143/318103681_b984362e94.jpg"
        )
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
        val slotMs = 30L * 60 * 1000
        val programs = (0 until 96).map { i ->
            val st = start.plusMillis(slotMs * i)
            val (title, desc) = titles[i % titles.size]
            com.uxellence.tv.v3.epg.EpgProgram(
                channelId = "dashif",
                title = title,
                startUtc = st,
                endUtc = st.plusMillis(slotMs),
                description = desc,
                categories = listOf("test", "2026", "DASH-IF", "bez ograniczeń"),
                iconUrl = covers[title]
            )
        }
        val now = java.time.Instant.now()
        val currentIdx = programs.indexOfFirst { p ->
            !now.isBefore(p.startUtc) && now.isBefore(p.endUtc)
        }.coerceAtLeast(0)
        return com.uxellence.tv.v3.epg.ChannelEpgRow(
            channel = com.uxellence.tv.v3.channels.TvChannelData(
                id = "dashif",
                name = "DASH-IF",
                streamUrl = LIVESIM2_STREAM_URL,
                logoUrl = null,
                epgId = "dashif"
            ),
            channelNumber = 125,
            programs = programs,
            currentProgramIndex = currentIdx,
            lazyListState = androidx.compose.foundation.lazy.LazyListState()
        )
    }

    // Safari: realny MATERIAŁ wideo (klip safari zapętlony przez livesim2 jako live),
    // DVR 1 h — do testów przewijania na "normalnie wyglądającym" kanale.
    fun buildSafariRow(): com.uxellence.tv.v3.epg.ChannelEpgRow {
        val titles = listOf(
            "Safari w Namibii" to "Dzika przyroda Afryki — zapętlony materiał jako kanał live.",
            "Na sawannie" to "Zwierzęta w naturalnym środowisku.",
            "Wieczór na buszu" to "Przyrodniczy przegląd dnia."
        )
        // Okładki (mini-EPG): Flickr CC — patrz komentarz o UA przy Stargaze
        val covers = mapOf(
            "Safari w Namibii" to "https://live.staticflickr.com/2831/11502238866_a643de3ff0_b.jpg",
            "Na sawannie" to "https://live.staticflickr.com/145/401930619_c6ce5e6f54_b.jpg",
            "Wieczór na buszu" to "https://live.staticflickr.com/3160/2899305640_727e55cfea.jpg"
        )
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant()
        val slotMs = 30L * 60 * 1000
        val programs = (0 until 96).map { i ->
            val st = start.plusMillis(slotMs * i)
            val (title, desc) = titles[i % titles.size]
            com.uxellence.tv.v3.epg.EpgProgram(
                channelId = "safari",
                title = title,
                startUtc = st,
                endUtc = st.plusMillis(slotMs),
                description = desc,
                categories = listOf("przyroda", "2026", "Namibia", "7 lat"),
                iconUrl = covers[title]
            )
        }
        val now = java.time.Instant.now()
        val currentIdx = programs.indexOfFirst { p ->
            !now.isBefore(p.startUtc) && now.isBefore(p.endUtc)
        }.coerceAtLeast(0)
        return com.uxellence.tv.v3.epg.ChannelEpgRow(
            channel = com.uxellence.tv.v3.channels.TvChannelData(
                id = "safari",
                name = "Safari",
                streamUrl = SAFARI_STREAM_URL,
                logoUrl = null,
                epgId = "safari"
            ),
            channelNumber = 126,
            programs = programs,
            currentProgramIndex = currentIdx,
            lazyListState = androidx.compose.foundation.lazy.LazyListState()
        )
    }

    fun openEpg() {
        // Kolejność: barkery (122 DEMO, 127 Kino, 128 Kosmos, 129 Kids), potem kanały
        // live-stream (Stargaze/DASH-IF/Safari), potem realne z EPG
        val barkerRows = barkers.values.map { buildBarkerRow(it) }
        val demoRow = barkerRows.first()
        epgRows = barkerRows + listOf(buildStargazeRow(), buildLivesim2Row(), buildSafariRow()) +
            realChannelRows
        // Start jak pod Telewizją: pasek 1 kanału (tego, który jest na ekranie)
        epgExpanded = false
        epgChannelIndex = tunedChannelIndex.coerceIn(0, (epgRows.size - 1).coerceAtLeast(0))
        // TIME SYNC musi celować w INSTANT AKTUALNIE OGLĄDANY (pozycja playbacku,
        // nie zegar ścienny i nie START programu) — przy timeshifcie to inny program
        // niż live, a start programu wskazywałby na kanałach o grubszych blokach
        // (np. Stargaze 30 min) blok MINIONY: program trwający lądowałby obok
        // kolumny fokusa zamiast pod spodem.
        val watchedWallMs = activeCtl().antennaStartWallMs + when {
            activeBarker() != null -> currentVirtualMs
            isTunedLiveStream() -> (liveEdgeMs - liveBehindMs).coerceAtLeast(0L)
            else -> liveEdgeMs
        }
        val focused = if (liveEdgeMs > 0L) java.time.Instant.ofEpochMilli(watchedWallMs)
            else java.time.Instant.now()
        epgFocusedTime = focused
        epgProgramIndex.clear()
        epgRows.forEachIndexed { i, row ->
            val match = row.programs.indexOfFirst { p ->
                !focused.isBefore(p.startUtc) && focused.isBefore(p.endUtc)
            }
            epgProgramIndex[i] = if (match >= 0) match else row.currentProgramIndex
        }
        // Zero "dojeżdżania" pasków przy otwarciu (lekcja #12 CLAUDE.md: synchronous
        // initial state): każdy wiersz dostaje ŚWIEŻY LazyListState zseedowany na
        // program docelowy — paski są na miejscu od pierwszej klatki, a TIME SYNC
        // w DemoEpgLayer robi już tylko korekty przy nawigacji po otwartym EPG.
        epgRows = epgRows.mapIndexed { i, row ->
            row.copy(
                lazyListState = androidx.compose.foundation.lazy.LazyListState(
                    firstVisibleItemIndex = (epgProgramIndex[i] ?: row.currentProgramIndex)
                        .coerceAtLeast(0)
                )
            )
        }
        epgInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.EPG
    }

    // Indeks programu AKTUALNIE OGLĄDANEGO na danym kanale: barker = program pod
    // pozycją odtwarzania (przy timeshifcie miniony), realny kanał = program live
    fun liveProgramIndexFor(channelIdx: Int): Int {
        val row = epgRows.getOrNull(channelIdx) ?: return 0
        val bk = barkerFor(channelIdx)
        val chCtl = ctlFor(channelIdx)
        val refMs = if (bk != null && bk.ready.value && channelIdx == tunedChannelIndex) {
            bk.controller.currentVirtualPositionMs()
        } else {
            chCtl.virtualNow()
        }
        val instant = java.time.Instant.ofEpochMilli(chCtl.antennaStartWallMs + refMs)
        val idx = row.programs.indexOfFirst { p ->
            !instant.isBefore(p.startUtc) && instant.isBefore(p.endUtc)
        }
        return if (idx >= 0) idx else row.currentProgramIndex
    }

    // Czy fokus EPG jest na OGLĄDANEJ pozycji: ten sam kanał co na ekranie, jego
    // bieżący program, tryb 1-kanałowy. Jeśli nie — pierwszy BACK tam wraca,
    // dopiero kolejny zamyka warstwę
    fun isEpgAtWatchedPosition(): Boolean {
        if (epgExpanded) return false
        if (epgChannelIndex != tunedChannelIndex) return false
        val row = epgRows.getOrNull(tunedChannelIndex) ?: return false
        val focusedIdx = epgProgramIndex[tunedChannelIndex] ?: row.currentProgramIndex
        return focusedIdx == liveProgramIndexFor(tunedChannelIndex)
    }

    // ============ AKCJE (wywoływane przez DemoLiveKeyController) ============
    val actions = remember {
        DemoLiveActions(
            showEpg = { openEpg() },
            epgMove = { dir ->
                val row = epgRows.getOrNull(epgChannelIndex)
                if (row != null) {
                    val cur = epgProgramIndex[epgChannelIndex] ?: row.currentProgramIndex
                    val newIdx = (cur + dir).coerceIn(0, (row.programs.size - 1).coerceAtLeast(0))
                    epgProgramIndex[epgChannelIndex] = newIdx
                    // TIME SYNC: pozostałe kanały przewijają się do fokusowanego programu.
                    // Program TRWAJĄCY kotwiczymy na "teraz" (nie na starcie) — wtedy
                    // programy trwające na wszystkich kanałach stoją w jednej kolumnie
                    // niezależnie od długości bloków; miniony/przyszły — na starcie.
                    row.programs.getOrNull(newIdx)?.let { p ->
                        val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
                        epgFocusedTime =
                            if (!now.isBefore(p.startUtc) && now.isBefore(p.endUtc)) now
                            else p.startUtc
                    }
                }
                epgInteractionAt = System.currentTimeMillis()
            },
            epgMoveChannel = { dir ->
                // Pasek pojedynczego kanału (single): GÓRA i DÓŁ rozwijają do warstwy
                // wielu kanałów. Potem normalna nawigacja między kanałami.
                epgExpanded = true
                val newChannel = (epgChannelIndex + dir).coerceIn(0, (epgRows.size - 1).coerceAtLeast(0))
                epgChannelIndex = newChannel
                // Na nowym kanale fokusuj program emitowany o epgFocusedTime (siatka czasowa)
                epgRows.getOrNull(newChannel)?.let { row ->
                    val matching = row.programs.indexOfFirst { p ->
                        !epgFocusedTime.isBefore(p.startUtc) && epgFocusedTime.isBefore(p.endUtc)
                    }
                    epgProgramIndex[newChannel] =
                        if (matching >= 0) matching else row.currentProgramIndex
                }
                epgInteractionAt = System.currentTimeMillis()
            },
            epgSelect = {
                val row = epgRows.getOrNull(epgChannelIndex)
                val focusedProgIdx = epgProgramIndex[epgChannelIndex] ?: -1
                val program = row?.programs?.getOrNull(focusedProgIdx)
                Log.i(TAG, "epgSelect: ch=$epgChannelIndex '${row?.channel?.name}' progIdx=$focusedProgIdx " +
                    "prog='${program?.title}' expanded=$epgExpanded rows=${epgRows.size}")
                if (row != null && program != null &&
                    channelBlackout(epgChannelIndex, focusedProgIdx)
                ) {
                    // Blackout (brak praw) — nie da się odtworzyć/dostroić
                    demoToast = "Tego programu nie można odtworzyć"
                    epgInteractionAt = System.currentTimeMillis()
                    Log.i(TAG, "EPG select: '${program.title}' blackout → blocked")
                } else if (row != null && program != null) {
                    val selBarker = barkerFor(epgChannelIndex)
                    val selCtl = ctlFor(epgChannelIndex)
                    val targetStart = program.startUtc.toEpochMilli() - selCtl.antennaStartWallMs
                    val targetEnd = program.endUtc.toEpochMilli() - selCtl.antennaStartWallMs
                    // "Teraz na żywo": barker wg pozycji odtwarzania, realne kanały wg zegara
                    val nowRef = if (selBarker != null && selBarker.ready.value &&
                        epgChannelIndex == tunedChannelIndex
                    ) {
                        selBarker.controller.currentVirtualPositionMs()
                    } else {
                        selCtl.virtualNow()
                    }
                    val playingNow = nowRef in targetStart until targetEnd
                    when {
                        playingNow && epgChannelIndex == tunedChannelIndex -> {
                            // Klik na program AKTUALNIE OGLĄDANY (live LUB timeshift)
                            // → player UI bez ruszania pozycji odtwarzania. Dotąd
                            // w expanded szło przez tuneBarker → seekToLiveEdge
                            // i wyrzucało z timeshiftu do live.
                            epgExpanded = false
                            openPlayerButtons()
                            Log.i(TAG, "EPG select: '${program.title}' (oglądany) → PLAYER_UI")
                        }
                        playingNow && epgExpanded -> {
                            // Wybór programu nadawanego TERAZ na warstwie wielu kanałów →
                            // dostrój kanał i ZWIŃ do paska tylko tego kanału (EPG single).
                            // Player dopiero przy kolejnym OK na tym pasku.
                            tunedChannelIndex = epgChannelIndex
                            val tunedUrl = epgRows.getOrNull(epgChannelIndex)?.channel?.streamUrl.orEmpty()
                            when {
                                selBarker != null -> {
                                    // Kanał barker (lokalne pliki, pełny timeshift)
                                    tuneBarker(selBarker)
                                }
                                tunedUrl.isNotBlank() -> {
                                    // Kanał z realnym streamem (Stargaze): graj live HLS
                                    barkers.values.forEach { it.controller.player?.pause() }
                                    tuneLive(tunedUrl)
                                    isPaused = false
                                }
                                else -> {
                                    // Realny kanał bez streamu: plansza "Brak live"
                                    tuneLive(null)
                                    barkers.values.forEach { it.controller.player?.pause() }
                                }
                            }
                            // Sfokusuj program nadawany teraz na tym kanale (pasek single)
                            val liveIdx = liveProgramIndexFor(tunedChannelIndex)
                            epgProgramIndex[tunedChannelIndex] = liveIdx
                            epgRows.getOrNull(tunedChannelIndex)?.programs?.getOrNull(liveIdx)
                                ?.let { epgFocusedTime = it.startUtc }
                            epgExpanded = false
                            epgInteractionAt = System.currentTimeMillis()
                            Log.i(TAG, "EPG select: tune → ${row.channel.name} → pasek single")
                        }
                        playingNow -> {
                            // OK na pasku pojedynczego kanału (tryb single) → player
                            openPlayerButtons()
                            Log.i(TAG, "EPG select: '${program.title}' (single) → PLAYER_UI")
                        }
                        selBarker != null &&
                            selCtl.virtualNow() in targetStart until targetEnd -> {
                            // Program BIEŻĄCY WG ZEGARA na kanale barker, a my w timeshifcie
                            // (playingNow=false bo pozycja odtwarzania gdzie indziej) →
                            // WRÓĆ DO LIVE tego kanału. To naturalna droga powrotu z paska.
                            tunedChannelIndex = epgChannelIndex
                            tuneBarker(selBarker)   // ready → seekToLiveEdge
                            val liveIdx = liveProgramIndexFor(tunedChannelIndex)
                            epgProgramIndex[tunedChannelIndex] = liveIdx
                            epgRows.getOrNull(tunedChannelIndex)?.programs?.getOrNull(liveIdx)
                                ?.let { epgFocusedTime = it.startUtc }
                            epgExpanded = false
                            epgInteractionAt = System.currentTimeMillis()
                            Log.i(TAG, "EPG select: wróć do live → ${row.channel.name}")
                        }
                        else -> {
                            // Program miniony/przyszły (dowolny kanał) → detal jak na Wideo
                            openDetail(
                                program = program,
                                channelLogoUrl = row.channel.logoUrl,
                                isDemo = selBarker != null,
                                fromEpg = true,
                                channelName = row.channel.name,
                                channelNumber = row.channelNumber,
                                channelIndex = epgChannelIndex
                            )
                            Log.i(TAG, "EPG select: '${program.title}' (${row.channel.name}) → DETAIL (timing=$detailTiming)")
                        }
                    }
                }
            },
            epgBack = {
                if (isEpgAtWatchedPosition()) {
                    // Już na oglądanym kanale + bieżącym programie → zamknij EPG
                    layer = DemoLayer.FULLSCREEN
                    Log.i(TAG, "EPG BACK: at watched position → FULLSCREEN")
                } else {
                    // Wróć do oglądanego kanału + bieżącego programu (live), tryb 1-kanałowy
                    openEpg()
                    Log.i(TAG, "EPG BACK: re-home to watched channel=$tunedChannelIndex")
                }
            },
            playerMove = { dir ->
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.BUTTONS -> {
                        // Pauza ≠ live — przy pauzie slot 1 jest przyciskiem "Wróć do live".
                        // Barker: wg pozycji jego kontrolera; live-stream: wg liveBehindMs.
                        val atLive = when {
                            activeBarker() != null -> !isPaused && activeCtl().isAtLiveEdge()
                            isTunedLiveStream() -> !isPaused && liveBehindMs < 5_000L
                            else -> true
                        }
                        var newFocus = (playerButtonsFocus + dir).coerceIn(0, 4)
                        if (atLive && newFocus == 1) {
                            // Na live slot 1 to status "Oglądasz live" (niefokusowalny) — przeskocz
                            newFocus = (newFocus + dir).coerceIn(0, 4)
                        }
                        playerButtonsFocus = newFocus
                    }
                    PlayerZone.STRIP -> {
                        if (dir > 0 && tunedSeekPolicy() == DemoSeekPolicy.BACKWARD_ONLY) {
                            // Blokada przewijania DO PRZODU (TVN/Disney) — komunikat, bez ruchu
                            showForwardBlocked()
                        } else {
                            scrubStepWithSnap(dir)
                        }
                    }
                    PlayerZone.SNIPPET, PlayerZone.DETAIL -> { /* brak nawigacji poziomej */ }
                }
            },
            playerSelect = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.STRIP -> {
                        if (isTunedLiveStream()) {
                            // OK na taśmie live: przelicz kursor (oś wall-clock) na pozycję
                            // w oknie playera i skocz; UI znika jak na barkerze
                            val nowV = controller.virtualNow()
                            livePlayer?.let { p ->
                                val windowMs = p.duration.takeIf { it > 0 } ?: 38_000L
                                val behind = (nowV - scrubCursorMs).coerceIn(0L, windowMs)
                                p.seekTo((windowMs - behind).coerceAtLeast(0L))
                                p.play()
                            }
                            isPaused = false
                            rapidPressCount = 0
                            playerZone = PlayerZone.BUTTONS
                            playerButtonsFocus = 0
                            // Wytyczna 2026-07-14: OK po przewinięciu = skok
                            // i schowanie CAŁEGO UI (czysty obraz)
                            layer = DemoLayer.FULLSCREEN
                            Log.i(TAG, "STRIP(live) seek → ${scrubCursorMs}ms → FULLSCREEN")
                        } else if (isBlackoutAtVirtual(scrubCursorMs)) {
                            // Blackout (brak praw) — nie odtwarzaj tego fragmentu
                            demoToast = "Tego programu nie można odtworzyć"
                            Log.i(TAG, "STRIP OK on blackout → blocked")
                        } else {
                            // OK na taśmie = skok do kursora i schowanie CAŁEGO
                            // UI (wytyczna 2026-07-14: czysty obraz po seeku)
                            activeCtl().seekToVirtual(scrubCursorMs)
                            isPaused = false
                            rapidPressCount = 0
                            playerZone = PlayerZone.BUTTONS
                            playerButtonsFocus = 0
                            layer = DemoLayer.FULLSCREEN
                            Log.i(TAG, "STRIP seek → ${scrubCursorMs}ms → FULLSCREEN")
                        }
                    }
                    PlayerZone.BUTTONS -> when (playerButtonsFocus) {
                        0 -> {  // Zatrzymaj / Wznów — na AKTYWNYM playerze (live lub barker)
                            val p = if (isTunedLiveStream()) livePlayer else activeCtl().player
                            if (p != null) {
                                if (p.isPlaying) {
                                    p.pause(); isPaused = true
                                } else {
                                    p.play(); isPaused = false
                                }
                                Log.i(TAG, "Player: zatrzymaj → isPaused=$isPaused (live=${isTunedLiveStream()})")
                            }
                        }
                        1 -> {  // Wróć do live
                            if (isTunedLiveStream()) {
                                livePlayer?.seekToDefaultPosition()
                                livePlayer?.play()
                            } else {
                                activeCtl().seekToLiveEdge()
                            }
                            isPaused = false
                            Log.i(TAG, "Player: wróć do live (live=${isTunedLiveStream()})")
                        }
                        2 -> {  // Zacznij od początku
                            if (isTunedLiveStream()) {
                                // Live: początek OKNA DVR strumienia (tyle, ile daje źródło)
                                livePlayer?.seekTo(0)
                                livePlayer?.play()
                                isPaused = false
                                Log.i(TAG, "Player: od początku okna live")
                            } else {
                                val ctl = activeCtl()
                                val block = ctl.schedule.epgBlockAt(ctl.currentVirtualPositionMs())
                                ctl.seekToVirtual(block.startVirtualMs)
                                isPaused = false
                                Log.i(TAG, "Player: zacznij od początku → ${block.startVirtualMs}ms")
                            }
                        }
                        3 -> {  // Nagraj (REC) / Anuluj nagranie dla bieżącego programu
                            val block = blockForTunedChannel(
                                if (activeBarker() != null) currentVirtualMs
                                else (liveEdgeMs - liveBehindMs).coerceAtLeast(0L)
                            )
                            val blockStartWall = block?.let {
                                activeCtl().antennaStartWallMs + it.startVirtualMs
                            }
                            if (block != null && blockStartWall != null &&
                                DemoRecordingScheduler.isScheduled(block.title, blockStartWall)
                            ) {
                                // Zlecone → ANULUJ nagranie
                                DemoRecordingScheduler.cancel(context, block.title, blockStartWall)
                                demoToast = "Anulowano nagrywanie: ${block.title}"
                                Log.i(TAG, "REC → anulowano nagranie '${block.title}'")
                            } else if (block != null) {
                                val row = epgRows.getOrNull(tunedChannelIndex)
                                recordingCandidate = DemoRecordingScheduler.ScheduledRecording(
                                    title = block.title,
                                    subTitle = listOf(block.genre, block.year)
                                        .filter { it.isNotBlank() }.joinToString(", "),
                                    channelId = row?.channel?.id ?: "",
                                    channelName = row?.channel?.name ?: "DEMO TV",
                                    startUtcMs = activeCtl().antennaStartWallMs + block.startVirtualMs,
                                    endUtcMs = activeCtl().antennaStartWallMs + block.endVirtualMs,
                                    imageUrl = block.coverUrl,
                                    isSeries = false,
                                    keepLabel = "3 miesiące"
                                )
                                Log.i(TAG, "REC → modal nagrywania: '${block.title}'")
                            }
                        }
                        else -> { /* Napisy — atrapa */ }
                    }
                    PlayerZone.SNIPPET -> {
                        // OK na skrócie opisu → detal bieżącego programu aktywnego barkera
                        val bundle = activeBarker() ?: barkers.getValue("demo")
                        val ctl = bundle.controller
                        val block = bundle.schedule.epgBlockAt(ctl.currentVirtualPositionMs())
                        val program = com.uxellence.tv.v3.epg.EpgProgram(
                            channelId = bundle.channelId,
                            title = block.title,
                            startUtc = java.time.Instant.ofEpochMilli(ctl.antennaStartWallMs + block.startVirtualMs),
                            endUtc = java.time.Instant.ofEpochMilli(ctl.antennaStartWallMs + block.endVirtualMs),
                            description = block.description,
                            categories = listOf(block.genre, block.year, block.country),
                            iconUrl = block.coverUrl
                                ?: bundle.filmstrip.thumbUriFor(block.startVirtualMs, ctl.virtualNow(), context.cacheDir)
                        )
                        openDetail(
                            program, channelLogoUrl = null, isDemo = true, fromEpg = false,
                            channelName = bundle.name, channelNumber = bundle.number,
                            channelIndex = tunedChannelIndex
                        )
                        Log.i(TAG, "SNIPPET → DETAIL")
                    }
                    PlayerZone.DETAIL -> {
                        // OK obsługuje MovieDetailScreen (własny fokus) — tu nic
                    }
                }
            },
            playerUp = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.BUTTONS -> {
                        if (tunedSeekPolicy() == DemoSeekPolicy.NONE) {
                            // Kanał bez przewijania (np. Stargaze, okno ~30 s):
                            // komunikat przy PRÓBIE wejścia na taśmę
                            demoToast = "Przewijanie tego kanału nie jest możliwe — " +
                                "nadawca udostępnia tylko bieżący fragment"
                        } else if (isTunedLiveStream()) {
                            // Realny live: taśma na osi wall-clock, kursor od bieżącej
                            // pozycji (live minus cofnięcie w oknie DVR)
                            playerZone = PlayerZone.STRIP
                            scrubStartVirtualMs = controller.virtualNow() - liveBehindMs
                            scrubCursorMs = scrubStartVirtualMs
                            updateFilmstrip(scrubCursorMs)
                        } else {
                            // Z przycisków na taśmę (kursor startuje z bieżącej pozycji).
                            // CLAMP do okna DVR: pozycja z zegara MEDIÓW potrafi na live
                            // wyprzedzić wall-clockowy virtualNow() o ułamek sekundy —
                            // środkowy slot taśmy lądował "za live" i startował pustą
                            // klatką (LEFT/RIGHT robią ten sam coerceIn, stąd znikała).
                            playerZone = PlayerZone.STRIP
                            scrubStartVirtualMs = activeCtl().currentVirtualPositionMs()
                                .coerceIn(activeCtl().dvrStartMs(), activeCtl().virtualNow())
                            scrubCursorMs = scrubStartVirtualMs
                            updateFilmstrip(scrubCursorMs)
                        }
                    }
                    PlayerZone.SNIPPET -> {
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                    }
                    PlayerZone.STRIP, PlayerZone.DETAIL -> { /* nic */ }
                }
            },
            playerDown = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.BUTTONS -> {
                        playerZone = PlayerZone.SNIPPET   // fokus na skrót opisu (bez PIP)
                    }
                    PlayerZone.STRIP -> {
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                    }
                    PlayerZone.SNIPPET, PlayerZone.DETAIL -> { /* nic */ }
                }
            },
            playerBack = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.DETAIL -> {
                        if (detailFromEpg) {
                            openEpg()   // wróć tam, skąd przyszliśmy
                        } else {
                            playerZone = PlayerZone.SNIPPET
                        }
                    }
                    PlayerZone.STRIP -> {
                        // WSTECZ z przewijania wraca do OGLĄDANEJ treści (nie do klatki
                        // pod kursorem): barker/VOD → kadr startowy (playback nie ruszał
                        // się, commit jest dopiero na OK); realny kanał (startover/backward)
                        // → live. Klatka NIE gaśnie sama — wychodzimy tylko na WSTECZ.
                        if (tunedChannelIndex != 0) {
                            controller.seekToLiveEdge()
                            isPaused = false
                        }
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                    }
                    PlayerZone.SNIPPET -> {
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                    }
                    PlayerZone.BUTTONS -> openEpg()   // łańcuch: player UI → EPG → fullscreen → wyjście
                }
            },
            openStripWithStep = { direction ->
                val policy = tunedSeekPolicy()
                when {
                    policy == DemoSeekPolicy.NONE -> {
                        // Kanał bez przewijania — komunikat przy PRÓBIE przewijania
                        demoToast = "Przewijanie tego kanału nie jest możliwe — " +
                            "nadawca udostępnia tylko bieżący fragment"
                    }
                    isTunedLiveStream() -> {
                        // Realny stream live (Stargaze): taśma STRIP na osi wall-clock,
                        // kursor ograniczony do okna DVR playlisty (~38 s). Miniaturki
                        // ze zrzutów klatek robionych podczas oglądania (liveThumbs).
                        val nowV = controller.virtualNow()
                        val windowMs = livePlayer?.duration?.takeIf { it > 0 } ?: 38_000L
                        if (layer != DemoLayer.PLAYER_UI || playerZone != PlayerZone.STRIP) {
                            scrubStartVirtualMs = nowV - liveBehindMs
                            openStrip(scrubStartVirtualMs)
                        }
                        scrubCursorMs = (scrubCursorMs + getSeekStep() * direction)
                            .coerceIn(nowV - windowMs, nowV)
                        updateFilmstrip(scrubCursorMs)
                        playerInteractionAt = System.currentTimeMillis()
                        Log.i(TAG, "STRIP(live) ${if (direction > 0) "RIGHT" else "LEFT"} → ${scrubCursorMs}ms")
                    }
                    direction > 0 && policy == DemoSeekPolicy.BACKWARD_ONLY -> {
                        // Blokada do przodu — pokaż komunikat, ale wejdź w STRIP (żeby user
                        // widział pasek i mógł przewijać w tył / wrócić do live)
                        if (layer != DemoLayer.PLAYER_UI || playerZone != PlayerZone.STRIP) {
                            scrubStartVirtualMs = activeCtl().currentVirtualPositionMs()
                            openStrip(scrubStartVirtualMs)
                        }
                        showForwardBlocked()
                    }
                    else -> {
                        if (layer != DemoLayer.PLAYER_UI || playerZone != PlayerZone.STRIP) {
                            scrubStartVirtualMs = activeCtl().currentVirtualPositionMs()
                            openStrip(scrubStartVirtualMs)
                        }
                        scrubStepWithSnap(direction)
                        playerInteractionAt = System.currentTimeMillis()
                        Log.i(TAG, "STRIP ${if (direction > 0) "RIGHT" else "LEFT"} → ${scrubCursorMs}ms")
                    }
                }
            },
            goFullscreen = { layer = DemoLayer.FULLSCREEN },
            exit = { onBackPressed() }
        )
    }

    // Odświeżenie EPG na żądanie (np. barker ready → realne duracje bloków)
    LaunchedEffect(epgRefreshTick) {
        if (epgRefreshTick > 0 && layer == DemoLayer.EPG) openEpg()
    }

    // ============ SEKWENCJA STARTOWA: download → player → ekstrakcja ============
    // Primary (DEMO TV) blokuje isReady; pozostałe barkery pobierane leniwie przy
    // pierwszym dostrojeniu (ensureBarkerReady).
    LaunchedEffect(Unit) {
        try {
            val files = ArrayList<java.io.File>()
            demoBundle.schedule.items.forEachIndexed { i, it ->
                downloadLabel = "${it.title} (${i + 1}/${demoBundle.schedule.items.size})"
                downloadProgress = 0
                files += controller.downloadToCache(it.url) { downloadProgress = it }
            }
            controller.preparePlayer(files)
            playerRef = controller.player
            filmstrip.startExtraction(files.map { it.absolutePath })
            demoBundle.ready.value = true
            isReady = true
            openEpg()
            Log.i(TAG, "Demo ready: antennaStart=${controller.antennaStartWallMs}")
        } catch (e: Exception) {
            Log.e(TAG, "Setup error: ${e.message}")
            errorMsg = e.message ?: "Błąd pobierania"
        }
    }

    // Prawdziwe kanały z prawdziwym EPG (jak w EpgDayScreen) — ładowane w tle,
    // doklejane pod wierszem DEMO TV gdy gotowe
    LaunchedEffect(isReady) {
        if (!isReady) return@LaunchedEffect
        try {
            if (!com.uxellence.tv.v3.channels.ChannelManager.isInitialized()) {
                com.uxellence.tv.v3.channels.ChannelManager.initialize(context)
            }
            val repo = com.uxellence.tv.v3.repository.EpgRepository.getInstance(context)
            val now = java.time.Instant.now()
            val rows = mutableListOf<com.uxellence.tv.v3.epg.ChannelEpgRow>()
            var channelNumber = 0
            for (channel in com.uxellence.tv.v3.channels.ChannelManager.getAllChannels(includeUnavailable = false)) {
                val epgKey = channel.epgId ?: channel.id
                val programs = try {
                    // Wczoraj + dziś: getFullDayPrograms filtruje po starcie w obrębie
                    // doby kalendarzowej, więc program nadawany PRZEZ północ (start
                    // wczoraj wieczorem) wypada z zapytania "dziś" i powstaje luka tuż
                    // po 00:00. DEMO TV (ciągła oś barkera) celuje właśnie w tę porę,
                    // więc bez doby wczorajszej realne kanały nie miały programu o tym
                    // czasie i nie dawały się zsynchronizować (match=-1).
                    val today = repo.getFullDayPrograms(epgKey, now)
                    val yesterday = repo.getFullDayPrograms(epgKey, now.minus(java.time.Duration.ofDays(1)))
                    (yesterday + today).distinctBy { it.startUtc }
                } catch (e: Exception) {
                    emptyList()
                }
                if (programs.isEmpty()) continue
                val sorted = programs.sortedBy { it.startUtc }
                val currentIdx = sorted.indexOfFirst { p ->
                    !now.isBefore(p.startUtc) && now.isBefore(p.endUtc)
                }.coerceAtLeast(0)
                channelNumber++
                rows.add(
                    com.uxellence.tv.v3.epg.ChannelEpgRow(
                        channel = channel,
                        channelNumber = channelNumber,
                        programs = sorted,
                        currentProgramIndex = currentIdx,
                        lazyListState = androidx.compose.foundation.lazy.LazyListState()
                    )
                )
                if (channelNumber >= 9) break
            }
            realChannelRows = rows
            Log.i(TAG, "Real EPG rows loaded: ${rows.size}")

            // Kanał startowy z zakładki TV (karta "Teraz w TV"): dopasuj klucz
            // kliknięcia (np. "epg_Polsat_News_1783848600") do realnego kanału
            // i dostrój PRZED openEpg — pasek single wystartuje na tym kanale.
            val initialKey = com.uxellence.tv.v3.VodDataCache.demoLiveInitialChannelKey
            if (!initialKey.isNullOrBlank()) {
                com.uxellence.tv.v3.VodDataCache.demoLiveInitialChannelKey = null
                // "epg_Polsat_News_1783848600" → "Polsat News"
                val norm = initialKey.removePrefix("epg_")
                    .substringBeforeLast('_').replace('_', ' ').trim()
                fun candidates(ch: com.uxellence.tv.v3.channels.TvChannelData) =
                    listOfNotNull(ch.epgId, ch.name, ch.id)
                // Dwa przebiegi: najpierw dopasowanie DOKŁADNE (inaczej "Polsat
                // News" złapałby też "Polsat News Polityka"), potem zawieranie
                var idx = rows.indexOfFirst { r ->
                    candidates(r.channel).any {
                        it.equals(initialKey, true) || it.equals(norm, true)
                    }
                }
                if (idx < 0) idx = rows.indexOfFirst { r ->
                    candidates(r.channel).any {
                        norm.contains(it, true) || it.contains(norm, true)
                    }
                }
                if (idx >= 0) {
                    // Wiersze EPG: barkery + 3 kanały live-stream + realne
                    tunedChannelIndex = barkers.size + 3 + idx
                    val url = rows[idx].channel.streamUrl
                    if (url.isNotBlank()) {
                        barkers.values.forEach { it.controller.player?.pause() }
                        tuneLive(url)
                        isPaused = false
                    } else {
                        tuneLive(null)
                        barkers.values.forEach { it.controller.player?.pause() }
                    }
                    Log.i(TAG, "Initial channel '$norm' → ${rows[idx].channel.name} (row ${tunedChannelIndex})")
                } else {
                    Log.i(TAG, "Initial channel '$norm' not matched — DEMO TV")
                }
            }

            if (layer == DemoLayer.EPG) openEpg()  // odśwież widok o realne kanały
        } catch (e: Exception) {
            Log.e(TAG, "Real EPG load failed: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            livePlayer?.release()
            barkers.values.forEach {
                it.controller.release()
                it.filmstrip.release()
            }
        }
    }

    // Polling pozycji + log diagnostyczny co 5 s
    LaunchedEffect(isReady) {
        if (!isReady) return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(500)
            currentVirtualMs = activeCtl().currentVirtualPositionMs()
            // Cofnięcie względem live na realnym streamie (okno DVR playlisty)
            liveBehindMs = if (isTunedLiveStream()) {
                livePlayer?.let { (it.duration - it.currentPosition).coerceAtLeast(0L) } ?: 0L
            } else 0L
            // Miniaturki live: zrzut klatki co ~3 s podczas odtwarzania (ring ~40 szt.)
            if (isTunedLiveStream() && livePlayer?.isPlaying == true && tick % 6 == 5) {
                videoViewRef?.captureFrame()?.let { bmp ->
                    liveThumbs.addLast(System.currentTimeMillis() to bmp)
                    while (liveThumbs.size > 40) liveThumbs.removeFirst().second.recycle()
                }
            }
            liveEdgeMs = activeCtl().virtualNow()
            if (++tick % 10 == 0) {
                val sched = activeBarker()?.schedule ?: demoBundle.schedule
                val mp = sched.materialPositionFor(currentVirtualMs)
                val block = sched.epgBlockAt(currentVirtualMs)
                Log.i(
                    TAG,
                    "tick: virtual=${currentVirtualMs}ms edge=${liveEdgeMs}ms " +
                        "block='${block.title}'[${block.startVirtualMs}-${block.endVirtualMs}] " +
                        "material=item${mp.mediaItemIndex}@${mp.positionMs}ms cycle=${mp.cycle}"
                )
            }
        }
    }

    // Auto-hide warstwy EPG (single i expanded): 5 s bezczynności → pełny ekran.
    // (Wytyczna 2026-07-14: pasek z programami dnia ma sam znikać, gdy user
    // nic nie klika — wcześniej single bar zostawał na stałe.)
    LaunchedEffect(layer, epgInteractionAt, epgExpanded) {
        if (layer == DemoLayer.EPG && isReady) {
            delay(EPG_TIMEOUT_MS)
            layer = DemoLayer.FULLSCREEN
        }
    }

    // Auto-hide UI playera (strefa BUTTONS) po 10 s — chyba że spauzowane
    LaunchedEffect(layer, playerZone, playerInteractionAt, isPaused) {
        if (layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.BUTTONS && !isPaused) {
            delay(PLAYER_UI_TIMEOUT_MS)
            layer = DemoLayer.FULLSCREEN
        }
    }

    // Scrub preview NIE wygasa sam (wytyczne BO / benchmark Netflix: klatka nie
    // gaśnie i nie wraca do poprzedniej pozycji). Wyjście ze STRIP tylko akcją
    // użytkownika: OK (skok), WSTECZ (powrót do oglądanej treści) lub dalsze
    // przewijanie. Brak auto-exit po czasie.

    // ============ UI ============
    // Wspólny handler klawiszy — używany przez Compose root Box ORAZ przez
    // dispatchKeyEvent DemoVideoView (fokus okna potrafi wylądować na AndroidView).
    // UWAGA: BACK celowo NIE jest tu obsługiwany — przepuszczamy go do systemowego
    // OnBackPressedDispatcher (BackHandler niżej). Obsługa w obu miejscach dawała
    // podwójne przetworzenie jednego naciśnięcia (DOWN w widoku + dispatcher na UP).
    val keyHandler = rememberUpdatedState<(Int, Int) -> Boolean> { keyCode, repeatCount ->
        scrubKeyRepeat = repeatCount   // sygnał trzymania dla scrubStepWithSnap
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            false
        } else if (!isReady) {
            false
        } else if (layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DETAIL) {
            // Detal renderuje MovieDetailScreen z własnym fokusem i klawiszami —
            // nie przechwytuj (tylko BACK idzie przez nasz BackHandler)
            false
        } else if (keyCode == android.view.KeyEvent.KEYCODE_2) {
            // DEMO: cykluj politykę przewijania DEMO TV (pokaz blokad na żywym wideo)
            demoPolicyOverride = when (demoPolicyOverride) {
                null, DemoSeekPolicy.BOTH -> DemoSeekPolicy.BACKWARD_ONLY
                DemoSeekPolicy.BACKWARD_ONLY -> DemoSeekPolicy.NONE
                DemoSeekPolicy.NONE -> DemoSeekPolicy.BOTH
            }
            demoToast = "DEMO TV: przewijanie = ${demoPolicyOverride}"
            Log.i(TAG, "demoPolicyOverride=$demoPolicyOverride")
            true
        } else if (keyCode == android.view.KeyEvent.KEYCODE_3) {
            // DEMO: przełącz wygląd paska przycisków playera (tekstowy ⇄ ikonowy wg Figmy).
            // Zapis trwały w DemoPlayerPrefs — utrzymuje się aż do ponownego "3".
            val on = DemoPlayerPrefs.toggle(context)
            // odśwież licznik auto-hide, żeby pasek został widoczny i zmiana była od razu widać
            playerInteractionAt = System.currentTimeMillis()
            demoToast = if (on) "Przyciski: wersja Figma (ikony)" else "Przyciski: wersja tekstowa"
            Log.i(TAG, "useFigmaButtons=$on")
            true
        } else {
            val handled = DemoLiveKeyController.handleKey(keyCode, layer, actions)
            Log.i(TAG, "key=$keyCode layer(after)=$layer zone=$playerZone handled=$handled")
            handled
        }
    }

    // Deduplikacja BACK z detalu: MovieDetail konsumuje KeyDown (fokus na jego
    // przyciskach) i woła nasz callback; na części środowisk system MIMO TO
    // odpala potem dispatcher na KeyUp — bez tej flagi jedno naciśnięcie
    // cofałoby dwa poziomy (detal → EPG → fullscreen)
    var detailBackHandledAtMs by remember { mutableLongStateOf(0L) }

    // JEDYNY punkt obsługi BACK: systemowy OnBackPressedDispatcher — dokładnie
    // jedno wywołanie na naciśnięcie, działa też przy zgubionym fokusie okna
    androidx.activity.compose.BackHandler(enabled = true) {
        if (!isReady) {
            onBackPressed()
        } else if (System.currentTimeMillis() - detailBackHandledAtMs < 500) {
            Log.i(TAG, "BACK(dispatcher) zdeduplikowany po BACK(detail)")
        } else {
            DemoLiveKeyController.handleKey(android.view.KeyEvent.KEYCODE_BACK, layer, actions)
            Log.i(TAG, "BACK(dispatcher) layer(after)=$layer zone=$playerZone")
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        rootFocus.requestFocus()
    }
    // Zmiany warstw potrafią zgubić fokus okna — przywracaj na root (wzorzec Issue #4).
    // WYJĄTEK: strefa DETAIL — fokus należy do MovieDetailScreen (jego przyciski)
    LaunchedEffect(layer, playerZone) {
        if (layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DETAIL) return@LaunchedEffect
        delay(50)
        rootFocus.requestFocus()
    }

    // Jeden widok wideo renderowany z dwóch pozycji (movableContentOf — CLAUDE.md
    // lesson #10): fullscreen POD warstwami albo PIP NAD opisem (strefa DESCRIPTION)
    val videoLayer = remember {
        movableContentOf { modifier: Modifier ->
            AndroidView(
                factory = { ctx ->
                    DemoVideoView(ctx) { keyCode, rpt -> keyHandler.value(keyCode, rpt) }
                        .also { videoViewRef = it }
                },
                update = { view -> view.attach(playerRef) },
                modifier = modifier
            )
        }
    }

    val isDetail = layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DETAIL

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                // Modal nagrywania otwarty: nie przechwytuj — modal (zfokusowany)
                // sam obsługuje i konsumuje wszystkie klawisze
                if (recordingCandidate != null) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                keyHandler.value(event.nativeKeyEvent.keyCode, event.nativeKeyEvent.repeatCount)
            }
            .focusable()
    ) {
        if (!isDetail) {
            // Wideo fullscreen pod warstwami
            videoLayer(Modifier.fillMaxSize())
        }

        // Plansza pobierania barkera (leniwy download przy pierwszym dostrojeniu)
        val overlayBarker = activeBarker()
        if (overlayBarker != null && !overlayBarker.ready.value && !isDetail) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A0E2E))
                    .zIndex(5f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = overlayBarker.name,
                        style = TextStyle(fontSize = demoSp(28, sy), color = Color(0x99EEEEEE))
                    )
                    Spacer(Modifier.height(sy(12)))
                    Text(
                        text = "Przygotowuję kanał…",
                        style = TextStyle(
                            fontSize = demoSp(44, sy),
                            color = Color(0xFFEEEEEE),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(sy(12)))
                    Text(
                        text = "${overlayBarker.progressLabel.value}  ${overlayBarker.progress.intValue}%",
                        style = TextStyle(fontSize = demoSp(22, sy), color = Color(0x99EEEEEE))
                    )
                }
            }
        }

        // Realny kanał BEZ streamu → "Brak live"; kanał ze streamem, którego NIE DA SIĘ
        // odtworzyć (DRM/geo/sieć) → "Kanał niedostępny". Plansza pod warstwami
        // EPG/playera, żeby flow działał identycznie. Barkery mają własną planszę wyżej.
        val tunedRowForOverlay = epgRows.getOrNull(tunedChannelIndex)
        val tunedNoStream = tunedRowForOverlay?.channel?.streamUrl.isNullOrBlank()
        if (overlayBarker == null && tunedChannelIndex != 0 && !isDetail &&
            (tunedNoStream || livePlaybackError != null)
        ) {
            val tunedRow = tunedRowForOverlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A0E2E))
                    .zIndex(5f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tunedRow?.channel?.name ?: "",
                        style = TextStyle(
                            fontSize = demoSp(28, sy),
                            color = Color(0x99EEEEEE)
                        )
                    )
                    Spacer(Modifier.height(sy(12)))
                    Text(
                        text = if (tunedNoStream) "Brak live" else "Kanał niedostępny",
                        style = TextStyle(
                            fontSize = demoSp(56, sy),
                            color = Color(0xFFEEEEEE),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(sy(12)))
                    Text(
                        text = if (tunedNoStream) {
                            "Transmisja tego kanału jest niedostępna w demo — ramówka, detal i przewijanie działają normalnie"
                        } else {
                            "Nie udało się odtworzyć strumienia (${livePlaybackError}) — " +
                                "prawdopodobnie DRM/geolokalizacja lub sieć. Ramówka i detal działają normalnie"
                        },
                        style = TextStyle(
                            fontSize = demoSp(20, sy),
                            color = Color(0x99EEEEEE)
                        )
                    )
                }
            }
        }

        // Warstwa EPG (identyczna wizualnie z EpgDayScreen pod zakładką Telewizja)
        DemoEpgLayer(
            isVisible = layer == DemoLayer.EPG && isReady,
            rows = epgRows,
            focusedChannelIndex = epgChannelIndex,
            focusedProgramIndexFor = { i ->
                epgProgramIndex[i] ?: (epgRows.getOrNull(i)?.currentProgramIndex ?: 0)
            },
            focusedTime = epgFocusedTime,
            isExpanded = epgExpanded,
            isBlackout = { ch, prog -> channelBlackout(ch, prog) },
            tunedChannelIndex = tunedChannelIndex,
            // Pozycja oglądania per typ kanału: barker = playback (timeshift możliwy
            // na KAŻDYM barkerze, nie tylko DEMO TV), live stream = live minus
            // cofnięcie w oknie, realny kanał = live (brak timeshiftu w demo)
            playbackInstant = java.time.Instant.ofEpochMilli(
                activeCtl().antennaStartWallMs + when {
                    activeBarker() != null -> currentVirtualMs
                    isTunedLiveStream() -> (liveEdgeMs - liveBehindMs).coerceAtLeast(0L)
                    else -> liveEdgeMs
                }
            ),
            nowInstant = java.time.Instant.ofEpochMilli(activeCtl().antennaStartWallMs + liveEdgeMs),
            isRecording = { title, startUtc ->
                DemoRecordingScheduler.isScheduled(title, startUtc.toEpochMilli())
            },
            sx = sx,
            sy = sy
        )

        // Zunifikowane UI playera (BUTTONS / STRIP / SNIPPET); DETAIL renderuje
        // poniżej prawdziwy MovieDetailScreen (identyczny z zakładką Wideo).
        // W STRIP materiałem głównym (nagłówek + środkowy segment paska) jest
        // blok POD KURSOREM — przeskok na sąsiedni materiał przepina metadane
        // Barker: pozycja odtwarzania; realny stream live: live minus cofnięcie w oknie DVR
        val uiRefVirtualMs = if (activeBarker() != null) currentVirtualMs
            else (liveEdgeMs - liveBehindMs).coerceAtLeast(0L)
        val uiMainBlock = (if (playerZone == PlayerZone.STRIP) {
            blockForTunedChannel(scrubCursorMs)
        } else {
            blockForTunedChannel(uiRefVirtualMs)
        }) ?: demoBundle.schedule.epgBlockAt(uiRefVirtualMs)
        val uiPrevBlock = if (uiMainBlock.startVirtualMs > 0) {
            blockForTunedChannel(uiMainBlock.startVirtualMs - 1)
        } else null
        val uiNextBlock = blockForTunedChannel(uiMainBlock.endVirtualMs + 1)
        val playerTunedRow = epgRows.getOrNull(tunedChannelIndex)
        DemoPlayerUi(
            isVisible = layer == DemoLayer.PLAYER_UI && playerZone != PlayerZone.DETAIL,
            zone = playerZone,
            block = uiMainBlock,
            prevBlock = uiPrevBlock,
            nextBlock = uiNextBlock,
            channel = playerTunedRow?.channel,
            channelNumber = playerTunedRow?.channelNumber ?: 122,
            currentVirtualMs = uiRefVirtualMs,
            // Pauza ≠ live: każde odsunięcie od live (seek LUB pauza) pokazuje
            // przycisk "Wróć do live" zamiast statusu "Oglądasz live"
            isAtLiveEdge = when {
                activeBarker() != null -> !isPaused && (liveEdgeMs - currentVirtualMs) < 5_000L
                isTunedLiveStream() -> !isPaused && liveBehindMs < 5_000L
                else -> true
            },
            liveEdgeVirtualMs = liveEdgeMs.coerceAtLeast(1L),
            dvrStartVirtualMs = if (isTunedLiveStream()) {
                // Live: początek okna DVR strumienia (nie oś barkera)
                (liveEdgeMs - (livePlayer?.duration?.takeIf { it > 0 } ?: 38_000L)).coerceAtLeast(0L)
            } else activeCtl().dvrStartMs(),
            scrubCursorMs = scrubCursorMs,
            antennaStartWallMs = activeCtl().antennaStartWallMs,
            isPaused = isPaused,
            buttonsFocusIndex = if (playerZone == PlayerZone.BUTTONS) playerButtonsFocus else -1,
            figmaButtons = useFigmaButtons,
            frames = filmstripFrames,
            blockTitleFor = { v -> blockForTunedChannel(v)?.title },
            recScheduled = DemoRecordingScheduler.isScheduled(
                uiMainBlock.title,
                activeCtl().antennaStartWallMs + uiMainBlock.startVirtualMs
            ),
            blockMetaFor = { v ->
                blockForTunedChannel(v)?.let { b ->
                    listOf(b.genre, b.year).filter { it.isNotBlank() }.joinToString(", ")
                }
            },
            sx = sx,
            sy = sy
        )

        // ===== FLOW NAGRYWANIA (Figma "Nagrywanie serii") =====
        // Modal zlecenia (zIndex 30, nad detalami) + toast potwierdzenia.
        // Zlecone nagrania: DemoRecordingScheduler → MOJE → Nagrania (Zaplanowane)
        recordingCandidate?.let { cand ->
            DemoRecordingModal(
                title = cand.title,
                subtitle = cand.subTitle,
                keepLabel = cand.keepLabel,
                onRecordEpisode = {
                    DemoRecordingScheduler.schedule(context, cand.copy(isSeries = false))
                    val started = cand.startUtcMs <= System.currentTimeMillis()
                    recordingToast = (if (started) "Rozpoczęto nagrywanie odcinka:"
                        else "Zlecono nagrywanie odcinka:") to cand.title
                    recordingCandidate = null
                    Log.i(TAG, "REC: zlecono odcinek '${cand.title}'")
                },
                onRecordSeries = {
                    DemoRecordingScheduler.schedule(context, cand.copy(isSeries = true))
                    recordingToast = "Zlecono nagrywanie serii:" to cand.title
                    recordingCandidate = null
                    Log.i(TAG, "REC: zlecono serię '${cand.title}'")
                },
                onDismiss = { recordingCandidate = null },
                sx = sx, sy = sy
            )
        }
        DemoInfoToast(
            text = demoToast,
            onHidden = { demoToast = null },
            sx = sx, sy = sy
        )
        recordingToast?.let { (header, recTitle) ->
            DemoRecordingToast(
                header = header,
                title = recTitle,
                visible = true,
                onHidden = { recordingToast = null },
                sx = sx, sy = sy
            )
        }

        if (isDetail) {
            val slide = detailSlide
            if (slide != null) {
                // PRAWDZIWY MovieDetailScreen w trybie WIDEO — wygląd identyczny
                // z detalami programów pod zakładką Wideo. Własny fokus i klawisze;
                // BACK przechodzi przez nasz BackHandler → playerBack
                Box(modifier = Modifier
                    .fillMaxSize()
                    .zIndex(14f)
                ) {
                    com.uxellence.tv.v3.moviedetail.MovieDetailScreen(
                        item = slide,
                        // MovieDetail KONSUMUJE KeyDown BACK (fokus na jego przyciskach,
                        // np. Nagraj/Przypomnij przy programie przyszłym) — skonsumowany
                        // DOWN wyłącza systemowy back-tracking i dispatcher NIGDY nie
                        // odpala. Dlatego tu wołamy tę samą akcję co BackHandler.
                        onBackPressed = {
                            detailBackHandledAtMs = System.currentTimeMillis()
                            DemoLiveKeyController.handleKey(
                                android.view.KeyEvent.KEYCODE_BACK, layer, actions
                            )
                            Log.i(TAG, "BACK(detail) layer(after)=$layer zone=$playerZone")
                        },
                        // Program PRZYSZŁY: nie da się go oglądać — [Nagraj, Przypomnij];
                        // miniony/bieżący: standardowe [Oglądaj, Do obejrzenia]
                        customButtons = when {
                            detailTiming == BlockTiming.FUTURE -> {
                                val scheduled = DemoRecordingScheduler
                                    .isScheduled(slide.title, detailStartWallMs)
                                listOf(
                                    if (scheduled) "Anuluj nagranie" else "Nagraj",
                                    "Przypomnij"
                                )
                            }
                            detailTiming == BlockTiming.PAST &&
                                barkerFor(detailChannelIndex) == null -> {
                                // Program MINIONY na kanale bez catchupu (np.
                                // Stargaze): nie da się go obejrzeć ponownie —
                                // BEZ przycisków Oglądaj / Do obejrzenia
                                emptyList()
                            }
                            else -> null
                        },
                        onCustomButtonClicked = { index ->
                            if (index == 0 && DemoRecordingScheduler
                                    .isScheduled(slide.title, detailStartWallMs)
                            ) {
                                // Zlecone → ANULUJ nagranie (przycisk zmienia się z powrotem)
                                DemoRecordingScheduler.cancel(context, slide.title, detailStartWallMs)
                                demoToast = "Anulowano nagrywanie: ${slide.title}"
                            } else if (index == 0) {
                                // Nagraj (program przyszły) → modal zlecenia nagrania
                                // (flow Figma "Nagrywanie serii"); zlecone trafia do
                                // MOJE → Nagrania (Zaplanowane)
                                recordingCandidate = DemoRecordingScheduler.ScheduledRecording(
                                    title = slide.title,
                                    subTitle = slide.genre,
                                    channelId = epgRows.getOrNull(detailChannelIndex)
                                        ?.channel?.id ?: "",
                                    channelName = detailChannelName,
                                    startUtcMs = detailStartWallMs,
                                    endUtcMs = detailEndWallMs,
                                    imageUrl = slide.backgroundUrl.ifBlank { null },
                                    isSeries = false,
                                    keepLabel = "3 miesiące"
                                )
                            } else {
                                demoToast = "Przypomnimy o programie: ${slide.title} (atrapa)"
                            }
                        },
                        onWatchClicked = {
                            val detailBarker = barkerFor(detailChannelIndex)
                            if (detailBarker != null) {
                                // Kanał barker: dostrój (jeśli inny niż oglądany) i graj
                                when (detailTiming) {
                                    BlockTiming.CURRENT -> {
                                        tunedChannelIndex = detailChannelIndex
                                        tuneBarker(detailBarker)
                                        openPlayerButtons()
                                        Log.i(TAG, "DETAIL: Oglądaj (bieżący, ${detailBarker.channelId}) → PLAYER_UI")
                                    }
                                    BlockTiming.PAST -> {
                                        tunedChannelIndex = detailChannelIndex
                                        tuneBarker(detailBarker)
                                        detailBarker.controller.seekToVirtual(detailStartVirtualMs)
                                        isPaused = false
                                        // Materiał z przeszłości: od razu CZYSTY OBRAZ —
                                        // bez pasków/kontrolek (user wywołuje UI sam: OK/strzałki)
                                        layer = DemoLayer.FULLSCREEN
                                        Log.i(TAG, "DETAIL: Oglądaj od początku → ${detailStartVirtualMs}ms (FULLSCREEN)")
                                    }
                                    BlockTiming.FUTURE -> { /* nieosiągalne: FUTURE ma customButtons */ }
                                }
                            } else {
                                val liveUrl = epgRows.getOrNull(detailChannelIndex)
                                    ?.channel?.streamUrl.orEmpty()
                                when {
                                    liveUrl.isNotBlank() && detailTiming == BlockTiming.CURRENT -> {
                                        // Kanał z realnym streamem: Oglądaj = dostrój live
                                        tunedChannelIndex = detailChannelIndex
                                        barkers.values.forEach { it.controller.player?.pause() }
                                        tuneLive(liveUrl)
                                        isPaused = false
                                        openPlayerButtons()
                                        Log.i(TAG, "DETAIL: Oglądaj (live $detailChannelName) → PLAYER_UI")
                                    }
                                    liveUrl.isNotBlank() -> {
                                        demoToast = "Ten program już się skończył — kanał nie ma catchup"
                                    }
                                    else -> {
                                        demoToast = "Demo: brak streamu tego kanału"
                                    }
                                }
                            }
                        },
                        // Linia "czas + NA ŻYWO" nad tytułem wg Figmy (Detail 5507:5100).
                        // NA ŻYWO + kropka REC gdy program bieżący; "od początku" dla
                        // bieżącego/minionego (timeshift dostępny).
                        wideoHeaderSlot = {
                            DemoDetailInfoLine(
                                timeRange = formatWall(detailStartWallMs, false) +
                                    " – " + formatWall(detailEndWallMs, false),
                                isLive = detailTiming == BlockTiming.CURRENT,
                                canStartOver = detailTiming != BlockTiming.FUTURE,
                                sx = sx, sy = sy
                            )
                        },
                        // Logo kanału (Figma 208x208) na szczycie SCROLLOWANEJ kolumny
                        // — przewija się razem z kartą (wytyczna 2026-07-14)
                        wideoTopSlot = {
                            Box(
                                modifier = Modifier
                                    .size(sx(208), sy(208))
                                    .clip(RoundedCornerShape(sx(8)))
                            ) {
                                if (!detailChannelLogoUrl.isNullOrBlank()) {
                                    coil.compose.AsyncImage(
                                        model = detailChannelLogoUrl,
                                        contentDescription = "Logo kanału",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    DemoChannelLogoBadge(
                                        channelNumber = detailChannelNumber,
                                        channelName = detailChannelName,
                                        sx = sx, sy = sy
                                    )
                                }
                            }
                        }
                    )
                }
            }
            // PIP: ten sam widok wideo w prawym dolnym rogu, NAD warstwą detalu.
            // Wymiary/zaokrąglenie/margines wg Figmy (Detail 5507:5100):
            // 572x336, radius 32, 32 px od prawej i dolnej krawędzi.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = sx(32), bottom = sy(32))
                    .width(sx(572))
                    .height(sy(336))
                    .zIndex(15f)
                    .clip(RoundedCornerShape(sx(32)))
            ) {
                videoLayer(Modifier.fillMaxSize())
            }
        }

        // Pobieranie / błąd
        if (!isReady) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (errorMsg != null) {
                    Text(
                        "Błąd: $errorMsg",
                        style = TextStyle(fontSize = demoSp(24, sy), color = Color(0xFFFF6B6B))
                    )
                } else {
                    Text(
                        "Pobieranie $downloadLabel… ${downloadProgress}%",
                        style = TextStyle(fontSize = demoSp(24, sy), color = Color.White)
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .width(sx(400))
                            .height(sy(8))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(downloadProgress / 100f)
                                .fillMaxHeight()
                                .background(Color(0xFF5AECD3))
                        )
                    }
                }
            }
        }
    }
}
