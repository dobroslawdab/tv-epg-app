package com.uxellence.tv.v3.demolive

import java.util.Calendar

/**
 * DEMO BARKER CHANNEL SCHEDULE — czysta logika kanału testowego.
 *
 * Barker channel: ramówka zakotwiczona w ZEGARZE ŚCIENNYM — start dziś o 9:00,
 * materiały A (Sintel ~14:48) i B (Big Buck Bunny ~9:56) grane naprzemiennie
 * w pętli, bloki ramówki rozstawione DOKŁADNIE co długość materiałów (do ~20:00).
 * Wejście o dowolnej godzinie (np. 12:33) trafia w zaplanowany przedział,
 * a czasy bloków zgadzają się z czasami prawdziwego EPG innych kanałów.
 *
 * Oś wirtualna: virtualMs = wallClock - barkerStart(9:00). Live edge = virtualNow().
 */
object DemoChannelSchedule {
    const val URL_A = "https://archive.org/download/Sintel/sintel-2048-stereo_512kb.mp4"
    const val URL_B = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

    // Nominalne czasy trwania — doprecyzowywane z Timeline ExoPlayera po STATE_READY
    @Volatile var durAMs: Long = 888_000L
    @Volatile var durBMs: Long = 596_000L
    val materialCycleMs: Long get() = durAMs + durBMs

    const val BARKER_START_HOUR = 9   // start anteny: dziś 9:00
    const val BARKER_END_HOUR = 20    // koniec ramówki (informacyjnie)

    /** Start barker channel: dziś 9:00 lokalnie (wczoraj 9:00 jeśli teraz przed 9:00). */
    fun barkerStartWallMs(nowWallMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowWallMs
            set(Calendar.HOUR_OF_DAY, BARKER_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis > nowWallMs) cal.add(Calendar.DAY_OF_YEAR, -1)
        return cal.timeInMillis
    }

    data class MaterialPos(val mediaItemIndex: Int, val positionMs: Long, val cycle: Long)

    data class EpgBlock(
        val title: String,
        val startVirtualMs: Long,
        val endVirtualMs: Long,
        val genre: String,
        val year: String,
        val country: String,
        val age: String,
        val description: String,
        val coverUrl: String? = null   // okładka programu (EPG/detal); null = klatka z materiału
    )

    private val BLOCK_A_META = EpgBlock(
        title = "Sintel",
        startVirtualMs = 0L, endVirtualMs = 0L,
        genre = "fantasy", year = "2010 r.", country = "Holandia", age = "12 lat",
        coverUrl = "https://m.media-amazon.com/images/S/pv-target-images/6faeb35e463ad90c72c97d47d06367ec7bc4d9d0be63659d6c9fb18777cf3b12.png",
        description = "Samotna wojowniczka Sintel przemierza świat w poszukiwaniu Scales — " +
            "małego smoka, którego niegdyś uratowała i wychowała, a który został jej brutalnie " +
            "odebrany. Wędrówka przez lodowe pustkowia i mroczne jaskinie wystawi jej " +
            "determinację na ostateczną próbę. Nagradzany film studia Blender."
    )

    private val BLOCK_B_META = EpgBlock(
        title = "Big Buck Bunny",
        startVirtualMs = 0L, endVirtualMs = 0L,
        genre = "animacja", year = "2008 r.", country = "Holandia", age = "7 lat",
        coverUrl = "https://m.media-amazon.com/images/M/MV5BMjMzNDM1ZmEtYzRjOC00Nzg5LWFlZTAtMTA1M2I0NDc2Njg3XkEyXkFqcGc@._V1_.jpg",
        description = "Ogromny, dobroduszny królik budzi się pewnego ranka, by cieszyć się " +
            "urokami leśnej polany. Sielankę przerywa trójka złośliwych gryzoni, która dla zabawy " +
            "dręczy mniejsze zwierzęta. Gdy ich ofiarą padają ukochane motyle królika, " +
            "łagodny olbrzym postanawia dać łobuzom nauczkę."
    )

    /** Mapowanie pozycji wirtualnej → (indeks MediaItem, pozycja w pliku, numer cyklu). */
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

    /**
     * Blok ramówki obejmujący pozycję wirtualną. Barker channel: blok == materiał,
     * więc granice bloków pokrywają się z przejściami plików.
     */
    fun epgBlockAt(virtualMs: Long): EpgBlock {
        val v = virtualMs.coerceAtLeast(0L)
        val cycleStart = (v / materialCycleMs) * materialCycleMs
        val inCycle = v % materialCycleMs
        return if (inCycle < durAMs) {
            BLOCK_A_META.copy(
                startVirtualMs = cycleStart,
                endVirtualMs = cycleStart + durAMs
            )
        } else {
            BLOCK_B_META.copy(
                startVirtualMs = cycleStart + durAMs,
                endVirtualMs = cycleStart + materialCycleMs
            )
        }
    }

    /**
     * Lista bloków ramówki wokół danej pozycji: [before] bloków wstecz,
     * blok bieżący i [after] bloków w przód (rail warstwy EPG).
     */
    fun blocksAround(virtualMs: Long, before: Int, after: Int): List<EpgBlock> {
        val result = ArrayDeque<EpgBlock>()
        var block = epgBlockAt(virtualMs)
        result.add(block)
        repeat(before) {
            val prevStart = result.first().startVirtualMs - 1
            if (prevStart < 0) return@repeat
            result.addFirst(epgBlockAt(prevStart))
        }
        repeat(after) {
            result.add(epgBlockAt(result.last().endVirtualMs + 1))
        }
        return result.toList()
    }

    /** Pozycje wirtualne granic bloków ramówki w zakresie [from, to]. */
    fun blockBoundariesIn(fromVirtualMs: Long, toVirtualMs: Long): List<Long> {
        if (toVirtualMs < fromVirtualMs) return emptyList()
        val result = mutableListOf<Long>()
        var k = (fromVirtualMs / materialCycleMs) - 1
        while (k * materialCycleMs <= toVirtualMs) {
            val base = k * materialCycleMs
            for (boundary in listOf(base, base + durAMs)) {
                if (boundary in fromVirtualMs..toVirtualMs) result.add(boundary)
            }
            k++
        }
        return result.sorted()
    }
}
