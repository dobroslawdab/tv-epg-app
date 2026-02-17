package com.uxellence.tv.v3.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manager for handling app updates
 *
 * Features:
 * - Check for updates from Supabase
 * - Download APK using DownloadManager
 * - Install APK (triggers system installer)
 *
 * Usage:
 * ```
 * val updateManager = UpdateManager(context)
 * val updateInfo = updateManager.checkForUpdate()
 * if (updateInfo != null) {
 *     updateManager.downloadAndInstall(updateInfo)
 * }
 * ```
 */
class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val APK_FILE_NAME = "app_update.apk"
    }

    private val repository = UpdateRepository()
    private var downloadId: Long = -1

    /**
     * Get current app version code
     */
    fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current version code", e)
            0
        }
    }

    /**
     * Get current app version name
     */
    fun getCurrentVersionName(): String {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current version name", e)
            "unknown"
        }
    }

    /**
     * Check for updates
     * Returns AppUpdateInfo if update is available, null otherwise
     */
    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val currentVersionCode = getCurrentVersionCode()
        Log.d(TAG, "Current version: $currentVersionCode")

        val result = repository.fetchLatestVersion()
        val latestVersion = result.getOrNull()

        if (latestVersion != null && latestVersion.versionCode > currentVersionCode) {
            Log.d(TAG, "Update available: ${latestVersion.versionName} (${latestVersion.versionCode})")
            latestVersion
        } else {
            Log.d(TAG, "No update available")
            null
        }
    }

    /**
     * Download APK using DownloadManager
     * Returns download ID for tracking
     */
    fun downloadApk(updateInfo: AppUpdateInfo): Long {
        Log.d(TAG, "Starting download from: ${updateInfo.apkUrl}")

        // Delete old APK if exists
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (apkFile.exists()) {
            apkFile.delete()
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("BOX TV Update")
            .setDescription("Pobieranie wersji ${updateInfo.versionName}...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        downloadId = downloadManager.enqueue(request)
        Log.d(TAG, "Download started with ID: $downloadId")

        return downloadId
    }

    /**
     * Install downloaded APK
     * Call this after download is complete
     */
    fun installApk() {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
            return
        }

        Log.d(TAG, "Installing APK: ${apkFile.absolutePath}")

        val apkUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(intent)
    }

    /**
     * Delete downloaded APK to free up space
     * Call this after successful installation or when user cancels
     */
    fun cleanupDownloadedApk() {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (apkFile.exists()) {
            val deleted = apkFile.delete()
            Log.d(TAG, "Cleanup APK: ${if (deleted) "success" else "failed"}")
        }
    }

    /**
     * Register receiver to handle download completion
     */
    fun registerDownloadReceiver(onDownloadComplete: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    Log.d(TAG, "Download complete")
                    onDownloadComplete()
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        return receiver
    }

    /**
     * Cleanup resources
     */
    fun close() {
        repository.close()
    }
}
