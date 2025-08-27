package com.example.tv.repository

import android.content.Context
import androidx.room.Room
import com.example.tv.database.*
import com.example.tv.epg.*
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
        private const val MAX_CHANNELS_LOAD = 50
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
        return programDao.getCurrentProgramForChannel(channelId, now)?.toEpgProgram()
    }
    
    suspend fun getFullDayPrograms(channelId: String, date: Instant): List<EpgProgram> {
        val startOfDay = date.atZone(java.time.ZoneId.systemDefault())
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
        val endOfDay = startOfDay.plus(Duration.ofDays(1))
        
        return programDao.getProgramsForChannel(channelId, startOfDay, endOfDay)
            .map { it.toEpgProgram() }
    }
    
    // Background refresh - call from Application or Service
    fun startBackgroundRefresh() {
        repositoryScope.launch {
            try {
                if (isCacheExpired() || isEmpty()) {
                    val now = Instant.now()
                    val startOfDay = now.atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                    val endOfDay = startOfDay.plus(Duration.ofDays(1))
                    refreshEpgData(
                        startTime = startOfDay,
                        endTime = endOfDay,
                        maxChannels = MAX_CHANNELS_LOAD
                    )
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
            // Parse fresh data from network
            val freshGuide = XmlTvParser.parseUrlWindowForTopChannels(
                url = "https://epg.ovh/pltv.gz",
                maxChannels = maxChannels,
                windowStart = startTime,
                windowEnd = endTime
            )
            
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
        return channelDao.getAllChannels().isEmpty()
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