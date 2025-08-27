package com.example.tv.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllChannels(): List<ChannelEntity>
    
    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC")
    fun getAllChannelsFlow(): Flow<List<ChannelEntity>>
    
    @Query("SELECT * FROM channels WHERE id = :channelId")
    suspend fun getChannel(channelId: String): ChannelEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: ChannelEntity)
    
    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()
}

@Dao
interface ProgramDao {
    @Query("""
        SELECT * FROM programs 
        WHERE channelId = :channelId 
        AND startUtc >= :startTime 
        AND startUtc < :endTime 
        ORDER BY startUtc ASC
    """)
    suspend fun getProgramsForChannel(
        channelId: String, 
        startTime: Instant, 
        endTime: Instant
    ): List<ProgramEntity>
    
    @Query("""
        SELECT * FROM programs 
        WHERE channelId = :channelId 
        AND startUtc >= :startTime 
        AND startUtc < :endTime 
        ORDER BY startUtc ASC
    """)
    fun getProgramsForChannelFlow(
        channelId: String, 
        startTime: Instant, 
        endTime: Instant
    ): Flow<List<ProgramEntity>>
    
    @Query("""
        SELECT * FROM programs 
        WHERE startUtc >= :startTime 
        AND startUtc < :endTime 
        ORDER BY channelId ASC, startUtc ASC
    """)
    suspend fun getProgramsInTimeWindow(
        startTime: Instant, 
        endTime: Instant
    ): List<ProgramEntity>
    
    @Query("""
        SELECT * FROM programs 
        WHERE channelId IN (:channelIds)
        AND startUtc >= :startTime 
        AND startUtc < :endTime 
        ORDER BY channelId ASC, startUtc ASC
    """)
    suspend fun getProgramsForChannels(
        channelIds: List<String>,
        startTime: Instant, 
        endTime: Instant
    ): List<ProgramEntity>
    
    @Query("SELECT * FROM programs WHERE :now >= startUtc AND :now < endUtc")
    suspend fun getCurrentPrograms(now: Instant): List<ProgramEntity>
    
    @Query("""
        SELECT * FROM programs 
        WHERE channelId = :channelId 
        AND :now >= startUtc 
        AND :now < endUtc 
        LIMIT 1
    """)
    suspend fun getCurrentProgramForChannel(channelId: String, now: Instant): ProgramEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<ProgramEntity>)
    
    @Query("DELETE FROM programs WHERE startUtc < :beforeTime")
    suspend fun deleteOldPrograms(beforeTime: Instant)
    
    @Query("DELETE FROM programs WHERE channelId = :channelId")
    suspend fun deleteProgramsForChannel(channelId: String)
    
    @Query("DELETE FROM programs")
    suspend fun deleteAllPrograms()
}

@Dao
interface EpgMetadataDao {
    @Query("SELECT * FROM epg_metadata WHERE key = :key")
    suspend fun getMetadata(key: String): EpgMetadata?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: EpgMetadata)
    
    @Query("DELETE FROM epg_metadata WHERE key = :key")
    suspend fun deleteMetadata(key: String)
}