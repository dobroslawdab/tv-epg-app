package com.uxellence.tv.v3.demolive

import android.graphics.Bitmap
import com.uxellence.tv.v3.epg.FrameCaptureManager

/**
 * DEMO FILMSTRIP PROVIDER — miniaturki na osi wirtualnej kanału barker.
 * Jedna instancja = jeden kanał (schedule z N materiałami).
 *
 * Po jednej instancji FrameCaptureManager na materiał — pozycje klatek pozostają
 * w naturalnej osi pliku. Slot filmstripa mapowany jest z osi wirtualnej na
 * (materiał, pozycja w pliku) i pobiera najbliższą klatkę z właściwej instancji.
 */
class DemoFilmstripProvider(private val schedule: BarkerSchedule) {

    private val managers = List(schedule.items.size) { FrameCaptureManager() }
    private var started = false

    /** Ekstrakcja klatek ze wszystkich plików — wołać raz, po przygotowaniu playera. */
    fun startExtraction(paths: List<String>) {
        if (started) return
        started = true
        paths.forEachIndexed { i, path ->
            // Pliki lokalne (kanał z nagrania / pobrany barker): TRWAŁY cache
            // klatek obok materiału (frames/<program>/f_<pos>.jpg) — kolejne
            // sesje mają taśmę dokładną od razu, bez ekstrakcji z mp4
            val diskDir = if (!path.startsWith("http")) {
                val f = java.io.File(path.removePrefix("file://"))
                java.io.File(f.parentFile, "frames/${f.nameWithoutExtension}")
            } else null
            // Z cache dyskowym gęstość NIE jest ograniczona RAM-em: klatka co
            // ~10 s (krok taśmy) — RAM trzyma przerzedzony podzbiór, dokładne
            // kadry doczytywane z dysku w getClosestFrame
            val count = if (diskDir != null) {
                (schedule.durMs[i] / 10_000L).toInt().coerceAtLeast(1)
            } else {
                (schedule.durMs[i] / 6_500L).toInt()
                    .coerceIn(1, FrameCaptureManager.MAX_FRAMES - 2)
            }
            managers[i].extractKeyFrames(
                path, schedule.durMs[i], count = count, diskCacheDir = diskDir
            )
        }
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
                val mp = schedule.materialPositionFor(v)
                managers[mp.mediaItemIndex].getClosestFrame(mp.positionMs)
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
        val mp = schedule.materialPositionFor(blockStartVirtualMs)
        val bitmap = managers[mp.mediaItemIndex].getClosestFrame(mp.positionMs) ?: return null
        val dir = java.io.File(cacheDir, "demo_live")
        dir.mkdirs()
        val file = java.io.File(dir, "thumb_${schedule.hashCode()}_${blockStartVirtualMs}.png")
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
        managers.forEach { it.release() }
    }
}
