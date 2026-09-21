package com.uxellence.tv.v3.demolive2

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
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
 *   OK / GÓRA / DÓŁ  → na czystym obrazie: pokaż nakładkę, fokus na kontrolkach
 *   LEWO/PRAWO       → pas kontrolek: wybór ikony
 *   GÓRA             → pas przewijania (playhead na mint); LEWO/PRAWO przewija
 *                      ±30 s, ZATRZYMUJĄC SIĘ na każdej granicy programu;
 *                      OK zatwierdza przewinięcie (bez OK kanał się nie przestraja)
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
/**
 * Auto-hide w trakcie PRZEWIJANIA: dopóki przewinięcie nie jest zatwierdzone
 * OK-iem, cały scrub (pasek + taśma podglądu) zostaje na ekranie MINUTĘ od
 * ostatniego ruchu — user przegląda materiał i nie chce, żeby UI uciekało
 * po 6 s jak przy zwykłej nakładce.
 */
private const val SCRUB_HIDE_MS = 60_000L
/** Ile programów pokazywać w mini-EPG wstecz / w przód od bieżącego. */
private const val EPG_BEFORE = 3
private const val EPG_AFTER = 4
/**
 * Margines przy skoku na POCZĄTEK materiału. Granica bloku leży dokładnie na
 * styku dwóch plików, a realne długości z ExoPlayera różnią się od nominalnych
 * o ułamki sekundy — seek dokładnie na granicę potrafił wylądować tuż przed
 * końcem POPRZEDNIEGO materiału i natychmiast przeskoczyć do następnego
 * (objaw: "klikam informacyjny, a leci Agrobiznes").
 */
private const val START_MARGIN_MS = 2_000L
private const val SCRUB_STEP_MS = 30_000L
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
    var toast by remember { mutableStateOf<String?>(null) }
    // Program, dla którego otwarty jest modal nagrywania (jak v1)
    var recordingFor by remember { mutableStateOf<PnProgram?>(null) }
    var lastInputAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Oś: pozycja odtwarzania + live edge (tickowane), kursor przewijania
    var positionMs by remember { mutableLongStateOf(0L) }
    var liveEdgeMs by remember { mutableLongStateOf(0L) }
    var cursorMs by remember { mutableStateOf<Long?>(null) }
    // Taśma podglądu wchodzi DOPIERO po pierwszym LEWO/PRAWO na pasku
    var scrubTapeVisible by remember { mutableStateOf(false) }
    // repeatCount ostatniego KeyDown: 0 = nowe fizyczne naciśnięcie, >0 = trzymanie
    var scrubKeyRepeat by remember { mutableStateOf(0) }
    // Pozycja granicy, na której kursor STOI aż do puszczenia klawisza (-1 = brak)
    var snapLockMs by remember { mutableLongStateOf(-1L) }
    // 3. poziom fokusa: miniaturka/karta. Offset liczony w BLOKACH ramówki od
    // programu granego (0 = grany), OK rozwija kartę w detal.
    var cardOffset by remember { mutableStateOf(0) }
    var detailOpen by remember { mutableStateOf(false) }
    // Detal otwarty Z MINI-EPG (a nie z karty playera) — wtedy dotyczy programu
    // wskazanego w liście i ma akcje zależne od tego, czy już leciał.
    var detailFromEpg by remember { mutableStateOf(false) }
    // Seek zlecony dla kanału, który dopiero się przygotowuje (zmiana kanału
    // tworzy NOWY kontroler) — wykonywany po `prepared`.
    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var detailActionIndex by remember { mutableStateOf(0) }

    // Program pokazywany na karcie:
    //  - strefa CARD/detal → blok oddalony o cardOffset od granego,
    //  - inaczej → blok pod kursorem (albo pod pozycją odtwarzania).
    val schedule = controller.schedule
    // TEN SAM STRAŻNIK co w moveCursor: 0 (tick jeszcze nie odczytał pozycji) ani
    // pozycja starsza niż całe okno DVR nie są realną pozycją odtwarzania. Bez
    // tego epgBlockAt(0) zwracał PIERWSZY blok nagrania i "Oglądaj" seekowało
    // do zera zamiast na początek programu.
    val shownBase = cursorMs ?: positionMs.let { p ->
        val now = controller.virtualNow()
        if (p <= 0L || now - p >= DVR_WINDOW_MS) now else p
    }
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
    val miniRows = remember(recorded, channel.id, prepared, positionMs / 60_000L) {
        val now = System.currentTimeMillis()
        val recordedRows = recorded.map { rec ->
            // KANAŁ DOSTROJONY musi korzystać z harmonogramu KONTROLERA: ten ma
            // długości nadpisane przez ExoPlayer, a świeży BarkerSchedule tylko
            // nominalne z manifestu. Przy ~200 cyklach rozjazd jest tak duży, że
            // bloki wypadały na początku nagrania (startVirtualMs = 0) i "Oglądaj"
            // seekowało do zera. Dla pozostałych kanałów nie mamy nic lepszego
            // niż wartości nominalne.
            val sch = if (rec.id == channel.id) controller.schedule else BarkerSchedule(rec.items)
            val virt = (now - rec.recordedAtWallMs).coerceAtLeast(0L)
            // Programy TAKŻE WSTECZ — user ma móc cofnąć się kilka materiałów
            // i odtworzyć je od początku (jak v1).
            val blocks = sch.blocksAround(virt, before = EPG_BEFORE, after = EPG_AFTER)
            val live = blocks.indexOfFirst { virt < it.endVirtualMs }.coerceAtLeast(0)
            PnChannelRow(
                name = rec.name,
                number = rec.number,
                logoUrl = pnLogoFor(rec.name),
                programs = blocks.map { it.toPnProgram(rec.recordedAtWallMs) },
                liveIndex = live,
                tunable = true
            )
        }
        // Mock nie ma własnych klatek — podkładamy okładki z paczek nagrań,
        // żeby rzędy mini-EPG nie były pustymi prostokątami.
        val covers = recorded.flatMap { rec -> rec.items.mapNotNull { it.coverUrl } }
        recordedRows + pnMockChannelRows(now, covers)
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

    // ── komunikat atrapy znika sam ──
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        delay(2_500)
        toast = null
    }

    // ── klatki taśmy DOPIERO przy pierwszym przewijaniu ──
    // Ekstrakcja idzie po 15 plikach po ~1 GB z karty SD; robiona przy starcie
    // kanału dławiła box (I/O + CPU) razem z odtwarzaniem i seekiem.
    LaunchedEffect(scrubTapeVisible, prepared, channel.id) {
        if (!scrubTapeVisible || !prepared) return@LaunchedEffect
        filmstrip.startExtraction(channel.items.map { it.url.removePrefix("file://") })
    }

    // ── seek zlecony przed przełączeniem kanału ──
    LaunchedEffect(prepared, channel.id, pendingSeekMs) {
        val target = pendingSeekMs ?: return@LaunchedEffect
        if (!prepared) return@LaunchedEffect
        delay(1_600)                     // po przekotwiczeniu osi (patrz niżej)
        controller.seekToVirtual(target)
        isPaused = false
        pendingSeekMs = null
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
    LaunchedEffect(lastInputAt, overlayVisible, zone, cursorMs != null) {
        if (!overlayVisible) return@LaunchedEffect
        // Rozpoczęte, niezatwierdzone przewinięcie trzyma UI przez minutę
        val hideAfter = if (cursorMs != null) SCRUB_HIDE_MS else AUTO_HIDE_MS
        delay(hideAfter)
        if (System.currentTimeMillis() - lastInputAt >= hideAfter) {
            overlayVisible = false
            cursorMs = null
            scrubTapeVisible = false
            snapLockMs = -1L
            detailOpen = false
            cardOffset = 0
            zone = PnZone.CONTROLS
        }
    }

    // BEZ auto-commitu: przewinięcie zatwierdza dopiero OK (jak w 1. wersji).
    // Wcześniej seek odpalał się sam po 800 ms bezczynności, więc przy wolniejszym
    // przewijaniu kanał "wstrajał się" w miejscu, w którym user tylko przystanął.

    fun touch() {
        lastInputAt = System.currentTimeMillis()
    }

    fun show(target: PnZone) {
        overlayVisible = true
        zone = target
        touch()
    }

    /**
     * Krok przewijania ze SNAPEM do granicy materiału — wzorzec z 1. wersji
     * (scrubStepWithSnap): przy TRZYMANIU strzałki (autorepeat, repeatCount > 0)
     * kursor zatrzymuje się na granicy i stoi, aż user puści klawisz. NOWE
     * fizyczne naciśnięcie (repeatCount == 0) zwalnia blokadę i rusza dalej.
     * Sygnałem jest repeatCount, nie timing — dzięki temu szybkie klikanie nigdy
     * nie daje trwałej blokady, a trzymanie nie przelatuje przez programy.
     */
    fun moveCursor(deltaMs: Long) {
        scrubTapeVisible = true
        val now = controller.virtualNow()
        val raw = cursorMs ?: positionMs
        // 0 albo pozycja starsza niż całe okno DVR nie są realną pozycją odtwarzania
        val base = if (raw <= 0L || now - raw >= DVR_WINDOW_MS) now else raw

        if (scrubKeyRepeat == 0) snapLockMs = -1L      // nowy klik zwalnia lock
        if (snapLockMs >= 0 && base == snapLockMs) {
            touch()
            return                                     // autorepeat na granicy — stój
        }
        snapLockMs = -1L

        val block = schedule.epgBlockAt(base)
        val stepped = base + deltaMs
        val snapped = when {
            deltaMs < 0 && stepped < block.startVirtualMs && base > block.startVirtualMs ->
                block.startVirtualMs
            deltaMs > 0 && stepped > block.endVirtualMs && base < block.endVirtualMs ->
                block.endVirtualMs
            else -> stepped
        }
        if (snapped != stepped) snapLockMs = snapped    // stanęliśmy na granicy

        cursorMs = snapped.coerceIn(controller.dvrStartMs(), now)
        touch()
    }

    // ── DETAL: program, czas względem teraz i zestaw akcji (jak v1) ──
    val nowWall = System.currentTimeMillis()
    val epgProgram = miniRows.getOrNull(epgRowIndex)?.programs?.getOrNull(epgProgramIndex)
    val detailProgram =
        if (detailFromEpg) epgProgram else shownBlock.toPnProgram(controller.antennaStartWallMs)
    val detailTiming = when {
        detailProgram == null -> PnTiming.CURRENT
        detailProgram.endWallMs <= nowWall -> PnTiming.PAST
        detailProgram.startWallMs > nowWall -> PnTiming.FUTURE
        else -> PnTiming.CURRENT
    }
    val detailActions = pnDetailActions(detailTiming)

    fun closeDetail() {
        detailOpen = false
        detailFromEpg = false
        detailActionIndex = 0
    }

    /** OK na przycisku detalu — odwzorowanie zachowania v1. */
    fun activateDetailAction() {
        val program = detailProgram ?: return
        when (detailActions.getOrNull(detailActionIndex)) {
            PnDetailAction.WATCH -> {
                val targetRow = if (detailFromEpg) epgRowIndex else channelIdx
                val row = miniRows.getOrNull(targetRow)
                if (row != null && !row.tunable) {
                    toast = "${row.name}: kanał bez materiału w makiecie"
                    return
                }
                // Seek w osi wirtualnej kanału docelowego
                // Miniony program oglądamy OD POCZĄTKU (timeshift), bieżący
                // po prostu dostrajamy na żywo. Pozycja bierze się WPROST z osi
                // wirtualnej programu — tak jak v1 używa detailStartVirtualMs —
                // zamiast przeliczania przez zegar, gdzie łatwo o rozjazd.
                val seekTo = if (detailTiming == PnTiming.PAST && program.startVirtualMs >= 0) {
                    program.startVirtualMs + START_MARGIN_MS
                } else -1L
                android.util.Log.i(
                    "DemoLive2",
                    "detal:${detailActions[detailActionIndex]} timing=$detailTiming " +
                        "row=$targetRow→ch=$channelIdx virt=${program.startVirtualMs} " +
                        "seekTo=$seekTo '${program.title}'"
                )

                if (targetRow != channelIdx) {
                    channelIdx = targetRow              // nowy kontroler
                    pendingSeekMs = seekTo.takeIf { it > 0 }
                } else if (seekTo > 0) {
                    controller.seekToVirtual(seekTo)
                    isPaused = false
                } else {
                    controller.seekToLiveEdge()
                    isPaused = false
                }
                // Jak v1: miniony materiał startuje na CZYSTYM obrazie
                closeDetail()
                cardOffset = 0
                cursorMs = null
                scrubTapeVisible = false
                zone = PnZone.CONTROLS
                overlayVisible = detailTiming != PnTiming.PAST
            }
            PnDetailAction.RECORD -> recordingFor = program
            PnDetailAction.REMIND -> toast = "Ustawiono przypomnienie: ${program.title}"
            null -> Unit
        }
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
                // Jak v1: blok liczony z AKTUALNEJ POZYCJI ODTWARZANIA, a nie
                // z karty (ta może pokazywać inny program po przewinięciu).
                val block = schedule.epgBlockAt(controller.currentVirtualPositionMs())
                controller.seekToVirtual(block.startVirtualMs)
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
            // Jak v1: pozycje bez logiki dają komunikat, żeby było widać, że
            // klik został przyjęty (a nie że przycisk jest martwy)
            PnControl.REC -> recordingFor = shownBlock.toPnProgram(controller.antennaStartWallMs)
            // Opis z kontrolek = TEN SAM detal co po kliknięciu miniaturki
            PnControl.INFO -> {
                cardOffset = 0
                detailFromEpg = false
                detailActionIndex = 0
                detailOpen = true
                zone = PnZone.CARD
            }
            PnControl.SETTINGS -> toast = "Napisy i dźwięk — atrapa makiety"
        }
        touch()
    }

    fun handleKey(keyCode: Int, repeatCount: Int): Boolean {
        scrubKeyRepeat = repeatCount
        touch()
        if (!overlayVisible) {
            when (keyCode) {
                // Na CZYSTYM playerze OK, GÓRA i DÓŁ robią to samo: pokazują
                // player (pas kontrolek). Do paska przewijania i do mini-EPG
                // wchodzi się dopiero Z nakładki — inaczej DÓŁ z czystego obrazu
                // wrzucał od razu w listę kanałów, z pominięciem playera.
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    show(PnZone.CONTROLS); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> { show(PnZone.SCRUB); moveCursor(-SCRUB_STEP_MS); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { show(PnZone.SCRUB); moveCursor(SCRUB_STEP_MS); return true }
                else -> return false
            }
        }
        // DETAL ma własną obsługę NIEZALEŻNIE od strefy — otwiera się i z karty
        // (zone=CARD), i z mini-EPG (zone=MINI_EPG). Wcześniej siedziała tylko
        // w gałęzi CARD, więc OK w detalu z listy nie działał.
        if (detailOpen) {
            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    detailActionIndex = (detailActionIndex - 1).coerceAtLeast(0); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    detailActionIndex =
                        (detailActionIndex + 1).coerceAtMost(detailActions.lastIndex); true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    activateDetailAction(); true
                }
                else -> false
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
                    epgRowIndex = channelIdx
                    epgProgramIndex = miniRows.getOrNull(channelIdx)?.liveIndex ?: 0
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
                    snapLockMs = -1L
                    zone = PnZone.CONTROLS
                    true
                }
                // OK ZATWIERDZA przewinięcie — dopiero tu następuje seek
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    cursorMs?.let { controller.seekToVirtual(it) }
                    cursorMs = null
                    scrubTapeVisible = false
                    snapLockMs = -1L
                    isPaused = false
                    zone = PnZone.CONTROLS
                    true
                }
                else -> false
            }
            PnZone.CARD -> when {
                else -> when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { cardOffset--; true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cardOffset++; true }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Mini-EPG leży NAD playerem: miniaturka to ostatni poziom
                        // playera, więc kolejne GÓRA wychodzi z niego do listy.
                        cardOffset = 0
                        epgRowIndex = channelIdx
                        epgProgramIndex = miniRows.getOrNull(channelIdx)?.liveIndex ?: 0
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
                    if (epgRowIndex > 0) {
                        epgRowIndex--
                        epgProgramIndex = miniRows.getOrNull(epgRowIndex)?.liveIndex ?: 0
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (epgRowIndex < miniRows.lastIndex) {
                        epgRowIndex++
                        epgProgramIndex = miniRows.getOrNull(epgRowIndex)?.liveIndex ?: 0
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    epgProgramIndex = (epgProgramIndex - 1).coerceAtLeast(0); true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val last = (miniRows.getOrNull(epgRowIndex)?.programs?.lastIndex ?: 0)
                    epgProgramIndex = (epgProgramIndex + 1).coerceAtMost(last); true
                }
                // OK otwiera DETAL programu (jak v1) — dopiero z niego wychodzi
                // oglądanie od początku / nagrywanie.
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // Program, KTÓRY WŁAŚNIE TRWA, włącza się od razu — bez detalu
                    // (jak v1). Detal jest dla minionych (oglądaj od początku)
                    // i przyszłych (nagrywanie).
                    val prog = miniRows.getOrNull(epgRowIndex)
                        ?.programs?.getOrNull(epgProgramIndex)
                    val nowMs = System.currentTimeMillis()
                    val isNow = prog != null &&
                        prog.startWallMs <= nowMs && nowMs < prog.endWallMs
                    if (isNow) {
                        val row = miniRows.getOrNull(epgRowIndex)
                        if (row != null && !row.tunable) {
                            toast = "${row.name}: kanał bez materiału w makiecie"
                        } else {
                            if (epgRowIndex != channelIdx) channelIdx = epgRowIndex
                            cursorMs = null
                            scrubTapeVisible = false
                            zone = PnZone.CONTROLS
                        }
                    } else {
                        detailActionIndex = 0
                        detailFromEpg = true
                        detailOpen = true
                    }
                    true
                }
                else -> false
            }
        }
    }

    // BACK: nakładka widoczna → schowaj; schowana → wyjście (wzorzec z demolive)
    BackHandler(enabled = true) {
        when {
            detailOpen -> closeDetail()
            zone == PnZone.MINI_EPG -> zone = PnZone.CARD
            overlayVisible -> {
                overlayVisible = false
                cursorMs = null
                scrubTapeVisible = false
                snapLockMs = -1L
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
        // STALE VAL: factory AndroidView wykonuje się RAZ i zamyka w sobie handleKey
        // z PIERWSZEJ kompozycji — wraz z detailProgram, detailTiming, shownBlock
        // i miniRows sprzed wszystkich zmian. Objaw: "Oglądaj" na minionym programie
        // odtwarzało zawsze ten sam, bieżący-na-starcie materiał. Handler musi być
        // czytany przez State, tak jak keyHandler w v1 (rememberUpdatedState).
        val keyHandler = rememberUpdatedState<(Int, Int) -> Boolean> { code, repeat ->
            handleKey(code, repeat)
        }
        AndroidView(
            factory = { ctx -> PnVideoView(ctx) { code, repeat -> keyHandler.value(code, repeat) } },
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

        recordingFor?.let { prog ->
            com.uxellence.tv.v3.demolive.DemoRecordingModal(
                title = prog.title,
                subtitle = "${pnClock(prog.startWallMs)} - ${pnClock(prog.endWallMs)}",
                keepLabel = "Zachowaj do 30 dni",
                onRecordEpisode = {
                    recordingFor = null
                    toast = "Zlecono nagrywanie odcinka: ${prog.title}"
                },
                onRecordSeries = {
                    recordingFor = null
                    toast = "Zlecono nagrywanie serii: ${prog.title}"
                },
                onDismiss = { recordingFor = null },
                sx = sx, sy = sy
            )
        }

        toast?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(30f),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = sy(120))
                        .background(PN_SCRIM, RoundedCornerShape(sx(12)))
                        .padding(horizontal = sx(28), vertical = sy(14))
                ) {
                    Text(text = msg, color = PN_TEXT, fontSize = pnSp(26, sy))
                }
            }
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
            miniEpgTunedIndex = channelIdx,
            detailOpen = detailOpen,
            detailDescription =
                if (detailFromEpg) (epgProgram?.description ?: "") else shownBlock.description,
            detailActionIndex = detailActionIndex,
            detailProgram = detailProgram,
            detailActions = detailActions,
            detailTiming = detailTiming,
            detailIsToday = detailTiming != PnTiming.FUTURE ||
                (detailProgram?.startWallMs ?: 0L) - nowWall < 12 * 3_600_000L,
            shownIsPlaying = shownIsPlaying,
            shownTiming = run {
                val startWall = controller.antennaStartWallMs + shownBlock.startVirtualMs
                val endWall = controller.antennaStartWallMs + shownBlock.endVirtualMs
                when {
                    endWall <= nowWall -> PnTiming.PAST
                    startWall > nowWall -> PnTiming.FUTURE
                    else -> PnTiming.CURRENT
                }
            },
            hasPrevProgram = shownBlock.startVirtualMs > controller.dvrStartMs(),
            isAtLiveEdge = controller.isAtLiveEdge(),
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
        endWallMs = antennaStartWallMs + endVirtualMs,
        description = description,
        startVirtualMs = startVirtualMs
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
    private val onKey: (Int, Int) -> Boolean,   // (keyCode, repeatCount)
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
        if (event.action == android.view.KeyEvent.ACTION_DOWN &&
            onKey(event.keyCode, event.repeatCount)
        ) return true
        return super.dispatchKeyEvent(event)
    }
}
