package com.uxellence.tv.v3.demolive

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.Timeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

/**
 * DEMO CHANNEL PLAYER CONTROLLER — player "anteny" symulowanego kanału live.
 * Jedna instancja = jeden kanał barker (schedule z N materiałami).
 *
 * - Playlista ExoPlayer (N plików) + REPEAT_MODE_ALL: przejście do następnego materiału
 *   następuje przy FAKTYCZNYM końcu pliku (auto transition), nie przy kropce ramówki.
 * - Oś wirtualna zakotwiczona w zegarze ściennym (wspólny start 9:00 dla wszystkich
 *   barkerów — patrz BarkerSchedule).
 * - trackedCycle: numer pętli materiałów — inkrementowany WYŁĄCZNIE przy
 *   automatycznym przejściu ostatni→pierwszy (REASON_AUTO na index 0); przy seeku jawnie.
 */
class DemoChannelPlayerController(
    private val context: Context,
    val schedule: BarkerSchedule
) {

    companion object {
        private const val TAG = "DemoLive"
        private const val LIVE_EDGE_TOLERANCE_MS = 5_000L
        // Okno DVR: godzina wstecz od live edge — obejmuje poprzedni materiał,
        // a pasek przewijania pozostaje czytelny (barker gra od 9:00)
        private const val DVR_WINDOW_MS = 3_600_000L
    }

    /** Barker channel: antena wystartowała dziś o 9:00 (zegar ścienny). */
    val antennaStartWallMs: Long = BarkerSchedule.barkerStartWallMs()

    var player: ExoPlayer? = null
        private set

    @Volatile private var trackedCycle = 0L

    /**
     * Pobiera plik do cache (cacheDir/demo_live/). Pobieranie idzie do pliku .part,
     * rename na finalny dopiero po sukcesie — przerwane pobieranie nie udaje cache hit.
     */
    suspend fun downloadToCache(url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "demo_live")
            dir.mkdirs()
            val finalFile = File(dir, "demo_${url.hashCode()}.mp4")
            if (finalFile.exists() && finalFile.length() > 0) {
                Log.i(TAG, "Cache hit: ${finalFile.absolutePath}")
                return@withContext finalFile
            }

            val partFile = File(dir, "${finalFile.name}.part")
            partFile.delete()
            Log.i(TAG, "Downloading: $url")
            val conn = URL(url).openConnection()
            val totalSize = conn.contentLengthLong
            conn.getInputStream().use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var total = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        total += read
                        if (totalSize > 0) onProgress(((total * 100) / totalSize).toInt())
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (totalSize > 0 && partFile.length() != totalSize) {
                partFile.delete()
                throw IOException("Niekompletne pobieranie: ${partFile.length()}/$totalSize B")
            }
            if (!partFile.renameTo(finalFile)) {
                throw IOException("Nie udało się przenieść ${partFile.name} → ${finalFile.name}")
            }
            Log.i(TAG, "Downloaded: ${finalFile.absolutePath} (${finalFile.length()} B)")
            finalFile
        }

    /** Przygotuj player anteny z listą plików (kolejność = kolejność w schedule). */
    fun preparePlayer(files: List<File>) {
        require(files.size == schedule.items.size) {
            "files(${files.size}) != schedule.items(${schedule.items.size})"
        }
        val exo = ExoPlayer.Builder(context).build()
        exo.playWhenReady = true
        exo.setSeekParameters(SeekParameters.EXACT)
        exo.repeatMode = Player.REPEAT_MODE_ALL
        exo.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    exo.currentMediaItemIndex == 0
                ) {
                    trackedCycle++
                    Log.i(TAG, "onMediaItemTransition reason=AUTO → nowa pętla, trackedCycle=$trackedCycle")
                } else {
                    Log.i(TAG, "onMediaItemTransition reason=$reason index=${exo.currentMediaItemIndex}")
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (timeline.windowCount >= schedule.items.size) {
                    val window = Timeline.Window()
                    var allKnown = true
                    for (i in schedule.items.indices) {
                        val d = timeline.getWindow(i, window).durationMs
                        if (d > 0) schedule.durMs[i] = d else allKnown = false
                    }
                    if (allKnown) {
                        Log.i(TAG, "Timeline: dur=${schedule.durMs.joinToString()}ms")
                    }
                }
            }
        })
        exo.setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) })
        exo.prepare()
        player = exo
        seekToVirtual(virtualNow())
    }

    /** Live edge w osi wirtualnej — rośnie z zegarem ściennym. */
    fun virtualNow(): Long = System.currentTimeMillis() - antennaStartWallMs

    /** Początek okna DVR (60 min wstecz od live edge, nie wcześniej niż start anteny). */
    fun dvrStartMs(): Long = (virtualNow() - DVR_WINDOW_MS).coerceAtLeast(0L)

    /** Bieżąca pozycja odtwarzania w osi wirtualnej. */
    fun currentVirtualPositionMs(): Long {
        val p = player ?: return 0L
        return schedule.virtualFor(trackedCycle, p.currentMediaItemIndex, p.currentPosition)
    }

    /** Seek do pozycji wirtualnej (clamp do okna DVR); przełącza MediaItem jeśli trzeba. */
    fun seekToVirtual(targetVirtualMs: Long) {
        val p = player ?: return
        val clamped = targetVirtualMs.coerceIn(dvrStartMs(), virtualNow())
        val mp = schedule.materialPositionFor(clamped)
        // Nie seekuj na sam koniec okna (ochrona przed natychmiastową auto-transition
        // i IllegalSeekPositionException przy pozycji > duration okna)
        val windowDur = schedule.durMs[mp.mediaItemIndex]
        val safePos = mp.positionMs.coerceAtMost((windowDur - 500L).coerceAtLeast(0L))
        trackedCycle = mp.cycle
        p.seekTo(mp.mediaItemIndex, safePos)
        p.play()
        Log.i(TAG, "seekToVirtual($targetVirtualMs → $clamped) = item=${mp.mediaItemIndex} pos=${safePos}ms cycle=${mp.cycle}")
    }

    fun seekToLiveEdge() = seekToVirtual(virtualNow())

    fun isAtLiveEdge(): Boolean = virtualNow() - currentVirtualPositionMs() < LIVE_EDGE_TOLERANCE_MS

    fun release() {
        player?.stop()
        player?.release()
        player = null
    }
}
