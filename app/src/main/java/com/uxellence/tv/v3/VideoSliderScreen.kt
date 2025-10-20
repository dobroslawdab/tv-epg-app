package com.uxellence.tv.v3

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.offset
import androidx.compose.ui.zIndex
import android.widget.VideoView
import android.net.Uri
import coil.compose.AsyncImage
import com.uxellence.tv.v3.ui.theme.figmaRadialBackground
import com.uxellence.tv.v3.version001.VodContent
import com.uxellence.tv.v3.version001.loadVodContentFromAssets
import kotlinx.coroutines.launch

// Kolory z Figma
object HotelCardColors {
    val Background = Color(0xFF5B3987)
    val TextPrimary = Color(0xFFEEEEEE)
    val ButtonBackground = Color(0xFF5FEDD4)
    val ButtonText = Color(0xFF48227C)
    val GradientStart = Color(0xFF5A3887)
    val GradientEnd = Color(0x005A3887)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VideoSliderScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Create slides data - 3 hotel video slides
    val slides = remember {
        listOf(
            // Hotel slide 1
            VodContent(
                title = "Grand Budapest Hotel",
                description = "Odkryj magię tego wyjątkowego hotelu, gdzie każdy pokój to opowieść, a każdy gość staje się częścią legendy. Położony w sercu malowniczego miasta, oferuje niezapomniane doświadczenia i najwyższy standard obsługi.",
                category = "Hotel",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://www.dropbox.com/scl/fi/vr14sy7ygk5bwtig5w9cm/bob.mp4?rlkey=t1sjv5volsfyqkhjrmuh8mncm&st=fl2mmby7&dl=1"
            ),
            // Hotel slide 2
            VodContent(
                title = "Luxury Resort & Spa",
                description = "Nowoczesny ośrodek wypoczynkowy z własną plażą i centrum SPA. Idealny na relaks i regenerację w otoczeniu przepięknej natury. Każdy dzień tutaj to niezapomniane chwile pełne luksusu i komfortu.",
                category = "Resort",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://www.dropbox.com/scl/fi/vr14sy7ygk5bwtig5w9cm/bob.mp4?rlkey=t1sjv5volsfyqkhjrmuh8mncm&st=fl2mmby7&dl=1"
            ),
            // Hotel slide 3
            VodContent(
                title = "Mountain Lodge Retreat",
                description = "Przytulny górski domek z widokiem na zaśnieżone szczyty. Idealne miejsce dla miłośników przygód zimowych i ciszy natury. Atmosfera jak z bajki, gdzie czas płynie wolniej.",
                category = "Lodge",
                imageUrl = "",
                channelLogoUrl = "",
                link = "https://www.dropbox.com/scl/fi/vr14sy7ygk5bwtig5w9cm/bob.mp4?rlkey=t1sjv5volsfyqkhjrmuh8mncm&st=fl2mmby7&dl=1"
            )
        )
    }

    var focusedIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequesters = remember(slides.size) { List(slides.size) { FocusRequester() } }

    // This effect handles scrolling and requesting focus when the focusedIndex changes.
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < slides.size) {
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
                // We only handle KeyDown events.
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }

                // Prevent handling events while scrolling.
                if (listState.isScrollInProgress) {
                    return@onPreviewKeyEvent true
                }

                val newIndex = when (event.key) {
                    Key.DirectionLeft -> if (focusedIndex > 0) focusedIndex - 1 else focusedIndex
                    Key.DirectionRight -> if (focusedIndex < slides.size - 1) focusedIndex + 1 else focusedIndex
                    else -> focusedIndex
                }

                if (newIndex != focusedIndex) {
                    focusedIndex = newIndex
                    return@onPreviewKeyEvent true // Consume the event
                }

                false // Don't consume other events
            },
        contentAlignment = Alignment.Center
    ) {
        // Request focus for the first item when the screen is first displayed.
        LaunchedEffect(Unit) {
            if (slides.isNotEmpty()) {
                focusRequesters.firstOrNull()?.requestFocus()
            }
        }

        if (slides.isNotEmpty()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = sx(134)), // (1920 - 1652) / 2
                horizontalArrangement = Arrangement.spacedBy(sx(20)),
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = false // Disable scroll by drag
            ) {
                itemsIndexed(slides) { index, slide ->
                    HotelSlideCard(
                        hotel = slide,
                        isFocused = index == focusedIndex,
                        focusRequester = focusRequesters[index],
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSlideCard(
    movie: VodContent,
    slideIndex: Int,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (isFocused: Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    isVideoSlide: Boolean = false
) {
    var isButtonFocused by remember { mutableStateOf(false) }

    // Main container - 1652x742px with purple background
    Box(
        modifier = Modifier
            .width(sx(1652))
            .height(sy(742))
            .clip(RoundedCornerShape(sx(20)))
            .background(Color(0xFF5B3987))
    ) {
        // Video positioned at left: 334px, top: 0px, size: 1318x742px
        if (isVideoSlide && isButtonFocused) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse("https://www.dropbox.com/scl/fi/vr14sy7ygk5bwtig5w9cm/bob.mp4?rlkey=t1sjv5volsfyqkhjrmuh8mncm&st=fl2mmby7&dl=1"))
                        setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = false
                            start()
                        }
                        setOnErrorListener { _, _, _ ->
                            // Log error but don't crash
                            false
                        }
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                },
                modifier = Modifier
                    .offset(x = sx(334), y = sy(0))
                    .width(sx(1318))
                    .height(sy(742))
                    .zIndex(2f) // Ensure video is above gradient
                    .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20)))
            )
        } else {
            // Placeholder background when video is not playing
            Box(
                modifier = Modifier
                    .offset(x = sx(334), y = sy(0))
                    .width(sx(1318))
                    .height(sy(742))
                    .zIndex(1f)
                    .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20)))
                    .background(Color(0xFF2A1B3D)) // Darker purple background
            )
        }

        // Gradient overlay positioned at left: 334px, top: 0px, size: 1318x742px (identical to image)
        Box(
            modifier = Modifier
                .offset(x = sx(334), y = sy(0))
                .width(sx(1318))
                .height(sy(742))
                .zIndex(3f) // Gradient above video and image
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF5A3887),
                            Color(0x005A3887) // rgba(90, 56, 135, 0)
                        )
                    )
                )
        )

        // Content area with padding: 100px, width: 874px
        Column(
            modifier = Modifier
                .padding(sx(100))
                .width(sx(874))
                .fillMaxHeight()
                .zIndex(4f), // Content above everything
            verticalArrangement = Arrangement.Bottom
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(sy(31))
            ) {
                // Text Details - always show video slide content
                Column(
                    verticalArrangement = Arrangement.spacedBy(sy(14))
                ) {
                    // Title - font-size: 64px, line-height: 88px
                    Text(
                        text = movie.title,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(64).value.sp,
                        fontWeight = FontWeight.W500,
                        lineHeight = sy(88).value.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Description - font-size: 28px, line-height: 40px, max-height: 82px
                    Text(
                        text = movie.description,
                        color = Color(0xFFEEEEEE),
                        fontSize = sy(28).value.sp,
                        fontWeight = FontWeight.W500,
                        lineHeight = sy(40).value.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(max = sy(82))
                    )
                }

                // Action Button - max-width: 472px, height: 72px
                Button(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .widthIn(max = sx(472))
                        .height(sy(72))
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            isButtonFocused = it.isFocused
                            onFocusChanged(it.isFocused)
                        },
                    shape = RoundedCornerShape(sx(8)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isButtonFocused) Color(0xFF5FEDD4) else Color.Transparent,
                        contentColor = if (isButtonFocused) Color(0xFF48227C) else Color(0xFFEEEEEE)
                    ),
                    border = if (!isButtonFocused) BorderStroke(2.dp, Color(0xFFEEEEEE)) else null
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Dowiedz sie wiecej",
                            fontSize = sy(24).value.sp,
                            fontWeight = FontWeight.W700,
                            lineHeight = sy(32).value.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelSlideCard(
    hotel: VodContent,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Card(
        modifier = Modifier
            .width(sx(1652))
            .height(sy(742)),
        shape = RoundedCornerShape(sx(20)),
        colors = CardDefaults.cardColors(
            containerColor = HotelCardColors.Background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 16.dp else 8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Background Video positioned at right side
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                if (isFocused) {
                    AndroidView(
                        factory = { context ->
                            VideoView(context).apply {
                                // Use hotel.link to determine video resource name
                                val videoFileName = hotel.link.replace(".mp4", "")
                                val resourceId = context.resources.getIdentifier(videoFileName, "raw", context.packageName)
                                setVideoURI(Uri.parse("android.resource://${context.packageName}/$resourceId"))
                                setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = true // Loop the video for continuous playback
                                    start()
                                }
                                setOnErrorListener { _, _, _ ->
                                    // Log error but don't crash
                                    false
                                }
                                setOnCompletionListener {
                                    // Reset video to beginning when completed
                                    seekTo(0)
                                    start()
                                }
                                isFocusable = false
                                isFocusableInTouchMode = false
                            }
                        },
                        modifier = Modifier
                            .width(sx(1318))
                            .height(sy(742))
                            .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20)))
                    )
                } else {
                    // Placeholder background when video is not playing
                    Box(
                        modifier = Modifier
                            .width(sx(1318))
                            .height(sy(742))
                            .clip(RoundedCornerShape(topEnd = sx(20), bottomEnd = sx(20)))
                            .background(Color(0xFF2A1B3D)) // Darker purple background
                    )
                }

                // Gradient overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    HotelCardColors.GradientStart,
                                    HotelCardColors.GradientEnd
                                ),
                                endX = sx(1318).value * 0.75f
                            )
                        )
                )
            }

            // Content on the left
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sx(874))
                    .padding(sx(100)),
                verticalArrangement = Arrangement.spacedBy(sy(31))
            ) {
                // Text Details
                Column(verticalArrangement = Arrangement.spacedBy(sy(14))) {
                    Text(
                        text = hotel.title,
                        color = HotelCardColors.TextPrimary,
                        fontSize = sy(64).value.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = hotel.description,
                        color = HotelCardColors.TextPrimary,
                        fontSize = sy(28).value.sp,
                        fontWeight = FontWeight.W500,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = (sy(28).value * 1.43).sp
                    )
                }

                // Action Button
                Button(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .widthIn(max = sx(472))
                        .height(sy(72))
                        .focusRequester(focusRequester)
                        .focusable(true), // Ensure the button can receive focus
                    shape = RoundedCornerShape(sx(8)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFocused) HotelCardColors.ButtonBackground else Color.Transparent,
                        contentColor = if (isFocused) HotelCardColors.ButtonText else HotelCardColors.TextPrimary
                    ),
                    border = if (!isFocused) BorderStroke(2.dp, HotelCardColors.TextPrimary) else null
                ) {
                    Text(
                        text = "Sprawdź dostępność",
                        fontSize = sy(24).value.sp,
                        fontWeight = FontWeight.W700
                    )
                }
            }
        }
    }
}

@Composable
private fun metadataStyle(sy: (Int) -> androidx.compose.ui.unit.Dp) = TextStyle(
    color = Color(0xCCEEEEEE),
    fontSize = sy(20).value.sp,
    fontWeight = FontWeight.W700,
    letterSpacing = 0.4.sp
)

@Composable
private fun MetadataSeparator(sy: (Int) -> androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier
        .width(2.dp)
        .height(sy(24))
        .background(Color(0xCCEEEEEE)))
}