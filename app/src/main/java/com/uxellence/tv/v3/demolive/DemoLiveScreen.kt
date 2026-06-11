package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val TAG = "DemoLive"
private const val SEEK_STEP_MS = 10_000L
private const val EPG_TIMEOUT_MS = 12_000L
private const val CONTROLS_TIMEOUT_MS = 10_000L
private const val SEEK_AUTOCANCEL_MS = 5_000L

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
 * DEMO LIVE SCREEN — symulacja kanału live z ramówką (pełny flow):
 *
 *   EPG (start; rail bloków ramówki jak w EpgDayScreen)
 *     | BACK / timeout 12s
 *     v
 *   FULLSCREEN --OK--> CONTROLS (pauza / zacznij od początku / nagraj)
 *     | LEFT/RIGHT             | BACK / timeout 10s (gdy nie spauzowane)
 *     v
 *   SEEK_OVERLAY (filmstrip + segmentowany pasek ramówki + "Wróć do live")
 *
 * Wejście = 20 min po starcie anteny: jesteśmy w połowie bloku 2, a cały blok 1
 * (poprzedni materiał) jest w DVR — można się do niego przewinąć.
 * Kluczowy case: ramówka NIE pokrywa się z materiałami — patrz DemoChannelSchedule.
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

    // Warstwa EPG: lista bloków + fokus
    var epgBlocks by remember { mutableStateOf<List<DemoChannelSchedule.EpgBlock>>(emptyList()) }
    var epgFocusIndex by remember { mutableIntStateOf(0) }
    var epgInteractionAt by remember { mutableLongStateOf(0L) }

    // Warstwa CONTROLS
    var controlsFocusIndex by remember { mutableIntStateOf(0) }
    var controlsInteractionAt by remember { mutableLongStateOf(0L) }

    // Seek state
    var seekVirtualMs by remember { mutableLongStateOf(0L) }
    var lastSeekActionTime by remember { mutableLongStateOf(0L) }
    var returnToLiveFocused by remember { mutableStateOf(false) }
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

    fun updateFilmstrip() {
        filmstripFrames = filmstrip.framesAround(
            centerVirtualMs = seekVirtualMs,
            liveEdgeVirtualMs = controller.virtualNow(),
            stepMs = SEEK_STEP_MS,
            sideCount = 3
        )
    }

    fun openEpg() {
        val now = controller.currentVirtualPositionMs()
        epgBlocks = DemoChannelSchedule.blocksAround(now, before = 2, after = 3)
        epgFocusIndex = epgBlocks.indexOfFirst { now in it.startVirtualMs until it.endVirtualMs }
            .coerceAtLeast(0)
        epgInteractionAt = System.currentTimeMillis()
        layer = DemoLayer.EPG
    }

    // ============ AKCJE (wywoływane przez DemoLiveKeyController) ============
    val actions = remember {
        DemoLiveActions(
            showEpg = { openEpg() },
            epgMove = { dir ->
                epgFocusIndex = (epgFocusIndex + dir).coerceIn(0, (epgBlocks.size - 1).coerceAtLeast(0))
                epgInteractionAt = System.currentTimeMillis()
            },
            epgSelect = {
                val block = epgBlocks.getOrNull(epgFocusIndex)
                if (block != null && block.startVirtualMs <= controller.virtualNow()) {
                    // Odtwarzaj od początku bloku (timeshift do ramówki); clamp w kontrolerze
                    controller.seekToVirtual(block.startVirtualMs)
                    isPaused = false
                    layer = DemoLayer.FULLSCREEN
                    Log.i(TAG, "EPG select: '${block.title}' → ${block.startVirtualMs}ms")
                } else {
                    epgInteractionAt = System.currentTimeMillis()  // blok przyszły — ignoruj
                }
            },
            showControls = {
                controlsFocusIndex = 0
                controlsInteractionAt = System.currentTimeMillis()
                layer = DemoLayer.CONTROLS
            },
            controlsMove = { dir ->
                controlsFocusIndex = (controlsFocusIndex + dir).coerceIn(0, 2)
                controlsInteractionAt = System.currentTimeMillis()
            },
            controlsSelect = {
                controlsInteractionAt = System.currentTimeMillis()
                when (controlsFocusIndex) {
                    0 -> {  // Pauza / Wznów
                        val p = controller.player
                        if (p != null) {
                            if (p.isPlaying) {
                                p.pause(); isPaused = true
                            } else {
                                p.play(); isPaused = false
                            }
                            Log.i(TAG, "Controls: pauza → isPaused=$isPaused")
                        }
                    }
                    1 -> {  // Zacznij od początku bieżącego bloku ramówki
                        val block = DemoChannelSchedule.epgBlockAt(controller.currentVirtualPositionMs())
                        controller.seekToVirtual(block.startVirtualMs)
                        isPaused = false
                        layer = DemoLayer.FULLSCREEN
                        Log.i(TAG, "Controls: zacznij od początku → ${block.startVirtualMs}ms")
                    }
                    2 -> { /* Nagraj — atrapa */ }
                }
            },
            goFullscreen = { layer = DemoLayer.FULLSCREEN },
            exit = { onBackPressed() },
            seekStep = { direction ->
                if (layer != DemoLayer.SEEK_OVERLAY) {
                    controller.player?.pause()
                    seekVirtualMs = controller.currentVirtualPositionMs()
                    returnToLiveFocused = false
                    layer = DemoLayer.SEEK_OVERLAY
                }
                val step = getSeekStep() * direction
                seekVirtualMs = (seekVirtualMs + step).coerceIn(0L, controller.virtualNow())
                lastSeekActionTime = System.currentTimeMillis()
                updateFilmstrip()
                Log.i(TAG, "SEEK ${if (direction > 0) "RIGHT" else "LEFT"} → ${seekVirtualMs}ms / edge=${controller.virtualNow()}ms")
            },
            seekConfirm = {
                controller.seekToVirtual(seekVirtualMs)
                isPaused = false
                rapidPressCount = 0
                layer = DemoLayer.FULLSCREEN
            },
            seekCancel = {
                controller.player?.play()
                isPaused = false
                rapidPressCount = 0
                layer = DemoLayer.FULLSCREEN
                Log.i(TAG, "SEEK cancel")
            },
            returnToLive = {
                controller.seekToLiveEdge()
                isPaused = false
                rapidPressCount = 0
                returnToLiveFocused = false
                layer = DemoLayer.FULLSCREEN
            },
            isReturnToLiveFocused = { returnToLiveFocused },
            seekFocusChange = { toButton ->
                returnToLiveFocused = toButton
                lastSeekActionTime = System.currentTimeMillis()
            }
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

    // Auto-hide kontrolek po 10 s (chyba że spauzowane — wtedy zostają)
    LaunchedEffect(layer, controlsInteractionAt, isPaused) {
        if (layer == DemoLayer.CONTROLS && !isPaused) {
            delay(CONTROLS_TIMEOUT_MS)
            layer = DemoLayer.FULLSCREEN
        }
    }

    // Auto-cancel seek po 5 s bezczynności
    LaunchedEffect(lastSeekActionTime) {
        if (lastSeekActionTime == 0L) return@LaunchedEffect
        delay(SEEK_AUTOCANCEL_MS)
        if (layer == DemoLayer.SEEK_OVERLAY) {
            controller.player?.play()
            isPaused = false
            layer = DemoLayer.FULLSCREEN
            Log.i(TAG, "SEEK auto-cancel po ${SEEK_AUTOCANCEL_MS}ms")
        }
    }

    // ============ UI ============
    // Wspólny handler klawiszy — używany przez Compose root Box ORAZ przez
    // dispatchKeyEvent DemoVideoView (fokus okna potrafi wylądować na AndroidView)
    val keyHandler = rememberUpdatedState<(Int) -> Boolean> { keyCode ->
        if (!isReady) {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                onBackPressed(); true
            } else false
        } else {
            val handled = DemoLiveKeyController.handleKey(keyCode, layer, actions)
            Log.i(TAG, "key=$keyCode layer(after)=$layer handled=$handled")
            handled
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        rootFocus.requestFocus()
    }
    // Zmiany warstw potrafią zgubić fokus okna — przywracaj na root (wzorzec Issue #4)
    LaunchedEffect(layer) {
        delay(50)
        rootFocus.requestFocus()
    }

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
        // Wideo zawsze fullscreen pod warstwami
        AndroidView(
            factory = { ctx -> DemoVideoView(ctx) { keyCode -> keyHandler.value(keyCode) } },
            update = { view -> view.attach(playerRef) },
            modifier = Modifier.fillMaxSize()
        )

        // Warstwa EPG (start; jak EpgDayScreen pod zakładką Telewizja)
        DemoEpgLayer(
            isVisible = layer == DemoLayer.EPG && isReady,
            blocks = epgBlocks,
            focusedIndex = epgFocusIndex,
            currentVirtualMs = currentVirtualMs,
            dvrStartVirtualMs = 0L,
            antennaStartWallMs = controller.antennaStartWallMs,
            thumbnailFor = { block ->
                filmstrip.framesAround(
                    centerVirtualMs = block.startVirtualMs,
                    liveEdgeVirtualMs = liveEdgeMs,
                    stepMs = SEEK_STEP_MS,
                    sideCount = 0
                ).firstOrNull()?.second
            },
            sx = sx,
            sy = sy
        )

        // Warstwa kontrolek playera (OK z pełnego ekranu)
        DemoControlsLayer(
            isVisible = layer == DemoLayer.CONTROLS,
            block = DemoChannelSchedule.epgBlockAt(currentVirtualMs),
            currentVirtualMs = currentVirtualMs,
            antennaStartWallMs = controller.antennaStartWallMs,
            isPaused = isPaused,
            focusedIndex = controlsFocusIndex,
            sx = sx,
            sy = sy
        )

        // Overlay przewijania
        DemoSeekOverlay(
            isVisible = layer == DemoLayer.SEEK_OVERLAY,
            seekVirtualMs = seekVirtualMs,
            liveEdgeVirtualMs = liveEdgeMs.coerceAtLeast(1L),
            frames = filmstripFrames,
            boundaries = DemoChannelSchedule.blockBoundariesIn(0L, liveEdgeMs),
            blockTitle = DemoChannelSchedule.epgBlockAt(seekVirtualMs).title,
            antennaStartWallMs = controller.antennaStartWallMs,
            isReturnToLiveFocused = returnToLiveFocused,
            sx = sx,
            sy = sy
        )

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
