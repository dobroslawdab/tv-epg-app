package com.uxellence.tv.v3.demolive

import java.util.Calendar

/**
 * BARKER SCHEDULE — czysta logika symulowanego kanału live (uogólnienie dawnego
 * DemoChannelSchedule z 2 materiałów na N, wiele instancji = wiele kanałów).
 *
 * Barker channel: ramówka zakotwiczona w ZEGARZE ŚCIENNYM — start dziś o 9:00,
 * materiały grane po kolei w pętli, bloki ramówki rozstawione DOKŁADNIE co długość
 * materiałów. Wejście o dowolnej godzinie trafia w zaplanowany przedział, a czasy
 * bloków zgadzają się z czasami prawdziwego EPG innych kanałów.
 *
 * Oś wirtualna: virtualMs = wallClock - barkerStart(9:00). Live edge = virtualNow().
 * Oś jest WSPÓLNA dla wszystkich barkerów (ten sam start 9:00) — kanały różnią się
 * wyłącznie materiałami/playbackiem.
 */
class BarkerSchedule(val items: List<BarkerItem>) {

    /** Materiał barkera: źródło + metadane bloku ramówki. */
    data class BarkerItem(
        val url: String,
        val title: String,
        val genre: String,
        val year: String,
        val country: String,
        val age: String,
        val description: String,
        val coverUrl: String? = null,     // okładka programu; null = klatka z materiału
        val nominalDurMs: Long            // doprecyzowywane z Timeline po STATE_READY
    )

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
        val coverUrl: String? = null
    )

    companion object {
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
    }

    /** Czasy trwania materiałów — nominalne, nadpisywane z Timeline ExoPlayera. */
    val durMs = LongArray(items.size) { items[it].nominalDurMs }

    val materialCycleMs: Long get() = durMs.sum()

    private fun prefixMs(index: Int): Long {
        var acc = 0L
        for (i in 0 until index) acc += durMs[i]
        return acc
    }

    /** Mapowanie pozycji wirtualnej → (indeks MediaItem, pozycja w pliku, numer cyklu). */
    fun materialPositionFor(virtualMs: Long): MaterialPos {
        val v = virtualMs.coerceAtLeast(0L)
        val cycle = v / materialCycleMs
        var inCycle = v % materialCycleMs
        for (i in durMs.indices) {
            if (inCycle < durMs[i]) return MaterialPos(i, inCycle, cycle)
            inCycle -= durMs[i]
        }
        return MaterialPos(durMs.lastIndex, durMs.last() - 1, cycle)  // krawędź numeryczna
    }

    /** Mapowanie odwrotne: (cykl, indeks MediaItem, pozycja w pliku) → pozycja wirtualna. */
    fun virtualFor(cycle: Long, mediaItemIndex: Int, positionMs: Long): Long =
        cycle * materialCycleMs + prefixMs(mediaItemIndex) + positionMs

    /**
     * Blok ramówki obejmujący pozycję wirtualną. Barker channel: blok == materiał,
     * więc granice bloków pokrywają się z przejściami plików.
     */
    fun epgBlockAt(virtualMs: Long): EpgBlock {
        val v = virtualMs.coerceAtLeast(0L)
        val cycleStart = (v / materialCycleMs) * materialCycleMs
        val mp = materialPositionFor(v)
        val item = items[mp.mediaItemIndex]
        val blockStart = cycleStart + prefixMs(mp.mediaItemIndex)
        return EpgBlock(
            title = item.title,
            startVirtualMs = blockStart,
            endVirtualMs = blockStart + durMs[mp.mediaItemIndex],
            genre = item.genre,
            year = item.year,
            country = item.country,
            age = item.age,
            description = item.description,
            coverUrl = item.coverUrl
        )
    }

    /**
     * Lista bloków ramówki wokół danej pozycji: [before] bloków wstecz,
     * blok bieżący i [after] bloków w przód (rail warstwy EPG).
     */
    fun blocksAround(virtualMs: Long, before: Int, after: Int): List<EpgBlock> {
        val result = ArrayDeque<EpgBlock>()
        result.add(epgBlockAt(virtualMs))
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
            var acc = base
            for (i in durMs.indices) {
                if (acc in fromVirtualMs..toVirtualMs) result.add(acc)
                acc += durMs[i]
            }
            k++
        }
        return result.sorted()
    }
}
