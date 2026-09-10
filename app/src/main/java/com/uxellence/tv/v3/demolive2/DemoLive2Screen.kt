package com.uxellence.tv.v3.demolive2

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.demolive.BarkerSchedule
import com.uxellence.tv.v3.demolive.DemoChannelPlayerController
import com.uxellence.tv.v3.demolive.RecordedChannelLoader
import kotlinx.coroutines.delay
import java.io.File

/**
 * DEMO: KANAŁ LIVE 2 — warstwa playera odwzorowana z LAUNCHERA PLAY (Play Now box).
 *
 * Osobny ekran obok "Demo: Kanał live + ramówka" — tamten zostaje bez zmian,
 * tu jest wyłącznie NOWY wygląd nakładki (pas kontrolek, pas przewijania, mini-EPG)
 * zmierzony 1:1 na PLAY BOX TV 4B. Silnik anteny jest RE-UŻYTY z demolive
 * (BarkerSchedule + DemoChannelPlayerController + RecordedChannelLoader) —
 * nic tam nie zmieniamy.
 *
 * Materiał: kanały z nagrań anteny (paczki manifest.json + program_NN.mp4),
 * domyślnie TVP1 Retro. Brak paczki → plansza z informacją, jak ją wgrać.
 *
 * Nawigacja (jak na boxie):
 *   OK               → pokaż nakładkę, fokus na pasie kontrolek
 *   LEWO/PRAWO       → pas kontrolek: wybór ikony
 *   GÓRA             → pas przewijania (playhead na mint), LEWO/PRAWO przewija
 *   DÓŁ              → mini-EPG (GÓRA/DÓŁ kanał, LEWO/PRAWO program, OK dostrój)
 *   BACK             → schowaj nakładkę; przy schowanej — wyjście z ekranu
 */

private const val AUTO_HIDE_MS = 6_000L
private const val SCRUB_STEP_MS = 30_000L
private const val SCRUB_COMMIT_MS = 800L

@Composable
fun DemoLive2Screen(
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
) {
    val context = LocalContext.current

    // ── kanały z nagrań (TVP1 Retro i ewentualne inne paczki) ──
    val recorded = remember { RecordedChannelLoader.loadAll(context) }
    if (recorded.isEmpty()) {
        PnMissingMaterial(onBackPressed, sy)
        return
    }
    // Domyślnie kanał z TVP1; gdy brak — pierwszy dostępny
    val initialIdx = remember {
        recorded.indexOfFirst { it.name.contains("TVP1", ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
    }

    var channelIdx by remember { mutableStateOf(initialIdx) }
    val channel = recorded[channelIdx]

    // ── kontroler anteny bieżącego kanału (pełna doba DVR — nagranie ma godziny) ──
    val controller = remember(channel.id) {
        DemoChannelPlayerController(
            context = context,
            schedule = BarkerSchedule(channel.items),
            dvrWindowMs = 24 * 3_600_000L,
            antennaStartWallMs = channel.recordedAtWallMs
        )
    }
    var prepared by remember(channel.id) { mutableStateOf(false) }
    var prepareError by remember(channel.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(channel.id) {
        try {
            val files = channel.items.map { File(it.url.removePrefix("file://")) }
            controller.preparePlayer(files)
            prepared = true
        } catch (e: Exception) {
            prepareError = e.message ?: e.toString()
        }
    }
    DisposableEffect(channel.id) {
        onDispose { controller.release() }
    }

    // ── stan nakładki ──
    var overlayVisible by remember { mutableStateOf(true) }
    var zone by remember { mutableStateOf(PnZone.CONTROLS) }
    var controlIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var lastInputAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Oś: pozycja odtwarzania + live edge (tickowane), kursor przewijania
    var positionMs by remember { mutableLongStateOf(0L) }
    var liveEdgeMs by remember { mutableLongStateOf(0L) }
    var cursorMs by remember { mutableStateOf<Long?>(null) }
    var cursorTouchedAt by remember { mutableLongStateOf(0L) }

    // Program pokazywany na karcie: po przewinięciu — blok pod kursorem
    val schedule = controller.schedule
    val shownBase = cursorMs ?: positionMs
    val shownBlock = remember(shownBase / 1000L, prepared) { schedule.epgBlockAt(shownBase) }
    val nextBlock = remember(shownBase / 1000L, prepared) {
        schedule.epgBlockAt(shownBlock.endVirtualMs + 1)
    }

    // mini-EPG
    var epgRowIndex by remember { mutableStateOf(0) }
    var epgProgramIndex by remember { mutableStateOf(0) }
    val miniRows = remember(recorded, channelIdx, positionMs / 60_000L) {
        recorded.map { rec ->
            val sch = BarkerSchedule(rec.items)
            val virt = (System.currentTimeMillis() - rec.recordedAtWallMs).coerceAtLeast(0L)
            val blocks = sch.blocksAround(virt, before = 0, after = 3)
            PnChannelRow(
                name = rec.name,
                number = rec.number,
                logoUrl = pnLogoFor(rec.name),
                programs = blocks.map { it.toPnProgram() },
                liveIndex = 0
            )
        }
    }

    // ── tick pozycji/zegara ──
    LaunchedEffect(prepared) {
        while (true) {
            liveEdgeMs = controller.virtualNow()
            if (prepared) positionMs = controller.currentVirtualPositionMs()
            delay(200)
        }
    }

    // ── auto-hide nakładki ──
    LaunchedEffect(lastInputAt, overlayVisible, zone) {
        if (!overlayVisible) return@LaunchedEffect
        delay(AUTO_HIDE_MS)
        if (System.currentTimeMillis() - lastInputAt >= AUTO_HIDE_MS) {
            overlayVisible = false
            cursorMs = null
        }
    }

    // ── zatwierdzenie przewijania po chwili bezczynności ──
    LaunchedEffect(cursorTouchedAt) {
        if (cursorMs == null) return@LaunchedEffect
        delay(SCRUB_COMMIT_MS)
        if (System.currentTimeMillis() - cursorTouchedAt >= SCRUB_COMMIT_MS) {
            cursorMs?.let { controller.seekToVirtual(it) }
            cursorMs = null
            isPaused = false
        }
    }

    fun touch() {
        lastInputAt = System.currentTimeMillis()
    }

    fun show(target: PnZone) {
        overlayVisible = true
        zone = target
        touch()
    }

    fun moveCursor(deltaMs: Long) {
        val base = cursorMs ?: positionMs
        val target = (base + deltaMs)
            .coerceIn(controller.dvrStartMs(), controller.virtualNow())
        cursorMs = target
        cursorTouchedAt = System.currentTimeMillis()
        touch()
    }

    fun activateControl() {
        when (PnControl.values()[controlIndex]) {
            PnControl.LIVE -> {
                controller.seekToLiveEdge()
                cursorMs = null
                isPaused = false
            }
            PnControl.RESTART -> {
                controller.seekToVirtual(shownBlock.startVirtualMs)
                cursorMs = null
                isPaused = false
            }
            PnControl.PAUSE -> {
                val p = controller.player
                if (p != null) {
                    if (p.isPlaying) { p.pause(); isPaused = true } else { p.play(); isPaused = false }
                }
            }
            PnControl.GUIDE -> {
                epgRowIndex = channelIdx
                epgProgramIndex = 0
                zone = PnZone.MINI_EPG
            }
            // REC / INFO / SETTINGS — poza zakresem tej wersji (tylko warstwa playera)
            PnControl.REC, PnControl.INFO, PnControl.SETTINGS -> Unit
        }
        touch()
    }

    fun handleKey(keyCode: Int): Boolean {
        touch()
        if (!overlayVisible) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { show(PnZone.CONTROLS); return true }
                KeyEvent.KEYCODE_DPAD_UP -> { show(PnZone.SCRUB); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    epgRowIndex = channelIdx; epgProgramIndex = 0
                    show(PnZone.MINI_EPG); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> { show(PnZone.SCRUB); moveCursor(-SCRUB_STEP_MS); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { show(PnZone.SCRUB); moveCursor(SCRUB_STEP_MS); return true }
                else -> return false
            }
        }
        return when (zone) {
            PnZone.CONTROLS -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    controlIndex = (controlIndex - 1).coerceAtLeast(0); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    controlIndex = (controlIndex + 1).coerceAtMost(PnControl.values().lastIndex); true
                }
                KeyEvent.KEYCODE_DPAD_UP -> { zone = PnZone.SCRUB; true }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    epgRowIndex = channelIdx; epgProgramIndex = 0
                    zone = PnZone.MINI_EPG; true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { activateControl(); true }
                else -> false
            }
            PnZone.SCRUB -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { moveCursor(-SCRUB_STEP_MS); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { moveCursor(SCRUB_STEP_MS); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { zone = PnZone.CONTROLS; true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    cursorMs?.let { controller.seekToVirtual(it) }
                    cursorMs = null
                    isPaused = false
                    zone = PnZone.CONTROLS
                    true
                }
                else -> false
            }
            PnZone.MINI_EPG -> when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (epgRowIndex > 0) { epgRowIndex--; epgProgramIndex = 0 }
                    else zone = PnZone.CONTROLS
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (epgRowIndex < miniRows.lastIndex) { epgRowIndex++; epgProgramIndex = 0 }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    epgProgramIndex = (epgProgramIndex - 1).coerceAtLeast(0); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val last = (miniRows.getOrNull(epgRowIndex)?.programs?.lastIndex ?: 0)
                    epgProgramIndex = (epgProgramIndex + 1).coerceAtMost(last); true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (epgRowIndex != channelIdx) channelIdx = epgRowIndex
                    zone = PnZone.CONTROLS
                    true
                }
                else -> false
            }
        }
    }

    // BACK: nakładka widoczna → schowaj; schowana → wyjście (wzorzec z demolive)
    BackHandler(enabled = true) {
        if (overlayVisible) {
            overlayVisible = false
            cursorMs = null
        } else onBackPressed()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ── wideo (TextureView — nie SurfaceView, z-order w Compose) ──
        val player = controller.player
        AndroidView(
            factory = { ctx -> PnVideoView(ctx) { code -> handleKey(code) } },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.attach(player) }
        )

        if (prepareError != null) {
            Text(
                text = "Nie udało się uruchomić kanału: $prepareError",
                color = PN_TEXT,
                fontSize = pnSp(28, sy),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        PlayNowOverlay(
            visible = overlayVisible,
            zone = zone,
            controlIndex = controlIndex,
            isPaused = isPaused,
            channelName = channel.name,
            channelNumber = channel.number,
            channelLogoUrl = pnLogoFor(channel.name),
            shownProgram = shownBlock.toPnProgram(),
            nextProgram = nextBlock.toPnProgram(),
            shownIsLive = positionMs in shownBlock.startVirtualMs until shownBlock.endVirtualMs,
            positionMs = positionMs,
            cursorMs = cursorMs,
            antennaStartWallMs = controller.antennaStartWallMs,
            liveEdgeMs = liveEdgeMs,
            miniEpgRows = miniRows,
            miniEpgRowIndex = epgRowIndex,
            miniEpgProgramIndex = epgProgramIndex,
            sx = sx, sy = sy
        )
    }
}

/** EpgBlock silnika anteny → model karty/mini-EPG. */
private fun BarkerSchedule.EpgBlock.toPnProgram(): PnProgram {
    val minutes = ((endVirtualMs - startVirtualMs) / 60_000L).toInt()
    return PnProgram(
        title = title,
        meta = listOf(year, genre, "$minutes min.", age),
        coverUrl = coverUrl,
        startMs = startVirtualMs,
        endMs = endVirtualMs
    )
}

/**
 * Logo kanału z listy realnych kanałów (nagranie "TVP1 Retro" → logo "TVP 1").
 * Brak dopasowania → null, wtedy nakładka pokazuje nazwę kanału tekstem.
 */
private fun pnLogoFor(recordedName: String): String? {
    val probe = when {
        recordedName.contains("TVP1", ignoreCase = true) -> "TVP 1"
        recordedName.contains("TVP2", ignoreCase = true) -> "TVP 2"
        else -> recordedName
    }
    return runCatching { ChannelManager.getChannelByName(probe)?.logoUrl }.getOrNull()
}

/** Plansza, gdy na urządzeniu nie ma paczki z nagraniem. */
@Composable
private fun PnMissingMaterial(onBackPressed: () -> Unit, sy: (Int) -> Dp) {
    BackHandler(enabled = true) { onBackPressed() }
    Box(
        modifier = Modifier.fillMaxSize().background(PN_PURPLE),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Brak paczki z nagraniem anteny.\n\n" +
                "Wgraj katalog z manifest.json + program_NN.mp4 do\n" +
                "Android/data/<pkg>/files/ (np. tvp1rec/) i wróć tutaj.",
            color = PN_TEXT,
            fontSize = pnSp(30, sy),
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Widok wideo: TextureView w FrameLayout + letterbox + przekazywanie klawiszy
 * do handlera ekranu (fokus okna na TV potrafi wylądować na AndroidView).
 * Wzorzec zgodny z DemoVideoView w demolive — tu własna kopia, żeby ekran 2
 * nie zależał od prywatnych klas ekranu 1.
 */
private class PnVideoView(
    context: android.content.Context,
    private val onKey: (Int) -> Boolean,
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
            LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, android.view.Gravity.CENTER
            )
        )
        isFocusable = true
        isFocusableInTouchMode = false
    }

    fun attach(player: com.google.android.exoplayer2.ExoPlayer?) {
        if (attachedPlayer === player) return
        attachedPlayer?.removeListener(videoListener)
        attachedPlayer?.clearVideoTextureView(textureView)
        attachedPlayer = player
        if (player != null) {
            player.addListener(videoListener)
            player.setVideoTextureView(textureView)
            applyAspect(player.videoSize)
        }
    }

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
