package com.uxellence.tv.v3.vodplayer

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.ui.PlayerView
import com.uxellence.tv.v3.epg.FrameCaptureManager
import com.uxellence.tv.v3.epg.TimeshiftOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "VodPlayer"
private const val SEEK_STEP_MS = 5_000L
private const val CAPTURE_INTERVAL_MS = 5_000L

/**
 * VOD Player with filmstrip seeking overlay.
 *
 * LEFT/RIGHT = seek ±10s (pauses + shows filmstrip)
 * OK = confirm seek / toggle play-pause
 * BACK = cancel seek / exit player
 */
@Composable
fun VodPlayerScreen(
    streamUrl: String,
    title: String,
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    fullFilmstrip: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var localFilePath by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }

    // Seek mode state
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var seekStartPositionMs by remember { mutableLongStateOf(0L) }
    var lastSeekActionTime by remember { mutableLongStateOf(0L) }

    // Acceleration: rapid presses → faster seeking
    var rapidPressCount by remember { mutableIntStateOf(0) }
    var lastPressTimeNano by remember { mutableLongStateOf(0L) }

    fun getSeekStep(): Long {
        val now = System.nanoTime()
        val elapsedMs = (now - lastPressTimeNano) / 1_000_000
        lastPressTimeNano = now

        // 600ms threshold — covers Android TV initial key repeat delay (~500ms)
        // After that, repeats come every ~50-80ms when holding the button
        if (elapsedMs < 600) {
            rapidPressCount++
        } else {
            rapidPressCount = 0
        }

        val multiplier = when {
            rapidPressCount >= 25 -> 10  // ~2s hold → x10 = 50s/step
            rapidPressCount >= 15 -> 5   // ~1.2s hold → x5 = 25s/step
            rapidPressCount >= 6  -> 2   // ~0.5s hold → x2 = 10s/step
            else -> 1                     // start → x1 = 5s/step
        }
        return SEEK_STEP_MS * multiplier
    }

    // Frame capture
    val frameCaptureManager = remember { FrameCaptureManager() }
    var filmstripFrames by remember { mutableStateOf<List<Pair<Long, Bitmap?>>>(emptyList()) }

    // Helper: update filmstrip from pre-extracted keyframes only (no PixelCopy during seek)
    fun updateFilmstrip(posMs: Long) {
        filmstripFrames = frameCaptureManager.getFramesAround(
            centerPositionMs = posMs,
            stepMs = SEEK_STEP_MS,
            sideCount = 3
        )
    }

    // Step 1: Download MP4 to local cache
    LaunchedEffect(streamUrl) {
        if (streamUrl.isEmpty()) return@LaunchedEffect

        if (!streamUrl.startsWith("http")) {
            localFilePath = streamUrl
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "vod_cache")
                cacheDir.mkdirs()
                val fileName = "vod_${streamUrl.hashCode()}.mp4"
                val localFile = File(cacheDir, fileName)

                if (localFile.exists() && localFile.length() > 0) {
                    Log.i(TAG, "Cache hit: ${localFile.absolutePath}")
                    localFilePath = localFile.absolutePath
                    return@withContext
                }

                Log.i(TAG, "Downloading: $streamUrl")
                // Pobieranie do .part + rename po sukcesie — przerwane pobieranie
                // (BACK w trakcie) nie zostawia uciętego pliku udającego cache hit
                val partFile = File(cacheDir, "$fileName.part")
                partFile.delete()
                val conn = URL(streamUrl).openConnection()
                val totalSize = conn.contentLength
                val input = conn.getInputStream()
                val output = partFile.outputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) downloadProgress = ((totalRead * 100) / totalSize).toInt()
                }
                output.close()
                input.close()
                if (totalSize > 0 && partFile.length() != totalSize.toLong()) {
                    partFile.delete()
                    throw java.io.IOException("Incomplete download: ${partFile.length()}/$totalSize B")
                }
                partFile.renameTo(localFile)
                Log.i(TAG, "Downloaded: ${localFile.absolutePath} (${localFile.length()} bytes)")
                localFilePath = localFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}")
                localFilePath = streamUrl
            }
        }
    }

    // Step 2: Create ExoPlayer
    DisposableEffect(localFilePath) {
        val path = localFilePath ?: return@DisposableEffect onDispose {}

        val exo = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setSeekParameters(SeekParameters.EXACT)

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        if (duration > 0 && duration != Long.MIN_VALUE + 1) {
                            durationMs = duration
                        }
                    }
                    Log.i(TAG, "State=${playbackState} dur=${duration}ms seekable=${isCurrentMediaItemSeekable}")
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    Log.i(TAG, "isPlaying changed → $playing")
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Error: ${error.errorCodeName} - ${error.message}")
                }
            })
        }

        val uri = if (path.startsWith("http")) Uri.parse(path) else Uri.fromFile(File(path))
        Log.i(TAG, "Creating player for: $uri (playWhenReady=true)")
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        player = exo
        isPlaying = true

        onDispose {
            exo.stop()
            exo.release()
            player = null
            frameCaptureManager.release()
        }
    }

    // Position polling
    LaunchedEffect(player) {
        while (true) {
            delay(500L)
            player?.let { p ->
                if (!isSeeking) currentPositionMs = p.currentPosition
                if (p.duration > 0 && p.duration != Long.MIN_VALUE + 1) durationMs = p.duration
                isPlaying = p.isPlaying
            }
        }
    }

    // Periodic frame capture during normal playback
    // (pomijany przy fullFilmstrip — pełna ekstrakcja wypełnia ring buffer,
    //  PixelCopy wypychałby klatki z początku pliku)
    LaunchedEffect(player, isSeeking) {
        if (fullFilmstrip) return@LaunchedEffect
        val p = player ?: return@LaunchedEffect
        while (true) {
            delay(CAPTURE_INTERVAL_MS)
            if (!isSeeking && p.isPlaying && playerView != null) {
                frameCaptureManager.captureFrame(playerView, p.currentPosition)
            }
        }
    }

    // Background keyframe extraction once duration is known
    LaunchedEffect(durationMs, localFilePath) {
        val path = localFilePath ?: return@LaunchedEffect
        if (durationMs <= 0) return@LaunchedEffect
        // Use local file path for extraction (faster than URL)
        val extractPath = if (path.startsWith("/")) path else streamUrl
        // fullFilmstrip: klatka co ~5s na całej długości (limit ring buffera);
        // default: 30 klatek jak dotychczas (Kino Play)
        val count = if (fullFilmstrip) {
            (durationMs / 5_000L).toInt().coerceIn(1, FrameCaptureManager.MAX_FRAMES - 2)
        } else 30
        frameCaptureManager.extractKeyFrames(extractPath, durationMs, count = count)
        Log.i(TAG, "Keyframe extraction started for ${durationMs}ms")
    }

    // Auto-hide seeking overlay after 5s of inactivity
    LaunchedEffect(lastSeekActionTime) {
        if (!isSeeking || lastSeekActionTime == 0L) return@LaunchedEffect
        delay(5000L)
        if (isSeeking) {
            // Auto-hide = cancel, resume from original position
            player?.seekTo(seekStartPositionMs)
            player?.play()
            isPlaying = true
            isSeeking = false
            Log.i(TAG, "Auto-hide: back to ${seekStartPositionMs}ms")
        }
    }

    // Key handler
    val handleKeyDown: (Int) -> Boolean = handleKeyDown@{ keyCode ->
        val p = player ?: return@handleKeyDown false

        when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isSeeking) {
                    // Enter seek mode: pause video, it stays frozen at current position
                    p.pause()
                    isPlaying = false
                    isSeeking = true
                    seekStartPositionMs = p.currentPosition
                    seekPositionMs = p.currentPosition
                }
                // Move thumbnail cursor, don't move the video
                val step = getSeekStep()
                seekPositionMs = if (durationMs > 0) {
                    (seekPositionMs + step).coerceAtMost(durationMs)
                } else {
                    seekPositionMs + step
                }
                lastSeekActionTime = System.currentTimeMillis()
                updateFilmstrip(seekPositionMs)
                Log.i(TAG, "BROWSE RIGHT → ${seekPositionMs}ms / ${durationMs}ms")
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isSeeking) {
                    p.pause()
                    isPlaying = false
                    isSeeking = true
                    seekStartPositionMs = p.currentPosition
                    seekPositionMs = p.currentPosition
                }
                val step = getSeekStep()
                seekPositionMs = (seekPositionMs - step).coerceAtLeast(0L)
                lastSeekActionTime = System.currentTimeMillis()
                updateFilmstrip(seekPositionMs)
                Log.i(TAG, "BROWSE LEFT → ${seekPositionMs}ms / ${durationMs}ms")
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                if (isSeeking) {
                    // OK = jump to selected thumbnail position and resume
                    p.seekTo(seekPositionMs)
                    p.play()
                    isPlaying = true
                    isSeeking = false
                    rapidPressCount = 0
                    Log.i(TAG, "CONFIRM seek → ${seekPositionMs}ms")
                } else {
                    if (p.isPlaying) {
                        p.pause()
                        isPlaying = false
                    } else {
                        p.play()
                        isPlaying = true
                    }
                }
                true
            }

            AndroidKeyEvent.KEYCODE_BACK -> {
                if (isSeeking) {
                    // BACK = cancel, resume from original position (where seeking started)
                    p.seekTo(seekStartPositionMs)
                    p.play()
                    isPlaying = true
                    isSeeking = false
                    rapidPressCount = 0
                    Log.i(TAG, "CANCEL seek, back to ${seekStartPositionMs}ms")
                } else {
                    onBackPressed()
                }
                true
            }

            else -> false
        }
    }

    // UI
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(200)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                handleKeyDown(event.nativeKeyEvent.keyCode)
            }
            .focusable()
    ) {
        // Video — AndroidView always present, player assigned in update
        AndroidView(
            factory = { ctx ->
                object : PlayerView(ctx) {
                    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                        if (event.action == AndroidKeyEvent.ACTION_DOWN) {
                            val handled = handleKeyDown(event.keyCode)
                            if (handled) return true
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }.apply {
                    useController = false
                    isFocusable = true
                    isFocusableInTouchMode = false
                    playerView = this
                }
            },
            update = { view ->
                if (view.player !== player) {
                    view.player = player
                    Log.i(TAG, "PlayerView.player updated: ${player != null}")
                }
                playerView = view
            },
            modifier = Modifier.fillMaxSize()
        )

        // Download progress
        if (localFilePath == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Downloading... ${downloadProgress}%", style = TextStyle(fontSize = 24.sp, color = Color.White))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.width(sx(400)).height(sy(8)).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxWidth(downloadProgress / 100f).fillMaxHeight().background(Color(0xFF5AECD3)))
                }
            }
        }

        // Filmstrip overlay during seeking
        TimeshiftOverlay(
            isVisible = isSeeking,
            offsetMs = seekPositionMs,
            frames = filmstripFrames,
            isAtLiveEdge = false,
            maxBufferMs = durationMs,
            totalOffsetMs = seekPositionMs,
            sx = sx,
            sy = sy,
            isVodMode = true,
            vodDurationMs = durationMs
        )
    }
}
