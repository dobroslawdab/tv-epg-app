# 📱 APP-ICONS Channel Type - Complete Documentation

**Version**: 2.0
**Last Updated**: 2025-10-15
**Reference Implementations**:
- AplikacjeChannelsScreen ("Ostatnio używane", "Aplikacje") - AppItem pattern
- TelewizjaChannelsScreen ("Dla dzieci", "Dokumenty", "HBO", "Informacyjne") - TvChannel pattern with appIconsData

---

## 🎯 Overview

**app-icons** to typ channela używający modelu **scrolling focus** (focus fixed, content scrolls), przeznaczony do wyświetlania ikon aplikacji lub kanałów TV w stylu app-icons.

### Kluczowe cechy:
- ✅ **Scrolling Focus Model**: Fokus pozostaje na stałej pozycji (colIndex 0), LazyRow przewija się pod nim
- ✅ **Fixed CategoryIcon**: CategoryIcon floating outside LazyRow, znika gdy lista przewinięta
- ✅ **Channel Name Above**: Nazwa channela pojawia się nad listą gdy scrolled (firstVisibleItemIndex > 0)
- ✅ **Large Icons**: 300x170px ikony z tekstem poniżej (20px bold) gdy focused
- ✅ **Tight Spacing**: 12px między itemami
- ✅ **No Expansion**: Brak animacji rozszerzania wiersza (w przeciwieństwie do horizontal channels)

---

## 📐 Visual Specifications

### Layout Dimensions (1920x1080 baseline)

```
CategoryIcon:                  LazyRow content:
┌────────────────┐             ┌─────────┬─────────┬─────────┐
│                │             │  Icon 1 │  Icon 2 │  Icon 3 │
│  CategoryIcon  │             │ 300x170 │ 300x170 │ 300x170 │
│    216x216     │             │  Name   │  Name   │  Name   │
│  (if visible)  │             └─────────┴─────────┴─────────┘
└────────────────┘
X: 80px                        X: 380px, spacing: 12px
```

### Icon Card Specifications

```kotlin
// AppIconCard dimensions
Width: 320px (icon 300px + padding)
Height: 220px (icon 170px + spacing 8px + text ~30px)

// Icon area
Size: 300x170px
Border: 6px when focused (color: 0xFF5AECD3)
Corner radius: 12px

// Name text (shown only when focused)
Font size: 20sp
Font weight: W700 (Bold)
Color: 0xFFEEEEEE (white)
Align: Center
```

### LazyRow Configuration

```kotlin
contentPadding: PaddingValues(start = sx(380), end = sx(20))
horizontalArrangement: Arrangement.spacedBy(sx(12))
```

---

## 🔧 Focus System Architecture

### 1. FocusRequesters Structure

**CRITICAL**: app-icons używa **2 FocusRequesters per row**:

```kotlin
val channelFocusRequesters = remember(channels.size) {
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(channels.size) { rowIndex ->
            val channelName = channels.getOrNull(rowIndex) ?: ""
            if (isAppIconsChannel(channelName)) {
                // app-icons: CategoryIcon + Fixed content position
                put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                put(Pair(rowIndex, 0), FocusRequester())  // Fixed focus position
            }
        }
    }
}
```

**Mapping**:
- `Pair(rowIndex, -1)` → CategoryIcon
- `Pair(rowIndex, 0)` → Content (wszystkie itemy w LazyRow używają tego samego)

**❌ WRONG** - Creating per-item requesters:
```kotlin
// DON'T DO THIS:
repeat(apps.size) { colIndex ->
    put(Pair(rowIndex, colIndex), FocusRequester()) // ❌ Wrong for scrolling model
}
```

### 2. Focus State Management

```kotlin
var focusedRowIndex by remember { mutableStateOf(0) }
var focusedColIndex by remember { mutableStateOf(-2) } // -2 = no focus on start
```

**Focus positions**:
- `focusedColIndex = -1` → CategoryIcon focused
- `focusedColIndex = 0` → Content focused (at firstVisibleItemIndex)
- `focusedColIndex = -2` → No focus (initial state)

### 3. LazyListState Management

```kotlin
val lazyListStates = remember(channels.size) {
    mutableMapOf<Int, LazyListState>().apply {
        repeat(channels.size) { rowIndex ->
            put(rowIndex, LazyListState())
        }
    }
}
```

**Auto-reset** (optional - resets unfocused rows to start):
```kotlin
LaunchedEffect(focusedRowIndex, focusedColIndex, isInitialized) {
    if (isInitialized) {
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
```

---

## 🎨 Rendering Implementation

### LazyRow with Items

```kotlin
LazyRow(
    modifier = Modifier.fillMaxWidth(),
    state = lazyListState,
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(12))
) {
    items(apps.size) { colIndex ->
        val app = apps[colIndex]

        // ⭐ CRITICAL: Focus detection using firstVisibleItemIndex
        val isItemFocused = rowIndex == focusedRowIndex &&
                colIndex == lazyListState.firstVisibleItemIndex &&
                focusedColIndex == 0

        // ⭐ CRITICAL: Use SAME requester for ALL items (position 0)
        val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()

        AppIconCard(
            app = app,
            isFocused = isItemFocused,
            focusRequester = focusRequester,
            onFocusChange = { onChannelContentFocusChange(rowIndex, colIndex) },
            sx = sx,
            sy = sy
        )
    }

    // Spacer items for scrollable area
    items(8) {
        Spacer(
            modifier = Modifier
                .width(sx(320))
                .height(sy(220))
        )
    }
}
```

### 🔑 CRITICAL: FocusRequester Pattern Explained

**Why does each item need its own FocusRequester?**

This is the MOST CONFUSING part of scrolling model. Let me explain step-by-step:

#### The Map Only Has 2 FocusRequesters

```kotlin
val channelFocusRequesters = remember(channels.size) {
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(channels.size) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
            put(Pair(rowIndex, 0), FocusRequester())  // Fixed focus position
        }
    }
}
```

So for row 3, we have:
- `Pair(3, -1)` → CategoryIcon FocusRequester
- `Pair(3, 0)` → Content FocusRequester

That's it! Only 2 requesters per row.

#### But Each Item Tries to Get Its Own

```kotlin
items(apps.size) { colIndex ->
    // colIndex = 0, 1, 2, 3, 4, 5...
    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
}
```

So what happens:
- **colIndex 0**: `channelFocusRequesters[Pair(3, 0)]` → **EXISTS** in map ✅
- **colIndex 1**: `channelFocusRequesters[Pair(3, 1)]` → **DOESN'T EXIST** → creates NEW FocusRequester()
- **colIndex 2**: `channelFocusRequesters[Pair(3, 2)]` → **DOESN'T EXIST** → creates NEW FocusRequester()
- **colIndex 3**: `channelFocusRequesters[Pair(3, 3)]` → **DOESN'T EXIST** → creates NEW FocusRequester()
- etc.

**Result**: Each item gets a UNIQUE FocusRequester object!

#### Why This is Necessary

**❌ WRONG** - If all items used the SAME FocusRequester:
```kotlin
val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
// Problem: ALL items use Pair(rowIndex, 0) → SAME requester!
```

When you call `requestFocus()` on that requester:
1. Compose tries to focus ALL items that have that requester
2. Compose gets confused - which item should be focused?
3. **Result**: Focus doesn't work!

**✅ CORRECT** - Each item has its own FocusRequester:
```kotlin
val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
// Each item has UNIQUE requester (from map or newly created)
```

When you call `requestFocus()`:
1. Compose knows exactly which item to focus (unique requester)
2. Visual focus is controlled by `isFocused` (only item at firstVisibleItemIndex)
3. **Result**: Focus works perfectly!

#### The Two Systems Working Together

**System 1: Compose Focus System**
- Each item has UNIQUE FocusRequester
- When `requestFocus()` called → Compose focuses that specific item
- This is internal Compose machinery

**System 2: Visual Focus**
- Only item at `firstVisibleItemIndex` has `isFocused = true`
- This controls the visual appearance (border, etc.)
- This is what user sees

**Both systems must work together**:
```kotlin
// Visual focus
val isItemFocused = rowIndex == focusedRowIndex &&
        colIndex == lazyListState.firstVisibleItemIndex &&
        focusedColIndex == 0

// Compose focus (unique per item!)
val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
```

#### When We Request Focus

```kotlin
// In navigation handler (DirectionRight from CategoryIcon):
onFocusChange(focusedRowIndex, 0)
channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
```

This requests focus on `Pair(focusedRowIndex, 0)` - which is the requester in the map.

But wait - items 1, 2, 3... have DIFFERENT requesters (newly created)!

**How does it work?**
1. We request focus on Pair(rowIndex, 0) requester
2. Only item with colIndex 0 has that requester (from map)
3. That item becomes Compose-focused
4. LazyRow scrolls to show that item
5. As LazyRow scrolls, `firstVisibleItemIndex` changes
6. Item at new `firstVisibleItemIndex` gets `isFocused = true` (visual)
7. That item has its OWN unique requester (newly created)

**The magic**: Visual focus and Compose focus work independently!
- Compose focus: Always on item 0 (from requestFocus)
- Visual focus: On item at firstVisibleItemIndex (from isFocused)

As you scroll:
- Compose focus stays on item 0 requester
- Visual focus moves to item 1, 2, 3... (as they become firstVisibleItemIndex)
- Each item has unique requester → no conflicts!

#### Summary

**✅ DO**: Each item gets its own requester via `Pair(rowIndex, colIndex)`
```kotlin
val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
```

**❌ DON'T**: All items share same requester
```kotlin
val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
// This breaks focus - all items conflict!
```

**Key insight**: In scrolling model, Compose focus != Visual focus. Each item needs unique requester for Compose's internal tracking, while visual focus is controlled separately via `isFocused`.

---

### Channel Name Above List

```kotlin
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
```

**Warunki wyświetlenia**:
1. `isAppIcons` - channel typu app-icons
2. `isCurrentRow` - wiersz zfokusowany
3. `focusedColIndex >= 0` - fokus na treści (nie na CategoryIcon)
4. `lazyListState.firstVisibleItemIndex > 0` - lista przewinięta

### CategoryIcon with Alpha Animation

```kotlin
// CategoryIcon alpha: fade out when app-icons scrolled
val categoryAlpha = if (isAppIcons && isCurrentRow && focusedColIndex >= 0 && lazyListState.firstVisibleItemIndex > 0) 0f else 1f

// CategoryIcon zIndex: app-icons below LazyRow, others normal
val categoryZIndex = if (isAppIcons) -1f else 0f

// CategoryIcon (fixed position)
Box(
    modifier = Modifier
        .offset(x = sx(80), y = sy(0))
        .alpha(categoryAlpha)
        .zIndex(categoryZIndex)
) {
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
```

**Dlaczego zIndex = -1f?**
- Umieszcza CategoryIcon **pod** LazyRow
- Zapobiega nachodzeniu na zawartość podczas scrollowania
- Nazwa channela (nad listą) zastępuje wizualnie CategoryIcon

---

## 🎮 Navigation Handler

### Scrolling Model Navigation

```kotlin
fun handleAppIconsNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<AppItem>>,
    onReturnToMenu: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    when (event.key) {
        Key.DirectionLeft -> {
            when {
                focusedColIndex == -1 -> {
                    // Already on CategoryIcon - do nothing
                    return true
                }
                focusedColIndex == 0 -> {
                    // On content - scroll left or go to CategoryIcon
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
            }
            return true
        }

        Key.DirectionRight -> {
            val channelName = channels.getOrNull(focusedRowIndex) ?: ""
            val apps = gridContent[channelName] ?: emptyList()

            when {
                focusedColIndex == -1 -> {
                    // From CategoryIcon to content
                    onFocusChange(focusedRowIndex, 0)
                    channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
                }
                focusedColIndex == 0 -> {
                    // On content - scroll right
                    val lazyListState = lazyListStates[focusedRowIndex]
                    val maxIndex = apps.size - 1

                    if (lazyListState != null && lazyListState.firstVisibleItemIndex < maxIndex) {
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
                        }
                    }
                }
            }
            return true
        }

        Key.DirectionUp -> {
            if (focusedRowIndex > 0) {
                val newRowIndex = focusedRowIndex - 1
                // Preserve focus type (CategoryIcon vs content)
                val targetColIndex = if (focusedColIndex == -1) -1 else 0
                onFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            } else {
                // From first row - return to menu
                onReturnToMenu()
            }
            return true
        }

        Key.DirectionDown -> {
            if (focusedRowIndex < channels.size - 1) {
                val newRowIndex = focusedRowIndex + 1
                // Preserve focus type (CategoryIcon vs content)
                val targetColIndex = if (focusedColIndex == -1) -1 else 0
                onFocusChange(newRowIndex, targetColIndex)
                channelFocusRequesters[Pair(newRowIndex, targetColIndex)]?.requestFocus()
            }
            return true
        }
    }

    return false
}
```

**Navigation Logic**:
- **LEFT**: Scroll content left OR go to CategoryIcon (when at start)
- **RIGHT**: Go to content OR scroll content right
- **UP/DOWN**: Move between rows, preserving focus type (CategoryIcon vs content)

### Navigation with Multiple Data Sources

**Use Case**: When app-icons channels use different data types (TvChannel instead of AppItem)

**Problem**: gridContent contains emptyList() for TvChannel-based channels, breaking maxIndex calculation.

**Solution**: Add `appIconsData` parameter with TvChannel lists.

**Updated Signature**:
```kotlin
fun handleTelewizjaNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),  // ⭐ NEW
    onReturnToMenu: () -> Unit
): Boolean
```

**Updated DirectionRight Logic**:
```kotlin
Key.DirectionRight -> {
    if (focusedColIndex == -1) {
        // From CategoryIcon to content
        onFocusChange(focusedRowIndex, 0)
        channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
    } else if (focusedColIndex == 0) {
        // From content - scroll right
        val lazyListState = lazyListStates[focusedRowIndex]
        val channelName = channels.getOrNull(focusedRowIndex) ?: ""

        // ⭐ Check appIconsData FIRST, then gridContent
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
                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
            }
        }
    }
    return true
}
```

**Why Check appIconsData First?**
1. **Priority**: TvChannel data is primary source for app-icons channels in TELEWIZJA
2. **Fallback**: gridContent handles VOD channels and APLIKACJE
3. **Compatibility**: Works with both data types seamlessly

**Usage Example**:
```kotlin
// APLIKACJE (AppItem data)
handleAplikacjeChannelsNavigation(
    gridContent = mapOf("Aplikacje" to appsList),
    // No appIconsData - uses gridContent only
)

// TELEWIZJA (TvChannel data)
handleTelewizjaNavigation(
    gridContent = mapOf("Dla dzieci" to emptyList()),  // Empty for app-icons!
    appIconsData = mapOf("Dla dzieci" to kidsChannels),  // Actual data here
)
```

---

## 📦 AppIconCard Component

```kotlin
@Composable
private fun AppIconCard(
    app: AppItem,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
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
        // Icon with border on focus
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

        // Name shown only when focused
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
```

### Variant: TvAppIconCard (dla TV channels)

```kotlin
@Composable
private fun TvAppIconCard(
    channel: TvChannel, // Uses channel.logo (URL) instead of drawable
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Column(/* same structure */) {
        Box(/* same structure */) {
            AsyncImage( // ⭐ Difference: AsyncImage for remote URLs
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        if (isFocused) {
            Text(text = channel.name /* same styling */)
        }
    }
}
```

---

## ✅ Implementation Checklist

### Pre-Implementation

- [ ] Przeczytaj FOCUS_ARCHITECT.md
- [ ] Przeczytaj FOCUS_NAVIGATION_GUIDE.md
- [ ] Sprawdź istniejące app-icons w APLIKACJE (reference implementation)
- [ ] **Określ dane źródłowe (AppItem vs TvChannel)** ⭐
  - [ ] AppItem → use gridContent only
  - [ ] TvChannel → use gridContent + appIconsData
- [ ] Jeśli TvChannel: Sprawdź dostępność filterTvChannelsByCategory()

### FocusRequesters Setup

- [ ] Tylko 2 FocusRequesters per row: `Pair(rowIndex, -1)` i `Pair(rowIndex, 0)`
- [ ] NIE twórz per-item requesters (0, 1, 2...)
- [ ] Dodaj do `remember(channels.size)` dependency

### LazyListState Setup

- [ ] Jeden LazyListState per row w `Map<Int, LazyListState>`
- [ ] (Optional) Auto-reset unfocused rows w LaunchedEffect
- [ ] LazyListState używany do `firstVisibleItemIndex` tracking

### Data Setup (TvChannel Pattern)

**⚠️ ONLY if using TvChannel data (like TELEWIZJA)**

- [ ] Load channel data: `loadTvChannelsFromAssets(context)`
- [ ] Filter by category: `filterTvChannelsByCategory(channels, "category-name")`
- [ ] Create appIconsData map linking channel names to TvChannel lists
- [ ] gridContent should return `emptyList()` for TvChannel channels
- [ ] Pass appIconsData to navigation handler

### LazyRow Rendering

- [ ] `contentPadding(start = sx(380), end = sx(20))`
- [ ] `horizontalArrangement: Arrangement.spacedBy(sx(12))`
- [ ] Fokus detection: `colIndex == lazyListState.firstVisibleItemIndex && focusedColIndex == 0`
- [ ] FocusRequester: `channelFocusRequesters[Pair(rowIndex, colIndex)]` - **KAŻDY item swój requester** (nie `Pair(rowIndex, 0)` dla wszystkich!)
- [ ] Spacer items (8 sztuk) na końcu LazyRow

### Channel Name Above List

- [ ] Warunek: `isAppIcons && isCurrentRow && focusedColIndex >= 0 && firstVisibleItemIndex > 0`
- [ ] Pozycja: `offset(x = sx(80), y = sy(-45))`
- [ ] Styl: 24sp Bold, 0xFFEEEEEE

### CategoryIcon Behavior

- [ ] Alpha: `0f` gdy scrolled, `1f` otherwise
- [ ] zIndex: `-1f` dla app-icons
- [ ] Offset: `x = sx(80), y = sy(0)`
- [ ] FocusRequester: `Pair(rowIndex, -1)`

### Navigation Handler

- [ ] LEFT: Scroll left OR go to CategoryIcon (at start)
- [ ] RIGHT: Go to content OR scroll right
  - [ ] **If TvChannel**: maxIndex from appIconsData first, then gridContent ⭐
  - [ ] **If AppItem**: maxIndex from gridContent only
- [ ] UP/DOWN: Move between rows, preserve focus type
- [ ] Return to menu from first row UP
- [ ] **If TvChannel**: Add appIconsData parameter to handler signature ⭐

### Testing

- [ ] Focus navigation LEFT/RIGHT działa
- [ ] Scrolling przewija zawartość
- [ ] Nazwa channela pojawia się nad listą
- [ ] CategoryIcon znika gdy scrolled
- [ ] CategoryIcon wraca gdy scroll to start
- [ ] UP/DOWN między channelami działa

---

## 🚫 Common Mistakes (Anti-Patterns)

### ❌ WRONG: Per-Item FocusRequesters

```kotlin
// DON'T DO THIS:
items(apps.size) { colIndex ->
    val focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
    // ❌ This expects per-item requesters, but we only have 0!
}
```

### ✅ RIGHT: Fixed Position FocusRequester

```kotlin
// DO THIS:
items(apps.size) { colIndex ->
    val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
    // ✅ All items use SAME requester (position 0)
}
```

### ❌ WRONG: Missing Channel Name

```kotlin
// DON'T FORGET:
// If you don't add channel name above list, user won't know which channel is scrolled!
```

### ❌ WRONG: CategoryIcon Always Visible

```kotlin
// DON'T DO THIS:
Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
    CategoryIcon(...) // ❌ No alpha animation!
}
```

### ✅ RIGHT: CategoryIcon with Alpha

```kotlin
// DO THIS:
val categoryAlpha = if (isAppIcons && ... && firstVisibleItemIndex > 0) 0f else 1f
Box(modifier = Modifier.offset(...).alpha(categoryAlpha).zIndex(-1f)) {
    CategoryIcon(...)
}
```

### ❌ WRONG: Using gridContent for TvChannel Data

```kotlin
// DON'T DO THIS:
Key.DirectionRight -> {
    val channelName = channels.getOrNull(focusedRowIndex) ?: ""
    val rowContent = gridContent[channelName] ?: emptyList()  // Returns emptyList() for app-icons!
    val maxIndex = rowContent.size - 1  // = -1

    if (lazyListState.firstVisibleItemIndex < maxIndex) {  // NEVER true!
        // ❌ Scrolling NEVER happens
    }
}
```

**Problem**: TvChannel channels have `emptyList()` in gridContent → maxIndex is -1 → scrolling blocked.

### ✅ RIGHT: Check appIconsData First

```kotlin
// DO THIS:
Key.DirectionRight -> {
    val channelName = channels.getOrNull(focusedRowIndex) ?: ""

    // ⭐ Check appIconsData FIRST, then gridContent
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

    if (lazyListState.firstVisibleItemIndex < maxIndex) {
        // ✅ Scrolling works correctly!
    }
}
```

**Solution**: Priority check - appIconsData for TvChannel, fallback to gridContent for AppItem/VodContent.

---

## 📚 Reference Implementations

### APLIKACJE Section

**File**: `TopMenuScreen2.kt`

**Channels**:
- "Ostatnio używane" (2 apps: Disney+, Apple TV)
- "Aplikacje" (6 apps: Netflix, YouTube, Prime Video, Spotify, Disney+, Apple TV)

**Lines**:
- FocusRequesters: 1864-1880
- LazyListStates: 1882-1888
- Rendering: 4819-4836 (LazyRow items)
- Channel Name: 4961-4972
- CategoryIcon Alpha: 4974-4975
- CategoryIcon zIndex: 4977-4978
- CategoryIcon Render: 4981-5023

**Navigation Handler**: `handleAplikacjeChannelsNavigation()` (lines 3808+)

### TELEWIZJA Section (After Fix)

**File**: `TopMenuScreen2.kt`

**Channels**:
- "Dla dzieci" (3 channels from tv_channels.json, category: "dla-dzieci")
- "Dokumenty" (2 channels, category: "dokumenty")
- "Filmy i seriale HBO" (3 channels, category: "hbo")
- "Informacyjne" (2 channels, category: "informacyjne")

**Lines**:
- FocusRequesters: 1445-1478 (after fix)
- Rendering: 5746-5799 (after fix)

**Navigation Handler**: `handleTelewizjaNavigation()` (lines 4128+)

**Critical Implementation Details**:
- appIconsData map: 1423-1431
- appIconsData passed to handler: 1618
- Navigation handler with appIconsData parameter: 4139
- maxIndex calculation fix: 4263-4273

---

## 🔧 TELEWIZJA Implementation with TvChannel Data

### Problem: gridContent Returns emptyList()

TELEWIZJA używa danych z `tv_channels.json` (typ `TvChannel`) zamiast `VodContent`. Channels app-icons mają `emptyList()` w `gridContent`:

```kotlin
val gridContent = remember {
    channels.associateWith { channelName ->
        when (channelName) {
            "Dla dzieci", "Dokumenty", "Filmy i seriale HBO", "Informacyjne" -> emptyList() // ❌
            else -> vodContentList.shuffled().take(10)
        }
    }
}
```

**Konsekwencja**: Navigation handler nie może obliczyć `maxIndex`:

```kotlin
// DirectionRight navigation
val rowContent = gridContent[channelName] ?: emptyList() // Returns emptyList()!
val maxIndex = rowContent.size - 1  // = -1
if (lazyListState.firstVisibleItemIndex < maxIndex) {  // NEVER true!
    // This scroll NEVER happens
}
```

**Symptom**: Użytkownik może zfokusować CategoryIcon i pierwszy item, ale **nie może scrollować w prawo**.

### Root Cause Analysis

1. **Data Source Mismatch**: App-icons channels używają `TvChannel` lists (kidsChannels, docChannels, etc.), ale navigation handler sprawdza `gridContent` (VodContent)
2. **Empty gridContent**: Channels mają `emptyList()` bo nie mają VodContent
3. **maxIndex = -1**: Pusty list → size 0 → maxIndex -1
4. **Blocked Scrolling**: Warunek `firstVisibleItemIndex < maxIndex` nigdy nie jest spełniony

### Solution: appIconsData Map

**Krok 1: Stwórz appIconsData map**

```kotlin
// After gridContent definition (line ~1422)
val appIconsData = remember {
    mapOf(
        "Dla dzieci" to kidsChannels,
        "Dokumenty" to docChannels,
        "Filmy i seriale HBO" to hboChannels,
        "Informacyjne" to newsChannels
    )
}
```

**Gdzie pochodzą listy?**

```kotlin
// Earlier in component (line ~1349)
val tvChannelLogos = remember { loadTvChannelsFromAssets(context) }
val kidsChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "dla-dzieci") }
val docChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "dokumenty") }
val hboChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "hbo") }
val newsChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "informacyjne") }
```

**TvChannel data structure**:

```kotlin
data class TvChannel(
    val id: String,
    val name: String,
    val logo: String,  // Remote URL
    val category: String,
    val streamUrl: String
)
```

**Krok 2: Pass appIconsData do navigation handler**

```kotlin
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
    lazyListStates = lazyListStates,
    coroutineScope = coroutineScope,
    gridContent = gridContent,
    appIconsData = appIconsData,  // ⭐ NEW
    onReturnToMenu = onReturnToMenu
)
```

**Krok 3: Update navigation handler signature**

```kotlin
fun handleTelewizjaNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<VodContent>>,
    appIconsData: Map<String, List<TvChannel>> = emptyMap(),  // ⭐ NEW
    onReturnToMenu: () -> Unit
): Boolean
```

**Krok 4: Fix maxIndex calculation w DirectionRight**

```kotlin
Key.DirectionRight -> {
    if (focusedColIndex == -1) {
        // From CategoryIcon to content
        onFocusChange(focusedRowIndex, 0)
        channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
    } else if (focusedColIndex == 0) {
        // From content - scroll right
        val lazyListState = lazyListStates[focusedRowIndex]
        val channelName = channels.getOrNull(focusedRowIndex) ?: ""

        // ⭐ Check appIconsData FIRST, then gridContent
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
                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex + 1)
            }
        }
    }
    return true
}
```

**Opcjonalnie: Fix DirectionLeft (consistency)**

```kotlin
Key.DirectionLeft -> {
    // Same pattern - check appIconsData first for edge detection
    if (focusedColIndex == 0) {
        val lazyListState = lazyListStates[focusedRowIndex]
        if (lazyListState != null && lazyListState.firstVisibleItemIndex > 0) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(lazyListState.firstVisibleItemIndex - 1)
            }
        } else {
            // At start - go to CategoryIcon
            onFocusChange(focusedRowIndex, -1)
            channelFocusRequesters[Pair(focusedRowIndex, -1)]?.requestFocus()
        }
    }
}
```

### Complete Implementation Pattern

**TelewizjaChannelsScreen (complete setup)**:

```kotlin
@Composable
private fun TelewizjaChannelsScreen(
    onReturnToMenu: () -> Unit = {},
    shouldAutoFocus: Boolean = false,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    resetTrigger: Int = 0
) {
    val context = LocalContext.current

    // 1. Load TV channel logos from JSON
    val tvChannelLogos = remember { loadTvChannelsFromAssets(context) }
    val kidsChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "dla-dzieci") }
    val docChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "dokumenty") }
    val hboChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "hbo") }
    val newsChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "informacyjne") }

    // 2. Define channels list
    val channels = listOf(
        "Slider Mix",
        "Teraz w TV",
        "Najczęściej oglądane",
        "Dla dzieci",       // app-icons
        "Dokumenty",        // app-icons
        "Filmy i seriale HBO",  // app-icons
        "Informacyjne"      // app-icons
    )

    // 3. Define channel types
    val channelTypes = remember {
        mapOf(
            "Slider Mix" to "slider-max",
            "Teraz w TV" to "horizontal",
            "Najczęściej oglądane" to "horizontal",
            "Dla dzieci" to "app-icons",
            "Dokumenty" to "app-icons",
            "Filmy i seriale HBO" to "app-icons",
            "Informacyjne" to "app-icons"
        )
    }

    // 4. gridContent for VOD channels
    val gridContent = remember {
        val vodContentList = VodDataCache.getVodContentList()
        channels.associateWith { channelName ->
            when (channelName) {
                "Slider Mix" -> vodContentList.shuffled().take(10)
                "Teraz w TV" -> vodContentList.shuffled().take(10)
                "Najczęściej oglądane" -> vodContentList.shuffled().take(10)
                "Dla dzieci", "Dokumenty", "Filmy i seriale HBO", "Informacyjne" -> emptyList()
                else -> vodContentList.shuffled().take(10)
            }
        }
    }

    // 5. ⭐ appIconsData for TvChannel channels
    val appIconsData = remember {
        mapOf(
            "Dla dzieci" to kidsChannels,
            "Dokumenty" to docChannels,
            "Filmy i seriale HBO" to hboChannels,
            "Informacyjne" to newsChannels
        )
    }

    // 6. FocusRequesters - ONLY 2 per app-icons row!
    val channelFocusRequesters = remember(channels.size) {
        mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
            repeat(channels.size) { rowIndex ->
                val channelName = channels.getOrNull(rowIndex) ?: ""
                when (channelTypes[channelName]) {
                    "app-icons" -> {
                        put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
                        put(Pair(rowIndex, 0), FocusRequester())  // Fixed focus
                    }
                    // ... other types
                }
            }
        }
    }

    // 7. LazyListStates
    val lazyListStates = remember(channels.size) {
        mutableMapOf<Int, LazyListState>().apply {
            repeat(channels.size) { rowIndex ->
                put(rowIndex, LazyListState())
            }
        }
    }

    // 8. Navigation with appIconsData
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
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
                    lazyListStates = lazyListStates,
                    coroutineScope = coroutineScope,
                    gridContent = gridContent,
                    appIconsData = appIconsData,  // ⭐
                    onReturnToMenu = onReturnToMenu
                )
            }
    ) {
        // Channel rows rendering...
    }
}
```

### Testing TELEWIZJA app-icons

**Test Case 1: Focus Navigation**
1. Navigate to "Dla dzieci" CategoryIcon → ✅ Should focus
2. Press RIGHT → ✅ Should move to content (first icon)
3. Press DOWN → ✅ Should move to "Dokumenty" CategoryIcon

**Test Case 2: Scrolling RIGHT**
1. Focus on "Dla dzieci" content (first icon)
2. Press RIGHT repeatedly → ✅ Should scroll through all 3 kids channels
3. At last channel → ✅ Should stop (not crash)

**Test Case 3: Scrolling LEFT**
1. Focus on "Dla dzieci" content (scrolled to middle)
2. Press LEFT repeatedly → ✅ Should scroll back to start
3. At first channel → ✅ Should return to CategoryIcon

**Test Case 4: Channel Name Display**
1. Focus on "Dla dzieci" CategoryIcon → ✅ CategoryIcon visible
2. Press RIGHT (to content) → ✅ CategoryIcon still visible (not scrolled)
3. Press RIGHT (scroll to second item) → ✅ Channel name "Dla dzieci" appears above list, CategoryIcon fades out

**Expected Results**:
- ✅ All 4 app-icons channels scroll correctly
- ✅ maxIndex calculation works (kidsChannels.size = 3 → maxIndex = 2)
- ✅ No crashes or focus loss
- ✅ Channel name replaces CategoryIcon when scrolled

---

## 📊 Data Source Patterns

### Pattern 1: AppItem (APLIKACJE)

**Use Case**: Displaying installed apps or app shortcuts

**Data Structure**:
```kotlin
data class AppItem(
    val id: String,
    val name: String,
    val iconResId: Int  // Local drawable resource
)
```

**Source**:
```kotlin
val recentApps = remember {
    listOf(
        AppItem("1", "Disney+", R.drawable.disney_plus_logo),
        AppItem("2", "Apple TV", R.drawable.apple_tv_logo)
    )
}
```

**gridContent Usage**:
```kotlin
val gridContent = remember {
    mapOf(
        "Ostatnio używane" to recentApps,
        "Aplikacje" to allApps
    )
}
```

**Navigation**:
```kotlin
handleAplikacjeChannelsNavigation(
    // ... other params
    gridContent = gridContent
    // No appIconsData needed!
)
```

### Pattern 2: TvChannel (TELEWIZJA)

**Use Case**: Displaying TV channel logos from JSON

**Data Structure**:
```kotlin
data class TvChannel(
    val id: String,
    val name: String,
    val logo: String,      // Remote URL!
    val category: String,
    val streamUrl: String
)
```

**Source**:
```kotlin
val tvChannelLogos = remember { loadTvChannelsFromAssets(context) }
val kidsChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "dla-dzieci") }
val docChannels = remember { filterTvChannelsByCategory(tvChannelLogos, "dokumenty") }
```

**gridContent + appIconsData Usage**:
```kotlin
val gridContent = remember {
    channels.associateWith { channelName ->
        when (channelName) {
            "Dla dzieci", "Dokumenty" -> emptyList() // TvChannel channels!
            else -> vodContentList.shuffled().take(10)
        }
    }
}

val appIconsData = remember {
    mapOf(
        "Dla dzieci" to kidsChannels,
        "Dokumenty" to docChannels
    )
}
```

**Navigation**:
```kotlin
handleTelewizjaNavigation(
    // ... other params
    gridContent = gridContent,
    appIconsData = appIconsData  // ⭐ Required!
)
```

### Decision Tree: When to Use appIconsData

```
Is your app-icons channel using TvChannel data?
├─ YES → Use appIconsData map
│         - Create map linking channel name → TvChannel list
│         - Pass to navigation handler
│         - Update maxIndex calculation
│
└─ NO → Use gridContent only
          - AppItem data works directly with gridContent
          - No appIconsData needed
```

### filterTvChannelsByCategory Usage

**Function** (in TopMenuScreen2.kt):
```kotlin
private fun filterTvChannelsByCategory(channels: List<TvChannel>, category: String): List<TvChannel> {
    return channels.filter { it.category.equals(category, ignoreCase = true) }
}
```

**Usage**:
```kotlin
val kidsChannels = remember {
    filterTvChannelsByCategory(tvChannelLogos, "dla-dzieci")
}
```

**Categories in tv_channels.json**:
- `"dla-dzieci"` → Kids channels
- `"dokumenty"` → Documentary channels
- `"hbo"` → HBO channels
- `"informacyjne"` → News channels

---

## 🔄 Migration from Other Channel Types

### From "horizontal" to "app-icons"

**Changes needed**:
1. FocusRequesters: Remove per-item, keep only -1 and 0
2. Fokus detection: Change to `colIndex == firstVisibleItemIndex && focusedColIndex == 0`
3. Add: Channel name above list
4. Add: CategoryIcon alpha animation
5. Change: CategoryIcon zIndex to -1f
6. Update: Item spacing to 12px
7. Update: Item dimensions to 320x220 (AppIconCard)
8. Remove: Details overlay (no expansion for app-icons)

### From "direct-focus" to "app-icons"

**Changes needed**:
1. Navigation: From direct to scrolling model (use animateScrollToItem)
2. FocusRequesters: From per-item (0-N) to fixed (only -1, 0)
3. LazyRow: Add state parameter
4. Add: All scrolling model features (channel name, alpha, etc.)

---

## 📖 See Also

- [FOCUS_ARCHITECT.md](../agents/focus-architect.md) - Focus system architecture
- [FOCUS_NAVIGATION_GUIDE.md](../focus-patterns/FOCUS_NAVIGATION_GUIDE.md) - Navigation patterns
- [CHANNEL_SYSTEM.md](../CHANNEL_SYSTEM.md) - All channel types overview
- [CLAUDE.md](../../CLAUDE.md) - Key Event Management System

---

## 📝 Changelog

**v2.0** (2025-10-15):
- ✅ Added complete TELEWIZJA implementation with TvChannel data
- ✅ Documented appIconsData pattern for multiple data sources
- ✅ Added maxIndex calculation fix for DirectionRight scrolling
- ✅ New section: Data Source Patterns (AppItem vs TvChannel)
- ✅ Updated Implementation Checklist with TvChannel-specific steps
- ✅ Added anti-pattern: Using gridContent for TvChannel data
- ✅ Complete code examples for TelewizjaChannelsScreen

**v1.0** (2025-10-15):
- Initial documentation
- APLIKACJE reference implementation
- Scrolling focus model explanation
- FocusRequester pattern documentation

---

**Last Reviewed**: 2025-10-15
**Status**: ✅ Production-ready with dual data source support
**Maintainer**: Focus Architect
**Tested**: APLIKACJE (AppItem), TELEWIZJA (TvChannel)
