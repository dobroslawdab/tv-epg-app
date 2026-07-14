package com.uxellence.tv.v3.epg

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.util.Log
import android.view.SurfaceView
import com.google.android.exoplayer2.ui.PlayerView
import kotlin.math.abs

/**
 * FRAME CAPTURE MANAGER
 *
 * Captures frames from PlayerView every 5 seconds and stores them in a ring buffer.
 * Used for thumbnail previews during timeshift seeking.
 *
 * Implementation: PixelCopy API (API 24+, Android TV minimum)
 * Ring buffer: 60 frames (5 minutes at 5s intervals) = ~5.5 MB
 * Thumbnail size: 208x116 px (matches EPG cover dimensions)
 */
class FrameCaptureManager {
    private val TAG = "FrameCapture"

    companion object {
        const val THUMB_WIDTH = 208
        const val THUMB_HEIGHT = 116
        const val MAX_FRAMES = 120  // 10 minutes at 5s intervals
        const val CAPTURE_INTERVAL_MS = 5000L
    }

    data class TimestampedFrame(
        val bitmap: Bitmap,
        val positionMs: Long
    )

    private val ringBuffer = ArrayDeque<TimestampedFrame>(MAX_FRAMES)
    private val handlerThread = HandlerThread("FrameCapture").also { it.start() }
    private val handler = Handler(handlerThread.looper)

    // Trwały cache dyskowy: indeks pozycja→plik (wszystkie f_<pos>.jpg — gęstość
    // NIE jest ograniczona RAM-em) + mały LRU zdekodowanych bitmap. RAM ring
    // trzyma przerzedzony podzbiór; dokładne kadry doczytywane z dysku (małe
    // JPG, ~1-3 ms) — taśma może kroczyć gęściej niż mieści się w pamięci.
    private val diskIndex = java.util.TreeMap<Long, java.io.File>()
    private val diskLru = object : LinkedHashMap<Long, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>?) =
            size > 80   // bez recycle przy evict — bitmapa może być właśnie na ekranie
    }
    // Minimalny odstęp klatek trzymanych w RAM (dysk trzyma gęściej)
    private val ramSpacingMs = 30_000L

    /**
     * Capture a frame from the PlayerView at the current playback position.
     * Uses PixelCopy for SurfaceView-based capture (works with ExoPlayer's default SurfaceView).
     * Called periodically (every 5s) during normal playback — NOT during seek (to avoid stutter).
     */
    fun captureFrame(playerView: PlayerView?, positionMs: Long) {
        if (playerView == null) return

        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        if (surfaceView == null) {
            Log.d(TAG, "VideoSurfaceView is not SurfaceView, skipping capture")
            return
        }

        // Check surface is valid
        if (surfaceView.holder.surface == null || !surfaceView.holder.surface.isValid) {
            Log.d(TAG, "Surface not valid, skipping capture")
            return
        }

        val bitmap = Bitmap.createBitmap(THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.ARGB_8888)

        try {
            PixelCopy.request(surfaceView, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    synchronized(ringBuffer) {
                        if (ringBuffer.size >= MAX_FRAMES) {
                            ringBuffer.removeFirst().bitmap.recycle()
                        }
                        ringBuffer.addLast(TimestampedFrame(bitmap, positionMs))
                    }
                    Log.d(TAG, "Frame captured at ${positionMs}ms (buffer: ${ringBuffer.size}/$MAX_FRAMES)")
                } else {
                    bitmap.recycle()
                    Log.d(TAG, "PixelCopy failed with result: $result")
                }
            }, handler)
        } catch (e: Exception) {
            bitmap.recycle()
            Log.e(TAG, "PixelCopy error: ${e.message}")
        }
    }

    /**
     * Find the closest captured frame to the target position.
     * Returns null if no frames are available.
     */
    fun getClosestFrame(targetPositionMs: Long): Bitmap? {
        synchronized(ringBuffer) {
            val ramBest = ringBuffer.minByOrNull { abs(it.positionMs - targetPositionMs) }
            val ramDist = ramBest?.let { abs(it.positionMs - targetPositionMs) } ?: Long.MAX_VALUE
            if (ramDist <= 6_000L) return ramBest?.bitmap

            // Dysk ma gęstsze klatki niż RAM — doszukaj dokładniejszego kadru
            val floor = diskIndex.floorEntry(targetPositionMs)
            val ceil = diskIndex.ceilingEntry(targetPositionMs)
            val diskBest = listOfNotNull(floor, ceil)
                .minByOrNull { abs(it.key - targetPositionMs) }
            if (diskBest != null && abs(diskBest.key - targetPositionMs) < ramDist) {
                diskLru[diskBest.key]?.takeIf { !it.isRecycled }?.let { return it }
                val bmp = android.graphics.BitmapFactory.decodeFile(diskBest.value.absolutePath)
                if (bmp != null) {
                    diskLru[diskBest.key] = bmp
                    return bmp
                }
            }
            return ramBest?.bitmap
        }
    }

    /**
     * Get a list of frames around a center position, spaced by stepMs.
     * Returns list of Pair(offsetMs relative to center, Bitmap?) — null bitmap if no frame available.
     * Example: getFramesAround(pos, 10000, 2) → [-20s, -10s, 0, +10s, +20s]
     *
     * Uses ring buffer only — fast, no PixelCopy during seek.
     * Each slot gets the closest available frame (within tolerance of stepMs/2).
     */
    fun getFramesAround(
        centerPositionMs: Long,
        stepMs: Long,
        sideCount: Int
    ): List<Pair<Long, Bitmap?>> {
        synchronized(ringBuffer) {
            if (ringBuffer.isEmpty()) {
                return (-sideCount..sideCount).map { i -> Pair(i * stepMs, null) }
            }

            val tolerance = stepMs / 2

            // Each slot independently finds the closest frame — simple and correct
            return (-sideCount..sideCount).map { i ->
                val targetMs = centerPositionMs + (i * stepMs)

                val bestFrame = ringBuffer
                    .minByOrNull { abs(it.positionMs - targetMs) }

                val bitmap = if (bestFrame != null && abs(bestFrame.positionMs - targetMs) <= tolerance) {
                    bestFrame.bitmap
                } else {
                    null
                }

                Pair(i * stepMs, bitmap)
            }
        }
    }

    /**
     * Check if there are any captured frames available.
     */
    fun hasFrames(): Boolean = synchronized(ringBuffer) { ringBuffer.isNotEmpty() }

    /**
     * Get the time range of captured frames.
     * Returns Pair(oldestMs, newestMs) or null if empty.
     */
    fun getTimeRange(): Pair<Long, Long>? {
        synchronized(ringBuffer) {
            if (ringBuffer.isEmpty()) return null
            return Pair(ringBuffer.first().positionMs, ringBuffer.last().positionMs)
        }
    }

    /**
     * Clear all frames and release bitmaps.
     * Call when switching channels or releasing player.
     */
    fun clear() {
        synchronized(ringBuffer) {
            ringBuffer.forEach { it.bitmap.recycle() }
            ringBuffer.clear()
        }
        Log.d(TAG, "Frame buffer cleared")
    }

    /**
     * Extract frames at key positions from a video URL using MediaMetadataRetriever.
     * Runs on background HandlerThread — does NOT affect ExoPlayer playback.
     * Call once when video duration is known to pre-populate filmstrip.
     */
    fun extractKeyFrames(
        videoUrl: String,
        durationMs: Long,
        count: Int = 10,
        // Trwały cache klatek (f_<positionMs>.jpg): istniejące wczytywane od razu
        // (taśma dokładna od startu sesji), nowe dopisywane po ekstrakcji
        diskCacheDir: java.io.File? = null
    ) {
        handler.post {
            // 1) Zaindeksuj trwały cache (pozycja→plik; WSZYSTKIE klatki) i wczytaj
            //    do RAM przerzedzony podzbiór (co ramSpacingMs) — dekodowanie małych
            //    JPG jest o rzędy wielkości szybsze niż retriever na dużym mp4
            if (diskCacheDir != null && diskCacheDir.isDirectory) {
                var loaded = 0
                diskCacheDir.listFiles { f -> f.name.startsWith("f_") && f.name.endsWith(".jpg") }
                    ?.sortedBy { it.name }
                    ?.forEach { f ->
                        val pos = f.name.removePrefix("f_").removeSuffix(".jpg").toLongOrNull()
                            ?: return@forEach
                        synchronized(ringBuffer) {
                            diskIndex[pos] = f
                            val ramHasNearby =
                                ringBuffer.any { abs(it.positionMs - pos) < ramSpacingMs }
                            if (!ramHasNearby && ringBuffer.size < MAX_FRAMES) {
                                android.graphics.BitmapFactory.decodeFile(f.absolutePath)?.let {
                                    ringBuffer.addLast(TimestampedFrame(it, pos))
                                    loaded++
                                }
                            }
                        }
                    }
                if (diskIndex.isNotEmpty()) {
                    Log.d(TAG, "Disk cache: ${diskIndex.size} indexed, $loaded in RAM (${diskCacheDir.name})")
                }
            }

            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                    retriever.setDataSource(videoUrl, HashMap<String, String>())
                } else {
                    retriever.setDataSource(videoUrl)
                }

                val step = durationMs / (count + 1)
                for (i in 1..count) {
                    val positionMs = step * i
                    val positionUs = positionMs * 1000  // MediaMetadataRetriever uses microseconds

                    // Pozycja pokryta (RAM lub dysk) → pomiń kosztowną ekstrakcję
                    val covered = synchronized(ringBuffer) {
                        val onDisk = listOfNotNull(
                            diskIndex.floorKey(positionMs), diskIndex.ceilingKey(positionMs)
                        ).any { abs(it - positionMs) < step / 2 }
                        onDisk || (diskCacheDir == null &&
                            ringBuffer.any { abs(it.positionMs - positionMs) < step / 2 })
                    }
                    if (covered) continue

                    val frame = retriever.getFrameAtTime(
                        positionUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )

                    if (frame != null) {
                        val scaled = Bitmap.createScaledBitmap(frame, THUMB_WIDTH, THUMB_HEIGHT, true)
                        if (scaled !== frame) frame.recycle()

                        // 2) Trwały cache: KAŻDA klatka na dysk (gęstość bez limitu RAM)
                        if (diskCacheDir != null) {
                            runCatching {
                                diskCacheDir.mkdirs()
                                val out = java.io.File(diskCacheDir, "f_$positionMs.jpg")
                                java.io.FileOutputStream(out).use { fos ->
                                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                                }
                                synchronized(ringBuffer) { diskIndex[positionMs] = out }
                            }
                        }

                        synchronized(ringBuffer) {
                            // RAM: przerzedzony podzbiór (dokładne kadry doczyta dysk)
                            val spacing = if (diskCacheDir != null) ramSpacingMs else step / 2
                            val hasNearby =
                                ringBuffer.any { abs(it.positionMs - positionMs) < spacing }
                            if (!hasNearby) {
                                if (ringBuffer.size >= MAX_FRAMES) {
                                    ringBuffer.removeFirst().bitmap.recycle()
                                }
                                ringBuffer.addLast(TimestampedFrame(scaled, positionMs))
                            } else if (diskCacheDir == null) {
                                scaled.recycle()
                            }
                        }
                        Log.d(TAG, "Extracted keyframe at ${positionMs}ms (${i}/$count)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaMetadataRetriever error: ${e.message}")
            } finally {
                try { retriever?.release() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Release all resources. Call on disposal.
     */
    fun release() {
        clear()
        handlerThread.quitSafely()
        Log.d(TAG, "FrameCaptureManager released")
    }
}
