package com.uxellence.tv.v3.model

import java.time.Instant
import java.time.Duration

/**
 * Recording Status - calculated based on time comparison with now
 *
 * Used for badge display in RecordingCard:
 * - RECORDED: checkmark + "Ogladaj do DD.MM.YYYY"
 * - RECORDING: red "REC" badge
 * - SCHEDULED: white "REC" badge + 30% opacity
 */
enum class RecordingStatus {
    RECORDED,    // Past: endUtc < now
    RECORDING,   // Now: startUtc <= now < endUtc
    SCHEDULED    // Future: startUtc > now
}

/**
 * Card display type for RecordingCard component
 * Maps to Figma RecordingCardBig variants
 */
enum class RecordingCardType {
    OBEJRZANE,      // Watched: checkmark + date, progress 100%
    OGLADAJ,        // Watching: checkmark + date, partial progress
    SERIA,          // Series bundle: stack icon, "20 odcinkow (11 h)"
    NAGRYWANIE,     // Recording now: red "REC", partial progress
    ZAPLANOWANE     // Scheduled: white "REC", 30% opacity
}

/**
 * Individual recording (single movie or TV program episode)
 *
 * Generated from EPG data with status calculated based on time
 */
data class Recording(
    val id: String,                    // Unique ID: "rec_{channelId}_{startUtcEpoch}"
    val title: String,                 // Program title
    val description: String?,          // Program description (may contain S01E01 patterns)
    val subTitle: String?,             // Episode subtitle (e.g., "S01E05 - The Beginning")
    val channelId: String,             // EPG channel ID
    val channelName: String,           // Display name (e.g., "TVP 1")
    val channelLogoUrl: String?,       // Channel logo URL
    val startUtc: Instant,             // Recording start time
    val endUtc: Instant,               // Recording end time
    val imageUrl: String?,             // Program thumbnail/poster URL
    val categories: List<String>,      // Program categories (e.g., ["serial", "dramat"])
    val status: RecordingStatus,       // Calculated status
    val seriesId: String? = null,      // null = individual, non-null = part of series
    val watchProgress: Float = 0f,     // 0.0 to 1.0 for progress bar
    val expirationDate: Instant? = null // "Ogladaj do" date
) {
    /**
     * Duration in minutes
     */
    val durationMinutes: Long
        get() = Duration.between(startUtc, endUtc).toMinutes()

    /**
     * Duration formatted as hours and minutes (e.g., "1 h 23 min" or "45 min")
     */
    val durationFormatted: String
        get() {
            val hours = durationMinutes / 60
            val mins = durationMinutes % 60
            return when {
                hours > 0 && mins > 0 -> "${hours} h ${mins} min"
                hours > 0 -> "${hours} h"
                else -> "${mins} min"
            }
        }

    /**
     * Get card type based on status and watch progress
     */
    fun toCardType(): RecordingCardType {
        return when (status) {
            RecordingStatus.RECORDED -> {
                if (watchProgress >= 1.0f) RecordingCardType.OBEJRZANE
                else if (watchProgress > 0f) RecordingCardType.OGLADAJ
                else RecordingCardType.OBEJRZANE // Default to watched for recorded
            }
            RecordingStatus.RECORDING -> RecordingCardType.NAGRYWANIE
            RecordingStatus.SCHEDULED -> RecordingCardType.ZAPLANOWANE
        }
    }

    companion object {
        /**
         * Calculate recording status based on current time
         */
        fun calculateStatus(startUtc: Instant, endUtc: Instant, now: Instant = Instant.now()): RecordingStatus {
            return when {
                now.isBefore(startUtc) -> RecordingStatus.SCHEDULED
                now.isAfter(endUtc) -> RecordingStatus.RECORDED
                else -> RecordingStatus.RECORDING
            }
        }
    }
}

/**
 * Series bundle containing multiple episodes grouped by title
 *
 * Displayed as single tile in grid with episode count badge
 * Click opens drill-down to individual episodes
 */
data class SeriesBundle(
    val id: String,                    // Unique ID: "series_{normalizedTitle}"
    val title: String,                 // Series name (e.g., "Klan", "M jak milosc")
    val imageUrl: String?,             // Most recent episode thumbnail
    val channelLogoUrl: String?,       // Primary channel logo
    val episodes: List<Recording>,     // All episodes in bundle
    val lastRecordedDate: Instant?     // For sorting "most recently recorded"
) {
    /**
     * Total number of episodes
     */
    val episodeCount: Int
        get() = episodes.size

    /**
     * Count of episodes with RECORDED status
     */
    val recordedCount: Int
        get() = episodes.count { it.status == RecordingStatus.RECORDED }

    /**
     * Count of episodes with RECORDING status
     */
    val recordingCount: Int
        get() = episodes.count { it.status == RecordingStatus.RECORDING }

    /**
     * Count of episodes with SCHEDULED status
     */
    val scheduledCount: Int
        get() = episodes.count { it.status == RecordingStatus.SCHEDULED }

    /**
     * Total duration of all episodes in minutes
     */
    val totalDurationMinutes: Long
        get() = episodes.sumOf { it.durationMinutes }

    /**
     * Total duration formatted (e.g., "11 h" or "2 h 30 min")
     */
    val totalDurationFormatted: String
        get() {
            val hours = totalDurationMinutes / 60
            val mins = totalDurationMinutes % 60
            return when {
                hours > 0 && mins > 0 -> "${hours} h ${mins} min"
                hours > 0 -> "${hours} h"
                else -> "${mins} min"
            }
        }

    /**
     * Subtitle for card display: "20 odcinkow (11 h)"
     */
    val cardSubtitle: String
        get() = "$episodeCount odcinkow ($totalDurationFormatted)"
}

/**
 * Sealed class for grid content - can be individual recording or series bundle
 *
 * Used by RecordingsGridScreen to handle mixed content
 */
sealed class RecordingContent {
    data class Individual(val recording: Recording) : RecordingContent()
    data class Series(val bundle: SeriesBundle) : RecordingContent()

    /**
     * Get title for display
     */
    val title: String
        get() = when (this) {
            is Individual -> recording.title
            is Series -> bundle.title
        }

    /**
     * Get image URL for display
     */
    val imageUrl: String?
        get() = when (this) {
            is Individual -> recording.imageUrl
            is Series -> bundle.imageUrl
        }

    /**
     * Get channel logo URL
     */
    val channelLogoUrl: String?
        get() = when (this) {
            is Individual -> recording.channelLogoUrl
            is Series -> bundle.channelLogoUrl
        }

    /**
     * Check if this is a series bundle
     */
    val isSeries: Boolean
        get() = this is Series
}

/**
 * Storage info for the recordings panel
 * Shows used/remaining space
 */
data class RecordingStorageInfo(
    val usedHours: Int,        // e.g., 220
    val remainingHours: Int,   // e.g., 80
    val totalHours: Int        // e.g., 300
) {
    val usedPercentage: Float
        get() = if (totalHours > 0) usedHours.toFloat() / totalHours else 0f
}
