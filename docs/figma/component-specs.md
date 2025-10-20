# 📦 Component Specifications

**Purpose**: Manual specs dla komponentów UI (template + przykłady z kodu)
**When to use**: Gdy konwertujesz design z Figmy na Compose bez Framelink API
**Last Update**: 2025-10-03

---

## 📋 TEMPLATE

Użyj tego template dla każdego nowego komponentu:

```markdown
## ComponentName

**Figma Frame**: [Name]
**Figma Node ID**: [optional - 123:456]
**Source Code**: [path to file:line if exists]

### Dimensions
- Width: [value]px
- Height: [value]px
- Aspect Ratio: [w:h if applicable]

### Position (in parent)
- X: [value]px (relative to parent)
- Y: [value]px or "calculated" if dynamic

### Styling
- Background: #HEXCODE or "Image" or "Gradient"
- Border radius: [value]px
- Border (default): [width]px solid #HEXCODE or "none"
- Border (focused): [width]px solid #HEXCODE

### Typography (if text present)
- Font size: [value]px
- Font weight: [Regular/Medium/SemiBold/Bold]
- Line height: [value]px
- Color: #HEXCODE
- Alignment: [left/center/right]

### Content
- Icon/Image: [size]px, [description]
- Text: [description]
- Other elements: [list]

### States
- Default: [description]
- Focused: [what changes - border, scale, color, etc.]
- Disabled: [if applicable]

### Spacing
- Padding: [top] [right] [bottom] [left] px
- Margin: [top] [right] [bottom] [left] px
- Gap (if container): [value]px between children

### Notes
- [Any special behaviors, animations, constraints]
```

---

## 🎯 PRZYKŁADY Z PROJEKTU

### CategoryIcon

**Figma Frame**: CategoryIcon
**Source Code**: `Version001Screen.kt:540-590`, `TopMenuScreen2.kt` (multiple sections)

#### Dimensions
- Width: 216px
- Height: 216px
- Aspect Ratio: 1:1 (square)

#### Position (in parent)
- X: 80px from left screen edge
- Y: calculated dynamically by row system

#### Styling
- Background: #2C2C2C (dark gray)
- Border radius: 12px
- Border (default): 0px (none)
- Border (focused): 4px solid #5AECD3 (aqua)

#### Typography
- N/A (icon only, no text visible)

#### Content
- Icon: 120x120px, centered
- Icon color: #EEEEEE (white)
- Icon type: Vector drawable or Lottie animation

#### States
- **Default**: No border, normal appearance
- **Focused**: 4px aqua border, icon may animate (if Lottie)

#### Spacing
- Padding: N/A (icon is centered via Alignment.Center)
- Margin: 80px from left screen edge
- Gap: 40px vertical between CategoryIcons (between rows)

#### Notes
- Always positioned at X=80px in all sections
- Y position animates smoothly (500ms) when focus changes between rows
- First focusable element in each row (col=-1)

---

### MiniatureCard (Content Thumbnail)

**Figma Frame**: MiniatureCard / ContentCard
**Source Code**: `Version001Screen.kt:660-750` (ContentCard)

#### Dimensions
- Width: 280px
- Height: 157px
- Aspect Ratio: 16:9

#### Position (in parent)
- X: 380px (first card), then +300px for each next (280 + 20 gap)
- Y: 0px relative to channel row, slides down 290px when focused

#### Styling
- Background: Image (AsyncImage from URL)
- Border radius: 8px
- Border (default): 0px (none)
- Border (focused): 3px solid #5AECD3 (aqua)

#### Typography
- N/A (thumbnail only, no overlay text in basic version)

#### Content
- Thumbnail: Full bleed image, ContentScale.Crop
- Optional: Gradient overlay at bottom (in DetailedContentOverlay variant)

#### States
- **Default**: No border, normal size
- **Focused**: 3px aqua border, slides down 290px (parent animation)

#### Spacing
- Padding: 0px (image fills entire card)
- Margin: 0px
- Gap: 20px horizontal between cards (in LazyRow)

#### Notes
- Slides down 290px when parent row is focused AND focus is on content (not CategoryIcon)
- Animation: 350ms duration, EaseInOutCubic easing
- Scrolls horizontally in LazyRow

---

### TvChannelIconCard

**Figma Frame**: TvChannelIconCard
**Source Code**: `TopMenuScreen2.kt:251-305`

#### Dimensions
- Total Width: 170px
- Total Height: 190px
- Logo box: 150x150px
- Icon inside: 100x100px

#### Position (in parent)
- X: starts after CategoryIcon in LazyRow
- Y: 0px relative to row

#### Styling
**Logo Box:**
- Background: #2C2C2C (dark gray)
- Border radius: 12px
- Border (default): 0px
- Border (focused): 4px solid #5AECD3 (aqua)

**Label (when focused):**
- Background: transparent
- No border

#### Typography
- Font size: 16px
- Font weight: SemiBold (600)
- Line height: auto
- Color: #EEEEEE (white)
- Alignment: Center
- Max lines: 1, ellipsis overflow

#### Content
- **Logo**: 150x150px box with 100x100px icon centered (AsyncImage from URL)
- **Label**: Channel name, visible only when focused
- **Gap**: 8px vertical between logo and label

#### States
- **Default**: Logo box only, no border, no label
- **Focused**: 4px aqua border, label appears below

#### Spacing
- Padding: 0px
- Column gap: 8px (between logo and label)
- Gap in row: 20px horizontal (in LazyRow)

#### Notes
- Used in TELEWIZJA section for "Twoje kanały" and "Wszystkie kanały" rows
- CategoryIcon + scrollable LazyRow pattern (like APLIKACJE)
- Label shows only on focus to save vertical space

---

### ServiceLogoCard

**Figma Frame**: ServiceLogoCard
**Source Code**: `TopMenuScreen2.kt` (ServiceLogoCard in TELEWIZJA)

#### Dimensions
- Width: 200px
- Height: 200px
- Aspect Ratio: 1:1

#### Position (in parent)
- X: starts at 120px from left (no CategoryIcon in this row)
- Y: 0px

#### Styling
- Background: transparent or #2C2C2C
- Border radius: 12px
- Border (default): 0px
- Border (focused): 4px solid #5AECD3

#### Typography
- N/A (logo only)

#### Content
- Logo image: 85x85px to 150x150px (varies by service), centered
- Source: Drawable resource

#### States
- **Default**: No border
- **Focused**: 4px aqua border

#### Spacing
- Padding: 0px
- Gap in row: 20px horizontal between logos

#### Notes
- Used for VOD service logos (Disney+, Netflix, HBO Max, etc.)
- Fixed position row, no scrolling
- No CategoryIcon in this row type

---

### SliderMaxCard

**Figma Frame**: SliderMaxCard / HeroSlider
**Source Code**: `TopMenuScreen2.kt` (SliderMaxCard in TELEWIZJA Slider Mix)

#### Dimensions
- Width: 1326px
- Height: 742px
- Aspect Ratio: ~16:9

#### Position (in parent)
- X: 120px from left (no CategoryIcon)
- Y: 0px
- Scrolls horizontally in LazyRow

#### Styling
- Background: Image/Video (ExoPlayer for Live TV)
- Border radius: 12px
- Border (default): 0px
- Border (focused): 6px solid #5AECD3 (thicker for large card)

#### Typography
- Overlay text (if any): varies by content type

#### Content
- **Live TV variant**: ExoPlayer PlayerView, full bleed
- **VOD variant**: AsyncImage thumbnail
- **Overlay**: Optional gradient + title at bottom

#### States
- **Default**: No border, video/image visible
- **Focused**: 6px aqua border

#### Spacing
- Padding: 0px (full bleed content)
- Gap in row: 20px horizontal between slides

#### Notes
- First slide often contains Live TV with ExoPlayer
- Can show PiP (Picture-in-Picture) when focus moves to next row
- Fullscreen mode available on select (OK button)

---

### Top10ContentCard

**Figma Frame**: Top10Card / VerticalPosterCard
**Source Code**: `TopMenuScreen2.kt` (Top10ContentCard)

#### Dimensions
- Width: 261px
- Height: 324px
- Aspect Ratio: ~2:3 (poster ratio)

#### Position (in parent)
- X: starts at 380px (after CategoryIcon)
- Y: 0px, slides down 290px when focused

#### Styling
- Background: Image (poster)
- Border radius: 8px
- Border (default): 0px
- Border (focused): 3px solid #5AECD3

#### Typography
- Ranking number: 48px, Bold, #5AECD3 (aqua)
- Title (overlay): 20px, SemiBold, #EEEEEE

#### Content
- **Poster**: Full card background (AsyncImage)
- **Ranking**: Large number 1-10 overlaid (top-left)
- **Title**: Optional overlay at bottom

#### States
- **Default**: No border
- **Focused**: 3px aqua border, slides down 290px

#### Spacing
- Padding: 16px (for ranking number position)
- Gap in row: 20px horizontal

#### Notes
- Vertical poster format (not 16:9 like miniatures)
- Ranking number is prominent visual element
- Used in VOD "Top 10" rows

---

### MiniCard (VOD Content Card)

**Figma Frame**: mini
**Figma Node ID**: 5972-6468
**Source Code**: `components/MiniCard.kt`
**Method**: Framelink Auto (depth=2)
**Last Updated**: 2025-10-03

#### Dimensions
- Width: 887px
- Height: 503px (default) / 661px (focused)
- Aspect Ratio: ~16:9

#### Position (in parent)
- X: Variable (centered or in Row)
- Y: 0px (relative to container)

#### Styling
**Card:**
- Background: #5A3888 (container background, not visible behind image)
- Border radius: 20px
- Border (default): 6px solid rgba(95, 237, 212, 0) - transparent
- Border (focused): 6px solid #5FEDD4 (aqua)

**Background Image:**
- Position: x: 0, y: 0
- Size: 887x499px
- ContentScale: Crop

**Gradient Overlay (Vector):**
- Position: x: -5, y: 253 (extends 5px left beyond card edge)
- Size: 892x250px (wider than card)
- Default gradient: `linear-gradient(0deg, rgba(0, 0, 0, 1) 0%, rgba(90, 56, 135, 0) 100%)`
- Focused gradient: `linear-gradient(0deg, rgba(91, 57, 135, 1) 0%, rgba(90, 56, 135, 0) 100%)`
- Direction: Bottom to top (0deg)

#### Typography
**Title:**
- Font: Manrope Bold
- Size: 46px
- Line height: 1.91em
- Color: #EEEEEE
- Always visible

**Metadata row:**
- Font: Manrope Bold
- Size: 20px
- Line height: 1.4em
- Letter spacing: 2%
- Color: rgba(238, 238, 238, 0.8)
- Visible: Only on focus (opacity 0 → 1, 350ms)

**Description:**
- Font: Manrope Medium
- Size: 28px
- Line height: 1.43em
- Color: #EEEEEE
- Max lines: 2
- Visible: Only on focus (opacity 0 → 1, 350ms)

#### Content
- **Background image**: Full card width, 499px height
- **Channel logo**: 168x168px, top-left of content area
- **Content overlay (Frame 66)**:
  - Position: x: 29, y: 216
  - Size: 845px wide
  - Gap: 31px between elements
  - Default height: 283px (fixed)
  - Focused height: auto (hug content)

#### States
- **Default (fokus=Default)**:
  - Height: 503px
  - Border: transparent
  - Gradient: Black to transparent
  - Metadata: hidden (opacity 0)
  - Description: hidden (opacity 0)

- **Focused (fokus=fokus2)**:
  - Height: 661px
  - Border: 6px #5FEDD4
  - Gradient: Purple (#5B3987) to transparent
  - Metadata: visible (opacity 1)
  - Description: visible (opacity 1)

#### Spacing
- Content overlay padding left: 29px
- Content overlay padding bottom: 29px (from card bottom: 503 - 216 - 283 = 4px... actually positioned at y:216)
- Gap between content elements: 31px

#### Animations
- Duration: 350ms (standard Compose tween)
- Animated properties:
  - Card height: 503px → 661px
  - Border width: 0dp → 6dp
  - Metadata opacity: 0f → 1f
  - Description opacity: 0f → 1f

#### Notes
- Gradient extends 5px beyond left edge of card (x: -5)
- Gradient is 892px wide (wider than card 887px) to ensure coverage
- Content overlay y-position is fixed at 216px in both states
- Frame 66 sizing changes from "fixed" (283px) to "hug" when focused
- Background color #5A3888 is only visible if image fails to load

---

## 🎨 COMMON PATTERNS

### Pattern 1: Card with Border on Focus

```
Component: [Any card]
Default state: No border, RoundedCornerShape(radius)
Focused state: Border(width=3-4px, color=#5AECD3), same shape
Animation: Instant (no animation on border)
```

**Example**: MiniatureCard, TvChannelIconCard, ServiceLogoCard

### Pattern 2: Card with Slide-Down Animation

```
Component: [Content cards in expandable rows]
Default state: Y offset = 0px
Focused state: Y offset = +290px
Animation: 350ms, EaseInOutCubic
Trigger: Parent row focused AND focus on content (not CategoryIcon)
```

**Example**: MiniatureCard, Top10ContentCard in MOJE/START/APLIKACJE

### Pattern 3: Text Appears on Focus

```
Component: [Cards with optional labels]
Default state: Label hidden (or not rendered)
Focused state: Label visible below main content
Animation: Instant (no fade, just appears)
```

**Example**: TvChannelIconCard (channel name appears on focus)

### Pattern 4: CategoryIcon + Scrollable Row

```
Layout: Row with CategoryIcon at X=80, LazyRow starts at X=80 (contentPadding)
CategoryIcon: First item in LazyRow, fixed position
Content: Scrollable items after CategoryIcon
Spacing: 20px gap between items (horizontalArrangement.spacedBy)
```

**Example**: MOJE, START, APLIKACJE, TELEWIZJA "Twoje kanały"

---

## 🔗 HOW TO USE THIS FILE

### When converting from Figma (Manual Method):

1. **Find component in Figma** - select it, check specs in right panel
2. **Fill out template above** - copy template, replace [placeholders] with actual values
3. **Add to this file** - paste filled template at bottom of file
4. **Call Layout Engineer**:
   ```bash
   claude code \
     -f docs/agents/layout-engineer.md \
     -f docs/figma/component-specs.md \
     -p "Layout Engineer: Build [ComponentName] from specs in component-specs.md"
   ```

### When using existing component as reference:

1. **Find similar component** - look through examples above
2. **Copy its structure** - use as starting point
3. **Modify dimensions/colors** - adapt to your needs
4. **Call Layout Engineer** with modifications

---

## 📚 RELATED FILES

- **Design System**: `docs/figma/design-system.md` (colors, typography, spacing values)
- **Agent**: `docs/agents/layout-engineer.md` (conversion rules, how to use specs)
- **Workflow**: `docs/workflows/figma-to-compose.md` (complete workflow guide)
- **Source Code**: `app/src/main/java/com/example/tv/` (actual implementations)

---

**END OF COMPONENT SPECS**

Last updated: 2025-10-03

**Next**: When you have new components to add, use the template at top of this file.
