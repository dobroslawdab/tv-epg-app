package com.uxellence.tv.v3.demolive

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.uxellence.tv.v3.epg.FrameCaptureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "DemoVodPlayer"
private const val SEEK_STEP_MS = 10_000L
private const val PLAYER_UI_TIMEOUT_MS = 10_000L

/**
 * DEMO VOD PLAYER — player POJEDYNCZEGO zwiastuna wyglądający DOKŁADNIE jak
 * player z „Demo: kanał live" (ten sam DemoPlayerUi: kontrolki, taśma,
 * opis pod foldem, gradient, pasek powiadomień), ale bez poprzedniego/
 * następnego materiału, bez EPG i bez kanałów.
 *
 * Klawisze jak w demo live:
 *  - FULLSCREEN: OK/GÓRA/DÓŁ → kontrolki; LEWO/PRAWO → taśma przewijania
 *  - BUTTONS: LEWO/PRAWO fokus przycisków, GÓRA → taśma, DÓŁ → opis,
 *    OK: pauza / od początku
 *  - STRIP: LEWO/PRAWO kursor po klatkach (STOPKLATKA — wideo spauzowane),
 *    OK = skok + powrót na kontrolki, BACK = kontrolki (wznowienie)
 *  - auto-hide kontrolek po 10 s (gdy nie spauzowane)
 */
@Composable
fun DemoVodPlayerScreen(
    streamUrl: String,
    title: String,
    description: String,
    genre: String = "zwiastun",
    year: String = "",
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    remember { DemoPlayerPrefs.load(context) }
    val useFigmaButtons = DemoPlayerPrefs.useFigmaButtons.value

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var localPath by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }

    var layer by remember { mutableStateOf(DemoLayer.FULLSCREEN) }
    var playerZone by remember { mutableStateOf(PlayerZone.BUTTONS) }
    var buttonsFocus by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var scrubCursorMs by remember { mutableLongStateOf(0L) }
    var interactionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var demoToast by remember { mutableStateOf<String?>(null) }
    var filmstripFrames by remember { mutableStateOf<List<Pair<Long, android.graphics.Bitmap?>>>(emptyList()) }

    // Oś czasu jak w demo live: „antena" startuje w momencie startu odtwarzania,
    // dzięki czemu pasek pokazuje realne godziny (wygląd 1:1 z kanałem live)
    val antennaStartWallMs = remember { System.currentTimeMillis() }
    val frameCapture = remember { FrameCaptureManager() }

    fun updateFilmstrip(centerMs: Long) {
        filmstripFrames = frameCapture.getFramesAround(
            centerPositionMs = centerMs, stepMs = SEEK_STEP_MS, sideCount = 3
        )
    }

    // Pobranie MP4 do cache (progressive; jak VodPlayerScreen — .part → rename)
    LaunchedEffect(streamUrl) {
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "vod_cache").apply { mkdirs() }
                val file = File(dir, "vod_${streamUrl.hashCode()}.mp4")
                if (file.exists() && file.length() > 0) {
                    localPath = file.absolutePath
                    return@withContext
                }
                val part = File(dir, "${file.name}.part").apply { delete() }
                val conn = URL(streamUrl).openConnection()
                val total = conn.contentLength
                conn.getInputStream().use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            sum += read
                            if (total > 0) downloadProgress = ((sum * 100) / total).toInt()
                        }
                    }
                }
                if (total > 0 && part.length() != total.toLong()) {
                    part.delete(); return@withContext
                }
                part.renameTo(file)
                localPath = file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
            }
        }
    }

    // Player + ekstrakcja klatek po pobraniu
    LaunchedEffect(localPath) {
        val path = localPath ?: return@LaunchedEffect
        val p = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(File(path).toURI().toString()))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) durationMs = duration
                }
            })
        }
        player = p
        // Klatki na całej długości (co ~5 s) — miniaturki taśmy
        while (p.duration <= 0) delay(100)
        durationMs = p.duration
        val count = (durationMs / 5_000L).toInt()
            .coerceIn(1, FrameCaptureManager.MAX_FRAMES - 2)
        frameCapture.extractKeyFrames(path, durationMs, count = count)
    }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            frameCapture.release()
        }
    }

    // Poll pozycji (500 ms, jak demo live)
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            positionMs = p.currentPosition
            delay(500)
        }
    }

    // Auto-hide kontrolek po 10 s (jak demo live)
    LaunchedEffect(layer, playerZone, interactionAt, isPaused) {
        if (layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.BUTTONS && !isPaused) {
            delay(PLAYER_UI_TIMEOUT_MS)
            layer = DemoLayer.FULLSCREEN
        }
    }

    fun openButtons() {
        playerZone = PlayerZone.BUTTONS
        buttonsFocus = 0
        layer = DemoLayer.PLAYER_UI
        interactionAt = System.currentTimeMillis()
    }

    fun openStrip() {
        // STOPKLATKA: wejście w przewijanie pauzuje wideo — kadr zamrożony
        player?.pause()
        isPaused = true
        playerZone = PlayerZone.STRIP
        scrubCursorMs = player?.currentPosition ?: 0L
        updateFilmstrip(scrubCursorMs)
        layer = DemoLayer.PLAYER_UI
        interactionAt = System.currentTimeMillis()
    }

    fun handleKey(keyCode: Int): Boolean {
        val p = player ?: return false
        // Detal (MovieDetailScreen) ma własny fokus i klawisze — nie przechwytuj
        if (layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DETAIL) return false
        interactionAt = System.currentTimeMillis()
        if (keyCode == android.view.KeyEvent.KEYCODE_3) {
            val on = DemoPlayerPrefs.toggle(context)
            demoToast = if (on) "Przyciski: wersja Figma (ikony)" else "Przyciski: wersja tekstowa"
            return true
        }
        return when (layer) {
            DemoLayer.FULLSCREEN -> when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { openButtons(); true }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { openStrip(); true }
                else -> false
            }
            else -> when (playerZone) {
                PlayerZone.BUTTONS -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { buttonsFocus = (buttonsFocus - 1).coerceAtLeast(0); true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { buttonsFocus = (buttonsFocus + 1).coerceAtMost(2); true }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { openStrip(); true }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { playerZone = PlayerZone.SNIPPET; true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        when (buttonsFocus) {
                            0 -> {  // Zatrzymaj / Wznów
                                if (p.isPlaying) { p.pause(); isPaused = true }
                                else { p.play(); isPaused = false }
                            }
                            1 -> {  // Zacznij od początku
                                p.seekTo(0); p.play(); isPaused = false
                            }
                            else -> { /* Napisy — atrapa w demie VOD */ }
                        }
                        true
                    }
                    else -> false
                }
                PlayerZone.STRIP -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Z miniaturek W DÓŁ na player: wznowienie od stopklatki
                        p.play(); isPaused = false
                        openButtons(); true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        scrubCursorMs = (scrubCursorMs - SEEK_STEP_MS).coerceAtLeast(0L)
                        updateFilmstrip(scrubCursorMs); true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        scrubCursorMs = (scrubCursorMs + SEEK_STEP_MS)
                            .coerceAtMost(durationMs.coerceAtLeast(0L))
                        updateFilmstrip(scrubCursorMs); true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                        // OK = skok do kursora i POWRÓT NA KONTROLKI (jak demo live)
                        p.seekTo(scrubCursorMs); p.play(); isPaused = false
                        openButtons(); true
                    }
                    else -> false
                }
                PlayerZone.SNIPPET -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> { playerZone = PlayerZone.BUTTONS; true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER -> {
                        // OK na tekście → DETAL (jak w demo live, z PIP)
                        playerZone = PlayerZone.DETAIL; true
                    }
                    else -> keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                }
                else -> false
            }
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(localPath) {
        if (localPath != null) { delay(200); rootFocus.requestFocus() }
    }
    BackHandler(enabled = true) {
        when {
            layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DETAIL -> {
                playerZone = PlayerZone.SNIPPET
                interactionAt = System.currentTimeMillis()
            }
            layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.STRIP -> {
                // BACK z taśmy: wznowienie od miejsca stopklatki, kontrolki
                player?.play(); isPaused = false
                openButtons()
            }
            layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.SNIPPET ->
                playerZone = PlayerZone.BUTTONS
            layer == DemoLayer.PLAYER_UI -> layer = DemoLayer.FULLSCREEN
            else -> onBackPressed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    return@onPreviewKeyEvent false
                }
                handleKey(event.nativeKeyEvent.keyCode)
            }
            .focusable()
    ) {
        // Jeden widok wideo renderowany z dwóch pozycji (movableContentOf,
        // jak demo live): fullscreen POD warstwami albo PIP NAD detalem
        val isDetail = layer == DemoLayer.PLAYER_UI && playerZone == PlayerZone.DETAIL
        val videoLayer = remember {
            movableContentOf { modifier: Modifier ->
                player?.let { p ->
                    AndroidView(
                        factory = { ctx ->
                            android.view.TextureView(ctx).also { p.setVideoTextureView(it) }
                        },
                        update = { tv -> p.setVideoTextureView(tv) },
                        modifier = modifier
                    )
                }
            }
        }
        if (!isDetail) {
            videoLayer(Modifier.fillMaxSize())
        }

        // Plansza pobierania
        if (localPath == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = title,
                    color = Color(0xFFEEEEEE),
                    fontSize = demoSp(32, sy),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Pobieranie… $downloadProgress%",
                    color = Color(0x99EEEEEE),
                    fontSize = demoSp(24, sy)
                )
                CircularProgressIndicator(color = Color(0xFF5AECD3))
            }
        }

        // ===== ZUNIFIKOWANE UI PLAYERA — identyczne z Demo: kanał live =====
        val block = BarkerSchedule.EpgBlock(
            title = title,
            startVirtualMs = 0L,
            endVirtualMs = durationMs.coerceAtLeast(1L),
            genre = genre,
            year = year,
            country = "",
            age = "",
            description = description
        )
        DemoPlayerUi(
            isVisible = layer == DemoLayer.PLAYER_UI && playerZone != PlayerZone.DETAIL,
            zone = playerZone,
            block = block,
            prevBlock = null,       // pojedynczy materiał — bez sąsiadów
            nextBlock = null,
            channel = null,         // bez badge'a kanału
            channelNumber = 0,
            currentVirtualMs = positionMs,
            liveEdgeVirtualMs = durationMs.coerceAtLeast(1L),
            dvrStartVirtualMs = 0L,
            scrubCursorMs = scrubCursorMs,
            antennaStartWallMs = antennaStartWallMs,
            isPaused = isPaused,
            isAtLiveEdge = true,    // VOD: bez przycisku "Wróć do live"
            buttonsFocusIndex = if (playerZone == PlayerZone.BUTTONS) buttonsFocus else -1,
            figmaButtons = useFigmaButtons,
            vodButtons = true,
            frames = filmstripFrames,
            blockTitleFor = { title },
            scrubNextTile = DemoPlayerPrefs.scrubNextTile.value,
            blockMetaFor = { listOf(genre, year).filter { it.isNotBlank() }.joinToString(", ") },
            sx = sx, sy = sy
        )

        if (isDetail) {
            // PRAWDZIWY MovieDetailScreen jak w demo live + PIP z żywym wideo
            Box(modifier = Modifier.fillMaxSize().zIndex(14f)) {
                com.uxellence.tv.v3.moviedetail.MovieDetailScreen(
                    item = com.uxellence.tv.v3.VodSlideData(
                        title = title,
                        genre = genre,
                        duration = "${(durationMs / 60_000L).coerceAtLeast(1)} min",
                        year = year,
                        country = "",
                        ageRating = "12 lat",
                        description = description,
                        price = "",
                        backgroundUrl = "",
                        posterUrl = ""
                    ),
                    onBackPressed = { /* BACK obsługuje BackHandler */ },
                    onWatchClicked = {
                        // Oglądaj = zwiastun od początku, powrót na kontrolki
                        player?.seekTo(0); player?.play(); isPaused = false
                        openButtons()
                    },
                    wideoHeaderSlot = {
                        DemoDetailInfoLine(
                            timeRange = "Zwiastun • " +
                                "${(durationMs / 60_000L).coerceAtLeast(1)} min",
                            isLive = false,
                            canStartOver = true,
                            sx = sx, sy = sy
                        )
                    }
                )
                // PIP: to samo wideo w prawym dolnym rogu (Figma Detail 5507:5100)
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
        }

        DemoInfoToast(
            text = demoToast,
            onHidden = { demoToast = null },
            sx = sx, sy = sy
        )
    }
}
