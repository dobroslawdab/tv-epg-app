package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import com.uxellence.tv.v3.epg.FrameCaptureManager

/**
 * DEMO FILMSTRIP PROVIDER — miniaturki na osi wirtualnej kanału demo.
 *
 * Dwie instancje FrameCaptureManager (po jednej na materiał) — pozycje klatek
 * pozostają w naturalnej osi pliku. Slot filmstripa mapowany jest z osi wirtualnej
 * na (materiał, pozycja w pliku) i pobiera najbliższą klatkę z właściwej instancji.
 * PixelCopy nie jest używany — pełna ekstrakcja z lokalnych plików wystarcza.
 */
class DemoFilmstripProvider {

    private val managerA = FrameCaptureManager()
    private val managerB = FrameCaptureManager()
    private var started = false

    /** Ekstrakcja klatek z obu plików — wołać raz, po przygotowaniu playera. */
    fun startExtraction(pathA: String, pathB: String) {
        if (started) return
        started = true
        val countA = (DemoChannelSchedule.durAMs / 6_500L).toInt()
            .coerceIn(1, FrameCaptureManager.MAX_FRAMES - 2)
        val countB = (DemoChannelSchedule.durBMs / 6_500L).toInt()
            .coerceIn(1, FrameCaptureManager.MAX_FRAMES - 2)
        managerA.extractKeyFrames(pathA, DemoChannelSchedule.durAMs, count = countA)
        managerB.extractKeyFrames(pathB, DemoChannelSchedule.durBMs, count = countB)
    }

    /**
     * 7 slotów filmstripa wokół pozycji wirtualnej: [(offset względem centrum, Bitmap?)].
     * Sloty poza DVR window (przed 0 lub za live edge) dostają null.
     */
    fun framesAround(
        centerVirtualMs: Long,
        liveEdgeVirtualMs: Long,
        stepMs: Long = 10_000L,
        sideCount: Int = 3,
        dvrStartVirtualMs: Long = 0L
    ): List<Pair<Long, Bitmap?>> {
        return (-sideCount..sideCount).map { i ->
            val offset = i * stepMs
            val v = centerVirtualMs + offset
            val bitmap = if (v < dvrStartVirtualMs || v > liveEdgeVirtualMs) {
                null
            } else {
                val mp = DemoChannelSchedule.materialPositionFor(v)
                val manager = if (mp.mediaItemIndex == 0) managerA else managerB
                manager.getClosestFrame(mp.positionMs)
            }
            offset to bitmap
        }
    }

    /**
     * URI pliku PNG z klatką dla początku bloku ramówki (cover karty EPG —
     * EpgDayItem przyjmuje tylko iconUrl). Plik powstaje raz, w cache demo.
     * Zwraca null gdy blok poza DVR albo klatki jeszcze nie wyekstrahowane.
     */
    fun thumbUriFor(blockStartVirtualMs: Long, liveEdgeVirtualMs: Long, cacheDir: java.io.File): String? {
        // Barker channel: materiał zapętlony, więc cover znamy też dla bloków
        // przyszłych — pokazuj klatkę dla każdego bloku ramówki
        if (blockStartVirtualMs < 0) return null
        val mp = DemoChannelSchedule.materialPositionFor(blockStartVirtualMs)
        val manager = if (mp.mediaItemIndex == 0) managerA else managerB
        val bitmap = manager.getClosestFrame(mp.positionMs) ?: return null
        val dir = java.io.File(cacheDir, "demo_live")
        dir.mkdirs()
        val file = java.io.File(dir, "thumb_${blockStartVirtualMs}.png")
        if (!file.exists() || file.length() == 0L) {
            runCatching {
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                }
            }.onFailure { return null }
        }
        return file.toURI().toString()
    }

    fun release() {
        managerA.release()
        managerB.release()
    }
}
