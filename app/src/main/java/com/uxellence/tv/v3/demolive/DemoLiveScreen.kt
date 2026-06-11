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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

private const val TAG = "DemoLive"
private const val SEEK_STEP_MS = 10_000L
private const val LIVE_INFO_TIMEOUT_MS = 10_000L
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

    /** Letterbox: dopasuj wysokość TextureView do proporcji wideo (środek, czarne pasy). */
    private fun applyAspect(videoSize: com.google.android.exoplayer2.video.VideoSize) {
        if (videoSize.height == 0 || videoSize.width == 0 || width == 0) return
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
 *   LIVE_INFO (start) --OK--> DETAIL (z PIP) --BACK--> LIVE_INFO
 *       |  BACK / timeout 10s
 *       v
 *   FULLSCREEN --LEFT/RIGHT--> SEEK_OVERLAY (filmstrip + segmentowany pasek ramówki)
 *       |  BACK = wyjście do HOME            |  OK = potwierdź / "Wróć do live"
 *
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

    var layer by remember { mutableStateOf(DemoLayer.LIVE_INFO) }
    var isReady by remember { mutableStateOf(false) }
    // Player jako stan Compose — inaczej update AndroidView nie wykona się ponownie
    // po utworzeniu playera (lambda obserwuje wyłącznie snapshot state)
    var playerRef by remember { mutableStateOf<com.google.android.exoplayer2.ExoPlayer?>(null) }
    var downloadLabel by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var currentVirtualMs by remember { mutableLongStateOf(0L) }
    var liveEdgeMs by remember { mutableLongStateOf(0L) }

    // Seek state
    var seekVirtualMs by remember { mutableLongStateOf(0L) }
    var lastSeekActionTime by remember { mutableLongStateOf(0L) }
    var returnToLiveFocused by remember { mutableStateOf(false) }
    var filmstripFrames by remember { mutableStateOf<List<Pair<Long, Bitmap?>>>(emptyList()) }

    // Detail state
    var detailFocusIndex by remember { mutableIntStateOf(0) }

    // Auto-hide warstwy LIVE_INFO
    var liveInfoShownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

    // ============ AKCJE (wywoływane przez DemoLiveKeyController) ============
    val actions = remember {
        DemoLiveActions(
            showLiveInfo = {
                layer = DemoLayer.LIVE_INFO
                liveInfoShownAt = System.currentTimeMillis()
            },
            showDetail = {
                detailFocusIndex = 0
                layer = DemoLayer.DETAIL
            },
            goFullscreen = { layer = DemoLayer.FULLSCREEN },
            exit = { onBackPressed() },
            seekStep = { direction ->
                if (layer != DemoLayer.SEEK_OVERLAY) {
                    // Wejście w tryb przewijania: pauza, kursor od bieżącej pozycji
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
                rapidPressCount = 0
                layer = DemoLayer.FULLSCREEN
            },
            seekCancel = {
                controller.player?.play()
                rapidPressCount = 0
                layer = DemoLayer.FULLSCREEN
                Log.i(TAG, "SEEK cancel")
            },
            returnToLive = {
                controller.seekToLiveEdge()
                rapidPressCount = 0
                returnToLiveFocused = false
                layer = DemoLayer.FULLSCREEN
            },
            isReturnToLiveFocused = { returnToLiveFocused },
            seekFocusChange = { toButton ->
                returnToLiveFocused = toButton
                lastSeekActionTime = System.currentTimeMillis()
            },
            detailFocusedIndex = { detailFocusIndex },
            detailFocusChange = { detailFocusIndex = it },
            startOver = {
                val block = DemoChannelSchedule.epgBlockAt(controller.currentVirtualPositionMs())
                controller.seekToVirtual(block.startVirtualMs)
                layer = DemoLayer.FULLSCREEN
                Log.i(TAG, "Zacznij od początku → blockStart=${block.startVirtualMs}ms")
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
            liveInfoShownAt = System.currentTimeMillis()
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

    // Auto-hide LIVE_INFO po 10 s
    LaunchedEffect(layer, liveInfoShownAt) {
        if (layer == DemoLayer.LIVE_INFO && isReady) {
            delay(LIVE_INFO_TIMEOUT_MS)
            layer = DemoLayer.FULLSCREEN
        }
    }

    // Auto-cancel seek po 5 s bezczynności
    LaunchedEffect(lastSeekActionTime) {
        if (lastSeekActionTime == 0L) return@LaunchedEffect
        delay(SEEK_AUTOCANCEL_MS)
        if (layer == DemoLayer.SEEK_OVERLAY) {
            controller.player?.play()
            layer = DemoLayer.FULLSCREEN
            Log.i(TAG, "SEEK auto-cancel po ${SEEK_AUTOCANCEL_MS}ms")
        }
    }

    // ============ UI ============
    // Wspólny handler klawiszy — używany przez Compose root Box ORAZ przez
    // dispatchKeyEvent PlayerView (sprawdzony wzorzec z VodPlayerScreen:356-363:
    // fokus okna potrafi wylądować na AndroidView zamiast na focusable() Boxie).
    val keyHandler = rememberUpdatedState<(Int) -> Boolean> { keyCode ->
        if (!isReady) {
            // Podczas pobierania tylko BACK działa
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                onBackPressed(); true
            } else false
        } else {
            val handled = DemoLiveKeyController.handleKey(keyCode, layer, actions)
            Log.i(TAG, "key=$keyCode layer(before)=$layer handled=$handled")
            handled
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        rootFocus.requestFocus()
    }
    // Re-parenting DemoVideoView (movableContentOf przy zmianie warstwy) potrafi
    // zgubić fokus okna — przywracaj na root po każdej zmianie warstwy (wzorzec Issue #4)
    LaunchedEffect(layer) {
        delay(50)
        rootFocus.requestFocus()
    }

    // Jeden widok wideo renderowany z dwóch pozycji w drzewie (movableContentOf —
    // patrz CLAUDE.md lesson #10): fullscreen POD overlayami albo PIP NAD detalem.
    // TextureView zamiast PlayerView/SurfaceView — SurfaceView w Compose daje
    // czarny ekran (z-order; patrz CLAUDE.md "Trailer Auto-Play System").
    val videoLayer = remember {
        movableContentOf { modifier: Modifier ->
            AndroidView(
                factory = { ctx -> DemoVideoView(ctx) { keyCode -> keyHandler.value(keyCode) } },
                update = { view -> view.attach(playerRef) },
                modifier = modifier
            )
        }
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
        if (layer == DemoLayer.DETAIL) {
            // Warstwa detalu na spodzie, PIP (ten sam PlayerView) NA WIERZCHU w rogu
            DemoDetailLayer(
                isVisible = true,
                block = DemoChannelSchedule.epgBlockAt(currentVirtualMs),
                currentVirtualMs = currentVirtualMs,
                antennaStartWallMs = controller.antennaStartWallMs,
                thumbnail = filmstrip.framesAround(
                    centerVirtualMs = DemoChannelSchedule.epgBlockAt(currentVirtualMs).startVirtualMs,
                    liveEdgeVirtualMs = liveEdgeMs,
                    stepMs = SEEK_STEP_MS,
                    sideCount = 0
                ).firstOrNull()?.second,
                focusedButtonIndex = detailFocusIndex,
                sx = sx,
                sy = sy
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = sx(60), bottom = sy(140))
                    .width(sx(480))
                    .height(sy(270))
                    .zIndex(5f)
                    .border(2.dp, Color(0x66EEEEEE), RoundedCornerShape(sx(8)))
            ) {
                videoLayer(Modifier.fillMaxSize())
            }
        } else {
            // Fullscreen video pod overlayami
            videoLayer(Modifier.fillMaxSize())

            DemoLiveInfoBar(
                isVisible = layer == DemoLayer.LIVE_INFO && isReady,
                block = DemoChannelSchedule.epgBlockAt(currentVirtualMs),
                currentVirtualMs = currentVirtualMs,
                antennaStartWallMs = controller.antennaStartWallMs,
                sx = sx,
                sy = sy
            )

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
                        style = TextStyle(fontSize = 24.sp, color = Color(0xFFFF6B6B))
                    )
                } else {
                    Text(
                        "Pobieranie $downloadLabel… ${downloadProgress}%",
                        style = TextStyle(fontSize = 24.sp, color = Color.White)
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
