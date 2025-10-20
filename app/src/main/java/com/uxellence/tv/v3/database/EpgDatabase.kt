package com.uxellence.tv.v3.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant

@TypeConverters(EpgTypeConverters::class)
@Database(
    entities = [ChannelEntity::class, ProgramEntity::class, EpgMetadata::class],
    version = 1,
    exportSchema = false
)
abstract class EpgDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun programDao(): ProgramDao
    abstract fun metadataDao(): EpgMetadataDao
    
    companion object {
        const val DATABASE_NAME = "epg_database"
        
        // Migration from version 1 to 2 (example for future use)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration code
                // database.execSQL("ALTER TABLE channels ADD COLUMN newColumn TEXT")
            }
        }
    }
}

class EpgTypeConverters {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.epochSecond
    }
    
    @TypeConverter
    fun toInstant(epochSecond: Long?): Instant? {
        return epochSecond?.let { Instant.ofEpochSecond(it) }
    }
}