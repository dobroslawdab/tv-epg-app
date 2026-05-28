package com.uxellence.tv.v3.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

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
    var downloadProgress: Int = 0
        private set

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
     * Download APK using HttpURLConnection (handles GitHub redirects, works with private repos).
     * Calls onProgress with percentage (0-100) and onComplete when done.
     */
    suspend fun downloadApk(
        updateInfo: AppUpdateInfo,
        onProgress: (Int) -> Unit = {},
        onComplete: (success: Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting download from: ${updateInfo.apkUrl}")

        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (apkFile.exists()) apkFile.delete()

        try {
            val conn = URL(updateInfo.apkUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.connect()

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed: HTTP $responseCode")
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }

            val totalSize = conn.contentLength.toLong()
            val input = conn.inputStream
            val output = java.io.FileOutputStream(apkFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            try {
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        val progress = ((totalRead * 100) / totalSize).toInt()
                        downloadProgress = progress
                        withContext(Dispatchers.Main) { onProgress(progress) }
                    }
                }
                // Force buffered bytes to disk BEFORE we tell the UI the
                // download is complete. Without flush + fd.sync the user can
                // tap "Zainstaluj" before the file is actually visible to
                // PackageInstaller (read returns 0 bytes / file size mismatch
                // → silent install failure with no UI feedback).
                output.flush()
                output.fd.sync()
            } finally {
                output.close()
                input.close()
                conn.disconnect()
            }

            // Verify the file landed and matches the expected size before we
            // hand off to UI. Failed integrity → cleanup + report failure
            // so the dialog drops back to READY state instead of getting stuck
            // showing "Zainstaluj" over a half-written file.
            val written = apkFile.length()
            if (totalSize > 0 && written != totalSize) {
                Log.e(TAG, "Download size mismatch: expected=$totalSize actual=$written")
                apkFile.delete()
                withContext(Dispatchers.Main) { onComplete(false) }
                return@withContext
            }

            Log.d(TAG, "Download complete: ${apkFile.absolutePath} ($written bytes)")
            withContext(Dispatchers.Main) { onComplete(true) }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            apkFile.delete()
            withContext(Dispatchers.Main) { onComplete(false) }
        }
    }

    /**
     * Returns true when the previously-downloaded APK is on disk and matches
     * what the system installer expects (readable + non-zero size).
     */
    fun isApkReady(): Boolean {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        return apkFile.exists() && apkFile.canRead() && apkFile.length() > 0
    }

    /**
     * Install downloaded APK. Returns a sealed [InstallResult] so the UI can
     * react: success → dismiss dialog, file-not-ready → tell user to wait,
     * error → drop back to download state. Previously this returned Unit and
     * silently logged failures — users tapped "Zainstaluj" and "nothing
     * happened" because the file hadn't fully flushed yet, or because
     * ACTION_VIEW threw an ActivityNotFoundException on box ROMs without a
     * built-in PackageInstaller.
     */
    fun installApk(): InstallResult {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.w(TAG, "installApk: file not ready (exists=${apkFile.exists()}, size=${apkFile.length()})")
            return InstallResult.NotReady
        }

        Log.d(TAG, "Installing APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

        val apkUri: Uri = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider.getUriForFile failed", e)
            return InstallResult.Error("FileProvider: ${e.message}")
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        return try {
            context.startActivity(intent)
            InstallResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "startActivity for installer failed", e)
            InstallResult.Error("Installer not available: ${e.message}")
        }
    }

    sealed class InstallResult {
        object Success : InstallResult()
        object NotReady : InstallResult()
        data class Error(val message: String) : InstallResult()
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
     * Cleanup resources
     */
    fun close() {
        repository.close()
    }
}
