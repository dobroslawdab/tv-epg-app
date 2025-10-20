package com.uxellence.tv.v3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.components.SliderComponent
import com.uxellence.tv.v3.components.ShortcutComponent
import com.uxellence.tv.v3.components.ReusableTopMenu
import com.uxellence.tv.v3.components.MiniCard
import kotlinx.coroutines.launch
import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

// Data model for component showcase
data class ComponentInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val documentation: String = "",
    val usage: String = "",
    val content: @Composable (isParentFocused: Boolean) -> Unit
)

// VOD data model
data class VodItem(
    val thumbnailUrl: String,
    val logoUrl: String,
    val channelUrl: String,
    val title: String,
    val category: String,
    val description: String
)

// Parse VOD data from CSV
fun parseVodData(context: Context): List<VodItem> {
    val vodItems = mutableListOf<VodItem>()
    try {
        val inputStream = context.assets.open("vod_data.csv")
        val reader = BufferedReader(InputStreamReader(inputStream))
        reader.readLine() // Skip header
        reader.forEachLine { line ->
            val parts = line.split(";")
            if (parts.size >= 6) {
                vodItems.add(
                    VodItem(
                        thumbnailUrl = parts[0],
                        logoUrl = parts[1],
                        channelUrl = parts[2],
                        title = parts[3],
                        category = parts[4],
                        description = parts[5]
                    )
                )
            }
        }
        reader.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return vodItems
}

/**
 * Component Showcase Screen
 * Displays all available components in the project for testing and demonstration
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ComponentShowcaseScreen() {
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val context = androidx.compose.ui.platform.LocalContext.current
    val vodData = remember { parseVodData(context) }

    // Define all available reusable components
    val components = remember {
        listOf(
            ComponentInfo(
                id = "slider_component",
                name = "SliderComponent",
                description = "Reusable horizontal movie/content slider with focus management and automatic scrolling",
                category = "Navigation"
            ) { isParentFocused ->
                SliderComponent(
                    modifier = Modifier.fillMaxWidth(),
                    isSectionFocused = true
                )
            },
            ComponentInfo(
                id = "shortcut_component",
                name = "ShortcutComponent",
                description = "Reusable horizontal shortcuts row with Lottie animations and focus states",
                category = "Navigation"
            ) { isParentFocused ->
                ShortcutComponent(
                    modifier = Modifier.fillMaxWidth(),
                    isSectionFocused = false
                )
            },
            ComponentInfo(
                id = "reusable_topmenu",
                name = "ReusableTopMenu",
                description = "Complete reusable top menu with tabs, right menu, digital clock, and content management",
                category = "Layout"
            ) { isParentFocused ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(400)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ReusableTopMenu Demo\n(Complete menu system with clock and navigation)\n\nUsed in: Version002Screen, TopMenuScreen",
                        color = Color.White,
                        fontSize = sy(14).value.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        lineHeight = sy(18).value.sp
                    )
                }
            },
            ComponentInfo(
                id = "mini_card",
                name = "MiniCard",
                description = "Content card with background image, channel logo, title, metadata, and description. Shows expanded details on focus.",
                category = "Content",
                documentation = """
                    Component converted from Figma (Node ID: 5972-6468)

                    Features:
                    • Two states: Default (887x503px) and Focused (887x661px)
                    • Background image with gradient overlay
                    • Channel logo (168x168px) in top-left corner
                    • Title always visible (Manrope Bold 46px)
                    • Metadata row (category, duration, year, country, age rating) - visible on focus
                    • Description text (max 2 lines) - visible on focus
                    • Smooth animations (350ms duration)
                    • 6px aqua border on focus (#5FEDD4)
                    • 20px border radius

                    Animations:
                    • Height: 503px → 661px
                    • Border: 0dp → 6dp
                    • Metadata opacity: 0 → 1
                    • Description opacity: 0 → 1
                """.trimIndent(),
                usage = """
                    MiniCard(
                        imageUrl = "https://example.com/image.jpg",
                        channelLogoUrl = "https://example.com/logo.png",
                        title = "Grand Budapest Hotel",
                        category = "program informacyjny",
                        duration = "25 min",
                        year = "2020 r.",
                        country = "Polska",
                        ageRating = "7 lat",
                        description = "Long description text...",
                        isFocused = isFocused,
                        modifier = Modifier
                            .focusable()
                            .onFocusChanged { focusState ->
                                isFocused = focusState.isFocused
                            }
                    )
                """.trimIndent()
            ) { isParentFocused ->
                // Two MiniCards side by side with VOD data
                var focusedCardIndex by remember { mutableStateOf(0) }
                val card1Data = vodData.find { it.title == "Sisi" }
                val card2Data = if (vodData.size > 1) vodData[1] else null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(750))
                        .onPreviewKeyEvent { event ->
                            if (!isParentFocused || event.type != KeyEventType.KeyDown) {
                                return@onPreviewKeyEvent false
                            }

                            when (event.key) {
                                Key.DirectionLeft -> {
                                    if (focusedCardIndex > 0) {
                                        focusedCardIndex = 0
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                Key.DirectionRight -> {
                                    if (focusedCardIndex < 1) {
                                        focusedCardIndex = 1
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                            false
                        },
                    horizontalArrangement = Arrangement.spacedBy(sx(40)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Card 1
                    if (card1Data != null) {
                        MiniCard(
                            imageUrl = card1Data.thumbnailUrl,
                            channelLogoUrl = card1Data.logoUrl,
                            title = card1Data.title,
                            category = card1Data.category,
                            duration = "45 min",
                            year = "2023 r.",
                            country = "Polska",
                            ageRating = "12 lat",
                            description = card1Data.description,
                            isFocused = isParentFocused && focusedCardIndex == 0
                        )
                    }

                    // Card 2
                    if (card2Data != null) {
                        MiniCard(
                            imageUrl = card2Data.thumbnailUrl,
                            channelLogoUrl = card2Data.logoUrl,
                            title = card2Data.title,
                            category = card2Data.category,
                            duration = "50 min",
                            year = "2024 r.",
                            country = "USA",
                            ageRating = "16 lat",
                            description = card2Data.description,
                            isFocused = isParentFocused && focusedCardIndex == 1
                        )
                    }
                }
            }
        )
    }

    var focusedIndex by remember { mutableStateOf(0) }
    var selectedComponentIndex by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(components.size) { List(components.size) { FocusRequester() } }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < components.size) {
            scope.launch { 
                listState.animateScrollToItem(focusedIndex)
                focusRequesters[focusedIndex].requestFocus()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // If modal is open, handle Back key to close it
                if (selectedComponentIndex != null) {
                    if (event.key == Key.Back || event.key == Key.Escape) {
                        selectedComponentIndex = null
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // Handle navigation keys
                when (event.key) {
                    Key.DirectionUp -> {
                        if (focusedIndex > 0) {
                            focusedIndex -= 1
                            return@onPreviewKeyEvent true
                        }
                    }
                    Key.DirectionDown -> {
                        if (focusedIndex < components.size - 1) {
                            focusedIndex += 1
                            return@onPreviewKeyEvent true
                        }
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        selectedComponentIndex = focusedIndex
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        LaunchedEffect(Unit) {
            if (components.isNotEmpty()) {
                focusRequesters[0].requestFocus()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(sx(40))
        ) {
            // Header
            Text(
                text = "Reusable Components",
                fontSize = sy(32).value.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = sy(20))
            )

            Text(
                text = "Komponenty wielokrotnego użytku - gotowe do wykorzystania w różnych ekranach",
                fontSize = sy(16).value.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = sy(30))
            )

            // Components list
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(sy(40)),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(components) { index, component ->
                    ComponentShowcaseItem(
                        component = component,
                        isFocused = index == focusedIndex,
                        focusRequester = focusRequesters[index],
                        onFocusChanged = { isFocused ->
                            if (isFocused) focusedIndex = index
                        },
                        onClick = { selectedComponentIndex = index },
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
        }

        // Component detail modal
        selectedComponentIndex?.let { index ->
            ComponentDetailModal(
                component = components[index],
                onDismiss = { selectedComponentIndex = null },
                sx = { sx(it) },
                sy = { sy(it) }
            )
        }
    }
}

@Composable
private fun ComponentDetailModal(
    component: ComponentInfo,
    onDismiss: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var demoFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape)) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(sx(1600))
                .height(sy(900))
                .padding(sx(40)),
            shape = RoundedCornerShape(sx(20)),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2C2C2C)
            ),
            border = androidx.compose.foundation.BorderStroke(sx(3), Color(0xFF5AECD3))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(sx(40))
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = component.name,
                            fontSize = sy(32).value.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5AECD3)
                        )
                        Text(
                            text = component.category,
                            fontSize = sy(14).value.sp,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(top = sy(4))
                        )
                    }

                    Text(
                        text = "Press BACK to close",
                        fontSize = sy(12).value.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(sy(30)))

                // Scrollable content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(sy(24))
                ) {
                    // Description
                    item {
                        SectionTitle("Description", sy)
                        Text(
                            text = component.description,
                            fontSize = sy(14).value.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = sy(20).value.sp
                        )
                    }

                    // Documentation
                    if (component.documentation.isNotEmpty()) {
                        item {
                            SectionTitle("Documentation", sy)
                            Text(
                                text = component.documentation,
                                fontSize = sy(13).value.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = sy(18).value.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }

                    // Usage example
                    if (component.usage.isNotEmpty()) {
                        item {
                            SectionTitle("Usage Example", sy)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color.Black.copy(alpha = 0.4f),
                                        RoundedCornerShape(sx(8))
                                    )
                                    .padding(sx(16))
                            ) {
                                Text(
                                    text = component.usage,
                                    fontSize = sy(11).value.sp,
                                    color = Color(0xFF5AECD3),
                                    lineHeight = sy(16).value.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Interactive demo
                    item {
                        SectionTitle("Interactive Demo", sy)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.Black.copy(alpha = 0.3f),
                                    RoundedCornerShape(sx(12))
                                )
                                .padding(sx(24)),
                            contentAlignment = Alignment.Center
                        ) {
                            component.content(isParentFocused = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, sy: (Int) -> androidx.compose.ui.unit.Dp) {
    Text(
        text = title,
        fontSize = sy(18).value.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(bottom = sy(8))
    )
}

@Composable
private fun ComponentShowcaseItem(
    component: ComponentInfo,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    onClick: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    var isItemFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                val nowFocused = focusState.isFocused
                if (nowFocused != isItemFocused) {
                    isItemFocused = nowFocused
                    onFocusChanged(nowFocused)
                }
            }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(sx(12)),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0x1A5AECD3) else Color(0x0A000000)
        ),
        border = if (isFocused)
            androidx.compose.foundation.BorderStroke(sx(2), Color(0xFF5AECD3))
        else
            androidx.compose.foundation.BorderStroke(sx(1), Color(0x20FFFFFF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sx(20))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = component.name,
                        fontSize = sy(18).value.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) Color(0xFF5AECD3) else Color.White
                    )
                    Text(
                        text = component.category,
                        fontSize = sy(12).value.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(top = sy(2))
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isFocused) Color(0xFF5AECD3) else Color(0xFF444444),
                            shape = RoundedCornerShape(sx(16))
                        )
                        .padding(horizontal = sx(12), vertical = sy(4))
                ) {
                    Text(
                        text = component.id,
                        fontSize = sy(10).value.sp,
                        color = if (isFocused) Color(0xFF48227C) else Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Description
            Text(
                text = component.description,
                fontSize = sy(14).value.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = sy(12))
            )

            // Component preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(sx(8))
                    )
                    .padding(sx(16))
            ) {
                component.content(isParentFocused = isFocused)
            }
        }
    }
}