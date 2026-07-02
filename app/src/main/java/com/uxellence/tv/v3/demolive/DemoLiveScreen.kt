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
import kotlinx.coroutines.delay

private const val TAG = "DemoLive"
private const val SEEK_STEP_MS = 10_000L
private const val EPG_TIMEOUT_MS = 12_000L
private const val PLAYER_UI_TIMEOUT_MS = 10_000L
// Stargaze — realny kanał FAST (Tivio Studio, w ofercie Play), stream HLS.
// Publicznego EPG brak (Tivio API wymaga klucza SDK) → syntetyczna ramówka
// z realnych formatów kanału w buildStargazeRow().
private const val STARGAZE_STREAM_URL =
    "https://play.streaming.tivio.studio/channels/MQniU6Lt1LKPU0V3UwJl/index.m3u8"

/**
 * Widok wideo demo: TextureView (nie SurfaceView — z-order w Compose) z zachowaniem
 * proporcji obrazu (letterbox przez skalowanie TextureView) + przekazywaniem klawiszy
 * do handlera ekranu (wzorzec z VodPlayerScreen — fokus okna potrafi trafić w View).
 */
private class DemoVideoView(
    context: android.content.Context,
    private val onKey: (Int) -> Boolean
) : android.widget.FrameLayout(context) {

    private val textureView = android.view.TextureView(context)
    private var attachedPlayer: com.google.android.exoplayer2.ExoPlayer? = null

    private val videoListener = object : com.google.android.exoplayer2.Player.Listener {
        override fun onVideoSizeChanged(videoSize: com.google.android.exoplayer2.video.VideoSize) {
            applyAspect(videoSize)
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        addView(
            textureView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER)
        )
        isFocusable = true
        isFocusableInTouchMode = false
    }

    /** Zrzut bieżącej klatki (miniaturki taśmy dla realnego live). */
    fun captureFrame(): android.graphics.Bitmap? =
        if (textureView.isAvailable) textureView.getBitmap(320, 180) else null

    fun attach(player: com.google.android.exoplayer2.ExoPlayer?) {
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(videoListener)
        // KLUCZOWE przy przełączaniu playerów (barker ⇄ live HLS): odepnij TextureView
        // od starego playera — inaczej stary trzyma surface i na ekranie zostaje jego
        // zamrożona klatka, a nowy gra bez obrazu.
        attachedPlayer?.clearVideoTextureView(textureView)
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
        if (event.action == android.view.KeyEvent.ACTION_DOWN && onKey(event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }
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
    val controller = remember { DemoChannelPlayerController(context) }
    val filmstrip = remember { DemoFilmstripProvider() }

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
    var isPaused by remember { mutableStateOf(false) }
    // Dostrojony kanał: 0 = DEMO TV (gra barker), >0 = realny kanał. Kanał z niepustym
    // streamUrl (np. Stargaze — HLS z Tivio) gra PRAWDZIWE live przez livePlayer;
    // pozostałe realne kanały pokazują planszę "Brak live". UI playera działa tak samo.
    var tunedChannelIndex by remember { mutableIntStateOf(0) }
    // Drugi ExoPlayer do realnego live (HLS) — barker (controller.player) zostaje
    // nietknięty i wraca po przełączeniu na DEMO TV.
    var livePlayer by remember { mutableStateOf<com.google.android.exoplayer2.ExoPlayer?>(null) }

    /** Przełącz źródło wideo: url=null/blank → barker; inaczej realne live (HLS). */
    fun tuneLive(url: String?) {
        livePlayer?.release()
        livePlayer = null
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
                }
            }
        })
        exo.prepare()
        livePlayer = exo
        playerRef = exo
        Log.i(TAG, "tuneLive → $url")
    }

    // Warstwa EPG: wiersz 0 = DEMO TV (sztuczna ramówka), 1..N = prawdziwe kanały z EPG
    var epgRows by remember { mutableStateOf<List<com.uxellence.tv.v3.epg.ChannelEpgRow>>(emptyList()) }
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
    var forwardBlockedAt by remember { mutableLongStateOf(0L) }
    var forwardBlockedMsgVisible by remember { mutableStateOf(false) }
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
    var detailChannelNumber by remember { mutableIntStateOf(122) }
    var detailChannelIndex by remember { mutableIntStateOf(0) }  // indeks w epgRows (tuning z detalu)

    // Akceleracja seeka (wzorzec z VodPlayerScreen)
    var rapidPressCount by remember { mutableIntStateOf(0) }
    var lastPressTimeNano by remember { mutableLongStateOf(0L) }
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

    fun updateFilmstrip(centerMs: Long) {
        filmstripFrames = if (tunedChannelIndex == 0) {
            filmstrip.framesAround(
                centerVirtualMs = centerMs,
                liveEdgeVirtualMs = controller.virtualNow(),
                stepMs = SEEK_STEP_MS,
                sideCount = 3,
                dvrStartVirtualMs = controller.dvrStartMs()
            )
        } else if (livePlayer != null) {
            // Realny stream live: miniaturki ze zrzutów TextureView (ring buffer).
            // Slot dostaje najbliższą klatkę w tolerancji 5 s; sprzed dostrojenia
            // klatek nie ma — slot pusty.
            (-3..3).map { i ->
                val offset = i * SEEK_STEP_MS
                val slotWall = controller.antennaStartWallMs + centerMs + offset
                val bmp = liveThumbs.minByOrNull { kotlin.math.abs(it.first - slotWall) }
                    ?.takeIf { kotlin.math.abs(it.first - slotWall) <= 5_000L }
                    ?.second?.takeIf { !it.isRecycled }
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
        return DemoSeekPolicyClassifier.policyForChannel(name, isDemoBarker = tunedChannelIndex == 0)
    }

    fun showForwardBlocked() {
        forwardBlockedAt = System.currentTimeMillis()
        forwardBlockedMsgVisible = true
        playerInteractionAt = System.currentTimeMillis()
        Log.i(TAG, "Forward seek blocked (policy=${tunedSeekPolicy()})")
    }

    // Blackout (brak praw) — kanały z realnym streamem (Stargaze) nigdy nie mają
    // blackoutów (wszystko odtwarzalne live); reszta wg deterministycznego klasyfikatora
    fun channelBlackout(chIdx: Int, progIdx: Int): Boolean {
        val hasStream = epgRows.getOrNull(chIdx)?.channel?.streamUrl?.isNotBlank() == true
        return !hasStream && DemoSeekPolicyClassifier.isBlackout(chIdx, progIdx)
    }

    // Blackout (brak praw) programu pod daną pozycją wirtualną na dostrojonym kanale
    fun isBlackoutAtVirtual(virtualMs: Long): Boolean {
        val row = epgRows.getOrNull(tunedChannelIndex) ?: return false
        val instant = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + virtualMs)
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
    fun blockForTunedChannel(virtualMs: Long): DemoChannelSchedule.EpgBlock? {
        if (tunedChannelIndex == 0) {
            return DemoChannelSchedule.epgBlockAt(virtualMs)
        }
        val row = epgRows.getOrNull(tunedChannelIndex) ?: return null
        val instant = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + virtualMs)
        val program = row.programs.firstOrNull { p ->
            !instant.isBefore(p.startUtc) && instant.isBefore(p.endUtc)
        } ?: return null
        return DemoChannelSchedule.EpgBlock(
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
        scrubCursorMs = initialCursor
        updateFilmstrip(initialCursor)
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
        val startV = program.startUtc.toEpochMilli() - controller.antennaStartWallMs
        val endV = program.endUtc.toEpochMilli() - controller.antennaStartWallMs
        val nowV = controller.currentVirtualPositionMs()
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
            endV <= controller.virtualNow() -> BlockTiming.PAST
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

    // Wiersz DEMO TV: bloki sztucznej ramówki jako EpgProgram (cover = klatka z materiału)
    fun buildDemoRow(): com.uxellence.tv.v3.epg.ChannelEpgRow {
        val nowV = controller.currentVirtualPositionMs()
        val edge = controller.virtualNow()
        val blocks = DemoChannelSchedule.blocksAround(nowV, before = 3, after = 8)
        val programs = blocks.map { b ->
            com.uxellence.tv.v3.epg.EpgProgram(
                channelId = "demo",
                title = b.title,
                startUtc = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + b.startVirtualMs),
                endUtc = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + b.endVirtualMs),
                description = b.description,
                categories = listOf(b.genre, b.year, b.country, b.age),
                iconUrl = b.coverUrl ?: filmstrip.thumbUriFor(b.startVirtualMs, edge, context.cacheDir)
            )
        }
        val nowInstant = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + nowV)
        val currentIdx = programs.indexOfFirst { p ->
            !nowInstant.isBefore(p.startUtc) && nowInstant.isBefore(p.endUtc)
        }.coerceAtLeast(0)
        return com.uxellence.tv.v3.epg.ChannelEpgRow(
            channel = com.uxellence.tv.v3.channels.TvChannelData(
                id = "demo",
                name = "DEMO TV",
                streamUrl = "",
                logoUrl = null,
                epgId = "demo"
            ),
            channelNumber = 122,
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
                iconUrl = null
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

    fun openEpg() {
        val demoRow = buildDemoRow()
        epgRows = listOf(demoRow, buildStargazeRow()) + realChannelRows
        // Start jak pod Telewizją: pasek 1 kanału (tego, który jest na ekranie)
        epgExpanded = false
        epgChannelIndex = tunedChannelIndex.coerceIn(0, (epgRows.size - 1).coerceAtLeast(0))
        // TIME SYNC musi celować w program AKTUALNIE ODTWARZANY (pozycja playbacku,
        // nie zegar ścienny) — przy timeshifcie to różne programy; bez tego fokus
        // ląduje poza wycentrowanym kafelkiem i OK otwiera detal zamiast playera
        val focusRow = epgRows.getOrNull(epgChannelIndex) ?: demoRow
        val focused = focusRow.programs.getOrNull(focusRow.currentProgramIndex)?.startUtc
            ?: java.time.Instant.now()
        epgFocusedTime = focused
        epgProgramIndex.clear()
        epgRows.forEachIndexed { i, row ->
            val match = row.programs.indexOfFirst { p ->
                !focused.isBefore(p.startUtc) && focused.isBefore(p.endUtc)
            }
            epgProgramIndex[i] = if (match >= 0) match else row.currentProgramIndex
        }
        epgInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.EPG
    }

    // Indeks programu AKTUALNIE OGLĄDANEGO na danym kanale: DEMO TV = program pod
    // pozycją odtwarzania (przy timeshifcie miniony), realny kanał = program live
    fun liveProgramIndexFor(channelIdx: Int): Int {
        val row = epgRows.getOrNull(channelIdx) ?: return 0
        val refMs = if (channelIdx == 0) controller.currentVirtualPositionMs() else controller.virtualNow()
        val instant = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + refMs)
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
                    // TIME SYNC: pozostałe kanały przewiną się do czasu startu fokusowanego programu
                    row.programs.getOrNull(newIdx)?.let { epgFocusedTime = it.startUtc }
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
                if (row != null && program != null &&
                    channelBlackout(epgChannelIndex, focusedProgIdx)
                ) {
                    // Blackout (brak praw) — nie da się odtworzyć/dostroić
                    android.widget.Toast.makeText(
                        context, "Tego programu nie można odtworzyć", android.widget.Toast.LENGTH_SHORT
                    ).show()
                    epgInteractionAt = System.currentTimeMillis()
                    Log.i(TAG, "EPG select: '${program.title}' blackout → blocked")
                } else if (row != null && program != null) {
                    val isDemo = epgChannelIndex == 0
                    val targetStart = program.startUtc.toEpochMilli() - controller.antennaStartWallMs
                    val targetEnd = program.endUtc.toEpochMilli() - controller.antennaStartWallMs
                    // "Teraz na żywo": DEMO TV wg pozycji odtwarzania, realne kanały wg zegara
                    val nowRef = if (isDemo) controller.currentVirtualPositionMs() else controller.virtualNow()
                    val playingNow = nowRef in targetStart until targetEnd
                    when {
                        playingNow && epgExpanded -> {
                            // Wybór programu nadawanego TERAZ na warstwie wielu kanałów →
                            // dostrój kanał i ZWIŃ do paska tylko tego kanału (EPG single).
                            // Player dopiero przy kolejnym OK na tym pasku.
                            tunedChannelIndex = epgChannelIndex
                            val tunedUrl = epgRows.getOrNull(epgChannelIndex)?.channel?.streamUrl.orEmpty()
                            when {
                                epgChannelIndex == 0 -> {
                                    // DEMO TV: z powrotem barker
                                    tuneLive(null)
                                    controller.player?.play()
                                    isPaused = false
                                }
                                tunedUrl.isNotBlank() -> {
                                    // Kanał z realnym streamem (Stargaze): graj live HLS
                                    controller.player?.pause()
                                    tuneLive(tunedUrl)
                                    isPaused = false
                                }
                                else -> {
                                    // Realny kanał bez streamu: plansza "Brak live"
                                    tuneLive(null)
                                    controller.player?.pause()
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
                        else -> {
                            // Program miniony/przyszły (dowolny kanał) → detal jak na Wideo
                            openDetail(
                                program = program,
                                channelLogoUrl = row.channel.logoUrl,
                                isDemo = isDemo,
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
                        // Pauza ≠ live — przy pauzie slot 1 jest przyciskiem "Wróć do live"
                        val atLive = tunedChannelIndex != 0 || (!isPaused && controller.isAtLiveEdge())
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
                            scrubCursorMs = (scrubCursorMs + getSeekStep() * dir)
                                .coerceIn(controller.dvrStartMs(), controller.virtualNow())
                            updateFilmstrip(scrubCursorMs)
                            forwardBlockedMsgVisible = false
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
                            layer = DemoLayer.FULLSCREEN
                            Log.i(TAG, "STRIP(live) seek → ${scrubCursorMs}ms → FULLSCREEN")
                        } else if (isBlackoutAtVirtual(scrubCursorMs)) {
                            // Blackout (brak praw) — nie odtwarzaj tego fragmentu
                            android.widget.Toast.makeText(
                                context, "Tego programu nie można odtworzyć", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            Log.i(TAG, "STRIP OK on blackout → blocked")
                        } else {
                            // OK na taśmie = skok do kursora i ukrycie WSZYSTKICH warstw UI
                            // (czysty player FULLSCREEN, bez paska kontrolek/opisu)
                            controller.seekToVirtual(scrubCursorMs)
                            isPaused = false
                            rapidPressCount = 0
                            forwardBlockedMsgVisible = false
                            playerZone = PlayerZone.BUTTONS  // stan wyjściowy gdy UI wróci
                            playerButtonsFocus = 0
                            layer = DemoLayer.FULLSCREEN
                            Log.i(TAG, "STRIP seek → ${scrubCursorMs}ms → FULLSCREEN (UI ukryte)")
                        }
                    }
                    PlayerZone.BUTTONS -> when (playerButtonsFocus) {
                        0 -> {  // Zatrzymaj / Wznów — na AKTYWNYM playerze (live lub barker)
                            val p = if (isTunedLiveStream()) livePlayer else controller.player
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
                                controller.seekToLiveEdge()
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
                                val block = DemoChannelSchedule.epgBlockAt(controller.currentVirtualPositionMs())
                                controller.seekToVirtual(block.startVirtualMs)
                                isPaused = false
                                Log.i(TAG, "Player: zacznij od początku → ${block.startVirtualMs}ms")
                            }
                        }
                        else -> { /* Nagraj / Napisy — atrapy */ }
                    }
                    PlayerZone.SNIPPET -> {
                        // OK na skrócie opisu → detal bieżącego programu DEMO TV
                        val block = DemoChannelSchedule.epgBlockAt(controller.currentVirtualPositionMs())
                        val program = com.uxellence.tv.v3.epg.EpgProgram(
                            channelId = "demo",
                            title = block.title,
                            startUtc = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + block.startVirtualMs),
                            endUtc = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + block.endVirtualMs),
                            description = block.description,
                            categories = listOf(block.genre, block.year, block.country),
                            iconUrl = block.coverUrl
                                ?: filmstrip.thumbUriFor(block.startVirtualMs, controller.virtualNow(), context.cacheDir)
                        )
                        openDetail(program, channelLogoUrl = null, isDemo = true, fromEpg = false)
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
                        if (isTunedLiveStream()) {
                            // Realny live: taśma na osi wall-clock, kursor od bieżącej
                            // pozycji (live minus cofnięcie w oknie DVR)
                            playerZone = PlayerZone.STRIP
                            scrubStartVirtualMs = controller.virtualNow() - liveBehindMs
                            scrubCursorMs = scrubStartVirtualMs
                            updateFilmstrip(scrubCursorMs)
                        } else if (tunedSeekPolicy() == DemoSeekPolicy.NONE) {
                            // Telewizja bez startover — brak przewijania, nie otwieraj taśmy
                            android.widget.Toast.makeText(
                                context, "Przewijanie niedostępne na tym kanale", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Z przycisków na taśmę (kursor startuje z bieżącej pozycji)
                            playerZone = PlayerZone.STRIP
                            scrubStartVirtualMs = controller.currentVirtualPositionMs()
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
                        forwardBlockedMsgVisible = false
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
                    policy == DemoSeekPolicy.NONE -> {
                        // Telewizja bez startover — brak przewijania
                        android.widget.Toast.makeText(
                            context, "Przewijanie niedostępne na tym kanale", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    direction > 0 && policy == DemoSeekPolicy.BACKWARD_ONLY -> {
                        // Blokada do przodu — pokaż komunikat, ale wejdź w STRIP (żeby user
                        // widział pasek i mógł przewijać w tył / wrócić do live)
                        if (layer != DemoLayer.PLAYER_UI || playerZone != PlayerZone.STRIP) {
                            scrubStartVirtualMs = controller.currentVirtualPositionMs()
                            openStrip(scrubStartVirtualMs)
                        }
                        showForwardBlocked()
                    }
                    else -> {
                        if (layer != DemoLayer.PLAYER_UI || playerZone != PlayerZone.STRIP) {
                            scrubStartVirtualMs = controller.currentVirtualPositionMs()
                            openStrip(scrubStartVirtualMs)
                        }
                        scrubCursorMs = (scrubCursorMs + getSeekStep() * direction)
                            .coerceIn(controller.dvrStartMs(), controller.virtualNow())
                        updateFilmstrip(scrubCursorMs)
                        playerInteractionAt = System.currentTimeMillis()
                        Log.i(TAG, "STRIP ${if (direction > 0) "RIGHT" else "LEFT"} → ${scrubCursorMs}ms")
                    }
                }
            },
            goFullscreen = { layer = DemoLayer.FULLSCREEN },
            exit = { onBackPressed() }
        )
    }

    // ============ SEKWENCJA STARTOWA: download → player → ekstrakcja ============
    LaunchedEffect(Unit) {
        try {
            downloadLabel = "Sintel (1/2)"
            val fileA = controller.downloadToCache(DemoChannelSchedule.URL_A) { downloadProgress = it }
            downloadLabel = "Big Buck Bunny (2/2)"
            downloadProgress = 0
            val fileB = controller.downloadToCache(DemoChannelSchedule.URL_B) { downloadProgress = it }
            controller.preparePlayer(fileA, fileB)
            playerRef = controller.player
            filmstrip.startExtraction(fileA.absolutePath, fileB.absolutePath)
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
            if (layer == DemoLayer.EPG) openEpg()  // odśwież widok o realne kanały
        } catch (e: Exception) {
            Log.e(TAG, "Real EPG load failed: ${e.message}")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            livePlayer?.release()
            controller.release()
            filmstrip.release()
        }
    }

    // Polling pozycji + log diagnostyczny co 5 s
    LaunchedEffect(isReady) {
        if (!isReady) return@LaunchedEffect
        var tick = 0
        while (true) {
            delay(500)
            currentVirtualMs = controller.currentVirtualPositionMs()
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
            liveEdgeMs = controller.virtualNow()
            if (++tick % 10 == 0) {
                val mp = DemoChannelSchedule.materialPositionFor(currentVirtualMs)
                val block = DemoChannelSchedule.epgBlockAt(currentVirtualMs)
                Log.i(
                    TAG,
                    "tick: virtual=${currentVirtualMs}ms edge=${liveEdgeMs}ms " +
                        "block='${block.title}'[${block.startVirtualMs}-${block.endVirtualMs}] " +
                        "material=item${mp.mediaItemIndex}@${mp.positionMs}ms cycle=${mp.cycle}"
                )
            }
        }
    }

    // Auto-hide tylko dla rozwiniętej warstwy EPG (przeglądanie wielu kanałów).
    // Pasek pojedynczego kanału (single) NIE znika sam — zostaje aż użytkownik
    // sam zadziała (OK → player, WSTECZ → pełny ekran, GÓRA/DÓŁ → wiele kanałów).
    // Inaczej „wracając do kanału" pasek po chwili sam przechodził w player.
    LaunchedEffect(layer, epgInteractionAt, epgExpanded) {
        if (layer == DemoLayer.EPG && isReady && epgExpanded) {
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

    // Komunikat „Przewijanie do przodu nie jest dostępne" znika po ~3 s
    LaunchedEffect(forwardBlockedAt) {
        if (forwardBlockedMsgVisible) {
            delay(3_000)
            forwardBlockedMsgVisible = false
        }
    }

    // ============ UI ============
    // Wspólny handler klawiszy — używany przez Compose root Box ORAZ przez
    // dispatchKeyEvent DemoVideoView (fokus okna potrafi wylądować na AndroidView).
    // UWAGA: BACK celowo NIE jest tu obsługiwany — przepuszczamy go do systemowego
    // OnBackPressedDispatcher (BackHandler niżej). Obsługa w obu miejscach dawała
    // podwójne przetworzenie jednego naciśnięcia (DOWN w widoku + dispatcher na UP).
    val keyHandler = rememberUpdatedState<(Int) -> Boolean> { keyCode ->
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
            android.widget.Toast.makeText(
                context, "DEMO TV: przewijanie = ${demoPolicyOverride}", android.widget.Toast.LENGTH_SHORT
            ).show()
            Log.i(TAG, "demoPolicyOverride=$demoPolicyOverride")
            true
        } else if (keyCode == android.view.KeyEvent.KEYCODE_3) {
            // DEMO: przełącz wygląd paska przycisków playera (tekstowy ⇄ ikonowy wg Figmy).
            // Zapis trwały w DemoPlayerPrefs — utrzymuje się aż do ponownego "3".
            val on = DemoPlayerPrefs.toggle(context)
            // odśwież licznik auto-hide, żeby pasek został widoczny i zmiana była od razu widać
            playerInteractionAt = System.currentTimeMillis()
            android.widget.Toast.makeText(
                context,
                if (on) "Przyciski: wersja Figma (ikony)" else "Przyciski: wersja tekstowa",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            Log.i(TAG, "useFigmaButtons=$on")
            true
        } else {
            val handled = DemoLiveKeyController.handleKey(keyCode, layer, actions)
            Log.i(TAG, "key=$keyCode layer(after)=$layer zone=$playerZone handled=$handled")
            handled
        }
    }

    // JEDYNY punkt obsługi BACK: systemowy OnBackPressedDispatcher — dokładnie
    // jedno wywołanie na naciśnięcie, działa też przy zgubionym fokusie okna
    androidx.activity.compose.BackHandler(enabled = true) {
        if (!isReady) {
            onBackPressed()
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
                    DemoVideoView(ctx) { keyCode -> keyHandler.value(keyCode) }
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
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                keyHandler.value(event.nativeKeyEvent.keyCode)
            }
            .focusable()
    ) {
        if (!isDetail) {
            // Wideo fullscreen pod warstwami
            videoLayer(Modifier.fillMaxSize())
        }

        // Realny kanał BEZ streamu — plansza "Brak live" zamiast wideo (pod warstwami
        // EPG/playera). Kanały ze streamUrl (Stargaze) grają prawdziwe live w videoLayer.
        val tunedRowForOverlay = epgRows.getOrNull(tunedChannelIndex)
        if (tunedChannelIndex != 0 && !isDetail &&
            tunedRowForOverlay?.channel?.streamUrl.isNullOrBlank()
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
                        text = "Brak live",
                        style = TextStyle(
                            fontSize = demoSp(56, sy),
                            color = Color(0xFFEEEEEE),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                    Spacer(Modifier.height(sy(12)))
                    Text(
                        text = "Transmisja tego kanału jest niedostępna w demo — ramówka, detal i przewijanie działają normalnie",
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
            // Realny kanał nie ma timeshiftu w demo — pozycja oglądania = live
            playbackInstant = java.time.Instant.ofEpochMilli(
                controller.antennaStartWallMs +
                    (if (tunedChannelIndex == 0) currentVirtualMs else liveEdgeMs)
            ),
            nowInstant = java.time.Instant.ofEpochMilli(controller.antennaStartWallMs + liveEdgeMs),
            sx = sx,
            sy = sy
        )

        // Zunifikowane UI playera (BUTTONS / STRIP / SNIPPET); DETAIL renderuje
        // poniżej prawdziwy MovieDetailScreen (identyczny z zakładką Wideo).
        // W STRIP materiałem głównym (nagłówek + środkowy segment paska) jest
        // blok POD KURSOREM — przeskok na sąsiedni materiał przepina metadane
        // Realny stream live: pozycja = live minus cofnięcie w oknie DVR (kreska na pasku)
        val uiRefVirtualMs = if (tunedChannelIndex == 0) currentVirtualMs
            else (liveEdgeMs - liveBehindMs).coerceAtLeast(0L)
        val uiMainBlock = (if (playerZone == PlayerZone.STRIP) {
            blockForTunedChannel(scrubCursorMs)
        } else {
            blockForTunedChannel(uiRefVirtualMs)
        }) ?: DemoChannelSchedule.epgBlockAt(uiRefVirtualMs)
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
                tunedChannelIndex == 0 -> !isPaused && (liveEdgeMs - currentVirtualMs) < 5_000L
                isTunedLiveStream() -> !isPaused && liveBehindMs < 5_000L
                else -> true
            },
            liveEdgeVirtualMs = liveEdgeMs.coerceAtLeast(1L),
            dvrStartVirtualMs = if (isTunedLiveStream()) {
                // Live: początek okna DVR strumienia (nie oś barkera)
                (liveEdgeMs - (livePlayer?.duration?.takeIf { it > 0 } ?: 38_000L)).coerceAtLeast(0L)
            } else controller.dvrStartMs(),
            scrubCursorMs = scrubCursorMs,
            antennaStartWallMs = controller.antennaStartWallMs,
            isPaused = isPaused,
            buttonsFocusIndex = if (playerZone == PlayerZone.BUTTONS) playerButtonsFocus else -1,
            figmaButtons = useFigmaButtons,
            forwardBlockedMsgVisible = forwardBlockedMsgVisible,
            frames = filmstripFrames,
            sx = sx,
            sy = sy
        )

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
                        onBackPressed = { /* BACK obsługuje BackHandler (dispatcher) */ },
                        // Program PRZYSZŁY: nie da się go oglądać — [Nagraj, Przypomnij];
                        // miniony/bieżący: standardowe [Oglądaj, Do obejrzenia]
                        customButtons = if (detailTiming == BlockTiming.FUTURE) {
                            listOf("Nagraj", "Przypomnij")
                        } else null,
                        onCustomButtonClicked = { index ->
                            val msg = if (index == 0) {
                                "Nagranie zaplanowane: ${slide.title} (atrapa)"
                            } else {
                                "Przypomnimy o programie: ${slide.title} (atrapa)"
                            }
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onWatchClicked = {
                            if (detailIsDemo) {
                                when (detailTiming) {
                                    BlockTiming.CURRENT -> {
                                        openPlayerButtons()
                                        Log.i(TAG, "DETAIL: Oglądaj (bieżący) → PLAYER_UI")
                                    }
                                    BlockTiming.PAST -> {
                                        controller.seekToVirtual(detailStartVirtualMs)
                                        isPaused = false
                                        openPlayerButtons()
                                        Log.i(TAG, "DETAIL: Oglądaj od początku → ${detailStartVirtualMs}ms")
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
                                        controller.player?.pause()
                                        tuneLive(liveUrl)
                                        isPaused = false
                                        openPlayerButtons()
                                        Log.i(TAG, "DETAIL: Oglądaj (live $detailChannelName) → PLAYER_UI")
                                    }
                                    liveUrl.isNotBlank() -> {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Ten program już się skończył — kanał nie ma catchup",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    else -> {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Demo: brak streamu tego kanału",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
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
                        }
                    )
                }
                // Logo kanału w lewym górnym rogu (Figma: 208x208 @ 128,24).
                // Kanały z logoUrl → obrazek; bez (np. DEMO TV) → badge zastępczy.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = sx(128), top = sy(24))
                        .size(sx(208), sy(208))
                        .zIndex(15f)
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
