package com.uxellence.tv.v3.epg

import java.time.Instant

data class EpgProgram(
    val channelId: String,
    val title: String,
    val startUtc: Instant,
    val endUtc: Instant,
    val subTitle: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val iconUrl: String? = null
)

data class EpgChannel(
    val id: String,
    val name: String
)

data class EpgGuide(
    val channels: List<EpgChannel>,
    val programs: List<EpgProgram>
)


