package com.uxellence.tv.v3
import com.uxellence.tv.v3.R

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import coil.request.ImageRequest
import coil.size.Size
import com.airbnb.lottie.compose.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.version001.Version001Screen
import com.uxellence.tv.v3.version001.*
import com.uxellence.tv.v3.focus.*
import com.uxellence.tv.v3.search.SearchScreenNew
import com.uxellence.tv.v3.ShortcutItem
import com.uxellence.tv.v3.ShortcutIcon
import com.uxellence.tv.v3.ShortcutCard
import com.uxellence.tv.v3.pip.PipDialogController
import com.uxellence.tv.v3.pip.PipDialogMenu
import java.time.LocalTime
import coil.compose.AsyncImage
import android.util.Log
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerView
import android.widget.Toast
import android.content.Context
import com.uxellence.tv.v3.utils.VersionTracker

// PIP Dialog Constants
private val DIALOG_ALLOWED_KEYS = setOf(
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionCenter,
    Key.Enter,
    Key.Back,
    Key.Escape
)

// Data classes
data class TopMenuState2(
    val focusedItemId: String = "",
    val selectedItemId: String = "",
    val isMenuFocused: Boolean = true
)

data class MenuItem2(
    val id: String,
    val title: String
)


data class VodSlideData(
    val title: String,
    val genre: String,
    val duration: String,
    val year: String,
    val country: String,
    val ageRating: String,
    val description: String,
    val price: String,
    val backgroundUrl: String
)

data class PackageItem(
    val title: String,
    val imageUrl: String,
    val price: String,
    val description: String = ""
)

data class AppItem(
    val id: String,
    val name: String,
    val iconResId: Int // Drawable resource ID
)

data class TvChannel(
    val name: String,
    val logo: String,
    val epgId: String? = null,  // EPG channel ID for navigation to EPG Day screen
    val channelNumber: Int? = null  // Channel number for display (e.g., 1, 50, 600)
)

data class ServiceLogoItem(
    val id: String,
    val name: String,
    val logoDrawable: Int
)

enum class VodFocusArea {
    MAIN_CONTENT
}

// MARK: - MOJE Expandable Channel Data Structures

// Faza 2: SubChannel data class deleted - sub-channels will be regular channels in dynamic list

/**
 * ExpandableChannel - Simple state tracker for NAGRANIA expansion
 * Faza 2: Simplified - only tracks isExpanded state, no nested subChannels
 *
 * @param id Unikalny identyfikator kanału (np. "NAGRANIA")
 * @param title Wyświetlany tytuł kanału
 * @param isExpanded Stan rozwinięcia (true = 8 channels, false = 5 channels)
 */
data class ExpandableChannel(
    val id: String,
    val title: String,
    var isExpanded: Boolean = false
)

// Faza 2: MojeFocusPosition deleted - using simple Pair<rowIndex, colIndex> like other sections

// Helper function to load channels from JSON file with channel numbers
private fun loadChannelsFromJson(context: Context, fileName: String, startNumber: Int): List<TvChannel> {
    return try {
        val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(jsonString)
        val channels = mutableListOf<TvChannel>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            var logoUrl = obj.getString("logo")

            // Fix double "https:https://" prefix
            if (logoUrl.startsWith("https:https://")) {
                logoUrl = logoUrl.removePrefix("https:")
            }

            channels.add(
                TvChannel(
                    name = obj.getString("name"),
                    logo = logoUrl,
                    epgId = obj.optString("id", null),
                    channelNumber = startNumber + i
                )
            )
        }

        // Deduplicate by name
        channels.distinctBy { it.name }
            .mapIndexed { index, channel -> channel.copy(channelNumber = startNumber + index) }
    } catch (e: Exception) {
        android.util.Log.e("TELEWIZJA_DEBUG", "Error loading channels from $fileName: ${e.message}")
        emptyList()
    }
}

// Function to load TV channels from JSON
private fun loadTvChannelsFromAssets(context: Context): List<TvChannel> {
    return try {
        val jsonString = context.assets.open("lista_kanalow.json").bufferedReader().use { it.readText() }
        val jsonArray = org.json.JSONArray(jsonString)
        val channels = mutableListOf<TvChannel>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            var logoUrl = obj.getString("logo")

            // Fix double "https:https://" prefix
            if (logoUrl.startsWith("https:https://")) {
                logoUrl = logoUrl.removePrefix("https:")
            }

            channels.add(
                TvChannel(
                    name = obj.getString("name"),
                    logo = logoUrl,
                    epgId = obj.optString("id", null),  // Use 'id' field as EPG ID
                    channelNumber = i + 1  // Initial numbering (will be renumbered after deduplication)
                )
            )
        }

        // Deduplicate by name (477 → 119 unique channels) and re-number
        channels.distinctBy { it.name }
            .mapIndexed { index, channel -> channel.copy(channelNumber = index + 1) }  // Final numbers: 1, 2, 3...
    } catch (e: Exception) {
        android.util.Log.e("TELEWIZJA_DEBUG", "Error loading TV channels from lista_kanalow.json: ${e.message}")
        emptyList()
    }
}

// Filter TV channels by category - load from specific JSON files with channel numbers
private fun filterTvChannelsByCategory(context: Context, channels: List<TvChannel>, category: String): List<TvChannel> {
    return when (category) {
        "filmy-i-seriale" -> loadChannelsFromJson(context, "filmy_i_seriale.json", 50)  // 50-529 (480 channels)
        "dla-dzieci" -> loadChannelsFromJson(context, "dladzieci.json", 600)  // 600-687 (88 channels)
        "sport" -> loadChannelsFromJson(context, "sport.json", 700)  // 700-805 (106 channels)
        "dokumenty" -> loadChannelsFromJson(context, "dok.json", 500)  // 500-987 (488 channels)
        "informacyjne" -> {
            // Filter from lista_kanalow.json and assign numbers 200+
            channels.filter {
                it.name in listOf(
                    "Fokus TV HD", "Bloomberg Television",  // Old channels
                    "Fokus TV", "News24", "Nowość! Euronews PL", "POLSAT News",
                    "POLSAT News 2", "Polsat News Polityka", "Sky News", "TVP INFO"
                )
            }.mapIndexed { index, channel -> channel.copy(channelNumber = 200 + index) }  // 200-209
        }
        "moja-lista" -> listOf(
            TvChannel("TVP", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/8499963/images/952146681?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0", "TVP 1", 1),
            TvChannel("Polsat", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/9817820/images/819859960?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0", "Polsat", 2),
            TvChannel("Polsat News Polityka", "https://r.dcs.redcdn.pl/file/play/playtv/upload/live/24725756/images/937177205", "Polsat News Polityka", 3),
            TvChannel("4 Fun TV", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/3452692/images/350594752?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0", "4Fun.tv", 4),
            TvChannel("TV4", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/9979708/images/913218406?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0", "TV4", 5),
            TvChannel("Polsat News", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/20183312/images/896415049?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0", "Polsat News HD", 6),
            TvChannel("TVP 3", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/8499965/images/952041085?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0", "TVP 3 Warszawa", 7),
            TvChannel("TVN24", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/7208754/images/1032763214?srcmode=3&srcw=1/1&srch=1/1&dstw=120&dsth=120&quality=100", "TVN 24", 8),
            TvChannel("TVP Sport", "https://r.dcs.redcdn.pl/scale/play/playtv/upload/live/13352686/images/831494260?srcmode=3&srcw=1/1&srch=1/1&dstw=120&dsth=120&quality=100", "TVP Sport", 9)
        )
        else -> emptyList()
    }
}

// Service logos for TELEWIZJA Row 1
private val serviceLogos = listOf(
    ServiceLogoItem("disney", "Disney+", R.drawable.disney_plus_logo),
    ServiceLogoItem("prime", "Prime Video", R.drawable.prime_video_logo),
    ServiceLogoItem("netflix", "Netflix", 0),  // Placeholder - no drawable yet
    ServiceLogoItem("hbomax", "HBO Max", 0),   // Placeholder - no drawable yet
    ServiceLogoItem("appletv", "Apple TV+", 0) // Placeholder - no drawable yet
)

// MARK: - Service Logo Card (Row 1: Serwisy VOD)
@Composable
private fun ServiceLogoCard(
    service: ServiceLogoItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(sx(200))
            .height(sy(200))
            .clip(RoundedCornerShape(sx(12)))
            .background(Color(0xFF2C2C2C))
            .then(
                if (isFocused) Modifier.border(
                    width = sx(4),
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        if (service.logoDrawable != 0) {
            Image(
                painter = painterResource(id = service.logoDrawable),
                contentDescription = service.name,
                modifier = Modifier.size(sx(120), sy(120)),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = service.name,
                style = TextStyle(
                    fontSize = (16 * sy(1).value / 1).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
    }
}

// MARK: - Channel Logo Card (Row 2: Kanały TV - Grid item)
@Composable
private fun ChannelLogoCard(
    channel: TvChannel,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(sx(150))
            .height(sy(150))
            .clip(RoundedCornerShape(sx(12)))
            .background(Color(0xFF2C2C2C))
            .then(
                if (isFocused) Modifier.border(
                    width = sx(4),
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = channel.logo,
            contentDescription = channel.name,
            modifier = Modifier.size(sx(100), sy(100)),
            contentScale = ContentScale.Fit
        )
    }
}

// MARK: - Text Channel Header (for EPG channels like Seriale, Sport, Teleturnieje)
@Composable
private fun TextChannelHeader(
    channelName: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(sx(288))  // Szerokość CategoryIcon
            .height(sy(216))  // Wysokość CategoryIcon
            .clip(RoundedCornerShape(sx(12)))
            .background(Color(0xFF000000).copy(alpha = if (isFocused) 0.3f else 0.1f))  // Black with 0.1/0.3 opacity (like WIDEO)
            .border(
                width = if (isFocused) sx(4) else 0.dp,
                color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                shape = RoundedCornerShape(sx(12))
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = channelName,
            fontSize = (32 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFEEEEEE),
            textAlign = TextAlign.Center
        )
    }
}

// MARK: - TV Channel Icon Card (for scrollable rows like APLIKACJE)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvChannelIconCard(
    channel: TvChannel,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit = {},
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .width(sx(170))
            .height(sy(190))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    android.util.Log.d("TV_CHANNEL_CLICK", "OK pressed on channel: ${channel.name}")
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(8))
    ) {
        Box(
            modifier = Modifier
                .size(sx(150), sy(150))
                .clip(RoundedCornerShape(sx(12)))
                .background(Color(0xFF2C2C2C))
                .border(
                    width = if (isFocused) sx(4) else 0.dp,
                    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.size(sx(100), sy(100)),
                contentScale = ContentScale.Fit
            )
        }
        if (isFocused) {
            Text(
                text = if (channel.channelNumber != null) {
                    "${channel.channelNumber}. ${channel.name}"  // Display: "1. TVP1", "50. CANAL+ 360"
                } else {
                    channel.name
                },
                color = Color(0xFFEEEEEE),
                fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(sx(170))
            )
        }
    }
}

// MARK: - TV App Icon Card (Large TV channel icons like APLIKACJE)
@Composable
private fun TvAppIconCard(
    channel: TvChannel,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .width(sx(320))
            .height(sy(220))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(8))
    ) {
        Box(
            modifier = Modifier
                .size(sx(300), sy(170))
                .clip(RoundedCornerShape(sx(12)))
                .border(
                    width = if (isFocused) (6 * sx(1).value / 1.dp.value).dp else 0.dp,
                    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                )
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (isFocused) {
            Text(
                text = if (channel.channelNumber != null) {
                    "${channel.channelNumber}. ${channel.name}"  // Display: "600. Ginx eSports TV", "700. Polsat Sport 1"
                } else {
                    channel.name
                },
                color = Color(0xFFEEEEEE),
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.Center
            )
        }
    }
}

// MARK: - Channel List Card (Figma design: 208x208 with channel number)
/**
 * ChannelListCard - Converted from Figma (node-id=6102:17460)
 *
 * Specs:
 * - Card: 208x208px (square)
 * - Background: rgba(0,0,0,0.6) default, rgba(0,0,0,0.8) focused
 * - Logo: 148x148px (centered)
 * - Channel number label: 40px height, padding 8/12, border 2px, radius 4px
 * - Font: Manrope Medium 24px
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelListCard(
    channel: TvChannel,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit = {},
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .size(sx(TELEWIZJA_CHANNEL_LIST_CARD_WIDTH), sy(TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT))  // Figma: 208x208
            .clip(RoundedCornerShape(sx(12)))
            .background(Color(0xFF000000).copy(alpha = if (isFocused) 0.3f else 0.1f))  // Black with 0.1/0.3 opacity (like WIDEO)
            .border(
                width = if (isFocused) sx(4) else 0.dp,  // Standard focused border
                color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                shape = RoundedCornerShape(sx(12))
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    android.util.Log.d("CHANNEL_LIST_CLICK", "OK pressed on channel: ${channel.name}")
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .padding(bottom = sy(20)),  // Figma: padding-bottom 20px
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(8))
    ) {
        // Logo (148x148)
        Box(
            modifier = Modifier.size(sx(TELEWIZJA_CHANNEL_LIST_CARD_LOGO_SIZE), sy(TELEWIZJA_CHANNEL_LIST_CARD_LOGO_SIZE)),  // Figma: 148x148
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Channel number label
        Box(
            modifier = Modifier
                .height(sy(54))  // 54px height fits 28sp lineHeight perfectly without cutoff
                .widthIn(min = sx(52))  // Minimum width to fit 2-digit number (01, 02, etc.)
                .clip(RoundedCornerShape(sx(4)))  // Figma: radius 4px
                .border(
                    width = sx(2),  // Figma: 2px border
                    color = Color(0xFFEEEEEE).copy(alpha = 0.4f),  // Figma: rgba(238,238,238,0.4)
                    shape = RoundedCornerShape(sx(4))
                )
                .padding(horizontal = sx(12)),  // Only horizontal padding - vertical removed to prevent cutoff
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (channel.channelNumber ?: 0).toString().padStart(2, '0'),  // Use channel's assigned number
                fontSize = (24 * sy(1).value / 1).sp,  // Figma: 24px
                fontWeight = FontWeight.Medium,  // Figma: Medium (500)
                lineHeight = (28 * sy(1).value / 1).sp,  // Reduced from 32px to 28px - fits in 54px box without cutoff
                letterSpacing = 0.48.sp,  // Figma: 2% of 24px = 0.48sp
                color = Color(0xFFEEEEEE)  // Figma: #EEEEEE
            )
        }
    }
}

// MARK: - Channel Logos Grid (Row 2: Kanały TV - 2x7 grid)
@Composable
private fun ChannelLogosGrid(
    channels: List<TvChannel>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (rowOffset: Int, colOffset: Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val rowIndex = 2 // Grid is in Row 2
    val rows = channels.chunked(7) // 2 rows of 7 channels each

    Column(
        modifier = Modifier.padding(start = sx(80)),
        verticalArrangement = Arrangement.spacedBy(sy(20))
    ) {
        rows.forEachIndexed { gridRowIndex, rowChannels ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                rowChannels.forEachIndexed { colIndex, channel ->
                    val globalColIndex = gridRowIndex * 7 + colIndex
                    val isFocused = focusedRowIndex == rowIndex && focusedColIndex == globalColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, globalColIndex)] ?: FocusRequester()

                    ChannelLogoCard(
                        channel = channel,
                        isFocused = isFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { onFocusChange(rowIndex, globalColIndex) },
                        sx = sx,
                        sy = sy
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopMenuScreen2(
    onBackPressed: (isMenuFocused: Boolean) -> Boolean = { false },
    onReturnToEpgDay: () -> Unit = {},  // NEW: Return to EPG Day Test from PIP
    onShowMainMenu: () -> Unit = {},
    onNavigateToLiveScreen: (String) -> Unit = {},
    onNavigateToEpg: () -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> },
    onNavigateToStartupMode: () -> Unit = {},  // Navigate to startup mode selection
    onFocusRestored: () -> Unit = {},
    restoredTelewizjaFocus: FocusState? = null,
    restoredSection: String? = null,
    pipPlayer: com.google.android.exoplayer2.ExoPlayer? = null,  // PIP player instance
    onClosePip: () -> Unit = {},  // Callback to close PIP
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },  // Navigate to VOD grid (Nagrania, Wypożyczone, Do obejrzenia, etc.)
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> }  // Navigate to KINO grid (Akcja, Horror - vertical posters)
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { com.uxellence.tv.v3.repository.EpgRepository.getInstance(context) }

    // Global state for keyboard shortcuts (defined early for use in global handlers)
    var isEpgSectionExpanded by remember { mutableStateOf(com.uxellence.tv.v3.utils.VersionTracker.getEpgSectionExpanded(context)) }
    var showNagraniaV2 by remember { mutableStateOf(com.uxellence.tv.v3.utils.VersionTracker.getNagraniaVersion(context) == "v2") }

    val menuItems = remember {
        listOf(
            MenuItem2("ODKRYWAJ", "Start"),       // Moved from position 1 to 0
            MenuItem2("MOJE", "Moje"),            // Moved from position 2 to 1
            MenuItem2("TELEWIZJA", "Telewizja"),  // Moved from position 3 to 2
            MenuItem2("KINO_PLAY", "Kino Play"),  // Moved from position 4 to 3
            MenuItem2("WIDEO", "Wideo"),          // Moved from position 5 to 4
            MenuItem2("APLIKACJE", "Aplikacje"),  // Moved from position 6 to 5
            MenuItem2("SEARCH", "Search")         // Moved from position 0 to 6 (Lupa na koniec)
        )
    }

    val globalFocusState = GlobalFocusManager.rememberGlobalFocusState(
        initialRow = if (restoredTelewizjaFocus != null && restoredSection == "TELEWIZJA") {
            1  // Restore to content mode (actual channel/item restoration handled by ID-based logic)
        } else {
            0  // Default to menu
        },
        initialPosition = MenuPositions.getPositionForSection(restoredSection ?: "ODKRYWAJ"),  // Match position to section
        initialSection = restoredSection ?: "ODKRYWAJ"  // Restore saved section or default to ODKRYWAJ
    )

    // Track fresh PIP mode (resets automatically when pipPlayer changes)
    var freshPipMode by remember(pipPlayer) {
        mutableStateOf(pipPlayer != null)
    }

    // PIP dialog state - show when PLAY/PAUSE pressed with active PIP
    var showPipDialog by remember { mutableStateOf(false) }

    // Debounce state for PLAY/PAUSE to prevent rapid dialog opens (50ms minimum between opens)
    var lastDialogOpenTime by remember { mutableLongStateOf(0L) }

    var isContentLoading by remember { mutableStateOf(false) }

    val focusRequesters = remember(menuItems.size) {
        menuItems.associate { it.id to FocusRequester() }
    }

    // Right section state (0=CandyBar, 1=Profile, 2=Settings, -1=none focused)
    var focusedRightButton by remember { mutableStateOf(-1) }
    val rightButtonFocusRequesters = remember {
        mapOf(
            0 to FocusRequester(), // CandyBar
            1 to FocusRequester(), // Profile
            2 to FocusRequester()  // Settings
        )
    }

    // State for keyboard shortcuts (loaded from SharedPreferences)
    var isCandyBarVisible by remember { mutableStateOf(com.uxellence.tv.v3.utils.VersionTracker.getCandyBarVisibility(context)) }
    var showProfileNotificationBadge by remember { mutableStateOf(com.uxellence.tv.v3.utils.VersionTracker.getNotificationBadge(context)) }

    // Manage focus for right section buttons
    LaunchedEffect(focusedRightButton) {
        if (focusedRightButton >= 0 && globalFocusState.value.currentRow == 0) {
            // Focus on right section button
            delay(50)
            rightButtonFocusRequesters[focusedRightButton]?.requestFocus()
        }
    }

    // Auto-focus Profile button when returning from ACCOUNT content to menu
    LaunchedEffect(globalFocusState.value.sectionId, globalFocusState.value.currentRow) {
        if (globalFocusState.value.sectionId == "ACCOUNT" && globalFocusState.value.currentRow == 0) {
            delay(150)  // Increased delay prevents visible focus flash on START/SZUKAJ
            focusedRightButton = 1  // Focus Profile button
        }
    }

    LaunchedEffect(globalFocusState.value.currentPosition, globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0) {
            val currentSection = MenuPositions.getSectionForPosition(globalFocusState.value.currentPosition)
            if (globalFocusState.value.sectionId != currentSection) {
                // DEBOUNCE: Wait 350ms before loading content
                // If user moves LEFT/RIGHT quickly, LaunchedEffect cancels and content never loads
                // This allows free navigation without blocking - content loads only when user stops
                delay(350)

                // Show loader (smooth fade in)
                isContentLoading = true

                // Change section
                globalFocusState.value = globalFocusState.value.copy(sectionId = currentSection)

                // Short delay for content initialization (50ms - optimized from original 100ms)
                delay(50)

                // Hide loader (smooth fade out)
                isContentLoading = false
            }
        }
    }

    LaunchedEffect(globalFocusState.value.currentRow, globalFocusState.value.currentPosition) {
        if (globalFocusState.value.currentRow == 0) {
            val currentSection = MenuPositions.getSectionForPosition(globalFocusState.value.currentPosition)
            focusRequesters[currentSection]?.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // PIP Dialog Guard: CRITICAL - Must be FIRST before any key handling!
                // When dialog is open, only allow specific keys through (whitelist pattern)
                // Strengthened input gate prevents unexpected keys from reaching background navigation
                if (showPipDialog) {
                    if (event.key in DIALOG_ALLOWED_KEYS) {
                        android.util.Log.d("TopMenuScreen2", "PIP dialog open - allowing key to dialog: ${event.key}")
                        return@onPreviewKeyEvent false  // Let dialog handle allowed keys
                    } else {
                        android.util.Log.d("TopMenuScreen2", "PIP dialog open - blocking unexpected key: ${event.key}")
                        return@onPreviewKeyEvent true  // Consume and block all other keys
                    }
                }

                if (event.key == Key.Back) {
                    // PIP mode: BACK from menu on ANY tab - always return to EPG
                    if (pipPlayer != null && globalFocusState.value.currentRow == 0) {
                        android.util.Log.d("PIP_NAVIGATION", "BACK from menu with PIP (tab: ${globalFocusState.value.sectionId}) - returning to EPG Day Test")
                        onReturnToEpgDay()
                        onClosePip()
                        return@onPreviewKeyEvent true
                    }

                    // Normal BACK - from content to menu
                    if (globalFocusState.value.currentRow != 0) {
                        globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
                        return@onPreviewKeyEvent true
                    }

                    // BACK from menu without PIP - consume event (stay in app, don't exit)
                    return@onPreviewKeyEvent true
                }

                // PIP: Handle "0" key to close PIP
                if (event.key == Key.Zero && pipPlayer != null) {
                    android.util.Log.d("TopMenuScreen2", "Key 0: Closing PIP")
                    onClosePip()
                    return@onPreviewKeyEvent true
                }

                // PIP: Handle PLAY/PAUSE key - show dialog when PIP is active
                // Debounce: Minimum 50ms between dialog opens to prevent rapid key press issues
                val keyCode = event.nativeKeyEvent.keyCode
                if ((keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                     keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PLAY ||
                     keyCode == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE) && pipPlayer != null) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastDialogOpenTime >= 50) {
                        android.util.Log.d("TopMenuScreen2", "PLAY/PAUSE pressed with active PIP - showing dialog")
                        showPipDialog = true
                        lastDialogOpenTime = currentTime
                    } else {
                        android.util.Log.d("TopMenuScreen2", "PLAY/PAUSE debounced - ignoring rapid key press")
                    }
                    return@onPreviewKeyEvent true
                }

                // ===== GLOBAL KEYBOARD SHORTCUTS (work everywhere) =====

                // Global: Key "1" - Return to main menu (works from any tab)
                if (event.key == Key.One) {
                    android.util.Log.d("TopMenuScreen2", "Key '1' pressed - Returning to main menu")
                    onShowMainMenu()
                    return@onPreviewKeyEvent true
                }

                // Global: Key "3" - Toggle EPG "Było w TV" section (changed from Key.One)
                if (event.key == Key.Three) {
                    isEpgSectionExpanded = !isEpgSectionExpanded
                    com.uxellence.tv.v3.utils.VersionTracker.setEpgSectionExpanded(context, isEpgSectionExpanded)
                    android.util.Log.d("TopMenuScreen2", "Key '3' pressed - EPG section toggled: $isEpgSectionExpanded (saved to prefs)")
                    return@onPreviewKeyEvent true
                }

                // Global: Key "5" - Toggle CandyBar visibility
                if (event.key == Key.Five) {
                    isCandyBarVisible = !isCandyBarVisible
                    com.uxellence.tv.v3.utils.VersionTracker.setCandyBarVisibility(context, isCandyBarVisible)
                    android.util.Log.d("TopMenuScreen2", "Key '5' pressed - CandyBar visibility toggled: $isCandyBarVisible (saved to prefs)")
                    return@onPreviewKeyEvent true
                }

                // Global: Key "6" - Toggle notification badge
                if (event.key == Key.Six) {
                    showProfileNotificationBadge = !showProfileNotificationBadge
                    com.uxellence.tv.v3.utils.VersionTracker.setNotificationBadge(context, showProfileNotificationBadge)
                    android.util.Log.d("TopMenuScreen2", "Key '6' pressed - Notification badge toggled: $showProfileNotificationBadge (saved to prefs)")
                    return@onPreviewKeyEvent true
                }

                // Global: Key "7" - Open startup mode selection
                if (event.key == Key.Seven) {
                    android.util.Log.d("TopMenuScreen2", "Key '7' pressed - Opening startup mode selection")
                    onNavigateToStartupMode()
                    return@onPreviewKeyEvent true
                }

                // Global: Key "8" - Toggle Nagrania version (v1 expandable ↔ v2 with 4 buttons)
                if (event.key == Key.Eight) {
                    showNagraniaV2 = !showNagraniaV2
                    val version = if (showNagraniaV2) "v2" else "v1"
                    com.uxellence.tv.v3.utils.VersionTracker.setNagraniaVersion(context, version)
                    android.util.Log.d("TopMenuScreen2", "Key '8' pressed - Nagrania version toggled: $version (saved to prefs)")
                    return@onPreviewKeyEvent true
                }

                // ===== END GLOBAL SHORTCUTS =====

                when (globalFocusState.value.currentRow) {
                    0 -> { // Menu navigation
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (focusedRightButton >= 0) {
                                    // In right section - navigate left within buttons or back to main menu
                                    if (focusedRightButton > 0) {
                                        // Skip CandyBar (0) if it's hidden - go directly to last menu item
                                        if (focusedRightButton == 1 && !isCandyBarVisible) {
                                            focusedRightButton = -1
                                            val lastMenuPosition = menuItems.size - 1
                                            globalFocusState.value = globalFocusState.value.copy(currentPosition = lastMenuPosition)
                                        } else {
                                            focusedRightButton--
                                        }
                                    } else {
                                        // From CandyBar (0) back to last menu item
                                        focusedRightButton = -1
                                        val lastMenuPosition = menuItems.size - 1
                                        globalFocusState.value = globalFocusState.value.copy(currentPosition = lastMenuPosition)
                                    }
                                } else {
                                    // Normal menu navigation
                                    globalFocusState.value = GlobalFocusManager.navigateRow(globalFocusState.value, RowDirection.LEFT)
                                }
                                true
                            }
                            /**
                             * RIGHT KEY NAVIGATION (Row 0: Menu)
                             *
                             * Pattern: Last menu item transitions to right section (CandyBar/Profile/Settings)
                             * - Last item → focusedRightButton (0=CandyBar, 1=Profile, 2=Settings)
                             * - Other items → Continue normal menu navigation via GlobalFocusManager
                             *
                             * Current menu order: ODKRYWAJ(0), MOJE(1), TELEWIZJA(2),
                             *                     KINO_PLAY(3), WIDEO(4), APLIKACJE(5), SEARCH(6)
                             * Last item: menuItems.size - 1 (currently 6 = SEARCH)
                             *
                             * @see GlobalFocusManager.navigateRow for normal menu navigation
                             */
                            Key.DirectionRight -> {
                                if (focusedRightButton >= 0) {
                                    // In right section - navigate right within buttons
                                    if (focusedRightButton < 2) {
                                        focusedRightButton++
                                    }
                                    // else: already at Settings (2), stay there
                                } else {
                                    // Check if at last menu item (dynamic based on menuItems.size)
                                    val lastMenuPosition = menuItems.size - 1
                                    if (globalFocusState.value.currentPosition == lastMenuPosition) {
                                        // Move to right section - skip CandyBar if hidden
                                        focusedRightButton = if (isCandyBarVisible) 0 else 1
                                    } else {
                                        // Normal menu navigation
                                        globalFocusState.value = GlobalFocusManager.navigateRow(globalFocusState.value, RowDirection.RIGHT)
                                    }
                                }
                                true
                            }
                            // Keys 1, 5, 6 removed - now handled globally above
                            Key.DirectionDown, Key.Enter, Key.DirectionCenter -> {
                                if (focusedRightButton >= 0) {
                                    // In right section - let button handle click via onFocusChanged
                                    false // Don't consume, delegate to button
                                } else {
                                    // In main menu - transition to content
                                    val currentSection = MenuPositions.getSectionForPosition(globalFocusState.value.currentPosition)
                                    // Allow all sections (including ACCOUNT) to transition to content
                                    globalFocusState.value = GlobalFocusManager.transitionToContent(globalFocusState.value, currentSection)
                                    true
                                }
                            }
                            else -> false
                        }
                    }
                    else -> { // Content area navigation
                        val currentSection = globalFocusState.value.sectionId
                        
                        when (currentSection) {
                            "KINO_PLAY" -> {
                                // KINO_PLAY handles its own navigation entirely
                                false // Let VodWithChannels handle all keys
                            }
                            "WIDEO" -> {
                                // WIDEO handles its own navigation entirely
                                false // Let VodWithChannels handle all keys
                            }
                            "TELEWIZJA" -> {
                                // TELEWIZJA handles its own navigation, but intercept key "2" for EPG refresh
                                if (event.key == Key.Two && event.type == KeyEventType.KeyDown) {
                                    // Force EPG refresh
                                    android.util.Log.d("TELEWIZJA_DEBUG", "Key '2' pressed - Force refreshing EPG...")
                                    coroutineScope.launch {
                                        repository.clearCache()
                                        repository.startBackgroundRefresh()
                                        android.util.Log.d("TELEWIZJA_DEBUG", "EPG refresh triggered, wait 30 seconds...")
                                    }
                                    true
                                } else {
                                    false // Let TelewizjaChannelsScreen handle other keys
                                }
                            }
                            "MOJE" -> {
                                // MOJE handles its own navigation entirely
                                false // Let MojeChannelsScreen handle all keys
                            }
                            "APLIKACJE" -> {
                                // APLIKACJE handles its own navigation entirely
                                false // Let AplikacjeChannelsScreen handle all keys
                            }
                            "ODKRYWAJ" -> {
                                // ODKRYWAJ handles its own navigation entirely
                                false // Let OdkrywajChannelsScreen handle all keys
                            }
                            "SEARCH" -> {
                                // SEARCH handles its own navigation entirely
                                false // Let SearchScreenNew handle all keys
                            }
                            "ACCOUNT" -> {
                                // ACCOUNT handles its own navigation entirely
                                false // Let AccountChannelsScreen handle all keys
                            }
                            else -> {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // Wrap FullPageContent to block focus when dialog is open
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showPipDialog) {
                        // When dialog open, overlay blocks background keys but lets dialog keys through
                        // CRITICAL: NO .focusable() here - overlay must NOT capture focus!
                        Modifier
                            .onPreviewKeyEvent { event ->
                                // Let dialog handle UP/DOWN/ENTER/BACK for its own navigation
                                when (event.key) {
                                    Key.DirectionUp,
                                    Key.DirectionDown,
                                    Key.Enter,
                                    Key.DirectionCenter,
                                    Key.Back -> false  // Pass through to dialog
                                    else -> true  // Block all other keys from background
                                }
                            }
                    } else {
                        Modifier  // Normal state - no blocking
                    }
                )
        ) {
            FullPageContent(
                selectedSection = globalFocusState.value.sectionId,
                onReturnToMenu = { globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value) },
                onUserNavigated = { freshPipMode = false },  // Clear fresh PIP mode when user navigates
                shouldAutoFocus = globalFocusState.value.currentRow != 0,
                sx = { sx(it) },
                sy = { sy(it) },
                globalFocusState = globalFocusState,
                onNavigateToEpg = onNavigateToEpg,
                onNavigateToEpgDay = onNavigateToEpgDay,
                onNavigateToStartupMode = onNavigateToStartupMode,
                onFocusRestored = onFocusRestored,
                restoredTelewizjaFocus = restoredTelewizjaFocus,
                onNavigateToChannelGrid = onNavigateToChannelGrid,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                isEpgSectionExpanded = isEpgSectionExpanded,
                onEpgSectionExpandedChange = { expanded ->
                    isEpgSectionExpanded = expanded
                },
                showNagraniaV2 = showNagraniaV2,
                onShowNagraniaV2Change = { v2 ->
                    showNagraniaV2 = v2
                }
            )
        }

        // Gradient from top (same as EPG Day TopMenuOverlay)
        // Solid purple at top (0-30%), fades to transparent (30-60%)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(TOP_MENU_GRADIENT_HEIGHT))  // 600px height
                .align(Alignment.TopCenter)
                .zIndex(5f)  // Above content (0f), below menu (10f)
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xFF48227C),    // 0%: Solid purple at top
                        0.3f to Color(0xFF48227C),    // 30%: Still solid purple
                        0.6f to Color(0x0048227C),    // 60%: Transparent purple
                        startY = 0f,
                        endY = sy(TOP_MENU_GRADIENT_HEIGHT).value
                    )
                )
        )

        // Smooth loader overlay (Netflix-style transition)
        AnimatedVisibility(
            visible = isContentLoading,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF48227C)), // Menu background color
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF5AECD3), // Aqua focus color
                        modifier = Modifier.size(56.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Ładowanie...",
                        color = Color(0xFFEEEEEE),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        val compatMenuState = TopMenuState2(
            focusedItemId = MenuPositions.getSectionForPosition(globalFocusState.value.currentPosition),
            selectedItemId = globalFocusState.value.sectionId,
            isMenuFocused = globalFocusState.value.currentRow == 0 && focusedRightButton == -1
        )

        TopMenuBar2(
            menuItems = menuItems,
            menuState = compatMenuState,
            focusRequesters = focusRequesters,
            onMenuItemFocused = { itemId ->
                val newPosition = MenuPositions.getPositionForSection(itemId)
                globalFocusState.value = globalFocusState.value.copy(currentPosition = newPosition)
            },
            sx = { sx(it) },
            sy = { sy(it) },
            isVodMode = globalFocusState.value.sectionId == "KINO_PLAY",
            currentSelectedSection = globalFocusState.value.sectionId,
            isInStartContent = false,
            focusedRightButton = focusedRightButton,
            rightButtonFocusRequesters = rightButtonFocusRequesters,
            onCandyBarClick = {
                // Navigate to Points History section
                globalFocusState.value = globalFocusState.value.copy(
                    sectionId = "POINTS_HISTORY",
                    currentRow = 0,
                    currentPosition = 0
                )
                focusedRightButton = 0
            },
            onProfileClick = {
                // Navigate to Account section (focus transfers to content)
                globalFocusState.value = GlobalFocusManager.transitionToContent(
                    globalFocusState.value,
                    "ACCOUNT"
                )
                focusedRightButton = -1 // Clear right section focus
            },
            onSettingsClick = {
                // Open Android TV system settings
                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                context.startActivity(intent)
                focusedRightButton = 2
            },
            isCandyBarVisible = isCandyBarVisible,
            showProfileNotificationBadge = showProfileNotificationBadge,
            modifier = Modifier.align(Alignment.TopCenter).zIndex(10f)
        )

        // PIP (Picture-in-Picture) Overlay
        if (pipPlayer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = sy(80), end = sx(80))
                    .size(width = sx(480), height = sy(270))  // 480×270px (16:9 aspect ratio)
                    .zIndex(1000f)  // Highest - above everything including dialog
            ) {
                AndroidView(
                    factory = { ctx ->
                        com.google.android.exoplayer2.ui.PlayerView(ctx).apply {
                            useController = false  // No controls, just video
                            player = pipPlayer
                        }
                    },
                    update = { playerView ->
                        // Upewnij się że player odtwarza po przypisaniu
                        pipPlayer?.let { player ->
                            if (!player.isPlaying) {
                                player.playWhenReady = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // PIP Dialog - shown when PLAY/PAUSE pressed with active PIP
        if (showPipDialog && pipPlayer != null) {
            PipDialogMenu(
                onDismiss = {
                    showPipDialog = false
                },
                onFullscreen = {
                    onReturnToEpgDay()
                    onClosePip()
                },
                onClose = {
                    onClosePip()
                },
                sx = ::sx,
                sy = ::sy
            )
        }
    }
}

@Composable
fun TopMenuBar2(
    menuItems: List<MenuItem2>,
    menuState: TopMenuState2,
    focusRequesters: Map<String, FocusRequester>,
    onMenuItemFocused: (String) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    isVodMode: Boolean = false,
    currentSelectedSection: String = "",
    isInStartContent: Boolean = false,
    focusedRightButton: Int = -1,
    rightButtonFocusRequesters: Map<Int, FocusRequester> = emptyMap(),
    onCandyBarClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isCandyBarVisible: Boolean = true,
    showProfileNotificationBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .padding(top = sy(20), start = sx(20))
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        // Layer 1: Main content row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(97)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(22)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(sy(97))
                    .wrapContentWidth()
                    .background(
                        color = Color(0x4A000000),
                        shape = RoundedCornerShape(sx(49))
                    )
                    .padding(horizontal = sx(10), vertical = sy(8))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(13)),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(sy(81))
                ) {
                    val containerItems = menuItems
                    containerItems.forEach { item ->
                        if (item.id == "SEARCH") {
                            MenuSearchIcon2(
                                isSelected = menuState.selectedItemId == item.id,
                                isFocused = menuState.focusedItemId == item.id && menuState.isMenuFocused,
                                focusRequester = focusRequesters[item.id]!!,
                                onFocused = { onMenuItemFocused(item.id) },
                                currentSelectedSection = currentSelectedSection,
                                isInStartContent = isInStartContent,
                                sx = sx,
                                sy = sy
                            )
                        } else {
                            MenuButton2(
                                title = item.title,
                                isSelected = menuState.selectedItemId == item.id,
                                isFocused = menuState.focusedItemId == item.id && menuState.isMenuFocused,
                                focusRequester = focusRequesters[item.id]!!,
                                onFocused = { onMenuItemFocused(item.id) },
                                currentSelectedSection = currentSelectedSection,
                                isInStartContent = isInStartContent,
                                sx = sx,
                                sy = sy
                            )
                        }
                    }
                }
            }
        }

        // Right section: CandyBar + Profile + Settings + Clock
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(15)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // CandyBar button with toggle visibility
            AnimatedVisibility(
                visible = isCandyBarVisible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                if (rightButtonFocusRequesters.containsKey(0)) {
                    CandyBarButton(
                        points = 40,
                        days = 21,
                        isFocused = focusedRightButton == 0,
                        focusRequester = rightButtonFocusRequesters[0]!!,
                        onClick = onCandyBarClick,
                        sx = sx,
                        sy = sy
                    )
                }
            }

            // Profile button
            if (rightButtonFocusRequesters.containsKey(1)) {
                ProfileButton(
                    isFocused = focusedRightButton == 1,
                    focusRequester = rightButtonFocusRequesters[1]!!,
                    onClick = onProfileClick,
                    showBadge = showProfileNotificationBadge,
                    sx = sx,
                    sy = sy
                )
            }

            // Settings button
            if (rightButtonFocusRequesters.containsKey(2)) {
                SettingsButton(
                    isFocused = focusedRightButton == 2,
                    focusRequester = rightButtonFocusRequesters[2]!!,
                    onClick = onSettingsClick,
                    sx = sx,
                    sy = sy
                )
            }

            Spacer(modifier = Modifier.width(sx(9))) // 24px total gap to clock (15+9)

            // Clock
            Text(
                text = "${currentTime.hour.toString().padStart(2, '0')}:${currentTime.minute.toString().padStart(2, '0')}",
                color = Color.White,
                fontSize = sy(40).value.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = sy(39).value.sp,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(end = sx(30))
            )
        }
        }

        // Layer 2: Tooltip overlay (z-index on top, doesn't affect layout)
        if (focusedRightButton >= 0) {
            val labelText = when (focusedRightButton) {
                0 -> "Zarządzaj punktami"
                1 -> "Konto, profile"
                2 -> "Ustawienia systemowe"
                else -> ""
            }

            // Calculate offset from right edge to center text below button
            // Box width is 400px, offset = icon_center - box_width/2 to center Box on icon
            val boxHalfWidth = 200
            val labelOffsetFromRight = when (focusedRightButton) {
                0 -> sx(511 - boxHalfWidth)  // CandyBar center (511) - half box width
                1 -> sx(294 - boxHalfWidth)  // Profile center (294) - half box width
                2 -> sx(199 - boxHalfWidth)  // Settings center (199) - half box width
                else -> sx(0)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(
                        x = -labelOffsetFromRight,
                        y = sy(105)  // 97px row height + 8px gap
                    )
                    .width(sx(400))  // Fixed width for centering
                    .wrapContentHeight()
            ) {
                Text(
                    text = labelText,
                    color = Color(0xFF5FEDD4),  // Aqua color
                    fontSize = sy(28).value.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.TopCenter)  // Center text in Box
                )
            }
        }
    }
}

@Composable
private fun CandyBarButton(
    points: Int = 40,
    days: Int = 21,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused) Color(0xFF5AECD3) else Color(0x4A000000)
    val contentColor = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)

    Box(
        modifier = modifier
            .widthIn(max = sx(324))
            .wrapContentWidth()
            .height(sy(80))
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(sx(64))
            )
            .focusRequester(focusRequester)
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = sx(32)),
            horizontalArrangement = Arrangement.spacedBy(sx(16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left section: Wallet icon + points
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(8)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_wallet),
                    contentDescription = "Punkty",
                    tint = contentColor,
                    modifier = Modifier.size(sx(24), sy(24))
                )
                Text(
                    text = "$points pkt",
                    color = contentColor,
                    fontSize = (16 * sx(1).value / 1).sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Right section: Calendar icon + days
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(8)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_calendar),
                    contentDescription = "Dni",
                    tint = contentColor,
                    modifier = Modifier.size(sx(24), sy(24))
                )
                Text(
                    text = "$days dni",
                    color = contentColor,
                    fontSize = (16 * sx(1).value / 1).sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ProfileButton(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    showBadge: Boolean = false,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused) Color(0xFF5AECD3) else Color(0x4A000000)
    val contentColor = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)

    Box(
        modifier = modifier
            .size(sx(80), sy(80))
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .focusRequester(focusRequester)
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box {
            Image(
                painter = painterResource(id = R.drawable.lamp),
                contentDescription = "Profil",
                modifier = Modifier.size(sx(40), sy(40))
            )

            // Notification badge (upper-right corner on button area, not on icon)
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .size(sx(32), sy(32))
                        .offset(x = sx(34), y = -sy(10))
                        .background(Color.Red, CircleShape)
                        .border(sx(3), Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused) Color(0xFF5AECD3) else Color(0x4A000000)
    val contentColor = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)

    Box(
        modifier = modifier
            .size(sx(80), sy(80))
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(sx(64))
            )
            .focusRequester(focusRequester)
            .focusable()
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Ustawienia",
            tint = contentColor,
            modifier = Modifier.size(sx(32), sy(32))
        )
    }
}

@Composable
private fun MenuSearchIcon2(
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    currentSelectedSection: String = "",
    isInStartContent: Boolean = false,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val backgroundColor = when {
        isFocused && !isInStartContent -> Color(0xFF5AECD3) // Aqua when focused
        isSelected -> Color.White // White when selected
        else -> Color(0x08FFFFFF)
    }
    
    val iconColor = when {
        isFocused -> Color(0xFF48227C) // Purple when focused
        isSelected -> Color(0xFF48227C) // Purple when selected
        else -> Color(0xFFEEEEEE)
    }

    Box(
        modifier = Modifier
            .size(sx(80), sy(80))
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = iconColor,
            modifier = Modifier.size(sx(48), sy(48))
        )
    }
}

@Composable
private fun ShortcutAddButton(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val cardWidth = sx(210)
    val cardHeight = sy(279)

    // Container Box (transparent, no border, no background)
    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
    ) {
        // Plus icon in circle (centered) - ONLY THIS is focusable
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(sx(120), sy(120))
                .background(
                    color = if (isFocused) Color(0xFF5AECD3) else Color(0x1AEEEEEE),
                    shape = CircleShape
                )
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    onFocusChange(focusState.isFocused)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Shortcut",
                tint = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE),
                modifier = Modifier.size(sx(64), sy(64))
            )
        }
    }
}

@Composable
private fun MenuButton2(
    title: String,
    isSelected: Boolean,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    currentSelectedSection: String = "",
    isInStartContent: Boolean = false,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val backgroundColor = when {
        isFocused && !isInStartContent -> Color(0xFF5AECD3) // Show focus when NOT in START content
        isSelected -> Color.White
        else -> Color(0x0AEEEEEE)
    }
    
    val textColor = when {
        isFocused -> Color(0xFF48227C)
        isSelected -> Color(0xFF48227C)
        else -> Color(0xFFEEEEEE)
    }

    Box(
        modifier = Modifier
            .height(sy(80))
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            .padding(horizontal = sx(32)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = sy(24).value.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = sy(32).value.sp,
            letterSpacing = 0.48.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FullPageContent(
    selectedSection: String,
    onReturnToMenu: () -> Unit,
    onUserNavigated: () -> Unit = {},  // NEW: Callback when user navigates (clears fresh PIP mode)
    shouldAutoFocus: Boolean,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToEpg: () -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> },
    onNavigateToStartupMode: () -> Unit = {},  // Navigate to startup mode selection
    onFocusRestored: () -> Unit = {},
    restoredTelewizjaFocus: FocusState? = null,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    isEpgSectionExpanded: Boolean = false,
    onEpgSectionExpandedChange: (Boolean) -> Unit = {},
    showNagraniaV2: Boolean = false,
    onShowNagraniaV2Change: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Track fresh entry (transition from menu Row 0 → content Row 1+)
    // Used to auto-focus first interactive element only when user enters section, not while hovering tab
    var previousRow by remember { mutableStateOf(0) }
    val isFreshEntry = remember(globalFocusState.value.currentRow) {
        val wasFreshEntry = previousRow == 0 && globalFocusState.value.currentRow > 0
        previousRow = globalFocusState.value.currentRow
        wasFreshEntry
    }

    when (selectedSection) {
        "SEARCH" -> {
            SearchScreenNew(onReturnToMenu = onReturnToMenu, shouldAutoFocus = isFreshEntry)
        }
        "MOJE" -> {
            MojeScreenContent(
                globalFocusState = globalFocusState,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                sx = sx,
                sy = sy,
                showNagraniaV2 = showNagraniaV2,
                onShowNagraniaV2Change = onShowNagraniaV2Change
            )
        }
        "ODKRYWAJ" -> {
            OdkrywajScreenContent(
                globalFocusState = globalFocusState,
                onUserNavigated = onUserNavigated,  // Clear fresh PIP mode on navigation
                onNavigateToChannelGrid = onNavigateToChannelGrid,
                onNavigateToVodGrid = onNavigateToVodGrid,
                appIconsData = emptyMap(),  // TODO: Pass real appIconsData
                sx = sx,
                sy = sy
            )
        }
        "TELEWIZJA" -> {
            TelewizjaScreenContent(
                globalFocusState = globalFocusState,
                sx = sx,
                sy = sy,
                onNavigateToEpg = onNavigateToEpg,
                onNavigateToEpgDay = onNavigateToEpgDay,
                onFocusRestored = onFocusRestored,
                restoredTelewizjaFocus = restoredTelewizjaFocus,
                onNavigateToChannelGrid = onNavigateToChannelGrid,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                isEpgSectionExpanded = isEpgSectionExpanded,
                onEpgSectionExpandedChange = onEpgSectionExpandedChange
            )
        }
        "KINO_PLAY" -> {
            VodScreenContent(
                globalFocusState = globalFocusState,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                sx = sx,
                sy = sy
            )
        }
        "WIDEO" -> {
            WideoScreenContent(
                globalFocusState = globalFocusState,
                onNavigateToVodGrid = onNavigateToVodGrid,
                sx = sx,
                sy = sy
            )
        }
        "APLIKACJE" -> {
            AplikacjeScreenContent(
                globalFocusState = globalFocusState,
                onNavigateToChannelGrid = onNavigateToChannelGrid,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                appIconsData = emptyMap(),  // TODO: Pass real appIconsData
                sx = sx,
                sy = sy
            )
        }
        "ACCOUNT" -> {
            AccountScreenContent(
                globalFocusState = globalFocusState,
                onNavigateToStartupMode = onNavigateToStartupMode,
                sx = sx,
                sy = sy
            )
        }
        "POINTS_HISTORY" -> {
            PointsHistoryScreenContent(
                globalFocusState = globalFocusState,
                sx = sx,
                sy = sy
            )
        }
        "START" -> {
            StartScreenContent(
                globalFocusState = globalFocusState,
                onNavigateToChannelGrid = onNavigateToChannelGrid,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                sx = sx,
                sy = sy
            )
        }
        else -> {
            Text(
                text = "$selectedSection - Section",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.fillMaxSize().wrapContentSize()
            )
        }
    }
}

@Composable
private fun MojeScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    showNagraniaV2: Boolean = false,
    onShowNagraniaV2Change: (Boolean) -> Unit = {}
) {
    var resetTrigger by remember { mutableStateOf(0) }
    
    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "MOJE") {
            resetTrigger++
        }
    }
    
    MojeChannelsScreen(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        onNavigateToVodGrid = onNavigateToVodGrid,
        onNavigateToKinoGrid = onNavigateToKinoGrid,
        shouldAutoFocus = globalFocusState.value.sectionId == "MOJE" && globalFocusState.value.currentRow > 0,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger,
        showNagraniaV2 = showNagraniaV2,
        onShowNagraniaV2Change = onShowNagraniaV2Change
    )
}

@Composable
private fun AplikacjeScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var resetTrigger by remember { mutableStateOf(0) }

    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "APLIKACJE") {
            resetTrigger++
        }
    }

    AplikacjeChannelsScreen(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        shouldAutoFocus = globalFocusState.value.sectionId == "APLIKACJE" && globalFocusState.value.currentRow > 0,
        onNavigateToChannelGrid = onNavigateToChannelGrid,
        onNavigateToVodGrid = onNavigateToVodGrid,
        onNavigateToKinoGrid = onNavigateToKinoGrid,
        appIconsData = appIconsData,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger
    )
}

// ODKRYWAJ section functions

@Composable
private fun OdkrywajScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onUserNavigated: () -> Unit = {},  // NEW: Callback when user navigates content
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var resetTrigger by remember { mutableIntStateOf(0) }

    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "ODKRYWAJ") {
            resetTrigger++
        }
    }

    OdkrywajChannelsScreen(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        onUserNavigated = {
            // Clear fresh PIP mode when user navigates content
            onUserNavigated()
        },
        shouldAutoFocus = globalFocusState.value.sectionId == "ODKRYWAJ" && globalFocusState.value.currentRow > 0,
        onNavigateToChannelGrid = onNavigateToChannelGrid,
        onNavigateToVodGrid = onNavigateToVodGrid,
        onNavigateToKinoGrid = onNavigateToKinoGrid,
        appIconsData = appIconsData,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger
    )
}

@Composable
private fun TelewizjaScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    onNavigateToEpg: () -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> },
    onFocusRestored: () -> Unit = {},
    restoredTelewizjaFocus: FocusState? = null,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    isEpgSectionExpanded: Boolean = false,
    onEpgSectionExpandedChange: (Boolean) -> Unit = {}
) {
    var resetTrigger by remember { mutableStateOf(0) }

    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "TELEWIZJA") {
            resetTrigger++
        }
    }

    TelewizjaChannelsScreen(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        shouldAutoFocus = globalFocusState.value.sectionId == "TELEWIZJA"
                          && globalFocusState.value.currentRow > 0
                          && restoredTelewizjaFocus == null,  // NIE auto-focus gdy restorujemy z EPG Day Test
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger,
        onNavigateToEpg = onNavigateToEpg,
        onNavigateToEpgDay = onNavigateToEpgDay,
        onContentFocusRestored = { row ->
            // Update globalFocusState to deactivate menu focus
            globalFocusState.value = globalFocusState.value.copy(currentRow = row)
            android.util.Log.d("TELEWIZJA_FOCUS", "Updated globalFocusState.currentRow = $row (menu deactivated)")
        },
        onFocusRestored = onFocusRestored,
        restoredTelewizjaFocus = restoredTelewizjaFocus,
        sectionId = globalFocusState.value.sectionId,
        onNavigateToChannelGrid = onNavigateToChannelGrid,
        onNavigateToVodGrid = onNavigateToVodGrid,
        onNavigateToKinoGrid = onNavigateToKinoGrid,
        isEpgSectionExpanded = isEpgSectionExpanded,
        onEpgSectionExpandedChange = onEpgSectionExpandedChange
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OdkrywajChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    onUserNavigated: () -> Unit = {},  // NEW: Callback when user navigates content
    shouldAutoFocus: Boolean = false,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current

    // Row 0: Slider Mix, Row 1: Skróty, Row 2: Popularne,
    // Row 3: Nowości, Row 4: Nowe filmy, Row 5: Top 10, Row 6: Kolekcje
    val channels = listOf(
        "Slider Mix",
        "Skróty",
        "Popularne",
        "Nowości",
        "Nowe filmy",
        "Top 10",
        "Kolekcje"
    )

    // Define channel types
    val channelTypes = remember {
        mapOf(
            "Slider Mix" to "slider-max",
            "Skróty" to "shortcuts",
            "Popularne" to "horizontal",
            "Nowości" to "horizontal",
            "Nowe filmy" to "vertical",
            "Top 10" to "top10",
            "Kolekcje" to "collection-slider"
        )
    }

    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        val kinoPlayMovies = VodDataCache.getKinoPlayMovies()

        if (vodContentList.isNotEmpty() && kinoPlayMovies.isNotEmpty()) {
            // Group VOD by category for collections
            val vodByCategory = vodContentList.groupBy { it.category }
            val collections = listOf(
                VodContent("test_horrory", "Horrory", "Kolekcja horrorów na Halloween", "Horror", "https://images6.alphacoders.com/140/1400473.jpg", "", ""),
                VodContent("test_thrillery", "Thrillery", "Ekscytujące thrillery", "Thriller", "https://images6.alphacoders.com/135/1356452.jpeg", "", ""),
                VodContent("test_komedie", "Komedie", "Najlepsze komedie", "Komedia", "https://images6.alphacoders.com/135/1356452.jpeg", "", ""),
                VodContent("test_dokumenty", "Dokumenty", "Fascynujące dokumenty", "Dokumentalny", "https://images6.alphacoders.com/135/1356452.jpeg", "", ""),
                VodContent("test_scifi", "Sci-Fi", "Fantastyka naukowa", "Sci-Fi", "https://images4.alphacoders.com/135/1353792.png", "", "")
            )

            channels.associateWith { channelName ->
                when (channelName) {
                    "Slider Mix" -> vodContentList.shuffled().take(10) // VOD Play content for big slider
                    "Teraz w TV" -> vodContentList.shuffled().take(10) // Horizontal VOD content
                    "Najczęściej oglądane" -> vodContentList.shuffled().take(10) // Horizontal VOD content
                    "Skróty" -> emptyList() // Special type, no grid content
                    "Dla dzieci", "Dokumenty", "Filmy i seriale HBO", "Informacyjne" -> emptyList() // App-icons type, no VOD grid content
                    "Top 10", "Nowe filmy" -> kinoPlayMovies.shuffled().take(10) // Top 10 and vertical channels
                    "Kolekcje" -> collections // Collections slider
                    else -> vodContentList.shuffled().take(10) // Horizontal
                }
            }
        } else {
            emptyMap()
        }
    }

    // Shortcuts data
    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "Moja lista kanałów", ShortcutIcon.LottieIcon("tvaa.lottie")),
            ShortcutItem("2", "Nagrania", ShortcutIcon.LottieIcon("nagrania.lottie")),
            ShortcutItem("3", "Wypożyczone", ShortcutIcon.LottieIcon("wypozyczone.lottie")),
            ShortcutItem("4", "Disney Plus", ShortcutIcon.VectorIcon(R.drawable.disney_plus_logo)),
            ShortcutItem("5", "Do obejrzenia", ShortcutIcon.LottieIcon("doobejzenia.lottie"))
        )
    }

    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie

    // Track if user has navigated (for PIP mode)
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(focusedRowIndex, focusedColIndex) {
        if (!hasNavigated && focusedColIndex != -2) {
            hasNavigated = true
            android.util.Log.d("PIP_NAVIGATION", "User navigated in ODKRYWAJ - clearing fresh PIP mode")
            onUserNavigated()
        }
    }

    // Reset focus state when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 0
            focusedColIndex = -2
        }
    }

    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                if (rowIndex == 1) {
                    // Row 1 (shortcuts): create FocusRequesters for items 0-5 (5 shortcuts + plus button)
                    repeat(6) { colIndex ->
                        put(Pair(rowIndex, colIndex), FocusRequester())
                    }
                } else {
                    // Other rows: CategoryIcon + Fixed focus position
                    put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon (or slider placeholder)
                    put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                }
            }
        }
    }

    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    // Auto-reset LazyListState for unfocused rows
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            // Reduced from 150ms to 0ms for instant tab switching
            kotlinx.coroutines.delay(0)
            repeat(channels.size) { rowIndex ->
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            // When coming from menu, focus on first slide of slider-max (0, 0)
            focusedRowIndex = 0
            focusedColIndex = 0 // Focus on first slide
            // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
            val firstSlideFocusRequester = channelFocusRequesters[Pair(0, 0)]
            if (firstSlideFocusRequester != null) {
                Log.d("ODKRYWAJ_DEBUG", "Auto-focus: Setting state (0, 0) and requesting focus on first slide")
                firstSlideFocusRequester.requestFocus()
            }
        }
        isInitialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleOdkrywajNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu
                )
            }
            .focusable()
    ) {
        OdkrywajChannelRowsLayout(
            channels = channels,
            channelTypes = channelTypes,
            gridContent = gridContent,
            shortcuts = shortcuts,
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col ->
                Log.d("ODKRYWAJ_DEBUG", "Focus changed to row $row, col $col")
                focusedRowIndex = row
                focusedColIndex = col
            },
            onNavigateToChannelGrid = onNavigateToChannelGrid,
            onNavigateToVodGrid = onNavigateToVodGrid,
            onNavigateToKinoGrid = onNavigateToKinoGrid,
            appIconsData = appIconsData,
            lazyListStates = lazyListStates,
            sx = sx,
            sy = sy
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TelewizjaChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0,
    onNavigateToEpg: () -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> },
    onContentFocusRestored: (Int) -> Unit = {},
    onFocusRestored: () -> Unit = {},
    restoredTelewizjaFocus: FocusState? = null,
    sectionId: String = "TELEWIZJA",
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    isEpgSectionExpanded: Boolean = false,
    onEpgSectionExpandedChange: (Boolean) -> Unit = {}
) {
    android.util.Log.d("EPG_DEBUG", "=== TelewizjaChannelsScreen RENDERED ===")

    val context = LocalContext.current

    // EPG Repository for "Teraz w TV"
    val epgRepository = remember {
        android.util.Log.d("EPG_DEBUG", "Creating EPG Repository...")
        com.uxellence.tv.v3.repository.EpgRepository.getInstance(context)
    }

    // State dla "Teraz w TV" z EPG
    var terazWTvPrograms by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var isLoadingEpg by remember { mutableStateOf(true) }

    // Załadować EPG i aktualne programy
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("EPG_LOADING", "⏱️ [Teraz w TV] LaunchedEffect START at $startTime")
        try {
            // Refresh EPG data (jeśli cache expired)
            android.util.Log.d("EPG_LOADING", "   Calling startBackgroundRefresh...")
            epgRepository.startBackgroundRefresh()

            // Załadować aktualne programy
            android.util.Log.d("EPG_LOADING", "   Loading current programs...")
            terazWTvPrograms = com.uxellence.tv.v3.utils.EpgAdapter.getCurrentProgramsAsVodContent(epgRepository, context)

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            android.util.Log.d("EPG_LOADING", "✅ [Teraz w TV] Loaded ${terazWTvPrograms.size} programs in ${duration}ms")
            if (terazWTvPrograms.isNotEmpty()) {
                android.util.Log.d("EPG_LOADING", "   First program: ID='${terazWTvPrograms[0].id}', title='${terazWTvPrograms[0].title}'")
            }

            isLoadingEpg = false
        } catch (e: Exception) {
            android.util.Log.e("EPG_LOADING", "❌ [Teraz w TV] Failed to load EPG", e)
            isLoadingEpg = false
        }
    }

    // Auto-refresh "Teraz w TV" co 30 minut
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30 * 60 * 1000L) // 30 minut
            try {
                terazWTvPrograms = com.uxellence.tv.v3.utils.EpgAdapter.getCurrentProgramsAsVodContent(epgRepository, context)
            } catch (e: Exception) {
                android.util.Log.e("TELEWIZJA", "Failed to refresh EPG", e)
            }
        }
    }

    // State dla "Najczęściej" - filmy pełnometrażowe z ostatnich 24h
    var najczesciejMovies by remember { mutableStateOf<List<VodContent>>(emptyList()) }

    // State dla "Seriale" - seriale z ostatnich 24h
    var serialePrograms by remember { mutableStateOf<List<VodContent>>(emptyList()) }

    // State dla "Sport" - programy sportowe z ostatnich 24h
    var sportPrograms by remember { mutableStateOf<List<VodContent>>(emptyList()) }

    // State dla "Teleturnieje" - teleturnieje z ostatnich 24h
    var teleturniejePrograms by remember { mutableStateOf<List<VodContent>>(emptyList()) }

    // State dla "Kategorie EPG" - current EPG programs
    var epgCategoriesProgramsAll by remember { mutableStateOf<List<VodContent>>(emptyList()) }
    var epgCategoriesVisibleCount by remember { mutableStateOf(9) } // Show 9 initially
    val epgCategoriesPrograms = remember(epgCategoriesProgramsAll, epgCategoriesVisibleCount) {
        epgCategoriesProgramsAll.take(epgCategoriesVisibleCount)
    }

    // Załadować filmy pełnometrażowe z ostatnich 24h dla "Najczęściej"
    LaunchedEffect(Unit) {
        android.util.Log.d("EPG_DEBUG", "=== Loading feature-length movies from last 24h ===")
        try {
            najczesciejMovies = com.uxellence.tv.v3.utils.EpgAdapter.getLast24HoursMoviesAsVodContent(epgRepository)
            android.util.Log.d("EPG_DEBUG", "Feature movies loaded: ${najczesciejMovies.size}")
        } catch (e: Exception) {
            android.util.Log.e("EPG_DEBUG", "Failed to load movies from last 24h", e)
        }
    }

    // Załadować aktualne programy dla "Kategorie EPG" (using ChannelManager like EpgDayScreen)
    LaunchedEffect(Unit) {
        android.util.Log.d("EPG_DEBUG", "=== Loading current EPG programs for Kategorie EPG ===")
        try {
            epgCategoriesProgramsAll = com.uxellence.tv.v3.utils.EpgAdapter.getCurrentProgramsAsVodContent(epgRepository, context)
            android.util.Log.d("EPG_DEBUG", "Current programs loaded: ${epgCategoriesProgramsAll.size}")
        } catch (e: Exception) {
            android.util.Log.e("EPG_DEBUG", "Failed to load current programs", e)
        }
    }

    // Załadować seriale, sport, teleturnieje z ostatnich 24h
    LaunchedEffect(Unit) {
        android.util.Log.d("EPG_DEBUG", "=== Loading EPG categories from last 24h ===")
        try {
            serialePrograms = com.uxellence.tv.v3.utils.EpgAdapter.getLast24HoursSeriesAsVodContent(epgRepository)
            android.util.Log.d("EPG_DEBUG", "Series loaded: ${serialePrograms.size}")

            sportPrograms = com.uxellence.tv.v3.utils.EpgAdapter.getLast24HoursSportsAsVodContent(epgRepository)
            android.util.Log.d("EPG_DEBUG", "Sports loaded: ${sportPrograms.size}")

            teleturniejePrograms = com.uxellence.tv.v3.utils.EpgAdapter.getLast24HoursGameShowsAsVodContent(epgRepository)
            android.util.Log.d("EPG_DEBUG", "Game shows loaded: ${teleturniejePrograms.size}")
        } catch (e: Exception) {
            android.util.Log.e("EPG_DEBUG", "Failed to load EPG categories", e)
        }
    }


    // Load TV channel logos from JSON
    val tvChannelLogos = remember { loadTvChannelsFromAssets(context) }
    val filmyISerialeChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "filmy-i-seriale") }  // 50-529
    val kidsChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "dla-dzieci") }  // 600-687
    val sportChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "sport") }  // 700-805
    val docChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "dokumenty") }  // 500-987
    val newsChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "informacyjne") }  // 200-209
    val mojaListaChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "moja-lista") }  // 1-9

    // EPG section collapse state - MOVED TO TOP (now defined globally for keyboard shortcut access)
    // var isEpgSectionExpanded - defined at line ~710

    // Toggle function for EPG section (triggered by key "3")
    val toggleEpgSection: () -> Unit = {
        onEpgSectionExpandedChange(!isEpgSectionExpanded)
        android.util.Log.d("TELEWIZJA_DEBUG", "EPG section expanded: ${!isEpgSectionExpanded} (key '3' pressed)")
    }

    // New structure: Header rows + content channels + moved rows
    // Dynamic list: EPG section (header + 4 channels) collapse/expand with key "1"
    val channels = remember(isEpgSectionExpanded) {
        if (isEpgSectionExpanded) {
            // EXPANDED: All 16 channels visible (EPG section shown)
            listOf(
                "[HEADER] Teraz w TV",          // Row 0 - header above EPG
                "Kategorie EPG",                 // Row 1 - EPG thumbnails
                "Skróty v2",                     // Row 2 - shortcuts
                "[HEADER] Było w TV - oglądaj teraz", // Row 3 - header above movies ← EPG SECTION
                "FILMY",                         // Row 4 ← EPG SECTION
                "SERIALE",                       // Row 5 ← EPG SECTION
                "SPORT",                         // Row 6 ← EPG SECTION
                "TELETURNIEJE",                  // Row 7 ← EPG SECTION
                "Wszystkie kanały",              // Row 8
                "Moja lista kanałów",            // Row 9
                "Dla dzieci",                    // Row 10
                "Sport",                         // Row 11
                "Dokumenty",                     // Row 12
                "Filmy i seriale",               // Row 13
                "Informacyjne",                  // Row 14
                "Teraz w TV"                     // Row 15 - horizontal with current programs
            )
        } else {
            // COLLAPSED: 11 channels (EPG section hidden - press "1" to show)
            listOf(
                "[HEADER] Teraz w TV",          // Row 0 - header above EPG
                "Kategorie EPG",                 // Row 1 - EPG thumbnails
                "Skróty v2",                     // Row 2 - shortcuts
                // "[HEADER] Było w TV - oglądaj teraz" ← HIDDEN
                // "FILMY",                      ← HIDDEN
                // "SERIALE",                    ← HIDDEN
                // "SPORT",                      ← HIDDEN
                // "TELETURNIEJE",               ← HIDDEN
                "Wszystkie kanały",              // Row 3 (was 8)
                "Moja lista kanałów",            // Row 4 (was 9)
                "Dla dzieci",                    // Row 5 (was 10)
                "Sport",                         // Row 6 (was 11)
                "Dokumenty",                     // Row 7 (was 12)
                "Filmy i seriale",               // Row 8 (was 13)
                "Informacyjne",                  // Row 9 (was 14)
                "Teraz w TV"                     // Row 10 (was 15)
            )
        }
    }

    // Define channel types
    val channelTypes = remember {
        mapOf(
            "[HEADER] Teraz w TV" to "header",
            "Kategorie EPG" to "collection-slider",
            "Skróty v2" to "shortcuts-v2",
            "[HEADER] Było w TV - oglądaj teraz" to "header",
            "FILMY" to "horizontal",
            "SERIALE" to "horizontal",
            "SPORT" to "horizontal",
            "TELETURNIEJE" to "horizontal",
            "Wszystkie kanały" to "app-icons",
            "Moja lista kanałów" to "app-icons",
            "Dla dzieci" to "app-icons",
            "Sport" to "app-icons",  // NEW CATEGORY
            "Dokumenty" to "app-icons",
            "Filmy i seriale" to "app-icons",  // RENAMED
            "Informacyjne" to "app-icons",
            "Teraz w TV" to "horizontal"
        )
    }

    val gridContent = remember(isEpgSectionExpanded, terazWTvPrograms, najczesciejMovies, serialePrograms, sportPrograms, teleturniejePrograms, epgCategoriesPrograms) {
        val vodContentList = VodDataCache.getVodContentList()
        val kinoPlayMovies = VodDataCache.getKinoPlayMovies()

        // IMPORTANT: Always create gridContent with at least empty lists for each channel
        // This ensures content verification doesn't timeout when restoring focus
        channels.associateWith { channelName ->
            when (channelName) {
                "[HEADER] Teraz w TV", "[HEADER] Było w TV - oglądaj teraz" -> emptyList() // Headers have no content
                "Skróty v2" -> emptyList() // No horizontal content
                "Kategorie EPG" -> {
                    android.util.Log.d("GRID_CONTENT", "Kategorie EPG: ${epgCategoriesPrograms.size} current programs")
                    epgCategoriesPrograms // Current EPG programs
                }
                "Teraz w TV" -> {
                    android.util.Log.d("GRID_CONTENT", "Teraz w TV: ${terazWTvPrograms.size} EPG programs (NO FALLBACK)")
                    if (terazWTvPrograms.isNotEmpty()) {
                        android.util.Log.d("GRID_CONTENT", "   First ID: ${terazWTvPrograms[0].id}, title: ${terazWTvPrograms[0].title}")
                    }
                    terazWTvPrograms // ✅ Zawsze EPG data, bez fallback VOD
                }
                "FILMY" -> {
                    android.util.Log.d("GRID_CONTENT", "FILMY: ${najczesciejMovies.size} EPG movies (NO FALLBACK)")
                    najczesciejMovies // ✅ Zawsze EPG data, bez fallback VOD
                }
                "SERIALE" -> {
                    android.util.Log.d("GRID_CONTENT", "SERIALE: ${serialePrograms.size} EPG series (NO FALLBACK)")
                    serialePrograms // ✅ Zawsze EPG data, bez fallback VOD
                }
                "SPORT" -> {
                    android.util.Log.d("GRID_CONTENT", "SPORT: ${sportPrograms.size} EPG sports (NO FALLBACK)")
                    sportPrograms // ✅ Zawsze EPG data, bez fallback VOD
                }
                "TELETURNIEJE" -> {
                    android.util.Log.d("GRID_CONTENT", "TELETURNIEJE: ${teleturniejePrograms.size} EPG game shows (NO FALLBACK)")
                    teleturniejePrograms // ✅ Zawsze EPG data, bez fallback VOD
                }
                "Moja lista kanałów", "Wszystkie kanały", "Dla dzieci", "Sport", "Dokumenty", "Filmy i seriale", "Informacyjne" -> emptyList() // App-icons type, no VOD grid content
                else -> if (vodContentList.isNotEmpty()) vodContentList.shuffled().take(10) else emptyList() // Fallback
            }
        }
    }

    // App-icons data for TELEWIZJA channels
    val appIconsData = remember {
        mapOf(
            "Moja lista kanałów" to mojaListaChannels,  // 1-9
            "Wszystkie kanały" to tvChannelLogos,  // 1-119
            "Dla dzieci" to kidsChannels,  // 600-687
            "Sport" to sportChannels,  // 700-805 (NEW CATEGORY)
            "Dokumenty" to docChannels,  // 500-987
            "Filmy i seriale" to filmyISerialeChannels,  // 50-529 (RENAMED)
            "Informacyjne" to newsChannels  // 200-209
        )
    }

    // Shortcuts v2 data for "Skróty v2" channel
    val shortcuts = telewizjaShortcutsV2

    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie

    // State for showing LiveScreen
    var showLiveScreen by remember { mutableStateOf(false) }
    var selectedChannelName by remember { mutableStateOf<String?>(null) }

    // Reset focus state when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 0
            focusedColIndex = -2
        }
    }

    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                val channelName = channels.getOrNull(rowIndex) ?: ""
                when {
                    channelName.startsWith("[HEADER]") -> {
                        // Headers have no focus - skip
                    }
                    channelName == "Kategorie EPG" -> {
                        // collection-slider: NO CategoryIcon, only scrollable content
                        put(Pair(rowIndex, 0), FocusRequester()) // Only content focus
                    }
                    channelName == "Skróty v2" -> {
                        // Shortcuts v2: direct focus colIndex 0-3 (no CategoryIcon)
                        repeat(4) { colIndex ->
                            put(Pair(rowIndex, colIndex), FocusRequester())
                        }
                    }
                    channelName == "Dla dzieci" -> {
                        // CategoryIcon + app-icons (scrolling model: fixed focus at 0, LazyRow scrolls)
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                    }
                    channelName == "Sport" -> {
                        // CategoryIcon + app-icons (scrolling model: fixed focus at 0, LazyRow scrolls) - NEW CATEGORY
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                    }
                    channelName == "Dokumenty" -> {
                        // CategoryIcon + app-icons (scrolling model: fixed focus at 0, LazyRow scrolls)
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                    }
                    channelName == "Filmy i seriale" -> {
                        // CategoryIcon + app-icons (scrolling model: fixed focus at 0, LazyRow scrolls) - RENAMED
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                    }
                    channelName == "Informacyjne" -> {
                        // CategoryIcon + app-icons (scrolling model: fixed focus at 0, LazyRow scrolls)
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                    }
                    else -> {
                        // Other rows: CategoryIcon + Fixed focus position
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                    }
                }
            }
        }
    }

    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Simple restoration flag to block Auto-focus during restoration
    var restorationInProgress by remember { mutableStateOf(false) }
    var wasRestoration by remember { mutableStateOf(false) }  // Permanent block for auto-focus after restoration
    var isInitialized by remember { mutableStateOf(false) }

    // ✅ INITIALIZATION: ALWAYS set to true (no conditions) - enables navigation
    LaunchedEffect(Unit) {
        isInitialized = true
        android.util.Log.d("TELEWIZJA_FOCUS", "✅ Initialization complete - navigation enabled")
    }

    // ✅ ID-BASED RESTORATION: Uses unique IDs instead of positions
    LaunchedEffect(restoredTelewizjaFocus) {
        restoredTelewizjaFocus?.let { focusState ->
            android.util.Log.d("TELEWIZJA_FOCUS", "=== ID-BASED RESTORATION STARTED === channelId=${focusState.channelId}, itemId=${focusState.itemId}, scrollPos=${focusState.scrollPosition}")
            restorationInProgress = true
            wasRestoration = true  // Mark that restoration happened - blocks auto-focus permanently

            // Find row index from channelId
            val row = channels.indexOf(focusState.channelId)
            if (row == -1) {
                android.util.Log.e("TELEWIZJA_FOCUS", "❌ Channel not found: '${focusState.channelId}' (available: ${channels.joinToString()})")
                restorationInProgress = false
                return@let
            }

            android.util.Log.d("TELEWIZJA_FOCUS", "✓ Channel resolved: '${focusState.channelId}' → row=$row")
            android.util.Log.d("TELEWIZJA_FOCUS", "⏳ Waiting for content to load for channel: ${focusState.channelId}")

            // DEBUG: Log current gridContent state for this channel
            android.util.Log.d("TELEWIZJA_FOCUS", "📊 DEBUG: gridContent keys = ${gridContent.keys}")
            val currentContent = gridContent[focusState.channelId]
            android.util.Log.d("TELEWIZJA_FOCUS", "📊 DEBUG: gridContent[${focusState.channelId}] = ${currentContent?.size ?: "null"} items")

            // Wait for content based on what we're restoring to
            // - itemId != null: Restoring to content item → wait for non-empty list
            // - itemId == null: Restoring to CategoryIcon → empty list is OK
            val needsContent = focusState.itemId != null
            var attempts = 0
            while (attempts < 80) {  // 80 * 50ms = 4000ms
                // Read LIVE state variables instead of frozen gridContent
                val content = when (focusState.channelId) {
                    "Kategorie EPG" -> epgCategoriesProgramsAll  // Current EPG programs (all)
                    "Teraz w TV" -> terazWTvPrograms
                    "FILMY" -> najczesciejMovies
                    "SERIALE" -> serialePrograms
                    "SPORT" -> sportPrograms
                    "TELETURNIEJE" -> teleturniejePrograms
                    else -> gridContent[focusState.channelId] ?: emptyList()
                }
                android.util.Log.d("TELEWIZJA_FOCUS", "📊 Attempt $attempts: content = ${content.size} (live state), needsContent=$needsContent")

                val isReady = if (needsContent) {
                    // Need actual content loaded
                    content.isNotEmpty()
                } else {
                    // CategoryIcon - always ready (even if empty)
                    true
                }

                if (isReady) {
                    android.util.Log.d("TELEWIZJA_FOCUS", "✅ Channel '${focusState.channelId}' ready (${content.size} items) after ${attempts * 50}ms")
                    break
                }
                kotlinx.coroutines.delay(50)
                attempts++
            }

            // Check if we timed out
            if (attempts >= 80) {
                // Re-read live state for final check
                val content = when (focusState.channelId) {
                    "Kategorie EPG" -> epgCategoriesProgramsAll  // Current EPG programs (all)
                    "Teraz w TV" -> terazWTvPrograms
                    "FILMY" -> najczesciejMovies
                    "SERIALE" -> serialePrograms
                    "SPORT" -> sportPrograms
                    "TELETURNIEJE" -> teleturniejePrograms
                    else -> gridContent[focusState.channelId] ?: emptyList()
                }
                val isReady = if (needsContent) {
                    content.isNotEmpty()
                } else {
                    true // CategoryIcon always ready
                }
                if (!isReady) {
                    android.util.Log.e("TELEWIZJA_FOCUS", "❌ TIMEOUT after ${attempts * 50}ms: content not ready (needsContent=$needsContent, size=${content.size})")
                    restorationInProgress = false
                    return@let
                }
            }

            // Verify content is ready based on what we need (use live state)
            val content = when (focusState.channelId) {
                "Kategorie EPG" -> epgCategoriesPrograms  // 9 live TV channels from JSON
                "Teraz w TV" -> terazWTvPrograms
                "FILMY" -> najczesciejMovies
                "SERIALE" -> serialePrograms
                "SPORT" -> sportPrograms
                "TELETURNIEJE" -> teleturniejePrograms
                else -> gridContent[focusState.channelId] ?: emptyList()
            }
            // content is List<VodContent>, never null from when expression

            if (needsContent && content.isEmpty()) {
                android.util.Log.e("TELEWIZJA_FOCUS", "❌ TIMEOUT: Channel '${focusState.channelId}' has EMPTY content after ${attempts * 50}ms (needed non-empty for item restoration)")
                restorationInProgress = false
                return@let
            }

            // Determine focus target: CategoryIcon or specific item
            val col: Int
            var targetItemIndex: Int
            if (focusState.itemId == null) {
                // Restore focus to CategoryIcon
                col = -1
                targetItemIndex = 0
                android.util.Log.d("TELEWIZJA_FOCUS", "🎯 Target: CategoryIcon (col=-1)")
            } else {
                // Find item by ID
                col = 0  // Fixed focus model

                // Debug: Show all available IDs in content
                android.util.Log.d("TELEWIZJA_FOCUS", "🔍 Looking for itemId='${focusState.itemId}' in ${content.size} items")
                content.forEachIndexed { idx, item ->
                    android.util.Log.d("TELEWIZJA_FOCUS", "  [$idx] id='${item.id}', title='${item.title}'")
                }

                targetItemIndex = content.indexOfFirst { it.id == focusState.itemId }
                if (targetItemIndex == -1) {
                    android.util.Log.w("TELEWIZJA_FOCUS", "⚠️ Item ID '${focusState.itemId}' not found in content, using scrollPosition=${focusState.scrollPosition}")
                    // Fallback to scroll position if item not found
                    targetItemIndex = focusState.scrollPosition.coerceIn(0, content.size - 1)
                } else {
                    android.util.Log.d("TELEWIZJA_FOCUS", "✅ Item found: '${focusState.itemId}' at index=$targetItemIndex")
                }
            }

            // Set focus state
            android.util.Log.d("TELEWIZJA_FOCUS", "📍 Setting local focus state: focusedRowIndex=$row, focusedColIndex=$col")
            focusedRowIndex = row
            focusedColIndex = col
            android.util.Log.d("TELEWIZJA_FOCUS", "✓ Local focus state updated")

            // Scroll LazyRow to target position (if applicable)
            if (col == 0) {
                lazyListStates[row]?.let { listState ->
                    try {
                        listState.scrollToItem(targetItemIndex)
                        android.util.Log.d("TELEWIZJA_FOCUS", "📜 Scrolled to index $targetItemIndex")
                    } catch (e: Exception) {
                        android.util.Log.e("TELEWIZJA_FOCUS", "❌ Failed to scroll", e)
                    }
                }
            }

            // Wait for FocusRequester to exist (max 1 second)
            android.util.Log.d("TELEWIZJA_FOCUS", "⏳ Waiting for FocusRequester at ($row, $col)")
            var focusAttempts = 0
            while (focusAttempts < 20) {  // 20 * 50ms = 1000ms
                if (channelFocusRequesters[Pair(row, col)] != null) {
                    android.util.Log.d("TELEWIZJA_FOCUS", "✓ FocusRequester found after ${focusAttempts * 50}ms")
                    break
                }
                kotlinx.coroutines.delay(50)
                focusAttempts++
            }

            // Request focus on the element
            channelFocusRequesters[Pair(row, col)]?.let { focusRequester ->
                try {
                    android.util.Log.d("TELEWIZJA_FOCUS", "🎯 Requesting focus on element ($row, $col)")
                    focusRequester.requestFocus()

                    // Give UI time to stabilize before user can navigate
                    kotlinx.coroutines.delay(100)

                    android.util.Log.d("TELEWIZJA_FOCUS", "=== RESTORATION COMPLETED === channelId=${focusState.channelId}, itemId=${focusState.itemId}")
                } catch (e: Exception) {
                    android.util.Log.e("TELEWIZJA_FOCUS", "❌ Failed to restore focus: ${e.message}", e)
                }
            } ?: run {
                android.util.Log.w("TELEWIZJA_FOCUS", "⚠️ FocusRequester not found for ($row, $col) after ${focusAttempts * 50}ms")
            }

            // Clear restoration flag after completion
            kotlinx.coroutines.delay(2000)
            restorationInProgress = false
            android.util.Log.d("TELEWIZJA_FOCUS", "✓ Restoration flag cleared")
        }
    }

    // ✅ 3. AUTO-RESET SCROLL: Only in NORMAL state
    // NOTE: restorationState NOT in dependencies - avoids re-execution when state transitions
    LaunchedEffect(focusedRowIndex, isInitialized) {
        if (isInitialized && !restorationInProgress) {
            android.util.Log.d("TELEWIZJA_FOCUS", "🔄 User changed row → resetting scroll for unfocused rows")
            repeat(channels.size) { rowIndex ->
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    // ✅ AUTO-FOCUS: Focus on first NON-HEADER channel when coming from menu
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus && !restorationInProgress && !wasRestoration) {
            // Find first focusable (non-header) row
            val firstFocusableRow = getNextFocusableRowIndex(-1, 1, channels) ?: 0
            android.util.Log.d("TELEWIZJA_FOCUS", "🎯 Auto-focus from menu: Setting ($firstFocusableRow, 0)")
            focusedRowIndex = firstFocusableRow
            focusedColIndex = 0
            kotlinx.coroutines.delay(50)
            channelFocusRequesters[Pair(firstFocusableRow, 0)]?.requestFocus()
        }
    }

    // Lazy loading: doładowywanie przy scrollowaniu dla "Kategorie EPG"
    val kategorieEpgLazyListState = lazyListStates[1] // Row 1 = "Kategorie EPG"
    LaunchedEffect(kategorieEpgLazyListState?.firstVisibleItemIndex) {
        val currentIndex = kategorieEpgLazyListState?.firstVisibleItemIndex ?: 0
        // Gdy scroll osiągnie przedostatni widoczny element, doładuj kolejne 5
        if (currentIndex >= epgCategoriesVisibleCount - 2 && epgCategoriesVisibleCount < epgCategoriesProgramsAll.size) {
            val newCount = (epgCategoriesVisibleCount + 5).coerceAtMost(epgCategoriesProgramsAll.size)
            android.util.Log.d("EPG_DEBUG", "Lazy loading: increasing visible count from $epgCategoriesVisibleCount to $newCount")
            epgCategoriesVisibleCount = newCount
        }
    }

    // Show LiveScreen if a channel was clicked
    if (showLiveScreen) {
        LiveScreen(
            onBackPressed = {
                showLiveScreen = false
                selectedChannelName = null
            },
            initialChannelName = selectedChannelName
        )
    } else {
        // Focus management: Validate focus after EPG section toggle
        LaunchedEffect(isEpgSectionExpanded, channels.size) {
            // If current focus is on a channel that was hidden, move to nearest visible channel
            if (focusedRowIndex >= channels.size) {
                val newRowIndex = (channels.size - 1).coerceAtLeast(0)
                android.util.Log.d("TELEWIZJA_DEBUG", "Focus out of bounds after toggle: $focusedRowIndex -> $newRowIndex")
                focusedRowIndex = newRowIndex
                kotlinx.coroutines.delay(50)
                channelFocusRequesters[Pair(newRowIndex, focusedColIndex)]?.requestFocus()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF48227C))
                .onPreviewKeyEvent { event ->
                    // EPG section toggle moved to global shortcuts (Key "3")
                    // Regular navigation (handleTelewizjaNavigation)
                    handleTelewizjaNavigation(
                        event = event,
                        focusedRowIndex = focusedRowIndex,
                        focusedColIndex = focusedColIndex,
                        onFocusChange = { row, col ->
                            focusedRowIndex = row
                            focusedColIndex = col
                        },
                        channelFocusRequesters = channelFocusRequesters,
                        channels = channels,
                        channelTypes = channelTypes,
                        lazyListStates = lazyListStates,
                        coroutineScope = coroutineScope,
                        gridContent = gridContent,
                        appIconsData = appIconsData,
                        onReturnToMenu = onReturnToMenu
                    )
                }
                .focusable()
        ) {
            TelewizjaChannelRowsLayout(
                channels = channels,
                channelTypes = channelTypes,
                gridContent = gridContent,
                shortcuts = shortcuts,
                kidsChannels = kidsChannels,
                docChannels = docChannels,
                filmyISerialeChannels = filmyISerialeChannels,
                newsChannels = newsChannels,
                appIconsData = appIconsData,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channelFocusRequesters = channelFocusRequesters,
                onChannelContentFocusChange = { row, col ->
                    Log.d("TELEWIZJA_DEBUG", "Focus changed to row $row, col $col")
                    focusedRowIndex = row
                    focusedColIndex = col
                },
                onChannelClick = { channelName ->
                    Log.d("TELEWIZJA_DEBUG", "Channel clicked: $channelName")
                    selectedChannelName = channelName
                    showLiveScreen = true
                },
                lazyListStates = lazyListStates,
                sx = sx,
                sy = sy,
                onNavigateToEpg = onNavigateToEpg,
                onNavigateToEpgDay = onNavigateToEpgDay,
                sectionId = sectionId,
                onNavigateToChannelGrid = onNavigateToChannelGrid,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MojeChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    shouldAutoFocus: Boolean = false,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0,
    showNagraniaV2: Boolean = false,
    onShowNagraniaV2Change: (Boolean) -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> }  // For TV channel click
) {
    val context = LocalContext.current

    // Load TV channel data for "Moja lista kanałów" in MOJE section
    val tvChannelLogos = remember { loadTvChannelsFromAssets(context) }
    val mojaListaChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "moja-lista") }

    // Faza 3: Old static channel list deleted - now using dynamic list below based on isNagraniaExpanded

    val packages = remember {
        listOf(
            PackageItem(
                title = "Pakiet 1",
                imageUrl = "https://r.dcs.redcdn.pl/scale/play/playtv/upload/packet/3116237/images/1010596641?srcmode=3&srcx=260&srcy=77&srcw=260&srch=77&dstw=260&dsth=77&type=0",
                price = "29,99 zł",
                description = "Pakiet podstawowy"
            ),
            PackageItem(
                title = "Pakiet 2",
                imageUrl = "https://r.dcs.redcdn.pl/scale/play/playtv/upload/packet/27452759/images/1037037910?srcmode=3&srcx=260&srcy=77&srcw=260&srch=77&dstw=260&dsth=77&type=0",
                price = "49,99 zł",
                description = "Pakiet rozszerzony"
            ),
            PackageItem(
                title = "Pakiet 3",
                imageUrl = "https://r.dcs.redcdn.pl/scale/play/playtv/upload/packet/30492532/images/1004802744?srcmode=3&srcx=260&srcy=77&srcw=260&srch=77&dstw=260&dsth=77&type=0",
                price = "79,99 zł",
                description = "Pakiet premium"
            ),
            PackageItem(
                title = "Pakiet 4",
                imageUrl = "https://r.dcs.redcdn.pl/scale/play/playtv/upload/packet/29020667/images/979943766?srcmode=3&srcx=260&srcy=77&srcw=260&srch=77&dstw=260&dsth=77&type=0",
                price = "99,99 zł",
                description = "Pakiet VIP"
            )
        )
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // EXPANDABLE CHANNELS PATTERN - NAGRANIA Implementation
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Pattern: Dynamiczne wstawianie sub-kanałów jako oddzielnych rzędów
    // Documentation: docs/patterns/EXPANDABLE_CHANNELS_PATTERN.md
    //
    // Kluczowe zasady:
    // 1. Sub-kanały = oddzielne rzędy (NIE zagnieżdżone komponenty)
    // 2. FocusRequestery = stabilna mapa dla MAX_CHANNELS
    // 3. Conditional assignment = focus window pattern (firstVisibleItemIndex)
    // 4. Auto-collapse = nawigacja graniczna (UP/DOWN przy brzegach)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // MARK: - Faza 3: Simple expansion state tracker
    // Only tracks whether NAGRANIA is expanded (no nested data)
    var isNagraniaExpanded by remember { mutableStateOf(false) }

    // Toggle between v1 (Moje nagrania expandable) and v2 (Header + Nagrania + Skróty v2)
    // Key "8" on remote toggles this state (loaded from SharedPreferences)
    // MOVED TO TOP (now defined globally for keyboard shortcut access at line ~711)
    // var showNagraniaV2 - defined at line ~711

    // Shortcuts data for "Skróty v2 Moje" channel (4 buttons)
    val mojeNagraniaShortcuts = remember {
        listOf(
            ShortcutItem("1", "Zarządzaj nagraniami", ShortcutIcon.MaterialIcon("settings")),
            ShortcutItem("2", "Pojedyncze", ShortcutIcon.MaterialIcon("video_library")),
            ShortcutItem("3", "Serie", ShortcutIcon.MaterialIcon("live_tv")),
            ShortcutItem("4", "Zaplanowane", ShortcutIcon.MaterialIcon("schedule"))
        )
    }

    // Maximum number of channels when NAGRANIA is expanded (for stable FocusRequester map)
    val MAX_MOJE_CHANNELS = 12  // Max from both versions (v1 expanded: 9, v2: 7)

    // Faza 3: Dynamic channel list - Toggle between v1 (Moje nagrania) and v2 (Header+Nagrania+Skróty)
    // Key "8" on remote toggles showNagraniaV2
    val channels = remember(isNagraniaExpanded, showNagraniaV2) {
        if (showNagraniaV2) {
            // ═══════════════════════════════════════════════════════════════
            // VERSION 2: Header + Nagrania + Skróty v2 (8 channels)
            // ═══════════════════════════════════════════════════════════════
            listOf(
                "Oglądaj dalej",
                "Moja lista kanałów",                  // App-icons channel (like TELEWIZJA)
                "[HEADER-RIGHT] Miejsce na nagrania",  // Storage counter header
                "Nagrania",                            // Standard horizontal channel with icon
                "Skróty v2 Moje",                      // 4 shortcuts row (reduced height)
                "Do obejrzenia",
                "Wypożyczone",
                "Aktywne pakiety"
            )
        } else {
            // ═══════════════════════════════════════════════════════════════
            // VERSION 1: Moje nagrania expandable (6 collapsed, 10 expanded)
            // ═══════════════════════════════════════════════════════════════
            if (isNagraniaExpanded) {
                listOf(
                    "Oglądaj dalej",
                    "Moja lista kanałów",   // App-icons channel (like TELEWIZJA)
                    "Moje nagrania",        // Expandable parent
                    "Skróty",               // Sub-channel 1 - shortcuts to recordings
                    "Pojedyncze nagrania",  // Sub-channel 2
                    "SERIE",                // Sub-channel 3
                    "ZAPLANOWANE",          // Sub-channel 4
                    "Do obejrzenia",
                    "Wypożyczone",
                    "Aktywne pakiety"
                )
            } else {
                listOf(
                    "Oglądaj dalej",
                    "Moja lista kanałów",   // App-icons channel (like TELEWIZJA)
                    "Moje nagrania",        // Collapsed
                    "Do obejrzenia",
                    "Wypożyczone",
                    "Aktywne pakiety"
                )
            }
        }
    }

    // Faza 3: Toggle NAGRANIA expansion (v1 only)
    val toggleNagraniaExpansion: () -> Unit = {
        isNagraniaExpanded = !isNagraniaExpanded
        Log.d("MOJE_DEBUG", "NAGRANIA expanded: $isNagraniaExpanded (channels: ${channels.size})")
    }

    // Toggle between v1 and v2 (Key "8" on remote)
    val toggleNagraniaVersion: () -> Unit = {
        onShowNagraniaV2Change(!showNagraniaV2)
        Log.d("MOJE_DEBUG", "Key 8 → Toggle Nagrania version: ${if (!showNagraniaV2) "v2" else "v1"} (channels: ${channels.size})")
    }

    // Faza 3: Grid content mapping - uses MojeContentCache for persistent content
    // Content is shuffled once per channel on first access and cached for app lifetime
    val gridContent = remember(isNagraniaExpanded, showNagraniaV2) {
        MojeContentCache.getContent(channels)
    }

    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie
    // Faza 2: focusedSubChannelIndex removed - sub-channels are now regular channels

    // Reset focus state when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 0
            focusedColIndex = -2 // Reset do stanu "brak fokusa"
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // STABLE FOCUSREQUESTER PATTERN (Component #2)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // CRITICAL: NO channels.size dependency - prevents race conditions!
    //
    // Problem: remember(channels.size) recreates map on every expand/collapse
    // Result: Old FocusRequesters orphaned, components lose focus
    //
    // Solution: Create ONCE for MAX_CHANNELS, never recreate
    // - Collapsed (5 channels): Use indices 0-4
    // - Expanded (8 channels): Use indices 0-7
    // - Map size constant, no recreation, zero race conditions
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    // Faza 1 Rollback: Pair keys (back to pre-Phase 6 structure)
    // Pair<rowIndex, colIndex>
    // colIndex: -1 = icon, 0 = content
    // Special handling for "Skróty" - NO CategoryIcon (like WIDEO "Skróty v2")
    // Recreate when channels change (expand/collapse) to match current channel list
    val channelFocusRequesters = remember(channels) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            channels.forEachIndexed { rowIndex, channelName ->
                when {
                    channelName.startsWith("[HEADER") -> {
                        // Headers: NO FocusRequesters (not focusable)
                    }
                    channelName == "Skróty" -> {
                        // Shortcuts: direct focus on single shortcut (NO CategoryIcon)
                        put(Pair(rowIndex, 0), FocusRequester())
                    }
                    channelName == "Skróty v2 Moje" -> {
                        // 4 shortcuts row: direct focus on 4 buttons (NO CategoryIcon)
                        repeat(4) { colIndex ->
                            put(Pair(rowIndex, colIndex), FocusRequester())
                        }
                    }
                    else -> {
                        // Standard channels: CategoryIcon + Content (includes "Nagrania")
                        put(Pair(rowIndex, -1), FocusRequester())
                        put(Pair(rowIndex, 0), FocusRequester())
                    }
                }
            }
        }
    }

    // FIX: Create for MAX_MOJE_CHANNELS to avoid recreation during expand/collapse
    val lazyListStates = remember {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(MAX_MOJE_CHANNELS) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    // Faza 2: subChannelLazyListStates removed - sub-channels are now regular channels with own lazyListStates

    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    // Auto-reset LazyListState for unfocused rows
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            // Reduced from 150ms to 0ms for instant tab switching
            kotlinx.coroutines.delay(0)
            repeat(channels.size) { rowIndex ->
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            // When coming from menu, immediately set focus state
            focusedRowIndex = 0
            focusedColIndex = -1
            // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
            // Faza 1 Rollback: Use Pair key for CategoryIcon
            val firstCategoryFocusRequester = channelFocusRequesters[Pair(0, -1)]
            if (firstCategoryFocusRequester != null) {
                Log.d("MOJE_DEBUG", "Auto-focus: Setting state (0, -1) and requesting focus")
                firstCategoryFocusRequester.requestFocus()
            }
        }
        isInitialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleMojeChannelsNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onChannelContentFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu,
                    onToggleExpansion = toggleNagraniaExpansion,
                    isNagraniaExpanded = isNagraniaExpanded,  // Faza 5
                    onToggleVersion = toggleNagraniaVersion   // Key "8" handler
                )
            }
            .focusable()
    ) {
        MojeChannelRowsLayout(
            channels = channels,
            gridContent = gridContent,
            packages = packages,
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col ->
                Log.d("MOJE_DEBUG", "Focus changed to row $row, col $col")
                focusedRowIndex = row
                focusedColIndex = col
            },
            onNavigateToVodGrid = onNavigateToVodGrid,
            onNavigateToKinoGrid = onNavigateToKinoGrid,
            lazyListStates = lazyListStates,
            sx = sx,
            sy = sy,
            isNagraniaExpanded = isNagraniaExpanded,
            toggleNagraniaExpansion = toggleNagraniaExpansion,
            mojeNagraniaShortcuts = mojeNagraniaShortcuts,  // NEW: pass shortcuts data
            mojaListaChannels = mojaListaChannels,  // NEW: pass TV channels for "Moja lista kanałów"
            onNavigateToEpgDay = onNavigateToEpgDay  // For TV channel click
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AplikacjeChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current
    val channels = listOf("Ostatnio używane", "Aplikacje", "Skróty v2", "Netflix", "YouTube", "Prime Video", "Disney+")

    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        if (vodContentList.isNotEmpty()) {
            channels.associateWith { channelName ->
                vodContentList.shuffled().take(10) // Horizontal content
            }
        } else {
            emptyMap()
        }
    }

    // Shortcuts data (like in START)
    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "Moja lista kanałów", ShortcutIcon.LottieIcon("tvaa.lottie")),
            ShortcutItem("2", "Nagrania", ShortcutIcon.LottieIcon("nagrania.lottie")),
            ShortcutItem("3", "Wypożyczone", ShortcutIcon.LottieIcon("wypozyczone.lottie")),
            ShortcutItem("4", "Disney Plus", ShortcutIcon.VectorIcon(R.drawable.disney_plus_icon)),
            ShortcutItem("5", "Do obejrzenia", ShortcutIcon.LottieIcon("doobejzenia.lottie"))
        )
    }

    // App icons for "Aplikacje" channel
    val apps = remember {
        listOf(
            AppItem("1", "Netflix", R.drawable.imgi_57_netflix_2x),
            AppItem("2", "YouTube", R.drawable.imgi_58_youtube_2x),
            AppItem("3", "Prime Video", R.drawable.imgi_59_prime_video_2x),
            AppItem("4", "Spotify", R.drawable.imgi_61_spotify_2x),
            AppItem("5", "Disney+", R.drawable.imgi_63_disney_2x),
            AppItem("6", "Apple TV", R.drawable.imgi_68_apple_tv_2x)
        )
    }

    // App icons for "Ostatnio używane" channel - only 2 apps
    val recentApps = remember {
        listOf(
            AppItem("1", "Disney+", R.drawable.imgi_63_disney_2x),
            AppItem("2", "Apple TV", R.drawable.imgi_68_apple_tv_2x)
        )
    }

    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie

    // Reset focus state when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 0
            focusedColIndex = -2 // Reset do stanu "brak fokusa"
        }
    }

    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                val channelName = channels.getOrNull(rowIndex) ?: ""
                if (channelName == "Skróty v2") {
                    // Shortcuts v2: direct focus colIndex 0-3 (no CategoryIcon)
                    repeat(4) { colIndex ->
                        put(Pair(rowIndex, colIndex), FocusRequester())
                    }
                } else {
                    // Normal channels: CategoryIcon (-1) + content (0)
                    put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                    put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
                }
            }
        }
    }

    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    // Auto-reset LazyListState for unfocused rows
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            // Reduced from 150ms to 0ms for instant tab switching
            kotlinx.coroutines.delay(0)
            repeat(channels.size) { rowIndex ->
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            // When coming from menu, focus on first channel
            val firstChannelName = channels.firstOrNull() ?: ""
            focusedRowIndex = 0

            if (firstChannelName == "Skróty v2") {
                // Shortcuts v2: focus on first shortcut (colIndex 0)
                focusedColIndex = 0
                // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
                val firstShortcutFocusRequester = channelFocusRequesters[Pair(0, 0)]
                if (firstShortcutFocusRequester != null) {
                    Log.d("APLIKACJE_DEBUG", "Auto-focus: Setting focus on first shortcut (0, 0)")
                    firstShortcutFocusRequester.requestFocus()
                }
            } else {
                // Normal channel: focus on CategoryIcon
                focusedColIndex = -1
                // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
                val firstCategoryFocusRequester = channelFocusRequesters[Pair(0, -1)]
                if (firstCategoryFocusRequester != null) {
                    Log.d("APLIKACJE_DEBUG", "Auto-focus: Setting focus on first CategoryIcon (0, -1)")
                    firstCategoryFocusRequester.requestFocus()
                }
            }
        }
        isInitialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleAplikacjeChannelsNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onChannelContentFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu
                )
            }
            .focusable()
    ) {
        AplikacjeChannelRowsLayout(
            channels = channels,
            gridContent = gridContent,
            shortcuts = aplikacjeShortcutsV2,
            apps = apps,
            recentApps = recentApps,
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col ->
                Log.d("APLIKACJE_DEBUG", "Focus changed to row $row, col $col")
                focusedRowIndex = row
                focusedColIndex = col
            },
            onNavigateToChannelGrid = onNavigateToChannelGrid,
            onNavigateToVodGrid = onNavigateToVodGrid,
            onNavigateToKinoGrid = onNavigateToKinoGrid,
            appIconsData = appIconsData,
            lazyListStates = lazyListStates,
            sx = sx,
            sy = sy
        )
    }
}

// START section functions

@Composable
private fun StartScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var resetTrigger by remember { mutableStateOf(0) }
    
    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "START") {
            resetTrigger++
        }
    }
    
    // New 3-row structure for START
    NewStartScreenContent(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        shouldAutoFocus = globalFocusState.value.sectionId == "START" && globalFocusState.value.currentRow > 0,
        onNavigateToChannelGrid = onNavigateToChannelGrid,
        onNavigateToVodGrid = onNavigateToVodGrid,
        onNavigateToKinoGrid = onNavigateToKinoGrid,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable 
private fun StartChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current
    val channels = listOf("Popularne", "Najnowsze", "Filmy", "Seriale", "Sport", "Dokumenty", "Dzieci")
    
    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        if (vodContentList.isNotEmpty()) {
            channels.associateWith { channelName ->
                vodContentList.shuffled().take(10)
            }
        } else {
            emptyMap()
        }
    }

    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie
    
    // Reset focus state when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 0
            focusedColIndex = -2 // Reset do stanu "brak fokusa"
        }
    }
    
    // Faza 1: Rollback to Pair keys (simplified from Triple)
    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                put(Pair(rowIndex, 0), FocusRequester()) // Content (fixed focus position)
            }
        }
    }

    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    // Auto-reset LazyListState for unfocused rows
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            // Reduced from 150ms to 0ms for instant tab switching
            kotlinx.coroutines.delay(0)
            repeat(channels.size) { rowIndex ->
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            // When coming from menu, immediately set focus state
            focusedRowIndex = 0
            focusedColIndex = -1
            // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
            // Faza 1: Pair key for CategoryIcon
            val firstCategoryFocusRequester = channelFocusRequesters[Pair(0, -1)]
            if (firstCategoryFocusRequester != null) {
                Log.d("START_DEBUG", "Auto-focus: Setting state (0, -1) and requesting focus")
                firstCategoryFocusRequester.requestFocus()
            }
        }
        isInitialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleStartChannelsNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onChannelContentFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu
                )
            }
            .focusable()
    ) {
        StartChannelRowsLayout(
            channels = channels,
            gridContent = gridContent,
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col ->
                Log.d("START_DEBUG", "Focus changed to row $row, col $col")
                focusedRowIndex = row
                focusedColIndex = col
            },
            lazyListStates = lazyListStates,
            currentRow = 3, // Legacy function - assume channels are focused (row 3+)
            sx = sx,
            sy = sy
        )
    }
}

// First StartChannelRowsLayout removed - using private version below

/*
@Composable
fun StartUnifiedChannelRow(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    
    var showDetailsWithDelay by remember { mutableStateOf(false) }
    
    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && focusedColIndex == 0
        
        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350) // 350ms = animation time
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "start_miniatures_y_offset_$rowIndex"
        )
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = miniaturesYOffset),
            state = lazyListState, 
            contentPadding = PaddingValues(start = sx(380), end = sx(20)),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            items(rowContent.size) { colIndex ->
                val vodContent = rowContent[colIndex]
                val isItemFocused = rowIndex == focusedRowIndex && 
                                   colIndex == lazyListState.firstVisibleItemIndex && 
                                   focusedColIndex == 0
                
                val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
                
                ContentCard(
                    vodContent = vodContent,
                    channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }
            
            // Spacer items
            items(8) { 
                Spacer(
                    modifier = Modifier
                        .width(sx(220))
                        .height(sy(380))
                )
            }
        }
        
        // Details overlay
        if (isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }
                        
                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }
        
        // CategoryIcon at fixed position
        Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
            val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
            val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]
            
            if (rowIndex < 3) { // Debug only for first 3 channels
                Log.d("START_DEBUG", "Channel '$channel' (row $rowIndex) - categoryIsFocused: $categoryIsFocused, focusRequester available: ${categoryFocusRequester != null}")
            }
            
            CategoryIcon(
                text = channel,
                isFocused = categoryIsFocused,
                onClick = { /* Channel click handler */ },
                onFocused = { isFocused -> 
                    if (isFocused) {
                        Log.d("START_DEBUG", "CategoryIcon '$channel' (row $rowIndex) gained focus")
                        onChannelContentFocusChange(rowIndex, -1)
                    }
                },
                focusRequester = categoryFocusRequester ?: FocusRequester(),
                sx = sx,
                sy = sy
            )
        }
    }
}
*/

private fun calculateStartChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    currentRow: Int,
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    // Calculate channelsY offset (same as in NewStartScreenContent)
    val isShortcutsFocused = currentRow == 2
    val isChannelsFocused = currentRow >= 3
    val animatedShortcutsY = when {
        isChannelsFocused -> -400 // Shortcuts off-screen when on channels
        isShortcutsFocused -> 280 // Focused: 40px higher
        else -> 960 // Default
    }
    val shortcutHeight = 279
    val channelsY = animatedShortcutsY + shortcutHeight + 40 // = 599 (focused) or 1279 (default) or -81 (channels)

    // Calculate relative position (to make focused channel at absolute Y=340)
    val targetAbsoluteY = START_FIXED_FOCUS_Y // 340
    val focusedChannelRelativeY = targetAbsoluteY - channelsY // -299 or -939

    return when {
        // When on SliderMix (row 1) or Shortcuts (row 2) - channels peek from bottom
        currentRow <= 2 -> {
            // Channels start at relative Y=0 (which becomes absolute Y=channelsY)
            sy(rowIndex * START_NORMAL_ROW_HEIGHT)
        }
        // Zfokusowany kanał - relatywna pozycja aby był na absolutnym Y=340
        rowIndex == focusedRowIndex -> {
            sy(focusedChannelRelativeY)
        }
        // Kanały powyżej zfokusowanego - przesuwają się w górę
        rowIndex < focusedRowIndex -> {
            // Dodatkowe 100px odsunięcie gdy fokus na treści (focusedColIndex >= 0)
            val extraSpacing = if (focusedColIndex >= 0) START_CONTENT_FOCUS_EXTRA_SPACING else 0
            sy(focusedChannelRelativeY - (focusedRowIndex - rowIndex) * START_NORMAL_ROW_HEIGHT - extraSpacing)
        }
        // Kanały poniżej zfokusowanego - przesuwają się w dół aby zrobić miejsce
        rowIndex > focusedRowIndex -> {
            // Sprawdzamy czy zfokusowany kanał ma miniaturkę zfokusowaną (powiększony)
            val focusedChannelExpansion = if (focusedColIndex >= 0) START_EXPANDED_ROW_HEIGHT else START_NORMAL_ROW_HEIGHT
            sy(focusedChannelRelativeY + focusedChannelExpansion + (rowIndex - focusedRowIndex - 1) * START_NORMAL_ROW_HEIGHT)
        }
        // Fallback (nie powinno się wydarzyć)
        else -> sy(rowIndex * START_NORMAL_ROW_HEIGHT)
    }
}

// Navigation handler for START channels
fun handleStartChannelsNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>, // Faza 1: Pair keys (simplified)
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            Log.d("START_DEBUG", "UP pressed: focusedRowIndex=$focusedRowIndex, focusedColIndex=$focusedColIndex")
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to CategoryIcon of first row
                Log.d("START_DEBUG", "UP: First movement from no focus -> row 0, col -1")
                onChannelContentFocusChange(0, -1)
                channelFocusRequesters[Pair(0, -1)]?.requestFocus()
                return true
            } else if (focusedRowIndex > 0) {
                val newRowIndex = focusedRowIndex - 1
                // Preserve type of position (CategoryIcon vs content)
                val targetColIndex = if (focusedColIndex == -1) -1 else 0
                Log.d("START_DEBUG", "UP: Moving from row $focusedRowIndex to $newRowIndex, col $targetColIndex")
                onChannelContentFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            } else {
                // From first row, go back to menu - reset all LazyListState positions first
                Log.d("START_DEBUG", "UP: From first row -> returning to menu")
                coroutineScope.launch {
                    channels.forEachIndexed { rowIndex, _ ->
                        val lazyListState = lazyListStates[rowIndex]
                        if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                            lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                        }
                    }
                }
                onReturnToMenu()
            }
            return true
        }
        
        Key.DirectionDown -> {
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to CategoryIcon of first row
                onChannelContentFocusChange(0, -1)
                channelFocusRequesters[Pair(0, -1)]?.requestFocus()
                return true
            } else if (focusedRowIndex < channels.size - 1) {
                val newRowIndex = focusedRowIndex + 1
                // Preserve type of position (CategoryIcon vs content)
                val targetColIndex = if (focusedColIndex == -1) -1 else 0
                onChannelContentFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }

        Key.DirectionLeft -> {
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to CategoryIcon of first row
                onChannelContentFocusChange(0, -1)
                channelFocusRequesters[Pair(0, -1)]?.requestFocus()
                return true
            } else if (focusedColIndex == -1) {
                // Already on CategoryIcon - do nothing
                return true
            } else if (focusedColIndex == 0) {
                // Fixed focus on position 0 - check if we can scroll left
                val lazyListState = lazyListStates[focusedRowIndex]
                if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                    // Scroll left by 1 position
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                    }
                } else {
                    // Can't scroll left - go to CategoryIcon
                    onChannelContentFocusChange(focusedRowIndex, -1)
                    channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                }
            }
            return true
        }

        Key.DirectionRight -> {
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to CategoryIcon of first row
                onChannelContentFocusChange(0, -1)
                channelFocusRequesters[Pair(0, -1)]?.requestFocus()
            } else if (focusedColIndex == -1) {
                // From CategoryIcon to first visible position (focus always on 0)
                onChannelContentFocusChange(focusedRowIndex, 0)
                channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
            } else if (focusedColIndex == 0) {
                // Fixed focus on position 0 - check if we can scroll right
                val lazyListState = lazyListStates[focusedRowIndex]
                val channelName = channels.getOrNull(focusedRowIndex)
                val channelContent = gridContent[channelName] ?: emptyList()

                val maxScrollPosition = channelContent.size + 8 - 1 // Include spacer items

                if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxScrollPosition) {
                    // Scroll right by 1 position
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                    }
                }
                // If can't scroll right - stay in place
            }
            return true
        }

        else -> return false
    }
}

// START section new components: SliderMix + Shortcuts + Channels

// Note: ShortcutIcon and ShortcutItem are defined in ShortcutScreen.kt

// START shortcuts list (6 items)
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StartShortcuts(
    focusRequesters: Map<Int, FocusRequester>,
    focusedIndex: Int,
    currentRow: Int,
    onFocusChange: (Int) -> Unit,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp
) {
    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "Moja lista kanałów", ShortcutIcon.LottieIcon("tvaa.lottie")),
            ShortcutItem("2", "Nagrania", ShortcutIcon.LottieIcon("nagrania.lottie")),
            ShortcutItem("3", "Wypożyczone", ShortcutIcon.LottieIcon("wypozyczone.lottie")),
            ShortcutItem("4", "Disney Plus", ShortcutIcon.VectorIcon(R.drawable.disney_plus_icon)),
            ShortcutItem("5", "Do obejrzenia", ShortcutIcon.LottieIcon("doobejzenia.lottie"))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sx(134)), // Aligned with SliderMix first slide
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            shortcuts.forEachIndexed { index, shortcut ->
                key(shortcut.title) {  // ✅ Stable key for Compose recomposition
                    StartShortcutCard(
                        shortcut = shortcut,
                        isFocused = currentRow == 2 && index == focusedIndex, // Show border only when Shortcuts row is focused AND this shortcut is selected
                        focusRequester = focusRequesters[index] ?: FocusRequester(),
                        onNavigateToChannelGrid = onNavigateToChannelGrid,
                        onNavigateToVodGrid = onNavigateToVodGrid,
                        onNavigateToKinoGrid = onNavigateToKinoGrid,
                        appIconsData = appIconsData,
                        sx = sx,
                        sy = sy,
                        onFocusChange = { isFocused ->
                            if (isFocused) {
                                onFocusChange(index)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StartShortcutCard(
    shortcut: ShortcutItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    onFocusChange: (Boolean) -> Unit
) {
    val cardWidth = sx(210)
    val cardHeight = sy(279)
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .border(
                width = (6 * sx(1).value / 1.dp.value).dp,
                color = borderColor,
                shape = RoundedCornerShape(sx(12))
            )
            .clip(RoundedCornerShape(sx(12)))
            .background(Color(0x33000000))
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    android.util.Log.d("START_CARD", "=== Enter/OK pressed for: ${shortcut.title} ===")
                    when (shortcut.title) {
                        "Moja lista kanałów" -> {
                            android.util.Log.d("START_CARD", "Nawigacja do ChannelGrid")
                            onNavigateToChannelGrid(
                                "Moja lista kanałów",
                                "Wszystkie",
                                null,
                                appIconsData["Moja lista kanałów"]
                            )
                        }
                        "Nagrania" -> {
                            android.util.Log.d("START_CARD", "Nawigacja do VodGrid - Nagrania")
                            val vodList = VodDataCache.getVodContentList()
                            val randomFilms = vodList.shuffled().take(20)
                            onNavigateToVodGrid("Wszystkie nagrania", randomFilms, "START")
                        }
                        "Wypożyczone" -> {
                            android.util.Log.d("START_CARD", "Nawigacja do KinoGrid - Wypożyczone (pionowe plakaty)")
                            val kinoList = VodDataCache.getKinoPlayMovies()
                            val randomMovies = kinoList.shuffled().take(2)
                            onNavigateToKinoGrid("Wypożyczone", randomMovies, "START")
                        }
                        "Do obejrzenia" -> {
                            android.util.Log.d("START_CARD", "Nawigacja do VodGrid - Do obejrzenia")
                            val vodList = VodDataCache.getVodContentList()
                            val randomFilms = vodList.shuffled().take(20)
                            onNavigateToVodGrid("Do obejrzenia", randomFilms, "START")
                        }
                    }
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable {
                android.util.Log.d("START_CARD", "=== clickable triggered for: ${shortcut.title} ===")
                when (shortcut.title) {
                    "Moja lista kanałów" -> {
                        android.util.Log.d("START_CARD", "Nawigacja do ChannelGrid")
                        onNavigateToChannelGrid(
                            "Moja lista kanałów",
                            "Wszystkie",
                            null,
                            appIconsData["Moja lista kanałów"]
                        )
                    }
                    "Nagrania" -> {
                        android.util.Log.d("START_CARD", "Nawigacja do VodGrid - Nagrania")
                        val vodList = VodDataCache.getVodContentList()
                        val randomFilms = vodList.shuffled().take(20)
                        onNavigateToVodGrid("Wszystkie nagrania", randomFilms, "START")
                    }
                    "Wypożyczone" -> {
                        android.util.Log.d("START_CARD", "Nawigacja do KinoGrid - Wypożyczone (pionowe plakaty)")
                        val kinoList = VodDataCache.getKinoPlayMovies()
                        val randomMovies = kinoList.shuffled().take(2)
                        onNavigateToKinoGrid("Wypożyczone", randomMovies, "START")
                    }
                    "Do obejrzenia" -> {
                        android.util.Log.d("START_CARD", "Nawigacja do VodGrid - Do obejrzenia")
                        val vodList = VodDataCache.getVodContentList()
                        val randomFilms = vodList.shuffled().take(20)
                        onNavigateToVodGrid("Do obejrzenia", randomFilms, "START")
                    }
                }
            }
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isFocused) Color(0x4D000000) else Color.Transparent
                )
        ) {
            // Icon centered in upper area (SVG or Lottie)
            when (shortcut.icon) {
                is ShortcutIcon.VectorIcon -> {
                    Icon(
                        painter = painterResource(shortcut.icon.iconRes),
                        contentDescription = shortcut.title,
                        modifier = Modifier
                            .size(sx(255))
                            .align(Alignment.Center)
                            .offset(y = sy(-15)),
                        tint = Color(0xFFEEEEEE)
                    )
                }
                is ShortcutIcon.LottieIcon -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(shortcut.icon.fileName))
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        isPlaying = isFocused,
                        restartOnPlay = true,
                        iterations = if (isFocused) 1 else 1
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { if (isFocused) progress else 1f },
                        modifier = Modifier
                            .size(sx(255))
                            .align(Alignment.Center)
                            .offset(y = sy(-15))
                    )
                }
                is ShortcutIcon.MaterialIcon -> {
                    val materialIcon = when (shortcut.icon.iconName) {
                        "add" -> Icons.Default.Add
                        "star" -> Icons.Default.Star
                        "search" -> Icons.Default.Search
                        "person" -> Icons.Default.Person
                        else -> Icons.Default.Star
                    }
                    Icon(
                        imageVector = materialIcon,
                        contentDescription = shortcut.title,
                        modifier = Modifier
                            .size(sx(255))
                            .align(Alignment.Center)
                            .offset(y = sy(-15)),
                        tint = Color(0xFFEEEEEE)
                    )
                }
            }
            
            // Text at bottom
            Text(
                text = shortcut.title,
                color = Color(0xFFEEEEEE),
                fontSize = sy(18).value.sp,
                fontWeight = FontWeight.W500,
                textAlign = TextAlign.Center,
                lineHeight = sy(24).value.sp,
                letterSpacing = 0.36.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = sx(16), vertical = sy(20))
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ShortcutCardV2(
    shortcut: ShortcutItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit = {},
    heightPx: Int = 179  // Configurable height (default 179px, MOJE v2 uses 120px)
) {
    val cardWidth = sx(310)
    val cardHeight = sy(heightPx)  // Use parameter instead of hardcoded value
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .border(
                width = (6 * sx(1).value / 1.dp.value).dp,
                color = borderColor,
                shape = RoundedCornerShape(sx(20))
            )
            .clip(RoundedCornerShape(sx(20)))
            .background(Color(0x3B000000)) // rgba(0, 0, 0, 0.23)
            .focusRequester(focusRequester)
            .focusable()
            .clickable { onClick() }
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isFocused) Color(0x4D000000) else Color.Transparent
                )
                .padding(sx(30)) // 30px padding
        ) {
            // Icon at top-right corner
            when (shortcut.icon) {
                is ShortcutIcon.VectorIcon -> {
                    Icon(
                        painter = painterResource(shortcut.icon.iconRes),
                        contentDescription = shortcut.title,
                        modifier = Modifier
                            .size(sx(69), sy(54))
                            .alpha(0.7f)
                            .align(Alignment.TopEnd),  // Prawy górny róg
                        tint = Color(0xFFEEEEEE)
                    )
                }
                is ShortcutIcon.MaterialIcon -> {
                    val materialIcon = when (shortcut.icon.iconName) {
                        "add" -> Icons.Default.Add
                        "star" -> Icons.Default.Star
                        "search" -> Icons.Default.Search
                        "person" -> Icons.Default.Person
                        else -> Icons.Default.Star
                    }
                    Icon(
                        imageVector = materialIcon,
                        contentDescription = shortcut.title,
                        modifier = Modifier
                            .size(sx(69), sy(54))
                            .alpha(0.7f)
                            .align(Alignment.TopEnd),  // Prawy górny róg
                        tint = Color(0xFFEEEEEE)
                    )
                }
                is ShortcutIcon.LottieIcon -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(shortcut.icon.fileName))
                    val progress by animateLottieCompositionAsState(
                        composition = composition,
                        isPlaying = isFocused,
                        restartOnPlay = true,
                        iterations = if (isFocused) 1 else 1
                    )

                    LottieAnimation(
                        composition = composition,
                        progress = { if (isFocused) progress else 1f },
                        modifier = Modifier
                            .size(sx(69), sy(54))
                            .alpha(0.7f)
                            .align(Alignment.TopEnd)  // Prawy górny róg
                    )
                }
            }

            // Text at bottom-left corner
            Text(
                text = shortcut.title,
                color = Color(0xFFEEEEEE),
                fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.W500,
                lineHeight = (32 * (sy(1).value / 1.dp.value)).sp,
                letterSpacing = 0.48.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)  // Lewy dolny róg
            )
        }
    }
}

// Shortcuts v2 data for APLIKACJE section
val aplikacjeShortcutsV2 = listOf(
    ShortcutItem("1", "Nowe", ShortcutIcon.MaterialIcon("add")),
    ShortcutItem("2", "Gry", ShortcutIcon.MaterialIcon("star")),
    ShortcutItem("3", "Rekomendowane", ShortcutIcon.MaterialIcon("search")),
    ShortcutItem("4", "Netflix", ShortcutIcon.VectorIcon(R.drawable.netflix_logo))
)

// START SliderMix component
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StartSliderMix(
    shouldFocus: Boolean,
    onFocusChange: (Boolean) -> Unit,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp,
    currentRow: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = offsetY)
    ) {
        SliderMixScreen(
            shouldAutoFocus = shouldFocus,
            isInTelewizjaSection = true, // Enable TV live playbook like in TELEWIZJA
            shouldShowFocusBorder = shouldFocus && currentRow == 1, // Show focus border only when SliderMix row is focused
            isShortcutsFocused = currentRow == 2 // Stop TV live when shortcuts are focused
        )
        // Invisible button removed - SliderMixScreen handles focus directly (like TELEWIZJA)
    }
}

// New 3-row START screen implementation
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NewStartScreenContent(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0
) {
    // Current focus state: Row (1=SliderMix, 2=Shortcuts, 3+=Channels), Position within row
    var currentRow by remember { mutableStateOf(1) }
    var shortcutFocusedIndex by remember { mutableStateOf(0) }
    var channelFocusedRowIndex by remember { mutableStateOf(0) }
    var channelFocusedColIndex by remember { mutableStateOf(-1) }

    // Focus requesters for all components
    val shortcutFocusRequesters = remember { (0..4).associateWith { FocusRequester() } } // 5 shortcuts (0-4)

    // Handle initial focus when coming from menu
    LaunchedEffect(shouldAutoFocus, resetTrigger) {
        if (shouldAutoFocus) {
            Log.d("START_DEBUG", "NewStartScreenContent: shouldAutoFocus=true, setting currentRow to 1")
            currentRow = 1
            // SliderMixScreen handles focus via shouldAutoFocus parameter (like TELEWIZJA)
        }
    }
    val channels = listOf("Oglądaj dalej", "Nagrania", "Do obejrzenia", "Wypożyczone", "Aktywne pakiety")
    // Faza 1: Rollback to Pair keys (simplified from Triple)
    val channelFocusRequesters = remember {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex -> // 5 channels from MOJE
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                put(Pair(rowIndex, 0), FocusRequester()) // Content (fixed focus position)
            }
        }
    }
    val context = LocalContext.current

    // Load TV channel data for "Moja lista kanałów" shortcut
    val tvChannelLogos = remember { loadTvChannelsFromAssets(context) }
    val mojaListaChannels = remember { filterTvChannelsByCategory(context, tvChannelLogos, "moja-lista") }

    val appIconsData = remember {
        mapOf(
            "Moja lista kanałów" to mojaListaChannels
        )
    }

    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        if (vodContentList.isNotEmpty()) {
            channels.associateWith { channelName ->
                vodContentList.shuffled().take(10)
            }
        } else {
            emptyMap()
        }
    }
    
    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Reset focus when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            currentRow = 1 // Start with SliderMix
            shortcutFocusedIndex = 0
            channelFocusedRowIndex = 0
            channelFocusedColIndex = -2
        }
    }

    // Reset LazyListState when returning to Shortcuts from Channels
    LaunchedEffect(currentRow) {
        if (currentRow == 2) { // Back on Shortcuts
            // Reset all channel miniatures to first position
            coroutineScope.launch {
                channels.forEachIndexed { rowIndex, _ ->
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
            // Reset channel focus state
            channelFocusedColIndex = -2
        }
    }

    // SliderMix is now background component (like TELEWIZJA) - no auto-focus needed
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleStartNavigation(
                    event = event,
                    currentRow = currentRow,
                    shortcutFocusedIndex = shortcutFocusedIndex,
                    channelFocusedRowIndex = channelFocusedRowIndex,
                    channelFocusedColIndex = channelFocusedColIndex,
                    onRowChange = { newRow -> currentRow = newRow },
                    onShortcutFocusChange = { index -> shortcutFocusedIndex = index },
                    onChannelFocusChange = { row, col ->
                        channelFocusedRowIndex = row
                        channelFocusedColIndex = col
                    },
                    shortcutFocusRequesters = shortcutFocusRequesters,
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu,
                    onSliderMixFocus = { /* SliderMix will handle its own focus internally */ }
                )
            }
    ) {
        // SliderMix at top (Y=0, 20px higher than before)
        // Animated Y positions based on focus state
        val isShortcutsFocused = currentRow == 2
        val isChannelsFocused = currentRow >= 3
        val animatedSliderY by animateDpAsState(
            targetValue = when {
                isChannelsFocused -> sy(-900) // Slider completely off-screen when on channels
                isShortcutsFocused -> sy(-600 - 140) // Additional 140px movement when on row 2
                else -> sy(0)
            },
            animationSpec = tween(durationMillis = 300),
            label = "sliderY"
        )
        val animatedShortcutsY by animateDpAsState(
            targetValue = when {
                isChannelsFocused -> sy(-400) // Shortcuts off-screen when on channels
                isShortcutsFocused -> sy(280) // Focused: 40px higher (280 instead of 320)
                else -> sy(960) // Default: below slider
            },
            animationSpec = tween(durationMillis = 300),
            label = "shortcutsY"
        )

        // Channels positioned under shortcuts: shortcut Y + shortcut height + 40px
        val shortcutHeight = sy(279) // From ShortcutCard height
        val channelsY = animatedShortcutsY + shortcutHeight + sy(40)
        
        // SliderMix as Row 1 (focused component)
        StartSliderMix(
            shouldFocus = currentRow == 1,
            onFocusChange = { isFocused ->
                if (isFocused) currentRow = 1
            },
            offsetY = animatedSliderY,
            currentRow = currentRow
        )
        
        StartShortcuts(
            focusRequesters = shortcutFocusRequesters,
            focusedIndex = shortcutFocusedIndex,
            currentRow = currentRow,
            onFocusChange = { index ->
                currentRow = 2 // Shortcuts are now Row 2
                shortcutFocusedIndex = index
            },
            onNavigateToChannelGrid = onNavigateToChannelGrid,
            onNavigateToVodGrid = onNavigateToVodGrid,
            onNavigateToKinoGrid = onNavigateToKinoGrid,
            appIconsData = appIconsData,
            sx = sx,
            sy = sy,
            offsetY = animatedShortcutsY
        )
        
        // Render channels at dynamic position (using MOJE channel rows)
        Box(
            modifier = Modifier.offset(y = channelsY)
        ) {
            // Use START positioning with MOJE channel rows (includes auto-reset)
            StartChannelRowsLayout(
                channels = channels,
                gridContent = gridContent,
                focusedRowIndex = channelFocusedRowIndex,
                focusedColIndex = channelFocusedColIndex,
                channelFocusRequesters = channelFocusRequesters,
                onChannelContentFocusChange = { row, col ->
                    currentRow = 3 + row // Channels start from Row 3
                    channelFocusedRowIndex = row
                    channelFocusedColIndex = col
                },
                lazyListStates = lazyListStates,
                currentRow = currentRow,
                sx = sx,
                sy = sy
            )
        }
        
        // Temporary positioning grid (20px spacing)
        PositioningGrid(
            visible = false, // Grid disabled for production
            sx = sx,
            sy = sy
        )
    }
}

// START channels layout (similar to MOJE but for START section)
@Composable
private fun StartChannelRowsLayout(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>, // Faza 1: Pair keys (simplified)
    onChannelContentFocusChange: (Int, Int) -> Unit,
    lazyListStates: Map<Int, LazyListState>,
    currentRow: Int,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(channels.size) { rowIndex ->
            val channelName = channels[rowIndex]
            val contentForChannel = gridContent[channelName] ?: emptyList()

            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

            val targetY = calculateStartChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                currentRow = currentRow,
                sy = sy
            )
            
            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                label = "start_channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                MojeUnifiedChannelRow(
                    channel = channelName,
                    rowIndex = rowIndex,
                    rowContent = contentForChannel,
                    packages = emptyList(), // START doesn't use packages
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }
        }
    }
}

// START unified channel row (similar to MOJE)
@Composable
private fun StartUnifiedChannelRow(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    
    var showDetailsWithDelay by remember { mutableStateOf(false) }
    
    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && focusedColIndex == 0
        
        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350) // 350ms = animation time
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Animated miniatures based on focus state
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "start_miniatures_y_offset_$rowIndex"
        )
        
        // LazyRow content - always rendered but animated
        LazyRow(
            modifier = Modifier
                .fillMaxWidth(),
            state = lazyListState,
            contentPadding = PaddingValues(
                start = sx(380),
                end = sx(20),
                top = if (isMiniaturesOnScreen) sy(290) else sy(0)
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            items(rowContent.size) { colIndex ->
                val vodContent = rowContent[colIndex]
                val isItemFocused = rowIndex == focusedRowIndex &&
                                   colIndex == lazyListState.firstVisibleItemIndex &&
                                   focusedColIndex == 0

                val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                ContentCard(
                    vodContent = vodContent,
                    channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                    isFocused = isItemFocused,
                    focusRequester = focusRequester,
                    onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }

            // Spacer items for smooth scrolling (like MOJE)
            items(8) {
                Spacer(
                    modifier = Modifier
                        .width(sx(220))
                        .height(sy(380))
                )
            }
        }
        
        // CategoryIcon (always visible, fixed position)
        val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]
        if (categoryFocusRequester != null) {
            CategoryIcon(
                text = channel,
                isFocused = isCurrentRow && focusedColIndex == -1,
                onClick = { },
                onFocused = { isFocused -> 
                    if (isFocused) {
                        Log.d("START_DEBUG", "CategoryIcon '$channel' (row $rowIndex) gained focus")
                        onChannelContentFocusChange(rowIndex, -1)
                    }
                },
                focusRequester = categoryFocusRequester,
                sx = sx,
                sy = sy
            )
        }
        
        // Details overlay
        if (showDetailsWithDelay && rowContent.isNotEmpty()) {
            DetailedContentOverlay(
                content = rowContent.first(),
                sx = sx,
                sy = sy
            )
        }
    }
}

// Navigation handler for new START 2-row system (SliderMix is background)
fun handleStartNavigation(
    event: KeyEvent,
    currentRow: Int,
    shortcutFocusedIndex: Int,
    channelFocusedRowIndex: Int,
    channelFocusedColIndex: Int,
    onRowChange: (Int) -> Unit,
    onShortcutFocusChange: (Int) -> Unit,
    onChannelFocusChange: (Int, Int) -> Unit,
    shortcutFocusRequesters: Map<Int, FocusRequester>,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>, // Faza 1: Pair keys (simplified)
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit,
    onSliderMixFocus: () -> Unit = {}
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            when (currentRow) {
                1 -> {
                    // From SliderMix to menu
                    onReturnToMenu()
                }
                2 -> {
                    // From Shortcuts to SliderMix (Row 1)
                    onRowChange(1)
                    onSliderMixFocus()
                }
                else -> {
                    // From Channels (Row 3+)
                    if (channelFocusedRowIndex > 0) {
                        // Move up within channels
                        val newRowIndex = channelFocusedRowIndex - 1
                        val targetColIndex = if (channelFocusedColIndex == -1) -1 else 0
                        onChannelFocusChange(newRowIndex, targetColIndex)
                        channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                    } else {
                        // From first channel to Shortcuts (Row 2)
                        onRowChange(2)
                        shortcutFocusRequesters[shortcutFocusedIndex]?.requestFocus()
                    }
                }
            }
            return true
        }
        
        Key.DirectionDown -> {
            when (currentRow) {
                1 -> {
                    // From SliderMix to Shortcuts (Row 2)
                    onRowChange(2)
                    shortcutFocusRequesters[shortcutFocusedIndex]?.requestFocus()
                }
                2 -> {
                    // From Shortcuts to Channels (Row 3)
                    onRowChange(3)
                    onChannelFocusChange(0, -1)
                    channelFocusRequesters[Pair(0, -1)]?.requestFocus()
                }
                else -> {
                    // Move down within channels
                    if (channelFocusedRowIndex < channels.size - 1) {
                        val newRowIndex = channelFocusedRowIndex + 1
                        val targetColIndex = if (channelFocusedColIndex == -1) -1 else 0
                        onChannelFocusChange(newRowIndex, targetColIndex)
                        channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                    }
                }
            }
            return true
        }

        Key.DirectionLeft -> {
            when (currentRow) {
                1 -> {
                    // SliderMix handles its own left navigation
                    return false
                }
                2 -> {
                    // Navigate left in shortcuts
                    if (shortcutFocusedIndex > 0) {
                        val newIndex = shortcutFocusedIndex - 1
                        onShortcutFocusChange(newIndex)
                        shortcutFocusRequesters[newIndex]?.requestFocus()
                    }
                }
                else -> {
                    // Handle channels navigation (reuse existing logic)
                    if (currentRow >= 3) {
                        if (channelFocusedColIndex == -1) {
                            // Already on CategoryIcon
                            return true
                        } else if (channelFocusedColIndex == 0) {
                            val lazyListState = lazyListStates[channelFocusedRowIndex]
                            if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                                }
                            } else {
                                onChannelFocusChange(channelFocusedRowIndex, -1)
                                channelFocusRequesters[Pair(channelFocusedRowIndex, -1)]?.requestFocus()
                            }
                        }
                    }
                }
            }
            return true
        }

        Key.DirectionRight -> {
            when (currentRow) {
                1 -> {
                    // SliderMix handles its own right navigation
                    return false
                }
                2 -> {
                    // Navigate right in shortcuts
                    if (shortcutFocusedIndex < 4) { // 0-4 = 5 shortcuts
                        val newIndex = shortcutFocusedIndex + 1
                        onShortcutFocusChange(newIndex)
                        shortcutFocusRequesters[newIndex]?.requestFocus()
                    }
                }
                else -> {
                    // Handle channels navigation (reuse existing logic)
                    if (currentRow >= 3) {
                        if (channelFocusedColIndex == -1) {
                            onChannelFocusChange(channelFocusedRowIndex, 0)
                            channelFocusRequesters[Pair(channelFocusedRowIndex, 0)]?.requestFocus()
                        } else if (channelFocusedColIndex == 0) {
                            val lazyListState = lazyListStates[channelFocusedRowIndex]
                            val channelName = channels.getOrNull(channelFocusedRowIndex)
                            val channelContent = gridContent[channelName] ?: emptyList()
                            val maxScrollPosition = channelContent.size + 8 - 1
                            
                            if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxScrollPosition) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                                }
                            }
                        }
                    }
                }
            }
            return true
        }
        
        else -> return false
    }
}

// MARK: - MOJE Expandable Channel Visual Components (Phase 3)

/**
 * SubChannelIcon - Ikona pod-kanału (240x140px)
 *
 * Podobna do CategoryIcon, ale mniejsza:
 * - CategoryIcon: 240x216px
 * - SubChannelIcon: 240x140px
 *
 * @param text Tytuł pod-kanału (np. "ZARZĄDZAJ NAGRANIAMI", "SERIE", "ZAPLANOWANE")
 * @param badge Opcjonalna plakietka (np. "11" dla SERIE)
 * @param isFocused Czy pod-kanał jest zfokusowany
 * @param onClick Akcja kliknięcia
 * @param onFocused Callback zmiany fokusa
 * @param focusRequester FocusRequester dla zarządzania fokusem
 */
// Faza 2: SubChannelIcon, SubChannelRow, and LeftSideMenu composables deleted
// Sub-channels will be regular channel rows (no special components needed)

/**
 * **Storage Counter Header** - Right-aligned header with progress bar for recording storage
 *
 * Displays storage usage: "Miejsce na nagrania" [progress bar] "pozostało X/Y h"
 * Based on Figma design (node 6431:12741)
 *
 * Visual:
 * - Semi-transparent pill container (rgba(255,255,255,0.07), 64px rounded corners)
 * - Progress bar showing remaining/total hours
 * - Right-aligned with 120px padding from screen edge
 * - Hardcoded values: 140h used, 220h total → 80h remaining (36.4% progress)
 *
 * @param usedHours Hours already used for recordings (default: 140)
 * @param totalHours Total available storage hours (default: 220)
 * @param sx Horizontal scaling function
 * @param sy Vertical scaling function
 */
@Composable
fun StorageCounterHeader(
    usedHours: Int = 140,
    totalHours: Int = 220,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val remainingHours = totalHours - usedHours
    val progressPercent = remainingHours.toFloat() / totalHours

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = sx(120))  // Right padding for alignment
    ) {
        // Semi-transparent pill container - right-aligned
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .background(
                    color = Color(0xFFFFFFFF).copy(alpha = 0.07f),
                    shape = RoundedCornerShape(sx(64))
                )
                .padding(horizontal = sx(32), vertical = sy(12))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(20)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left label: "Miejsce na nagrania"
                Text(
                    text = "Miejsce na nagrania",
                    fontSize = (18.8 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFEEEEEE)
                )

                // Progress bar container
                Box(
                    modifier = Modifier
                        .width(sx(200))
                        .height(sy(6))
                        .background(
                            color = Color(0xFFEEEEEE).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(sx(6))
                        )
                ) {
                    // Progress fill (white bar)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressPercent.coerceIn(0f, 1f))
                            .background(
                                color = Color(0xFFEEEEEE),
                                shape = RoundedCornerShape(sx(6))
                            )
                    )
                }

                // Right text: "pozostało 80/220 h"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(sx(2))
                ) {
                    Text(
                        text = "pozostało ",
                        fontSize = (18.8 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.8f)
                    )
                    Text(
                        text = "$remainingHours/",
                        fontSize = (25 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.8f)
                    )
                    Text(
                        text = "$totalHours h",
                        fontSize = (25 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * MojeShortcutCardV4 - Half-height shortcut card for "Skróty" sub-channel (MOJE → NAGRANIA)
 *
 * Displays one shortcut card "Zarządzaj nagraniami" positioned at X: 100px (NO CategoryIcon)
 * Card size: 310x90px (half height of shortcuts-v2, compact design for sub-channels)
 *
 * @param isFocused Whether the card is focused
 * @param focusRequester FocusRequester for focus management
 * @param onFocusChanged Callback when focus state changes
 * @param onClick Callback when card is clicked (navigate to all recordings)
 * @param sx Horizontal scaling function
 * @param sy Vertical scaling function
 */
@Composable
fun MojeShortcutCardV4(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var isCardFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .offset(x = sx(400), y = sy(0))  // Positioned at 400px from left edge
            .size(sx(310), sy(90))  // Half height: 90px (was 179px)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (nowFocused != isCardFocused) {
                    isCardFocused = nowFocused
                    onFocusChanged(nowFocused)
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(sx(20)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x3B000000)  // rgba(0,0,0,0.23)
        ),
        border = if (isFocused)
            androidx.compose.foundation.BorderStroke(sx(6), Color(0xFF5AECD3))  // 6px border
        else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFocused) Color(0x4D000000) else Color.Transparent)
                // rgba(0,0,0,0.30) overlay when focused
                .padding(sx(15))  // Smaller padding for smaller card
        ) {
            // Text centered - no icons
            Text(
                text = "Zarządzaj nagraniami",
                fontSize = (24 * (sy(1).value / 1.dp.value)).sp,  // Same as CategoryIcon: 24sp
                fontWeight = FontWeight.W500,
                color = Color(0xFFEEEEEE),
                lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp,  // Match CategoryIcon line height
                letterSpacing = 0.32.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        }
    }
}

// Faza 4: Simplified layout - sub-channels are now regular channels, no special rendering needed
@Composable
fun MojeChannelRowsLayout(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    packages: List<PackageItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    isNagraniaExpanded: Boolean = false,
    toggleNagraniaExpansion: (() -> Unit)? = null,
    mojeNagraniaShortcuts: List<ShortcutItem> = emptyList(),  // NEW: for "Skróty v2 Moje"
    mojaListaChannels: List<TvChannel> = emptyList(),  // NEW: for "Moja lista kanałów"
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> }  // For TV channel click
) {
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(channels.size) { rowIndex ->
            val channelName = channels[rowIndex]
            val contentForChannel = gridContent[channelName] ?: emptyList()
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

            // Faza 4: Simple Y position calculation (standard Version001Screen pattern)
            val targetY = calculateMojeChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channels = channels,
                sy = sy
            )

            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                animationSpec = tween(
                    durationMillis = 350, // Sync z miniatures (match VOD timing)
                    easing = androidx.compose.animation.core.EaseInOutCubic
                ),
                label = "moje_channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier
                    .offset(y = channelYOffset)
                    .zIndex(0f) // Above background overlay
            ) {
                MojeUnifiedChannelRow(
                    channel = channelName,
                    rowIndex = rowIndex,
                    rowContent = contentForChannel,
                    packages = packages,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    onNavigateToVodGrid = onNavigateToVodGrid,
                    onNavigateToKinoGrid = onNavigateToKinoGrid,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState,
                    isNagraniaExpanded = isNagraniaExpanded,
                    toggleNagraniaExpansion = toggleNagraniaExpansion,
                    mojeNagraniaShortcuts = mojeNagraniaShortcuts,  // NEW: pass shortcuts data
                    tvChannels = if (channelName == "Moja lista kanałów") mojaListaChannels else emptyList(),  // NEW: pass TV channels for app-icons
                    onNavigateToEpgDay = onNavigateToEpgDay  // For TV channel click
                )
            }
        }
    }
}

// Faza 4: Simplified row - no sub-channel parameters
@Composable
fun MojeUnifiedChannelRow(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    packages: List<PackageItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState,
    isNagraniaExpanded: Boolean = false,
    toggleNagraniaExpansion: (() -> Unit)? = null,
    mojeNagraniaShortcuts: List<ShortcutItem> = emptyList(),  // For "Skróty v2 Moje" rendering
    tvChannels: List<TvChannel> = emptyList(),  // For "Moja lista kanałów" rendering
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> }  // For TV channel click
) {
    val isCurrentRow = rowIndex == focusedRowIndex

    // Detect content type based on channel name
    val isVertical = channel == "Wypożyczone"
    val isPackages = channel == "Aktywne pakiety"
    val isAppIcons = channel == "Moja lista kanałów"

    var showDetailsWithDelay by remember { mutableStateOf(false) }

    // Show details after animation completes (content is focused)
    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && focusedColIndex == 0

        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350) // 350ms = animation time
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // NAGRANIA Groups - Three separate background groups with different expansion behaviors
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Group 1: "Moje nagrania" (old expandable) + sub-channels - EXPANDABLE background
    val isMojeNagraniaGroup = channel in listOf("Moje nagrania", "Skróty", "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE")

    // Group 2: "Nagrania" (new channel) - EXPANDABLE background (same as old Moje nagrania)
    val isNagraniaNewExpandable = channel == "Nagrania"

    // Group 3: Header + Skróty v2 - FIXED height (non-expandable)
    val isNagraniaNewFixed = channel.startsWith("[HEADER-RIGHT]") || channel == "Skróty v2 Moje"

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = sy(20))) {
        // Background Box - purple #3A1B63 for all three groups
        if (isMojeNagraniaGroup || isNagraniaNewExpandable || isNagraniaNewFixed) {
            val isCurrentRow = rowIndex == focusedRowIndex
            val isShortcuts = channel == "Skróty"
            val isHeader = channel.startsWith("[HEADER-RIGHT]")
            val isNewShortcuts = channel == "Skróty v2 Moje"

            // Y offset calculation
            val backgroundYOffset = when {
                // Fixed channels - always -20px
                isHeader -> sy(-20)
                isNewShortcuts -> sy(-20)
                isShortcuts -> sy(-20)
                // Expandable channels - shift up when content focused
                (isMojeNagraniaGroup || isNagraniaNewExpandable) && isCurrentRow && focusedColIndex >= 0 -> sy(-120)
                // Default - normal offset
                else -> sy(-20)
            }

            // Height calculation
            val animatedBackgroundHeight = when {
                // Fixed heights for non-expandable channels
                isHeader -> sy(80)                               // Header: 80px total
                isNewShortcuts -> sy(180)                        // Skróty v2: 120px card + 60px spacing
                isShortcuts -> sy(170)                           // Skróty (old): 90px card + 80px spacing
                // Expandable channels - expand when content focused
                (isMojeNagraniaGroup || isNagraniaNewExpandable) && isCurrentRow && focusedColIndex >= 0 -> sy(546)  // EXPANDED: CategoryIcon (216px) + miniatures (290px) + spacing (40px)
                // Default - normal height
                else -> sy(256)                                   // NORMAL: CategoryIcon (216px) + spacing (40px)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = backgroundYOffset)  // Animate position
                    .height(animatedBackgroundHeight)  // Animate height
                    .background(Color(0xFF3A1B63))  // Solid purple #3A1B63
                    .zIndex(-2f)  // Behind all content
            )
        }

        // Content wrapper Box without padding (content fills entire background)
        Box(modifier = Modifier.fillMaxWidth()) {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // NEW SECTION v2: Header + Content + Shortcuts (3 channels)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (channel.startsWith("[HEADER-RIGHT]")) {
            // 1. Storage Counter Header - right-aligned, no focus
            StorageCounterHeader(
                usedHours = 140,
                totalHours = 220,
                sx = sx,
                sy = sy
            )
        } else if (channel == "Skróty v2 Moje") {
            // 3. Shortcuts Row - 4 focusable buttons (Zarządzaj, Pojedyncze, Serie, Zaplanowane)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sx(400)),  // Align with content area (not 120px like TELEWIZJA)
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                mojeNagraniaShortcuts.forEachIndexed { colIndex, shortcut ->
                    val isItemFocused = isCurrentRow && colIndex == focusedColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    ShortcutCardV2(
                        shortcut = shortcut,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { isFocused ->
                            if (isFocused) {
                                onChannelContentFocusChange(rowIndex, colIndex)
                            }
                        },
                        onClick = {
                            val vodList = VodDataCache.getVodContentList()
                            when (shortcut.title) {
                                "Zarządzaj nagraniami" -> {
                                    onNavigateToVodGrid("Wszystkie nagrania", vodList.shuffled().take(20), "MOJE")
                                }
                                "Pojedyncze" -> {
                                    onNavigateToVodGrid("Pojedyncze nagrania", vodList.shuffled().take(20), "MOJE")
                                }
                                "Serie" -> {
                                    val seriesContent = vodList.filter {
                                        it.category.contains("Serial", ignoreCase = true)
                                    }.take(20)
                                    onNavigateToVodGrid("Serie", seriesContent, "MOJE")
                                }
                                "Zaplanowane" -> {
                                    onNavigateToVodGrid("Zaplanowane nagrania", vodList.shuffled().take(10), "MOJE")
                                }
                            }
                        },
                        sx = sx,
                        sy = sy,
                        heightPx = 120  // MOJE v2: Reduced height for compact layout
                    )
                }
            }
        } // 2. "Nagrania" uses standard LazyRow below (no special case needed)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // END NEW SECTION v2
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Special rendering for "Skróty" sub-channel (single shortcut card v4 - half height)
        else if (channel == "Skróty") {
            val shortcutFocusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
            MojeShortcutCardV4(
                isFocused = isCurrentRow && focusedColIndex == 0,
                focusRequester = shortcutFocusRequester,
                onFocusChanged = { isFocused ->
                    if (isFocused) {
                        onChannelContentFocusChange(rowIndex, 0)
                    }
                },
                onClick = {
                    Log.d("MOJE_DEBUG", "Zarządzaj nagraniami clicked - navigate to recordings grid")
                    val vodList = VodDataCache.getVodContentList()
                    val randomFilms = vodList.shuffled().take(20)
                    onNavigateToVodGrid("Wszystkie nagrania", randomFilms, "MOJE")
                },
                sx = sx,
                sy = sy
            )
        } else if (isAppIcons) {
            // App Icons row - TV channels from "Moja lista kanałów"
            // IDENTICAL to TELEWIZJA implementation (ChannelListCard, direct focus, 12px spacing)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = lazyListState,
                contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                horizontalArrangement = Arrangement.spacedBy(sx(12))  // Same as TELEWIZJA
            ) {
                items(tvChannels.size) { colIndex ->
                    val channel = tvChannels[colIndex]
                    // Direct focus model (same as TELEWIZJA)
                    val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    ChannelListCard(
                        channel = channel,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                        onClick = {
                            // Navigate to EPG Day screen (same as TELEWIZJA)
                            val epgId = channel.epgId ?: channel.name
                            onNavigateToEpgDay("Moja lista kanałów", epgId, 0, "MOJE")
                        },
                        sx = sx,
                        sy = sy
                    )
                }

                // Spacer items (same size as TELEWIZJA)
                items(8) {
                    Spacer(
                        modifier = Modifier
                            .width(sx(TELEWIZJA_CHANNEL_LIST_CARD_WIDTH))   // 208px
                            .height(sy(TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT))  // 208px
                    )
                }
            }
        } else {
        // Standard LazyRow content for other channels
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "moje_miniatures_y_offset_$rowIndex"
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = miniaturesYOffset),
            state = lazyListState,
            contentPadding = PaddingValues(start = sx(380), end = sx(20)),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            // For Pakiety channel, use packages data
            if (isPackages) {
                items(packages.size) { colIndex ->
                    val packageItem = packages[colIndex]
                    val isItemFocused = rowIndex == focusedRowIndex &&
                                       colIndex == lazyListState.firstVisibleItemIndex &&
                                       focusedColIndex == 0

                    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    // CONDITIONAL FOCUSREQUESTER ASSIGNMENT (Component #3)
                    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    // PATTERN: Fixed focus window - only firstVisibleItemIndex owns FocusRequester
                    //
                    // Problem: LazyRow has 100+ items, all can't have same FocusRequester
                    // Result: Focus conflicts, random jumping, crashes
                    //
                    // Solution: Only visible item (firstVisibleItemIndex) gets real FocusRequester
                    // - Focus appears "fixed" at position 0
                    // - Content scrolls underneath
                    // - FocusRequester automatically reassigned on scroll
                    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    // FIX: Only assign FocusRequester to currently visible item (like WIDEO)
                    val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
                        channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    } else {
                        FocusRequester()
                    }

                    PackageCard(
                        packageItem = packageItem,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                        sx = sx,
                        sy = sy
                    )
                }
            } else {
                items(rowContent.size) { colIndex ->
                    val vodContent = rowContent[colIndex]
                    val isItemFocused = rowIndex == focusedRowIndex &&
                                       colIndex == lazyListState.firstVisibleItemIndex &&
                                       focusedColIndex == 0

                    // Same pattern as PackageCard above - see Component #3 documentation
                    // FIX: Only assign FocusRequester to currently visible item (like WIDEO)
                    val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
                        channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    } else {
                        FocusRequester()
                    }

                    when {
                        isVertical -> {
                            VodContentCard(
                                vodContent = vodContent,
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy,
                                expiryText = if (channel == "Wypożyczone") "rented" else null
                            )
                        }
                        else -> {
                            ContentCard(
                                vodContent = vodContent,
                                channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy,
                                lazyListState = lazyListState
                                // No onClick for MOJE section
                            )
                        }
                    }
                }
            }

            // Spacer items - different sizes for vertical vs horizontal
            items(8) {
                val spacerWidth = if (isVertical) sx(220) else sx(368)
                val spacerHeight = if (isVertical) sy(380) else sy(208)

                Spacer(
                    modifier = Modifier
                        .width(spacerWidth)
                        .height(spacerHeight)
                )
            }
        }

        // Details overlay - show when content is focused
        if (isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }
                        
                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }
        } // end else (standard LazyRow content)

        // Title above scrolled list for app-icons (like TELEWIZJA)
        val isCurrentRow = rowIndex == focusedRowIndex
        if (isAppIcons && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) {
            Box(modifier = Modifier.offset(x = sx(80), y = sy(-45))) {
                Text(
                    text = channel,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // CategoryIcon alpha and zIndex for app-icons fade (like TELEWIZJA)
        val categoryAlpha = if (isAppIcons && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) 0f else 1f
        val categoryZIndex = if (isAppIcons) -1f else 0f

        // CategoryIcon (hidden for "Skróty", Headers, and "Skróty v2 Moje")
        if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
        Box(modifier = Modifier
            .offset(x = sx(80), y = sy(0))
            .alpha(categoryAlpha)
            .zIndex(categoryZIndex)
        ) {
            // Faza 4: Standard focus check
            val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
            val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]

            // Icon mapping for different channels (PNG)
            val logoDrawableId = when (channel) {
                "Oglądaj dalej" -> R.drawable.ic_keep_watching
                "Moje nagrania" -> R.drawable.ic_records  // Old expandable channel
                "Nagrania" -> R.drawable.ic_records       // New channel with icon
                "Do obejrzenia" -> R.drawable.ic_add_to_watch
                "Aktywne pakiety" -> R.drawable.ic_packages
                "Wypożyczone" -> R.drawable.ic_rented
                "Moja lista kanałów" -> null  // App-icons channel - text-only
                else -> null
            }

            // Sub-channels and app-icons channels use WIDEO style (text-only, bg on focus)
            val isSubChannel = channel in listOf(
                "Skróty",
                "Pojedyncze nagrania",
                "SERIE",
                "ZAPLANOWANE",
                "Moja lista kanałów"  // App-icons channel - text-only like TELEWIZJA
            )

            if (rowIndex < 3) { // Debug only for first 3 channels
                Log.d("MOJE_DEBUG", "Channel '$channel' (row $rowIndex) - categoryIsFocused: $categoryIsFocused, focusRequester available: ${categoryFocusRequester != null}")
            }

            CategoryIcon(
                text = channel,
                isFocused = categoryIsFocused,
                onClick = {
                    // Empty - expansion for "Moje nagrania" is handled by chevron click (onChevronClick)
                    // MOJE CategoryIcons don't navigate to grids (unlike START section)
                },
                onFocused = { isFocused ->
                    if (isFocused) {
                        Log.d("MOJE_DEBUG", "CategoryIcon '$channel' (row $rowIndex) gained focus")
                    }
                },
                focusRequester = categoryFocusRequester ?: FocusRequester(),
                sx = sx,
                sy = sy,
                logoDrawableId = if (!isSubChannel) logoDrawableId else null,
                showIcon = !isSubChannel,                      // Sub-channels: false (text-only like WIDEO)
                showBackgroundWhenFocused = isSubChannel,      // Sub-channels: true (background on focus)
                isExpanded = (channel == "Moje nagrania") && isNagraniaExpanded,
                showChevron = (channel == "Moje nagrania"),
                onChevronClick = if (channel == "Moje nagrania") { { toggleNagraniaExpansion?.invoke() } } else null
            )
        }
        }  // end if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje")
        }  // End content wrapper Box (with padding for NAGRANIA)
    }  // End main outer Box (with background for NAGRANIA)
}

// Helper function to calculate Y position for MOJE channels
// MOJE Screen spacing constants - CategoryIcon height (216px) + spacing
private const val MOJE_FIXED_FOCUS_Y = 340 // Focused channel always at this height
// Horizontal miniatures (Oglądaj dalej, Nagrania, Do obejrzenia) - small spacing
private const val MOJE_HORIZONTAL_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + spacing (40px)
private const val MOJE_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546 // CategoryIcon (216px) + miniatures (290px) + spacing (40px)
// Vertical posters (Pakiety, Wypożyczone) - large spacing
private const val MOJE_VERTICAL_NORMAL_ROW_HEIGHT = 346 // CategoryIcon (216px) + spacing (130px)
private const val MOJE_VERTICAL_EXPANDED_ROW_HEIGHT = 636 // CategoryIcon (216px) + miniatures (290px) + spacing (130px)
// Shortcuts v4 (Skróty sub-channel) - fixed height, NO expansion, half-size card
private const val MOJE_SHORTCUTS_NORMAL_ROW_HEIGHT = 150 // Shortcut card (90px) + spacing (60px)
private const val MOJE_SHORTCUTS_EXPANDED_ROW_HEIGHT = 150 // NO expansion - always 150px
// Shortcuts v2 (Skróty v2 Moje) - fixed height, NO expansion, larger card
private const val MOJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT = 160 // Shortcut card (120px) + spacing (40px)
private const val MOJE_SHORTCUTS_V2_EXPANDED_ROW_HEIGHT = 160 // NO expansion - always 160px
private const val MOJE_CONTENT_FOCUS_EXTRA_SPACING = 100 // Extra spacing above focused content row
// App-icons (Moja lista kanałów) - NO expansion like TELEWIZJA
private const val MOJE_APP_ICONS_NORMAL_ROW_HEIGHT = 256  // CategoryIcon (216px) + spacing (40px)
private const val MOJE_APP_ICONS_EXPANDED_ROW_HEIGHT = 256 // NO expansion - always 256px (like TELEWIZJA)

// MOJE Sub-Channel spacing constants (Phase 4) - For expandable NAGRANIA channel
private const val MOJE_SUB_CHANNEL_NORMAL_HEIGHT = 180 // SubChannelIcon (140px) + spacing (40px)
private const val MOJE_SUB_CHANNEL_EXPANDED_HEIGHT = 470 // SubChannelIcon (140px) + miniatures (290px) + spacing (40px)
private const val MOJE_EXPANDABLE_CHANNEL_EXPANDED_HEIGHT = 1200 // CategoryIcon + 3 sub-channels (approximate, will calculate dynamically)

// START section spacing constants (same as MOJE)
private const val START_FIXED_FOCUS_Y = 340 // Focused channel always at this height  
private const val START_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + spacing (40px)
private const val START_EXPANDED_ROW_HEIGHT = 546 // CategoryIcon (216px) + miniatures (290px) + spacing (40px)
private const val START_CONTENT_FOCUS_EXTRA_SPACING = 100 // Extra spacing above focused content row

// APLIKACJE section spacing constants (same as MOJE for horizontal, special for shortcuts)
private const val APLIKACJE_FIXED_FOCUS_Y = 340 // Focused channel always at this height
private const val APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + spacing (40px)
private const val APLIKACJE_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546 // CategoryIcon (216px) + miniatures (290px) + spacing (40px)
private const val APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT = 346 // CategoryIcon (216px) + shortcuts height (279px) - overlaps slightly
private const val APLIKACJE_SHORTCUTS_EXPANDED_ROW_HEIGHT = 636 // CategoryIcon (216px) + shortcuts (279px) + extra (130px)
private const val APLIKACJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT = 246 // CategoryIcon (216px) + shortcuts v2 height (179px) - overlaps slightly
private const val APLIKACJE_SHORTCUTS_V2_EXPANDED_ROW_HEIGHT = 246 // NO expansion for shortcuts v2 (same as normal)
private const val APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + app icon height (220px) + spacing (10px) - overlaps (reduced by 30px)
private const val APLIKACJE_APP_ICONS_EXPANDED_ROW_HEIGHT = 286 // With focus (original spacing)
private const val APLIKACJE_CONTENT_FOCUS_EXTRA_SPACING = 100 // Extra spacing above focused content row

// ODKRYWAJ section constants
private const val ODKRYWAJ_FIXED_FOCUS_Y = 170 // Focused channel always at this height (140 menu + 30 spacing)
private const val ODKRYWAJ_SLIDER_MAX_NORMAL_ROW_HEIGHT = 782 // Big slider (742px) + spacing (40px)
private const val ODKRYWAJ_SLIDER_MAX_EXPANDED_ROW_HEIGHT = 782 // Same as normal (no expansion needed)
private const val ODKRYWAJ_SHORTCUTS_NORMAL_ROW_HEIGHT = 406 // Shortcuts base height (no extra spacing in constant)
private const val ODKRYWAJ_SHORTCUTS_EXPANDED_ROW_HEIGHT = 406 // NO expansion for shortcuts
private const val ODKRYWAJ_TOP10_NORMAL_ROW_HEIGHT = 406 // CategoryIcon (216px) + spacing (130px) + 60px extra
private const val ODKRYWAJ_TOP10_EXPANDED_ROW_HEIGHT = 696 // CategoryIcon (216px) + miniatures (290px) + spacing (130px) + 60px extra
private const val ODKRYWAJ_VERTICAL_NORMAL_ROW_HEIGHT = 320 // CategoryIcon (216px) + vertical card (280px) - slight overlap
private const val ODKRYWAJ_VERTICAL_EXPANDED_ROW_HEIGHT = 610 // CategoryIcon (216px) + vertical (280px) + slide (290px) - overlaps
private const val ODKRYWAJ_VERTICAL_VOD_NORMAL_ROW_HEIGHT = 346 // For VodContentCard (380px): CategoryIcon (216px) + spacing (130px)
private const val ODKRYWAJ_VERTICAL_VOD_EXPANDED_ROW_HEIGHT = 636 // For VodContentCard: CategoryIcon (216px) + miniatures (290px) + spacing (130px)
private const val ODKRYWAJ_HORIZONTAL_NORMAL_ROW_HEIGHT = 256 // Same as APLIKACJE
private const val ODKRYWAJ_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546 // Same as APLIKACJE
private const val ODKRYWAJ_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT = 544 // Collection slider (464px) + spacing (80px)
private const val ODKRYWAJ_COLLECTION_SLIDER_EXPANDED_ROW_HEIGHT = 544 // NO expansion for collection slider
private const val ODKRYWAJ_CONTENT_FOCUS_EXTRA_SPACING = 100 // Extra spacing above focused content row

// TELEWIZJA section constants
// Note: Wszystkie focusable rows at Y:270px, wszystko scrolluje razem
private const val TELEWIZJA_FIXED_FOCUS_Y = 270 // 270px offset from top of screen

// Top menu gradient constants (same as EPG Day TopMenuOverlay)
private const val TOP_MENU_GRADIENT_HEIGHT = 600 // Gradient height: 600px from top

private const val TELEWIZJA_SHORTCUTS_NORMAL_ROW_HEIGHT = 406
private const val TELEWIZJA_SHORTCUTS_EXPANDED_ROW_HEIGHT = 406
private const val TELEWIZJA_SHORTCUTS_V2_NORMAL_ROW_HEIGHT = 239 // Skróty v2: 4 horizontal shortcuts (NO expansion)
private const val TELEWIZJA_TOP10_NORMAL_ROW_HEIGHT = 406
private const val TELEWIZJA_TOP10_EXPANDED_ROW_HEIGHT = 696
private const val TELEWIZJA_VERTICAL_NORMAL_ROW_HEIGHT = 320
private const val TELEWIZJA_VERTICAL_EXPANDED_ROW_HEIGHT = 610
private const val TELEWIZJA_VERTICAL_VOD_NORMAL_ROW_HEIGHT = 346
private const val TELEWIZJA_VERTICAL_VOD_EXPANDED_ROW_HEIGHT = 636
private const val TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val TELEWIZJA_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
private const val TELEWIZJA_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT = 544
private const val TELEWIZJA_COLLECTION_SLIDER_EXPANDED_ROW_HEIGHT = 544
private const val TELEWIZJA_SERVICE_LOGOS_NORMAL_ROW_HEIGHT = 240  // 200px cards + 40px spacing
private const val TELEWIZJA_SERVICE_LOGOS_EXPANDED_ROW_HEIGHT = 240 // No expansion for service logos
private const val TELEWIZJA_CHANNEL_LOGOS_NORMAL_ROW_HEIGHT = 340  // 2x (150px + 20px) = 2x170 = 340px
private const val TELEWIZJA_CHANNEL_LOGOS_EXPANDED_ROW_HEIGHT = 340 // No expansion for channel logos grid
private const val TELEWIZJA_APP_ICONS_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + spacing (40px) - app-icons type
private const val TELEWIZJA_APP_ICONS_EXPANDED_ROW_HEIGHT = 256 // No expansion for app-icons (like APLIKACJE)
private const val TELEWIZJA_CHANNEL_LIST_CARD_WIDTH = 208 // Figma: channel_list_card width
private const val TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT = 208 // Figma: channel_list_card height (square)
private const val TELEWIZJA_CHANNEL_LIST_CARD_LOGO_SIZE = 148 // Figma: logo size (148x148)
private const val TELEWIZJA_HEADER_ROW_HEIGHT = 80 // Header text (32sp) + spacing
private const val TELEWIZJA_CONTENT_FOCUS_EXTRA_SPACING = 100

private fun calculateMojeChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    // Determine channel type: header, shortcuts, shortcuts-v2, vertical posters, app-icons, or horizontal miniatures
    val channelName = channels.getOrNull(rowIndex) ?: ""
    val isHeader = channelName.startsWith("[HEADER")
    val isShortcuts = channelName == "Skróty"
    val isShortcutsV2 = channelName == "Skróty v2 Moje"
    val isVertical = channelName == "Wypożyczone"
    val isAppIcons = channelName == "Moja lista kanałów"  // App-icons - NO expansion
    val normalRowHeight = when {
        isHeader -> 80                               // Header: 80px content, 0px spacing
        isShortcuts -> MOJE_SHORTCUTS_NORMAL_ROW_HEIGHT
        isShortcutsV2 -> MOJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
        isVertical -> MOJE_VERTICAL_NORMAL_ROW_HEIGHT
        isAppIcons -> MOJE_APP_ICONS_NORMAL_ROW_HEIGHT   // App-icons: no expansion
        else -> MOJE_HORIZONTAL_NORMAL_ROW_HEIGHT
    }
    val expandedRowHeight = when {
        isHeader -> 80                               // Header: no expansion
        isShortcuts -> MOJE_SHORTCUTS_EXPANDED_ROW_HEIGHT
        isShortcutsV2 -> MOJE_SHORTCUTS_V2_EXPANDED_ROW_HEIGHT
        isVertical -> MOJE_VERTICAL_EXPANDED_ROW_HEIGHT
        isAppIcons -> MOJE_APP_ICONS_EXPANDED_ROW_HEIGHT // App-icons: no expansion (256=256)
        else -> MOJE_HORIZONTAL_EXPANDED_ROW_HEIGHT
    }

    return when {
        // Zfokusowany kanał - zawsze na Y: 340px (MOJE_FIXED_FOCUS_Y)
        rowIndex == focusedRowIndex -> {
            sy(MOJE_FIXED_FOCUS_Y)
        }
        // Kanały powyżej zfokusowanego - przesuwają się w górę
        rowIndex < focusedRowIndex -> {
            // Dodatkowe 100px odsunięcie gdy fokus na treści (focusedColIndex >= 0)
            // EXCEPT for shortcuts - no extra spacing for shortcuts (compact layout)
            val focusedChannelName = channels.getOrNull(focusedRowIndex) ?: ""
            val isFocusedShortcuts = focusedChannelName in listOf("Skróty", "Skróty v2 Moje")
            val extraSpacing = if (focusedColIndex >= 0 && !isFocusedShortcuts) {
                MOJE_CONTENT_FOCUS_EXTRA_SPACING
            } else {
                0
            }
            // Calculate cumulative height from current row to focused row
            var cumulativeHeight = MOJE_FIXED_FOCUS_Y
            for (i in rowIndex until focusedRowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenIsHeader = betweenChannelName.startsWith("[HEADER")
                val betweenIsShortcuts = betweenChannelName == "Skróty"
                val betweenIsShortcutsV2 = betweenChannelName == "Skróty v2 Moje"
                val betweenIsVertical = betweenChannelName == "Wypożyczone"
                val betweenIsAppIcons = betweenChannelName == "Moja lista kanałów"
                val betweenRowHeight = when {
                    betweenIsHeader -> 80                        // Header: 0px spacing
                    betweenIsShortcuts -> MOJE_SHORTCUTS_NORMAL_ROW_HEIGHT
                    betweenIsShortcutsV2 -> MOJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    betweenIsVertical -> MOJE_VERTICAL_NORMAL_ROW_HEIGHT
                    betweenIsAppIcons -> MOJE_APP_ICONS_NORMAL_ROW_HEIGHT
                    else -> MOJE_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
                cumulativeHeight -= betweenRowHeight
            }
            sy(cumulativeHeight - extraSpacing)
        }
        // Kanały poniżej zfokusowanego - przesuwają się w dół aby zrobić miejsce
        rowIndex > focusedRowIndex -> {
            // Sprawdzamy czy zfokusowany kanał ma miniaturkę zfokusowaną (powiększony)
            val focusedChannelName = channels.getOrNull(focusedRowIndex) ?: ""
            val focusedIsShortcuts = focusedChannelName in listOf("Skróty", "Skróty v2 Moje")
            val focusedIsVertical = focusedChannelName == "Wypożyczone"
            val focusedIsAppIcons = focusedChannelName == "Moja lista kanałów"
            val focusedChannelExpansion = if (focusedColIndex >= 0) {
                when {
                    focusedIsShortcuts -> MOJE_SHORTCUTS_EXPANDED_ROW_HEIGHT
                    focusedIsVertical -> MOJE_VERTICAL_EXPANDED_ROW_HEIGHT
                    focusedIsAppIcons -> MOJE_APP_ICONS_EXPANDED_ROW_HEIGHT  // No expansion
                    else -> MOJE_HORIZONTAL_EXPANDED_ROW_HEIGHT
                }
            } else {
                when {
                    focusedIsShortcuts -> MOJE_SHORTCUTS_NORMAL_ROW_HEIGHT
                    focusedIsVertical -> MOJE_VERTICAL_NORMAL_ROW_HEIGHT
                    focusedIsAppIcons -> MOJE_APP_ICONS_NORMAL_ROW_HEIGHT
                    else -> MOJE_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }

            // Dodatkowy odstęp 20px dla pionowych miniaturek gdy są zfokusowane
            val verticalExtraSpacing = if (focusedIsVertical && focusedColIndex >= 0) 20 else 0

            // Calculate cumulative height from focused to this channel
            var cumulativeHeight = MOJE_FIXED_FOCUS_Y + focusedChannelExpansion + verticalExtraSpacing
            for (i in (focusedRowIndex + 1) until rowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenIsHeader = betweenChannelName.startsWith("[HEADER")
                val betweenIsShortcuts = betweenChannelName == "Skróty"
                val betweenIsShortcutsV2 = betweenChannelName == "Skróty v2 Moje"
                val betweenIsVertical = betweenChannelName == "Wypożyczone"
                val betweenIsAppIcons = betweenChannelName == "Moja lista kanałów"
                val betweenRowHeight = when {
                    betweenIsHeader -> 80                        // Header: 0px spacing
                    betweenIsShortcuts -> MOJE_SHORTCUTS_NORMAL_ROW_HEIGHT
                    betweenIsShortcutsV2 -> MOJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    betweenIsVertical -> MOJE_VERTICAL_NORMAL_ROW_HEIGHT
                    betweenIsAppIcons -> MOJE_APP_ICONS_NORMAL_ROW_HEIGHT
                    else -> MOJE_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
                cumulativeHeight += betweenRowHeight
            }
            sy(cumulativeHeight)
        }
        // Fallback (nie powinno się wydarzyć)
        else -> sy(140 + rowIndex * normalRowHeight)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// NAVIGATION HANDLER with AUTO-COLLAPSE (Component #4)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Complete navigation logic for MOJE section with NAGRANIA expandable sub-channels
//
// Key behaviors:
// - OK on "Nagrania" CategoryIcon: Toggle expansion (5↔8 channels)
// - UP from first sub-channel: Auto-collapse + focus parent
// - DOWN from last sub-channel: Auto-collapse + focus next channel
// - BACK from any sub-channel: Auto-collapse + focus parent icon
// - LEFT/RIGHT: Standard LazyRow scrolling with bounds checking
//
// CRITICAL: delay(50) before requestFocus() after collapse
// Reason: Recomposition needs 16-50ms to update channel list
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// Faza 4-5: MOJE navigation with auto-collapse logic
fun handleMojeChannelsNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit,
    onToggleExpansion: (() -> Unit)? = null,
    isNagraniaExpanded: Boolean = false,  // Faza 5: For auto-collapse detection
    onToggleVersion: (() -> Unit)? = null  // Key "8": Toggle v1/v2
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        // Key "8" removed - now handled globally

        // OK on "Moje nagrania" CategoryIcon → toggle expansion
        Key.Enter, Key.DirectionCenter -> {
            if (focusedColIndex == -1) {
                val channelName = channels.getOrNull(focusedRowIndex)
                if (channelName == "Moje nagrania") {
                    onToggleExpansion?.invoke()
                    Log.d("MOJE_DEBUG", "OK on MOJE NAGRANIA → toggle expansion")
                    return true
                }
            }
            return false
        }

        Key.DirectionUp -> {
            // Faza 5: Auto-collapse when going UP from "Skróty" (first sub-channel) to "Moje nagrania"
            val currentChannel = channels.getOrNull(focusedRowIndex)
            if (isNagraniaExpanded && currentChannel == "Skróty") {
                Log.d("MOJE_DEBUG", "AUTO-COLLAPSE: UP from Skróty → collapse and focus Moje nagrania")
                onToggleExpansion?.invoke() // Collapse
                // After collapse, "Moje nagrania" will be at row=1
                kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(50) // Wait for channel list to update
                    val targetColIndex = if (focusedColIndex == -1) -1 else 0
                    onChannelContentFocusChange(1, targetColIndex)
                    channelFocusRequesters[Pair(1, targetColIndex)]?.requestFocus()
                }
                return true
            }

            when {
                focusedRowIndex > 0 -> {
                    // Move up one channel
                    var newRowIndex = focusedRowIndex - 1
                    var newChannelName = channels.getOrNull(newRowIndex) ?: ""
                    val currentChannelName = channels.getOrNull(focusedRowIndex) ?: ""

                    // Skip header if we're moving to it (headers are not focusable)
                    if (newChannelName.startsWith("[HEADER")) {
                        newRowIndex -= 1
                        newChannelName = channels.getOrNull(newRowIndex) ?: ""
                    }

                    val targetColIndex = when {
                        newChannelName == "Skróty" -> 0  // Moving TO Skróty: always go to shortcut (NO CategoryIcon)
                        newChannelName == "Skróty v2 Moje" -> 0  // Moving TO Skróty v2: go to first shortcut
                        currentChannelName == "Skróty" -> -1  // Moving FROM Skróty: go to CategoryIcon of channel above
                        currentChannelName == "Skróty v2 Moje" -> -1  // Moving FROM Skróty v2: go to CategoryIcon
                        else -> if (focusedColIndex == -1) -1 else 0  // Standard: preserve type
                    }
                    onChannelContentFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                }
                focusedColIndex == -1 -> {
                    // From CategoryIcon of first channel → return to menu
                    coroutineScope.launch {
                        channels.forEachIndexed { rowIndex, _ ->
                            lazyListStates[rowIndex]?.let { state ->
                                if (state.firstVisibleItemIndex > 0) {
                                    state.animateScrollToItem(index = 0, scrollOffset = 0)
                                }
                            }
                        }
                    }
                    onReturnToMenu()
                }
                else -> {
                    // From content of first channel → CategoryIcon (or menu if Skróty)
                    val firstChannelName = channels.getOrNull(0) ?: ""
                    if (firstChannelName == "Skróty") {
                        // Skróty has NO CategoryIcon → return to menu
                        onReturnToMenu()
                    } else {
                        // Standard channel → go to CategoryIcon
                        onChannelContentFocusChange(0, -1)
                        channelFocusRequesters[Pair(0, -1)]?.requestFocus()
                    }
                }
            }
            return true
        }

        Key.DirectionDown -> {
            // Faza 5: Auto-collapse when going DOWN from "ZAPLANOWANE" to "Do obejrzenia"
            val currentChannel = channels.getOrNull(focusedRowIndex)
            if (isNagraniaExpanded && currentChannel == "ZAPLANOWANE") {
                Log.d("MOJE_DEBUG", "AUTO-COLLAPSE: DOWN from ZAPLANOWANE → collapse and focus Do obejrzenia")
                onToggleExpansion?.invoke() // Collapse
                // After collapse, "Do obejrzenia" will be at row=2
                kotlinx.coroutines.GlobalScope.launch {
                    kotlinx.coroutines.delay(50) // Wait for channel list to update
                    val targetColIndex = if (focusedColIndex == -1) -1 else 0
                    onChannelContentFocusChange(2, targetColIndex)
                    channelFocusRequesters[Pair(2, targetColIndex)]?.requestFocus()
                }
                return true
            }

            if (focusedRowIndex < channels.size - 1) {
                // Move down one channel
                var newRowIndex = focusedRowIndex + 1
                var newChannelName = channels.getOrNull(newRowIndex) ?: ""
                val currentChannelName = channels.getOrNull(focusedRowIndex) ?: ""

                // Skip header if we're moving to it (headers are not focusable)
                if (newChannelName.startsWith("[HEADER")) {
                    newRowIndex += 1
                    newChannelName = channels.getOrNull(newRowIndex) ?: ""
                }

                val targetColIndex = when {
                    newChannelName == "Skróty" -> 0  // Moving TO Skróty: always go to shortcut (NO CategoryIcon)
                    newChannelName == "Skróty v2 Moje" -> 0  // Moving TO Skróty v2: go to first shortcut
                    currentChannelName == "Skróty" -> -1  // Moving FROM Skróty: go to CategoryIcon of channel below
                    currentChannelName == "Skróty v2 Moje" -> -1  // Moving FROM Skróty v2: go to CategoryIcon
                    else -> if (focusedColIndex == -1) -1 else 0  // Standard: preserve type
                }
                onChannelContentFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }

        Key.DirectionLeft -> {
            val currentChannel = channels.getOrNull(focusedRowIndex)

            // Special handling for "Skróty" - only 1 item, nowhere to go
            if (currentChannel == "Skróty" && focusedColIndex == 0) {
                return true  // Consume event, do nothing (single shortcut, no CategoryIcon)
            }

            // Special handling for "Skróty v2 Moje" - 4 shortcuts, move between them
            if (currentChannel == "Skróty v2 Moje") {
                when {
                    focusedColIndex > 0 -> {
                        // Move to previous shortcut
                        val newColIndex = focusedColIndex - 1
                        onChannelContentFocusChange(focusedRowIndex, newColIndex)
                        channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                    }
                    focusedColIndex == 0 -> {
                        // Already at first shortcut - do nothing (no CategoryIcon for shortcuts)
                        return true
                    }
                }
                return true
            }

            when {
                focusedColIndex == -1 -> {
                    // On CategoryIcon - do nothing
                    return true
                }
                focusedColIndex == 0 -> {
                    val lazyListState = lazyListStates[focusedRowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        // Scroll left
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                        }
                    } else {
                        // Can't scroll left → go to CategoryIcon
                        onChannelContentFocusChange(focusedRowIndex, -1)
                        channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                    }
                    return true
                }
                else -> return true
            }
        }

        Key.DirectionRight -> {
            val currentChannel = channels.getOrNull(focusedRowIndex)

            // Special handling for "Skróty" - only 1 item, nowhere to go
            if (currentChannel == "Skróty" && focusedColIndex == 0) {
                return true  // Consume event, do nothing (single shortcut)
            }

            // Special handling for "Skróty v2 Moje" - 4 shortcuts, move between them
            if (currentChannel == "Skróty v2 Moje") {
                when {
                    focusedColIndex < 3 -> {
                        // Move to next shortcut (max colIndex is 3 for 4 shortcuts)
                        val newColIndex = focusedColIndex + 1
                        onChannelContentFocusChange(focusedRowIndex, newColIndex)
                        channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                    }
                    focusedColIndex == 3 -> {
                        // Already at last shortcut - do nothing
                        return true
                    }
                }
                return true
            }

            when {
                focusedColIndex == -1 -> {
                    // From CategoryIcon → go to content
                    onChannelContentFocusChange(focusedRowIndex, 0)
                    channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                    return true
                }
                focusedColIndex == 0 -> {
                    // Scroll right
                    val lazyListState = lazyListStates[focusedRowIndex]
                    val channelName = channels.getOrNull(focusedRowIndex)
                    val rowContent = gridContent[channelName] ?: emptyList()
                    val maxScrollPosition = rowContent.size + 8 - 1

                    if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxScrollPosition) {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                        }
                    }
                    return true
                }
                else -> return true
            }
        }

        else -> return false
    }
}

fun handleAplikacjeChannelsNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit
): Boolean {
    android.util.Log.d("APLIKACJE_NAV", "handleAplikacjeChannelsNavigation: key=${event.key}, focusedRow=$focusedRowIndex, focusedCol=$focusedColIndex")
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            android.util.Log.d("APLIKACJE_NAV", "UP pressed: focusedRow=$focusedRowIndex, focusedCol=$focusedColIndex")
            val currentChannelName = channels.getOrNull(focusedRowIndex) ?: ""

            when {
                focusedRowIndex > 0 -> {
                    // Move up within channels
                    val newRowIndex = focusedRowIndex - 1
                    val newChannelName = channels.getOrNull(newRowIndex) ?: ""

                    val targetColIndex = when {
                        // Moving TO Skróty v2: always go to first shortcut
                        newChannelName == "Skróty v2" -> 0
                        // Moving FROM Skróty v2 to normal channel: go to CategoryIcon or content
                        currentChannelName == "Skróty v2" -> if (focusedColIndex == 0) -1 else 0
                        // Normal navigation: preserve type (CategoryIcon -> CategoryIcon, content -> content)
                        else -> if (focusedColIndex == -1) -1 else 0
                    }

                    onChannelContentFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                }
                focusedRowIndex == 0 -> {
                    // From first channel - return to menu
                    val isAppIconsChannel = currentChannelName == "Aplikacje" || currentChannelName == "Ostatnio używane"

                    // Return to menu if:
                    // 1. Skróty v2 (no CategoryIcon) - any colIndex
                    // 2. Normal channels - from CategoryIcon (-1)
                    // 3. App icons channels - from content (>= 0), because app icons ARE the main content
                    if (currentChannelName == "Skróty v2" || focusedColIndex == -1 ||
                        (isAppIconsChannel && focusedColIndex >= 0)) {
                        onReturnToMenu()
                    }
                }
            }
            return true
        }

        Key.DirectionDown -> {
            val currentChannelName = channels.getOrNull(focusedRowIndex) ?: ""

            when {
                focusedRowIndex < channels.size - 1 -> {
                    // Move down within channels
                    val newRowIndex = focusedRowIndex + 1
                    val newChannelName = channels.getOrNull(newRowIndex) ?: ""

                    val targetColIndex = when {
                        // Moving TO Skróty v2: always go to first shortcut
                        newChannelName == "Skróty v2" -> 0
                        // Moving FROM Skróty v2 to normal channel: go to CategoryIcon or content
                        currentChannelName == "Skróty v2" -> if (focusedColIndex == 0) -1 else 0
                        // Normal navigation: preserve type (CategoryIcon -> CategoryIcon, content -> content)
                        else -> if (focusedColIndex == -1) -1 else 0
                    }

                    onChannelContentFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                }
            }
            return true
        }

        Key.DirectionLeft -> {
            val channelName = channels.getOrNull(focusedRowIndex) ?: ""

            // Shortcuts v2: direct focus navigation (NO scrolling)
            if (channelName == "Skróty v2") {
                if (focusedColIndex > 0) {
                    val newColIndex = focusedColIndex - 1
                    onChannelContentFocusChange(focusedRowIndex, newColIndex)
                    channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                }
                return true
            }

            when {
                focusedColIndex == -1 -> {
                    // Already on CategoryIcon - do nothing
                    return true
                }
                focusedColIndex == 0 -> {
                    // On content - scroll left or go to CategoryIcon
                    val lazyListState = lazyListStates[focusedRowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                        }
                    } else {
                        onChannelContentFocusChange(focusedRowIndex, -1)
                        channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                    }
                }
            }
            return true
        }

        Key.DirectionRight -> {
            val channelName = channels.getOrNull(focusedRowIndex) ?: ""

            // Shortcuts v2: direct focus navigation (NO scrolling, items 0-3)
            if (channelName == "Skróty v2") {
                if (focusedColIndex < 3) { // 4 shortcuts = colIndex 0-3
                    val newColIndex = focusedColIndex + 1
                    onChannelContentFocusChange(focusedRowIndex, newColIndex)
                    channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                }
                return true
            }

            when {
                focusedColIndex == -1 -> {
                    // From CategoryIcon to content
                    onChannelContentFocusChange(focusedRowIndex, 0)
                    channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                }
                focusedColIndex == 0 -> {
                    // On content - scroll right
                    val lazyListState = lazyListStates[focusedRowIndex]
                    val rowContent = gridContent[channelName] ?: emptyList()
                    val maxIndex = rowContent.size - 1

                    if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                        }
                    }
                }
            }
            return true
        }

        else -> return false
    }
}

fun handleOdkrywajNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit
): Boolean {
    android.util.Log.d("ODKRYWAJ_NAV", "handleOdkrywajNavigation: key=${event.key}, focusedRow=$focusedRowIndex, focusedCol=$focusedColIndex")
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            android.util.Log.d("ODKRYWAJ_NAV", "UP pressed: focusedRow=$focusedRowIndex, focusedCol=$focusedColIndex")
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to first slide (0, 0)
                onFocusChange(0, 0)
                channelFocusRequesters[Pair(0, 0)]?.requestFocus()
                return true
            } else if (focusedRowIndex > 0) {
                val newRowIndex = focusedRowIndex - 1
                // Row 0 (slider-max) and row 1 (shortcuts) have no CategoryIcon - always go to col=0
                val targetColIndex = if (newRowIndex <= 1) {
                    0 // No CategoryIcon, go to content
                } else {
                    if (focusedColIndex == -1) -1 else 0 // Preserve type for rows with CategoryIcon
                }
                onFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            } else {
                // From row 0 (slider-max) - go back to menu
                coroutineScope.launch {
                    channels.forEachIndexed { rowIndex, _ ->
                        val lazyListState = lazyListStates[rowIndex]
                        if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                            lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                        }
                    }
                }
                onReturnToMenu()
            }
            return true
        }

        Key.DirectionDown -> {
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to first slide (0, 0)
                onFocusChange(0, 0)
                channelFocusRequesters[Pair(0, 0)]?.requestFocus()
                return true
            } else if (focusedRowIndex < channels.size - 1) {
                val newRowIndex = focusedRowIndex + 1
                // Row 0 (slider-max) and row 1 (shortcuts) have no CategoryIcon - always go to col=0
                val targetColIndex = if (newRowIndex <= 1) {
                    0 // No CategoryIcon, go to content
                } else {
                    if (focusedColIndex == -1) -1 else 0 // Preserve type for rows with CategoryIcon
                }
                onFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }

        Key.DirectionLeft -> {
            if (focusedColIndex == -2) return false

            // Row 0 (slider-max) has scrolling, row 1 (shortcuts) has fixed position
            if (focusedRowIndex == 0) {
                // Slider-max: use scroll
                val lazyListState = lazyListStates[focusedRowIndex]
                if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                    }
                }
                return true
            } else if (focusedRowIndex == 1) {
                // Shortcuts: direct focus navigation (NO scrolling, items 0-5)
                if (focusedColIndex > 0) {
                    val newColIndex = focusedColIndex - 1
                    onFocusChange(focusedRowIndex, newColIndex)
                    channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                }
                return true
            }

            // For rows 2+: navigation like MOJE (scroll first, then CategoryIcon)
            if (focusedColIndex == -1) {
                // Already on CategoryIcon - do nothing
                return true
            } else if (focusedColIndex == 0) {
                // From content - try to scroll left first
                val lazyListState = lazyListStates[focusedRowIndex]
                if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                    // Can scroll - scroll left by 1 position
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                    }
                } else {
                    // Can't scroll left - go to CategoryIcon
                    onFocusChange(focusedRowIndex, -1)
                    channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                }
            }
            return true
        }

        Key.DirectionRight -> {
            if (focusedColIndex == -2) return false

            // Row 0 (slider-max) has scrolling, row 1 (shortcuts) has fixed position
            if (focusedRowIndex == 0) {
                // Slider-max: use scroll
                val lazyListState = lazyListStates[focusedRowIndex]
                val channelName = channels.getOrNull(focusedRowIndex) ?: ""
                val rowContent = gridContent[channelName] ?: emptyList()
                val maxIndex = rowContent.size - 1

                if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                    }
                }
                return true
            } else if (focusedRowIndex == 1) {
                // Shortcuts: direct focus navigation (NO scrolling, items 0-5, item 5 = plus button)
                if (focusedColIndex < 5) {
                    val newColIndex = focusedColIndex + 1
                    onFocusChange(focusedRowIndex, newColIndex)
                    channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                }
                return true
            }

            // For rows 2+: normal navigation with CategoryIcon
            if (focusedColIndex == -1) {
                // From CategoryIcon, go to content (col 0)
                onFocusChange(focusedRowIndex, 0)
                channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
            } else if (focusedColIndex == 0) {
                // From content, try to scroll right
                val lazyListState = lazyListStates[focusedRowIndex]
                val channelName = channels.getOrNull(focusedRowIndex) ?: ""
                val rowContent = gridContent[channelName] ?: emptyList()
                val maxIndex = rowContent.size - 1

                if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                    }
                }
            }
            return true
        }

        else -> return false
    }
}

// Shortcuts v2 data for TELEWIZJA section
val telewizjaShortcutsV2 = listOf(
    ShortcutItem("1", "Program telewizyjny", ShortcutIcon.MaterialIcon("add")),
    ShortcutItem("2", "Moja lista kanałów", ShortcutIcon.MaterialIcon("search")),
    ShortcutItem("3", "Lista kanałów", ShortcutIcon.MaterialIcon("star")),
    ShortcutItem("4", "Nagrania", ShortcutIcon.MaterialIcon("favorite"))
)

// Helper function to find next focusable row (skipping headers)
private fun getNextFocusableRowIndex(
    currentRowIndex: Int,
    direction: Int, // -1 for UP, +1 for DOWN
    channels: List<String>
): Int? {
    var candidateRow = currentRowIndex + direction

    while (candidateRow >= 0 && candidateRow < channels.size) {
        val channelName = channels.getOrNull(candidateRow) ?: return null

        // Check if this is NOT a header
        if (!channelName.startsWith("[HEADER]")) {
            return candidateRow
        }

        // Continue searching in the same direction
        candidateRow += direction
    }

    return null // No focusable row found
}

// Navigation handler for TELEWIZJA channels (copied from ODKRYWAJ)
fun handleTelewizjaNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    channelTypes: Map<String, String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    onReturnToMenu: () -> Unit
): Boolean {
    android.util.Log.d("TELEWIZJA_NAV", "handleTelewizjaNavigation: key=${event.key}, focusedRow=$focusedRowIndex, focusedCol=$focusedColIndex")
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            android.util.Log.d("TELEWIZJA_NAV", "UP pressed: focusedRow=$focusedRowIndex, focusedCol=$focusedColIndex")
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to first NON-HEADER channel
                val firstFocusableRow = getNextFocusableRowIndex(-1, 1, channels) ?: 0
                val firstChannelName = channels.getOrNull(firstFocusableRow) ?: ""
                val firstChannelType = channelTypes[firstChannelName] ?: "horizontal"
                // collection-slider has no CategoryIcon, go to content (col=0)
                // Other types: go to CategoryIcon (col=-1) if coming from menu
                val targetColIndex = if (firstChannelType == "collection-slider") 0 else -1
                onFocusChange(firstFocusableRow, targetColIndex)
                channelFocusRequesters[Pair(firstFocusableRow, targetColIndex)]?.requestFocus()
                return true
            }

            // Try to find next focusable row above (skipping headers)
            val newRowIndex = getNextFocusableRowIndex(focusedRowIndex, -1, channels)

            if (newRowIndex != null) {
                val newChannelName = channels.getOrNull(newRowIndex) ?: ""
                val newChannelType = channelTypes[newChannelName] ?: "horizontal"
                val currentChannelName = channels.getOrNull(focusedRowIndex) ?: ""

                // Determine target column based on channel type
                val targetColIndex = when {
                    // Moving TO shortcuts-v2: always go to first shortcut
                    newChannelName == "Skróty v2" -> 0
                    // Moving TO collection-slider: always go to content (NO CategoryIcon)
                    newChannelType == "collection-slider" -> 0
                    // Moving FROM shortcuts-v2 to normal channel (with CategoryIcon)
                    currentChannelName == "Skróty v2" -> if (focusedColIndex == 0) -1 else 0
                    // Other types have CategoryIcon - preserve focus type
                    else -> if (focusedColIndex == -1) -1 else 0
                }
                onFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            } else {
                // From first row - go back to menu
                coroutineScope.launch {
                    channels.forEachIndexed { rowIndex, _ ->
                        val lazyListState = lazyListStates[rowIndex]
                        if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                            lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                        }
                    }
                }
                onReturnToMenu()
            }
            return true
        }

        Key.DirectionDown -> {
            if (focusedColIndex == -2) {
                // First movement from "no focus" - go to first NON-HEADER channel
                val firstFocusableRow = getNextFocusableRowIndex(-1, 1, channels) ?: 0
                val firstChannelName = channels.getOrNull(firstFocusableRow) ?: ""
                val firstChannelType = channelTypes[firstChannelName] ?: "horizontal"
                // collection-slider has no CategoryIcon, go to content (col=0)
                // Other types: go to CategoryIcon (col=-1) if coming from menu
                val targetColIndex = if (firstChannelType == "collection-slider") 0 else -1
                onFocusChange(firstFocusableRow, targetColIndex)
                channelFocusRequesters[Pair(firstFocusableRow, targetColIndex)]?.requestFocus()
                return true
            }

            // Try to find next focusable row below (skipping headers)
            val newRowIndex = getNextFocusableRowIndex(focusedRowIndex, 1, channels)

            if (newRowIndex != null) {
                val newChannelName = channels.getOrNull(newRowIndex) ?: ""
                val newChannelType = channelTypes[newChannelName] ?: "horizontal"
                val currentChannelName = channels.getOrNull(focusedRowIndex) ?: ""

                // Determine target column based on channel type
                val targetColIndex = when {
                    // Moving TO shortcuts-v2: always go to first shortcut
                    newChannelName == "Skróty v2" -> 0
                    // Moving TO collection-slider: always go to content (NO CategoryIcon)
                    newChannelType == "collection-slider" -> 0
                    // Moving FROM shortcuts-v2 to normal channel (with CategoryIcon)
                    currentChannelName == "Skróty v2" -> if (focusedColIndex == 0) -1 else 0
                    // Other types have CategoryIcon - preserve focus type
                    else -> if (focusedColIndex == -1) -1 else 0
                }
                onFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }

        Key.DirectionLeft -> {
            if (focusedColIndex == -2) return false

            val channelName = channels.getOrNull(focusedRowIndex) ?: ""
            val channelType = channelTypes[channelName] ?: "horizontal"

            when (channelType) {
                "shortcuts-v2" -> {
                    // Shortcuts v2: direct focus navigation (NO scrolling, items 0-3)
                    if (focusedColIndex > 0) {
                        val newColIndex = focusedColIndex - 1
                        onFocusChange(focusedRowIndex, newColIndex)
                        channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                    }
                    return true
                }
                "collection-slider" -> {
                    // collection-slider: scrolling only (no CategoryIcon)
                    val lazyListState = lazyListStates[focusedRowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        coroutineScope.launch {
                            val newIndex = lazyListState.firstVisibleItemIndex - 1
                            lazyListState.animateScrollToItem(newIndex)
                            // Czekaj na zakończenie animacji i ustaw fokus na nowym pierwszym widocznym elemencie
                            delay(50)
                            channelFocusRequesters[Pair(focusedRowIndex, newIndex)]?.requestFocus()
                        }
                    }
                    return true
                }
                else -> {
                    // Check channel type to use correct navigation model
                    val channelType = channelTypes[channelName] ?: "horizontal"

                    if (channelType == "app-icons") {
                        // app-icons: DIRECT FOCUS MODEL (focusedColIndex = 0,1,2,3...)
                        if (focusedColIndex == -1) {
                            // Already on CategoryIcon - do nothing
                            return true
                        } else if (focusedColIndex > 0) {
                            // From content - move left to previous item
                            val newColIndex = focusedColIndex - 1
                            onFocusChange(focusedRowIndex, newColIndex)
                            channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()

                            // Also scroll if needed
                            val lazyListState = lazyListStates[focusedRowIndex]
                            if (lazyListState != null) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(newColIndex)
                                }
                            }
                        } else if (focusedColIndex == 0) {
                            // From first item - go to CategoryIcon
                            onFocusChange(focusedRowIndex, -1)
                            channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                        }
                    } else {
                        // horizontal: SCROLLING MODEL (focusedColIndex zawsze 0)
                        if (focusedColIndex == -1) {
                            // Already on CategoryIcon - do nothing
                            return true
                        } else if (focusedColIndex == 0) {
                            // Scroll left if possible, or go to CategoryIcon
                            val lazyListState = lazyListStates[focusedRowIndex]
                            if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                                coroutineScope.launch {
                                    val newIndex = lazyListState.firstVisibleItemIndex - 1
                                    lazyListState.animateScrollToItem(newIndex)
                                    // Czekaj na zakończenie animacji i ustaw fokus na nowym pierwszym widocznym elemencie
                                    delay(50)
                                    channelFocusRequesters[Pair(focusedRowIndex, newIndex)]?.requestFocus()
                                }
                            } else {
                                // From first item - go to CategoryIcon
                                onFocusChange(focusedRowIndex, -1)
                                channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                            }
                        }
                    }
                    return true
                }
            }
        }

        Key.DirectionRight -> {
            if (focusedColIndex == -2) return false

            val channelName = channels.getOrNull(focusedRowIndex) ?: ""
            val channelType = channelTypes[channelName] ?: "horizontal"

            when (channelType) {
                "shortcuts-v2" -> {
                    // Shortcuts v2: direct focus navigation (NO scrolling, items 0-3)
                    if (focusedColIndex < 3) { // 4 shortcuts = colIndex 0-3
                        val newColIndex = focusedColIndex + 1
                        onFocusChange(focusedRowIndex, newColIndex)
                        channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                    }
                    return true
                }
                "collection-slider" -> {
                    // collection-slider: scrolling only (no CategoryIcon)
                    val lazyListState = lazyListStates[focusedRowIndex]
                    val rowContent = gridContent[channelName] ?: emptyList()
                    val maxIndex = rowContent.size - 1

                    if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                        coroutineScope.launch {
                            val newIndex = lazyListState.firstVisibleItemIndex + 1
                            lazyListState.animateScrollToItem(newIndex)
                            // Czekaj na zakończenie animacji i ustaw fokus na nowym pierwszym widocznym elemencie
                            delay(50)
                            channelFocusRequesters[Pair(focusedRowIndex, newIndex)]?.requestFocus()
                        }
                    }
                    return true
                }
                else -> {
                    // Check channel type to use correct navigation model
                    val channelType = channelTypes[channelName] ?: "horizontal"

                    if (channelType == "app-icons") {
                        // app-icons: DIRECT FOCUS MODEL (focusedColIndex = 0,1,2,3...)
                        if (focusedColIndex == -1) {
                            // From CategoryIcon, go to content (col 0)
                            onFocusChange(focusedRowIndex, 0)
                            channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                        } else if (focusedColIndex >= 0) {
                            // From content, try to move right
                            val lazyListState = lazyListStates[focusedRowIndex]

                            val maxIndex = when {
                                appIconsData.containsKey(channelName) -> {
                                    val channelList = appIconsData[channelName] ?: emptyList()
                                    channelList.size - 1
                                }
                                else -> {
                                    val rowContent = gridContent[channelName] ?: emptyList()
                                    rowContent.size - 1
                                }
                            }

                            if (focusedColIndex < maxIndex) {
                                val newColIndex = focusedColIndex + 1
                                onFocusChange(focusedRowIndex, newColIndex)
                                channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()

                                // Also scroll if needed
                                if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(newColIndex)
                                    }
                                }
                            }
                        }
                    } else {
                        // horizontal: SCROLLING MODEL (focusedColIndex zawsze 0)
                        if (focusedColIndex == -1) {
                            // From CategoryIcon, go to content (col 0)
                            onFocusChange(focusedRowIndex, 0)
                            channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                        } else if (focusedColIndex == 0) {
                            // Scroll right if possible (scrolling model)
                            val lazyListState = lazyListStates[focusedRowIndex]

                            val maxIndex = when {
                                appIconsData.containsKey(channelName) -> {
                                    val channelList = appIconsData[channelName] ?: emptyList()
                                    channelList.size - 1
                                }
                                else -> {
                                    val rowContent = gridContent[channelName] ?: emptyList()
                                    rowContent.size - 1
                                }
                            }

                            if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                                coroutineScope.launch {
                                    val newIndex = lazyListState.firstVisibleItemIndex + 1
                                    lazyListState.animateScrollToItem(newIndex)
                                    // Czekaj na zakończenie animacji i ustaw fokus na nowym pierwszym widocznym elemencie
                                    delay(50)
                                    channelFocusRequesters[Pair(focusedRowIndex, newIndex)]?.requestFocus()
                                }
                            }
                        }
                    }
                    return true
                }
            }
        }

        else -> return false
    }
}

// Focusable content card component - styled like shortcuts
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FocusableContentCard(
    title: String,
    onReturnToMenu: () -> Unit,
    shouldAutoFocus: Boolean = false // Control auto-focus behavior
) {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp
    
    val focusRequester = remember { FocusRequester() }
    // Force focus state when shouldAutoFocus is true
    var isFocused by remember { mutableStateOf(false) }
    
    // Set focus state when shouldAutoFocus changes
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            isFocused = true // Set visual focus state
            // Also try FocusRequester for proper focus management
            // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
            focusRequester.requestFocus()
        } else {
            isFocused = false // Reset when not auto-focusing
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && isFocused) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onReturnToMenu()
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // Styled like ShortcutCard
        val cardWidth = sx(210)
        val cardHeight = sy(279)
        val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
        
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .border(
                    width = (6 * sx(1).value / 1.dp.value).dp, // Same border width as shortcuts
                    color = borderColor,
                    shape = RoundedCornerShape(sx(12))
                )
                .clip(RoundedCornerShape(sx(12)))
                .background(Color(0x33000000)) // Same background as shortcuts
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { focusState ->
                    // Update focus state when system focus changes
                    if (!shouldAutoFocus) { // Only update if not in manual mode
                        isFocused = focusState.isFocused
                    }
                }
        ) {
            // Content background - darker when focused (like shortcuts)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isFocused) Color(0x4D000000) else Color.Transparent
                    )
            ) {
                // Icon area - placeholder circle like in shortcuts
                Box(
                    modifier = Modifier
                        .size(sx(85)) // Same as shortcut icon size
                        .align(Alignment.Center)
                        .offset(y = sy(-45))
                        .background(
                            color = Color(0xFFEEEEEE),
                            shape = RoundedCornerShape(sx(42))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title.take(2), // First 2 letters as icon
                        color = Color(0xFF48227C),
                        fontSize = sy(24).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Text at bottom (like shortcuts)
                Text(
                    text = "$title Content",
                    color = Color(0xFFEEEEEE),
                    fontSize = sy(16).value.sp, // Shortcut font size
                    fontWeight = FontWeight.W500,
                    textAlign = TextAlign.Center,
                    lineHeight = sy(20).value.sp,
                    letterSpacing = 0.32.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = sx(12), vertical = sy(16))
                        .fillMaxWidth()
                )
            }
        }
    }
}

// APLIKACJE section functions

private fun calculateAplikacjeChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    val channelName = channels.getOrNull(rowIndex) ?: ""
    val isShortcuts = channelName == "Skróty"

    val focusedChannelName = channels.getOrNull(focusedRowIndex) ?: ""
    val focusedIsShortcuts = focusedChannelName == "Skróty"
    val focusedIsShortcutsV2 = focusedChannelName == "Skróty v2"
    val focusedIsAppIcons = focusedChannelName == "Aplikacje" || focusedChannelName == "Ostatnio używane"

    return when {
        rowIndex == focusedRowIndex -> sy(APLIKACJE_FIXED_FOCUS_Y)
        rowIndex < focusedRowIndex -> {
            val extraSpacing = if (!focusedIsAppIcons && !focusedIsShortcuts && !focusedIsShortcutsV2 && focusedColIndex >= 0) APLIKACJE_CONTENT_FOCUS_EXTRA_SPACING else 0
            var cumulativeHeight = APLIKACJE_FIXED_FOCUS_Y
            for (i in rowIndex until focusedRowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenIsShortcuts = betweenChannelName == "Skróty"
                val betweenIsShortcutsV2 = betweenChannelName == "Skróty v2"
                val betweenIsAppIcons = betweenChannelName == "Aplikacje" || betweenChannelName == "Ostatnio używane"
                cumulativeHeight -= when {
                    betweenIsShortcuts -> APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT
                    betweenIsShortcutsV2 -> APLIKACJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    betweenIsAppIcons -> APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT
                    else -> APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight - extraSpacing)
        }
        rowIndex > focusedRowIndex -> {
            val focusedChannelExpansion = if (focusedColIndex >= 0) {
                when {
                    focusedIsShortcuts -> APLIKACJE_SHORTCUTS_EXPANDED_ROW_HEIGHT
                    focusedIsShortcutsV2 -> APLIKACJE_SHORTCUTS_V2_EXPANDED_ROW_HEIGHT
                    focusedIsAppIcons -> APLIKACJE_APP_ICONS_EXPANDED_ROW_HEIGHT
                    else -> APLIKACJE_HORIZONTAL_EXPANDED_ROW_HEIGHT
                }
            } else {
                when {
                    focusedIsShortcuts -> APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT
                    focusedIsShortcutsV2 -> APLIKACJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    focusedIsAppIcons -> APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT
                    else -> APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            var cumulativeHeight = APLIKACJE_FIXED_FOCUS_Y + focusedChannelExpansion
            for (i in (focusedRowIndex + 1) until rowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenIsShortcuts = betweenChannelName == "Skróty"
                val betweenIsShortcutsV2 = betweenChannelName == "Skróty v2"
                val betweenIsAppIcons = betweenChannelName == "Aplikacje" || betweenChannelName == "Ostatnio używane"
                cumulativeHeight += when {
                    betweenIsShortcuts -> APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT
                    betweenIsShortcutsV2 -> APLIKACJE_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    betweenIsAppIcons -> APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT
                    else -> APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight)
        }
        else -> sy(140 + rowIndex * APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT)
    }
}

private fun calculateOdkrywajChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    channelTypes: Map<String, String>, // "slider-max", "shortcuts", "vertical", "horizontal"
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    val channelName = channels.getOrNull(rowIndex) ?: ""
    val channelType = channelTypes[channelName] ?: "horizontal"

    val focusedChannelName = channels.getOrNull(focusedRowIndex) ?: ""
    val focusedChannelType = channelTypes[focusedChannelName] ?: "horizontal"

    return when {
        rowIndex == focusedRowIndex -> sy(ODKRYWAJ_FIXED_FOCUS_Y)
        rowIndex < focusedRowIndex -> {
            // Apply extra spacing: 40px for shortcuts, 100px for other channels
            val extraSpacing = if (focusedChannelType == "shortcuts" && focusedColIndex >= 0) {
                40 // Smaller spacing for shortcuts (only add spacing from top)
            } else if (focusedChannelType != "shortcuts" && focusedColIndex >= 0) {
                ODKRYWAJ_CONTENT_FOCUS_EXTRA_SPACING // 100px for other channels
            } else 0

            var cumulativeHeight = ODKRYWAJ_FIXED_FOCUS_Y
            for (i in rowIndex until focusedRowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenType = channelTypes[betweenChannelName] ?: "horizontal"
                cumulativeHeight -= when (betweenType) {
                    "slider-max" -> ODKRYWAJ_SLIDER_MAX_NORMAL_ROW_HEIGHT
                    "shortcuts" -> ODKRYWAJ_SHORTCUTS_NORMAL_ROW_HEIGHT
                    "top10" -> ODKRYWAJ_TOP10_NORMAL_ROW_HEIGHT
                    "collection-slider" -> ODKRYWAJ_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (betweenChannelName == "Nowe filmy") {
                            ODKRYWAJ_VERTICAL_VOD_NORMAL_ROW_HEIGHT
                        } else {
                            ODKRYWAJ_VERTICAL_NORMAL_ROW_HEIGHT
                        }
                    }
                    else -> ODKRYWAJ_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight - extraSpacing)
        }
        rowIndex > focusedRowIndex -> {
            val focusedChannelExpansion = if (focusedColIndex >= 0) {
                when (focusedChannelType) {
                    "slider-max" -> ODKRYWAJ_SLIDER_MAX_EXPANDED_ROW_HEIGHT
                    "shortcuts" -> ODKRYWAJ_SHORTCUTS_EXPANDED_ROW_HEIGHT
                    "top10" -> ODKRYWAJ_TOP10_EXPANDED_ROW_HEIGHT
                    "collection-slider" -> ODKRYWAJ_COLLECTION_SLIDER_EXPANDED_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (focusedChannelName == "Nowe filmy") {
                            ODKRYWAJ_VERTICAL_VOD_EXPANDED_ROW_HEIGHT
                        } else {
                            ODKRYWAJ_VERTICAL_EXPANDED_ROW_HEIGHT
                        }
                    }
                    else -> ODKRYWAJ_HORIZONTAL_EXPANDED_ROW_HEIGHT
                }
            } else {
                when (focusedChannelType) {
                    "slider-max" -> ODKRYWAJ_SLIDER_MAX_NORMAL_ROW_HEIGHT
                    "shortcuts" -> ODKRYWAJ_SHORTCUTS_EXPANDED_ROW_HEIGHT
                    "top10" -> ODKRYWAJ_TOP10_NORMAL_ROW_HEIGHT
                    "collection-slider" -> ODKRYWAJ_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (focusedChannelName == "Nowe filmy") {
                            ODKRYWAJ_VERTICAL_VOD_NORMAL_ROW_HEIGHT
                        } else {
                            ODKRYWAJ_VERTICAL_NORMAL_ROW_HEIGHT
                        }
                    }
                    else -> ODKRYWAJ_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }

            var cumulativeHeight = ODKRYWAJ_FIXED_FOCUS_Y + focusedChannelExpansion

            for (i in (focusedRowIndex + 1) until rowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenType = channelTypes[betweenChannelName] ?: "horizontal"
                cumulativeHeight += when (betweenType) {
                    "slider-max" -> ODKRYWAJ_SLIDER_MAX_NORMAL_ROW_HEIGHT
                    "shortcuts" -> ODKRYWAJ_SHORTCUTS_NORMAL_ROW_HEIGHT
                    "top10" -> ODKRYWAJ_TOP10_NORMAL_ROW_HEIGHT
                    "collection-slider" -> ODKRYWAJ_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (betweenChannelName == "Nowe filmy") {
                            ODKRYWAJ_VERTICAL_VOD_NORMAL_ROW_HEIGHT
                        } else {
                            ODKRYWAJ_VERTICAL_NORMAL_ROW_HEIGHT
                        }
                    }
                    else -> ODKRYWAJ_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight)
        }
        else -> sy(140 + rowIndex * ODKRYWAJ_HORIZONTAL_NORMAL_ROW_HEIGHT)
    }
}

// Helper function to calculate Y position for TELEWIZJA channels (copied from ODKRYWAJ)
private fun calculateTelewizjaChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    channelTypes: Map<String, String>, // "slider-max", "shortcuts", "vertical", "horizontal"
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    val channelName = channels.getOrNull(rowIndex) ?: ""
    val channelType = channelTypes[channelName] ?: "horizontal"

    // ✅ POCZĄTKOWA POZYCJA (focusedColIndex == -2, menu focused)
    // Row 0 zaczyna się 60px pod top menu (120px menu + 60px spacing = Y:180px)
    if (focusedColIndex == -2) {
        var cumulativeY = 180 // Pierwszy element 60px pod menu
        for (i in 0 until rowIndex) {
            val prevChannelName = channels.getOrNull(i) ?: ""
            val prevType = channelTypes[prevChannelName] ?: "horizontal"
            cumulativeY += when (prevType) {
                "header" -> TELEWIZJA_HEADER_ROW_HEIGHT
                "collection-slider" -> TELEWIZJA_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                "shortcuts-v2" -> TELEWIZJA_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                else -> TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT
            }
        }
        return sy(cumulativeY)
    }

    val focusedChannelName = channels.getOrNull(focusedRowIndex) ?: ""
    val focusedChannelType = channelTypes[focusedChannelName] ?: "horizontal"

    // ✅ NORMALNE SCROLLOWANIE: Wszystko scrolluje razem
    // Wszystkie focusable rows na Y=270px
    return when {
        rowIndex == focusedRowIndex -> {
            // Każdy zfokusowany row -> Y = 270px
            sy(TELEWIZJA_FIXED_FOCUS_Y)
        }
        rowIndex < focusedRowIndex -> {
            // Stałe odległości między rzędami (bez extraSpacing)
            var cumulativeHeight = TELEWIZJA_FIXED_FOCUS_Y
            for (i in rowIndex until focusedRowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenType = channelTypes[betweenChannelName] ?: "horizontal"
                cumulativeHeight -= when (betweenType) {
                    "header" -> TELEWIZJA_HEADER_ROW_HEIGHT
                    "app-icons" -> TELEWIZJA_APP_ICONS_NORMAL_ROW_HEIGHT
                    "service-logos" -> TELEWIZJA_SERVICE_LOGOS_NORMAL_ROW_HEIGHT
                    "channel-logos" -> TELEWIZJA_CHANNEL_LOGOS_NORMAL_ROW_HEIGHT
                    "shortcuts" -> TELEWIZJA_SHORTCUTS_NORMAL_ROW_HEIGHT
                    "shortcuts-v2" -> TELEWIZJA_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    "top10" -> TELEWIZJA_TOP10_NORMAL_ROW_HEIGHT
                    "collection-slider" -> TELEWIZJA_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (betweenChannelName == "Nowe filmy") {
                            TELEWIZJA_VERTICAL_VOD_NORMAL_ROW_HEIGHT
                        } else {
                            TELEWIZJA_VERTICAL_NORMAL_ROW_HEIGHT
                        }
                    }
                    else -> TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight)
        }
        rowIndex > focusedRowIndex -> {
            val focusedChannelExpansion = if (focusedColIndex >= 0) {
                when (focusedChannelType) {
                    "header" -> TELEWIZJA_HEADER_ROW_HEIGHT
                    "app-icons" -> TELEWIZJA_APP_ICONS_EXPANDED_ROW_HEIGHT
                    "service-logos" -> TELEWIZJA_SERVICE_LOGOS_EXPANDED_ROW_HEIGHT
                    "channel-logos" -> TELEWIZJA_CHANNEL_LOGOS_EXPANDED_ROW_HEIGHT
                    "shortcuts" -> TELEWIZJA_SHORTCUTS_EXPANDED_ROW_HEIGHT
                    "shortcuts-v2" -> TELEWIZJA_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    "top10" -> TELEWIZJA_TOP10_EXPANDED_ROW_HEIGHT
                    "collection-slider" -> TELEWIZJA_COLLECTION_SLIDER_EXPANDED_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (focusedChannelName == "Nowe filmy") {
                            TELEWIZJA_VERTICAL_VOD_EXPANDED_ROW_HEIGHT
                        } else {
                            TELEWIZJA_VERTICAL_EXPANDED_ROW_HEIGHT
                        }
                    }
                    else -> TELEWIZJA_HORIZONTAL_EXPANDED_ROW_HEIGHT
                }
            } else {
                when (focusedChannelType) {
                    "header" -> TELEWIZJA_HEADER_ROW_HEIGHT
                    "app-icons" -> TELEWIZJA_APP_ICONS_NORMAL_ROW_HEIGHT
                    "service-logos" -> TELEWIZJA_SERVICE_LOGOS_NORMAL_ROW_HEIGHT
                    "channel-logos" -> TELEWIZJA_CHANNEL_LOGOS_NORMAL_ROW_HEIGHT
                    "shortcuts" -> TELEWIZJA_SHORTCUTS_EXPANDED_ROW_HEIGHT
                    "shortcuts-v2" -> TELEWIZJA_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    "top10" -> TELEWIZJA_TOP10_NORMAL_ROW_HEIGHT
                    "collection-slider" -> TELEWIZJA_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (focusedChannelName == "Nowe filmy") {
                            TELEWIZJA_VERTICAL_VOD_NORMAL_ROW_HEIGHT
                        } else {
                            TELEWIZJA_VERTICAL_NORMAL_ROW_HEIGHT
                        }
                    }
                    else -> TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }

            // Determine focus Y based on focused row (wszystkie na 270px)
            var cumulativeHeight = TELEWIZJA_FIXED_FOCUS_Y + focusedChannelExpansion

            for (i in (focusedRowIndex + 1) until rowIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenType = channelTypes[betweenChannelName] ?: "horizontal"
                cumulativeHeight += when (betweenType) {
                    "header" -> TELEWIZJA_HEADER_ROW_HEIGHT
                    "app-icons" -> TELEWIZJA_APP_ICONS_NORMAL_ROW_HEIGHT
                    "service-logos" -> TELEWIZJA_SERVICE_LOGOS_NORMAL_ROW_HEIGHT
                    "channel-logos" -> TELEWIZJA_CHANNEL_LOGOS_NORMAL_ROW_HEIGHT
                    "shortcuts" -> TELEWIZJA_SHORTCUTS_NORMAL_ROW_HEIGHT
                    "shortcuts-v2" -> TELEWIZJA_SHORTCUTS_V2_NORMAL_ROW_HEIGHT
                    "top10" -> TELEWIZJA_TOP10_NORMAL_ROW_HEIGHT
                    "collection-slider" -> TELEWIZJA_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT
                    "vertical" -> {
                        // Use VOD heights for Nowe filmy (VodContentCard)
                        if (betweenChannelName == "Nowe filmy") {
                            TELEWIZJA_VERTICAL_VOD_NORMAL_ROW_HEIGHT
                        } else {
                            TELEWIZJA_VERTICAL_NORMAL_ROW_HEIGHT
                        }
                    }
                    else -> TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight)
        }
        else -> {
            // Fallback: Oblicz pozycję względem pierwszego focusable
            val firstFocusableRow = getNextFocusableRowIndex(-1, 1, channels) ?: 0
            sy(TELEWIZJA_HEADER_ROW_HEIGHT + (rowIndex - firstFocusableRow) * TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT)
        }
    }
}

@Composable
fun AplikacjeChannelRowsLayout(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    shortcuts: List<ShortcutItem>,
    apps: List<AppItem>,
    recentApps: List<AppItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        channels.forEachIndexed { rowIndex, channelName ->
            val rowContent = gridContent[channelName] ?: emptyList()
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

            // Select correct apps list based on channel name
            val currentApps = when (channelName) {
                "Ostatnio używane" -> recentApps
                "Aplikacje" -> apps
                else -> apps
            }

            val targetY = calculateAplikacjeChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channels = channels,
                sy = sy
            )

            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                animationSpec = tween(durationMillis = 500),
                label = "aplikacje_channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                AplikacjeUnifiedChannelRow(
                    channel = channelName,
                    rowIndex = rowIndex,
                    rowContent = rowContent,
                    shortcuts = shortcuts,
                    apps = currentApps,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }
        }
    }
}

@Composable
fun AplikacjeUnifiedChannelRow(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    shortcuts: List<ShortcutItem>,
    apps: List<AppItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    val isShortcuts = channel == "Skróty"
    val isShortcutsV2 = channel == "Skróty v2"
    val isAppIcons = channel == "Aplikacje" || channel == "Ostatnio używane"

    var showDetailsWithDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = !isAppIcons && !isShortcuts && !isShortcutsV2 && isCurrentRow && focusedColIndex == 0

        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350) // 350ms = animation time
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (!isAppIcons && !isShortcuts && !isShortcutsV2 && isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "aplikacje_miniatures_y_offset_$rowIndex"
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = miniaturesYOffset),
            state = lazyListState,
            contentPadding = PaddingValues(
                start = sx(380),
                end = sx(20)
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isAppIcons) sx(12) else sx(20))
        ) {
            if (isShortcuts) {
                // Shortcuts row
                items(shortcuts.size) { colIndex ->
                    val isItemFocused = rowIndex == focusedRowIndex &&
                            colIndex == lazyListState.firstVisibleItemIndex &&
                            focusedColIndex == 0
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    StartShortcutCard(
                        shortcut = shortcuts[colIndex],
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        sx = sx,
                        sy = sy,
                        onFocusChange = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                        }
                    )
                }
            } else if (isAppIcons) {
                // App Icons row
                items(apps.size) { colIndex ->
                    val app = apps[colIndex]
                    val isItemFocused = rowIndex == focusedRowIndex &&
                            colIndex == lazyListState.firstVisibleItemIndex &&
                            focusedColIndex == 0
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    AppIconCard(
                        app = app,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                        sx = sx,
                        sy = sy
                    )
                }
            } else if (!isShortcutsV2) {
                // Normal horizontal content (only for non-shortcuts-v2 channels)
                items(rowContent.size) { colIndex ->
                    val vodContent = rowContent[colIndex]
                    val isItemFocused = rowIndex == focusedRowIndex &&
                            colIndex == lazyListState.firstVisibleItemIndex &&
                            focusedColIndex == 0
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    ContentCard(
                        vodContent = vodContent,
                        channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                        sx = sx,
                        sy = sy,
                        lazyListState = lazyListState
                    )
                }
            }

            // Spacer items
            items(8) {
                val spacerWidth = when {
                    isShortcuts -> sx(210)
                    isShortcutsV2 -> sx(310)
                    isAppIcons -> sx(320)
                    else -> sx(368)
                }
                val spacerHeight = when {
                    isShortcuts -> sy(279)
                    isShortcutsV2 -> sy(199)
                    isAppIcons -> sy(220)  // Zwiększone z 190 na 220 (zgodnie z AppIconCard)
                    else -> sy(208)
                }
                Spacer(
                    modifier = Modifier
                        .width(spacerWidth)
                        .height(spacerHeight)
                )
            }
        }

        // Shortcuts v2 row (NO scrolling, direct focus, starts from x=100)
        if (isShortcutsV2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = miniaturesYOffset)
                    .padding(start = sx(100)),
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                shortcuts.forEachIndexed { colIndex, shortcut ->
                    val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    ShortcutCardV2(
                        shortcut = shortcut,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        sx = sx,
                        sy = sy,
                        onFocusChange = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                        }
                    )
                }
            }
        }

        // Details overlay (only for non-shortcuts and non-app-icons channels)
        if (!isShortcuts && !isShortcutsV2 && !isAppIcons && isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }

                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }

        // Category name text (visible when app-icons scrolled, replaces CategoryIcon)
        if (isAppIcons && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) {
            Box(modifier = Modifier.offset(x = sx(80), y = sy(-45))) {
                Text(
                    text = channel,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // CategoryIcon alpha: fade out when app-icons scrolled
        val categoryAlpha = if (isAppIcons && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) 0f else 1f

        // CategoryIcon zIndex: app-icons below LazyRow, others normal
        val categoryZIndex = if (isAppIcons) -1f else 0f

        // CategoryIcon (fixed position for all channels)
        Box(
            modifier = Modifier
                .offset(x = sx(80), y = sy(0))
                .alpha(categoryAlpha)
                .zIndex(categoryZIndex)
        ) {
            val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
            val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]

            // Logo mapping for channels
            val categoryLogo = when (channel) {
                "Ostatnio używane", "Aplikacje" -> R.drawable.appli
                "Netflix" -> R.drawable.netflix_logo
                "YouTube" -> R.drawable.youtube_logo
                "Prime Video" -> R.drawable.prime_video_logo
                "Disney+" -> R.drawable.disney_plus_logo
                else -> null
            }

            if (categoryFocusRequester != null) {
                CategoryIcon(
                    text = channel,
                    isFocused = categoryIsFocused,
                    onClick = { /* Channel click handler */ },
                    onFocused = { isFocused ->
                        if (isFocused) {
                            Log.d("APLIKACJE_DEBUG", "CategoryIcon '$channel' (row $rowIndex) gained focus")
                            onChannelContentFocusChange(rowIndex, -1)
                        }
                    },
                    focusRequester = categoryFocusRequester,
                    sx = sx,
                    sy = sy,
                    logoUrl = null,
                    logoDrawableId = categoryLogo
                )
            }
        }
    }
}

@Composable
fun OdkrywajChannelRowsLayout(
    channels: List<String>,
    channelTypes: Map<String, String>,
    gridContent: Map<String, List<VodContent>>,
    shortcuts: List<ShortcutItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        channels.forEachIndexed { rowIndex, channelName ->
            val rowContent = gridContent[channelName] ?: emptyList()
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()
            val channelType = channelTypes[channelName] ?: "horizontal"

            val targetY = calculateOdkrywajChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channels = channels,
                channelTypes = channelTypes,
                sy = sy
            )

            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                animationSpec = tween(durationMillis = 500),
                label = "odkrywaj_channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                OdkrywajUnifiedChannelRow(
                    channel = channelName,
                    channelType = channelType,
                    rowIndex = rowIndex,
                    rowContent = rowContent,
                    shortcuts = shortcuts,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    onNavigateToChannelGrid = onNavigateToChannelGrid,
                    onNavigateToVodGrid = onNavigateToVodGrid,
                    onNavigateToKinoGrid = onNavigateToKinoGrid,
                    appIconsData = appIconsData,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }
        }
    }
}

@Composable
fun OdkrywajUnifiedChannelRow(
    channel: String,
    channelType: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    shortcuts: List<ShortcutItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState
) {
    // ODKRYWAJ section - no onClick to EPG Day needed here
    val isCurrentRow = rowIndex == focusedRowIndex

    var showDetailsWithDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = channelType in listOf("top10", "vertical", "horizontal") && isCurrentRow && focusedColIndex == 0

        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350)
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Miniatures Y offset animation (for top10, vertical, horizontal - NOT for slider-max)
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (channelType in listOf("top10", "vertical", "horizontal") && isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "odkrywaj_miniatures_y_offset_$rowIndex"
        )

        when (channelType) {
            "slider-max" -> {
                // Big slider row (NO CategoryIcon, starts from x=120)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(120), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                           colIndex == lazyListState.firstVisibleItemIndex &&
                                           focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        SliderMaxCard(
                            vodContent = vodContent,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }

                    // Spacer items (ensure scrollable area)
                    items(5) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(1326))
                                .height(sy(742))
                        )
                    }
                }
            }
            "shortcuts" -> {
                // Shortcuts row (starts from x=120, NO SCROLLING - fixed position)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset)
                        .padding(start = sx(120)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    // 5 shortcuts (items 0-4)
                    shortcuts.forEachIndexed { colIndex, shortcut ->
                        val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        StartShortcutCard(
                            shortcut = shortcut,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onNavigateToChannelGrid = onNavigateToChannelGrid,
                            onNavigateToVodGrid = onNavigateToVodGrid,
                            onNavigateToKinoGrid = onNavigateToKinoGrid,
                            appIconsData = appIconsData,
                            sx = sx,
                            sy = sy,
                            onFocusChange = { isFocused ->
                                if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                            }
                        )
                    }

                    // Plus button (item 5)
                    val plusButtonIndex = 5
                    val isPlusButtonFocused = rowIndex == focusedRowIndex && plusButtonIndex == focusedColIndex
                    val plusButtonFocusRequester = channelFocusRequesters[Pair(rowIndex, plusButtonIndex)] ?: FocusRequester()

                    ShortcutAddButton(
                        isFocused = isPlusButtonFocused,
                        focusRequester = plusButtonFocusRequester,
                        onFocusChange = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, plusButtonIndex)
                        },
                        sx = sx,
                        sy = sy
                    )
                }
            }
            "collection-slider" -> {
                // Collection slider row (977x464px cards with gradient background)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(120), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        CollectionSliderCard(
                            vodContent = vodContent,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }

                    // Spacer items (ensure scrollable area)
                    items(5) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(977))
                                .height(sy(464))
                        )
                    }
                }
            }
            "top10" -> {
                // Top 10 row (like VOD Top 10 with ranking numbers)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        Top10ContentCard(
                            vodContent = vodContent,
                            rankingNumber = colIndex + 1,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }

                    // Spacer items (Top 10 card size: 261x324)
                    items(8) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(261))
                                .height(sy(324))
                        )
                    }
                }
            }
            "vertical" -> {
                // Vertical miniatures row (Filmy/Seriale use VodContentCard with price)
                val useVodCardWithPrice = channel in listOf("Filmy", "Seriale")

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        if (useVodCardWithPrice) {
                            // Use VodContentCard (220x380) with scale animation and price
                            VodContentCard(
                                vodContent = vodContent,
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy
                            )
                        } else {
                            // Use VerticalContentCard (220x280) for other vertical channels
                            VerticalContentCard(
                                vodContent = vodContent,
                                channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy
                            )
                        }
                    }

                    // Spacer items (different sizes for VodContentCard vs VerticalContentCard)
                    items(8) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(220))
                                .height(if (useVodCardWithPrice) sy(380) else sy(280))
                        )
                    }
                }
            }
            "header" -> {
                // Header row - just text, no focus, no interaction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sx(120))
                        .offset(y = sy(0))
                ) {
                    Text(
                        text = channel.removePrefix("[HEADER] "),
                        color = Color(0xFFEEEEEE),
                        fontSize = (32 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            "shortcuts-v2" -> {
                // Shortcuts v2 row rendered separately below (if isShortcutsV2 block)
                // This empty case prevents falling through to horizontal placeholder
            }
            else -> {
                // Horizontal miniatures row
                if (rowContent.isEmpty()) {
                    // Empty content placeholder - attach FocusRequester to prevent crash
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    val isItemFocused = rowIndex == focusedRowIndex && focusedColIndex == 0

                    Box(
                        modifier = Modifier
                            .offset(x = sx(380), y = miniaturesYOffset)
                            .width(sx(368))
                            .height(sy(208))
                            .clip(RoundedCornerShape(sx(12)))
                            .background(Color(0xFF000000).copy(alpha = 0.1f))
                            .border(
                                width = if (isItemFocused) sx(6) else 0.dp,
                                color = if (isItemFocused) Color(0xFF5AECD3) else Color.Transparent,
                                shape = RoundedCornerShape(sx(12))
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onChannelContentFocusChange(rowIndex, 0) }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak programów",
                            color = Color(0xFFEEEEEE).copy(alpha = 0.5f),
                            fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = miniaturesYOffset),
                        state = lazyListState,
                        contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                        horizontalArrangement = Arrangement.spacedBy(sx(20))
                    ) {
                        items(rowContent.size) { colIndex ->
                            val vodContent = rowContent[colIndex]
                            val isItemFocused = rowIndex == focusedRowIndex &&
                                    colIndex == lazyListState.firstVisibleItemIndex &&
                                    focusedColIndex == 0
                            val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                            ContentCard(
                                vodContent = vodContent,
                                channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy,
                                lazyListState = lazyListState,
                                // No onClick for ODKRYWAJ section
                            )
                        }

                        // Spacer items
                        items(8) {
                            Spacer(
                                modifier = Modifier
                                    .width(sx(368))
                                    .height(sy(208))
                            )
                        }
                    }
                }
            }
        }

        // Details overlay (for top10, vertical, horizontal channels)
        if (channelType in listOf("top10", "vertical", "horizontal") && isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }

                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.5f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }

        // Channel name text (visible when app-icons scrolled, replaces CategoryIcon)
        if (channelType == "app-icons" && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) {
            Box(modifier = Modifier.offset(x = sx(80), y = sy(-45))) {
                Text(
                    text = channel,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // CategoryIcon alpha: fade out when app-icons scrolled
        val categoryAlpha = if (channelType == "app-icons" && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) 0f else 1f

        // CategoryIcon zIndex: app-icons below LazyRow, others normal
        val categoryZIndex = if (channelType == "app-icons") -1f else 0f

        // CategoryIcon (skip for slider-max/shortcuts/collection-slider - they don't have CategoryIcon)
        if (channelType !in listOf("slider-max", "shortcuts", "collection-slider")) {
            Box(
                modifier = Modifier
                    .offset(x = sx(80), y = sy(0))
                    .alpha(categoryAlpha)
                    .zIndex(categoryZIndex)
            ) {
                val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]

                // Logo dla channeli
                val logoDrawableId = when (channel) {
                    "Top 10" -> R.drawable.kinoplay2
                    "Popularne" -> R.drawable.tv_icon
                    "Nowości" -> R.drawable.tv_icon
                    "Nowe filmy" -> R.drawable.kinoplay2
                    "Teraz w TV" -> R.drawable.tv_icon
                    else -> null
                }

                CategoryIcon(
                    text = channel,
                    isFocused = categoryIsFocused,
                    onClick = { },
                    onFocused = { isFocused ->
                        if (isFocused) {
                            Log.d("ODKRYWAJ_DEBUG", "CategoryIcon '$channel' (row $rowIndex) gained focus")
                            onChannelContentFocusChange(rowIndex, -1)
                        }
                    },
                    focusRequester = categoryFocusRequester ?: FocusRequester(),
                    sx = sx,
                    sy = sy,
                    logoUrl = null,
                    logoDrawableId = logoDrawableId
                )
            }
        }
    }
}

// Layout for TELEWIZJA channel rows (copied from ODKRYWAJ)
@Composable
fun TelewizjaChannelRowsLayout(
    channels: List<String>,
    channelTypes: Map<String, String>,
    gridContent: Map<String, List<VodContent>>,
    shortcuts: List<ShortcutItem>,
    kidsChannels: List<TvChannel> = emptyList(),
    docChannels: List<TvChannel> = emptyList(),
    filmyISerialeChannels: List<TvChannel> = emptyList(),
    newsChannels: List<TvChannel> = emptyList(),
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onChannelClick: (String) -> Unit = {},
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    livePlayer: ExoPlayer? = null,
    liveNormalPlayerView: PlayerView? = null,
    liveChannelIndex: Int = 0,
    liveTvChannels: List<VodContent> = emptyList(),
    liveLastChannelChangeTime: Long = 0L,
    liveIsError: Boolean = false,
    liveIsLiveTvSlideFocused: Boolean = false,
    liveOnLiveTvFocusChange: (Boolean) -> Unit = {},
    liveShowPiP: Boolean = false,
    liveShowFullscreen: Boolean = false,
    liveOnShowFullscreen: (Boolean) -> Unit = {},
    onNavigateToEpg: () -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> },
    sectionId: String = "TELEWIZJA",
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> }
) {
    Box(modifier = Modifier.fillMaxSize()) {
        channels.forEachIndexed { rowIndex, channelName ->
            val rowContent = gridContent[channelName] ?: emptyList()
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()
            val channelType = channelTypes[channelName] ?: "horizontal"

            val targetY = calculateTelewizjaChannelYPosition(
                rowIndex = rowIndex,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channels = channels,
                channelTypes = channelTypes,
                sy = sy
            )

            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                animationSpec = tween(durationMillis = 500),
                label = "telewizja_channel_y_offset_$rowIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                TelewizjaUnifiedChannelRow(
                    channel = channelName,
                    channelType = channelType,
                    rowIndex = rowIndex,
                    rowContent = rowContent,
                    shortcuts = shortcuts,
                    tvChannelLogos = appIconsData[channelName] ?: emptyList(),
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    onChannelClick = onChannelClick,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState,
                    onNavigateToEpg = onNavigateToEpg,
                    onNavigateToEpgDay = onNavigateToEpgDay,
                    sectionId = channelName,  // Pass row channel name (e.g., "Moja lista kanałów") for proper focus restoration
                    onNavigateToChannelGrid = onNavigateToChannelGrid,
                    onNavigateToVodGrid = onNavigateToVodGrid,
                    onNavigateToKinoGrid = onNavigateToKinoGrid,
                    appIconsData = appIconsData
                )
            }
        }
    }
}

// Unified channel row component for TELEWIZJA (copied from ODKRYWAJ)
@Composable
fun TelewizjaUnifiedChannelRow(
    channel: String,
    channelType: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    shortcuts: List<ShortcutItem>,
    tvChannelLogos: List<TvChannel> = emptyList(),
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onChannelClick: (String) -> Unit = {},
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState,
    livePlayer: ExoPlayer? = null,
    liveNormalPlayerView: PlayerView? = null,
    liveChannelIndex: Int = 0,
    liveTvChannels: List<VodContent> = emptyList(),
    liveLastChannelChangeTime: Long = 0L,
    liveIsError: Boolean = false,
    liveOnLiveTvFocusChange: (Boolean) -> Unit = {},
    liveShowPiP: Boolean = false,
    liveShowFullscreen: Boolean = false,
    liveOnShowFullscreen: (Boolean) -> Unit = {},
    onNavigateToEpg: () -> Unit = {},
    onNavigateToEpgDay: (channelId: String, itemId: String?, scrollPosition: Int, sectionId: String) -> Unit = { _, _, _, _ -> },
    sectionId: String = "TELEWIZJA",
    onNavigateToChannelGrid: (title: String, category: String, filter: ((TvChannel) -> Boolean)?, channelList: List<TvChannel>?) -> Unit = { _, _, _, _ -> },
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    appIconsData: Map<String, List<TvChannel>> = emptyMap()
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    val isShortcutsV2 = channel == "Skróty v2"

    var showDetailsWithDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = !isShortcutsV2 && channelType in listOf("top10", "vertical", "horizontal") && isCurrentRow && focusedColIndex == 0

        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350)
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Miniatures Y offset animation (for top10, vertical, horizontal - NOT for slider-max, app-icons, shortcuts-v2)
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (!isShortcutsV2 && channelType in listOf("top10", "vertical", "horizontal") && isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "telewizja_miniatures_y_offset_$rowIndex"
        )

        when (channelType) {
            "slider-max" -> {
                // Big slider row (NO CategoryIcon, starts from x=120)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(120), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                           colIndex == lazyListState.firstVisibleItemIndex &&
                                           focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        SliderMaxCard(
                            vodContent = vodContent,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }

                    // Spacer items (ensure scrollable area)
                    items(5) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(1326))
                                .height(sy(742))
                        )
                    }
                }
            }
            "shortcuts" -> {
                // Shortcuts row (starts from x=120, NO SCROLLING - fixed position)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset)
                        .padding(start = sx(120)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    // 5 shortcuts (items 0-4)
                    shortcuts.forEachIndexed { colIndex, shortcut ->
                        val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        StartShortcutCard(
                            shortcut = shortcut,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onNavigateToChannelGrid = onNavigateToChannelGrid,
                            onNavigateToVodGrid = onNavigateToVodGrid,
                            onNavigateToKinoGrid = onNavigateToKinoGrid,
                            appIconsData = appIconsData,
                            sx = sx,
                            sy = sy,
                            onFocusChange = { isFocused ->
                                if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                            }
                        )
                    }

                    // Plus button (item 5)
                    val plusButtonIndex = 5
                    val isPlusButtonFocused = rowIndex == focusedRowIndex && plusButtonIndex == focusedColIndex
                    val plusButtonFocusRequester = channelFocusRequesters[Pair(rowIndex, plusButtonIndex)] ?: FocusRequester()

                    ShortcutAddButton(
                        isFocused = isPlusButtonFocused,
                        focusRequester = plusButtonFocusRequester,
                        onFocusChange = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, plusButtonIndex)
                        },
                        sx = sx,
                        sy = sy
                    )
                }
            }
            "service-logos" -> {
                // Service logos row (Row 1: Disney+, Prime, Netflix, HBO Max, Apple TV+)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sx(120)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    serviceLogos.forEachIndexed { colIndex, service ->
                        val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        ServiceLogoCard(
                            service = service,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }
            "channel-logos" -> {
                // Channel logos grid (Row 2: 2x7 grid of TV channel logos)
                ChannelLogosGrid(
                    channels = tvChannelLogos,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onFocusChange = onChannelContentFocusChange,
                    channelFocusRequesters = channelFocusRequesters,
                    sx = sx,
                    sy = sy
                )
            }
            "tv-channel-icons" -> {
                // TV Channel icons row (CategoryIcon + scrollable channel logos, like APLIKACJE)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(80), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    // CategoryIcon as first item
                    item {
                        val isCategoryIconFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                        val categoryIconFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)] ?: FocusRequester()

                        CategoryIcon(
                            text = channel,
                            isFocused = isCategoryIconFocused,
                            onClick = { },
                            onFocused = { isFocused ->
                                if (isFocused) {
                                    onChannelContentFocusChange(rowIndex, -1)
                                }
                            },
                            focusRequester = categoryIconFocusRequester,
                            sx = sx,
                            sy = sy
                        )
                    }

                    // Channel logo icons
                    items(tvChannelLogos.size) { colIndex ->
                        val channel = tvChannelLogos[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        TvChannelIconCard(
                            channel = channel,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            onClick = { onChannelClick(channel.name) },
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }
            "app-icons" -> {
                // App-style icons (TV channels with app-icon styling, scrolling focus model like APLIKACJE)
                // CategoryIcon in WIDEO style (text-only with black background)
                if (tvChannelLogos.isEmpty()) {
                    // Empty content placeholder - attach FocusRequester to prevent crash
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    val isItemFocused = rowIndex == focusedRowIndex && focusedColIndex == 0

                    Box(
                        modifier = Modifier
                            .offset(x = sx(380), y = sy(0))
                            .width(sx(TELEWIZJA_CHANNEL_LIST_CARD_WIDTH))
                            .height(sy(TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT))
                            .clip(RoundedCornerShape(sx(12)))
                            .background(Color(0xFF000000).copy(alpha = 0.1f))
                            .border(
                                width = if (isItemFocused) sx(4) else 0.dp,
                                color = if (isItemFocused) Color(0xFF5AECD3) else Color.Transparent,
                                shape = RoundedCornerShape(sx(12))
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onChannelContentFocusChange(rowIndex, 0) }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak kanałów",
                            color = Color(0xFFEEEEEE).copy(alpha = 0.5f),
                            fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        state = lazyListState,
                        contentPadding = PaddingValues(start = sx(380), end = sx(20)),  // Start at 380px (space for CategoryIcon)
                        horizontalArrangement = Arrangement.spacedBy(sx(12))  // 12px spacing like APLIKACJE app-icons
                    ) {
                        items(tvChannelLogos.size) { colIndex ->
                            val tvChannel = tvChannelLogos[colIndex]
                            // ✅ Direct focus check: item is focused when its indices match
                            val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                            // ⭐ Each item gets its own FocusRequester (from map or new) - like APLIKACJE
                            val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                            ChannelListCard(
                                channel = tvChannel,
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                onClick = {
                                    // Navigate to EPG Day screen (like "Teraz w TV" row)
                                    val epgId = tvChannel.epgId ?: tvChannel.name
                                    android.util.Log.d("TELEWIZJA_CLICK", "=== EPG DAY NAVIGATION ===")
                                    android.util.Log.d("TELEWIZJA_CLICK", "Channel clicked: name='${tvChannel.name}', logo='${tvChannel.logo}', epgId='${tvChannel.epgId}'")
                                    android.util.Log.d("TELEWIZJA_CLICK", "Row name: '$channel', Derived epgId: '$epgId'")
                                    android.util.Log.d("TELEWIZJA_CLICK", "Passing: channelId='$channel' (row), itemId='$epgId' (epgId), scrollPos=0, sectionId='$sectionId'")
                                    onNavigateToEpgDay(channel, epgId, 0, sectionId)  // Pass row name as channelId, epgId as itemId
                                },
                                sx = sx,
                                sy = sy
                            )
                        }

                        // Spacer items (ensure scrollable area)
                        items(8) {
                            Spacer(
                                modifier = Modifier
                                    .width(sx(TELEWIZJA_CHANNEL_LIST_CARD_WIDTH))  // Same as ChannelListCard width (208px)
                                    .height(sy(TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT))  // Same as ChannelListCard height (208px)
                            )
                        }
                    }
                }

                // CategoryIcon is rendered by global section (line 6189-6222) with WIDEO style
            }
            "tv-apps" -> {
                // TV App-style icons (large TV channel icons like APLIKACJE)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(12))  // Tighter spacing like app-icons
                ) {
                    items(tvChannelLogos.size) { colIndex ->
                        val channel = tvChannelLogos[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                           colIndex == lazyListState.firstVisibleItemIndex &&
                                           focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        TvAppIconCard(
                            channel = channel,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }
                }

                // CategoryIcon (floating, outside LazyRow)
                Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
                    val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                    val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)] ?: FocusRequester()

                    CategoryIcon(
                        text = channel,
                        isFocused = categoryIsFocused,
                        onClick = {},
                        onFocused = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, -1)
                        },
                        focusRequester = categoryFocusRequester,
                        sx = sx,
                        sy = sy
                    )
                }
            }
            "collection-slider" -> {
                // Collection slider row (977x464px cards with gradient background)
                // Uses VodContent for all collection-slider channels (including "Kategorie EPG")
                if (rowContent.isEmpty()) {
                    // ✅ Empty content placeholder - attach FocusRequester to prevent crash during restoration
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    val isItemFocused = rowIndex == focusedRowIndex && focusedColIndex == 0

                    Box(
                        modifier = Modifier
                            .offset(x = sx(120), y = sy(0))
                            .width(sx(977))
                            .height(sy(464))
                            .clip(RoundedCornerShape(sx(12)))
                            .background(Color(0xFF000000).copy(alpha = 0.1f))
                            .border(
                                width = if (isItemFocused) sx(6) else 0.dp,
                                color = if (isItemFocused) Color(0xFF5AECD3) else Color.Transparent,
                                shape = RoundedCornerShape(sx(12))
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onChannelContentFocusChange(rowIndex, 0) }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak programów EPG",
                            color = Color(0xFFEEEEEE).copy(alpha = 0.5f),
                            fontSize = (32 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        state = lazyListState,
                        contentPadding = PaddingValues(start = sx(120), end = sx(20)),
                        horizontalArrangement = Arrangement.spacedBy(sx(20))
                    ) {
                        items(rowContent.size) { colIndex ->
                            val vodContent = rowContent[colIndex]
                            val isItemFocused = rowIndex == focusedRowIndex &&
                                    colIndex == lazyListState.firstVisibleItemIndex &&
                                    focusedColIndex == 0
                            val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                            CollectionSliderCard(
                                vodContent = vodContent,
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, 0) },  // Fixed focus model: always col=0
                                onClick = {
                                    android.util.Log.d("TELEWIZJA_CLICK", "Opening EPG Day Test from $channel: itemId=${vodContent.id}, scroll=${lazyListState.firstVisibleItemIndex}, section=$sectionId")
                                    onNavigateToEpgDay(channel, vodContent.id, lazyListState.firstVisibleItemIndex, sectionId)  // ID-based: channelId, itemId, scrollPosition
                                },
                                sx = sx,
                                sy = sy
                            )
                        }

                        // Spacer items (ensure scrollable area)
                        items(5) {
                            Spacer(
                                modifier = Modifier
                                    .width(sx(977))
                                    .height(sy(464))
                            )
                        }
                    }
                }
            }
            "top10" -> {
                // Top 10 row (like VOD Top 10 with ranking numbers)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        Top10ContentCard(
                            vodContent = vodContent,
                            rankingNumber = colIndex + 1,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }

                    // Spacer items (Top 10 card size: 261x324)
                    items(8) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(261))
                                .height(sy(324))
                        )
                    }
                }
            }
            "vertical" -> {
                // Vertical miniatures row (Filmy/Seriale use VodContentCard with price)
                val useVodCardWithPrice = channel in listOf("Filmy", "Seriale")

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = miniaturesYOffset),
                    state = lazyListState,
                    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                    horizontalArrangement = Arrangement.spacedBy(sx(20))
                ) {
                    items(rowContent.size) { colIndex ->
                        val vodContent = rowContent[colIndex]
                        val isItemFocused = rowIndex == focusedRowIndex &&
                                colIndex == lazyListState.firstVisibleItemIndex &&
                                focusedColIndex == 0
                        val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                        if (useVodCardWithPrice) {
                            // Use VodContentCard (220x380) with scale animation and price
                            VodContentCard(
                                vodContent = vodContent,
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy
                            )
                        } else {
                            // Use VerticalContentCard (220x280) for other vertical channels
                            VerticalContentCard(
                                vodContent = vodContent,
                                channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy
                            )
                        }
                    }

                    // Spacer items (different sizes for VodContentCard vs VerticalContentCard)
                    items(8) {
                        Spacer(
                            modifier = Modifier
                                .width(sx(220))
                                .height(if (useVodCardWithPrice) sy(380) else sy(280))
                        )
                    }
                }
            }
            "header" -> {
                // Header row - just text, no focus, no interaction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sx(120))
                        .offset(y = sy(0))
                ) {
                    Text(
                        text = channel.removePrefix("[HEADER] "),
                        color = Color(0xFFEEEEEE),
                        fontSize = (32 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            "shortcuts-v2" -> {
                // Shortcuts v2 row rendered separately below (if isShortcutsV2 block)
                // This empty case prevents falling through to horizontal placeholder
            }
            else -> {
                // Horizontal miniatures row
                if (rowContent.isEmpty()) {
                    // Empty content placeholder - attach FocusRequester to prevent crash
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    val isItemFocused = rowIndex == focusedRowIndex && focusedColIndex == 0

                    Box(
                        modifier = Modifier
                            .offset(x = sx(380), y = miniaturesYOffset)
                            .width(sx(368))
                            .height(sy(208))
                            .clip(RoundedCornerShape(sx(12)))
                            .background(Color(0xFF000000).copy(alpha = 0.1f))
                            .border(
                                width = if (isItemFocused) sx(6) else 0.dp,
                                color = if (isItemFocused) Color(0xFF5AECD3) else Color.Transparent,
                                shape = RoundedCornerShape(sx(12))
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onChannelContentFocusChange(rowIndex, 0) }
                            .focusable(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Brak programów",
                            color = Color(0xFFEEEEEE).copy(alpha = 0.5f),
                            fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = miniaturesYOffset),
                        state = lazyListState,
                        contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                        horizontalArrangement = Arrangement.spacedBy(sx(20))
                    ) {
                        items(rowContent.size) { colIndex ->
                            val vodContent = rowContent[colIndex]
                            val isItemFocused = rowIndex == focusedRowIndex &&
                                    colIndex == lazyListState.firstVisibleItemIndex &&
                                    focusedColIndex == 0
                            val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                            ContentCard(
                                vodContent = vodContent,
                                channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                                isFocused = isItemFocused,
                                focusRequester = focusRequester,
                                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                                sx = sx,
                                sy = sy,
                                lazyListState = lazyListState,
                                onClick = {
                                    android.util.Log.d("TELEWIZJA_CLICK", "Opening EPG Day Test from $channel: itemId=${vodContent.id}, scroll=${lazyListState.firstVisibleItemIndex}, section=$sectionId")
                                    onNavigateToEpgDay(channel, vodContent.id, lazyListState.firstVisibleItemIndex, sectionId)  // ID-based: channelId, itemId, scrollPosition
                                }
                            )
                        }

                        // Spacer items
                        items(8) {
                            Spacer(
                                modifier = Modifier
                                    .width(sx(368))
                                    .height(sy(208))
                            )
                        }
                    }
                }
            }
        }

        // Details overlay (for top10, vertical, horizontal channels)
        if (channelType in listOf("top10", "vertical", "horizontal") && isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }

                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.5f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }

        // Channel name text (visible when app-icons scrolled, replaces CategoryIcon)
        if (channelType == "app-icons" && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) {
            Box(modifier = Modifier.offset(x = sx(80), y = sy(-45))) {
                Text(
                    text = channel,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // Shortcuts v2 row (NO scrolling, direct focus, starts from x=100, like APLIKACJE)
        if (isShortcutsV2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = miniaturesYOffset)
                    .padding(start = sx(100)),
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                shortcuts.forEachIndexed { colIndex, shortcut ->
                    val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    ShortcutCardV2(
                        shortcut = shortcut,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        sx = sx,
                        sy = sy,
                        onFocusChange = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                        },
                        onClick = {
                            when (shortcut.title) {
                                "Program telewizyjny" -> {
                                    onNavigateToEpg()
                                }
                                "Moja lista kanałów" -> {
                                    // Navigate to ChannelGridScreen with channels 1-9
                                    onNavigateToChannelGrid(
                                        "Moja lista kanałów",
                                        "Wszystkie",
                                        null,  // No filter - use preloaded channels
                                        appIconsData["Moja lista kanałów"]  // Pass pre-loaded channels
                                    )
                                }
                                "Lista kanałów" -> {
                                    // Navigate to ChannelGridScreen with all channels
                                    onNavigateToChannelGrid(
                                        "Lista kanałów TV",
                                        "Wszystkie",
                                        null,  // No filter - use preloaded channels
                                        appIconsData["Wszystkie kanały"]  // Pass pre-loaded channels
                                    )
                                }
                                "Nagrania" -> {
                                    // Navigate to Nagrania grid (20 random VOD)
                                    val vodList = VodDataCache.getVodContentList()
                                    val randomFilms = vodList.shuffled().take(20)
                                    onNavigateToVodGrid("Wszystkie nagrania", randomFilms, "TELEWIZJA")
                                }
                            }
                        }
                    )
                }
            }
        }

        // CategoryIcon alpha: fade out when app-icons scrolled
        val categoryAlpha = if (channelType == "app-icons" && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) 0f else 1f

        // CategoryIcon zIndex: app-icons below LazyRow, others normal
        val categoryZIndex = if (channelType == "app-icons") -1f else 0f

        // CategoryIcon (skip for slider-max/shortcuts/shortcuts-v2/collection-slider/header - they don't have CategoryIcon)
        if (channelType !in listOf("slider-max", "shortcuts", "shortcuts-v2", "collection-slider", "header")) {
            Box(
                modifier = Modifier
                    .offset(x = sx(80), y = sy(0))
                    .alpha(categoryAlpha)
                    .zIndex(categoryZIndex)
            ) {
                val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]

                // Logo dla channeli
                val logoDrawableId = when (channel) {
                    "Top 10" -> R.drawable.kinoplay2
                    "Popularne" -> R.drawable.tv_icon
                    "Nowości" -> R.drawable.tv_icon
                    "Nowe filmy" -> R.drawable.kinoplay2
                    "Teraz w TV" -> R.drawable.tv_icon
                    else -> null
                }

                // EPG channels (text-only, no icon)
                val isEpgChannel = channel in listOf("Teraz w TV", "FILMY", "SERIALE", "SPORT", "TELETURNIEJE", "Kategorie EPG")

                CategoryIcon(
                    text = channel,
                    isFocused = categoryIsFocused,
                    onClick = {
                        // Handle app-icons CategoryIcon clicks - navigate to ChannelGridScreen
                        when (channel) {
                            "Wszystkie kanały" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Wszystkie kanały",
                                    "Wszystkie",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                            "Moja lista kanałów" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Moja lista",
                                    "Wszystkie",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                            "Dokumenty" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Dokumenty",
                                    "Dokumenty",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                            "Dla dzieci" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Dla dzieci",
                                    "Dzieci",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                            "Sport" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Sport",
                                    "Sport",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                            "Filmy i seriale" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Filmy i seriale",
                                    "Filmy i seriale",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                            "Informacyjne" -> {
                                onNavigateToChannelGrid(
                                    "Lista kanałów - Informacyjne",
                                    "Informacyjne",
                                    null,  // No filter - use preloaded channels
                                    appIconsData[channel]  // Pass pre-loaded channels
                                )
                            }
                        }
                    },
                    onFocused = { isFocused ->
                        if (isFocused) {
                            Log.d("TELEWIZJA_DEBUG", "CategoryIcon '$channel' (row $rowIndex) gained focus")
                            onChannelContentFocusChange(rowIndex, -1)
                        }
                    },
                    focusRequester = categoryFocusRequester ?: FocusRequester(),
                    sx = sx,
                    sy = sy,
                    logoUrl = null,
                    logoDrawableId = logoDrawableId,
                    showIcon = channelType != "app-icons" && !isEpgChannel,              // ⭐ Hide icon for app-icons and EPG channels
                    showBackgroundWhenFocused = channelType == "app-icons" || isEpgChannel  // ⭐ Black background for app-icons and EPG
                )
            }
        }
    }
}

@Composable
private fun VodScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var resetTrigger by remember { mutableStateOf(0) }

    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "KINO_PLAY") {
            resetTrigger++
        }
    }

    VodWithChannels(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        shouldAutoFocus = globalFocusState.value.sectionId == "KINO_PLAY" && globalFocusState.value.currentRow > 0,
        onNavigateToVodGrid = onNavigateToVodGrid,
        onNavigateToKinoGrid = onNavigateToKinoGrid,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger,
        globalFocusState = globalFocusState
    )
}

@Composable
private fun WideoScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var resetTrigger by remember { mutableStateOf(0) }

    // Detect when user returns to menu to trigger focus reset
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "WIDEO") {
            resetTrigger++
        }
    }

    WideoChannelsScreen(
        onReturnToMenu = {
            globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
        },
        shouldAutoFocus = globalFocusState.value.sectionId == "WIDEO" && globalFocusState.value.currentRow > 0,
        onNavigateToVodGrid = onNavigateToVodGrid,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger
    )
}

/**
 * Filter VOD content by WIDEO section category name
 * Maps WIDEO category names to actual VodContent.category values
 */
private fun filterVodByWIDEOCategory(vodList: List<VodContent>, wideoCategory: String): List<VodContent> {
    return when (wideoCategory) {
        "Seriale" -> emptyList()  // Brak seriali w kino_play.json
        "Filmy fabularne" -> vodList.filter {
            it.category in listOf("Akcja", "Komedia", "Dramat", "Thriller", "Przygodowy", "Fantasy", "Sci-fi")
        }
        "Filmy dokumentalne" -> vodList.filter {
            it.category.lowercase().contains("dokument") ||
            it.category.lowercase().contains("documentary")
        }
        "Dla dzieci" -> vodList.filter {
            it.category.lowercase().contains("familij") ||
            it.category.lowercase().contains("animac") ||
            it.category == "Family"
        }
        "Świetna rozrywka" -> vodList.filter {
            it.category.lowercase().contains("komedia")
        }
        "Filmy" -> vodList.filter {
            it.category in listOf("Akcja", "Komedia", "Dramat", "Thriller", "Horror", "Sci-fi", "Fantasy", "Przygodowy")
        }
        "Cinemax", "KOLEKCJE", "Kolekcje", "Najlepsze wg Filmwebu" -> vodList.shuffled()  // Wszystkie filmy (randomizowane)
        else -> vodList
    }.take(100)  // Limit dla wydajności
}

/**
 * Filter movies by category supporting multiple category names (e.g., "Akcja|Action")
 */
private fun filterMoviesByCategory(movies: List<VodContent>, categoryFilter: String): List<VodContent> {
    val categories = categoryFilter.split("|").map { it.lowercase().trim() }
    return movies.filter { movie ->
        categories.any { category ->
            movie.category.lowercase().contains(category)
        }
    } // Returns all matching movies (full catalog for KinoGridScreen)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VodWithChannels(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0,
    globalFocusState: MutableState<GlobalFocusState>
) {
    val context = LocalContext.current
    val channels = listOf("Kino Play", "Skróty v3", "Polecane", "Top 10", "Ostatnio dodane", "Akcja", "Komedie", "Horror", "Biograficzne")

    val gridContent = remember {
        val kinoPlayMovies = VodDataCache.getKinoPlayMovies()
        if (kinoPlayMovies.isNotEmpty()) {
            channels.associateWith { channelName ->
                when (channelName) {
                    "Skróty v3" -> emptyList() // Shortcuts don't have grid content
                    "Kino Play", "Polecane", "Top 10", "Ostatnio dodane" ->
                        kinoPlayMovies.shuffled().take(10)
                    "Akcja" -> filterMoviesByCategory(kinoPlayMovies, "Akcja|Action")
                    "Komedie" -> filterMoviesByCategory(kinoPlayMovies, "Komedia|Comedy")
                    "Horror" -> filterMoviesByCategory(kinoPlayMovies, "Horror")
                    "Biograficzne" -> filterMoviesByCategory(kinoPlayMovies, "Biograficzny|Biography|Biographical")
                    else -> emptyList()
                }
            }
        } else {
            emptyMap()
        }
    }

    // Row 0 = menu, Row 1 = slider, Row 2+ = channels
    var focusedRowIndex by remember { mutableStateOf(1) } // Start at slider
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie

    // Reset focus state when returning to menu (like MOJE)
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 1 // Back to slider
            focusedColIndex = -2
        }
    }

    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            // Row 1 = slider (no focus requesters needed)
            // Rows 2-9 = channels (8 channels now)
            repeat(channels.size) { rowIndex ->
                val adjustedRowIndex = rowIndex + 2 // Channels start at row 2
                val channelName = channels[rowIndex]

                if (channelName == "Skróty v3") {
                    // Shortcuts row: 6 horizontal items (col 0-5), NO CategoryIcon
                    repeat(6) { colIndex ->
                        put(Pair(adjustedRowIndex, colIndex), FocusRequester())
                    }
                } else {
                    // Regular channels: CategoryIcon + content
                    put(Pair(adjustedRowIndex, -1), FocusRequester()) // CategoryIcon
                    put(Pair(adjustedRowIndex, 0), FocusRequester()) // Fixed focus position
                }
            }
        }
    }

    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            focusedRowIndex = 1 // Start at slider
            focusedColIndex = 0 // Focus on rent button
        }
        isInitialized = true
    }

    // Auto-reset LazyListState for unfocused rows (like MOJE)
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            // Reduced from 150ms to 0ms for instant tab switching
            kotlinx.coroutines.delay(0)
            repeat(channels.size) { channelIndex ->
                val channelRowIndex = channelIndex + 2 // Channels start at row 2
                if (channelRowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[channelIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleVodNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu
                )
            }
            .focusable()
    ) {
        VodLayoutWithSlider(
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channels = channels,
            gridContent = gridContent,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col ->
                Log.d("VOD_DEBUG", "Focus changed to row $row, col $col")
                focusedRowIndex = row
                focusedColIndex = col
            },
            onNavigateToVodGrid = onNavigateToVodGrid,
            onNavigateToKinoGrid = onNavigateToKinoGrid,
            lazyListStates = lazyListStates,
            globalFocusState = globalFocusState,
            sx = sx,
            sy = sy
        )
    }
}

@Composable
private fun VodLayoutWithSlider(
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    lazyListStates: Map<Int, LazyListState>,
    globalFocusState: MutableState<GlobalFocusState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Slider (Row 1) - fullscreen with animation, z-index 1
        val sliderYOffset by animateDpAsState(
            targetValue = if (focusedRowIndex >= 2) sy(-1200) else sy(0),
            animationSpec = tween(durationMillis = 500),
            label = "vod_slider_y_offset"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = sliderYOffset)
                .zIndex(1f)
        ) {
            VodHeroSlider(
                isFocused = focusedRowIndex == 1 && globalFocusState.value.currentRow > 0,
                sx = sx,
                sy = sy
            )
        }

        // Channele (Row 2+) - z-index 10 (nad sliderem)
        // Pierwszy channel wystawający 60px od dołu
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
            VodChannelRows(
                channels = channels,
                gridContent = gridContent,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channelFocusRequesters = channelFocusRequesters,
                onChannelContentFocusChange = onChannelContentFocusChange,
                onNavigateToVodGrid = onNavigateToVodGrid,
                onNavigateToKinoGrid = onNavigateToKinoGrid,
                lazyListStates = lazyListStates,
                sx = sx,
                sy = sy
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VodHeroSlider(
    isFocused: Boolean,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var currentSlide by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val sliderItems = remember {
        listOf(
            VodSlideData(
                "Ballerina. Z uniwersum Johna Wicka",
                "Akcja",
                "120 min.",
                "2025 r.",
                "USA",
                "13 lat",
                "Zemsta ma nowe, piękniejsze oblicze. Ale wciąż nie zna litości. John Wick powraca, by wspomóc swoją godną następczynię!",
                "24 zł/48h",
                "https://n-1401-7.dcs.redcdn.pl/scale/play/playtv/upload/tvod/36490504/images/1053333674?srcmode=3&srcw=16&srch=9&dstw=1280&dsth=720&type=1&quality=85"
            ),
            VodSlideData(
                "Fantastyczna 4: Pierwsze kroki",
                "Akcja",
                "110 min",
                "2025 r.",
                "USA",
                "13 lat",
                "Pierwsza Rodzina Marvela staje przed swoim najtrudniejszym wyzwaniem. Zmuszeni do balansowania między rolą bohaterów a siłą rodzinnych więzi, muszą obronić Ziemię przed wygłodniałym kosmicznym bogiem Galactusem i jego heroldem, Srebrnym Surferem.",
                "24 zł/48h",
                "https://n-1401-7.dcs.redcdn.pl/scale/play/playtv/images/vod/e3f1e1b2-a53b-4a24-bfad-99ed0ce5e0ea/billboard_mobile.jpg?srcmode=3&srcw=16&srch=9&dstw=1920&dsth=1080&quality=80&type=1"
            ),
            VodSlideData(
                "Materialiści",
                "Komedia",
                "116 min",
                "2025 r.",
                "USA",
                "13 lat",
                "Lucy (Dakota Johnson) to młoda, ambitna swatka z Nowego Jorku, która wierzy, że zna przepis na miłość. Pewnego wieczoru poznaje wysokiego, przystojnego bruneta, prawdziwego „jednorożca\" (Pedro Pascal), a przypadkowe spotkanie z byłym chłopakiem (Chris Evans) stawia ją przed trudnym wyborem. Teraz musi zdecydować między idealnym partnerem a nieidealnym byłym.",
                "24 zł/48h",
                "https://n-1401-4.dcs.redcdn.pl/scale/play/playtv/upload/tvod/36337247/images/1051510641?srcmode=3&srcw=16&srch=9&dstw=1280&dsth=720&type=1&quality=85"
            ),
            VodSlideData(
                "Oszukać przeznaczenie: Więzy krwi",
                "Horror",
                "132 min",
                "2025 r.",
                "USA",
                "13 lat",
                "Dręczona przez gwałtowny, powtarzający się koszmar studentka Stefani wraca do domu, aby wytropić jedyną osobę, która może przerwać ten cykl i uratować jej rodzinę przed makabryczną śmiercią, która na nią czeka.",
                "24 zł/48h",
                "https://n-1411-10.dcs.redcdn.pl/scale/play/playtv/upload/tvod/35502381/images/1034599634?srcmode=3&srcw=16&srch=9&dstw=1280&dsth=720&type=1&quality=85"
            )
        )
    }

    val currentItem = sliderItems[currentSlide]

    LaunchedEffect(isFocused) {
        if (isFocused) {
            // Reduced from 100ms to 0ms for instant focus
            kotlinx.coroutines.delay(0)
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!isFocused) return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (event.key) {
                    Key.DirectionLeft -> {
                        if (currentSlide > 0) {
                            currentSlide--
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (currentSlide < sliderItems.size - 1) {
                            currentSlide++
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        AsyncImage(
            model = currentItem.backgroundUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .let { modifier ->
                    if (currentSlide == 1) {
                        modifier.scale(scaleX = -1f, scaleY = 1f)
                    } else {
                        modifier
                    }
                },
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xAA000000),
                            Color(0x33000000),
                            Color.Transparent
                        ),
                        endX = sx(960).value
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(sx(800))
                .padding(sx(80))
                .zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(sy(24))
        ) {
            Text(
                text = currentItem.title,
                color = Color.White,
                fontSize = sy(64).value.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(16))
            ) {
                Text(currentItem.genre, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.duration, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.year, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.country, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.ageRating, style = vodMetadataStyle(sy))
            }

            Text(
                text = currentItem.description,
                color = Color(0xFFEEEEEE),
                fontSize = sy(24).value.sp,
                fontWeight = FontWeight.W400,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (sy(24).value * 1.4).sp
            )

            Spacer(modifier = Modifier.height(sy(24)))

            // Rent button only
            Box(
                modifier = Modifier
                    .height(sy(72))
                    .background(
                        color = if (isFocused) Color(0xFF5FEDD4) else Color(0x33EEEEEE),
                        shape = RoundedCornerShape(sx(8))
                    )
                    .padding(horizontal = sx(32)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Wypożycz: ${currentItem.price}",
                    color = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE),
                    fontSize = sy(24).value.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.48).sp
                )
            }

            Spacer(modifier = Modifier.height(sy(24)))

            // Bullety
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(24)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(sliderItems.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (index == currentSlide) sx(32) else sx(16),
                                height = if (index == currentSlide) sy(32) else sy(16)
                            )
                            .background(
                                color = if (index == currentSlide) Color(0xFFEEEEEE) else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = if (index == currentSlide) 0.dp else 2.dp,
                                color = if (index == currentSlide) Color.Transparent else Color(0x99EEEEEE),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun VodChannelRows(
    channels: List<String>,
    gridContent: Map<String, List<VodContent>>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        channels.forEachIndexed { channelIndex, channelName ->
            val actualRowIndex = channelIndex + 2 // Channels start at row 2
            val rowContent = gridContent[channelName] ?: emptyList()
            val lazyListState = lazyListStates[channelIndex] ?: LazyListState()

            // All channels use the same scrolling system (like MOJE)
            val targetY = calculateVodChannelYPosition(
                channelIndex = channelIndex,
                channelName = channelName,
                focusedRowIndex = focusedRowIndex,
                focusedColIndex = focusedColIndex,
                channels = channels,
                sy = sy
            )

            val channelYOffset by animateDpAsState(
                targetValue = targetY,
                animationSpec = tween(
                    durationMillis = 350, // Sync z miniatures (było 500ms)
                    easing = androidx.compose.animation.core.EaseInOutCubic // Match miniatures easing
                ),
                label = "vod_channel_y_offset_$channelIndex"
            )

            Box(
                modifier = Modifier.offset(y = channelYOffset)
            ) {
                if (channelName == "Skróty v3") {
                    // Special rendering for Skróty v3 - horizontal shortcuts row
                    VodShortcutsV3Row(
                        actualRowIndex = actualRowIndex,
                        focusedRowIndex = focusedRowIndex,
                        focusedColIndex = focusedColIndex,
                        channelFocusRequesters = channelFocusRequesters,
                        onChannelContentFocusChange = onChannelContentFocusChange,
                        onNavigateToKinoGrid = onNavigateToKinoGrid,
                        sx = sx,
                        sy = sy
                    )
                } else {
                    VodUnifiedChannelRow(
                        channel = channelName,
                        actualRowIndex = actualRowIndex,
                        rowContent = rowContent,
                        focusedRowIndex = focusedRowIndex,
                        focusedColIndex = focusedColIndex,
                        channelFocusRequesters = channelFocusRequesters,
                        onChannelContentFocusChange = onChannelContentFocusChange,
                        onNavigateToKinoGrid = onNavigateToKinoGrid,
                        sx = sx,
                        sy = sy,
                        lazyListState = lazyListState
                    )
                }
            }
        }
    }
}

@Composable
private fun VodUnifiedChannelRow(
    channel: String,
    actualRowIndex: Int,
    rowContent: List<VodContent>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState
) {
    val isCurrentRow = actualRowIndex == focusedRowIndex

    var showDetailsWithDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = isCurrentRow && focusedColIndex == 0

        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350)
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "vod_miniatures_y_offset_$actualRowIndex"
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = miniaturesYOffset), // Animacja 350ms - sync z channel offset
            state = lazyListState,
            contentPadding = PaddingValues(
                start = sx(380),
                end = sx(20)
                // top usunięte - pozycjonowanie przez .offset() dla smooth animation
            ),
            horizontalArrangement = Arrangement.spacedBy(sx(20))
        ) {
            items(rowContent.size) { colIndex ->
                val vodContent = rowContent[colIndex]
                val isItemFocused = actualRowIndex == focusedRowIndex &&
                        colIndex == lazyListState.firstVisibleItemIndex &&
                        focusedColIndex == 0

                val focusRequester = channelFocusRequesters[Pair(actualRowIndex, colIndex)] ?: FocusRequester()

                // Detect content type based on channel name
                val isHorizontal = false // No horizontal channels anymore - all use vertical posters

                when {
                    isHorizontal -> {
                        ContentCard(
                            vodContent = vodContent,
                            channelNumber = String.format("%03d", (actualRowIndex * 10 + colIndex + 1)),
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(actualRowIndex, colIndex) },
                            sx = sx,
                            sy = sy,
                            lazyListState = lazyListState
                        )
                    }
                    channel == "Top 10" -> {
                        Top10ContentCard(
                            vodContent = vodContent,
                            rankingNumber = colIndex + 1,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(actualRowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }
                    else -> {
                        VodContentCard(
                            vodContent = vodContent,
                            isFocused = isItemFocused,
                            focusRequester = focusRequester,
                            onFocusChange = { onChannelContentFocusChange(actualRowIndex, colIndex) },
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }

            // Spacer items - different sizes for Top 10 vs vertical posters
            items(8) {
                val isTop10 = channel == "Top 10"

                val spacerWidth = when {
                    isTop10 -> sx(261)
                    else -> sx(220) // Vertical poster width (all new channels)
                }

                val spacerHeight = when {
                    isTop10 -> sy(324)
                    else -> sy(380) // Vertical poster height (all new channels)
                }

                Spacer(
                    modifier = Modifier
                        .width(spacerWidth)
                        .height(spacerHeight)
                )
            }
        }

        // Details overlay
        if (isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(14))
                    ) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.width(sx(1500))
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(16)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstVisibleContent.category,
                                color = Color(0xCCEEEEEE),
                                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp,
                                letterSpacing = (0.4 * (sy(1).value / 1.dp.value)).sp
                            )
                        }

                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }

        // CategoryIcon
        Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
            val categoryIsFocused = actualRowIndex == focusedRowIndex && focusedColIndex == -1
            val categoryFocusRequester = channelFocusRequesters[Pair(actualRowIndex, -1)]

            // Logo dla różnych channeli
            val logoUrl = if (channel == "Cinemax") {
                "https://n-1411-1.dcs.redcdn.pl/scale/play/playtv/upload/live/19195238/images/1050844145?srcmode=3&srcx=0&srcy=0&srcw=1&srch=1&dstw=512&dsth=512&type=0"
            } else null

            val logoDrawableId = when (channel) {
                "Kino Play", "Polecane", "Top 10", "Ostatnio dodane" -> R.drawable.kinoplay2
                "Seriale", "Filmy fabularne" -> R.drawable.wideo_kat
                else -> null
            }

            // VOD channels that should be text-only (like WIDEO style)
            val isVodCategoryChannel = channel in listOf("Akcja", "Komedie", "Horror", "Biograficzne")

            CategoryIcon(
                text = channel,
                isFocused = categoryIsFocused,
                onClick = {
                    // Navigate to KinoGridScreen with category-specific filtered content
                    if (isVodCategoryChannel) {
                        val vodList = VodDataCache.getKinoPlayMovies()
                        val categoryFilter = when (channel) {
                            "Akcja" -> "Akcja|Action"
                            "Komedie" -> "Komedia|Comedy"
                            "Horror" -> "Horror"
                            "Biograficzne" -> "Biograficzny|Biography|Biographical"
                            else -> channel
                        }
                        val filtered = filterMoviesByCategory(vodList, categoryFilter)
                        onNavigateToKinoGrid(channel, filtered, "KINO_PLAY")
                    }
                },
                onFocused = { isFocused ->
                    if (isFocused) {
                        Log.d("VOD_DEBUG", "CategoryIcon '$channel' (row $actualRowIndex) gained focus")
                        onChannelContentFocusChange(actualRowIndex, -1)
                    }
                },
                focusRequester = categoryFocusRequester ?: FocusRequester(),
                sx = sx,
                sy = sy,
                logoUrl = if (!isVodCategoryChannel) logoUrl else null,
                logoDrawableId = if (!isVodCategoryChannel) logoDrawableId else null,
                showIcon = !isVodCategoryChannel,                    // text-only dla kategorii VOD
                showBackgroundWhenFocused = isVodCategoryChannel     // czarne tło dla kategorii VOD
            )
        }
    }
}

@Composable
private fun ContentCard(
    vodContent: VodContent,
    channelNumber: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState,
    onClick: () -> Unit = {}
) {
    val itemWidth = sx(368)
    val itemHeight = sy(208)

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12)))
            .then(
                if (isFocused) Modifier.border(
                    width = (6 * sx(1).value / 1.dp.value).dp,
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        AsyncImage(
            model = vodContent.imageUrl,
            contentDescription = vodContent.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sy(88))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    ),
                    shape = RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )

        // Channel number
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = sx(12), top = sy(12))
                .border(width = sx(1), color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(sx(4)))
                .padding(horizontal = sx(12), vertical = sy(8)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelNumber,
                color = Color.White,
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Title
        Text(
            text = vodContent.title,
            color = Color.White,
            fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(12), bottom = sy(12), end = sx(12))
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SliderMiniatureCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val itemWidth = sx(368)
    val itemHeight = sy(208)

    // ExoPlayer for TV Live
    val player = remember {
        com.google.android.exoplayer2.ExoPlayer.Builder(context).build()
    }

    // Play TV stream when focused
    LaunchedEffect(isFocused, vodContent.link) {
        if (isFocused && vodContent.link.isNotEmpty()) {
            try {
                val mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(vodContent.link)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                android.util.Log.e("SliderMiniatureCard", "Error playing TV stream: ${e.message}")
            }
        } else {
            player.stop()
            player.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12)))
            .then(
                if (isFocused) Modifier.border(
                    width = (6 * sx(1).value / 1.dp.value).dp,
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable()
    ) {
        // ExoPlayer view
        if (isFocused && vodContent.link.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    com.google.android.exoplayer2.ui.PlayerView(ctx).apply {
                        this.player = player
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Static placeholder when not focused
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1a1a1a))
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sy(80))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black
                        )
                    ),
                    shape = RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )

        // Channel title
        Text(
            text = vodContent.title,
            color = Color.White,
            fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(16), bottom = sy(12))
        )

        // "LIVE" badge
        if (isFocused && vodContent.category == "Live TV") {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = sx(12), top = sy(12))
                    .background(Color.Red, shape = RoundedCornerShape(sx(4)))
                    .padding(horizontal = sx(8), vertical = sy(4)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SliderMaxCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .width(sx(1328))
            .height(sy(742)),
        shape = RoundedCornerShape(sx(20)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF5B3987)
        ),
        border = if (isFocused) BorderStroke(sx(10), Color(0xFF5FEDD4)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 16.dp else 8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Background image
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                if (vodContent.imageUrl.isNotBlank()) {
                    val imageRequest = remember(vodContent.imageUrl) {
                        ImageRequest.Builder(context)
                            .data(vodContent.imageUrl)
                            .size(Size.ORIGINAL)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .width(sx(1028))
                            .height(sy(742))
                            .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20))),
                        contentScale = ContentScale.Crop
                    )
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF5A3887), Color(0x005A3887)),
                                endX = sx(1028).value * 0.75f
                            )
                        )
                )
            }

            // Content overlay (CenterStart)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sx(874))
                    .padding(sx(100))
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(sy(31))
            ) {
                // Platform logo (168x168)
                if (vodContent.channelLogoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = vodContent.channelLogoUrl,
                        contentDescription = "Channel Logo",
                        modifier = Modifier.size(sx(168)).clip(RoundedCornerShape(8.dp))
                    )
                }

                // Content info
                Column(verticalArrangement = Arrangement.spacedBy(sy(14))) {
                    // Title
                    Text(
                        text = vodContent.title.ifBlank { "Bez tytułu" },
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(64).value.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Metadata row with separators
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sx(16))
                    ) {
                        Text(
                            text = vodContent.category.ifBlank { "Ogólne" },
                            color = Color(0xFFEEEEEE).copy(alpha = 0.8f),
                            fontSize = sy(20).value.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = sy(28).value.sp,
                            letterSpacing = 0.4.sp
                        )
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(sy(24))
                                .background(Color(0xCCEEEEEE))
                        )
                        Text("25 min", color = Color(0xFFEEEEEE).copy(alpha = 0.8f), fontSize = sy(20).value.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(sy(24))
                                .background(Color(0xCCEEEEEE))
                        )
                        Text("2020 r.", color = Color(0xFFEEEEEE).copy(alpha = 0.8f), fontSize = sy(20).value.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(sy(24))
                                .background(Color(0xCCEEEEEE))
                        )
                        Text("Polska", color = Color(0xFFEEEEEE).copy(alpha = 0.8f), fontSize = sy(20).value.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(sy(24))
                                .background(Color(0xCCEEEEEE))
                        )
                        Text("7 lat", color = Color(0xFFEEEEEE).copy(alpha = 0.8f), fontSize = sy(20).value.sp, fontWeight = FontWeight.Bold)
                    }

                    // Description (max 3 lines)
                    Text(
                        text = vodContent.description.ifBlank { "Brak opisu" },
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(28).value.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (sy(28).value * 1.43).sp
                    )
                }
            }

            // Invisible focusable Button (for focus management)
            Button(
                onClick = { },
                modifier = Modifier
                    .width(sx(300))
                    .height(sy(600))
                    .align(Alignment.TopStart)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onFocusChange()
                        }
                    },
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) { }
        }
    }
}

/**
 * Channel ID to Live Stream URL mapping
 * Maps EPG channel IDs to their live TV stream URLs
 * Keys must match exact channelId from EPG XML (e.g., "TVP 1" with space)
 */
private object ChannelStreamMapping {
    private val streamUrls = mapOf(
        "TVP 1" to "https://ec06-krk3.cache.orange.pl/dai4/org1/vb/104/tvp1hd/index.m3u8",
        "Polsat" to "https://lb2-e2-19.pluscdn.pl/ch/1502600/308/dash/20a18c30/live.mpd",
        "Polsat News" to "http://cdn-s-lb2.pluscdn.pl/lv/1517830/349/dash/81ec4c32/live.mpd",
        "Polsat News Polityka" to "https://lb2-e3-20.pluscdn.pl/lv/1511888/322/dash/52a9b70b/live.mpd",
        "Polsat Viasat Nature" to "https://liveovh010.cda.pl/enc104/polsatviasatnaturehdraw/polsatviasatnaturehdraw.mpd",
        "4FUN TV" to "https://stream.4fun.tv:8888/hls/4f.m3u8",
        "Viasat Explore Classic" to "https://da9c49fa.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLXBsX1ZpYXNhdEV4cGxvcmVfSExT/playlist.m3u8",
        "Euronews Polska" to "https://7060743b4b224241b86325460b14d152.mediatailor.eu-west-1.amazonaws.com/v1/master/0547f18649bd788bec7b67b746e47670f558b6b2/production-LiveChannel-6769/bitok/eyJzdGlkIjoiMTE3OTllNGEtYmU1OC00ZjQyLTkxOTYtY2VlYWQzZGU2MDJjIiwibWt0IjoicGwiLCJjaCI6Njc2OSwicHRmIjo1fQ==/26235/euronews-pl.m3u8",
        "Top Movies Polska" to "https://top-movies-rakuten-tv-pl.fast.rakuten.tv/v1/master/0547f18649bd788bec7b67b746e47670f558b6b2/production-LiveChannel-6059/master.m3u8"
    )

    fun getStreamUrl(channelId: String): String? {
        return streamUrls[channelId]
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CollectionSliderCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    onClick: () -> Unit = {},
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val cardWidth = sx(825)  // 16:9 aspect ratio (825x464)
    val cardHeight = sy(464)

    // Detect if this is an EPG card (link contains timestamps)
    val isEpgCard = vodContent.link.contains("|")

    // Parse EPG data if available
    val epgData = if (isEpgCard) {
        val parts = vodContent.link.split("|")
        if (parts.size >= 4) {
            val startUtc = java.time.Instant.parse(parts[0])
            val endUtc = java.time.Instant.parse(parts[1])
            val channelId = parts[2]
            val channelName = parts[3]

            // Calculate progress
            val now = java.time.Instant.now()
            val totalDuration = java.time.Duration.between(startUtc, endUtc).toMillis().toFloat()
            val elapsed = java.time.Duration.between(startUtc, now).toMillis().toFloat()
            val progress = if (totalDuration > 0) (elapsed / totalDuration).coerceIn(0f, 1f) else 0f

            // Format time
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            val zoneId = java.time.ZoneId.systemDefault()
            val startTime = startUtc.atZone(zoneId).format(timeFormatter)
            val endTime = endUtc.atZone(zoneId).format(timeFormatter)

            Triple(progress, "$channelName | $startTime - $endTime", channelName)
        } else null
    } else null

    // Live TV playback support for EPG cards
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlayingLive by remember { mutableStateOf(false) }
    var isLoadingStream by remember { mutableStateOf(false) }

    // ExoPlayer instance
    val player = remember {
        com.google.android.exoplayer2.ExoPlayer.Builder(context).build()
    }

    // Extract channel ID from EPG data
    val channelId = if (isEpgCard) {
        val parts = vodContent.link.split("|")
        if (parts.size >= 4) parts[2] else null
    } else null

    // Start live TV immediately on focus
    LaunchedEffect(isFocused, channelId) {
        if (isFocused && isEpgCard && channelId != null) {
            isLoadingStream = false
            isPlayingLive = false
            player.stop()

            // Small delay to avoid loading streams when quickly scrolling (1 second)
            kotlinx.coroutines.delay(1000)

            // After delay, check if still focused
            if (isFocused) {
                val streamUrl = ChannelStreamMapping.getStreamUrl(channelId)
                if (streamUrl != null) {
                    try {
                        isLoadingStream = true
                        val mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(streamUrl)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.playWhenReady = true
                        isPlayingLive = true
                        isLoadingStream = false
                    } catch (e: Exception) {
                        isLoadingStream = false
                        isPlayingLive = false
                    }
                }
            }
        } else {
            // Lost focus or not EPG card - stop playback
            isPlayingLive = false
            isLoadingStream = false
            player.stop()
        }
    }

    // Cleanup player on disposal
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocusChange()
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)) {
                    android.util.Log.d("COLLECTION_SLIDER_CLICK", "OK pressed on: ${vodContent.title}")
                    // Zatrzymaj mini player jeśli jest aktywny
                    if (isPlayingLive) {
                        player.stop()
                        isPlayingLive = false
                    }
                    // Wywołaj callback - otwórz LiveScreen
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(),
        shape = RoundedCornerShape(sx(10)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF5B3987)
        ),
        border = if (isFocused) BorderStroke(sx(8), Color(0xFF5FEDD4)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 12.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background image with gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(sx(10)))
            ) {
                // Background image from collection imageUrl
                AsyncImage(
                    model = vodContent.imageUrl,
                    contentDescription = vodContent.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Live TV player overlay (replaces static image when playing)
                if (isPlayingLive) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            com.google.android.exoplayer2.ui.PlayerView(ctx).apply {
                                this.player = player
                                useController = false
                                controllerAutoShow = false
                                controllerHideOnTouch = true
                                hideController()
                                setShowBuffering(com.google.android.exoplayer2.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Loading indicator
                if (isLoadingStream) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Color(0xFF5FEDD4),
                            modifier = Modifier.size(sx(60))
                        )
                    }
                }

                // Gradient overlay (EPG: vertical, Collections: horizontal)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = if (isEpgCard) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFF5A3887).copy(alpha = 0.6f),
                                        Color(0xFF5A3887).copy(alpha = 0.9f)
                                    ),
                                    startY = 0f,
                                    endY = 1500f
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF5A3887),
                                        Color(0xFF5A3887).copy(alpha = 0f)
                                    ),
                                    startX = 0f,
                                    endX = 1000f
                                )
                            }
                        )
                )
            }

            // Content overlay (EPG layout vs Collection layout)
            if (isEpgCard && epgData != null) {
                // EPG Layout: Title → Metadata → Channel+Time → Progress Bar
                val (progress, channelAndTime, _) = epgData
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = sx(40), bottom = sy(40), end = sx(40))
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(sy(12))
                ) {
                    // Program title (TOP)
                    Text(
                        text = vodContent.title,
                        color = Color(0xFFEEEEEE),
                        fontSize = (36 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = (48 * (sy(1).value / 1.dp.value)).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Metadata (category field)
                    if (vodContent.category.isNotBlank()) {
                        Text(
                            text = vodContent.category,
                            color = Color(0xFFEEEEEE).copy(alpha = 0.8f),
                            fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Channel name + time
                    Text(
                        text = channelAndTime,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.9f),
                        fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Progress bar (BOTTOM)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sy(6))
                            .clip(RoundedCornerShape(sx(3)))
                            .background(Color.White.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color(0xFF5FEDD4))
                        )
                    }
                }

                // Channel logo in top right corner (only for EPG cards)
                if (vodContent.channelLogoUrl.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = sy(40), end = sx(40))
                            .size(sx(90), sy(90))
                            .clip(RoundedCornerShape(sx(8)))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        AsyncImage(
                            model = vodContent.channelLogoUrl,
                            contentDescription = "Channel logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            } else {
                // Collection Layout: Title + Description + Logo
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = sx(100), bottom = sy(100), end = sx(100))
                        .width(sx(442)),
                    verticalArrangement = Arrangement.spacedBy(sy(20))
                ) {
                    // Collection title
                    Text(
                        text = vodContent.title,
                        color = Color(0xFFEEEEEE),
                        fontSize = (41 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (57 * (sy(1).value / 1.dp.value)).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Collection description
                    if (vodContent.description.isNotBlank()) {
                        Text(
                            text = vodContent.description,
                            color = Color(0xFFEEEEEE).copy(alpha = 0.8f),
                            fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Logo in bottom right corner (only for collections)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = sx(40), bottom = sy(40))
                        .size(sx(90), sy(90))
                        .clip(RoundedCornerShape(sx(8)))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Placeholder for logo
                    Text(
                        text = vodContent.category.take(1),
                        color = Color.White,
                        fontSize = (40 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// EPG Collection Slider Card for TELEWIZJA "Kategorie EPG" channel
@Composable
private fun EpgCollectionSliderCard(
    epgProgram: com.uxellence.tv.v3.epg.EpgProgram,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val cardWidth = sx(977)
    val cardHeight = sy(464)

    // Calculate progress (how much of the program has elapsed)
    val now = java.time.Instant.now()
    val totalDuration = java.time.Duration.between(epgProgram.startUtc, epgProgram.endUtc).toMillis().toFloat()
    val elapsed = java.time.Duration.between(epgProgram.startUtc, now).toMillis().toFloat()
    val progress = if (totalDuration > 0) (elapsed / totalDuration).coerceIn(0f, 1f) else 0f

    // Format time
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val zoneId = java.time.ZoneId.systemDefault()
    val startTime = epgProgram.startUtc.atZone(zoneId).format(timeFormatter)
    val endTime = epgProgram.endUtc.atZone(zoneId).format(timeFormatter)

    // Get channel name from channelId
    val channelName = com.uxellence.tv.v3.utils.EpgAdapter.getChannelNameFromEpgId(epgProgram.channelId)

    Card(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocusChange()
                }
            }
            .focusable(),
        shape = RoundedCornerShape(sx(10)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF5B3987)
        ),
        border = if (isFocused) BorderStroke(sx(8), Color(0xFF5FEDD4)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 12.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Background image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(sx(10)))
            ) {
                AsyncImage(
                    model = epgProgram.iconUrl ?: "https://epg.ovh/logo/${channelName.lowercase().replace(" ", "-")}.png",
                    contentDescription = epgProgram.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient overlay (darker for text readability)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF5A3887).copy(alpha = 0.6f),
                                    Color(0xFF5A3887).copy(alpha = 0.9f)
                                ),
                                startY = 0f,
                                endY = 1500f
                            )
                        )
                )
            }

            // Content overlay (vertical layout)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = sx(40), bottom = sy(40), end = sx(40))
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(sy(12))
            ) {
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(6))
                        .clip(RoundedCornerShape(sx(3)))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Color(0xFF5FEDD4))
                    )
                }

                // Channel name + time
                Text(
                    text = "$channelName | $startTime - $endTime",
                    color = Color(0xFFEEEEEE).copy(alpha = 0.9f),
                    fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Medium
                )

                // Metadata (parsed from description)
                val metadata = com.uxellence.tv.v3.utils.EpgAdapter.buildMetadataStringPublic(epgProgram)
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.8f),
                        fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Categories
                val categoriesText = epgProgram.categories.joinToString(" • ")
                if (categoriesText.isNotBlank()) {
                    Text(
                        text = categoriesText,
                        color = Color(0xFFEEEEEE).copy(alpha = 0.8f),
                        fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Program title
                Text(
                    text = epgProgram.title,
                    color = Color(0xFFEEEEEE),
                    fontSize = (36 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = (48 * (sy(1).value / 1.dp.value)).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VerticalContentCard(
    vodContent: VodContent,
    channelNumber: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val itemWidth = sx(220)
    val itemHeight = sy(280)

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12)))
            .then(
                if (isFocused) Modifier.border(
                    width = (6 * sx(1).value / 1.dp.value).dp,
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable()
    ) {
        AsyncImage(
            model = vodContent.imageUrl,
            contentDescription = vodContent.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sy(100))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black
                        )
                    ),
                    shape = RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )

        // Channel number
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = sx(12), top = sy(12))
                .border(width = sx(1), color = Color.White.copy(alpha = 0.4f), shape = RoundedCornerShape(sx(4)))
                .padding(horizontal = sx(12), vertical = sy(8)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelNumber,
                color = Color.White,
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Title
        Text(
            text = vodContent.title,
            color = Color.White,
            fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(12), bottom = sy(12), end = sx(12))
        )
    }
}

@Composable
private fun VodShortcutsV3Row(
    actualRowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    onNavigateToKinoGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val configuration = LocalConfiguration.current

    val shortcuts = remember {
        listOf(
            ShortcutItem("1", "AKCJA", ShortcutIcon.VectorIcon(R.drawable.ic_shortcut_akcja), categoryFilter = "Akcja|Action"),
            ShortcutItem("2", "BIOGRAFICZNY", ShortcutIcon.VectorIcon(R.drawable.ic_shortcut_biograficzny), categoryFilter = "Biograficzny|Biography|Biographical"),
            ShortcutItem("3", "DOKUMENTALNE", ShortcutIcon.VectorIcon(R.drawable.ic_shortcut_dokumentalne), categoryFilter = "Dokumentalny|Documentary"),
            ShortcutItem("4", "PRZYGODOWE", ShortcutIcon.VectorIcon(R.drawable.ic_shortcut_przygodowe), categoryFilter = "Przygodowy|Adventure"),
            ShortcutItem("5", "HORROR", ShortcutIcon.VectorIcon(R.drawable.ic_shortcut_horror), categoryFilter = "Horror"),
            ShortcutItem("6", "FILMY POLSKIE", ShortcutIcon.VectorIcon(R.drawable.ic_shortcut_filmy_polskie), categoryFilter = "Polski|Polish")
        )
    }

    val isCurrentRow = actualRowIndex == focusedRowIndex

    // Skróty v3: NO expansion animation - keep constant size
    // Horizontal LazyRow with 6 shortcuts (Figma: card width 235px + spacing 24px)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(sy(208)), // Figma: 208px card height - fixed, no animation
        contentPadding = PaddingValues(
            start = sx(80), // Align with CategoryIcon position
            end = sx(20)
        ),
        horizontalArrangement = Arrangement.spacedBy(sx(24)) // Figma: 24px spacing
    ) {
        itemsIndexed(shortcuts) { index, shortcut ->
            val isFocused = isCurrentRow && focusedColIndex == index
            val focusRequester = channelFocusRequesters[Pair(actualRowIndex, index)] ?: FocusRequester()

            ShortcutCard(
                shortcut = shortcut,
                isFocused = isFocused,
                focusRequester = focusRequester,
                sx = sx,
                sy = sy,
                onFocusChange = { focused ->
                    if (focused) onChannelContentFocusChange(actualRowIndex, index)
                },
                onClick = {
                    // Navigate to KinoGridScreen with shortcut-specific filtered content
                    val vodList = VodDataCache.getKinoPlayMovies()
                    val filtered = if (shortcut.categoryFilter != null) {
                        filterMoviesByCategory(vodList, shortcut.categoryFilter)
                    } else {
                        vodList.shuffled().take(10)
                    }
                    onNavigateToKinoGrid(shortcut.title, filtered, "KINO_PLAY")
                }
            )
        }
    }
}

@Composable
private fun VodContentCard(
    vodContent: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    expiryText: String? = null
) {
    val itemWidth = sx(220)
    val itemHeight = sy(380)

    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f)

    // Generate random expiry day for rented content
    val expiryDay = remember {
        listOf("piątek", "sobota", "niedziela", "poniedziałek").random()
    }

    Column(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(16))
    ) {
        Box(
            modifier = Modifier
                .width(sx(200))
                .height(sy(280))
                .clip(RoundedCornerShape(sx(12)))
                .border(
                    width = if (isFocused) (6 * sx(1).value / 1.dp.value).dp else 0.dp,
                    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                )
        ) {
            AsyncImage(
                model = vodContent.imageUrl,
                contentDescription = vodContent.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (isFocused) {
            Text(
                text = expiryText?.let { "oglądaj do: $expiryDay" } ?: "10,00 zł",
                color = Color(0xFFEEEEEE),
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun AppIconCard(
    app: AppItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .width(sx(320))
            .height(sy(220))  // Zwiększone z 190 na 220 dla tekstu
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sy(8))
    ) {
        Box(
            modifier = Modifier
                .size(sx(300), sy(170))
                .clip(RoundedCornerShape(sx(12)))
                .border(
                    width = if (isFocused) (6 * sx(1).value / 1.dp.value).dp else 0.dp,
                    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                    shape = RoundedCornerShape(sx(12))
                )
        ) {
            Image(
                painter = painterResource(id = app.iconResId),
                contentDescription = app.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (isFocused) {
            Text(
                text = app.name,
                color = Color(0xFFEEEEEE),
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.W700,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PackageCard(
    packageItem: PackageItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val itemWidth = sx(368)
    val itemHeight = sy(208)

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12)))
            .then(
                if (isFocused) Modifier.border(
                    width = (6 * sx(1).value / 1.dp.value).dp,
                    color = Color(0xFF5AECD3),
                    shape = RoundedCornerShape(sx(12))
                ) else Modifier
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable()
    ) {
        // Black background with 50% opacity
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        // Package image centered (larger fill for better visibility)
        AsyncImage(
            model = packageItem.imageUrl,
            contentDescription = packageItem.title,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun Top10ContentCard(
    vodContent: VodContent,
    rankingNumber: Int,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val itemWidth = sx(261)
    val itemHeight = sy(324)

    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f)

    // Mapowanie numeru na drawable resource
    val rankingDrawable = when (rankingNumber) {
        1 -> R.drawable.top10_1
        2 -> R.drawable.top10_2
        3 -> R.drawable.top10_3
        4 -> R.drawable.top10_4
        5 -> R.drawable.top10_5
        6 -> R.drawable.top10_6
        7 -> R.drawable.top10_7
        8 -> R.drawable.top10_8
        9 -> R.drawable.top10_9
        10 -> R.drawable.top10_10
        else -> null
    }

    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusChange()
            }
            .focusable()
    ) {
        // Duży numer rankingu po lewej
        if (rankingDrawable != null) {
            Icon(
                painter = painterResource(id = rankingDrawable),
                contentDescription = "Ranking $rankingNumber",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(sx(61), sy(115)),
                tint = if (isFocused) Color(0xFF5AECD3) else Color(0xFFEEEEEE)
            )
        }

        // Kolumna z plakatem i tekstem po prawej (left: 61px)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(sx(200)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sy(16))
        ) {
            // Plakat 200x280px
            Box(
                modifier = Modifier
                    .width(sx(200))
                    .height(sy(280))
                    .clip(RoundedCornerShape(sx(12)))
                    .border(
                        width = if (isFocused) (6 * sx(1).value / 1.dp.value).dp else 0.dp,
                        color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                        shape = RoundedCornerShape(sx(12))
                    )
            ) {
                AsyncImage(
                    model = vodContent.imageUrl,
                    contentDescription = vodContent.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Cena (tylko gdy jest fokus)
            if (isFocused) {
                Text(
                    text = "10,00 zł",
                    textAlign = TextAlign.Center,
                    color = Color(0xFFEEEEEE),
                    fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

// VOD Screen spacing constants
private const val VOD_FIXED_FOCUS_Y = 340 // Focused channel always at this height
// Vertical posters (Kino Play, Polecane, Top 10, Ostatnio dodane) - large spacing
private const val VOD_VERTICAL_NORMAL_ROW_HEIGHT = 346 // CategoryIcon (216px) + spacing (130px)
private const val VOD_VERTICAL_EXPANDED_ROW_HEIGHT = 636 // CategoryIcon (216px) + miniatures (290px) + spacing (130px)
// Horizontal miniatures (Seriale, Filmy fabularne, Cinemax) - small spacing like MOJE
private const val VOD_HORIZONTAL_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + spacing (40px)
private const val VOD_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546 // CategoryIcon (216px) + miniatures (290px) + spacing (40px)
private const val VOD_CONTENT_FOCUS_EXTRA_SPACING = 100 // Extra spacing above focused content row

private fun calculateVodChannelYPosition(
    channelIndex: Int,
    channelName: String,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    firstChannelBaseY: Int = 280
): androidx.compose.ui.unit.Dp {
    // Convert channelIndex to rowIndex (channels start at row 2)
    val rowIndex = channelIndex + 2

    // Determine if channel is horizontal, vertical, or Skróty v3
    val isHorizontal = channelName in emptyList<String>() // No horizontal channels anymore - all use vertical posters
    val isShortcutsV3 = channelName == "Skróty v3"

    val normalRowHeight = when {
        isHorizontal -> VOD_HORIZONTAL_NORMAL_ROW_HEIGHT
        isShortcutsV3 -> 278 // Figma: 208px card + 70px spacing
        else -> VOD_VERTICAL_NORMAL_ROW_HEIGHT
    }

    val expandedRowHeight = when {
        isHorizontal -> VOD_HORIZONTAL_EXPANDED_ROW_HEIGHT
        isShortcutsV3 -> 278 // Same as normal - no expansion for Skróty v3
        else -> VOD_VERTICAL_EXPANDED_ROW_HEIGHT
    }

    return when {
        // When on menu (row 0) or slider (row 1) - channels peek 90px from bottom
        focusedRowIndex <= 1 -> {
            // Calculate cumulative height for all channels above this one
            var cumulativeHeight = 990
            for (i in 0 until channelIndex) {
                val prevChannelName = channels.getOrNull(i) ?: ""
                val prevIsHorizontal = prevChannelName in emptyList<String>() // No horizontal channels anymore - all use vertical posters
                val prevIsShortcutsV3 = prevChannelName == "Skróty v3"
                cumulativeHeight += when {
                    prevIsHorizontal -> VOD_HORIZONTAL_NORMAL_ROW_HEIGHT
                    prevIsShortcutsV3 -> 278
                    else -> VOD_VERTICAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight)
        }
        // Focused channel - always at Y: 340px (VOD_FIXED_FOCUS_Y)
        rowIndex == focusedRowIndex -> {
            sy(VOD_FIXED_FOCUS_Y)
        }
        // Channels above focused - scroll up
        rowIndex < focusedRowIndex -> {
            // Extra 100px spacing when content is focused (focusedColIndex >= 0)
            val extraSpacing = if (focusedColIndex >= 0) VOD_CONTENT_FOCUS_EXTRA_SPACING else 0
            // Calculate cumulative height from focused to this channel
            var cumulativeHeight = VOD_FIXED_FOCUS_Y
            for (i in channelIndex until focusedRowIndex - 2) {
                val betweenChannelName = channels.getOrNull(i + 1) ?: ""
                val betweenIsHorizontal = betweenChannelName in emptyList<String>() // No horizontal channels anymore - all use vertical posters
                val betweenIsShortcutsV3 = betweenChannelName == "Skróty v3"
                cumulativeHeight -= when {
                    betweenIsHorizontal -> VOD_HORIZONTAL_NORMAL_ROW_HEIGHT
                    betweenIsShortcutsV3 -> 278
                    else -> VOD_VERTICAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight - extraSpacing)
        }
        // Channels below focused - scroll down to make room
        rowIndex > focusedRowIndex -> {
            // Check if focused channel is expanded (content focused)
            val focusedChannelName = channels.getOrNull(focusedRowIndex - 2) ?: ""
            val focusedIsHorizontal = focusedChannelName in emptyList<String>() // No horizontal channels anymore - all use vertical posters
            val focusedIsShortcutsV3 = focusedChannelName == "Skróty v3"
            val focusedChannelExpansion = if (focusedColIndex >= 0) {
                when {
                    focusedIsHorizontal -> VOD_HORIZONTAL_EXPANDED_ROW_HEIGHT
                    focusedIsShortcutsV3 -> 278 // Same as normal - no expansion for Skróty v3
                    else -> VOD_VERTICAL_EXPANDED_ROW_HEIGHT
                }
            } else {
                when {
                    focusedIsHorizontal -> VOD_HORIZONTAL_NORMAL_ROW_HEIGHT
                    focusedIsShortcutsV3 -> 278
                    else -> VOD_VERTICAL_NORMAL_ROW_HEIGHT
                }
            }
            // 30px extra spacing for vertical channels when content is focused
            val verticalExtraSpacing = if (!focusedIsHorizontal && focusedColIndex >= 0) 30 else 0
            // Calculate cumulative height from focused to this channel
            var cumulativeHeight = VOD_FIXED_FOCUS_Y + focusedChannelExpansion + verticalExtraSpacing
            for (i in (focusedRowIndex - 2 + 1) until channelIndex) {
                val betweenChannelName = channels.getOrNull(i) ?: ""
                val betweenIsHorizontal = betweenChannelName in emptyList<String>() // No horizontal channels anymore - all use vertical posters
                val betweenIsShortcutsV3 = betweenChannelName == "Skróty v3"
                cumulativeHeight += when {
                    betweenIsHorizontal -> VOD_HORIZONTAL_NORMAL_ROW_HEIGHT
                    betweenIsShortcutsV3 -> 278
                    else -> VOD_VERTICAL_NORMAL_ROW_HEIGHT
                }
            }
            sy(cumulativeHeight)
        }
        // Fallback (should not happen)
        else -> sy(140 + rowIndex * normalRowHeight)
    }
}

// Helper to detect if channel is Skróty v3 (shortcuts without CategoryIcon)
private fun isShortcutsV3Channel(rowIndex: Int, channels: List<String>): Boolean {
    val channelIndex = rowIndex - 2
    return channels.getOrNull(channelIndex) == "Skróty v3"
}

fun handleVodNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false

    when (event.key) {
        Key.DirectionUp -> {
            android.util.Log.d("VOD_NAV", "UP pressed: focusedRowIndex=$focusedRowIndex, focusedColIndex=$focusedColIndex")
            when {
                focusedRowIndex == 1 -> {
                    // From slider to menu
                    android.util.Log.d("VOD_NAV", "Going from slider (row 1) to menu (row 0)")
                    onReturnToMenu()
                }
                focusedRowIndex == 2 -> {
                    // From first channel
                    if (focusedColIndex == -1) {
                        // From CategoryIcon of first channel - go to slider
                        android.util.Log.d("VOD_NAV", "Going from first channel CategoryIcon (row 2) to slider (row 1)")
                        onFocusChange(1, 0)
                        // Slider handles its own focus via LaunchedEffect(isFocused)
                    } else {
                        // From content of first channel - go to CategoryIcon of first channel
                        android.util.Log.d("VOD_NAV", "Going from first channel content (row 2, col $focusedColIndex) to CategoryIcon (row 2, col -1)")
                        onFocusChange(2, -1)
                        channelFocusRequesters[Pair(2, -1)]?.requestFocus()
                    }
                }
                focusedRowIndex > 2 -> {
                    // Between channels - smart targeting for Skróty v3
                    val newRowIndex = focusedRowIndex - 1
                    val currentIsShortcutsV3 = isShortcutsV3Channel(focusedRowIndex, channels)
                    val targetIsShortcutsV3 = isShortcutsV3Channel(newRowIndex, channels)

                    val targetColIndex = when {
                        targetIsShortcutsV3 -> 0 // Go to first shortcut (no CategoryIcon)
                        currentIsShortcutsV3 -> -1 // Coming from shortcuts, go to CategoryIcon
                        focusedColIndex == -1 -> -1 // Preserve CategoryIcon
                        else -> 0 // Preserve content
                    }

                    android.util.Log.d("VOD_NAV", "Going from channel row $focusedRowIndex to row $newRowIndex, targetCol=$targetColIndex")
                    onFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                }
            }
            return true
        }

        Key.DirectionDown -> {
            when {
                focusedRowIndex == 1 -> {
                    // From slider to first channel
                    onFocusChange(2, -1) // Start at CategoryIcon
                    channelFocusRequesters[Pair(2, -1)]?.requestFocus()
                }
                focusedRowIndex < channels.size + 1 -> {
                    // Between channels - smart targeting for Skróty v3
                    val newRowIndex = focusedRowIndex + 1
                    val currentIsShortcutsV3 = isShortcutsV3Channel(focusedRowIndex, channels)
                    val targetIsShortcutsV3 = isShortcutsV3Channel(newRowIndex, channels)

                    val targetColIndex = when {
                        targetIsShortcutsV3 -> 0 // Go to first shortcut (no CategoryIcon)
                        currentIsShortcutsV3 -> -1 // Coming from shortcuts, go to CategoryIcon
                        focusedColIndex == -1 -> -1 // Preserve CategoryIcon
                        else -> 0 // Preserve content
                    }

                    onFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                }
            }
            return true
        }

        Key.DirectionLeft -> {
            if (focusedRowIndex == 1) {
                // Slider navigation handled by VodHeroSlider
                return false
            }

            // Special handling for Skróty v3
            val isShortcutsV3 = isShortcutsV3Channel(focusedRowIndex, channels)

            if (isShortcutsV3) {
                // Shortcuts v3: move between items 0-5
                when {
                    focusedColIndex > 0 -> {
                        // Move left within shortcuts
                        onFocusChange(focusedRowIndex, focusedColIndex - 1)
                        channelFocusRequesters[Pair(focusedRowIndex, focusedColIndex - 1)]?.requestFocus()
                    }
                    focusedColIndex == 0 -> {
                        // At first shortcut - can't go left (no CategoryIcon)
                        return true
                    }
                }
            } else {
                // Regular channel logic
                if (focusedColIndex == -1) {
                    // Already on CategoryIcon
                    return true
                } else if (focusedColIndex == 0) {
                    // Check if can scroll left
                    val channelIndex = focusedRowIndex - 2
                    val lazyListState = lazyListStates[channelIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        coroutineScope.launch {
                            val newIndex = lazyListState.firstVisibleItemIndex - 1
                            lazyListState.animateScrollToItem(newIndex)
                            // Wait for animation and set focus
                            delay(50)
                            channelFocusRequesters[Pair(focusedRowIndex, newIndex)]?.requestFocus()
                        }
                    } else {
                        // Go to CategoryIcon
                        onFocusChange(focusedRowIndex, -1)
                        channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                    }
                }
            }
            return true
        }

        Key.DirectionRight -> {
            if (focusedRowIndex == 1) {
                // Slider navigation handled by VodHeroSlider
                return false
            }

            // Special handling for Skróty v3
            val isShortcutsV3 = isShortcutsV3Channel(focusedRowIndex, channels)

            if (isShortcutsV3) {
                // Shortcuts v3: move between items 0-5 (6 items total)
                when {
                    focusedColIndex < 5 -> {
                        // Move right within shortcuts
                        onFocusChange(focusedRowIndex, focusedColIndex + 1)
                        channelFocusRequesters[Pair(focusedRowIndex, focusedColIndex + 1)]?.requestFocus()
                    }
                    focusedColIndex == 5 -> {
                        // At last shortcut - can't go right
                        return true
                    }
                }
            } else {
                // Regular channel logic
                if (focusedColIndex == -1) {
                    // From CategoryIcon to content
                    onFocusChange(focusedRowIndex, 0)
                    channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                } else if (focusedColIndex == 0) {
                    // Scroll right if possible
                    val channelIndex = focusedRowIndex - 2
                    val lazyListState = lazyListStates[channelIndex]
                    val channelName = channels.getOrNull(channelIndex)
                    val channelContent = gridContent[channelName] ?: emptyList()

                    if (lazyListState != null && lazyListState.firstVisibleItemIndex < channelContent.size - 1) {
                        coroutineScope.launch {
                            val newIndex = lazyListState.firstVisibleItemIndex + 1
                            lazyListState.animateScrollToItem(newIndex)
                            // Wait for animation and set focus
                            delay(50)
                            channelFocusRequesters[Pair(focusedRowIndex, newIndex)]?.requestFocus()
                        }
                    }
                }
            }
            return true
        }

        else -> return false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VodFullscreenSlider(
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var currentSlide by remember { mutableStateOf(0) }
    var currentFocusArea by remember { mutableStateOf(VodFocusArea.MAIN_CONTENT) }
    val focusRequester = remember { FocusRequester() }
    
    val sliderItems = remember {
        listOf(
            VodSlideData(
                "Ballerina. Z uniwersum Johna Wicka",
                "Akcja",
                "120 min.",
                "2025 r.",
                "USA",
                "13 lat",
                "Zemsta ma nowe, piękniejsze oblicze. Ale wciąż nie zna litości. John Wick powraca, by wspomóc swoją godną następczynię!",
                "24 zł/48h",
                "https://n-1401-7.dcs.redcdn.pl/scale/play/playtv/upload/tvod/36490504/images/1053333674?srcmode=3&srcw=16&srch=9&dstw=1280&dsth=720&type=1&quality=85"
            ),
            VodSlideData(
                "Fantastyczna 4: Pierwsze kroki",
                "Akcja",
                "110 min",
                "2025 r.",
                "USA",
                "13 lat",
                "Pierwsza Rodzina Marvela staje przed swoim najtrudniejszym wyzwaniem. Zmuszeni do balansowania między rolą bohaterów a siłą rodzinnych więzi, muszą obronić Ziemię przed wygłodniałym kosmicznym bogiem Galactusem i jego heroldem, Srebrnym Surferem.",
                "24 zł/48h",
                "https://n-1401-7.dcs.redcdn.pl/scale/play/playtv/images/vod/e3f1e1b2-a53b-4a24-bfad-99ed0ce5e0ea/billboard_mobile.jpg?srcmode=3&srcw=16&srch=9&dstw=1920&dsth=1080&quality=80&type=1"
            ),
            VodSlideData(
                "Materialiści", 
                "Komedia", 
                "116 min", 
                "2025 r.", 
                "USA", 
                "13 lat", 
                "Lucy (Dakota Johnson) to młoda, ambitna swatka z Nowego Jorku, która wierzy, że zna przepis na miłość. Pewnego wieczoru poznaje wysokiego, przystojnego bruneta, prawdziwego „jednorożca\" (Pedro Pascal), a przypadkowe spotkanie z byłym chłopakiem (Chris Evans) stawia ją przed trudnym wyborem. Teraz musi zdecydować między idealnym partnerem a nieidealnym byłym.", 
                "24 zł/48h",
                "https://n-1401-4.dcs.redcdn.pl/scale/play/playtv/upload/tvod/36337247/images/1051510641?srcmode=3&srcw=16&srch=9&dstw=1280&dsth=720&type=1&quality=85"
            ),
            VodSlideData(
                "Oszukać przeznaczenie: Więzy krwi", 
                "Horror", 
                "132 min", 
                "2025 r.", 
                "USA", 
                "13 lat", 
                "Dręczona przez gwałtowny, powtarzający się koszmar studentka Stefani wraca do domu, aby wytropić jedyną osobę, która może przerwać ten cykl i uratować jej rodzinę przed makabryczną śmiercią, która na nią czeka.", 
                "24 zł/48h",
                "https://n-1411-10.dcs.redcdn.pl/scale/play/playtv/upload/tvod/35502381/images/1034599634?srcmode=3&srcw=16&srch=9&dstw=1280&dsth=720&type=1&quality=85"
            )
        )
    }

    val currentItem = sliderItems[currentSlide]
    
    LaunchedEffect(Unit) {
        // Reduced from 100ms to 0ms for instant focus
        kotlinx.coroutines.delay(0)
        focusRequester.requestFocus()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (currentSlide > 0) {
                            currentSlide--
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (currentSlide < sliderItems.size - 1) {
                            currentSlide++
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        AsyncImage(
            model = currentItem.backgroundUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .let { modifier ->
                    if (currentSlide == 1) {
                        modifier.scale(scaleX = -1f, scaleY = 1f)
                    } else {
                        modifier
                    }
                },
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xAA000000),
                            Color(0x33000000),
                            Color.Transparent
                        ),
                        endX = sx(960).value
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(sx(800))
                .padding(sx(80))
                .zIndex(1f),
            verticalArrangement = Arrangement.spacedBy(sy(24))
        ) {
            Text(
                text = currentItem.title,
                color = Color.White,
                fontSize = sy(64).value.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(16))
            ) {
                Text(currentItem.genre, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.duration, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.year, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.country, style = vodMetadataStyle(sy))
                VodMetadataSeparator(sy)
                Text(currentItem.ageRating, style = vodMetadataStyle(sy))
            }
            
            Text(
                text = currentItem.description,
                color = Color(0xFFEEEEEE),
                fontSize = sy(24).value.sp,
                fontWeight = FontWeight.W400,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (sy(24).value * 1.4).sp
            )

            Spacer(modifier = Modifier.height(sy(24)))

            // Buttons row
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(24)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rent button (fokusowy)
                Box(
                    modifier = Modifier
                        .height(sy(72))
                        .background(
                            color = Color(0xFF5FEDD4),
                            shape = RoundedCornerShape(sx(8))
                        )
                        .padding(horizontal = sx(32)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wypożycz: ${currentItem.price}",
                        color = Color(0xFF48227C),
                        fontSize = sy(24).value.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = (-0.48).sp
                    )
                }

                // Arrow button (tylko wizualny)
                Box(
                    modifier = Modifier
                        .size(sx(72), sy(72))
                        .background(
                            color = Color(0x33EEEEEE),
                            shape = RoundedCornerShape(sx(8))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "→",
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(32).value.sp,
                        fontWeight = FontWeight.W700
                    )
                }
            }

            Spacer(modifier = Modifier.height(sy(24)))

            // Bullety (slider indicators)
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(24)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(sliderItems.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (index == currentSlide) sx(32) else sx(16),
                                height = if (index == currentSlide) sy(32) else sy(16)
                            )
                            .background(
                                color = if (index == currentSlide) Color(0xFFEEEEEE) else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(
                                width = if (index == currentSlide) 0.dp else 2.dp,
                                color = if (index == currentSlide) Color.Transparent else Color(0x99EEEEEE),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun vodMetadataStyle(sy: (Int) -> androidx.compose.ui.unit.Dp) = TextStyle(
    color = Color(0xCCEEEEEE),
    fontSize = sy(20).value.sp,
    fontWeight = FontWeight.W700,
    letterSpacing = 0.4.sp
)

@Composable
private fun VodMetadataSeparator(sy: (Int) -> androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier
        .width(2.dp)
        .height(sy(24))
        .background(Color(0xCCEEEEEE)))
}

// Placeholder MiniatureCard function for START section
@Composable
private fun MiniatureCard(
    content: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    // Simple placeholder implementation using Box
    Box(
        modifier = Modifier
            .size(sx(290), sy(290))
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            }
            .background(
                color = Color(0xFF333333),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = content.title,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
    }
}

// Placeholder DetailedContentOverlay function for START section
@Composable
private fun DetailedContentOverlay(
    content: VodContent,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    // Simple placeholder implementation using Box
    Box(
        modifier = Modifier
            .size(sx(400), sy(200))
            .offset(x = sx(320), y = sy(300))
            .background(
                color = Color(0xE6000000),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = content.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content.description.take(100) + "...",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                maxLines = 3
            )
        }
    }
}

// Positioning Grid Component for development
@Composable
fun PositioningGrid(
    visible: Boolean = true,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Get screen dimensions
                    val screenWidth = size.width
                    val screenHeight = size.height
                    
                    // 20px grid lines - convert to density independent pixels
                    val gridSpacing = 20 * density
                    
                    // Vertical lines (every 20px)
                    var x = 0f
                    while (x <= screenWidth) {
                        drawLine(
                            color = androidx.compose.ui.graphics.Color(0x40FF0000), // Semi-transparent red
                            start = Offset(x, 0f),
                            end = Offset(x, screenHeight),
                            strokeWidth = 1f
                        )
                        x += gridSpacing
                    }
                    
                    // Horizontal lines (every 20px)
                    var y = 0f
                    while (y <= screenHeight) {
                        drawLine(
                            color = androidx.compose.ui.graphics.Color(0x40FF0000), // Semi-transparent red
                            start = Offset(0f, y),
                            end = Offset(screenWidth, y),
                            strokeWidth = 1f
                        )
                        y += gridSpacing
                    }
                }
        )
    }
}

// ============================================
// WIDEO SECTION - NEW IMPLEMENTATION
// ============================================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WideoChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current

    // Row 0 = menu, Row 1 = Slider Mix, Row 2 = Skróty v2 (vertical), Row 3+ = horizontal channels
    val channels = listOf(
        "Slider Mix",
        "Skróty v2",
        "Seriale",
        "Filmy fabularne",
        "Cinemax",
        "Filmy dokumentalne",
        "Świetna rozrywka",
        "Najlepsze wg Filmwebu",
        "KOLEKCJE"
    )

    // Vertical shortcuts for Row 2 (Skróty v2) - with Material Design icons
    val verticalShortcuts = remember {
        listOf(
            ShortcutItem("1", "Kolekcje", ShortcutIcon.MaterialIcon("add")),
            ShortcutItem("2", "Dla dzieci", ShortcutIcon.MaterialIcon("star")),
            ShortcutItem("3", "Filmy", ShortcutIcon.MaterialIcon("search")),
            ShortcutItem("4", "Seriale", ShortcutIcon.MaterialIcon("person"))
        )
    }

    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        if (vodContentList.isNotEmpty()) {
            channels.associateWith { channelName ->
                when (channelName) {
                    "Slider Mix", "Skróty v2" -> emptyList() // No horizontal content
                    else -> vodContentList.shuffled().take(10)
                }
            }
        } else {
            emptyMap()
        }
    }

    // Row 1 = Slider Mix, Row 2 = Skróty v2, Row 3+ = channels
    var focusedRowIndex by remember { mutableStateOf(1) } // Start at slider
    var focusedColIndex by remember { mutableStateOf(-2) } // -2 = brak fokusa na starcie

    // Reset focus state when returning to menu
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusedRowIndex = 1 // Back to slider
            focusedColIndex = -2
        }
    }

    val channelFocusRequesters = remember(channels.size, verticalShortcuts.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            // Row 1 = Slider Mix (colIndex 0 = rent button)
            put(Pair(1, 0), FocusRequester())

            // Row 2 = Skróty v2 (colIndex 0-3 = 4 horizontal shortcuts, NO CategoryIcon)
            repeat(verticalShortcuts.size) { colIndex ->
                put(Pair(2, colIndex), FocusRequester())
            }

            // Row 3+ = horizontal channels (CategoryIcon -1 + content 0)
            for (rowIndex in 3 until channels.size + 1) {
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                put(Pair(rowIndex, 0), FocusRequester()) // Fixed focus position
            }
        }
    }

    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            // Row 3+ = horizontal channels with LazyRow
            for (rowIndex in 3 until channels.size + 1) {
                put(rowIndex, LazyListState())
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isInitialized by remember { mutableStateOf(false) }

    // Auto-reset LazyListState for unfocused rows
    LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
        if (isInitialized) {
            kotlinx.coroutines.delay(0)
            for (rowIndex in 3 until channels.size + 1) {
                if (rowIndex != focusedRowIndex) {
                    val lazyListState = lazyListStates[rowIndex]
                    if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                        lazyListState.animateScrollToItem(index = 0, scrollOffset = 0)
                    }
                }
            }
        }
    }

    LaunchedEffect(shouldAutoFocus, resetTrigger) {
        if (shouldAutoFocus) {
            // When coming from menu, focus on slider
            focusedRowIndex = 1
            focusedColIndex = 0
            // SliderMixScreen handles focus via shouldAutoFocus parameter (like START)
        }
        isInitialized = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                handleWideoChannelsNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onChannelContentFocusChange = { row, col ->
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    channelFocusRequesters = channelFocusRequesters,
                    channels = channels,
                    verticalShortcuts = verticalShortcuts,
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    onReturnToMenu = onReturnToMenu
                )
            }
    ) {
        WideoChannelRowsLayout(
            channels = channels,
            verticalShortcuts = verticalShortcuts,
            gridContent = gridContent,
            focusedRowIndex = focusedRowIndex,
            focusedColIndex = focusedColIndex,
            channelFocusRequesters = channelFocusRequesters,
            onChannelContentFocusChange = { row, col ->
                focusedRowIndex = row
                focusedColIndex = col
            },
            onNavigateToVodGrid = onNavigateToVodGrid,
            lazyListStates = lazyListStates,
            sx = sx,
            sy = sy
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun handleWideoChannelsNavigation(
    event: androidx.compose.ui.input.key.KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    verticalShortcuts: List<ShortcutItem>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    onReturnToMenu: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }

    when (event.key) {
        Key.DirectionUp -> {
            when {
                focusedRowIndex == 1 -> {
                    // From Slider Mix -> return to menu
                    onReturnToMenu()
                    return true
                }
                focusedRowIndex > 1 -> {
                    // From Row 2+ -> move up within channels
                    val newRowIndex = focusedRowIndex - 1

                    // Special case: Moving TO Row 1 (Slider Mix)
                    if (newRowIndex == 1) {
                        // DON'T call requestFocus() - SliderMixScreen uses shouldAutoFocus parameter
                        onChannelContentFocusChange(1, 0)
                        return true
                    }

                    // Determine target colIndex using "preserve type" principle
                    val targetColIndex = when {
                        // Moving TO Row 2 (Skróty v2): always go to first shortcut (col=0)
                        newRowIndex == 2 -> 0
                        // Moving FROM Row 2 (Skróty v2) to normal channel:
                        // If on first shortcut -> CategoryIcon, else -> content
                        focusedRowIndex == 2 -> if (focusedColIndex == 0) -1 else 0
                        // Normal navigation: preserve type (CategoryIcon->CategoryIcon, content->content)
                        else -> if (focusedColIndex == -1) -1 else 0
                    }

                    onChannelContentFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                    return true
                }
            }
        }

        Key.DirectionDown -> {
            when {
                focusedRowIndex < channels.size -> {
                    // Move down within channels
                    val newRowIndex = focusedRowIndex + 1

                    // Determine target colIndex using "preserve type" principle
                    val targetColIndex = when {
                        // Moving TO Row 2 (Skróty v2): always go to first shortcut (col=0)
                        newRowIndex == 2 -> 0
                        // Moving FROM Row 2 (Skróty v2) to normal channel:
                        // If on first shortcut -> CategoryIcon, else -> content
                        focusedRowIndex == 2 -> if (focusedColIndex == 0) -1 else 0
                        // Normal navigation: preserve type (CategoryIcon->CategoryIcon, content->content)
                        else -> if (focusedColIndex == -1) -1 else 0
                    }

                    onChannelContentFocusChange(newRowIndex, targetColIndex)
                    channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
                    return true
                }
            }
        }

        Key.DirectionLeft -> {
            when (focusedRowIndex) {
                // Row 1 (Slider Mix): Delegate to SliderMixScreen to handle carousel (like START)
                1 -> return false
                // Row 2 (Skróty v2): Direct focus navigation (NO scrolling, like APLIKACJE)
                2 -> {
                    if (focusedColIndex > 0) {
                        val newColIndex = focusedColIndex - 1
                        onChannelContentFocusChange(focusedRowIndex, newColIndex)
                        channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                    }
                    return true
                }
                // Rows 3+ (Normal channels): CategoryIcon + scrolling content
                else -> {
                    when (focusedColIndex) {
                        -1 -> return true // Already on CategoryIcon
                        0 -> {
                            val lazyListState = lazyListStates[focusedRowIndex]
                            if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
                                }
                            } else {
                                onChannelContentFocusChange(focusedRowIndex, -1)
                                channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
                            }
                            return true
                        }
                    }
                }
            }
        }

        Key.DirectionRight -> {
            when (focusedRowIndex) {
                // Row 1 (Slider Mix): Delegate to SliderMixScreen to handle carousel (like START)
                1 -> return false
                // Row 2 (Skróty v2): Direct focus navigation (NO scrolling, like APLIKACJE)
                2 -> {
                    if (focusedColIndex < verticalShortcuts.size - 1) {
                        val newColIndex = focusedColIndex + 1
                        onChannelContentFocusChange(focusedRowIndex, newColIndex)
                        channelFocusRequesters[Pair(focusedRowIndex, newColIndex)]?.requestFocus()
                    }
                    return true
                }
                // Rows 3+ (Normal channels): CategoryIcon + scrolling content
                else -> {
                    when (focusedColIndex) {
                        -1 -> {
                            onChannelContentFocusChange(focusedRowIndex, 0)
                            channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                            return true
                        }
                        0 -> {
                            val lazyListState = lazyListStates[focusedRowIndex]
                            val rowContent = gridContent[channels.getOrNull(focusedRowIndex - 1)] ?: emptyList()
                            if (lazyListState != null && lazyListState.firstVisibleItemIndex < rowContent.size - 1) {
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                                }
                            }
                            return true
                        }
                    }
                }
            }
        }
    }

    return false
}

@Composable
fun WideoChannelRowsLayout(
    channels: List<String>,
    verticalShortcuts: List<ShortcutItem>,
    gridContent: Map<String, List<VodContent>>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    lazyListStates: Map<Int, LazyListState>,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Row 1: Slider Mix (like in START)
        val sliderYOffset = calculateWideoSliderYOffset(focusedRowIndex, sy)
        val animatedSliderY by animateDpAsState(
            targetValue = sliderYOffset,
            animationSpec = tween(durationMillis = 300),
            label = "wideo_slider_y"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = animatedSliderY)
        ) {
            SliderMixScreen(
                shouldAutoFocus = focusedRowIndex == 1,
                isInTelewizjaSection = false, // VOD only - no TV live
                shouldShowFocusBorder = focusedRowIndex == 1 && focusedColIndex >= 0,
                isShortcutsFocused = focusedRowIndex == 2,
                externalSx = sx,
                externalSy = sy
            )
        }

        // Row 2+: All channels (Skróty v2 + horizontal channels) using unified component
        for (channelIndex in 1 until channels.size) {
            val rowIndex = channelIndex + 1 // Row 2 = Skróty v2, Row 3+ = horizontal channels
            val channelName = channels[channelIndex]
            val rowContent = gridContent[channelName] ?: emptyList()
            val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

            val channelYOffset = calculateWideoRowYPosition(rowIndex, focusedRowIndex, focusedColIndex, sy)
            val channelYOffsetAnimated by animateDpAsState(
                targetValue = channelYOffset,
                animationSpec = tween(durationMillis = 500),
                label = "wideo_channel_y_offset_$rowIndex"
            )

            Box(modifier = Modifier.offset(y = channelYOffsetAnimated)) {
                WideoUnifiedChannelRow(
                    channel = channelName,
                    rowIndex = rowIndex,
                    rowContent = rowContent,
                    shortcuts = verticalShortcuts,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelContentFocusChange = onChannelContentFocusChange,
                    onNavigateToVodGrid = onNavigateToVodGrid,
                    sx = sx,
                    sy = sy,
                    lazyListState = lazyListState
                )
            }
        }
    }
}

@Composable
fun VerticalShortcutsV2(
    shortcuts: List<ShortcutItem>,
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .offset(x = sx(134), y = sy(0))
            .wrapContentSize(),
        verticalArrangement = Arrangement.spacedBy(sy(30))
    ) {
        shortcuts.forEachIndexed { colIndex, shortcut ->
            val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
            val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

            ShortcutCardV2(
                shortcut = shortcut,
                isFocused = isItemFocused,
                focusRequester = focusRequester,
                sx = sx,
                sy = sy,
                onFocusChange = { isFocused ->
                    if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                }
            )
        }
    }
}

@Composable
fun WideoUnifiedChannelRow(
    channel: String,
    rowIndex: Int,
    rowContent: List<VodContent>,
    shortcuts: List<ShortcutItem>,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onChannelContentFocusChange: (Int, Int) -> Unit,
    onNavigateToVodGrid: (title: String, prefiltered: List<VodContent>?, sourceSection: String) -> Unit = { _, _, _ -> },
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    lazyListState: LazyListState
) {
    val isCurrentRow = rowIndex == focusedRowIndex
    val isShortcutsV2 = channel == "Skróty v2"
    var showDetailsWithDelay by remember { mutableStateOf(false) }

    LaunchedEffect(isCurrentRow, focusedColIndex) {
        val shouldShowDetails = !isShortcutsV2 && isCurrentRow && focusedColIndex == 0
        if (shouldShowDetails) {
            kotlinx.coroutines.delay(350)
            showDetailsWithDelay = true
        } else {
            showDetailsWithDelay = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
        val miniaturesYOffset by animateDpAsState(
            targetValue = if (!isShortcutsV2 && isMiniaturesOnScreen) sy(290) else sy(0),
            animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
            label = "wideo_miniatures_y_offset_$rowIndex"
        )

        // Normal channel content (LazyRow with scrolling)
        if (!isShortcutsV2) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = miniaturesYOffset),
                state = lazyListState,
                contentPadding = PaddingValues(start = sx(380), end = sx(20)),
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                items(rowContent.size) { colIndex ->
                    val vodContent = rowContent[colIndex]
                    val isItemFocused = rowIndex == focusedRowIndex &&
                            colIndex == lazyListState.firstVisibleItemIndex &&
                            focusedColIndex == 0
                    // Only the focused item (firstVisibleItemIndex) gets the mapped FocusRequester
                    val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
                        channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
                    } else {
                        FocusRequester()
                    }

                    ContentCard(
                        vodContent = vodContent,
                        channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)),
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                        sx = sx,
                        sy = sy,
                        lazyListState = lazyListState
                    )
                }

                items(8) {
                    Spacer(modifier = Modifier.width(sx(368)).height(sy(208)))
                }
            }
        }

        // Skróty v2 row (NO scrolling, direct focus navigation, horizontal layout like APLIKACJE)
        if (isShortcutsV2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = miniaturesYOffset)
                    .padding(start = sx(100)),
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
                shortcuts.forEachIndexed { colIndex, shortcut ->
                    val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
                    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

                    ShortcutCardV2(
                        shortcut = shortcut,
                        isFocused = isItemFocused,
                        focusRequester = focusRequester,
                        sx = sx,
                        sy = sy,
                        onFocusChange = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, colIndex)
                        },
                        onClick = {
                            // Navigate to VodGridScreen with shortcut-specific filtered content
                            val vodList = VodDataCache.getVodContentList()
                            val filtered = filterVodByWIDEOCategory(vodList, shortcut.title)
                            onNavigateToVodGrid(shortcut.title, filtered, "WIDEO")
                        }
                    )
                }
            }
        }

        // Details overlay (only for normal channels, not Skróty v2)
        if (!isShortcutsV2 && isCurrentRow && focusedColIndex == 0 && showDetailsWithDelay) {
            val firstVisibleContent = rowContent.getOrNull(lazyListState.firstVisibleItemIndex)
            if (firstVisibleContent != null) {
                Box(
                    modifier = Modifier
                        .offset(x = sx(380), y = sy(0))
                        .width(sx(1500))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(sy(14))) {
                        Text(
                            text = firstVisibleContent.title,
                            color = Color(0xFFEEEEEE),
                            fontSize = (64 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (64 * 1.38f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(1500))
                        )

                        Text(
                            text = firstVisibleContent.category,
                            color = Color(0xCCEEEEEE),
                            fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = (20 * 1.4f * (sy(1).value / 1.dp.value)).sp
                        )

                        Text(
                            text = firstVisibleContent.description,
                            color = Color(0xFFEEEEEE),
                            fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (28 * 1.43f * (sy(1).value / 1.dp.value)).sp,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.width(sx(874))
                        )
                    }
                }
            }
        }

        // CategoryIcon (only for normal channels, NOT for Skróty v2)
        if (!isShortcutsV2) {
            Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
                val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
                val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]

                if (categoryFocusRequester != null) {
                    CategoryIcon(
                        text = channel,
                        isFocused = categoryIsFocused,
                        onClick = {
                            // Navigate to VodGridScreen with channel-specific filtered content
                            val vodList = VodDataCache.getVodContentList()
                            val filtered = filterVodByWIDEOCategory(vodList, channel)
                            onNavigateToVodGrid(channel, filtered, "WIDEO")
                        },
                        onFocused = { isFocused ->
                            if (isFocused) onChannelContentFocusChange(rowIndex, -1)
                        },
                        focusRequester = categoryFocusRequester,
                        sx = sx,
                        sy = sy,
                        showIcon = false,  // WIDEO channels: bez ikony, tylko nazwa
                        showBackgroundWhenFocused = true  // WIDEO channels: czarne tło gdy focused
                    )
                }
            }
        }
    }
}

// Calculate Y offset for WIDEO Slider Mix (Row 1) - Match START behavior
private fun calculateWideoSliderYOffset(
    focusedRowIndex: Int,
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    return when {
        focusedRowIndex == 1 -> sy(0) // Slider focused - at top
        focusedRowIndex == 2 -> sy(-640) // Shortcuts focused - move up (reduced from -740 for better spacing)
        focusedRowIndex >= 3 -> sy(-900) // Channels focused - completely off-screen
        else -> sy(0) // Default
    }
}

// Y-position calculation for WIDEO rows
private fun calculateWideoRowYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    sy: (Int) -> androidx.compose.ui.unit.Dp
): androidx.compose.ui.unit.Dp {
    val FIXED_FOCUS_Y = 340
    val SLIDER_MIX_HEIGHT = 640 // Height for 238px spacing: slider ends at 742px, shortcuts at 980px (742+238=980; 980-340=640)
    val SHORTCUTS_V2_HEIGHT = 246 // Height for horizontal shortcuts row (like APLIKACJE)
    val HORIZONTAL_NORMAL_HEIGHT = 256 // CategoryIcon (216px) + spacing (40px)
    val HORIZONTAL_EXPANDED_HEIGHT = 546 // CategoryIcon (216px) + miniatures (290px) + spacing (40px)

    return when {
        rowIndex < focusedRowIndex -> {
            // Rows above focused row
            var cumulativeHeight = FIXED_FOCUS_Y - when (rowIndex) {
                1 -> SLIDER_MIX_HEIGHT
                2 -> SHORTCUTS_V2_HEIGHT
                else -> HORIZONTAL_NORMAL_HEIGHT
            }

            for (i in (rowIndex + 1) until focusedRowIndex) {
                cumulativeHeight -= when (i) {
                    1 -> SLIDER_MIX_HEIGHT
                    2 -> SHORTCUTS_V2_HEIGHT
                    else -> HORIZONTAL_NORMAL_HEIGHT
                }
            }

            sy(cumulativeHeight)
        }
        rowIndex == focusedRowIndex -> {
            sy(FIXED_FOCUS_Y)
        }
        else -> {
            // Rows below focused row
            val focusedRowExpansion = when (focusedRowIndex) {
                1 -> SLIDER_MIX_HEIGHT
                2 -> SHORTCUTS_V2_HEIGHT
                else -> {
                    if (focusedColIndex >= 0) HORIZONTAL_EXPANDED_HEIGHT else HORIZONTAL_NORMAL_HEIGHT
                }
            }

            var cumulativeHeight = FIXED_FOCUS_Y + focusedRowExpansion

            for (i in (focusedRowIndex + 1) until rowIndex) {
                cumulativeHeight += when (i) {
                    1 -> SLIDER_MIX_HEIGHT
                    2 -> SHORTCUTS_V2_HEIGHT
                    else -> HORIZONTAL_NORMAL_HEIGHT
                }
            }

            sy(cumulativeHeight)
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MARK: - ACCOUNT SECTION
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * AccountScreenContent - Wrapper for Account section
 *
 * Design: Figma node 6359-11841 (Moje konto)
 * Pattern: Similar to MojeScreenContent with reset trigger
 */

/**
 * PointsHistoryScreenContent - Placeholder for Points History section
 */
@Composable
private fun PointsHistoryScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
                    return@onPreviewKeyEvent true
                }
                false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sy(40))
        ) {
            // Title
            Text(
                text = "Historia punktów",
                color = Color.White,
                fontSize = sy(48).value.sp,
                fontWeight = FontWeight.Bold
            )

            // Placeholder info
            Text(
                text = "Ekran w przygotowaniu",
                color = Color(0xCCEEEEEE),
                fontSize = sy(32).value.sp
            )

            // Current points display
            Box(
                modifier = Modifier
                    .width(sx(400))
                    .height(sy(120))
                    .background(
                        color = Color(0xFF5AECD3),
                        shape = RoundedCornerShape(sx(60))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(16)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_wallet),
                        contentDescription = null,
                        tint = Color(0xFF48227C),
                        modifier = Modifier.size(sx(48), sy(48))
                    )
                    Text(
                        text = "40 pkt / 21 dni",
                        color = Color(0xFF48227C),
                        fontSize = sy(32).value.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Navigation hint
            Text(
                text = "Naciśnij BACK aby wrócić do menu",
                color = Color(0x80EEEEEE),
                fontSize = sy(24).value.sp,
                modifier = Modifier.padding(top = sy(60))
            )
        }
    }
}

@Composable
private fun AccountScreenContent(
    globalFocusState: MutableState<GlobalFocusState>,
    onNavigateToStartupMode: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    var resetTrigger by remember { mutableStateOf(0) }

    // Reset when returning to menu
    LaunchedEffect(globalFocusState.value.currentRow) {
        if (globalFocusState.value.currentRow == 0 && globalFocusState.value.sectionId == "ACCOUNT") {
            resetTrigger++
        }
    }

    AccountChannelsScreen(
        onReturnToMenu = {
            // Return to menu (LaunchedEffect will auto-focus Profile button)
            globalFocusState.value = globalFocusState.value.copy(
                currentRow = 0,
                isActive = true
            )
        },
        onNavigateToStartupMode = onNavigateToStartupMode,
        shouldAutoFocus = globalFocusState.value.sectionId == "ACCOUNT" && globalFocusState.value.currentRow > 0,
        sx = sx,
        sy = sy,
        resetTrigger = resetTrigger
    )
}

/**
 * Focus levels for Account section (simplified to 2 levels)
 * Level 1: Top Menu (handled by GlobalFocusManager)
 * Level 2: Profile button ("Profil: Andrzej")
 * Level 3: Menu List (9 menu items: Powiadomienia + 8 others)
 */
private enum class AccountFocusLevel {
    PROFILE,      // "Profil: Andrzej"
    MENU_LIST     // Lista menu (starts with Powiadomienia)
}

/**
 * AccountChannelsScreen - Main Account section component with simplified 2-level focus hierarchy
 *
 * Simplified Design:
 * - Profile button ("Profil: Andrzej") - Level 1
 * - Menu List: 9 menu items (Powiadomienia + 8 others) - Level 2
 *
 * Focus Hierarchy:
 * 1. Top Menu → PROFILE (auto-focus on entry)
 * 2. PROFILE → DOWN → MENU_LIST (Powiadomienia)
 * 3. MENU_LIST → UP (from Powiadomienia) → PROFILE
 * 4. MENU_LIST → UP/DOWN within list (9 items)
 * 5. Any level → BACK/LEFT → return to top menu
 */

/**
 * Get current startup mode label for Account menu subtitle
 */
private fun getCurrentStartupModeLabel(context: Context): String {
    return when (VersionTracker.getStartupMode(context)) {
        VersionTracker.MODE_EPG_DAY -> "Wybrany ekran startowy: Telewizja"
        VersionTracker.MODE_TOP_MENU -> "Wybrany ekran startowy: Telewizja i Aplikacje"
        else -> "Wybrany ekran startowy: Telewizja i Aplikacje" // fallback
    }
}

@Composable
private fun AccountChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    onNavigateToStartupMode: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current

    // 10 menu items (Powiadomienia first, then Ekran startowy at row 3)
    val menuItems = remember(context) {
        listOf(
            AccountMenuItem(
                id = "notifications",
                title = "Powiadomienia",
                subtitle = "0 powiadomień",
                icon = Icons.Default.Notifications
            ),
            AccountMenuItem(
                id = "service_number",
                title = "Numer usługi: 696XXXXXXXXXX",
                subtitle = "Zaloguj się do Telewizji Play na innych urządzeniach",
                icon = Icons.Default.Info
            ),
            AccountMenuItem(
                id = "packages",
                title = "Pakiety",
                subtitle = "Zarządzaj pakietami telewizyjnymi i streamingowymi",
                icon = Icons.Default.Star
            ),
            AccountMenuItem(
                id = "startup_mode",
                title = "Ekran startowy",
                subtitle = getCurrentStartupModeLabel(context),
                icon = Icons.Default.Settings
            ),
            AccountMenuItem(
                id = "payments",
                title = "Płatności",
                subtitle = "Opłać bieżące faktury, sprawdź historię płatności",
                icon = Icons.Default.AccountCircle
            ),
            AccountMenuItem(
                id = "purchases",
                title = "Zakupy",
                subtitle = "Sprawdź m.in. historię wypożyczonych filmów",
                icon = Icons.Default.ShoppingCart
            ),
            AccountMenuItem(
                id = "pin_code",
                title = "Kod PIN",
                subtitle = "Ustaw kod blokady dostępu dla materiałów 18+",
                icon = Icons.Default.Lock
            ),
            AccountMenuItem(
                id = "diagnostics",
                title = "Diagnostyka",
                subtitle = "Sprawdź urządzenie i połączenie internetowe",
                icon = Icons.Default.Settings
            ),
            AccountMenuItem(
                id = "channel_search",
                title = "Wyszukiwanie kanałów TV",
                subtitle = "Podłącz kabel DVB i wyszukaj kanały naziemnej telewizji cyfrowej",
                icon = Icons.Default.Search
            ),
            AccountMenuItem(
                id = "help",
                title = "Pomoc",
                subtitle = "Znajdź odpowiedź na najczęstsze pytania",
                icon = Icons.Default.Info
            )
        )
    }

    // 2-level focus state (Profile, Menu List)
    var focusLevel by remember { mutableStateOf(AccountFocusLevel.PROFILE) }
    var menuListIndex by remember { mutableStateOf(0) } // 0-9 for menu items (10 total)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // FocusRequesters for 2 levels
    val profileFocusRequester = remember { FocusRequester() }

    // FocusRequesters for Menu List (10 items: Powiadomienia + Ekran startowy + 8 others)
    val menuListFocusRequesters = remember {
        (0 until 10).associateWith { FocusRequester() }
    }

    // Auto-focus PROFILE when entering content (follows Focus Architect pattern)
    LaunchedEffect(shouldAutoFocus, resetTrigger) {
        if (shouldAutoFocus) {
            delay(100)
            focusLevel = AccountFocusLevel.PROFILE
            profileFocusRequester.requestFocus()
        }
    }

    // Reset focus when resetTrigger changes
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            focusLevel = AccountFocusLevel.PROFILE
            menuListIndex = 0
        }
    }

    // Auto-scroll to keep focused item centered
    // LazyColumn structure: [0] Profile, [1-9] Menu items (Powiadomienia + 8 others)
    LaunchedEffect(menuListIndex, focusLevel) {
        coroutineScope.launch {
            when (focusLevel) {
                AccountFocusLevel.PROFILE -> {
                    // Scroll to Profile (item 0)
                    listState.animateScrollToItem(
                        index = 0,
                        scrollOffset = -sy(200).value.toInt()
                    )
                }
                AccountFocusLevel.MENU_LIST -> {
                    // Scroll to menu item (items 1-9, so menuListIndex + 1)
                    listState.animateScrollToItem(
                        index = menuListIndex + 1,
                        scrollOffset = -sy(200).value.toInt()
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                handleAccountNavigation(
                    event = event,
                    focusLevel = focusLevel,
                    menuListIndex = menuListIndex,
                    onFocusLevelChange = { newLevel -> focusLevel = newLevel },
                    onMenuListIndexChange = { newIndex -> menuListIndex = newIndex },
                    profileFocusRequester = profileFocusRequester,
                    menuListFocusRequesters = menuListFocusRequesters,
                    menuItemsCount = menuItems.size,
                    onReturnToMenu = onReturnToMenu
                )
            }
    ) {
        // LazyColumn with all content (header + menu items scroll together)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = sy(200)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(sy(24)),
            contentPadding = PaddingValues(bottom = sy(150))
        ) {
            // Item 0: Profile button
            item {
                ProfileButton(
                    isFocused = shouldAutoFocus && focusLevel == AccountFocusLevel.PROFILE,
                    focusRequester = profileFocusRequester,
                    onFocused = { focusLevel = AccountFocusLevel.PROFILE },
                    onClick = { Log.d("ACCOUNT", "Profile clicked") },
                    sx = sx,
                    sy = sy
                )
            }

            // Items 1-10: Menu List (Powiadomienia + Ekran startowy + 8 others)
            itemsIndexed(menuItems) { index, item ->
                AccountMenuItemCard(
                    item = item,
                    isFocused = shouldAutoFocus && focusLevel == AccountFocusLevel.MENU_LIST && menuListIndex == index,
                    focusRequester = menuListFocusRequesters[index]!!,
                    onFocused = {
                        focusLevel = AccountFocusLevel.MENU_LIST
                        menuListIndex = index
                    },
                    onClick = {
                        when (item.id) {
                            "notifications" -> {
                                Log.d("ACCOUNT", "Powiadomienia clicked - feature coming soon")
                                // TODO: Implement notifications screen
                            }
                            "startup_mode" -> {
                                Log.d("ACCOUNT", "Navigate to Startup Mode Selection")
                                onNavigateToStartupMode()
                            }
                            else -> {
                                Log.d("ACCOUNT", "Clicked: ${item.title}")
                            }
                        }
                    },
                    sx = sx,
                    sy = sy
                )
            }
        }
    }
}

/**
 * Data class for Account menu items
 */
data class AccountMenuItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * AccountMenuItemCard - Single menu item component
 *
 * Figma specs:
 * - Size: 756×136px
 * - Background: rgba(0,0,0,0.2)
 * - Focus border: 8px #5AECD3
 * - Border radius: 8px
 * - Icon: 64×64px
 * - Gap: 24px between icon and text
 */
@Composable
private fun AccountMenuItemCard(
    item: AccountMenuItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val colorFocusBorder = Color(0xFF5AECD3)
    val colorBackground = Color(0x33000000) // rgba(0,0,0,0.2)
    val colorTextPrimary = Color(0xFFEEEEEE)
    val colorTextSecondary = Color(0xCCEEEEEE) // 80% opacity

    Box(
        modifier = Modifier
            .width(sx(756))
            .height(sy(136))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = sx(8),
                        color = colorFocusBorder,
                        shape = RoundedCornerShape(sx(8))
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(sx(8)))
            .background(colorBackground)
            .padding(horizontal = sx(32), vertical = sy(32))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sx(24))
        ) {
            // Icon (64×64px)
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(sx(64), sy(64)),
                tint = colorTextPrimary
            )

            // Text content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(sy(4))
            ) {
                // Title (32px Bold from Figma)
                Text(
                    text = item.title,
                    style = TextStyle(
                        fontSize = (32 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Bold,
                        color = colorTextPrimary,
                        lineHeight = (40 * sy(1).value / 1).sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle (24px Medium from Figma)
                Text(
                    text = item.subtitle,
                    style = TextStyle(
                        fontSize = (24 * sy(1).value / 1).sp,
                        fontWeight = FontWeight.Medium,
                        color = colorTextSecondary,
                        lineHeight = (32 * sy(1).value / 1).sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * ProfileButton - Focusable "Profil: Andrzej" button (szerokość listy, wysokość jak action buttons)
 */
@Composable
private fun ProfileButton(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val colorFocusBorder = Color(0xFF5AECD3)
    val colorBackground = Color(0x33EEEEEE) // rgba(238,238,238,0.2)
    val colorTextPrimary = Color(0xFFEEEEEE)

    Box(
        modifier = Modifier
            .width(sx(756)) // Szerokość listy menu
            .height(sy(110)) // Wysokość jak Wallet/Calendar buttons
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = sx(8),
                        color = colorFocusBorder,
                        shape = RoundedCornerShape(sx(88))
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(sx(88)))
            .background(colorBackground)
            .padding(horizontal = sx(44), vertical = sy(14)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(11)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profil",
                modifier = Modifier.size(sx(66), sy(66)),
                tint = colorTextPrimary
            )
            Text(
                text = "Profil: Andrzej",
                style = TextStyle(
                    fontSize = (28 * sy(1).value / 1).sp,
                    fontWeight = FontWeight.Medium,
                    color = colorTextPrimary
                )
            )
        }
    }
}

/**
 * WalletCalendarButton - Combined "40 pkt" + "21 dni" button with PNG icons
 */
@Composable
private fun WalletCalendarButton(
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val context = LocalContext.current
    val colorFocusBorder = Color(0xFF5AECD3)
    val colorBackground = Color(0x33EEEEEE)
    val colorTextPrimary = Color(0xFFEEEEEE)

    Box(
        modifier = Modifier
            .width(sx(500))
            .height(sy(110))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = sx(8),
                        color = colorFocusBorder,
                        shape = RoundedCornerShape(sx(83))
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(sx(83)))
            .background(colorBackground)
            .padding(horizontal = sx(44)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(11)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wallet icon (PNG)
            Image(
                painter = painterResource(id = R.drawable.ic_wallet),
                contentDescription = "Portfel",
                modifier = Modifier.size(sx(66), sy(66))
            )
            Text(
                text = "40 pkt",
                style = TextStyle(
                    fontSize = (28 * sy(1).value / 1).sp,
                    fontWeight = FontWeight.Medium,
                    color = colorTextPrimary
                )
            )

            Spacer(modifier = Modifier.width(sx(20)))

            // Calendar icon (PNG)
            Image(
                painter = painterResource(id = R.drawable.ic_calendar),
                contentDescription = "Kalendarz",
                modifier = Modifier.size(sx(66), sy(66))
            )
            Text(
                text = "21 dni",
                style = TextStyle(
                    fontSize = (28 * sy(1).value / 1).sp,
                    fontWeight = FontWeight.Medium,
                    color = colorTextPrimary
                )
            )
        }
    }
}

/**
 * ActionButton - Generic action button for Notifications/Settings
 */
@Composable
private fun ActionButton(
    icon: Int, // Drawable resource ID
    label: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    val colorFocusBorder = Color(0xFF5AECD3)
    val colorBackground = Color(0x33EEEEEE)
    val colorTextPrimary = Color(0xFFEEEEEE)

    Box(
        modifier = Modifier
            .size(sx(110), sy(110))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onClick() }
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = sx(8),
                        color = colorFocusBorder,
                        shape = RoundedCornerShape(sx(83))
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(sx(83)))
            .background(colorBackground),
        contentAlignment = Alignment.Center
    ) {
        // Use Material Icon instead of PNG for now
        Icon(
            imageVector = when (label) {
                "Powiadomienia" -> Icons.Default.Notifications
                "Ustawienia" -> Icons.Default.Settings
                else -> Icons.Default.Settings
            },
            contentDescription = label,
            modifier = Modifier.size(sx(66), sy(66)),
            tint = colorTextPrimary
        )
    }
}
/**
 * handleAccountNavigation - Navigation logic for Account section with simplified 2-level hierarchy
 *
 * Level 1 (PROFILE): "Profil: Andrzej"
 * - DOWN: Move to MENU_LIST (first item = Powiadomienia)
 * - UP/BACK/LEFT: Return to top menu
 *
 * Level 2 (MENU_LIST): 9 menu items (Powiadomienia + 8 others)
 * - UP: Previous item, or return to PROFILE if at first item (Powiadomienia)
 * - DOWN: Next item (stay in place if at last item)
 * - BACK/LEFT: Return to top menu
 */
private fun handleAccountNavigation(
    event: KeyEvent,
    focusLevel: AccountFocusLevel,
    menuListIndex: Int,
    onFocusLevelChange: (AccountFocusLevel) -> Unit,
    onMenuListIndexChange: (Int) -> Unit,
    profileFocusRequester: FocusRequester,
    menuListFocusRequesters: Map<Int, FocusRequester>,
    menuItemsCount: Int,
    onReturnToMenu: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (focusLevel) {
        AccountFocusLevel.PROFILE -> {
            when (event.key) {
                Key.DirectionDown -> {
                    // Move to first menu item (Powiadomienia)
                    onFocusLevelChange(AccountFocusLevel.MENU_LIST)
                    onMenuListIndexChange(0)
                    menuListFocusRequesters[0]?.requestFocus()
                    true
                }
                Key.DirectionUp, Key.Back, Key.DirectionLeft -> {
                    // Return to top menu
                    onReturnToMenu()
                    true
                }
                else -> false
            }
        }
        AccountFocusLevel.MENU_LIST -> {
            when (event.key) {
                Key.DirectionUp -> {
                    if (menuListIndex > 0) {
                        // Move to previous menu item
                        val newIndex = menuListIndex - 1
                        onMenuListIndexChange(newIndex)
                        menuListFocusRequesters[newIndex]?.requestFocus()
                        true
                    } else {
                        // From first item (Powiadomienia), return to PROFILE
                        onFocusLevelChange(AccountFocusLevel.PROFILE)
                        profileFocusRequester.requestFocus()
                        true
                    }
                }
                Key.DirectionDown -> {
                    if (menuListIndex < menuItemsCount - 1) {
                        // Move to next menu item
                        val newIndex = menuListIndex + 1
                        onMenuListIndexChange(newIndex)
                        menuListFocusRequesters[newIndex]?.requestFocus()
                        true
                    } else {
                        // At last item, stay in place
                        true
                    }
                }
                Key.Back, Key.DirectionLeft -> {
                    // Return to top menu
                    onReturnToMenu()
                    true
                }
                else -> false
            }
        }
    }
}

