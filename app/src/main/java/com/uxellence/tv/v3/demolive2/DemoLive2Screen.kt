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
import com.uxellence.tv.v3.demolive.DemoFilmstripProvider
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
 *   GÓRA             → pas przewijania (playhead na mint); LEWO/PRAWO przewija
 *                      i ODSŁANIA taśmę podglądu (nasz dodatek, Play Now jej nie ma)
 *   GÓRA z paska     → miniaturka/karta (3. poziom); LEWO/PRAWO chodzi po
 *                      ramówce kanału, OK rozwija detal (opis + Nagraj/Przypomnij)
 *   GÓRA z miniaturki → mini-EPG (lista leży NAD playerem)
 *   DÓŁ z kontrolek  → mini-EPG (to samo, z drugiego końca stosu)
 *                      W liście: GÓRA/DÓŁ kanały, LEWO/PRAWO programy,
 *                      a do playera wraca OK (dostrojenie) albo BACK.
 *   BACK             → schowaj nakładkę; przy schowanej — wyjście z ekranu
 */

private const val AUTO_HIDE_MS = 6_000L
private const val SCRUB_STEP_MS = 30_000L
private const val SCRUB_COMMIT_MS = 800L
private const val DVR_WINDOW_MS = 24 * 3_600_000L

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
            dvrWindowMs = DVR_WINDOW_MS,
            antennaStartWallMs = channel.recordedAtWallMs
        )
    }
    var prepared by remember(channel.id) { mutableStateOf(false) }
    var prepareError by remember(channel.id) { mutableStateOf<String?>(null) }

    // Miniaturki podglądu przewijania — RE-UŻYTY provider z demolive (trwały cache
    // klatek obok materiału: frames/<program>/f_<pos>.jpg, więc kolejne wejścia
    // mają taśmę od razu, bez ekstrakcji z mp4)
    val filmstrip = remember(channel.id) { DemoFilmstripProvider(controller.schedule) }

    LaunchedEffect(channel.id) {
        try {
            val files = channel.items.map { File(it.url.removePrefix("file://")) }
            controller.preparePlayer(files)
            prepared = true
            filmstrip.startExtraction(files.map { it.absolutePath })
        } catch (e: Exception) {
            prepareError = e.message ?: e.toString()
        }
    }
    DisposableEffect(channel.id) {
        onDispose {
            controller.release()
            filmstrip.release()
        }
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
    // Taśma podglądu wchodzi DOPIERO po pierwszym LEWO/PRAWO na pasku
    var scrubTapeVisible by remember { mutableStateOf(false) }
    // 3. poziom fokusa: miniaturka/karta. Offset liczony w BLOKACH ramówki od
    // programu granego (0 = grany), OK rozwija kartę w detal.
    var cardOffset by remember { mutableStateOf(0) }
    var detailOpen by remember { mutableStateOf(false) }
    var detailActionIndex by remember { mutableStateOf(0) }

    // Program pokazywany na karcie:
    //  - strefa CARD/detal → blok oddalony o cardOffset od granego,
    //  - inaczej → blok pod kursorem (albo pod pozycją odtwarzania).
    val schedule = controller.schedule
    val shownBase = cursorMs ?: positionMs
    val onCardLevel = zone == PnZone.CARD || detailOpen
    val shownBlock = remember(shownBase / 1000L, prepared, onCardLevel, cardOffset) {
        val here = schedule.epgBlockAt(shownBase)
        if (!onCardLevel || cardOffset == 0) here else {
            var b = here
            repeat(kotlin.math.abs(cardOffset)) {
                b = if (cardOffset > 0) schedule.epgBlockAt(b.endVirtualMs + 1)
                else schedule.epgBlockAt((b.startVirtualMs - 1).coerceAtLeast(0L))
            }
            b
        }
    }
    val nextBlock = remember(shownBlock.startVirtualMs) {
        schedule.epgBlockAt(shownBlock.endVirtualMs + 1)
    }
    // Czy karta pokazuje program AKTUALNIE GRANY — steruje wariantem paska
    val shownIsPlaying = positionMs in shownBlock.startVirtualMs until shownBlock.endVirtualMs

    // Klatki wokół kursora: przeliczane przy każdym kroku kursora (getClosestFrame
    // czyta z cache RAM/dysku, więc to tanie); poza taśmą nie liczymy nic.
    val scrubFrames = remember(scrubTapeVisible, cursorMs, prepared) {
        if (!scrubTapeVisible || !prepared) emptyList()
        else filmstrip.framesAround(
            centerVirtualMs = cursorMs ?: positionMs,
            liveEdgeVirtualMs = controller.virtualNow(),
            dvrStartVirtualMs = controller.dvrStartMs()
        ).map { (offset, bmp) -> bmp to offset }
    }

    // mini-EPG
    var epgRowIndex by remember { mutableStateOf(0) }
    var epgProgramIndex by remember { mutableStateOf(0) }
    // Rzędy mini-EPG: najpierw kanały z nagrań (grywalne), potem mockupowe
    // (sama ramówka, bez materiału — patrz PnMockChannels).
    val miniRows = remember(recorded, positionMs / 60_000L) {
        val now = System.currentTimeMillis()
        val recordedRows = recorded.map { rec ->
            val sch = BarkerSchedule(rec.items)
            val virt = (now - rec.recordedAtWallMs).coerceAtLeast(0L)
            val blocks = sch.blocksAround(virt, before = 0, after = 4)
            PnChannelRow(
                name = rec.name,
                number = rec.number,
                logoUrl = pnLogoFor(rec.name),
                programs = blocks.map { it.toPnProgram(rec.recordedAtWallMs) },
                liveIndex = 0,
                tunable = true
            )
        }
        recordedRows + pnMockChannelRows(now)
    }

    // ── przekotwiczenie osi wirtualnej po poznaniu REALNYCH długości ──
    // BarkerSchedule startuje z nominalnymi długościami z manifestu, a
    // onTimelineChanged NADPISUJE je tym, co zmierzył ExoPlayer. trackedCycle
    // policzony przy preparePlayer na nominalnych przestaje wtedy pasować:
    // pozycja wirtualna = cycle × materialCycleMs + …, więc przy ~480 cyklach
    // nawet sekundy różnicy na cykl dają GODZINY odjazdu (objaw: pierwsze
    // LEWO/PRAWO wyrzucało kursor na brzeg okna DVR, taśma pusta po lewej).
    // Jeden seek na live edge po ustabilizowaniu Timeline liczy trackedCycle
    // od nowa, już na realnych długościach.
    LaunchedEffect(prepared) {
        if (!prepared) return@LaunchedEffect
        delay(1_500)
        controller.seekToLiveEdge()
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
            scrubTapeVisible = false
            detailOpen = false
            cardOffset = 0
            zone = PnZone.CONTROLS
        }
    }

    // ── zatwierdzenie przewijania po chwili bezczynności ──
    LaunchedEffect(cursorTouchedAt) {
        if (cursorMs == null) return@LaunchedEffect
        delay(SCRUB_COMMIT_MS)
        if (System.currentTimeMillis() - cursorTouchedAt >= SCRUB_COMMIT_MS) {
            cursorMs?.let { controller.seekToVirtual(it) }
            cursorMs = null
            scrubTapeVisible = false
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
        scrubTapeVisible = true
        // Baza musi być SENSOWNA: 0 (tick jeszcze nie odczytał pozycji) ani
        // pozycja starsza niż całe okno DVR (oś jeszcze nie przekotwiczona —
        // patrz LaunchedEffect wyżej) nie są realną pozycją odtwarzania.
        val now = controller.virtualNow()
        val raw = cursorMs ?: positionMs
        val base = if (raw <= 0L || now - raw >= DVR_WINDOW_MS) now else raw
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
                // Mini-EPG otwiera się z OBU stron stosu playera: DOŁEM
                // z kontrolek i GÓRĄ z miniaturki (patrz gałąź CARD).
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
                KeyEvent.KEYCODE_DPAD_UP -> {
                    // trzeci poziom: miniaturka nad paskiem
                    scrubTapeVisible = false
                    cursorMs = null
                    cardOffset = 0
                    zone = PnZone.CARD
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    scrubTapeVisible = false
                    zone = PnZone.CONTROLS
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    cursorMs?.let { controller.seekToVirtual(it) }
                    cursorMs = null
                    scrubTapeVisible = false
                    isPaused = false
                    zone = PnZone.CONTROLS
                    true
                }
                else -> false
            }
            PnZone.CARD -> when {
                detailOpen -> when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        detailActionIndex = (detailActionIndex - 1).coerceAtLeast(0); true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        detailActionIndex =
                            (detailActionIndex + 1).coerceAtMost(PnDetailAction.values().lastIndex)
                        true
                    }
                    // Akcje "Nagraj"/"Przypomnij" są w makiecie bez skutków —
                    // chodzi o układ i stan fokusa, nie o realne nagrywanie.
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true
                    else -> false
                }
                else -> when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { cardOffset--; true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cardOffset++; true }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Mini-EPG leży NAD playerem: miniaturka to ostatni poziom
                        // playera, więc kolejne GÓRA wychodzi z niego do listy.
                        cardOffset = 0
                        epgRowIndex = channelIdx
                        epgProgramIndex = 0
                        zone = PnZone.MINI_EPG
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { cardOffset = 0; zone = PnZone.SCRUB; true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        detailActionIndex = 0
                        detailOpen = true
                        true
                    }
                    else -> false
                }
            }
            PnZone.MINI_EPG -> when (keyCode) {
                // GÓRA/DÓŁ chodzą WYŁĄCZNIE po kanałach — do playera wraca się
                // OK-iem (dostrojenie) albo BACK-iem, nie kierunkiem.
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (epgRowIndex > 0) { epgRowIndex--; epgProgramIndex = 0 }
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
                    // Kanały mockupowe nie mają materiału — OK je tylko zamyka
                    val row = miniRows.getOrNull(epgRowIndex)
                    if (row != null && row.tunable && epgRowIndex != channelIdx) {
                        channelIdx = epgRowIndex
                    }
                    zone = PnZone.CONTROLS
                    true
                }
                else -> false
            }
        }
    }

    // BACK: nakładka widoczna → schowaj; schowana → wyjście (wzorzec z demolive)
    BackHandler(enabled = true) {
        when {
            detailOpen -> detailOpen = false
            zone == PnZone.MINI_EPG -> zone = PnZone.CARD
            overlayVisible -> {
                overlayVisible = false
                cursorMs = null
                scrubTapeVisible = false
                cardOffset = 0
                zone = PnZone.CONTROLS
            }
            else -> onBackPressed()
        }
        touch()
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
            shownProgram = shownBlock.toPnProgram(controller.antennaStartWallMs),
            nextProgram = nextBlock.toPnProgram(controller.antennaStartWallMs),
            shownIsLive = positionMs in shownBlock.startVirtualMs until shownBlock.endVirtualMs,
            positionMs = positionMs,
            cursorMs = cursorMs,
            antennaStartWallMs = controller.antennaStartWallMs,
            liveEdgeMs = liveEdgeMs,
            miniEpgRows = miniRows,
            miniEpgRowIndex = epgRowIndex,
            miniEpgProgramIndex = epgProgramIndex,
            detailOpen = detailOpen,
            detailDescription = shownBlock.description,
            detailActionIndex = detailActionIndex,
            shownIsPlaying = shownIsPlaying,
            scrubTapeVisible = scrubTapeVisible && zone == PnZone.SCRUB,
            scrubFrames = scrubFrames,
            dvrStartMs = controller.dvrStartMs(),
            blockTitleFor = { v -> schedule.epgBlockAt(v).title },
            sx = sx, sy = sy
        )
    }
}

/**
 * EpgBlock silnika anteny → model karty/mini-EPG.
 * [antennaStartWallMs] przelicza oś wirtualną kanału na zegar ścienny — każdy
 * kanał ma własną oś, a mini-EPG zestawia je obok siebie.
 */
private fun BarkerSchedule.EpgBlock.toPnProgram(antennaStartWallMs: Long): PnProgram {
    val minutes = ((endVirtualMs - startVirtualMs) / 60_000L).toInt()
    return PnProgram(
        title = title,
        meta = listOf(year, genre, "$minutes min.", age),
        coverUrl = coverUrl,
        startWallMs = antennaStartWallMs + startVirtualMs,
        endWallMs = antennaStartWallMs + endVirtualMs
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
        modifier = Modifier.fillMaxSize().background(PN_SCRIM),
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
