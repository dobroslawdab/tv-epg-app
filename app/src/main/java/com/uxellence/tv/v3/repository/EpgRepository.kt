package com.uxellence.tv.v3.repository

import android.content.Context
import androidx.room.Room
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.database.*
import com.uxellence.tv.v3.epg.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

class EpgRepository private constructor(context: Context) {
    
    private val database = Room.databaseBuilder(
        context.applicationContext,
        EpgDatabase::class.java,
        EpgDatabase.DATABASE_NAME
    ).build()
    
    private val channelDao = database.channelDao()
    private val programDao = database.programDao()
    private val metadataDao = database.metadataDao()
    
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        @Volatile
        private var INSTANCE: EpgRepository? = null
        
        fun getInstance(context: Context): EpgRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EpgRepository(context).also { INSTANCE = it }
            }
        }
        
        private const val EPG_METADATA_KEY = "main_epg"
        private const val CACHE_DURATION_HOURS = 6L
        private const val MAX_CHANNELS_LOAD = 300  // Increased to include all Polish channels

        // Preferred channels for content filtering (FILMY/SERIALE/SPORT/TELETURNIEJE)
        // + original 9 channels from tv_channels_with_streams.json for "Teraz w TV"
        private val PREFERRED_EPG_CHANNELS = setOf(
            "TVP 1", "TVP 2", "Polsat", "TV 4", "TV 6",
            "TVN", "TV Puls", "Puls 2", "TTV", "Fokus TV",
            "Super Polsat", "METRO", "ZOOM TV", "WP", "Antena",
            "HBO", "HBO 2", "HBO 3", "Kino Polska", "Stopklatka TV",
            "AXN", "Viasat True Crime", "HISTORY", "Discovery Channel",
            "BBC First", "TVP 3 Warszawa", "TV TRWAM", "TVN 24", "Kabaret TV",
            // Original 9 channels from JSON (missing 5 added back):
            "Polsat News Polityka",
            "4FUN TV",
            "Polsat News",
            "TVP Sport",
            "TVP 3"  // Keep both "TVP 3" and "TVP 3 Warszawa"
        )
    }
    
    // Public interface matching existing EpgGuide
    suspend fun getEpgGuide(
        startTime: Instant? = null,
        endTime: Instant? = null,
        maxChannels: Int = MAX_CHANNELS_LOAD
    ): EpgGuide {
        val (start, end) = if (startTime != null && endTime != null) {
            Pair(startTime, endTime)
        } else {
            getFullDayWindow()
        }
        
        // Try to get from cache first
        val cachedData = getCachedEpgGuide(start, end, maxChannels)
        if (cachedData != null && !isCacheExpired()) {
            return cachedData
        }
        
        // If cache miss or expired, refresh data
        return refreshEpgData(start, end, maxChannels)
    }
    
    suspend fun getChannelsFlow(): Flow<List<EpgChannel>> {
        return channelDao.getAllChannelsFlow().map { entities ->
            entities.map { it.toEpgChannel() }
        }
    }
    
    suspend fun getProgramsForChannelFlow(
        channelId: String,
        startTime: Instant,
        endTime: Instant
    ): Flow<List<EpgProgram>> {
        return programDao.getProgramsForChannelFlow(channelId, startTime, endTime).map { entities ->
            entities.map { it.toEpgProgram() }
        }
    }
    
    suspend fun getCurrentProgram(channelId: String): EpgProgram? {
        val now = Instant.now()
        android.util.Log.d("EpgRepository", "getCurrentProgram: channelId=$channelId, now=$now")
        val result = programDao.getCurrentProgramForChannel(channelId, now)?.toEpgProgram()
        android.util.Log.d("EpgRepository", "  Result: ${if (result == null) "NULL" else result.title}")
        return result
    }
    
    suspend fun getFullDayPrograms(channelId: String, date: Instant): List<EpgProgram> {
        val startOfDay = date.atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(Duration.ofDays(1))

        return programDao.getProgramsForChannel(channelId, startOfDay, endOfDay)
            .map { it.toEpgProgram() }
    }

    suspend fun getLast24HoursMovies(): List<EpgProgram> {
        val now = Instant.now()
        val last24Hours = now.minus(Duration.ofHours(24))

        android.util.Log.d("EpgRepository", "getLast24HoursMovies: $last24Hours to $now")

        // Pobierz wszystkie programy z ostatnich 24h
        val allPrograms = programDao.getProgramsInTimeWindow(last24Hours, now)
            .map { it.toEpgProgram() }

        android.util.Log.d("EpgRepository", "Total programs in last 24h: ${allPrograms.size}")

        // Niepożądane kategorie (magazyny, rozrywka, teleturnieje)
        val unwantedCategories = setOf(
            "magazyn", "magazyn filmowy",
            "rozrywka", "show", "reality show",
            "teleturniej", "talk-show",
            "publicystyka", "widowisko",
            "informacja", "wiadomości"
        )

        // Filtruj tylko filmy pełnometrażowe z wybranych kanałów
        val movies = allPrograms
            .filter { it.channelId in PREFERRED_EPG_CHANNELS }  // Filtruj po preferowanych kanałach
            .filter { program ->
            // 1. Kategoria zawiera "film" ale nie "dokumentalny"
            val hasFilmCategory = program.categories.any { category ->
                val lower = category.lowercase()
                lower.contains("film") && !lower.contains("dokumentalny")
            }

            // 2. Brak niepożądanych kategorii
            val noUnwantedCategories = program.categories.none { category ->
                unwantedCategories.any { unwanted ->
                    category.lowercase().contains(unwanted)
                }
            }

            // 3. Czas trwania > 60 minut (film pełnometrażowy)
            val duration = Duration.between(program.startUtc, program.endUtc).toMinutes()
            val isFeatureLength = duration > 60

            android.util.Log.d("EpgRepository", "  ${program.title}: hasFilm=$hasFilmCategory, noUnwanted=$noUnwantedCategories, duration=$duration min")

            hasFilmCategory && noUnwantedCategories && isFeatureLength
        }

        android.util.Log.d("EpgRepository", "Feature-length movies found: ${movies.size}")

        // Sortuj po czasie startu (najnowsze najpierw)
        // Usuń duplikaty (ten sam film na różnych kanałach)
        // Weź 10 unikalnych filmów
        val uniqueMovies = movies
            .sortedByDescending { it.startUtc }
            .distinctBy { it.title.lowercase().trim() }
            .take(10)

        android.util.Log.d("EpgRepository", "Unique movies after deduplication: ${uniqueMovies.size}")

        return uniqueMovies
    }

    suspend fun getLast24HoursSeries(): List<EpgProgram> {
        val now = Instant.now()
        val last24Hours = now.minus(Duration.ofHours(24))

        android.util.Log.d("EpgRepository", "getLast24HoursSeries: $last24Hours to $now")

        // Pobierz wszystkie programy z ostatnich 24h
        val allPrograms = programDao.getProgramsInTimeWindow(last24Hours, now)
            .map { it.toEpgProgram() }

        android.util.Log.d("EpgRepository", "Total programs in last 24h: ${allPrograms.size}")

        // Niepożądane kategorie dla seriali (wykluczyć rozrywkę i programy informacyjne)
        val unwantedCategories = setOf(
            "rozrywka", "rozrywkowy", "show", "reality show", "talk-show",
            "informacyjny", "wiadomości", "news",
            "magazyn", "poradnik", "teleturniej"
        )

        // Filtruj tylko seriale z wybranych kanałów
        val series = allPrograms
            .filter { it.channelId in PREFERRED_EPG_CHANNELS }  // Filtruj po preferowanych kanałach
            .filter { program ->
            // 1. Kategoria zawiera "serial"
            val hasSeriesCategory = program.categories.any { category ->
                category.lowercase().contains("serial")
            }

            // 2. LUB description zawiera S[X]E[Y] lub E[N]
            val hasEpisodeInfo = program.description?.let { desc ->
                desc.contains(Regex("S\\d+E\\d+")) || desc.contains(Regex("E\\d+"))
            } ?: false

            // 3. Wykluczyć programy rozrywkowe
            val noUnwantedCategories = program.categories.none { category ->
                unwantedCategories.any { unwanted ->
                    category.lowercase().contains(unwanted)
                }
            }

            // 4. Czas trwania > 20 minut (odcinki seriali)
            val duration = Duration.between(program.startUtc, program.endUtc).toMinutes()
            val isEpisodeLength = duration > 20

            android.util.Log.d("EpgRepository", "  ${program.title}: hasSeries=$hasSeriesCategory, hasEpisode=$hasEpisodeInfo, noUnwanted=$noUnwantedCategories, duration=$duration min")

            (hasSeriesCategory || hasEpisodeInfo) && noUnwantedCategories && isEpisodeLength
        }

        android.util.Log.d("EpgRepository", "Series found: ${series.size}")

        // Sortuj po czasie startu (najnowsze najpierw)
        // Usuń duplikaty (ten sam serial na różnych kanałach)
        // Weź 10 unikalnych seriali
        val uniqueSeries = series
            .sortedByDescending { it.startUtc }
            .distinctBy { it.title.lowercase().trim() }
            .take(10)

        android.util.Log.d("EpgRepository", "Unique series after deduplication: ${uniqueSeries.size}")

        return uniqueSeries
    }

    suspend fun getLast24HoursSports(): List<EpgProgram> {
        val now = Instant.now()
        val last24Hours = now.minus(Duration.ofHours(24))

        android.util.Log.d("EpgRepository", "getLast24HoursSports: $last24Hours to $now")

        // Pobierz wszystkie programy z ostatnich 24h
        val allPrograms = programDao.getProgramsInTimeWindow(last24Hours, now)
            .map { it.toEpgProgram() }

        android.util.Log.d("EpgRepository", "Total programs in last 24h: ${allPrograms.size}")

        // Słowa kluczowe dla sportu (rozszerzona lista)
        val sportKeywords = setOf(
            "sport", "mecz", "match", "rozgrywki",
            "piłka nożna", "football", "soccer", "tenis", "tennis",
            "siatkówka", "volleyball", "koszykówka", "basketball",
            "liga", "puchar", "mistrzostwa", "championship",
            "formuła", "formula", "wyścig", "race", "golf",
            "hokej", "hockey", "boks", "boxing", "rugby",
            "skoki", "jumping", "narciarstwo", "skiing"
        )

        // Filtruj programy sportowe z wybranych kanałów
        val sports = allPrograms
            .filter { it.channelId in PREFERRED_EPG_CHANNELS }  // Filtruj po preferowanych kanałach
            .filter { program ->
            program.categories.any { category ->
                sportKeywords.any { keyword ->
                    category.lowercase().contains(keyword)
                }
            }
        }

        android.util.Log.d("EpgRepository", "Sports found: ${sports.size}")

        // Sortuj po czasie startu (najnowsze najpierw)
        // Usuń duplikaty
        // Weź 10 unikalnych programów sportowych
        val uniqueSports = sports
            .sortedByDescending { it.startUtc }
            .distinctBy { it.title.lowercase().trim() }
            .take(10)

        android.util.Log.d("EpgRepository", "Unique sports after deduplication: ${uniqueSports.size}")

        return uniqueSports
    }

    suspend fun getLast24HoursGameShows(): List<EpgProgram> {
        val now = Instant.now()
        val last24Hours = now.minus(Duration.ofHours(24))

        android.util.Log.d("EpgRepository", "getLast24HoursGameShows: $last24Hours to $now")

        // Pobierz wszystkie programy z ostatnich 24h
        val allPrograms = programDao.getProgramsInTimeWindow(last24Hours, now)
            .map { it.toEpgProgram() }

        android.util.Log.d("EpgRepository", "Total programs in last 24h: ${allPrograms.size}")

        // Kategorie teleTurniejowe (tylko teleturnieje)
        val gameShowCategories = setOf("teleturniej")

        // Filtruj tylko teleturnieje z wybranych kanałów
        val gameShows = allPrograms
            .filter { it.channelId in PREFERRED_EPG_CHANNELS }  // Filtruj po preferowanych kanałach
            .filter { program ->
            program.categories.any { category ->
                gameShowCategories.any { gameShowCategory ->
                    category.lowercase().contains(gameShowCategory)
                }
            }
        }

        android.util.Log.d("EpgRepository", "Game shows found: ${gameShows.size}")

        // Sortuj po czasie startu (najnowsze najpierw)
        // Usuń duplikaty
        // Weź 10 unikalnych teleturniej
        val uniqueGameShows = gameShows
            .sortedByDescending { it.startUtc }
            .distinctBy { it.title.lowercase().trim() }
            .take(10)

        android.util.Log.d("EpgRepository", "Unique game shows after deduplication: ${uniqueGameShows.size}")

        return uniqueGameShows
    }

    // Clear EPG cache (force refresh on next load)
    suspend fun clearCache() {
        android.util.Log.d("EpgRepository", "=== Clearing EPG cache ===")
        metadataDao.deleteMetadata(EPG_METADATA_KEY)
        channelDao.deleteAllChannels()
        programDao.deleteAllPrograms()
        android.util.Log.d("EpgRepository", "EPG cache cleared")
    }

    // Background refresh - call from Application or Service
    fun startBackgroundRefresh() {
        android.util.Log.d("EpgRepository", "=== startBackgroundRefresh called ===")
        repositoryScope.launch {
            try {
                val expired = isCacheExpired()
                val empty = isEmpty()
                android.util.Log.d("EpgRepository", "Cache expired: $expired, isEmpty: $empty")

                if (expired || empty) {
                    android.util.Log.d("EpgRepository", "Refreshing EPG data...")
                    val now = Instant.now()
                    val startOfDay = now.atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                    val endOfDay = startOfDay.plus(Duration.ofDays(1))

                    android.util.Log.d("EpgRepository", "Time range: $startOfDay to $endOfDay")
                    val result = refreshEpgData(
                        startTime = startOfDay,
                        endTime = endOfDay,
                        maxChannels = MAX_CHANNELS_LOAD
                    )
                    android.util.Log.d("EpgRepository", "EPG refreshed: ${result.channels.size} channels, ${result.programs.size} programs")
                } else {
                    android.util.Log.d("EpgRepository", "EPG cache is fresh, skipping refresh")
                }
            } catch (e: Exception) {
                // Log error but don't crash app
                android.util.Log.e("EpgRepository", "Background refresh failed", e)
            }
        }
    }
    
    // Force refresh with current full day window
    suspend fun refreshFullDay(): EpgGuide {
        val (start, end) = getFullDayWindow()
        android.util.Log.d("EpgRepository", "Refreshing full day: $start to $end")
        return refreshEpgData(start, end, MAX_CHANNELS_LOAD)
    }
    
    // Private implementation
    private suspend fun getCachedEpgGuide(
        startTime: Instant,
        endTime: Instant,
        maxChannels: Int
    ): EpgGuide? {
        return try {
            val channels = channelDao.getAllChannels().take(maxChannels).map { it.toEpgChannel() }
            if (channels.isEmpty()) return null
            
            val channelIds = channels.map { it.id }
            val programs = programDao.getProgramsForChannels(channelIds, startTime, endTime)
                .map { it.toEpgProgram() }
            
            EpgGuide(channels = channels, programs = programs)
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun refreshEpgData(
        startTime: Instant,
        endTime: Instant,
        maxChannels: Int
    ): EpgGuide {
        return withContext(Dispatchers.IO) {
            // Get EPG IDs from ChannelManager (if available) for intelligent filtering
            // Combine original 9 channels from JSON + 28 additional channels from PREFERRED_EPG_CHANNELS
            val filterIds = if (ChannelManager.isInitialized()) {
                val jsonIds = ChannelManager.getEpgIds()  // 9 channels from tv_channels_with_streams.json
                val combinedIds = (jsonIds + PREFERRED_EPG_CHANNELS).toSet()  // 9 + 28 extras = 37 unique
                combinedIds.also { ids ->
                    android.util.Log.d("EpgRepository", "Intelligent filtering enabled: ${ids.size} channels (${jsonIds.size} from JSON + ${PREFERRED_EPG_CHANNELS.size} extras)")
                    android.util.Log.d("EpgRepository", "JSON IDs: ${jsonIds.joinToString()}")
                    android.util.Log.d("EpgRepository", "Combined IDs: ${ids.joinToString()}")
                }
            } else {
                PREFERRED_EPG_CHANNELS.also {
                    android.util.Log.w("EpgRepository", "ChannelManager not initialized, using PREFERRED_EPG_CHANNELS only (${it.size} channels)")
                }
            }

            // Parse fresh data from network with intelligent filtering
            val freshGuide = XmlTvParser.parseUrlWindowForTopChannels(
                url = "https://epg.ovh/pltv.gz",
                maxChannels = maxChannels,
                windowStart = startTime,
                windowEnd = endTime,
                filterChannelIds = filterIds  // Intelligent filtering parameter
            )

            android.util.Log.d("EpgRepository", "EPG loaded: ${freshGuide.channels.size} channels, ${freshGuide.programs.size} programs")

            // Save to database
            saveToCache(freshGuide)

            // Update metadata
            metadataDao.insertMetadata(
                EpgMetadata(
                    key = EPG_METADATA_KEY,
                    lastUpdated = Instant.now(),
                    totalChannels = freshGuide.channels.size,
                    totalPrograms = freshGuide.programs.size
                )
            )

            freshGuide
        }
    }
    
    private suspend fun saveToCache(guide: EpgGuide) {
        // Save channels
        val channelEntities = guide.channels.mapIndexed { index, channel ->
            ChannelEntity(
                id = channel.id,
                name = channel.name,
                sortOrder = index
            )
        }
        channelDao.insertChannels(channelEntities)
        
        // Save programs
        val programEntities = guide.programs.map { program ->
            ProgramEntity(
                channelId = program.channelId,
                title = program.title,
                subTitle = program.subTitle,
                description = program.description,
                startUtc = program.startUtc,
                endUtc = program.endUtc,
                categories = program.categories.joinToString(","),
                iconUrl = program.iconUrl
            )
        }
        programDao.insertPrograms(programEntities)
        
        // Clean old programs (older than 1 day)
        programDao.deleteOldPrograms(Instant.now().minus(Duration.ofDays(1)))
    }
    
    private suspend fun isCacheExpired(): Boolean {
        val metadata = metadataDao.getMetadata(EPG_METADATA_KEY)
        return metadata == null || 
               Duration.between(metadata.lastUpdated, Instant.now()).toHours() > CACHE_DURATION_HOURS
    }
    
    private suspend fun isEmpty(): Boolean {
        val channels = channelDao.getAllChannels()
        android.util.Log.d("EpgRepository", "isEmpty: ${channels.size} channels in DB")
        if (channels.isNotEmpty()) {
            android.util.Log.d("EpgRepository", "First 20 channel IDs:")
            channels.take(20).forEach {
                android.util.Log.d("EpgRepository", "  - ${it.id}")
            }
        }
        return channels.isEmpty()
    }
    
    // Debug function to log all channels and program counts
    suspend fun debugLogChannelIds() {
        val channels = channelDao.getAllChannels()
        val now = Instant.now()
        val startOfDay = now.atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(Duration.ofDays(1))

        android.util.Log.d("EPG_DEBUG", "=== EPG DATABASE CHANNELS (${channels.size} total) ===")
        channels.forEach { channel ->
            val programCount = programDao.getProgramsForChannel(channel.id, startOfDay, endOfDay).size
            android.util.Log.d("EPG_DEBUG", "  ID: '${channel.id}' | Name: '${channel.name}' | Programs today: $programCount")
        }
        android.util.Log.d("EPG_DEBUG", "=== END CHANNEL LIST ===")
    }

    // Helper function for consistent full day time windows
    private fun getFullDayWindow(): Pair<Instant, Instant> {
        val now = Instant.now()
        val startOfDay = now.atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(Duration.ofDays(1))
        return Pair(startOfDay, endOfDay)
    }
}

// Extension functions for conversion
private fun ChannelEntity.toEpgChannel() = EpgChannel(id = id, name = name)

private fun ProgramEntity.toEpgProgram() = EpgProgram(
    channelId = channelId,
    title = title,
    startUtc = startUtc,
    endUtc = endUtc,
    subTitle = subTitle,
    description = description,
    categories = categories?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    iconUrl = iconUrl
)