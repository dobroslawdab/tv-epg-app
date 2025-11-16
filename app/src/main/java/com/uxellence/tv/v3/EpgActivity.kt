package com.uxellence.tv.v3

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.uxellence.tv.v3.channels.ChannelManager
import java.util.*

/**
 * EpgActivity - Entry point when app is launched from Android TV app drawer
 *
 * Purpose: Handles regular app launch (CATEGORY_LAUNCHER) when user opens the app
 * from the TV's app drawer/grid.
 *
 * Behavior:
 * - Starts navigation at SPLASH screen (normal startup flow)
 * - Follows startup mode preference: EPG Day Test or TopMenuScreen2
 * - Shows What's New screen on updates (if applicable)
 * - Same initialization as MainActivity (VodDataCache, ChannelManager, Polish locale)
 *
 * Startup Flow:
 * 1. SPLASH (2 seconds)
 * 2. WHATS_NEW (if update detected)
 * 3. STARTUP_MODE_SELECTION (if first install)
 * 4. EPG_DAY or TOP_MENU2 (based on user preference)
 *
 * User Experience:
 * - App drawer → EpgActivity → SPLASH → ... → EPG Day Test (with overlay)
 * - HOME button → LauncherActivity → TopMenuScreen2 (if app set as launcher)
 *
 * Technical Notes:
 * - Intent filter in AndroidManifest.xml includes CATEGORY_LAUNCHER
 * - launchMode="singleTask" ensures only one instance exists
 * - Regular app launch behavior (not launcher mode)
 *
 * @see LauncherActivity for HOME button entry point
 * @see MainActivity for development/testing entry point
 */
class EpgActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force Polish locale for the entire application
        setAppLocale(this, "pl")

        // Initialize VOD data cache once at startup (avoids repeated I/O)
        VodDataCache.initialize(this)

        // Initialize ChannelManager with TV channels database
        ChannelManager.initialize(this)

        // Start at SPLASH screen - follows normal startup flow
        setContent {
            TvRoot(startScreen = NavigationScreen.SPLASH)
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
