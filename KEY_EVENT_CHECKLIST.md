# 📋 Key Event Management Checklist

## **Pre-Implementation Checklist**

### **Before Adding ANY Key Event Handler:**

#### ☐ **1. Conflict Assessment**
- [ ] Search existing codebase for the same key: `grep -r "Key\.Back\|KEYCODE_BACK" app/src/`
- [ ] Check if parent components already handle this key
- [ ] Verify no conflicts at the same priority level
- [ ] Document any existing handlers that might be affected

#### ☐ **2. Priority Level Decision**
- [ ] **GLOBAL (MainActivity)**: Cross-screen navigation, app exit
- [ ] **SCREEN (TopMenuScreen)**: Intra-screen navigation, coordination with MainActivity
- [ ] **COMPONENT (SliderScreen)**: Component-specific interactions
- [ ] **WIDGET (Individual elements)**: Micro-interactions only

#### ☐ **3. Coordination Strategy**
- [ ] **BACK Key**: Always needs coordination with parent level
- [ ] **Directional Keys**: Check if delegation is needed
- [ ] **Enter/OK**: Usually local to component
- [ ] Design callback structure for parent-child communication

#### ☐ **4. Documentation Requirements**
- [ ] Handler ID follows naming convention: `{Component}.{Scope}.{Purpose}`
- [ ] Add descriptive comment explaining scope and delegation
- [ ] Update CLAUDE.md if introducing new patterns
- [ ] Document any non-standard behavior

---

## **Implementation Checklist**

### **☐ Code Structure**

#### **Handler Registration**
- [ ] Use `RegisterKeyHandler` composable for new architecture
- [ ] OR use `SafeNavigationScope` for existing code migration
- [ ] Set appropriate priority level
- [ ] Use descriptive handler ID
- [ ] Include comprehensive description

#### **Key Event Logic**
- [ ] Check `event.type == KeyEventType.KeyDown` (prevent double events)
- [ ] Handle only keys declared in registration
- [ ] Return explicit `true` (consumed) or `false` (pass through)
- [ ] Implement proper callback delegation for BACK key

#### **Error Handling**
- [ ] Wrap complex logic in try-catch blocks
- [ ] Provide fallback behavior for failed operations
- [ ] Log errors appropriately without crashing

### **☐ Coordination Patterns**

#### **For BACK Key Handlers**
- [ ] Implement callback delegation: `onBackPressed: (state) -> Boolean`
- [ ] Check parent result before handling locally
- [ ] Never directly intercept BACK without coordination
- [ ] Document the delegation chain

#### **For Directional Keys**
- [ ] Consider if delegation is needed
- [ ] Implement proper focus management
- [ ] Avoid conflicts with child component navigation

### **☐ Testing Strategy**
- [ ] Test with `KeyEventDebugOverlay` enabled
- [ ] Run conflict detection: `KeyEventManager.detectConflicts()`
- [ ] Verify proper event flow with live monitoring
- [ ] Test edge cases (rapid key presses, focus changes)

---

## **Testing & Validation Checklist**

### **☐ Debug Testing**

#### **Conflict Detection**
- [ ] Enable debug overlay: `AdvancedKeyEventDebugOverlay`
- [ ] Check for red conflict warnings
- [ ] Run `ConflictAnalysisDialog` for detailed analysis
- [ ] Resolve any detected conflicts before proceeding

#### **Live Monitoring**
- [ ] Enable `LiveKeyEventMonitor`
- [ ] Test all key combinations
- [ ] Verify events are consumed by correct handlers
- [ ] Check for event leakage (unconsumde events)

#### **Priority Verification**
- [ ] Confirm higher priority handlers execute first
- [ ] Test delegation chain works correctly
- [ ] Verify callbacks are invoked in proper order

### **☐ Functional Testing**

#### **Navigation Flow**
- [ ] Test complete navigation scenarios
- [ ] Verify BACK key behavior from all states
- [ ] Check focus management works correctly
- [ ] Test rapid navigation (edge cases)

#### **Integration Testing**
- [ ] Test with other screen components
- [ ] Verify no interference with existing functionality
- [ ] Check memory usage (handler registration/cleanup)

---

## **Post-Implementation Checklist**

### **☐ Documentation Updates**

#### **Code Documentation**
- [ ] Add comprehensive handler comments
- [ ] Update CLAUDE.md if new patterns introduced
- [ ] Document any breaking changes
- [ ] Add examples for future reference

#### **Architecture Documentation**
- [ ] Update key event flow diagrams
- [ ] Document new coordination patterns
- [ ] Add troubleshooting notes for common issues

### **☐ Code Review Items**

#### **Review Checklist for PRs**
- [ ] Handler IDs follow naming conventions
- [ ] Proper priority levels assigned
- [ ] No undocumented key handlers
- [ ] Conflict resolution strategy documented
- [ ] Debug tools integrated
- [ ] Test coverage adequate

### **☐ Maintenance**

#### **Ongoing Monitoring**
- [ ] Set up automated conflict detection in CI/CD
- [ ] Monitor performance impact
- [ ] Review handler registrations periodically
- [ ] Update documentation as system evolves

---

## **Common Patterns Quick Reference**

### **✅ CORRECT PATTERNS**

#### **Global Navigation (MainActivity)**
```kotlin
TopMenuScreen(
    onBackPressed = { isMenuFocused ->
        if (isMenuFocused) {
            currentScreen = NavigationScreen.HOME
            true
        } else {
            false
        }
    }
)
```

#### **Screen Coordination (TopMenuScreen)**
```kotlin
RegisterKeyHandler(
    id = "TopMenuScreen.Screen.Navigation",
    priority = KeyEventManager.Priority.SCREEN,
    keys = setOf(Key.Back),
    description = "Coordinates BACK key with MainActivity"
) { event ->
    if (event.key == Key.Back) {
        val handled = onBackPressed(menuState.isMenuFocused)
        if (!handled && !menuState.isMenuFocused) {
            menuState = menuState.copy(isMenuFocused = true)
            return@RegisterKeyHandler true
        }
        handled
    } else false
}
```

#### **Component Navigation**
```kotlin
SafeNavigationScope(
    scopeId = "SliderComponent.Navigation",
    priority = KeyEventManager.Priority.COMPONENT,
    keys = CommonKeySets.DIRECTIONAL,
    onKeyEvent = { event ->
        when (event.key) {
            Key.DirectionLeft -> handleLeft()
            Key.DirectionRight -> handleRight()
            else -> false
        }
    }
) {
    // Component content
}
```

### **❌ ANTI-PATTERNS TO AVOID**

#### **Multiple BACK Handlers**
```kotlin
// WRONG: Don't do this
.onPreviewKeyEvent { event ->
    if (event.key == Key.Back) {
        currentScreen = NavigationScreen.HOME // Conflicts!
        true
    }
    false
}
```

#### **Undocumented Handlers**
```kotlin
// WRONG: No context or coordination info
.onPreviewKeyEvent { event ->
    handleKey(event) // What does this do?
}
```

#### **Direct Interception**
```kotlin
// WRONG: No coordination with parent
.onPreviewKeyEvent { event ->
    // This bypasses the coordination system
    handleDirectly(event)
}
```

---

## **Emergency Conflict Resolution**

If you encounter conflicts after implementation:

### **☐ Immediate Steps**
1. [ ] Enable `AdvancedKeyEventDebugOverlay`
2. [ ] Run `ConflictAnalysisDialog`
3. [ ] Identify conflicting handlers
4. [ ] Check priority levels and delegation chains

### **☐ Resolution Strategies**
1. [ ] **Different Priorities**: Assign different priority levels
2. [ ] **Callback Delegation**: Use parent-child coordination
3. [ ] **Scope Separation**: Narrow the scope of each handler
4. [ ] **Conditional Handling**: Add state-based conditions

### **☐ Testing After Resolution**
1. [ ] Re-run conflict detection
2. [ ] Test complete navigation flows
3. [ ] Verify no new conflicts introduced
4. [ ] Update documentation

---

## **Checklist Template for PRs**

```markdown
## Key Event Handler Changes

### Pre-Implementation ✅
- [ ] Searched for existing handlers of same keys
- [ ] Determined appropriate priority level
- [ ] Designed coordination strategy
- [ ] Planned documentation updates

### Implementation ✅
- [ ] Used proper registration pattern
- [ ] Followed naming conventions
- [ ] Added descriptive comments
- [ ] Implemented proper delegation

### Testing ✅
- [ ] No conflicts detected in debug tools
- [ ] All navigation flows tested
- [ ] Edge cases verified
- [ ] Performance impact assessed

### Documentation ✅
- [ ] Handler purpose documented
- [ ] Coordination strategy explained
- [ ] CLAUDE.md updated if needed
- [ ] Examples provided
```

---

**Remember**: The goal is to prevent conflicts BEFORE they happen. Use this checklist religiously, and you'll never have key event conflicts again! 🎯