# 🔧 Focus Conflict Resolution Strategies

**Purpose**: Step-by-step strategies for resolving focus conflicts in TV_componenty project
**Last Updated**: 2025-10-03

---

## General Resolution Process

### Phase 1: Detection (5-10 min)

1. **Identify the symptom**:
   - What isn't working? (specific navigation action)
   - Which section? (VOD, MOJE, TELEWIZJA, etc.)
   - Which key? (Back, Up, Down, Left, Right)

2. **Reproduce reliably**:
   - Document exact steps to trigger issue
   - Test on device if possible
   - Note any error messages in logcat

3. **Search for handlers**:
   ```bash
   # Find all handlers for the problematic key
   grep -rn "Key\.DirectionUp" app/src/main/java/com/example/tv/

   # Find handlers in specific files
   grep -n "onPreviewKeyEvent\|onKeyEvent" TopMenuScreen2.kt
   ```

### Phase 2: Analysis (10-15 min)

1. **Map the handler hierarchy**:
   ```
   Who handles this key?
   - File A: line X (Level: Global/Screen/Component)
   - File B: line Y (Level: Global/Screen/Component)
   - File C: line Z (Level: Global/Screen/Component)
   ```

2. **Check execution order**:
   - Parent runs before child (onPreviewKeyEvent)
   - If parent returns `true`, child never sees event
   - If parent returns `false`, child gets the event

3. **Verify delegation pattern**:
   ```bash
   # Check if section uses delegation
   grep -A 2 '"VOD" ->' TopMenuScreen2.kt

   # Check if callback exists
   grep "onReturnToMenu" VodWithChannels.kt
   ```

4. **Review history** (check if this was fixed before):
   ```bash
   git log --grep="VOD navigation" --oneline
   git blame TopMenuScreen2.kt | grep "VOD"
   ```

### Phase 3: Solution (15-30 min)

Choose strategy based on conflict type (see below).

### Phase 4: Verification (10-15 min)

1. **Test the fix**:
   - Reproduce original issue → should be fixed
   - Test related scenarios (BACK, UP, DOWN, LEFT, RIGHT)
   - Test other sections (ensure no side effects)

2. **Run automated checks**:
   ```bash
   ./scripts/focus-audit.sh
   ```

3. **Code review yourself**:
   - Check for new conflicts introduced
   - Verify naming conventions
   - Ensure documentation updated

### Phase 5: Documentation (5-10 min)

1. **Update CLAUDE.md**:
   ```markdown
   #### Issue #X: [Short Description] (YYYY-MM-DD)
   - Issue: [What was wrong]
   - Symptom: [How it manifested]
   - Root Cause: [Why it happened]
   - Solution: [What was changed]
   - Files: [filename.kt:line]
   ```

2. **Update FOCUS_NAVIGATION_GUIDE.md** (if new pattern):
   - Add section for new component
   - Document navigation flow

3. **Commit message**:
   ```
   Fix: [Component] navigation conflict

   - Issue: [Description]
   - Solution: [What changed]
   - Fixes: #[issue number]
   ```

---

## Strategy 1: Duplicate Handler Resolution

**When**: Two components handle same key at same level

### Diagnosis:
```bash
# Check for duplicates
grep -rn "Key\.Back" app/src/ | grep ".kt:"

# If multiple files shown at same level → duplicate handler
```

### Resolution Steps:

1. **Identify the hierarchy**:
   - Which handler should win?
   - Does child need to sometimes handle, sometimes parent?

2. **Choose approach**:

   **Option A: Parent owns key (simple case)**
   ```kotlin
   // Parent handles completely
   .onPreviewKeyEvent { event ->
       if (event.key == Key.Back) {
           handleBack()
           true
       }
   }

   // Child removes its handler entirely
   // (delete the onPreviewKeyEvent for that key)
   ```

   **Option B: Child owns key (delegation)**
   ```kotlin
   // Parent delegates
   .onPreviewKeyEvent { event ->
       if (event.key == Key.Back) {
           false  // Let child handle
       }
   }

   // Child handles
   .onPreviewKeyEvent { event ->
       if (event.key == Key.Back) {
           handleBack()
           true
       }
   }
   ```

   **Option C: Conditional (callback pattern)**
   ```kotlin
   // Parent uses callback
   Child(
       onBackPressed = { childState ->
           if (shouldParentHandle(childState)) {
               handleInParent()
               true
           } else {
               false  // Let child handle
           }
       }
   )

   // Child coordinates via callback
   .onPreviewKeyEvent { event ->
       if (event.key == Key.Back) {
           val handled = onBackPressed(currentState)
           if (!handled) {
               handleInChild()
           }
           true
       }
   }
   ```

3. **Verify no other duplicates**:
   ```bash
   grep -c "Key\.Back" Parent.kt  # Should be 0 or 1
   grep -c "Key\.Back" Child.kt   # Should be 0 or 1
   ```

---

## Strategy 2: Parent Interception Resolution

**When**: Parent intercepts keys that child should handle

### Diagnosis:
```bash
# Check delegation block
grep -A 5 '"VOD" ->' TopMenuScreen2.kt

# If you see actual key handling inside → parent intercepts
# Should see just: false  (for delegation)
```

### Resolution Steps:

1. **Identify section type**:
   - Complex multi-row navigation (VOD, MOJE) → Use full delegation
   - Simple grid navigation (TELEWIZJA) → Custom handling OK

2. **For complex sections**:

   **Remove parent interception**:
   ```kotlin
   // BEFORE (wrong):
   "VOD" -> {
       when (event.key) {
           Key.DirectionUp -> {
               // Custom logic here
               true
           }
           else -> false
       }
   }

   // AFTER (correct):
   "VOD" -> {
       false  // Let child handle everything
   }
   ```

   **Ensure child has callback**:
   ```kotlin
   // Child handler function
   fun handleVodNavigation(
       onReturnToMenu: () -> Unit,  // Add if missing
       // ... other params
   ) {
       // When should return to menu:
       when {
           focusedRowIndex == 0 && focusedColIndex == -1 -> {
               onReturnToMenu()  // Use callback
               true
           }
       }
   }
   ```

3. **For simple sections**:
   - Keep parent handling (Pattern 2)
   - Child doesn't handle keys at all
   - Use GlobalFocusManager for state

---

## Strategy 3: Missing Delegation Resolution

**When**: Section not in TopMenuScreen2 delegation block

### Diagnosis:
```bash
# Check if section is listed
grep "APLIKACJE" TopMenuScreen2.kt

# If not found in delegation block → missing
```

### Resolution Steps:

1. **Locate delegation block** (around line 200 in TopMenuScreen2.kt):
   ```kotlin
   .onPreviewKeyEvent { event ->
       when (currentSection) {
           "VOD" -> false
           "MOJE" -> false
           "START" -> false
           // Missing sections here!
           "TELEWIZJA" -> { /* custom */ }
           else -> false
       }
   }
   ```

2. **Add missing section**:
   ```kotlin
   when (currentSection) {
       "VOD" -> false
       "MOJE" -> false
       "START" -> false
       "APLIKACJE" -> false  // ✅ Add here
       "NOWOŚCI" -> false    // ✅ Add new section
       "TELEWIZJA" -> { /* custom */ }
       else -> false
   }
   ```

3. **Verify alphabetical order** (optional, for maintainability):
   ```kotlin
   when (currentSection) {
       "APLIKACJE" -> false
       "MOJE" -> false
       "NOWOŚCI" -> false
       "START" -> false
       "TELEWIZJA" -> { /* custom */ }
       "VOD" -> false
       else -> false
   }
   ```

---

## Strategy 4: Missing Callback Resolution

**When**: Callback parameter exists but not invoked

### Diagnosis:
```bash
# Check if callback parameter exists
grep "onReturnToMenu: () -> Unit" MyNavigation.kt

# Check if it's called
grep "onReturnToMenu()" MyNavigation.kt

# If first grep found but second didn't → missing invocation
```

### Resolution Steps:

1. **Find where callback should be invoked**:
   - When should component return to menu?
   - Typically: UP from first channel + CategoryIcon

2. **Add callback invocation**:
   ```kotlin
   // BEFORE (wrong):
   when {
       focusedRowIndex == 0 && focusedColIndex == -1 -> {
           // Should return to menu but doesn't
           true
       }
   }

   // AFTER (correct):
   when {
       focusedRowIndex == 0 && focusedColIndex == -1 -> {
           onReturnToMenu()  // ✅ Call callback
           true
       }
   }
   ```

3. **Verify parent receives callback**:
   ```kotlin
   // In parent composable:
   MySection(
       onReturnToMenu = {
           println("Callback received!")  // Debug
           currentSection = "ODKRYWAJ"
       }
   )
   ```

---

## Strategy 5: Regression Resolution

**When**: Previously fixed issue returns

### Diagnosis:
```bash
# Check git history
git log --grep="VOD navigation" --oneline

# Check when line was last modified
git blame TopMenuScreen2.kt | grep "VOD"

# Find the fix commit
git show <commit-hash>
```

### Resolution Steps:

1. **Find original fix**:
   ```bash
   git log -p --grep="Issue #3"
   # Review what was changed
   ```

2. **Re-apply fix**:
   - Copy code from original fix commit
   - Or manually apply same pattern

3. **Identify why it regressed**:
   - Merge conflict resolved incorrectly?
   - Code copied from old version?
   - Lack of automated testing?

4. **Add prevention**:
   ```bash
   # Add to scripts/focus-audit.sh:
   echo "Checking VOD delegation..."
   if grep -q '"VOD" -> {' TopMenuScreen2.kt; then
       echo "❌ VOD using custom handling (should be: false)"
   else
       echo "✅ VOD delegation OK"
   fi
   ```

---

## Strategy 6: Event Type Check Resolution

**When**: Navigation fires twice (on key down + key up)

### Diagnosis:
```bash
# Check for event type verification
grep "KeyEventType.KeyDown" MyComponent.kt

# If not found → missing check
```

### Resolution Steps:

1. **Add type check at top of handler**:
   ```kotlin
   // BEFORE (wrong):
   .onPreviewKeyEvent { event ->
       when (event.key) {
           Key.DirectionUp -> {
               navigate()  // Fires twice!
               true
           }
       }
   }

   // AFTER (correct):
   .onPreviewKeyEvent { event ->
       if (event.type != KeyEventType.KeyDown) {
           return@onPreviewKeyEvent false
       }

       when (event.key) {
           Key.DirectionUp -> {
               navigate()  // Fires once
               true
           }
       }
   }
   ```

2. **Verify all handlers have check**:
   ```bash
   # Find handlers without type check
   grep -L "KeyEventType.KeyDown" \
     $(grep -l "onPreviewKeyEvent" app/src/main/java/com/example/tv/*.kt)
   ```

---

## Strategy 7: Hardcoded State Resolution

**When**: Child directly manipulates parent state

### Diagnosis:
```bash
# Look for .value = in child components
grep "currentSection.value =" app/src/main/java/com/example/tv/components/

# If found → hardcoded state manipulation
```

### Resolution Steps:

1. **Replace state parameter with callback**:
   ```kotlin
   // BEFORE (wrong):
   @Composable
   fun MySection(
       currentSection: MutableState<String>,  // ❌ Direct access
       sx: (Int) -> Dp
   ) {
       Box(modifier = Modifier.onPreviewKeyEvent {
           currentSection.value = "ODKRYWAJ"  // ❌ Direct manipulation
       })
   }

   // AFTER (correct):
   @Composable
   fun MySection(
       onReturnToMenu: () -> Unit,  // ✅ Callback
       sx: (Int) -> Dp
   ) {
       Box(modifier = Modifier.onPreviewKeyEvent {
           onReturnToMenu()  // ✅ Use callback
       })
   }
   ```

2. **Update parent usage**:
   ```kotlin
   // Parent:
   MySection(
       onReturnToMenu = {
           currentSection = "ODKRYWAJ"
           // Can add other logic here
       }
   )
   ```

---

## Emergency Quick Fixes

When you need a FAST fix (temporary):

### Quick Fix 1: Disable Handler
```kotlin
// Temporarily disable problematic handler
.onPreviewKeyEvent { event ->
    if (true) return@onPreviewKeyEvent false  // Bypass entire handler
    // ... rest of handler
}
```

### Quick Fix 2: Log Everything
```kotlin
// Add logging to understand flow
.onPreviewKeyEvent { event ->
    Log.d("FOCUS", "Component: ${event.key}, type: ${event.type}")
    // ... rest of handler
}
```

### Quick Fix 3: Force Return False
```kotlin
// Force delegation (even if logic is wrong)
"PROBLEMATIC_SECTION" -> {
    false  // Force delegate to child
}
```

**⚠️ WARNING**: Quick fixes are NOT solutions! Always follow up with proper resolution.

---

## Resolution Decision Tree

```
START: Focus issue detected
  |
  ├─→ Same key handled twice?
  │   └─→ YES: Use Strategy 1 (Duplicate Handler)
  |
  ├─→ Parent intercepts child's keys?
  │   └─→ YES: Use Strategy 2 (Parent Interception)
  |
  ├─→ Section missing from delegation block?
  │   └─→ YES: Use Strategy 3 (Missing Delegation)
  |
  ├─→ Callback parameter not called?
  │   └─→ YES: Use Strategy 4 (Missing Callback)
  |
  ├─→ Was this fixed before?
  │   └─→ YES: Use Strategy 5 (Regression)
  |
  ├─→ Navigation fires twice?
  │   └─→ YES: Use Strategy 6 (Event Type Check)
  |
  └─→ Child manipulates parent state directly?
      └─→ YES: Use Strategy 7 (Hardcoded State)
```

---

## Post-Resolution Checklist

After applying any strategy:

```bash
□ Issue is fixed (tested on device if possible)
□ No new conflicts introduced (grep check)
□ Related sections still work (regression test)
□ Documentation updated (CLAUDE.md)
□ Commit message follows template
□ scripts/focus-audit.sh runs clean
□ Code review completed (self or peer)
```

---

**End of Resolution Strategies**
