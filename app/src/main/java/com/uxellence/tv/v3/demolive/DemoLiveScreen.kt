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
        filmstripFrames = filmstrip.framesAround(
            centerVirtualMs = centerMs,
            liveEdgeVirtualMs = controller.virtualNow(),
            stepMs = SEEK_STEP_MS,
            sideCount = 3,
            dvrStartVirtualMs = controller.dvrStartMs()
        )
    }

    fun openPlayerButtons() {
        playerZone = PlayerZone.BUTTONS
        playerButtonsFocus = 0
        playerInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.PLAYER_UI
    }

    fun openStrip(initialCursor: Long) {
        playerZone = PlayerZone.STRIP
        scrubCursorMs = initialCursor
        updateFilmstrip(initialCursor)
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
                iconUrl = filmstrip.thumbUriFor(b.startVirtualMs, edge, context.cacheDir)
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
        epgRows = listOf(buildDemoRow()) + realChannelRows
        epgChannelIndex = 0
        epgProgramIndex.clear()
        epgRows.forEachIndexed { i, row -> epgProgramIndex[i] = row.currentProgramIndex }
        epgFocusedTime = java.time.Instant.now()
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
                    if (epgChannelIndex == 0) {
                        // DEMO TV: program AKTUALNIE odtwarzany → kontynuuj bez cofania;
                        // miniony → timeshift do początku. Fokus ląduje na "Zatrzymaj".
                        val targetStart = program.startUtc.toEpochMilli() - controller.antennaStartWallMs
                        val targetEnd = program.endUtc.toEpochMilli() - controller.antennaStartWallMs
                        val playingNow = controller.currentVirtualPositionMs() in targetStart until targetEnd
                        if (playingNow || targetStart <= controller.virtualNow()) {
                            if (!playingNow) {
                                controller.seekToVirtual(targetStart)
                                isPaused = false
                            }
                            openPlayerButtons()
                            Log.i(TAG, "EPG select: '${program.title}' playingNow=$playingNow → PLAYER_UI")
                        } else {
                            epgInteractionAt = System.currentTimeMillis()  // program przyszły
                        }
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Demo: odtwarzanie działa tylko na kanale DEMO TV",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        epgInteractionAt = System.currentTimeMillis()
                    }
                }
            },
            playerMove = { dir ->
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.BUTTONS -> {
                        playerButtonsFocus = (playerButtonsFocus + dir).coerceIn(0, 4)
                    }
                    PlayerZone.STRIP -> {
                        scrubCursorMs = (scrubCursorMs + getSeekStep() * dir)
                            .coerceIn(controller.dvrStartMs(), controller.virtualNow())
                        updateFilmstrip(scrubCursorMs)
                    }
                    PlayerZone.DESCRIPTION -> { /* brak nawigacji poziomej */ }
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
                    PlayerZone.DESCRIPTION -> { /* nic */ }
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
                    PlayerZone.DESCRIPTION -> {
                        playerZone = PlayerZone.BUTTONS   // wideo wraca z PIP na pełny ekran
                    }
                    PlayerZone.STRIP -> { /* nic */ }
                }
            },
            playerDown = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.BUTTONS -> {
                        playerZone = PlayerZone.DESCRIPTION   // opis, wideo do PIP
                    }
                    PlayerZone.STRIP -> {
                        playerZone = PlayerZone.BUTTONS
                        playerButtonsFocus = 0
                    }
                    PlayerZone.DESCRIPTION -> { /* nic */ }
                }
            },
            playerBack = {
                playerInteractionAt = System.currentTimeMillis()
                when (playerZone) {
                    PlayerZone.STRIP, PlayerZone.DESCRIPTION -> {
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
    // Zmiany warstw potrafią zgubić fokus okna — przywracaj na root (wzorzec Issue #4)
    LaunchedEffect(layer, playerZone) {
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

    val isDescription = layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DESCRIPTION

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
        if (!isDescription) {
            // Wideo fullscreen pod warstwami
            videoLayer(Modifier.fillMaxSize())
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

        // Zunifikowane UI playera (BUTTONS / STRIP / DESCRIPTION)
        DemoPlayerUi(
            isVisible = layer == DemoLayer.PLAYER_UI,
            zone = playerZone,
            block = DemoChannelSchedule.epgBlockAt(currentVirtualMs),
            currentVirtualMs = currentVirtualMs,
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

        if (isDescription) {
            // PIP: ten sam widok wideo w prawym dolnym rogu, NAD warstwą opisu
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
