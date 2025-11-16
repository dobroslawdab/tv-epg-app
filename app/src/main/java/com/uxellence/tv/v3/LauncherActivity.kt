package com.uxellence.tv.v3

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.uxellence.tv.v3.channels.ChannelManager
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force Polish locale for the entire application
        setAppLocale(this, "pl")

        // Initialize VOD data cache once at startup (avoids repeated I/O)
        VodDataCache.initialize(this)

        // Initialize ChannelManager with TV channels database
        ChannelManager.initialize(this)

        // Start at TopMenuScreen2 (ODKRYWAJ section) - launcher mode
        setContent {
            TvRoot(startScreen = NavigationScreen.TOP_MENU2)
        }
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
