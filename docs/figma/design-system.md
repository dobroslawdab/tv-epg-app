# 🎨 TV Components - Design System

**Source**: Extracted from codebase (TopMenuScreen2.kt, Version001Screen.kt, CLAUDE.md)
**Baseline Resolution**: 1920x1080px (Full HD)
**Last Update**: 2025-10-03

---

## 📏 DESIGN BASELINE

- **Target Resolution**: 1920x1080px (Full HD)
- **Wszystkie wartości**: podane w px przy rozdzielczości bazowej
- **Automatyczne skalowanie**: UI scales na 720p, 4K, inne rozdzielczości
- **System skalowania**: sx() dla horizontal, sy() dla vertical

---

## 🎨 COLOR PALETTE

### Background Colors

```kotlin
val colorBg = Color(0xFF48227C)              // Main background (deep purple)
val colorSurfaceDark = Color(0xFF2C2C2C)     // Dark surface (cards, icons)
val colorSurfaceLight = Color(0xFF3D3D3D)    // Light surface (hover states - unused on TV)
val colorBlack = Color(0xFF000000)           // Pure black (overlays, shadows)
val colorTransparent = Color.Transparent     // Transparent backgrounds
```

**Usage:**
- `colorBg` - main screen background
- `colorSurfaceDark` - CategoryIcon, Cards, Grid items
- `colorBlack` - gradient overlays, shadow effects

### Text Colors

```kotlin
val colorTextPrimary = Color(0xFFEEEEEE)     // Primary text (white, 100%)
val colorTextSecondary = Color(0xCCEEEEEE)   // Secondary text (white, 80% opacity)
val colorTextTertiary = Color(0x99EEEEEE)    // Tertiary text (white, 60% opacity)
val colorTextDisabled = Color(0x66EEEEEE)    // Disabled text (white, 40% opacity)
```

**Usage:**
- `colorTextPrimary` - titles, focused text, important labels
- `colorTextSecondary` - descriptions, subtitles
- `colorTextTertiary` - hints, captions
- `colorTextDisabled` - inactive elements

### Interactive Colors

```kotlin
val colorFocusBg = Color(0xFF5AECD3)         // Aqua - focused background
val colorFocusText = Color(0xFF48227C)       // Purple - text on aqua background
val colorFocusBorder = Color(0xFF5AECD3)     // Aqua - focused border (most common)
```

**Usage:**
- `colorFocusBg` - background of focused buttons, chips
- `colorFocusText` - text color when on aqua background (contrast)
- `colorFocusBorder` - border around focused cards, icons (3-4px)

### Chips & Badges

```kotlin
val colorChipBg = Color(0x1AEEEEEE)          // Chip background (white, 10% opacity)
val colorChipText = Color(0xFFEEEEEE)        // Chip text (white, 100%)
val colorChipBorder = Color(0x33EEEEEE)      // Chip border (white, 20% opacity)
```

**Usage:**
- Category chips, filters, badges
- Subtle background with white text

---

## ✍️ TYPOGRAPHY

### Font Sizes (scaled with sy)

```kotlin
// Headings
val fontSize_h1 = 48       // Large titles                // Use: (48 * sy(1).value / 1).sp
val fontSize_h2 = 36       // Section headers             // Use: (36 * sy(1).value / 1).sp
val fontSize_h3 = 28       // Subsection headers          // Use: (28 * sy(1).value / 1).sp

// Body
val fontSize_body_large = 24   // Standard text (most common)   // Use: (24 * sy(1).value / 1).sp
val fontSize_body = 20         // Small text                    // Use: (20 * sy(1).value / 1).sp
val fontSize_caption = 16      // Captions, labels              // Use: (16 * sy(1).value / 1).sp
val fontSize_tiny = 14         // Very small text               // Use: (14 * sy(1).value / 1).sp
```

**Conversion Pattern:**
```kotlin
Text(
    text = "Title",
    style = TextStyle(
        fontSize = (24 * sy(1).value / 1).sp  // For 24px from Figma
    )
)
```

### Font Weights

```kotlin
val fontWeight_regular = FontWeight.Normal     // 400
val fontWeight_medium = FontWeight.Medium      // 500
val fontWeight_semibold = FontWeight.SemiBold  // 600 (most common for titles)
val fontWeight_bold = FontWeight.Bold          // 700
```

**Usage Guidelines:**
- **Regular (400)**: Body text, descriptions
- **Medium (500)**: Subtitles, secondary headings
- **SemiBold (600)**: Card titles, focused labels
- **Bold (700)**: Main titles, emphasized text

### Line Heights (1.3-1.5x font size)

```kotlin
val lineHeight_h1 = 60     // 1.25x of 48px      // Use: (60 * sy(1).value / 1).sp
val lineHeight_h2 = 48     // 1.33x of 36px      // Use: (48 * sy(1).value / 1).sp
val lineHeight_body = 32   // 1.33x of 24px      // Use: (32 * sy(1).value / 1).sp
val lineHeight_caption = 24 // 1.5x of 16px      // Use: (24 * sy(1).value / 1).sp
```

### Letter Spacing

```kotlin
val letterSpacing_default = 0.sp       // Normal spacing
val letterSpacing_tight = (-0.5).sp    // Tighter for large headings
val letterSpacing_wide = 0.5.sp        // Wider for small caps
```

---

## 📐 SPACING SYSTEM

### Standard Spacing (8px base)

```kotlin
val spacing_xs = 8      // Extra small      // Use: sx(8) or sy(8)
val spacing_sm = 12     // Small            // Use: sx(12) or sy(12)
val spacing_md = 16     // Medium (default) // Use: sx(16) or sy(16)
val spacing_lg = 24     // Large            // Use: sx(24) or sy(24)
val spacing_xl = 40     // Extra large      // Use: sx(40) or sy(40)
val spacing_xxl = 64    // Section spacing  // Use: sx(64) or sy(64)
```

**Usage Examples:**
```kotlin
// Padding inside card
Box(modifier = Modifier.padding(sx(16)))  // spacing_md

// Gap between rows
Column(verticalArrangement = Arrangement.spacedBy(sy(40)))  // spacing_xl

// Margin from screen edge
Box(modifier = Modifier.padding(start = sx(80)))  // Custom value
```

### Component-Specific Spacing

```kotlin
// Channel Rows
val channel_row_gap = 40             // sy(40) - Vertical gap between channel rows
val category_icon_offset_x = 80      // sx(80) - CategoryIcon from left screen edge
val miniature_start_x = 380          // sx(380) - First miniature position
val miniature_gap = 20               // sx(20) - Gap between miniatures

// Grid Layout
val grid_column_gap = 20      // sx(20) - Horizontal gap in grids
val grid_row_gap = 20         // sy(20) - Vertical gap in grids

// Top Menu
val top_menu_item_gap = 16    // sx(16) - Gap between menu items
val top_menu_height = 64      // sy(64) - Height of top menu bar
```

---

## 🔲 BORDER & RADIUS

### Border Widths

```kotlin
val border_thin = 2       // sx(2) - Subtle borders (rarely used)
val border_default = 3    // sx(3) - Standard borders (most cards)
val border_thick = 4      // sx(4) - Emphasized borders (CategoryIcon, large cards)
val border_none = 0       // 0.dp - No border (default state)
```

**Usage:**
```kotlin
// Standard card focus
.border(
    width = if (isFocused) sx(3) else 0.dp,
    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
)

// CategoryIcon focus (thicker)
.border(
    width = if (isFocused) sx(4) else 0.dp,
    color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent
)
```

### Corner Radius

```kotlin
val radius_xs = 4         // sx(4) - Very subtle (rarely used)
val radius_sm = 8         // sx(8) - Small cards, buttons
val radius_md = 12        // sx(12) - Standard (most common: CategoryIcon, Cards)
val radius_lg = 16        // sx(16) - Large cards, containers
val radius_xl = 24        // sx(24) - Hero elements (rarely used)
val radius_full = 999     // CircleShape - Perfect circles
```

**Usage:**
```kotlin
// Most common - radius_md
.clip(RoundedCornerShape(sx(12)))

// Small cards
.clip(RoundedCornerShape(sx(8)))

// Circle avatar
.clip(CircleShape)
```

---

## 🎬 ANIMATIONS

### Timing (in milliseconds)

```kotlin
val duration_fast = 150         // Quick transitions (hover states - unused on TV)
val duration_standard = 350     // Standard (MOST COMMON: expand, slide)
val duration_slow = 500         // Slow, dramatic (row positioning)
```

**Usage:**
```kotlin
val offsetY by animateDpAsState(
    targetValue = if (isFocused) sy(290) else sy(0),
    animationSpec = tween(durationMillis = 350)  // duration_standard
)
```

### Easing Curves

```kotlin
import androidx.compose.animation.core.*

val easing_linear = LinearEasing
val easing_default = FastOutSlowInEasing       // Most common
val easing_emphasized = EaseInOutCubic         // Dramatic movements
```

**Usage:**
```kotlin
// Standard animation
animationSpec = tween(
    durationMillis = 350,
    easing = FastOutSlowInEasing
)

// Dramatic animation
animationSpec = tween(
    durationMillis = 500,
    easing = EaseInOutCubic
)
```

### Common Animation Patterns

#### Slide Down (Miniature Expansion)

```kotlin
val miniaturesYOffset by animateDpAsState(
    targetValue = if (isCurrentRow && focusedColIndex >= 0) sy(290) else sy(0),
    animationSpec = tween(durationMillis = 350, easing = EaseInOutCubic),
    label = "miniatures_y_offset"
)
```

#### Scale Up (Focus)

```kotlin
val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.1f else 1f,
    animationSpec = tween(durationMillis = 350)
)

Box(modifier = Modifier.scale(scale))
```

#### Fade In/Out (Opacity)

```kotlin
val alpha by animateFloatAsState(
    targetValue = if (isVisible) 1f else 0f,
    animationSpec = tween(durationMillis = 350)
)

Box(modifier = Modifier.alpha(alpha))
```

---

## 📦 COMPONENT DIMENSIONS

### CategoryIcon

```kotlin
// Dimensions
val categoryIcon_size = 216              // sx(216) x sy(216)
val categoryIcon_icon_size = 120         // sx(120) x sy(120) - icon inside
val categoryIcon_border_radius = 12      // sx(12)
val categoryIcon_border_focused = 4      // sx(4)
val categoryIcon_offset_x = 80           // sx(80) - from left edge
```

**Location**: Version001Screen.kt, TopMenuScreen2.kt (MOJE, START, APLIKACJE, VOD)

### MiniatureCard (Content Thumbnail)

```kotlin
// Dimensions
val miniatureCard_width = 280            // sx(280)
val miniatureCard_height = 157           // sy(157)
val miniatureCard_border_radius = 8      // sx(8)
val miniatureCard_border_focused = 3     // sx(3)
val miniatureCard_gap = 20               // sx(20) - between cards
val miniatureCard_start_x = 380          // sx(380) - first card position
```

**Location**: Version001Screen.kt (MiniatureCard)

### Top10 Card (Vertical Poster)

```kotlin
// Dimensions
val top10Card_width = 261                // sx(261)
val top10Card_height = 324               // sy(324)
val top10Card_border_radius = 8          // sx(8)
val top10Card_border_focused = 3         // sx(3)
val top10Card_gap = 20                   // sx(20)
```

**Location**: TopMenuScreen2.kt (Top10ContentCard)

### Grid Channel Card (TELEWIZJA)

```kotlin
// Dimensions (TvChannelIconCard)
val tvChannelIcon_total_width = 170      // sx(170) - total column width
val tvChannelIcon_total_height = 190     // sy(190) - total height (logo + label)
val tvChannelIcon_logo_size = 150        // sx(150) x sy(150) - logo box
val tvChannelIcon_icon_size = 100        // sx(100) x sy(100) - icon inside logo
val tvChannelIcon_border_radius = 12     // sx(12)
val tvChannelIcon_border_focused = 4     // sx(4)
val tvChannelIcon_label_gap = 8          // sy(8) - between logo and label
```

**Location**: TopMenuScreen2.kt:251-305 (TvChannelIconCard)

### Service Logo Card (VOD Services)

```kotlin
// Dimensions
val serviceLogoCard_size = 200           // sx(200) x sy(200)
val serviceLogoCard_border_radius = 12   // sx(12)
val serviceLogoCard_border_focused = 4   // sx(4)
val serviceLogoCard_gap = 20             // sx(20)
```

**Location**: TopMenuScreen2.kt (ServiceLogoCard)

### Slider Max Card (Big Slider)

```kotlin
// Dimensions
val sliderMaxCard_width = 1326           // sx(1326)
val sliderMaxCard_height = 742           // sy(742)
val sliderMaxCard_border_radius = 12     // sx(12)
val sliderMaxCard_border_focused = 6     // sx(6) - thicker for large card
val sliderMaxCard_gap = 20               // sx(20)
```

**Location**: TopMenuScreen2.kt (SliderMaxCard)

### Top Menu Items

```kotlin
// Dimensions
val topMenuItem_height = 64              // sy(64)
val topMenuItem_padding_h = 16           // sx(16) - horizontal padding
val topMenuItem_border_radius = 8        // sx(8)
val topMenuItem_border_focused = 3       // sx(3)
val topMenuItem_gap = 16                 // sx(16) - between items
```

**Location**: TopMenuScreen2.kt (TopMenuTabs)

---

## 🎭 FOCUS STATES

### Visual Hierarchy

1. **Focused**: Border 3-4px #5AECD3 + optional scale 1.05-1.1x
2. **Hovered**: (N/A on TV - no mouse)
3. **Default**: No border, normal scale

### Implementation Patterns

#### Standard Card Focus

```kotlin
Box(
    modifier = Modifier
        .size(sx(280), sy(157))
        .clip(RoundedCornerShape(sx(8)))
        .border(
            width = if (isFocused) sx(3) else 0.dp,
            color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
            shape = RoundedCornerShape(sx(8))
        )
        .focusRequester(focusRequester)
        .onFocusChanged { if (it.isFocused) onFocusChange() }
        .focusable()
)
```

#### CategoryIcon Focus (Thicker Border)

```kotlin
Box(
    modifier = Modifier
        .size(sx(216), sy(216))
        .clip(RoundedCornerShape(sx(12)))
        .background(Color(0xFF2C2C2C))
        .border(
            width = if (isFocused) sx(4) else 0.dp,  // Thicker - 4px
            color = if (isFocused) Color(0xFF5AECD3) else Color.Transparent,
            shape = RoundedCornerShape(sx(12))
        )
)
```

#### Text Focus (Color Change)

```kotlin
Text(
    text = title,
    style = TextStyle(
        fontSize = (24 * sy(1).value / 1).sp,
        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
        color = if (isFocused) Color(0xFF5AECD3) else Color(0xFFEEEEEE)  // Aqua when focused
    )
)
```

---

## 📱 LAYOUT GRID

### Base Grid

- **Columns**: Flexible (varies by component)
- **Gutter**: 20px horizontal (`sx(20)`)
- **Row Gap**: 20-40px vertical (`sy(20)` or `sy(40)`)
- **Margin**: 80px left (`sx(80)`), 20px right (`sx(20)`)

### Screen Margins

```kotlin
val screen_margin_left = 80       // sx(80) - most content starts here (after CategoryIcon)
val screen_margin_right = 20      // sx(20) - right edge padding
val screen_margin_top = 64        // sy(64) - below top menu
val screen_margin_bottom = 40     // sy(40) - bottom padding
```

### Breakpoints (Theoretical - TV is typically fixed)

- **1920x1080**: Full HD (baseline) - scale factor 1.0
- **1280x720**: HD Ready - scale factor ~0.67
- **3840x2160**: 4K - scale factor 2.0

**Note**: System automatically scales via sx/sy - no manual breakpoints needed.

---

## ♿ ACCESSIBILITY

### Minimum Sizes (TV Optimized)

```kotlin
val min_touch_target = 64        // sy(64) - minimum for TV remote (larger than mobile 48dp)
val min_text_size = 20           // (20 * sy(1).value / 1).sp - readable at 10-foot distance
val min_border_width = 3         // sx(3) - visible from distance
val min_icon_size = 48           // sx(48) x sy(48) - recognizable icons
```

### Contrast Ratios

- **Text on background**: 7:1 (AAA level)
  - White (#EEEEEE) on Purple (#48227C) ✅
- **Focused elements**: High contrast aqua (#5AECD3) on purple (#48227C) ✅
- **Border visibility**: 4px aqua border easily visible from 10 feet ✅

---

## 🔧 ROW HEIGHT CONSTANTS

### MOJE / START Sections

```kotlin
private const val FIXED_FOCUS_Y = 340                  // Focused row always at Y=340
private const val NORMAL_ROW_HEIGHT = 256              // CategoryIcon (216) + spacing (40)
private const val EXPANDED_ROW_HEIGHT = 546            // CategoryIcon (216) + miniatures (290) + spacing (40)
private const val CONTENT_FOCUS_EXTRA_SPACING = 100    // Extra spacing above focused content row
```

**Usage**: Animowane pozycjonowanie wierszy w Version001Screen.kt

### TELEWIZJA Section

```kotlin
private const val TELEWIZJA_FIXED_FOCUS_Y = 170
private const val TELEWIZJA_SLIDER_MAX_NORMAL_ROW_HEIGHT = 782
private const val TELEWIZJA_SLIDER_MAX_EXPANDED_ROW_HEIGHT = 782       // No expansion
private const val TELEWIZJA_TV_CHANNEL_ICONS_NORMAL_ROW_HEIGHT = 256
private const val TELEWIZJA_TV_CHANNEL_ICONS_EXPANDED_ROW_HEIGHT = 256 // No expansion
private const val TELEWIZJA_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val TELEWIZJA_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
private const val TELEWIZJA_CONTENT_FOCUS_EXTRA_SPACING = 100
```

**Usage**: TopMenuScreen2.kt - calculateTelewizjaChannelYPosition()

### APLIKACJE Section

```kotlin
private const val APLIKACJE_FIXED_FOCUS_Y = 340
private const val APLIKACJE_APP_ICONS_NORMAL_ROW_HEIGHT = 256
private const val APLIKACJE_APP_ICONS_EXPANDED_ROW_HEIGHT = 256        // No expansion
private const val APLIKACJE_HORIZONTAL_NORMAL_ROW_HEIGHT = 256
private const val APLIKACJE_HORIZONTAL_EXPANDED_ROW_HEIGHT = 546
private const val APLIKACJE_SHORTCUTS_NORMAL_ROW_HEIGHT = 346
private const val APLIKACJE_SHORTCUTS_EXPANDED_ROW_HEIGHT = 636
private const val APLIKACJE_CONTENT_FOCUS_EXTRA_SPACING = 100
```

**Usage**: TopMenuScreen2.kt - calculateAplikacjeChannelYPosition()

---

## 📚 USAGE EXAMPLES

### Standard Card with Focus

```kotlin
@Composable
fun ExampleCard(
    title: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Card(
        modifier = Modifier
            .size(sx(280), sy(157))              // From design system
            .border(
                width = if (isFocused) sx(3) else 0.dp,  // border_default
                color = if (isFocused) colorFocusBorder else Color.Transparent,
                shape = RoundedCornerShape(sx(8))  // radius_sm
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocusChange() }
            .focusable(),
        shape = RoundedCornerShape(sx(8)),       // radius_sm
        colors = CardDefaults.cardColors(
            containerColor = colorSurfaceDark     // From color system
        )
    ) {
        // Content
        Text(
            text = title,
            style = TextStyle(
                fontSize = (20 * sy(1).value / 1).sp,      // fontSize_body
                fontWeight = fontWeight_semibold,          // Font weight
                color = colorTextPrimary                   // Text color
            ),
            modifier = Modifier.padding(sx(16))   // spacing_md
        )
    }
}
```

### Row with Spacing

```kotlin
Row(
    modifier = Modifier.padding(start = sx(80)),  // screen_margin_left
    horizontalArrangement = Arrangement.spacedBy(sx(20))  // miniature_gap
) {
    repeat(5) { index ->
        MiniatureCard(
            index = index,
            sx = sx,
            sy = sy
        )
    }
}
```

---

## 🔗 RELATED FILES

- **Source**: `app/src/main/java/com/example/tv/TopMenuScreen2.kt` (main component library)
- **Source**: `app/src/main/java/com/example/tv/version001/Version001Screen.kt` (MOJE section patterns)
- **Documentation**: `CLAUDE.md` (project overview, scaling system)
- **Agent**: `docs/agents/layout-engineer.md` (conversion guidelines)

---

**END OF DESIGN SYSTEM**

Last updated: 2025-10-03
