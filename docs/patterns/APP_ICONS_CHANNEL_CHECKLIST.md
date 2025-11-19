# APP-ICONS Channel Type - Complete Implementation Checklist

**Status**: Production-ready pattern
**Last Updated**: 2025-11-19
**Reference Implementation**: TELEWIZJA section (TopMenuScreen2.kt:8310-8395)

---

## Overview

Typ `app-icons` wyświetla listę kanałów TV z kartami ChannelListCard (208x208px) w poziomym rzędzie. Charakteryzuje się:
- **Brak rozszerzania** (stała wysokość 256px)
- **Direct focus model** (fokus przeskakuje bezpośrednio na element)
- **CategoryIcon fade** (znika gdy lista przewinięta, pojawia się tytuł)

---

## Lesson Learned: Błędy z sesji MOJE (2025-11-19)

### Iteracja 1: Zły komponent i focus model
- **Błąd**: Użyto `TvAppIconCard` (300x170px, scrolling window) zamiast `ChannelListCard` (208x208px, direct)
- **Przyczyna**: Brak sprawdzenia wzorca TELEWIZJA przed implementacją

### Iteracja 2: CategoryIcon pokazuje "MO" placeholder
- **Błąd**: Nie dodano kanału do listy `isSubChannel`
- **Przyczyna**: isSubChannel kontroluje `showIcon=false` dla text-only CategoryIcon

### Iteracja 3: Channel się rozszerza, brak fade/tytułu
- **Błąd**: Brak stałych `APP_ICONS_NORMAL/EXPANDED_ROW_HEIGHT` w calculateYPosition
- **Przyczyna**: Bez tych stałych używane są heights dla horizontal (256→546px expansion)

---

## Template Prompta

Użyj tego prompta aby dodać kanał app-icons za pierwszym razem:

```
"Dodaj kanał '[NAZWA_KANAŁU]' typu app-icons do sekcji [SECTION].

PRZED IMPLEMENTACJĄ:
1. Przeczytaj: docs/patterns/APP_ICONS_CHANNEL_CHECKLIST.md
2. Wzoruj się na: TELEWIZJA app-icons (TopMenuScreen2.kt:8310-8395)

WYMAGANIA TECHNICZNE:
- Component: ChannelListCard (208x208px), NIE TvAppIconCard
- Focus: Direct model, NIE scrolling window
- Spacing: 12px między kartami
- Heights: NORMAL = EXPANDED = 256px (NO expansion)
- CategoryIcon: isSubChannel=true (text-only, bg on focus)
- Fade: CategoryIcon alpha=0 gdy lista przewinięta
- Title: Tytuł nad listą gdy firstVisibleItemIndex > 0

KROKI:
1. Dodaj stałe SECTION_APP_ICONS_NORMAL/EXPANDED_ROW_HEIGHT
2. Zaktualizuj calculateSectionChannelYPosition (wszystkie when bloki)
3. Dodaj rendering w UnifiedChannelRow (ChannelListCard + LazyRow)
4. Dodaj do isSubChannel dla text-only CategoryIcon
5. Dodaj CategoryIcon fade (alpha/zIndex)
6. Dodaj tytuł nad listą
"
```

---

## Complete Implementation Checklist

### Plik do modyfikacji
- [ ] `TopMenuScreen2.kt`

---

### Krok 1: Stałe Spacing (sekcja constans, ~linia 5590)

Dodaj stałe dla nowej sekcji:

```kotlin
// App-icons ([NAZWA_KANAŁU]) - NO expansion like TELEWIZJA
private const val [SECTION]_APP_ICONS_NORMAL_ROW_HEIGHT = 256  // CategoryIcon (216px) + spacing (40px)
private const val [SECTION]_APP_ICONS_EXPANDED_ROW_HEIGHT = 256 // NO expansion - always 256px
```

**WAŻNE**: Normal MUSI równać się Expanded (256=256) - to zapobiega rozszerzaniu!

---

### Krok 2: calculateSectionChannelYPosition

Zaktualizuj funkcję `calculate[SECTION]ChannelYPosition`:

#### 2.1 Dodaj isAppIcons check (po isVertical)
```kotlin
val isAppIcons = channelName == "[NAZWA_KANAŁU]"
```

#### 2.2 Zaktualizuj normalRowHeight when block
```kotlin
val normalRowHeight = when {
    isHeader -> 80
    isShortcuts -> [SECTION]_SHORTCUTS_NORMAL_ROW_HEIGHT
    isVertical -> [SECTION]_VERTICAL_NORMAL_ROW_HEIGHT
    isAppIcons -> [SECTION]_APP_ICONS_NORMAL_ROW_HEIGHT   // <-- DODAJ
    else -> [SECTION]_HORIZONTAL_NORMAL_ROW_HEIGHT
}
```

#### 2.3 Zaktualizuj expandedRowHeight when block
```kotlin
val expandedRowHeight = when {
    isHeader -> 80
    isShortcuts -> [SECTION]_SHORTCUTS_EXPANDED_ROW_HEIGHT
    isVertical -> [SECTION]_VERTICAL_EXPANDED_ROW_HEIGHT
    isAppIcons -> [SECTION]_APP_ICONS_EXPANDED_ROW_HEIGHT  // <-- DODAJ
    else -> [SECTION]_HORIZONTAL_EXPANDED_ROW_HEIGHT
}
```

#### 2.4 Zaktualizuj pierwszą pętlę (rowIndex < focusedRowIndex)
```kotlin
val betweenIsAppIcons = betweenChannelName == "[NAZWA_KANAŁU]"
val betweenRowHeight = when {
    betweenIsHeader -> 80
    betweenIsShortcuts -> [SECTION]_SHORTCUTS_NORMAL_ROW_HEIGHT
    betweenIsVertical -> [SECTION]_VERTICAL_NORMAL_ROW_HEIGHT
    betweenIsAppIcons -> [SECTION]_APP_ICONS_NORMAL_ROW_HEIGHT  // <-- DODAJ
    else -> [SECTION]_HORIZONTAL_NORMAL_ROW_HEIGHT
}
```

#### 2.5 Zaktualizuj focusedChannelExpansion (rowIndex > focusedRowIndex)
```kotlin
val focusedIsAppIcons = focusedChannelName == "[NAZWA_KANAŁU]"

val focusedChannelExpansion = if (focusedColIndex >= 0) {
    when {
        focusedIsShortcuts -> [SECTION]_SHORTCUTS_EXPANDED_ROW_HEIGHT
        focusedIsVertical -> [SECTION]_VERTICAL_EXPANDED_ROW_HEIGHT
        focusedIsAppIcons -> [SECTION]_APP_ICONS_EXPANDED_ROW_HEIGHT  // <-- DODAJ
        else -> [SECTION]_HORIZONTAL_EXPANDED_ROW_HEIGHT
    }
} else {
    when {
        focusedIsShortcuts -> [SECTION]_SHORTCUTS_NORMAL_ROW_HEIGHT
        focusedIsVertical -> [SECTION]_VERTICAL_NORMAL_ROW_HEIGHT
        focusedIsAppIcons -> [SECTION]_APP_ICONS_NORMAL_ROW_HEIGHT    // <-- DODAJ
        else -> [SECTION]_HORIZONTAL_NORMAL_ROW_HEIGHT
    }
}
```

#### 2.6 Zaktualizuj drugą pętlę (w rowIndex > focusedRowIndex)
```kotlin
val betweenIsAppIcons = betweenChannelName == "[NAZWA_KANAŁU]"
val betweenRowHeight = when {
    betweenIsHeader -> 80
    betweenIsShortcuts -> [SECTION]_SHORTCUTS_NORMAL_ROW_HEIGHT
    betweenIsVertical -> [SECTION]_VERTICAL_NORMAL_ROW_HEIGHT
    betweenIsAppIcons -> [SECTION]_APP_ICONS_NORMAL_ROW_HEIGHT  // <-- DODAJ
    else -> [SECTION]_HORIZONTAL_NORMAL_ROW_HEIGHT
}
```

---

### Krok 3: UnifiedChannelRow - Rendering app-icons

W funkcji `[Section]UnifiedChannelRow`, dodaj branch dla app-icons:

```kotlin
} else if (isAppIcons) {
    // App Icons row - TV channels
    // IDENTICAL to TELEWIZJA implementation (ChannelListCard, direct focus, 12px spacing)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = lazyListState,
        contentPadding = PaddingValues(start = sx(380), end = sx(20)),
        horizontalArrangement = Arrangement.spacedBy(sx(12))  // 12px spacing!
    ) {
        items(tvChannels.size) { colIndex ->
            val channel = tvChannels[colIndex]
            // Direct focus model (NOT scrolling window!)
            val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
            val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()

            ChannelListCard(  // NOT TvAppIconCard!
                channel = channel,
                isFocused = isItemFocused,
                focusRequester = focusRequester,
                onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
                onClick = {
                    val epgId = channel.epgId ?: channel.name
                    onNavigateToEpgDay("[NAZWA_KANAŁU]", epgId, 0, "[SECTION]")
                },
                sx = sx,
                sy = sy
            )
        }

        // Spacer items (same size as card)
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

---

### Krok 4: isSubChannel - Text-only CategoryIcon

Dodaj nazwę kanału do listy `isSubChannel`:

```kotlin
val isSubChannel = channel in listOf(
    "Skróty",
    "Pojedyncze nagrania",
    "SERIE",
    "ZAPLANOWANE",
    "[NAZWA_KANAŁU]"  // <-- DODAJ - text-only like TELEWIZJA
)
```

Opcjonalnie dodaj do logoDrawableId mapping:
```kotlin
val logoDrawableId = when (channel) {
    // ... existing mappings ...
    "[NAZWA_KANAŁU]" -> null  // App-icons channel - text-only
    else -> null
}
```

---

### Krok 5: CategoryIcon Fade

Przed sekcją CategoryIcon, dodaj:

```kotlin
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

// CategoryIcon alpha and zIndex for app-icons fade
val categoryAlpha = if (isAppIcons && isCurrentRow && focusedColIndex >= 0 &&
    lazyListState.firstVisibleItemIndex > 0) 0f else 1f
val categoryZIndex = if (isAppIcons) -1f else 0f
```

---

### Krok 6: Zaktualizuj Box z CategoryIcon

Dodaj modifiers alpha i zIndex:

```kotlin
Box(modifier = Modifier
    .offset(x = sx(80), y = sy(0))
    .alpha(categoryAlpha)      // <-- DODAJ
    .zIndex(categoryZIndex)    // <-- DODAJ
) {
    CategoryIcon(...)
}
```

---

### Krok 7: Przekaż tvChannels do UnifiedChannelRow

W funkcji `[Section]ChannelRowsLayout`, przekaż dane TV:

```kotlin
[Section]UnifiedChannelRow(
    // ... existing params ...
    tvChannels = if (channelName == "[NAZWA_KANAŁU]") tvChannelsList else emptyList(),
    onNavigateToEpgDay = onNavigateToEpgDay
)
```

---

### Krok 8: Dodaj channel do listy channels

```kotlin
val channels = listOf(
    // ... existing channels ...
    "[NAZWA_KANAŁU]",  // <-- DODAJ w odpowiedniej pozycji
)

val channelTypes = mapOf(
    // ... existing mappings ...
    "[NAZWA_KANAŁU]" to "app-icons",  // <-- DODAJ
)
```

---

## Validation Checklist

Po implementacji sprawdź:

- [ ] **Wygląd kart**: ChannelListCard 208x208px (nie TvAppIconCard 300x170px)
- [ ] **Spacing**: 12px między kartami
- [ ] **Fokus**: Przeskakuje bezpośrednio na element (nie scrolling window)
- [ ] **Wysokość**: Channel NIE rozszerza się przy fokusie na content
- [ ] **Odstępy**: Stałe między kanałami (poprzedni i następny nie przesuwają się)
- [ ] **CategoryIcon unfocused**: Pokazuje pełną nazwę tekstu (nie "MO" placeholder)
- [ ] **CategoryIcon focused**: Ma czarne tło (showBackgroundWhenFocused=true)
- [ ] **Lista przewinięta**: CategoryIcon znika (alpha=0)
- [ ] **Lista przewinięta**: Tytuł pojawia się nad listą (Y=-45px)
- [ ] **Navigation**: UP/DOWN między kanałami działa poprawnie

---

## Anti-Patterns / Common Mistakes

### ❌ NIE używaj TvAppIconCard
```kotlin
// WRONG
TvAppIconCard(
    appIcon = channel,
    isFocused = isItemFocused,
    ...
)

// CORRECT
ChannelListCard(
    channel = channel,
    isFocused = isItemFocused,
    ...
)
```

### ❌ NIE używaj scrolling window focus
```kotlin
// WRONG - scrolling window model
val isItemFocused = ... && colIndex == lazyListState.firstVisibleItemIndex && focusedColIndex == 0

// CORRECT - direct focus model
val isItemFocused = rowIndex == focusedRowIndex && colIndex == focusedColIndex
```

### ❌ NIE zapomnij o stałych APP_ICONS
```kotlin
// WRONG - brak stałych, używa horizontal heights
// Channel będzie się rozszerzać z 256 do 546px!

// CORRECT - dodaj stałe z identycznymi wartościami
private const val MOJE_APP_ICONS_NORMAL_ROW_HEIGHT = 256
private const val MOJE_APP_ICONS_EXPANDED_ROW_HEIGHT = 256  // IDENTYCZNE!
```

### ❌ NIE zapomnij o isSubChannel
```kotlin
// WRONG - CategoryIcon pokazuje "MO" placeholder
val isSubChannel = channel in listOf("Skróty", "SERIE")

// CORRECT - CategoryIcon pokazuje pełną nazwę
val isSubChannel = channel in listOf("Skróty", "SERIE", "Moja lista kanałów")
```

### ❌ NIE zapomnij o fade i tytule
```kotlin
// WRONG - CategoryIcon zawsze widoczny, brak tytułu
Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
    CategoryIcon(...)
}

// CORRECT - fade + tytuł
val categoryAlpha = if (isAppIcons && ...) 0f else 1f
Box(modifier = Modifier
    .offset(x = sx(80), y = sy(0))
    .alpha(categoryAlpha)
    .zIndex(categoryZIndex)
) {
    CategoryIcon(...)
}
```

---

## Reference Code Locations

### TELEWIZJA (wzorzec do naśladowania)
- **Stałe**: TopMenuScreen2.kt:5659-5660
- **calculateYPosition**: TopMenuScreen2.kt:7036-7107
- **Rendering app-icons**: TopMenuScreen2.kt:8310-8395
- **CategoryIcon fade**: TopMenuScreen2.kt:7941-7997

### MOJE (implementacja z tej sesji)
- **Stałe**: TopMenuScreen2.kt:5593-5595
- **calculateYPosition**: TopMenuScreen2.kt:5677-5787
- **Rendering app-icons**: TopMenuScreen2.kt:5313-5351
- **CategoryIcon fade**: TopMenuScreen2.kt:5517-5541

---

## Summary

Dodanie kanału app-icons wymaga **8 kroków** w **1 pliku**:
1. Stałe spacing (NORMAL=EXPANDED=256)
2. calculateYPosition (6 miejsc do zaktualizowania)
3. Rendering w UnifiedChannelRow (ChannelListCard + direct focus)
4. isSubChannel (text-only CategoryIcon)
5. CategoryIcon fade (alpha + zIndex)
6. Tytuł nad listą
7. Przekazanie tvChannels
8. Dodanie do channels i channelTypes

**Czas implementacji**: 15-20 minut z tą checklistą
**Bez checklisty**: 3+ iteracje poprawek

---

**Koniec dokumentacji**
