package com.uxellence.tv.v3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.uxellence.tv.v3.channels.ChannelManager
import com.uxellence.tv.v3.config.ConfigManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

/**
 * LauncherActivity - Entry point when app is set as Android TV Launcher
 *
 * Purpose: Handles HOME button intent when user sets this app as their TV launcher.
 * When the HOME button is pressed on the remote, Android TV automatically launches
 * this activity.
 *
 * Behavior:
 * - Starts navigation at TopMenuScreen2 (ODKRYWAJ/START section)
 * - Same initialization as MainActivity (VodDataCache, ChannelManager, Polish locale)
 * - Skips SPLASH screen for immediate launcher experience
 *
 * User Setup:
 * 1. Open Android TV Settings
 * 2. Navigate to Apps → See all apps
 * 3. Find "BOX TV" app
 * 4. Select "Open by default" or "Set as Home app"
 * 5. Choose "BOX TV" as default Home app
 *
 * After setup:
 * - HOME button → LauncherActivity → TopMenuScreen2
 * - App drawer → EpgActivity → EpgDayScreen (with overlay)
 *
 * Technical Notes:
 * - Intent filter in AndroidManifest.xml includes CATEGORY_HOME
 * - launchMode="singleTask" ensures only one instance exists
 * - HOME button behavior is automatic when app is set as launcher
 *
 * @see EpgActivity for app drawer entry point
 * @see MainActivity for development/testing entry point
 */
class LauncherActivity : ComponentActivity() {
    // State to trigger HOME button navigation
    // Incrementing this value triggers navigation to TopMenuScreen2/START
    private val homePressedTrigger = mutableStateOf(0)

    // BroadcastReceiver for HOME button events from AccessibilityService
    private val homeButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == HomeButtonAccessibilityService.HOME_PRESSED_ACTION) {
                android.util.Log.d("LAUNCHER_HOME", "HOME from AccessibilityService - navigating to START")
                // Increment trigger - TvRoot will detect change and navigate
                homePressedTrigger.value++
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force Polish locale for the entire application
        setAppLocale(this, "pl")

        // Initialize VOD data cache once at startup (avoids repeated I/O)
        VodDataCache.initialize(this)

        // Initialize ChannelManager with TV channels database
        ChannelManager.initialize(this)

        // Initialize ConfigManager - load cached config
        ConfigManager.initialize(this)
        // Auto-refresh config from Supabase on app startup
        lifecycleScope.launch {
            ConfigManager.refreshConfig(this@LauncherActivity)
        }

        // Register BroadcastReceiver for HOME button events from AccessibilityService
        val filter = IntentFilter(HomeButtonAccessibilityService.HOME_PRESSED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(homeButtonReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(homeButtonReceiver, filter)
        }

        // Start at TopMenuScreen2 (ODKRYWAJ section) - launcher mode
        setContent {
            TvRoot(
                startScreen = NavigationScreen.TOP_MENU2,
                homePressedTrigger = homePressedTrigger.value  // Pass trigger to TvRoot
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister BroadcastReceiver to prevent memory leaks
        try {
            unregisterReceiver(homeButtonReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered - safe to ignore
        }
    }

    /**
     * Override onNewIntent to detect HOME button presses
     *
     * STANDARD ANDROID API: When this app is set as the launcher and the user
     * presses HOME button while the app is already running, Android Framework
     * sends a new Intent with ACTION_MAIN + CATEGORY_HOME to this method.
     *
     * This is the CORRECT way to detect HOME button in launcher apps
     * (not KeyEvent interception, which is blocked by Android security).
     *
     * Navigation behavior:
     * - From EPG Day Test with PIP → Return to fullscreen EPG (like Key.Zero)
     * - From EPG Day Test without PIP → Navigate to TopMenuScreen2/START with PIP
     * - From TopMenuScreen2 → Navigate to TopMenuScreen2/START (refresh)
     *
     * @see MainActivity LaunchedEffect(homePressedTrigger) for navigation logic
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Check if this is a HOME button press (ACTION_MAIN + CATEGORY_HOME)
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)) {

            android.util.Log.d("LAUNCHER_HOME", "HOME button detected via onNewIntent - incrementing trigger")

            // Increment trigger - TvRoot will detect change and navigate accordingly
            // (see MainActivity.kt LaunchedEffect(homePressedTrigger) for logic)
            homePressedTrigger.value++
        }
    }

    /**
     * Override onKeyDown to intercept HOME button presses
     *
     * NOTE: This method WILL NOT receive KEYCODE_HOME events when app is launcher.
     * Android Framework intercepts HOME at system level and sends Intent instead.
     * Kept for reference/debugging purposes only.
     *
     * @see onNewIntent for actual HOME button detection
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_HOME -> {
                    android.util.Log.d("LAUNCHER_HOME", "HOME button pressed - navigating to START")
                    // Increment trigger - TvRoot will detect change and navigate
                    homePressedTrigger.value++
                    return true  // Consume event
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(setAppLocale(newBase, "pl"))
    }

    private fun setAppLocale(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode, "PL")
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
