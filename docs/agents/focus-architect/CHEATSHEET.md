# 🎯 Focus Architect - Cheat Sheet

Quick reference for daily Focus Architect usage.

---

## 🔥 Most Common Commands

```bash
# Before coding new navigation
./scripts/focus-quick-check.sh

# Before git commit
./scripts/focus-quick-check.sh && git commit -m "..."

# Before release
./scripts/focus-audit.sh

# Quick diagnosis
grep -r "Key\.DirectionUp" app/src/ | wc -l
```

---

## 📋 Claude Code Templates

### Template 1: Pre-Implementation Audit

```bash
claude code \
  -f docs/agents/focus-architect.md \
  -f FOCUS_NAVIGATION_GUIDE.md \
  -f KEY_EVENT_CHECKLIST.md \
  -p "Focus Architect: Pre-implementation audit for section [SECTION_NAME].
      Requirements: [describe navigation requirements].
      Use Template 1."
```

### Template 2: Code Review

```bash
claude code \
  -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/components/[Component].kt \
  -p "Focus Architect: Code review of [Component] navigation.
      Use Template 2."
```

### Template 3: Fix Conflict

```bash
claude code \
  -f docs/agents/focus-architect.md \
  -f docs/focus-patterns/resolution-strategies.md \
  -f app/src/main/java/com/example/tv/[File].kt \
  -p "Focus Architect: [Describe issue].
      Use Strategy [1-7] from resolution-strategies.md."
```

### Template 4: Full System Audit

```bash
claude code \
  -f docs/agents/focus-architect.md \
  -f app/src/main/java/com/example/tv/*.kt \
  -p "Focus Architect: Full system conflict detection.
      Use Template 3."
```

---

## 🔍 Quick Grep Checks

```bash
# Find all handlers for a key
grep -rn "Key\.Back" app/src/

# Check delegation
grep -A 2 '"VOD" ->' app/src/main/java/com/example/tv/TopMenuScreen2.kt

# Verify callbacks
grep -c "onReturnToMenu" app/src/

# Check event type coverage
grep -r "KeyEventType\.KeyDown" app/src/ | wc -l

# Find hardcoded state
grep -r "currentSection\.value =" app/src/main/java/com/example/tv/components/
```

---

## 📐 Decision Trees

### Which Pattern?

```
Multi-row channel navigation? → Pattern 1 (Full Delegation)
Simple grid/row navigation?   → Pattern 2 (Custom + GlobalFocusManager)
When in doubt?                 → Pattern 1 (safer)
```

### Which Strategy?

```
Same key handled twice?              → Strategy 1
Parent intercepts child's keys?      → Strategy 2
Section missing from delegation?     → Strategy 3
Callback not invoked?                → Strategy 4
Was this fixed before?               → Strategy 5
Navigation fires twice?              → Strategy 6
Hardcoded state manipulation?        → Strategy 7
```

---

## 🚨 Common Anti-Patterns

### ❌ WRONG: Multiple BACK handlers

```kotlin
// Parent
.onPreviewKeyEvent { if (event.key == Key.Back) { ... } }

// Child
.onPreviewKeyEvent { if (event.key == Key.Back) { ... } }  // Conflict!
```

### ✅ RIGHT: Callback delegation

```kotlin
// Parent
Child(onBackPressed = { ... })

// Child
.onPreviewKeyEvent {
    if (event.key == Key.Back) {
        onBackPressed()
    }
}
```

---

### ❌ WRONG: Parent intercepts

```kotlin
"VOD" -> {
    when (event.key) {
        Key.DirectionUp -> { /* logic */ true }  // Bypasses child!
    }
}
```

### ✅ RIGHT: Full delegation

```kotlin
"VOD" -> false  // Let child handle all keys
```

---

### ❌ WRONG: Missing event type check

```kotlin
.onPreviewKeyEvent { event ->
    when (event.key) {
        Key.DirectionUp -> { navigate(); true }  // Fires twice!
    }
}
```

### ✅ RIGHT: Check event type

```kotlin
.onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

    when (event.key) {
        Key.DirectionUp -> { navigate(); true }  // Fires once
    }
}
```

---

### ❌ WRONG: Hardcoded state

```kotlin
@Composable
fun MySection(currentSection: MutableState<String>) {
    currentSection.value = "ODKRYWAJ"  // Direct manipulation!
}
```

### ✅ RIGHT: Use callback

```kotlin
@Composable
fun MySection(onReturnToMenu: () -> Unit) {
    onReturnToMenu()  // Parent decides what this means
}
```

---

## 🎯 Pattern 1 Checklist

Full Delegation for complex navigation:

```kotlin
// ✅ TopMenuScreen2.kt
"SECTION" -> false  // Delegate

// ✅ SectionComponent.kt
@Composable
fun SectionComponent(
    onReturnToMenu: () -> Unit,  // Required!
    sx: (Int) -> Dp,
    sy: (Int) -> Dp
) {
    Box(modifier = Modifier.onPreviewKeyEvent {
        handleSectionNavigation(event, onReturnToMenu)
    })
}

// ✅ SectionNavigation.kt
fun handleSectionNavigation(
    event: KeyEvent,
    onReturnToMenu: () -> Unit  // Required!
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    return when (event.key) {
        Key.DirectionUp -> {
            if (shouldReturnToMenu) {
                onReturnToMenu()  // Call it!
                true
            } else {
                // Navigate
                true
            }
        }
        else -> false
    }
}
```

---

## 📝 Commit Message Template

```
Fix/Add: [Component] navigation

- Issue: [What was wrong]
- Solution: [What changed]
- Pattern: [1/2/3]
- Files: [list]

Fixes: #[issue]
```

---

## 🔧 Git Hooks

### Pre-commit Hook

```bash
#!/bin/bash
./scripts/focus-quick-check.sh
exit $?
```

**Install**:
```bash
echo '#!/bin/bash
./scripts/focus-quick-check.sh
exit $?' > .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

---

## 📊 Audit Interpretation

### focus-audit.sh Output:

```
Key.Back: 35 handlers           → Normal (many components use BACK)
Key.DirectionUp: 85 handlers    → Normal (navigation is complex)

✅ VOD: Properly delegated      → Good! Pattern 1 working
❌ VOD: Missing delegation       → FIX IMMEDIATELY (use Strategy 2)

✅ Callbacks: 279 references    → Good! Sections using callbacks
⚠️  Callbacks: Low count        → Check new sections (use Strategy 4)

✅ Event type: 80% coverage     → Good!
❌ Event type: 26% coverage     → Add checks (use Strategy 6)

✅ No hardcoded state           → Good!
❌ Found 3 instances            → Fix with callbacks (use Strategy 7)
```

---

## 🆘 Emergency Quick Fixes

When stuck, use these temporary fixes:

```kotlin
// 1. Disable handler (debug)
.onPreviewKeyEvent { event ->
    Log.d("FOCUS", "Key: ${event.key}")
    false  // Bypass
}

// 2. Force delegation (temporary)
"SECTION" -> false  // Force delegate (even if wrong)

// 3. Add logging
.onPreviewKeyEvent { event ->
    Log.d("FOCUS", "Component X: ${event.key}, handled: $result")
    result
}
```

**⚠️ WARNING**: These are NOT permanent solutions! Follow up with proper fix.

---

## 📚 File Quick Reference

| Need | File | Size |
|------|------|------|
| Quick help | CHEATSHEET.md | 1 page |
| Usage guide | README.md | 16 KB |
| Core agent | focus-architect.md | 19 KB |
| Patterns | delegation-patterns.md | 8 KB |
| Known issues | common-conflicts.md | 12 KB |
| Fix procedures | resolution-strategies.md | 14 KB |
| Quick check | ./scripts/focus-quick-check.sh | 5 sec |
| Full audit | ./scripts/focus-audit.sh | 30 sec |

---

## ⚡ One-Liners

```bash
# Count handlers by key
for key in Back DirectionUp DirectionDown DirectionLeft DirectionRight; do
    echo "$key: $(grep -rc "Key\.$key" app/src/ 2>/dev/null | awk -F: '{s+=$2} END {print s}')";
done

# Check all section delegations
grep -A 1 '"[A-Z].*" ->' app/src/main/java/com/example/tv/TopMenuScreen2.kt

# Find handlers without type check
comm -23 \
  <(grep -l "onPreviewKeyEvent" app/src/main/java/com/example/tv/*.kt | sort) \
  <(grep -l "KeyEventType.KeyDown" app/src/main/java/com/example/tv/*.kt | sort)

# Git pre-commit hook one-liner
echo './scripts/focus-quick-check.sh' > .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
```

---

**TIP**: Print this cheat sheet or keep it open during development!

**Last Updated**: 2025-10-03
