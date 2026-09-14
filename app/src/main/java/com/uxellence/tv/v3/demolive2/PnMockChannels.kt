package com.uxellence.tv.v3.demolive2

import java.util.Calendar

/**
 * KANAŁY MOCKUPOWE do mini-EPG — żeby było po czym przewijać.
 *
 * Są to kanały **wyłącznie ramówkowe**: mają numer, nazwę i pełną siatkę
 * programów na dziś, ale NIE MA pod nimi materiału wideo, więc nie da się na
 * nie przełączyć (`tunable = false`). Grają tylko kanały z nagrań anteny.
 * OK na rzędzie mockowym zamyka mini-EPG i zostawia bieżący kanał.
 *
 * Ramówka jest deterministyczna: każdy kanał ma zapętloną listę slotów
 * zakotwiczoną na dzisiejszej 6:00, więc godziny są sensowne o każdej porze
 * dnia i nie skaczą przy recompose.
 */

private data class MockSlot(
    val title: String,
    val genre: String,
    val minutes: Int,
    val year: String = "",
    val age: String = "12 lat",
)

private data class MockChannel(
    val number: Int,
    val name: String,
    val slots: List<MockSlot>,
)

private val MOCK_CHANNELS = listOf(
    MockChannel(2, "TVP2", listOf(
        MockSlot("Pytanie na śniadanie", "magazyn poranny", 120, age = "b/o"),
        MockSlot("Barwy szczęścia", "serial obyczajowy", 30, "2024 r."),
        MockSlot("Familiada", "teleturniej", 30, "2024 r.", "b/o"),
        MockSlot("Koło fortuny", "teleturniej", 35, "2024 r.", "b/o"),
        MockSlot("Panorama", "program informacyjny", 25),
        MockSlot("Rodzinka.pl", "serial komediowy", 30, "2019 r."),
    )),
    MockChannel(3, "TVN", listOf(
        MockSlot("Dzień dobry TVN", "magazyn poranny", 150, age = "b/o"),
        MockSlot("Ukryta prawda", "serial paradokumentalny", 45, "2023 r."),
        MockSlot("Szpital", "serial paradokumentalny", 45, "2022 r."),
        MockSlot("Fakty", "program informacyjny", 25),
        MockSlot("Kuchenne rewolucje", "program rozrywkowy", 60, "2024 r."),
    )),
    MockChannel(4, "TVN 7", listOf(
        MockSlot("Brzydula", "serial komediowy", 30, "2020 r."),
        MockSlot("Przyjaciele", "serial komediowy", 30, "2004 r.", "b/o"),
        MockSlot("Mam talent!", "program rozrywkowy", 90, "2023 r."),
        MockSlot("Kobra: oddział specjalny", "serial sensacyjny", 55, "2021 r."),
    )),
    MockChannel(5, "Polsat", listOf(
        MockSlot("Nowy dzień", "magazyn poranny", 120, age = "b/o"),
        MockSlot("Malanowski i partnerzy", "serial kryminalny", 30, "2018 r."),
        MockSlot("Wydarzenia", "program informacyjny", 25),
        MockSlot("Świat według Kiepskich", "serial komediowy", 30, "2016 r."),
        MockSlot("Nasz nowy dom", "program rozrywkowy", 60, "2024 r."),
    )),
    MockChannel(6, "TV4", listOf(
        MockSlot("Policjantki i policjanci", "serial sensacyjny", 55, "2023 r."),
        MockSlot("Gliniarze", "serial kryminalny", 55, "2022 r."),
        MockSlot("STOP Drogówka", "magazyn", 60, "2024 r."),
        MockSlot("Hardkorowy lombard", "program rozrywkowy", 30, "2019 r."),
    )),
    MockChannel(7, "TVP Sport", listOf(
        MockSlot("Magazyn olimpijski", "magazyn sportowy", 45, age = "b/o"),
        MockSlot("Siatkówka: PlusLiga", "transmisja sportowa", 130, age = "b/o"),
        MockSlot("Sportowy wieczór", "magazyn sportowy", 30, age = "b/o"),
        MockSlot("Piłka nożna: Ekstraklasa", "transmisja sportowa", 120, age = "b/o"),
    )),
    MockChannel(8, "TVP Kultura", listOf(
        MockSlot("Informacje kulturalne", "magazyn kulturalny", 20),
        MockSlot("Studio Kultura — rozmowy", "talk-show", 40),
        MockSlot("Teatr Telewizji: Kartoteka", "spektakl", 95, "1979 r.", "16 lat"),
        MockSlot("Pegaz", "magazyn kulturalny", 35),
    )),
    MockChannel(9, "Discovery", listOf(
        MockSlot("Jak to jest zrobione?", "serial dokumentalny", 30, "2022 r., b/o"),
        MockSlot("Złoto Jukonu", "serial dokumentalny", 55, "2023 r."),
        MockSlot("Pogromcy mitów", "serial popularnonaukowy", 50, "2018 r."),
        MockSlot("Wyprawa na dno oceanu", "film dokumentalny", 60, "2021 r."),
    )),
)

/** Anchor ramówki: dzisiaj 6:00 (wczoraj, gdy teraz jest przed 6:00). */
private fun anchorWallMs(nowWallMs: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = nowWallMs
        set(Calendar.HOUR_OF_DAY, 6)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis > nowWallMs) cal.add(Calendar.DAY_OF_YEAR, -1)
    return cal.timeInMillis
}

/**
 * Rzędy mini-EPG dla kanałów mockupowych: program lecący TERAZ + [after] kolejnych.
 * Czasy w zegarze ściennym, więc wpinają się wprost obok kanałów z nagrań.
 */
fun pnMockChannelRows(nowWallMs: Long, after: Int = 4): List<PnChannelRow> {
    val anchor = anchorWallMs(nowWallMs)
    return MOCK_CHANNELS.map { ch ->
        val cycleMs = ch.slots.sumOf { it.minutes } * 60_000L
        val intoCycle = ((nowWallMs - anchor) % cycleMs + cycleMs) % cycleMs
        val cycleStart = nowWallMs - intoCycle

        // slot lecący teraz
        var idx = 0
        var acc = 0L
        for ((i, slot) in ch.slots.withIndex()) {
            val dur = slot.minutes * 60_000L
            if (intoCycle < acc + dur) { idx = i; break }
            acc += dur
        }

        var start = cycleStart + acc
        val programs = mutableListOf<PnProgram>()
        repeat(after + 1) { k ->
            val slot = ch.slots[(idx + k) % ch.slots.size]
            val dur = slot.minutes * 60_000L
            programs += PnProgram(
                title = slot.title,
                meta = listOf(slot.year, slot.genre, "${slot.minutes} min.", slot.age),
                coverUrl = null,
                startWallMs = start,
                endWallMs = start + dur,
            )
            start += dur
        }

        PnChannelRow(
            name = ch.name,
            number = ch.number,
            logoUrl = null,
            programs = programs,
            liveIndex = 0,
            tunable = false,
        )
    }
}
