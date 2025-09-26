package com.example.tv.version001

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.example.tv.ui.theme.figmaRadialBackground

@Composable
fun Version001Screen() {
    // Skalowanie względem projektu 1920x1080
    val configuration = LocalConfiguration.current
    val scaleX = configuration.screenWidthDp / 1920f
    val scaleY = configuration.screenHeightDp / 1080f
    fun sx(px: Int) = (px * scaleX).dp
    fun sy(px: Int) = (px * scaleY).dp

    // Stany nawigacji - dodajemy Szukaj przed Start
    val tabs = listOf("Szukaj", "Start", "Moje", "Telewizja", "Kino Play", "Wideo", "Aplikacje")
    var selectedTab by remember { mutableStateOf("Start") }
    var focusedTab by remember { mutableStateOf("Szukaj") } // focus na Szukaj na początku
    
    // Focus requesters dla każdej zakładki
    val focusRequesters = remember { tabs.associateWith { FocusRequester() } }
    
    // Kategorie dla lewej nawigacji pionowej
    val categories = listOf(
        "Polecane", "Nowości", "Filmy", "Seriale", "Sport", "Dzieci", "Dokumenty", "Muzyka"
    )
    var selectedCategory by remember { mutableStateOf("Polecane") }
    var focusedCategory by remember { mutableStateOf("Polecane") }
    val categoryFocusRequesters = remember { categories.associateWith { FocusRequester() } }
    
    // Stan nawigacji 2D
    var currentFocus by remember { mutableStateOf(NavigationFocus.TOP_TABS) }
    
    // Focus requesters dla horizontal rows - każdy wiersz ma 10 elementów
    // Mamy 5 kategorii, każda z 10 elementami = 50 elementów total
    val contentFocusRequesters = remember { 
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(5) { rowIndex -> // 5 wierszy
                repeat(10) { colIndex -> // 10 elementów w wierszu
                    put(Pair(rowIndex, colIndex), FocusRequester())
                }
            }
        }
    }
    var focusedRowIndex by remember { mutableStateOf(0) }
    var focusedColIndex by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .figmaRadialBackground()
            .onPreviewKeyEvent { event ->
                handleKeyNavigation(
                    event = event,
                    currentFocus = currentFocus,
                    onFocusChange = { currentFocus = it },
                    focusedTab = focusedTab,
                    focusedCategory = focusedCategory,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onTabFocusChange = { focusedTab = it },
                    onCategoryFocusChange = { focusedCategory = it },
                    onContentFocusChange = { row, col -> 
                        focusedRowIndex = row
                        focusedColIndex = col
                    },
                    focusRequesters = focusRequesters,
                    categoryFocusRequesters = categoryFocusRequesters,
                    contentFocusRequesters = contentFocusRequesters,
                    tabs = tabs,
                    categories = categories
                )
            }
            .focusable()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Nawigacja główna na górze
            MainNavigation(
                tabs = tabs,
                selectedTab = selectedTab,
                focusedTab = focusedTab,
                onTabSelected = { selectedTab = it },
                onTabFocused = { focusedTab = it },
                focusRequesters = focusRequesters,
                currentFocus = currentFocus,
                sx = { sx(it) },
                sy = { sy(it) }
            )
            
            // Główna zawartość z lewą nawigacją i content grid
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = sx(48))
            ) {
                // Lewa pionowa nawigacja (kategorie)
                LeftCategoryNavigation(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    focusedCategory = focusedCategory,
                    onCategorySelected = { selectedCategory = it },
                    onCategoryFocused = { focusedCategory = it },
                    categoryFocusRequesters = categoryFocusRequesters,
                    currentFocus = currentFocus,
                    sx = { sx(it) },
                    sy = { sy(it) }
                )
                
                Spacer(modifier = Modifier.width(sx(32)))
                
                // Główna siatka treści - pozycjonowana obok lewej nawigacji
                Box(modifier = Modifier.fillMaxSize()) {
                    AllContentRows(
                        selectedCategory = selectedCategory,
                        focusedRowIndex = focusedRowIndex,
                        focusedColIndex = focusedColIndex,
                        contentFocusRequesters = contentFocusRequesters,
                        onContentFocusChange = { row, col -> 
                            focusedRowIndex = row
                            focusedColIndex = col
                        },
                        currentFocus = currentFocus,
                        categories = categories,
                        sx = { sx(it) },
                        sy = { sy(it) }
                    )
                    
                    // Overlay z detalami gdy focus na content
                    if (currentFocus == NavigationFocus.CONTENT_GRID) {
                        ContentDetailOverlay(
                            selectedCategory = selectedCategory,
                            focusedRowIndex = focusedRowIndex,
                            focusedColIndex = focusedColIndex,
                            sx = { sx(it) },
                            sy = { sy(it) }
                        )
                    }
                }
            }
        }
    }
    
    // Auto focus na pierwszej zakładce
    LaunchedEffect(Unit) {
        focusRequesters["Szukaj"]?.requestFocus()
    }
}

// Enum dla nawigacji 2D (przeniesiony na poziom pliku)
enum class NavigationFocus { TOP_TABS, LEFT_CATEGORIES, CONTENT_GRID }

// Funkcja obsługi nawigacji 2D
fun handleKeyNavigation(
    event: KeyEvent,
    currentFocus: NavigationFocus,
    onFocusChange: (NavigationFocus) -> Unit,
    focusedTab: String,
    focusedCategory: String,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onTabFocusChange: (String) -> Unit,
    onCategoryFocusChange: (String) -> Unit,
    onContentFocusChange: (Int, Int) -> Unit,
    focusRequesters: Map<String, FocusRequester>,
    categoryFocusRequesters: Map<String, FocusRequester>,
    contentFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    tabs: List<String>,
    categories: List<String>
): Boolean {
    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return false
    
    when (event.key) {
        Key.DirectionUp -> {
            when (currentFocus) {
                NavigationFocus.LEFT_CATEGORIES -> {
                    val currentIndex = categories.indexOf(focusedCategory)
                    if (currentIndex > 0) {
                        // Przejście do poprzedniej kategorii
                        val newCategory = categories[currentIndex - 1]
                        onCategoryFocusChange(newCategory)
                        categoryFocusRequesters[newCategory]?.requestFocus()
                    } else {
                        // Jeśli jesteśmy na pierwszej kategorii, przejdź do górnej nawigacji
                        onFocusChange(NavigationFocus.TOP_TABS)
                        focusRequesters[focusedTab]?.requestFocus()
                    }
                    return true
                }
                NavigationFocus.CONTENT_GRID -> {
                    val currentCategoryIndex = categories.indexOf(focusedCategory)
                    if (currentCategoryIndex > 0) {
                        // Przejście do poprzedniego wiersza (kategorii)
                        val newCategory = categories[currentCategoryIndex - 1]
                        val newCategoryIndex = currentCategoryIndex - 1
                        onCategoryFocusChange(newCategory)
                        onContentFocusChange(newCategoryIndex, focusedColIndex)
                        contentFocusRequesters[Pair(newCategoryIndex, focusedColIndex)]?.requestFocus()
                    } else {
                        // Jeśli jesteśmy na pierwszym wierszu, przejdź do górnej nawigacji
                        onFocusChange(NavigationFocus.TOP_TABS)
                        focusRequesters[focusedTab]?.requestFocus()
                    }
                    return true
                }
                else -> return false
            }
        }
        
        Key.DirectionDown -> {
            when (currentFocus) {
                NavigationFocus.TOP_TABS -> {
                    // Z górnej nawigacji do PIERWSZEJ kategorii, nie do aktualnie wybranej
                    val firstCategory = categories.firstOrNull() ?: return false
                    onFocusChange(NavigationFocus.LEFT_CATEGORIES)
                    onCategoryFocusChange(firstCategory)
                    categoryFocusRequesters[firstCategory]?.requestFocus()
                    return true
                }
                NavigationFocus.LEFT_CATEGORIES -> {
                    // Góra/dół w kategoriach
                    val currentIndex = categories.indexOf(focusedCategory)
                    if (currentIndex < categories.size - 1) {
                        val newCategory = categories[currentIndex + 1]
                        onCategoryFocusChange(newCategory)
                        categoryFocusRequesters[newCategory]?.requestFocus()
                    }
                    return true
                }
                NavigationFocus.CONTENT_GRID -> {
                    // Dół w content rows - przejście do następnego wiersza (kategorii)
                    val currentCategoryIndex = categories.indexOf(focusedCategory)
                    if (currentCategoryIndex < categories.size - 1) {
                        val newCategory = categories[currentCategoryIndex + 1]
                        val newCategoryIndex = currentCategoryIndex + 1
                        onCategoryFocusChange(newCategory)
                        onContentFocusChange(newCategoryIndex, focusedColIndex)
                        contentFocusRequesters[Pair(newCategoryIndex, focusedColIndex)]?.requestFocus()
                    }
                    return true
                }
                else -> return false
            }
        }
        
        Key.DirectionLeft -> {
            when (currentFocus) {
                NavigationFocus.TOP_TABS -> {
                    // Lewo/prawo w górnych zakładkach
                    val currentIndex = tabs.indexOf(focusedTab)
                    if (currentIndex > 0) {
                        val newTab = tabs[currentIndex - 1]
                        onTabFocusChange(newTab)
                        focusRequesters[newTab]?.requestFocus()
                    }
                    return true
                }
                NavigationFocus.CONTENT_GRID -> {
                    // Z content rows z powrotem do kategorii lub lewo w wierszu
                    if (focusedColIndex == 0) {
                        // Powrót do kategorii - synchronizuj kategorię z wierszem
                        val targetCategory = categories.getOrNull(focusedRowIndex) ?: categories.firstOrNull() ?: return false
                        onFocusChange(NavigationFocus.LEFT_CATEGORIES)
                        onCategoryFocusChange(targetCategory)
                        categoryFocusRequesters[targetCategory]?.requestFocus()
                    } else {
                        // Lewo w wierszu - zabezpieczenie przed wyjściem poza granice
                        if (focusedColIndex > 0) {
                            val newCol = focusedColIndex - 1
                            val currentCategoryIndex = categories.indexOf(focusedCategory).coerceAtLeast(0)
                            onContentFocusChange(currentCategoryIndex, newCol)
                            contentFocusRequesters[Pair(currentCategoryIndex, newCol)]?.requestFocus()
                        }
                    }
                    return true
                }
                else -> return false
            }
        }
        
        Key.DirectionRight -> {
            when (currentFocus) {
                NavigationFocus.TOP_TABS -> {
                    // Lewo/prawo w górnych zakładkach
                    val currentIndex = tabs.indexOf(focusedTab)
                    if (currentIndex < tabs.size - 1) {
                        val newTab = tabs[currentIndex + 1]
                        onTabFocusChange(newTab)
                        focusRequesters[newTab]?.requestFocus()
                    }
                    return true
                }
                NavigationFocus.LEFT_CATEGORIES -> {
                    // Z kategorii do content rows - wiersz odpowiada kategorii
                    val categoryIndex = categories.indexOf(focusedCategory).coerceAtLeast(0)
                    onFocusChange(NavigationFocus.CONTENT_GRID)
                    onContentFocusChange(categoryIndex, 0) // focus na pierwszym elemencie wiersza kategorii
                    contentFocusRequesters[Pair(categoryIndex, 0)]?.requestFocus()
                    return true
                }
                NavigationFocus.CONTENT_GRID -> {
                    // Prawo w content rows - 10 elementów w wierszu (0-9)
                    // Zabezpieczenie przed wyjściem poza granice
                    if (focusedColIndex < 9) {
                        val newCol = focusedColIndex + 1
                        val currentCategoryIndex = categories.indexOf(focusedCategory).coerceAtLeast(0)
                        onContentFocusChange(currentCategoryIndex, newCol)
                        contentFocusRequesters[Pair(currentCategoryIndex, newCol)]?.requestFocus()
                    }
                    return true
                }
                else -> return false
            }
        }
        
        else -> return false
    }
}

@Composable
fun MainNavigation(
    tabs: List<String>,
    selectedTab: String,
    focusedTab: String,
    onTabSelected: (String) -> Unit,
    onTabFocused: (String) -> Unit,
    focusRequesters: Map<String, FocusRequester>,
    currentFocus: NavigationFocus,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sx(48), vertical = sy(32)),
        horizontalArrangement = Arrangement.spacedBy(sx(40)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar użytkownika (lewa sekcja)
        Box(
            modifier = Modifier
                .background(
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(sx(60))
                )
                .padding(sx(10))
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(sx(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(sx(80))
                        .background(
                            Color(0x33EEEEEE),
                            RoundedCornerShape(sx(64))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        color = Color(0xFFE2247C),
                        fontSize = (48 * (sx(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.96 * (sx(1).value / 1.dp.value)).sp
                    )
                }
                
                // Placeholder dla ikony (24x24)
                Box(modifier = Modifier.size(sx(24)))
            }
        }
        
        // Zakładki nawigacyjne (prawa sekcja)
        Row(
            horizontalArrangement = Arrangement.spacedBy(sx(10)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                NavigationTab(
                    text = tab,
                    isSelected = tab == selectedTab,
                    isFocused = tab == focusedTab && currentFocus == NavigationFocus.TOP_TABS,
                    onClick = { 
                        onTabSelected(tab)
                        onTabFocused(tab)
                    },
                    onFocused = { onTabFocused(tab) },
                    focusRequester = focusRequesters[tab] ?: FocusRequester(),
                    sx = sx,
                    sy = sy,
                    isSearchTab = tab == "Szukaj" // oznaczamy zakładkę wyszukiwania
                )
            }
        }
    }
}

@Composable
fun NavigationTab(
    text: String,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    isSearchTab: Boolean = false
) {
    // Określ kolor tła i tekstu na podstawie stanu
    val backgroundColor = when {
        isFocused -> Color(0xFF5AECD3) // FOCUSED - aqua
        isSelected && !isFocused -> Color(0xFFEEEEEE) // SELECTED - białe
        else -> Color.Transparent // DEFAULT - przezroczyste
    }
    
    val textColor = when {
        isFocused || isSelected -> Color(0xFF303030) // ciemny tekst
        else -> Color(0xFFEEEEEE) // biały tekst
    }

    Box(
        modifier = Modifier
            .background(
                backgroundColor,
                RoundedCornerShape(sx(30))
            )
            .padding(horizontal = sx(30), vertical = sy(10))
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center // wyśrodkowanie zawartości
    ) {
        if (isSearchTab) {
            // Dla zakładki Szukaj - pokaż tylko ikonę lupki z taką samą wysokością jak tekst
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Szukaj",
                tint = textColor,
                modifier = Modifier.size((28 * (sy(1).value / 1.dp.value)).dp) // identyczny rozmiar jak font
            )
        } else {
            // Dla pozostałych zakładek - pokaż tekst
            Text(
                text = text,
                color = textColor,
                fontSize = (28 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (0.20 * (sy(1).value / 1.dp.value)).sp
            )
        }
    }
}


@Composable
fun CategoryIcon(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Rozmiary z Figma
    val containerWidth = sx(240)
    val containerHeight = sy(216)
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
    
    Box(
        modifier = Modifier
            .width(containerWidth) // 240 z Figma
            .height(containerHeight) // 216 z Figma
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = (6 * sx(1).value / 1.dp.value).dp, // border width: 6 z Figma
                        color = borderColor,
                        shape = RoundedCornerShape(sx(4))
                    )
                } else {
                    Modifier // brak obramowania gdy nie focused
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        // Pozycjonowanie jak w Figma - left: 72, top: 40
        Box(
            modifier = Modifier
                .offset(x = sx(0), y = sy(-8)) // Wyśrodkowanie w kontenerze
                .width(sx(200))
                .height(sy(144)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sy(16)), // spacing: 16 z Figma
                modifier = Modifier.fillMaxSize()
            ) {
                // Placeholder dla ikony 96x96
                Box(
                    modifier = Modifier
                        .size(sx(96)) // 96x96 z Figma
                        .background(
                            Color(0x33EEEEEE), // półprzezroczyste tło dla ikony
                            RoundedCornerShape(sx(8))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Placeholder - tutaj można dodać konkretne ikony dla kategorii
                    Text(
                        text = text.take(2).uppercase(), // pierwsze 2 litery jako placeholder
                        color = Color(0xFFEEEEEE),
                        fontSize = (24 * (sx(1).value / 1.dp.value)).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Tekst kategorii
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp, // fontSize: 24 z Figma
                    fontWeight = FontWeight.Medium, // fontWeight: 500 z Figma
                    letterSpacing = (0.48 * (sy(1).value / 1.dp.value)).sp, // letterSpacing: 0.48 z Figma
                    lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp, // lineHeight: 1.33 z Figma
                    modifier = Modifier.widthIn(max = sx(200)) // maxWidth: 200 z Figma
                )
            }
        }
    }
}

@Composable 
fun LeftCategoryNavigation(
    categories: List<String>,
    selectedCategory: String,
    focusedCategory: String,
    onCategorySelected: (String) -> Unit,
    onCategoryFocused: (String) -> Unit,
    categoryFocusRequesters: Map<String, FocusRequester>,
    currentFocus: NavigationFocus,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    LazyColumn(
        modifier = Modifier.width(sx(240)), // szerokość kontenera z Figma
        verticalArrangement = Arrangement.spacedBy(sy(20)) // spacing między elementami
    ) {
        items(categories) { category ->
            CategoryIcon(
                text = category,
                isFocused = category == focusedCategory && currentFocus == NavigationFocus.LEFT_CATEGORIES,
                onClick = { 
                    onCategorySelected(category)
                    onCategoryFocused(category)
                },
                onFocused = { onCategoryFocused(category) },
                focusRequester = categoryFocusRequesters[category] ?: FocusRequester(),
                sx = sx,
                sy = sy
            )
        }
    }
}

@Composable
fun AllContentRows(
    selectedCategory: String,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    contentFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onContentFocusChange: (Int, Int) -> Unit,
    currentFocus: NavigationFocus,
    categories: List<String>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {    
    // Pozycjonowanie na podstawie Figma - spacing: 80 od CategoryIcon
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = sx(80)) // spacing: 80 z Figma
    ) {
        // LazyColumn z wierszami dla wszystkich kategorii
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(sy(20)) // spacing między wierszami kategorii
        ) {
            items(categories.size) { categoryIndex ->
                // Każda kategoria ma swój własny wiersz treści
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sy(228)) // wysokość jak CategoryIcon + padding
                ) {
                    Box(
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        ContentRow(
                            category = categories[categoryIndex],
                            rowIndex = categoryIndex,
                            focusedRowIndex = focusedRowIndex,
                            focusedColIndex = focusedColIndex,
                            contentFocusRequesters = contentFocusRequesters,
                            onContentFocusChange = onContentFocusChange,
                            currentFocus = currentFocus,
                            sx = sx,
                            sy = sy
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContentRow(
    category: String,
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    contentFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    onContentFocusChange: (Int, Int) -> Unit,
    currentFocus: NavigationFocus,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = sx(16)),
        horizontalArrangement = Arrangement.spacedBy(sx(20)) // spacing: 20 z Figma
    ) {
        items(10) { colIndex -> // 10 elementów w każdym wierszu
            ContentCard(
                title = "Lato w dolinie Muminków", // tytuł z Figma
                category = category,
                channelNumber = String.format("%03d", (rowIndex * 10 + colIndex + 1)), // numer kanału oparty na pozycji
                index = colIndex, // dla zgodności z interfejsem
                isFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex && currentFocus == NavigationFocus.CONTENT_GRID,
                focusRequester = contentFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester(),
                onFocusChange = { onContentFocusChange(rowIndex, colIndex) },
                sx = sx,
                sy = sy
            )
        }
    }
}

@Composable
fun ContentCard(
    title: String,
    category: String,
    channelNumber: String,
    index: Int,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Rozmiary z Figma - zachowaj aspect ratio obrazków
    val itemWidth = sx(368) // szerokość z Figma
    val itemHeight = sy(208) // wysokość z Figma - taka sama jak CategoryIcon
    
    Box(
        modifier = Modifier
            .width(itemWidth)
            .height(itemHeight)
            .clip(RoundedCornerShape(sx(12))) // borderRadius: 12 z Figma
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = (6 * sx(1).value / 1.dp.value).dp,
                        color = Color(0xFF5AECD3),
                        shape = RoundedCornerShape(sx(12))
                    )
                } else {
                    Modifier
                }
            )
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onFocusChange()
                }
            }
            .focusable()
    ) {
        // Główne tło obrazek
        AsyncImage(
            model = "https://placehold.co/368x208", // placeholder z Figma
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Gradient overlay na dole
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(118)) // wysokość gradientu z Figma
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Black.copy(alpha = 0.80f),
                            Color.Transparent
                        ),
                        startY = Float.POSITIVE_INFINITY,
                        endY = 0f
                    ),
                    RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )
        
        // Kolejny gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sy(132)) // wysokość z Figma
                .align(Alignment.BottomCenter)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.80f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(
                        bottomStart = sx(12),
                        bottomEnd = sx(12)
                    )
                )
        )
        
        // Numer kanału w prawym górnym rogu
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = sx(12))
                .height(sy(40))
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = sx(64))
                    .height(sy(40))
                    .background(
                        Color.Transparent,
                        RoundedCornerShape(sx(4))
                    )
                    .border(
                        width = sx(2),
                        color = Color(0x66EEEEEE), // 40% opacity
                        shape = RoundedCornerShape(sx(4))
                    )
                    .padding(horizontal = sx(12), vertical = sy(8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channelNumber,
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (0.48 * (sy(1).value / 1.dp.value)).sp,
                    lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp
                )
            }
        }
        
        // Tytuł programu w lewym dolnym rogu
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(12), bottom = sy(100))
                .width(sx(250))
        ) {
            Text(
                text = title,
                color = Color(0xFFEEEEEE),
                fontSize = (20 * (sy(1).value / 1.dp.value)).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (0.40 * (sy(1).value / 1.dp.value)).sp,
                lineHeight = (20 * 1.40f * (sy(1).value / 1.dp.value)).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Logo kanału w lewym dolnym rogu
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = sx(8), bottom = sy(20))
                .size(sx(80))
                .clip(RoundedCornerShape(sx(4)))
        ) {
            AsyncImage(
                model = "https://placehold.co/80x80",
                contentDescription = "Channel logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Kategoria tag w lewym górnym rogu
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = sx(12), top = sy(20))
        ) {
            Box(
                modifier = Modifier
                    .height(sy(32))
                    .background(
                        Color(0xFFEEEEEE),
                        RoundedCornerShape(sx(8))
                    )
                    .padding(horizontal = sx(8), vertical = sy(4))
            ) {
                Text(
                    text = category.uppercase(),
                    color = Color(0xFF48227C),
                    fontSize = (16 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (0.32 * (sy(1).value / 1.dp.value)).sp,
                    lineHeight = (16 * 1.50f * (sy(1).value / 1.dp.value)).sp
                )
            }
        }
    }
}

@Composable
fun ContentDetailOverlay(
    selectedCategory: String,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // Animacja pojawiania się overlay
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "overlay_alpha_animation"
    )
    
    // Pozycja overlay - lewy dolny róg jak na ilustracji 2.png
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
    ) {
        // Power Sisters content - jak na ilustracji
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(sx(24))
                .background(
                    Color(0x99000000), // półprzezroczysty czarny
                    RoundedCornerShape(sx(12))
                )
                .padding(sx(24))
                .width(sx(400))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(sy(16))
            ) {
                Text(
                    text = "Power Sisters",
                    color = Color(0xFFEEEEEE),
                    fontSize = (24 * (sy(1).value / 1.dp.value)).sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "A dynamic duo of superhero siblings join forces to save their city from a sinister villain, rediscovering sisterhood in action.",
                    color = Color(0xCCEEEEEE),
                    fontSize = (14 * (sy(1).value / 1.dp.value)).sp,
                    lineHeight = (18 * (sy(1).value / 1.dp.value)).sp
                )
                
                // Dodatkowe informacje
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sx(16)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Action/Superhero",
                        color = Color(0xCCEEEEEE),
                        fontSize = (12 * (sy(1).value / 1.dp.value)).sp
                    )
                    
                    Text(
                        text = "•",
                        color = Color(0xCCEEEEEE),
                        fontSize = (12 * (sy(1).value / 1.dp.value)).sp
                    )
                    
                    Text(
                        text = "2022",
                        color = Color(0xCCEEEEE),
                        fontSize = (12 * (sy(1).value / 1.dp.value)).sp
                    )
                    
                    Text(
                        text = "•",
                        color = Color(0xCCEEEEEE),
                        fontSize = (12 * (sy(1).value / 1.dp.value)).sp
                    )
                    
                    Text(
                        text = "2h 15m",
                        color = Color(0xCCEEEEEE),
                        fontSize = (12 * (sy(1).value / 1.dp.value)).sp
                    )
                }
            }
        }
    }
}