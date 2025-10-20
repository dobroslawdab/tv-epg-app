# Channel Implementation Checklist

**Version**: 1.0
**Date**: 2025-10-14
**Target**: TopMenuScreen2.kt

---

## Overview

This checklist provides a step-by-step guide for adding new channel rows to any section (MOJE, START, APLIKACJE, ODKRYWAJ, TELEWIZJA, VOD). Follow each step carefully to ensure proper positioning, navigation, and visual consistency.

**📖 Companion Document**: See `CHANNEL_SYSTEM.md` for complete specifications of all 8 channel types.

---

## Pre-Implementation Planning

### Step 1: Define Channel Requirements

Before starting implementation, answer these questions:

**Content Questions:**
- [ ] What type of content will this channel display? (movies, series, apps, etc.)
- [ ] What data source provides the content? (API, local database, static list)
- [ ] How many items will be displayed? (10, 20, unlimited scrolling)
- [ ] Does content need real-time updates? (live TV, recently played)

**Visual Questions:**
- [ ] Which channel type best fits the content?
  - 16:9 thumbnails → `horizontal`
  - Portrait posters → `vertical`
  - App icons → `app-icons`
  - Ranked content → `top10`
  - Hero banners → `slider-max`
  - Large collections → `collection-slider`
  - Action buttons → `shortcuts`
  - TV channels → `epg-channels`
- [ ] Does the channel need expansion/miniatures? (Most do, except app-icons, slider-max, collection-slider)
- [ ] What is the channel name/label? (Must fit in CategoryIcon: 210x216px)

**Navigation Questions:**
- [ ] Which section will contain this channel? (MOJE, START, APLIKACJE, ODKRYWAJ, VOD, TELEWIZJA)
- [ ] What position in the list? (First, middle, last)
- [ ] Does the channel need special navigation behavior? (e.g., auto-play, infinite scroll)

**✅ Proceed to implementation only after all questions are answered**

---

## Implementation Steps

### Phase 1: Component Level

#### Step 2: Verify/Create Card Component

**Location**: `TopMenuScreen2.kt` (lines ~6850-8000)

**Check if component exists:**
- [ ] Search for existing component: `@Composable fun [Type]Card`
- [ ] If exists: verify dimensions match your requirements
- [ ] If not exists: create new component following template below

**Component Template:**
```kotlin
@Composable
fun [Type]Card(
    title: String,
    imageUrl: String?,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp,
    onClick: () -> Unit = {},
    onFocused: (Boolean) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .width(sx([WIDTH]))          // e.g., sx(368)
            .height(sy([HEIGHT]))        // e.g., sy(208)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocused(it.isFocused) }
            .clickable { onClick() }
            .background(
                color = if (isFocused) Color(0xFF5AECD3) else Color(0x1AEEEEEE),
                shape = RoundedCornerShape(sx(8))
            )
    ) {
        // Card content (image, text, etc.)
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Title overlay (if needed)
        Text(
            text = title,
            color = if (isFocused) Color(0xFF48227C) else Color(0xFFEEEEEE),
            fontSize = (16 * sx(1)).value.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(sx(8))
        )
    }
}
```

**Dimensions Reference:**
| Type              | Width × Height | Spacing |
|-------------------|----------------|---------|
| horizontal        | 368 × 208      | 20px    |
| vertical          | 288 × 432      | 20px    |
| app-icons         | 320 × 190      | 12px    |
| top10             | 432 × 288      | 20px    |
| shortcuts         | 210 × 279      | 20px    |
| epg-channels      | 368 × 208      | 20px    |

**Testing:**
- [ ] Component compiles without errors
- [ ] Focus state changes visual appearance (border, background color)
- [ ] Dimensions match specification in `CHANNEL_SYSTEM.md`
- [ ] Content scales properly on different screen sizes

---

### Phase 2: Row Level

#### Step 3: Determine Row Configuration

**Questions:**
- [ ] Does this channel type expand on focus? (Check `CHANNEL_SYSTEM.md` expansion table)
- [ ] What spacing should be used? (12px for app-icons, 20px for most, 30px for collection-slider)
- [ ] Where should CategoryIcon be positioned? (Fixed at X=80px for all except slider-max)

**Configuration Checklist:**
- [ ] Determine `isAppIcons` condition (true only for "Ostatnio używane", "Aplikacje")
- [ ] Determine `isShortcuts` condition (true only for "Skróty")
- [ ] Determine miniatures behavior (enabled for most, disabled for app-icons/slider-max/collection-slider)
- [ ] Determine NORMAL_ROW_HEIGHT (depends on card height + spacing)
- [ ] Determine EXPANDED_ROW_HEIGHT (NORMAL + 290px for expandable types)

**Example Calculation:**
```
For horizontal channel:
- Card height: 208px
- CategoryIcon: 216px (taller, determines row height)
- Spacing: 40px
- NORMAL_ROW_HEIGHT = 216 + 40 = 256px
- EXPANDED_ROW_HEIGHT = 216 + 290 (miniatures) + 40 = 546px
```

#### Step 4: Add Channel to Data List

**Location**: Find the channels list for your section

**Example (MOJE section):**
```kotlin
// Around line 800-900
val mojeChannels = listOf(
    "Polecane",
    "Wznów oglądanie",
    "Ostatnio oglądane",
    "[YOUR NEW CHANNEL]"  // Add here
)
```

**Section-Specific Locations:**
- MOJE: `mojeChannels` (~line 800)
- START: `startChannels` (~line 2000)
- APLIKACJE: `aplikacjeChannels` (~line 3300)
- ODKRYWAJ: `odkrywajChannels` (~line 4800)
- VOD: `vodChannels` (~line 6100)
- TELEWIZJA: `telewizjaChannels` (~line 5500)

**Testing:**
- [ ] Channel appears in section's channel list
- [ ] Channel order is correct (matches your design)
- [ ] Channel name is spelled correctly

#### Step 5: Add Channel Type Detection

**Location**: Find `isAppIcons`/`isShortcuts` detection logic in UnifiedChannelRow

**Example:**
```kotlin
// Around line 4450 (APLIKACJE section)
val isAppIcons = channel == "Ostatnio używane" || channel == "Aplikacje"
val isShortcuts = channel == "Skróty"
val isTop10 = channel.startsWith("TOP 10")
// Add your detection:
val isYourType = channel == "[YOUR CHANNEL NAME]"
```

**Common Patterns:**
```kotlin
// Exact match
val isType = channel == "Channel Name"

// Prefix match
val isType = channel.startsWith("Prefix")

// Contains match
val isType = channel.contains("Keyword")

// Multiple channels
val isType = channel in listOf("Channel 1", "Channel 2")
```

**Testing:**
- [ ] Detection logic returns `true` for your channel
- [ ] Detection logic returns `false` for other channels
- [ ] No conflicts with existing detection logic

#### Step 6: Configure LazyRow Parameters

**Location**: UnifiedChannelRow LazyRow configuration (~line 4520)

**Update spacing logic:**
```kotlin
LazyRow(
    state = lazyListState,
    modifier = Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(
        start = sx(380),  // Standard for all
        end = sx(20)
    ),
    horizontalArrangement = Arrangement.spacedBy(
        when {
            isAppIcons -> sx(12)          // Tighter spacing
            isCollectionSlider -> sx(30)  // Wider spacing
            isYourType -> sx([SPACING])   // Your custom spacing
            else -> sx(20)                // Default
        }
    )
) { /* ... */ }
```

**Spacing Guidelines:**
- app-icons: 12px (minimal spacing for small icons)
- collection-slider: 30px (wider spacing for large banners)
- Default: 20px (standard spacing for most content)

**Testing:**
- [ ] Items are spaced correctly (measure in pixels or compare visually)
- [ ] First item starts at X=380px
- [ ] Last item has 20px margin from right edge

#### Step 7: Configure Spacer Dimensions

**Location**: Spacer dimensions logic (~line 4616)

**Update spacer logic:**
```kotlin
val spacerWidth = when {
    isShortcuts -> sx(210)
    isAppIcons -> sx(320)
    isYourType -> sx([WIDTH])
    else -> sx(368)
}
val spacerHeight = when {
    isShortcuts -> sy(279)
    isAppIcons -> sy(190)
    isYourType -> sy([HEIGHT])
    else -> sy(208)
}
```

**Purpose**: Spacers act as invisible placeholders when no content is available. Must match your card dimensions exactly.

**Testing:**
- [ ] Spacer dimensions match card dimensions
- [ ] Empty channels display proper spacing (test with empty data)

#### Step 8: Add Items to LazyRow

**Location**: LazyRow items block (~line 4650)

**Add item rendering:**
```kotlin
LazyRow(...) {
    // Existing items for other types...

    // Add your items:
    if (isYourType) {
        items(yourContent.size) { index ->
            val item = yourContent[index]
            val isFocused = rowIndex == focusedRowIndex && index == focusedColIndex
            val focusRequester = channelFocusRequesters[Pair(rowIndex, index)]

            if (focusRequester != null) {
                YourCard(
                    title = item.title,
                    imageUrl = item.imageUrl,
                    isFocused = isFocused,
                    focusRequester = focusRequester,
                    sx = sx,
                    sy = sy,
                    onClick = { /* Handle click */ },
                    onFocused = { isFocused ->
                        if (isFocused) {
                            onChannelContentFocusChange(rowIndex, index)
                        }
                    }
                )
            }
        }
    }
}
```

**Testing:**
- [ ] Items render correctly (image, text, layout)
- [ ] Focus moves between items with LEFT/RIGHT keys
- [ ] First item receives focus when navigating down from CategoryIcon
- [ ] Clicking item triggers correct action

#### Step 9: Add CategoryIcon with Logo Mapping

**Location**: CategoryIcon fixed positioning block (~line 4657)

**Add logo mapping:**
```kotlin
// Logo mapping for channels
val categoryLogo = when (channel) {
    "Ostatnio używane", "Aplikacje" -> R.drawable.appli
    "Netflix" -> R.drawable.netflix_logo
    "YouTube" -> R.drawable.youtube_logo
    "[YOUR CHANNEL]" -> R.drawable.your_logo  // Add your mapping
    else -> null
}
```

**Logo Requirements:**
- Size: ~200x200px recommended
- Format: PNG with transparency preferred
- Location: `app/src/main/res/drawable/`
- Naming: Use snake_case (e.g., `my_channel_logo.png`)

**Testing:**
- [ ] Logo appears in CategoryIcon
- [ ] Logo scales correctly (no distortion)
- [ ] Logo is visible in both focused and unfocused states

---

### Phase 3: Positioning System Level

#### Step 10: Add Positioning Constants

**Location**: Section constants block (e.g., APLIKACJE constants at ~line 3310)

**Add constants for your channel type (if new):**
```kotlin
// APLIKACJE Section constants (example)
private const val APLIKACJE_FIXED_FOCUS_Y = 340
private const val APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val APLIKACJE_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
// ... existing constants ...

// Add your new type:
private const val APLIKACJE_[TYPE]_NORMAL_ROW_HEIGHT = [HEIGHT]
private const val APLIKACJE_[TYPE]_EXPANDED_ROW_HEIGHT = [HEIGHT + 290 or same if no expansion]
```

**Calculation Guide:**
```
NORMAL_ROW_HEIGHT calculation:
1. Find the taller of: card height or CategoryIcon height (216px)
2. Add spacing:
   - 40px for horizontal-like channels
   - 130px for vertical-like channels (taller cards)
3. Result: NORMAL_ROW_HEIGHT

EXPANDED_ROW_HEIGHT calculation:
1. Start with NORMAL_ROW_HEIGHT
2. If channel expands (has miniatures):
   - Add 290px (miniatures slide-down distance)
3. If channel doesn't expand:
   - EXPANDED = NORMAL (same value)
4. Result: EXPANDED_ROW_HEIGHT
```

**Examples:**
```kotlin
// horizontal: card=208, CategoryIcon=216, spacing=40
NORMAL = 256
EXPANDED = 546 (+290 for miniatures)

// vertical: card=432, CategoryIcon=216, spacing=130
NORMAL = 346
EXPANDED = 636 (+290 for miniatures)

// app-icons: card=190, CategoryIcon=216, spacing=40, NO expansion
NORMAL = 256
EXPANDED = 256 (no change)
```

**Testing:**
- [ ] Constants are declared before `calculateXXXChannelYPosition` function
- [ ] Constants follow naming convention: `[SECTION]_[TYPE]_[NORMAL|EXPANDED]_ROW_HEIGHT`
- [ ] Values are integers (not floats)

#### Step 11: Update calculateYPosition Function

**Location**: `calculateXXXChannelYPosition` function for your section

**Update row height logic:**
```kotlin
private fun calculateAplikacjeChannelYPosition(
    rowIndex: Int,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    currentRow: Int,
    channels: List<String>,  // Add this parameter if not present
    sy: (Int) -> Dp
): Dp {
    // Determine row type for focused channel
    val focusedChannel = channels.getOrNull(focusedRowIndex)
    val focusedIsAppIcons = focusedChannel == "Ostatnio używane" || focusedChannel == "Aplikacje"
    val focusedIsShortcuts = focusedChannel == "Skróty"
    val focusedIsYourType = focusedChannel == "[YOUR CHANNEL]"  // Add detection

    // Determine heights based on type
    val normalRowHeight = when {
        focusedIsAppIcons -> APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT
        focusedIsShortcuts -> APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT
        focusedIsYourType -> APLIKACJE_[TYPE]_NORMAL_ROW_HEIGHT  // Add case
        else -> APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT
    }

    val expandedRowHeight = when {
        focusedIsAppIcons -> APLIKACJE_APP_ICONS_EXPANDED_ROW_HEIGHT
        focusedIsShortcuts -> APLIKACJE_SHORTCUTS_EXPANDED_ROW_HEIGHT
        focusedIsYourType -> APLIKACJE_[TYPE]_EXPANDED_ROW_HEIGHT  // Add case
        else -> APLIKACJE_HORIZONTAL_EXPANDED_ROW_HEIGHT
    }

    // ... rest of positioning logic remains the same
    return when {
        rowIndex == focusedRowIndex -> {
            sy(focusedChannelRelativeY)
        }
        rowIndex < focusedRowIndex -> {
            val extraSpacing = if (focusedColIndex >= 0) APLIKACJE_CONTENT_FOCUS_EXTRA_SPACING else 0
            sy(focusedChannelRelativeY - (focusedRowIndex - rowIndex) * normalRowHeight - extraSpacing)
        }
        rowIndex > focusedRowIndex -> {
            val focusedChannelExpansion = if (focusedColIndex >= 0) expandedRowHeight else normalRowHeight
            sy(focusedChannelRelativeY + focusedChannelExpansion + (rowIndex - focusedRowIndex - 1) * normalRowHeight)
        }
        else -> sy(rowIndex * normalRowHeight)
    }
}
```

**Key Changes:**
1. Add `channels: List<String>` parameter if not present
2. Add detection for your channel type
3. Add cases to `normalRowHeight` and `expandedRowHeight` when blocks
4. Positioning logic (the `when` block at the end) remains unchanged

**Testing:**
- [ ] Function compiles without errors
- [ ] Your channel type is detected correctly (add debug logs)
- [ ] Correct heights are used (add debug logs)

#### Step 12: Update Function Call Sites

**Location**: Where `calculateXXXChannelYPosition` is called (~line 4500)

**Update function call:**
```kotlin
// BEFORE (if channels parameter didn't exist):
val yPosition = calculateAplikacjeChannelYPosition(
    rowIndex = rowIndex,
    focusedRowIndex = focusedRowIndex,
    focusedColIndex = focusedColIndex,
    currentRow = channels.size,
    sy = sy
)

// AFTER (with channels parameter):
val yPosition = calculateAplikacjeChannelYPosition(
    rowIndex = rowIndex,
    focusedRowIndex = focusedRowIndex,
    focusedColIndex = focusedColIndex,
    currentRow = channels.size,
    channels = channels,  // Add this
    sy = sy
)
```

**Testing:**
- [ ] No compilation errors
- [ ] Function receives correct channel list (verify with logs)

---

### Phase 4: Animation Configuration

#### Step 13: Configure Slide-Down Animation

**Location**: Animation logic (~line 4509)

**Update animation condition:**
```kotlin
val miniaturesYOffset by animateDpAsState(
    targetValue = if (
        !isAppIcons &&
        !isYourTypeWithoutAnimation &&  // Add if your type doesn't animate
        isMiniaturesOnScreen
    ) sy(290) else sy(0),
    animationSpec = tween(
        durationMillis = 350,
        easing = androidx.compose.animation.core.EaseInOutCubic
    ),
    label = "miniatures_y_offset_$rowIndex"
)
```

**Animation Rules:**
- ✅ Enable for: horizontal, vertical, top10, shortcuts, epg-channels
- ❌ Disable for: app-icons, slider-max, collection-slider

**Testing:**
- [ ] Animation plays when navigating to content (from CategoryIcon)
- [ ] Animation duration is 350ms (feels smooth)
- [ ] Animation doesn't play for non-expandable types
- [ ] Miniatures slide down exactly 290px

#### Step 14: Configure Details Overlay Timing

**Location**: Details overlay LaunchedEffect (~line 4700)

**Verify timing logic:**
```kotlin
LaunchedEffect(isMiniaturesOnScreen) {
    if (isMiniaturesOnScreen && !isYourTypeWithoutAnimation) {
        delay(350)  // Wait for animation to complete
        showDetails = true
    } else {
        showDetails = false
    }
}
```

**Rules:**
- Details appear **after** 350ms delay (matches animation duration)
- Details don't appear for types without animation
- Details hide immediately when miniatures hide

**Testing:**
- [ ] Details appear after animation completes (not before)
- [ ] Details contain correct content (title, description, etc.)
- [ ] Details are positioned correctly (typically Y=290px)
- [ ] Details hide when focus moves away

---

### Phase 5: Navigation Integration

#### Step 15: Verify Navigation Handler

**Location**: Section navigation handler (e.g., `handleAplikacjeChannelsNavigation`)

**Checklist:**
- [ ] Handler exists for your section
- [ ] Handler is called from `onPreviewKeyEvent`
- [ ] Navigation works for LEFT/RIGHT (within row)
- [ ] Navigation works for UP/DOWN (between rows)
- [ ] Navigation from CategoryIcon to first content item works
- [ ] Navigation from content item to CategoryIcon works
- [ ] Navigation from first channel to menu works (UP from CategoryIcon)

**Example Handler Structure:**
```kotlin
private fun handleAplikacjeChannelsNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onFocusChange: (Int, Int) -> Unit,
    channelFocusRequesters: Map<Pair<Int, Int>, FocusRequester>,
    channels: List<String>,
    lazyListStates: Map<Int, LazyListState>,
    coroutineScope: CoroutineScope,
    gridContent: Map<String, List<Any>>,
    onReturnToMenu: () -> Unit
): Boolean {
    when (event.key) {
        Key.DirectionLeft -> {
            // Move left or to CategoryIcon
        }
        Key.DirectionRight -> {
            // Move right within content
        }
        Key.DirectionUp -> {
            // Move to previous row or menu
        }
        Key.DirectionDown -> {
            // Move to next row
        }
    }
    return true
}
```

**Testing:**
- [ ] All directional keys work correctly
- [ ] Focus wrapping behaves as expected (at start/end of rows)
- [ ] No crashes when navigating to/from your channel
- [ ] Focus indicators (visual borders) appear correctly

#### Step 16: Update Top-Level Delegation

**Location**: `TopMenuScreen2` onPreviewKeyEvent (~line 200)

**Verify delegation:**
```kotlin
when (currentSection) {
    "APLIKACJE" -> {
        // APLIKACJE handles its own navigation entirely
        false  // Let AplikacjeChannelsScreen handle all keys
    }
    // ... other sections
}
```

**Rules:**
- Section with complex multi-row navigation → Return `false` (delegate to child)
- Section with simple navigation → Return `true` with custom handling

**Testing:**
- [ ] Keys are delegated correctly to child component
- [ ] No key event conflicts (parent vs child)
- [ ] Navigation feels responsive (no lag)

---

### Phase 6: Data Integration

#### Step 17: Add Data Source

**Location**: Section component (e.g., `AplikacjeChannelsScreen`)

**Add data fetching/preparation:**
```kotlin
@Composable
fun AplikacjeChannelsScreen(...) {
    // Existing data sources...

    // Add your data source:
    val yourContent by remember {
        mutableStateOf(
            listOf(
                YourDataType(title = "Item 1", imageUrl = "..."),
                YourDataType(title = "Item 2", imageUrl = "..."),
                // ... more items
            )
        )
    }

    // Map channel to content:
    val gridContent = mapOf(
        "Polecane" to horizontalContent,
        "Skróty" to shortcuts,
        "[YOUR CHANNEL]" to yourContent,  // Add mapping
        // ... other mappings
    )

    // ... rest of component
}
```

**Data Source Options:**
1. **Static list**: `listOf(...)`  [Simple, for testing]
2. **ViewModel**: `viewModel.yourContent.collectAsState()`  [Recommended]
3. **API call**: `LaunchedEffect { data = fetchFromAPI() }`  [For remote data]
4. **Database**: `database.yourDao().getAllItems().collectAsState()`  [For cached data]

**Testing:**
- [ ] Data loads successfully (no errors)
- [ ] Correct number of items appears
- [ ] Content is up-to-date (if dynamic)
- [ ] Loading states are handled (show placeholders if needed)

#### Step 18: Add FocusRequesters

**Location**: FocusRequester initialization (~line 3400)

**Verify FocusRequester map includes your channel:**
```kotlin
val channelFocusRequesters = remember(channels.size) {
    buildMap {
        channels.forEachIndexed { rowIndex, channel ->
            // CategoryIcon
            put(Pair(rowIndex, -1), FocusRequester())

            // Content items
            val itemCount = when (channel) {
                "Polecane" -> 10
                "Skróty" -> 6
                "[YOUR CHANNEL]" -> yourContent.size  // Add your channel
                else -> 10
            }
            repeat(itemCount) { colIndex ->
                put(Pair(rowIndex, colIndex), FocusRequester())
            }
        }
    }
}
```

**Rules:**
- CategoryIcon always at `Pair(rowIndex, -1)`
- Content items at `Pair(rowIndex, 0)` through `Pair(rowIndex, itemCount-1)`
- Create exactly as many FocusRequesters as items

**Testing:**
- [ ] FocusRequesters are created for all items
- [ ] No missing FocusRequesters (no null pointer exceptions)
- [ ] Focus request succeeds (focus moves to correct item)

#### Step 19: Add LazyListState

**Location**: LazyListState initialization (~line 3450)

**Verify LazyListState map includes your channel:**
```kotlin
val lazyListStates = remember(channels.size) {
    channels.associateWith { LazyListState() }
}
```

**This is usually automatic** - all channels get a LazyListState by default.

**Testing:**
- [ ] LazyRow scrolls correctly when navigating
- [ ] Scroll position is maintained when focus moves away and back
- [ ] No crashes related to LazyListState

---

### Phase 7: Testing & Validation

#### Step 20: Visual Testing

**Checklist:**
- [ ] **Layout**: Channel appears in correct position in list
- [ ] **Dimensions**: Card size matches specification
- [ ] **Spacing**: Correct gaps between items (12px/20px/30px)
- [ ] **CategoryIcon**: Fixed at X=80px, doesn't scroll
- [ ] **First item**: Starts at X=380px
- [ ] **Colors**: Focus states use correct colors (aqua focus, purple unfocused)
- [ ] **Fonts**: Text is readable at 10-foot distance
- [ ] **Images**: Load correctly, no broken images
- [ ] **Animation**: Smooth 350ms slide-down (if applicable)
- [ ] **Details**: Appear after animation completes (if applicable)

**Test on multiple screen sizes:**
- [ ] 1920x1080 (Full HD)
- [ ] 1280x720 (HD)
- [ ] 3840x2160 (4K) - if supported

#### Step 21: Navigation Testing

**Horizontal Navigation (LEFT/RIGHT):**
- [ ] From CategoryIcon, RIGHT moves to first content item
- [ ] Within content, LEFT/RIGHT moves between items
- [ ] At first item, LEFT moves to CategoryIcon
- [ ] At last item, RIGHT stays at last item (no wrap)

**Vertical Navigation (UP/DOWN):**
- [ ] From menu, DOWN moves to first channel's CategoryIcon
- [ ] Within channels, UP/DOWN moves between channel rows
- [ ] From first channel's CategoryIcon, UP moves to menu
- [ ] From last channel, DOWN stays at last channel (no wrap)

**Focus Behavior:**
- [ ] Focus indicator (border/background) is clearly visible
- [ ] Focus moves smoothly without lag
- [ ] No "lost focus" situations (focus always on valid item)
- [ ] Focus returns to correct item when navigating back

**Edge Cases:**
- [ ] Empty channel (no content items) - focus stays on CategoryIcon
- [ ] Single item - navigation works correctly
- [ ] Many items (>20) - scrolling works smoothly

#### Step 22: Positioning Testing

**Focus Positioning:**
- [ ] Focused channel appears at FIXED_FOCUS_Y (340px or 170px for ODKRYWAJ)
- [ ] Measure on screen: focused CategoryIcon top edge should be at exact Y position
- [ ] Position is consistent regardless of which channel is focused

**Expansion Testing (if applicable):**
- [ ] Focused channel expands by exactly 290px (miniatures visible)
- [ ] Channels above focused channel move up correctly
- [ ] Channels below focused channel move down correctly
- [ ] No overlapping channels (each maintains its height + spacing)

**Extra Spacing Testing:**
- [ ] When CategoryIcon focused: normal spacing (40px or 130px)
- [ ] When content focused: extra 100px spacing above focused row
- [ ] Transition between CategoryIcon and content focus is smooth

**Visual Measurement:**
Use Android Studio Layout Inspector or on-screen measurement tool:
```
1. Focus on your channel (navigate with remote)
2. Take screenshot or use Layout Inspector
3. Measure Y position of focused channel's top edge
4. Should match FIXED_FOCUS_Y constant exactly
```

#### Step 23: Performance Testing

**Scroll Performance:**
- [ ] Smooth scrolling at 60fps (no stuttering)
- [ ] No lag when scrolling quickly
- [ ] Images load progressively (don't block scrolling)

**Memory Usage:**
- [ ] No memory leaks (use Android Studio Profiler)
- [ ] Memory usage stable over time (navigate away and back)
- [ ] Large images are properly downsampled

**Animation Performance:**
- [ ] Slide-down animation is smooth (no dropped frames)
- [ ] Multiple animations don't cause lag
- [ ] Animations complete fully (no abrupt stops)

**Test Duration:**
- [ ] Use channel for 5+ minutes continuously
- [ ] Navigate to/from channel multiple times
- [ ] Scroll through all items multiple times
- [ ] Monitor for crashes, ANRs, or memory issues

#### Step 24: Edge Case Testing

**Data Edge Cases:**
- [ ] Empty content list (0 items)
- [ ] Single item (1 item)
- [ ] Many items (50+ items)
- [ ] Very long titles (text truncation works)
- [ ] Missing images (placeholder appears)
- [ ] Slow network (loading states handled)

**Navigation Edge Cases:**
- [ ] Rapid key presses (no crashes)
- [ ] Holding down directional keys (smooth continuous movement)
- [ ] Back key from different focus states
- [ ] Menu key during animation

**Focus Edge Cases:**
- [ ] Navigate away during animation (animation cancels correctly)
- [ ] Request focus programmatically (works as expected)
- [ ] Restore focus after app background/foreground
- [ ] Multiple rapid focus changes (state remains consistent)

---

## Post-Implementation

### Step 25: Code Review Checklist

**Code Quality:**
- [ ] No hardcoded values (use constants or sx/sy functions)
- [ ] Consistent naming conventions
- [ ] Proper null safety (no force unwraps `!!`)
- [ ] No suppressed warnings without justification
- [ ] Code follows project style guide

**Documentation:**
- [ ] Added comments explaining complex logic
- [ ] Updated `CHANNEL_SYSTEM.md` if new channel type created
- [ ] Updated this checklist if new steps discovered
- [ ] Added inline comments for non-obvious calculations

**Error Handling:**
- [ ] Null checks for optional data
- [ ] Graceful degradation for missing resources
- [ ] No silent failures (log errors appropriately)

### Step 26: Update Documentation

**Files to Update:**

1. **CHANNEL_SYSTEM.md** (if new channel type):
   - [ ] Add to channel types specification
   - [ ] Add dimensions to reference table
   - [ ] Add spacing to reference table
   - [ ] Add expansion behavior to reference table
   - [ ] Add code location references
   - [ ] Update "Usage by Section" section

2. **CLAUDE.md**:
   - [ ] Add to feature list if major feature
   - [ ] Update version history with new channel

3. **README.md** (if user-facing):
   - [ ] Update feature list
   - [ ] Add screenshots if visual change

### Step 27: Create Backup

**Before deploying to production:**

```bash
# Create backup of current TopMenuScreen2.kt
cp app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt \
   app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2_backup_[CHANNEL_NAME]_$(date +%Y%m%d_%H%M%S).kt

# Build APK for backup
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk \
   backups/app-debug-[CHANNEL_NAME]-$(date +%Y%m%d_%H%M%S).apk
```

**Backup Naming Convention:**
- Code: `TopMenuScreen2_backup_[feature]_[YYYYMMDD_HHMMSS].kt`
- APK: `app-debug-[feature]-[YYYYMMDD_HHMMSS].apk`

### Step 28: Commit Changes

**Commit Message Template:**
```
feat(channels): Add [CHANNEL_NAME] to [SECTION_NAME]

- Added [TYPE] channel type with [WIDTH]x[HEIGHT]px cards
- Configured [NORMAL/EXPANDED] row heights ([HEIGHT]px/[HEIGHT]px)
- [Enabled/Disabled] slide-down animation
- Added logo mapping for "[CHANNEL_NAME]"
- Integrated with [DATA_SOURCE] data source

Dimensions:
- Container: [WIDTH]x[HEIGHT]px
- Spacing: [SPACING]px
- CategoryIcon: Fixed at X=80px

Navigation:
- LEFT/RIGHT: Within channel content
- UP/DOWN: Between channels
- Delegation: [Parent/Child] handles navigation

Testing:
- ✅ Visual layout matches design
- ✅ Navigation works correctly
- ✅ Focus positioning at Y=[FIXED_FOCUS_Y]px
- ✅ Performance is smooth (60fps)
- ✅ Edge cases handled (empty, single item, many items)

Backup: TopMenuScreen2_backup_[feature]_[timestamp].kt

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## Common Issues & Solutions

### Issue 1: CategoryIcon Scrolls with Content

**Symptom**: CategoryIcon at X=80px moves when scrolling content

**Solution**:
- Move CategoryIcon outside LazyRow
- Position with `Box(modifier = Modifier.offset(x = sx(80)))`
- LazyRow contentPadding should be `start = sx(380)` (not 80)

**Reference**: See `CHANNEL_SYSTEM.md` "CategoryIcon Patterns" section

### Issue 2: Wrong Spacing Between Items

**Symptom**: Items too close together or too far apart

**Solution**:
- Check `horizontalArrangement = Arrangement.spacedBy(sx([SPACING]))`
- app-icons: 12px
- collection-slider: 30px
- Everything else: 20px

**Reference**: See Step 6 in this checklist

### Issue 3: Channel Doesn't Expand

**Symptom**: Miniatures don't slide down when focused

**Solution**:
- Check animation condition includes your channel type
- Verify `isMiniaturesOnScreen` is true
- Check EXPANDED_ROW_HEIGHT is different from NORMAL_ROW_HEIGHT
- Ensure channel type should expand (app-icons, slider-max, collection-slider don't expand)

**Reference**: See Step 13 in this checklist

### Issue 4: Wrong Positioning (Not at FIXED_FOCUS_Y)

**Symptom**: Focused channel appears at wrong Y position

**Solution**:
- Check constants: NORMAL_ROW_HEIGHT and EXPANDED_ROW_HEIGHT
- Verify `calculateXXXChannelYPosition` uses correct heights for your channel type
- Ensure FIXED_FOCUS_Y is correct (340 for most, 170 for ODKRYWAJ)
- Check calculation: `focusedChannelRelativeY = FIXED_FOCUS_Y - scrollY`

**Reference**: See Step 10-12 in this checklist

### Issue 5: Focus Lost or Navigation Doesn't Work

**Symptom**: Can't navigate to channel, or focus disappears

**Solution**:
- Check FocusRequesters are created for all items (CategoryIcon + content)
- Verify `channelFocusRequesters[Pair(rowIndex, colIndex)]` returns non-null
- Check navigation handler includes your channel in gridContent map
- Ensure LazyListState exists for your channel

**Reference**: See Step 18-19 in this checklist

### Issue 6: Channels Overlap

**Symptom**: Channels render on top of each other

**Solution**:
- Check row height calculation includes proper spacing
- Horizontal channels: CategoryIcon (216) + spacing (40) = 256
- Vertical channels: CategoryIcon (216) + spacing (130) = 346
- Verify expanded height adds 290px for miniatures
- Check no negative positioning values

**Reference**: See Step 10 in this checklist

### Issue 7: Animation Timing Wrong

**Symptom**: Details appear before/during animation

**Solution**:
- Check `LaunchedEffect(isMiniaturesOnScreen)` includes `delay(350)`
- Verify delay matches animation duration
- Ensure details only show when `showDetails == true`
- Check animation isn't disabled for your channel type by mistake

**Reference**: See Step 14 in this checklist

### Issue 8: Images Don't Load

**Symptom**: Blank cards or placeholder images

**Solution**:
- Check image URLs are valid
- Verify internet permission in AndroidManifest.xml
- Check Coil image loader is configured
- Test with known-good image URL
- Check for HTTPS certificate issues

**Reference**: Check `app/src/main/AndroidManifest.xml` for permissions

### Issue 9: Performance Lag

**Symptom**: Scrolling stutters, animations drop frames

**Solution**:
- Reduce item count in LazyRow (use pagination)
- Downsample large images (use Coil transformations)
- Check for expensive computations in composables (use `remember`)
- Profile with Android Studio Profiler
- Consider virtualization (LazyRow already does this)

**Reference**: See Step 23 in this checklist

### Issue 10: Focus Indicator Not Visible

**Symptom**: Can't tell which item is focused

**Solution**:
- Check focus border color has high contrast
- Verify `isFocused` parameter is passed correctly
- Ensure border width is sufficient (at least 4px)
- Check z-index (border should be on top)
- Test on actual TV (not just emulator)

**Reference**: See Step 2 card component template

---

## Quick Reference

### Timing Checklist (Estimated)

- [ ] Step 1: Pre-planning (30 min)
- [ ] Step 2: Component creation/verification (1 hour)
- [ ] Step 3-9: Row configuration (2 hours)
- [ ] Step 10-12: Positioning system (1 hour)
- [ ] Step 13-14: Animation configuration (30 min)
- [ ] Step 15-16: Navigation integration (1 hour)
- [ ] Step 17-19: Data integration (1 hour)
- [ ] Step 20-24: Testing (2 hours)
- [ ] Step 25-28: Documentation and commit (1 hour)

**Total: ~10 hours** for a complete channel implementation

### Critical Files

| File | Purpose | Typical Line Ranges |
|------|---------|---------------------|
| TopMenuScreen2.kt | Main UI file | All implementation |
| CHANNEL_SYSTEM.md | Specifications | Documentation |
| CHANNEL_CHECKLIST.md | Implementation guide | This file |
| CLAUDE.md | Project context | Historical reference |

### Section-Specific Quick Links

| Section | Channels List | Calculate Function | Constants Start |
|---------|---------------|-------------------|-----------------|
| MOJE | Line ~800 | Line ~930 | Line ~850 |
| START | Line ~2000 | Line ~2194 | Line ~2100 |
| APLIKACJE | Line ~3300 | Line ~3377 | Line ~3310 |
| ODKRYWAJ | Line ~4800 | Line ~4900 | Line ~4850 |
| TELEWIZJA | Line ~5500 | N/A (GlobalFocusManager) | N/A |
| VOD | Line ~6100 | Line ~6200 | Line ~6150 |

### Constants Quick Reference

```kotlin
// CategoryIcon position (all sections)
X = 80px (fixed, outside LazyRow)

// Content start position (all sections)
X = 380px (LazyRow contentPadding.start)

// Focus position (most sections)
FIXED_FOCUS_Y = 340px

// Focus position (ODKRYWAJ only)
FIXED_FOCUS_Y = 170px

// Miniatures slide-down distance (expandable types)
ANIMATION_DISTANCE = 290px
ANIMATION_DURATION = 350ms

// Spacing
horizontal/vertical/top10/shortcuts/epg: 20px
app-icons: 12px
collection-slider: 30px

// Extra spacing when content focused
CONTENT_FOCUS_EXTRA_SPACING = 100px
```

---

## Version History

- **v1.0** (2025-10-14): Initial comprehensive checklist
  - 28 implementation steps
  - 10 common issues with solutions
  - Quick reference guides
  - Timing estimates
