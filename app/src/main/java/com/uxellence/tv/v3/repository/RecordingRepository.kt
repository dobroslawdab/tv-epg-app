package com.uxellence.tv.v3.repository

import android.content.Context
import android.util.Log
import com.uxellence.tv.v3.model.*
import com.uxellence.tv.v3.epg.EpgProgram
import kotlinx.coroutines.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * RECORDING REPOSITORY
 *
 * Generates mock recordings data from EPG for the MOJE section.
 * Uses EpgRepository as data source and transforms EPG programs into recordings.
 *
 * Mockup Logic:
 * 1. Fetch programs from EPG: last 7 days + next 3 days
 * 2. Filter series: category "serial" OR regex S\d+E\d+ in description
 * 3. Filter movies: category "film", duration >= 60 min
 * 4. Group series by normalized title
 * 5. Calculate status based on time: RECORDED/RECORDING/SCHEDULED
 *
 * Usage:
 * - "Zarządzaj nagraniami" → getAllRecordings() (mixed individual + series)
 * - "Pojedyncze nagrania" → getIndividualRecordings() (movies only)
 * - "SERIE" → getSeriesBundles() (series grouped by title)
 * - "ZAPLANOWANE" → getScheduledRecordings() (future recordings)
 * - Series drill-down → getSeriesEpisodes(seriesId)
 */
class RecordingRepository private constructor(private val context: Context) {

    private val TAG = "RecordingRepository"
    private val epgRepository by lazy { EpgRepository.getInstance(context) }
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Cache for recordings data
    private var cachedRecordings: List<Recording>? = null
    private var cachedSeriesBundles: List<SeriesBundle>? = null
    private var lastCacheTime: Instant? = null
    private val CACHE_DURATION_MINUTES = 30L

    companion object {
        @Volatile
        private var INSTANCE: RecordingRepository? = null

        fun getInstance(context: Context): RecordingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RecordingRepository(context).also { INSTANCE = it }
            }
        }

        // Series detection patterns
        private val EPISODE_PATTERN = Regex("S\\d+E\\d+|E\\d+|odc\\.?\\s*\\d+|odcinek\\s*\\d+", RegexOption.IGNORE_CASE)
        private val SEASON_EPISODE_PATTERN = Regex("S(\\d+)E(\\d+)", RegexOption.IGNORE_CASE)

        // Unwanted categories for series (excludes entertainment programs)
        private val SERIES_UNWANTED_CATEGORIES = setOf(
            "rozrywka", "rozrywkowy", "show", "reality show", "talk-show",
            "informacyjny", "wiadomości", "news",
            "magazyn", "poradnik", "teleturniej"
        )

        // Movie categories
        private val MOVIE_CATEGORIES = setOf(
            "film", "film fabularny", "film przygodowy", "film akcji",
            "film sensacyjny", "film kryminalny", "dramat", "komedia",
            "horror", "thriller", "science fiction", "fantasy"
        )
    }

    /**
     * Get all recordings (individual + series bundles) for "Zarządzaj nagraniami"
     *
     * Returns mixed list of individual recordings and series bundles,
     * sorted by most recent first.
     */
    suspend fun getAllRecordings(): List<RecordingContent> {
        refreshCacheIfNeeded()

        val individual = cachedRecordings
            ?.filter { it.seriesId == null }
            ?.map { RecordingContent.Individual(it) }
            ?: emptyList()

        val series = cachedSeriesBundles
            ?.map { RecordingContent.Series(it) }
            ?: emptyList()

        // Zlecenia użytkownika z makiety demo live (flow "Nagrywanie serii")
        val userScheduled = userScheduledRecordings().map { RecordingContent.Individual(it) }

        // Merge and sort by most recent
        return (userScheduled + individual + series).sortedByDescending {
            when (it) {
                is RecordingContent.Individual -> it.recording.startUtc
                is RecordingContent.Series -> it.bundle.lastRecordedDate ?: Instant.EPOCH
            }
        }
    }

    /**
     * Nagrania zlecone przez użytkownika w makiecie demo live (DemoRecordingScheduler,
     * flow Figma "Nagrywanie serii") zmapowane na model Recording — dzięki temu
     * zlecenie z playera trafia do sekcji MOJE → Nagrania (Zaplanowane).
     */
    private fun userScheduledRecordings(): List<Recording> =
        com.uxellence.tv.v3.demolive.DemoRecordingScheduler.recordings.value.values.map { r ->
            Recording(
                id = "user_${r.title}_${r.startUtcMs}",
                title = r.title,
                description = r.subTitle.ifBlank { null },
                subTitle = r.subTitle.ifBlank { null },
                channelId = r.channelId,
                channelName = r.channelName,
                channelLogoUrl = null,
                startUtc = Instant.ofEpochMilli(r.startUtcMs),
                endUtc = Instant.ofEpochMilli(r.endUtcMs),
                imageUrl = r.imageUrl,
                categories = if (r.isSeries) listOf("serial") else listOf("film"),
                status = RecordingStatus.SCHEDULED,
                seriesId = null,
                watchProgress = 0f
            )
        }

    /**
     * Get only individual recordings (movies) for "Pojedyncze nagrania"
     */
    suspend fun getIndividualRecordings(): List<Recording> {
        refreshCacheIfNeeded()
        return cachedRecordings
            ?.filter { it.seriesId == null }
            ?.sortedByDescending { it.startUtc }
            ?: emptyList()
    }

    /**
     * Get series bundles for "SERIE" channel
     */
    suspend fun getSeriesBundles(): List<SeriesBundle> {
        refreshCacheIfNeeded()
        return cachedSeriesBundles
            ?.sortedByDescending { it.lastRecordedDate }
            ?: emptyList()
    }

    /**
     * Get scheduled (future) recordings for "ZAPLANOWANE"
     * Returns ALL scheduled recordings (individual films + series episodes)
     */
    suspend fun getScheduledRecordings(): List<Recording> {
        refreshCacheIfNeeded()
        val mock = cachedRecordings
            ?.filter { it.status == RecordingStatus.SCHEDULED }
            ?: emptyList()
        // Zlecenia użytkownika (demo live) na początku listy Zaplanowane
        return (userScheduledRecordings() + mock).sortedBy { it.startUtc }
    }

    /**
     * Get watched (fully completed) recordings for "OBEJRZANE"
     * Returns ALL watched recordings (individual films + series episodes)
     * watchProgress >= 1.0 means fully watched
     */
    suspend fun getWatchedRecordings(): List<Recording> {
        refreshCacheIfNeeded()
        return cachedRecordings
            ?.filter { it.watchProgress >= 1.0f }
            ?.sortedByDescending { it.startUtc }
            ?: emptyList()
    }

    /**
     * Get episodes for a specific series (drill-down)
     *
     * @param seriesId The series ID to get episodes for
     * @return List of episodes sorted by start time (newest first)
     */
    suspend fun getSeriesEpisodes(seriesId: String): List<Recording> {
        refreshCacheIfNeeded()
        return cachedRecordings
            ?.filter { it.seriesId == seriesId }
            ?.sortedByDescending { it.startUtc }
            ?: emptyList()
    }

    /**
     * Get series bundle by ID
     */
    suspend fun getSeriesBundleById(seriesId: String): SeriesBundle? {
        refreshCacheIfNeeded()
        return cachedSeriesBundles?.find { it.id == seriesId }
    }

    /**
     * Get recordings filtered by status
     */
    suspend fun getRecordingsByStatus(status: RecordingStatus): List<Recording> {
        refreshCacheIfNeeded()
        return cachedRecordings
            ?.filter { it.status == status }
            ?.sortedByDescending { it.startUtc }
            ?: emptyList()
    }

    /**
     * Get storage info (mock data)
     */
    fun getStorageInfo(): RecordingStorageInfo {
        return RecordingStorageInfo(
            usedHours = 220,
            remainingHours = 80,
            totalHours = 300
        )
    }

    /**
     * Clear cache to force refresh on next access
     */
    fun clearCache() {
        cachedRecordings = null
        cachedSeriesBundles = null
        lastCacheTime = null
        Log.d(TAG, "Recording cache cleared")
    }

    /**
     * Refresh cache if expired or empty
     */
    private suspend fun refreshCacheIfNeeded() {
        val now = Instant.now()
        val cacheExpired = lastCacheTime?.let {
            Duration.between(it, now).toMinutes() > CACHE_DURATION_MINUTES
        } ?: true

        if (cacheExpired || cachedRecordings == null) {
            Log.d(TAG, "Refreshing recordings cache...")
            generateMockRecordings()
            lastCacheTime = now
        }
    }

    /**
     * Generate mock recordings from EPG data
     *
     * Logic:
     * 1. Fetch EPG programs from last 7 days + next 3 days
     * 2. Identify series by category or episode pattern
     * 3. Identify movies by category and duration
     * 4. Group series episodes by normalized title
     * 5. Create Recording objects with calculated status
     */
    private suspend fun generateMockRecordings() = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val startTime = now.minus(Duration.ofDays(7))
        val endTime = now.plus(Duration.ofDays(3))

        Log.d(TAG, "Generating mock recordings: $startTime to $endTime")

        // Fetch all EPG programs in time window
        val allPrograms = fetchEpgProgramsInWindow(startTime, endTime)
        Log.d(TAG, "Fetched ${allPrograms.size} EPG programs")

        // Separate series and movies
        val seriesPrograms = allPrograms.filter { isSeries(it) }
        val moviePrograms = allPrograms.filter { isMovie(it) }

        Log.d(TAG, "Found ${seriesPrograms.size} series programs, ${moviePrograms.size} movie programs")

        // Convert programs to recordings
        val allRecordings = mutableListOf<Recording>()

        // Process series - group by normalized title
        val seriesGroups = seriesPrograms.groupBy { normalizeTitle(it.title) }
        val seriesBundles = mutableListOf<SeriesBundle>()

        seriesGroups.forEach { (normalizedTitle, episodes) ->
            val seriesId = "series_${normalizedTitle.hashCode()}"

            // Convert episodes to recordings with seriesId
            val episodeRecordings = episodes.map { program ->
                createRecording(program, seriesId, now)
            }

            allRecordings.addAll(episodeRecordings)

            // Create series bundle
            if (episodeRecordings.isNotEmpty()) {
                val bundle = createSeriesBundle(seriesId, normalizedTitle, episodeRecordings)
                seriesBundles.add(bundle)
            }
        }

        // Process movies - individual recordings (no seriesId)
        moviePrograms.forEach { program ->
            val recording = createRecording(program, null, now)
            allRecordings.add(recording)
        }

        // Update cache
        cachedRecordings = allRecordings
        cachedSeriesBundles = seriesBundles

        Log.d(TAG, "Generated ${allRecordings.size} recordings, ${seriesBundles.size} series bundles")
    }

    /**
     * Fetch EPG programs in time window using EpgRepository
     */
    private suspend fun fetchEpgProgramsInWindow(
        startTime: Instant,
        endTime: Instant
    ): List<EpgProgram> {
        return try {
            val guide = epgRepository.getEpgGuide(startTime, endTime)
            guide.programs
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching EPG programs", e)
            emptyList()
        }
    }

    /**
     * Check if program is a TV series
     */
    private fun isSeries(program: EpgProgram): Boolean {
        // 1. Check category contains "serial"
        val hasSeriesCategory = program.categories.any { category ->
            category.lowercase().contains("serial")
        }

        // 2. Check description for episode pattern (S01E01, E01, odc. 1)
        val hasEpisodePattern = program.description?.let { desc ->
            EPISODE_PATTERN.containsMatchIn(desc)
        } ?: false

        // Also check subtitle for episode info
        val subtitleHasEpisode = program.subTitle?.let { sub ->
            EPISODE_PATTERN.containsMatchIn(sub)
        } ?: false

        // 3. Exclude unwanted categories
        val noUnwantedCategories = program.categories.none { category ->
            SERIES_UNWANTED_CATEGORIES.any { unwanted ->
                category.lowercase().contains(unwanted)
            }
        }

        // 4. Duration check (episodes typically 20-90 min)
        val duration = Duration.between(program.startUtc, program.endUtc).toMinutes()
        val isEpisodeLength = duration in 20..90

        return (hasSeriesCategory || hasEpisodePattern || subtitleHasEpisode) &&
                noUnwantedCategories && isEpisodeLength
    }

    /**
     * Check if program is a movie
     */
    private fun isMovie(program: EpgProgram): Boolean {
        // 1. Check category contains movie-related keywords
        val hasMovieCategory = program.categories.any { category ->
            MOVIE_CATEGORIES.any { movieCat ->
                category.lowercase().contains(movieCat)
            }
        }

        // 2. Exclude documentaries
        val isNotDocumentary = program.categories.none { category ->
            category.lowercase().contains("dokumentalny") ||
                    category.lowercase().contains("dokument")
        }

        // 3. Exclude series
        val isNotSeries = !isSeries(program)

        // 4. Duration check (feature films >= 60 min)
        val duration = Duration.between(program.startUtc, program.endUtc).toMinutes()
        val isFeatureLength = duration >= 60

        return hasMovieCategory && isNotDocumentary && isNotSeries && isFeatureLength
    }

    /**
     * Normalize title for grouping (remove episode numbers, special chars)
     */
    private fun normalizeTitle(title: String): String {
        return title
            .replace(EPISODE_PATTERN, "")
            .replace(Regex("[^a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ0-9\\s]"), "")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Create Recording from EpgProgram
     */
    private fun createRecording(
        program: EpgProgram,
        seriesId: String?,
        now: Instant
    ): Recording {
        val status = Recording.calculateStatus(program.startUtc, program.endUtc, now)

        // Postęp obejrzenia — SEEDOWANY po ID nagrania (kanał + czas startu), nie
        // globalnym Random. Wcześniej `Random.nextFloat()` losował od nowa przy
        // każdym odświeżeniu cache (restart apki / 30 min), więc paski postępu
        // w gridzie "Zarządzanie nagraniami" skakały między sesjami badawczymi.
        // Ten sam wzorzec co generateMockSeriesMetadata (seed po tytule).
        val recordingId = "rec_${program.channelId}_${program.startUtc.epochSecond}"
        val seeded = Random(recordingId.hashCode())
        val watchProgress = when (status) {
            RecordingStatus.RECORDED -> {
                val rand = seeded.nextFloat()
                when {
                    rand < 0.30f -> 1.0f  // 30% fully watched
                    rand < 0.70f -> 0.1f + seeded.nextFloat() * 0.8f  // 40% partial (0.1-0.9)
                    else -> 0f  // 30% not started
                }
            }
            RecordingStatus.RECORDING -> seeded.nextFloat() * 0.5f // 0.0 - 0.5 (partial)
            RecordingStatus.SCHEDULED -> 0f
        }

        val expirationDate = if (status == RecordingStatus.RECORDED) {
            program.endUtc.plus(Duration.ofDays(30)) // 30 days from broadcast
        } else null

        // Extract episode info from description/subtitle
        val subTitle = extractEpisodeSubtitle(program)

        return Recording(
            id = recordingId,
            title = program.title,
            description = program.description,
            subTitle = subTitle,
            channelId = program.channelId,
            channelName = program.channelId, // Use channelId as name (EPG doesn't have separate name)
            channelLogoUrl = "https://epg.ovh/logo/${program.channelId}.png",
            startUtc = program.startUtc,
            endUtc = program.endUtc,
            imageUrl = program.iconUrl,
            categories = program.categories,
            status = status,
            seriesId = seriesId,
            watchProgress = watchProgress,
            expirationDate = expirationDate
        )
    }

    /**
     * Extract episode subtitle from program description
     */
    private fun extractEpisodeSubtitle(program: EpgProgram): String? {
        // First check explicit subtitle
        program.subTitle?.let { return it }

        // Try to extract from description
        program.description?.let { desc ->
            SEASON_EPISODE_PATTERN.find(desc)?.let { match ->
                val season = match.groupValues[1]
                val episode = match.groupValues[2]
                return "S${season.padStart(2, '0')}E${episode.padStart(2, '0')}"
            }

            // Try simple episode pattern
            Regex("odc\\.?\\s*(\\d+)|odcinek\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(desc)?.let { match ->
                    val epNum = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                    epNum?.let { return "odc. $it" }
                }
        }

        return null
    }

    /**
     * Create SeriesBundle from list of episode recordings
     */
    private fun createSeriesBundle(
        seriesId: String,
        normalizedTitle: String,
        episodes: List<Recording>
    ): SeriesBundle {
        // Get original title from most recent episode
        val originalTitle = episodes.maxByOrNull { it.startUtc }?.title
            ?: normalizedTitle.replaceFirstChar { it.uppercase() }

        // Get most recent image and channel logo
        val mostRecentRecorded = episodes
            .filter { it.status == RecordingStatus.RECORDED }
            .maxByOrNull { it.startUtc }

        val imageUrl = mostRecentRecorded?.imageUrl ?: episodes.firstOrNull()?.imageUrl
        val channelLogoUrl = mostRecentRecorded?.channelLogoUrl ?: episodes.firstOrNull()?.channelLogoUrl

        // Calculate last recorded date
        val lastRecordedDate = episodes
            .filter { it.status == RecordingStatus.RECORDED }
            .maxOfOrNull { it.endUtc }

        // Get categories from episodes (use most common)
        val categories = episodes
            .flatMap { it.categories }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        // Generate mock metadata for series info panel
        val mockMetadata = generateMockSeriesMetadata(normalizedTitle, categories)

        return SeriesBundle(
            id = seriesId,
            title = originalTitle,
            imageUrl = imageUrl,
            channelLogoUrl = channelLogoUrl,
            episodes = episodes.sortedByDescending { it.startUtc },
            lastRecordedDate = lastRecordedDate,
            categories = categories,
            year = mockMetadata.year,
            country = mockMetadata.country,
            ageRating = mockMetadata.ageRating,
            krritLabels = mockMetadata.krritLabels,
            description = mockMetadata.description
        )
    }

    /**
     * Mock metadata holder for series
     */
    private data class MockSeriesMetadata(
        val year: String?,
        val country: String?,
        val ageRating: String?,
        val krritLabels: Set<KrritLabel>,
        val description: String?
    )

    /**
     * Generate mock metadata based on title and categories
     */
    private fun generateMockSeriesMetadata(
        normalizedTitle: String,
        categories: List<String>
    ): MockSeriesMetadata {
        val random = Random(normalizedTitle.hashCode())

        // Mock years (2018-2024)
        val years = listOf("2018 r.", "2019 r.", "2020 r.", "2021 r.", "2022 r.", "2023 r.", "2024 r.")
        val year = years[random.nextInt(years.size)]

        // Mock countries
        val countries = listOf("Polska", "USA", "Niemcy", "Wielka Brytania", "Francja")
        val country = countries[random.nextInt(countries.size)]

        // Mock age ratings
        val ageRatings = listOf("7 lat", "12 lat", "16 lat", "18 lat", null)
        val ageRating = ageRatings[random.nextInt(ageRatings.size)]

        // Mock KRRIT labels (based on categories and random)
        val krritLabels = mutableSetOf<KrritLabel>()
        val hasDrama = categories.any { it.lowercase().contains("dramat") }
        val hasCrime = categories.any { it.lowercase().contains("kryminalny") || it.lowercase().contains("sensacyjny") }

        if (hasDrama && random.nextFloat() > 0.5f) krritLabels.add(KrritLabel.S)
        if (hasCrime) krritLabels.add(KrritLabel.P)
        if (random.nextFloat() > 0.7f) krritLabels.add(KrritLabel.W)
        if (random.nextFloat() > 0.9f) krritLabels.add(KrritLabel.N)

        // Mock descriptions
        val descriptions = listOf(
            "Emocjonująca historia pełna zwrotów akcji i nieoczekiwanych wydarzeń, która wciąga od pierwszego odcinka.",
            "Serial przedstawia losy bohaterów zmagających się z codziennymi problemami w fascynujący sposób.",
            "Wciągająca fabuła i świetni aktorzy tworzą niepowtarzalny klimat tego wyjątkowego serialu.",
            "Historia, która porusza ważne tematy społeczne i zmusza do refleksji nad otaczającą nas rzeczywistością.",
            null
        )
        val description = descriptions[random.nextInt(descriptions.size)]

        return MockSeriesMetadata(
            year = year,
            country = country,
            ageRating = ageRating,
            krritLabels = krritLabels,
            description = description
        )
    }
}
