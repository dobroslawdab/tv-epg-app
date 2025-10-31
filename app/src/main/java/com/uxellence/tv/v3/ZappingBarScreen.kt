package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uxellence.tv.v3.channels.TvChannelData
import com.uxellence.tv.v3.epg.EpgProgram
import com.uxellence.tv.v3.repository.EpgRepository
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ZAPPING BAR SCREEN
 *
 * Converted from Figma design: https://www.figma.com/design/amSVOTqmxh8LHgIL9aVmnW/Hydepark?node-id=6173-28172
 *
 * Layout Engineer specs:
 * - Baseline: 1920x1080px
 * - All dimensions scaled via sx()/sy()
 * - Colors: #1a0c2c (bg), #eeeeee (text), #5fedd4 (accent)
 *
 * Focus Architect specs:
 * - Level: Screen Component
 * - Pattern: Simple navigation (BACK only)
 * - Delegation: onBackPressed() callback to MainActivity
 */

/**
 * Data model for Zapping Bar content
 */
data class ZappingBarData(
    val channelNumber: String = "12",
    val channelName: String = "TVP",
    val channelLogoUrl: String? = null,  // Optional channel logo
    val programTitle: String = "Dom z papieru wielka przesada wielka – seria wielkich",
    val season: String = "sezon 1, odc. 235",
    val category: String = "program informacyjny",
    val ageRating: String = "7 lat",
    val krritLabels: List<String> = listOf("S", "W", "N", "P"),
    val timeRange: String = "15:50 – 16:20",
    val progress: Float = 0.77f,  // 0.0 to 1.0 (77% completion)
    val currentTime: String = "15:02"
)

/**
 * Main Zapping Bar Screen
 *
 * @param onBackPressed Callback for BACK key navigation (Focus Architect pattern)
 * @param sx Horizontal scaling function (Layout Engineer)
 * @param sy Vertical scaling function (Layout Engineer)
 * @param data Test data for zapping bar content
 */
@Composable
fun ZappingBarScreen(
    onBackPressed: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    data: ZappingBarData = ZappingBarData()  // Default test data
) {
    val focusRequester = remember { FocusRequester() }

    // Auto-focus on screen load
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // REMOVED: .background(Color.Black) - było dla standalone screen
            // Teraz overlay na live TV - transparent background
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                // Focus Architect: Simple BACK navigation
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBackPressed()
                    true
                } else false
            }
            .focusable()
    ) {
        // REMOVED: Background overlay - live TV musi być w pełni widoczne
        // Figma miało gradient overlay, ale dla live TV overlay musi być transparent

        // Current time label (top right)
        // Figma node: 6173:28238 - time_label
        TimeLabel(
            time = data.currentTime,
            sx = sx,
            sy = sy
        )

        // Large channel number card (top right, below time)
        // Figma node: 6302:68062 - channel_number_card
        ChannelNumberCard(
            channelNumber = data.channelNumber,
            channelName = data.channelName,
            sx = sx,
            sy = sy
        )

        // Main zapping bar (bottom center)
        // Figma node: 6173:28465 - zapping_bar
        ZappingBarContainer(
            data = data,
            sx = sx,
            sy = sy
        )
    }
}

/**
 * Time Label (top right corner)
 *
 * Figma specs:
 * - Position: calc(80%+180px) from left, 60px from top
 * - Size: 82x48px
 * - Font: 32px Bold, line-height 48px
 * - Color: #eeeeee
 */
@Composable
fun TimeLabel(
    time: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.TopEnd)
            .padding(top = sy(60), end = sx(180))  // Figma: top=60, right offset
    ) {
        Text(
            text = time,
            style = TextStyle(
                fontSize = (32 * sy(1).value / 1).sp,  // Figma: 32px
                fontWeight = FontWeight.Bold,
                lineHeight = (48 * sy(1).value / 1).sp,  // Figma: 48px
                color = Color(0xFFEEEEEE),  // Figma: text-primary
                textAlign = TextAlign.Center,
                letterSpacing = (0.64 * sy(1).value / 1).sp  // Figma: 0.64px
            )
        )
    }
}

/**
 * Channel Number Card (large aqua number, top right)
 *
 * Figma specs:
 * - Position: 72.92% from left, 69.07% from top
 * - Size: 256x272px
 * - Background: #1a0c2c (container-banner)
 * - Border radius: 16px
 * - Number font: 88px Medium, line-height 120px, color #5fedd4 (aqua)
 * - Name font: 20px Bold, line-height 28px, color #eeeeee
 */
@Composable
fun ChannelNumberCard(
    channelNumber: String,
    channelName: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopEnd)
            .padding(top = sy(746), end = sx(264))  // Figma: inset positioning
    ) {
        Box(
            modifier = Modifier
                .width(sx(256))  // Figma: 256px
                .height(sy(272))  // Figma: ~272px
                .clip(RoundedCornerShape(sx(16)))  // Figma: radius=16px
                .background(Color(0xFF1A0C2C)),  // Figma: container-banner
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large channel number (aqua)
                Text(
                    text = channelNumber,
                    style = TextStyle(
                        fontSize = (88 * sy(1).value / 1).sp,  // Figma: 88px
                        fontWeight = FontWeight.Medium,
                        lineHeight = (120 * sy(1).value / 1).sp,  // Figma: 120px
                        color = Color(0xFF5FEDD4),  // Figma: text-status (aqua)
                        letterSpacing = (-1.76 * sy(1).value / 1).sp  // Figma: -1.76px
                    )
                )

                // Channel name
                Text(
                    text = channelName,
                    style = TextStyle(
                        fontSize = (20 * sy(1).value / 1).sp,  // Figma: 20px
                        fontWeight = FontWeight.Bold,
                        lineHeight = (28 * sy(1).value / 1).sp,  // Figma: 28px
                        color = Color(0xFFEEEEEE),  // Figma: text-primary
                        textAlign = TextAlign.Center,
                        letterSpacing = (0.4 * sy(1).value / 1).sp  // Figma: 0.4px
                    ),
                    modifier = Modifier.width(sx(224)),  // Figma: max-width 224px
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Main Zapping Bar Container
 *
 * Figma specs:
 * - Position: inset 68.15% 10.73% 4.82% 13.02%
 * - Size: ~1464x292px
 * - Background: #1a0c2c
 * - Border radius: 16px
 * - Horizontal arrangement: ChannelCard (left) + ProgramInfo (right)
 * - Gap: 32px
 */
@Composable
fun ZappingBarContainer(
    data: ZappingBarData,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.BottomStart)
            .padding(start = sx(250), end = sx(206), bottom = sy(52))  // Figma: inset positioning
    ) {
        Row(
            modifier = Modifier
                .width(sx(1464))  // Figma: calculated from insets
                .height(sy(292))  // Figma: 292px
                .clip(RoundedCornerShape(sx(16)))  // Figma: radius=16px
                .background(Color(0xFF1A0C2C)),  // Figma: container-banner
            horizontalArrangement = Arrangement.spacedBy(sx(32)),  // Figma: gap=32px
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Channel Card
            ChannelCard(
                channelNumber = data.channelNumber,
                channelName = data.channelName,
                channelLogoUrl = data.channelLogoUrl,
                sx = sx,
                sy = sy
            )

            // Right: Program Info
            ProgramInfo(
                data = data,
                sx = sx,
                sy = sy
            )
        }
    }
}

/**
 * Channel Card (left side of zapping bar)
 *
 * Figma specs:
 * - Size: 256x292px (fixed width in row)
 * - Layout: Logo (120x120) + Channel name + Channel number badge
 * - Vertical alignment: bottom with 48px padding
 */
@Composable
fun ChannelCard(
    channelNumber: String,
    channelName: String,
    channelLogoUrl: String?,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Column(
        modifier = Modifier
            .width(sx(256))  // Figma: 256px fixed width
            .fillMaxHeight()
            .padding(bottom = sy(48)),  // Figma: pb-48
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Channel logo placeholder
        Box(
            modifier = Modifier
                .size(sx(120), sy(120))  // Figma: 120x120px
                .clip(RoundedCornerShape(sx(8)))
                .background(Color(0xFF2C2C2C)),  // Placeholder background
            contentAlignment = Alignment.Center
        ) {
            if (channelLogoUrl != null) {
                AsyncImage(
                    model = channelLogoUrl,
                    contentDescription = "Logo $channelName",
                    modifier = Modifier.size(sx(100), sy(100))
                )
            } else {
                // Fallback: show channel number
                Text(
                    text = channelNumber,
                    style = TextStyle(
                        fontSize = (36 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5FEDD4)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(sy(8)))  // Figma: gap=8px

        // Channel name
        Text(
            text = channelName.uppercase(),
            style = TextStyle(
                fontSize = (20 * sy(1).value / 1).sp,  // Figma: 20px
                fontWeight = FontWeight.Bold,
                lineHeight = (28 * sy(1).value / 1).sp,  // Figma: 28px
                color = Color(0xFFEEEEEE),  // Figma: text-primary
                textAlign = TextAlign.Center,
                letterSpacing = (0.4 * sy(1).value / 1).sp
            ),
            modifier = Modifier.width(sx(224)),  // Figma: max-width
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(sy(8)))  // Figma: gap=8px

        // Channel number badge
        Box(
            modifier = Modifier
                .widthIn(max = sx(64))  // Figma: max-width=64px
                .height(sy(40))  // Figma: 40px
                .clip(RoundedCornerShape(sx(4)))  // Figma: radius=4px
                .border(
                    width = sx(2),  // Figma: stroke=2px
                    color = Color(0x66EEEEEE),  // Figma: stroke-disabled (40% opacity)
                    shape = RoundedCornerShape(sx(4))
                )
                .padding(horizontal = sx(12), vertical = sy(8)),  // Figma: px-12, py-8
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelNumber,
                style = TextStyle(
                    fontSize = (24 * sy(1).value / 1).sp,  // Figma: 24px
                    fontWeight = FontWeight.Medium,
                    lineHeight = (32 * sy(1).value / 1).sp,  // Figma: 32px
                    color = Color(0xFFEEEEEE),  // Figma: text-primary
                    letterSpacing = (0.48 * sy(1).value / 1).sp
                )
            )
        }
    }
}

/**
 * Program Info (right side of zapping bar)
 *
 * Figma specs:
 * - Size: ~1136px width, 292px height
 * - Layout: Title + Metadata + Timeline + Time range
 * - Vertical alignment: top 32px, bottom 40px padding
 */
@Composable
fun ProgramInfo(
    data: ZappingBarData,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Column(
        modifier = Modifier
            .width(sx(1136))  // Figma: 1136px
            .fillMaxHeight()
            .padding(top = sy(32), bottom = sy(40)),  // Figma: pt-32, pb-40
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top: Title + Metadata
        Column(
            verticalArrangement = Arrangement.spacedBy(sy(8))  // Figma: gap=8px
        ) {
            // Program title
            Text(
                text = data.programTitle,
                style = TextStyle(
                    fontSize = (48 * sy(1).value / 1).sp,  // Figma: 48px
                    fontWeight = FontWeight.Medium,
                    lineHeight = (64 * sy(1).value / 1).sp,  // Figma: 64px
                    color = Color(0xFFEEEEEE)  // Figma: text-primary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Metadata row (season, category, age, KRRIT labels)
            MetadataRow(
                season = data.season,
                category = data.category,
                ageRating = data.ageRating,
                krritLabels = data.krritLabels,
                sx = sx,
                sy = sy
            )
        }

        // Bottom: Timeline + Time range
        Column(
            verticalArrangement = Arrangement.spacedBy(sy(16))  // Figma: gap=16px
        ) {
            // Timeline progress bar
            Timeline(
                progress = data.progress,
                sx = sx,
                sy = sy
            )

            // Time range label
            Text(
                text = data.timeRange,
                style = TextStyle(
                    fontSize = (32 * sy(1).value / 1).sp,  // Figma: 32px
                    fontWeight = FontWeight.Bold,
                    lineHeight = (48 * sy(1).value / 1).sp,  // Figma: 48px
                    color = Color(0xFFEEEEEE),  // Figma: text-primary
                    letterSpacing = (0.64 * sy(1).value / 1).sp
                )
            )
        }
    }
}

/**
 * Metadata Row (season, category, age, KRRIT labels)
 *
 * Figma specs:
 * - Font: 20px Bold, line-height 28px
 * - Color: #eeeeee (80% opacity for secondary)
 * - Separator: Vertical divider (rotated 90°, 24px height)
 * - KRRIT labels: 20x20px boxes with 2px border
 */
@Composable
fun MetadataRow(
    season: String,
    category: String,
    ageRating: String,
    krritLabels: List<String>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(sx(16)),  // Figma: gap=16px
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Season
        Text(
            text = season,
            style = TextStyle(
                fontSize = (20 * sy(1).value / 1).sp,  // Figma: 20px
                fontWeight = FontWeight.Bold,
                lineHeight = (28 * sy(1).value / 1).sp,
                color = Color(0xCCEEEEEE),  // Figma: text-secondary (80%)
                letterSpacing = (0.4 * sy(1).value / 1).sp
            )
        )

        // Divider
        MetadataDivider(sy)

        // Category
        Text(
            text = category,
            style = TextStyle(
                fontSize = (20 * sy(1).value / 1).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (28 * sy(1).value / 1).sp,
                color = Color(0xCCEEEEEE),
                letterSpacing = (0.4 * sy(1).value / 1).sp
            )
        )

        // Divider
        MetadataDivider(sy)

        // Age rating
        Text(
            text = ageRating,
            style = TextStyle(
                fontSize = (20 * sy(1).value / 1).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (28 * sy(1).value / 1).sp,
                color = Color(0xCCEEEEEE),
                letterSpacing = (0.4 * sy(1).value / 1).sp
            )
        )

        // Divider
        MetadataDivider(sy)

        // KRRIT labels
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(20))  // Figma: gap=20px
        ) {
            krritLabels.forEach { label ->
                KrritLabel(label, sx, sy)
            }
        }
    }
}

/**
 * Vertical divider for metadata
 * Figma: Rotated 90°, height 24px, color #eeeeee
 */
@Composable
fun MetadataDivider(sy: (Int) -> Dp) {
    Box(
        modifier = Modifier
            .width(sy(2))  // Figma: stroke=2px
            .height(sy(24))  // Figma: 24px height
            .background(Color(0xFFEEEEEE))
    )
}

/**
 * KRRIT Label (e.g., S, W, N, P)
 *
 * Figma specs:
 * - Size: 20x20px (content box inside padding)
 * - Border: 2px solid rgba(238,238,238,0.8)
 * - Radius: 4px
 * - Font: 16px Bold, color rgba(238,238,238,0.8)
 */
@Composable
fun KrritLabel(
    label: String,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .size(sx(20), sy(20))  // Figma: 20x20px
            .clip(RoundedCornerShape(sx(4)))  // Figma: radius=4px
            .border(
                width = sx(2),  // Figma: stroke=2px
                color = Color(0xCCEEEEEE),  // Figma: stroke-secondary (80%)
                shape = RoundedCornerShape(sx(4))
            )
            .padding(sx(10)),  // Figma: p=10px
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = (16 * sy(1).value / 1).sp,  // Figma: 16px
                fontWeight = FontWeight.Bold,
                lineHeight = (24 * sy(1).value / 1).sp,  // Figma: 24px
                color = Color(0xCCEEEEEE),  // Figma: text-secondary
                textAlign = TextAlign.Center,
                letterSpacing = (0.32 * sy(1).value / 1).sp
            )
        )
    }
}

/**
 * Timeline Progress Bar
 *
 * Figma specs:
 * - Size: 1136x12px
 * - Background: rgba(238,238,238,0.4) - stroke-disabled
 * - Progress fill: #eeeeee - text-primary
 * - Border radius: 8px
 */
@Composable
fun Timeline(
    progress: Float,  // 0.0 to 1.0
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .width(sx(1136))  // Figma: 1136px
            .height(sy(12))  // Figma: 12px
            .clip(RoundedCornerShape(sx(8)))  // Figma: radius=8px
            .background(Color(0x66EEEEEE))  // Figma: stroke-disabled (40%)
    ) {
        // Progress fill
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))  // Progress percentage
                .clip(RoundedCornerShape(sx(8)))
                .background(Color(0xFFEEEEEE))  // Figma: text-primary
        )
    }
}

// ==================== EXTENSION FUNCTIONS ====================

/**
 * Convert TvChannelData + EpgProgram to ZappingBarData
 *
 * Maps channel and program info to Zapping Bar display format
 */
fun TvChannelData.toZappingBarData(
    program: EpgProgram?,
    epgRepository: EpgRepository?
): ZappingBarData {
    return ZappingBarData(
        channelNumber = extractChannelNumber(),
        channelName = name,
        channelLogoUrl = logoUrl,
        programTitle = program?.title ?: "Brak informacji o programie",
        season = program?.extractSeasonEpisode() ?: "",
        category = program?.categories?.firstOrNull() ?: "",
        ageRating = program?.extractAgeRating() ?: "",
        krritLabels = program?.extractKrritLabels() ?: emptyList(),
        timeRange = program?.formatTimeRange() ?: "",
        progress = program?.calculateProgress() ?: 0f,
        currentTime = getCurrentTime()
    )
}

/**
 * Extract channel number from ID or name
 * Examples: "tvp1" → "1", "polsat" → "1", "tvn24" → "24"
 */
private fun TvChannelData.extractChannelNumber(): String {
    // Try to extract number from ID or name
    val numberRegex = Regex("\\d+")
    val match = numberRegex.find(id) ?: numberRegex.find(name)
    return match?.value ?: "0"
}

/**
 * Extract season/episode info from EPG program
 * Examples: "S01E05" → "sezon 1, odc. 5"
 */
private fun EpgProgram.extractSeasonEpisode(): String {
    val desc = description ?: ""

    // Try S01E05 format
    val seasonEpisodeRegex = Regex("S(\\d+)E(\\d+)")
    val match = seasonEpisodeRegex.find(desc)
    if (match != null) {
        val season = match.groupValues[1].toIntOrNull() ?: 0
        val episode = match.groupValues[2].toIntOrNull() ?: 0
        return "sezon $season, odc. $episode"
    }

    // Try E05 format
    val episodeRegex = Regex("E(\\d+)")
    val episodeMatch = episodeRegex.find(desc)
    if (episodeMatch != null) {
        val episode = episodeMatch.groupValues[1].toIntOrNull() ?: 0
        return "odc. $episode"
    }

    return ""
}

/**
 * Extract age rating from program description
 * Examples: "7+", "12+", "16+"
 */
private fun EpgProgram.extractAgeRating(): String {
    val desc = description ?: ""
    val ageRegex = Regex("(\\d+)\\+")
    val match = ageRegex.find(desc)
    return if (match != null) {
        "${match.groupValues[1]} lat"
    } else {
        ""
    }
}

/**
 * Extract KRRIT labels from program categories
 * Examples: ["S", "W", "N", "P"]
 */
private fun EpgProgram.extractKrritLabels(): List<String> {
    // Placeholder - implement actual KRRIT extraction logic
    // Based on categories or description
    return emptyList()
}

/**
 * Format program time range
 * Example: "15:50 – 16:20"
 */
private fun EpgProgram.formatTimeRange(): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())

    val start = formatter.format(startUtc)
    val end = formatter.format(endUtc)

    return "$start – $end"
}

/**
 * Calculate program progress (0.0 to 1.0)
 */
private fun EpgProgram.calculateProgress(): Float {
    val now = Instant.now()

    // Program hasn't started yet
    if (now.isBefore(startUtc)) return 0f

    // Program has ended
    if (now.isAfter(endUtc)) return 1f

    // Calculate progress
    val total = Duration.between(startUtc, endUtc).toMillis().toFloat()
    val elapsed = Duration.between(startUtc, now).toMillis().toFloat()

    return (elapsed / total).coerceIn(0f, 1f)
}

/**
 * Get current time formatted as HH:mm
 */
private fun getCurrentTime(): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.now())
}
