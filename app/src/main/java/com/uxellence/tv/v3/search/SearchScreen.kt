package com.uxellence.tv.v3.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale

enum class SearchFocusMode { RESULTS, TOGGLE, INPUT, VOICE }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    val searchState by viewModel.searchState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    // Focus state for search results navigation
    var focusedConversationIndex by remember { mutableStateOf(-1) }
    var focusedPosterIndex by remember { mutableStateOf(-1) }
    var isInResultsMode by remember { mutableStateOf(false) }

    // New navigation states
    var currentFocusMode by remember { mutableStateOf<SearchFocusMode?>(null) }
    var showSearchInput by remember { mutableStateOf(searchState.conversationHistory.isEmpty()) }

    // Auto focus on load
    LaunchedEffect(shouldAutoFocus) {
        if (shouldAutoFocus) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
        }
    }

    // Initialize speech recognition
    LaunchedEffect(Unit) {
        viewModel.initializeSpeechRecognition(context)
    }

    // Auto scroll to bottom when new conversation added
    LaunchedEffect(searchState.conversationHistory.size) {
        if (searchState.conversationHistory.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(searchState.conversationHistory.size - 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF48227C)) // Purple background like other sections
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            when (currentFocusMode) {
                                SearchFocusMode.RESULTS -> {
                                    // From results back to menu
                                    onReturnToMenu()
                                    true
                                }
                                SearchFocusMode.TOGGLE -> {
                                    // From toggle to results (if available)
                                    val lastConversation = searchState.conversationHistory.lastOrNull()
                                    if (lastConversation?.recommendations?.isNotEmpty() == true) {
                                        currentFocusMode = SearchFocusMode.RESULTS
                                        isInResultsMode = true
                                        focusedConversationIndex = searchState.conversationHistory.size - 1
                                        focusedPosterIndex = 0
                                        true
                                    } else {
                                        // No results, go to menu
                                        onReturnToMenu()
                                        true
                                    }
                                }
                                SearchFocusMode.INPUT -> {
                                    // From input to toggle
                                    currentFocusMode = SearchFocusMode.TOGGLE
                                    true
                                }
                                SearchFocusMode.VOICE -> {
                                    // From voice to input
                                    currentFocusMode = SearchFocusMode.INPUT
                                    true
                                }
                                null -> {
                                    if (searchState.conversationHistory.isEmpty()) {
                                        onReturnToMenu()
                                        true
                                    } else false
                                }
                            }
                        }
                        Key.DirectionDown -> {
                            when (currentFocusMode) {
                                null -> {
                                    // Coming from menu - check hierarchy
                                    val lastConversation = searchState.conversationHistory.lastOrNull()
                                    if (lastConversation?.recommendations?.isNotEmpty() == true) {
                                        // Go to results first
                                        currentFocusMode = SearchFocusMode.RESULTS
                                        isInResultsMode = true
                                        focusedConversationIndex = searchState.conversationHistory.size - 1
                                        focusedPosterIndex = 0
                                    } else if (showSearchInput) {
                                        // Go to toggle
                                        currentFocusMode = SearchFocusMode.TOGGLE
                                    }
                                    true
                                }
                                SearchFocusMode.RESULTS -> {
                                    // From results to toggle (if search input shown)
                                    if (showSearchInput) {
                                        currentFocusMode = SearchFocusMode.TOGGLE
                                        isInResultsMode = false
                                        true
                                    } else false
                                }
                                SearchFocusMode.TOGGLE -> {
                                    // From toggle to input
                                    currentFocusMode = SearchFocusMode.INPUT
                                    true
                                }
                                SearchFocusMode.INPUT -> {
                                    // From input to voice
                                    currentFocusMode = SearchFocusMode.VOICE
                                    true
                                }
                                SearchFocusMode.VOICE -> false // Bottom level
                            }
                        }
                        Key.DirectionLeft -> {
                            if (currentFocusMode == SearchFocusMode.RESULTS && focusedPosterIndex > 0) {
                                focusedPosterIndex--
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (currentFocusMode == SearchFocusMode.RESULTS) {
                                val currentConversation = searchState.conversationHistory.getOrNull(focusedConversationIndex)
                                val maxIndex = (currentConversation?.recommendations?.size ?: 0) - 1
                                if (focusedPosterIndex < maxIndex) {
                                    focusedPosterIndex++
                                    true
                                } else false
                            } else false
                        }
                        Key.Enter -> {
                            when (currentFocusMode) {
                                SearchFocusMode.INPUT -> {
                                    // Only now focus the input to open keyboard
                                    focusRequester.requestFocus()
                                    true
                                }
                                SearchFocusMode.TOGGLE -> {
                                    viewModel.toggleInternetSearch()
                                    true
                                }
                                SearchFocusMode.VOICE -> {
                                    viewModel.toggleVoiceRecording()
                                    true
                                }
                                else -> false
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = sx(50))
            ) {
                if (searchState.conversationHistory.isEmpty()) {
                    // Welcome screen
                    WelcomeSearchScreen(
                        sx = { sx(it) },
                        sy = { sy(it) },
                        onTextInput = {
                            // Navigate to toggle mode instead of direct focus
                            currentFocusMode = SearchFocusMode.TOGGLE
                        },
                        onVoiceInput = {
                            viewModel.toggleVoiceRecording()
                        },
                        useInternetSearch = searchState.useInternetSearch,
                        onToggleInternetSearch = {
                            viewModel.toggleInternetSearch()
                        }
                    )
                } else {
                    // Conversation history - positioned Y=100px from top (under menu)
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = sy(100), bottom = sy(40)),
                        verticalArrangement = Arrangement.spacedBy(sy(60)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(searchState.conversationHistory) { index, turn ->
                            ConversationTurnItem(
                                turn = turn,
                                isFocusedConversation = currentFocusMode == SearchFocusMode.RESULTS && index == focusedConversationIndex,
                                focusedPosterIndex = if (currentFocusMode == SearchFocusMode.RESULTS && index == focusedConversationIndex) focusedPosterIndex else -1,
                                onPosterFocusChange = { posterIndex ->
                                    focusedPosterIndex = posterIndex
                                },
                                onShowSearchInput = {
                                    showSearchInput = true
                                    currentFocusMode = SearchFocusMode.INPUT
                                },
                                sx = { sx(it) },
                                sy = { sy(it) }
                            )
                        }
                    }
                }
            }

            // Search input bar at bottom - only show when no results or when explicitly requested
            if (showSearchInput) {
                SearchInputBar(
                    query = searchState.currentQuery,
                    onQueryChange = viewModel::updateQuery,
                    onSend = { viewModel.sendQuery() },
                    isLoading = searchState.isLoading,
                    isRecording = searchState.isRecording,
                    onToggleRecording = {
                        viewModel.toggleVoiceRecording()
                    },
                    focusRequester = focusRequester,
                    sx = { sx(it) },
                    sy = { sy(it) },
                    useInternetSearch = searchState.useInternetSearch,
                    onToggleInternetSearch = viewModel::toggleInternetSearch,
                    isFocusedToggle = currentFocusMode == SearchFocusMode.TOGGLE,
                    isFocusedInput = currentFocusMode == SearchFocusMode.INPUT,
                    isFocusedVoice = currentFocusMode == SearchFocusMode.VOICE
                )
            }
        }
    }
}

@Composable
private fun WelcomeSearchScreen(
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    onTextInput: () -> Unit,
    onVoiceInput: () -> Unit,
    useInternetSearch: Boolean = false,
    onToggleInternetSearch: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Only the main title - no duplicate toggle or input
        Text(
            text = "Znajdź swój\nnastępny ulubiony\nfilm lub serial",
            style = androidx.compose.ui.text.TextStyle(
                color = Color(0xFFEEEEEE),
                fontSize = sy(80).value.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = sy(90).value.sp
            ),
            modifier = Modifier.width(sx(795))
        )
    }
}

@Composable
private fun ConversationTurnItem(
    turn: ConversationTurn,
    isFocusedConversation: Boolean,
    focusedPosterIndex: Int,
    onPosterFocusChange: (Int) -> Unit,
    onShowSearchInput: () -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = sx(100), top = sy(80)),
        verticalArrangement = Arrangement.spacedBy(sy(30))
    ) {
        // User query
        Text(
            text = turn.query,
            color = Color.White,
            fontSize = sy(36).value.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = sy(48).value.sp
        )

        // AI response or loading
        if (turn.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(16))
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(sy(32)),
                    color = Color(0xFF5FEDD4),
                    strokeWidth = sy(3)
                )
                Text(
                    text = "Myślę...",
                    color = Color(0xFFAAAAAA),
                    fontSize = sy(24).value.sp
                )
            }
        } else if (turn.error != null) {
            Text(
                text = turn.error,
                color = Color(0xFFFF6B6B),
                fontSize = sy(24).value.sp
            )
        } else if (turn.answer != null) {
            Text(
                text = turn.answer,
                color = Color(0xFFEEEEEE),
                fontSize = sy(28).value.sp,
                lineHeight = sy(38).value.sp
            )

            // Recommendations
            turn.recommendations?.let { recommendations ->
                if (recommendations.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(sy(20))
                    ) {
                        Text(
                            text = "Rekomendacje:",
                            color = Color.White,
                            fontSize = sy(24).value.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = sy(20))
                        )

                        // Horizontal row with poster cards like in Kino Play channel
                        SearchResultsRow(
                            recommendations = recommendations,
                            focusedIndex = if (isFocusedConversation) focusedPosterIndex else -1,
                            onFocusChange = onPosterFocusChange,
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )

                        // Action buttons after recommendations
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(sx(20)),
                            modifier = Modifier.padding(top = sy(30))
                        ) {
                            // "Powiedz więcej" button
                            Button(
                                onClick = { /* TODO: Implement more details */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEEEEEE).copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(sx(8))
                            ) {
                                Text(
                                    text = "Powiedz więcej",
                                    color = Color(0xFFEEEEEE),
                                    fontSize = sy(18).value.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // "Pełny opis" button
                            Button(
                                onClick = { /* TODO: Implement full description */ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEEEEEE).copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(sx(8))
                            ) {
                                Text(
                                    text = "Pełny opis",
                                    color = Color(0xFFEEEEEE),
                                    fontSize = sy(18).value.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // "Szukaj dalej" button
                            Button(
                                onClick = onShowSearchInput,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEEEEEE).copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(sx(8))
                            ) {
                                Text(
                                    text = "Szukaj dalej",
                                    color = Color(0xFFEEEEEE),
                                    fontSize = sy(18).value.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp,
    useInternetSearch: Boolean,
    onToggleInternetSearch: () -> Unit,
    isFocusedToggle: Boolean = false,
    isFocusedInput: Boolean = false,
    isFocusedVoice: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(sx(40)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(sx(50)),
        elevation = CardDefaults.cardElevation(defaultElevation = sx(8))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sx(30), vertical = sy(20))
        ) {
            // Internet search toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(12)),
                modifier = Modifier
                    .padding(bottom = sy(16))
                    .background(
                        color = if (isFocusedToggle) Color(0xFF333333) else Color.Transparent,
                        shape = RoundedCornerShape(sx(8))
                    )
                    .padding(sx(8))
            ) {
                Switch(
                    checked = useInternetSearch,
                    onCheckedChange = { onToggleInternetSearch() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF5FEDD4),
                        checkedTrackColor = Color(0xFF5FEDD4).copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = "Wyszukiwanie internetowe",
                    color = if (isFocusedToggle) Color(0xFF5FEDD4) else Color.White,
                    fontSize = sy(16).value.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(sx(20))
            ) {
            // Text input
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = if (isRecording) "Słucham..." else "Zapytaj o filmy lub seriale...",
                        color = Color(0xFF666666),
                        fontSize = sy(20).value.sp
                    )
                },
                enabled = !isLoading && !isRecording,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { }
                    .background(
                        color = if (isFocusedInput) Color(0xFF333333) else Color.Transparent,
                        shape = RoundedCornerShape(sx(8))
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = if (isFocusedInput) Color(0xFF5FEDD4) else Color(0xFF333333),
                    unfocusedBorderColor = if (isFocusedInput) Color(0xFF5FEDD4) else Color(0xFF333333),
                    cursorColor = Color(0xFF5FEDD4)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = sy(20).value.sp,
                    localeList = LocaleList(Locale("pl-PL"))
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text,
                    autoCorrect = true,
                    capitalization = KeyboardCapitalization.Sentences
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() }
                ),
                singleLine = true
            )

            // Voice button
            IconButton(
                onClick = onToggleRecording,
                enabled = !isLoading,
                modifier = Modifier
                    .size(sy(60))
                    .background(
                        color = when {
                            isRecording -> Color(0xFFFF6B6B)
                            isFocusedVoice -> Color(0xFF5FEDD4)
                            else -> Color(0xFF5FEDD4).copy(alpha = 0.7f)
                        },
                        shape = CircleShape
                    )
                    .border(
                        width = if (isFocusedVoice) 3.dp else 0.dp,
                        color = Color(0xFF5FEDD4),
                        shape = CircleShape
                    )
            ) {
                Text(
                    text = "🎤",
                    fontSize = sy(24).value.sp
                )
            }

            // Send button
            IconButton(
                onClick = onSend,
                enabled = !isLoading && query.trim().isNotEmpty(),
                modifier = Modifier
                    .size(sy(60))
                    .background(
                        color = if (!isLoading && query.trim().isNotEmpty()) Color(0xFF5FEDD4) else Color(0xFF333333),
                        shape = CircleShape
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(sy(24)),
                        color = Color(0xFF0A0A0A),
                        strokeWidth = sy(2)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Wyślij zapytanie",
                        tint = Color(0xFF0A0A0A),
                        modifier = Modifier.size(sy(24))
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun SearchResultsRow(
    recommendations: List<MediaRecommendation>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    // Create FocusRequesters for each poster
    val focusRequesters = remember(recommendations.size) {
        mutableMapOf<Int, FocusRequester>().apply {
            repeat(recommendations.size) { index ->
                put(index, FocusRequester())
            }
        }
    }

    // Auto-focus on focused poster
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0 && focusedIndex < recommendations.size) {
            focusRequesters[focusedIndex]?.requestFocus()
        }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = sx(50)),
        horizontalArrangement = Arrangement.spacedBy(sx(20)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = sy(20))
    ) {
        itemsIndexed(recommendations) { index, recommendation ->
            val focusRequester = focusRequesters[index] ?: remember { FocusRequester() }
            val isFocused = index == focusedIndex

            PosterContentCard(
                recommendation = recommendation,
                isFocused = isFocused,
                focusRequester = focusRequester,
                onFocusChange = { focused ->
                    if (focused) {
                        onFocusChange(index)
                    }
                },
                sx = sx,
                sy = sy
            )
        }
    }
}

@Composable
private fun PosterContentCard(
    recommendation: MediaRecommendation,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    sx: (Int) -> androidx.compose.ui.unit.Dp,
    sy: (Int) -> androidx.compose.ui.unit.Dp
) {
    val itemWidth = sx(220)
    val itemHeight = sy(380)

    val scale by animateFloatAsState(if (isFocused) 1.1f else 1.0f, label = "poster_scale")

    Column(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
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
                model = recommendation.posterUrl,
                contentDescription = recommendation.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (isFocused) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = recommendation.title,
                    color = Color(0xFFEEEEEE),
                    fontSize = (18 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recommendation.releaseYear,
                    color = Color(0xFFEEEEEE).copy(alpha = 0.7f),
                    fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}