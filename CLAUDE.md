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

### Architecture Patterns
- **MVVM Pattern**: ViewModels manage UI state and business logic
- **Jetpack Compose**: Declarative UI framework optimized for Android TV
- **XML-TV Parser**: Custom parser handling large EPG datasets (64MB+)
- **State Management**: Compose state and remember for UI state persistence

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
- `animacja1.riv` / `untitled.riv` - Rive animations for UI
- Channel logos: `https://epg.ovh/logo/{channelId}.png`

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
✅ **MVP Complete** - Core EPG functionality implemented  
🚧 **In Development** - Category filters, search functionality  
📋 **Planned** - Live TV integration, recording capabilities

## Important Patterns

### Compose State Management
```kotlin
// EPG screen state handling
val scrollState = rememberLazyListState()
var selectedProgram by remember { mutableStateOf<EpgProgram?>(null) }
```

### Time-based Calculations  
```kotlin
// Program positioning in grid
val programWidth = (durationMinutes * pixelsPerMinute).coerceAtLeast(minWidth)
val startOffset = Duration.between(gridStart, program.startUtc).toMinutes() * pixelsPerMinute
```

### Focus Handling
```kotlin
// TV remote navigation optimization
Modifier.focusable()
    .onFocusChanged { focusState ->
        if (focusState.isFocused) {
            onProgramSelected(program)
        }
    }
```