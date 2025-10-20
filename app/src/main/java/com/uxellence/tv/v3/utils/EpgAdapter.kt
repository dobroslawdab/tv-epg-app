package com.uxellence.tv.v3.utils

import com.uxellence.tv.v3.epg.EpgProgram
import com.uxellence.tv.v3.repository.EpgRepository
import com.uxellence.tv.v3.version001.VodContent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object EpgAdapter {
    private data class ProgramMetadata(
        val genre: String? = null,
        val episode: String? = null,
        val season: String? = null,
        val year: String? = null,
        val ageRating: String? = null
    )

    // Mapowanie nazw kanałów - używamy dokładnie tych samych ID co w bazie danych EPG!
    private val channelMapping = mapOf(
        "TVP1" to "TVP 1",          // Baza używa "TVP 1" (z spacją!)
        "TVP2" to "TVP 2",          // Baza używa "TVP 2" (z spacją!)
        "Polsat" to "Polsat",
        "TVN" to "TVN",
        "TVN 7" to "TVN 7",         // Baza używa "TVN 7" (z spacją!)
        "TV4" to "TV 4",            // Baza używa "TV 4" (z spacją!)
        "TV Puls" to "TV Puls",     // Baza używa "TV Puls" (z spacją!)
        "Tele 5" to "Tele 5",
        "TV 6" to "TV 6",
        "Puls 2" to "Puls 2"
    )

    // Odwrotne mapowanie: ID z EPG -> nazwa kanału
    private val reverseChannelMapping = channelMapping.entries.associate { (k, v) -> v to k }

    /**
     * Get current programs as EpgProgram objects (for TELEWIZJA "Kategorie EPG")
     * Returns raw EpgProgram list (not converted to VodContent)
     */
    suspend fun getCurrentPrograms(
        epgRepository: EpgRepository
    ): List<EpgProgram> {
        android.util.Log.d("EpgAdapter", "=== Getting current programs (EpgProgram) ===")
        val channels = listOf(
            "TVP1", "TVP2", "Polsat", "TVN", "TVN 7",
            "TV4", "TV Puls", "Tele 5", "TV 6", "Puls 2"
        )

        val result = channels.mapNotNull { channelName ->
            val epgChannelId = channelMapping[channelName] ?: return@mapNotNull null
            android.util.Log.d("EpgAdapter", "Fetching: $channelName -> $epgChannelId")

            try {
                val currentProgram = epgRepository.getCurrentProgram(epgChannelId)
                if (currentProgram == null) {
                    android.util.Log.w("EpgAdapter", "  No program for $channelName")
                    return@mapNotNull null
                }

                android.util.Log.d("EpgAdapter", "  Found: ${currentProgram.title}")
                currentProgram
            } catch (e: Exception) {
                android.util.Log.e("EpgAdapter", "Error for $channelName", e)
                null
            }
        }

        android.util.Log.d("EpgAdapter", "Total programs: ${result.size}")
        return result
    }

    suspend fun getCurrentProgramsAsVodContent(
        epgRepository: EpgRepository
    ): List<VodContent> {
        android.util.Log.d("EpgAdapter", "=== Getting current programs ===")
        val channels = listOf(
            "TVP1", "TVP2", "Polsat", "TVN", "TVN 7",
            "TV4", "TV Puls", "Tele 5", "TV 6", "Puls 2"
        )

        val result = channels.mapNotNull { channelName ->
            val epgChannelId = channelMapping[channelName] ?: return@mapNotNull null
            android.util.Log.d("EpgAdapter", "Fetching: $channelName -> $epgChannelId")

            try {
                val currentProgram = epgRepository.getCurrentProgram(epgChannelId)
                if (currentProgram == null) {
                    android.util.Log.w("EpgAdapter", "  No program for $channelName")
                    return@mapNotNull null
                }

                android.util.Log.d("EpgAdapter", "  Found: ${currentProgram.title}")
                android.util.Log.d("EpgAdapter", "    iconUrl: ${currentProgram.iconUrl}")
                android.util.Log.d("EpgAdapter", "    categories: ${currentProgram.categories}")
                android.util.Log.d("EpgAdapter", "    description: ${currentProgram.description?.take(50)}")

                currentProgram.let { program ->
                    VodContent(
                        title = program.title,
                        description = buildDescription(channelName, program),
                        category = buildMetadataString(program),
                        imageUrl = program.iconUrl ?: getChannelLogo(channelName),
                        channelLogoUrl = getChannelLogo(channelName),
                        link = "${program.startUtc}|${program.endUtc}|${program.channelId}|${channelName}"  // Timestamps + channelId + channelName for EPG cards
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("EpgAdapter", "Error for $channelName", e)
                null
            }
        }

        android.util.Log.d("EpgAdapter", "Total programs: ${result.size}")
        return result
    }

    suspend fun getLast24HoursMoviesAsVodContent(
        epgRepository: EpgRepository
    ): List<VodContent> {
        android.util.Log.d("EpgAdapter", "=== Getting movies from last 24 hours ===")

        try {
            val movies = epgRepository.getLast24HoursMovies()
            android.util.Log.d("EpgAdapter", "Found ${movies.size} feature-length movies")

            return movies.map { program ->
                // Odwrotne mapowanie: channelId z EPG -> nazwa kanału
                val channelName = getChannelNameFromId(program.channelId)

                android.util.Log.d("EpgAdapter", "  ${program.title} on $channelName")
                android.util.Log.d("EpgAdapter", "    ${program.startUtc} - ${program.endUtc}")
                android.util.Log.d("EpgAdapter", "    categories: ${program.categories}")

                VodContent(
                    title = program.title,
                    description = buildDescriptionForMovies(channelName, program),
                    category = buildMetadataString(program),
                    imageUrl = program.iconUrl ?: getChannelLogo(channelName),
                    channelLogoUrl = getChannelLogo(channelName),
                    link = ""
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("EpgAdapter", "Error getting movies from last 24h", e)
            return emptyList()
        }
    }

    suspend fun getLast24HoursSeriesAsVodContent(
        epgRepository: EpgRepository
    ): List<VodContent> {
        android.util.Log.d("EpgAdapter", "=== Getting series from last 24 hours ===")

        try {
            val series = epgRepository.getLast24HoursSeries()
            android.util.Log.d("EpgAdapter", "Found ${series.size} series")

            return series.map { program ->
                // Odwrotne mapowanie: channelId z EPG -> nazwa kanału
                val channelName = getChannelNameFromId(program.channelId)

                android.util.Log.d("EpgAdapter", "  ${program.title} on $channelName")
                android.util.Log.d("EpgAdapter", "    ${program.startUtc} - ${program.endUtc}")
                android.util.Log.d("EpgAdapter", "    categories: ${program.categories}")

                VodContent(
                    title = program.title,
                    description = buildDescriptionForMovies(channelName, program),
                    category = buildMetadataString(program),
                    imageUrl = program.iconUrl ?: getChannelLogo(channelName),
                    channelLogoUrl = getChannelLogo(channelName),
                    link = ""
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("EpgAdapter", "Error getting series from last 24h", e)
            return emptyList()
        }
    }

    suspend fun getLast24HoursSportsAsVodContent(
        epgRepository: EpgRepository
    ): List<VodContent> {
        android.util.Log.d("EpgAdapter", "=== Getting sports from last 24 hours ===")

        try {
            val sports = epgRepository.getLast24HoursSports()
            android.util.Log.d("EpgAdapter", "Found ${sports.size} sports programs")

            return sports.map { program ->
                val channelName = getChannelNameFromId(program.channelId)

                android.util.Log.d("EpgAdapter", "  ${program.title} on $channelName")
                android.util.Log.d("EpgAdapter", "    ${program.startUtc} - ${program.endUtc}")
                android.util.Log.d("EpgAdapter", "    categories: ${program.categories}")

                VodContent(
                    title = program.title,
                    description = buildDescriptionForMovies(channelName, program),
                    category = buildMetadataString(program),
                    imageUrl = program.iconUrl ?: getChannelLogo(channelName),
                    channelLogoUrl = getChannelLogo(channelName),
                    link = ""
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("EpgAdapter", "Error getting sports from last 24h", e)
            return emptyList()
        }
    }

    suspend fun getLast24HoursGameShowsAsVodContent(
        epgRepository: EpgRepository
    ): List<VodContent> {
        android.util.Log.d("EpgAdapter", "=== Getting game shows from last 24 hours ===")

        try {
            val gameShows = epgRepository.getLast24HoursGameShows()
            android.util.Log.d("EpgAdapter", "Found ${gameShows.size} game show programs")

            return gameShows.map { program ->
                val channelName = getChannelNameFromId(program.channelId)

                android.util.Log.d("EpgAdapter", "  ${program.title} on $channelName")
                android.util.Log.d("EpgAdapter", "    ${program.startUtc} - ${program.endUtc}")
                android.util.Log.d("EpgAdapter", "    categories: ${program.categories}")

                VodContent(
                    title = program.title,
                    description = buildDescriptionForMovies(channelName, program),
                    category = buildMetadataString(program),
                    imageUrl = program.iconUrl ?: getChannelLogo(channelName),
                    channelLogoUrl = getChannelLogo(channelName),
                    link = ""
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("EpgAdapter", "Error getting game shows from last 24h", e)
            return emptyList()
        }
    }

    /**
     * Get EPG programs for category collections (for "Kategorie EPG" slider)
     * Returns 5 programs - one from each category
     */
    suspend fun getEpgCategoriesPrograms(
        epgRepository: EpgRepository
    ): List<EpgProgram> {
        android.util.Log.d("EpgAdapter", "=== Getting EPG category programs ===")

        try {
            val programs = mutableListOf<EpgProgram>()

            // 1. Filmy fabularne (feature-length movies)
            epgRepository.getLast24HoursMovies().firstOrNull()?.let {
                android.util.Log.d("EpgAdapter", "Movie: ${it.title}")
                programs.add(it)
            }

            // 2. Seriale
            epgRepository.getLast24HoursSeries().firstOrNull()?.let {
                android.util.Log.d("EpgAdapter", "Series: ${it.title}")
                programs.add(it)
            }

            // 3. Sport
            epgRepository.getLast24HoursSports().firstOrNull()?.let {
                android.util.Log.d("EpgAdapter", "Sport: ${it.title}")
                programs.add(it)
            }

            // 4. Dla dzieci (get programs with "dla dzieci" or "dziecięcy" category)
            epgRepository.getCurrentProgram("TVP ABC")?.let {
                android.util.Log.d("EpgAdapter", "Kids: ${it.title}")
                programs.add(it)
            } ?: run {
                // Fallback: search for kids programs in last 24h
                epgRepository.getLast24HoursSeries().firstOrNull { program ->
                    program.categories.any { cat ->
                        cat.lowercase().contains("dziec") || cat.lowercase().contains("bajk")
                    }
                }?.let {
                    android.util.Log.d("EpgAdapter", "Kids (fallback): ${it.title}")
                    programs.add(it)
                }
            }

            // 5. Teleturnieje
            epgRepository.getLast24HoursGameShows().firstOrNull()?.let {
                android.util.Log.d("EpgAdapter", "Game show: ${it.title}")
                programs.add(it)
            }

            android.util.Log.d("EpgAdapter", "Total category programs: ${programs.size}")
            return programs
        } catch (e: Exception) {
            android.util.Log.e("EpgAdapter", "Error getting EPG category programs", e)
            return emptyList()
        }
    }

    private fun parseMetadata(description: String?): ProgramMetadata? {
        if (description.isNullOrBlank() || !description.startsWith("G:")) {
            return null // To nie są metadane
        }

        var genre: String? = null
        var episode: String? = null
        var season: String? = null
        var year: String? = null
        var ageRating: String? = null

        val parts = description.split(". ")

        parts.forEach { part ->
            when {
                part.startsWith("G: ") -> genre = part.substringAfter("G: ").trim()
                part.startsWith("E") && !part.contains("S") -> {
                    // Format: E1048
                    episode = part.substring(1).trim()
                }
                part.startsWith("S") && "E" in part -> {
                    // Format: S1E23
                    val match = Regex("S(\\d+)E(\\d+)").find(part)
                    if (match != null) {
                        season = match.groupValues[1]
                        episode = match.groupValues[2]
                    }
                }
                part.startsWith("R: ") -> year = part.substringAfter("R: ").trim()
                part.startsWith("W: ") -> ageRating = part.substringAfter("W: ").trim()
            }
        }

        return ProgramMetadata(
            genre = genre,
            episode = episode,
            season = season,
            year = year,
            ageRating = ageRating
        )
    }

    private fun buildMetadataString(program: EpgProgram): String {
        val metadata = parseMetadata(program.description)

        if (metadata != null) {
            val parts = mutableListOf<String>()

            // Gatunek (z categories lub metadata)
            val genre = program.categories.firstOrNull() ?: metadata.genre
            if (genre != null) {
                parts.add(genre.replaceFirstChar { it.uppercase() })
            }

            // Odcinek
            if (metadata.season != null && metadata.episode != null) {
                parts.add("Sezon ${metadata.season}, Odcinek ${metadata.episode}")
            } else if (metadata.episode != null) {
                parts.add("Odcinek ${metadata.episode}")
            }

            // Rok
            if (metadata.year != null) {
                parts.add("Rok produkcji: ${metadata.year}")
            }

            // Wiek
            if (metadata.ageRating != null && metadata.ageRating.isNotEmpty()) {
                parts.add("Od ${metadata.ageRating} lat")
            }

            return if (parts.isNotEmpty()) {
                parts.joinToString(" • ")
            } else {
                program.categories.firstOrNull() ?: "TV"
            }
        } else {
            // Fallback do categories
            return program.categories.firstOrNull() ?: "TV"
        }
    }

    private fun buildDescription(channelName: String, program: EpgProgram): String {
        val parts = mutableListOf<String>()

        // Wiersz 1: Kanał | Czas
        parts.add("$channelName | ${formatProgramTime(program)}")

        // Wiersz 2: SubTitle (nazwa odcinka)
        if (!program.subTitle.isNullOrBlank()) {
            parts.add("\"${program.subTitle}\"")
        }

        // Wiersz 3+: Prawdziwy opis (jeśli description NIE zawiera metadanych)
        if (!program.description.isNullOrBlank() && !program.description.startsWith("G:")) {
            parts.add(program.description)
        }

        return parts.joinToString("\n\n")
    }

    private fun buildDescriptionForMovies(channelName: String, program: EpgProgram): String {
        val parts = mutableListOf<String>()

        // Wiersz 1: "Oglądaj teraz" zamiast czasu
        parts.add("Oglądaj teraz")

        // Wiersz 2: SubTitle (nazwa filmu/odcinka)
        if (!program.subTitle.isNullOrBlank()) {
            parts.add("\"${program.subTitle}\"")
        }

        // Wiersz 3+: Opis (jeśli nie zawiera metadanych)
        if (!program.description.isNullOrBlank() && !program.description.startsWith("G:")) {
            parts.add(program.description)
        }

        return parts.joinToString("\n\n")
    }

    private fun formatProgramTime(program: EpgProgram): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val zoneId = ZoneId.systemDefault()
        val start = program.startUtc.atZone(zoneId).format(formatter)
        val end = program.endUtc.atZone(zoneId).format(formatter)
        return "$start-$end"
    }

    private fun getChannelNameFromId(channelId: String): String {
        // Odwrotne mapowanie: "TVP 1" -> "TVP1"
        return reverseChannelMapping[channelId] ?: channelId
    }

    // Public wrapper for getChannelNameFromId (used by EpgCollectionSliderCard)
    fun getChannelNameFromEpgId(channelId: String): String {
        return getChannelNameFromId(channelId)
    }

    // Public wrapper for buildMetadataString (used by EpgCollectionSliderCard)
    fun buildMetadataStringPublic(program: EpgProgram): String {
        return buildMetadataString(program)
    }

    private fun getChannelLogo(channelName: String): String {
        // Mapowanie nazw do logo ID
        val logoMap = mapOf(
            "TVP1" to "tvp1",
            "TVP2" to "tvp2",
            "Polsat" to "polsat",
            "TVN" to "tvn",
            "TVN 7" to "tvn7",
            "TV4" to "tv4",
            "TV Puls" to "tv-puls",
            "Tele 5" to "tele5",
            "TV 6" to "tv6",
            "Puls 2" to "puls2"
        )
        val logoId = logoMap[channelName] ?: channelName.lowercase().replace(" ", "-")
        return "https://epg.ovh/logo/$logoId.png"
    }
}
