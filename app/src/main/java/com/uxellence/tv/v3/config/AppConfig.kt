package com.uxellence.tv.v3.config

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * App Configuration - colors, texts, feature flags
 * Fetched from Supabase and cached locally
 */
@Serializable
data class AppConfig(
    val id: Int = 1,
    val version: Int = 1,
    val updated_at: String = "",

    // === COLORS ===
    val color_background: String = "#281443",           // Main background
    val color_background_secondary: String = "#48227C", // Secondary background
    val color_focus: String = "#5FEDD4",                // Focus/accent color (aqua)
    val color_focus_glow: String = "#4D5FEDD4",         // Focus glow (30% opacity)
    val color_text_primary: String = "#EEEEEE",         // Primary text
    val color_text_secondary: String = "#CCEEEEEE",     // Secondary text (80% opacity)
    val color_selected_bg: String = "#FFFFFF",          // Selected item background
    val color_selected_text: String = "#48227C",        // Selected item text
    val color_item_bg: String = "#66000000",            // Menu item background (40% black)
    val color_container_bg: String = "#1AEEEEEE",       // Container background (10% white)

    // === TEXTS ===
    val text_menu_start: String = "Start",
    val text_menu_moje: String = "Moje",
    val text_menu_telewizja: String = "Telewizja",
    val text_menu_kino_play: String = "Kino Play",
    val text_menu_wideo: String = "Wideo",
    val text_menu_aplikacje: String = "Aplikacje",
    val text_menu_pakiety: String = "Pakiety",
    val text_menu_search: String = "Szukaj",
    val text_tooltip_konto: String = "Konto",
    val text_tooltip_ustawienia: String = "Ustawienia",
    val text_tooltip_profil: String = "Zmień profil",
    val text_tooltip_pakiety: String = "Pakiety kanałów",

    // === FEATURE FLAGS ===
    val feature_pakiety_enabled: Boolean = true,
    val feature_candy_bar_enabled: Boolean = false,
    val feature_profile_badge_enabled: Boolean = true,
    val feature_epg_section_expanded: Boolean = false,
    val feature_nagrania_v2_enabled: Boolean = false,

    // === LAYOUT ===
    val layout_container_height: Int = 112,
    val layout_container_radius: Int = 64,
    val layout_item_height: Int = 80,
    val layout_item_gap: Int = 20,
    val layout_focus_border_width: Int = 8,
    val layout_icon_size: Int = 80
) {
    companion object {
        val DEFAULT = AppConfig()

        /**
         * Parse hex color string to Compose Color
         * Supports: #RGB, #RRGGBB, #AARRGGBB
         */
        fun parseColor(hex: String): Color {
            return try {
                val cleanHex = hex.removePrefix("#")
                when (cleanHex.length) {
                    3 -> {
                        // #RGB -> #RRGGBB
                        val r = cleanHex[0].toString().repeat(2)
                        val g = cleanHex[1].toString().repeat(2)
                        val b = cleanHex[2].toString().repeat(2)
                        Color(android.graphics.Color.parseColor("#$r$g$b"))
                    }
                    6 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
                    8 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
                    else -> Color.Magenta // Error indicator
                }
            } catch (e: Exception) {
                Color.Magenta // Error indicator
            }
        }
    }

    // Computed Color properties for easy use in Compose
    val backgroundColor: Color get() = parseColor(color_background)
    val backgroundSecondaryColor: Color get() = parseColor(color_background_secondary)
    val focusColor: Color get() = parseColor(color_focus)
    val focusGlowColor: Color get() = parseColor(color_focus_glow)
    val textPrimaryColor: Color get() = parseColor(color_text_primary)
    val textSecondaryColor: Color get() = parseColor(color_text_secondary)
    val selectedBgColor: Color get() = parseColor(color_selected_bg)
    val selectedTextColor: Color get() = parseColor(color_selected_text)
    val itemBgColor: Color get() = parseColor(color_item_bg)
    val containerBgColor: Color get() = parseColor(color_container_bg)
}
