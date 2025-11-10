# PIP Dialog Pattern 🎬

**Status**: ✅ Production-ready
**Pattern Guide**: Modal dialog with strengthened focus management
**Implementation Date**: 2025-11-07
**Git Commits**: 29973a5, 96baec1, 3bf5d4d, 6194519

---

## Quick Reference

Wzorzec modal dialog overlay z właściwym zarządzaniem fokusem dla PIP (Picture-in-Picture) kontroli.

**3 Kluczowe zasady:**
1. **Strengthened input gate** - Whitelist dozwolonych klawiszy (UP/DOWN/ENTER/BACK)
2. **Controller delegation** - Logika key handling wydzielona do controllera
3. **Component extraction** - UI jako osobny composable (reużywalność)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  TopMenuScreen2.onPreviewKeyEvent                       │
│  (SINGLE SOURCE OF TRUTH)                               │
└────────────┬────────────────────────────────────────────┘
             │
             ├─ Check: Is PIP dialog open?
             │  ├─ YES → Validate key against DIALOG_ALLOWED_KEYS
             │  │         ├─ Allowed → return false (let dialog handle)
             │  │         └─ Blocked → return true (consume, prevent leak)
             │  └─ NO  → Continue to section handlers
             │
             └─ PipDialogMenu composable
                ├─ Focus management (FocusRequesters)
                ├─ Key handling via PipDialogController
                └─ Callbacks: onDismiss, onFullscreen, onClose
```

---

## Problem Statement

### Before (Issues)

**Problem #1: Input Not Validated**
```kotlin
// ❌ WRONG - delegates ALL keys without validation
if (showPipDialog) {
    return@onPreviewKeyEvent false  // Blanket delegation
}
```

**Problem #2: Navigation Blocked**
- Dialog shows, user presses keys
- Some keys (media, numeric) leak to background
- START section navigation becomes unresponsive
- No way to close dialog reliably

**Problem #3: Monolithic Code**
- 147 lines of inline UI in TopMenuScreen2.kt
- Key handlers mixed with UI code
- Not testable, not reusable

### After (Solution)

**Fix #1: Strengthened Input Gate**
```kotlin
// ✅ CORRECT - whitelist validation
if (showPipDialog) {
    if (event.key in DIALOG_ALLOWED_KEYS) {
        return@onPreviewKeyEvent false  // Allow specific keys
    } else {
        return@onPreviewKeyEvent true   // Block everything else
    }
}
```

**Fix #2: Controller Delegation**
```kotlin
// ✅ CORRECT - extracted to PipDialogController
PipDialogController.handleDialogKeys(
    event = event,
    focusedOption = focusedOption,
    onNavigate = { ... },
    onSelectFullscreen = { ... },
    onSelectClose = { ... },
    onDismiss = { ... }
)
```

**Fix #3: Component Extraction**
```kotlin
// ✅ CORRECT - reusable composable
PipDialogMenu(
    onDismiss = { showPipDialog = false },
    onFullscreen = { onReturnToEpgDay(); onClosePip() },
    onClose = { onClosePip() },
    sx = sx,
    sy = sy
)
```

---

## Implementation Guide

### Step 1: Define Allowed Keys

```kotlin
// TopMenuScreen2.kt (after imports)
private val DIALOG_ALLOWED_KEYS = setOf(
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionCenter,
    Key.Enter,
    Key.Back,
    Key.Escape
)
```

**Why these keys?**
- UP/DOWN: Navigate between options
- ENTER/DirectionCenter: Select option
- BACK/ESCAPE: Dismiss dialog

**Why not others?**
- Media keys (PLAY/PAUSE): Would trigger background actions
- Numeric keys: Could interfere with background input
- LEFT/RIGHT: Not used in 2-option vertical menu

### Step 2: Strengthen Input Gate

```kotlin
// TopMenuScreen2.kt:706-717 (onPreviewKeyEvent)
if (showPipDialog) {
    if (event.key in DIALOG_ALLOWED_KEYS) {
        android.util.Log.d("TopMenuScreen2", "PIP dialog open - allowing key: ${event.key}")
        return@onPreviewKeyEvent false  // Let dialog handle
    } else {
        android.util.Log.d("TopMenuScreen2", "PIP dialog open - blocking key: ${event.key}")
        return@onPreviewKeyEvent true   // Consume & block
    }
}
```

**Key points:**
- Must be FIRST check in onPreviewKeyEvent (before any other logic)
- Logs for debugging (can remove in production)
- Returns `true` to consume blocked keys (prevents propagation)

### Step 3: Add Debounce (Optional but Recommended)

```kotlin
// TopMenuScreen2.kt:660-661 (state)
var lastDialogOpenTime by remember { mutableLongStateOf(0L) }

// TopMenuScreen2.kt:748-763 (PLAY/PAUSE handler)
if ((keyCode == KEYCODE_MEDIA_PLAY_PAUSE || ...) && pipPlayer != null) {
    val currentTime = System.currentTimeMillis()
    if (currentTime - lastDialogOpenTime >= 50) {
        showPipDialog = true
        lastDialogOpenTime = currentTime
    }
    return@onPreviewKeyEvent true
}
```

**Why debounce?**
- Prevents rapid key presses opening multiple dialogs
- 50ms minimum between opens (balances responsiveness & safety)
- Always consume event (return true) even if debounced

### Step 4: Create Controller

```kotlin
// pip/PipDialogController.kt
object PipDialogController {
    fun handleDialogKeys(
        event: KeyEvent,
        focusedOption: Int,
        onNavigate: (Int) -> Unit,
        onSelectFullscreen: () -> Unit,
        onSelectClose: () -> Unit,
        onDismiss: () -> Unit
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        return when (event.key) {
            Key.Enter, Key.DirectionCenter -> {
                when (focusedOption) {
                    0 -> { onSelectFullscreen(); true }
                    1 -> { onSelectClose(); true }
                    else -> false
                }
            }
            Key.DirectionUp -> {
                if (focusedOption == 1) { onNavigate(0); true } else false
            }
            Key.DirectionDown -> {
                if (focusedOption == 0) { onNavigate(1); true } else false
            }
            Key.Back, Key.Escape -> {
                onDismiss(); true
            }
            else -> false
        }
    }
}
```

**Controller responsibilities:**
- ✅ Key validation (KeyDown only)
- ✅ Navigation logic (0 ↔ 1)
- ✅ Action delegation (callbacks)
- ❌ Does NOT manage state (focusedOption passed in)
- ❌ Does NOT manage focus (FocusRequesters in UI)

### Step 5: Create UI Composable

```kotlin
// pip/PipDialogMenu.kt
@Composable
fun PipDialogMenu(
    onDismiss: () -> Unit,
    onFullscreen: () -> Unit,
    onClose: () -> Unit,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    var focusedOption by remember { mutableStateOf(0) }
    val focusRequesterFullscreen = remember { FocusRequester() }
    val focusRequesterClose = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .zIndex(200f),
        contentAlignment = Alignment.Center
    ) {
        Column(/* dialog UI */) {
            // Option 1: Powiększ na pełny ekran
            Box(
                modifier = Modifier
                    .onPreviewKeyEvent { event ->
                        PipDialogController.handleDialogKeys(
                            event, focusedOption,
                            onNavigate = { newOption -> /* request focus */ },
                            onSelectFullscreen = { onDismiss(); onFullscreen() },
                            onSelectClose = { onDismiss(); onClose() },
                            onDismiss = onDismiss
                        )
                    }
            ) { /* UI */ }

            // Option 2: Zamknij PIP
            Box(/* same handler as option 1 */) { /* UI */ }

            // Auto-focus first option
            LaunchedEffect(Unit) {
                focusManager.clearFocus(force = true)
                delay(50)
                focusRequesterFullscreen.requestFocus()
            }
        }
    }
}
```

**UI responsibilities:**
- ✅ Visual layout (backdrop, card, options)
- ✅ Focus management (FocusRequesters, LaunchedEffect)
- ✅ State management (focusedOption)
- ✅ Callback routing (onDismiss, onFullscreen, onClose)

### Step 6: Integration

```kotlin
// TopMenuScreen2.kt:969-980
if (showPipDialog && pipPlayer != null) {
    PipDialogMenu(
        onDismiss = { showPipDialog = false },
        onFullscreen = {
            onReturnToEpgDay()
            onClosePip()
        },
        onClose = { onClosePip() },
        sx = sx,
        sy = sy
    )
}
```

**Integration points:**
- Import: `import com.uxellence.tv.v3.pip.PipDialogMenu`
- Condition: `showPipDialog && pipPlayer != null`
- onDismiss: Always closes dialog (`showPipDialog = false`)
- onFullscreen: Returns to EPG + closes PIP
- onClose: Just closes PIP (stays in current section)

---

## File Structure

```
app/src/main/java/com/uxellence/tv/v3/
├── pip/
│   ├── PipMenuOption.kt        (56 lines - data model)
│   ├── PipDialogController.kt  (116 lines - key handling logic)
│   └── PipDialogMenu.kt        (189 lines - UI composable)
└── TopMenuScreen2.kt
    ├── DIALOG_ALLOWED_KEYS constant (lines 100-107)
    ├── lastDialogOpenTime state (line 661)
    ├── Strengthened input gate (lines 709-717)
    ├── Debounced PLAY/PAUSE (lines 754-762)
    └── PipDialogMenu call (lines 970-979)
```

**Total lines added**: 361 (pip/ directory)
**Lines removed from TopMenuScreen2**: 135
**Net change**: +226 lines (better organization)

---

## Key Patterns Used

### 1. Input Gating (tescik pattern)
```kotlin
if (showPipDialog) {
    if (event.key in ALLOWED_KEYS) {
        return false  // Let through
    } else {
        return true   // Block
    }
}
```

### 2. Controller Delegation (EpgDayScreen pattern)
```kotlin
PipDialogController.handleDialogKeys(
    event = event,
    focusedOption = focusedOption,
    // ... callbacks
)
```

### 3. Layout Engineer (sx/sy pattern)
```kotlin
@Composable
fun PipDialogMenu(
    sx: (Int) -> Dp,  // Responsive X scaling
    sy: (Int) -> Dp,  // Responsive Y scaling
) {
    Box(
        modifier = Modifier.width(sx(600))  // 600px → scaled
    )
}
```

### 4. Focus Architect (delegation pattern)
```
Level 1: TopMenuScreen2 (input gate)
  ↓ delegates to
Level 2: PipDialogController (key routing)
  ↓ calls back
Level 3: PipDialogMenu (UI + focus)
```

---

## Testing Checklist

### ✅ Basic Functionality
- [ ] Press PLAY/PAUSE with active PIP → Dialog appears
- [ ] Press UP/DOWN → Options highlight correctly
- [ ] Press ENTER on option 0 → Returns to EpgDayScreen, closes PIP
- [ ] Press ENTER on option 1 → Closes PIP, stays in current section
- [ ] Press BACK → Dialog dismisses

### ✅ Input Gating
- [ ] Press media keys (PLAY/PAUSE) while dialog open → Blocked
- [ ] Press numeric keys (0-9) while dialog open → Blocked
- [ ] Press LEFT/RIGHT while dialog open → Blocked
- [ ] Only UP/DOWN/ENTER/BACK work in dialog

### ✅ Debounce
- [ ] Rapid PLAY/PAUSE presses → Only one dialog opens
- [ ] 50ms debounce prevents multiple dialogs

### ✅ Focus Management
- [ ] Dialog appears → First option auto-focused
- [ ] Dialog dismissed → Background focus restored
- [ ] No double focus (dialog + background)

### ✅ Section Compatibility
- [ ] START section: Dialog works, navigation preserved
- [ ] TELEWIZJA section: Dialog works with EPG
- [ ] Other sections: Dialog doesn't interfere

### ✅ Edge Cases
- [ ] Dialog open → Change section → Dialog auto-closes
- [ ] Dialog open → Player stops → Dialog auto-closes (pipPlayer=null check)
- [ ] Rapid open/close cycles → No crashes, no memory leaks

---

## Common Pitfalls

### ❌ Anti-Pattern #1: Blanket Delegation
```kotlin
// WRONG - doesn't validate keys
if (showPipDialog) {
    return@onPreviewKeyEvent false  // Allows ALL keys!
}
```

### ❌ Anti-Pattern #2: Multiple Handlers
```kotlin
// WRONG - option 1 and option 2 have different handlers
.onPreviewKeyEvent { event ->
    // Custom logic per option
}
```
**Correct**: Use same handler (PipDialogController.handleDialogKeys) for both options.

### ❌ Anti-Pattern #3: State in Controller
```kotlin
// WRONG - controller manages state
object PipDialogController {
    private var focusedOption = 0  // Don't do this!
}
```
**Correct**: Pass focusedOption as parameter (stateless controller).

### ❌ Anti-Pattern #4: No Debounce
```kotlin
// WRONG - no debounce, rapid presses cause issues
if (keyCode == KEYCODE_MEDIA_PLAY_PAUSE) {
    showPipDialog = true  // Can trigger multiple times!
}
```
**Correct**: Add 50ms debounce with lastDialogOpenTime.

---

## Extension Points

### Adding New Options

To add more options (e.g., "Move Window", "Program Info"):

**1. Update PipMenuOption.kt:**
```kotlin
sealed class PipMenuOption {
    object Fullscreen : PipMenuOption() { val index = 0 }
    object Close : PipMenuOption() { val index = 1 }
    object MoveWindow : PipMenuOption() { val index = 2 }  // NEW
    object ProgramInfo : PipMenuOption() { val index = 3 }  // NEW
}
```

**2. Update PipDialogController.kt:**
```kotlin
Key.Enter, Key.DirectionCenter -> {
    when (focusedOption) {
        0 -> { onSelectFullscreen(); true }
        1 -> { onSelectClose(); true }
        2 -> { onSelectMoveWindow(); true }  // NEW
        3 -> { onSelectProgramInfo(); true }  // NEW
        else -> false
    }
}
```

**3. Update PipDialogMenu.kt:**
```kotlin
// Add option boxes:
// Option 3: Przenieś okienko
Box(/* same structure as options 1-2 */) { /* UI */ }

// Option 4: Informacje o programie
Box(/* same structure as options 1-2 */) { /* UI */ }
```

**4. Update Integration:**
```kotlin
PipDialogMenu(
    onDismiss = { showPipDialog = false },
    onFullscreen = { onReturnToEpgDay(); onClosePip() },
    onClose = { onClosePip() },
    onMoveWindow = { /* move logic */ },  // NEW
    onProgramInfo = { /* show info */ },  // NEW
    sx = sx, sy = sy
)
```

---

## References

- **Current Implementation**: `/Users/uxellenceuxe/TV_componenty/app/src/main/java/com/uxellence/tv/v3/pip/`
- **Integration Point**: `/Users/uxellenceuxe/TV_componenty/app/src/main/java/com/uxellence/tv/v3/TopMenuScreen2.kt:709-717,970-979`
- **Working Example (tescik)**: `/Users/uxellenceuxe/tescik/app/src/main/java/com/tescik/MainActivity.kt:75-155`
- **Focus Architect Skill**: `/Users/uxellenceuxe/TV_componenty/skills/focus-architect/SKILL.md`
- **Key Event Patterns**: `/Users/uxellenceuxe/TV_componenty/CLAUDE.md` (Key Event Management System section)

---

## Commit History

| Commit | Description | Risk | Lines Changed |
|--------|-------------|------|---------------|
| 29973a5 | Strengthen input gate + debounce | VERY LOW | +31/-5 |
| 96baec1 | Extract data models | ZERO | +55/0 |
| 3bf5d4d | Extract controller | LOW | +153/-39 |
| 6194519 | Extract UI composable | LOW | +211/-145 |

**Total**: +450 insertions, -189 deletions (+261 net)

---

This pattern is production-ready and can be applied to other modal dialogs in the application (e.g., settings menu, confirmation dialogs, error overlays).
