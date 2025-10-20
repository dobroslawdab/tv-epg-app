# ⚠️ Common Focus Conflicts & Solutions

**Purpose**: Catalog of known focus conflicts in TV_componenty project with proven solutions
**Last Updated**: 2025-10-03

---

## Conflict Type 1: Duplicate Key Handlers

### Description:
Multiple components handle the same key at the same level, causing the first handler to always win and others to be ignored.

### Symptoms:
- Navigation doesn't work as expected
- One component "steals" input from another
- Focus jumps unexpectedly

### Example from History:

**Issue #1: MainActivity BACK Key Conflict** (Historical)

```kotlin
// ❌ WRONG: Both handle Key.Back

// MainActivity.kt:
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        currentScreen = NavigationScreen.HOME
        true  // Consumes event
    }
}

// TopMenuScreen.kt (never executes!):
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        menuState = menuState.copy(isMenuFocused = true)
        true  // Never reached!
    }
}
```

### Solution:

**Use callback delegation pattern**:

```kotlin
// ✅ RIGHT

// MainActivity.kt:
TopMenuScreen(
    onBackPressed = { isMenuFocused ->
        if (isMenuFocused) {
            currentScreen = NavigationScreen.HOME
            true
        } else {
            false  // Let child handle
        }
    }
)

// TopMenuScreen.kt:
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        val handled = onBackPressed(menuState.isMenuFocused)
        if (!handled) {
            // Handle locally
            menuState = menuState.copy(isMenuFocused = true)
            return@onPreviewKeyEvent true
        }
        return@onPreviewKeyEvent handled
    }
}
```

### Prevention:
- Always grep for existing handlers: `grep -r "Key\.Back" app/src/`
- Use callbacks for parent↔child communication
- Never have two `onPreviewKeyEvent` for same key at same level

---

## Conflict Type 2: Parent Intercepts Child's Keys

### Description:
Parent component intercepts keys that should be handled by child, bypassing child's navigation logic.

### Symptoms:
- Child navigation completely broken
- Can't move through child's content
- Always jumps to parent action

### Example from History:

**Issue #3: VOD Navigation Conflict** (2025-09-30)

```kotlin
// ❌ WRONG: Parent intercepts UP for VOD

// TopMenuScreen2.kt:
"VOD" -> {
    when (event.key) {
        Key.DirectionUp -> {
            if (globalFocusState.value.currentRow == 1) {
                globalFocusState.value = GlobalFocusManager.returnToMenu(...)
                true  // ❌ Parent intercepts! VodWithChannels never runs
            } else false
        }
        else -> false
    }
}

// VodWithChannels.kt (never reached):
fun handleVodNavigation(...) {
    when (event.key) {
        Key.DirectionUp -> {
            // Complex channel navigation logic
            // Never executes!
        }
    }
}
```

### Solution:

**Full delegation - parent returns false**:

```kotlin
// ✅ RIGHT

// TopMenuScreen2.kt:
"VOD" -> {
    false  // Let VodWithChannels handle ALL keys
}

// VodWithChannels.kt:
fun handleVodNavigation(
    onReturnToMenu: () -> Unit  // Callback for menu transition
) {
    when (event.key) {
        Key.DirectionUp -> {
            when {
                focusedRowIndex == 0 && focusedColIndex == -1 -> {
                    onReturnToMenu()  // ✅ Child uses callback
                    true
                }
                else -> {
                    // Handle channel navigation
                    true
                }
            }
        }
    }
}
```

### Prevention:
- For complex navigation: ALWAYS delegate (return false)
- Child uses `onReturnToMenu` callback for parent actions
- Parent only intercepts for simple sections (like TELEWIZJA)

---

## Conflict Type 3: Missing Delegation in TopMenuScreen2

### Description:
New section added but not included in TopMenuScreen2's delegation block, causing it to fall through to default handler.

### Symptoms:
- Pressing UP from any row jumps to menu immediately
- Can't navigate channel-by-channel
- Section behaves differently than similar sections (MOJE/VOD)

### Example from History:

**Issue #2: APLIKACJE Navigation Conflict** (2025-09-30)

```kotlin
// ❌ WRONG: APLIKACJE not in delegation list

// TopMenuScreen2.kt:
.onPreviewKeyEvent { event ->
    when (currentSection) {
        "VOD" -> false
        "MOJE" -> false
        "START" -> false
        // APLIKACJE missing!
        "TELEWIZJA" -> { /* custom */ }
        else -> {
            // Falls through here - wrong behavior!
            when (event.key) {
                Key.DirectionUp -> {
                    if (globalFocusState.value.currentRow == 1) {
                        globalFocusState.value = GlobalFocusManager.returnToMenu(...)
                        true  // Jumps to menu from any channel!
                    } else false
                }
            }
        }
    }
}
```

### Solution:

**Add to delegation list**:

```kotlin
// ✅ RIGHT

// TopMenuScreen2.kt:
.onPreviewKeyEvent { event ->
    when (currentSection) {
        "VOD" -> false
        "MOJE" -> false
        "START" -> false
        "APLIKACJE" -> false  // ✅ Added! Now delegates properly
        "TELEWIZJA" -> { /* custom */ }
        else -> false
    }
}
```

### Prevention:
- Maintain checklist of all sections
- When adding new section, immediately add to delegation block
- Use grep to verify: `grep "APLIKACJE" TopMenuScreen2.kt`

---

## Conflict Type 4: Callback Not Invoked

### Description:
Handler function receives `onReturnToMenu` callback but forgets to call it when needed.

### Symptoms:
- Can't return to menu from first channel
- BACK key doesn't work
- Stuck in section

### Example:

```kotlin
// ❌ WRONG: Callback parameter exists but never called

fun handleMyNavigation(
    onReturnToMenu: () -> Unit,  // Parameter exists...
    // ... other params
): Boolean {
    return when (event.key) {
        Key.DirectionUp -> {
            when {
                focusedRowIndex == 0 && focusedColIndex == -1 -> {
                    // Should return to menu here!
                    // But forgot to call onReturnToMenu()
                    true
                }
                else -> {
                    // Navigate
                    true
                }
            }
        }
        else -> false
    }
}
```

### Solution:

**Always invoke callback when condition met**:

```kotlin
// ✅ RIGHT

fun handleMyNavigation(
    onReturnToMenu: () -> Unit,
    // ... other params
): Boolean {
    return when (event.key) {
        Key.DirectionUp -> {
            when {
                focusedRowIndex == 0 && focusedColIndex == -1 -> {
                    onReturnToMenu()  // ✅ Call the callback!
                    true
                }
                else -> {
                    // Navigate
                    true
                }
            }
        }
        else -> false
    }
}
```

### Prevention:
- Code review: verify callback is called
- Test: try returning to menu from first channel
- Search for callback name: `grep "onReturnToMenu()" MyNavigation.kt`

---

## Conflict Type 5: Regression (Previously Fixed Issue Returns)

### Description:
Conflict that was already fixed gets re-introduced by later changes, often due to merge conflicts or copy-paste from old code.

### Symptoms:
- Same symptoms as original issue
- Grep shows conflict pattern that was previously removed
- Git history shows fix was applied before

### Example from History:

**Issue #5: VOD Navigation Regression** (2025-10-03)

```kotlin
// Issue #3 was fixed on 2025-09-30
// But Issue #5 re-introduced same conflict on 2025-10-03

// TopMenuScreen2.kt (regression):
"VOD" -> {
    when (event.key) {
        Key.DirectionUp -> {
            // This code was REMOVED in Issue #3 fix
            // But came back somehow!
            if (globalFocusState.value.currentRow == 1) {
                globalFocusState.value = GlobalFocusManager.returnToMenu(...)
                true
            } else false
        }
    }
}
```

### Solution:

**Re-apply original fix + add prevention**:

```kotlin
// ✅ RIGHT: Same fix as Issue #3

"VOD" -> {
    false  // Back to simple delegation
}

// PLUS: Add automated check
// See scripts/focus-audit.sh
```

### Prevention:
- Use `scripts/focus-audit.sh` before every commit
- Code review: check git blame for recent changes to focus handlers
- Document in CLAUDE.md "Recent Conflict Resolution"
- Consider CI/CD integration (automated grep checks)

---

## Conflict Type 6: Missing Event Type Check

### Description:
Handler doesn't check `event.type == KeyEventType.KeyDown`, causing double-firing on key press+release.

### Symptoms:
- Navigation jumps two steps instead of one
- Callbacks fire twice
- State changes double

### Example:

```kotlin
// ❌ WRONG: No event type check

.onPreviewKeyEvent { event ->
    when (event.key) {
        Key.DirectionUp -> {
            navigateUp()  // Fires on both DOWN and UP!
            true
        }
        else -> false
    }
}
```

### Solution:

**Always check event type first**:

```kotlin
// ✅ RIGHT

.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) {
        return@onPreviewKeyEvent false  // Ignore key release
    }

    when (event.key) {
        Key.DirectionUp -> {
            navigateUp()  // Only fires once
            true
        }
        else -> false
    }
}
```

### Prevention:
- Template for all handlers should include type check
- Code review: verify `event.type` check at top
- Lint rule (future): enforce type check

---

## Conflict Type 7: Hardcoded State Manipulation

### Description:
Child component directly manipulates parent state instead of using callback.

### Symptoms:
- Tight coupling between parent/child
- Difficult to test
- Can't reuse component
- State changes bypass parent logic

### Example:

```kotlin
// ❌ WRONG: Child directly changes parent state

@Composable
fun MySection(
    currentSection: MutableState<String>,  // Direct access to parent state!
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier.onPreviewKeyEvent { event ->
            when (event.key) {
                Key.Back -> {
                    currentSection.value = "ODKRYWAJ"  // ❌ Direct manipulation
                    true
                }
                else -> false
            }
        }
    )
}
```

### Solution:

**Use callback for all parent state changes**:

```kotlin
// ✅ RIGHT

@Composable
fun MySection(
    onReturnToMenu: () -> Unit,  // Callback abstraction
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(
        modifier = Modifier.onPreviewKeyEvent { event ->
            when (event.key) {
                Key.Back -> {
                    onReturnToMenu()  // ✅ Use callback
                    true
                }
                else -> false
            }
        }
    )
}

// Parent decides what "return to menu" means:
MySection(
    onReturnToMenu = {
        currentSection = "ODKRYWAJ"
        // Can add other logic here (analytics, etc.)
    }
)
```

### Prevention:
- Never pass mutable state down to children
- Always use callbacks for upward communication
- Code review: check for `.value =` in child components

---

## Detection Checklist

Before committing focus-related code, run:

```bash
# 1. Duplicate handlers?
grep -r "Key\.Back" app/src/ | wc -l
grep -r "Key\.DirectionUp" app/src/ | wc -l

# 2. Proper delegation?
grep '"VOD" ->' TopMenuScreen2.kt
grep '"MOJE" ->' TopMenuScreen2.kt
grep '"APLIKACJE" ->' TopMenuScreen2.kt

# 3. Callbacks present?
grep -r "onReturnToMenu" app/src/

# 4. Event type checks?
grep -r "KeyEventType.KeyDown" app/src/

# 5. No hardcoded state?
grep -r "currentSection.value =" app/src/main/java/com/example/tv/components/
```

---

## Priority Matrix

| Conflict Type | Severity | Frequency | Detection | Fix Time |
|---------------|----------|-----------|-----------|----------|
| Duplicate Handlers | 🔴 High | Medium | Easy (grep) | 30 min |
| Parent Intercepts | 🔴 High | Medium | Medium | 1 hour |
| Missing Delegation | 🟡 Medium | Low | Easy | 15 min |
| Callback Not Invoked | 🟡 Medium | Low | Hard (testing) | 30 min |
| Regression | 🟡 Medium | Low | Easy (git) | 15 min |
| Missing Type Check | 🟢 Low | Medium | Easy (grep) | 5 min |
| Hardcoded State | 🟡 Medium | Rare | Medium | 1 hour |

---

**End of Common Conflicts**
