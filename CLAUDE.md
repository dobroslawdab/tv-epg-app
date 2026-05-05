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
- **`YouTubeTrailerPlayer.kt`** - Video trailer player for KINO PLAY slider (2025-12-16)

## Trailer Auto-Play System (KINO PLAY Slider)

### Overview
Automatic trailer playback when user focuses on movie poster in KINO PLAY slider for 2+ seconds.

### Architecture
```
TopMenuScreen2.kt (VodHeroSlider)
    └── YouTubeTrailerPlayer.kt
            └── ExoPlayer + TextureView (Compose AndroidView)
                    └── YouTubeStreamExtractor.kt (URL extraction)
```

### Key Implementation Details

**1. Direct TextureView (NOT PlayerView/StyledPlayerView)**
```kotlin
// ❌ NIE DZIAŁA w Compose:
PlayerView(ctx)  // Używa SurfaceView - problemy z z-order

// ✅ DZIAŁA w Compose:
AndroidView(factory = { ctx ->
    android.view.TextureView(ctx).also { textureView ->
        exoPlayer.setVideoTextureView(textureView)
    }
})
```

**2. Obsługiwane formaty URL**
- **Direct MP4/M3U8**: `*.mp4`, `*.m3u8` → Natychmiastowe odtwarzanie
- **Supabase Storage**: `supabase.co/storage/*` → Natychmiastowe odtwarzanie
- **YouTube**: `youtube.com/watch?v=*` → Wymaga ekstrakcji przez Vercel API

**3. Data source**
- URL trailera: `youtube_url` pole w tabeli `movies` (Supabase)
- Dla direct URLs (MP4/Supabase): No extraction, instant playback
- Dla YouTube: Extraction via `yt-extract-api.vercel.app`

**4. Focus-based auto-play logic**
```kotlin
LaunchedEffect(stableItem.title) {
    while (true) {
        if (isFocused && !trailerUrl.isNullOrBlank() && !showTrailer) {
            delay(2000)  // 2 sec delay
            if (isFocused) showTrailer = true
        } else if (!isFocused && showTrailer) {
            showTrailer = false
        }
        delay(100)
    }
}
```

**5. Layer structure (z-order)**
```
Layer 1 (bottom): AsyncImage (backdrop)
Layer 2: 20% black overlay (only when !showTrailer)
Layer 3: Trailer video (zIndex=5, only when showTrailer)
Layer 4: Gradient (zIndex=6)
Layer 5 (top): Text, buttons (UI)
```

### Files
| File | Purpose |
|------|---------|
| `TopMenuScreen2.kt:11001-11080` | VodHeroSlider trailer logic |
| `YouTubeTrailerPlayer.kt` | ExoPlayer + TextureView composable |
| `YouTubeStreamExtractor.kt` | URL detection & extraction |

### Debugging
```bash
# Trailer logs
adb logcat | grep -E "YouTubeTrailer|VodHeroSlider|ExoPlayer"
```

### Known Issues & Solutions
| Issue | Cause | Solution |
|-------|-------|----------|
| Black screen, no video | SurfaceView z-order in Compose | Use TextureView directly |
| Purple background | Trailer under other layers | zIndex(5f) + render ON TOP |
| URL extraction fails | YouTube rate limiting | Use Supabase Storage for direct MP4 |

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

## Key Event Management System

### 🎯 **Core Principle: Single Source of Truth**
**NEVER allow multiple components to handle the same key event simultaneously**

### **Hierarchy of Key Event Handling**

#### **Level 1: MainActivity (Global Navigation)**
- **Scope**: Cross-screen navigation (back to HOME, app exit)
- **Keys**: BACK (when needs to exit screens)
- **Pattern**: Uses callbacks from child screens to coordinate
- **Rule**: Only handles keys when child screens explicitly delegate

```kotlin
// ✅ CORRECT: Callback-based coordination
TopMenuScreen(
    onBackPressed = { isMenuFocused ->
        if (isMenuFocused) {
            currentScreen = NavigationScreen.HOME
            true // consumed
        } else {
            false // let child handle
        }
    }
)
```

#### **Level 2: Screen Components (Screen-specific Logic)**
- **Scope**: Intra-screen navigation (menu ↔ content)
- **Keys**: BACK, UP, DOWN, LEFT, RIGHT
- **Pattern**: Uses `onPreviewKeyEvent` with callback delegation
- **Rule**: Must coordinate with MainActivity via callbacks

```kotlin
// ✅ CORRECT: Screen-level with callback delegation
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        val handled = onBackPressed(menuState.isMenuFocused)
        if (!handled && !menuState.isMenuFocused) {
            // Handle locally if not handled by parent
            menuState = menuState.copy(isMenuFocused = true)
            return@onPreviewKeyEvent true
        }
        return@onPreviewKeyEvent handled
    }
    // ... other keys
}
```

#### **Level 3: Component Widgets (Micro-interactions)**
- **Scope**: Individual component focus (list navigation, form inputs)
- **Keys**: LEFT, RIGHT, UP, DOWN, ENTER
- **Pattern**: Uses `onKeyEvent` or state management
- **Rule**: Never handle BACK - always delegate up

```kotlin
// ✅ CORRECT: Widget-level navigation only
LazyRow(
    modifier = Modifier.onPreviewKeyEvent { event ->
        when (event.key) {
            Key.DirectionLeft, Key.DirectionRight -> {
                // Handle internal navigation
                true
            }
            else -> false // Delegate all other keys up
        }
    }
)
```

### **🚫 ANTI-PATTERNS (That Cause Conflicts)**

#### **❌ Multiple BACK handlers**
```kotlin
// WRONG: MainActivity has onPreviewKeyEvent for BACK
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        currentScreen = NavigationScreen.HOME
        true
    }
}

// WRONG: AND TopMenuScreen also handles BACK
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        // This will NEVER execute!
        menuState = menuState.copy(isMenuFocused = true)
        true
    }
}
```

#### **❌ Direct event interception without coordination**
```kotlin
// WRONG: No callback, direct interception
.onPreviewKeyEvent { event ->
    // This creates race conditions
    handleDirectly(event)
}
```

### **✅ STANDARD PATTERNS**

#### **Pattern 1: Callback Delegation (MainActivity ↔ Screens)**
```kotlin
// Parent (MainActivity)
Screen(
    onBackPressed = { screenState ->
        if (shouldExitScreen(screenState)) {
            navigateAway()
            true
        } else {
            false // Let screen handle
        }
    }
)

// Child (Screen)
fun Screen(onBackPressed: (State) -> Boolean) {
    .onPreviewKeyEvent { event ->
        if (event.key == Key.Back) {
            val handled = onBackPressed(currentState)
            if (!handled) {
                // Handle locally
                handleBackLocally()
                return@onPreviewKeyEvent true
            }
            return@onPreviewKeyEvent handled
        }
    }
}
```

#### **Pattern 2: State-Based Focus Management**
```kotlin
// Use state to coordinate between components
var focusLevel by remember { mutableStateOf(FocusLevel.MENU) }

when (focusLevel) {
    FocusLevel.MENU -> // Menu handles keys
    FocusLevel.CONTENT -> // Content handles keys
}
```

### **📱 SECTION NAVIGATION DELEGATION PATTERN**

**Critical Pattern**: For multi-screen applications with a central navigation hub (like TopMenuScreen2), sections must delegate navigation to their child components.

#### **When to Use Delegation vs. Custom Handling**

**✅ Use Delegation** (return `false` to let child handle):
- Section has its own `handleNavigation` function (e.g., `handleMojeChannelsNavigation`, `handleVodNavigation`)
- Section has complex multi-row navigation (channel-by-channel)
- Section manages its own focus state and FocusRequesters
- Examples: **MOJE**, **START**, **APLIKACJE**, **VOD**

**❌ Use Custom Handling** (return `true` after handling):
- Section has simple single-level navigation
- Section uses GlobalFocusManager for row navigation
- Section needs special menu transition logic
- Example: **TELEWIZJA** (uses GlobalFocusManager.navigateRow)

#### **Implementation Pattern**

**In TopMenuScreen2 onPreviewKeyEvent** (line ~200):
```kotlin
when (currentSection) {
    "VOD" -> {
        // VOD handles its own navigation entirely
        false // Let VodWithChannels handle all keys
    }
    "MOJE" -> {
        // MOJE handles its own navigation entirely
        false // Let MojeChannelsScreen handle all keys
    }
    "START" -> {
        // START handles its own navigation entirely
        false // Let StartChannelsScreen handle all keys
    }
    "APLIKACJE" -> {
        // APLIKACJE handles its own navigation entirely
        false // Let AplikacjeChannelsScreen handle all keys
    }
    "TELEWIZJA" -> {
        // TELEWIZJA uses custom GlobalFocusManager navigation
        when (event.key) {
            Key.DirectionUp -> {
                val newState = GlobalFocusManager.navigateRow(globalFocusState.value, RowDirection.UP)
                if (newState.currentRow == 0) {
                    globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
                } else {
                    globalFocusState.value = newState
                }
                true
            }
            // ... other keys
        }
    }
}
```

**In Child Component** (e.g., VodWithChannels):
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .onPreviewKeyEvent { event ->
            handleVodNavigation(
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
                onReturnToMenu = onReturnToMenu // Callback to parent
            )
        }
) { /* content */ }
```

#### **Checklist: Adding a New Section**

When adding a new section to TopMenuScreen2:

1. ✅ **Does section have complex navigation?**
   - Multi-row channel navigation → Use delegation pattern
   - Simple navigation → Consider custom handling

2. ✅ **Create complete navigation handler**
   - Function: `handleXXXNavigation(event, focusedRowIndex, focusedColIndex, ...)`
   - Handle: UP, DOWN, LEFT, RIGHT
   - Include: `onReturnToMenu` callback for menu transition

3. ✅ **Add to TopMenuScreen2 delegation list**
   - Add section to `when (currentSection)` block
   - Return `false` with comment: `"// XXX handles its own navigation entirely"`

4. ✅ **Test navigation flow**
   - Channel-to-channel (UP/DOWN): Should move between channels
   - First channel UP: Should eventually reach menu
   - Menu transition: Should use `onReturnToMenu` callback

5. ✅ **Document in Recent Conflict Resolution**
   - If solving navigation conflict, document in CLAUDE.md

#### **Common Anti-Pattern**

**❌ WRONG** - Parent intercepts keys that child should handle:
```kotlin
"VOD" -> {
    when (event.key) {
        Key.DirectionUp -> {
            if (globalFocusState.value.currentRow == 1) {
                globalFocusState.value = GlobalFocusManager.returnToMenu(globalFocusState.value)
                true // ❌ Parent handles menu transition directly
            } else false
        }
        else -> false
    }
}
```

**✅ CORRECT** - Parent delegates to child, child calls callback:
```kotlin
// In TopMenuScreen2:
"VOD" -> {
    false // Let VodWithChannels handle all keys
}

// In VodWithChannels handleVodNavigation:
when {
    focusedRowIndex == 1 -> {
        onReturnToMenu() // ✅ Child uses callback for menu transition
    }
}
```

### **🔧 DEBUGGING TOOLS**

#### **Key Event Flow Logging**
Add to any key handler for debugging:
```kotlin
.onPreviewKeyEvent { event ->
    Log.d("KeyEvent", "${componentName}: ${event.key} - handled: $result")
    result
}
```

#### **Focus State Debug Overlay**
```kotlin
// Debug overlay showing current key handlers
Box {
    YourContent()
    if (BuildConfig.DEBUG) {
        KeyEventDebugOverlay(
            activeHandlers = listOf("MainActivity.onBack", "TopMenu.onPreview"),
            currentFocus = currentFocusState
        )
    }
}
```

### **📋 CONFLICT PREVENTION CHECKLIST**

**🚨 MANDATORY**: Before adding ANY key event handler, follow the complete checklist:

**→ See `KEY_EVENT_CHECKLIST.md` for the comprehensive checklist ←**

Quick pre-implementation checks:

1. **❓ Is there already a handler for this key?**
   - Search codebase: `grep -r "Key\.Back\|KEYCODE_BACK"`
   - Check parent components

2. **❓ What level should handle this key?**
   - Global navigation → MainActivity callback
   - Screen navigation → Screen component
   - Widget navigation → Widget component

3. **❓ Does this need coordination?**
   - BACK always needs coordination
   - Directional keys might need delegation

4. **❓ Is this handler documented?**
   - Add comment explaining scope and delegation
   - Update this section if adding new patterns

**For complete implementation and testing guidelines, use `KEY_EVENT_CHECKLIST.md`**

### **⚠️ CRITICAL RULES**

1. **ONE BACK HANDLER PER LEVEL**: Never have multiple components handle Key.Back at the same level
2. **ALWAYS DELEGATE UP**: Lower-level components should use callbacks to coordinate with higher levels  
3. **EXPLICIT CONSUMPTION**: Always return explicit `true`/`false` from key handlers
4. **DOCUMENT SCOPE**: Comment what keys each handler manages and why

### **📝 COMMIT MESSAGE TEMPLATE**
When modifying key event handling:
```
Fix/Add: [Component] key event handling

- Scope: [What keys and scenarios]
- Coordination: [How it delegates/coordinates]
- Conflicts: [What conflicts this resolves/avoids]

Fixes: [Issue description if applicable]
```

### **🏗️ NAMING CONVENTIONS & STRUCTURE**

#### **Handler IDs (for KeyEventManager)**
```
Pattern: {Component}.{Scope}.{Purpose}

Examples:
✅ MainActivity.Global.Navigation
✅ TopMenuScreen.Screen.BackNavigation  
✅ SliderComponent.Component.ArrowKeys
✅ SearchButton.Widget.FocusControl

❌ backHandler (too vague)
❌ keyListener (not descriptive)
❌ handler1 (no context)
```

#### **Callback Function Names**
```
Pattern: on{Action}{Context}

Examples:
✅ onBackPressed(isAtTopLevel: Boolean) -> Boolean
✅ onNavigateToContent() -> Unit
✅ onReturnToMenu() -> Unit  
✅ onFocusChanged(isFocused: Boolean) -> Unit

❌ handleBack() (doesn't indicate delegation)
❌ backCallback() (too generic)
❌ onKey() (no action specified)
```

#### **State Variable Names**
```
Pattern: {scope}{State}{Type}

Examples:
✅ menuFocusState: FocusState
✅ contentShouldFocus: Boolean
✅ currentNavigationLevel: NavigationLevel
✅ isMenuFocused: Boolean

❌ focused (ambiguous scope)
❌ state (too generic)
❌ flag (no meaning)
```

#### **File Organization**
```
/utils/
  ├── KeyEventManager.kt       # Central coordination system
  ├── KeyEventPatterns.kt      # Reusable patterns & helpers
  └── KeyEventDebugTools.kt    # Debug overlays & logging

/screens/
  ├── MainActivity.kt          # Global-level handlers only
  ├── TopMenuScreen.kt         # Screen-level + delegation
  └── SliderScreen.kt          # Screen-level + delegation

Pattern: Keep key handling logic close to the component that uses it
```

#### **Comment Templates**
```kotlin
// ✅ GOOD: Descriptive handler comment
/**
 * KEY HANDLER: TopMenuScreen Navigation
 * 
 * Scope: Handles BACK key coordination with MainActivity
 * Delegation: BACK from menu -> MainActivity (exit screen)
 *            BACK from content -> local (return to menu)
 * Conflicts: None (uses callback delegation pattern)
 * 
 * @see MainActivity.onBackPressed for coordination logic
 */
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        val handled = onBackPressed(menuState.isMenuFocused)
        // ... rest of logic
    }
}

// ❌ BAD: No context or delegation info
.onPreviewKeyEvent { event ->
    // Handle back key
    handleBack(event)
}
```

### **📚 CENTRAL SYSTEMS (New Architecture)**

#### **KeyEventManager (Singleton)**
- **Location**: `/utils/KeyEventManager.kt`
- **Purpose**: Centralized registration and conflict detection
- **Usage**: `RegisterKeyHandler` composable or direct registration
- **Benefits**: Automatic priority handling, conflict detection, debug logging

#### **KeyEventPatterns (Utilities)**
- **Location**: `/utils/KeyEventPatterns.kt`  
- **Purpose**: Pre-built patterns for common scenarios
- **Available Patterns**:
  - `GlobalNavigationKeyHandler` - MainActivity level
  - `ScreenNavigationKeyHandler` - Screen level with delegation
  - `ComponentNavigationKeyHandler` - Component level
  - `WidgetKeyHandler` - Widget micro-interactions
- **Benefits**: Consistent behavior, reduced boilerplate, conflict prevention

#### **Safe Migration Path**
When updating existing components:
1. **Wrap existing logic** with `SafeNavigationScope`
2. **Register handlers** using `RegisterKeyHandler`
3. **Add debugging** with `KeyEventDebugOverlay`
4. **Test for conflicts** using `KeyEventManager.detectConflicts()`
5. **Gradually migrate** to standard patterns

Example migration:
```kotlin
// OLD: Direct onPreviewKeyEvent
.onPreviewKeyEvent { event ->
    when (event.key) {
        Key.Back -> handleBack()
        Key.DirectionLeft -> handleLeft()
        else -> false
    }
}

// NEW: Using KeyEventManager
SafeNavigationScope(
    scopeId = "SliderScreen.Navigation",
    priority = KeyEventManager.Priority.SCREEN,
    keys = setOf(Key.Back, Key.DirectionLeft, Key.DirectionRight),
    onKeyEvent = { event ->
        when (event.key) {
            Key.Back -> handleBack()
            Key.DirectionLeft -> handleLeft()
            Key.DirectionRight -> handleRight()
            else -> false
        }
    },
    showDebugOverlay = BuildConfig.DEBUG
) {
    // Your screen content
}
```

### **Recent Conflict Resolution (Reference)**

#### **Issue #1: MainActivity BACK Key Conflict** (Historical)
- **Issue**: MainActivity `onPreviewKeyEvent` for BACK conflicted with TopMenuScreen callback system
- **Solution**: Removed MainActivity `onPreviewKeyEvent`, used callback delegation pattern
- **Result**: Clean separation - MainActivity handles exit, TopMenuScreen handles internal navigation
- **Files**: `MainActivity.kt`, `TopMenuScreen.kt` (Commit: 25d0639)

#### **Issue #2: APLIKACJE Navigation Conflict** (2025-09-30)
- **Issue**: TopMenuScreen2 intercepted UP key for APLIKACJE in `else` block (line 242), preventing channel-by-channel navigation
- **Symptom**: Pressing UP from any channel content jumped directly to menu instead of moving to previous channel
- **Root Cause**: APLIKACJE not listed in delegation sections, fell through to default behavior
- **Solution**: Added APLIKACJE to delegation list (lines 238-240), returning `false` to let AplikacjeChannelsScreen handle all keys
- **Result**: UP navigation now moves channel-by-channel, only returns to menu from CategoryIcon of first channel
- **Files**: `TopMenuScreen2.kt:238-240` (AplikacjeChannelsScreen handles its own navigation via `handleAplikacjeChannelsNavigation`)

#### **Issue #3: VOD Navigation Conflict** (2025-09-30)
- **Issue**: TopMenuScreen2 had custom UP key handling for VOD (lines 201-210), intercepting `currentRow == 1` to return to menu directly
- **Symptom**: VOD navigation behavior inconsistent with MOJE/START/APLIKACJE pattern
- **Root Cause**: VOD used old pattern of parent-level interception instead of delegation to child component
- **Solution**: Replaced custom handling with delegation pattern (return `false`), letting VodWithChannels handle all keys
- **Result**: VOD now uses same delegation pattern as other sections, child component handles menu transition via `onReturnToMenu` callback
- **Files**: `TopMenuScreen2.kt:201-204` (VodWithChannels handles its own navigation via `handleVodNavigation`)
- **Documentation**: Added comprehensive Section Navigation Delegation Pattern to CLAUDE.md

#### **Issue #4: EpgDayScreen Monolithic Handler Refactoring** (2025-10-31)
- **Issue**: Monolityczny `onPreviewKeyEvent` handler (376 linii) obsługujący wszystko: CH+/CH-/Digits/UP/DOWN/LEFT/RIGHT/OK/BACK/Special keys
- **Symptoms**:
  1. BACK wyłącza aplikację zamiast wrócić do zakładki Telewizja
  2. CH+/CH- przestaje działać po auto-hide Zapping Bar (utrata fokusu)
  3. Czarny ekran na starcie zamiast live TV
  4. Złożona 4-poziomowa logika BACK była wrażliwa i niedziałała deterministycznie
- **Root Cause**: Antywzorzec - Single monolithic handler zamiast delegacji do kontrolerów (Focus Architect violation)
- **Solution**: Proper Refactoring - Ekstrakcja do 3 wyspecjalizowanych kontrolerów:
  1. **ZappingBarController** (80 linii) - CH+/CH-/Digits → Zapping Bar
  2. **BackNavigationController** (90 linii) - BACK (uproszczony 2-poziomowy zamiast 4)
  3. **EpgNavigationController** (95 linii) - UP/DOWN/LEFT/RIGHT/OK
  4. **Simplified handler** (54 linie) - Delegacja w priority order: Zapping → BACK → Navigation → Special keys
- **Result**:
  - ✅ 86% redukcja linii kodu w main handler (376 → 54)
  - ✅ BACK działa poprawnie (2-poziomowa logika: Hide interface → Exit)
  - ✅ CH+/CH- działa po auto-hide (`rootFocus.requestFocus()` restore)
  - ✅ GUI widoczne na starcie (`interfaceVisible = true`)
  - ✅ Focus Architect compliance - delegation pattern z callbacks
- **Files**:
  - Created: `ZappingBarController.kt`, `BackNavigationController.kt`, `EpgNavigationController.kt`
  - Modified: `EpgDayScreen.kt:573-694` (controller init + simplified handler)
- **Documentation**: Full refactoring documented in this section

#### **Issue #5: PIP Dialog Focus Blocking** (2025-11-07)
- **Issue**: PIP dialog blocks underlying navigation, buttons unresponsive when dialog open
- **Symptoms**:
  1. Dialog shows on PLAY/PAUSE press
  2. START section navigation becomes unresponsive (keys don't work)
  3. No way to close dialog reliably
  4. Background elements still receive key events
- **Root Cause**:
  - Dialog guard returns `false` (delegates ALL keys without validation)
  - Unexpected keys (media, numeric) leak to background
  - No debounce on dialog trigger (rapid presses cause issues)
  - Monolithic inline UI (147 lines) in TopMenuScreen2.kt
- **Solution**: Hybrid Pattern (Strengthened Input Gate + Controller Delegation)
  1. **Strengthened Input Gate**: Added `DIALOG_ALLOWED_KEYS` whitelist (UP/DOWN/ENTER/BACK/ESCAPE)
  2. **Key Validation**: Dialog guard validates against whitelist - allowed keys delegate, others blocked
  3. **Debounce**: 50ms minimum between dialog opens (`lastDialogOpenTime` state)
  4. **Controller Extraction**: Created `PipDialogController.kt` with `handleDialogKeys()` function
  5. **UI Extraction**: Created `PipDialogMenu.kt` composable (189 lines)
  6. **Data Models**: Created `PipMenuOption.kt` sealed class (2 options: Fullscreen, Close)
- **Result**:
  - ✅ Navigation preserved: START section works when dialog closed
  - ✅ Unexpected keys blocked: Media/numeric keys can't reach background
  - ✅ Reliable close: BACK always dismisses dialog
  - ✅ No rapid opens: Debounce prevents multiple dialogs
  - ✅ Clean architecture: 147 lines → 12-line composable call
  - ✅ Testable: Controller can be unit tested independently
  - ✅ Reusable: PipDialogMenu can be used elsewhere
- **Files**:
  - Created: `pip/PipMenuOption.kt`, `pip/PipDialogController.kt`, `pip/PipDialogMenu.kt`
  - Modified: `TopMenuScreen2.kt:100-107` (constant), `TopMenuScreen2.kt:661` (debounce state), `TopMenuScreen2.kt:709-717` (strengthened guard), `TopMenuScreen2.kt:754-762` (debounce logic), `TopMenuScreen2.kt:970-979` (composable call)
- **Documentation**: `docs/patterns/PIP_DIALOG_PATTERN.md` (comprehensive pattern guide)
- **Pattern Reference**: Follows tescik example (input gating) + EpgDayScreen pattern (controller extraction)
- **Commits**: 29973a5, 96baec1, 3bf5d4d, 6194519 (4 atomic commits)

#### **Issue #6: TopMenu Right Section Dual Focus** (2025-11-15)
- **Issue**: Right section (CandyBar/Profile/Settings buttons) showed dual focus with main menu, violating single-focus principle
- **Symptoms**:
  1. APLIKACJE tab showed aqua focus (white background) while CandyBar also showed aqua focus
  2. Pressing OK activated APLIKACJE (content transition) instead of CandyBar (Points History)
  3. CandyBar width stretched beyond content instead of fitting naturally
- **Root Cause**:
  - `isMenuFocused` calculation didn't exclude right section focus state
  - ENTER/OK handler always intercepted keys without delegating to focused buttons
  - CandyBar used fixed width instead of content-fitted width
- **Solution**: Focus Architect Compliance - Three targeted fixes:
  1. **isMenuFocused Guard** (Line 1072): Changed from `currentRow == 0` to `currentRow == 0 && focusedRightButton == -1`
  2. **ENTER/OK Delegation** (Lines 911-922): Added `if (focusedRightButton >= 0) return false` to delegate to button callbacks
  3. **CandyBar Width** (Lines 1328-1329): Changed from `.width(sx(324))` to `.widthIn(max = sx(324)).wrapContentWidth()`
- **Result**:
  - ✅ Single focus guarantee: Only CandyBar shows aqua when focused, APLIKACJE shows "selected" (white bg)
  - ✅ OK delegation works: Pressing OK on CandyBar opens Points History via button's `onFocusChanged` callback
  - ✅ CandyBar width fits content: Dynamic width based on "40 pkt / 21 dni" text length
  - ✅ Clean separation: Main menu "selected" state independent from right section "focused" state
  - ✅ Focus Architect pattern: Callback delegation instead of parent interception
- **Files**: `TopMenuScreen2.kt:1072` (isMenuFocused), `TopMenuScreen2.kt:911-922` (delegation), `TopMenuScreen2.kt:1328-1329` (width)
- **Documentation**: Added to Recent Conflict Resolution with Focus Architect compliance notes
- **User Feedback**: "mamy dwo fokusy... powinienem mieć taką sytuację ze na aplikacje zostaje selected ale fokus jest na candy punktach"

#### **Issue #7: SEARCH Navigation Skipped (Menu-to-Right Section)** (2025-11-18)
- **Issue**: Hardcoded `MenuPositions.APLIKACJE` check prevented SEARCH(6) from transitioning to right section (CandyBar/Profile/Settings) after menu reordering
- **Symptoms**:
  1. RIGHT from SEARCH(6) stayed on SEARCH instead of moving to CandyBar/Profile
  2. LEFT from CandyBar/Profile skipped SEARCH(6) and went to APLIKACJE(5)
  3. SEARCH effectively unreachable when navigating between menu and right section
- **Root Cause**: Navigation logic hardcoded assumption that APLIKACJE (position 5) was last menu item, but SEARCH moved to position 6 in menu reorder
- **Solution**: Replaced 3 hardcoded position checks with dynamic `menuItems.size - 1` calculation:
  1. **Line 967** (RIGHT key): Check if at last menu item before transitioning to right section
  2. **Line 949** (LEFT from CandyBar): Return to last menu item dynamically
  3. **Line 941** (LEFT from Profile with CandyBar hidden): Return to last menu item dynamically
- **Result**:
  - ✅ RIGHT from SEARCH(6) correctly transitions to CandyBar/Profile
  - ✅ LEFT from CandyBar/Profile correctly returns to SEARCH(6)
  - ✅ Navigation robust to future menu reordering (no hardcoded positions)
  - ✅ Self-documenting code with clear "last menu item" pattern
- **Files**: `TopMenuScreen2.kt:941, 949, 967` (3 occurrences of hardcoded APLIKACJE position replaced)
- **Documentation**: Added comprehensive documentation comment explaining "last menu item → right section" pattern
- **Pattern**: Always use dynamic calculations (`menuItems.size - 1`) instead of hardcoded positions for edge case logic
- **Anti-pattern Warning**: Hardcoding position checks (`currentPosition == MenuPositions.APLIKACJE`) creates fragile navigation that breaks when menu order changes

#### **Issue #8: TOP MENU V1 Duplicate Selected Indicators** (2026-01-16)
- **Issue**: MenuButton2 showed duplicate indicators when content had focus - both AnimatedFocusIndicator (white) AND internal border
- **Symptoms**:
  1. During LEFT/RIGHT navigation in menu, white "selected" border appeared on previous tab
  2. When focus moved to content (DOWN), TWO indicators appeared around selected tab (inner border + outer floating indicator)
  3. Inner gray/white border shouldn't display when AnimatedFocusIndicator handles selected state
- **Root Cause**: Multiple issues in MenuButton2:
  1. `showSelectedBorder` didn't check `isMenuFocused` - border showed during menu navigation
  2. `showSelectedBorder` didn't exclude `FLOATING_INDICATOR` type - both border AND floating indicator showed
  3. Second MenuButton2 call (line 2540) was missing `isMenuFocused` parameter
- **Solution**: Three targeted fixes in MenuButton2 function:
  1. **Added `&& !isMenuFocused`** to showSelectedBorder condition - border only shows when content has focus
  2. **Added `useFloatingIndicator` variable** and excluded it from showSelectedBorder - for V1, AnimatedFocusIndicator handles both focus AND selected states
  3. **Added `isMenuFocused = menuState.isMenuFocused`** to second MenuButton2 call
  4. **CLASSIC_FILL fixes**: backgroundColor and textColor now respect isMenuFocused (aqua/purple only when menu focused)
- **Result**:
  - ✅ Menu focused + LEFT/RIGHT: ONLY aqua floating indicator (no white border on previous tab)
  - ✅ Content focused: ONLY white floating indicator (no inner border)
  - ✅ NEVER two indicators at once
  - ✅ CLASSIC_FILL mode also fixed (aqua fill only when menu focused)
- **Files**: `TopMenuScreen2.kt:3275-3317` (MenuButton2 function), `TopMenuScreen2.kt:2548` (isMenuFocused parameter)
- **Pattern**: For focus types using external indicators (FLOATING_INDICATOR, FLOATING_FILL), the button component should NOT show its own border/fill for selected state
- **Commit**: `fc104e4`

#### **Issue #9: Skróty v3 Chip Layout & Y-Position Bug** (2026-05-05)
- **Issue**: Migration z 6 ShortcutCardów (235×208 z ikonami) na 11 chipów tekstowych 202×80 z Figmy. Po zmianie ROW_HEIGHT 278→136, plakaty Kino Play (vertical posters) zaczęły nakładać się na chipy gdy fokus przechodził na Skróty.
- **Symptoms**:
  1. Layout nie scrollował wyżej gdy fokus na chipie — Skróty zostawały na pozycji "below Kino Play" zamiast Y=340
  2. Plakaty Kino Play overlap z chipami when focused on Skróty
  3. Spacing 56px (między dolną krawędzią chipa a Polecane) wymagany przez design
  4. Nawigacja zatrzymywała się na 5. chipie (hardcoded `< 5` z czasów 6 ShortcutCardów)
  5. Auto-reset scrolla X nie działał (LazyRow tworzył własny LazyListState)
- **Root Cause**: Pre-existing bug w `calculateVodChannelYPosition` linia 17737 — `channels.getOrNull(i + 1)` zamiast `channels.getOrNull(i)`. Formula odejmowała wysokość kolejnego channela (Skróty) zamiast aktualnego (Kino Play). Maskowane przez stare wartości (Skróty=278 ≈ vertical=346); ujawnione po zmianie 278→136.
- **Solution**:
  1. **Bug fix**: `i+1` → `i` w `calculateVodChannelYPosition`
  2. **Constanty**: `ODKRYWAJ_SHORTCUTS_V3_*_ROW_HEIGHT = 136` (chip 80 + 56 spacing dół) + zmiana wszystkich hardcoded 278 → 136 w funkcji
  3. **Chip spec z Figma**: `size(sx(202), sy(80))` jako pierwszy modifier (NIE `defaultMinSize`/`requiredHeight` — ignorowane przez LazyRow constraints)
  4. **lazyListState propagation**: `VodShortcutsV3Row` przyjmuje `lazyListState` jako parametr, propaguje do `LazyRow.state` → parent `LaunchedEffect` auto-resetuje scroll
  5. **Dynamic max col**: `channelFocusRequesters.keys.maxOfOrNull` zamiast hardcoded `< 5` w `handleVodNavigation`
- **Result**:
  - ✅ Fokus na chipie scroll'uje stronę: chipy Y=340, Kino Play off-screen
  - ✅ 56px spacing między chipem (dół) a Polecane (góra)
  - ✅ Nawigacja przez wszystkie 11 chipów
  - ✅ Auto-reset scrolla X po opuszczeniu Skróty
  - ✅ Manrope Medium 24sp, letter-spacing 0.48, line-height 32 zgodnie z Figma
- **Files**: `TopMenuScreen2.kt` — `VodShortcutsV3Row` (17263+), `CategoryChip` (17354+), `calculateVodChannelYPosition` (17737), `handleVodNavigation` (17963)
- **Documentation**: `docs/patterns/SHORTCUTS_V3_CHIP_LAYOUT_PATTERN.md` (pełen pattern guide z anti-patterns)
- **Lesson**: Pre-existing bugs mogą być maskowane przez "happy path" wartości. Po zmianie design tokenu, sprawdź formuły layoutu z większym scrutiny — często są fragile od dawna.

#### **Issue #10: KinoGridScreen + Kino Play Overlay Focus Restoration** (2026-05-05)
- **Issue**: Two interrelated features:
  1. New `KinoGridScreen` (full-screen movie grid with category picker) opened from Kino Play needed exact-poster restoration after BACK from MovieDetail
  2. From the Kino Play tab itself, BACK from MovieDetail dropped the user back on the Start tab (or slider, or first poster of the channel) — never on the actual clicked poster
- **Symptoms**:
  1. Grid: focus visibly "jumped" from first card to restored card after data loaded (mignięcie)
  2. Kino Play tab: BACK returned to TopMenu but lost section, then lost row, then lost column — every layer of focus state was reset
  3. Many failed restoration attempts caused focus to disappear entirely (Compose limbo state) and arrows stopped working
- **Root Cause** (multi-layered):
  1. KinoGridScreen unmounted on every nav, so any `LaunchedEffect`-based restoration always lost the race with internal Compose mechanisms (LazyRow lazy composition, miniaturesYOffset 350ms slide animation, fresh `?: FocusRequester()` per recompose, gradient/details overlay, `LaunchedEffect(selectedCategory)` resetting `focusedRow=0`)
  2. TopMenuScreen2 also unmounted on MOVIE_DETAIL navigation, dropping `VodWithChannels` state including `lazyListStates` scroll positions and `channelFocusRequesters` focus bindings
  3. `VodScreenContent` had a `LaunchedEffect(currentRow)` that fired on the default `currentRow=0` after remount, bumping `resetTrigger` and yanking `focusedRowIndex` back to 1 (slider) — overwriting any restoration state set milliseconds earlier
  4. `TopMenuScreen2` had a `LaunchedEffect(currentRow)` (line ~1607) that on `currentRow==0` requestFocus()ed the menu tab — hijacking any focus we tried to set on a poster
  5. The "fixed focus position" pattern (`focusedColIndex == 0` always for regular channels, visual focus tracks `lazyListState.firstVisibleItemIndex`) only allocates real `FocusRequester`s for col=0 / col=-1; pre-allocating for all columns broke navigation by triggering `onFocusChanged` callbacks that flipped `focusedColIndex` to non-zero and broke the visual indicator condition
- **Solution**:
  1. **KinoGridScreen — synchronous initial state**: Extracted `computeKinoGridInitialState()` that replicates the filter logic; `var ... by remember { mutableStateOf(initState.X) }` for all derived state; `rememberLazyGridState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)` for the grid scroll. From frame 1, the right poster is focused, scroll is at the right Y, no `LaunchedEffect` race
  2. **Kino Play overlay via `movableContentOf`**: Wrapped the entire TopMenuScreen2 invocation (with all its callbacks and the PIP `DisposableEffect`) in `movableContentOf { ... }`. Rendered both for `currentScreen == TOP_MENU2` and as base layer when `currentScreen == MOVIE_DETAIL && previousScreen == TOP_MENU2`. The instance moves between positions without unmounting — all `remember` state survives
  3. **Refocus signal `VodDataCache.kinoPlayRefocusTrigger: MutableState<Int>`**: Incremented by `MainActivity.MovieDetail.onBackPressed` (when source was Kino Play). VodWithChannels and VodHeroSliderV4 watch it
  4. **Channel refocus = focus the outer Box, not a specific item**: Added `rootBoxFocusRequester` on the Box that owns `onPreviewKeyEvent { handleVodNavigation(...) }`. After overlay close, requestFocus on the Box — keys flow, the existing `isItemFocused == firstVisibleItemIndex` pattern keeps the visual focus on the saved poster automatically (because the LazyRow's scroll position was preserved)
  5. **Slider refocus + trailer suppression**: New `refocusTriggerKey: Int` parameter on `VodHeroSliderV4`; second `LaunchedEffect(refocusTriggerKey)` re-grabs focus on the slider's internal FR (the existing `LaunchedEffect(isFocused)` only fires on transitions, useless when isFocused stays true across overlay). Same trigger sets `suppressTrailerAfterOverlayBack=true`, lifted on first user interaction — user lands on a static poster, not an unexpected re-playing trailer
  6. **Anti-hijack**: In every refocus branch, also push `globalFocusState.value.copy(currentRow = focusedRowIndex)` so `currentRow != 0` and TopMenuScreen2's menu-tab refocus effect doesn't fire
  7. **`hasSeenFirstCurrentRow` flag in `VodScreenContent`**: When `VodDataCache.savedKinoPlayFocus != null` on mount, skip the FIRST `LaunchedEffect(currentRow)` fire so the default `currentRow=0` doesn't bump resetTrigger and yank focus back to slider
- **Result**:
  - ✅ Grid: exact poster from frame 1, no mignięcie, picker scrollable to fullscreen, all categories from chips visible (collections + genres + Nowości 🔥)
  - ✅ Kino Play tab: BACK from MovieDetail returns to exact channel + exact visible poster + working arrow navigation
  - ✅ Slider BACK: focus restored, trailer not auto-restarted
  - ✅ Filter persistence across MovieDetail (selectedCategory survives via `onCategoryChanged` callback to MainActivity)
  - ✅ Title in grid header reflects active filter (`"Wszystkie filmy"` for All, otherwise selectedCategory)
  - ✅ ENTER on poster (grid + Kino Play) opens MovieDetail with correctly mapped VodSlideData
- **Failed approaches** (documented to prevent repeats):
  - Pre-allocating 60 FRs per channel — broke `focusedColIndex == 0` pattern, navigation got stuck on second column
  - `LazyListState(initialFirstVisibleItemIndex = saved)` + dynamic FR registration + scrollToItem — multiple races, focus appeared briefly then disappeared
  - Channel-only restoration without overlay (savedKinoPlayFocus + remount) — channel restored but not poster, plus other state lost
- **Files**: `KinoGridScreen.kt` (massive rewrite), `MainActivity.kt` (movableContentOf restructure), `TopMenuScreen2.kt` (VodWithChannels rootBoxFocusRequester, VodHeroSliderV4 refocusTriggerKey, VodScreenContent hasSeenFirstCurrentRow), `VodDataCache.kt` (kinoChannelMap, savedKinoPlayFocus data class, kinoPlayRefocusTrigger), `components/VerticalVodCard.kt` (onPreviewKeyEvent for Enter, larger dimensions)
- **Documentation**: `docs/patterns/KINO_GRID_AND_OVERLAY_FOCUS_PATTERN.md` (full pattern guide with failed approaches and quick-reference for adding overlay restoration to a new section)
- **Lesson**: When state preservation across navigation is critical, **`movableContentOf` is the right Compose primitive** — better than lifting state up or trying to re-create it via async restoration. For "I need keys to work but the focused item may be off-screen", **focus the parent container that owns the key handler**, not the specific item.

#### **Key Learnings**
1. **Delegation Pattern**: Sections with complex multi-row navigation (MOJE, START, APLIKACJE, VOD) should delegate ALL keys to child components
2. **Callback Pattern**: Child components use `onReturnToMenu` callback for menu transitions instead of parent intercepting keys
3. **Consistency**: All similar sections should follow the same pattern for maintainability
4. **Documentation**: Critical patterns must be documented in CLAUDE.md with examples and checklists
5. **Monolithic Handlers Are Anti-patterns**: Extract to specialized controllers (Focus Architect principle) - prevents conflicts, improves maintainability, reduces bugs
6. **Modal Dialogs Require Input Gating**: Never delegate ALL keys blindly - use whitelist validation to prevent unexpected keys from leaking to background (PIP Dialog pattern)
7. **External Indicator Pattern**: When using external indicators (AnimatedFocusIndicator), the button component should NOT render its own border/fill for focus/selected states - prevents duplicate indicators
8. **Layout Formula Bugs Maskowane przez Design Tokens**: Pre-existing bugs w funkcjach pozycjonowania (`calculateVodChannelYPosition` itp.) mogą być ukryte gdy wartości "podobne" do siebie (np. 278 vs 346). Zmiana jednej constanty ujawnia overlap. Sprawdzaj formuły **iteracyjne** uważnie po każdej zmianie tokenu rozmiaru.
9. **LazyListState Propagation**: `LaunchedEffect` auto-reset scrolla w parent działa **tylko** dla children które używają passed-down `lazyListState`. Custom child composables tworzące własny `LazyListState()` są pomijane. Zawsze propaguj `lazyListState` przez parametr.
10. **`movableContentOf` for cross-navigation state preservation**: Gdy ekran musi przetrwać nawigację do detail view (np. poster grid → MovieDetail → BACK z exact-poster restoration), zamiast unmount/remount + asynchroniczny restoration, użyj `movableContentOf` w `MainActivity`'s `when (currentScreen)`. Render z dwóch pozycji w drzewie kompozycji zachowuje **tę samą** instancję ze stanem nienaruszonym. Patrz Issue #10.
11. **Focus the container, not the item**: Gdy musisz przywrócić obsługę klawiszy do parent który ma `onPreviewKeyEvent`, ale dany focusable item może być off-screen (LazyRow lazy unmount), `requestFocus()` na **parent Box's `FocusRequester`** zamiast specific item. Visual focus indicator może osobno śledzić `firstVisibleItemIndex`.
12. **Synchronous initial state >>> async restoration**: Jeśli dane są dostępne synchronicznie (np. `VodDataCache` cache), seed `remember { mutableStateOf(...) }` od pierwszej klatki zamiast `LaunchedEffect { delay; setState; requestFocus }`. Eliminuje wszystkie race conditions z internal Compose mechanics (LazyRow lazy composition, animacje, recomposition cascades).

---

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

## TELEWIZJA EPG Channels System (2025-10-17)

### 📋 System Overview

**Purpose**: Display EPG (Electronic Program Guide) content from last 24 hours organized by content type with descriptive two-line channel headers.

### 📺 Channel Structure

TELEWIZJA section contains 12 channels organized as follows:

#### **Row 0: Slider Mix** (Type: `slider-max`)
- Large hero slider with mixed VOD content
- Full-width presentation

#### **Row 1: Teraz w TV** (Type: `horizontal`)
- Current live programs from all channels
- Data source: `EpgRepository.getCurrentProgram()` for 10 channels
- Updates in real-time based on current time

#### **Rows 2-5: EPG Content Channels** (Type: `horizontal`)
All use **two-line naming format** with EPG data from last 24 hours:

1. **"FILMY, dzis były w TV"**
   - Feature-length movies (90+ minutes)
   - Data source: `EpgRepository.getLast24HoursMovies()`
   - Filter: duration ≥ 90 min, categories contain "film"

2. **"SERIALE, dzis były w TV"**
   - TV series episodes
   - Data source: `EpgRepository.getLast24HoursSeries()`
   - Filter: categories contain "serial" OR description has S[X]E[Y]/E[N] format
   - **Excluded categories**: magazyn, poradnik, teleturniej, rozrywka, rozrywkowy, show, reality show, talk-show, informacyjny, wiadomości, news

3. **"SPORT, dzis było w TV"**
   - Sports events and matches
   - Data source: `EpgRepository.getLast24HoursSports()`
   - Filter: categories match sport keywords (mecz, liga, puchar, etc.)
   - **Excluded keywords**: "turniej" (removed to prevent catching "teleturniej")

4. **"TELETURNIEJE, dzis były w TV"**
   - Game shows only
   - Data source: `EpgRepository.getLast24HoursGameShows()`
   - Filter: categories contain "teleturniej"

#### **Rows 6-11: TV Channel Collections** (Type: `app-icons`)
- "Moja lista kanałów" - User's favorite channels
- "Wszystkie kanały" - All available channels
- "Dla dzieci" - Kids channels
- "Dokumenty" - Documentary channels
- "Filmy i seriale HBO" - HBO content
- "Informacyjne" - News channels

### 🎨 Visual Design: EPG Channels

EPG channels (rows 2-5) use **text-only CategoryIcon** style:

```kotlin
// CategoryIcon configuration for EPG channels
val isEpgChannel = channel in listOf(
    "Teraz w TV",
    "FILMY, dzis były w TV",
    "SERIALE, dzis były w TV",
    "SPORT, dzis było w TV",
    "TELETURNIEJE, dzis były w TV"
)

CategoryIcon(
    text = channel,
    showIcon = false,  // No icon, text only
    showBackgroundWhenFocused = true  // Black background when focused
)
```

**Text properties** (Version001Screen.kt:703-712):
- Font size: 24sp (scaled)
- Font weight: Medium (500)
- Color: #EEEEEE (white)
- Max width: 200px
- **Max lines: 2** (enables two-line wrapping)
- Text align: Center
- Line height: 1.33

**Visual appearance**:
- Unfocused: Black background (rgba(0, 0, 0, 0.10)), no border
- Focused: Darker black (rgba(0, 0, 0, 0.30)), aqua border (#5AECD3)
- Same dimensions as regular CategoryIcon: 240x216px

### 🗂 Data Mapping

**File**: `TopMenuScreen2.kt`

**Channel names** (line 1513-1526):
```kotlin
val channels = listOf(
    "Slider Mix",
    "Teraz w TV",
    "FILMY, dzis były w TV",      // EPG: movies
    "SERIALE, dzis były w TV",     // EPG: series
    "SPORT, dzis było w TV",       // EPG: sports
    "TELETURNIEJE, dzis były w TV", // EPG: game shows
    "Moja lista kanałów",
    "Wszystkie kanały",
    "Dla dzieci",
    "Dokumenty",
    "Filmy i seriale HBO",
    "Informacyjne"
)
```

**Channel types mapping** (line 1529-1544):
```kotlin
val channelTypes = mapOf(
    "Slider Mix" to "slider-max",
    "Teraz w TV" to "horizontal",
    "FILMY, dzis były w TV" to "horizontal",
    "SERIALE, dzis były w TV" to "horizontal",
    "SPORT, dzis było w TV" to "horizontal",
    "TELETURNIEJE, dzis były w TV" to "horizontal",
    "Moja lista kanałów" to "app-icons",
    // ... other channels
)
```

**Grid content sources** (line 1546-1620):
```kotlin
val gridContent = remember(terazWTvPrograms, najczesciejMovies, serialePrograms, sportPrograms, teleturniejePrograms) {
    channels.associateWith { channelName ->
        when (channelName) {
            "Teraz w TV" -> terazWTvPrograms  // Current live programs
            "FILMY, dzis były w TV" -> najczesciejMovies  // Last 24h movies
            "SERIALE, dzis były w TV" -> serialePrograms  // Last 24h series
            "SPORT, dzis było w TV" -> sportPrograms  // Last 24h sports
            "TELETURNIEJE, dzis były w TV" -> teleturniejePrograms  // Last 24h game shows
            // ... other channels
        }
    }
}
```

### 🔍 EPG Data Filtering

**File**: `EpgRepository.kt`

#### Movies Filter (line 98-154)
```kotlin
suspend fun getLast24HoursMovies(): List<EpgProgram> {
    val movies = allPrograms.filter { program ->
        // Duration ≥ 90 minutes (feature-length)
        val duration = Duration.between(program.startUtc, program.endUtc).toMinutes()
        val isFeatureLength = duration >= 90

        // Categories contain "film"
        val hasMovieCategory = program.categories.any {
            it.lowercase().contains("film")
        }

        hasMovieCategory && isFeatureLength
    }

    return movies
        .sortedByDescending { it.startUtc }
        .distinctBy { it.title.lowercase().trim() }
        .take(10)
}
```

#### Series Filter (line 157-216)
```kotlin
suspend fun getLast24HoursSeries(): List<EpgProgram> {
    val unwantedCategories = setOf(
        "rozrywka", "rozrywkowy", "show", "reality show", "talk-show",
        "informacyjny", "wiadomości", "news",
        "magazyn", "poradnik", "teleturniej"
    )

    val series = allPrograms.filter { program ->
        // 1. Category contains "serial"
        val hasSeriesCategory = program.categories.any {
            it.lowercase().contains("serial")
        }

        // 2. OR description contains S[X]E[Y] or E[N]
        val hasEpisodeInfo = program.description?.let { desc ->
            desc.contains(Regex("S\\d+E\\d+")) || desc.contains(Regex("E\\d+"))
        } ?: false

        // 3. Exclude unwanted categories
        val noUnwantedCategories = program.categories.none { category ->
            unwantedCategories.any { unwanted ->
                category.lowercase().contains(unwanted)
            }
        }

        // 4. Duration > 20 minutes
        val duration = Duration.between(program.startUtc, program.endUtc).toMinutes()
        val isEpisodeLength = duration > 20

        (hasSeriesCategory || hasEpisodeInfo) && noUnwantedCategories && isEpisodeLength
    }

    return series
        .sortedByDescending { it.startUtc }
        .distinctBy { it.title.lowercase().trim() }
        .take(10)
}
```

#### Sports Filter (line 219-261)
```kotlin
suspend fun getLast24HoursSports(): List<EpgProgram> {
    val sportKeywords = setOf(
        "sport", "mecz", "match", "rozgrywki",
        "piłka nożna", "football", "soccer", "tenis", "tennis",
        "siatkówka", "volleyball", "koszykówka", "basketball",
        "liga", "puchar", "mistrzostwa", "championship",
        "formuła", "formula", "wyścig", "race", "golf",
        "hokej", "hockey", "boks", "boxing", "rugby",
        "skoki", "jumping", "narciarstwo", "skiing"
        // NOTE: "turniej" removed to prevent catching "teleturniej"
    )

    val sports = allPrograms.filter { program ->
        program.categories.any { category ->
            sportKeywords.any { keyword ->
                category.lowercase().contains(keyword)
            }
        }
    }

    return sports
        .sortedByDescending { it.startUtc }
        .distinctBy { it.title.lowercase().trim() }
        .take(10)
}
```

#### Game Shows Filter (line 265-302)
```kotlin
suspend fun getLast24HoursGameShows(): List<EpgProgram> {
    val gameShowCategories = setOf("teleturniej")

    val gameShows = allPrograms.filter { program ->
        program.categories.any { category ->
            gameShowCategories.any { gameShowCategory ->
                category.lowercase().contains(gameShowCategory)
            }
        }
    }

    return gameShows
        .sortedByDescending { it.startUtc }
        .distinctBy { it.title.lowercase().trim() }
        .take(10)
}
```

### 🎯 Key Design Decisions

1. **Two-line naming**: Provides context ("dzis były/było w TV") while maintaining clean visual hierarchy
2. **Text-only style**: Matches WIDEO section pattern (Seriale, Filmy fabularne) - no icons, just text
3. **Last 24h window**: Shows recently aired content that users might want to catch up on
4. **Strict filtering**: Prevents content misclassification (e.g., game shows in sports, magazines in series)
5. **Deduplication**: `distinctBy { title }` ensures same program doesn't appear multiple times

### 💾 Backup Files
- `TV_componenty_backup_20251017_104642` - Two-line EPG channels implementation

### 🛠 Implementation Files
- **TopMenuScreen2.kt** (lines 1513-1620, 6290): Channel configuration and grid content
- **Version001Screen.kt** (lines 597-716): CategoryIcon component with maxLines support
- **EpgRepository.kt** (lines 98-302): EPG data filtering logic
- **EpgAdapter.kt**: EPG to VodContent conversion

## Expandable Channels Pattern (NAGRANIA Implementation) 🆕

**Status**: ✅ Production-ready (MOJE section)
**Pattern Guide**: [`docs/patterns/EXPANDABLE_CHANNELS_PATTERN.md`](docs/patterns/EXPANDABLE_CHANNELS_PATTERN.md)
**Implementation Date**: 2025-11-05
**Git Commit**: `f0fb65a`

### Quick Reference

Wzorzec dynamicznego wstawiania sub-kanałów jako oddzielnych pełnowymiarowych rzędów (NIE zagnieżdżonych komponentów).

**4 Kluczowe zasady:**
1. **Sub-kanały = oddzielne rzędy** - `remember(isExpanded)` generuje listę 5→8 kanałów
2. **FocusRequestery = stabilna mapa** - `remember { }` bez key, `repeat(MAX_CHANNELS)`
3. **Conditional assignment = focus window** - Tylko `firstVisibleItemIndex` otrzymuje real FocusRequester
4. **Auto-collapse = nawigacja graniczna** - UP/DOWN przy brzegach sub-kanałów → collapse + fokus na parent

### Lokalizacje w kodzie

**TopMenuScreen2.kt:**
- Lines **2413-2439**: Setup (MAX_MOJE_CHANNELS, isExpanded, dynamic channel list)
- Lines **2482-2500**: Stable FocusRequesters & LazyListStates
- Lines **4205-4233**: Conditional FocusRequester assignment (focus window pattern)
- Lines **4509-4665**: handleMojeChannelsNavigation (complete navigation logic)

**Backup**: `TopMenuScreen2_backup_before_triple_keys_20251105_144749.kt` (zawiera failed nested approach - reference co NIE robić)

### Przykładowe zastosowania

- ✅ **MOJE - Nagrania**: Pojedyncze/Serie/Zaplanowane (5→8 kanałów) - **DONE**
- 🚧 **APLIKACJE - Gry**: Gry akcji/sportowe/edukacyjne (future)
- 🚧 **VOD - Playlisty**: Gatunki filmowe (future)

### Common Pitfalls

❌ **NIE używaj**: Nested SubChannel components, Triple keys, `remember(channels.size)`
❌ **NIE przypisuj**: Tego samego FocusRequestera do wszystkich LazyRow items
❌ **NIE zapominaj**: O `delay(50)` przed requestFocus() po collapse

✅ **Zawsze używaj**: Dynamic list insertion, stable maps, conditional assignment, auto-collapse callbacks

**Szczegółowa dokumentacja**: Przeczytaj `docs/patterns/EXPANDABLE_CHANNELS_PATTERN.md` przed implementacją w nowej sekcji.

---

## HOME Button Navigation Pattern (Launcher Integration) 🆕

**Status**: ✅ Production-ready (wersja 3.12.0)
**Pattern Guide**: [`docs/patterns/HOME_BUTTON_NAVIGATION_PATTERN.md`](docs/patterns/HOME_BUTTON_NAVIGATION_PATTERN.md)
**Implementation Date**: 2025-11-18

### Quick Reference

Pełna obsługa przycisku HOME na Android TV z inteligentną nawigacją PIP (Picture-in-Picture), identyczną jak klawisz "0".

**3 Kluczowe komponenty:**
1. **LauncherActivity.onNewIntent()** - Standard Android API do wykrywania HOME button w launcherze
2. **State Flag Communication** - `shouldNavigateHomeWithPip` jako trigger MainActivity → EpgDayScreen
3. **Infrastructure Reuse** - `onNavigateToPipMode()` callback wykorzystany z klawisza "0"

### Lokalizacje w kodzie

**LauncherActivity.kt:**
- Lines **120-134**: `onNewIntent()` - wykrywanie HOME button, incrementacja `homePressedTrigger`

**MainActivity.kt:**
- Line **318**: `shouldNavigateHomeWithPip` state flag
- Lines **327-368**: `LaunchedEffect(homePressedTrigger)` - 3-scenariuszowa logika nawigacji
- Lines **630-631**: Przekazanie parametrów do EpgDayScreen

**EpgDayScreen.kt:**
- Lines **125-126**: Function signature z nowymi parametrami
- Lines **226-241**: `LaunchedEffect(shouldNavigateHomeWithPip)` - transfer playera do PIP

### Scenariusze nawigacji

1. **Fullscreen EPG → HOME** → TopMenu2 START z PIP (jak klawisz "0")
2. **TopMenu2 z PIP → HOME** → Fullscreen EPG (zamknięcie PIP)
3. **Netflix/inne → HOME** → TopMenu2 START (standardowy launcher)

### Kluczowe wzorce

**State Flag Communication:**
```kotlin
// MainActivity - sender
var shouldNavigateHomeWithPip by remember { mutableStateOf(false) }

// EpgDayScreen - receiver
LaunchedEffect(shouldNavigateHomeWithPip) {
    if (shouldNavigateHomeWithPip) {
        isPipMode = true
        onNavigateToPipMode(player, streamUrl)
        onHomeNavigationComplete()  // CRITICAL: always reset flag
    }
}
```

**Callback Reset Pattern:**
```kotlin
onHomeNavigationComplete = { shouldNavigateHomeWithPip = false }
```

### Wymagania systemowe

- ✅ Aplikacja MUSI być ustawiona jako domyślny launcher
- ✅ AccessibilityService (opcjonalny, dodatkowa warstwa)
- ✅ `android:launchMode="singleTask"` w AndroidManifest.xml

**Ustawienie jako launcher:**
```bash
adb shell cmd role add-role-holder android.app.role.HOME com.uxellence.tv.v3
```

### Common Pitfalls

❌ **NIE zapomnij**: Wywołać `onHomeNavigationComplete()` - ryzyko infinite loop
❌ **NIE używaj**: Direct callbacks zamiast state flags - gorsze separation of concerns
❌ **NIE zakładaj**: Że HOME działa bez ustawienia jako launcher

✅ **Zawsze sprawdzaj**: Czy `player != null` przed transferem do PIP
✅ **Zawsze używaj**: `isPipMode = true` flag przed `onNavigateToPipMode()`
✅ **Zawsze testuj**: Wszystkie 3 scenariusze nawigacji

**Szczegółowa dokumentacja**: Przeczytaj `docs/patterns/HOME_BUTTON_NAVIGATION_PATTERN.md` - zawiera flow diagrams, debugging tips, FAQ.

---

## MOJE Section Spacing System (2025-09-29)

### 📋 System Documentation

**Zasada**: Wysokość wiersza (CategoryIcon 216px) + odstęp między wierszami

### 📊 Stałe systemu:
```kotlin
private const val MOJE_FIXED_FOCUS_Y = 340 // Zfokusowany wiersz zawsze na tej wysokości
private const val MOJE_NORMAL_ROW_HEIGHT = 256 // CategoryIcon (216px) + odstęp (40px)
private const val MOJE_EXPANDED_ROW_HEIGHT = 546 // CategoryIcon (216px) + miniaturki (290px) + odstęp (40px)
private const val MOJE_CONTENT_FOCUS_EXTRA_SPACING = 100 // Dodatkowe odsunięcie nad zfokusowanym wierszem z treścią
```

### 🔄 Scenariusze:
- **Fokus na CategoryIcon**: 40px odstęp między wierszami
- **Fokus na treści**: dodatkowo 100px odsunięcie nad zfokusowanym wierszem
- **Wiersze nie nachodzą** na siebie - każdy ma pełną wysokość + odstęp

### 💾 Backup Files:
- `TopMenuScreen2_backup_moje_spacing_system_20250929_100436.kt` (52K)
- `app-debug-moje-spacing-system-20250929_100446.apk` (604M)

### 🛠 Implementacja:
- Plik: `TopMenuScreen2.kt:930` - funkcja `calculateMojeChannelYPosition`
- Wzorowana na Version001Screen z dostosowanymi wartościami
- Automatyczne wykrywanie fokusa: CategoryIcon (`focusedColIndex == -1`) vs treść (`focusedColIndex >= 0`)

---

## 📋 Channel Type Implementation Guides

### Quick Reference - Template Prompts

When adding channels of specific types, use these template prompts to avoid multiple iterations:

#### App-Icons Channel
```
"Dodaj kanał '[NAZWA]' typu app-icons do sekcji [SECTION].
PRZED IMPLEMENTACJĄ: Przeczytaj docs/patterns/APP_ICONS_CHANNEL_CHECKLIST.md
Wzoruj się na: TELEWIZJA app-icons (TopMenuScreen2.kt:8310-8395)"
```

**Detailed checklist**: [`docs/patterns/APP_ICONS_CHANNEL_CHECKLIST.md`](docs/patterns/APP_ICONS_CHANNEL_CHECKLIST.md)

### Available Checklists

| Channel Type | Checklist | Key Requirements |
|-------------|-----------|------------------|
| **app-icons** | `APP_ICONS_CHANNEL_CHECKLIST.md` | ChannelListCard 208x208, direct focus, NO expansion (256=256), CategoryIcon fade |
| horizontal | *(in channel-type-specialist)* | Standard miniatures with slide-down animation |
| vertical | *(in channel-type-specialist)* | Poster-style cards (198x286) |
| shortcuts | *(in channel-type-specialist)* | Fixed height (150px), no expansion |

### Why Use Checklists?

Without checklist: **3+ iterations** of fixes (wrong component, missing constants, missing features)
With checklist: **1 prompt, 0 fixes** (all requirements documented upfront)

---

## 🤖 AVAILABLE SKILLS & AGENTS

When working on this project, Claude should reference these expert agents as needed:

### 🔧 Channel Type Specialist
**File:** `skills/channel-type-specialist/SKILL.md`
**Use when:**
- Adding or modifying any of 8 channel types + variants (horizontal, vertical, app-icons, top10, slider-max, collection-slider, shortcuts, shortcuts-v3, epg-channels)
- Working with positioning algorithms (calculateYPosition functions)
- Troubleshooting focus management or navigation issues
- Understanding 3-layer architecture (Component/Row/Positioning)
- Implementing channel-specific animations or behaviors

### 🎨 Layout Engineer
**File:** `skills/layout-engineer/SKILL.md`
**Use when:** 
- Converting Figma designs to Compose code
- Working with responsive scaling (sx/sy functions)
- Positioning elements precisely on screen
- Implementing visual design requirements from mockups
- Calculating proper spacing and dimensions

### 🎯 Focus Architect
**File:** `skills/focus-architect/SKILL.md`
**Use when:** 
- Navigation problems or focus conflicts arise
- Key event handling issues (BACK, UP, DOWN, LEFT, RIGHT)
- Focus state management and FocusRequester coordination
- D-pad navigation optimization for Android TV
- Debugging callback delegation patterns

### 📦 Version Control Workflow
**File:** `skills/version-control-workflow/SKILL.md`
**Use when:** 
- Committing changes or creating branches
- Version control operations needed
- ALWAYS use instead of manual backups!
- Git workflow guidance or best practices needed
- Creating structured commit messages

---

**Note for Claude Code**: When a task relates to any of the above areas, automatically read the relevant SKILL.md file to get expert guidance before proceeding with implementation. These Skills contain specialized knowledge, patterns, and best practices specific to this project's architecture.

---

## 🔍 SKILL DISCOVERY & EXPANSION

### Purpose
This section tracks potential areas that might benefit from dedicated Skills/Agents as the project evolves.

### Areas Under Consideration

#### 🎬 VoD Content Management System
**Status**: 🟡 Candidate for Skill
**Complexity**: Medium-High
**Why**: 
- CSV parsing with multiple formats (semicolon vs comma)
- Data transformation (VodContent model)
- Cache management system
- Multiple data sources (seriale_playnow, vod_data.csv)
**Current files**: `VodAdapter.kt`, cache system
**Decision**: Monitor - if parsing issues arise frequently, create Skill

#### 📊 EPG Data System
**Status**: 🟡 Candidate for Skill  
**Complexity**: High
**Why**:
- XML-TV parsing (64MB+ files)
- Time-based filtering (last 24h)
- Category detection (movies, series, sports, game shows)
- Channel aggregation logic
**Current files**: `EpgRepository.kt`, `XmlTvParser.kt`, `EpgAdapter.kt`
**Decision**: Monitor - complex domain logic may warrant dedicated agent

#### 🎨 Animation Choreography System
**Status**: 🟢 Low Priority
**Complexity**: Medium
**Why**:
- Slide-down animations (290px)
- Timing coordination (350ms delays)
- Synchronization between components
- Auto-reset behaviors
**Current files**: `Version001Screen.kt`, `TopMenuScreen2.kt`
**Decision**: Well-documented in existing files, create Skill only if animation bugs become frequent

#### 🎨 Design System & Theming
**Status**: 🟢 Low Priority
**Complexity**: Low-Medium
**Why**:
- Color palette management (#48227C, #5AECD3, etc.)
- Responsive scaling (sx/sy functions)
- Component sizing standards
**Current files**: Theme files, utility functions
**Decision**: Layout Engineer Skill already covers this partially

#### 🧪 TV Component Testing Patterns
**Status**: 🔴 Future Consideration
**Complexity**: Medium
**Why**:
- D-pad navigation testing
- Focus state verification
- TV-specific test utilities
**Current status**: Not yet implemented
**Decision**: Create Skill when test suite development begins

---

### When to Create a New Skill

**Create a Skill when:**
✅ System/pattern used in **3+ different places**
✅ Complex logic with **multiple edge cases** that require deep understanding
✅ Requires **specialized domain knowledge** (e.g., Android TV APIs, EPG standards)
✅ Area is **frequently debugged** or causes repeated issues
✅ New team members would benefit from **expert guidance** in this area
✅ Logic has **evolved significantly** and needs consolidated documentation

**Don't create a Skill when:**
❌ Pattern is simple and self-explanatory
❌ Used in only 1-2 places
❌ Already well-covered by existing Skill
❌ Logic is stable and rarely touched

---

### How to Propose a New Skill

**Step 1: Analysis Request**
```
"Analyze [System Name] - should this become a dedicated Skill?
- Where is it used?
- How complex is the logic?
- What are common issues?
- Would a Skill provide value?"
```

**Step 2: Skill Creation** (if approved)
```
"Create a new Skill for [System Name]:
1. Create skills/[system-name]/SKILL.md
2. Document: purpose, patterns, edge cases, examples
3. Update CLAUDE.md with new Skill reference
4. Create ZIP package for Claude Desktop"
```

**Step 3: Testing**
```
"Use the new [System Name] Skill to solve [specific problem]"
```

---

### Skill Maintenance Log

**2025-10-20**: Initial Skills setup
- ✅ Channel Type Specialist
- ✅ Layout Engineer
- ✅ Focus Architect
- ✅ Version Control Workflow
- 📝 Discovery system established

**Future additions**: (track here when new Skills are created)

---

### Quick Discovery Command

To scan for undocumented systems that might need Skills:
```
"Discovery Agent: Scan project for:
1. Repeated patterns across multiple files
2. Complex systems without dedicated documentation  
3. Areas with frequent bug fixes
4. Suggest top 3 candidates for new Skills"
```