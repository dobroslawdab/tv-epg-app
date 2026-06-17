package com.uxellence.tv.v3.demolive

/**
 * DEMO SEEK POLICY — polityka przewijania per kanał (tylko makieta A/B).
 *
 * Wytyczne BO: blokada jest PER KANAŁ, nie per program.
 *  - BOTH         — przewijanie w tył i w przód (w obrębie DVR/live).
 *  - BACKWARD_ONLY— tylko w tył; przewijanie DO PRZODU zablokowane (grupa TVN, Disney).
 *                   „Wróć do live" pozostaje dostępne (to skok, nie scrub-forward).
 *  - NONE         — brak przewijania (telewizja bez startover).
 *
 * Klasyfikacja tylko na potrzeby demo — NIE dotykamy produkcyjnego TvChannelData.
 * Mapowanie po nazwie kanału (reprezentatywnie), DEMO TV (barker) = BOTH.
 */
enum class DemoSeekPolicy { BOTH, BACKWARD_ONLY, NONE }

object DemoSeekPolicyClassifier {

    /** Polityka kanału. isDemoBarker = wiersz DEMO TV (zawsze pełne przewijanie). */
    fun policyForChannel(name: String, isDemoBarker: Boolean): DemoSeekPolicy {
        if (isDemoBarker) return DemoSeekPolicy.BOTH
        val n = name.lowercase()
        return when {
            // Grupy z blokadą przewijania do przodu (np. anty-skip reklam)
            n.contains("tvn") || n.contains("disney") -> DemoSeekPolicy.BACKWARD_ONLY
            // Kanał informacyjny bez startover (brak przewijania)
            n.contains("news") || n.contains("info") -> DemoSeekPolicy.NONE
            else -> DemoSeekPolicy.BOTH
        }
    }

    /**
     * Blackout (brak praw do odtworzenia danego programu) — deterministyczny,
     * rzadki podzbiór na realnych kanałach. DEMO TV (channelIdx 0) bez blackoutów,
     * żeby główny materiał demonstracyjny był zawsze odtwarzalny.
     */
    fun isBlackout(channelIdx: Int, programIndex: Int): Boolean {
        if (channelIdx == 0) return false
        if (programIndex < 0) return false
        // ~1 na 5 programów; offset zależny od kanału, by nie wypadały w jednej kolumnie
        return (programIndex + channelIdx) % 5 == 2
    }
}
