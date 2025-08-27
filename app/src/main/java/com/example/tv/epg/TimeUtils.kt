package com.example.tv.epg

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object TimeUtils {
    fun toLocalTimeString(instant: Instant): String {
        val z = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
        return String.format("%02d:%02d", z.hour, z.minute)
    }
}


