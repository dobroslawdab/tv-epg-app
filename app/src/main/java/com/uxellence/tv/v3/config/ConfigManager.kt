package com.uxellence.tv.v3.config

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Singleton manager for app configuration
 * - Loads cached config on init
 * - Provides StateFlow for Compose observation
 * - Refreshes config from Supabase on demand
 */
object ConfigManager {
    private const val TAG = "ConfigManager"
    private const val PREFS_NAME = "app_config_cache"
    private const val KEY_CONFIG_JSON = "config_json"
    private const val KEY_LAST_REFRESH = "last_refresh_timestamp"

    private var _config: AppConfig = AppConfig.DEFAULT
    val config: AppConfig get() = _config

    // StateFlow for Compose observation
    private val _configState = MutableStateFlow(AppConfig.DEFAULT)
    val configState: StateFlow<AppConfig> = _configState.asStateFlow()

    // Last refresh status
    private val _lastRefreshStatus = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)
    val lastRefreshStatus: StateFlow<RefreshStatus> = _lastRefreshStatus.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Initialize ConfigManager - load cached config if available
     * Call this in Application.onCreate() or MainActivity.onCreate()
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing ConfigManager...")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CONFIG_JSON, null)

        if (jsonString != null) {
            try {
                _config = json.decodeFromString(jsonString)
                _configState.value = _config
                Log.d(TAG, "Loaded cached config: version=${_config.version}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse cached config, using defaults", e)
                _config = AppConfig.DEFAULT
                _configState.value = _config
            }
        } else {
            Log.d(TAG, "No cached config found, using defaults")
        }
    }

    /**
     * Refresh config from Supabase
     * @return Result with new config or error
     */
    suspend fun refreshConfig(context: Context): Result<AppConfig> {
        _lastRefreshStatus.value = RefreshStatus.Loading

        val repo = SupabaseConfigRepository(context)
        val result = repo.fetchConfig()

        result.onSuccess { newConfig ->
            _config = newConfig
            _configState.value = newConfig
            saveToCache(context, newConfig)
            _lastRefreshStatus.value = RefreshStatus.Success(
                message = "Pobrano konfigurację v${newConfig.version}",
                timestamp = System.currentTimeMillis()
            )
            Log.d(TAG, "Config refreshed successfully: version=${newConfig.version}")
        }

        result.onFailure { error ->
            _lastRefreshStatus.value = RefreshStatus.Error(
                message = "Błąd: ${error.message ?: "Nieznany błąd"}",
                timestamp = System.currentTimeMillis()
            )
            Log.e(TAG, "Failed to refresh config", error)
        }

        repo.close()
        return result
    }

    /**
     * Save config to SharedPreferences cache
     */
    private fun saveToCache(context: Context, config: AppConfig) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = json.encodeToString(config)
            prefs.edit()
                .putString(KEY_CONFIG_JSON, jsonString)
                .putLong(KEY_LAST_REFRESH, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "Config saved to cache")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config to cache", e)
        }
    }

    /**
     * Get timestamp of last successful refresh
     */
    fun getLastRefreshTimestamp(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_REFRESH, 0L)
    }

    /**
     * Clear cached config (for debugging)
     */
    fun clearCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        _config = AppConfig.DEFAULT
        _configState.value = _config
        _lastRefreshStatus.value = RefreshStatus.Idle
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Refresh status sealed class
     */
    sealed class RefreshStatus {
        data object Idle : RefreshStatus()
        data object Loading : RefreshStatus()
        data class Success(val message: String, val timestamp: Long) : RefreshStatus()
        data class Error(val message: String, val timestamp: Long) : RefreshStatus()
    }
}
