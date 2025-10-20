# Channel System Architecture

**Version**: 1.0
**Date**: 2025-10-14
**Location**: `TopMenuScreen2.kt`

---

## Overview

The TV application uses a modular channel system with **8 distinct channel types** that can be reused across 6 sections (MOJE, START, APLIKACJE, ODKRYWAJ, TELEWIZJA, VOD). Each channel type has specific dimensions, expansion behaviors, and positioning rules.

### 3-Layer Architecture

```
┌─────────────────────────────────────────────────────────┐
│  LAYER 3: Positioning System                            │
│  - calculateXXXChannelYPosition() functions             │
│  - 60+ constants (FIXED_FOCUS_Y, ROW_HEIGHT, etc.)      │
│  - Dynamic spacing calculations                         │
└─────────────────────────────────────────────────────────┘
            ↓
┌─────────────────────────────────────────────────────────┐
│  LAYER 2: Channel Row Components                        │
│  - UnifiedChannelRow functions                          │
│  - LazyRow configuration                                │
│  - CategoryIcon positioning                             │
│  - Animation controls                                   │
└─────────────────────────────────────────────────────────┘
            ↓
┌─────────────────────────────────────────────────────────┐
│  LAYER 1: Card Components                               │
│  - ContentCard, AppIconCard, Top10Card, etc.           │
│  - Visual styling and focus states                      │
│  - Dimensions and proportions                           │
└─────────────────────────────────────────────────────────┘
```

---

## Channel Types Specification

### 1. **horizontal** - ContentCard

**Visual**: Standard 16:9 thumbnails arranged horizontally
**Component**: `ContentCard`
**Used in**: MOJE, START, APLIKACJE, ODKRYWAJ

#### Dimensions
```kotlin
Container: 368x208px (sx/sy scaled)
Thumbnail: 368x208px (fills container)
Spacing: 20px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐    │
│        │ 368x │  │ 368x │  │ 368x │  │ 368x │    │
│        │ 208  │  │ 208  │  │ 208  │  │ 208  │    │
│        └──────┘  └──────┘  └──────┘  └──────┘    │
│        X=380     (20px spacing between)            │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 256px = CategoryIcon (216px) + spacing (40px)
- **Expanded**: 546px = CategoryIcon (216px) + miniatures (290px) + spacing (40px)

#### Expansion Behavior
- ✅ **Expands on focus**: Miniatures slide down 290px when focused
- ✅ **Slide-down animation**: 350ms EaseInOutCubic animation
- ✅ **Details overlay**: Appears at Y=290px after animation

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(20))
)
```

#### CategoryIcon
- **Position**: Fixed at X=80px, outside LazyRow
- **Size**: 210x216px with rounded corners
- **Focus**: Separate FocusRequester at colIndex=-1

---

### 2. **vertical** - ContentCard (Portrait)

**Visual**: Portrait-oriented thumbnails (3:4 aspect ratio)
**Component**: `ContentCard` (portrait variant)
**Used in**: MOJE, ODKRYWAJ, VOD

#### Dimensions
```kotlin
Container: 288x432px (sx/sy scaled)
Thumbnail: 288x432px (3:4 aspect ratio)
Spacing: 20px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌───┐   ┌───┐   ┌───┐   ┌───┐             │
│        │288│   │288│   │288│   │288│             │
│        │ x │   │ x │   │ x │   │ x │             │
│        │432│   │432│   │432│   │432│             │
│        └───┘   └───┘   └───┘   └───┘             │
│        X=380  (20px spacing between)               │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 346px = CategoryIcon (216px) + content height adjustment + spacing (130px)
- **Expanded**: 636px = CategoryIcon (216px) + miniatures (290px) + spacing (130px)

#### Expansion Behavior
- ✅ **Expands on focus**: Miniatures slide down 290px when focused
- ✅ **Slide-down animation**: 350ms EaseInOutCubic animation
- ⚠️ **Different spacing**: Uses 130px spacing instead of 40px (taller cards need more space)

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(20))
)
```

---

### 3. **app-icons** - AppIconCard

**Visual**: Android TV app icons arranged horizontally
**Component**: `AppIconCard`
**Used in**: APLIKACJE tab only ("Ostatnio używane", "Aplikacje")

#### Dimensions
```kotlin
Container: 320x190px (sx/sy scaled)  // Optimized for minimal padding
Icon: 300x170px (fills most of container)
Spacing: 12px horizontal gap  // Tighter than other channels
Internal padding: 10px on each side
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐           │
│        │ 320 │ │ 320 │ │ 320 │ │ 320 │           │
│        │  x  │ │  x  │ │  x  │ │  x  │           │
│        │ 190 │ │ 190 │ │ 190 │ │ 190 │           │
│        └─────┘ └─────┘ └─────┘ └─────┘           │
│        X=380  (12px spacing - tighter)             │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 256px = CategoryIcon (216px) + spacing (40px)
- **Expanded**: 256px = **NO EXPANSION** (app icons don't expand)

#### Expansion Behavior
- ❌ **NO expansion**: App icons remain at fixed height
- ❌ **NO slide-down animation**: Disabled via `if (!isAppIcons && isMiniaturesOnScreen)`
- ❌ **NO details overlay**: App icons don't show miniature details

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(12))  // Note: tighter spacing
)
```

#### CategoryIcon
- **Position**: Fixed at X=80px, outside LazyRow
- **Logo mapping**: "Ostatnio używane" → R.drawable.appli, "Aplikacje" → R.drawable.appli

#### Code Reference
- Component: `TopMenuScreen2.kt:7092`
- Animation condition: `TopMenuScreen2.kt:4509`
- Spacing logic: `TopMenuScreen2.kt:4523`

---

### 4. **top10** - Top10Card

**Visual**: Oversized thumbnails with ranking numbers (1-10)
**Component**: `Top10Card`
**Used in**: ODKRYWAJ section only

#### Dimensions
```kotlin
Container: 432x288px (sx/sy scaled)  // 1.5x scale of horizontal
Thumbnail: 432x288px
Ranking number: Large overlay (top-left)
Spacing: 20px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌────────┐  ┌────────┐  ┌────────┐        │
│        │  1     │  │  2     │  │  3     │        │
│        │  432x  │  │  432x  │  │  432x  │        │
│        │  288   │  │  288   │  │  288   │        │
│        └────────┘  └────────┘  └────────┘        │
│        X=380      (20px spacing between)           │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 406px = CategoryIcon (216px) + oversized card height + spacing
- **Expanded**: 696px = CategoryIcon (216px) + miniatures (290px) + card height + spacing

#### Expansion Behavior
- ✅ **Expands on focus**: Miniatures slide down 290px when focused
- ✅ **Slide-down animation**: 350ms EaseInOutCubic animation
- ⚠️ **Special styling**: Ranking number overlay with gradient background

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(20))
)
```

---

### 5. **slider-max** - SliderCard

**Visual**: Full-width hero slider with large thumbnails
**Component**: `SliderCard`
**Used in**: ODKRYWAJ section only (typically first row)

#### Dimensions
```kotlin
Container: Full viewport width (1920px)
Thumbnail: ~1200x675px (large hero images)
Spacing: 40px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  ┌────────────────────────────────────────────┐   │
│  │                                             │   │
│  │         Hero Slider Card                    │   │
│  │         ~1200x675px                         │   │
│  │                                             │   │
│  └────────────────────────────────────────────┘   │
│                                                     │
│  (Auto-scrolling carousel with dot indicators)     │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 782px = Full hero height + spacing
- **Expanded**: 782px = **NO EXPANSION** (already maximum size)

#### Expansion Behavior
- ❌ **NO expansion**: Hero slider already takes maximum space
- ❌ **NO slide-down animation**: Static positioning
- ✅ **Auto-scrolling**: 5-second interval carousel
- ✅ **Dot indicators**: Shows current slide position

#### Special Features
- **Auto-play**: Automatic carousel rotation
- **Manual navigation**: LEFT/RIGHT to change slides
- **Focus state**: Entire card focusable as single unit

---

### 6. **collection-slider** - CollectionCard

**Visual**: Medium-sized collection showcases (e.g., "New on Netflix")
**Component**: `CollectionCard`
**Used in**: ODKRYWAJ section only

#### Dimensions
```kotlin
Container: 832x468px (sx/sy scaled)
Thumbnail: 832x468px (collection banner)
Spacing: 30px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌───────────────┐  ┌───────────────┐       │
│        │               │  │               │       │
│        │   832x468     │  │   832x468     │       │
│        │   Collection  │  │   Collection  │       │
│        │               │  │               │       │
│        └───────────────┘  └───────────────┘       │
│        X=380            (30px spacing)             │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 544px = CategoryIcon (216px) + collection height + spacing
- **Expanded**: 544px = **NO EXPANSION** (collections don't expand)

#### Expansion Behavior
- ❌ **NO expansion**: Collection cards remain at fixed size
- ❌ **NO slide-down animation**: Static positioning
- ✅ **Click action**: Navigate to collection detail page

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(30))  // Wider spacing
)
```

---

### 7. **shortcuts** - ShortcutCard

**Visual**: Large action buttons with icons and labels
**Component**: `ShortcutCard`
**Used in**: APLIKACJE section ("Skróty")

#### Dimensions
```kotlin
Container: 210x279px (sx/sy scaled)
Icon: 120x120px centered
Label: Below icon, centered
Spacing: 20px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐        │
│        │     │  │     │  │     │  │     │        │
│        │ 🎬  │  │ 📺  │  │ 🔍  │  │ ⚙️  │        │
│        │     │  │     │  │     │  │     │        │
│        │Label│  │Label│  │Label│  │Label│        │
│        └─────┘  └─────┘  └─────┘  └─────┘        │
│        X=380   (20px spacing between)              │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 346px = CategoryIcon (216px) + shortcut height + spacing (130px)
- **Expanded**: 636px = CategoryIcon (216px) + miniatures (290px) + spacing (130px)

#### Expansion Behavior
- ✅ **Expands on focus**: Miniatures slide down 290px when focused
- ✅ **Slide-down animation**: 350ms EaseInOutCubic animation
- ⚠️ **Note**: Uses vertical-style spacing (130px) due to taller cards

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(20))
)
```

---

### 8. **epg-channels** - EpgChannelCard

**Visual**: TV channel cards with logos and current program info
**Component**: `EpgChannelCard`
**Used in**: TELEWIZJA section only

#### Dimensions
```kotlin
Container: 368x208px (sx/sy scaled)
Logo: 120x120px centered
Program info: Below logo, text overlay
Spacing: 20px horizontal gap
```

#### Layout
```
┌────────────────────────────────────────────────────┐
│  CategoryIcon (fixed at X=80px)                    │
│                                                     │
│        ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐    │
│        │ LOGO │  │ LOGO │  │ LOGO │  │ LOGO │    │
│        │ 📺   │  │ 📺   │  │ 📺   │  │ 📺   │    │
│        │ Now  │  │ Now  │  │ Now  │  │ Now  │    │
│        └──────┘  └──────┘  └──────┘  └──────┘    │
│        X=380    (20px spacing between)             │
└────────────────────────────────────────────────────┘
```

#### Row Heights
- **Normal**: 256px = CategoryIcon (216px) + spacing (40px)
- **Expanded**: 546px = CategoryIcon (216px) + miniatures (290px) + spacing (40px)

#### Expansion Behavior
- ✅ **Expands on focus**: Miniatures slide down 290px when focused
- ✅ **Slide-down animation**: 350ms EaseInOutCubic animation
- ✅ **Real-time updates**: Current program info updates automatically

#### LazyRow Configuration
```kotlin
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20)),
    horizontalArrangement = Arrangement.spacedBy(sx(20))
)
```

#### Special Features
- **Live data**: Integration with EPG XML-TV parser
- **Program progress**: Visual indicator of current program progress
- **Channel logos**: Loaded from `https://epg.ovh/logo/{channelId}.png`

---

## Positioning System Constants

### Core Concept

Each section has positioning constants that define:
1. **FIXED_FOCUS_Y**: Absolute Y position where focused channel appears (340px or 170px)
2. **NORMAL_ROW_HEIGHT**: Collapsed height of channel row
3. **EXPANDED_ROW_HEIGHT**: Expanded height when focused (for channels that expand)
4. **CONTENT_FOCUS_EXTRA_SPACING**: Additional spacing above focused row when content (not CategoryIcon) is focused

### MOJE Section
```kotlin
private const val MOJE_FIXED_FOCUS_Y = 340
private const val MOJE_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val MOJE_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
private const val MOJE_VERTICAL_NORMAL_ROW_HEIGHT = 346
private const val MOJE_VERTICAL_EXPANDED_ROW_HEIGHT = 636
private const val MOJE_CONTENT_FOCUS_EXTRA_SPACING = 100
```

**Channel Types**: horizontal, vertical

### START Section
```kotlin
private const val START_FIXED_FOCUS_Y = 340
private const val START_NORMAL_ROW_HEIGHT = 256
private const val START_EXPANDED_ROW_HEIGHT = 546
private const val START_CONTENT_FOCUS_EXTRA_SPACING = 100
```

**Channel Types**: horizontal

### APLIKACJE Section
```kotlin
private const val APLIKACJE_FIXED_FOCUS_Y = 340
private const val APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val APLIKACJE_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
private const val APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT = 346
private const val APLIKACJE_SHORTCUTS_EXPANDED_ROW_HEIGHT = 636
private const val APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT = 256
private const val APLIKACJE_APP_ICONS_EXPANDED_ROW_HEIGHT = 256  // NO expansion
private const val APLIKACJE_CONTENT_FOCUS_EXTRA_SPACING = 100
```

**Channel Types**: horizontal, shortcuts, app-icons

### ODKRYWAJ Section
```kotlin
private const val ODKRYWAJ_FIXED_FOCUS_Y = 170  // Different! (lower)
private const val ODKRYWAJ_SLIDER_MAX_NORMAL_ROW_HEIGHT = 782
private const val ODKRYWAJ_TOP10_NORMAL_ROW_HEIGHT = 406
private const val ODKRYWAJ_TOP10_EXPANDED_ROW_HEIGHT = 696
private const val ODKRYWAJ_VERTICAL_NORMAL_ROW_HEIGHT = 320
private const val ODKRYWAJ_VERTICAL_VOD_NORMAL_ROW_HEIGHT = 346
private const val ODKRYWAJ_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val ODKRYWAJ_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
private const val ODKRYWAJ_COLLECTION_SLIDER_NORMAL_ROW_HEIGHT = 544
```

**Channel Types**: slider-max, top10, vertical, horizontal, collection-slider

⚠️ **Note**: ODKRYWAJ has FIXED_FOCUS_Y = 170px (not 340px) to accommodate larger slider-max at top

### TELEWIZJA Section
```kotlin
// Uses GlobalFocusManager instead of calculateYPosition
// Channel rows positioned manually
```

**Channel Types**: epg-channels

### VOD Section
```kotlin
private const val VOD_FIXED_FOCUS_Y = 340
private const val VOD_VERTICAL_NORMAL_ROW_HEIGHT = 346
private const val VOD_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
```

**Channel Types**: vertical, horizontal

---

## Positioning Calculation Pattern

All sections (except TELEWIZJA) follow this calculation pattern:

```kotlin
private fun calculateXXXChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    currentRow: Int,
    sy: (Int) -> Dp
): Dp {
    return when {
        // Focused row - positioned at FIXED_FOCUS_Y
        rowIndex == focusedRowIndex -> {
            sy(focusedChannelRelativeY)  // Calculated to appear at FIXED_FOCUS_Y
        }

        // Rows above focused - pushed up
        rowIndex < focusedRowIndex -> {
            val extraSpacing = if (focusedColIndex >= 0) CONTENT_FOCUS_EXTRA_SPACING else 0
            sy(focusedChannelRelativeY -
               (focusedRowIndex - rowIndex) * NORMAL_ROW_HEIGHT -
               extraSpacing)
        }

        // Rows below focused - pushed down
        rowIndex > focusedRowIndex -> {
            val focusedChannelExpansion = if (focusedColIndex >= 0) {
                EXPANDED_ROW_HEIGHT
            } else {
                NORMAL_ROW_HEIGHT
            }
            sy(focusedChannelRelativeY +
               focusedChannelExpansion +
               (rowIndex - focusedRowIndex - 1) * NORMAL_ROW_HEIGHT)
        }

        else -> sy(rowIndex * NORMAL_ROW_HEIGHT)
    }
}
```

### Key Logic

1. **Focused row**: Always positioned at `FIXED_FOCUS_Y` (absolute Y coordinate)
2. **Rows above**: Pushed up by `NORMAL_ROW_HEIGHT` per row
3. **Rows below**: Pushed down by focused row's expansion (`EXPANDED_ROW_HEIGHT`)
4. **Extra spacing**: Added above focused row when content (not CategoryIcon) is focused

---

## CategoryIcon Patterns

### Fixed Position Pattern (Most channels)
```kotlin
// CategoryIcon positioned OUTSIDE LazyRow
Box(modifier = Modifier.offset(x = sx(80), y = sy(0))) {
    CategoryIcon(
        text = channel,
        isFocused = categoryIsFocused,
        focusRequester = categoryFocusRequester,
        logoDrawableId = categoryLogo
    )
}

// LazyRow starts at X=380
LazyRow(
    contentPadding = PaddingValues(start = sx(380), end = sx(20))
) {
    // Content items...
}
```

**Used by**: horizontal, vertical, app-icons, top10, collection-slider, shortcuts, epg-channels

### Scrollable Pattern (Historical - deprecated)
```kotlin
// CategoryIcon INSIDE LazyRow (old pattern, not recommended)
LazyRow(
    contentPadding = PaddingValues(start = sx(80), end = sx(20))
) {
    item {
        CategoryIcon(...)
    }
    items(content) { ... }
}
```

⚠️ **Deprecated**: This pattern caused scrolling/positioning inconsistencies

---

## Animation System

### Slide-Down Animation (Miniatures)

Most channels use this animation when focused:

```kotlin
val miniaturesYOffset by animateDpAsState(
    targetValue = if (!isAppIcons && isMiniaturesOnScreen) sy(290) else sy(0),
    animationSpec = tween(
        durationMillis = 350,
        easing = androidx.compose.animation.core.EaseInOutCubic
    ),
    label = "miniatures_y_offset_$rowIndex"
)

// Applied to miniatures container
Box(
    modifier = Modifier
        .offset(y = miniaturesYOffset)
) {
    // Miniature cards with details...
}
```

### Animation Rules by Channel Type

| Channel Type      | Slide-Down? | Duration | Notes                          |
|-------------------|-------------|----------|--------------------------------|
| horizontal        | ✅ Yes      | 350ms    | Standard miniatures slide      |
| vertical          | ✅ Yes      | 350ms    | Standard miniatures slide      |
| app-icons         | ❌ No       | N/A      | Explicitly disabled            |
| top10             | ✅ Yes      | 350ms    | Standard miniatures slide      |
| slider-max        | ❌ No       | N/A      | No expansion                   |
| collection-slider | ❌ No       | N/A      | No expansion                   |
| shortcuts         | ✅ Yes      | 350ms    | Standard miniatures slide      |
| epg-channels      | ✅ Yes      | 350ms    | Standard miniatures slide      |

### Details Overlay Timing

Details appear **after** slide-down animation completes:

```kotlin
LaunchedEffect(isMiniaturesOnScreen) {
    if (isMiniaturesOnScreen) {
        delay(350)  // Wait for animation to complete
        showDetails = true
    } else {
        showDetails = false
    }
}
```

---

## Quick Reference Tables

### Dimensions by Channel Type

| Type              | Width × Height | Aspect Ratio | Internal Padding |
|-------------------|----------------|--------------|------------------|
| horizontal        | 368 × 208      | 16:9         | 0px              |
| vertical          | 288 × 432      | 3:4          | 0px              |
| app-icons         | 320 × 190      | ~16:9        | 10px each side   |
| top10             | 432 × 288      | 3:2          | 0px              |
| slider-max        | ~1200 × 675    | 16:9         | 0px              |
| collection-slider | 832 × 468      | 16:9         | 0px              |
| shortcuts         | 210 × 279      | 3:4          | 0px              |
| epg-channels      | 368 × 208      | 16:9         | 0px              |

### Spacing by Channel Type

| Type              | LazyRow Gap | ContentPadding Start | CategoryIcon X |
|-------------------|-------------|----------------------|----------------|
| horizontal        | 20px        | 380px                | 80px           |
| vertical          | 20px        | 380px                | 80px           |
| app-icons         | 12px        | 380px                | 80px           |
| top10             | 20px        | 380px                | 80px           |
| slider-max        | 40px        | Varies               | N/A (no icon)  |
| collection-slider | 30px        | 380px                | 80px           |
| shortcuts         | 20px        | 380px                | 80px           |
| epg-channels      | 20px        | 380px                | 80px           |

### Expansion by Channel Type

| Type              | Normal Height | Expanded Height | Expansion | Animation |
|-------------------|---------------|-----------------|-----------|-----------|
| horizontal        | 256px         | 546px           | +290px    | ✅ Yes    |
| vertical          | 346px         | 636px           | +290px    | ✅ Yes    |
| app-icons         | 256px         | 256px           | None      | ❌ No     |
| top10             | 406px         | 696px           | +290px    | ✅ Yes    |
| slider-max        | 782px         | 782px           | None      | ❌ No     |
| collection-slider | 544px         | 544px           | None      | ❌ No     |
| shortcuts         | 346px         | 636px           | +290px    | ✅ Yes    |
| epg-channels      | 256px         | 546px           | +290px    | ✅ Yes    |

---

## Usage by Section

### MOJE Section
- **horizontal**: Polecane, Wznów oglądanie, Ostatnio oglądane
- **vertical**: (Optional) Portrait-oriented content

### START Section
- **horizontal**: All channels (Teraz popularny, Nowości, etc.)

### APLIKACJE Section
- **horizontal**: Platformy VOD (Netflix, Disney+, etc.)
- **shortcuts**: Skróty (Quick actions)
- **app-icons**: Ostatnio używane, Aplikacje

### ODKRYWAJ Section
- **slider-max**: Hero slider (typically first row)
- **top10**: Top 10 rankings
- **horizontal**: Standard content rows
- **vertical**: Portrait content
- **collection-slider**: Collections (e.g., "New on Netflix")

### TELEWIZJA Section
- **epg-channels**: TV channel listings with EPG data

### VOD Section
- **vertical**: Primary VOD content (movies, series)
- **horizontal**: (Optional) Featured content

---

## Code Locations Reference

| Component/Function                     | Location (TopMenuScreen2.kt) |
|----------------------------------------|------------------------------|
| ContentCard                            | Line ~6850                   |
| AppIconCard                            | Line ~7092                   |
| Top10Card                              | Line ~7200                   |
| SliderCard                             | Line ~7350                   |
| CollectionCard                         | Line ~7500                   |
| ShortcutCard                           | Line ~7650                   |
| EpgChannelCard                         | Line ~7800                   |
| calculateMojeChannelYPosition          | Line ~930                    |
| calculateStartChannelYPosition         | Line ~2194                   |
| calculateAplikacjeChannelYPosition     | Line ~3377                   |
| calculateOdkrywajChannelYPosition      | Line ~4900                   |
| calculateVodChannelYPosition           | Line ~6200                   |
| UnifiedChannelRow (APLIKACJE variant)  | Line ~4450                   |
| Animation condition (app-icons)        | Line ~4509                   |
| Spacing logic (app-icons)              | Line ~4523                   |
| CategoryIcon fixed positioning         | Line ~4657                   |

---

## Best Practices

### When to Use Each Channel Type

1. **horizontal**: Default choice for 16:9 content (movies, series, etc.)
2. **vertical**: Portrait content (movie posters, portrait photos)
3. **app-icons**: App launchers, recently used apps (APLIKACJE only)
4. **top10**: Ranked content (trending, top charts)
5. **slider-max**: Hero banners, featured promotions
6. **collection-slider**: Large collections, platform showcases
7. **shortcuts**: Action buttons, quick access menu items
8. **epg-channels**: TV channels with live program data

### Common Pitfalls

1. ❌ **Don't scroll CategoryIcon**: Always position it outside LazyRow at fixed X=80px
2. ❌ **Don't mix spacing values**: app-icons use 12px, most others use 20px
3. ❌ **Don't forget animation conditions**: app-icons need explicit `!isAppIcons` check
4. ❌ **Don't ignore FIXED_FOCUS_Y**: ODKRYWAJ uses 170px, others use 340px
5. ❌ **Don't expand non-expandable types**: slider-max, collection-slider, app-icons don't expand

### Testing Checklist

- [ ] CategoryIcon stays at X=80px when scrolling
- [ ] First content item starts at X=380px
- [ ] Correct spacing between items
- [ ] Expansion animation works (if applicable)
- [ ] Details overlay appears after animation (if applicable)
- [ ] Focus navigation works (LEFT/RIGHT within row, UP/DOWN between rows)
- [ ] Row positioning matches FIXED_FOCUS_Y when focused
- [ ] Rows above/below move correctly when focus changes

---

## Version History

- **v1.0** (2025-10-14): Initial comprehensive documentation
  - Documented all 8 channel types
  - Complete positioning system constants
  - Animation rules and patterns
  - CategoryIcon positioning patterns
  - Usage examples and best practices
