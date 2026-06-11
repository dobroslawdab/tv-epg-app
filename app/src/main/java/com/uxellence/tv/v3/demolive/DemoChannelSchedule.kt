package com.uxellence.tv.v3.demolive

/**
 * DEMO CHANNEL SCHEDULE — czysta logika symulowanego kanału live.
 *
 * "Antena" = dwa lokalne pliki MP4 grane back-to-back w pętli:
 *   A = Sintel (~14:48), B = Big Buck Bunny (~9:56) → MATERIAL_CYCLE ~24:44
 *   (gtv-videos-bucket przestał być publiczny — Sintel z archive.org,
 *    BBB z exoplayer-test-media-0; oba to lekkie, sprawdzone MP4 h264)
 *
 * Ramówka (EPG) celowo NIE pokrywa się z anteną:
 *   blok 1 "Sintel" planowo 12:00, blok 2 "Big Buck Bunny" planowo 14:00
 *   → EPG_CYCLE 26:00 ≠ MATERIAL_CYCLE 24:44
 *
 * Symulowane przypadki rozjazdu (kluczowy cel demo):
 *   - materiał A kończy się ~2:48 ZA kropką końca bloku 1,
 *   - materiał B kończy się ~1:16 PRZED kropką końca bloku 2.
 *
 * Oś wirtualna: virtualMs = wallClock - antennaStart. Live edge = virtualNow().
 */
object DemoChannelSchedule {
    const val URL_A = "https://archive.org/download/Sintel/sintel-2048-stereo_512kb.mp4"
    const val URL_B = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

    // Nominalne czasy trwania — doprecyzowywane z Timeline ExoPlayera po STATE_READY
    @Volatile var durAMs: Long = 888_000L
    @Volatile var durBMs: Long = 596_000L
    val materialCycleMs: Long get() = durAMs + durBMs

    const val BLOCK_1_PLANNED_MS = 720_000L   // 12:00
    const val BLOCK_2_PLANNED_MS = 840_000L   // 14:00
    val epgCycleMs: Long get() = BLOCK_1_PLANNED_MS + BLOCK_2_PLANNED_MS

    data class MaterialPos(val mediaItemIndex: Int, val positionMs: Long, val cycle: Long)

    data class EpgBlock(
        val title: String,
        val startVirtualMs: Long,
        val endVirtualMs: Long,
        val genre: String,
        val year: String,
        val country: String,
        val age: String,
        val description: String
    )

    private val BLOCK_A_META = EpgBlock(
        title = "Sintel",
        startVirtualMs = 0L, endVirtualMs = 0L,
        genre = "fantasy", year = "2010 r.", country = "Holandia", age = "12 lat",
        description = "Samotna wojowniczka Sintel przemierza świat w poszukiwaniu Scales — " +
            "małego smoka, którego niegdyś uratowała i wychowała, a który został jej brutalnie " +
            "odebrany. Wędrówka przez lodowe pustkowia i mroczne jaskinie wystawi jej " +
            "determinację na ostateczną próbę. Nagradzany film studia Blender."
    )

    private val BLOCK_B_META = EpgBlock(
        title = "Big Buck Bunny",
        startVirtualMs = 0L, endVirtualMs = 0L,
        genre = "animacja", year = "2008 r.", country = "Holandia", age = "7 lat",
        description = "Ogromny, dobroduszny królik budzi się pewnego ranka, by cieszyć się " +
            "urokami leśnej polany. Sielankę przerywa trójka złośliwych gryzoni, która dla zabawy " +
            "dręczy mniejsze zwierzęta. Gdy ich ofiarą padają ukochane motyle królika, " +
            "łagodny olbrzym postanawia dać łobuzom nauczkę."
    )

    /** Mapowanie pozycji wirtualnej → (indeks MediaItem, pozycja w pliku, numer cyklu materiałów). */
    fun materialPositionFor(virtualMs: Long): MaterialPos {
        val v = virtualMs.coerceAtLeast(0L)
        val cycle = v / materialCycleMs
        val inCycle = v % materialCycleMs
        return if (inCycle < durAMs) {
            MaterialPos(mediaItemIndex = 0, positionMs = inCycle, cycle = cycle)
        } else {
            MaterialPos(mediaItemIndex = 1, positionMs = inCycle - durAMs, cycle = cycle)
        }
    }

    /** Mapowanie odwrotne: (cykl, indeks MediaItem, pozycja w pliku) → pozycja wirtualna. */
    fun virtualFor(cycle: Long, mediaItemIndex: Int, positionMs: Long): Long =
        cycle * materialCycleMs + (if (mediaItemIndex == 0) positionMs else durAMs + positionMs)

    /** Blok ramówki obejmujący daną pozycję wirtualną (z wypełnionymi czasami start/end). */
    fun epgBlockAt(virtualMs: Long): EpgBlock {
        val v = virtualMs.coerceAtLeast(0L)
        val cycleStart = (v / epgCycleMs) * epgCycleMs
        val inCycle = v % epgCycleMs
        return if (inCycle < BLOCK_1_PLANNED_MS) {
            BLOCK_A_META.copy(
                startVirtualMs = cycleStart,
                endVirtualMs = cycleStart + BLOCK_1_PLANNED_MS
            )
        } else {
            BLOCK_B_META.copy(
                startVirtualMs = cycleStart + BLOCK_1_PLANNED_MS,
                endVirtualMs = cycleStart + epgCycleMs
            )
        }
    }

    /** Pozycje wirtualne kropek granic bloków ramówki w zakresie [from, to]. */
    fun blockBoundariesIn(fromVirtualMs: Long, toVirtualMs: Long): List<Long> {
        if (toVirtualMs < fromVirtualMs) return emptyList()
        val result = mutableListOf<Long>()
        var k = (fromVirtualMs / epgCycleMs) - 1
        while (k * epgCycleMs <= toVirtualMs) {
            val base = k * epgCycleMs
            for (boundary in listOf(base, base + BLOCK_1_PLANNED_MS)) {
                if (boundary in fromVirtualMs..toVirtualMs) result.add(boundary)
            }
            k++
        }
        return result.sorted()
    }
}
