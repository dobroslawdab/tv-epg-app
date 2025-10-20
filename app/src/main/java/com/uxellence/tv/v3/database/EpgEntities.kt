package com.uxellence.tv.v3.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "channels",
    indices = [Index(value = ["id"], unique = true)]
)
data class ChannelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val displayName: String? = null,
    val iconUrl: String? = null,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "programs",
    indices = [
        Index(value = ["channelId"]),
        Index(value = ["startUtc"]),
        Index(value = ["endUtc"]),
        Index(value = ["channelId", "startUtc"], unique = true)
    ]
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: String,
    val title: String,
    val subTitle: String? = null,
    val description: String? = null,
    val startUtc: Instant,
    val endUtc: Instant,
    val categories: String? = null, // JSON string of categories list
    val iconUrl: String? = null,
    val eventId: String? = null
)

@Entity(tableName = "epg_metadata")
data class EpgMetadata(
    @PrimaryKey
    val key: String,
    val lastUpdated: Instant,
    val version: String? = null,
    val totalChannels: Int = 0,
    val totalPrograms: Int = 0
)