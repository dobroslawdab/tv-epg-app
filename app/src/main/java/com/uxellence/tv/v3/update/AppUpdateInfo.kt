package com.uxellence.tv.v3.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data class representing app update information from Supabase
 *
 * Table: app_updates
 * Columns: id, version_code, version_name, apk_url, release_notes, force_update, created_at
 */
@Serializable
data class AppUpdateInfo(
    @SerialName("id")
    val id: Int = 0,

    @SerialName("version_code")
    val versionCode: Int,

    @SerialName("version_name")
    val versionName: String,

    @SerialName("apk_url")
    val apkUrl: String,

    @SerialName("release_notes")
    val releaseNotes: String? = null,

    @SerialName("force_update")
    val forceUpdate: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null
)
