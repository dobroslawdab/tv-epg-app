package com.uxellence.tv.v3.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ui.PlayerView

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpgScreen(
    guide: EpgGuide,
    windowStart: Instant? = null,
    windowEnd: Instant? = null,
    onBackPressed: () -> Unit = {}
) {
    val channels = guide.channels
    val programsByChannel = remember(guide) { guide.programs.groupBy { it.channelId }.mapValues { it.value.sortedBy { p -> p.startUtc } } }
    val now = Instant.now()
    val start = windowStart ?: (programsByChannel.values.flatten().minByOrNull { it.startUtc }?.startUtc ?: now.minus(Duration.ofHours(1)))
    val end = windowEnd ?: (programsByChannel.values.flatten().maxByOrNull { it.endUtc }?.endUtc ?: now.plus(Duration.ofHours(2)))
    val totalMinutes = Duration.between(start, end).toMinutes().coerceAtLeast(60)
    val widthPerMinute = 6.dp
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberLazyListState()

    // Skalowanie względem projektu 1920x1080
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp
    val rowHeight = sy(64)
    val rowSpacing = sy(6)
    val channelsColumnWidth = sx(288)
    val density = LocalDensity.current
    val pxPerMin = with(density) { widthPerMinute.toPx() }
    val viewportPx = with(density) { (configuration.screenWidthDp.dp - channelsColumnWidth).toPx() }
    
    // Focus target row (5th row from top, 0-indexed = 4)
    val focusTargetRow = 4

    // Kolory
    val colorBg = Color(0xFF48227C)
    val colorTextPrimary = Color(0xFFEEEEEE)
    val colorFocusBg = Color(0xFF85E9D4)
    val colorFocusText = Color(0xFF48227C)
    val tileShape = RoundedCornerShape(8.dp)

    // Proste sterowanie zaznaczeniem (bez systemu focus Compose)
    var channelIndex by remember { mutableStateOf(0) }
    var programIndex by remember { mutableStateOf(0) }

    // Inicjalny wybór: bieżący program na pierwszym kanale
    LaunchedEffect(guide) {
        val firstChannelId = channels.firstOrNull()?.id ?: return@LaunchedEffect
        val row = programsByChannel[firstChannelId].orEmpty()
        val idx = row.indexOfFirst { !now.isBefore(it.startUtc) && now.isBefore(it.endUtc) }.let { if (it >= 0) it else 0 }
        channelIndex = 0
        programIndex = idx.coerceAtLeast(0)
        // Wycentruj widok na starcie
        row.getOrNull(programIndex)?.let { p ->
            val leftPx = Duration.between(start, p.startUtc).toMinutes().coerceAtLeast(0) * pxPerMin
            val rightPx = Duration.between(start, p.endUtc).toMinutes().coerceAtLeast(0) * pxPerMin
            val center = (leftPx + rightPx) / 2f
            val target = (center - viewportPx / 2f).coerceAtLeast(0f)
            horizontalScrollState.scrollTo(target.toInt())
            // Also scroll vertically to keep focus on target row
            verticalScrollState.scrollToItem(channelIndex.coerceAtLeast(focusTargetRow) - focusTargetRow)
        }
    }

    // Funkcja: przewiń tak, by wybrany program był w kadrze (centrowanie)
    suspend fun bringSelectedIntoView() {
        val chId = channels.getOrNull(channelIndex)?.id ?: return
        val row = programsByChannel[chId].orEmpty()
        val p = row.getOrNull(programIndex) ?: return
        val leftPx = Duration.between(start, p.startUtc).toMinutes().coerceAtLeast(0) * pxPerMin
        val rightPx = Duration.between(start, p.endUtc).toMinutes().coerceAtLeast(0) * pxPerMin
        val center = (leftPx + rightPx) / 2f
        val contentPx = totalMinutes * pxPerMin
        val target = (center - viewportPx / 2f)
            .coerceAtLeast(0f)
            .coerceAtMost((contentPx - viewportPx).coerceAtLeast(0f))
        horizontalScrollState.scrollTo(target.toInt())
        // Keep focus on target row
        verticalScrollState.scrollToItem(channelIndex.coerceAtLeast(focusTargetRow) - focusTargetRow)
    }

    val scope = rememberCoroutineScope()
    val rootFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorBg)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { keyEvent: androidx.compose.ui.input.key.KeyEvent ->
                if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when (keyEvent.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val chId = channels.getOrNull(channelIndex)?.id
                        if (programIndex > 0) {
                            programIndex -= 1
                            scope.launch { bringSelectedIntoView() }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val chId = channels.getOrNull(channelIndex)?.id
                        val row = programsByChannel[chId]?.orEmpty() ?: emptyList()
                        if (programIndex < row.lastIndex) {
                            programIndex += 1
                            scope.launch { bringSelectedIntoView() }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        if (channelIndex > 0) {
                            val current = channels[channelIndex]
                            val currentRow = programsByChannel[current.id].orEmpty()
                            val currentProg = currentRow.getOrNull(programIndex)
                            channelIndex -= 1
                            val targetChId = channels[channelIndex].id
                            val targetRow = programsByChannel[targetChId].orEmpty()
                            programIndex = if (currentProg != null) {
                                // wybierz program o najbliższym środku czasu
                                val mid = currentProg.startUtc.epochSecond + (currentProg.endUtc.epochSecond - currentProg.startUtc.epochSecond) / 2
                                targetRow.indices.minByOrNull { i ->
                                    val tp = targetRow[i]
                                    val tMid = tp.startUtc.epochSecond + (tp.endUtc.epochSecond - tp.startUtc.epochSecond) / 2
                                    kotlin.math.abs(tMid - mid)
                                } ?: 0
                            } else 0
                            scope.launch { bringSelectedIntoView() }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (channelIndex < channels.lastIndex) {
                            val current = channels[channelIndex]
                            val currentRow = programsByChannel[current.id].orEmpty()
                            val currentProg = currentRow.getOrNull(programIndex)
                            channelIndex += 1
                            val targetChId = channels[channelIndex].id
                            val targetRow = programsByChannel[targetChId].orEmpty()
                            programIndex = if (currentProg != null) {
                                val mid = currentProg.startUtc.epochSecond + (currentProg.endUtc.epochSecond - currentProg.startUtc.epochSecond) / 2
                                targetRow.indices.minByOrNull { i ->
                                    val tp = targetRow[i]
                                    val tMid = tp.startUtc.epochSecond + (tp.endUtc.epochSecond - tp.startUtc.epochSecond) / 2
                                    kotlin.math.abs(tMid - mid)
                                } ?: 0
                            } else 0
                            scope.launch { bringSelectedIntoView() }
                        }
                        true
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        onBackPressed()
                        true
                    }
                    else -> false
                }
            }
    ) {
        LaunchedEffect(Unit) { rootFocus.requestFocus() }
        Column(modifier = Modifier.fillMaxSize()) {
            // Time axis row (fixed header)
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(channelsColumnWidth))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(horizontalScrollState)
                        .height(40.dp)
                        .padding(vertical = 8.dp)
                ) {
                    val startZ = ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0)
                    val endZ = ZonedDateTime.ofInstant(end, ZoneId.systemDefault()).plusMinutes(59).withMinute(0).withSecond(0).withNano(0)
                    var cursorHour = startZ
                    while (!cursorHour.isAfter(endZ)) {
                        Box(
                            modifier = Modifier.width(widthPerMinute * 60f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = String.format("%02d:00", cursorHour.hour), color = Color(0xCCEEEEEE), fontSize = 12.sp)
                        }
                        cursorHour = cursorHour.plusHours(1)
                    }
                }
            }
            
            // Scrollable content
            LazyColumn(
                state = verticalScrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(channels) { chIdx, ch ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Channel column
                        Box(
                            modifier = Modifier
                                .width(channelsColumnWidth)
                                .height(rowHeight)
                                .background(Color(0x22000000), shape = tileShape)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = "https://epg.ovh/logo/${ch.id}.png",
                                    contentDescription = ch.name,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    ch.name,
                                    color = if (chIdx == channelIndex) colorFocusBg else colorTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        // Programs row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(widthPerMinute * totalMinutes.toFloat())
                                    .fillMaxHeight()
                            ) {
                                // Programs for this channel
                                Row(modifier = Modifier.matchParentSize()) {
                                    val row = programsByChannel[ch.id].orEmpty()
                                    var cursor = start
                                    row.forEachIndexed { pIdx, p ->
                                        val leftGap = Duration.between(cursor, p.startUtc).toMinutes().coerceAtLeast(0)
                                        if (leftGap > 0) Spacer(Modifier.width(widthPerMinute * leftGap.toFloat()))
                                        val width = Duration.between(maxOf(p.startUtc, start), minOf(p.endUtc, end)).toMinutes().coerceAtLeast(1)
                                        val isNow = !now.isBefore(p.startUtc) && now.isBefore(p.endUtc)
                                        val isSelected = (chIdx == channelIndex && pIdx == programIndex)
                                        ProgramTile(
                                            title = p.title,
                                            modifier = Modifier
                                                .width(widthPerMinute * width.toFloat())
                                                .fillMaxHeight(),
                                            colorTextPrimary = colorTextPrimary,
                                            colorFocusBg = colorFocusBg,
                                            colorFocusText = colorFocusText,
                                            shape = tileShape,
                                            isNow = isNow,
                                            isSelected = isSelected
                                        )
                                        cursor = p.endUtc
                                    }
                                }
                                
                                // Hour dividers for this row
                                val startZ = ZonedDateTime.ofInstant(start, ZoneId.systemDefault()).withMinute(0).withSecond(0).withNano(0)
                                val endZ = ZonedDateTime.ofInstant(end, ZoneId.systemDefault()).plusMinutes(59).withMinute(0).withSecond(0).withNano(0)
                                var cursorHour = startZ
                                while (!cursorHour.isAfter(endZ)) {
                                    val minutesFromStart = Duration.between(start, cursorHour.toInstant()).toMinutes().coerceIn(0, totalMinutes)
                                    Box(
                                        modifier = Modifier
                                            .offset(x = widthPerMinute * minutesFromStart.toFloat())
                                            .width(1.dp)
                                            .fillMaxHeight()
                                            .background(Color(0x33EEEEEE))
                                            .zIndex(100f)
                                    )
                                    cursorHour = cursorHour.plusHours(1)
                                }
                                
                                // Current time line for this row
                                val minutesFromStartNow = Duration.between(start, now).toMinutes().coerceIn(0, totalMinutes)
                                if (minutesFromStartNow in 1 until totalMinutes) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = widthPerMinute * minutesFromStartNow.toFloat())
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(Color(0xFF5AECD3))
                                            .zIndex(200f)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Row spacing
                    Spacer(Modifier.height(rowSpacing))
                }
            }
        }
        
        // Program Detail Box (lewy dolny róg)
        ProgramDetailBox(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .zIndex(200f),
            program = channels.getOrNull(channelIndex)?.let { ch ->
                programsByChannel[ch.id]?.getOrNull(programIndex)
            },
            channelName = channels.getOrNull(channelIndex)?.name
        )
    }
}

@Composable
fun ProgramDetailBox(
    modifier: Modifier = Modifier,
    program: EpgProgram?,
    channelName: String?
) {
    if (program == null) return
    
    // Skalowanie względem projektu 1920x1080
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp
    
    val detailBoxWidth = sx(1280)  // Szerokość jak w Figma
    val detailBoxHeight = sy(320)   // Wysokość jak w Figma
    
    // Kolory z Figma design
    val colorDetailBg = Color(0xFF281443)
    val colorAqua = Color(0xFF5FEDD4)
    val colorTextPrimary = Color(0xFFEEEEEE)
    val colorTextSecondary = Color(0xCCEEEEEE)
    
    // Format daty i czasu
    val now = Instant.now()
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale("pl", "PL"))
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val zone = java.time.ZoneId.systemDefault()
    
    val dateText = now.atZone(zone).format(dateFormatter).uppercase()
    val startTime = program.startUtc.atZone(zone).format(timeFormatter)
    val endTime = program.endUtc.atZone(zone).format(timeFormatter)
    val timeRange = "$startTime – $endTime"
    
    Box(
        modifier = modifier
            .width(detailBoxWidth)
            .height(detailBoxHeight)
            .background(
                colorDetailBg,
                RoundedCornerShape(sx(32))
            )
            .padding(sx(40))
    ) {
        // Data w lewym górnym rogu
        Text(
            text = dateText,
            color = colorAqua,
            fontSize = (20 * scaleY).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-sx(16)), y = (-sy(22)))
        )
        
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(sx(30))
        ) {
            // Lewa strona - Ilustracja programu
            Box(
                modifier = Modifier
                    .width(sx(260))
                    .height(sy(147))
                    .background(
                        Color(0xFF1A0B2E),
                        RoundedCornerShape(sx(8))
                    )
                    .clip(RoundedCornerShape(sx(8)))
            ) {
                // Spróbuj załadować obraz programu
                val imageUrl = program.iconUrl ?: generateProgramImageUrl(program.title, channelName)
                
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Obraz programu ${program.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Gradient overlay z Figma design
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    Color(0x38271344),
                                    Color(0x00271344)
                                ),
                                center = Offset(65f, 300f),
                                radius = 250f
                            )
                        )
                )
                
                // Logo kanału na obrazie (prawy dolny róg)
                channelName?.let { name ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-sx(8)), y = (-sy(8)))
                            .background(
                                Color(0x99000000),
                                RoundedCornerShape(sx(6))
                            )
                            .padding(horizontal = sx(8), vertical = sy(4))
                    ) {
                        Text(
                            text = name,
                            color = colorTextPrimary,
                            fontSize = (12 * scaleY).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Prawa strona - Informacje o programie
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(sy(12))
            ) {
                // Czas trwania
                Text(
                    text = timeRange,
                    color = colorTextSecondary,
                    fontSize = (32 * scaleY).sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Tytuł programu
                Text(
                    text = program.title,
                    color = colorTextPrimary,
                    fontSize = (48 * scaleY).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (64 * scaleY).sp
                )
                
                // Metadata row - clean data without prefixes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(16)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create clean metadata string from available program data
                    val metadataItems = buildList {
                        // Add genre/category if available - clean format without prefixes
                        if (program.categories.isNotEmpty()) {
                            val category = program.categories.first()
                            // Remove prefixes like "G:", "R:", etc.
                            val cleanCategory = category.split(":").lastOrNull()?.trim() ?: category
                            if (cleanCategory.isNotBlank()) {
                                add(cleanCategory)
                            }
                        }
                        
                        // Extract year from start time if available
                        val year = program.startUtc.atZone(zone).year
                        if (year > 1900) {
                            add("$year r.")
                        }
                        
                        // Add default country
                        add("Polska")
                    }
                    
                    // Display metadata with dividers
                    metadataItems.forEachIndexed { index, item ->
                        if (index > 0) {
                            // Divider
                            Box(
                                modifier = Modifier
                                    .width(sx(2))
                                    .height(sy(24))
                                    .background(colorTextSecondary)
                            )
                        }
                        
                        Text(
                            text = item,
                            color = colorTextSecondary,
                            fontSize = (20 * scaleY).sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Opis programu - 2 linie jak w Figma
                program.description?.let { description ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(4))
                    ) {
                        // Split description into 2 lines
                        val words = description.split(" ")
                        val maxWordsPerLine = words.size / 2
                        val firstLine = words.take(maxWordsPerLine).joinToString(" ")
                        val secondLine = words.drop(maxWordsPerLine).joinToString(" ")
                        
                        // First line of description
                        if (firstLine.isNotBlank()) {
                            Text(
                                text = firstLine,
                                color = colorTextPrimary,
                                fontSize = (28 * scaleY).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = (40 * scaleY).sp
                            )
                        }
                        
                        // Second line of description
                        if (secondLine.isNotBlank()) {
                            Text(
                                text = secondLine,
                                color = colorTextPrimary,
                                fontSize = (28 * scaleY).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = (40 * scaleY).sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Mini-player (bottom-right) outside main Box: wrap in a positioning Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .zIndex(50f),
        contentAlignment = Alignment.BottomEnd
    ) {
        MiniPlayer()
    }
}

@Composable
fun ProgramTile(
    title: String,
    modifier: Modifier,
    colorTextPrimary: Color,
    colorFocusBg: Color,
    colorFocusText: Color,
    shape: RoundedCornerShape,
    isNow: Boolean,
    isSelected: Boolean
) {
    Box(
        modifier = modifier
            .background(
                when {
                    isSelected -> colorFocusBg
                    isNow -> Color(0x33000000) // 20%
                    else -> Color(0x1A000000) // 10%
                },
                shape = shape
            )
            .border(width = if (isSelected) 2.dp else 0.dp, color = colorFocusText, shape = shape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = title,
            color = if (isSelected) colorFocusText else colorTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Usunięto chipy kategorii, oś czasu i dodatkowe panele

// Usunięto oś czasu wraz z linią "TERAZ"


// Generator URL obrazków programów
fun generateProgramImageUrl(title: String, channelName: String?): String {
    // Używamy różnych serwisów obrazków jako fallback
    val cleanTitle = title.replace(" ", "+").take(20)
    val imageServices = listOf(
        "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=260&h=147&fit=crop&q=80", // TV/Film generic
        "https://images.unsplash.com/photo-1489599006857-f0e37d2daa5c?w=260&h=147&fit=crop&q=80", // Camera/Media
        "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=260&h=147&fit=crop&q=80", // Abstract/TV
        "https://images.unsplash.com/photo-1598300042247-d088f8ab3a91?w=260&h=147&fit=crop&q=80", // Studio/Entertainment
        "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=260&h=147&fit=crop&q=80"  // Media/Broadcasting
    )
    
    // Wybierz obraz na podstawie hash tytułu dla consistency
    val index = kotlin.math.abs(title.hashCode()) % imageServices.size
    return imageServices[index]
}

// Prosty test: filtr programów dla kanału TVN na dziś
fun EpgGuide.programsForTvnToday(now: Instant = Instant.now()): List<EpgProgram> {
    val startOfDay = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
    val endOfDay = startOfDay.plus(Duration.ofDays(1))
    val tvnId = channels.firstOrNull { it.name.contains("TVN", ignoreCase = true) }?.id ?: return emptyList()
    return programs.filter { it.channelId == tvnId && it.startUtc.isBefore(endOfDay) && it.endUtc.isAfter(startOfDay) }
}

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    // PIP live: TVP1 (pierwszy kanał z listy ChannelManagera); parametr
    // zostaje jako fallback gdyby lista nie miała kanałów
    streamUrl: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleY = configuration.screenHeightDp / 1080f
    fun sy(px: Int) = (px * scaleY).dp
    // Dopasuj wysokość do sekcji opisu programu
    val targetHeight = sy(320)
    val targetWidth = targetHeight * (16f / 9f)
    // Live URL pierwszego kanału (TVP1 HD) — jak przy wejściu w Telewizję live
    val liveUrl = remember {
        streamUrl ?: run {
            if (!com.uxellence.tv.v3.channels.ChannelManager.isInitialized()) {
                try {
                    com.uxellence.tv.v3.channels.ChannelManager.initialize(context)
                } catch (_: Exception) { }
            }
            com.uxellence.tv.v3.channels.ChannelManager
                .getAllChannels(includeUnavailable = false)
                .firstOrNull()?.streamUrl
        }
    }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            liveUrl?.let { setMediaItem(MediaItem.fromUri(it)) }
            playWhenReady = true
            volume = 0f
            prepare()
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    Box(modifier = modifier
        .width(targetWidth)
        .height(targetHeight)
        .background(Color.Black, RoundedCornerShape(16.dp))
        .clip(RoundedCornerShape(16.dp))
    ) {
        // TextureView zamiast PlayerView: SurfaceView w Compose ma problemy
        // z z-orderem (czarny prostokąt) — patrz CLAUDE.md "Known Issues"
        AndroidView(
            factory = { ctx ->
                android.view.TextureView(ctx).also { textureView ->
                    player.setVideoTextureView(textureView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}


