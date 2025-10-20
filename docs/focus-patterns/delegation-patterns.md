# 🔄 Focus Delegation Patterns

**Purpose**: Reference guide for proven delegation patterns in TV_componenty project
**Last Updated**: 2025-10-03

---

## Pattern 1: Full Delegation (Complex Navigation)

**Use when**: Section has multi-row, channel-by-channel navigation

**Example sections**: VOD, MOJE, START, APLIKACJE

### Parent (TopMenuScreen2):

```kotlin
.onPreviewKeyEvent { event ->
    when (currentSection) {
        "VOD" -> false  // Let child handle everything
        // ... other sections
    }
}
```

### Child (VodWithChannels):

```kotlin
@Composable
fun VodWithChannels(
    onReturnToMenu: () -> Unit,  // ⭐ Required callback
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                handleVodNavigation(
                    event = event,
                    focusedRowIndex = focusedRowIndex,
                    focusedColIndex = focusedColIndex,
                    onReturnToMenu = onReturnToMenu  // Pass through
                    // ... other params
                )
            }
    )
}
```

### Handler Function:

```kotlin
fun handleVodNavigation(
    event: KeyEvent,
    focusedRowIndex: Int,
    focusedColIndex: Int,
    onReturnToMenu: () -> Unit,  // ⭐ Critical
    // ... other params
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.DirectionUp -> {
            when {
                focusedColIndex == -1 && focusedRowIndex > 0 -> {
                    // Move to previous channel
                    onFocusChange(focusedRowIndex - 1, -1)
                    true
                }
                focusedColIndex == -1 && focusedRowIndex == 0 -> {
                    // Return to menu via callback
                    onReturnToMenu()
                    true
                }
                else -> {
                    // Handle content navigation
                    // ...
                    true
                }
            }
        }
        // ... other keys
        else -> false
    }
}
```

### Pros:
- ✅ Clean separation parent/child
- ✅ Child owns all navigation logic
- ✅ Easy to test independently
- ✅ No conflicts possible

### Cons:
- ⚠️ More boilerplate (handler function)
- ⚠️ Must remember onReturnToMenu callback

---

## Pattern 2: Custom Handling (Simple Navigation)

**Use when**: Section uses GlobalFocusManager for simple grid/row navigation

**Example sections**: TELEWIZJA

### Parent (TopMenuScreen2):

```kotlin
.onPreviewKeyEvent { event ->
    when (currentSection) {
        "TELEWIZJA" -> {
            when (event.key) {
                Key.DirectionUp -> {
                    val newState = GlobalFocusManager.navigateRow(
                        globalFocusState.value,
                        RowDirection.UP
                    )
                    if (newState.currentRow == 0) {
                        // Return to menu
                        globalFocusState.value = GlobalFocusManager.returnToMenu(
                            globalFocusState.value
                        )
                    } else {
                        globalFocusState.value = newState
                    }
                    true
                }
                Key.DirectionDown -> {
                    globalFocusState.value = GlobalFocusManager.navigateRow(
                        globalFocusState.value,
                        RowDirection.DOWN
                    )
                    true
                }
                // ... other keys
                else -> false
            }
        }
        // ... other sections
    }
}
```

### Child (TelewizjaGrid):

```kotlin
@Composable
fun TelewizjaGrid(
    globalFocusState: MutableState<GlobalFocusState>,
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    // No key handling - parent manages focus via GlobalFocusManager

    LazyVerticalGrid(
        columns = GridCells.Fixed(5)
    ) {
        items(channels) { channel ->
            val isFocused = GlobalFocusManager.shouldFocus(
                globalFocusState.value,
                targetRow = rowIndex,
                targetPosition = colIndex
            )

            ChannelCard(
                channel = channel,
                isFocused = isFocused,
                sx = sx,
                sy = sy
            )
        }
    }
}
```

### Pros:
- ✅ Less code in child (no handler)
- ✅ Centralized focus state (GlobalFocusManager)
- ✅ Simple mental model

### Cons:
- ⚠️ Only for simple navigation (row/grid)
- ⚠️ Parent has section-specific logic
- ⚠️ Less modular

---

## Pattern 3: Hybrid (Rare)

**Use when**: Section has mix of simple and complex navigation

**Example**: Not currently used, but possible for future

### Parent:

```kotlin
"HYBRID_SECTION" -> {
    when {
        isInSimpleMode -> {
            // Use GlobalFocusManager (Pattern 2)
            handleSimpleNavigation(event)
        }
        isInComplexMode -> {
            // Delegate to child (Pattern 1)
            false
        }
        else -> false
    }
}
```

**Recommendation**: Avoid if possible. Prefer Pattern 1 or 2 exclusively.

---

## Decision Tree: Which Pattern?

```
START: New section to implement
  |
  ├─→ Simple grid/row navigation?
  │   └─→ YES: Use Pattern 2 (Custom + GlobalFocusManager)
  │
  └─→ Complex multi-row with channels?
      └─→ YES: Use Pattern 1 (Full Delegation)
```

---

## Migration Guide: Pattern 2 → Pattern 1

If section grows complex, migrate:

### Before (Pattern 2):

```kotlin
"SECTION" -> {
    when (event.key) {
        Key.DirectionUp -> { /* custom logic */ }
        // ...
    }
}
```

### After (Pattern 1):

```kotlin
// 1. Create SectionNavigation.kt with handler function
fun handleSectionNavigation(...) { ... }

// 2. Create Section composable
@Composable
fun SectionComponent(onReturnToMenu: () -> Unit) {
    Box(modifier = Modifier.onPreviewKeyEvent {
        handleSectionNavigation(..., onReturnToMenu)
    })
}

// 3. Update TopMenuScreen2
"SECTION" -> false  // Delegate
```

---

## Common Implementation Mistakes

### Mistake #1: Missing onReturnToMenu callback

```kotlin
// ❌ WRONG
@Composable
fun MySection(sx: (Int) -> Dp, sy: (Int) -> Dp) {
    // No onReturnToMenu parameter!
}

// ✅ RIGHT
@Composable
fun MySection(
    onReturnToMenu: () -> Unit,  // Required!
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
)
```

### Mistake #2: Parent intercepts after delegating

```kotlin
// ❌ WRONG
"VOD" -> {
    false  // Delegates...
}
// ... later in same file:
.onPreviewKeyEvent { event ->
    if (currentSection == "VOD" && event.key == Key.Back) {
        // But then intercepts anyway!
    }
}

// ✅ RIGHT
"VOD" -> {
    false  // Delegates, and ONLY delegates
}
```

### Mistake #3: Callback not invoked

```kotlin
// ❌ WRONG
fun handleNavigation(
    onReturnToMenu: () -> Unit
): Boolean {
    return when {
        shouldReturnToMenu -> {
            // Forgot to call onReturnToMenu()!
            true
        }
        else -> false
    }
}

// ✅ RIGHT
fun handleNavigation(
    onReturnToMenu: () -> Unit
): Boolean {
    return when {
        shouldReturnToMenu -> {
            onReturnToMenu()  // Call the callback!
            true
        }
        else -> false
    }
}
```

---

## Testing Delegation Patterns

### Manual Test Checklist:

For Pattern 1 (Full Delegation):
```bash
□ Navigate UP/DOWN through all channels
□ Navigate LEFT/RIGHT through content
□ Press UP from first channel → returns to menu
□ Press BACK from content → returns to CategoryIcon
□ Press BACK from CategoryIcon → returns to menu
□ No other sections affected
```

For Pattern 2 (Custom Handling):
```bash
□ Navigate through grid using GlobalFocusManager
□ Return to menu works from first row
□ Focus state persists across section changes
□ No other sections affected
```

### Automated Grep Checks:

```bash
# Verify delegation is set
grep '"VOD" ->' TopMenuScreen2.kt | grep "false"

# Verify callback exists
grep "onReturnToMenu" VodWithChannels.kt

# Verify no duplicates
grep -c "Key.DirectionUp" VodWithChannels.kt  # Should be 1
grep -c "Key.DirectionUp" TopMenuScreen2.kt   # Check context
```

---

## Pattern Evolution History

### Version 0.01-0.05:
- No delegation pattern
- All navigation in one file (messy!)

### Version 0.06:
- Introduced Pattern 1 for VOD
- TELEWIZJA still used inline handling

### Version 0.07 (current):
- Pattern 1: VOD, MOJE, START, APLIKACJE
- Pattern 2: TELEWIZJA (simple grid)
- All conflicts resolved

### Future:
- Possible Pattern 1 for all sections (consistency)
- Extract GlobalFocusManager to library (reusability)

---

**End of Delegation Patterns**
