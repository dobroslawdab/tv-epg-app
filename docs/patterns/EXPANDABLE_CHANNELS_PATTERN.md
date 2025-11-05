# Wzorzec Expandable Channels (Rozwijane Kanały)

## Spis treści
1. [Przegląd](#przegląd)
2. [Kiedy używać](#kiedy-używać)
3. [Architektura wzorca](#architektura-wzorca)
4. [Komponenty wzorca](#komponenty-wzorca)
5. [Implementacja krok po kroku](#implementacja-krok-po-kroku)
6. [Pułapki i Anti-patterns](#pułapki-i-anti-patterns)
7. [Historia debugowania](#historia-debugowania)
8. [Checklist testowy](#checklist-testowy)
9. [Referencje do kodu](#referencje-do-kodu)

---

## Przegląd

**Expandable Channels Pattern** to architektoniczny wzorzec implementacji rozwijanych kanałów w aplikacji Android TV. Pozwala na dynamiczne wstawianie/usuwanie sub-kanałów jako **oddzielnych pełnowymiarowych rzędów** w liście kanałów.

### Diagram: Collapsed vs Expanded

```
COLLAPSED (5 kanałów):                    EXPANDED (8 kanałów):
┌─────────────────────────┐              ┌─────────────────────────┐
│ [Icon] Oglądaj dalej    │              │ [Icon] Oglądaj dalej    │
├─────────────────────────┤              ├─────────────────────────┤
│ [Icon] Nagrania     ▼   │  ────────>   │ [Icon] Nagrania     ▲   │
├─────────────────────────┤              ├─────────────────────────┤
│ [Icon] Do obejrzenia    │              │ [Icon] Pojedyncze...    │ <- Sub 1
├─────────────────────────┤              ├─────────────────────────┤
│ [Icon] Wypożyczone      │              │ [Icon] SERIE            │ <- Sub 2
├─────────────────────────┤              ├─────────────────────────┤
│ [Icon] Aktywne pakiety  │              │ [Icon] ZAPLANOWANE      │ <- Sub 3
└─────────────────────────┘              ├─────────────────────────┤
                                         │ [Icon] Do obejrzenia    │
                                         ├─────────────────────────┤
                                         │ [Icon] Wypożyczone      │
                                         ├─────────────────────────┤
                                         │ [Icon] Aktywne pakiety  │
                                         └─────────────────────────┘
```

### Kluczowe zasady

1. **Sub-kanały = oddzielne rzędy** (NIE zagnieżdżone komponenty)
2. **FocusRequestery = stabilna mapa** dla maksymalnej liczby kanałów
3. **Conditional assignment = focus window** (tylko `firstVisibleItemIndex`)
4. **Auto-collapse = nawigacja graniczna** (UP/DOWN przy brzegach)

---

## Kiedy używać

### Idealny dla:

- ✅ **Kategorie z sub-kategoriami**: Nagrania → Pojedyncze/Serie/Zaplanowane
- ✅ **Playlisty VOD**: Moje filmy → Akcja/Komedia/Dramat
- ✅ **Aplikacje z folderami**: Aplikacje → Gry/Streaming/Narzędzia
- ✅ **Historyczne grupowanie**: Historia → Dzisiaj/Wczoraj/Tydzień temu

### Nieodpowiedni dla:

- ❌ **Zagnieżdżone menu** (więcej niż 1 poziom głębokości)
- ❌ **Dynamiczne kategorie** (nieokreślona liczba sub-kanałów)
- ❌ **Różne wysokości rzędów** (sub-kanały MUSZĄ wyglądać jak regularne kanały)

---

## Architektura wzorca

### Podstawowa struktura danych

```kotlin
// ✅ CORRECT: Prosta flaga Boolean
var isNagraniaExpanded by remember { mutableStateOf(false) }

// ❌ WRONG: Złożone nested data structures
data class ExpandableChannel(
    val name: String,
    val subChannels: List<SubChannel> = emptyList() // NIE!
)
```

### Dynamiczna lista kanałów

```kotlin
val MAX_MOJE_CHANNELS = 8  // Maksymalna liczba gdy rozwinięte

val channels = remember(isNagraniaExpanded) {
    if (isNagraniaExpanded) {
        listOf(
            "Oglądaj dalej",
            "Nagrania",              // Parent channel
            "Pojedyncze nagrania",   // Sub-channel 1 (inserted)
            "SERIE",                 // Sub-channel 2 (inserted)
            "ZAPLANOWANE",           // Sub-channel 3 (inserted)
            "Do obejrzenia",
            "Wypożyczone",
            "Aktywne pakiety"
        )
    } else {
        listOf(
            "Oglądaj dalej",
            "Nagrania",              // Parent channel (collapsed)
            "Do obejrzenia",
            "Wypożyczone",
            "Aktywne pakiety"
        )
    }
}
```

**Kluczowe:**
- `remember(isNagraniaExpanded)` - przetwarzane przy zmianie stanu
- Sub-kanały **wstawione BEZPOŚREDNIO** do listy (nie w nested structure)
- Wszystkie kanały renderowane tym samym `MojeUnifiedChannelRow` composable

---

## Komponenty wzorca

### 1. Dynamic Channel List (Dynamiczna lista)

**Cel**: Rozszerzanie/zwijanie listy kanałów przez wstawianie/usuwanie elementów.

**Kod** (`TopMenuScreen2.kt:2418-2439`):

```kotlin
val channels = remember(isNagraniaExpanded) {
    if (isNagraniaExpanded) {
        // Lista z 8 kanałami (3 sub-kanały wstawione)
        listOf("A", "Parent", "Sub1", "Sub2", "Sub3", "B", "C", "D")
    } else {
        // Lista z 5 kanałami (sub-kanały usunięte)
        listOf("A", "Parent", "B", "C", "D")
    }
}
```

**Dlaczego to działa:**
- `remember(key)` - rekalkulacja tylko gdy `key` się zmieni
- Ta sama struktura danych dla wszystkich kanałów
- Brak specjalnej logiki renderowania dla sub-kanałów

---

### 2. Stable FocusRequesters (Stabilne FocusRequestery)

**Problem**: Race conditions przy tworzeniu FocusRequesterów podczas expand/collapse.

**Rozwiązanie**: Tworzenie mapy dla **maksymalnej liczby kanałów**, bez zależności od `channels.size`.

**Kod** (`TopMenuScreen2.kt:2482-2491`):

```kotlin
// ❌ WRONG: Recreates on every expansion
val channelFocusRequesters = remember(channels.size) {
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(channels.size) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester()) // CategoryIcon
            put(Pair(rowIndex, 0), FocusRequester())  // Content
        }
    }
}

// ✅ CORRECT: Created once for max channels
val channelFocusRequesters = remember {  // NO KEY!
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(MAX_MOJE_CHANNELS) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester())
            put(Pair(rowIndex, 0), FocusRequester())
        }
    }
}
```

**Dlaczego to krytyczne:**
- `channels.size` zmienia się: 5 → 8 → 5
- `remember(key)` przetwarzane gdy klucz się zmieni
- Stare FocusRequestery stają się nieważne ("orphaned")
- Komponenty nadal referencują stare FocusRequestery → crash/focus lost

**Diagram race condition:**

```
TIME  | channels.size | FocusRequesters Map       | Components State
-------------------------------------------------------------------
T0    | 5             | {0..4} created (Map A)    | Components → Map A ✅
T1    | User presses OK (expand)
T2    | 8             | {0..7} created (Map B)    | Components → Map A ❌ (orphaned!)
      |               | Map A deleted             | FOCUS LOST!
T3    | User presses BACK (collapse)
T4    | 5             | {0..4} created (Map C)    | Components → Map A/B ❌ (both gone!)
      |               | Map B deleted             | CRASH: "FocusRequester not initialized"
```

**Rozwiązanie:**

```
TIME  | channels.size | FocusRequesters Map       | Components State
-------------------------------------------------------------------
T0    | 5             | {0..7} created ONCE       | Components → Map {0..7} ✅
T1    | User presses OK (expand)
T2    | 8             | Same map (no recreation)  | Components → Map {0..7} ✅
T3    | User presses BACK (collapse)
T4    | 5             | Same map (no recreation)  | Components → Map {0..7} ✅
```

**Ten sam wzorzec dla LazyListState** (`TopMenuScreen2.kt:2494-2500`):

```kotlin
val lazyListStates = remember {  // NO KEY!
    mutableMapOf<Int, LazyListState>().apply {
        repeat(MAX_MOJE_CHANNELS) { rowIndex ->
            put(rowIndex, LazyListState())
        }
    }
}
```

---

### 3. Conditional FocusRequester Assignment (Focus Window)

**Problem**: LazyRow może mieć 100+ kart, ale tylko **jedna** powinna posiadać FocusRequester.

**Rozwiązanie**: "Fixed focus window" - fokus pozostaje na pozycji 0, content scrolluje się pod spodem.

**Kod** (`TopMenuScreen2.kt:4205-4233`):

```kotlin
items(rowContent.size) { colIndex ->
    val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

    // Focused jeśli: correct row AND visible item AND colIndex=0
    val isItemFocused = rowIndex == focusedRowIndex &&
                       colIndex == lazyListState.firstVisibleItemIndex &&
                       focusedColIndex == 0

    // ✅ CRITICAL: Only visible item gets real FocusRequester
    val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
        channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
    } else {
        FocusRequester()  // Dummy for non-visible items
    }

    ContentCard(
        vodContent = vodContent,
        isFocused = isItemFocused,
        focusRequester = focusRequester,
        // ...
    )
}
```

**Diagram focus window:**

```
LazyRow Items:     [0]  [1]  [2]  [3]  [4]  [5] ...
                    ↑
                    └── firstVisibleItemIndex = 0
                    └── Gets real FocusRequester

User presses RIGHT:

LazyRow scrolls:   [0] [1]  [2]  [3]  [4]  [5] ...
                        ↑
                        └── firstVisibleItemIndex = 1
                        └── Gets real FocusRequester (reassigned!)
```

**Dlaczego to działa:**
- Fokus WYGLĄDA jakby był na pozycji 0 (nie porusza się)
- Content scrolluje się w lewo
- FocusRequester automatycznie przeniesiony na nowy `firstVisibleItemIndex`
- Brak konfliktów fokusa (tylko jedna karta ma real FocusRequester)

**❌ Częsty błąd:**

```kotlin
// WRONG: All 100 items get same FocusRequester
val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)]

ContentCard(focusRequester = focusRequester)  // ❌ Focus conflicts!
```

---

### 4. Navigation Handler (Obsługa nawigacji)

**Modyfikacje**: Standardowa nawigacja UP/DOWN/LEFT/RIGHT z dodatkowymi edge cases.

**Główne zmiany** (`TopMenuScreen2.kt:4509-4665`):

#### A. LEFT/RIGHT w content (scrollowanie LazyRow)

```kotlin
// RIGHT: Scroll content w prawo
Key.DirectionRight -> {
    if (focusedColIndex == -1) {
        // CategoryIcon → Content (first item)
        onChannelContentFocusChange(focusedRowIndex, 0)
        channelFocusRequesters[Pair(focusedRowIndex, 0)]?.requestFocus()
        return true
    } else {
        // Content → scroll right with bounds check
        val lazyListState = lazyListStates[focusedRowIndex]
        val maxIndex = (channels content size) - 1
        if (lazyListState.firstVisibleItemIndex < maxIndex) {
            GlobalScope.launch {
                lazyListState.animateScrollToItem(
                    lazyListState.firstVisibleItemIndex + 1
                )
            }
        }
        return true
    }
}
```

#### B. UP/DOWN z auto-collapse

```kotlin
// UP from first sub-channel → collapse and focus parent
if (isNagraniaExpanded && currentChannel == "Pojedyncze nagrania") {
    onToggleExpansion?.invoke()  // Collapse
    GlobalScope.launch {
        delay(50)  // Wait for channel list update
        onChannelContentFocusChange(1, targetColIndex)  // Parent row
        channelFocusRequesters[Pair(1, targetColIndex)]?.requestFocus()
    }
    return true
}

// DOWN from last sub-channel → collapse and focus next
if (isNagraniaExpanded && currentChannel == "ZAPLANOWANE") {
    onToggleExpansion?.invoke()  // Collapse
    GlobalScope.launch {
        delay(50)
        onChannelContentFocusChange(2, targetColIndex)  // Next row after parent
        channelFocusRequesters[Pair(2, targetColIndex)]?.requestFocus()
    }
    return true
}
```

**Dlaczego delay(50)?**
- `onToggleExpansion()` zmienia `isNagraniaExpanded`
- `remember(isNagraniaExpanded)` przetwarzane (channels list update)
- Recomposition trwa ~16-50ms
- Delay zapewnia że nowe indeksy są gotowe przed requestFocus()

---

### 5. Auto-Collapse Logic (Automatyczne zwijanie)

**Cel**: Intuicyjna nawigacja - wychodząc poza sub-kanały automatycznie zwijaj.

**Triggery:**

| Akcja | Warunek | Rezultat |
|-------|---------|----------|
| UP | Z pierwszego sub-kanału | Collapse + fokus na parent |
| DOWN | Z ostatniego sub-kanału | Collapse + fokus na następny kanał po parent |
| BACK | Z dowolnego miejsca | Collapse + fokus na parent |

**Implementacja** (`TopMenuScreen2.kt:4539-4599`):

```kotlin
// UP from first sub-channel
Key.DirectionUp -> {
    if (isNagraniaExpanded && currentChannel == "Pojedyncze nagrania") {
        onToggleExpansion?.invoke()
        GlobalScope.launch {
            delay(50)
            val targetRow = 1  // Parent "Nagrania"
            onChannelContentFocusChange(targetRow, targetColIndex)
            channelFocusRequesters[Pair(targetRow, targetColIndex)]?.requestFocus()
        }
        return true
    }
    // ... standard UP logic
}

// DOWN from last sub-channel
Key.DirectionDown -> {
    if (isNagraniaExpanded && currentChannel == "ZAPLANOWANE") {
        onToggleExpansion?.invoke()
        GlobalScope.launch {
            delay(50)
            val targetRow = 2  // "Do obejrzenia" (po collapse będzie index 2)
            onChannelContentFocusChange(targetRow, targetColIndex)
            channelFocusRequesters[Pair(targetRow, targetColIndex)]?.requestFocus()
        }
        return true
    }
    // ... standard DOWN logic
}

// BACK from any sub-channel
Key.Back -> {
    if (isNagraniaExpanded &&
        currentChannel in listOf("Pojedyncze nagrania", "SERIE", "ZAPLANOWANE")) {
        onToggleExpansion?.invoke()
        GlobalScope.launch {
            delay(50)
            val targetRow = 1  // Parent "Nagrania"
            onChannelContentFocusChange(targetRow, -1)  // Focus on CategoryIcon
            channelFocusRequesters[Pair(targetRow, -1)]?.requestFocus()
        }
        return true
    }
    return false  // Propagate to parent handler
}
```

---

## Implementacja krok po kroku

### Checklist dla nowej sekcji (np. APLIKACJE)

#### Faza 1: Setup expansion state

- [ ] Dodaj stałą `MAX_[SECTION]_CHANNELS` (wartość = max liczba kanałów gdy rozwinięte)
- [ ] Dodaj `var is[Category]Expanded by remember { mutableStateOf(false) }`
- [ ] Stwórz `val toggleExpansion: () -> Unit = { is[Category]Expanded = !is[Category]Expanded }`

**Kod:**
```kotlin
val MAX_APLIKACJE_CHANNELS = 10  // 7 base + 3 sub-channels
var isGamesExpanded by remember { mutableStateOf(false) }
val toggleGamesExpansion: () -> Unit = {
    isGamesExpanded = !isGamesExpanded
}
```

#### Faza 2: Dynamic channel list

- [ ] Stwórz `val channels = remember(is[Category]Expanded) { ... }`
- [ ] W `if (is[Category]Expanded)`: zwróć expanded list z sub-kanałami
- [ ] W `else`: zwróć collapsed list bez sub-kanałów
- [ ] Upewnij się że sub-kanały są wstawione **bezpośrednio po parent channel**

**Kod:**
```kotlin
val channels = remember(isGamesExpanded) {
    if (isGamesExpanded) {
        listOf(
            "Wszystkie aplikacje",
            "Gry",                  // Parent
            "Gry akcji",           // Sub 1
            "Gry sportowe",        // Sub 2
            "Gry edukacyjne",      // Sub 3
            "Streaming",
            "Narzędzia"
        )
    } else {
        listOf(
            "Wszystkie aplikacje",
            "Gry",                  // Parent (collapsed)
            "Streaming",
            "Narzędzia"
        )
    }
}
```

#### Faza 3: Stable FocusRequesters

- [ ] Znajdź deklarację `val channelFocusRequesters = remember(...)`
- [ ] **Usuń** klucz z `remember()` (change `remember(channels.size)` to `remember`)
- [ ] Zmień `repeat(channels.size)` na `repeat(MAX_[SECTION]_CHANNELS)`
- [ ] Powtórz dla `lazyListStates`

**Kod:**
```kotlin
// BEFORE:
val channelFocusRequesters = remember(channels.size) {
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(channels.size) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester())
            put(Pair(rowIndex, 0), FocusRequester())
        }
    }
}

// AFTER:
val channelFocusRequesters = remember {  // NO KEY!
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(MAX_APLIKACJE_CHANNELS) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester())
            put(Pair(rowIndex, 0), FocusRequester())
        }
    }
}
```

#### Faza 4: Conditional FocusRequester assignment

- [ ] Znajdź `items(rowContent.size)` w channel row LazyRow
- [ ] Znajdź przypisanie `val focusRequester = ...`
- [ ] Zmień na conditional assignment bazując na `firstVisibleItemIndex`
- [ ] Powtórz dla WSZYSTKICH typów kart (PackageCard, ContentCard, VodContentCard, etc.)

**Kod:**
```kotlin
items(rowContent.size) { colIndex ->
    val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

    // ✅ Add conditional assignment
    val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
        channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
    } else {
        FocusRequester()
    }

    ContentCard(
        focusRequester = focusRequester,  // Use conditional requester
        // ...
    )
}
```

#### Faza 5: Navigation handler updates

- [ ] Znajdź funkcję `handle[Section]Navigation(...)`
- [ ] Dodaj parametr `onToggleExpansion: (() -> Unit)? = null`
- [ ] Dodaj parametr `isExpanded: Boolean = false`
- [ ] Implementuj auto-collapse logic dla UP/DOWN/BACK

**Kod:**
```kotlin
// Function signature update
private fun handleAplikacjeNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    channels: List<String>,
    // ... other params
    onToggleExpansion: (() -> Unit)? = null,  // ✅ Add
    isExpanded: Boolean = false                // ✅ Add
): Boolean {
    val currentChannel = channels.getOrNull(focusedRowIndex) ?: return false

    return when (event.key) {
        // UP from first sub-channel
        Key.DirectionUp -> {
            if (isExpanded && currentChannel == "Gry akcji") {
                onToggleExpansion?.invoke()
                GlobalScope.launch {
                    delay(50)
                    onChannelContentFocusChange(1, targetColIndex)  // Parent row
                    channelFocusRequesters[Pair(1, targetColIndex)]?.requestFocus()
                }
                return true
            }
            // ... standard UP logic
        }

        // DOWN from last sub-channel
        Key.DirectionDown -> {
            if (isExpanded && currentChannel == "Gry edukacyjne") {
                onToggleExpansion?.invoke()
                GlobalScope.launch {
                    delay(50)
                    onChannelContentFocusChange(2, targetColIndex)  // Next after parent
                    channelFocusRequesters[Pair(2, targetColIndex)]?.requestFocus()
                }
                return true
            }
            // ... standard DOWN logic
        }

        // BACK from any sub-channel
        Key.Back -> {
            if (isExpanded &&
                currentChannel in listOf("Gry akcji", "Gry sportowe", "Gry edukacyjne")) {
                onToggleExpansion?.invoke()
                GlobalScope.launch {
                    delay(50)
                    onChannelContentFocusChange(1, -1)  // Parent CategoryIcon
                    channelFocusRequesters[Pair(1, -1)]?.requestFocus()
                }
                return true
            }
            return false
        }

        else -> false
    }
}
```

#### Faza 6: Call site updates

- [ ] Znajdź wywołanie navigation handler
- [ ] Dodaj `onToggleExpansion = toggle[Category]Expansion`
- [ ] Dodaj `isExpanded = is[Category]Expanded`

**Kod:**
```kotlin
val navigationHandled = handleAplikacjeNavigation(
    event = event,
    focusedRowIndex = focusedRowIndex,
    focusedColIndex = focusedColIndex,
    channels = channels,
    // ... other args
    onToggleExpansion = toggleGamesExpansion,  // ✅ Add
    isExpanded = isGamesExpanded                // ✅ Add
)
```

#### Faza 7: Testing

- [ ] Test expansion: Press OK on parent channel
- [ ] Test collapse: Press BACK from sub-channel
- [ ] Test auto-collapse UP: Navigate up from first sub-channel
- [ ] Test auto-collapse DOWN: Navigate down from last sub-channel
- [ ] Test focus stability: Expand/collapse multiple times rapidly
- [ ] Test LEFT/RIGHT navigation: In all channels (parent + sub-channels)
- [ ] Test content scrolling: Scroll through 20+ items in LazyRow

---

## Pułapki i Anti-patterns

### ❌ Pułapka 1: Nested Components Approach

**Czym jest:**
Próba implementacji sub-kanałów jako zagnieżdżonych komponentów wewnątrz parent channel.

**Przykładowy (błędny) kod:**

```kotlin
// ❌ WRONG: Nested data structure
data class SubChannel(val name: String, val content: List<VodContent>)
data class ExpandableChannel(
    val name: String,
    val subChannels: List<SubChannel> = emptyList(),
    val isExpanded: Boolean = false
)

// ❌ WRONG: Triple keys for nested navigation
val focusRequester = Triple(channelIndex, subChannelIndex, contentIndex)

// ❌ WRONG: Special rendering for sub-channels
@Composable
fun SubChannelRow(...) {
    Row {
        Spacer(width = 80.dp)  // Indent sub-channel
        SubChannelIcon(...)     // Different icon type
        SubChannelContent(...)  // Different layout
    }
}
```

**Dlaczego to złe:**
- Łamie Channel Row Pattern (CategoryIcon + LazyRow content)
- Wymaga specjalnej logiki renderowania
- Złożona koordynacja fokusa między poziomami
- Trudne debugowanie (3 poziomy fokusa)
- Nie skaluje się (więcej poziomów = eksponencjalny wzrost złożoności)

**Konsekwencje:**
- 250+ linii niepotrzebnego kodu
- 4 nowe composables (SubChannelIcon, SubChannelRow, LeftSideMenu, etc.)
- Triple keys zamiast Pair keys
- Dodatkowe state trackery (`focusedSubChannelIndex`, `subChannelLazyListStates`)
- **WSZYSTKO USUNIĘTE W FAZIE 2**

---

### ❌ Pułapka 2: Race Conditions z remember(channels.size)

**Czym jest:**
Używanie `channels.size` jako klucza dla `remember()`, co powoduje rekreację FocusRequesterów.

**Przykładowy (błędny) kod:**

```kotlin
// ❌ WRONG: Key depends on channels.size
val channelFocusRequesters = remember(channels.size) {
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(channels.size) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester())
            put(Pair(rowIndex, 0), FocusRequester())
        }
    }
}
```

**Dlaczego to złe:**
- `channels.size` zmienia się: 5 → 8 → 5
- `remember(key)` rekalkuluje gdy `key != poprzedni_key`
- Stare FocusRequestery zostają "orphaned" (komponenty je referencują, ale nie istnieją)
- Nowe FocusRequestery nie są przypisane do istniejących komponentów

**Timeline problemu:**

```
T0: channels.size = 5
    remember(5) tworzy Map A {0..4}
    Components {0..4} → Map A ✅

T1: User presses OK (expand)
    channels.size = 8
    remember(8) tworzy Map B {0..7}  ← NEW MAP!
    Map A deleted by GC
    Components {0..4} → Map A (gone!) ❌
    Components {5..7} → Map B ✅ (nowe komponenty)

RESULT: Components 0-4 lost focus! Only 5-7 work.

T2: User presses BACK (collapse)
    channels.size = 5
    remember(5) tworzy Map C {0..4}  ← NEW MAP AGAIN!
    Map B deleted by GC
    All components → Map A/B (both gone!) ❌

RESULT: Crash - "FocusRequester not initialized"
```

**Symptomy:**
- Fokus "znika" po expansion
- Cannot navigate on channels 0-4 after expand
- Random crashes przy collapse
- Error: "FocusRequester is not initialized"

**Rozwiązanie:**

```kotlin
// ✅ CORRECT: No key, create once for MAX
val MAX_CHANNELS = 8
val channelFocusRequesters = remember {  // NO KEY!
    mutableMapOf<Pair<Int, Int>, FocusRequester>().apply {
        repeat(MAX_CHANNELS) { rowIndex ->
            put(Pair(rowIndex, -1), FocusRequester())
            put(Pair(rowIndex, 0), FocusRequester())
        }
    }
}
```

---

### ❌ Pułapka 3: Missing Conditional Assignment

**Czym jest:**
Przypisanie tego samego FocusRequestera do wszystkich items w LazyRow.

**Przykładowy (błędny) kod:**

```kotlin
items(rowContent.size) { colIndex ->
    // ❌ WRONG: All 100 items get same FocusRequester!
    val focusRequester = channelFocusRequesters[Pair(rowIndex, 0)]

    ContentCard(
        vodContent = vodContent,
        focusRequester = focusRequester  // Conflict!
    )
}
```

**Dlaczego to złe:**
- FocusRequester może być przypisany tylko do **jednego** komponentu
- Przypisanie do wielu komponentów = conflict
- Jetpack Compose nie wie który komponent ma fokus
- Random focus jumping, crashes

**Symptomy:**
- LEFT/RIGHT navigation nie działa
- Focus "skacze" losowo między kartami
- Brak program info overlay (bo focus nie jest poprawnie tracked)
- Warning w logcat: "FocusRequester used on multiple focusable items"

**Rozwiązanie:**

```kotlin
items(rowContent.size) { colIndex ->
    val lazyListState = lazyListStates[rowIndex] ?: LazyListState()

    // ✅ CORRECT: Only visible item gets real FocusRequester
    val focusRequester = if (colIndex == lazyListState.firstVisibleItemIndex) {
        channelFocusRequesters[Pair(rowIndex, 0)] ?: FocusRequester()
    } else {
        FocusRequester()  // Dummy for non-visible
    }

    ContentCard(
        focusRequester = focusRequester  // Unique per item!
    )
}
```

---

### ❌ Pułapka 4: Brak delay() w auto-collapse

**Czym jest:**
Próba requestFocus() przed zakończeniem recomposition po zmianie `isExpanded`.

**Przykładowy (błędny) kod:**

```kotlin
// ❌ WRONG: Immediate requestFocus without delay
Key.DirectionUp -> {
    if (isExpanded && currentChannel == "Sub1") {
        onToggleExpansion()  // Changes isExpanded
        // Channels list NOT YET updated!
        onChannelContentFocusChange(1, 0)
        channelFocusRequesters[Pair(1, 0)]?.requestFocus()  // ❌ Old indices!
        return true
    }
}
```

**Dlaczego to złe:**
- `onToggleExpansion()` zmienia `isExpanded = false`
- `remember(isExpanded)` **schedule** recomposition (nie natychmiast!)
- Recomposition trwa 16-50ms
- `requestFocus()` używa starych indeksów (przed recomposition)
- Focus trafia do nieistniejącego komponentu

**Symptomy:**
- Auto-collapse nie fokusuje parent channel
- Focus "znika" po UP/DOWN z sub-kanału
- Trzeba ręcznie nawigować żeby odzyskać fokus

**Rozwiązanie:**

```kotlin
// ✅ CORRECT: Delay to wait for recomposition
Key.DirectionUp -> {
    if (isExpanded && currentChannel == "Sub1") {
        onToggleExpansion()  // Changes isExpanded
        GlobalScope.launch {
            delay(50)  // ✅ Wait for recomposition (16-50ms)
            onChannelContentFocusChange(1, 0)
            channelFocusRequesters[Pair(1, 0)]?.requestFocus()  // ✅ New indices!
        }
        return true
    }
}
```

**Dlaczego 50ms?**
- Compose recomposition: 16ms (60 FPS frame)
- Safety margin: 2-3 frames = ~50ms
- Too short: Risk of using old indices
- Too long: Noticeable lag for user

---

### ❌ Pułapka 5: Niepoprawne indeksy po collapse

**Czym jest:**
Używanie hardcoded indeksów które się zmieniają po collapse.

**Przykładowy (błędny) kod:**

```kotlin
// Channels when expanded:
// 0: "A"
// 1: "Parent"
// 2: "Sub1"
// 3: "Sub2"
// 4: "Sub3"
// 5: "B"    ← Index 5 when expanded

// ❌ WRONG: Hardcoded index doesn't account for collapse
Key.DirectionDown -> {
    if (isExpanded && currentChannel == "Sub3") {
        onToggleExpansion()
        GlobalScope.launch {
            delay(50)
            onChannelContentFocusChange(5, 0)  // ❌ Wrong! Will focus wrong channel
        }
    }
}

// After collapse:
// 0: "A"
// 1: "Parent"
// 2: "B"    ← Now index 2, NOT 5!
```

**Rozwiązanie:**

```kotlin
// ✅ CORRECT: Calculate target index after collapse
Key.DirectionDown -> {
    if (isExpanded && currentChannel == "Sub3") {
        onToggleExpansion()
        GlobalScope.launch {
            delay(50)
            // After collapse: "B" will be at index 2
            // (0: "A", 1: "Parent", 2: "B")
            onChannelContentFocusChange(2, 0)  // ✅ Correct!
        }
    }
}
```

**Wskazówka**: Zawsze myśl o indeksach **PO COLLAPSE**, nie przed!

---

## Historia debugowania

### Problem 1: Focus Lost After Expansion

**Zgłoszenie użytkownika:**
> "ale chodzi mi o to ze fokus prawo lewo zniknął na wszystkich channelach w tej sekcji a na wideo jest działa"

**Symptomy:**
- Brak wizualnego fokusa na kanałach MOJE
- LEFT/RIGHT navigation nie działa
- Brak program info overlay
- WIDEO section działa poprawnie

**Proces diagnozy:**

1. **Task agent comparison**: Porównanie implementacji MOJE vs WIDEO
   ```
   Agent findings:
   - MOJE: remember(channels.size) ← PROBLEM!
   - WIDEO: remember (no key) ← WORKS!
   ```

2. **Race condition identified**:
   - FocusRequestery recreated on channels.size change
   - Old FocusRequesters orphaned
   - Components lost focus references

3. **Solution implemented**:
   - Added `MAX_MOJE_CHANNELS = 8`
   - Changed to `remember` without key
   - Applied to both `channelFocusRequesters` and `lazyListStates`

**Commit**: Part of `f0fb65a`

---

### Problem 2: LEFT/RIGHT Navigation Broken

**Zgłoszenie użytkownika:**
> (Po fix #1) LEFT/RIGHT navigation nadal nie działa na content items

**Symptomy:**
- Cannot scroll horizontally in channel rows
- Focus "stuck" on first item
- No program info overlay

**Proces diagnozy:**

1. **Agent analysis**: Porównanie MojeUnifiedChannelRow vs WIDEO LazyRow
   ```
   Agent findings:
   - MOJE: ALL items using Pair(rowIndex, 0) ← PROBLEM!
   - WIDEO: Conditional assignment based on firstVisibleItemIndex ← WORKS!
   ```

2. **Root cause**:
   - Incomplete refactoring from Triple→Pair
   - Didn't copy "fixed focus window" pattern from WIDEO
   - All LazyRow items had same FocusRequester

3. **Solution implemented**:
   - Added conditional assignment in PackageCard items (line 4205-4210)
   - Added conditional assignment in ContentCard items (line 4228-4233)
   - Pattern: Only `firstVisibleItemIndex` gets real FocusRequester

**User feedback**: "brawooo! zapiszmy tą wersję"

**Commit**: `f0fb65a` - "fix(moje): Naprawiono fokus LEFT/RIGHT w LazyRow content items"

---

### Lessons Learned

1. **Stable FocusRequester Pattern is CRITICAL**
   - Always create for MAX channels
   - Never use `channels.size` as remember() key
   - Same pattern for LazyListState

2. **Reference Working Implementation**
   - WIDEO section was working correctly
   - Copy patterns from working sections
   - Use Task agent to compare implementations

3. **Test Focus Scenarios Thoroughly**
   - Not just UP/DOWN (channel switching)
   - Also LEFT/RIGHT (content scrolling)
   - Rapid expand/collapse stress test

4. **Document Anti-Patterns**
   - What NOT to do is as important as what to do
   - Failed approaches = valuable knowledge

---

## Checklist testowy

### Expansion/Collapse

- [ ] **OK na parent channel** → Expand (5 → 8 channels)
- [ ] **OK na expanded parent** → Collapse (8 → 5 channels)
- [ ] **BACK z sub-kanału** → Collapse + fokus na parent CategoryIcon
- [ ] **Rapid expand/collapse** (10x) → Fokus nie ginie

### Auto-Collapse Navigation

- [ ] **UP z pierwszego sub-kanału** → Collapse + fokus na parent content
- [ ] **DOWN z ostatniego sub-kanału** → Collapse + fokus na następny kanał
- [ ] **UP/DOWN przez parent** (gdy collapsed) → Normalnie przeskakuje

### Focus Stability

- [ ] **Fokus widoczny** na CategoryIcon (aqua border)
- [ ] **Fokus widoczny** na content cards (white border + scale)
- [ ] **Fokus nie ginie** podczas expand/collapse
- [ ] **Brak crashy** "FocusRequester not initialized"

### LEFT/RIGHT Navigation

- [ ] **LEFT z CategoryIcon** → Nic (już na lewej krawędzi)
- [ ] **RIGHT z CategoryIcon** → Fokus na pierwszy content item
- [ ] **RIGHT w content** → Scroll do następnego itema
- [ ] **LEFT w content** → Scroll do poprzedniego itema
- [ ] **LEFT z pierwszego itema** → Fokus na CategoryIcon

### Content Scrolling

- [ ] **Scroll przez 20+ items** → FocusRequester prawidłowo reassigned
- [ ] **Scroll do końca** → Brak crash, focus na ostatnim
- [ ] **Scroll do początku** → Fokus na pierwszym

### Program Info Overlay

- [ ] **Fokus na content item** → Program info się pojawia
- [ ] **Program info shows correct data** (title, description, time)
- [ ] **LEFT/RIGHT w content** → Program info się aktualizuje

### Edge Cases

- [ ] **Expand → Navigation UP/DOWN** → Fokus na poprawnych kanałach
- [ ] **Navigation do sub-kanału** → LEFT/RIGHT działa prawidłowo
- [ ] **Collapse podczas fokusa na sub-kanale** → Fokus wraca do parent
- [ ] **Sub-kanały wyglądają identycznie** jak regularne kanały (same heights, same layouts)

---

## Referencje do kodu

### Główny plik implementacji

**Lokalizacja**: `/Users/uxellenceuxe/TV_componenty/app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt`

### Sekcje kodu

| Linie | Sekcja | Opis |
|-------|--------|------|
| **2409-2414** | Expansion state & MAX constant | `isNagraniaExpanded`, `MAX_MOJE_CHANNELS = 8` |
| **2418-2439** | Dynamic channel list | `remember(isNagraniaExpanded)` - 5 collapsed → 8 expanded |
| **2448-2464** | Grid content mapping | Mapowanie nazw kanałów na content |
| **2482-2491** | Stable FocusRequester map | `remember { }` bez key, `repeat(MAX_MOJE_CHANNELS)` |
| **2494-2500** | Stable LazyListState map | Same pattern jak FocusRequesters |
| **2545-2561** | Navigation handler call | Przekazanie `onToggleExpansion`, `isExpanded` |
| **4092-4144** | MojeChannelRowsLayout | Rendering wszystkich channel rows |
| **4146-4356** | MojeUnifiedChannelRow | Pojedynczy channel row (CategoryIcon + LazyRow) |
| **4205-4210** | Conditional FocusRequester (PackageCard) | `if (colIndex == firstVisibleItemIndex)` |
| **4228-4233** | Conditional FocusRequester (ContentCard) | Same pattern for content |
| **4509-4665** | handleMojeChannelsNavigation | Complete navigation logic + auto-collapse |
| **4539-4553** | Auto-collapse UP | Z pierwszego sub-kanału → parent |
| **4585-4599** | Auto-collapse DOWN | Z ostatniego sub-kanału → następny |

### Git History

| Commit | Faza | Opis |
|--------|------|------|
| `320ebfa` | Backup | Przed rollback (Triple keys + nested approach) |
| `e84430c` | Faza 2 | Usunięcie 250 linii wrong implementation |
| `896dff1` | Faza 3 | Dynamic channel list (5→8 channels) |
| `87e2f82` | Faza 4 | Przepisanie navigation handler (56% redukcja kodu) |
| `ff25f12` | Faza 5 | Auto-collapse logic (UP/DOWN at boundaries) |
| `f0fb65a` | Final fix | LEFT/RIGHT navigation in content (conditional assignment) |

### Backup File

**Lokalizacja**: `/Users/uxellenceuxe/TV_componenty/app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2_backup_before_triple_keys_20251105_144749.kt`

Backup zawiera:
- Nested SubChannel approach (failed)
- Triple keys pattern (failed)
- SubChannelIcon, SubChannelRow, LeftSideMenu composables (all deleted)

**Użycie**: Reference dla tego co NIE robić

---

## Kolejne kroki

### Gotowe do implementacji w:

1. **APLIKACJE section** - Expandable "Gry" category
   - Sub-channels: "Gry akcji", "Gry sportowe", "Gry edukacyjne"
   - Similar content structure (app cards)

2. **VOD section** - Expandable playlists
   - Sub-channels: Genre-based playlists
   - Content: VodContentCard

3. **History section** - Time-based grouping
   - Sub-channels: "Dzisiaj", "Wczoraj", "Ten tydzień"
   - Content: History items

### Po drugiej implementacji:

- **Create Skill** (`skills/expandable-channels/SKILL.md`)
- Automation dla scaffolding wzorca
- Interactive Q&A dla section-specific details

---

## Kontakt / Pytania

**Pattern Author**: Claude Code (AI assistant)
**Implementation Date**: 2025-11-05
**Production Status**: ✅ Live w MOJE section (NAGRANIA sub-channels)
**Git Commit**: `f0fb65a`

Jeśli masz pytania lub napotkasz problemy z implementacją tego wzorca:

1. Przeczytaj [Pułapki i Anti-patterns](#pułapki-i-anti-patterns)
2. Sprawdź [Checklist testowy](#checklist-testowy)
3. Porównaj swoją implementację z [Referencje do kodu](#referencje-do-kodu)
4. Zobacz [Historia debugowania](#historia-debugowania) - twój problem może być tam opisany

---

**Wersja dokumentu**: 1.0
**Ostatnia aktualizacja**: 2025-11-05
