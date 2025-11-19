# WIZUALNY DIAGRAM - App-Icons Rendering

## 1. TELEWIZJA - Prawidłowa Implementacja

```
┌─────────────────────────────────────────────────────────────────────┐
│  TELEWIZJA Section - "Moja lista kanałów" (app-icons type)         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  X: 0px            X: 80px                    X: 380px             │
│  ┌──────────────┐  ┌──────────────────────┐  ┌────────────────┐   │
│  │ CategoryIcon │  │   CategoryIcon Text  │  │ ChannelListCard│   │
│  │ (rendered by │  │   (WIDEO Style)      │  │  208x208px     │   │
│  │  global      │  │                      │  │                │   │
│  │  section)    │  │   showIcon = false   │  │  Logo 148x148  │   │
│  │              │  │   showBg = false     │  │  Number badge  │   │
│  │              │  │   Text-only!         │  │                │   │
│  │              │  │   max 2 lines        │  └────────────────┘   │
│  └──────────────┘  └──────────────────────┘  ┌────────────────┐   │
│      240x216px         240x216px             │ ChannelListCard│   │
│                                               │  208x208px     │   │
│     Focus: aqua border 6px                    │                │   │
│             (no background)                   │ Logo 148x148   │   │
│                                               │ Number badge   │   │
│                                               └────────────────┘   │
│                                               ... (spacing 12px)   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

TELEWIZJA Rendering Pipeline:
────────────────────────────
1. TelewizjaChannelRowsLayout (forEachIndexed channels)
   ↓
2. TelewizjaUnifiedChannelRow (channel="Moja lista kanałów", channelType="app-icons")
   ↓
3. LazyRow (contentPadding start=380px, spacing=12px)
   ├─ items(tvChannelLogos.size) → ChannelListCard
   │  ├─ Direct focus: colIndex == focusedColIndex
   │  └─ Each item has own FocusRequester
   └─ items(8) → Spacer (208x208px)
   ↓
4. CategoryIcon rendering: GLOBAL SECTION (linia 6189)
   ├─ NOT inside LazyRow
   ├─ Positioned at x=80px, y=0px
   ├─ showIcon = false (TEXT-ONLY)
   ├─ showBackgroundWhenFocused = false
   └─ Focus: aqua border 6px (no background)

Key Points:
──────────
✅ CategoryIcon OUTSIDE LazyRow → no ContentPadding interference
✅ showIcon = false → text-only rendering
✅ Direct focus model → colIndex = actual array index
✅ No scroll-window logic → always responsive
✅ 12px spacing between cards (tighter than MOJE's 20px)
```

---

## 2. MOJE - Obecna Implementacja (Z BŁĘDAMI)

```
┌─────────────────────────────────────────────────────────────────────┐
│  MOJE Section - "Moja lista kanałów" (app-icons type)              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  X: 80px                        X: 380px                           │
│  ┌──────────────────────────┐  ┌────────────────┐                 │
│  │  CategoryIcon            │  │ ChannelListCard│                 │
│  │  (LOCAL in MOJE fn)      │  │  208x208px     │                 │
│  │                          │  │                │                 │
│  │  ❌ showIcon = true!     │  │  Logo 148x148  │                 │
│  │     (shows placeholder)  │  │  Number badge  │                 │
│  │  ❌ showBg = false!      │  │                │                 │
│  │     (no background)      │  └────────────────┘                 │
│  │  ❌ Text: "MO"           │  ┌────────────────┐                 │
│  │     (first 2 letters)    │  │ ChannelListCard│                 │
│  │  ❌ No special style     │  │  208x208px     │                 │
│  │                          │  │                │                 │
│  └──────────────────────────┘  │ Logo 148x148   │                 │
│      240x216px                  │ Number badge   │                 │
│                                 └────────────────┘                 │
│     Focus: aqua border 6px      ... (spacing 12px)                │
│             (no background)                                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

MOJE Rendering Pipeline (BŁĘDNIE):
──────────────────────────────────
1. MojeChannelsScreen (forEachIndexed channels)
   ↓
2. MojeUnifiedChannelRow (channel="Moja lista kanałów")
   ├─ isAppIcons = (channel == "Moja lista kanałów") ✓ CORRECT
   │
   ├─ LazyRow (contentPadding start=380px, spacing=12px) ✓ CORRECT
   │  ├─ items(tvChannels.size) → ChannelListCard
   │  ├─ Direct focus: colIndex == focusedColIndex ✓ CORRECT
   │  └─ Each item has own FocusRequester ✓ CORRECT
   │
   └─ CategoryIcon rendering: LOCAL in MOJE function (linia 5518)
      ├─ isSubChannel = false (NOT in sub-channel list)
      ├─ showIcon = !isSubChannel = true ❌ BŁĄD!
      ├─ showBackgroundWhenFocused = isSubChannel = false ❌ BŁĄD!
      ├─ logoDrawableId = null (no mapping) ❌ BŁĄD!
      └─ Result: Shows "MO" placeholder, no background
         (Should be text-only with background on focus)

Problems:
────────
❌ showIcon = true → shows placeholder "MO" instead of channel name
❌ showBackgroundWhenFocused = false → no background on focus
❌ logoDrawableId not mapped → default behavior triggers placeholder
❌ Not consistent with TELEWIZJA style
❌ Doesn't match WIDEO style design
```

---

## 3. SIDE-BY-SIDE: Focus State Comparison

```
┌─────────────────────────────────────┬─────────────────────────────────────┐
│  TELEWIZJA (✅ CORRECT)             │  MOJE (❌ WRONG)                    │
├─────────────────────────────────────┼─────────────────────────────────────┤
│                                     │                                     │
│  UNFOCUSED:                         │  UNFOCUSED:                         │
│  ┌─────────────────────────────┐   │  ┌─────────────────────────────┐   │
│  │                             │   │  │                             │   │
│  │     Moja lista kanałów      │   │  │          MO                 │   │
│  │     (text wraps 2 lines)    │   │  │     (placeholder)           │   │
│  │                             │   │  │                             │   │
│  │  (NO background)            │   │  │  (NO background)            │   │
│  │                             │   │  │                             │   │
│  └─────────────────────────────┘   │  └─────────────────────────────┘   │
│       240x216px, no border          │       240x216px, no border          │
│                                     │                                     │
│  ─────────────────────────────────  │  ─────────────────────────────────  │
│                                     │                                     │
│  FOCUSED:                           │  FOCUSED:                           │
│  ┌─────────────────────────────┐   │  ┌─────────────────────────────┐   │
│  │ ┌───────────────────────┐   │   │  │ ┌───────────────────────┐   │   │
│  │ │                       │   │   │  │ │                       │   │   │
│  │ │  Moja lista kanałów   │   │   │  │ │        MO             │   │   │
│  │ │                       │   │   │  │ │                       │   │   │
│  │ │ (text wraps)          │   │   │  │ │ (placeholder)         │   │   │
│  │ │                       │   │   │  │ │                       │   │   │
│  │ └───────────────────────┘   │   │  │ └───────────────────────┘   │   │
│  │  (AQUA border 6px)          │   │  │  (AQUA border 6px)          │   │
│  │  (NO background)            │   │  │  (NO background)            │   │
│  └─────────────────────────────┘   │  └─────────────────────────────┘   │
│       240x216px                     │       240x216px                     │
│                                     │                                     │
│  showIcon: false ✓                  │  showIcon: true ❌                  │
│  showBg: false ✓                    │  showBg: false ❌                   │
│  Text: "Moja lista kanałów" ✓       │  Text: "MO" ❌                      │
│                                     │                                     │
└─────────────────────────────────────┴─────────────────────────────────────┘

Expected for app-icons (WIDEO Style):
────────────────────────────────────
  UNFOCUSED:                         FOCUSED:
  ┌─────────────────────────────┐   ┌─────────────────────────────┐
  │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │ ┌───────────────────────┐   │
  │ ░                           ░   │ │ ┌─────────────────────┐ │   │
  │ ░   Moja lista kanałów      ░   │ │ │                     │ │   │
  │ ░                           ░   │ │ │ Moja lista kanałów  │ │   │
  │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │ │ │                     │ │   │
  │                             │   │ │ └─────────────────────┘ │   │
  │ (Black bg 0.1 opacity)      │   │ │ (AQUA border 6px)       │   │
  │ (NO border)                 │   │ │ (Black bg 0.3 opacity)  │   │
  └─────────────────────────────┘   │ └───────────────────────┘   │
       240x216px                     │     240x216px               │
                                     │                             │
  showIcon: false ✓                  │  showIcon: false ✓          │
  showBg: false ✓                    │  showBg: true ✓             │
  Text: "Moja lista kanałów" ✓       │  Text: "Moja lista kanałów"✓
```

---

## 4. Code Flow Comparison

### TELEWIZJA (✅ Prawidłowo):

```
LazyRow(contentPadding = start=380px)
├─ items(tvChannelLogos) → ChannelListCard
│  │
│  ├─ isItemFocused = (rowIndex == focusedRowIndex && colIndex == focusedColIndex)
│  │  (DIRECT: 0→0, 1→1, 2→2, ...)
│  │
│  ├─ focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
│  │  (ALWAYS assigned, every item has one)
│  │
│  └─ ChannelListCard rendering
│
└─ CategoryIcon rendering (OUTSIDE LazyRow, global section)
   │
   ├─ showIcon = false (hardcoded for app-icons)
   ├─ showBackgroundWhenFocused = false (hardcoded for app-icons)
   ├─ isFocused = (rowIndex == focusedRowIndex && focusedColIndex == -1)
   │
   └─ CategoryIcon component with WIDEO style applied
```

### MOJE (❌ Z błędami):

```
LazyRow(contentPadding = start=380px)
├─ items(tvChannels) → ChannelListCard
│  │
│  ├─ isItemFocused = (rowIndex == focusedRowIndex && colIndex == focusedColIndex)
│  │  (DIRECT: 0→0, 1→1, 2→2, ...) ✓ CORRECT
│  │
│  ├─ focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)] ?: FocusRequester()
│  │  (ALWAYS assigned) ✓ CORRECT
│  │
│  └─ ChannelListCard rendering ✓ CORRECT
│
└─ CategoryIcon rendering (INSIDE MojeUnifiedChannelRow)
   │
   ├─ isSubChannel = (channel in listOf(...)) ← "Moja lista kanałów" NOT listed ❌
   │
   ├─ showIcon = !isSubChannel → true ❌
   │  (Should be false for app-icons)
   │
   ├─ showBackgroundWhenFocused = isSubChannel → false ❌
   │  (Should be true for app-icons)
   │
   ├─ logoDrawableId = when(channel) { ... } → null (no mapping) ❌
   │  (Missing "Moja lista kanałów" case)
   │
   └─ CategoryIcon component renders with WRONG parameters ❌
      (Shows placeholder "MO", no background on focus)
```

---

## 5. Fix Implementation Flow

```
Current State (BŁĘDY):
│
├─ Line 5536: isSubChannel = ["Skróty", "Pojedyncze nagrania", "SERIE", "ZAPLANOWANE"]
│  └─ "Moja lista kanałów" returns false (NOT in list)
│
├─ Line 5558: showIcon = !isSubChannel = true
│  └─ Shows placeholder "MO" ❌
│
├─ Line 5559: showBackgroundWhenFocused = isSubChannel = false
│  └─ No background on focus ❌
│
├─ Line 5525-5532: logoDrawableId when { ... else → null }
│  └─ No mapping for "Moja lista kanałów" ❌
│
└─ Result: Looks like old channel, not like WIDEO style app-icons ❌

↓ ↓ ↓

After Fixes:
│
├─ ZMIANA #1 (Line 5536): Add "Moja lista kanałów" to isSubChannel
│  └─ "Moja lista kanałów" returns true ✓
│
├─ ZMIANA #2 (Line 5525-5532): Add mapping for "Moja lista kanałów" → null
│  └─ Explicit mapping for app-icons ✓
│
├─ Auto-calculated:
│  ├─ showIcon = !isSubChannel = false ✓ (TEXT-ONLY)
│  └─ showBackgroundWhenFocused = isSubChannel = true ✓ (BACKGROUND ON FOCUS)
│
└─ Result: Looks exactly like TELEWIZJA app-icons ✓
   ├─ Text-only rendering (no placeholder)
   ├─ Background on focus (rgba(0,0,0,0.3))
   ├─ Aqua border on focus (#5AECD3)
   └─ Consistent visual style ✓
```

---

## 6. Focus Navigation Paths

### TELEWIZJA - app-icons:

```
         ▲ UP
         │
    [CategoryIcon]  ◄─── focusedColIndex = -1
    (left column)       
         │
    [Card-0]  ◄─────────► focusedColIndex = 0
        │
    [Card-1]  ◄─────────► focusedColIndex = 1
        │
    [Card-2]  ◄─────────► focusedColIndex = 2
        │
        ▼ DOWN

Focus Model:
────────────
- Rows: focusedRowIndex (which channel row)
- Cols: focusedColIndex
  - (-1) = CategoryIcon
  - (0-∞) = direct index in tvChannelLogos list

LEFT: focusedColIndex -= 1 (until -1, then stop at CategoryIcon)
RIGHT: focusedColIndex += 1 (from -1 to 0, then scroll content)
UP/DOWN: focusedRowIndex ± 1 (change channels, can preserve -1 or go to 0)
```

### MOJE - app-icons (Should be identical):

```
Same structure as TELEWIZJA, but currently CategoryIcon looks wrong ❌
After fix: Will be identical ✓
```

---

## 7. Component Hierarchy

### TELEWIZJA:

```
TopMenuScreen2
├─ TelewizjaChannelRowsLayout
│  ├─ channels.forEachIndexed { rowIndex, channelName }
│  │
│  ├─ Box(offset by calculateTelewizjaChannelYPosition)
│  │  │
│  │  └─ TelewizjaUnifiedChannelRow
│  │     │
│  │     └─ when (channelType) {
│  │        │
│  │        ├─ "app-icons" →
│  │        │  │
│  │        │  ├─ if (tvChannelLogos.isEmpty())
│  │        │  │  └─ Box(placeholder "Brak kanałów")
│  │        │  │
│  │        │  └─ else
│  │        │     └─ LazyRow(contentPadding = start:380px, spacing:12px)
│  │        │        └─ items(tvChannelLogos)
│  │        │           └─ ChannelListCard
│  │        │
│  │        └─ ... (other types)
│  │
│  └─ (Global CategoryIcon rendering elsewhere - line 6189)
│     └─ CategoryIcon(showIcon=false, showBg=false)
│
└─ (CategoryIcon is OUTSIDE the row, positioned absolutely)
```

### MOJE:

```
TopMenuScreen2
├─ MojeChannelsScreen
│  ├─ channels.forEachIndexed { rowIndex, channelName }
│  │
│  ├─ Box(offset by calculateMojeChannelYPosition)
│  │  │
│  │  └─ MojeUnifiedChannelRow
│  │     │
│  │     ├─ if (channel.startsWith("[HEADER")) → StorageCounterHeader
│  │     │
│  │     ├─ else if (channel == "Skróty v2 Moje") → Shortcuts v2
│  │     │
│  │     ├─ else if (channel == "Skróty") → MojeShortcutCardV4
│  │     │
│  │     ├─ else if (isAppIcons) → LazyRow(contentPadding=start:380px, spacing:12px)
│  │     │  │                        (IDENTICAL to TELEWIZJA logic ✓)
│  │     │  └─ items(tvChannels)
│  │     │     └─ ChannelListCard
│  │     │
│  │     ├─ else → LazyRow (other types)
│  │     │
│  │     └─ CategoryIcon rendering (INSIDE MojeUnifiedChannelRow) ← DIFFERS FROM TELEWIZJA
│  │        ├─ isSubChannel = false ← "Moja lista kanałów" NOT in list ❌
│  │        ├─ showIcon = true ❌
│  │        ├─ showBg = false ❌
│  │        └─ Result: wrong appearance ❌
│  │
│  └─ (CategoryIcon is INSIDE the row)
│
└─ (Different architecture from TELEWIZJA)
```

**Key Difference**: TELEWIZJA renders CategoryIcon globally (outside rows), MOJE renders it locally (inside MojeUnifiedChannelRow). For app-icons specifically, both should look the same, but MOJE's local rendering currently has wrong parameters.

---

## 8. Summary Table

| Aspect | TELEWIZJA | MOJE (Before) | MOJE (After) |
|--------|-----------|---------------|-------------|
| CategoryIcon Location | Global (line 6189) | Local (line 5518) | Local (line 5518) |
| showIcon | false (hardcoded) | !isSubChannel = true ❌ | !isSubChannel = false ✓ |
| showBackgroundWhenFocused | false (hardcoded) | isSubChannel = false ❌ | isSubChannel = true ✓ |
| logoDrawableId | N/A | null (no mapping) ❌ | null (mapped) ✓ |
| Visual Result | Text + border | Placeholder + border ❌ | Text + border ✓ |
| isSubChannel value | N/A | false ❌ | true ✓ |
| Consistency | Yes ✓ | No ❌ | Yes ✓ |

---

**Diagram koniec**
