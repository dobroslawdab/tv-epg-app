package com.uxellence.tv.v3

import com.uxellence.tv.v3.model.*
import com.uxellence.tv.v3.version001.VodContent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * RECORDING ADAPTER
 *
 * Converts Recording and SeriesBundle models to VodContent for display
 * in existing MOJE section components (MiniatureContent, HorizontalVodCard, etc.)
 *
 * This adapter allows recordings to be displayed using the existing
 * UI infrastructure while maintaining the recording-specific data model.
 */
object RecordingAdapter {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .withZone(ZoneId.systemDefault())

    /**
     * Convert Recording to VodContent
     *
     * Maps recording fields to VodContent structure for display
     */
    fun toVodContent(recording: Recording): VodContent {
        return VodContent(
            id = recording.id,
            title = recording.title,
            description = buildRecordingDescription(recording),
            category = buildCategoryLabel(recording),
            imageUrl = recording.imageUrl ?: "",
            channelLogoUrl = recording.channelLogoUrl ?: "",
            link = "",  // Not used for recordings
            price = null
        )
    }

    /**
     * Convert SeriesBundle to VodContent
     *
     * Maps series bundle to display as single tile
     */
    fun toVodContent(bundle: SeriesBundle): VodContent {
        return VodContent(
            id = bundle.id,
            title = bundle.title,
            description = bundle.cardSubtitle,  // "20 odcinków (11 h)"
            category = "Seria",
            imageUrl = bundle.imageUrl ?: "",
            channelLogoUrl = bundle.channelLogoUrl ?: "",
            link = "",
            price = null
        )
    }

    /**
     * Convert RecordingContent (sealed class) to VodContent
     */
    fun toVodContent(content: RecordingContent): VodContent {
        return when (content) {
            is RecordingContent.Individual -> toVodContent(content.recording)
            is RecordingContent.Series -> toVodContent(content.bundle)
        }
    }

    /**
     * Convert list of recordings to VodContent list
     */
    fun recordingsToVodContent(recordings: List<Recording>): List<VodContent> {
        return recordings.map { toVodContent(it) }
    }

    /**
     * Convert list of series bundles to VodContent list
     */
    fun bundlesToVodContent(bundles: List<SeriesBundle>): List<VodContent> {
        return bundles.map { toVodContent(it) }
    }

    /**
     * Convert mixed RecordingContent list to VodContent list
     */
    fun contentListToVodContent(contentList: List<RecordingContent>): List<VodContent> {
        return contentList.map { toVodContent(it) }
    }

    /**
     * Build description string for recording display
     *
     * Format: "odc. X sez. Y (Xh Ymin)" or just duration
     */
    private fun buildRecordingDescription(recording: Recording): String {
        val parts = mutableListOf<String>()

        // Add episode/subtitle info
        recording.subTitle?.let { parts.add(it) }

        // Add duration
        parts.add("(${recording.durationFormatted})")

        // Add expiration if available
        recording.expirationDate?.let { expDate ->
            val formattedDate = dateFormatter.format(expDate)
            parts.add("Do $formattedDate")
        }

        return parts.joinToString(" ")
    }

    /**
     * Build category label based on recording status
     */
    private fun buildCategoryLabel(recording: Recording): String {
        return when (recording.status) {
            RecordingStatus.RECORDED -> "Nagrane"
            RecordingStatus.RECORDING -> "Nagrywanie"
            RecordingStatus.SCHEDULED -> "Zaplanowane"
        }
    }

    /**
     * Get status badge text for recording
     */
    fun getStatusBadge(recording: Recording): String {
        return when (recording.status) {
            RecordingStatus.RECORDED -> recording.expirationDate?.let {
                "Oglądaj do ${dateFormatter.format(it)}"
            } ?: "Nagrane"
            RecordingStatus.RECORDING -> "REC"
            RecordingStatus.SCHEDULED -> "ZAPLANOWANE"
        }
    }

    /**
     * Get status badge text for series bundle
     */
    fun getStatusBadge(bundle: SeriesBundle): String {
        return "${bundle.episodeCount} odc."
    }
}
