# RAPORT: Analiza Implementacji Kanału "app-icons"
## TELEWIZJA vs MOJE - Wszystkie Różnice

**Data**: 2025-11-18  
**Analiza**: Implementacja каналу typu "app-icons" w sekcji TELEWIZJA ("Moja lista kanałów", "Wszystkie kanały", "Dla dzieci") vs MOJE ("Moja lista kanałów")

---

## 1. IMPLEMENTACJA TELEWIZJA (WZORZEC - JAK POWINNO BYĆ)

### 1.1 Rendering app-icons w TelewizjaUnifiedChannelRow
**Plik**: `TopMenuScreen2.kt` (linie 8310-8388)

```kotlin
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
```

### 1.2 ChannelListCard - Rendering pojedynczego kanału
**Plik**: `TopMenuScreen2.kt` (linie 551-637)

```kotlin
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
            .background(Color(0xFF000000).copy(alpha = if (isFocused) 0.3f else 0.1f))  // Black with 0.1/0.3 opacity
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
            modifier = Modifier.size(sx(TELEWIZJA_CHANNEL_LIST_CARD_LOGO_SIZE), sy(TELEWIZJA_CHANNEL_LIST_CARD_LOGO_SIZE)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Channel number label (54px height, 12px padding horizontal)
        Box(
            modifier = Modifier
                .height(sy(54))
                .widthIn(min = sx(52))
                .clip(RoundedCornerShape(sx(4)))
                .border(
                    width = sx(2),
                    color = Color(0xFFEEEEEE).copy(alpha = 0.4f),
                    shape = RoundedCornerShape(sx(4))
                )
                .padding(horizontal = sx(12)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (channel.channelNumber ?: 0).toString().padStart(2, '0'),
                fontSize = (24 * sy(1).value / 1).sp,
                fontWeight = FontWeight.Medium,
                lineHeight = (28 * sy(1).value / 1).sp,
                letterSpacing = 0.48.sp,
                color = Color(0xFFEEEEEE)
            )
        }
    }
}
```

### 1.3 CategoryIcon - WIDEO Style (Text-Only)
**Plik**: `Version001Screen.kt` (linie 603-753)

```kotlin
fun CategoryIcon(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    logoUrl: String? = null,
    logoDrawableId: Int? = null,
    showIcon: Boolean = true,           // ✅ KLUCZOWY PARAMETR: false dla app-icons
    showBackgroundWhenFocused: Boolean = false,  // ✅ KLUCZOWY: true dla EPG channels
    isExpanded: Boolean = false,
    showChevron: Boolean = false,
    onChevronClick: (() -> Unit)? = null
) {
    val containerWidth = sx(240)
    val containerHeight = sy(216)
    val borderColor = if (isFocused) Color(0xFF5AECD3) else Color.Transparent

    Box(
        modifier = Modifier
            .width(containerWidth)
            .height(containerHeight)
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = (6 * sx(1).value / 1.dp.value).dp,
                        color = borderColor,
                        shape = RoundedCornerShape(sx(4))
                    )
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(sx(4)))
            .then(
                if (showBackgroundWhenFocused) {
                    if (isFocused) {
                        Modifier.background(Color(0x4D000000)) // rgba(0, 0, 0, 0.30) - focused
                    } else {
                        Modifier.background(Color(0x1A000000)) // rgba(0, 0, 0, 0.10) - unfocused
                    }
                } else {
                    Modifier
                }
            )
            .focusRequester(focusRequester)
            .clickable { onClick() }
            .onFocusChanged { focusState ->
                onFocused(focusState.isFocused)
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset(x = sx(0), y = sy(-8))
                .width(sx(200))
                .height(sy(144)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // ✅ Spacing między ikoną i tekstem (16px jeśli showIcon=true, centerVertical jeśli false)
                verticalArrangement = if (showIcon) Arrangement.spacedBy(sy(16)) else Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // ✅ Ikona 96x96 - pokazywana TYLKO jeśli showIcon=true
                if (showIcon) {
                    Box(
                        modifier = Modifier
                            .size(sx(96))
                            .clip(RoundedCornerShape(sx(8))),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            logoDrawableId != null -> {
                                Icon(
                                    painter = painterResource(id = logoDrawableId),
                                    contentDescription = text,
                                    modifier = Modifier.size(sx(68)),
                                    tint = Color(0xFFEEEEEE)
                                )
                            }
                            else -> {
                                Text(
                                    text = text.take(2).uppercase(),
                                    color = Color(0xFFEEEEEE),
                                    fontSize = (24 * (sx(1).value / 1.dp.value)).sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ✅ Tekst kategorii
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = sx(200))
                ) {
                    Text(
                        text = text,
                        textAlign = TextAlign.Center,
                        color = Color(0xFFEEEEEE),
                        fontSize = (24 * (sy(1).value / 1.dp.value)).sp,  // 24sp
                        fontWeight = FontWeight.Medium,  // 500
                        letterSpacing = (0.48 * (sy(1).value / 1.dp.value)).sp,  // 0.48sp = 2% of 24px
                        lineHeight = (24 * 1.33f * (sy(1).value / 1.dp.value)).sp,  // 1.33 = 32px / 24px
                        maxLines = 2,  // ✅ KLUCZOWY: Umożliwia wrapping EPG channel names
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
```

### 1.4 Focus Management - Direct Model
**Kod**: Linia 8352-8355

```kotlin
val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
```

**Charakterystyka TELEWIZJA**:
- ✅ **DIRECT FOCUS** - `colIndex` zawsze odpowiada indeksowi w `tvChannelLogos`
- ✅ **Każdy item ma własny FocusRequester** - brak scroll-window logic
- ✅ **Brak conditional assignment** - FocusRequester zawsze przypisywany
- ✅ **Działa z dowolnym LazyList index** - focus nie zależy od `firstVisibleItemIndex`

### 1.5 Animacja Slide-Down dla Miniaturek
**Kod**: Linie 8137-8143

```kotlin
// Miniatures Y offset animation (for top10, vertical, horizontal - NOT for slider-max, app-icons, shortcuts-v2)
val isMiniaturesOnScreen = isCurrentRow && focusedColIndex >= 0
val miniaturesYOffset by animateDpAsState(
    targetValue = if (!isShortcutsV2 && channelType in listOf("top10", "vertical", "horizontal") && isMiniaturesOnScreen) sy(290) else sy(0),
    animationSpec = tween(durationMillis = 350, easing = androidx.compose.animation.core.EaseInOutCubic),
    label = "telewizja_miniatures_y_offset_$rowIndex"
)
```

**WAŻNE**: App-icons **NIE ma animacji slide-down** miniaturek (bo nie ma miniaturek)

### 1.6 Wymiary i Spacing
**Konstante**: Linie 5654-5656

```kotlin
private const val TELEWIZJA_CHANNEL_LIST_CARD_WIDTH = 208      // Figma: card width
private const val TELEWIZJA_CHANNEL_LIST_CARD_HEIGHT = 208     // Figma: card height (square)
private const val TELEWIZJA_CHANNEL_LIST_CARD_LOGO_SIZE = 148  // Figma: logo size (148x148)
```

**Spacing**:
- **contentPadding** `start = sx(380)` - Miejsce dla CategoryIcon (240px) + 140px marginesu lewego
- **horizontalArrangement** `Arrangement.spacedBy(sx(12))` - 12px między kartami (jak APLIKACJE)
- **Card size** 208x208px - square format
- **Logo size** 148x148px - centered w karcie
- **Border** 4px (focused), 0px (unfocused)
- **Background** `rgba(0,0,0,0.1)` (unfocused), `rgba(0,0,0,0.3)` (focused)

**CategoryIcon Positioning**:
- **X offset** 80px (start of LazyRow contentPadding)
- **Y offset** 0px (inline with cards)
- **Size** 240x216px (standard)

---

## 2. IMPLEMENTACJA MOJE (BŁĘDY - JAK JEST TERAZ)

### 2.1 Rendering app-icons w MojeUnifiedChannelRow
**Plik**: `TopMenuScreen2.kt` (linie 5313-5351)

```kotlin
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
}
```

### 2.2 BŁĄD #1: Brak Global CategoryIcon Renderowania
**Lokalizacja**: Linia 5517-5564 (CategoryIcon section)

```kotlin
// CategoryIcon (hidden for "Skróty", Headers, and "Skróty v2 Moje")
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
    Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
        // Faza 4: Standard focus check
        val categoryIsFocused = rowIndex == focusedRowIndex && focusedColIndex == -1
        val categoryFocusRequester = channelFocusRequesters[Pair(rowIndex, -1)]
        
        // Icon mapping for different channels (PNG)
        val logoDrawableId = when (channel) {
            "Oglądaj dalej" -> R.drawable.ic_keep_watching
            "Moje nagrania" -> R.drawable.ic_records
            "Nagrania" -> R.drawable.ic_records
            "Do obejrzenia" -> R.drawable.ic_add_to_watch
            "Aktywne pakiety" -> R.drawable.ic_packages
            "Wypożyczone" -> R.drawable.ic_rented
            else -> null  // ❌ BRAK MAPPINGU DLA "Moja lista kanałów"
        }
        
        CategoryIcon(
            text = channel,
            isFocused = categoryIsFocused,
            onClick = {
                // Empty - expansion for "Moje nagrania" is handled by chevron click
            },
            // ... reszta parametrów
        )
    }
}
```

**PROBLEM**: Dla `channel == "Moja lista kanałów"`:
- `logoDrawableId = null` (brak mappingu w `when`)
- CategoryIcon BĘDZIE renderowana, ale:
  - ❌ Brak możliwości ustawienia `showIcon = false`
  - ❌ Będzie pokazywać placeholder "MO" zamiast tekstu
  - ❌ Brak `showBackgroundWhenFocused = true`

### 2.3 BŁĄD #2: CategoryIcon nie ma WIDEO Style
**Brakuje parametrów w CategoryIcon() call** (linie ~5542-5563):

```kotlin
CategoryIcon(
    text = channel,
    isFocused = categoryIsFocused,
    onClick = { },
    onFocused = { isFocused -> if (isFocused) { Log.d("MOJE_DEBUG", ...) } },
    focusRequester = categoryFocusRequester ?: FocusRequester(),
    sx = sx,
    sy = sy,
    logoDrawableId = if (!isSubChannel) logoDrawableId else null,
    showIcon = !isSubChannel,
    showBackgroundWhenFocused = isSubChannel,
    // ❌ BRAKUJE DLA APP-ICONS:
    // - showIcon = false       (powinno być true dla app-icons)
    // - showBackgroundWhenFocused = true  (powinno być dla app-icons)
    isExpanded = (channel == "Moje nagrania") && isNagraniaExpanded,
    showChevron = (channel == "Moje nagrania"),
    onChevronClick = if (channel == "Moje nagrania") { { toggleNagraniaExpansion?.invoke() } } else null
)
```

**PROBLEM**:
- `showIcon` zależy od `isSubChannel` (sub-channels z NAGRANIA)
- Dla `"Moja lista kanałów"` (app-icons):
  - `isSubChannel = false` (bo nie w liście sub-channels)
  - Dlatego `showIcon = true` (powinno być FALSE!)
  - Brak tła `showBackgroundWhenFocused = false` (powinno być TRUE!)

---

## 3. PORÓWNANIE - WSZYSTKIE RÓŻNICE

### Tabela Porównawcza

| Aspekt | TELEWIZJA (✅ PRAWIDŁOWO) | MOJE (❌ BŁĄD) |
|--------|--------------------------|--------------|
| **1. CategoryIcon Rendering** | Global section (linia 6189) | MojeUnifiedChannelRow (linia 5518) |
| **2. showIcon parametr** | ❌ Brak (renderowana w LazyRow bez CategoryIcon) | ⚠️ `showIcon = !isSubChannel` (zwraca true dla app-icons) |
| **3. showBackgroundWhenFocused** | ❌ Brak | ⚠️ `showBackgroundWhenFocused = isSubChannel` (zwraca false dla app-icons) |
| **4. logoDrawableId mapping** | Brak (nie potrzebna dla app-icons) | ❌ Brak dla "Moja lista kanałów" |
| **5. Focus Model** | ✅ Direct (colIndex = actual index) | ✅ Direct (colIndex = actual index) |
| **6. FocusRequester Assignment** | ✅ Zawsze przypisywany | ✅ Zawsze przypisywany |
| **7. Card Component** | ✅ ChannelListCard (208x208) | ✅ ChannelListCard (208x208) |
| **8. Card Spacing** | ✅ 12px (Arrangement.spacedBy) | ✅ 12px (Arrangement.spacedBy) |
| **9. contentPadding start** | ✅ 380px (dla ChannelListCard) | ✅ 380px (dla ChannelListCard) |
| **10. Empty Placeholder** | ✅ Box z "Brak kanałów" (linia 8318) | ❌ Brak (LazyRow zawsze renderowana) |
| **11. Slide-Down Animation** | ✅ Brak (app-icons nie ma miniaturek) | ✅ Brak |
| **12. LazyRow Spacers** | ✅ 8 items (208x208) | ✅ 8 items (208x208) |

---

## 4. LISTA ZMIAN DO IMPLEMENTACJI W MOJE

### Zmiana #1: Dodać "Moja lista kanałów" do `isSubChannel`
**Plik**: `TopMenuScreen2.kt`, linia 5536

**ZMIEŃ Z**:
```kotlin
val isSubChannel = channel in listOf("Skróty", "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE")
```

**ZMIEŃ NA**:
```kotlin
val isSubChannel = channel in listOf(
    "Skróty", "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE",
    "Moja lista kanałów"  // ✅ Dodane: potrzebuje WIDEO style
)
```

**Uzasadnienie**: To automatycznie ustawia:
- `showIcon = false` (tekst-only, nie ikona)
- `showBackgroundWhenFocused = true` (czarne tło przy focus)

### Zmiana #2: Dodać logoDrawableId mapping dla app-icons
**Plik**: `TopMenuScreen2.kt`, linia 5525-5532

**ZMIEŃ Z**:
```kotlin
val logoDrawableId = when (channel) {
    "Oglądaj dalej" -> R.drawable.ic_keep_watching
    "Moje nagrania" -> R.drawable.ic_records
    "Nagrania" -> R.drawable.ic_records
    "Do obejrzenia" -> R.drawable.ic_add_to_watch
    "Aktywne pakiety" -> R.drawable.ic_packages
    "Wypożyczone" -> R.drawable.ic_rented
    else -> null
}
```

**ZMIEŃ NA**:
```kotlin
val logoDrawableId = when (channel) {
    "Oglądaj dalej" -> R.drawable.ic_keep_watching
    "Moje nagrania" -> R.drawable.ic_records
    "Nagrania" -> R.drawable.ic_records
    "Do obejrzenia" -> R.drawable.ic_add_to_watch
    "Aktywne pakiety" -> R.drawable.ic_packages
    "Wypożyczone" -> R.drawable.ic_rented
    "Moja lista kanałów" -> null  // ✅ Dodane: app-icons bez ikony
    else -> null
}
```

**Uzasadnienie**: Jawnie wskazuje, że "Moja lista kanałów" nie ma ikony (tylko tekst).

### Zmiana #3: Walidacja CategoryIcon rendering dla app-icons
**Plik**: `TopMenuScreen2.kt`, linia 5518

**ZMIEŃ Z**:
```kotlin
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
```

**ZMIEŃ NA**:
```kotlin
if (!channel.startsWith("[HEADER") && channel != "Skróty" && channel != "Skróty v2 Moje") {
    // ✅ WAŻNE: CategoryIcon powinien być renderowany dla "Moja lista kanałów"
    // Używa WIDEO style (text-only, background on focus)
```

**Uzasadnienie**: Komentarz wyjaśniający logikę dla przyszłych zmian.

### Zmiana #4 (OPCJONALNA): Dodać empty placeholder dla app-icons
**Plik**: `TopMenuScreen2.kt`, linia 5313

**DODAJ**:
```kotlin
} else if (isAppIcons) {
    // App Icons row - TV channels from "Moja lista kanałów"
    if (tvChannels.isEmpty()) {
        // ✅ Empty content placeholder (optional, jak TELEWIZJA)
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
            // ... reszta kodu
        )
    }
}
```

---

## 5. PODSUMOWANIE ZMIAN

### Obowiązkowe:
1. ✅ **Zmiana #1**: Dodać "Moja lista kanałów" do `isSubChannel`
2. ✅ **Zmiana #2**: Dodać mapping dla "Moja lista kanałów" w `logoDrawableId`
3. ✅ **Zmiana #3**: Dodać komentarz wyjaśniający

### Opcjonalne:
4. ⚠️ **Zmiana #4**: Dodać empty placeholder (dla feature parity z TELEWIZJA)

---

## 6. VERIFY CHECKLIST

Po implementacji zmian:

- [ ] **CategoryIcon rendering**: CategoryIcon dla "Moja lista kanałów" renderuje się z tekstem bez ikony
- [ ] **Focus**: CategoryIcon można fokusować (focusedColIndex == -1)
- [ ] **Background**: Czarne tło pojawia się przy focus (rgba(0,0,0,0.3))
- [ ] **Border**: Aqua border (#5AECD3) pojawia się przy focus
- [ ] **Navigation**: LEFT/RIGHT między kartami
- [ ] **Navigation**: UP/DOWN pomiędzy wierszami
- [ ] **Empty state**: Placeholder pokazuje się gdy tvChannels.isEmpty() (opcjonalne)
- [ ] **Consistency**: Wygląd identyczny z TELEWIZJA app-icons

---

## 7. APPENDIX: Kluczowe Liczby

### Wymiary ChannelListCard:
- **Width**: 208px
- **Height**: 208px
- **Logo Size**: 148x148px
- **Logo Padding**: centered (30px z każdej strony)
- **Number Label Height**: 54px
- **Number Font**: 24sp

### Spacing:
- **Between Cards**: 12px
- **ContentPadding Start**: 380px (240px CategoryIcon + 140px margin)
- **ContentPadding End**: 20px
- **Spacer Count**: 8 items

### Colors:
- **Background Unfocused**: rgba(0, 0, 0, 0.1)
- **Background Focused**: rgba(0, 0, 0, 0.3)
- **Border Focused**: #5AECD3 (aqua, 4px)
- **Border Unfocused**: transparent (0px)
- **Text**: #EEEEEE (white)

### CategoryIcon (WIDEO Style):
- **Width**: 240px
- **Height**: 216px
- **showIcon**: false
- **showBackgroundWhenFocused**: true
- **Border**: 6px aqua (quando focused)
- **Text**: max 2 lines
- **Font**: 24sp Medium, lineHeight 1.33

---

**Koniec Raportu**
