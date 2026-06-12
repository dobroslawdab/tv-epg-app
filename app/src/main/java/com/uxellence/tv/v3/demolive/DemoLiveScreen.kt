package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
private const val STRIP_AUTOEXIT_MS = 5_000L

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

    fun attach(player: com.google.android.exoplayer2.ExoPlayer?) {
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(videoListener)
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
    var isPaused by remember { mutableStateOf(false) }
    // Dostrojony kanał: 0 = DEMO TV (gra barker), >0 = realny kanał — w demo nie ma
    // jego streamu, więc zamiast wideo plansza "Brak live"; UI playera działa tak samo
    var tunedChannelIndex by remember { mutableIntStateOf(0) }

    // Warstwa EPG: wiersz 0 = DEMO TV (sztuczna ramówka), 1..N = prawdziwe kanały z EPG
    var epgRows by remember { mutableStateOf<List<com.uxellence.tv.v3.epg.ChannelEpgRow>>(emptyList()) }
    var realChannelRows by remember { mutableStateOf<List<com.uxellence.tv.v3.epg.ChannelEpgRow>>(emptyList()) }
    var epgChannelIndex by remember { mutableIntStateOf(0) }
    val epgProgramIndex = remember { mutableStateMapOf<Int, Int>() }
    var epgInteractionAt by remember { mutableLongStateOf(0L) }
    // Czas fokusu siatki EPG (TIME SYNC) — start fokusowanego programu;
    // wszystkie wiersze przewijają się do programu emitowanego o tym czasie
    var epgFocusedTime by remember { mutableStateOf(java.time.Instant.now()) }

    // PLAYER_UI: strefa + fokus przycisków (0..4) + kursor taśmy
    var playerZone by remember { mutableStateOf(PlayerZone.BUTTONS) }
    var playerButtonsFocus by remember { mutableIntStateOf(0) }
    var scrubCursorMs by remember { mutableLongStateOf(0L) }
    var playerInteractionAt by remember { mutableLongStateOf(0L) }
    var filmstripFrames by remember { mutableStateOf<List<Pair<Long, Bitmap?>>>(emptyList()) }
    // Detal programu (strefa DETAIL): renderowany PRAWDZIWYM MovieDetailScreen
    // (tryb WIDEO — identyczny wygląd jak detale programów pod zakładką Wideo).
    // detailSlide = dane do ekranu; timing/isDemo sterują akcją "Oglądaj";
    // fromEpg steruje dokąd wraca BACK; detailStartVirtualMs = cel timeshiftu
    var detailSlide by remember { mutableStateOf<com.uxellence.tv.v3.VodSlideData?>(null) }
    var detailTiming by remember { mutableStateOf(BlockTiming.CURRENT) }
    var detailIsDemo by remember { mutableStateOf(true) }
    var detailFromEpg by remember { mutableStateOf(false) }
    var detailStartVirtualMs by remember { mutableLongStateOf(0L) }

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
        fromEpg: Boolean
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

    fun openEpg() {
        val demoRow = buildDemoRow()
        epgRows = listOf(demoRow) + realChannelRows
        epgChannelIndex = 0
        // TIME SYNC musi celować w program AKTUALNIE ODTWARZANY (pozycja playbacku,
        // nie zegar ścienny) — przy timeshifcie to różne programy; bez tego fokus
        // ląduje poza wycentrowanym kafelkiem i OK otwiera detal zamiast playera
        val focused = demoRow.programs.getOrNull(demoRow.currentProgramIndex)?.startUtc
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
                val program = row?.programs?.getOrNull(epgProgramIndex[epgChannelIndex] ?: -1)
                if (row != null && program != null) {
                    val isDemo = epgChannelIndex == 0
                    val targetStart = program.startUtc.toEpochMilli() - controller.antennaStartWallMs
                    val targetEnd = program.endUtc.toEpochMilli() - controller.antennaStartWallMs
                    // "Teraz na żywo": DEMO TV wg pozycji odtwarzania, realne kanały wg zegara
                    val nowRef = if (isDemo) controller.currentVirtualPositionMs() else controller.virtualNow()
                    val playingNow = nowRef in targetStart until targetEnd
                    when {
                        playingNow && epgChannelIndex == tunedChannelIndex -> {
                            // Drugie kliknięcie na dostrojonym kanale → UI playera
                            // (przewijanie i przyciski działają tak samo wszędzie)
                            openPlayerButtons()
                            Log.i(TAG, "EPG select: '${program.title}' (dostrojony) → PLAYER_UI")
                        }
                        playingNow -> {
                            // Pierwsze kliknięcie: dostrój kanał. DEMO TV gra barker;
                            // realny kanał nie ma streamu w demo → plansza "Brak live",
                            // warstwa EPG zostaje otwarta
                            tunedChannelIndex = epgChannelIndex
                            if (epgChannelIndex == 0) {
                                controller.player?.play()
                                isPaused = false
                            } else {
                                controller.player?.pause()
                            }
                            epgInteractionAt = System.currentTimeMillis()
                            Log.i(TAG, "EPG select: tune → ${row.channel.name} (kanał ${row.channelNumber})")
                        }
                        else -> {
                            // Program miniony/przyszły (dowolny kanał) → detal jak na Wideo
                            openDetail(
                                program = program,
                                channelLogoUrl = row.channel.logoUrl,
                                isDemo = isDemo,
                                fromEpg = true
                            )
                            Log.i(TAG, "EPG select: '${program.title}' (${row.channel.name}) → DETAIL (timing=$detailTiming)")
                        }
                    }
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
                        scrubCursorMs = (scrubCursorMs + getSeekStep() * dir)
                            .coerceIn(controller.dvrStartMs(), controller.virtualNow())
                        updateFilmstrip(scrubCursorMs)
                    }
                    PlayerZone.SNIPPET, PlayerZone.DETAIL -> { /* brak nawigacji poziomej */ }
                }
            },
            playerSelect = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.STRIP -> {
                        // OK na taśmie = skok do kursora, fokus wraca na "Zatrzymaj"
                        controller.seekToVirtual(scrubCursorMs)
                        isPaused = false
                        rapidPressCount = 0
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                        Log.i(TAG, "STRIP seek → ${scrubCursorMs}ms → BUTTONS")
                    }
                    PlayerZone.BUTTONS -> when (playerButtonsFocus) {
                        0 -> {  // Zatrzymaj / Wznów
                            val p = controller.player
                            if (p != null) {
                                if (p.isPlaying) {
                                    p.pause(); isPaused = true
                                } else {
                                    p.play(); isPaused = false
                                }
                                Log.i(TAG, "Player: zatrzymaj → isPaused=$isPaused")
                            }
                        }
                        1 -> {  // Wróć do live
                            controller.seekToLiveEdge()
                            isPaused = false
                            Log.i(TAG, "Player: wróć do live")
                        }
                        2 -> {  // Zacznij od początku bieżącego bloku ramówki
                            val block = DemoChannelSchedule.epgBlockAt(controller.currentVirtualPositionMs())
                            controller.seekToVirtual(block.startVirtualMs)
                            isPaused = false
                            Log.i(TAG, "Player: zacznij od początku → ${block.startVirtualMs}ms")
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
                        // Z przycisków na taśmę (kursor startuje z bieżącej pozycji)
                        playerZone = PlayerZone.STRIP
                        scrubCursorMs = controller.currentVirtualPositionMs()
                        updateFilmstrip(scrubCursorMs)
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
                    PlayerZone.STRIP, PlayerZone.SNIPPET -> {
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                    }
                    PlayerZone.BUTTONS -> openEpg()   // łańcuch: player UI → EPG → fullscreen → wyjście
                }
            },
            openStripWithStep = { direction ->
                if (layer != DemoLayer.PLAYER_UI || playerZone != PlayerZone.STRIP) {
                    openStrip(controller.currentVirtualPositionMs())
                }
                scrubCursorMs = (scrubCursorMs + getSeekStep() * direction)
                    .coerceIn(controller.dvrStartMs(), controller.virtualNow())
                updateFilmstrip(scrubCursorMs)
                playerInteractionAt = System.currentTimeMillis()
                Log.i(TAG, "STRIP ${if (direction > 0) "RIGHT" else "LEFT"} → ${scrubCursorMs}ms")
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
                val programs = try {
                    repo.getFullDayPrograms(channel.epgId ?: channel.id, now)
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

    // Auto-hide warstwy EPG po 12 s bez interakcji
    LaunchedEffect(layer, epgInteractionAt) {
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

    // Taśma: 5 s bezczynności → powrót na przyciski (bez seeka)
    LaunchedEffect(layer, playerZone, playerInteractionAt) {
        if (layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.STRIP) {
            delay(STRIP_AUTOEXIT_MS)
            playerZone = PlayerZone.BUTTONS
            playerButtonsFocus = 0
            Log.i(TAG, "STRIP auto-exit po ${STRIP_AUTOEXIT_MS}ms")
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
                factory = { ctx -> DemoVideoView(ctx) { keyCode -> keyHandler.value(keyCode) } },
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

        // Realny kanał: w demo nie ma jego streamu — plansza "Brak live" zamiast
        // wideo (pod warstwami EPG/playera, więc cały flow działa identycznie)
        if (tunedChannelIndex != 0 && !isDetail) {
            val tunedRow = epgRows.getOrNull(tunedChannelIndex)
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
            sx = sx,
            sy = sy
        )

        // Zunifikowane UI playera (BUTTONS / STRIP / SNIPPET); DETAIL renderuje
        // poniżej prawdziwy MovieDetailScreen (identyczny z zakładką Wideo).
        // W STRIP materiałem głównym (nagłówek + środkowy segment paska) jest
        // blok POD KURSOREM — przeskok na sąsiedni materiał przepina metadane
        val uiRefVirtualMs = if (tunedChannelIndex == 0) currentVirtualMs else liveEdgeMs
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
            isAtLiveEdge = tunedChannelIndex != 0 ||
                (!isPaused && (liveEdgeMs - currentVirtualMs) < 5_000L),
            liveEdgeVirtualMs = liveEdgeMs.coerceAtLeast(1L),
            dvrStartVirtualMs = controller.dvrStartMs(),
            scrubCursorMs = scrubCursorMs,
            antennaStartWallMs = controller.antennaStartWallMs,
            isPaused = isPaused,
            buttonsFocusIndex = if (playerZone == PlayerZone.BUTTONS) playerButtonsFocus else -1,
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
                                android.widget.Toast.makeText(
                                    context,
                                    "Demo: odtwarzanie działa tylko na kanale DEMO TV",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
            // PIP: ten sam widok wideo w prawym dolnym rogu, NAD warstwą detalu
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = sx(60), bottom = sy(60))
                    .width(sx(480))
                    .height(sy(270))
                    .zIndex(15f)
                    .border(2.dp, Color(0x66EEEEEE), RoundedCornerShape(sx(8)))
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
