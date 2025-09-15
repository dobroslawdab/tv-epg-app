# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android TV EPG (Electronic Program Guide) application written in Kotlin using Jetpack Compose. It displays a TV program guide with automatic positioning to the current time.

## Technical Architecture

The application follows modern Android architecture patterns with Jetpack Compose for TV:

### Core Components
- `EpgScreen.kt` - Main screen with program grid
- `EpgModels.kt` - Data models (EpgProgram, EpgChannel, EpgGuide)  
- `XmlTvParser.kt` - EPG data parser for XML-TV format
- `TimeUtils.kt` - Time formatting utilities
- **`Version001Screen.kt`** - Channel row interface with enhanced UX (v0.06)
- **`SliderScreen.kt`** - Statistics dashboard with interactive analytics (v0.06)
- **`MainActivity.kt`** - Main navigation hub with 3 screen options

### Architecture Patterns
- **MVVM Pattern**: ViewModels manage UI state and business logic
- **Jetpack Compose**: Declarative UI framework optimized for Android TV
- **XML-TV Parser**: Custom parser handling large EPG datasets (64MB+)
- **State Management**: Compose state and remember for UI state persistence
- **Channel Row Pattern**: Fixed CategoryIcon + Scrollable LazyRow per channel

### UI Design System (Figma-based)
- **Target Resolution**: 1920x1080px with automatic scaling
- **Row Height**: 64px
- **Channel Column Width**: 288px  
- **Grid Position**: 210px from top
- **Responsive Scaling**: UI scales automatically for different TV resolutions

## Development Commands

### Build & Run
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Install on connected device  
./gradlew assembleRelease        # Build release APK
```

### Testing
```bash
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests
./gradlew testDebugUnitTest      # Run specific variant unit tests
```

### Code Quality  
```bash
./gradlew lint                   # Run lint checks
./gradlew lintDebug             # Run lint for debug variant
```

## Key Features

### ✅ Implemented
1. **Auto-positioning**: Automatic scroll to current time on startup
2. **"NOW" Line**: Vertical aqua line (#5AECD3) with current time label  
3. **Focus Management**: Automatic focus on current program
4. **Categories**: Filter chips for programs (All, Movies, Sports)
5. **Responsive Design**: UI scaling for different TV resolutions
6. **Live Info Panel**: Details panel for selected program (bottom left)
7. **Picture-in-Picture**: Placeholder for live TV (bottom right)

### 🎨 Design System
```kotlin
// Colors from Figma design
val colorBg = Color(0xFF48227C)           // Main background (purple)
val colorTextPrimary = Color(0xFFEEEEEE)  // Primary text (white)  
val colorTextSecondary = Color(0xCCEEEEEE) // Secondary text
val colorFocusBg = Color(0xFF5AECD3)      // Aqua focus background
val colorFocusText = Color(0xFF48227C)    // Focus text (purple)
val colorChipBg = Color(0x1AEEEEEE)       // Category chip background
```

## Android TV Specific Requirements

### Focus Management
- All UI components properly configured for D-pad navigation
- Custom focus handling for EPG grid traversal
- Visual focus indicators with high contrast colors
- Automatic focus positioning on current program

### TV Design Patterns  
- Large text sizes optimized for 10-foot viewing
- High contrast color scheme for TV displays
- Overscan-safe layouts avoiding screen edges
- Performance optimized for TV hardware limitations

## Data Structure

### EpgProgram:
```kotlin
data class EpgProgram(
    val channelId: String,
    val title: String,
    val startUtc: Instant,
    val endUtc: Instant,
    val subTitle: String? = null,
    val description: String? = null,
    val categories: List<String> = emptyList(),
    val iconUrl: String? = null
)
```

### EpgChannel:
```kotlin
data class EpgChannel(
    val id: String,
    val name: String
)
```

## Key Algorithms

### 1. Auto-scroll to Current Time
```kotlin
LaunchedEffect(Unit) {
    val minutesFromStart = Duration.between(start, now).toMinutes()
    val rawTarget = minutesFromStart * pxPerMin
    val centered = (rawTarget - viewportPx / 2f).coerceAtLeast(0f)
    scrollState.scrollTo(centered.toInt())
}
```

### 2. Program Focus Selection
```kotlin
// 1) Current program (if running)
row.firstOrNull { !now.isBefore(it.startUtc) && now.isBefore(it.endUtc) }
    ?: run {
        // 2) Closest to current time (by program center)
        row.minByOrNull { p ->
            val mid = p.startUtc.epochSecond + (p.endUtc.epochSecond - p.startUtc.epochSecond) / 2
            kotlin.math.abs(now.epochSecond - mid)
        }
    }
```

## Resource Files
- `epg.xml` - EPG data (64.3MB, complete program guide)
- `vod_data.csv` - PlayNow serials data (semicolon-separated format)
- `animacja1.riv` / `untitled.riv` - Rive animations for UI
- Channel logos: `https://epg.ovh/logo/{channelId}.png`

## Data Structure (v0.06)

### New CSV Format:
```
thumbnail_url;logo_url;channel_url;title;category;description
```

### VodContent Model (Enhanced):
```kotlin
data class VodContent(
    val title: String,        // parts[3] - Serial title
    val description: String,  // parts[5] - Full description  
    val category: String,     // parts[4] - Serial category
    val imageUrl: String      // parts[0] - Thumbnail URL
)
```

## Technical Notes

### Key Technologies
- **Jetpack Compose for TV**: Declarative UI optimized for television
- **XML-TV Parser**: Handles large EPG files (64MB+) efficiently  
- **Responsive Scaling**: UI coefficient based on 1920x1080 baseline
- **Focus Management**: Optimized for TV remote navigation
- **External APIs**: Automatic channel logo loading from epg.ovh

### Performance Considerations
- Large dataset handling (64MB EPG data)
- Memory-efficient parsing and caching
- Smooth scrolling performance on TV hardware
- Focus state management across large grids

## Development Status
✅ **v0.06 Complete** - Full integration with enhanced UX and analytics  
✅ **Multi-Screen Architecture** - EPG, Slider Dashboard, Channel Interface  
✅ **Data Migration** - PlayNow serials integration with optimized parsing  
✅ **Advanced Animations** - Synchronized timing and auto-reset functionality  
🚧 **In Development** - Category filters, search functionality  
📋 **Planned** - Live TV integration, recording capabilities

## Version History

### Version 0.06 - Full Integration with Enhanced UX (2025-09-12)
- **Separate Slider Screen**: Dedicated statistics dashboard with 4 pages (Overview, Retention, Realtime, Traffic Sources)
- **Restored Version 0.01**: Clean channel interface without hero slider interference  
- **Enhanced Data Source**: PlayNow serials CSV (seriale_playnow_bez_duplikatow.csv) replacing old VOD data
- **Synchronized Animations**: Details appear after 350ms slide-down animation completion
- **Complete Auto-Reset**: Channels reset on menu transitions (TOP_TABS ↔ CHANNEL_ROWS)
- **Full-Width Titles**: DetailedContentOverlay expanded from 874px to 1500px width
- **CSV Parser Optimization**: Semicolon separator with direct field mapping
- **Cache v2**: Updated cache system for new data format
- **Main Screen**: 3 navigation options - EPG, Slider, Wersja 0.01

### Version 0.05 - Smart Focus Navigation with Auto-Reset (2025-01-08)
- **Smart Focus Navigation**: UP/DOWN preserves context (CategoryIcon→CategoryIcon, Miniature→Miniature)
- **Auto-Reset LazyListState**: Unfocused rows automatically scroll to position 0
- **Crash Prevention**: Safe FocusRequester access prevents navigation crashes
- **Smart Logic**: `targetColIndex = if (focusedColIndex == -1) -1 else FIXED_FOCUS_POSITION`
- **LaunchedEffect Reset**: Automatic miniature position reset for unfocused channels
- **Smooth UX**: No more "left edge clipping" - miniatures return to home position
- **Backup**: `/Users/uxellenceuxe/TV_componenty/Version001Screen_v005_smart_focus_navigation.kt`

### Version 0.04 - Full Width LazyRow with Floating CategoryIcon (2025-01-08)
- **LazyRow Full Width**: Spans entire screen (0px → 1920px) eliminating clipping issues
- **Floating CategoryIcon**: Z-index positioned above LazyRow at X: 80px
- **ContentPadding Positioning**: First miniature at X: 380px via LazyRow contentPadding
- **No Clipping**: Eliminated Box wrappers and offset restrictions
- **Clean Architecture**: LazyRow as background, CategoryIcon floating on top
- **Identical Visual**: CategoryIcon: 80px, Miniature: 380px, Overlay: 380px
- **Backup**: `/Users/uxellenceuxe/TV_componenty/Version001Screen_v004_fullwidth_lazyrow.kt`

### Version 0.03 - Fixed Y Positioning System (2025-01-08)
- **FIXED_FOCUS_Y**: Focused channel always at Y: 340px (corrected from 500px)
- **Animated Miniatures**: Slide down 290px when focused using `animateDpAsState`
- **Channel Expansion**: Focused channel expands, pushing channels below down
- **CategoryIcon**: Always stays at Y: 0px within channel
- **DetailedContentOverlay**: Positioned at Y: 0px, X: 380px (Flutter-accurate)
- **Backup**: `/Users/uxellenceuxe/TV_componenty/Version001Screen_v003_fixed_Y_positioning.kt`

### Version 0.02 - Channel Row Structure (2024-08-28)
- **NEW STRUCTURE**: Fixed CategoryIcon + Scrollable Content per row
- **Navigation**: Simplified to TOP_TABS ↔ CHANNEL_ROWS
- **Focus System**: CategoryIcon (col=-1) + Content items (col=0-9)
- **Layout**: Each channel row = CategoryIcon + LazyRow with 10 content items
- **Backup**: Previous version saved as `/Users/uxellenceuxe/TV_componenty/Version001Screen_backup.kt`

### Version 0.01 - Left Navigation + Content Grid
- **Structure**: Separate left navigation column + content grid
- **Navigation**: TOP_TABS ↔ LEFT_CATEGORIES ↔ CONTENT_GRID
- **Focus System**: 2D grid navigation with category synchronization

## Important Patterns

### Channel Row Structure (v0.02)
```kotlin
// Each channel row: Fixed CategoryIcon + Scrollable content
Row {
    CategoryIcon(
        isFocused = focusedColIndex == -1, // col=-1 for CategoryIcon
        focusRequester = channelFocusRequesters[Pair(rowIndex, -1)]
    )
    LazyRow { // Scrollable content (col=0-9)
        items(10) { colIndex ->
            ContentCard(
                isFocused = focusedColIndex == colIndex,
                focusRequester = channelFocusRequesters[Pair(rowIndex, colIndex)]
            )
        }
    }
}
```

### New Focus Management (v0.02)
```kotlin
// Simplified 2-level navigation
enum class NavigationFocus { TOP_TABS, CHANNEL_ROWS }

// Channel focus system: Pair<rowIndex, colIndex>
// CategoryIcon: Pair(rowIndex, -1)
// Content items: Pair(rowIndex, 0-9)
val channelFocusRequesters = Map<Pair<Int, Int>, FocusRequester>

// Navigation flow:
// TOP_TABS → DOWN → CHANNEL_ROWS(0, -1) // First channel, CategoryIcon
// CHANNEL_ROWS → UP (from row 0) → TOP_TABS
// CHANNEL_ROWS → LEFT/RIGHT → Move within row or between CategoryIcon/Content
// CHANNEL_ROWS → UP/DOWN → Move between channel rows
```

### Compose State Management
```kotlin
// Channel rows state handling
val channels = listOf("Polecane", "Nowości", "Filmy", "Seriale", "Sport", "Dzieci", "Dokumenty", "Muzyka")
var focusedChannel by remember { mutableStateOf("Polecane") }
var focusedRowIndex by remember { mutableStateOf(0) }
var focusedColIndex by remember { mutableStateOf(-1) } // Start on CategoryIcon
```

### Focus Handling
```kotlin
// TV remote navigation for channel rows
CategoryIcon(
    isFocused = rowIndex == focusedRowIndex && focusedColIndex == -1,
    focusRequester = channelFocusRequesters[Pair(rowIndex, -1)],
    onFocused = { 
        onChannelFocusChange(channel)
        onChannelContentFocusChange(rowIndex, -1)
    }
)
```